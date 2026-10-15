package dev.cloudy.qualification.baseline

import java.io.File
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real private-file parsing; no Android JSON stubs or credential material. */
class SshConfigTest {
    @get:Rule val folder = TemporaryFolder()
    private val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32))
    private val otherFingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
    private val hash = "a".repeat(64)
    private fun spec() = SshConfigSpec.validate(1, "192.0.2.1", 2222, "probe", "/fixture", fingerprint, 64, hash)
    private fun json(): String = """{"schemaVersion":1,"host":"192.0.2.1","port":2222,"username":"probe","remoteDirectory":"/fixture","expectedHostKeySha256":"$fingerprint","sentinelLength":64,"sentinelSha256":"$hash"}"""
    private fun read(text: String): SshConfigSpec = folder.newFile().apply { writeText(text) }.let(SshConfig::read)
    private fun rejects(text: String) { assertThrows(IllegalArgumentException::class.java) { read(text) } }

    @Test fun fieldOrderWhitespaceAndJsonEscapesDoNotChangeIdentity() {
        val canonical = read(json())
        val reordered = """{ "sentinelSha256":"$hash", "sentinelLength":64,
            "expectedHostKeySha256":"$fingerprint", "remoteDirectory":"\/fixture", "username":"pr\u006fbe",
            "port":2222, "host":"192.0.2.1", "schemaVersion":1 }"""
        assertEquals(spec(), canonical)
        assertEquals(canonical.configIdentity, read(reordered).configIdentity)
        assertTrue(canonical.endpointId.matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun hostTrustSurvivesLoginChangesButQueuedConfigConsentDoesNot() {
        val original = spec()
        val changed = original.copy(username = "another_user")
        assertEquals(original.endpointId, changed.endpointId)
        assertNotEquals(original.configIdentity, changed.configIdentity)
        assertNotEquals(original.endpointId, original.copy(port = 2223).endpointId)
        assertNotEquals(original.endpointId, original.copy(host = "192.0.2.2").endpointId)
    }

    @Test fun everySecurityRelevantConfigFieldChangesItsIdentity() {
        val original = spec()
        val changes = listOf(original.copy(host = "192.0.2.2"), original.copy(port = 2223),
            original.copy(username = "another"), original.copy(remoteDirectory = "/other"),
            original.copy(expectedHostKeySha256 = otherFingerprint), original.copy(sentinelLength = 65),
            original.copy(sentinelSha256 = "b".repeat(64)))
        changes.forEach { assertNotEquals(original.configIdentity, it.configIdentity) }
        assertEquals(changes.size, changes.map { it.configIdentity }.distinct().size)
    }

    @Test fun literalIpv4AndHighPortBoundsPreventDnsAndAmbiguousEndpoints() {
        listOf("localhost", "192.000.2.1", "192.0.2", "192.0.2.256", "0.1.2.3", "224.0.0.1",
            "[::1]", "::1", "192.0.2.1 ", "1.2.3.4.example", "0x7f000001").forEach {
            assertThrows(IllegalArgumentException::class.java) { spec().copy(host = it) }
        }
        listOf(0, 22, 1023, 65536).forEach { assertThrows(IllegalArgumentException::class.java) { spec().copy(port = it) } }
        assertEquals(1024, spec().copy(port = 1024).port)
        assertEquals(65535, spec().copy(port = 65535, host = "223.255.255.255").port)
    }

    @Test fun fingerprintsRequireCanonicalSha256Base64IncludingUnusedPadBits() {
        assertTrue(SshConfigSpec.validFingerprint(fingerprint))
        assertFalse(SshConfigSpec.validFingerprint(fingerprint.dropLast(1) + "B"))
        listOf(fingerprint + "=", fingerprint.dropLast(1), fingerprint.replace("SHA256:", "MD5:"),
            fingerprint.lowercase(), "SHA256:" + "_".repeat(43)).forEach {
            assertThrows(IllegalArgumentException::class.java) { spec().copy(expectedHostKeySha256 = it) }
        }
    }

    @Test fun pathAndLoginBoundsExcludeTraversalControlsAndAmbiguousComponents() {
        listOf("relative", "/../fixture", "/a/./b", "/a//b", "/a/", "/a\nb", "/a\u0000b", "/é", "/" + "x".repeat(256)).forEach {
            assertThrows(IllegalArgumentException::class.java) { spec().copy(remoteDirectory = it) }
        }
        listOf("", "Root", "user@host", "-option", "user name", "u".repeat(33)).forEach {
            assertThrows(IllegalArgumentException::class.java) { spec().copy(username = it) }
        }
        assertEquals("/literal space", spec().copy(remoteDirectory = "/literal space").remoteDirectory)
        assertEquals("/", spec().copy(remoteDirectory = "/").remoteDirectory)
        assertEquals(256, spec().copy(remoteDirectory = "/" + "x".repeat(255)).remoteDirectory.length)
    }

    @Test fun typedSchemaRejectsCoercionAndMissingUnknownOrDuplicateFields() {
        listOf(json().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
            json().replace("\"port\":2222", "\"port\":\"2222\""),
            json().replace("\"host\":\"192.0.2.1\"", "\"host\":123"),
            json().replace("\"sentinelLength\":64,", ""),
            json().replace("{", "{\"unexpected\":1,"),
            json().replace("{", "{\"port\":2222,"),
            json().replace("{", "{\"po\\u0072t\":2222,"),
            json().replace("\"schemaVersion\":1", "\"schemaVersion\":2")).forEach(::rejects)
    }

    @Test fun parserRejectsRelaxedJsonAndNonIntegerNumbers() {
        listOf("null", "true", "[]", "{}", "64.0", "6.4e1", "064", "+64", "2147483648").forEach {
            rejects(json().replace("\"sentinelLength\":64", "\"sentinelLength\":$it"))
        }
        listOf(json() + "{}", json().replace("}", ",}"), json().replace('"', '\''),
            "/*comment*/" + json(), json().replace("\"host\"", "host"), json().dropLast(1)).forEach(::rejects)
    }

    @Test fun invalidEscapesAndDecodedControlCharactersAreRejected() {
        listOf("\\q", "\\uZZZZ", "\\u0000", "\\n", "\\uD800", "\\u00e9").forEach {
            rejects(json().replace("/fixture", "/fixture$it"))
        }
    }

    @Test fun fileReadIsBoundedAndMalformedUtf8IsAnObservableFailure() {
        val oversized = folder.newFile().apply { writeBytes(ByteArray(SshConfig.CONFIG_BYTE_LIMIT + 1) { 32 }) }
        assertThrows(IllegalArgumentException::class.java) { SshConfig.read(oversized) }
        val malformed = folder.newFile().apply { writeBytes(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertThrows(CharacterCodingException::class.java) { SshConfig.read(malformed) }
        assertThrows(IOException::class.java) { SshConfig.read(File(folder.root, "missing")) }
        // Exactly the documented byte bound remains usable when valid JSON fits.
        val exact = json() + " ".repeat(SshConfig.CONFIG_BYTE_LIMIT - json().toByteArray().size)
        assertEquals(spec(), read(exact))
    }

    @Test fun configAdmissionRejectsSymlinksAndDirectoriesBeforeReading() {
        val target = folder.newFile().apply { writeText(json()) }
        val link = File(folder.root, "linked-config")
        Files.createSymbolicLink(link.toPath(), target.toPath())
        assertThrows(IllegalArgumentException::class.java) { SshConfig.read(link) }
        assertThrows(IllegalArgumentException::class.java) { SshConfig.read(folder.newFolder()) }
        assertEquals(spec(), SshConfig.read(target))
    }

    @Test fun sentinelBoundsAndSanitizedPrintableFormAreExplicit() {
        listOf(0, 1025).forEach { assertThrows(IllegalArgumentException::class.java) { spec().copy(sentinelLength = it) } }
        listOf("x".repeat(64), "a".repeat(63)).forEach { assertThrows(IllegalArgumentException::class.java) { spec().copy(sentinelSha256 = it) } }
        assertEquals(hash, read(json().replace(hash, hash.uppercase())).sentinelSha256)
        val printed = spec().toString()
        listOf("192.0.2.1", "probe", "/fixture", fingerprint).forEach { assertFalse(printed.contains(it)) }
    }
}
