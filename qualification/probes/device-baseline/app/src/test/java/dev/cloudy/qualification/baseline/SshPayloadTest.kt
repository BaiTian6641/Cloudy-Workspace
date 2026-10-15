package dev.cloudy.qualification.baseline

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** Synthetic byte readers test admission/integrity only; none establishes an SSH server result. */
class SshPayloadTest {
    @Test fun publicFixtureHasFixedLengthAndDigest() {
        val bytes = SshPayload.generate()
        assertEquals(65_536, bytes.size)
        assertEquals(SshPayload.SHA256, digest(bytes))
        bytes[0] = 0
        assertFalse(bytes.contentEquals(SshPayload.generate()))
    }

    @Test fun shortChunksAreConsumedAndAnExtraEofReadIsRequired() {
        val bytes = "real finite fixture".toByteArray()
        var calls = 0
        val hash = SshPayload.readVerified(bytes.size, digest(bytes), { offset, buffer, requested ->
            calls++
            val start = offset.toInt()
            if (start == bytes.size) -1 else {
                val size = minOf(3, requested, bytes.size - start)
                bytes.copyInto(buffer, 0, start, start + size)
                size
            }
        }, {})
        assertEquals(digest(bytes), hash)
        assertEquals(8, calls) // Seven partial reads and a distinct EOF request.
    }

    @Test fun matchingPrefixWithExtraByteIsRejected() {
        val bytes = "sentinel".toByteArray()
        assertFailure("payload-extra-data-or-missing-eof") {
            SshPayload.readVerified(bytes.size, digest(bytes), { offset, buffer, requested ->
                if (offset == bytes.size.toLong()) { buffer[0] = 1; 1 }
                else { bytes.copyInto(buffer); requested }
            }, {})
        }
    }

    @Test fun earlyEofAndZeroProgressCannotPassOrLoopForever() {
        assertFailure("payload-short-read") {
            SshPayload.readVerified(1, digest(byteArrayOf(1)), { _, _, _ -> -1 }, {})
        }
        assertFailure("payload-no-progress") {
            SshPayload.readVerified(1, digest(byteArrayOf(1)), { _, _, _ -> 0 }, {})
        }
    }

    @Test fun readCountBeyondRequestIsRejectedBeforeDigestUpdate() {
        assertFailure("payload-oversized-response") {
            SshPayload.readVerified(1, digest(byteArrayOf(1)), { _, _, requested -> requested + 1 }, {})
        }
    }

    @Test fun sameLengthWrongBytesFailIntegrity() {
        assertFailure("payload-sha256-mismatch") {
            SshPayload.readVerified(1, digest(byteArrayOf(1)), { offset, buffer, _ ->
                if (offset > 0) -1 else { buffer[0] = 2; 1 }
            }, {})
        }
    }

    @Test fun cancellationBeforeReadOrAtVerificationPreventsSuccess() {
        var reads = 0
        val read = { offset: Long, buffer: ByteArray, _: Int ->
            reads++
            if (offset > 0) -1 else { buffer[0] = 1; 1 }
        }
        assertFailure("test-cancel") {
            SshPayload.readVerified(1, digest(byteArrayOf(1)), read) { throw SshProbeFailure("test-cancel") }
        }
        assertEquals(0, reads)
        var checks = 0
        assertFailure("test-cancel") {
            SshPayload.readVerified(1, digest(byteArrayOf(1)), read) {
                if (++checks == 3) throw SshProbeFailure("test-cancel")
            }
        }
        assertEquals(2, reads)
    }

    @Test fun invalidBoundsAreRejectedBeforeAnyRead() {
        val invalid = { _: Long, _: ByteArray, _: Int -> error("Must not read invalid bounds") }
        assertThrows(IllegalArgumentException::class.java) {
            SshPayload.readVerified(0, SshPayload.SHA256, invalid, {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            SshPayload.readVerified(SshPayload.LENGTH + 1, SshPayload.SHA256, invalid, {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            SshPayload.readVerified(1, "unvalidated-hash", invalid, {})
        }
    }

    @Test fun sec1EnvelopeAllowsOnlyBoundedCanonicalUnencryptedBase64() {
        // Synthetic envelope bytes test format admission only, not an actual EC key or SSH authentication.
        val text = envelope(ByteArray(128) { if (it == 0) 0x30 else 1 })
        assertEquals(text, SshPrivateKeyPem.validate(text))
        assertEquals(text, SshPrivateKeyPem.validate(text.replace("\n", "\r\n")))
        assertFailure("private-key-encoding-invalid") { SshPrivateKeyPem.validate(envelope(ByteArray(513) { 0x30 })) }
        assertFailure("private-key-encoding-invalid") { SshPrivateKeyPem.validate(envelope(ByteArray(8) { 0x30 })) }
    }

    @Test fun encryptedAndAlternativeKeyFormatsAreNeverSentToTheDecoder() {
        val text = envelope(ByteArray(128) { if (it == 0) 0x30 else 1 })
        listOf("OPENSSH PRIVATE KEY", "ENCRYPTED PRIVATE KEY", "PRIVATE KEY", "RSA PRIVATE KEY").forEach {
            assertFailure("private-key-format-unsupported") {
                SshPrivateKeyPem.validate(text.replace("EC PRIVATE KEY", it))
            }
        }
        assertFailure("private-key-format-unsupported") { SshPrivateKeyPem.validate("PuTTY-User-Key-File-3: ecdsa\n") }
        assertFailure("private-key-encoding-invalid") {
            SshPrivateKeyPem.validate(text.replace("-----BEGIN EC PRIVATE KEY-----\n",
                "-----BEGIN EC PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\nDEK-Info: AES-256-CBC,0000\n"))
        }
    }

    @Test fun keyEnvelopeRejectsExtraObjectsWhitespaceAndNoncanonicalEncoding() {
        val text = envelope(ByteArray(128) { if (it == 0) 0x30 else 1 })
        listOf("\n$text", "$text\n").forEach {
            assertFailure("private-key-format-unsupported") { SshPrivateKeyPem.validate(it) }
        }
        assertFailure("private-key-encoding-invalid") { SshPrivateKeyPem.validate(text + text) }
        assertFailure("private-key-encoding-invalid") { SshPrivateKeyPem.validate(text.replace("AQEB", "AQ B")) }
        assertFailure("private-key-encoding-invalid") { SshPrivateKeyPem.validate(text.replace("=\n-----END", "\n-----END")) }
    }

    private fun assertFailure(code: String, block: () -> Unit) {
        assertEquals(code, assertThrows(SshProbeFailure::class.java) { block() }.code)
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun envelope(bytes: ByteArray): String = "-----BEGIN EC PRIVATE KEY-----\n" +
        Base64.getEncoder().encodeToString(bytes).chunked(64).joinToString("\n") +
        "\n-----END EC PRIVATE KEY-----\n"
}
