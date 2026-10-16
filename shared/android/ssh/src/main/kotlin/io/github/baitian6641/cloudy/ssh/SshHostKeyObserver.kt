package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.PublicKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

/**
 * Finite direct observation, called on an owning worker (never the Android main thread).
 * DNS and Android network permission belong to the caller. Address is selected independently of
 * the logical peer; only its numeric hostAddress is passed to SSHJ, avoiding Java/reverse DNS.
 * No auth/session/store APIs are invoked. Host verification always rejects: a signed key is
 * observed after KEX signature verification, before NEWKEYS, not a completed handshake.
 */
class SshHostKeyObserver {
    private val busy = AtomicBoolean(false)
    private val quarantined = AtomicBoolean(false)

    fun observe(peer: SshPeerIdentity, address: InetAddress, cancellation: SshObservationCancellation,
                timeoutMillis: Int = 10_000): SshObservationResult {
        if (timeoutMillis !in 1..10_000) return failure(SshObservationCode.INVALID_TIMEOUT)
        if (quarantined.get()) return failure(SshObservationCode.QUARANTINED)
        if (!busy.compareAndSet(false, true)) return failure(SshObservationCode.BUSY)
        try {
            if (quarantined.get()) return failure(SshObservationCode.QUARANTINED)
            if (Thread.currentThread().isInterrupted) cancellation.cancel()
            if (cancellation.isCancelled) return failure(SshObservationCode.CANCELLED)
            return perform(peer, address, cancellation, timeoutMillis)
        } finally { busy.set(false) }
    }

    private fun perform(peer: SshPeerIdentity, address: InetAddress, cancellation: SshObservationCancellation,
                        timeoutMillis: Int): SshObservationResult {
        val attempt = Attempt(cancellation, timeoutMillis)
        if (!cancellation.attach { attempt.wake.countDown() }) return failure(SshObservationCode.BUSY)
        val retirement = SshReaderRetirement()
        val observed = AtomicReference<SshHostKeyIdentity?>(null)
        val rejected = AtomicReference<SshObservationCode?>(null)
        var client: SSHClient? = null
        var uncheckedFailure = false
        var interrupted = false
        var completed = false
        var retired = false
        var watcherStarted = false
        try {
            attempt.watcher.start(); watcherStarted = true
            val numeric = checkNotNull(address.hostAddress)
            val ssh = object : SSHClient(SshObservationPolicy.create(retirement)) {
                override fun doKex() {
                    // TransportImpl.init has returned from reader.start at this public hook.
                    retirement.expected.set(true)
                    super.doKex()
                }
            }
            client = ssh
            ssh.connectTimeout = timeoutMillis
            ssh.timeout = timeoutMillis
            ssh.socketFactory = attempt.socketFactory
            ssh.addAlgorithmsVerifier { algorithms ->
                val accepted = algorithms.keyExchangeAlgorithm == SshObservationPolicy.KEX &&
                    algorithms.signatureAlgorithm == SshObservationPolicy.SIGNATURE &&
                    algorithms.client2ServerCipherAlgorithm == SshObservationPolicy.CIPHER &&
                    algorithms.server2ClientCipherAlgorithm == SshObservationPolicy.CIPHER &&
                    algorithms.client2ServerMACAlgorithm == SshObservationPolicy.MAC &&
                    algorithms.server2ClientMACAlgorithm == SshObservationPolicy.MAC &&
                    algorithms.client2ServerCompressionAlgorithm == "none" && algorithms.server2ClientCompressionAlgorithm == "none"
                if (!accepted) rejected.compareAndSet(null, SshObservationCode.ALGORITHM_REJECTED)
                accepted && attempt.active()
            }
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = listOf(SshObservationPolicy.SIGNATURE)
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    retirement.record(Thread.currentThread())
                    if (hostname != numeric || port != peer.port || !attempt.active()) {
                        rejected.compareAndSet(null, SshObservationCode.KEY_REJECTED)
                        return false
                    }
                    try {
                        val identity = SshObservedKeyCodec.encode(key)
                        if (identity.keyFormat != SshObservationPolicy.SIGNATURE || !observed.compareAndSet(null, identity)) {
                            rejected.compareAndSet(null, SshObservationCode.KEY_REJECTED)
                        }
                    } catch (_: Exception) { rejected.compareAndSet(null, SshObservationCode.KEY_REJECTED) }
                    return false
                }
            })
            if (attempt.active()) ssh.connect(numeric, peer.port)
            completed = true
        } catch (_: IOException) {
            // Expected host-verifier rejection also arrives here. No exception text crosses API.
            completed = true
        } catch (_: Exception) {
            // Unexpected setup/control flow may have started an unobserved reader: fail closed.
            uncheckedFailure = true
            completed = true
        } finally {
            if (Thread.interrupted()) { interrupted = true; cancellation.cancel() }
            val cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            attempt.closeSocket()
            // SSHClient.close may wait for transport locks. Keep that shutdown off the owning
            // worker and include its actual Thread retirement in the same finite cleanup budget.
            val closeCompleted = AtomicBoolean(client == null)
            val closeWorker = client?.let { closing ->
                Thread({
                    try { closing.close(); closeCompleted.set(true) }
                    catch (_: Exception) { attempt.closeFailed.set(true) }
                }, "cloudy-ssh-observation-close").apply { isDaemon = true }
            }
            try { closeWorker?.start() } catch (_: Exception) { attempt.closeFailed.set(true) }
            attempt.finished.set(true); attempt.wake.countDown()
            try {
                while (true) {
                    try {
                        retired = retirement.awaitAndJoin(cleanupDeadline)
                        if (watcherStarted) {
                            val remaining = cleanupDeadline - System.nanoTime()
                            if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(attempt.watcher, remaining)
                            retired = retired && !attempt.watcher.isAlive
                        }
                        if (closeWorker != null) {
                            val remaining = cleanupDeadline - System.nanoTime()
                            if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(closeWorker, remaining)
                            retired = retired && !closeWorker.isAlive
                        }
                        break
                    } catch (_: InterruptedException) { interrupted = true; cancellation.cancel(); attempt.closeSocket() }
                }
            } finally {
                cancellation.detach()
                retired = retired && closeCompleted.get() && attempt.socketClosed() && !attempt.closeFailed.get() && !uncheckedFailure && completed
                if (!retired) quarantined.set(true)
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
        if (!retired) return failure(SshObservationCode.CLEANUP_UNCERTAIN)
        if (cancellation.isCancelled) return failure(SshObservationCode.CANCELLED)
        if (attempt.expired()) return failure(SshObservationCode.TIMED_OUT)
        rejected.get()?.let { return failure(it) }
        val key = observed.get() ?: return failure(if (retirement.expected.get()) SshObservationCode.NO_SIGNED_KEY else SshObservationCode.CONNECT_FAILED)
        return SshObservationResult.SignedKeyObserved(peer, key)
    }

    private class Attempt(val cancellation: SshObservationCancellation, timeoutMillis: Int) {
        private val started = System.nanoTime()
        private val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())
        val wake = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val closeFailed = AtomicBoolean(false)
        private val socket = AtomicReference<Socket?>(null)
        fun expired(): Boolean = System.nanoTime() - started >= timeoutNanos
        fun active(): Boolean = !cancellation.isCancelled && !expired() && !finished.get()
        fun closeSocket() {
            try { socket.get()?.close() } catch (_: Exception) { closeFailed.set(true) }
        }
        fun socketClosed(): Boolean = socket.get()?.isClosed != false
        val watcher = Thread({
            try {
                val remaining = timeoutNanos - (System.nanoTime() - started)
                if (remaining > 0) wake.await(remaining, TimeUnit.NANOSECONDS)
            } catch (_: InterruptedException) { cancellation.cancel() }
            if (!finished.get()) closeSocket()
        }, "cloudy-ssh-observation-deadline").apply { isDaemon = true }
        val socketFactory = object : SocketFactory() {
            override fun createSocket(): Socket {
                if (!active()) throw IOException("OBSERVATION_STOPPED")
                val created = Socket()
                if (!socket.compareAndSet(null, created)) { created.close(); throw IOException("SECOND_SOCKET") }
                // A concurrent cancel/deadline before registration must not leak a new socket.
                if (!active()) { closeSocket(); throw IOException("OBSERVATION_STOPPED") }
                return created
            }
            override fun createSocket(host: String, port: Int): Socket = refuse()
            override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = refuse()
            override fun createSocket(host: InetAddress, port: Int): Socket = refuse()
            override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket = refuse()
            private fun refuse(): Nothing = throw IOException("UNEXPECTED_SOCKET_PATH")
        }
    }

    private fun failure(code: SshObservationCode) = SshObservationResult.Failure(code)
}
