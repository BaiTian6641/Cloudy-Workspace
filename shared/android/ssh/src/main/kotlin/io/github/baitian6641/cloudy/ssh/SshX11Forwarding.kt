package io.github.baitian6641.cloudy.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.Channel
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.forwarded.ConnectListener
import net.schmizz.sshj.connection.channel.forwarded.X11Forwarder
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Creates an SSHJ client with the reviewed narrow algorithm policy and no quiet-channel timeout. */
object SshX11ClientFactory {
    @JvmStatic fun create(): SSHClient = SSHClient(SshObservationPolicy.create(SshReaderRetirement(), idleReadTimeoutMillis = 0))
}

/** Bidirectional blocking streams to the local X server, with one explicit close. */
class SshX11Duplex(
    val input: InputStream,
    val output: OutputStream,
    private val closer: () -> Unit,
) {
    fun close() = closer()
}

/** A local X server connection factory. Implementations connect; they never authenticate. */
interface SshX11ServerEndpoint {
    fun open(): SshX11Duplex
}

/** UNIX-socket endpoint (Linux host /tmp/.X11-unix/X<n>; Android adapters may substitute). */
class SshX11UnixEndpoint(private val socketPath: Path) : SshX11ServerEndpoint {
    override fun open(): SshX11Duplex {
        val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            channel.connect(UnixDomainSocketAddress.of(socketPath))
        } catch (error: IOException) {
            runCatching { channel.close() }
            throw error
        }
        return SshX11Duplex(Channels.newInputStream(channel), Channels.newOutputStream(channel)) {
            runCatching { channel.close() }
        }
    }

    override fun toString(): String = "SshX11UnixEndpoint(redacted)"
}

/** TCP endpoint for the conventional localhost:6000+n listener when one is in scope. */
class SshX11TcpEndpoint(private val host: String, private val display: Int) : SshX11ServerEndpoint {
    override fun open(): SshX11Duplex {
        val channel = SocketChannel.open()
        try {
            channel.connect(InetSocketAddress(host, 6000 + display))
        } catch (error: IOException) {
            runCatching { channel.close() }
            throw error
        }
        return SshX11Duplex(Channels.newInputStream(channel), Channels.newOutputStream(channel)) {
            runCatching { channel.close() }
        }
    }

    override fun toString(): String = "SshX11TcpEndpoint(redacted)"
}

/** Supplies the real cookie for the bound local display; null means no valid authorization. */
fun interface SshX11AuthorityProvider {
    fun realCookie(): ByteArray?
}

/**
 * One owner's X11 forwarding over one authenticated SSH connection (RFC 4254 §6.3): a random
 * fake cookie is sent with x11-req, incoming `x11` channels present it, and each accepted channel
 * is substituted with the real local cookie before splicing to the local server. Wrong-cookie,
 * non-MIT-protocol, oversized and timed-out attempts close without reaching the server.
 */
class SshX11Forwarding private constructor(
    private val forwarder: X11Forwarder,
    private val endpoint: SshX11ServerEndpoint,
    private val authority: SshX11AuthorityProvider,
    private val workers: ExecutorService,
    private val watchdog: ScheduledExecutorService,
    private val maxChannels: Int,
    private val handshakeTimeoutMillis: Long,
    private val onError: (SshX11Code, String?) -> Unit,
) {
    /** Issued per owner, sent to the server, replaced on return; never valid anywhere else. */
    val fakeCookie: ByteArray = SshX11Cookie.generate()
    private val active = AtomicInteger(0)
    private val stopped = AtomicBoolean(false)

    /**
     * Requests X11 forwarding on an open session channel before shell/exec start. The server
     * responds by publishing DISPLAY plus this owner's fake cookie to the remote session.
     */
    fun enableOn(session: Session, screen: Int = 0) {
        if (stopped.get()) throw SshX11Exception(SshX11Code.CLOSED)
        if (screen !in 0..65535) throw SshX11Exception(SshX11Code.INVALID_INPUT)
        session.reqX11Forwarding(SshXauthorityCodec.MIT_MAGIC_COOKIE_1, SshX11Cookie.hex(fakeCookie), screen)
    }

    fun activeChannels(): Int = active.get()

    /** Stops accepting new channels and closes active ones; the SSH connection itself is left. */
    fun stop() {
        if (stopped.compareAndSet(false, true)) {
            forwarder.stop()
            watchdog.shutdownNow()
            workers.shutdownNow()
        }
    }

    /**
     * Never throws and never rejects: sshj 0.41.2's CHANNEL_OPEN_FAILURE packet omits the RFC
     * 4254 §5.1 language field, and OpenSSH answers that malformed reject by disconnecting the
     * whole transport. Every denial is therefore a protocol-legal confirm-then-close.
     */
    private val listener = ConnectListener { channel ->
        val admitted = if (stopped.get()) {
            false
        } else {
            val count = active.incrementAndGet()
            // Over-limit channels release their slot immediately and take the deny path.
            if (count > maxChannels) { active.decrementAndGet(); false } else true
        }
        val serve: () -> Unit = serve@{
            try {
                if (admitted) serveChannel(channel) else closeUnwanted(channel)
            } catch (error: Throwable) {
                runCatching { onError(if (error is SshX11Exception) error.code else SshX11Code.CHANNEL_FAILED, error.message) }
                // Every failure closes the channel without reaching the local server.
            } finally {
                if (admitted) active.decrementAndGet()
                runCatching { channel.close() }
            }
        }
        try {
            workers.execute(serve)
        } catch (_: RejectedExecutionException) {
            if (admitted) active.decrementAndGet()
            runCatching { closeUnwanted(channel) }
        }
    }

    /** Bounded deny: confirm (legal on arrival) then close; never the malformed reject. */
    private fun closeUnwanted(channel: Channel.Forwarded) {
        runCatching { channel.confirm() }
        runCatching { channel.close() }
    }

    /**
     * sshj's forwarded channels deliver no bytes before confirmation, so the order is fixed:
     * resolve authority and connect the local server first (failures reject the channel without
     * a server connection), confirm, then read/substitute the setup block and splice to EOF.
     */
    private fun serveChannel(channel: Channel.Forwarded) {
        // Any pre-confirm failure still confirms first, then closes: sshj cannot emit a valid
        // reject against strict servers (see the listener note). The local server is contacted
        // only after authority resolution succeeds.
        val realCookie = try {
            authority.realCookie() ?: throw SshX11Exception(SshX11Code.COOKIE_NOT_FOUND)
        } catch (error: Throwable) {
            closeUnwanted(channel)
            throw error
        }
        val local = try {
            endpoint.open()
        } catch (error: IOException) {
            closeUnwanted(channel)
            throw SshX11Exception(SshX11Code.CONNECT_FAILED, error.message)
        }
        try {
            channel.confirm()
        } catch (error: Throwable) {
            runCatching { local.close() }
            runCatching { channel.close() }
            throw error
        }
        val remoteIn = channel.inputStream
        val remoteOut = channel.outputStream
        // A channel that never completes its setup block is closed by the watchdog; reads on
        // sshj streams can otherwise park indefinitely with no data.
        val expiry = watchdog.schedule({ runCatching { channel.close() } },
            handshakeTimeoutMillis, TimeUnit.MILLISECONDS)
        val rewritten = try {
            val header = readFully(remoteIn, SshX11Handshake.HEADER_BYTES)
            val block = readFully(remoteIn, SshX11Handshake.paddedLength(header) - SshX11Handshake.HEADER_BYTES, header)
            SshX11Handshake.substituteCookie(block, fakeCookie, realCookie)
        } catch (error: Throwable) {
            runCatching { local.close() }
            throw error
        } finally {
            expiry.cancel(false)
        }
        try {
            local.output.write(rewritten)
            local.output.flush()
            val uplink = Thread({ pump(local.input, remoteOut) }, "cloudy-x11-up").apply {
                isDaemon = true
                start()
            }
            try {
                pump(remoteIn, local.output)
            } finally {
                // Either direction ending retires the pair; the remote client sees a close.
                runCatching { local.close() }
                uplink.join(PUMP_JOIN_MILLIS)
            }
        } finally {
            runCatching { local.close() }
        }
    }

    private fun pump(source: InputStream, destination: OutputStream) {
        val buffer = ByteArray(PUMP_BUFFER_BYTES)
        try {
            while (true) {
                val read = source.read(buffer)
                if (read < 0) return
                if (read == 0) continue
                destination.write(buffer, 0, read)
                destination.flush()
            }
        } catch (_: IOException) {
            // EOF/reset both retire the splice; the caller closes the pair.
        }
    }

    private fun readFully(input: InputStream, length: Int, prefix: ByteArray? = null): ByteArray {
        val result = ByteArray((prefix?.size ?: 0) + length)
        prefix?.let { System.arraycopy(it, 0, result, 0, it.size) }
        var offset = prefix?.size ?: 0
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(handshakeTimeoutMillis)
        while (offset < result.size) {
            if (System.nanoTime() > deadline) throw SshX11Exception(SshX11Code.TIMED_OUT)
            val read = try {
                input.read(result, offset, result.size - offset)
            } catch (error: IOException) {
                throw SshX11Exception(SshX11Code.CHANNEL_FAILED, error.message)
            }
            if (read < 0) throw SshX11Exception(SshX11Code.CHANNEL_FAILED, "eof")
            offset += read
        }
        return result
    }

    companion object {
        private const val PUMP_BUFFER_BYTES = 16 * 1024
        private const val PUMP_JOIN_MILLIS = 5_000L

        /**
         * Registers this owner as the connection's single X11 listener (SSHJ supports one) and
         * returns the bound handle. No request is sent; [enableOn] arms a specific session.
         */
        fun attach(
            client: SSHClient,
            endpoint: SshX11ServerEndpoint,
            authority: SshX11AuthorityProvider,
            maxChannels: Int = 16,
            handshakeTimeoutMillis: Long = 10_000,
            onError: (SshX11Code, String?) -> Unit = { _, _ -> },
        ): SshX11Forwarding {
            if (maxChannels !in 1..64) throw SshX11Exception(SshX11Code.INVALID_INPUT, "channels")
            if (handshakeTimeoutMillis !in 1_000..30_000) {
                throw SshX11Exception(SshX11Code.INVALID_INPUT, "timeout")
            }
            val workers = Executors.newCachedThreadPool { action ->
                Thread(action, "cloudy-x11-serve").apply { isDaemon = true }
            }
            val watchdog = Executors.newScheduledThreadPool(1) { action ->
                Thread(action, "cloudy-x11-watchdog").apply { isDaemon = true }
            }
            lateinit var owner: SshX11Forwarding
            val forwarder = client.registerX11Forwarder(ConnectListener { channel ->
                owner.listener.gotConnect(channel)
            })
            owner = SshX11Forwarding(forwarder, endpoint, authority, workers, watchdog,
                maxChannels, handshakeTimeoutMillis, onError)
            return owner
        }
    }
}
