package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Controlled real TCP failure boundaries; OpenSSH success needs an explicitly owned fixture. */
class SshObservationHostTest {
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Test fun realOpenSshKeyMatchesIndependentFingerprintAndObserverCanBeReused() {
        val portText = System.getenv("CLOUDY_SSH_TEST_PORT")
        val fingerprint = System.getenv("CLOUDY_SSH_TEST_FINGERPRINT")
        assumeTrue("Owned OpenSSH fixture is opt-in", portText != null && fingerprint != null)
        val port = checkNotNull(portText).toInt().also { require(it in 1..65535) }
        val expected = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", checkNotNull(fingerprint))
        val peer = SshPeerIdentity.create("127.0.0.1", port)
        val observer = SshHostKeyObserver()
        repeat(2) {
            val result = observer.observe(peer, loopback, SshObservationCancellation())
            assertTrue("Observed key requires real retirement: $result", result is SshObservationResult.SignedKeyObserved)
            result as SshObservationResult.SignedKeyObserved
            assertTrue("Independent SSH wire fingerprint mismatch", result.key == expected && result.peer == peer)
        }
    }

    @Test fun withheldGreetingDeadlineClosesTheActualSocketWithoutQuarantine() = stalled(false, false)
    @Test fun withheldKexDeadlineJoinsTheActualReaderWithoutQuarantine() = stalled(true, false)
    @Test fun cancelledGreetingClosesTheActualSocketWithoutQuarantine() = stalled(false, true)
    @Test fun cancelledKexJoinsTheActualReaderWithoutQuarantine() = stalled(true, true)

    private fun stalled(sendGreeting: Boolean, cancel: Boolean) {
        ServerSocket(0, 1, loopback).use { listener ->
            listener.soTimeout = 4_000
            val accepted = CountDownLatch(1)
            val kexSent = CountDownLatch(1)
            val release = CountDownLatch(1)
            val peerSocket = AtomicReference<Socket?>()
            val server = Thread({
                listener.accept().use { socket ->
                    peerSocket.set(socket)
                    if (sendGreeting) {
                        socket.getOutputStream().write("SSH-2.0-CloudyBoundaryTest\r\n".toByteArray(Charsets.US_ASCII))
                        socket.getOutputStream().flush()
                    }
                    accepted.countDown()
                    if (sendGreeting) {
                        socket.soTimeout = 3_000
                        val input = DataInputStream(socket.getInputStream())
                        var ended = false
                        repeat(256) {
                            if (!ended) ended = input.readUnsignedByte() == 10
                        }
                        check(ended) { "Bounded client identification" }
                        val length = input.readInt()
                        check(length in 2..131_072)
                        input.readUnsignedByte() // Padding length; no implementation parses payload here.
                        check(input.readUnsignedByte() == 20) { "Expected actual SSH_MSG_KEXINIT" }
                        // SSHClient sends KEXINIT after TransportImpl.init starts the Reader.
                        kexSent.countDown()
                    }
                    release.await(5, TimeUnit.SECONDS)
                }
            }, "cloudy-ssh-test-peer").apply { isDaemon = true; start() }
            val cancellation = SshObservationCancellation()
            val outcome = AtomicReference<SshObservationResult?>()
            val observer = SshHostKeyObserver()
            val worker = Thread({
                outcome.set(observer.observe(SshPeerIdentity.create("127.0.0.1", listener.localPort),
                    loopback, cancellation, timeoutMillis = 2_000))
            }, "cloudy-ssh-test-owner").apply { isDaemon = true }
            try {
                worker.start()
                assertTrue("Real TCP accept", accepted.await(3, TimeUnit.SECONDS))
                if (sendGreeting) assertTrue("Actual client KEXINIT after Reader start", kexSent.await(3, TimeUnit.SECONDS))
                if (cancel) {
                    cancellation.cancel()
                }
                worker.join(5_000)
                assertFalse("Owned observation deadline", worker.isAlive)
                val result = outcome.get()
                assertTrue("Typed failure expected: $result", result is SshObservationResult.Failure)
                assertEquals(if (cancel) SshObservationCode.CANCELLED else SshObservationCode.TIMED_OUT,
                    (result as SshObservationResult.Failure).code)
                // A completed cancellation/deadline must not quarantine the reusable adapter.
                val stopped = observer.observe(SshPeerIdentity.create("127.0.0.1", listener.localPort),
                    loopback, SshObservationCancellation().apply { cancel() })
                assertEquals(SshObservationCode.CANCELLED, (stopped as SshObservationResult.Failure).code)
            } finally {
                cancellation.cancel(); peerSocket.get()?.close(); release.countDown()
                worker.join(3_000); server.join(3_000)
                assertFalse("Test worker must retire", worker.isAlive)
                assertFalse("Test peer must retire", server.isAlive)
            }
        }
    }
}
