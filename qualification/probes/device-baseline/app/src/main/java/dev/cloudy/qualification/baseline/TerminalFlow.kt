package dev.cloudy.qualification.baseline

import java.io.IOException
import java.util.ArrayDeque

internal class TerminalFlowClosed(val reason: String) : IOException(reason)

/** Credits include queued and posted bytes. Only an exact, ordered parser ACK returns them. */
internal class TerminalFlow(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    internal data class Chunk(val bytes: ByteArray, val isStderr: Boolean, val queuedAt: Long)
    private data class Pending(val sequence: Int, val length: Int, val sentAt: Long)
    private val monitor = Object()
    private val queued = ArrayDeque<Chunk>()
    private val pending = ArrayDeque<Pending>()
    private var outstanding = 0
    private var pressure = false
    private var closedReason: String? = null

    /** Worker-only blocking admission; the caller posts a single coalesced UI drain afterwards. */
    fun put(bytes: ByteArray, isStderr: Boolean) = synchronized(monitor) {
        require(bytes.size in 1..TerminalLimits.OUTPUT_CHUNK_BYTES)
        while (true) {
            checkOpen()
            checkDeadline()
            if (pressure && outstanding <= TerminalLimits.OUTPUT_LOW_WATER_BYTES) pressure = false
            if (!pressure && outstanding + bytes.size <= TerminalLimits.OUTPUT_HIGH_WATER_BYTES &&
                queued.size + pending.size < MAX_FRAMES) break
            pressure = true
            monitor.wait(250)
        }
        queued.addLast(Chunk(bytes.copyOf(), isStderr, nowMillis()))
        outstanding += bytes.size
    }

    /** UI owner claims before posting, so even a prompt reply can never over-credit the queue. */
    fun claim(sequence: Int): Chunk? = synchronized(monitor) {
        checkOpen()
        checkDeadline()
        require(sequence > 0 && (pending.peekLast()?.sequence ?: 0) < sequence)
        val chunk = queued.pollFirst() ?: return@synchronized null
        pending.addLast(Pending(sequence, chunk.bytes.size, nowMillis()))
        chunk
    }

    fun acknowledge(sequence: Int, length: Int) = synchronized(monitor) {
        checkOpen()
        checkDeadline()
        val first = pending.peekFirst()
        require(first != null && first.sequence == sequence && first.length == length) { "terminal-invalid-ack" }
        pending.removeFirst()
        outstanding -= length
        if (outstanding <= TerminalLimits.OUTPUT_LOW_WATER_BYTES) pressure = false
        monitor.notifyAll()
    }

    fun hasQueued(): Boolean = synchronized(monitor) { closedReason == null && queued.isNotEmpty() }
    fun outstandingBytes(): Int = synchronized(monitor) { outstanding }

    /** Call only after producers have stopped. Transport EOF alone is not parser completion. */
    fun awaitDrained() = synchronized(monitor) {
        val startedAt = nowMillis()
        while (true) {
            checkOpen()
            checkDeadline()
            if (outstanding == 0) return@synchronized
            if (nowMillis() - startedAt >= TerminalLimits.RENDERER_DEADLINE_MILLIS) {
                close("renderer-drain-timeout")
                throw TerminalFlowClosed("renderer-drain-timeout")
            }
            monitor.wait(250)
        }
    }

    /** Idle terminals have no ACK deadline. Only genuinely posted output starts this clock. */
    fun ackExpired(): Boolean = synchronized(monitor) {
        val first = pending.peekFirst()
        closedReason == null && first != null && nowMillis() - first.sentAt >= TerminalLimits.RENDERER_DEADLINE_MILLIS
    }

    fun expiredReason(): String? = synchronized(monitor) {
        when {
            closedReason != null -> null
            ackExpired() -> "renderer-parser-ack-timeout"
            queued.isNotEmpty() && nowMillis() - queued.first.queuedAt >= TerminalLimits.RENDERER_DEADLINE_MILLIS -> "renderer-delivery-timeout"
            else -> null
        }
    }

    fun close(reason: String = "terminal-closed") = synchronized(monitor) {
        if (closedReason == null) closedReason = reason
        queued.clear()
        pending.clear()
        outstanding = 0
        monitor.notifyAll()
    }

    private fun checkOpen() { closedReason?.let { throw TerminalFlowClosed(it) } }
    private fun checkDeadline() {
        expiredReason()?.let { reason -> close(reason); throw TerminalFlowClosed(reason) }
    }

    companion object { private const val MAX_FRAMES = 64 }
}
