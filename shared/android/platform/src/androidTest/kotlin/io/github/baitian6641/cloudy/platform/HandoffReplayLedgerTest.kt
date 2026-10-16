package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.HandoffEnvelope
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SQLite, filesystem and locking; injected time belongs only to this test source set. */
@RunWith(AndroidJUnit4::class)
class HandoffReplayLedgerTest {
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(application.noBackupFilesDir, "replay-test-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val context = object : ContextWrapper(application) {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir(): File = root
    }
    private var now = Instant.parse("2026-10-16T10:00:00.123456789Z")
    private val directory get() = File(root, "handoff-replay-v1")
    private val marker get() = File(root, "handoff-replay-v1.instance")
    private val database get() = File(directory, "requests.db")
    private fun ledger(capacity: Int = 4096) = HandoffReplayLedger(context, { now }, capacity)
    private fun envelope(
        id: String = UUID.randomUUID().toString(),
        created: Instant = now.minusSeconds(1), expiry: Instant = now.plusSeconds(30),
    ) = HandoffEnvelope.create(1, id, created.toString(), expiry.toString(),
        "open-console", "host", "environment", "workspace", "resource")

    @After fun cleanup() { check(root.deleteRecursively()) }

    @Test fun freshReservationSurvivesIndependentInstancesAndReopen() {
        val request = envelope()
        assertEquals(HandoffReplayResult.RESERVED, ledger().reserve(request))
        assertEquals(HandoffReplayResult.DUPLICATE, ledger().reserve(request))
        assertEquals(HandoffReplayResult.DUPLICATE, ledger().reserve(request))
        assertTrue(database.isFile)
        assertEquals(36L, marker.length())
    }

    @Test fun concurrentInstancesConsumeOnlyOnce() {
        val request = envelope()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks = (1..24).map { Callable {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                var result: HandoffReplayResult
                do {
                    result = ledger().reserve(request)
                    if (result == HandoffReplayResult.BUSY) Thread.yield()
                } while (result == HandoffReplayResult.BUSY && System.nanoTime() < deadline)
                result
            } }
            val results = executor.invokeAll(tasks, 20, TimeUnit.SECONDS).map { it.get() }
            assertEquals(1, results.count { it == HandoffReplayResult.RESERVED })
            assertEquals(23, results.count { it == HandoffReplayResult.DUPLICATE })
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun expiryPruningIsExactAtNanosecondAndCapacityDenialDoesNotConsume() {
        val first = envelope(expiry = now.plusNanos(1))
        val replacement = envelope()
        assertEquals(HandoffReplayResult.RESERVED, ledger(1).reserve(first))
        assertEquals(HandoffReplayResult.CAPACITY_REACHED, ledger(1).reserve(replacement))
        now = now.plusNanos(1)
        assertEquals(HandoffReplayResult.RESERVED, ledger(1).reserve(replacement))
        assertEquals(HandoffReplayResult.EXPIRED, ledger(1).reserve(first))
    }

    @Test fun pruningPersistsHighWaterAndRollbackCannotResurrectAnOriginalEnvelope() {
        val start = now
        val old = envelope(expiry = now.plusSeconds(1))
        assertEquals(HandoffReplayResult.RESERVED, ledger(1).reserve(old))
        now = now.plusSeconds(1)
        val current = envelope()
        assertEquals(HandoffReplayResult.RESERVED, ledger(1).reserve(current))
        now = start
        assertEquals(HandoffReplayResult.CLOCK_ROLLBACK, ledger(1).reserve(old))
        assertEquals(HandoffReplayResult.CLOCK_ROLLBACK, ledger(1).reserve(envelope()))
        now = start.plusSeconds(1)
        assertEquals(HandoffReplayResult.EXPIRED, ledger(1).reserve(old))
        assertEquals(HandoffReplayResult.DUPLICATE, ledger(1).reserve(current))
    }

    @Test fun futureAndExpiredDenialsDoNotAdvanceTimeOrReserveIds() {
        val expired = envelope(created = now.minusSeconds(2), expiry = now)
        val future = envelope(created = now.plusSeconds(5).plusNanos(1), expiry = now.plusSeconds(20))
        assertEquals(HandoffReplayResult.EXPIRED, ledger().reserve(expired))
        assertEquals(HandoffReplayResult.NOT_YET_VALID, ledger().reserve(future))
        now = now.minusSeconds(10)
        assertEquals(HandoffReplayResult.RESERVED, ledger().reserve(envelope(id = expired.requestId)))
    }

    @Test fun retainedUuidRejectsChangedPayloadButReuseAfterPruningHasFiniteScope() {
        val original = envelope(expiry = now.plusSeconds(1))
        assertEquals(HandoffReplayResult.RESERVED, ledger(2).reserve(original))
        assertEquals(HandoffReplayResult.DUPLICATE, ledger(2).reserve(envelope(id = original.requestId)))
        now = now.plusSeconds(1)
        assertEquals(HandoffReplayResult.RESERVED, ledger(2).reserve(envelope()))
        // A newly dated request reusing a retired UUID is outside indefinite UUID uniqueness.
        assertEquals(HandoffReplayResult.RESERVED, ledger(2).reserve(envelope(id = original.requestId)))
        assertEquals(HandoffReplayResult.EXPIRED, ledger(2).reserve(original))
    }

    @Test fun missingDatabaseAndEvenMissingWholeDirectoryFailClosed() {
        assertEquals(HandoffReplayResult.RESERVED, ledger().reserve(envelope()))
        assertTrue(database.delete())
        assertEquals(HandoffReplayResult.STORE_UNAVAILABLE, ledger().reserve(envelope()))
        assertFalse(database.exists())
        assertTrue(directory.deleteRecursively())
        assertTrue(marker.delete())
        // The durable external lock anchor still witnesses prior initialization.
        assertEquals(HandoffReplayResult.STORE_UNAVAILABLE, ledger().reserve(envelope()))
        assertFalse(directory.exists())
        assertFalse(marker.exists())
    }

    @Test fun damagedMissingOrMismatchedMarkerCannotInitializeAnEmptyReplacement() {
        val request = envelope()
        assertEquals(HandoffReplayResult.RESERVED, ledger().reserve(request))
        val original = marker.readBytes()
        for (bytes in listOf(byteArrayOf(), "not-a-uuid".toByteArray(), UUID.randomUUID().toString().toByteArray())) {
            marker.writeBytes(bytes)
            assertEquals(HandoffReplayResult.STORE_UNAVAILABLE, ledger().reserve(envelope()))
            assertArrayEquals(bytes, marker.readBytes())
        }
        assertTrue(marker.delete())
        assertEquals(HandoffReplayResult.STORE_UNAVAILABLE, ledger().reserve(envelope()))
        assertFalse(marker.exists())
        marker.writeBytes(original)
        assertEquals(HandoffReplayResult.DUPLICATE, ledger().reserve(request))
    }

    @Test fun corruptOrZeroDatabaseIsPreservedAndNeverRecreated() {
        assertEquals(HandoffReplayResult.RESERVED, ledger().reserve(envelope()))
        for (bytes in listOf("corrupt database".toByteArray(), byteArrayOf())) {
            database.writeBytes(bytes)
            assertEquals(HandoffReplayResult.STORE_UNAVAILABLE, ledger().reserve(envelope()))
            assertTrue(database.isFile)
            assertArrayEquals(bytes, database.readBytes())
        }
    }

    @Test fun interruptedInitializationAnchorWithoutDatabaseFailsClosed() {
        File(root, "handoff-replay-v1.lock").writeText(UUID.randomUUID().toString())
        assertEquals(HandoffReplayResult.STORE_UNAVAILABLE, ledger().reserve(envelope()))
        assertFalse(database.exists())
        assertFalse(marker.exists())
    }

    @Test fun mainThreadAndContendedFileLockReturnTypedDenialWithoutConsuming() {
        val request = envelope()
        val mainResult = AtomicReference<HandoffReplayResult>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            mainResult.set(ledger().reserve(request))
        }
        assertEquals(HandoffReplayResult.WRONG_THREAD, mainResult.get())
        assertFalse(directory.exists())
        assertEquals(HandoffReplayResult.RESERVED, ledger().reserve(request))
        RandomAccessFile(File(root, "handoff-replay-v1.lock"), "rw").use { file ->
            file.channel.lock().use {
                assertEquals(HandoffReplayResult.BUSY, ledger().reserve(envelope()))
            }
        }
    }

    @Test fun invalidMetadataTypesAndLargeNanosFailClosedWithoutIntegerTruncation() {
        assertEquals(HandoffReplayResult.RESERVED, ledger().reserve(envelope()))
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE metadata SET high_nanos=4294967296")
        }
        assertEquals(HandoffReplayResult.STORE_UNAVAILABLE, ledger().reserve(envelope()))
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE metadata SET high_nanos='invalid'")
        }
        assertEquals(HandoffReplayResult.STORE_UNAVAILABLE, ledger().reserve(envelope()))
    }

    @Test fun deletedCommittedRowsCannotMasqueradeAsNeverInitializedLedger() {
        assertEquals(HandoffReplayResult.RESERVED, ledger().reserve(envelope()))
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DELETE FROM requests")
        }
        assertEquals(HandoffReplayResult.STORE_UNAVAILABLE, ledger().reserve(envelope()))
    }

    @Test fun deviceProtectedStorageContextIsDeniedWithoutOpeningItsStore() {
        val deviceProtected = application.createDeviceProtectedStorageContext()
        assertEquals(HandoffReplayResult.STORE_UNAVAILABLE,
            HandoffReplayLedger(deviceProtected, { now }, 4096).reserve(envelope()))
    }
}
