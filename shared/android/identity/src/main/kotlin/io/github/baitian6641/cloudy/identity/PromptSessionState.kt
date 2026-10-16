package io.github.baitian6641.cloudy.identity

/** Main-thread identity slot; cancellation keeps ownership until a terminal callback. */
internal class PromptSessionState<T : Any> {
    var current: T? = null
        private set

    fun begin(session: T): Boolean {
        if (current != null) return false
        current = session
        return true
    }

    fun retire(expected: T): Boolean {
        if (current !== expected) return false
        current = null
        return true
    }
}
