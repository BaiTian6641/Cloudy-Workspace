package dev.cloudy.qualification.baseline

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security
import java.util.Base64
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.HostKeyVerifier

/** One ordinary PTY connection. Runtime owns admission, durable history and the 15-minute watchdog. */
internal class TerminalTransport {
    private val started = AtomicBoolean(false)
    private val stopReason = AtomicReference<String?>(null)
    private val activeSocket = AtomicReference<Socket?>(null)
    private val mailbox = TerminalMailbox()

    fun offerInput(identity: TerminalIdentity, bytes: ByteArray): TerminalAdmission = mailbox.offerInput(identity, bytes)
    fun offerResize(identity: TerminalIdentity, size: TerminalSize): TerminalAdmission = mailbox.offerResize(identity, size)

    /** Sticky before connect as well as during idle reads or blocked writes. Never waits for a peer. */
    fun cancel(reason: String) {
        stopReason.compareAndSet(null, reason.takeIf { it.matches(Regex("[a-z][a-z0-9-]{0,79}")) } ?: "cancelled")
        mailbox.close()
        closeSocket()
    }

    fun run(
        config: TerminalConfigSpec,
        keyFile: File,
        identity: TerminalIdentity,
        trustedFingerprint: String?,
        initialSize: TerminalSize,
        listener: TerminalTransportListener,
    ): TerminalExit {
        check(started.compareAndSet(false, true)) { "Terminal transport instances are single-use" }
        val originalProviders = Security.getProviders().map { it.name }
        val observed = AtomicReference<String?>(null)
        val trustFailure = AtomicReference<String?>(null)
        val verifications = AtomicLong(0)
        val algorithms = AtomicReference<Map<String, String>>(emptyMap())
        val algorithmRejected = AtomicBoolean(false)
        val inputBytes = AtomicLong(0)
        val stdoutBytes = AtomicLong(0)
        val stderrBytes = AtomicLong(0)
        val lastSize = AtomicReference(initialSize)
        val helpers = TerminalHelpers(::closeSocket)
        var authenticated = false
        var ptyRequested = false
        var client: SSHClient? = null
        fun checkActive() {
            stopReason.get()?.let { throw SshProbeFailure(it) }
            if (Thread.currentThread().isInterrupted) throw SshProbeFailure("thread-interrupted")
            trustFailure.get()?.let { throw SshProbeFailure(it) }
            if (algorithmRejected.get()) throw SshProbeFailure("algorithm-policy-rejected")
            helpers.failure.get()?.let { throw it }
            if (Security.getProviders().map { it.name } != originalProviders) throw SshProbeFailure("provider-order-changed")
        }
        fun snapshot(): Map<String, String> {
            val result = algorithms.get() + mapOf(
                "hostVerificationCount" to verifications.get().toString(),
                "inputBytes" to inputBytes.get().toString(),
                "stdoutBytes" to stdoutBytes.get().toString(),
                "stderrBytes" to stderrBytes.get().toString(),
            )
            if (!ptyRequested) return result
            val size = lastSize.get()
            return result + mapOf(
                "ptyInitialCols" to initialSize.columns.toString(), "ptyInitialRows" to initialSize.rows.toString(),
                "ptyLastCols" to size.columns.toString(), "ptyLastRows" to size.rows.toString(),
            )
        }
        fun observe(status: String) {
            checkActive()
            // Only this owner publishes phases. No writer I/O is released until the open callback returns.
            listener.onObservation(TerminalObservation(status, observed.get(), authenticated, snapshot()))
            checkActive()
        }
        fun drain(stream: InputStream, isStderr: Boolean) {
            val buffer = ByteArray(TerminalLimits.OUTPUT_CHUNK_BYTES)
            while (true) {
                checkActive()
                val count = stream.read(buffer)
                if (count == -1) break
                if (count == 0) throw SshProbeFailure("output-no-progress")
                (if (isStderr) stderrBytes else stdoutBytes).addAndGet(count.toLong())
                checkActive()
                // Each stream has at most one callback in flight. Flow owns the shared renderer credit window.
                listener.onOutput(buffer.copyOf(count), isStderr)
                checkActive()
            }
        }
        var status = "failed"
        var reason: String? = null
        var cleanupCertain = true
        val cleanupFailed = AtomicBoolean(false)
        try {
            mailbox.bind(identity)
            checkActive()
            if (trustedFingerprint != null && trustedFingerprint != config.expectedHostKeySha256) {
                throw SshProbeFailure("trusted-reference-mismatch")
            }
            val policy = SshConnectionPolicy.create()
            checkActive()
            val ssh = SSHClient(policy)
            client = ssh
            ssh.connectTimeout = SshConnectionPolicy.IO_TIMEOUT_MILLIS
            ssh.timeout = SshConnectionPolicy.IO_TIMEOUT_MILLIS
            ssh.socketFactory = TrackingSocketFactory()
            ssh.addAlgorithmsVerifier { negotiated ->
                val selected = mapOf(
                    "kex" to negotiated.keyExchangeAlgorithm, "hostSignature" to negotiated.signatureAlgorithm,
                    "cipherC2S" to negotiated.client2ServerCipherAlgorithm,
                    "cipherS2C" to negotiated.server2ClientCipherAlgorithm,
                    "macC2S" to negotiated.client2ServerMACAlgorithm, "macS2C" to negotiated.server2ClientMACAlgorithm,
                    "compressionC2S" to negotiated.client2ServerCompressionAlgorithm,
                    "compressionS2C" to negotiated.server2ClientCompressionAlgorithm,
                )
                val accepted = selected["kex"] == SshConnectionPolicy.KEX &&
                    selected["hostSignature"] == SshConnectionPolicy.HOST_SIGNATURE &&
                    selected["cipherC2S"] == SshConnectionPolicy.CIPHER && selected["cipherS2C"] == SshConnectionPolicy.CIPHER &&
                    selected["macC2S"] == SshConnectionPolicy.MAC && selected["macS2C"] == SshConnectionPolicy.MAC &&
                    selected["compressionC2S"] == "none" && selected["compressionS2C"] == "none"
                if (accepted) algorithms.set(selected) else algorithmRejected.set(true)
                accepted && stopReason.get() == null
            }
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
                    listOf(SshConnectionPolicy.HOST_SIGNATURE)

                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    verifications.incrementAndGet()
                    val wireKey = Buffer.PlainBuffer().putPublicKey(key).compactData
                    val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding()
                        .encodeToString(MessageDigest.getInstance("SHA-256").digest(wireKey))
                    observed.compareAndSet(null, fingerprint)
                    val failure = when {
                        hostname != config.host || port != config.port -> "endpoint-mismatch"
                        fingerprint != config.expectedHostKeySha256 -> "reference-host-key-mismatch"
                        trustedFingerprint == null -> "trust-required"
                        fingerprint != trustedFingerprint -> "trusted-host-key-mismatch"
                        else -> null
                    }
                    // A later matching rekey must never erase an earlier rejection.
                    if (failure != null) trustFailure.compareAndSet(null, failure)
                    return trustFailure.get() == null && stopReason.get() == null
                }
            })
            observe("connecting")
            ssh.connect(config.host, config.port)
            checkActive()
            if (verifications.get() == 0L || observed.get() != trustedFingerprint) {
                throw SshProbeFailure("host-key-verification-missing")
            }
            observe("host-key-verified")
            val keyText = SshKeyFile.read(keyFile)
            checkActive()
            val provider = ssh.loadKeys(keyText, null, null)
            ssh.authPublickey(config.username, provider)
            authenticated = ssh.isAuthenticated
            if (!authenticated) throw SshProbeFailure("authentication-not-established")
            observe("authenticated")

            // Channel streams capture these settings at construction. A quiet terminal is allowed to idle;
            // the request/reply timeout remains bounded, and runtime separately owns the session watchdog.
            val socket = activeSocket.get() ?: throw SshProbeFailure("terminal-socket-unavailable")
            socket.soTimeout = 0
            ssh.transport.config.channelReadTimeoutMs = 0
            ssh.transport.config.channelErrorReadTimeoutMs = 0
            val session = ssh.startSession()
            session.allocatePTY("xterm-256color", initialSize.columns, initialSize.rows, 0, 0, emptyMap())
            ptyRequested = true
            val shell = session.startShell()
            mailbox.arm()
            observe("open")
            mailbox.release()
            val stderr = helpers.start("stderr") { drain(shell.errorStream, true) }
            helpers.start("writer") {
                while (true) {
                    val work = mailbox.take() ?: break
                    when (work) {
                        is TerminalWrite.Input -> {
                            try {
                                checkActive()
                                shell.outputStream.write(work.bytes)
                                shell.outputStream.flush()
                                // Returned write+flush is local stream progress, not a remote acknowledgement.
                                inputBytes.addAndGet(work.bytes.size.toLong())
                                checkActive()
                            } finally {
                                mailbox.completed(work)
                                work.bytes.fill(0)
                            }
                        }
                        is TerminalWrite.Resize -> {
                            checkActive()
                            shell.changeWindowDimensions(work.size.columns, work.size.rows, 0, 0)
                            lastSize.set(work.size) // SSH window-change does not provide an acknowledgement.
                            checkActive()
                        }
                    }
                }
            }
            drain(shell.inputStream, false)
            mailbox.close()
            stderr.get(SshConnectionPolicy.IO_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            checkActive()
            shell.join(SshConnectionPolicy.IO_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            if (shell.isOpen || !shell.isEOF) throw SshProbeFailure("channel-close-missing")
            // SSHJ Shell has no exit-status API. EOF plus channel close is never a command-success verdict.
            status = "closed"
            reason = "remote-eof"
        } catch (failure: Exception) {
            val trustReason = trustFailure.get()
            when {
                stopReason.get() != null || Thread.currentThread().isInterrupted || failure is InterruptedException -> {
                    status = "interrupted"
                    reason = stopReason.get() ?: "thread-interrupted"
                }
                authenticated && trustReason != null -> reason = "rekey-$trustReason"
                trustReason == "trust-required" -> {
                    status = "trust-required"
                    reason = "independent-host-key-matched"
                }
                trustReason != null -> {
                    status = "host-key-rejected"
                    reason = trustReason
                }
                else -> reason = safeFailure(helpers.failure.get() ?: failure)
            }
        } finally {
            mailbox.close()
            closeSocket()
            // Even SSHClient.close can wait on library locks. Run it under the same finite join budget.
            try {
                client?.let { ssh -> helpers.start("cleanup") {
                    try { ssh.close() } catch (failure: Exception) {
                        cleanupFailed.set(true)
                        throw failure
                    }
                } }
            } catch (_: Exception) {
                cleanupCertain = false
            }
            if (!helpers.closeAndJoin() || cleanupFailed.get()) cleanupCertain = false
        }
        if (!cleanupCertain) {
            return TerminalExit("failed", if (cleanupFailed.get()) "helper-cleanup-failed" else "helper-cleanup-timeout",
                observed.get(), authenticated, snapshot(), false)
        }
        // Recheck late rejection/cancellation after helpers stopped; never let a nominal EOF hide either.
        val lateTrust = trustFailure.get()
        when {
            stopReason.get() != null -> { status = "interrupted"; reason = stopReason.get() }
            authenticated && lateTrust != null -> { status = "failed"; reason = "rekey-$lateTrust" }
            algorithmRejected.get() -> { status = "failed"; reason = "algorithm-policy-rejected" }
            Security.getProviders().map { it.name } != originalProviders -> { status = "failed"; reason = "provider-order-changed" }
            helpers.failure.get() != null && status == "closed" -> { status = "failed"; reason = safeFailure(helpers.failure.get()!!) }
        }
        return TerminalExit(status, reason, observed.get(), authenticated, snapshot(), true)
    }

    private fun closeSocket() {
        try { activeSocket.getAndSet(null)?.close() } catch (_: IOException) { /* Cancellation stays sticky. */ }
    }

    private inner class TrackingSocketFactory : SocketFactory() {
        override fun createSocket(): Socket {
            if (stopReason.get() != null) throw SshProbeFailure("cancelled")
            val socket = Socket()
            if (!activeSocket.compareAndSet(null, socket)) {
                socket.close()
                throw SshProbeFailure("unexpected-second-socket")
            }
            if (stopReason.get() != null) {
                closeSocket()
                throw SshProbeFailure("cancelled")
            }
            return socket
        }
        override fun createSocket(host: String, port: Int): Socket = unexpected()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = unexpected()
        override fun createSocket(host: InetAddress, port: Int): Socket = unexpected()
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = unexpected()
        private fun unexpected(): Nothing = throw SshProbeFailure("unexpected-socket-factory-overload")
    }

    private fun safeFailure(failure: Exception): String = if (failure is SshProbeFailure) failure.code
        else failure.javaClass.simpleName.take(80).takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9]{0,79}")) } ?: "transport-failure"
}

internal sealed interface TerminalWrite {
    class Input(val bytes: ByteArray) : TerminalWrite
    data class Resize(val size: TerminalSize) : TerminalWrite
}

/** Fixed ring storage, one in-flight input chunk and one coalesced resize; no executor-per-key queue. */
internal class TerminalMailbox {
    private val monitor = Object()
    private val ring = ByteArray(TerminalLimits.INPUT_QUEUE_BYTES)
    private var identity: TerminalIdentity? = null
    private var armed = false
    private var released = false
    private var closed = false
    private var head = 0
    private var used = 0
    private var inFlight: TerminalWrite.Input? = null
    private var resize: TerminalSize? = null
    private var lastWasResize = false

    fun bind(value: TerminalIdentity) = synchronized(monitor) {
        check(identity == null)
        identity = value
    }
    fun arm() = synchronized(monitor) { if (!closed) armed = true }
    fun release() = synchronized(monitor) { if (!closed) { check(armed); released = true; monitor.notifyAll() } }

    fun offerInput(value: TerminalIdentity, bytes: ByteArray): TerminalAdmission = synchronized(monitor) {
        admission(value)?.let { return@synchronized it }
        val available = ring.size - used - (inFlight?.bytes?.size ?: 0)
        if (bytes.size > available) return@synchronized TerminalAdmission.FULL
        val tail = (head + used) % ring.size
        val first = minOf(bytes.size, ring.size - tail)
        bytes.copyInto(ring, tail, 0, first)
        bytes.copyInto(ring, 0, first, bytes.size)
        used += bytes.size
        monitor.notifyAll()
        TerminalAdmission.ACCEPTED
    }
    fun offerResize(value: TerminalIdentity, size: TerminalSize): TerminalAdmission = synchronized(monitor) {
        admission(value)?.let { return@synchronized it }
        resize = size
        monitor.notifyAll()
        TerminalAdmission.ACCEPTED
    }
    private fun admission(value: TerminalIdentity): TerminalAdmission? = when {
        identity != null && identity != value -> TerminalAdmission.STALE
        closed || !armed -> TerminalAdmission.STOPPED
        else -> null
    }

    /** One writer owns take/completed. A taken chunk remains charged until the real write finishes. */
    fun take(): TerminalWrite? = synchronized(monitor) {
        check(inFlight == null)
        while (!closed && (!released || used == 0 && resize == null)) monitor.wait()
        if (closed) return@synchronized null
        val pendingResize = resize
        if (pendingResize != null && (used == 0 || !lastWasResize)) {
            resize = null
            lastWasResize = true
            return@synchronized TerminalWrite.Resize(pendingResize)
        }
        val count = minOf(used, TerminalLimits.OUTPUT_CHUNK_BYTES)
        val bytes = ByteArray(count)
        val first = minOf(count, ring.size - head)
        ring.copyInto(bytes, 0, head, head + first)
        ring.copyInto(bytes, first, 0, count - first)
        ring.fill(0, head, head + first)
        ring.fill(0, 0, count - first)
        head = (head + count) % ring.size
        used -= count
        lastWasResize = false
        TerminalWrite.Input(bytes).also { inFlight = it }
    }
    fun completed(work: TerminalWrite.Input) = synchronized(monitor) {
        check(inFlight === work)
        inFlight = null
        monitor.notifyAll()
    }
    fun close() = synchronized(monitor) {
        closed = true
        ring.fill(0)
        used = 0
        resize = null
        monitor.notifyAll()
    }
}

/** Fixed helper count; library cleanup and both data directions share one finite shutdown budget. */
internal class TerminalHelpers(private val closeSocket: () -> Unit, private val joinTimeoutMillis: Long = 10_000) {
    init { require(joinTimeoutMillis > 0) }
    val failure = AtomicReference<Exception?>(null)
    private val threads = mutableListOf<Thread>()
    private val closing = AtomicBoolean(false)

    fun <T> start(name: String, work: () -> T): FutureTask<T> {
        check(threads.size < 3)
        val task = FutureTask {
            try { work() } catch (error: Exception) {
                // Closing an owned socket/interruption can fail a pending read/write normally.
                // Preserve every earlier failure and every failure of the cleanup operation itself.
                if (!closing.get() || name == "cleanup") {
                    failure.compareAndSet(null, error)
                    closeSocket()
                }
                throw error
            }
        }
        val thread = Thread(task, "cloudy-terminal-$name")
        threads.add(thread)
        thread.start()
        return task
    }
    fun closeAndJoin(): Boolean {
        closing.set(true)
        var interrupted = Thread.interrupted()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(joinTimeoutMillis)
        // Cleanup itself must get a chance to release SSHJ resources; socket closure already released I/O.
        threads.filter { it.name != "cloudy-terminal-cleanup" }.forEach { it.interrupt() }
        for (thread in threads) {
            while (thread.isAlive) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) break
                try { TimeUnit.NANOSECONDS.timedJoin(thread, remaining) }
                catch (_: InterruptedException) { interrupted = true }
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
        return threads.none { it.isAlive }
    }
}
