package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real POSIX SSH server with PTY+shell+SFTP; existing MINA files fixture has no shell. */
class SshPtyTransportHostTest {
    @Test fun realPtyResizeAndFilesShareOneAuthenticatedOwner() {
        val root = System.getenv("CLOUDY_SSH_PTY_FIXTURE_ROOT")
        assumeTrue("Explicit leader-owned real SSH fixture required", root != null)
        val file = File(checkNotNull(root), "fixture.properties")
        require(file.length() in 1..8_192)
        val p = Properties().apply { file.inputStream().use { load(it) } }
        val peer = SshPeerIdentity.create("127.0.0.1", p.getProperty("port").toInt())
        val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", p.getProperty("fingerprint"))
        val user = p.getProperty("user").toCharArray()
        val password = p.getProperty("password").toCharArray()
        val credential = try { SshPasswordCredentials.create(user, password) }
            finally { user.fill('\u0000'); password.fill('\u0000') }
        val admissions = AtomicInteger()
        val retirements = AtomicInteger()
        val opened = SshAuthenticatedTransportFactory().connectPassword(peer, address, key,
            SshObservationCancellation(), { exactPeer, exactKey ->
                assertTrue("EXACT_AUTH_BINDING", exactPeer == peer && exactKey == key)
                admissions.incrementAndGet()
                credential
            }, { proof -> proof.peer == peer && proof.key == key }, { retirements.incrementAndGet() })
        assertTrue("REAL_AUTHENTICATION_REQUIRED", opened is SshTransportOpenResult.Connected)
        val transport = (opened as SshTransportOpenResult.Connected).transport
        try {
            val result = transport.openPty(SshPtySize(80, 24))
            assertTrue("REAL_PTY_REQUIRED", result is SshPtyOpenResult.Opened)
            val pty = (result as SshPtyOpenResult.Opened).pty
            assertTrue("SECOND_PTY_REFUSED", transport.openPty(SshPtySize(80, 24)) is SshPtyOpenResult.Failure)
            assertTrue("SAME_CONNECTION_LIST_REQUIRED", transport.files.list(".") is SshFilesResult.Success)
            assertTrue("SAME_CONNECTION_READ_REQUIRED", transport.files.read("proof.txt") is SshFilesResult.Success)
            assertEquals(1, admissions.get())
            assertEquals(SshPtyAdmission.ACCEPTED, pty.offerResize(SshPtySize(123, 41)))
            // Remote marker does not occur literally in echoed command text.
            val command = "stty -echo; stty size; printf '\\103\\114\\117\\125\\104\\131\\137\\120\\124\\131\\137\\117\\113\\012'\n"
            assertEquals(SshPtyAdmission.ACCEPTED, pty.offerInput(command.toByteArray(Charsets.US_ASCII)))
            val bytes = ByteArrayOutputStream()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var observed = false
            while (System.nanoTime() < end && !observed) {
                when (val chunk = pty.read(100)) {
                    is SshPtyReadResult.Output -> {
                        assertTrue("BOUNDED_REMOTE_OUTPUT", bytes.size() + chunk.bytes.size <= 65_536)
                        bytes.write(chunk.bytes)
                        val text = bytes.toString("US-ASCII")
                        observed = text.contains("41 123") && text.contains("CLOUDY_PTY_OK")
                    }
                    is SshPtyReadResult.End, is SshPtyReadResult.Failure -> fail("UNEXPECTED_PTY_END")
                    SshPtyReadResult.Idle -> Unit
                }
            }
            assertTrue("REMOTE_RESIZE_AND_INPUT_REQUIRED", observed)
            pty.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed())
            assertEquals(1, retirements.get())
            assertTrue(transport.ownership.isRetired)
            assertEquals(SshPtyAdmission.STOPPED, pty.offerInput(byteArrayOf(1)))
        } finally {
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed())
        }
    }
}
