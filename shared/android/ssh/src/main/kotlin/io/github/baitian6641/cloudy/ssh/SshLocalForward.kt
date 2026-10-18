package io.github.baitian6641.cloudy.ssh

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

sealed interface SshLocalForwardStartResult {
    class Started(val forward: SshLocalForward) : SshLocalForwardStartResult
    class Failure(val code: String) : SshLocalForwardStartResult
}

/**
 * One loopback TCP listener whose accepted connections open direct-tcpip streams through the
 * retained SSH transport. It binds only 127.0.0.1, never accepts remote listeners, bounds active
 * connections and threads, and closes its listener plus all streams on [close] or transport loss.
 */
class SshLocalForward private constructor(
    private val transport: SshAuthenticatedTransport,
    private val server: ServerSocket,
    val localPort: Int,
    val destinationHost: String,
    val destinationPort: Int,
    private val listener: Listener,
) : AutoCloseable {
    interface Listener { fun onChanged(snapshot: Snapshot) }

    class Snapshot internal constructor(
        val running: Boolean, val localPort: Int, val destinationHost: String, val destinationPort: Int,
        val activeConnections: Int, val totalConnections: Long, val lastError: String?,
    ) { override fun toString() = "SshLocalForwardSnapshot(redacted)" }

    private val running = AtomicBoolean(true)
    private val activeConnections = AtomicInteger(0)
    private val totalConnections = AtomicLong(0)
    private val lastError = AtomicReference<String?>(null)
    private val connections: MutableSet<Connection> =
        Collections.newSetFromMap(ConcurrentHashMap<Connection, Boolean>())
    private val acceptThread = Thread({ acceptLoop() }, "cloudy-forward-accept").apply { isDaemon = true }

    init { acceptThread.start() }

    fun snapshot() = Snapshot(running.get() && !server.isClosed, localPort, destinationHost, destinationPort,
        activeConnections.get(), totalConnections.get(), lastError.get())

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { server.close() }
        for (connection in connections.toList()) connection.close()
        connections.clear()
        acceptThread.interrupt()
        runCatching { acceptThread.join(1_000) }
        notifyChanged()
    }

    private fun acceptLoop() {
        while (running.get() && !server.isClosed) {
            val socket = try { server.accept() } catch (_: IOException) { break } catch (_: SecurityException) { break }
            if (activeConnections.get() >= MAX_CONNECTIONS) {
                runCatching { socket.close() }
                lastError.set("CONNECTION_LIMIT")
                notifyChanged()
                continue
            }
            activeConnections.incrementAndGet()
            totalConnections.incrementAndGet()
            val connection = Connection(socket)
            connections.add(connection)
            connection.start()
            notifyChanged()
        }
    }

    private fun finished(connection: Connection, error: String?) {
        if (!connections.remove(connection)) return
        if (error != null) lastError.set(error)
        activeConnections.updateAndGet { value -> if (value > 0) value - 1 else 0 }
        notifyChanged()
    }

    private fun notifyChanged() {
        runCatching { listener.onChanged(snapshot()) }
    }

    private inner class Connection(private val local: Socket) {
        private val closed = AtomicBoolean(false)
        @Volatile private var channel: SshForwardChannel? = null
        private val setup = Thread({
            val opened = transport.openForward(destinationHost, destinationPort)
            if (closed.get()) {
                (opened as? SshForwardOpenResult.Opened)?.channel?.close()
                finished(this, null)
                return@Thread
            }
            when (opened) {
                is SshForwardOpenResult.Failure -> {
                    runCatching { local.close() }
                    finished(this, opened.code.name)
                }
                is SshForwardOpenResult.Opened -> {
                    channel = opened.channel
                    startPumps()
                }
            }
        }, "cloudy-forward-setup").apply { isDaemon = true }

        fun start() { setup.start() }

        private fun startPumps() {
            val owned = channel ?: return
            Thread({ pump(local.getInputStream(), owned.output) }, "cloudy-forward-up").apply { isDaemon = true }.start()
            Thread({ pump(owned.input, local.getOutputStream()) }, "cloudy-forward-down").apply { isDaemon = true }.start()
        }

        private fun pump(input: java.io.InputStream, output: java.io.OutputStream) {
            val buffer = ByteArray(32_768)
            var error: String? = null
            try {
                while (!closed.get() && running.get()) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (count <= 0) continue
                    output.write(buffer, 0, count)
                    output.flush()
                }
            } catch (_: IOException) {
                if (!closed.get() && running.get()) error = "STREAM_FAILED"
            } finally {
                buffer.fill(0)
                close(error)
            }
        }

        fun close(error: String? = null) {
            if (!closed.compareAndSet(false, true)) return
            runCatching { local.close() }
            channel?.close()
            finished(this, error)
        }
    }

    companion object {
        const val MAX_CONNECTIONS = 16

        fun start(transport: SshAuthenticatedTransport, localPort: Int, destinationHost: String,
                  destinationPort: Int, listener: Listener): SshLocalForwardStartResult {
            if (localPort !in 0..65535 || destinationPort !in 1..65535 ||
                destinationHost.isEmpty() || destinationHost.length > 255 ||
                destinationHost.any { Character.isISOControl(it) || it.isWhitespace() }) {
                return SshLocalForwardStartResult.Failure("INVALID_INPUT")
            }
            if (!transport.isActive()) return SshLocalForwardStartResult.Failure("UNAVAILABLE")
            val server = ServerSocket()
            return try {
                server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), localPort), 16)
                val forward = SshLocalForward(transport, server, server.localPort, destinationHost,
                    destinationPort, listener)
                listener.onChanged(forward.snapshot())
                SshLocalForwardStartResult.Started(forward)
            } catch (_: java.net.BindException) {
                runCatching { server.close() }
                SshLocalForwardStartResult.Failure("ADDRESS_IN_USE")
            } catch (_: Exception) {
                runCatching { server.close() }
                SshLocalForwardStartResult.Failure("UNAVAILABLE")
            }
        }
    }
}
