package io.github.baitian6641.cloudy.ssh

import org.junit.Assert.*
import org.junit.Test

class SshPasswordCredentialsTest {
    @Test fun exactCopiesAreTransferredOnceAndWipedIdempotently() {
        val username = " user Ω ".toCharArray()
        val password = charArrayOf(' ', '\u0000', '\n', '\t', '\ud83d', '\ude00', ' ')
        val expectedUser = username.copyOf()
        val expectedPassword = password.copyOf()
        val credentials = SshPasswordCredentials.create(username, password)
        username.fill('x'); password.fill('x')
        val material = checkNotNull(credentials.take())
        assertArrayEquals(expectedUser, material.username)
        assertArrayEquals(expectedPassword, material.password)
        assertTrue(credentials.isClosed)
        assertNull(credentials.take())
        credentials.close(); credentials.close()
        assertArrayEquals(expectedPassword, material.password)
        material.close(); material.close()
        assertTrue(material.username.all { it == '\u0000' } && material.password.all { it == '\u0000' })
        assertEquals("SshPasswordCredentials(redacted)", credentials.toString())
        expectedUser.fill('\u0000'); expectedPassword.fill('\u0000')
    }

    @Test fun closedContainerNeverTransfersAndCallerInputsAreNotWiped() {
        val user = "user".toCharArray(); val password = "secret".toCharArray()
        val value = SshPasswordCredentials.create(user, password)
        assertFalse(value.isClosed)
        value.close(); value.close()
        assertTrue(value.isClosed)
        assertNull(value.take())
        assertArrayEquals("user".toCharArray(), user)
        assertArrayEquals("secret".toCharArray(), password)
        user.fill('\u0000'); password.fill('\u0000')
    }

    @Test fun limitsAndUnicodeRejectWithFixedMessageWithoutCause() {
        val cases = listOf(
            charArrayOf() to charArrayOf('x'),
            CharArray(257) { 'x' } to charArrayOf('x'),
            charArrayOf('x') to charArrayOf(),
            charArrayOf('x') to CharArray(4097) { 'x' },
            charArrayOf('x', '\n') to charArrayOf('x'),
            charArrayOf('\ud800') to charArrayOf('x'),
            charArrayOf('x') to charArrayOf('\ud800'),
            charArrayOf('x') to charArrayOf('\udc00'),
        )
        for ((user, password) in cases) {
            val error = assertThrows(IllegalArgumentException::class.java) { SshPasswordCredentials.create(user, password) }
            assertEquals("INVALID_CREDENTIALS", error.message)
            assertNull(error.cause)
            user.fill('\u0000'); password.fill('\u0000')
        }
        SshPasswordCredentials.create(CharArray(256) { 'x' }, CharArray(4096) { '\u0000' }).close()
    }

    @Test fun accountSnapshotComesFromOwnedCopyAndDisappearsAfterTransferOrClose() {
        val input = "  User-e\u0301 \uD801\uDC00  ".toCharArray()
        val expected = input.concatToString()
        val holder = SshPasswordCredentials.create(input, "unrelated-password".toCharArray())
        input.fill('x')
        assertEquals(expected, holder.accountName())
        assertNotEquals(expected.trim(), holder.accountName())
        val snapshot = holder.accountName()
        val material = checkNotNull(holder.take())
        assertNull(holder.accountName())
        material.close()
        assertEquals(expected, snapshot)
        holder.close()
        assertNull(holder.accountName())
        val closed = SshPasswordCredentials.create("exact".toCharArray(), "other".toCharArray())
        closed.close()
        assertNull(closed.accountName())
        assertEquals("SshPasswordCredentials(redacted)", closed.toString())
    }

}
