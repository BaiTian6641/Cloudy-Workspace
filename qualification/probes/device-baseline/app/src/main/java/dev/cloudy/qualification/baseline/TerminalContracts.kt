package dev.cloudy.qualification.baseline

import java.util.UUID

/** The native confirmation authorizes exactly the identity displayed when the dialog opened. */
internal data class TerminalTrustTicket(val identity: TerminalIdentity, val configIdentity: String,
    val endpointId: String, val fingerprint: String)

internal fun canonicalTerminalUuid(value: String): Boolean =
    try { UUID.fromString(value).toString() == value } catch (_: IllegalArgumentException) { false }

/** Native identities fence delayed renderer messages from a later connection. */
internal data class TerminalIdentity(val sessionId: String, val attachmentId: String) {
    init {
        require(canonicalTerminalUuid(sessionId))
        require(canonicalTerminalUuid(attachmentId))
    }
}

internal data class TerminalSize(val columns: Int, val rows: Int) {
    init { require(columns in 2..512 && rows in 1..256) { "TerminalDimensions" } }
}

internal enum class TerminalAdmission { ACCEPTED, FULL, STALE, STOPPED }

internal object TerminalLimits {
    const val OUTPUT_CHUNK_BYTES = 16 * 1024
    const val OUTPUT_HIGH_WATER_BYTES = 256 * 1024
    const val OUTPUT_LOW_WATER_BYTES = 64 * 1024
    const val INPUT_QUEUE_BYTES = 64 * 1024
    const val RENDERER_DEADLINE_MILLIS = 10_000L
    const val SESSION_DEADLINE_SECONDS = 15L * 60L
}

internal data class TerminalObservation(
    val status: String,
    val observedHostKey: String? = null,
    val authenticated: Boolean = false,
    val details: Map<String, String> = emptyMap(),
)

internal data class TerminalExit(
    val status: String,
    val reason: String?,
    val observedHostKey: String?,
    val authenticated: Boolean,
    val details: Map<String, String>,
    val cleanupCertain: Boolean,
)

internal interface TerminalTransportListener {
    /** Serialized transport-owner observations; throwing aborts further protocol work. */
    fun onObservation(observation: TerminalObservation)

    /** Called off the UI thread. Bounded renderer backpressure may block this call. */
    fun onOutput(bytes: ByteArray, isStderr: Boolean)
}
