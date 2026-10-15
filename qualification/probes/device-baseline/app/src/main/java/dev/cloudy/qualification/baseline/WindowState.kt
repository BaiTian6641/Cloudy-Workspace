package dev.cloudy.qualification.baseline

import java.util.UUID

/** Only these fields cross Activity recreation through its own saved-state Bundle. */
internal data class SavedWindowFields(
    val version: Int,
    val logicalWindowId: String?,
    val markerIndex: Int,
    val changeCount: Long,
)

internal data class WindowLocalState(
    val logicalWindowId: String,
    val markerIndex: Int,
    val changeCount: Long,
) {
    val marker: String get() = MARKERS[markerIndex]
    val canChange: Boolean get() = changeCount < Long.MAX_VALUE

    fun changed(): WindowLocalState = if (!canChange) this else copy(
        markerIndex = (markerIndex + 1) % MARKERS.size,
        changeCount = changeCount + 1,
    )

    fun savedFields(): SavedWindowFields = SavedWindowFields(
        SAVED_STATE_VERSION, logicalWindowId, markerIndex, changeCount
    )

    companion object {
        const val SAVED_STATE_VERSION = 1
        private val MARKERS = listOf("A", "B", "C", "D")

        fun restore(fields: SavedWindowFields?, newId: () -> String = { UUID.randomUUID().toString() }): RestoredWindow {
            val id = fields?.logicalWindowId
            val validId = id != null && runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
            if (fields != null && fields.version == SAVED_STATE_VERSION && validId &&
                fields.markerIndex in MARKERS.indices && fields.changeCount >= 0
            ) {
                return RestoredWindow(WindowLocalState(requireNotNull(id), fields.markerIndex, fields.changeCount), "restored-bundle")
            }
            // Do not combine an invalid saved identity with a fresh counter: the
            // whole diagnostic state starts anew, with the reason retained.
            return RestoredWindow(WindowLocalState(newId(), 0, 0),
                if (fields == null) "fresh-no-bundle" else "invalid-bundle-reset")
        }
    }
}

internal data class RestoredWindow(val state: WindowLocalState, val source: String)

/** taskId is an observed platform value; generationId is fresh for every onCreate. */
internal data class WindowIdentity(val logicalWindowId: String, val taskId: Int, val generationId: String)
