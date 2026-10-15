package dev.cloudy.qualification.usb

import java.util.concurrent.atomic.AtomicLong

/** All resource methods run on one executor; invalidation may arrive from the UI at any time. */
internal class LeaseOwner(private val native: NativePort) {
    interface Connection { val fd: Int; fun close() }
    interface NativePort {
        fun open(fd: Int): Long
        fun describe(handle: Long): String
        fun close(handle: Long)
    }
    private val epoch = AtomicLong()
    private var connection: Connection? = null
    private var handle = 0L
    fun invalidate(): Long = epoch.incrementAndGet()
    fun current(ticket: Long): Boolean = epoch.get() == ticket

    fun inspect(ticket: Long, open: () -> Connection?): String {
        close()
        if (!current(ticket)) return "Cancelled before open"
        val acquired = open() ?: return "Device unavailable: open returned no connection"
        connection = acquired
        try {
            if (!current(ticket)) { close(); return "Cancelled after open" }
            check(acquired.fd >= 0) { "Invalid USB descriptor" }
            handle = native.open(acquired.fd)
            check(handle != 0L) { "Native wrap returned no handle" }
            if (!current(ticket)) { close(); return "Cancelled after native wrap" }
            val description = native.describe(handle)
            if (!current(ticket)) { close(); return "Cancelled during descriptor inspection" }
            return description
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    fun close() {
        // Native borrows the FD: release its handle/context before closing the Android owner.
        val oldHandle = handle
        val oldConnection = connection
        handle = 0L
        connection = null
        try { if (oldHandle != 0L) native.close(oldHandle) }
        finally { oldConnection?.close() }
    }
}
