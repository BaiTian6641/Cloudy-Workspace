package io.github.baitian6641.cloudy.terminal

import java.util.UUID

internal fun canonicalTerminalUuid(value: String): Boolean =
    try { UUID.fromString(value).toString() == value } catch (_: IllegalArgumentException) { false }

/** Native identities fence delayed renderer messages from an earlier connection. */
data class TerminalIdentity(val sessionId: String, val attachmentId: String) {
    init {
        require(canonicalTerminalUuid(sessionId))
        require(canonicalTerminalUuid(attachmentId))
    }

    override fun toString() = "TerminalIdentity(redacted)"
}

data class TerminalSize(val columns: Int, val rows: Int) {
    init { require(columns in 2..512 && rows in 1..256) { "TerminalDimensions" } }
    override fun toString() = "$columns×$rows"
}

enum class TerminalAdmission { ACCEPTED, FULL, STALE, STOPPED }

internal object TerminalLimits {
    const val OUTPUT_CHUNK_BYTES = 16 * 1024
    const val OUTPUT_HIGH_WATER_BYTES = 256 * 1024
    const val OUTPUT_LOW_WATER_BYTES = 64 * 1024
    const val INPUT_QUEUE_BYTES = 64 * 1024
    const val RENDERER_DEADLINE_MILLIS = 10_000L
}
