package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.File
import java.net.InetAddress
import java.util.Properties
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in real MINA SFTP staged-edit two-phase behavior. Host-side writes to the fixture's private
 * virtual root simulate a concurrent editor. Not a product journal, consent or crash-recovery test.
 */
class SshStagedEditHostTest {
    private class Fixture(val root: File) {
        private val values = Properties().apply {
            File(root, "fixture.properties").inputStream().use { load(it) }
        }
        val peer = SshPeerIdentity.create("127.0.0.1", values.getProperty("port").toInt())
        val address: InetAddress = InetAddress.getByName("127.0.0.1")
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", values.getProperty("fingerprint"))
        private val user = values.getProperty("user").toCharArray()
        private val password = values.getProperty("password").toCharArray()
        fun credentials() = SshPasswordCredentials.create(user.copyOf(), password.copyOf())
        fun hostFile(path: String) = File(File(root, "sftp-root"), path.trimStart('/'))
        fun wipe() { user.fill('\u0000'); password.fill('\u0000') }
    }

    private fun fixture(): Fixture {
        val path = System.getenv("CLOUDY_SSH_KEY_FIXTURE_ROOT")
        assumeTrue("Explicit leader-owned staged-edit fixture only", path != null)
        return Fixture(File(checkNotNull(path)))
    }

    private fun open(fixture: Fixture): SshAuthenticatedTransport {
        val credential = fixture.credentials()
        val opened = SshAuthenticatedTransportFactory().connectPassword(
            fixture.peer, fixture.address, fixture.key, SshObservationCancellation(),
            { _, _ -> credential }, { true }, { }, timeoutMillis = 4_000)
        assertTrue("transport: $opened", opened is SshTransportOpenResult.Connected)
        return (opened as SshTransportOpenResult.Connected).transport
    }

    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)
    private fun read(files: SshFiles, path: String) =
        (files.read(path) as SshFilesResult.Success).value.copyBytes()

    @Test fun stageCommitConflictExplicitReplaceAndCleanup() {
        val fixture = fixture()
        val transport = open(fixture)
        val files = transport.files
        val editor = SshFileEditor(files)
        val path = "/edit-${UUID.randomUUID().toString().take(8)}.txt"
        try {
            assertTrue(files.writeNew(path, bytes("one")) is SshFilesResult.Success)
            val first = (editor.open(path) as SshFilesResult.Success).value
            assertArrayEquals(bytes("one"), first.bytes.copyBytes())
            val staged = (editor.stage(first, bytes("edited")) as SshFilesResult.Success).value
            assertArrayEquals(bytes("one"), read(files, path)) // staging never touches target
            assertTrue(editor.commit(staged, replaceChanged = false) is SshFileEditor.CommitResult.Committed)
            assertArrayEquals(bytes("edited"), read(files, path))

            val second = (editor.open(path) as SshFilesResult.Success).value
            val pending = (editor.stage(second, bytes("pending")) as SshFilesResult.Success).value
            // Concurrent external edit after staging: 10 bytes replaces 6, so size detects it.
            fixture.hostFile(path).writeBytes(bytes("concurrent"))
            val conflict = editor.commit(pending, replaceChanged = false)
            assertTrue("conflict: $conflict", conflict is SshFileEditor.CommitResult.Conflict)
            assertEquals(10L, (conflict as SshFileEditor.CommitResult.Conflict).current.size)
            assertArrayEquals(bytes("concurrent"), read(files, path))
            assertTrue(files.stat(pending.tempPath) is SshFilesResult.Success)
            assertTrue(editor.commit(pending, replaceChanged = true) is SshFileEditor.CommitResult.Committed)
            assertArrayEquals(bytes("pending"), read(files, path))
            assertEquals(SshTransportCode.NOT_FOUND,
                (files.stat(pending.tempPath) as SshFilesResult.Failure).code)

            val third = (editor.open(path) as SshFilesResult.Success).value
            val discarded = (editor.stage(third, bytes("discarded")) as SshFilesResult.Success).value
            assertTrue(editor.discard(discarded) is SshFilesResult.Success)
            assertEquals(SshTransportCode.NOT_FOUND,
                (files.stat(discarded.tempPath) as SshFilesResult.Failure).code)
            assertArrayEquals(bytes("pending"), read(files, path))
        } finally {
            runCatching { files.removeFile(path) }
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
            fixture.wipe()
        }
    }

    @Test fun emptyEditAndBounds() {
        val fixture = fixture()
        val transport = open(fixture)
        val files = transport.files
        val editor = SshFileEditor(files)
        val path = "/empty-${UUID.randomUUID().toString().take(8)}.txt"
        try {
            assertTrue(files.writeNew(path, bytes("text")) is SshFilesResult.Success)
            val document = (editor.open(path) as SshFilesResult.Success).value
            val staged = (editor.stage(document, ByteArray(0)) as SshFilesResult.Success).value
            assertEquals(0, staged.byteCount)
            assertTrue(editor.commit(staged, replaceChanged = true) is SshFileEditor.CommitResult.Committed)
            assertArrayEquals(ByteArray(0), read(files, path))
            assertEquals(SshTransportCode.INVALID_INPUT,
                (editor.stage(document, ByteArray(SshFileEditor.MAX_EDIT_BYTES + 1)) as
                    SshFilesResult.Failure).code)
        } finally {
            runCatching { files.removeFile(path) }
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
            fixture.wipe()
        }
    }
}
