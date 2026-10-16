package io.github.baitian6641.cloudy.platform

import android.content.Context

/** Qualification is explicitly disabled in release: no file lookup, worker or retained identity. */
@Suppress("UNUSED_PARAMETER")
internal object FilesPositiveObservation {
    enum class Event {
        SENDER_CREATED, SENDER_DESTROYED, SUBMIT_ACCEPTED, CALL_FINISHED, CALL_RETIRED,
        RECEIVER_CREATED, RECEIVER_DESTROYED, DISPATCH_STARTED, DISPATCH_RETIRED,
        SESSION_CLAIMED, TRANSPORT_ESTABLISHED, TRANSPORT_RETIRED, RETIREMENT_UNCERTAIN,
        SAME_SESSION_IDLE, MODEL_CLEARED,
    }
    fun start(context: Context, event: Event, first: Any, second: Any? = null) = Unit
    fun record(event: Event, first: Any, second: Any? = null) = Unit
}
