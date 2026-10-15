package dev.cloudy.qualification.baseline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pure state-policy checks; these do not simulate Android Bundle callbacks. */
class WindowStateTest {
    private val firstId = "00000000-0000-0000-0000-000000000001"
    private val secondId = "00000000-0000-0000-0000-000000000002"

    @Test
    fun separateWindowsKeepIndependentMarkersAndIdentity() {
        val first = WindowLocalState.restore(null) { firstId }.state
        val second = WindowLocalState.restore(null) { secondId }.state
        val changed = first.changed().changed()
        assertEquals("C", changed.marker)
        assertEquals(2L, changed.changeCount)
        assertEquals("A", second.marker)
        assertEquals(0L, second.changeCount)
        assertNotEquals(changed.logicalWindowId, second.logicalWindowId)
        assertEquals(firstId, changed.logicalWindowId)
    }

    @Test
    fun recreationRestoresOnlyTheSuppliedWindowFields() {
        val first = WindowLocalState.restore(null) { firstId }.state.changed().changed().changed()
        val second = WindowLocalState.restore(null) { secondId }.state.changed()
        val restored = WindowLocalState.restore(first.savedFields()) { error("A valid restoration must not allocate another identity") }
        assertEquals(first, restored.state)
        assertEquals("restored-bundle", restored.source)
        assertEquals("B", second.marker)
        assertEquals(1L, second.changeCount)
    }

    @Test
    fun malformedOrFutureSavedStateResetsAllFieldsTogether() {
        val invalid = listOf(
            SavedWindowFields(2, firstId, 1, 5),
            SavedWindowFields(1, "1-1-1-1-1", 1, 5),
            SavedWindowFields(1, null, 1, 5),
            SavedWindowFields(1, firstId, -1, 5),
            SavedWindowFields(1, firstId, 4, 5),
            SavedWindowFields(1, firstId, 1, -1),
        )
        invalid.forEach { fields ->
            val restored = WindowLocalState.restore(fields) { secondId }
            assertEquals("invalid-bundle-reset", restored.source)
            assertEquals(WindowLocalState(secondId, 0, 0), restored.state)
        }
    }

    @Test
    fun markerCyclesWithoutResettingItsCounter() {
        var state = WindowLocalState.restore(null) { firstId }.state
        repeat(5) { state = state.changed() }
        assertEquals("B", state.marker)
        assertEquals(5L, state.changeCount)
    }

    @Test
    fun counterCannotWrapIntoNegativeSavedState() {
        val restored = WindowLocalState.restore(SavedWindowFields(1, firstId, 3, Long.MAX_VALUE)).state
        assertFalse(restored.canChange)
        assertEquals(restored, restored.changed())
    }
}
