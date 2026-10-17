package io.github.baitian6641.cloudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalInputPolicyTest {
    @Test fun defaultsMatchTheAmendedMobaXtermPointerContract() {
        val policy = TerminalInputPolicy.DEFAULT
        assertEquals(true, policy.copyOnSelect)
        assertEquals(TerminalRightClick.PASTE, policy.rightClick)
        assertEquals(true, policy.shiftInsertPaste)
        assertEquals(true, policy.ctrlWheelZoom)
        assertEquals(TerminalInputPolicy(), TerminalInputPolicy.DEFAULT)
    }

    @Test fun rightClickToggleChangesOnlyTheLocalSecondaryClickMeaning() {
        val menu = TerminalInputPolicy.forRightClick(TerminalRightClick.CONTEXT_MENU)
        assertEquals(TerminalRightClick.CONTEXT_MENU, menu.rightClick)
        assertEquals(true, menu.copyOnSelect)
        assertEquals(true, menu.shiftInsertPaste)
        assertEquals(true, menu.ctrlWheelZoom)
        assertNotEquals(TerminalInputPolicy.DEFAULT, menu)
        assertEquals(TerminalInputPolicy.DEFAULT,
            menu.copy(rightClick = TerminalRightClick.PASTE))
    }

    @Test fun wireIdsAreStableAndDistinctForPersistedSettings() {
        assertEquals(listOf("paste", "context-menu"), TerminalRightClick.entries.map { it.wireId })
        assertEquals(TerminalRightClick.entries.size, TerminalRightClick.entries.map { it.wireId }.toSet().size)
        for (action in TerminalRightClick.entries) {
            assertEquals(action, TerminalRightClick.valueOf(action.name))
            assertTrue(action.wireId.all { it.isLowerCase() || it == '-' })
        }
    }
}
