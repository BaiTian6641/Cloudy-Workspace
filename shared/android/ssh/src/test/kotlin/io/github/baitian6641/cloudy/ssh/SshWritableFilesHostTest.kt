package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.File
import java.net.InetAddress
import java.util.Properties
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in real MINA SFTP virtual filesystem. Exercises exclusive create, bounded writes, explicit
 * rename overwrite, typed not-found/already-exists failures and recoverable transport survival.
 * Not a transfer journal, staging, publication-consent or Googlebook result.
 */
class SshWritableFilesHostTest {
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
        assumeTrue("Explicit leader-owned SFTP fixture only", path != null)
        return Fixture(File(checkNotNull(path)))
    }

    private fun open(fixture: Fixture): SshAuthenticatedTransport {
        val credential = fixture.credentials()
        val opened = SshAuthenticatedTransportFactory().connectPassword(
            fixture.peer, fixture.address, fixture.key, SshObservationCancellation(),
            { _, _ -> credential }, { true }, { }, timeoutMillis = 4_000)
        assertTrue("SFTP transport failed: $opened", opened is SshTransportOpenResult.Connected)
        assertTrue("Credential must retire before publication", credential.isClosed)
        return (opened as SshTransportOpenResult.Connected).transport
    }

    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)
    private fun read(files: SshFiles, path: String) =
        (files.read(path) as SshFilesResult.Success).value.copyBytes()

    @Test fun exclusiveCreateRenameAndTypedFailuresKeepTheTransportUsable() {
        val fixture = fixture()
        val transport = open(fixture)
        val files = transport.files
        try {
            val listing = files.list("/")
            assertTrue("list: $listing", listing is SshFilesResult.Success)
            assertEquals(SshTransportCode.NOT_FOUND,
                (files.stat("/cloudy-write") as SshFilesResult.Failure).code)
            val created = files.mkdir("/cloudy-write")
            assertTrue("mkdir: $created", created is SshFilesResult.Success)
            assertEquals(SshTransportCode.ALREADY_EXISTS,
                (files.mkdir("/cloudy-write") as SshFilesResult.Failure).code)
            assertEquals(SshTransportCode.NOT_FOUND,
                (files.removeFile("/cloudy-write/missing") as SshFilesResult.Failure).code)
            assertEquals(SshTransportCode.NOT_FOUND,
                (files.stat("/cloudy-write/missing") as SshFilesResult.Failure).code)
            // Recoverable failures must not retire the retained transport.
            assertTrue(files.stat("/") is SshFilesResult.Success)

            assertTrue(files.writeNew("/cloudy-write/a.txt", bytes("alpha")) is SshFilesResult.Success)
            assertArrayEquals(bytes("alpha"), read(files, "/cloudy-write/a.txt"))
            assertEquals(SshTransportCode.ALREADY_EXISTS,
                (files.writeNew("/cloudy-write/a.txt", bytes("replacement")) as SshFilesResult.Failure).code)
            assertArrayEquals(bytes("alpha"), read(files, "/cloudy-write/a.txt"))
            val stat = (files.stat("/cloudy-write/a.txt") as SshFilesResult.Success).value
            assertEquals(SshFileKind.REGULAR, stat.kind)
            assertEquals(5L, stat.size)

            assertTrue(files.rename("/cloudy-write/a.txt", "/cloudy-write/b.txt", overwrite = false) is
                SshFilesResult.Success)
            assertArrayEquals(bytes("alpha"), read(files, "/cloudy-write/b.txt"))
            assertTrue(files.writeNew("/cloudy-write/c.txt", bytes("charlie")) is SshFilesResult.Success)
            assertEquals(SshTransportCode.ALREADY_EXISTS,
                (files.rename("/cloudy-write/b.txt", "/cloudy-write/c.txt", overwrite = false) as
                    SshFilesResult.Failure).code)
            assertArrayEquals(bytes("charlie"), read(files, "/cloudy-write/c.txt"))
            assertTrue(files.rename("/cloudy-write/b.txt", "/cloudy-write/c.txt", overwrite = true) is
                SshFilesResult.Success)
            assertArrayEquals(bytes("alpha"), read(files, "/cloudy-write/c.txt"))

            assertEquals(SshTransportCode.OPERATION_FAILED,
                (files.removeEmptyDirectory("/cloudy-write") as SshFilesResult.Failure).code)
            assertEquals(SshTransportCode.INVALID_INPUT,
                (files.removeFile("/cloudy-write") as SshFilesResult.Failure).code)
            assertTrue(files.removeFile("/cloudy-write/c.txt") is SshFilesResult.Success)
            assertTrue(files.removeEmptyDirectory("/cloudy-write") is SshFilesResult.Success)
            assertTrue(files.stat("/") is SshFilesResult.Success)
        } finally {
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
            fixture.wipe()
        }
    }

    @Test fun boundsRejectEmptyOversizeAndInvalidPathsWithoutMutation() {
        val fixture = fixture()
        val transport = open(fixture)
        val files = transport.files
        try {
            assertTrue(files.writeNew("/empty-write-check", ByteArray(0)) is SshFilesResult.Success)
            assertEquals(0L, (files.stat("/empty-write-check") as SshFilesResult.Success).value.size)
            assertTrue(files.removeFile("/empty-write-check") is SshFilesResult.Success)
            assertEquals(SshTransportCode.INVALID_INPUT,
                (files.writeNew("/x", ByteArray(SshFiles.MAX_WRITE_BYTES + 1)) as SshFilesResult.Failure).code)
            assertEquals(SshTransportCode.INVALID_INPUT,
                (files.writeNew("/bad\npath", bytes("x")) as SshFilesResult.Failure).code)
            assertEquals(SshTransportCode.INVALID_INPUT,
                (files.mkdir("") as SshFilesResult.Failure).code)
            assertTrue(files.stat("/") is SshFilesResult.Success)
        } finally {
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
            fixture.wipe()
        }
    }
}
