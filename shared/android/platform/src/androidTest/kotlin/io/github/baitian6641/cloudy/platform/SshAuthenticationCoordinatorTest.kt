package io.github.baitian6641.cloudy.platform

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.*
import io.github.baitian6641.cloudy.ssh.SshKeyCredentials
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import io.github.baitian6641.cloudy.ssh.SshSessionCredentials
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedOwnership
import io.github.baitian6641.cloudy.ssh.SshObservationCancellation
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledThreadPoolExecutor
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite ordering, with a deliberately explicit test-only owner/root and elapsed clock. */
@RunWith(AndroidJUnit4::class)
class SshAuthenticationCoordinatorTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(app.noBackupFilesDir, "auth-coordinator-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val owner = WindowOwner.CONSOLE
    private val packageName = "io.github.baitian6641.cloudy.console"
    private val profiles = ConnectionProfiles(root, owner, packageName, { false })
    private val trust = SshHostTrustStore(root, owner, packageName, { false })
    private var now = 100L
    private val coordinator = SshAuthenticationCoordinator(root, owner, profiles, trust) { now }
    private val handles = mutableListOf<SshAuthenticationHandle>()
    // Reflected objects below are policy simulations, never real authenticated engine evidence.
    private val simulatedProofs = mutableListOf<SshAuthenticatedOwnership>()
    private val resources = FilesResourceStore(root, owner, packageName, { false }, profiles, trust)
    private val key = key(1)

    private fun key(n: Int) = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", "SHA256:" +
        Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { n.toByte() }))
    private fun credentials() = SshPasswordCredentials.create("fixture-user".toCharArray(), "fixture-password".toCharArray())
    private fun <T> profileSuccess(r: ConnectionProfilesResult<T>): T = (r as ConnectionProfilesResult.Success).value
    private fun <T> trustSuccess(r: SshTrustStoreResult<T>): T = (r as SshTrustStoreResult.Success).value
    private fun draft(label: String = "Fixture", host: String = "fixture.invalid") =
        ConnectionProfileDraft.create(owner, label, ConnectionProtocol.SSH, host, 22)
    private fun seed(host: String = "fixture.invalid"): SshObservationProfile {
        val catalog = profileSuccess(profiles.load())
        val entry = profileSuccess(profiles.create(catalog.generation, draft(host = host)))
        val profile = SshObservationProfile.create(entry.version.generation, entry.profile)
        trustSuccess(trust.accept(SshTrustChallenge.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            trustSuccess(trust.evaluate(profile.peer, key)))))
        return profile
    }
    private fun begin(profile: SshObservationProfile): SshAuthenticationHandle =
        (coordinator.begin(profile, credentials()) as SshAuthenticationPreparation.Ready).handle.also { handles.add(it) }
    private fun version(profile: SshObservationProfile) =
        ConnectionProfileVersion.create(profile.generation, profile.profile.id, profile.profile.revision)
    private fun revoke(profile: SshObservationProfile) {
        val pin = trustSuccess(trust.lookup(profile.peer)).current!!
        trustSuccess(trust.revoke(SshTrustEditToken.create(owner, pin.generation, pin.peer, pin.revision)))
    }

    @After fun cleanup() {
        handles.forEach { it.close() }
        simulatedProofs.forEach { proof ->
            simulatedRetired(proof)
            handles.forEach { it.releaseSession(proof) }
        }
        SshAuthenticationRegistry.attempts.filter { it.root == root.absoluteFile }.forEach { it.close() }
        check(SshAuthenticationRegistry.attempts.none { it.root == root.absoluteFile })
        check(root.deleteRecursively())
    }

    @Test fun editAndRevokeWinningBeforeGatePreventAnyTransfer() {
        val profile = seed()
        val edited = begin(profile)
        profileSuccess(profiles.update(version(profile), draft("Changed")))
        assertTrue(edited.cancellation.isCancelled)
        assertNull(edited.admit(profile.peer, key))
        val current = profileSuccess(profiles.load())
        val fresh = SshObservationProfile.create(current.generation, current.profiles.single())
        val revoked = begin(fresh)
        revoke(fresh)
        assertTrue(revoked.cancellation.isCancelled)
        assertNull(revoked.admit(fresh.peer, key))
    }

    @Test fun gateTransfersOnceAndLaterRevokeSignalsTransportWithoutRetractingOwnership() {
        val profile = seed()
        val handle = begin(profile)
        val transferred = handle.admit(profile.peer, key)
        assertNotNull(transferred)
        revoke(profile)
        assertTrue(handle.cancellation.isCancelled)
        assertNull(handle.admit(profile.peer, key))
        transferred!!.close()
    }

    @Test fun keyGateRequiresCurrentTrustAndTransfersKeyMaterialOnce() {
        val profile = seed()
        val material = "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n".toByteArray()
        val credentials = SshKeyCredentials.create("fixture-user".toCharArray(), material, null)
        val prepared = coordinator.beginKey(profile, credentials)
        assertTrue("Key preparation requires exact current profile/trust", prepared is SshAuthenticationPreparation.Ready)
        val handle = (prepared as SshAuthenticationPreparation.Ready).handle.also { handles.add(it) }
        revoke(profile)
        assertTrue(handle.cancellation.isCancelled)
        assertNull(handle.admit(profile.peer, key))
        assertNull(handle.admit(profile.peer, key))
        material.fill(0)
    }

    @Test fun unrelatedProfileAndPeerMutationDoesNotCancel() {
        val first = seed()
        val second = seed("other.invalid")
        val handle = begin(first)
        profileSuccess(profiles.update(version(second), draft("Other changed", "other.invalid")))
        revoke(second)
        assertFalse(handle.cancellation.isCancelled)
        handle.admit(first.peer, key)!!.close()
    }

    @Test fun staleGenerationProfileAndUnacceptedTrustFailPreparation() {
        val profile = seed()
        assertEquals(SshAuthenticationCode.PROFILE_CHANGED,
            (coordinator.begin(SshObservationProfile.create(UUID.randomUUID().toString(), profile.profile), credentials())
                as SshAuthenticationPreparation.Failed).code)
        profileSuccess(profiles.update(version(profile), draft("Changed")))
        assertEquals(SshAuthenticationCode.PROFILE_CHANGED,
            (coordinator.begin(profile, credentials()) as SshAuthenticationPreparation.Failed).code)
        val catalog = profileSuccess(profiles.load())
        val fresh = SshObservationProfile.create(catalog.generation, catalog.profiles.single())
        revoke(fresh)
        assertEquals(SshAuthenticationCode.TRUST_REQUIRED,
            (coordinator.begin(fresh, credentials()) as SshAuthenticationPreparation.Failed).code)
    }

    @Test fun wrongObservedKeyAndExactDeadlineDenyAndCancel() {
        val profile = seed()
        val wrong = begin(profile)
        assertNull(wrong.admit(profile.peer, key(2)))
        assertTrue(wrong.cancellation.isCancelled)
        val expired = begin(profile)
        now = expired.expiresAtMillis
        assertNull(expired.admit(profile.peer, key))
        assertTrue(expired.cancellation.isCancelled)
    }

    @Test fun registryCapacityIsBoundedAndCloseReturnsCapacity() {
        val profile = seed()
        repeat(16) { begin(profile) }
        assertEquals(SshAuthenticationCode.CAPACITY,
            (coordinator.begin(profile, credentials()) as SshAuthenticationPreparation.Failed).code)
        handles.first().close()
        begin(profile)
    }

    @Test fun cancellationWinningSignalMonitorPreventsGateAndConcurrentRaceTransfersAtMostOnce() {
        val profile = seed()
        val cancelled = begin(profile)
        cancelled.cancellation.cancel()
        assertNull(cancelled.admit(profile.peer, key))
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(8) {
                val handle = begin(profile)
                val start = CountDownLatch(1)
                val gate = pool.submit<SshSessionCredentials?> { check(start.await(2, TimeUnit.SECONDS)); handle.admit(profile.peer, key) }
                val cancel = pool.submit { check(start.await(2, TimeUnit.SECONDS)); handle.cancellation.cancel() }
                start.countDown()
                val released = gate.get(5, TimeUnit.SECONDS)
                cancel.get(5, TimeUnit.SECONDS)
                assertTrue(handle.cancellation.isCancelled)
                assertNull(handle.admit(profile.peer, key))
                released?.close()
                handle.close()
            }
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS)) }
    }

    @Test fun sameKeyReenrollmentStillInvalidatesOriginalRevisionAuthority() {
        val profile = seed()
        val handle = begin(profile)
        revoke(profile)
        val replacement = trustSuccess(trust.accept(SshTrustChallenge.create(UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), trustSuccess(trust.evaluate(profile.peer, key)))))
        assertEquals(3L, replacement.revision)
        assertEquals(key, (replacement.status as SshTrustStatus.Accepted).key)
        assertNull(handle.admit(profile.peer, key))
        assertTrue(handle.cancellation.isCancelled)
        begin(profile).admit(profile.peer, key)!!.close()
    }

    @Test fun changedTrustStoreGenerationCannotReuseSameKeyAndRevisionAuthority() {
        val profile = seed()
        val handle = begin(profile)
        val generation = UUID.randomUUID().toString()
        // Isolated test root only: model a different internally consistent store lifetime.
        // There is deliberately no production reset/recovery operation.
        SshAuthenticationRegistry.lock.lock()
        try {
            RandomAccessFile(File(root, "ssh-host-trust-v1.lock"), "rw").use { anchor ->
                anchor.channel.lock().use {
                    SQLiteDatabase.openDatabase(File(root, "ssh-host-trust-v1/trust.db").path, null,
                        SQLiteDatabase.OPEN_READWRITE).use { db ->
                        db.execSQL("UPDATE metadata SET generation=? WHERE singleton=1", arrayOf(generation))
                    }
                    File(root, "ssh-host-trust-v1.instance").writeText(generation, Charsets.US_ASCII)
                    anchor.seek(0)
                    anchor.write(generation.toByteArray(Charsets.US_ASCII))
                    anchor.fd.sync()
                }
            }
        } finally { SshAuthenticationRegistry.lock.unlock() }
        val current = trustSuccess(trust.lookup(profile.peer)).current!!
        assertEquals(generation, current.generation)
        assertEquals(1L, current.revision)
        assertEquals(key, (current.status as SshTrustStatus.Accepted).key)
        assertNull(handle.admit(profile.peer, key))
        assertTrue(handle.cancellation.isCancelled)
    }

    @Test fun closedCredentialsAndFailedExpirySchedulingNeverRetainRegistryOrSecrets() {
        val closed = credentials().apply { close() }
        // Closed input is rejected before reading/initializing either production store.
        val unpersisted = SshObservationProfile.create(UUID.randomUUID().toString(), ConnectionProfile.create(draft()))
        assertEquals(SshAuthenticationCode.CREDENTIALS_UNAVAILABLE,
            (coordinator.begin(unpersisted, closed) as SshAuthenticationPreparation.Failed).code)
        assertTrue(root.listFiles()!!.isEmpty())
        val profile = seed()
        val scheduler = ScheduledThreadPoolExecutor(1).apply { shutdown() }
        val rejecting = SshAuthenticationCoordinator(root, owner, profiles, trust, expiryScheduler = scheduler) { now }
        val material = credentials()
        assertEquals(SshAuthenticationCode.STORE_UNAVAILABLE,
            (rejecting.begin(profile, material) as SshAuthenticationPreparation.Failed).code)
        assertTrue(material.isClosed)
        assertTrue(SshAuthenticationRegistry.attempts.none { it.root == root.absoluteFile })
        assertTrue(scheduler.awaitTermination(2, TimeUnit.SECONDS))
        val handle = begin(profile)
        val externallyClosed = credentials()
        val admittedClosed = (coordinator.begin(profile, externallyClosed) as SshAuthenticationPreparation.Ready).handle
        handles.add(admittedClosed)
        externallyClosed.close()
        assertNull(admittedClosed.admit(profile.peer, key))
        assertTrue(admittedClosed.cancellation.isCancelled)
        handle.close()
    }

    @Test fun expiredEntriesAreDisposedBeforeCapacityCheckEvenIfTimerHasNotRun() {
        val profile = seed()
        val material = (1..16).map {
            credentials().also { secret ->
                handles.add((coordinator.begin(profile, secret) as SshAuthenticationPreparation.Ready).handle)
            }
        }
        now += 45_000L
        begin(profile)
        assertTrue(material.all { it.isClosed })
        assertTrue(handles.take(16).all { it.cancellation.isCancelled })
    }

    @Test fun accidentalActualMainThreadAdmissionDoesNotWaitForCoordinationOrSQLite() {
        val profile = seed()
        val threadAware = SshAuthenticationCoordinator(root, owner, profiles, trust,
            onMainThread = { Looper.myLooper() == Looper.getMainLooper() }) { now }
        val secret = credentials()
        val handle = (threadAware.begin(profile, secret) as SshAuthenticationPreparation.Ready).handle
        handles.add(handle)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val holder = pool.submit {
                SshAuthenticationRegistry.lock.lock()
                try { locked.countDown(); release.await(2, TimeUnit.SECONDS) }
                finally { SshAuthenticationRegistry.lock.unlock() }
            }
            assertTrue(locked.await(2, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val start = SystemClock.elapsedRealtime()
                assertNull(handle.admit(profile.peer, key))
                assertTrue("Main admission waited for store coordination", SystemClock.elapsedRealtime() - start < 1_000)
            }
            assertTrue(secret.isClosed)
            assertTrue(handle.cancellation.isCancelled)
            release.countDown()
            holder.get(3, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS))
        }
    }


    @Test fun productionConfigurationRejectsAnotherProcessNameBeforeStorageIo() {
        val advertised = if (Application.getProcessName() == packageName)
            "io.github.baitian6641.cloudy.desktop" else packageName
        val advertisedOwner = if (advertised == packageName) owner else WindowOwner.DESKTOP
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = advertised
            override fun getNoBackupFilesDir(): File = throw AssertionError("Wrong process reached private storage")
        }
        assertEquals(ConnectionProfilesCode.WRONG_OWNER,
            (ConnectionProfiles(context).load() as ConnectionProfilesResult.Failure).code)
        assertEquals(SshTrustStoreCode.WRONG_OWNER,
            (SshHostTrustStore(context).load() as SshTrustStoreResult.Failure).code)
        val profile = SshObservationProfile.create(UUID.randomUUID().toString(), ConnectionProfile.create(
            ConnectionProfileDraft.create(advertisedOwner, "Fixture", ConnectionProtocol.SSH, "fixture.invalid", 22)))
        val secret = credentials()
        assertEquals(SshAuthenticationCode.WRONG_OWNER,
            (SshAuthenticationCoordinator(context).begin(profile, secret) as SshAuthenticationPreparation.Failed).code)
        assertTrue(secret.isClosed)
    }


    private fun resource(profile: SshObservationProfile, account: String = "fixture-user"): FilesResourceRegistration {
        val catalog = (resources.load() as FilesResourceResult.Success).value
        val pin = checkNotNull(trustSuccess(trust.lookup(profile.peer)).current)
        return (resources.create(catalog.generation, FilesResourceDraft.create(owner, "Owned directory", profile,
            pin, "./private/../directory", account)) as FilesResourceResult.Success).value
    }
    private fun registered(resource: FilesResourceRegistration): SshAuthenticationHandle =
        (coordinator.beginRegistered(resource, SshPasswordCredentials.create(resource.accountName.toCharArray(),
            "fixture-password".toCharArray())) as SshAuthenticationPreparation.Ready).handle.also { handles.add(it) }
    private fun resourceSql(statement: String) {
        // Deliberate isolated storage alteration bypasses production invalidation, testing the
        // admission/promotion reread itself rather than just the already-tested signal path.
        SshAuthenticationRegistry.lock.lock()
        try {
            SQLiteDatabase.openDatabase(File(root, "files-resources-v1/resources.db").path, null,
                SQLiteDatabase.OPEN_READWRITE).use { it.execSQL(statement) }
        } finally { SshAuthenticationRegistry.lock.unlock() }
    }
    private fun simulatedProof(handle: SshAuthenticationHandle): SshAuthenticatedOwnership {
        val constructor = SshAuthenticatedOwnership::class.java.getDeclaredConstructor(SshPeerIdentity::class.java,
            SshHostKeyIdentity::class.java, SshObservationCancellation::class.java).apply { isAccessible = true }
        return constructor.newInstance(handle.profile.peer, handle.expectedKey, handle.cancellation).also { simulatedProofs.add(it) }
    }
    private fun simulatedRetired(proof: SshAuthenticatedOwnership) {
        check(proof in simulatedProofs)
        val field = SshAuthenticatedOwnership::class.java.getDeclaredField("retired").apply { isAccessible = true }
        (field.get(proof) as AtomicBoolean).set(true)
    }

    @Test fun sameHolderCapturesExactAccountAndAcceptedTrustForOrdinaryPreparation() {
        val profile = seed()
        val user = "  User-e\u0301  ".toCharArray()
        val holder = SshPasswordCredentials.create(user, "fixture-password".toCharArray())
        val expected = user.concatToString()
        user.fill('x')
        val handle = (coordinator.begin(profile, holder) as SshAuthenticationPreparation.Ready).handle.also { handles.add(it) }
        assertEquals(expected, handle.accountName)
        assertEquals(trustSuccess(trust.lookup(profile.peer)).current, handle.acceptedTrust)
        assertNull(handle.resourceBinding)
        assertSame(holder, handle.admit(profile.peer, key))
        holder.close()
        assertNull(holder.accountName())
        assertEquals(expected, handle.accountName)
        assertFalse(handle.toString().contains(expected))
    }

    @Test fun registeredWrongAccountAndClosedHolderAreDisposedBeforeAnyAdmission() {
        val row = resource(seed(), " Exact-account ")
        for (account in listOf("Exact-account", " exact-account ")) {
            val holder = SshPasswordCredentials.create(account.toCharArray(), "fixture-password".toCharArray())
            assertEquals(SshAuthenticationCode.ACCOUNT_MISMATCH,
                (coordinator.beginRegistered(row, holder) as SshAuthenticationPreparation.Failed).code)
            assertTrue(holder.isClosed)
            assertNull(holder.accountName())
        }
        val accepted = registered(row)
        assertEquals(row.accountName, accepted.accountName)
        assertEquals(row, accepted.resourceBinding)
        accepted.close()
        val holder = credentials().also { it.close() }
        assertEquals(SshAuthenticationCode.CREDENTIALS_UNAVAILABLE,
            (coordinator.beginRegistered(row, holder) as SshAuthenticationPreparation.Failed).code)
        assertTrue(SshAuthenticationRegistry.attempts.none { it.root == root.absoluteFile })
    }

    @Test fun registeredPreparationResolvesExactCurrentStateRevisionAndFullMetadata() {
        val row = resource(seed())
        val changedDraft = FilesResourceDraft.create(owner, row.label, row.profile, row.acceptedTrust,
            "/different-root", row.accountName)
        val forgedMetadata = FilesResourceRegistration.restore(row.generation, row.reference, row.revision, row.state, changedDraft)
        val holder = credentials()
        assertEquals(SshAuthenticationCode.RESOURCE_CHANGED,
            (coordinator.beginRegistered(forgedMetadata, holder) as SshAuthenticationPreparation.Failed).code)
        assertTrue(holder.isClosed)
        val staleGeneration = FilesResourceRegistration.restore(UUID.randomUUID().toString(), row.reference,
            row.revision, row.state, row.draft)
        val staleHolder = credentials()
        assertEquals(SshAuthenticationCode.RESOURCE_CHANGED,
            (coordinator.beginRegistered(staleGeneration, staleHolder) as SshAuthenticationPreparation.Failed).code)
        assertTrue(staleHolder.isClosed)
        val shared = (resources.share(row.version()) as FilesResourceResult.Success).value
        val stale = credentials()
        assertEquals(SshAuthenticationCode.RESOURCE_CHANGED,
            (coordinator.beginRegistered(row, stale) as SshAuthenticationPreparation.Failed).code)
        assertTrue(stale.isClosed)
        registered(shared).close()
        val revoked = (resources.revoke(shared.version()) as FilesResourceResult.Success).value
        val denied = credentials()
        assertEquals(SshAuthenticationCode.RESOURCE_CHANGED,
            (coordinator.beginRegistered(revoked, denied) as SshAuthenticationPreparation.Failed).code)
        assertTrue(denied.isClosed)
    }

    @Test fun resourceRereadRejectsUnsignalledRevisionChangeBeforePasswordGate() {
        val row = resource(seed())
        val handle = registered(row)
        resourceSql("UPDATE resources SET revision=revision+1")
        assertFalse(handle.cancellation.isCancelled)
        assertNull(handle.admit(row.profile.peer, key))
        assertTrue(handle.cancellation.isCancelled)
        assertFalse(handle in SshAuthenticationRegistry.attempts)
    }

    @Test fun simulatedProofPromotionRechecksResourceAfterPasswordAdmission() {
        val row = resource(seed())
        val handle = registered(row)
        checkNotNull(handle.admit(row.profile.peer, key)).close()
        val proof = simulatedProof(handle)
        resourceSql("UPDATE resources SET navigation_root='/other-root'")
        assertFalse(handle.cancellation.isCancelled)
        assertFalse(handle.establishSession(proof))
        assertTrue(handle.cancellation.isCancelled)
        assertFalse(handle in SshAuthenticationRegistry.attempts)
    }

    @Test fun resourceShareAndRevokeCancelOnlyTheirExactRegisteredOwners() {
        val profile = seed()
        val first = resource(profile)
        val second = resource(profile)
        val ordinary = begin(profile)
        val firstOwner = registered(first)
        val secondOwner = registered(second)
        val shared = (resources.share(first.version()) as FilesResourceResult.Success).value
        assertTrue(firstOwner.cancellation.isCancelled)
        assertFalse(secondOwner.cancellation.isCancelled)
        assertFalse(ordinary.cancellation.isCancelled)
        val sharedOwner = registered(shared)
        resources.revoke(shared.version()) as FilesResourceResult.Success
        assertTrue(sharedOwner.cancellation.isCancelled)
        assertFalse(secondOwner.cancellation.isCancelled)
        assertFalse(ordinary.cancellation.isCancelled)
        checkNotNull(secondOwner.admit(profile.peer, key)).close()
        checkNotNull(ordinary.admit(profile.peer, key)).close()
    }

    @Test fun simulatedEstablishedResourceOwnerRetainsCapacityUntilExactProofRetires() {
        val row = resource(seed())
        val handle = registered(row)
        checkNotNull(handle.admit(row.profile.peer, key)).close()
        val proof = simulatedProof(handle)
        assertTrue(handle.establishSession(proof))
        assertFalse(handle.matchesEstablished(proof)) // Missing coordination is not a controller gate.
        SshAuthenticationRegistry.lock.lock()
        try { assertTrue(handle.matchesEstablished(proof)) }
        finally { SshAuthenticationRegistry.lock.unlock() }
        resources.revoke(row.version()) as FilesResourceResult.Success
        assertTrue(handle.cancellation.isCancelled)
        handle.close()
        handle.releaseSession(proof)
        assertTrue(handle in SshAuthenticationRegistry.attempts)
        SshAuthenticationRegistry.lock.lock()
        try { assertFalse(handle.matchesEstablished(proof)) }
        finally { SshAuthenticationRegistry.lock.unlock() }
        simulatedRetired(proof)
        handle.releaseSession(proof)
        assertFalse(handle in SshAuthenticationRegistry.attempts)
    }

    @Test fun resourceIdentityInvalidationDoesNotCrossRootOwnerGenerationOrResource() {
        val row = resource(seed())
        val handle = registered(row)
        SshAuthenticationRegistry.lock.lock()
        try {
            SshAuthenticationRegistry.invalidateResource(File(root, "other"), owner, row.generation, row.reference.resourceId)
            SshAuthenticationRegistry.invalidateResource(root, WindowOwner.DESKTOP, row.generation, row.reference.resourceId)
            SshAuthenticationRegistry.invalidateResource(root, owner, UUID.randomUUID().toString(), row.reference.resourceId)
            SshAuthenticationRegistry.invalidateResource(root, owner, row.generation, UUID.randomUUID().toString())
        } finally { SshAuthenticationRegistry.lock.unlock() }
        assertFalse(handle.cancellation.isCancelled)
        checkNotNull(handle.admit(row.profile.peer, key)).close()
    }

}
