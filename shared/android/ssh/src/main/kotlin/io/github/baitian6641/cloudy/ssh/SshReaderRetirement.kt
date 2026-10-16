package io.github.baitian6641.cloudy.ssh

import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.transport.Reader
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Selected SSHJ0.41.2 hook, not a generic logging/lifecycle contract. Reader.run unconditionally
 * invokes debug("Stopping") on normal/Exception/interrupt exit, including before its first read.
 * Capture only ownership, never log content; join the actual Thread after this event.
 * Missing/changed hooks or abnormal VM termination cannot produce a successful observation.
 */
internal class SshReaderRetirement : LoggerFactory {
    val expected = AtomicBoolean(false)
    private val reader = AtomicReference<Thread?>(null)
    private val mismatch = AtomicBoolean(false)
    private val terminal = CountDownLatch(1)
    private val logger = object : Logger by NOPLogger.NOP_LOGGER {
        override fun debug(message: String?) {
            if (message == "Stopping") {
                record(Thread.currentThread())
                terminal.countDown()
            }
        }
    }

    override fun getLogger(name: String?): Logger = if (name == Reader::class.java.name) logger else NOPLogger.NOP_LOGGER
    override fun getLogger(clazz: Class<*>?): Logger = getLogger(clazz?.name)

    fun record(thread: Thread) {
        if (thread !is Reader) { mismatch.set(true); return }
        val previous = reader.get()
        if (previous == null) {
            if (!reader.compareAndSet(null, thread) && reader.get() !== thread) mismatch.set(true)
        } else if (previous !== thread) mismatch.set(true)
    }

    /** Caller handles interruption and retries against its same finite cleanup deadline. */
    fun awaitAndJoin(deadlineNanos: Long): Boolean {
        if (!expected.get() && reader.get() == null && terminal.count != 0L) return !mismatch.get()
        val remaining = deadlineNanos - System.nanoTime()
        if (remaining <= 0 || !terminal.await(remaining, TimeUnit.NANOSECONDS)) return false
        val owned = reader.get() ?: return false
        while (owned.isAlive) {
            val left = deadlineNanos - System.nanoTime()
            if (left <= 0) return false
            // timedJoin preserves the submillisecond remainder: join(0) would wait forever.
            TimeUnit.NANOSECONDS.timedJoin(owned, left)
        }
        return !mismatch.get()
    }
}
