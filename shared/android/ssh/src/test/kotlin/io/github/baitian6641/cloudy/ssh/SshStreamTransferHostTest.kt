package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real MINA SFTP streaming, digest and cancellation behavior. */
class SshStreamTransferHostTest {
    private class Fixture(directory: File) {
        private val values = Properties().apply {
            File(directory, "fixture.properties").inputStream().use { load(it) }
        }
        val peer = SshPeerIdentity.create("127.0.0.1", values.getProperty("port").toInt())
        val address: InetAddress = InetAddress.getByName("127.0.0.1")
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", values.getProperty("fingerprint"))
        private val user = values.getProperty("user").toCharArray()
        private val password = values.getProperty("password").toCharArray()
        fun credentials() = SshPasswordCredentials.create(user.copyOf(), password.copyOf())
        fun wipe() { user.fill('\u0000'); password.fill('\u0000') }
    }

    private fun fixture(): Fixture {
        val path = System.getenv("CLOUDY_SSH_KEY_FIXTURE_ROOT")
        assumeTrue("Explicit leader-owned streaming fixture only", path != null)
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

    private fun bytes(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }
    private fun hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun streamUploadDigestDownloadAndExclusiveRefusal() {
        val fixture = fixture()
        val transport = open(fixture)
        val files = transport.files
        val path = "/stream-${UUID.randomUUID().toString().take(8)}.bin"
        val content = bytes(200_000)
        try {
            assertEquals(content.size.toLong(),
                (files.writeFrom(path, ByteArrayInputStream(content)) as SshFilesResult.Success).value)
            assertEquals(hex(content), (files.digest(path) as SshFilesResult.Success).value.sha256Hex())
            assertEquals(SshTransportCode.ALREADY_EXISTS,
                (files.writeFrom(path, ByteArrayInputStream(ByteArray(1))) as SshFilesResult.Failure).code)
            val sink = ByteArrayOutputStream()
            assertEquals(content.size.toLong(), (files.readTo(path, sink) as SshFilesResult.Success).value)
            assertArrayEquals(content, sink.toByteArray())
            assertTrue(files.stat("/") is SshFilesResult.Success)
        } finally {
            runCatching { files.removeFile(path) }
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
            fixture.wipe()
        }
    }

    @Test fun limitCancellationAndLocalSinkFailureKeepTheTransportAlive() {
        val fixture = fixture()
        val transport = open(fixture)
        val files = transport.files
        val path = "/limit-${UUID.randomUUID().toString().take(8)}.bin"
        val cancelled = SshTransferCancellation().apply { cancel() }
        try {
            assertEquals(SshTransportCode.LIMIT_EXCEEDED,
                (files.writeFrom(path, ByteArrayInputStream(ByteArray(101)), maxBytes = 100) as
                    SshFilesResult.Failure).code)
            assertEquals(SshTransportCode.NOT_FOUND, (files.stat(path) as SshFilesResult.Failure).code)
            assertEquals(SshTransportCode.CANCELLED,
                (files.writeFrom(path, ByteArrayInputStream(ByteArray(10)), cancellation = cancelled) as
                    SshFilesResult.Failure).code)
            assertEquals(SshTransportCode.NOT_FOUND, (files.stat(path) as SshFilesResult.Failure).code)
            val content = bytes(1_000)
            assertTrue(files.writeFrom(path, ByteArrayInputStream(content)) is SshFilesResult.Success)
            assertEquals(SshTransportCode.CANCELLED,
                (files.readTo(path, ByteArrayOutputStream(), cancellation = cancelled) as SshFilesResult.Failure).code)
            val failing = object : OutputStream() {
                override fun write(b: Int) { throw IOException("local sink") }
                override fun write(b: ByteArray, off: Int, len: Int) { throw IOException("local sink") }
            }
            assertEquals(SshTransportCode.OPERATION_FAILED,
                (files.readTo(path, failing) as SshFilesResult.Failure).code)
            assertTrue(files.stat("/") is SshFilesResult.Success)
            assertEquals(hex(content), (files.digest(path) as SshFilesResult.Success).value.sha256Hex())
        } finally {
            runCatching { files.removeFile(path) }
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
            fixture.wipe()
        }
    }
}
