package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.FilesResourceDraft
import io.github.baitian6641.cloudy.domain.FilesResourceReference
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.domain.FilesResourceState
import io.github.baitian6641.cloudy.domain.FilesResourceVersion
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.SshTrustChallenge
import io.github.baitian6641.cloudy.domain.SshTrustEditToken
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real isolated CE SQLite catalogs. Synthetic pins establish no network/listing or user consent. */
@RunWith(AndroidJUnit4::class)
class FilesResourceStoreTest {
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(application.noBackupFilesDir, "files-resources-test-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val workersSettled = AtomicBoolean(true)
    private val owner = WindowOwner.DESKTOP
    private val packageName = "io.github.baitian6641.cloudy.desktop"
    private val profiles = ConnectionProfiles(root, owner, packageName, { false })
    private val trust = SshHostTrustStore(root, owner, packageName, { false })
    private val directory get() = File(root, "files-resources-v1")
    private val database get() = File(directory, "resources.db")
    private val marker get() = File(root, "files-resources-v1.instance")
    private val anchor get() = File(root, "files-resources-v1.lock")

    private fun store(capacity: Int = 512, validProcess: () -> Boolean = { true }) =
        FilesResourceStore(root, owner, packageName, { false }, profiles, trust, capacity, validProcess)
    private fun key(value: Int) = SshHostKeyIdentity.create("ssh-ed25519",
        "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { value.toByte() }))
    private fun profileDraft(label: String = "Owned profile") =
        ConnectionProfileDraft.create(owner, label, ConnectionProtocol.SSH, "files.invalid", 22)
    private fun <T> success(result: FilesResourceResult<T>): T = when (result) {
        is FilesResourceResult.Success -> result.value
        is FilesResourceResult.Failure -> throw AssertionError("FILES_RESOURCE_${result.code}")
    }
    private fun <T> profileSuccess(result: ConnectionProfilesResult<T>): T = when (result) {
        is ConnectionProfilesResult.Success -> result.value
        is ConnectionProfilesResult.Failure -> throw AssertionError("PROFILE_${result.code}")
    }
    private fun <T> trustSuccess(result: SshTrustStoreResult<T>): T = when (result) {
        is SshTrustStoreResult.Success -> result.value
        is SshTrustStoreResult.Failure -> throw AssertionError("TRUST_${result.code}")
    }
    private fun denied(code: FilesResourceCode, result: FilesResourceResult<*>) {
        assertTrue(result is FilesResourceResult.Failure)
        assertEquals(code, (result as FilesResourceResult.Failure).code)
    }
    private fun challenge(peer: io.github.baitian6641.cloudy.domain.SshPeerIdentity, value: Int) =
        SshTrustChallenge.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            trustSuccess(trust.evaluate(peer, key(value))))
    private fun draft(label: String = "Owned resource"): FilesResourceDraft {
        val catalog = profileSuccess(profiles.load())
        val entry = profileSuccess(profiles.create(catalog.generation, profileDraft()))
        val observed = SshObservationProfile.create(catalog.generation, entry.profile)
        val pin = trustSuccess(trust.accept(challenge(observed.peer, 1)))
        return FilesResourceDraft.create(owner, label, observed, pin, "/owned/exact directory", " Exact Account ")
    }
    private fun create(draft: FilesResourceDraft = draft(), capacity: Int = 512): FilesResourceRegistration {
        val catalog = success(store(capacity).load())
        return success(store(capacity).create(catalog.generation, draft))
    }
    private fun revokeTrust(pin: SshTrustSnapshot) = trustSuccess(trust.revoke(
        SshTrustEditToken.create(owner, pin.generation, pin.peer, pin.revision)))
    private fun sql(statement: String) {
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL(statement) }
    }
    private fun retireWorkers(executor: ExecutorService) {
        executor.shutdownNow()
        val terminated = try { executor.awaitTermination(5, TimeUnit.SECONDS) }
        catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        workersSettled.set(terminated)
        check(terminated) { "ISOLATED_WORKERS_UNSETTLED" }
    }

    @After fun cleanup() {
        if (!workersSettled.get()) {
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("cloudyFilesResourceFixtureCleanup", "UNCERTAIN")
            })
            throw AssertionError("ISOLATED_WORKERS_UNSETTLED_PRESERVE_FIXTURE")
        }
        check(root.deleteRecursively() && !root.exists()) { "ISOLATED_FIXTURE_CLEANUP_UNCONFIRMED" }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudyFilesResourceFixtureCleanup", "REMOVED")
        })
    }

    @Test fun privateCreationShareResolveAndRetainedRevocationSurviveReopen() {
        val saved = create()
        assertEquals(FilesResourceState.PRIVATE, saved.state)
        assertEquals(1L, saved.revision)
        denied(FilesResourceCode.INVALID_STATE, store().resolve(saved.reference))
        assertEquals(saved, success(store().resolve(saved.reference, FilesResourceState.PRIVATE)))
        val shared = success(store().share(saved.version()))
        assertEquals(2L, shared.revision)
        assertEquals(shared, success(store().resolve(saved.reference)))
        val revoked = success(store().revoke(shared.version()))
        assertEquals(3L, revoked.revision)
        assertEquals(FilesResourceState.REVOKED, revoked.state)
        val catalog = success(store().load())
        assertEquals(saved.generation, catalog.generation)
        assertEquals(revoked, catalog.registrations.single())
        denied(FilesResourceCode.INVALID_STATE, store().resolve(saved.reference))
        denied(FilesResourceCode.INVALID_STATE, store().resolve(saved.reference, FilesResourceState.REVOKED))
        denied(FilesResourceCode.INVALID_STATE, store().share(revoked.version()))
        denied(FilesResourceCode.INVALID_STATE, store().revoke(revoked.version()))
    }

    @Test fun staleGenerationAndStaleRevisionCannotMutateOrResurrect() {
        val saved = create()
        denied(FilesResourceCode.STALE_GENERATION, store().create(UUID.randomUUID().toString(), saved.draft))
        denied(FilesResourceCode.STALE_GENERATION, store().share(FilesResourceVersion.create(
            UUID.randomUUID().toString(), saved.reference.resourceId, saved.revision)))
        denied(FilesResourceCode.INVALID_INPUT, store().create("invalid", saved.draft))
        val shared = success(store().share(saved.version()))
        denied(FilesResourceCode.CONFLICT, store().share(saved.version()))
        denied(FilesResourceCode.CONFLICT, store().revoke(saved.version()))
        success(store().revoke(shared.version()))
        denied(FilesResourceCode.CONFLICT, store().share(shared.version()))
        denied(FilesResourceCode.NOT_FOUND, store().revoke(FilesResourceVersion.create(
            saved.generation, UUID.randomUUID().toString(), 1)))
    }

    @Test fun exactFourIdsAreRequiredAndNoNamespaceIdsAreReused() {
        val first = create()
        val second = create(first.draft)
        val one = first.reference
        val two = second.reference
        val all = listOf(one.hostId, one.environmentId, one.workspaceId, one.resourceId,
            two.hostId, two.environmentId, two.workspaceId, two.resourceId)
        assertEquals(8, all.toSet().size)
        success(store().share(first.version()))
        val mixed = FilesResourceReference.restore(two.hostId, one.environmentId, one.workspaceId, one.resourceId)
        denied(FilesResourceCode.NOT_FOUND, store().resolve(mixed))
        sql("UPDATE resources SET host_id='${one.environmentId}' WHERE resource_id='${two.resourceId}'")
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
    }

    @Test fun actualProfileEditBlocksCreateShareResolveButNotRevocation() {
        val saved = create()
        val bound = saved.profile
        profileSuccess(profiles.update(ConnectionProfileVersion.create(bound.generation,
            bound.profile.id, bound.profile.revision), profileDraft("Edited profile")))
        denied(FilesResourceCode.STALE_BINDING, store().create(saved.generation, saved.draft))
        denied(FilesResourceCode.STALE_BINDING, store().share(saved.version()))
        denied(FilesResourceCode.STALE_BINDING, store().resolve(saved.reference, FilesResourceState.PRIVATE))
        assertEquals(FilesResourceState.REVOKED, success(store().revoke(saved.version())).state)
    }

    @Test fun actualProfileDeletionAndProtocolChangeDoNotRemainBindings() {
        val first = create()
        val bound = first.profile
        profileSuccess(profiles.update(ConnectionProfileVersion.create(bound.generation, bound.profile.id,
            bound.profile.revision), ConnectionProfileDraft.create(owner, "Changed", ConnectionProtocol.RDP,
            bound.profile.host, bound.profile.port)))
        denied(FilesResourceCode.STALE_BINDING, store().share(first.version()))
        val catalog = profileSuccess(profiles.load())
        val current = catalog.profiles.single()
        profileSuccess(profiles.delete(ConnectionProfileVersion.create(catalog.generation, current.id, current.revision)))
        denied(FilesResourceCode.STALE_BINDING, store().create(first.generation, first.draft))
    }

    @Test fun actualTrustReplacementAndRevocationInvalidateExactRevision() {
        val first = create()
        val shared = success(store().share(first.version()))
        val replacement = trustSuccess(trust.accept(challenge(first.profile.peer, 2)))
        denied(FilesResourceCode.STALE_BINDING, store().resolve(shared.reference))
        denied(FilesResourceCode.STALE_BINDING, store().create(first.generation, first.draft))
        revokeTrust(replacement)
        denied(FilesResourceCode.STALE_BINDING, store().resolve(shared.reference))
        success(store().revoke(shared.version()))
    }

    @Test fun sameKeyReacceptedAtNewTrustRevisionDoesNotReviveOldRegistration() {
        val saved = create()
        val shared = success(store().share(saved.version()))
        revokeTrust(saved.acceptedTrust)
        val reaccepted = trustSuccess(trust.accept(challenge(saved.profile.peer, 1)))
        assertEquals(saved.acceptedTrust.status, reaccepted.status)
        assertEquals(saved.acceptedTrust.revision + 2, reaccepted.revision)
        denied(FilesResourceCode.STALE_BINDING, store().resolve(shared.reference))
        denied(FilesResourceCode.STALE_BINDING, store().create(saved.generation, saved.draft))
        assertEquals(shared, success(store().load()).registrations.single())
    }

    @Test fun tombstonesCountTowardCapacityAndNoUpsertOccurs() {
        val saved = create(capacity = 1)
        success(store(1).revoke(saved.version()))
        denied(FilesResourceCode.CAPACITY, store(1).create(saved.generation, saved.draft))
        assertEquals(1, success(store(1).load()).registrations.size)
    }

    @Test fun competingRealConnectionsCannotBothAdvanceSameVersion() {
        val saved = create()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        workersSettled.set(false)
        try {
            val tasks = (0..1).map { executor.submit(Callable { start.await(); store().share(saved.version()) }) }
            start.countDown()
            val results = tasks.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is FilesResourceResult.Success })
            val failed = results.filterIsInstance<FilesResourceResult.Failure>().single()
            assertTrue(failed.code in setOf(FilesResourceCode.BUSY, FilesResourceCode.CONFLICT))
            denied(FilesResourceCode.CONFLICT, store().share(saved.version()))
            assertEquals(2L, success(store().load()).registrations.single().revision)
        } finally { retireWorkers(executor) }
    }

    @Test fun permanentFileAndCoordinationLocksFailBusyWithoutMutation() {
        val saved = create()
        RandomAccessFile(anchor, "rw").use { file -> file.channel.lock().use {
            denied(FilesResourceCode.BUSY, store().share(saved.version()))
        } }
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        workersSettled.set(false)
        try {
            val task = executor.submit {
                SshAuthenticationRegistry.lock.lock()
                try { held.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                finally { SshAuthenticationRegistry.lock.unlock() }
            }
            check(held.await(10, TimeUnit.SECONDS))
            denied(FilesResourceCode.BUSY, store().share(saved.version()))
            release.countDown(); task.get(10, TimeUnit.SECONDS)
            assertEquals(saved, success(store().load()).registrations.single())
        } finally { release.countDown(); retireWorkers(executor) }
    }

    @Test fun missingEstablishedDatabaseAndMarkerAreNeverRecreated() {
        val saved = create()
        assertTrue(database.delete())
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
        assertFalse(database.exists())
        assertTrue(marker.delete())
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().create(saved.generation, saved.draft))
        assertFalse(database.exists())
    }

    @Test fun missingPermanentAnchorDeniesWithoutReplacement() {
        create()
        val before = database.readBytes()
        assertTrue(anchor.delete())
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
        assertFalse(anchor.exists())
        assertArrayEquals(before, database.readBytes())
    }

    @Test fun interruptedBootstrapAndDifferentMarkerNeverResetTheStore() {
        anchor.writeText(UUID.randomUUID().toString(), Charsets.US_ASCII)
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
        assertFalse(directory.exists()); assertFalse(marker.exists())
        assertTrue(anchor.delete())
        create()
        val before = database.readBytes()
        marker.writeText(UUID.randomUUID().toString(), Charsets.US_ASCII)
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
        assertArrayEquals(before, database.readBytes())
    }

    @Test fun corruptBytesArePreservedAndNeverAutoDeleted() {
        create()
        val bytes = ByteArray(8192) { 0x5a }
        database.writeBytes(bytes)
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
        assertArrayEquals(bytes, database.readBytes())
    }

    @Test fun newerSchemaAndWrongMetadataFailClosed() {
        create()
        sql("PRAGMA user_version=2")
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
        sql("PRAGMA user_version=1")
        sql("UPDATE metadata SET owner_uid=-1")
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
    }

    @Test fun noncanonicalRowsAndWrongTypesAreNotAccepted() {
        create()
        sql("UPDATE resources SET host='FILES.INVALID'")
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
        sql("UPDATE resources SET host='files.invalid',revision='not-an-integer'")
        denied(FilesResourceCode.STORE_UNAVAILABLE, store().load())
    }

    @Test fun overflowDoesNotWriteOrWrap() {
        val saved = create()
        sql("UPDATE resources SET revision=${Long.MAX_VALUE}")
        val overflow = FilesResourceVersion.create(saved.generation, saved.reference.resourceId, Long.MAX_VALUE)
        denied(FilesResourceCode.REVISION_EXHAUSTED, store().share(overflow))
        denied(FilesResourceCode.REVISION_EXHAUSTED, store().revoke(overflow))
        assertEquals(Long.MAX_VALUE, success(store().load()).registrations.single().revision)
    }

    @Test fun actualRejectedInsertIsCompletionUnknownAndDoesNotAutoRetry() {
        val draft = draft()
        val generation = success(store().load()).generation
        sql("CREATE TRIGGER refuse_resource BEFORE INSERT ON resources BEGIN SELECT RAISE(ABORT,'TEST_ONLY'); END")
        denied(FilesResourceCode.COMPLETION_UNKNOWN, store().create(generation, draft))
        assertTrue(success(store().load()).registrations.isEmpty())
        assertTrue(success(store().load()).registrations.isEmpty())
    }

    @Test fun mainThreadDeviceProtectedOwnerAndProcessRejectBeforeStoreCreation() {
        val result = AtomicReference<FilesResourceResult<*>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(FilesResourceStore(application).load())
        }
        denied(FilesResourceCode.WRONG_THREAD, result.get())
        denied(FilesResourceCode.STORE_UNAVAILABLE, FilesResourceStore(application.createDeviceProtectedStorageContext()).load())
        denied(FilesResourceCode.WRONG_OWNER, FilesResourceStore(root, owner, "unexpected.package", { false }, profiles, trust).load())
        denied(FilesResourceCode.WRONG_OWNER, store(validProcess = { false }).load())
        assertFalse(anchor.exists()); assertFalse(marker.exists()); assertFalse(directory.exists())
    }

    @Test fun catalogAndResultRemainRedactedAndCatalogCannotBeMutated() {
        val saved = create()
        val catalog = success(store().load())
        assertFalse(catalog.toString().contains(saved.label))
        assertFalse(FilesResourceResult.Success(saved).toString().contains(saved.accountName))
        var rejected = false
        try { (catalog.registrations as MutableList<FilesResourceRegistration>).clear() } catch (_: UnsupportedOperationException) { rejected = true }
        assertTrue(rejected)
        assertEquals(1, success(store().load()).registrations.size)
    }
}
