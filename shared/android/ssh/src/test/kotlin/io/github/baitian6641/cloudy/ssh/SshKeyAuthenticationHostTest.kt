package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in real MINA SSH server. One generated OpenSSH client key is accepted; a second generated,
 * unregistered key must be refused without a password/keyboard-interactive fallback. Fixture
 * material stays private.
 */
class SshKeyAuthenticationHostTest {
    private class Fixture(directory: File) {
        private val values = Properties().apply {
            File(directory, "fixture.properties").inputStream().use { load(it) }
        }
        private val keyBytes = Files.readAllBytes(File(directory, "client_key").toPath())
        private val wrongKeyBytes = Files.readAllBytes(File(directory, "wrong_key").toPath())
        val peer = SshPeerIdentity.create("127.0.0.1", values.getProperty("port").toInt())
        val address: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", values.getProperty("fingerprint"))
        val username: CharArray = values.getProperty("user").toCharArray()
        fun credentials(): SshKeyCredentials =
            SshKeyCredentials.create(username.copyOf(), keyBytes.copyOf(), null)
        fun wrongCredentials(): SshKeyCredentials =
            SshKeyCredentials.create(username.copyOf(), wrongKeyBytes.copyOf(), null)
        init {
            require(keyBytes.size in 1..SshKeyCredentials.MAX_KEY_BYTES) { "FIXTURE_KEY_BOUND" }
        }
    }

    private fun fixture(): Fixture {
        val path = System.getenv("CLOUDY_SSH_KEY_FIXTURE_ROOT")
        assumeTrue("Explicit leader-owned key fixture only", path != null)
        return Fixture(File(checkNotNull(path)))
    }

    private fun authenticate(fixture: Fixture, credentials: SshKeyCredentials,
                             cancellation: SshObservationCancellation = SshObservationCancellation()) =
        SshAuthenticationCheck().authenticate(fixture.peer, fixture.address, fixture.key, cancellation,
            admit = { peer, key ->
                assertTrue("Exact fresh route/key required", peer == fixture.peer && key == fixture.key)
                credentials
            }, timeoutMillis = 4_000)

    @Test fun acceptedKeyAuthenticatesAndRetainsThenRetires() {
        val fixture = fixture()
        val factory = SshAuthenticatedTransportFactory()
        val cancellation = SshObservationCancellation()
        val admitted = AtomicInteger()
        val retired = AtomicInteger()
        val credential = fixture.credentials()
        val opened = factory.connectPublicKey(fixture.peer, fixture.address, fixture.key, cancellation,
            { peer, key ->
                assertTrue("Exact fresh route/key required", peer == fixture.peer && key == fixture.key)
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
            fail("Actual key authentication failed: $opened")
        }
        assertTrue("Key material must retire before live publication", credential.isClosed)
        assertEquals(1, admitted.get())
        val transport = (opened as SshTransportOpenResult.Connected).transport
        transport.requestClose()
        assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
        assertTrue(transport.ownership.isRetired)
        assertEquals(1, retired.get())
    }

    @Test fun wrongKeyIsRefusedWithoutPasswordFallback() {
        val fixture = fixture()
        val credential = fixture.wrongCredentials()
        try {
            val result = authenticate(fixture, credential)
            assertTrue("Wrong key must fail: $result", result is SshAuthenticationResult.Failure)
            assertEquals(SshAuthenticationCode.AUTHENTICATION_FAILED, (result as SshAuthenticationResult.Failure).code)
            assertTrue("Admitted key must be disposed", credential.isClosed)
        } finally { credential.close(); fixture.username.fill('\u0000') }
    }

    @Test fun preCancelledAttemptNeverAdmitsKey() {
        val fixture = fixture()
        val credential = fixture.credentials()
        val cancellation = SshObservationCancellation().apply { cancel() }
        var admitted = false
        try {
            val result = SshAuthenticationCheck().authenticate(
                fixture.peer, fixture.address, fixture.key, cancellation,
                admit = { _, _ -> admitted = true; credential }, timeoutMillis = 4_000)
            assertTrue(result is SshAuthenticationResult.Failure)
            assertEquals(SshAuthenticationCode.CANCELLED, (result as SshAuthenticationResult.Failure).code)
            assertFalse("A cancelled attempt must not admit", admitted)
            assertFalse("Unused caller-owned key stays caller-owned", credential.isClosed)
        } finally { credential.close(); fixture.username.fill('\u0000') }
    }
}
