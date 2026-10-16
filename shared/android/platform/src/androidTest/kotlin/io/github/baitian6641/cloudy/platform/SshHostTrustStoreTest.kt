package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshHostTrust
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustChallenge
import io.github.baitian6641.cloudy.domain.SshTrustEditToken
import io.github.baitian6641.cloudy.domain.SshTrustEvaluation
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Base64
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
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Real app-private SQLite/locking boundaries; public digest fixtures imply no SSH handshake. */
@RunWith(AndroidJUnit4::class)
class SshHostTrustStoreTest {
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(application.noBackupFilesDir, "trust-test-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val directory get() = File(root, "ssh-host-trust-v1")
    private val database get() = File(directory, "trust.db")
    private val marker get() = File(root, "ssh-host-trust-v1.instance")
    private val anchor get() = File(root, "ssh-host-trust-v1.lock")
    private val owner = WindowOwner.CONSOLE
    private val packageName = "io.github.baitian6641.cloudy.console"
    private val peer = SshPeerIdentity.create("example.test", 22)
    private val firstKey = key(1)
    private val secondKey = key(2)

    private fun store(capacity: Int = 512) = SshHostTrustStore(root, owner, packageName, { false }, capacity)
    private fun key(value: Int, format: String = "ssh-ed25519") = SshHostKeyIdentity.create(format,
        "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { value.toByte() }))
    private fun <T> success(result: SshTrustStoreResult<T>): T = when (result) {
        is SshTrustStoreResult.Success -> result.value
        is SshTrustStoreResult.Failure -> throw AssertionError("Expected success: ${result.code}")
    }
    private fun denied(code: SshTrustStoreCode, result: SshTrustStoreResult<*>) {
        assertTrue(result is SshTrustStoreResult.Failure)
        assertEquals(code, (result as SshTrustStoreResult.Failure).code)
    }
    private fun challenge(evaluation: SshTrustEvaluation) =
        SshTrustChallenge.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), evaluation)
    private fun evaluated(key: SshHostKeyIdentity = firstKey, at: SshPeerIdentity = peer, capacity: Int = 512) =
        success(store(capacity).evaluate(at, key))
    private fun enrolled(capacity: Int = 512) = success(store(capacity).accept(challenge(evaluated(capacity = capacity))))
    private fun token(snapshot: SshTrustSnapshot) =
        SshTrustEditToken.create(snapshot.owner, snapshot.generation, snapshot.peer, snapshot.revision)
    private fun changeDatabase(sql: String) {
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL(sql) }
    }

    @After fun cleanup() { check(root.deleteRecursively()) }

    @Test fun unknownMatchCrossAlgorithmChangedRevokedAndExplicitReenrollmentStayDistinct() {
        val unknown = evaluated()
        assertTrue(unknown is SshTrustEvaluation.Unknown)
        assertEquals(0L, unknown.token.expectedRevision)
        success(store().accept(challenge(unknown)))
        assertTrue(evaluated() is SshTrustEvaluation.Match)
        val otherType = key(1, "ecdsa-sha2-nistp256")
        val changed = evaluated(otherType)
        assertTrue(changed is SshTrustEvaluation.Changed)
        val replaced = success(store().accept(challenge(changed)))
        assertEquals(2L, replaced.revision)
        val revoked = success(store().revoke(token(replaced)))
        assertEquals(3L, revoked.revision)
        assertTrue(evaluated(otherType) is SshTrustEvaluation.Revoked)
        assertTrue(evaluated(secondKey) is SshTrustEvaluation.Revoked)
        val reenrolled = success(store().accept(challenge(evaluated(otherType))))
        assertEquals(4L, reenrolled.revision)
        assertTrue(evaluated(otherType) is SshTrustEvaluation.Match)
    }

    @Test fun pinAndRetainedTombstoneSurviveIndependentReopen() {
        val saved = enrolled()
        assertEquals(saved, success(store().lookup(peer)).current)
        val revoked = success(store().revoke(token(saved)))
        val reopened = success(store().load())
        assertEquals(saved.generation, reopened.generation)
        assertEquals(revoked, reopened.snapshots.single())
        assertEquals(firstKey, (revoked.status as SshTrustStatus.Revoked).lastKey)
    }

    @Test fun retainedAbsenceTokenCannotTurnEnrolledOrRevokedPeerIntoFirstUse() {
        val unknown = evaluated()
        val approval = challenge(unknown)
        val saved = success(store().accept(approval))
        denied(SshTrustStoreCode.CONFLICT, store().accept(approval))
        success(store().revoke(token(saved)))
        denied(SshTrustStoreCode.CONFLICT, store().accept(approval))
        denied(SshTrustStoreCode.NOT_FOUND, store().revoke(unknown.token))
        assertTrue(evaluated() is SshTrustEvaluation.Revoked)
    }

    @Test fun exactOldKeyAndReasonAreCheckedBeyondRevisionNumber() {
        val saved = enrolled()
        val wrongKeySnapshot = SshTrustSnapshot.create(owner, saved.generation, peer, saved.revision,
            SshTrustStatus.accepted(secondKey))
        val misleading = SshHostTrust.evaluate(owner, saved.generation, peer, key(3), wrongKeySnapshot)
        denied(SshTrustStoreCode.INVALID_STATE, store().accept(challenge(misleading)))
        val fakeTombstone = SshTrustSnapshot.create(owner, saved.generation, peer, saved.revision,
            SshTrustStatus.revoked(firstKey))
        val reenrollment = SshHostTrust.evaluate(owner, saved.generation, peer, secondKey, fakeTombstone)
        denied(SshTrustStoreCode.INVALID_STATE, store().accept(challenge(reenrollment)))
        assertTrue(evaluated() is SshTrustEvaluation.Match)
    }

    @Test fun generationMismatchDeniesAcceptanceAndRevocation() {
        val saved = enrolled()
        val generation = UUID.randomUUID().toString()
        val absent = SshHostTrust.evaluate(owner, generation, SshPeerIdentity.create("other.test", 22), firstKey, null)
        denied(SshTrustStoreCode.STALE_GENERATION, store().accept(challenge(absent)))
        denied(SshTrustStoreCode.STALE_GENERATION,
            store().revoke(SshTrustEditToken.create(owner, generation, peer, saved.revision)))
        assertEquals(saved, success(store().lookup(peer)).current)
    }

    @Test fun concurrentFirstUseHasOnlyOneWinner() {
        val first = challenge(evaluated())
        val second = challenge(evaluated(secondKey))
        val results = concurrently({ store().accept(first) }, { store().accept(second) })
        assertOneWinner(results)
        assertEquals(1L, success(store().lookup(peer)).current?.revision)
    }

    @Test fun concurrentReplacementHasOnlyOneWinner() {
        enrolled()
        val a = challenge(evaluated(secondKey))
        val b = challenge(evaluated(key(3)))
        assertOneWinner(concurrently({ store().accept(a) }, { store().accept(b) }))
        assertEquals(2L, success(store().lookup(peer)).current?.revision)
    }

    @Test fun replacementAndRevocationInvalidateEachOthersOriginalToken() {
        val saved = enrolled()
        val replacement = challenge(evaluated(secondKey))
        assertOneWinner(concurrently({ store().accept(replacement) }, { store().revoke(token(saved)) }))
        val current = checkNotNull(success(store().lookup(peer)).current)
        assertEquals(2L, current.revision)
        when (val status = current.status) {
            is SshTrustStatus.Accepted -> assertEquals(secondKey, status.key)
            is SshTrustStatus.Revoked -> assertEquals(firstKey, status.lastKey)
        }
        denied(SshTrustStoreCode.CONFLICT, store().accept(replacement))
        denied(SshTrustStoreCode.CONFLICT, store().revoke(token(saved)))
    }

    @Test fun tombstonesConsumeCapacityWithoutBlockingExplicitReenrollment() {
        val saved = enrolled(1)
        success(store(1).revoke(token(saved)))
        val another = SshPeerIdentity.create("other.test", 22)
        denied(SshTrustStoreCode.CAPACITY, store(1).accept(challenge(evaluated(at = another, capacity = 1))))
        success(store(1).accept(challenge(evaluated(secondKey, capacity = 1))))
        assertEquals(1, success(store(1).load()).snapshots.size)
    }

    @Test fun repeatedRevocationDoesNotBumpRevisionAndAbsentRevocationCreatesNoRow() {
        val unknown = evaluated()
        denied(SshTrustStoreCode.NOT_FOUND, store().revoke(unknown.token))
        assertTrue(success(store().load()).snapshots.isEmpty())
        val saved = enrolled()
        val revoked = success(store().revoke(token(saved)))
        denied(SshTrustStoreCode.INVALID_STATE, store().revoke(token(revoked)))
        assertEquals(revoked, success(store().lookup(peer)).current)
    }

    @Test fun ownerMismatchAndProductionContextDenyWithoutCreatingStorage() {
        denied(SshTrustStoreCode.WRONG_OWNER,
            SshHostTrustStore(root, WindowOwner.DESKTOP, packageName, { false }).load())
        assertFalse(anchor.exists())
        denied(SshTrustStoreCode.WRONG_OWNER, SshHostTrustStore(application).load())
        val generation = success(store().load()).generation
        val other = SshHostTrust.evaluate(WindowOwner.DESKTOP, generation, peer, firstKey, null)
        denied(SshTrustStoreCode.WRONG_OWNER, store().accept(challenge(other)))
        denied(SshTrustStoreCode.WRONG_OWNER, store().revoke(other.token))
    }

    @Test fun deviceProtectedContextAndActualMainThreadAreRejectedBeforeOpening() {
        val protected = application.createDeviceProtectedStorageContext()
        assertTrue(protected.isDeviceProtectedStorage)
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, SshHostTrustStore(protected).load())
        assertFalse(File(protected.noBackupFilesDir, "ssh-host-trust-v1").exists())
        val result = AtomicReference<SshTrustStoreResult<SshTrustCatalog>>()
        val main = SshHostTrustStore(root, owner, packageName, { Looper.myLooper() == Looper.getMainLooper() })
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(main.load()) }
        denied(SshTrustStoreCode.WRONG_THREAD, result.get())
        assertFalse(anchor.exists())
    }

    @Test fun contendedLockDoesNotEnrollAndMaximumRevisionCannotWrap() {
        val unknown = challenge(evaluated())
        RandomAccessFile(anchor, "rw").use { file ->
            file.channel.lock().use { denied(SshTrustStoreCode.BUSY, store().accept(unknown)) }
        }
        assertTrue(success(store().load()).snapshots.isEmpty())
        enrolled()
        changeDatabase("UPDATE pins SET revision=9223372036854775807")
        val maximum = checkNotNull(success(store().lookup(peer)).current)
        denied(SshTrustStoreCode.REVISION_EXHAUSTED, store().accept(challenge(evaluated(secondKey))))
        denied(SshTrustStoreCode.REVISION_EXHAUSTED, store().revoke(token(maximum)))
        assertEquals(Long.MAX_VALUE, success(store().lookup(peer)).current?.revision)
    }

    @Test fun metadataOwnerUidAndNewerSchemaFailClosed() {
        enrolled()
        changeDatabase("UPDATE metadata SET owner_uid=4294967296")
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
        changeDatabase("UPDATE metadata SET owner_uid=${android.os.Process.myUid()},version=2")
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
        changeDatabase("UPDATE metadata SET version=1")
        changeDatabase("PRAGMA user_version=2")
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
    }

    @Test fun noncanonicalRowsAndInvalidStateTypesAreNotImported() {
        enrolled()
        changeDatabase("UPDATE pins SET host='EXAMPLE.TEST'")
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
        changeDatabase("UPDATE pins SET host='example.test',port=4294967296")
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
        changeDatabase("UPDATE pins SET port=22,state='revoked'")
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
        changeDatabase("UPDATE pins SET state=1,fingerprint='invalid'")
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
    }

    @Test fun missingEstablishedStoreAndInterruptedInitializationNeverResetTrust() {
        val saved = enrolled()
        assertTrue(database.delete())
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().evaluate(peer, firstKey))
        assertFalse(database.exists())
        assertTrue(directory.deleteRecursively())
        assertTrue(marker.delete())
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
        assertFalse(directory.exists())
        assertTrue(saved.revision > 0)
    }

    @Test fun damagedMarkerAndSymlinkedAnchorArePreserved() {
        anchor.writeText(UUID.randomUUID().toString())
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
        assertFalse(directory.exists())
        assertTrue(anchor.delete())
        enrolled()
        marker.writeText(UUID.randomUUID().toString())
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
        assertTrue(anchor.delete())
        val target = File(root, "unrelated").apply { writeText("preserved") }
        Files.createSymbolicLink(anchor.toPath(), target.toPath())
        denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
        assertEquals("preserved", target.readText())
    }

    @Test fun corruptAndZeroDatabasesRemainUnavailableAndUnmodified() {
        enrolled()
        for (bytes in listOf("corrupt database".toByteArray(), byteArrayOf())) {
            database.writeBytes(bytes)
            denied(SshTrustStoreCode.STORE_UNAVAILABLE, store().load())
            assertArrayEquals(bytes, database.readBytes())
        }
    }

    @Test fun attemptedInsertAndRevocationFailuresRequireReloadWithoutAutomaticRetry() {
        val unknown = challenge(evaluated())
        changeDatabase("CREATE TRIGGER refuse_insert BEFORE INSERT ON pins BEGIN SELECT RAISE(ABORT,'fixture'); END")
        denied(SshTrustStoreCode.COMPLETION_UNKNOWN, store().accept(unknown))
        assertTrue(success(store().load()).snapshots.isEmpty())
        changeDatabase("DROP TRIGGER refuse_insert")
        val saved = success(store().accept(unknown))
        changeDatabase("CREATE TRIGGER refuse_update BEFORE UPDATE ON pins BEGIN SELECT RAISE(ABORT,'fixture'); END")
        denied(SshTrustStoreCode.COMPLETION_UNKNOWN, store().revoke(token(saved)))
        assertEquals(saved, success(store().lookup(peer)).current)
    }

    @Test fun catalogCannotBeMutatedAndRepresentationsAreRedacted() {
        enrolled()
        val catalog = success(store().load())
        try {
            (catalog.snapshots as MutableList<SshTrustSnapshot>).clear()
            fail("Expected immutable catalog")
        } catch (_: UnsupportedOperationException) { /* Expected wrapper boundary. */ }
        assertEquals(1, success(store().load()).snapshots.size)
        assertEquals("SshTrustCatalog(redacted)", catalog.toString())
        assertEquals("SshTrustLookup(redacted)", success(store().lookup(peer)).toString())
    }

    private fun concurrently(
        first: () -> SshTrustStoreResult<SshTrustSnapshot>, second: () -> SshTrustStoreResult<SshTrustSnapshot>,
    ): List<SshTrustStoreResult<SshTrustSnapshot>> {
        val executor = Executors.newFixedThreadPool(2)
        return try {
            executor.invokeAll(listOf(Callable { first() }, Callable { second() }), 10, TimeUnit.SECONDS).map { it.get() }
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun assertOneWinner(results: List<SshTrustStoreResult<SshTrustSnapshot>>) {
        assertEquals(1, results.count { it is SshTrustStoreResult.Success })
        val loser = results.filterIsInstance<SshTrustStoreResult.Failure>().single()
        assertTrue(loser.code in setOf(SshTrustStoreCode.BUSY, SshTrustStoreCode.CONFLICT))
    }
}
