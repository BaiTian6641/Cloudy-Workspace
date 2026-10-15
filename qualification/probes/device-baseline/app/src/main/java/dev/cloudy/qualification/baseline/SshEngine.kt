package dev.cloudy.qualification.baseline

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.verification.HostKeyVerifier

internal data class SshObservation(
    val status: String,
    val reason: String? = null,
    val observedHostKey: String? = null,
    val authenticated: Boolean = false,
    val details: Map<String, String> = emptyMap(),
)

internal data class SshEngineResult(
    val status: String,
    val reason: String? = null,
    val observedHostKey: String? = null,
    val authenticated: Boolean = false,
    val details: Map<String, String> = emptyMap(),
)

/** One explicit finite attempt. The caller owns the worker, durable state and operation watchdog. */
internal class SshEngine {
    private val started = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val activeSocket = AtomicReference<Socket?>(null)
    private val ptyControl = SshPtyControl()

    /** Sticky even before run; closing the socket also interrupts connect/handshake/SFTP waits. */
    fun cancel() {
        cancelled.set(true)
        ptyControl.close()
        closeSocket()
    }

    /** Fast admission only. Runtime must also fence the current attempt and visible observer. */
    fun control(action: SshPtyAction): Boolean = !cancelled.get() && ptyControl.offer(action)

    fun run(
        config: SshConfigSpec,
        privateKey: File,
        attemptId: String,
        trustedHostKey: String?,
        operation: SshOperation = SshOperation.SFTP,
        onObservation: (SshObservation) -> Unit,
    ): SshEngineResult {
        check(started.compareAndSet(false, true)) { "SSH engine instances are single-use" }
        val startedAt = System.nanoTime()
        val totalTimeoutNanos = TimeUnit.SECONDS.toNanos(operation.totalTimeoutSeconds)
        val helpers = SshHelpers(::closeSocket)
        val observed = AtomicReference<String?>(null)
        val trustFailure = AtomicReference<String?>(null)
        val verifications = AtomicInteger(0)
        val algorithms = AtomicReference<Map<String, String>>(emptyMap())
        val algorithmRejected = AtomicBoolean(false)
        val details = linkedMapOf<String, String>()
        var authenticated = false
        var client: SSHClient? = null
        fun checkActive() {
            if (cancelled.get() || Thread.currentThread().isInterrupted) {
                throw SshProbeFailure("cancelled")
            }
            if (System.nanoTime() - startedAt >= totalTimeoutNanos) {
                closeSocket()
                throw SshProbeFailure("total-deadline-exceeded")
            }
            helpers.failure.get()?.let { throw it }
            trustFailure.get()?.let { throw SshProbeFailure(it) }
            if (algorithmRejected.get()) throw SshProbeFailure("algorithm-policy-rejected")
        }
        fun snapshot(): Map<String, String> = details.toMap() + algorithms.get() +
            ("hostVerificationCount" to verifications.get().toString())
        fun observe(status: String) {
            checkActive()
            // Only this worker calls the callback. A failed durable observation aborts subsequent I/O.
            onObservation(SshObservation(status, observedHostKey = observed.get(),
                authenticated = authenticated, details = snapshot()))
            checkActive()
        }
        fun result(status: String, reason: String? = null) = SshEngineResult(
            status, reason, observed.get(), authenticated, snapshot(),
        )
        var cleanupFailure: String? = null
        val outcome = try {
            checkActive()
            require(UUID.fromString(attemptId).toString() == attemptId)
            require(config.sentinelLength in 1..SshPayload.LENGTH)
            if (trustedHostKey != null && trustedHostKey != config.expectedHostKeySha256) {
                throw SshProbeFailure("trusted-reference-mismatch")
            }
            val policy = modernConfig()
            val ssh = SSHClient(policy)
            client = ssh
            ssh.connectTimeout = IO_TIMEOUT_MILLIS
            ssh.timeout = IO_TIMEOUT_MILLIS
            ssh.socketFactory = TrackingSocketFactory()
            ssh.addAlgorithmsVerifier { negotiated ->
                val selected = mapOf(
                    "kex" to negotiated.keyExchangeAlgorithm,
                    "hostSignature" to negotiated.signatureAlgorithm,
                    "cipherC2S" to negotiated.client2ServerCipherAlgorithm,
                    "cipherS2C" to negotiated.server2ClientCipherAlgorithm,
                    "macC2S" to negotiated.client2ServerMACAlgorithm,
                    "macS2C" to negotiated.server2ClientMACAlgorithm,
                    "compressionC2S" to negotiated.client2ServerCompressionAlgorithm,
                    "compressionS2C" to negotiated.server2ClientCompressionAlgorithm,
                )
                val accepted = selected["kex"] == KEX && selected["hostSignature"] == HOST_SIGNATURE &&
                    selected["cipherC2S"] == CIPHER && selected["cipherS2C"] == CIPHER &&
                    selected["macC2S"] == MAC && selected["macS2C"] == MAC &&
                    selected["compressionC2S"] == "none" && selected["compressionS2C"] == "none"
                // Reject before retaining any unexpected server-controlled string in an observation.
                if (accepted) algorithms.set(selected) else algorithmRejected.set(true)
                accepted && !cancelled.get()
            }
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
                    listOf(HOST_SIGNATURE)

                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    verifications.incrementAndGet()
                    val wireKey = Buffer.PlainBuffer().putPublicKey(key).compactData
                    val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding()
                        .encodeToString(MessageDigest.getInstance("SHA-256").digest(wireKey))
                    // Keep the first observed identity immutable in history; still reject every mismatching rekey.
                    observed.compareAndSet(null, fingerprint)
                    val failure = when {
                        hostname != config.host || port != config.port -> "endpoint-mismatch"
                        fingerprint != config.expectedHostKeySha256 -> "reference-host-key-mismatch"
                        trustedHostKey == null -> "trust-required"
                        fingerprint != trustedHostKey -> "trusted-host-key-mismatch"
                        else -> null
                    }
                    if (failure != null) trustFailure.set(failure)
                    // This runs on SSHJ's transport thread, including rekeys. Never call UI/state here.
                    return failure == null && !cancelled.get()
                }
            })
            observe("connecting")
            ssh.connect(config.host, config.port)
            checkActive()
            if (verifications.get() == 0 || observed.get() != trustedHostKey || trustFailure.get() != null) {
                throw SshProbeFailure("host-key-verification-missing")
            }
            observe("host-key-verified")
            val keyText = readPrivateKey(privateKey)
            checkActive()
            val provider = ssh.loadKeys(keyText, null, null)
            ssh.authPublickey(config.username, provider)
            authenticated = ssh.isAuthenticated
            if (!authenticated) throw SshProbeFailure("authentication-not-established")
            observe("authenticated")

            if (operation == SshOperation.EXEC_PTY) {
                runExecPty(ssh, details, helpers, ::checkActive, ::observe)
            } else ssh.newSFTPClient().use { sftp ->
                sftp.getSFTPEngine().timeoutMs = IO_TIMEOUT_MILLIS
                details["sftpVersion"] = sftp.getSFTPEngine().operativeProtocolVersion.toString()
                val directory = config.remoteDirectory.trimEnd('/')
                readVerified(sftp, "$directory/sentinel.txt", config.sentinelLength,
                    config.sentinelSha256, ::checkActive)
                details["sentinelBytes"] = config.sentinelLength.toString()
                details["sentinelSha256"] = config.sentinelSha256
                observe("sentinel-verified")

                val uploadName = "cloudy-$attemptId.bin"
                val uploadPath = "$directory/$uploadName"
                details["uploadName"] = uploadName
                details["uploadBytes"] = SshPayload.LENGTH.toString()
                details["uploadSha256"] = SshPayload.SHA256
                details["uploadAcknowledgedBytes"] = "0"
                observe("upload-create-requested")
                val modes = EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL)
                val attributes = FileAttributes.Builder().withPermissions(384).build() // owner read/write
                sftp.open(uploadPath, modes, attributes).use { remote ->
                    observe("upload-created")
                    val payload = SshPayload.generate()
                    var offset = 0
                    while (offset < payload.size) {
                        checkActive()
                        val count = minOf(4_096, payload.size - offset)
                        remote.write(offset.toLong(), payload, offset, count)
                        offset += count
                        details["uploadAcknowledgedBytes"] = offset.toString()
                    }
                }
                checkActive()
                readVerified(sftp, uploadPath, SshPayload.LENGTH, SshPayload.SHA256, ::checkActive)
                details["readbackBytes"] = SshPayload.LENGTH.toString()
                details["readbackSha256"] = SshPayload.SHA256
                observe("upload-verified")

                // No TRUNC, replacement, deletion or second write is ever attempted, even on a broken server.
                val unexpectedHandle = try {
                    sftp.open(uploadPath, modes, attributes)
                } catch (failure: SFTPException) {
                    checkActive()
                    val code = failure.statusCode
                    if (code != Response.StatusCode.FILE_ALREADY_EXISTS &&
                        !(sftp.getSFTPEngine().operativeProtocolVersion == 3 && code == Response.StatusCode.FAILURE)) {
                        throw failure
                    }
                    details["collisionStatus"] = code.name
                    null
                }
                if (unexpectedHandle != null) {
                    unexpectedHandle.close()
                    throw SshProbeFailure("exclusive-create-unexpectedly-succeeded")
                }
                observe("collision-refused")
                readVerified(sftp, uploadPath, SshPayload.LENGTH, SshPayload.SHA256, ::checkActive)
                details["collisionBytes"] = SshPayload.LENGTH.toString()
                details["collisionSha256"] = SshPayload.SHA256
                observe("collision-verified")
            }
            checkActive()
            result("completed")
        } catch (failure: Exception) {
            val trustReason = trustFailure.get()
            when {
                cancelled.get() || (failure is SshProbeFailure && failure.code == "cancelled") ->
                    result("cancelled", "cancelled")
                authenticated && trustReason != null -> result("failed", "rekey-$trustReason")
                trustReason == "trust-required" -> result("trust-required", "independent-host-key-matched")
                trustReason != null -> result("host-key-rejected", trustReason)
                else -> result("failed", if (failure is SshProbeFailure) failure.code
                    else failure.javaClass.simpleName.take(80))
            }
        } finally {
            // Close the owned socket first; do not wait for a polite peer before releasing cancellation.
            ptyControl.close()
            closeSocket()
            if (!helpers.closeAndJoin()) cleanupFailure = "helper-cleanup-timeout"
            try {
                client?.close()
            } catch (failure: Exception) {
                if (cleanupFailure == null) cleanupFailure = "cleanup-${failure.javaClass.simpleName.take(80)}"
            }
        }
        // Cleanup uncertainty must survive a simultaneous cancel/deadline so runtime can quarantine ownership.
        cleanupFailure?.let {
            return outcome.copy(status = if (cancelled.get() || outcome.status == "cancelled") "cancelled" else "failed",
                reason = it)
        }
        if (cancelled.get() && outcome.status == "completed") return result("cancelled", "cancelled")
        if (outcome.status == "completed") {
            val lateFailure = when {
                trustFailure.get() != null -> "rekey-${trustFailure.get()}"
                algorithmRejected.get() -> "algorithm-policy-rejected"
                System.nanoTime() - startedAt >= totalTimeoutNanos -> "total-deadline-exceeded"
                else -> null
            }
            if (lateFailure != null) return result("failed", lateFailure)
        }
        return outcome
    }

    private fun runExecPty(
        ssh: SSHClient,
        details: MutableMap<String, String>,
        helpers: SshHelpers,
        checkActive: () -> Unit,
        observe: (String) -> Unit,
    ) {
        observe("exec-requested")
        ssh.startSession().use { session ->
            val command = session.exec(SshPtyProtocol.EXEC_COMMAND)
            // A bounded stderr reader prevents independent SSH stream windows from deadlocking.
            val stderr = helpers.start("exec-stderr") {
                SshPtyProtocol.drain(command.errorStream, checkActive)
            }
            val out = SshPtyProtocol.drain(command.inputStream, checkActive)
            val err = stderr.get(IO_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            checkActive()
            command.join(IO_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            if (command.isOpen) throw SshProbeFailure("exec-close-missing")
            command.close()
            details["execStdoutBytes"] = out.size.toString()
            details["execStdoutSha256"] = SshPtyProtocol.sha256(out)
            details["execStderrBytes"] = err.size.toString()
            details["execStderrSha256"] = SshPtyProtocol.sha256(err)
            val status = command.exitStatus ?: throw SshProbeFailure("exec-exit-status-missing")
            if (status !in 0..255) throw SshProbeFailure("exec-exit-status-out-of-bounds")
            details["execExitStatus"] = status.toString()
            SshPtyProtocol.requireBytes(out, SshPtyProtocol.EXEC_STDOUT, "exec-stdout-mismatch")
            SshPtyProtocol.requireBytes(err, SshPtyProtocol.EXEC_STDERR, "exec-stderr-mismatch")
            if (status != SshPtyProtocol.EXEC_EXIT) throw SshProbeFailure("exec-exit-status-mismatch")
            observe("exec-verified")
        }

        checkActive()
        // Socket SO_TIMEOUT is independent of channel waits: change both for the deliberate UI wait.
        // Connect/auth/exec and channel-request replies retain their original ten-second bounds.
        val socket = activeSocket.get() ?: throw SshProbeFailure("pty-socket-unavailable")
        socket.soTimeout = PTY_READ_TIMEOUT_MILLIS
        ssh.transport.config.channelReadTimeoutMs = PTY_READ_TIMEOUT_MILLIS
        ssh.transport.config.channelErrorReadTimeoutMs = PTY_READ_TIMEOUT_MILLIS
        observe("pty-requested")
        ssh.startSession().use { session ->
            session.allocatePTY("vt100", 80, 24, 0, 0, mapOf(
                PTYMode.ECHO to 0, PTYMode.ECHONL to 0, PTYMode.ICANON to 1, PTYMode.ONLCR to 0,
            ))
            val shell = session.startShell()
            val stderr = helpers.start("pty-stderr") {
                SshPtyProtocol.drain(shell.errorStream, checkActive)
            }
            helpers.start("pty-control") {
                while (true) {
                    val action = ptyControl.take() ?: break
                    checkActive()
                    val input = when (action) {
                        SshPtyAction.RESIZE -> {
                            // No acknowledgement is requested by SSHJ; the worker verifies actual remote SIZE.
                            shell.changeWindowDimensions(132, 43, 0, 0)
                            SshPtyProtocol.SIZE_INPUT
                        }
                        SshPtyAction.FINISH -> SshPtyProtocol.FINISH_INPUT
                    }
                    shell.outputStream.write(input.toByteArray(Charsets.US_ASCII))
                    shell.outputStream.flush()
                    checkActive()
                    ptyControl.applied(action)
                    if (action == SshPtyAction.FINISH) break
                }
            }
            val transcript = java.io.ByteArrayOutputStream()
            fun record(expected: String) {
                transcript.write(SshPtyProtocol.readExactly(shell.inputStream, expected, checkActive))
                details["ptyStdoutBytes"] = transcript.size().toString()
                details["ptyStdoutSha256"] = SshPtyProtocol.sha256(transcript.toByteArray())
            }
            fun awaitAction(action: SshPtyAction, phase: String, expected: String, inputBytes: Int) {
                ptyControl.arm(action)
                observe(phase)
                ptyControl.release(action)
                record(expected) // Real blocking channel read; only the independent control path sends input.
                if (!ptyControl.awaitApplied(action, IO_TIMEOUT_MILLIS.toLong())) {
                    throw SshProbeFailure("pty-response-without-applied-control")
                }
                checkActive()
                details["ptyInputBytes"] = inputBytes.toString()
            }
            record(SshPtyProtocol.READY)
            details["ptyInitialCols"] = "80"
            details["ptyInitialRows"] = "24"
            details["ptyInputBytes"] = "0"
            awaitAction(SshPtyAction.RESIZE, "pty-awaiting-resize", SshPtyProtocol.RESIZED,
                SshPtyProtocol.SIZE_INPUT.length)
            details["ptyResizedCols"] = "132"
            details["ptyResizedRows"] = "43"
            observe("pty-resize-verified")
            awaitAction(SshPtyAction.FINISH, "pty-awaiting-finish", SshPtyProtocol.DONE,
                SshPtyProtocol.SIZE_INPUT.length + SshPtyProtocol.FINISH_INPUT.length)
            SshPtyProtocol.requireEof(shell.inputStream, checkActive)
            val err = stderr.get(IO_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            details["ptyStderrBytes"] = err.size.toString()
            details["ptyStderrSha256"] = SshPtyProtocol.sha256(err)
            SshPtyProtocol.requireBytes(err, "", "pty-stderr-unexpected")
            shell.join(IO_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            if (shell.isOpen || !shell.isEOF) throw SshProbeFailure("pty-clean-eof-or-close-missing")
            shell.close()
            // Shell intentionally has no public exit-status getter; marker + EOF + cleanup is the contract.
            details["ptyCompletionMarker"] = SshPtyProtocol.DONE.trimEnd('\n')
            ptyControl.close()
            observe("pty-verified")
        }
    }

    private fun readVerified(
        sftp: SFTPClient,
        path: String,
        length: Int,
        sha256: String,
        checkActive: () -> Unit,
    ) {
        checkActive()
        val before = sftp.lstat(path)
        if (before.type != FileMode.Type.REGULAR || before.size != length.toLong()) {
            throw SshProbeFailure("remote-file-type-or-size-mismatch")
        }
        sftp.open(path, EnumSet.of(OpenMode.READ)).use { remote ->
            checkActive()
            val opened = remote.fetchAttributes()
            if (opened.type != FileMode.Type.REGULAR || opened.size != length.toLong()) {
                throw SshProbeFailure("opened-file-type-or-size-mismatch")
            }
            SshPayload.readVerified(length, sha256,
                { offset, buffer, requested -> remote.read(offset, buffer, 0, requested) }, checkActive)
        }
    }

    private fun readPrivateKey(file: File): String {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS)
        if (!attributes.isRegularFile || attributes.size() !in 1..MAX_PRIVATE_KEY_BYTES.toLong()) {
            throw SshProbeFailure("private-key-type-or-size-out-of-bounds")
        }
        val buffer = ByteArray(MAX_PRIVATE_KEY_BYTES + 1)
        try {
            val size = file.inputStream().use { input ->
                var size = 0
                while (size < buffer.size) {
                    val count = input.read(buffer, size, buffer.size - size)
                    if (count == -1) break
                    if (count == 0) throw SshProbeFailure("private-key-no-progress")
                    size += count
                }
                size
            }
            if (size !in 1..MAX_PRIVATE_KEY_BYTES) throw SshProbeFailure("private-key-size-out-of-bounds")
            // SSHJ's public content API retains a String; do not claim complete secret zeroization.
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(buffer, 0, size)).toString()
            return SshPrivateKeyPem.validate(text)
        } finally {
            buffer.fill(0)
        }
    }

    private fun modernConfig(): DefaultSecurityProviderConfig = DefaultSecurityProviderConfig().apply {
        // Initialize this class before SSHClient. It disables SSHJ auto-registration, not Android providers.
        keyExchangeFactories = keyExchangeFactories.filter { it.name == KEX || it.name == "ext-info-c" }
        keyAlgorithms = keyAlgorithms.filter { it.name == HOST_SIGNATURE }
        cipherFactories = cipherFactories.filter { it.name == CIPHER }
        setMACFactories(getMACFactories().filter { it.name == MAC })
        compressionFactories = compressionFactories.filter { it.name == "none" }
        if (keyExchangeFactories.none { it.name == KEX } || keyAlgorithms.isEmpty() ||
            cipherFactories.isEmpty() || getMACFactories().isEmpty() || compressionFactories.isEmpty()) {
            throw SshProbeFailure("modern-algorithm-unavailable")
        }
        timeoutMs = IO_TIMEOUT_MILLIS
        channelReadTimeoutMs = IO_TIMEOUT_MILLIS
        channelErrorReadTimeoutMs = IO_TIMEOUT_MILLIS
        maxCircularBufferSize = 1_048_576
    }

    private fun closeSocket() {
        try {
            activeSocket.getAndSet(null)?.close()
        } catch (_: IOException) {
            // The sticky cancellation flag still prevents another operation or socket registration.
        }
    }

    private inner class TrackingSocketFactory : SocketFactory() {
        override fun createSocket(): Socket {
            if (cancelled.get()) throw SshProbeFailure("cancelled")
            val socket = Socket()
            if (!activeSocket.compareAndSet(null, socket)) {
                socket.close()
                throw SshProbeFailure("unexpected-second-socket")
            }
            if (cancelled.get()) {
                closeSocket()
                throw SshProbeFailure("cancelled")
            }
            return socket
        }

        // The pinned SSHClient.connect(String, Int) uses only the unconnected overload above.
        // Refuse an unexpected path instead of connecting an untracked socket or accepting a DNS route.
        override fun createSocket(host: String, port: Int): Socket = unexpectedOverload()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            unexpectedOverload()
        override fun createSocket(host: InetAddress, port: Int): Socket = unexpectedOverload()
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            unexpectedOverload()
        private fun unexpectedOverload(): Nothing = throw SshProbeFailure("unexpected-socket-factory-overload")
    }

    private companion object {
        const val IO_TIMEOUT_MILLIS = 10_000
        const val PTY_READ_TIMEOUT_MILLIS = 120_000
        const val MAX_PRIVATE_KEY_BYTES = 16_384
        const val KEX = "ecdh-sha2-nistp256"
        const val HOST_SIGNATURE = "ecdsa-sha2-nistp256"
        const val CIPHER = "aes128-gcm@openssh.com"
        const val MAC = "hmac-sha2-256"
    }
}

/** At most one stderr reader plus one PTY control thread; every run joins its owned helpers. */
private class SshHelpers(private val closeSocket: () -> Unit) {
    val failure = AtomicReference<Exception?>(null)
    private val threads = mutableListOf<Thread>()

    fun <T> start(name: String, work: () -> T): FutureTask<T> {
        check(threads.size < 3) // Exec reader, then PTY reader and control; no per-action thread creation.
        val task = FutureTask {
            try {
                work()
            } catch (error: Exception) {
                failure.compareAndSet(null, error)
                closeSocket()
                throw error
            }
        }
        val thread = Thread(task, "cloudy-ssh-$name")
        threads.add(thread)
        thread.start()
        return task
    }

    fun closeAndJoin(): Boolean {
        var interrupted = Thread.interrupted()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        threads.forEach { it.interrupt() }
        for (thread in threads) {
            while (thread.isAlive) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) break
                try {
                    TimeUnit.NANOSECONDS.timedJoin(thread, remaining)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
        return threads.none { it.isAlive }
    }
}
