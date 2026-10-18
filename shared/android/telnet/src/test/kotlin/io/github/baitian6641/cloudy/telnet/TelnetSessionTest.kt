package io.github.baitian6641.cloudy.telnet

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class TelnetSessionTest {
    private fun acceptNegotiation(socket: Socket): ByteArray {
        val sent = ByteArrayOutputStream()
        socket.soTimeout = 5_000
        val output = socket.getOutputStream()
        output.write(byteArrayOf(255.toByte(), 253.toByte(), 24)) // IAC DO TTYPE
        output.write(byteArrayOf(255.toByte(), 250.toByte(), 24, 1, 255.toByte(), 240.toByte())) // SB TTYPE SEND
        output.write(byteArrayOf(255.toByte(), 253.toByte(), 31)) // IAC DO NAWS
        output.write(byteArrayOf(255.toByte(), 251.toByte(), 1)) // IAC WILL ECHO
        output.flush()
        val input = socket.getInputStream()
        val buffer = ByteArray(512)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val count = try { input.read(buffer) } catch (_: java.net.SocketTimeoutException) { break }
            if (count <= 0) break
            sent.write(buffer, 0, count)
            val text = String(sent.toByteArray(), Charsets.US_ASCII)
            if (text.contains("xterm-256color") && sent.toByteArray().hasNaws()) break
        }
        return sent.toByteArray()
    }

    private fun ByteArray.hasNaws(): Boolean {
        for (i in 0..size - 7) {
            if ((this[i].toInt() and 255) == 255 && (this[i + 1].toInt() and 255) == 250 &&
                (this[i + 2].toInt() and 255) == 31) return true
        }
        return false
    }

    @Test fun negotiatesTypeAndSizeDeliversDataAndEscapesInput() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val ended = CountDownLatch(1)
        var failure: Throwable? = null
        val serverThread = Thread({
            try {
                server.accept().use { socket ->
                    val negotiation = acceptNegotiation(socket)
                    val text = String(negotiation, Charsets.US_ASCII)
                    assertTrue("TTYPE missing: $text", text.contains("xterm-256color"))
                    assertTrue("NAWS missing", negotiation.hasNaws())
                    socket.getOutputStream().apply {
                        write("login: ".toByteArray())
                        write(byteArrayOf(255.toByte(), 255.toByte()))
                        write("ok\r\n".toByteArray())
                        flush()
                    }
                    val input = socket.getInputStream()
                    val received = ByteArrayOutputStream()
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (System.nanoTime() < deadline && !String(received.toByteArray(), Charsets.US_ASCII).contains("user\r\n")) {
                        val byte = input.read()
                        if (byte < 0) break
                        received.write(byte)
                    }
                    assertTrue("input missing CRLF: ${String(received.toByteArray(), Charsets.US_ASCII)}",
                        String(received.toByteArray(), Charsets.US_ASCII).contains("user\r\n"))
                }
            } catch (error: Throwable) {
                failure = error
            } finally { runCatching { server.close() } }
        }, "telnet-test-server").apply { isDaemon = true; start() }

        val output = ByteArrayOutputStream()
        val opened = TelnetSession.open(InetAddress.getByName("127.0.0.1"), server.localPort,
            TelnetSize(80, 24), object : TelnetSession.Listener {
                override fun onOutput(bytes: ByteArray) { synchronized(output) { output.write(bytes) } }
                override fun onEnded(code: String) { ended.countDown() }
            })
        assertTrue("open: $opened", opened is TelnetOpenResult.Opened)
        val session = (opened as TelnetOpenResult.Opened).session
        session.start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                val current = synchronized(output) { output.toByteArray() }
                if (current.size >= 10 && current.any { (it.toInt() and 255) == 255 }) break
                Thread.sleep(20)
            }
            val bytes = synchronized(output) { output.toByteArray() }
            assertTrue("data output: ${bytes.size}", String(bytes, Charsets.ISO_8859_1).contains("login: "))
            assertTrue("IAC escape missing", bytes.any { (it.toInt() and 255) == 255 })
            assertEquals(TelnetAdmission.ACCEPTED,
                session.offerInput("user\r".toByteArray(Charsets.US_ASCII)))
            assertEquals(TelnetAdmission.ACCEPTED, session.offerResize(TelnetSize(120, 40)))
            serverThread.join(3_000)
            assertNull("server failure: $failure", failure)
        } finally {
            session.requestClose()
            assertTrue(session.awaitClosed(3_000))
            assertTrue(ended.await(3_000, TimeUnit.MILLISECONDS))
        }
    }

    @Test fun invalidBoundsAndPostCloseInputAreRefused() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val acceptor = Thread({ server.accept().use { it.soTimeout = 3_000; Thread.sleep(500) } },
            "telnet-idle-server").apply { isDaemon = true; start() }
        val opened = TelnetSession.open(InetAddress.getByName("127.0.0.1"), server.localPort,
            TelnetSize(80, 24), object : TelnetSession.Listener {
                override fun onOutput(bytes: ByteArray) {}
                override fun onEnded(code: String) {}
            })
        assertTrue(opened is TelnetOpenResult.Opened)
        val session = (opened as TelnetOpenResult.Opened).session
        session.start()
        assertEquals(TelnetAdmission.STALE, session.offerResize(TelnetSize(0, 24)))
        assertEquals(TelnetAdmission.STALE, session.offerInput(ByteArray(0)))
        session.requestClose()
        assertTrue(session.awaitClosed(3_000))
        assertEquals(TelnetAdmission.STALE, session.offerInput(byteArrayOf(65)))
        runCatching { server.close() }
        acceptor.join(3_000)
    }
}
