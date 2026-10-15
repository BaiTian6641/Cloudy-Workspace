package dev.cloudy.qualification.baseline

import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TerminalConfigTest {
    @get:Rule val folder = TemporaryFolder()
    private val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32))
    private fun spec() = TerminalConfigSpec("192.0.2.1", 22, "probe", fingerprint)
    private fun json() = """{"schemaVersion":1,"host":"192.0.2.1","port":22,"username":"probe","expectedHostKeySha256":"$fingerprint"}"""
    private fun read(text: String) = folder.newFile().apply { writeText(text) }.let(TerminalConfig::read)

    @Test fun ordinarySshPortAndCanonicalJsonAreAccepted() {
        val value = read(json())
        assertEquals(spec(), value)
        assertEquals(value.configIdentity, read("""{ "username":"pr\u006fbe", "port":22,
            "expectedHostKeySha256":"$fingerprint", "schemaVersion":1, "host":"192.0.2.1" }""").configIdentity)
        assertEquals(1, spec().copy(port = 1).port)
        assertEquals(65535, spec().copy(port = 65535).port)
    }

    @Test fun loginAndReferenceChangesFenceConsentWithoutChangingEndpoint() {
        val original = spec()
        listOf(original.copy(username = "other"), original.copy(expectedHostKeySha256 =
            "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 }))).forEach {
            assertEquals(original.endpointId, it.endpointId)
            assertNotEquals(original.configIdentity, it.configIdentity)
        }
        assertNotEquals(original.endpointId, original.copy(port = 2222).endpointId)
        assertNotEquals(original.endpointId, original.copy(host = "192.0.2.2").endpointId)
        assertFalse(original.toString().contains(original.host))
        assertFalse(original.toString().contains(original.username))
        assertFalse(original.toString().contains(fingerprint))
    }

    @Test fun terminalIdentityHasItsOwnDomainAndNoFiniteFixtureFields() {
        val original = spec().copy(port = 2222)
        val finite = SshConfigSpec("192.0.2.1", 2222, "probe", "/fixture", fingerprint, 28, "a".repeat(64))
        assertEquals(original.endpointId, finite.endpointId)
        assertNotEquals(original.configIdentity, finite.configIdentity)
        listOf("remoteDirectory", "sentinelLength", "sentinelSha256", "password").forEach {
            assertThrows(IllegalArgumentException::class.java) { read(json().replace("{", "{\"$it\":1,")) }
        }
    }

    @Test fun addressLoginAndFingerprintAreStrict() {
        listOf("localhost", "192.000.2.1", "::1", "0.1.2.3", "224.0.0.1", "192.0.2.256", "192.0.2.1 ").forEach {
            assertThrows(IllegalArgumentException::class.java) { spec().copy(host = it) }
        }
        listOf(0, -1, 65536).forEach { assertThrows(IllegalArgumentException::class.java) { spec().copy(port = it) } }
        listOf("Root", "-option", "a\nb", "user@host", "x".repeat(33)).forEach {
            assertThrows(IllegalArgumentException::class.java) { spec().copy(username = it) }
        }
        listOf(fingerprint + "=", fingerprint.dropLast(1) + "B", "MD5:" + "a".repeat(43)).forEach {
            assertThrows(IllegalArgumentException::class.java) { spec().copy(expectedHostKeySha256 = it) }
        }
    }

    @Test fun missingDuplicateCoercedAndMalformedFieldsFailClosed() {
        listOf(json().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            json().replace("\"port\":22,", ""), json().replace("{", "{\"po\\u0072t\":22,"),
            json().replace("\"port\":22", "\"port\":\"22\""),
            json().replace("\"host\":\"192.0.2.1\"", "\"host\":22"),
            json() + "{}", json().replace("}", ",}"), json().replace('"', '\''),
            "/*comment*/" + json(), json().replace("probe", "pr\\uD800be")).forEach {
            assertThrows(IllegalArgumentException::class.java) { read(it) }
        }
        listOf("22.0", "2.2e1", "+22", "022", "null", "true", "[]", "2147483648").forEach {
            assertThrows(IllegalArgumentException::class.java) { read(json().replace("\"port\":22", "\"port\":$it")) }
        }
    }

    @Test fun fileReaderRejectsInvalidEncodingOversizeAndSpecialFiles() {
        val invalid = folder.newFile().apply { writeBytes(byteArrayOf(0xc0.toByte(), 0x80.toByte())) }
        assertThrows(CharacterCodingException::class.java) { TerminalConfig.read(invalid) }
        val oversize = folder.newFile().apply { writeText(" ".repeat(TerminalConfig.CONFIG_BYTE_LIMIT + 1)) }
        assertThrows(IllegalArgumentException::class.java) { TerminalConfig.read(oversize) }
        assertThrows(IllegalArgumentException::class.java) { TerminalConfig.read(folder.newFolder()) }
        val original = folder.newFile().apply { writeText(json()) }
        val link = folder.root.toPath().resolve("link")
        Files.createSymbolicLink(link, original.toPath())
        assertThrows(IllegalArgumentException::class.java) { TerminalConfig.read(link.toFile()) }
    }
}
