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
 * Finite password-only authentication check on an owning worker. No channels or reusable session.
 * A fresh exact-key KEX precedes the caller's short admission callback. DNS, foreground/profile/
 * trust coordination and permission belong to that caller. Success also requires owned retirement.
 * Password/username arrays are wiped, but JVM Strings and upstream packet copies cannot be erased
 * reliably; this adapter makes no complete-JVM-zeroization claim.
 */
class SshAuthenticationCheck {
    private val busy = AtomicBoolean(false)
    private val quarantined = AtomicBoolean(false)

    fun authenticate(peer: SshPeerIdentity, address: InetAddress, expectedKey: SshHostKeyIdentity,
                     cancellation: SshObservationCancellation,
                     admit: (SshPeerIdentity, SshHostKeyIdentity) -> SshPasswordCredentials?,
                     timeoutMillis: Int = 10_000): SshAuthenticationResult {
        if (timeoutMillis !in 1..10_000) return failure(SshAuthenticationCode.INVALID_TIMEOUT)
        if (quarantined.get()) return failure(SshAuthenticationCode.QUARANTINED)
        if (!busy.compareAndSet(false, true)) return failure(SshAuthenticationCode.BUSY)
        try {
            if (quarantined.get()) return failure(SshAuthenticationCode.QUARANTINED)
            if (Thread.currentThread().isInterrupted) cancellation.cancel()
            if (cancellation.isCancelled) return failure(SshAuthenticationCode.CANCELLED)
            return perform(peer, address, expectedKey, cancellation, admit, timeoutMillis)
        } finally { busy.set(false) }
    }

    private fun perform(peer: SshPeerIdentity, address: InetAddress, expectedKey: SshHostKeyIdentity,
                        cancellation: SshObservationCancellation,
                        admit: (SshPeerIdentity, SshHostKeyIdentity) -> SshPasswordCredentials?, timeoutMillis: Int): SshAuthenticationResult {
        val attempt = Attempt(cancellation, timeoutMillis)
        if (!cancellation.attach { attempt.wake.countDown() }) return failure(SshAuthenticationCode.BUSY)
        val retirement = SshReaderRetirement()
        val observed = AtomicReference<SshHostKeyIdentity?>(null)
        val rejected = AtomicReference<SshAuthenticationCode?>(null)
        var client: SSHClient? = null
        var credentials: SshPasswordCredentials? = null
        var material: SshPasswordCredentials.Material? = null
        var method: SshStrictPasswordMethod? = null
        var authenticated = false
        var keyCallbacks = 0
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
                if (!accepted) rejected.compareAndSet(null, SshAuthenticationCode.ALGORITHM_REJECTED)
                accepted && attempt.active()
            }
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = listOf(SshObservationPolicy.SIGNATURE)
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    retirement.record(Thread.currentThread())
                    keyCallbacks++
                    if (keyCallbacks != 1 || hostname != numeric || port != peer.port || !attempt.active()) {
                        rejected.compareAndSet(null, SshAuthenticationCode.KEY_REJECTED)
                        return false
                    }
                    try {
                        val identity = SshObservedKeyCodec.encode(key)
                        if (identity.keyFormat != SshObservationPolicy.SIGNATURE || identity != expectedKey ||
                            !observed.compareAndSet(null, identity)) {
                            rejected.compareAndSet(null, SshAuthenticationCode.KEY_REJECTED)
                            return false
                        }
                    } catch (_: Exception) {
                        rejected.compareAndSet(null, SshAuthenticationCode.KEY_REJECTED)
                        return false
                    }
                    return attempt.active()
                }
            })
            if (attempt.active()) ssh.connect(numeric, peer.port)
            val actualKey = observed.get()
            if (actualKey != null && attempt.active() && rejected.get() == null) {
                // No cancellation lock is held while entering platform coordination: its lock
                // order is coordination -> cancellation -> credential ownership, never reversed.
                credentials = try { admit(peer, actualKey) } catch (_: Exception) { null }
                val admitted = credentials
                if (admitted == null) rejected.compareAndSet(null, SshAuthenticationCode.ADMISSION_DENIED)
                else {
                    material = admitted.take()
                    val owned = material
                    if (owned == null) rejected.compareAndSet(null, SshAuthenticationCode.CREDENTIALS_UNAVAILABLE)
                    else if (attempt.active() && rejected.get() == null) {
                        val releaseActive = { attempt.active() && rejected.get() == null }
                        val finder = SshOneReleasePasswordFinder(owned, cancellation, releaseActive)
                        val strict = SshStrictPasswordMethod(finder, releaseActive)
                        method = strict
                        // SSHJ requires an immutable username String. No value is normalized or
                        // logged; password bytes are released only once and wiped after encoding.
                        ssh.auth(String(owned.username), strict)
                        authenticated = ssh.isAuthenticated
                    }
                }
            }
            completed = true
        } catch (_: IOException) {
            // Authentication denial, transport failure and key rejection are classified from
            // owned typed state, never from server text or an upstream exception transcript.
            completed = true
        } catch (_: Exception) {
            // Unexpected setup/control flow may have started an unobserved reader: fail closed.
            uncheckedFailure = true
            completed = true
        } finally {
            material?.close()
            credentials?.close()
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
                }, "cloudy-ssh-authentication-close").apply { isDaemon = true }
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
        if (!retired) return failure(SshAuthenticationCode.CLEANUP_UNCERTAIN)
        if (cancellation.isCancelled) return failure(SshAuthenticationCode.CANCELLED)
        if (attempt.expired()) return failure(SshAuthenticationCode.TIMED_OUT)
        if (method?.passwordChangeRefused?.get() == true) return failure(SshAuthenticationCode.PASSWORD_CHANGE_REFUSED)
        rejected.get()?.let { return failure(it) }
        if (observed.get() == null) return failure(if (retirement.expected.get()) SshAuthenticationCode.KEY_REJECTED else SshAuthenticationCode.CONNECT_FAILED)
        // A verified key does not distinguish server denial from a later transport failure.
        return if (authenticated) SshAuthenticationResult.Success else failure(SshAuthenticationCode.AUTHENTICATION_FAILED)
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
        }, "cloudy-ssh-authentication-deadline").apply { isDaemon = true }
        val socketFactory = object : SocketFactory() {
            override fun createSocket(): Socket {
                if (!active()) throw IOException("AUTHENTICATION_STOPPED")
                val created = Socket()
                if (!socket.compareAndSet(null, created)) { created.close(); throw IOException("SECOND_SOCKET") }
                // A concurrent cancel/deadline before registration must not leak a new socket.
                if (!active()) { closeSocket(); throw IOException("AUTHENTICATION_STOPPED") }
                return created
            }
            override fun createSocket(host: String, port: Int): Socket = refuse()
            override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = refuse()
            override fun createSocket(host: InetAddress, port: Int): Socket = refuse()
            override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket = refuse()
            private fun refuse(): Nothing = throw IOException("UNEXPECTED_SOCKET_PATH")
        }
    }

    private fun failure(code: SshAuthenticationCode) = SshAuthenticationResult.Failure(code)
}
