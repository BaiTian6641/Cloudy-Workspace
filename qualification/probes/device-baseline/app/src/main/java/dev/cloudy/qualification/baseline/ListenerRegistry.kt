package dev.cloudy.qualification.baseline

import java.util.concurrent.CopyOnWriteArraySet

/** Stable delivery snapshots without retaining subscriptions after removal. */
internal class ListenerRegistry<T> {
    private val entries = CopyOnWriteArraySet<T>()

    fun add(value: T) { entries.add(value) }

    fun removeMatching(predicate: (T) -> Boolean) {
        // Kotlin's Iterable.removeAll(predicate) uses iterator.remove(), which
        // CopyOnWriteArraySet forbids. Its native removeIf is supported on min 35.
        entries.removeIf { predicate(it) }
    }

    fun snapshot(): List<T> = entries.toList()
}
