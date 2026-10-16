package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustChallenge
import io.github.baitian6641.cloudy.domain.SshTrustEditToken
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedOwnership
import io.github.baitian6641.cloudy.ssh.SshObservationCancellation
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real SQLite coordinator POLICY tests only. Reflection below SIMULATES an engine proof and
 * retirement flag in this isolated test root; it is never authenticated-device/transport evidence.
 * No production factory, network, fake successful product path or engine lifecycle hook is added.
 */
@RunWith(AndroidJUnit4::class)
class SshEstablishedOwnershipTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(app.noBackupFilesDir, "established-policy-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val owner = WindowOwner.CONSOLE
    private val ownerPackage = "io.github.baitian6641.cloudy.console"
    private val profiles = ConnectionProfiles(root, owner, ownerPackage, { false })
    private val trust = SshHostTrustStore(root, owner, ownerPackage, { false })
    private var now = 100L
    private val coordinator = SshAuthenticationCoordinator(root, owner, profiles, trust) { now }
    private val handles = mutableListOf<SshAuthenticationHandle>()
    private val simulated = mutableListOf<SshAuthenticatedOwnership>()
    private val schedulers = mutableListOf<PausedExpiry>()
    private val key = key(1)

    private fun key(value: Int) = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", "SHA256:" +
        Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { value.toByte() }))
    private fun <T> profile(result: ConnectionProfilesResult<T>): T = (result as ConnectionProfilesResult.Success).value
    private fun <T> pin(result: SshTrustStoreResult<T>): T = (result as SshTrustStoreResult.Success).value
    private fun draft(label: String = "Policy fixture") =
        ConnectionProfileDraft.create(owner, label, ConnectionProtocol.SSH, "ownership.invalid", 22)
    private fun seed(): SshObservationProfile {
        val catalog = profile(profiles.load())
        val entry = profile(profiles.create(catalog.generation, draft()))
        val result = SshObservationProfile.create(entry.version.generation, entry.profile)
        pin(trust.accept(SshTrustChallenge.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            pin(trust.evaluate(result.peer, key)))))
        return result
    }
    private fun begin(at: SshObservationProfile, using: SshAuthenticationCoordinator = coordinator): SshAuthenticationHandle {
        val secret = SshPasswordCredentials.create("fixture-user".toCharArray(), "fixture-password".toCharArray())
        return (using.begin(at, secret) as SshAuthenticationPreparation.Ready).handle.also { handles.add(it) }
    }
    private fun admit(handle: SshAuthenticationHandle) {
        // Simulate engine consumption/disposal without ever authenticating in this policy suite.
        checkNotNull(handle.admit(handle.profile.peer, key)).close()
    }
    private fun simulatedProof(handle: SshAuthenticationHandle, peer: SshPeerIdentity = handle.profile.peer,
        identity: SshHostKeyIdentity = key, cancellation: SshObservationCancellation = handle.cancellation): SshAuthenticatedOwnership {
        val constructor = SshAuthenticatedOwnership::class.java.getDeclaredConstructor(
            SshPeerIdentity::class.java, SshHostKeyIdentity::class.java, SshObservationCancellation::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(peer, identity, cancellation).also { simulated.add(it) }
    }
    private fun simulateRetirement(proof: SshAuthenticatedOwnership) {
        check(proof in simulated)
        val field = SshAuthenticatedOwnership::class.java.getDeclaredField("retired").apply { isAccessible = true }
        check(field.type == AtomicBoolean::class.java)
        (field.get(proof) as AtomicBoolean).set(true)
    }
    private fun registered(handle: SshAuthenticationHandle) = handle in SshAuthenticationRegistry.attempts

    @After fun cleanupOnlyThisPolicyFixture() {
        schedulers.forEach { it.proceed.countDown() }
        handles.forEach { it.close() }
        // Only proofs constructed by THIS test may receive simulated retirement.
        simulated.forEach { proof -> simulateRetirement(proof); handles.forEach { it.releaseSession(proof) } }
        schedulers.forEach { it.shutdownNow(); check(it.awaitTermination(2, TimeUnit.SECONDS)) }
        check(SshAuthenticationRegistry.attempts.none { it.root == root.absoluteFile })
        check(root.deleteRecursively())
    }

    @Test fun exactAdmittedProofPromotesAndNoAuthenticationExpiryCanEvictIt() {
        val at = seed()
        val handle = begin(at)
        admit(handle)
        val proof = simulatedProof(handle)
        assertTrue(handle.establishSession(proof))
        now = handle.expiresAtMillis + 1
        // begin's expiry pruning must keep an established owner, not close it as stale auth.
        begin(at).close()
        assertTrue(registered(handle))
        assertFalse(handle.cancellation.isCancelled)
        handle.releaseSession(proof)
        assertTrue("An unretired proof cannot free capacity", registered(handle))
        simulateRetirement(proof)
        handle.releaseSession(proof)
        assertFalse(registered(handle))
    }

    @Test fun promotionAtExactOriginalDeadlineAndCancelledPromotionFailClosed() {
        val at = seed()
        val expired = begin(at)
        admit(expired)
        val late = simulatedProof(expired)
        now = expired.expiresAtMillis
        assertFalse(expired.establishSession(late))
        assertTrue(expired.cancellation.isCancelled)
        assertFalse(registered(expired))
        val cancelled = begin(at)
        admit(cancelled)
        val cancelledProof = simulatedProof(cancelled)
        cancelled.cancellation.cancel()
        assertFalse(cancelled.establishSession(cancelledProof))
        assertFalse(registered(cancelled))
    }

    @Test fun actualScheduledExpiryAlreadyRunningCannotCancelPromotionThatWonOwnership() {
        val at = seed()
        val scheduler = PausedExpiry().also { schedulers.add(it) }
        val using = SshAuthenticationCoordinator(root, owner, profiles, trust, expiryScheduler = scheduler) { now }
        val handle = begin(at, using)
        assertTrue(scheduler.started.await(3, TimeUnit.SECONDS))
        admit(handle)
        val proof = simulatedProof(handle)
        assertTrue(handle.establishSession(proof))
        scheduler.proceed.countDown()
        assertTrue(scheduler.finished.await(3, TimeUnit.SECONDS))
        assertFalse(handle.cancellation.isCancelled)
        assertTrue(registered(handle))
    }

    @Test fun actualScheduledExpiryWinningOwnershipPreventsLaterPromotion() {
        val at = seed()
        val scheduler = PausedExpiry().also { schedulers.add(it) }
        val using = SshAuthenticationCoordinator(root, owner, profiles, trust, expiryScheduler = scheduler) { now }
        val handle = begin(at, using)
        assertTrue(scheduler.started.await(3, TimeUnit.SECONDS))
        admit(handle)
        val proof = simulatedProof(handle)
        now = handle.expiresAtMillis
        scheduler.proceed.countDown()
        assertTrue(scheduler.finished.await(3, TimeUnit.SECONDS))
        assertFalse(handle.establishSession(proof))
        assertTrue(handle.cancellation.isCancelled)
        assertFalse(registered(handle))
    }

    @Test fun currentProfileEditBeforePromotionDeniesAndAfterPromotionRetainsUntilRetirement() {
        val at = seed()
        val before = begin(at)
        val after = begin(at)
        admit(before); admit(after)
        val firstProof = simulatedProof(before)
        val activeProof = simulatedProof(after)
        assertTrue(after.establishSession(activeProof))
        profile(profiles.update(ConnectionProfileVersion.create(at.generation, at.profile.id, at.profile.revision), draft("Changed")))
        assertFalse(before.establishSession(firstProof))
        assertTrue(before.cancellation.isCancelled)
        assertTrue(after.cancellation.isCancelled)
        assertTrue(registered(after))
        now = after.expiresAtMillis + 1
        val current = profile(profiles.load())
        begin(SshObservationProfile.create(current.generation, current.profiles.single())).close()
        assertTrue(registered(after))
        simulateRetirement(activeProof)
        after.releaseSession(activeProof)
        assertFalse(registered(after))
    }

    @Test fun promotionRechecksTrustGenerationAfterCredentialAdmission() {
        val at = seed()
        val handle = begin(at)
        admit(handle)
        val proof = simulatedProof(handle)
        val generation = UUID.randomUUID().toString()
        // Isolated test-only internally consistent lifetime replacement bypasses mutation
        // invalidation, proving the final promotion re-read itself refuses stale authority.
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
        assertFalse(handle.establishSession(proof))
        assertTrue(handle.cancellation.isCancelled)
        assertFalse(registered(handle))
    }

    @Test fun trustRevocationAfterPromotionCancelsButDoesNotDiscardUnretiredOwner() {
        val at = seed()
        val handle = begin(at)
        admit(handle)
        val proof = simulatedProof(handle)
        assertTrue(handle.establishSession(proof))
        val snapshot = pin(trust.lookup(at.peer)).current!!
        pin(trust.revoke(SshTrustEditToken.create(owner, snapshot.generation, at.peer, snapshot.revision)))
        assertTrue(handle.cancellation.isCancelled)
        assertTrue(registered(handle))
        handle.close()
        assertTrue(registered(handle))
        simulateRetirement(proof)
        handle.releaseSession(proof)
        assertFalse(registered(handle))
    }

    @Test fun closedUnretiredSessionsStillConsumeAllSixteenSlots() {
        val at = seed()
        val active = (1..16).map {
            val handle = begin(at)
            admit(handle)
            val proof = simulatedProof(handle)
            check(handle.establishSession(proof))
            handle.close()
            Pair(handle, proof)
        }
        now += 45_001
        val secret = SshPasswordCredentials.create("fixture".toCharArray(), "fixture".toCharArray())
        assertEquals(SshAuthenticationCode.CAPACITY,
            (coordinator.begin(at, secret) as SshAuthenticationPreparation.Failed).code)
        assertTrue(secret.isClosed)
        simulateRetirement(active.first().second)
        active.first().first.releaseSession(active.first().second)
        begin(at).close()
        assertEquals(15, active.count { registered(it.first) })
    }

    @Test fun wrongOrDuplicateProofCannotPromoteOrReleaseAnotherOwner() {
        val at = seed()
        val notAdmitted = begin(at)
        assertFalse(notAdmitted.establishSession(simulatedProof(notAdmitted)))
        val wrongSignal = begin(at)
        admit(wrongSignal)
        assertFalse(wrongSignal.establishSession(simulatedProof(wrongSignal, cancellation = SshObservationCancellation())))
        val wrongKey = begin(at)
        admit(wrongKey)
        assertFalse(wrongKey.establishSession(simulatedProof(wrongKey, identity = key(2))))
        val wrongPeer = begin(at)
        admit(wrongPeer)
        assertFalse(wrongPeer.establishSession(simulatedProof(wrongPeer, peer = SshPeerIdentity.create("other.invalid", 22))))
        val alreadyRetired = begin(at)
        admit(alreadyRetired)
        val retiredProof = simulatedProof(alreadyRetired)
        simulateRetirement(retiredProof)
        assertFalse(alreadyRetired.establishSession(retiredProof))
        val owner = begin(at)
        admit(owner)
        val proof = simulatedProof(owner)
        assertTrue(owner.establishSession(proof))
        val impostor = simulatedProof(owner)
        simulateRetirement(impostor)
        owner.releaseSession(impostor)
        assertTrue(registered(owner))
        assertFalse(owner.establishSession(proof))
        assertTrue("Duplicate establishment cancels but cannot free actual ownership", registered(owner))
        assertTrue(owner.cancellation.isCancelled)
        simulateRetirement(proof)
        owner.releaseSession(proof)
        owner.releaseSession(proof)
        assertFalse(registered(owner))
    }

    /** A real scheduler executes the expiry callback after the tested promotion/expiry winner.
     * The latch is test-only; cancel(false) cannot stop a callback that has already started. */
    private class PausedExpiry : ScheduledThreadPoolExecutor(1, { action ->
        Thread(action, "simulated-proof-expiry-test").apply { isDaemon = true }
    }) {
        val started = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val finished = CountDownLatch(1)
        override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
            super.schedule({
                started.countDown()
                try { check(proceed.await(10, TimeUnit.SECONDS)); command.run() }
                finally { finished.countDown() }
            }, 0, TimeUnit.MILLISECONDS)
    }
}
