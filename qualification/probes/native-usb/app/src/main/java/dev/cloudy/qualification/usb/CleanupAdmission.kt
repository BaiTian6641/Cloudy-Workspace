package dev.cloudy.qualification.usb

import java.util.concurrent.atomic.AtomicBoolean

/** Coalesce only until the worker finishes; delayed UI callbacks never suppress a new close. */
internal class CleanupAdmission {
    private val queued = AtomicBoolean()
    fun submit(execute: (() -> Unit) -> Unit, cleanup: () -> Unit): Boolean {
        if (!queued.compareAndSet(false, true)) return false
        try {
            execute { try { cleanup() } finally { queued.set(false) } }
        } catch (failure: RuntimeException) { queued.set(false); throw failure }
        return true
    }
}
