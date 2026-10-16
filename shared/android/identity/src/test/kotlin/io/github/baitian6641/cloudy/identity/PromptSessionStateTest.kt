package io.github.baitian6641.cloudy.identity

import org.junit.Assert.*
import org.junit.Test

class PromptSessionStateTest {
    @Test fun cancelledSessionDrainsBeforeReplacementAndRejectsLateCallbacks() {
        val slot = PromptSessionState<Any>()
        val old = Any()
        val replacement = Any()
        assertTrue(slot.begin(old))
        // Delivering local cancellation does not acknowledge framework retirement.
        assertFalse(slot.begin(replacement))
        assertSame(old, slot.current)
        assertTrue(slot.retire(old))
        assertTrue(slot.begin(replacement))
        assertFalse(slot.retire(old))
        assertSame(replacement, slot.current)
        assertTrue(slot.retire(replacement))
        assertNull(slot.current)
    }

    @Test fun correlationUsesIdentityRatherThanEqualValues() {
        data class Token(val value: Int)
        val slot = PromptSessionState<Token>()
        val actual = Token(1)
        assertTrue(slot.begin(actual))
        assertFalse(slot.retire(Token(1)))
        assertSame(actual, slot.current)
        assertTrue(slot.retire(actual))
        assertFalse(slot.retire(actual))
    }
}
