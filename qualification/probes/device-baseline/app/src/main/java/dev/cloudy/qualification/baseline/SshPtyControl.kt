package dev.cloudy.qualification.baseline

import java.util.concurrent.TimeUnit

internal enum class SshPtyAction { RESIZE, FINISH }

/** One engine's two ordered controls. No Activity, socket, executor queue or arbitrary input lives here. */
internal class SshPtyControl {
    private data class State(
        val action: SshPtyAction? = null,
        val released: Boolean = false,
        val accepted: Boolean = false,
        val taken: Boolean = false,
        val applied: Boolean = false,
        val closed: Boolean = false,
    )

    private val monitor = Object()
    private var state = State()

    /** Arm before publishing the phase, so a promptly delivered UI action is retained once. */
    fun arm(action: SshPtyAction) = synchronized(monitor) {
        check(!state.closed)
        check(if (action == SshPtyAction.RESIZE) state.action == null
            else state.action == SshPtyAction.RESIZE && state.applied)
        state = State(action = action)
    }

    /** Durable phase publication must succeed before the control thread can perform any I/O. */
    fun release(action: SshPtyAction) = synchronized(monitor) {
        check(!state.closed && state.action == action && !state.released)
        state = state.copy(released = true)
        monitor.notifyAll()
    }

    fun offer(action: SshPtyAction): Boolean = synchronized(monitor) {
        if (state.closed || state.action != action || state.accepted) return@synchronized false
        state = state.copy(accepted = true)
        monitor.notifyAll()
        true
    }

    /** Only the single control thread calls this; close or interruption releases its real wait. */
    fun take(): SshPtyAction? = synchronized(monitor) {
        while (!state.closed && !(state.released && state.accepted && !state.taken)) monitor.wait()
        if (state.closed) null else {
            state = state.copy(taken = true)
            state.action
        }
    }

    fun applied(action: SshPtyAction) = synchronized(monitor) {
        if (!state.closed) {
            check(state.action == action && state.taken && !state.applied)
            state = state.copy(applied = true)
            monitor.notifyAll()
        }
    }

    /** A matching unsolicited server token cannot advance a phase without its accepted control. */
    fun awaitApplied(action: SshPtyAction, timeoutMillis: Long): Boolean = synchronized(monitor) {
        require(timeoutMillis > 0)
        if (state.closed || state.action != action || !state.accepted) return@synchronized false
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!state.closed && !state.applied) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return@synchronized false
            TimeUnit.NANOSECONDS.timedWait(monitor, remaining)
        }
        !state.closed && state.action == action && state.applied
    }

    fun close() = synchronized(monitor) {
        state = state.copy(closed = true)
        monitor.notifyAll()
    }
}
