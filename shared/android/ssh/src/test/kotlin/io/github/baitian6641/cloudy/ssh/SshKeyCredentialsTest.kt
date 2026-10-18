package io.github.baitian6641.cloudy.ssh

import java.nio.ByteBuffer
import java.util.Base64
import net.schmizz.sshj.userauth.keyprovider.KeyFormat
import org.junit.Assert.*
import org.junit.Test

class SshKeyCredentialsTest {
    private val armor = "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----\n"

    @Test fun exactCopiesAreTransferredOnceAndWipedIdempotently() {
        val username = " user Ω ".toCharArray()
        val key = armor.toByteArray()
        val passphrase = charArrayOf(' ', '\u0000', 'p', '\ud83d', '\ude00')
        val expectedUser = username.copyOf()
        val expectedKey = key.copyOf()
        val expectedPassphrase = passphrase.copyOf()
        val credentials = SshKeyCredentials.create(username, key, passphrase)
        username.fill('x'); key.fill(0); passphrase.fill('x')
        val material = checkNotNull(credentials.take())
        assertArrayEquals(expectedUser, material.username)
        assertArrayEquals(expectedKey, material.key)
        assertArrayEquals(expectedPassphrase, material.passphrase)
        assertTrue(credentials.isClosed)
        assertNull(credentials.take())
        credentials.close(); credentials.close()
        material.close(); material.close()
        assertTrue(material.username.all { it == '\u0000' })
        assertTrue(material.key.all { it == 0.toByte() })
        assertTrue(checkNotNull(material.passphrase).all { it == '\u0000' })
        assertEquals("SshKeyCredentials(redacted)", credentials.toString())
        expectedUser.fill('\u0000'); expectedKey.fill(0); expectedPassphrase.fill('\u0000')
    }

    @Test fun closedContainerNeverTransfersAndUnencryptedKeyAllowsNoPassphrase() {
        val user = "user".toCharArray()
        val key = armor.toByteArray()
        val value = SshKeyCredentials.create(user, key, null)
        assertEquals("user", value.accountName())
        assertNull(checkNotNull(value.take()).passphrase)
        value.close(); value.close()
        assertNull(value.take())
        assertArrayEquals("user".toCharArray(), user)
        user.fill('\u0000'); key.fill(0)
    }

    @Test fun boundsAndMalformedContentRejectWithFixedMessage() {
        val user = "user".toCharArray()
        val cases = listOf(
            charArrayOf() to armor.toByteArray(),
            CharArray(257) { 'x' } to armor.toByteArray(),
            "user".toCharArray() to ByteArray(0),
            "user".toCharArray() to ByteArray(SshKeyCredentials.MAX_KEY_BYTES + 1) { 'a'.code.toByte() },
            "user".toCharArray() to byteArrayOf('a'.code.toByte(), 0),
            "user".toCharArray() to byteArrayOf(0xc3.toByte(), 0x28),
            charArrayOf('\ud800') to armor.toByteArray(),
            "user".toCharArray() to armor.toByteArray(),
        )
        for ((index, pair) in cases.withIndex()) {
            val passphrase = if (index == cases.lastIndex) CharArray(SshKeyCredentials.MAX_PASSPHRASE_UNITS + 1) { 'p' } else null
            val error = assertThrows(IllegalArgumentException::class.java) {
                SshKeyCredentials.create(pair.first, pair.second, passphrase)
            }
            assertEquals("INVALID_KEY_CREDENTIALS", error.message)
            assertNull(error.cause)
            pair.first.fill('\u0000'); pair.second.fill(0); passphrase?.fill('\u0000')
        }
        SshKeyCredentials.create(user, armor.toByteArray(), CharArray(0)).close()
        user.fill('\u0000')
    }

    @Test fun parserRefusesUnsupportedAndUnreadableKeysWithoutUpstreamText() {
        val user = "user".toCharArray()
        val cancellation = SshObservationCancellation()
        for ((body, expected) in listOf(
            "PuTTY-User-Key-File-2: ssh-rsa\n" to SshKeyFailure.UNSUPPORTED_FORMAT,
            "not a key\n" to SshKeyFailure.UNSUPPORTED_FORMAT,
            armor to SshKeyFailure.UNREADABLE,
        )) {
            val credentials = SshKeyCredentials.create(user, body.toByteArray(), null)
            val material = checkNotNull(credentials.take())
            try {
                val error = assertThrows(SshKeyException::class.java) {
                    SshKeyAuthentication.provider(material, cancellation) { true }
                }
                assertEquals(expected, error.failure)
                assertEquals("KEY_MATERIAL_REJECTED", error.message)
                assertNull(error.cause)
            } finally {
                material.close(); credentials.close()
            }
        }
        user.fill('\u0000')
    }

    @Test fun encryptedOpenSshArmorIsDetectedWithoutDecrypting() {
        val cipher = "aes256-ctr".toByteArray(Charsets.US_ASCII)
        val body = ByteBuffer.allocate(15 + 4 + cipher.size)
            .put("openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII))
            .putInt(cipher.size)
            .put(cipher)
            .array()
        val armored = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(body) +
            "\n-----END OPENSSH PRIVATE KEY-----\n"
        assertTrue(SshKeyAuthentication.encrypted(armored, KeyFormat.OpenSSHv1))
        val plain = ByteBuffer.allocate(15 + 4 + 4)
            .put("openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII))
            .putInt(4).put("none".toByteArray(Charsets.US_ASCII)).array()
        val plainArmored = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            Base64.getEncoder().encodeToString(plain) + "\n-----END OPENSSH PRIVATE KEY-----\n"
        assertFalse(SshKeyAuthentication.encrypted(plainArmored, KeyFormat.OpenSSHv1))
        assertTrue(SshKeyAuthentication.encrypted(
            "-----BEGIN ENCRYPTED PRIVATE KEY-----\n", KeyFormat.PKCS8))
    }
}
