package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real SSH direct-tcpip and loopback forwarding against the maintained MINA fixture. */
class SshLocalForwardHostTest {
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
        assumeTrue("Explicit leader-owned forwarding fixture only", path != null)
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

    private class EchoServer {
        private val server = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort
        val accepted = AtomicInteger()
        private val running = java.util.concurrent.atomic.AtomicBoolean(true)
        private val thread = Thread({
            while (running.get()) {
                val socket = try { server.accept() } catch (_: IOException) { break }
                accepted.incrementAndGet()
                Thread({
                    socket.use {
                        val input = it.getInputStream()
                        val output = it.getOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count); output.flush()
                        }
                    }
                }, "echo-connection").apply { isDaemon = true }.start()
            }
        }, "echo-accept").apply { isDaemon = true; start() }
        fun close() { running.set(false); runCatching { server.close() }; thread.interrupt() }
    }

    private fun roundTrip(port: Int, text: String): String {
        Socket().use { socket ->
            socket.soTimeout = 5_000
            socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 3_000)
            val bytes = text.toByteArray()
            socket.getOutputStream().apply { write(bytes); flush() }
            val response = ByteArray(bytes.size)
            var offset = 0
            while (offset < response.size) {
                val count = socket.getInputStream().read(response, offset, response.size - offset)
                if (count <= 0) break
                offset += count
            }
            return String(response, 0, offset, Charsets.UTF_8)
        }
    }

    @Test fun forwardsTwoConcurrentClientsAndClosesTheLoopbackListener() {
        val fixture = fixture()
        val transport = open(fixture)
        val echo = EchoServer()
        val snapshots = AtomicReference<SshLocalForward.Snapshot?>()
        val forward = try {
            val started = SshLocalForward.start(transport, 0, "127.0.0.1", echo.port,
                object : SshLocalForward.Listener {
                    override fun onChanged(snapshot: SshLocalForward.Snapshot) { snapshots.set(snapshot) }
                })
            assertTrue("start: $started", started is SshLocalForwardStartResult.Started)
            (started as SshLocalForwardStartResult.Started).forward
        } catch (error: Throwable) {
            echo.close(); transport.requestClose(); fixture.wipe(); throw error
        }
        try {
            assertTrue(forward.localPort in 1..65535)
            assertEquals("alpha", roundTrip(forward.localPort, "alpha"))
            assertEquals("gamma", roundTrip(forward.localPort, "gamma"))
            assertEquals(2, echo.accepted.get())
            assertTrue(transport.files.stat("/") is SshFilesResult.Success)
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3)
            while (System.nanoTime() < deadline && (snapshots.get()?.activeConnections ?: 0) > 0) Thread.sleep(20)
            assertEquals(0, snapshots.get()?.activeConnections)
        } finally {
            forward.close()
            echo.close()
        }
        val port = forward.localPort
        val closed = try {
            Socket().use { it.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 500); false }
        } catch (_: IOException) { true }
        assertTrue("local listener still accepts after close", closed)
        assertTrue(transport.files.stat("/") is SshFilesResult.Success)
        transport.requestClose()
        assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
        fixture.wipe()
    }

    @Test fun transportRetirementClosesAnActiveForwardConnection() {
        val fixture = fixture()
        val transport = open(fixture)
        val echo = EchoServer()
        val forward = (SshLocalForward.start(transport, 0, "127.0.0.1", echo.port,
            object : SshLocalForward.Listener {
                override fun onChanged(snapshot: SshLocalForward.Snapshot) {}
            }) as SshLocalForwardStartResult.Started).forward
        val client = try {
            Socket().apply {
                soTimeout = 5_000
                connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), forward.localPort), 3_000)
                getOutputStream().write("held".toByteArray()); getOutputStream().flush()
                val response = ByteArray(4)
                var read = 0
                while (read < response.size) {
                    val count = getInputStream().read(response, read, response.size - read)
                    if (count <= 0) break
                    read += count
                }
                assertEquals("held", String(response, 0, read, Charsets.UTF_8))
            }
        } catch (error: Throwable) {
            forward.close(); echo.close(); transport.requestClose(); fixture.wipe(); throw error
        }
        try {
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(5_000))
            val ended = try { client.getInputStream().read(); -1 } catch (_: IOException) { -1 }
            assertEquals(-1, ended)
        } finally {
            runCatching { client.close() }
            forward.close(); echo.close(); fixture.wipe()
        }
    }

    @Test fun ptyAndActiveForwardRetireTogether() {
        val fixture = fixture()
        val transport = open(fixture)
        val echo = EchoServer()
        val pty = transport.openPty(SshPtySize(80, 24))
        assertTrue("pty: $pty", pty is SshPtyOpenResult.Opened)
        val forward = (SshLocalForward.start(transport, 0, "127.0.0.1", echo.port,
            object : SshLocalForward.Listener {
                override fun onChanged(snapshot: SshLocalForward.Snapshot) {}
            }) as SshLocalForwardStartResult.Started).forward
        try {
            assertEquals("pty-forward", roundTrip(forward.localPort, "pty-forward"))
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(5_000))
        } finally {
            forward.close(); echo.close(); transport.requestClose(); fixture.wipe()
        }
    }

    @Test fun rejectsBoundPortAndInvalidDestinationWithoutStoppingTransport() {
        val fixture = fixture()
        val transport = open(fixture)
        val occupied = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        try {
            val collision = SshLocalForward.start(transport, occupied.localPort, "127.0.0.1", 9,
                object : SshLocalForward.Listener {
                    override fun onChanged(snapshot: SshLocalForward.Snapshot) {}
                })
            assertEquals("ADDRESS_IN_USE", (collision as SshLocalForwardStartResult.Failure).code)
            assertEquals("INVALID_INPUT", (SshLocalForward.start(transport, 0, "bad host", 9,
                object : SshLocalForward.Listener {
                    override fun onChanged(snapshot: SshLocalForward.Snapshot) {}
                }) as SshLocalForwardStartResult.Failure).code)
            assertTrue(transport.files.stat("/") is SshFilesResult.Success)
        } finally {
            runCatching { occupied.close() }
            transport.requestClose()
            assertEquals(SshTransportCloseResult.Retired, transport.awaitClosed(3_000))
            fixture.wipe()
        }
    }
}
