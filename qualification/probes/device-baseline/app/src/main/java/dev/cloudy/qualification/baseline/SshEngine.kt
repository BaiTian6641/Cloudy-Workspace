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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
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

/** One explicit finite attempt. The caller owns the worker, durable state and 60-second watchdog. */
internal class SshEngine {
    private val started = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val activeSocket = AtomicReference<Socket?>(null)

    /** Sticky even before run; closing the socket also interrupts connect/handshake/SFTP waits. */
    fun cancel() {
        cancelled.set(true)
        closeSocket()
    }

    fun run(
        config: SshConfigSpec,
        privateKey: File,
        attemptId: String,
        trustedHostKey: String?,
        onObservation: (SshObservation) -> Unit,
    ): SshEngineResult {
        check(started.compareAndSet(false, true)) { "SSH engine instances are single-use" }
        val startedAt = System.nanoTime()
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
            if (System.nanoTime() - startedAt >= TOTAL_TIMEOUT_NANOS) {
                closeSocket()
                throw SshProbeFailure("total-deadline-exceeded")
            }
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

            ssh.newSFTPClient().use { sftp ->
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
            closeSocket()
            try {
                client?.close()
            } catch (failure: Exception) {
                cleanupFailure = "cleanup-${failure.javaClass.simpleName.take(80)}"
            }
        }
        if (cancelled.get() && outcome.status == "completed") return result("cancelled", "cancelled")
        if (outcome.status == "completed") {
            val lateFailure = when {
                trustFailure.get() != null -> "rekey-${trustFailure.get()}"
                algorithmRejected.get() -> "algorithm-policy-rejected"
                System.nanoTime() - startedAt >= TOTAL_TIMEOUT_NANOS -> "total-deadline-exceeded"
                else -> null
            }
            if (lateFailure != null) return result("failed", lateFailure)
        }
        return cleanupFailure?.let { outcome.copy(
            status = if (outcome.status == "cancelled") "cancelled" else "failed", reason = it,
        ) } ?: outcome
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
        const val TOTAL_TIMEOUT_NANOS = 60_000_000_000L
        const val MAX_PRIVATE_KEY_BYTES = 16_384
        const val KEX = "ecdh-sha2-nistp256"
        const val HOST_SIGNATURE = "ecdsa-sha2-nistp256"
        const val CIPHER = "aes128-gcm@openssh.com"
        const val MAC = "hmac-sha2-256"
    }
}
