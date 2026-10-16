package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SshHostKeyObserverTest {
    private val peer = SshPeerIdentity.create("logical-peer.invalid", 22)
    private val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Test fun invalidTimeoutAndStickyPreCancellationReturnBeforeNetworking() {
        val observer = SshHostKeyObserver()
        val cancellation = SshObservationCancellation()
        for (timeout in listOf(Int.MIN_VALUE, 0, 10_001, Int.MAX_VALUE)) {
            val result = observer.observe(peer, address, cancellation, timeout) as SshObservationResult.Failure
            assertEquals(SshObservationCode.INVALID_TIMEOUT, result.code)
        }
        cancellation.cancel(); cancellation.cancel()
        assertTrue(cancellation.isCancelled)
        repeat(2) {
            val result = observer.observe(peer, address, cancellation) as SshObservationResult.Failure
            assertEquals(SshObservationCode.CANCELLED, result.code)
            assertFalse(result.toString().contains(peer.host))
        }
    }

    @Test fun cancellationHasOneBoundedOwnerAndWakesAlreadyCancelledRegistration() {
        val cancellation = SshObservationCancellation()
        val calls = AtomicInteger()
        assertTrue(cancellation.attach { calls.incrementAndGet() })
        assertFalse(cancellation.attach { error("Second owner must not attach") })
        cancellation.cancel()
        assertEquals(1, calls.get())
        cancellation.detach()
        assertTrue(cancellation.attach { calls.incrementAndGet() })
        assertEquals(2, calls.get())
        cancellation.detach()
    }

    @Test fun occupiedCancellationOwnerReturnsBusyWithoutOpeningTransport() {
        val token = SshObservationCancellation()
        assertTrue(token.attach {})
        val observer = SshHostKeyObserver()
        assertEquals(SshObservationCode.BUSY,
            (observer.observe(peer, address, token) as SshObservationResult.Failure).code)
        token.detach(); token.cancel()
        assertEquals(SshObservationCode.CANCELLED,
            (observer.observe(peer, address, token) as SshObservationResult.Failure).code)
    }

    @Test fun interruptedOwningWorkerCancelsAndPreservesInterruptFlag() {
        val cancellation = SshObservationCancellation()
        try {
            Thread.currentThread().interrupt()
            assertEquals(SshObservationCode.CANCELLED,
                (SshHostKeyObserver().observe(peer, address, cancellation) as SshObservationResult.Failure).code)
            assertTrue(cancellation.isCancelled)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }

    @Test fun exactNarrowPolicyContainsNoLegacyFallbackAndOtherLoggersStayNop() {
        val retirement = SshReaderRetirement()
        val policy = SshObservationPolicy.create(retirement)
        assertEquals(listOf(SshObservationPolicy.SIGNATURE), policy.keyAlgorithms.map { it.name })
        assertEquals(listOf(SshObservationPolicy.CIPHER), policy.cipherFactories.map { it.name })
        assertEquals(listOf(SshObservationPolicy.MAC), policy.getMACFactories().map { it.name })
        assertEquals(listOf("none"), policy.compressionFactories.map { it.name })
        assertTrue(policy.keyExchangeFactories.any { it.name == SshObservationPolicy.KEX })
        assertTrue(policy.keyExchangeFactories.all { it.name in setOf(SshObservationPolicy.KEX, "ext-info-c") })
        assertSame(NOPLogger.NOP_LOGGER, retirement.getLogger("unrelated.server.logger"))
    }

    @Test fun missingOrWrongThreadTerminalEventNeverProvesReaderRetirement() {
        val missing = SshReaderRetirement()
        missing.expected.set(true)
        assertFalse(missing.awaitAndJoin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1)))
        val wrong = SshReaderRetirement()
        wrong.expected.set(true)
        wrong.getLogger("net.schmizz.sshj.transport.Reader").debug("Stopping")
        assertFalse(wrong.awaitAndJoin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1)))
    }
}
