package io.github.baitian6641.cloudy.telnet

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Telnet is plaintext; this engine performs no authentication, local echo or credential storage. */
class TelnetSize(val columns: Int, val rows: Int) {
    override fun toString() = "TelnetSize($columns,$rows)"
}

enum class TelnetAdmission { ACCEPTED, FULL, STALE }

sealed interface TelnetOpenResult {
    class Opened(val session: TelnetSession) : TelnetOpenResult
    class Failure(val code: String) : TelnetOpenResult
}

/**
 * One TCP-bound Telnet NVT endpoint with bounded input queueing. Server output is delivered
 * synchronously to the listener, so slow rendering applies socket backpressure rather than
 * silently dropping bytes. Supported options: TTYPE, NAWS, SGA, BINARY and server ECHO.
 */
class TelnetSession private constructor(
    private val socket: Socket,
    private val listener: Listener,
    initial: TelnetSize,
) {
    interface Listener {
        fun onOutput(bytes: ByteArray)
        fun onEnded(code: String)
    }

    private val started = AtomicBoolean(false)
    private val input = ArrayBlockingQueue<ByteArray>(MAX_INPUT_ENTRIES)
    private val queued = AtomicInteger()
    private val closed = AtomicBoolean(false)
    private val ended = AtomicBoolean(false)
    private val latestSize = AtomicReference(initial)
    private val resizePending = AtomicBoolean(false)
    @Volatile private var nawsAgreed = false
    private val out = socket.getOutputStream()
    private val outputLock = Any()
    private val reader = Thread({ readLoop() }, "cloudy-telnet-reader").apply { isDaemon = true }
    private val writer = Thread({ writeLoop() }, "cloudy-telnet-writer").apply { isDaemon = true }

    init {
        socket.tcpNoDelay = true
        negotiateInitial(initial)
    }

    /** Starts socket readers only after the renderer owner has admitted the initial geometry. */
    fun start() {
        if (closed.get() || !started.compareAndSet(false, true)) return
        reader.start()
        writer.start()
    }

    fun offerInput(bytes: ByteArray): TelnetAdmission {
        if (closed.get()) return TelnetAdmission.STALE
        if (bytes.isEmpty() || bytes.size > MAX_ACTION_BYTES) return TelnetAdmission.STALE
        val copy = bytes.copyOf()
        synchronized(queued) {
            if (queued.get() + copy.size > MAX_QUEUED_BYTES || !input.offer(copy)) {
                copy.fill(0)
                return TelnetAdmission.FULL
            }
            queued.addAndGet(copy.size)
        }
        return TelnetAdmission.ACCEPTED
    }

    fun offerResize(size: TelnetSize): TelnetAdmission {
        if (closed.get()) return TelnetAdmission.STALE
        if (size.columns !in 1..MAX_DIMENSION || size.rows !in 1..MAX_DIMENSION) return TelnetAdmission.STALE
        latestSize.set(size)
        resizePending.set(true)
        return TelnetAdmission.ACCEPTED
    }

    fun requestClose() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        input.forEach { it.fill(0) }
        input.clear()
        queued.set(0)
        reader.interrupt()
        writer.interrupt()
        if (!started.get()) finish("CLOSED")
    }

    fun awaitClosed(timeoutMillis: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        for (thread in listOf(reader, writer)) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) break
            runCatching { thread.join(TimeUnit.NANOSECONDS.toMillis(remaining)) }
        }
        return ended.get() && !reader.isAlive && !writer.isAlive
    }

    private fun readLoop() {
        var code = "CLOSED"
        try {
            val input = socket.getInputStream()
            val buffer = ByteArray(8_192)
            val parser = Parser()
            while (!closed.get()) {
                val count = input.read(buffer)
                if (count == -1) break
                if (count <= 0) continue
                val output = parser.consume(buffer, count)
                if (output.isNotEmpty()) {
                    listener.onOutput(output)
                    java.util.Arrays.fill(output, 0)
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: IOException) {
            if (!closed.get()) code = "IO_FAILED"
        } finally {
            if (!closed.get()) requestClose()
            finish(code)
        }
    }

    private fun finish(code: String) {
        if (!ended.compareAndSet(false, true)) return
        closed.set(true)
        input.forEach { it.fill(0) }
        input.clear(); queued.set(0)
        runCatching { socket.close() }
        listener.onEnded(code)
    }

    private fun writeLoop() {
        try {
            while (!closed.get()) {
                val chunk = input.poll(200, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    synchronized(queued) { queued.addAndGet(-chunk.size) }
                    try {
                        writeData(chunk)
                    } finally { chunk.fill(0) }
                }
                if (resizePending.getAndSet(false) && nawsAgreed) sendNaws(latestSize.get())
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: IOException) {
            requestClose()
        }
    }

    private fun writeData(bytes: ByteArray) {
        synchronized(outputLock) {
            var index = 0
            while (index < bytes.size) {
                val value = bytes[index].toInt() and 255
                when {
                    value == IAC -> { out.write(IAC); out.write(IAC); index++ }
                    value == CR -> {
                        out.write(CR); out.write(LF)
                        index += if (index + 1 < bytes.size && (bytes[index + 1].toInt() and 255) == LF) 2 else 1
                    }
                    else -> { out.write(value); index++ }
                }
            }
            out.flush()
        }
    }

    private fun negotiateInitial(size: TelnetSize) {
        synchronized(outputLock) {
            for (option in intArrayOf(OPT_TTYPE, OPT_NAWS, OPT_SGA, OPT_BINARY)) {
                out.write(IAC); out.write(WILL); out.write(option)
            }
            for (option in intArrayOf(OPT_SGA, OPT_BINARY)) {
                out.write(IAC); out.write(DO); out.write(option)
            }
            out.write(IAC); out.write(WONT); out.write(OPT_LINEMODE)
            out.flush()
        }
    }

    private fun onDo(option: Int) {
        when (option) {
            OPT_TTYPE, OPT_NAWS, OPT_SGA, OPT_BINARY -> {
                synchronized(outputLock) {
                    out.write(IAC); out.write(WILL); out.write(option); out.flush()
                }
                if (option == OPT_NAWS) {
                    nawsAgreed = true
                    sendNaws(latestSize.get())
                }
            }
            else -> reply(WONT, option)
        }
    }

    private fun onDont(option: Int) {
        reply(WONT, option)
        if (option == OPT_NAWS) nawsAgreed = false
    }

    private fun onWill(option: Int) {
        when (option) {
            OPT_ECHO, OPT_SGA, OPT_BINARY -> reply(DO, option)
            else -> reply(DONT, option)
        }
    }

    private fun onWont(option: Int) { reply(DONT, option) }

    private fun reply(command: Int, option: Int) {
        synchronized(outputLock) {
            out.write(IAC); out.write(command); out.write(option); out.flush()
        }
    }

    private fun sendNaws(size: TelnetSize) {
        val columns = size.columns.coerceIn(1, MAX_DIMENSION)
        val rows = size.rows.coerceIn(1, MAX_DIMENSION)
        synchronized(outputLock) {
            out.write(IAC); out.write(SB); out.write(OPT_NAWS)
            out.write((columns ushr 8) and 255); out.write(columns and 255)
            out.write((rows ushr 8) and 255); out.write(rows and 255)
            out.write(IAC); out.write(SE); out.flush()
        }
    }

    private fun sendTerminalType() {
        val type = "xterm-256color".toByteArray(Charsets.US_ASCII)
        synchronized(outputLock) {
            out.write(IAC); out.write(SB); out.write(OPT_TTYPE); out.write(0) // IS
            out.write(type)
            out.write(IAC); out.write(SE); out.flush()
        }
    }

    private inner class Parser {
        private val output = ByteArrayOutputStream()
        private val sub = ByteArrayOutputStream()
        private var state = NORMAL

        fun consume(bytes: ByteArray, count: Int): ByteArray {
            for (index in 0 until count) {
                val value = bytes[index].toInt() and 255
                when (state) {
                    NORMAL -> if (value == IAC) state = IAC_STATE else output.write(value)
                    IAC_STATE -> when (value) {
                        IAC -> { output.write(0xFF); state = NORMAL }
                        DO -> state = DO_OPTION
                        DONT -> state = DONT_OPTION
                        WILL -> state = WILL_OPTION
                        WONT -> state = WONT_OPTION
                        SB -> { sub.reset(); state = SUBNEG }
                        else -> state = NORMAL
                    }
                    DO_OPTION -> { onDo(value); state = NORMAL }
                    DONT_OPTION -> { onDont(value); state = NORMAL }
                    WILL_OPTION -> { onWill(value); state = NORMAL }
                    WONT_OPTION -> { onWont(value); state = NORMAL }
                    SUBNEG -> if (value == IAC) state = SUBNEG_IAC else sub.write(value)
                    SUBNEG_IAC -> when (value) {
                        IAC -> { sub.write(0xFF); state = SUBNEG }
                        SE -> { subnegotiation(sub.toByteArray()); state = NORMAL }
                        else -> state = SUBNEG
                    }
                }
            }
            val result = output.toByteArray()
            output.reset()
            return result
        }

        private fun subnegotiation(data: ByteArray) {
            if (data.size < 2) return
            val option = data[0].toInt() and 255
            if (option == OPT_TTYPE && (data[1].toInt() and 255) == 1) sendTerminalType()
        }
    }

    companion object {
        const val MAX_ACTION_BYTES = 65_536
        const val MAX_QUEUED_BYTES = 65_536
        const val MAX_INPUT_ENTRIES = 64
        private const val MAX_DIMENSION = 65_535
        private const val IAC = 255
        private const val DO = 253
        private const val DONT = 254
        private const val WILL = 251
        private const val WONT = 252
        private const val SB = 250
        private const val SE = 240
        private const val NORMAL = 0
        private const val IAC_STATE = 1
        private const val DO_OPTION = 2
        private const val DONT_OPTION = 3
        private const val WILL_OPTION = 4
        private const val WONT_OPTION = 5
        private const val SUBNEG = 6
        private const val SUBNEG_IAC = 7
        private const val CR = 13
        private const val LF = 10
        private const val OPT_BINARY = 0
        private const val OPT_ECHO = 1
        private const val OPT_SGA = 3
        private const val OPT_TTYPE = 24
        private const val OPT_NAWS = 31
        private const val OPT_LINEMODE = 34
        fun open(address: InetAddress, port: Int, initial: TelnetSize, listener: Listener,
                 connectTimeoutMillis: Int = 10_000): TelnetOpenResult {
            if (port !in 1..65535 || initial.columns !in 1..MAX_DIMENSION || initial.rows !in 1..MAX_DIMENSION) {
                return TelnetOpenResult.Failure("INVALID_INPUT")
            }
            val socket = Socket()
            return try {
                socket.connect(java.net.InetSocketAddress(address, port), connectTimeoutMillis)
                TelnetOpenResult.Opened(TelnetSession(socket, listener, initial))
            } catch (_: java.net.SocketTimeoutException) {
                runCatching { socket.close() }
                TelnetOpenResult.Failure("TIMED_OUT")
            } catch (_: Exception) {
                runCatching { socket.close() }
                TelnetOpenResult.Failure("CONNECT_FAILED")
            }
        }
    }
}
