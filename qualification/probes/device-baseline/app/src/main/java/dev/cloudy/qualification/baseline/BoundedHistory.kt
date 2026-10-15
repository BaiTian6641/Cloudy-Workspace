package dev.cloudy.qualification.baseline

/** Caller owns synchronization; snapshots cannot change when later events arrive. */
internal class BoundedHistory<T>(private val capacity: Int) {
    init {
        require(capacity > 0) { "History capacity must be positive." }
    }

    private val entries = ArrayDeque<T>()
    var droppedCount: Long = 0
        private set

    fun add(value: T) {
        if (entries.size == capacity) {
            entries.removeFirst()
            droppedCount++
        }
        entries.addLast(value)
    }

    fun snapshot(): List<T> = entries.toList()
}
