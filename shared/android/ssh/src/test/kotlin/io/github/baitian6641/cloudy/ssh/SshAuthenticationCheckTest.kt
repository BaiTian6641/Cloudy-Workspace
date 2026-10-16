package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.net.InetAddress
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class SshAuthenticationCheckTest {
    private val peer = SshPeerIdentity.create("unused.invalid", 22)
    private val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", "SHA256:" +
        Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32)))

    @Test fun invalidTimeoutAndPreCancellationNeverEnterCredentialAdmission() {
        val engine = SshAuthenticationCheck()
        val calls = AtomicInteger()
        val token = SshObservationCancellation()
        val admit = { _: SshPeerIdentity, _: SshHostKeyIdentity -> calls.incrementAndGet(); null }
        for (timeout in listOf(Int.MIN_VALUE, 0, 10_001, Int.MAX_VALUE)) {
            assertEquals(SshAuthenticationCode.INVALID_TIMEOUT,
                (engine.authenticate(peer, address, key, token, admit, timeout) as SshAuthenticationResult.Failure).code)
        }
        token.cancel()
        assertEquals(SshAuthenticationCode.CANCELLED,
            (engine.authenticate(peer, address, key, token, admit) as SshAuthenticationResult.Failure).code)
        assertEquals(0, calls.get())
    }

    @Test fun occupiedCancellationAndInterruptedWorkerStopBeforeNetworking() {
        val token = SshObservationCancellation()
        assertTrue(token.attach {})
        assertEquals(SshAuthenticationCode.BUSY, (SshAuthenticationCheck().authenticate(peer, address, key, token,
            { _, _ -> error("Admission must not run") }) as SshAuthenticationResult.Failure).code)
        token.detach()
        try {
            Thread.currentThread().interrupt()
            assertEquals(SshAuthenticationCode.CANCELLED, (SshAuthenticationCheck().authenticate(peer, address, key,
                SshObservationCancellation(), { _, _ -> error("Admission must not run") }) as SshAuthenticationResult.Failure).code)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }

    @Test fun cancellationWinnerPreventsTransferAndActiveTransferCompletesBeforeCancelReturns() {
        val already = SshObservationCancellation()
        already.cancel()
        assertNull(already.tryIfActive { error("Cancelled transfer must not execute") })
        val token = SshObservationCancellation()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelStarted = CountDownLatch(1)
        val cancelReturned = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val transfer = pool.submit<Int?> {
                // Test-only gate establishes ordering; production blocks must be nonblocking.
                token.tryIfActive { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); 7 }
            }
            check(entered.await(5, TimeUnit.SECONDS))
            val cancel = pool.submit { cancelStarted.countDown(); token.cancel(); cancelReturned.set(true) }
            check(cancelStarted.await(5, TimeUnit.SECONDS))
            assertFalse(cancelReturned.get())
            release.countDown()
            assertEquals(7, transfer.get(5, TimeUnit.SECONDS))
            cancel.get(5, TimeUnit.SECONDS)
            assertTrue(token.isCancelled)
            assertNull(token.tryIfActive { 8 })
        } finally { release.countDown(); pool.shutdownNow() }
    }
}
