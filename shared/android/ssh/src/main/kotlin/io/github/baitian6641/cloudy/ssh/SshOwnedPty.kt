package io.github.baitian6641.cloudy.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.SessionChannel
import java.io.InputStream
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SshPtySize(val columns: Int, val rows: Int) {
    init { require(columns in 2..512 && rows in 1..256) { "PTY_SIZE" } }
}
enum class SshPtyAdmission { ACCEPTED, FULL, STOPPED, INVALID_INPUT }
sealed interface SshPtyOpenResult {
    class Opened(val pty: SshOwnedPty) : SshPtyOpenResult {
        override fun toString() = "SshPtyOpenResult.Opened(redacted)"
    }
    data class Failure(val code: SshTransportCode) : SshPtyOpenResult
}
sealed interface SshPtyReadResult {
    class Output internal constructor(val bytes: ByteArray, val isStderr: Boolean) : SshPtyReadResult {
        override fun toString() = "SshPtyReadResult.Output(redacted)"
    }
    data object Idle : SshPtyReadResult
    data class End(val code: SshTransportCode) : SshPtyReadResult
    data class Failure(val code: SshTransportCode) : SshPtyReadResult
}

/** Engine-issued authority by object identity; no raw SSHJ channel or reusable text ticket escapes. */
class SshOwnedPty internal constructor(
    private val client: SSHClient,
    private val size: SshPtySize,
    private val active: () -> Boolean,
    private val reason: () -> SshTransportCode,
    private val stop: (SshTransportCode) -> Unit,
) {
    private val channel = AtomicReference<SessionChannel?>()
    private val setupDone = CountDownLatch(1)
    private val eofStreams = AtomicInteger()
    private val workers = ArrayList<Thread>(3)
    private val input = SshPtyMailbox()
    private val output = SshPtyOutput()

    internal fun open(): SshPtyOpenResult {
        try {
            check(active())
            // Same selected-pin construction as SSHClient.startSession. Publish before open().
            val actual = SessionChannel(client.connection, client.remoteCharset)
            check(channel.compareAndSet(null, actual))
            check(active())
            actual.open()
            check(active())
            actual.allocatePTY("xterm-256color", size.columns, size.rows, 0, 0, emptyMap())
            check(active())
            val shell = actual.startShell()
            check(active())
            start("stdout") { drain(shell.inputStream, false) }
            start("stderr") { drain(shell.errorStream, true) }
            start("writer") { write(shell) }
            if (!active()) return SshPtyOpenResult.Failure(reason())
            return SshPtyOpenResult.Opened(this)
        } catch (_: Exception) {
            if (Thread.currentThread().isInterrupted) stop(SshTransportCode.CANCELLED)
            stop(SshTransportCode.OPERATION_FAILED)
            return SshPtyOpenResult.Failure(reason())
        } finally { setupDone.countDown() }
    }

    /** Accepted is bounded local queue admission, never remote consumption. */
    fun offerInput(bytes: ByteArray): SshPtyAdmission =
        if (!active()) SshPtyAdmission.STOPPED else input.offer(bytes)
    /** Last pending size wins; SSH window-change has no remote acknowledgment. */
    fun offerResize(size: SshPtySize): SshPtyAdmission =
        if (!active()) SshPtyAdmission.STOPPED else input.resize(size)
    /** One consumer; copies are caller-owned. No callbacks execute on SSHJ Reader. */
    fun read(timeoutMillis: Long = 1_000): SshPtyReadResult {
        if (timeoutMillis !in 1..10_000) return SshPtyReadResult.Failure(SshTransportCode.INVALID_INPUT)
        return try { output.read(timeoutMillis, reason) }
        catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            stop(SshTransportCode.CANCELLED)
            SshPtyReadResult.Failure(SshTransportCode.CANCELLED)
        }
    }
    /** First increment has one connection lifetime: this closes its linked files as well. */
    fun requestClose() { stop(SshTransportCode.CLOSED) }
    override fun toString() = "SshOwnedPty(redacted)"

    private fun start(name: String, action: () -> Unit) {
        val thread = Thread({
            try { action() }
            catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                if (active()) stop(SshTransportCode.CANCELLED)
            } catch (_: Exception) { if (active()) stop(SshTransportCode.OPERATION_FAILED) }
        }, "cloudy-ssh-pty-$name").apply { isDaemon = true }
        workers.add(thread)
        thread.start()
    }
    private fun drain(stream: InputStream, stderr: Boolean) {
        val bytes = ByteArray(16 * 1024)
        try {
            while (active()) {
                val count = stream.read(bytes)
                if (count < 0) {
                    // Drain both streams. Either may reach EOF first; neither is command success.
                    if (eofStreams.incrementAndGet() == 2) stop(SshTransportCode.CLOSED)
                    return
                }
                check(count > 0)
                output.put(bytes.copyOf(count), stderr)
            }
        } finally { bytes.fill(0) }
    }
    private fun write(shell: Session.Shell) {
        while (active()) {
            when (val work = input.take() ?: return) {
                is SshPtyWrite.Bytes -> try {
                    if (active()) { shell.outputStream.write(work.value); shell.outputStream.flush() }
                } finally { input.completed(work); work.value.fill(0) }
                is SshPtyWrite.Resize -> if (active())
                    shell.changeWindowDimensions(work.size.columns, work.size.rows, 0, 0)
            }
        }
    }
    internal fun stopQueues() { input.close(); output.close() }
    internal fun closeChannel() { channel.get()?.close() }
    internal fun awaitResources(endNanos: Long): Boolean {
        var left = endNanos - System.nanoTime()
        if (left <= 0 || !setupDone.await(left, TimeUnit.NANOSECONDS)) return false
        // setupDone publishes the final fixed worker set, even when setup/start partially fails.
        for (worker in workers) {
            worker.interrupt()
            left = endNanos - System.nanoTime()
            if (worker.isAlive && left > 0) TimeUnit.NANOSECONDS.timedJoin(worker, left)
        }
        return workers.none { it.isAlive } && channel.get()?.isOpen != true
    }
}

internal sealed interface SshPtyWrite {
    class Bytes(val value: ByteArray) : SshPtyWrite
    class Resize(val size: SshPtySize) : SshPtyWrite
}

/** One whole input action may use the 64 KiB capacity, including in-flight bytes; never split admission. */
internal class SshPtyMailbox {
    private val monitor = Object()
    private val queue = ArrayDeque<SshPtyWrite.Bytes>()
    private var charged = 0
    private var inFlight: SshPtyWrite.Bytes? = null
    private var pendingSize: SshPtySize? = null
    private var stopped = false
    private var lastResize = false
    fun offer(bytes: ByteArray): SshPtyAdmission = synchronized(monitor) {
        if (stopped) return@synchronized SshPtyAdmission.STOPPED
        if (bytes.isEmpty() || bytes.size > 64 * 1024) return@synchronized SshPtyAdmission.INVALID_INPUT
        if (charged + bytes.size > 64 * 1024 || queue.size >= 64) return@synchronized SshPtyAdmission.FULL
        queue.add(SshPtyWrite.Bytes(bytes.copyOf())); charged += bytes.size
        monitor.notifyAll()
        SshPtyAdmission.ACCEPTED
    }
    fun resize(size: SshPtySize): SshPtyAdmission = synchronized(monitor) {
        if (stopped) return@synchronized SshPtyAdmission.STOPPED
        pendingSize = size; monitor.notifyAll(); SshPtyAdmission.ACCEPTED
    }
    fun take(): SshPtyWrite? = synchronized(monitor) {
        check(inFlight == null)
        while (!stopped && queue.isEmpty() && pendingSize == null) monitor.wait()
        if (stopped) return@synchronized null
        val size = pendingSize
        if (size != null && (queue.isEmpty() || !lastResize)) {
            pendingSize = null; lastResize = true
            return@synchronized SshPtyWrite.Resize(size)
        }
        lastResize = false
        queue.removeFirst().also { inFlight = it }
    }
    fun completed(work: SshPtyWrite.Bytes) = synchronized(monitor) {
        check(inFlight === work); charged -= work.value.size; inFlight = null; monitor.notifyAll()
    }
    fun close() = synchronized(monitor) {
        stopped = true
        queue.forEach { it.value.fill(0) }; queue.clear()
        charged = inFlight?.value?.size ?: 0; pendingSize = null; monitor.notifyAll()
    }
}

/** One output copy per producer plus a bounded queue. Slow UI blocks only dedicated PTY readers. */
internal class SshPtyOutput {
    private val monitor = Object()
    private val queue = ArrayDeque<SshPtyReadResult.Output>()
    private var bytes = 0
    private var stopped = false
    fun put(value: ByteArray, stderr: Boolean) = synchronized(monitor) {
        require(value.isNotEmpty() && value.size <= 16 * 1024)
        try {
            while (!stopped && (bytes + value.size > 256 * 1024 || queue.size >= 64)) monitor.wait()
        } catch (interrupted: InterruptedException) {
            value.fill(0)
            throw interrupted
        }
        if (stopped) { value.fill(0); return@synchronized }
        queue.add(SshPtyReadResult.Output(value, stderr)); bytes += value.size; monitor.notifyAll()
    }
    fun read(timeoutMillis: Long, reason: () -> SshTransportCode): SshPtyReadResult = synchronized(monitor) {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!stopped && queue.isEmpty()) {
            val left = end - System.nanoTime()
            if (left <= 0) return@synchronized SshPtyReadResult.Idle
            TimeUnit.NANOSECONDS.timedWait(monitor, left)
        }
        if (queue.isEmpty()) return@synchronized SshPtyReadResult.End(reason())
        queue.removeFirst().also { bytes -= it.bytes.size; monitor.notifyAll() }
    }
    fun close() = synchronized(monitor) { stopped = true; monitor.notifyAll() }
}
