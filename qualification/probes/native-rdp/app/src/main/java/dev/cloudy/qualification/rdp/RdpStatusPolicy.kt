package dev.cloudy.qualification.rdp

/** Main-thread diagnostic text pacing; never gates frame notifications or input. */
class RdpStatusPolicy {
    data class Identity(
        val attemptId: String, val state: String, val detail: String, val nativeVersion: String,
        val width: Int, val height: Int, val busy: Boolean, val connected: Boolean,
    )

    private var renderedIdentity: Identity? = null
    private var renderedFrames = 0L
    private var renderedInputs = 0L
    private var renderedAt = 0L

    fun shouldRender(identity: Identity, frames: Long, inputs: Long, nowMillis: Long): Boolean {
        val urgent = identity != renderedIdentity
        val countersChanged = frames != renderedFrames || inputs != renderedInputs
        // The existing session poll supplies a later opportunity; no timer retains the UI.
        // A new attempt/error/geometry/connection bypasses counter pacing immediately.
        if (!urgent && (!countersChanged || nowMillis - renderedAt < 250L)) return false
        renderedIdentity = identity
        renderedFrames = frames
        renderedInputs = inputs
        renderedAt = nowMillis
        return true
    }
}
