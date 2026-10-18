package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.File
import java.net.InetAddress
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in real MINA SSH server with one keyboard-interactive prompt. The fixture password is the
 * accepted response; another response must be refused without password public-key fallback.
 */
class SshKeyboardInteractiveHostTest {
    private class Fixture(directory: File) {
        private val values = Properties().apply {
            File(directory, "fixture.properties").inputStream().use { load(it) }
        }
        val peer = SshPeerIdentity.create("127.0.0.1", values.getProperty("port").toInt())
        val address: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", values.getProperty("fingerprint"))
        private val username = values.getProperty("user").toCharArray()
        private val response = values.getProperty("password").toCharArray()
        fun credentials(): SshKeyboardInteractiveCredentials =
            SshKeyboardInteractiveCredentials.create(username.copyOf(), response.copyOf())
        fun wrongCredentials(): SshKeyboardInteractiveCredentials =
            SshKeyboardInteractiveCredentials.create(username.copyOf(), "wrong-response".toCharArray())
        fun promptedCredentials(): SshKeyboardInteractiveCredentials =
            SshKeyboardInteractiveCredentials.createPrompted(username.copyOf())
        fun promptedResponse(): CharArray = response.copyOf()
        fun wipe() { username.fill('\u0000'); response.fill('\u0000') }
    }

    private fun fixture(): Fixture {
        val path = System.getenv("CLOUDY_SSH_KEY_FIXTURE_ROOT")
        assumeTrue("Explicit leader-owned interactive fixture only", path != null)
        return Fixture(File(checkNotNull(path)))
    }

    private fun authenticate(fixture: Fixture, credentials: SshKeyboardInteractiveCredentials,
                             cancellation: SshObservationCancellation = SshObservationCancellation(),
                             prompts: SshInteractivePromptHandler? = null) =
        SshAuthenticationCheck().authenticate(fixture.peer, fixture.address, fixture.key, cancellation,
            admit = { peer, key ->
                assertTrue("Exact fresh route/key required", peer == fixture.peer && key == fixture.key)
                credentials
            }, timeoutMillis = 4_000, prompts = prompts)

    @Test fun acceptedResponseAuthenticatesAndRetainsThenRetires() {
        val fixture = fixture()
        val factory = SshAuthenticatedTransportFactory()
        val cancellation = SshObservationCancellation()
        val admitted = AtomicInteger()
        val retired = AtomicInteger()
        val credential = fixture.credentials()
        val opened = factory.connectKeyboardInteractive(fixture.peer, fixture.address, fixture.key, cancellation,
            { peer, key ->
                assertTrue(peer == fixture.peer && key == fixture.key)
                admitted.incrementAndGet()
                credential
            }, { proof ->
                assertTrue(proof.peer == fixture.peer && proof.key == fixture.key &&
                    proof.cancellation === cancellation && !proof.isRetired)
                true
            }, { proof ->
                assertTrue(proof.isRetired)
                retired.incrementAndGet()
            }, timeoutMillis = 4_000)
        if (opened !is SshTransportOpenResult.Connected) {
            credential.close()
            fail("Actual keyboard-interactive authentication failed: $opened")
        }
        assertTrue("Response must retire before live publication", credential.isClosed)
        assertEquals(1, admitted.get())
        val transport = (opened as SshTransportOpenResult.Connected).transport
        transport.requestClose()
        assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
        assertTrue(transport.ownership.isRetired)
        assertEquals(1, retired.get())
        fixture.wipe()
    }

    @Test fun wrongResponseIsRefusedWithoutMethodFallback() {
        val fixture = fixture()
        val credential = fixture.wrongCredentials()
        try {
            val result = authenticate(fixture, credential)
            assertTrue("Wrong response must fail: $result", result is SshAuthenticationResult.Failure)
            assertEquals(SshAuthenticationCode.AUTHENTICATION_FAILED,
                (result as SshAuthenticationResult.Failure).code)
            assertTrue("Admitted response must be disposed", credential.isClosed)
        } finally { credential.close(); fixture.wipe() }
    }

    @Test fun promptedCredentialAnswersTheRealServerPrompt() {
        val fixture = fixture()
        val credential = fixture.promptedCredentials()
        var asked = false
        try {
            val result = authenticate(fixture, credential, prompts = SshInteractivePromptHandler { prompt, echo ->
                asked = true
                assertTrue("Server prompt text required", prompt.isNotEmpty())
                assertFalse("The fixture password prompt is not echoed", echo)
                fixture.promptedResponse()
            })
            assertTrue("Prompted authentication must succeed: $result", result is SshAuthenticationResult.Success)
            assertTrue("The server prompt must be delivered", asked)
            assertTrue("Prompted credentials must retire after auth", credential.isClosed)
        } finally { credential.close(); fixture.wipe() }
    }

    @Test fun preCancelledAttemptNeverAdmitsResponse() {
        val fixture = fixture()
        val credential = fixture.credentials()
        val cancellation = SshObservationCancellation().apply { cancel() }
        var admitted = false
        try {
            val result = SshAuthenticationCheck().authenticate(
                fixture.peer, fixture.address, fixture.key, cancellation,
                admit = { _, _ -> admitted = true; credential }, timeoutMillis = 4_000)
            assertTrue(result is SshAuthenticationResult.Failure)
            assertEquals(SshAuthenticationCode.CANCELLED,
                (result as SshAuthenticationResult.Failure).code)
            assertFalse("A cancelled attempt must not admit", admitted)
            assertFalse("Unused caller-owned response stays caller-owned", credential.isClosed)
        } finally { credential.close(); fixture.wipe() }
    }
}
