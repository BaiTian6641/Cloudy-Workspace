package dev.cloudy.qualification.baseline

import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

/** Public, finite fixture bytes; neither remote content nor credentials enter the journal. */
internal object SshPayload {
    const val LENGTH = 65_536
    const val SHA256 = "f8a38b49873588eea0fa55c178b2ad20a05eb7f3eccf55623323405fdec79fe9"

    fun generate(): ByteArray = ByteArray(LENGTH) { ((it * 31 + 17) and 255).toByte() }

    /** Requests one extra byte after the expected contents, so a matching prefix cannot pass. */
    fun readVerified(
        expectedLength: Int,
        expectedSha256: String,
        readAt: (offset: Long, buffer: ByteArray, length: Int) -> Int,
        checkActive: () -> Unit,
    ): String {
        require(expectedLength in 1..LENGTH)
        require(expectedSha256.matches(Regex("[0-9a-f]{64}")))
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8_192)
        var received = 0
        while (received < expectedLength) {
            checkActive()
            val requested = minOf(buffer.size, expectedLength - received)
            val count = readAt(received.toLong(), buffer, requested)
            when {
                count < 0 -> throw SshProbeFailure("payload-short-read")
                count == 0 -> throw SshProbeFailure("payload-no-progress")
                count > requested -> throw SshProbeFailure("payload-oversized-response")
            }
            digest.update(buffer, 0, count)
            received += count
        }
        checkActive()
        val tail = readAt(received.toLong(), buffer, 1)
        if (tail != -1) throw SshProbeFailure("payload-extra-data-or-missing-eof")
        checkActive()
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (actual != expectedSha256) throw SshProbeFailure("payload-sha256-mismatch")
        return actual
    }
}

/** Codes are fixed by our source; exception messages from the remote peer are never published. */
internal class SshProbeFailure(val code: String) : IOException(code)

/** Admission only: SSHJ still decodes and validates the EC key; this does not implement cryptography. */
internal object SshPrivateKeyPem {
    fun validate(text: String): String {
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.removeSuffix("\n").split('\n')
        if (lines.size !in 3..18 || lines.first() != "-----BEGIN EC PRIVATE KEY-----" ||
            lines.last() != "-----END EC PRIVATE KEY-----") {
            throw SshProbeFailure("private-key-format-unsupported")
        }
        val body = lines.subList(1, lines.lastIndex)
        if (body.any { !it.matches(Regex("[A-Za-z0-9+/=]{1,64}")) }) {
            throw SshProbeFailure("private-key-encoding-invalid")
        }
        val encoded = body.joinToString("")
        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            throw SshProbeFailure("private-key-encoding-invalid")
        }
        try {
            // Generated unencrypted SEC1 P-256 is small. No encrypted KDF or alternate importer is admitted.
            if (bytes.size !in 32..512 || bytes[0] != 0x30.toByte() ||
                Base64.getEncoder().encodeToString(bytes) != encoded) {
                throw SshProbeFailure("private-key-encoding-invalid")
            }
        } finally {
            bytes.fill(0)
        }
        return normalized
    }
}
