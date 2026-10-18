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
 * Opt-in real MINA SSH server. A retained jump authenticates first, opens one loopback
 * direct-tcpip channel, and the target SSH client runs over that channel. This proves the nested
 * transport, exact target host-key pin, and dependent retirement; it is not two distinct hosts.
 */
class SshJumpTransportHostTest {
    private class Fixture(directory: File) {
        private val values = Properties().apply {
            File(directory, "fixture.properties").inputStream().use { load(it) }
        }
        val peer = SshPeerIdentity.create("127.0.0.1", values.getProperty("port").toInt())
        val address: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", values.getProperty("fingerprint"))
        private val user = values.getProperty("user").toCharArray()
        private val password = values.getProperty("password").toCharArray()
        fun credentials(): SshPasswordCredentials =
            SshPasswordCredentials.create(user.copyOf(), password.copyOf())
        fun wipe() { user.fill('\u0000'); password.fill('\u0000') }
    }

    private fun fixture(): Fixture {
        val path = System.getenv("CLOUDY_SSH_KEY_FIXTURE_ROOT")
        assumeTrue("Explicit leader-owned jump fixture only", path != null)
        return Fixture(File(checkNotNull(path)))
    }

    private class Opened {
        val jumpRetired = AtomicInteger()
        val targetRetired = AtomicInteger()
    }

    @Test fun jumpDirectChannelCarriesOneRetainedTargetAndRetiresBothOwners() {
        val fixture = fixture()
        val jumpFactory = SshAuthenticatedTransportFactory()
        val jumpCredential = fixture.credentials()
        val jumpCancellation = SshObservationCancellation()
        val opened = Opened()
        val jump = jumpFactory.connectPassword(fixture.peer, fixture.address, fixture.key, jumpCancellation,
            { _, _ -> jumpCredential }, { proof ->
                proof.peer == fixture.peer && proof.key == fixture.key && !proof.isRetired
            }, { proof -> assertTrue(proof.isRetired); opened.jumpRetired.incrementAndGet() },
            timeoutMillis = 4_000)
        assertTrue("Jump authentication failed: $jump", jump is SshTransportOpenResult.Connected)
        val jumpTransport = (jump as SshTransportOpenResult.Connected).transport
        assertTrue("Jump credential must retire before publication", jumpCredential.isClosed)
        try {
            val direct = jumpTransport.openDirect("127.0.0.1", fixture.peer.port, 5_000)
            assertTrue("Direct channel failed: $direct", direct is SshDirectResult.Opened)
            val targetFactory = SshAuthenticatedTransportFactory()
            val targetCredential = fixture.credentials()
            val target = targetFactory.connectPassword(fixture.peer, fixture.address, fixture.key,
                SshObservationCancellation(),
                { _, _ -> targetCredential }, { proof ->
                    proof.peer == fixture.peer && proof.key == fixture.key && !proof.isRetired
                }, { proof -> assertTrue(proof.isRetired); opened.targetRetired.incrementAndGet() },
                timeoutMillis = 4_000, direct = (direct as SshDirectResult.Opened).connection)
            assertTrue("Target over jump failed: $target", target is SshTransportOpenResult.Connected)
            val targetTransport = (target as SshTransportOpenResult.Connected).transport
            assertTrue("Target credential must retire before publication", targetCredential.isClosed)
            val pty = targetTransport.openPty(SshPtySize(80, 24))
            assertTrue("Target PTY over jump failed: $pty", pty is SshPtyOpenResult.Opened)
            targetTransport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, targetTransport.awaitClosed(3_000))
            assertTrue(targetTransport.ownership.isRetired)
            assertEquals(1, opened.targetRetired.get())
        } finally {
            jumpTransport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, jumpTransport.awaitClosed(3_000))
            assertTrue(jumpTransport.ownership.isRetired)
            assertEquals(1, opened.jumpRetired.get())
            fixture.wipe()
        }
    }

    @Test fun jumpAuthenticationFailureNeverOpensADirectChannel() {
        val fixture = fixture()
        val jumpCredential = fixture.credentials()
        val result = SshAuthenticatedTransportFactory().connectPassword(fixture.peer, fixture.address,
            fixture.key, SshObservationCancellation(), { _, _ -> jumpCredential }, { false },
            { }, timeoutMillis = 4_000)
        assertTrue("A refused establishment must fail: $result", result is SshTransportOpenResult.Failure)
        assertTrue("Pre-publication credentials must be disposed", jumpCredential.isClosed)
        fixture.wipe()
    }

    @Test fun targetFailureRetiresTheDependentChannelAndLeavesOwnedJumpClosable() {
        val fixture = fixture()
        val jumpFactory = SshAuthenticatedTransportFactory()
        val jumpCredential = fixture.credentials()
        val jump = jumpFactory.connectPassword(fixture.peer, fixture.address, fixture.key,
            SshObservationCancellation(), { _, _ -> jumpCredential }, { true }, { },
            timeoutMillis = 4_000)
        assertTrue(jump is SshTransportOpenResult.Connected)
        val jumpTransport = (jump as SshTransportOpenResult.Connected).transport
        try {
            val direct = jumpTransport.openDirect("127.0.0.1", fixture.peer.port, 5_000)
            assertTrue(direct is SshDirectResult.Opened)
            val wrong = SshPasswordCredentials.create("fixture-user".toCharArray(), "wrong-password".toCharArray())
            val target = SshAuthenticatedTransportFactory().connectPassword(fixture.peer, fixture.address,
                fixture.key, SshObservationCancellation(), { _, _ -> wrong }, { true }, { },
                timeoutMillis = 4_000, direct = (direct as SshDirectResult.Opened).connection)
            assertTrue("Wrong target credentials must fail: $target", target is SshTransportOpenResult.Failure)
            assertEquals(SshTransportCode.AUTHENTICATION_FAILED,
                (target as SshTransportOpenResult.Failure).code)
            assertTrue("Rejected target credentials must be disposed", wrong.isClosed)
        } finally {
            jumpTransport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, jumpTransport.awaitClosed(3_000))
            fixture.wipe()
        }
    }
}
