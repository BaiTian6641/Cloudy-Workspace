package dev.cloudy.qualification.baseline

import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SshKeyFileTest {
    @get:Rule val folder = TemporaryFolder()
    // An envelope-admission fixture, deliberately not an actual cryptographic key.
    private fun pem(): String {
        val bytes = ByteArray(32).apply { this[0] = 0x30 }
        return "-----BEGIN EC PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(bytes) +
            "\n-----END EC PRIVATE KEY-----\n"
    }

    @Test fun sharedReaderPreservesStrictEnvelopeAndNormalizesCrLf() {
        val file = folder.newFile().apply { writeText(pem().replace("\n", "\r\n")) }
        assertEquals(pem(), SshKeyFile.read(file))
    }

    @Test fun emptyOversizeDirectoryAndSymlinkAreRejectedBeforeKeyParsing() {
        val empty = folder.newFile()
        val large = folder.newFile().apply { writeBytes(ByteArray(16_385)) }
        listOf(empty, large, folder.newFolder()).forEach {
            assertEquals("private-key-type-or-size-out-of-bounds",
                assertThrows(SshProbeFailure::class.java) { SshKeyFile.read(it) }.code)
        }
        val original = folder.newFile().apply { writeText(pem()) }
        val link = folder.root.toPath().resolve("key-link")
        Files.createSymbolicLink(link, original.toPath())
        assertThrows(SshProbeFailure::class.java) { SshKeyFile.read(link.toFile()) }
    }

    @Test fun invalidUtf8AndAlternateOrEncryptedKeyFormatsFailClosed() {
        val invalid = folder.newFile().apply { writeBytes(byteArrayOf(0xc0.toByte(), 0x80.toByte())) }
        assertThrows(CharacterCodingException::class.java) { SshKeyFile.read(invalid) }
        listOf(pem().replace("EC PRIVATE KEY", "PRIVATE KEY"),
            pem().replace("EC PRIVATE KEY", "OPENSSH PRIVATE KEY"),
            pem().replace("\n", "\nProc-Type: 4,ENCRYPTED\n")).forEach { text ->
            val file = folder.newFile().apply { writeText(text) }
            assertThrows(SshProbeFailure::class.java) { SshKeyFile.read(file) }
        }
    }
}
