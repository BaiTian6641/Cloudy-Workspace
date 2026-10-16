package io.github.baitian6641.cloudy.ssh

/** Sticky cancellation. Cancellation wakes the attempt owner; it never performs socket I/O here. */
class SshObservationCancellation {
    private val monitor = Any()
    @Volatile private var cancelled = false
    private var wake: (() -> Unit)? = null
    val isCancelled: Boolean get() = cancelled

    fun cancel() {
        val listener = synchronized(monitor) { cancelled = true; wake }
        listener?.invoke()
    }

    internal fun attach(listener: () -> Unit): Boolean {
        val accepted = synchronized(monitor) {
            if (wake != null) false else { wake = listener; true }
        }
        if (accepted && cancelled) listener()
        return accepted
    }
    internal fun detach() { synchronized(monitor) { wake = null } }
    override fun toString(): String = "SshObservationCancellation(redacted)"
}
