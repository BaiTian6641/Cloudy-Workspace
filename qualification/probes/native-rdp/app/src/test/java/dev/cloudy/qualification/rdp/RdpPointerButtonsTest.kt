package dev.cloudy.qualification.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpPointerButtonsTest {
    @Test
    fun lostFocusCannotRearmAnOldDragThroughMovementOrRelease() {
        val state = RdpPointerButtons()
        assertEquals(listOf(1 to true), state.press(1, 1))
        state.clear() // The session owner separately releases actual forwarded input.
        assertTrue(state.release(1).isEmpty()) // Movement while the old button stays held.
        assertTrue(state.release(0).isEmpty()) // Its eventual physical release.
        assertFalse(state.hasOwnedButtons())
        assertEquals(listOf(1 to true), state.press(1, 1)) // A new explicit press works.
    }

    @Test
    fun aFreshChordButtonDoesNotAdoptAnOldHeldPrimary() {
        val state = RdpPointerButtons()
        state.press(1, 1)
        state.clear()
        assertEquals(listOf(2 to true), state.press(2, 3))
        assertEquals(listOf(2 to false), state.release(1))
        assertTrue(state.release(0).isEmpty())
        assertFalse(state.hasOwnedButtons())
    }

    @Test
    fun touchAndGenericDuplicatesAreDeduplicatedAndChordsReleaseIndependently() {
        val state = RdpPointerButtons()
        assertEquals(listOf(1 to true), state.press(1, 1))
        assertTrue(state.press(1, 1).isEmpty())
        assertTrue(state.release(3).isEmpty()) // State alone cannot acquire secondary.
        assertEquals(listOf(2 to true), state.press(2, 3))
        assertTrue(state.press(2, 3).isEmpty())
        assertEquals(listOf(1 to false), state.release(2))
        assertTrue(state.hasOwnedButtons())
        assertEquals(listOf(2 to false), state.release(0))
        assertFalse(state.hasOwnedButtons())
    }

    @Test
    fun unsupportedAndUnpressedButtonsCannotAcquireOwnership() {
        val state = RdpPointerButtons()
        assertTrue(state.press(8, 8).isEmpty())
        assertTrue(state.press(1, 0).isEmpty())
        assertTrue(state.release(7).isEmpty())
        assertFalse(state.hasOwnedButtons())
        assertEquals(listOf(4 to true), state.press(4, 4))
        state.clear() // Cancellation is idempotent, with no new presses.
        state.clear()
        assertTrue(state.release(4).isEmpty())
        assertFalse(state.hasOwnedButtons())
    }
}
