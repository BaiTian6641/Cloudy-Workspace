package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SQLite/file cases; fixture owner/root overrides confer no product or IPC identity. */
@RunWith(AndroidJUnit4::class)
class ConnectionProfilesTest {
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(application.noBackupFilesDir, "profiles-test-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val directory get() = File(root, "connection-profiles-v1")
    private val database get() = File(directory, "profiles.db")
    private val marker get() = File(root, "connection-profiles-v1.instance")
    private val anchor get() = File(root, "connection-profiles-v1.lock")
    private val ownerPackage = "io.github.baitian6641.cloudy.console"

    private fun store(capacity: Int = 512) = ConnectionProfiles(root, WindowOwner.CONSOLE, ownerPackage, { false }, capacity)
    private fun draft(label: String = "Local Linux", host: String = "example.test") =
        ConnectionProfileDraft.create(WindowOwner.CONSOLE, label, ConnectionProtocol.SSH, host, 22)

    private fun <T> success(result: ConnectionProfilesResult<T>): T = when (result) {
        is ConnectionProfilesResult.Success -> result.value
        is ConnectionProfilesResult.Failure -> throw AssertionError("Expected success: ${result.code}")
    }

    private fun denied(code: ConnectionProfilesCode, result: ConnectionProfilesResult<*>) {
        assertTrue(result is ConnectionProfilesResult.Failure)
        assertEquals(code, (result as ConnectionProfilesResult.Failure).code)
    }

    private fun insert(capacity: Int = 512): ConnectionProfileEntry {
        val store = store(capacity)
        return success(store.create(success(store.load()).generation, draft()))
    }

    private fun changeDatabase(sql: String) {
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL(sql) }
    }

    @After fun cleanup() { check(root.deleteRecursively()) }

    @Test fun jumpReferenceRequiresAnEligibleSameStoreHopAndBlocksDelete() {
        val store = store()
        val catalog = success(store.load())
        val hop = success(store.create(catalog.generation, draft("Jump", "jump.test")))
        fun targetDraft(jump: String?) = ConnectionProfileDraft.create(WindowOwner.CONSOLE, "Target",
            ConnectionProtocol.SSH, "target.test", 22, jumpProfileId = jump)
        val target = success(store.create(catalog.generation, targetDraft(hop.profile.id)))
        assertEquals(hop.profile.id, target.profile.jumpProfileId)
        assertEquals(hop.profile.id, success(store.load()).profiles.single { it.id == target.profile.id }.jumpProfileId)
        denied(ConnectionProfilesCode.CONFLICT, store.delete(hop.version))
        // A hop cannot be given its own jump while referenced.
        denied(ConnectionProfilesCode.INVALID_INPUT, store.update(hop.version, targetDraft(target.profile.id)))
        // Missing and self references fail closed.
        denied(ConnectionProfilesCode.INVALID_INPUT,
            store.create(catalog.generation, targetDraft(UUID.randomUUID().toString())))
        denied(ConnectionProfilesCode.INVALID_INPUT, store.update(target.version, targetDraft(target.profile.id)))
        // Deleting the unreferenced target leaves the hop deletable.
        success(store.delete(target.version))
        success(store.delete(hop.version))
    }

    @Test fun realCrudRetainsGenerationAndReopensIndependentInstances() {
        val catalog = success(store().load())
        assertTrue(catalog.profiles.isEmpty())
        val original = success(store().create(catalog.generation, draft()))
        assertEquals(1L, original.profile.revision)
        assertEquals(original.profile.id, original.version.id)
        assertEquals(catalog.generation, original.version.generation)
        val loaded = success(store().load())
        assertEquals(catalog.generation, loaded.generation)
        assertEquals(original.profile.id, loaded.profiles.single().id)
        val revised = success(store().update(original.version, draft("Edited", "other.test")))
        assertEquals(original.profile.id, revised.profile.id)
        assertEquals(2L, revised.profile.revision)
        assertEquals("other.test", success(store().load()).profiles.single().host)
        success(store().delete(revised.version))
        assertTrue(success(store().load()).profiles.isEmpty())
        assertEquals(catalog.generation, success(store().load()).generation)
    }

    @Test fun staleWindowsCannotOverwriteOrDeleteTheNewerRevision() {
        val original = insert()
        val updated = success(store().update(original.version, draft("New revision")))
        denied(ConnectionProfilesCode.CONFLICT, store().update(original.version, draft("Stale overwrite")))
        denied(ConnectionProfilesCode.CONFLICT, store().delete(original.version))
        assertEquals(updated.profile.label, success(store().load()).profiles.single().label)
    }

    @Test fun concurrentWritersHaveAtMostOneSuccessfulRevisionChange() {
        val original = insert()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf(
                Callable { store().update(original.version, draft("Window A")) },
                Callable { store().update(original.version, draft("Window B")) },
            ), 10, TimeUnit.SECONDS).map { it.get() }
            assertEquals(1, results.count { it is ConnectionProfilesResult.Success })
            val rejected = results.filterIsInstance<ConnectionProfilesResult.Failure>().single()
            assertTrue(rejected.code in setOf(ConnectionProfilesCode.BUSY, ConnectionProfilesCode.CONFLICT))
            assertEquals(2L, success(store().load()).profiles.single().revision)
        } finally {
            pool.shutdownNow()
            check(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun deletionNeverResurrectsAStaleEditorAndNewCreateUsesAnotherId() {
        val original = insert()
        success(store().delete(original.version))
        denied(ConnectionProfilesCode.NOT_FOUND, store().update(original.version, draft("Stale editor")))
        denied(ConnectionProfilesCode.NOT_FOUND, store().delete(original.version))
        val replacement = success(store().create(original.version.generation, draft()))
        assertNotEquals(original.profile.id, replacement.profile.id)
    }

    @Test fun generationMismatchDeniesEveryMutationWithoutChangingRows() {
        val original = insert()
        val other = UUID.randomUUID().toString()
        val stale = ConnectionProfileVersion.create(other, original.profile.id, original.profile.revision)
        denied(ConnectionProfilesCode.STALE_GENERATION, store().create(other, draft()))
        denied(ConnectionProfilesCode.STALE_GENERATION, store().update(stale, draft("Changed")))
        denied(ConnectionProfilesCode.STALE_GENERATION, store().delete(stale))
        assertEquals(1L, success(store().load()).profiles.single().revision)
    }

    @Test fun duplicatesAreSeparateProfilesAndCapacityDoesNotEvictThem() {
        val first = insert(2)
        val second = success(store(2).create(first.version.generation, draft()))
        assertNotEquals(first.profile.id, second.profile.id)
        denied(ConnectionProfilesCode.CAPACITY, store(2).create(first.version.generation, draft()))
        assertEquals(2, success(store(2).load()).profiles.size)
        success(store(2).delete(first.version))
        success(store(2).create(first.version.generation, draft()))
        assertEquals(2, success(store(2).load()).profiles.size)
    }

    @Test fun ownerMismatchDeniesReadsAndWritesWithoutCreatingStorage() {
        val wrong = ConnectionProfiles(root, WindowOwner.DESKTOP, ownerPackage, { false })
        denied(ConnectionProfilesCode.WRONG_OWNER, wrong.load())
        assertFalse(anchor.exists())
        val original = insert()
        val desktop = ConnectionProfileDraft.create(WindowOwner.DESKTOP, "Desktop", ConnectionProtocol.RDP, "example.test", 3389)
        denied(ConnectionProfilesCode.WRONG_OWNER, store().create(original.version.generation, desktop))
        denied(ConnectionProfilesCode.WRONG_OWNER, store().update(original.version, desktop))
        assertEquals(1L, success(store().load()).profiles.single().revision)
    }

    @Test fun productionContextRequiresItsActualProductPackageAndCredentialStorage() {
        denied(ConnectionProfilesCode.WRONG_OWNER, ConnectionProfiles(application).load())
        val protected = application.createDeviceProtectedStorageContext()
        assertTrue(protected.isDeviceProtectedStorage)
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, ConnectionProfiles(protected).load())
        assertFalse(File(protected.noBackupFilesDir, "connection-profiles-v1").exists())
    }

    @Test fun mainThreadAndContendedFileLockDenyBeforeMutating() {
        val mainStore = ConnectionProfiles(root, WindowOwner.CONSOLE, ownerPackage,
            { Looper.myLooper() == Looper.getMainLooper() })
        val result = AtomicReference<ConnectionProfilesResult<ConnectionProfileCatalog>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(mainStore.load()) }
        denied(ConnectionProfilesCode.WRONG_THREAD, result.get())
        assertFalse(anchor.exists())
        val original = insert()
        RandomAccessFile(anchor, "rw").use { file ->
            file.channel.lock().use {
                denied(ConnectionProfilesCode.BUSY, store().update(original.version, draft("Blocked")))
            }
        }
        assertEquals(1L, success(store().load()).profiles.single().revision)
    }

    @Test fun maximumRevisionCannotWrapAndDeletionRemainsPossible() {
        val original = insert()
        changeDatabase("UPDATE profiles SET revision=9223372036854775807")
        val maximum = success(store().load()).profiles.single()
        val version = ConnectionProfileVersion.create(original.version.generation, maximum.id, maximum.revision)
        denied(ConnectionProfilesCode.REVISION_EXHAUSTED, store().update(version, draft("Overflow")))
        success(store().delete(version))
        assertTrue(success(store().load()).profiles.isEmpty())
    }

    @Test fun malformedMetadataAndNewerSchemaFailClosed() {
        insert()
        // Metadata below the schema's own version marker is a tampered or partial state.
        changeDatabase("UPDATE metadata SET version=1")
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
        changeDatabase("UPDATE metadata SET version=2")
        // A newer user_version refuses without a migration attempt; the v1 case migrated
        // successfully is covered by ConnectionProfilesMigrationTest with a full fixture.
        changeDatabase("PRAGMA user_version=3")
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
        changeDatabase("PRAGMA user_version=2")
        changeDatabase("UPDATE metadata SET capacity='invalid'")
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
    }

    @Test fun noncanonicalOrOutOfBoundsRowsCannotBeSilentlyImported() {
        insert()
        changeDatabase("UPDATE profiles SET port=4294967296")
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
        changeDatabase("UPDATE profiles SET port=22,host='EXAMPLE.TEST'")
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
        changeDatabase("UPDATE profiles SET host='example.test',revision='invalid'")
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
    }

    @Test fun corruptionAndZeroLengthDatabaseArePreservedWithoutRecreation() {
        insert()
        for (bytes in listOf("corrupt database".toByteArray(), byteArrayOf())) {
            database.writeBytes(bytes)
            denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
            assertArrayEquals(bytes, database.readBytes())
        }
    }

    @Test fun attemptedSqlMutationReturnsUncertainCompletionAndRequiresReload() {
        val generation = success(store().load()).generation
        changeDatabase("CREATE TRIGGER refuse_insert BEFORE INSERT ON profiles " +
            "BEGIN SELECT RAISE(ABORT, 'fixture-rejection'); END")
        denied(ConnectionProfilesCode.COMPLETION_UNKNOWN, store().create(generation, draft()))
        // This fixture trigger aborts before insertion, but the API deliberately does not invite
        // automatic retry based on an exception's provider-specific details or diagnostic text.
        assertTrue(success(store().load()).profiles.isEmpty())
        changeDatabase("DROP TRIGGER refuse_insert")
        success(store().create(generation, draft()))
    }

    @Test fun missingDatabaseDirectoryOrMarkerCannotInitializeReplacement() {
        insert()
        assertTrue(database.delete())
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
        assertFalse(database.exists())
        assertTrue(directory.deleteRecursively())
        assertTrue(marker.delete())
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
        assertFalse(directory.exists())
        assertFalse(marker.exists())
    }

    @Test fun damagedMarkerAndInterruptedAnchorAreNotFirstUse() {
        anchor.writeText(UUID.randomUUID().toString())
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
        assertFalse(directory.exists())
        assertFalse(marker.exists())
        assertTrue(anchor.delete())
        val original = insert()
        val bytes = marker.readBytes()
        marker.writeText(UUID.randomUUID().toString())
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
        marker.writeBytes(bytes)
        assertEquals(original.profile.id, success(store().load()).profiles.single().id)
    }

    @Test fun fileSymlinksAreRejectedWithoutTouchingTheirTargets() {
        val outside = File(root, "unrelated").apply { writeText("preserved") }
        Files.createSymbolicLink(anchor.toPath(), outside.toPath())
        denied(ConnectionProfilesCode.STORE_UNAVAILABLE, store().load())
        assertEquals("preserved", outside.readText())
    }

    @Test fun catalogIsAnImmutableCopyAndRepresentationsAreRedacted() {
        val original = insert()
        val catalog = success(store().load())
        try {
            @Suppress("UNCHECKED_CAST")
            (catalog.profiles as MutableList<io.github.baitian6641.cloudy.domain.ConnectionProfile>).clear()
            fail("Expected immutable catalog")
        } catch (_: UnsupportedOperationException) { /* Expected Java unmodifiable-list boundary. */ }
        assertEquals(1, success(store().load()).profiles.size)
        assertEquals("ConnectionProfileCatalog(redacted)", catalog.toString())
        assertEquals("ConnectionProfileEntry(redacted)", original.toString())
        assertEquals("ConnectionProfileVersion(redacted)", original.version.toString())
    }

    @Test fun versionRestoreRejectsNoncanonicalIdentityAndRevision() {
        val entry = insert()
        val restored = ConnectionProfileVersion.parse(entry.version.generation, entry.profile.id, "1")
        assertEquals(entry.profile.id, restored.id)
        for (revision in listOf("0", "-1", "01", "+1", "9223372036854775808", "private-value")) {
            try {
                ConnectionProfileVersion.parse(entry.version.generation, entry.profile.id, revision)
                fail("Expected fixed validation error")
            } catch (error: IllegalArgumentException) { assertEquals("INVALID_INPUT", error.message) }
        }
        denied(ConnectionProfilesCode.INVALID_INPUT, store().create("invalid-private-generation", draft()))
    }
}
