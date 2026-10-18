package io.github.baitian6641.cloudy.ssh

import net.schmizz.sshj.userauth.password.AccountResource
import org.junit.Assert.*
import org.junit.Test

class SshKeyboardInteractiveCredentialsTest {
    @Test fun exactResponseIsTransferredOnceAndWiped() {
        val user = " user Ω ".toCharArray()
        val response = charArrayOf(' ', '\u0000', 'r', '\ud83d', '\ude00')
        val expectedUser = user.copyOf()
        val expectedResponse = response.copyOf()
        val credentials = SshKeyboardInteractiveCredentials.create(user, response)
        user.fill('x'); response.fill('x')
        val material = checkNotNull(credentials.take())
        assertArrayEquals(expectedUser, material.username)
        assertArrayEquals(expectedResponse, material.response)
        assertTrue(credentials.isClosed)
        assertNull(credentials.take())
        credentials.close(); credentials.close()
        material.close(); material.close()
        assertTrue(material.username.all { it == '\u0000' })
        assertTrue(material.response.all { it == '\u0000' })
        assertEquals("SshKeyboardInteractiveCredentials(redacted)", credentials.toString())
        expectedUser.fill('\u0000'); expectedResponse.fill('\u0000')
    }

    @Test fun limitsAndUnicodeRejectWithFixedMessage() {
        val cases = listOf(
            charArrayOf() to charArrayOf('x'),
            CharArray(257) { 'x' } to charArrayOf('x'),
            charArrayOf('x') to charArrayOf(),
            charArrayOf('x') to CharArray(4097) { 'x' },
            charArrayOf('\ud800') to charArrayOf('x'),
            charArrayOf('x') to charArrayOf('\ud800'),
            charArrayOf('x') to charArrayOf('\udc00'),
        )
        for ((user, response) in cases) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                SshKeyboardInteractiveCredentials.create(user, response)
            }
            assertEquals("INVALID_KEYBOARD_INTERACTIVE_CREDENTIALS", error.message)
            assertNull(error.cause)
            user.fill('\u0000'); response.fill('\u0000')
        }
        SshKeyboardInteractiveCredentials.create(CharArray(256) { 'x' }, CharArray(4096) { 'x' }).close()
    }

    @Test fun responderAnswersOnePromptOnceAndRefusesARepeatedChallenge() {
        val credentials = SshKeyboardInteractiveCredentials.create("user".toCharArray(), "secret".toCharArray())
        val material = checkNotNull(credentials.take())
        val responder = SshOneReleaseKeyboardInteractiveResponder(
            material, SshObservationCancellation()) { true }
        try {
            val resource = AccountResource("unused", "unused.invalid")
            responder.init(resource, "name", "instruction")
            assertTrue(responder.getSubmethods().isEmpty())
            assertArrayEquals("secret".toCharArray(), responder.getResponse("Response: ", false))
            assertThrows(net.schmizz.sshj.userauth.UserAuthException::class.java) {
                responder.getResponse("Response: ", false)
            }
            assertTrue(responder.unsupported.get())
            assertFalse(responder.shouldRetry())
            responder.eraseResponse()
            assertTrue(material.response.all { it == '\u0000' })
        } finally { material.close(); credentials.close() }
    }

    @Test fun cancelledOrInactiveResponderNeverReleasesResponse() {
        val credentials = SshKeyboardInteractiveCredentials.create("user".toCharArray(), "secret".toCharArray())
        val material = checkNotNull(credentials.take())
        val cancellation = SshObservationCancellation().apply { cancel() }
        val responder = SshOneReleaseKeyboardInteractiveResponder(material, cancellation) { false }
        try {
            assertThrows(net.schmizz.sshj.userauth.UserAuthException::class.java) {
                responder.getResponse("Response: ", false)
            }
            assertArrayEquals("secret".toCharArray(), material.response)
        } finally { material.close(); credentials.close() }
    }
}
