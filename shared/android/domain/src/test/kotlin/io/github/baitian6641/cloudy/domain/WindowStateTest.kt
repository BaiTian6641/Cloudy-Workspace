package io.github.baitian6641.cloudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowStateTest {
    @Test fun independentWindowsAndRenameKeepDistinctLogicalIdentities() {
        val first = WindowState.create(WindowOwner.CONSOLE, "  Workspace  ")
        val second = WindowState.create(WindowOwner.CONSOLE, "Workspace")
        val desktop = WindowState.create(WindowOwner.DESKTOP, "Workspace")
        assertNotEquals(first.windowId, second.windowId)
        assertNotEquals(first.windowId, desktop.windowId)
        val renamed = first.renamed("  新しい窓 🧑‍💻  ")
        assertEquals(first.windowId, renamed.windowId)
        assertEquals(first.owner, renamed.owner)
        assertEquals("新しい窓 🧑‍💻", renamed.name)
        assertEquals("Workspace", first.name)
        assertEquals("desktop", desktop.snapshot().owner)
    }

    @Test fun snapshotRoundTripPreservesIdentityAndRejectsCrossAppRestore() {
        val original = WindowState.create(WindowOwner.CONSOLE, "نافذة 日本語 👩‍💻")
        val snapshot = original.snapshot()
        val restored = WindowState.restore(WindowOwner.CONSOLE, snapshot)
        assertTrue(restored is WindowRestore.Restored)
        assertEquals(snapshot, (restored as WindowRestore.Restored).state.snapshot())
        assertEquals(WindowRestore.Rejected(WindowRestoreProblem.WRONG_OWNER),
            WindowState.restore(WindowOwner.DESKTOP, snapshot))
        assertEquals(WindowRestore.Rejected(WindowRestoreProblem.WRONG_OWNER),
            WindowState.restore(WindowOwner.CONSOLE, snapshot.copy(owner = "CONSOLE")))
    }

    @Test fun restoreRejectsUnrecognizedSchemaAndNoncanonicalIds() {
        val snapshot = WindowState.create(WindowOwner.DESKTOP, "Local").snapshot()
        assertEquals(WindowRestore.Rejected(WindowRestoreProblem.UNKNOWN_SCHEMA),
            WindowState.restore(WindowOwner.DESKTOP, snapshot.copy(schemaVersion = 2)))
        for (id in listOf("", "1-1-1-1-1", "ABCDEFAB-1234-1234-1234-123456789abc",
                snapshot.windowId + " ", "z".repeat(36), "a".repeat(1000))) {
            assertEquals(id, WindowRestore.Rejected(WindowRestoreProblem.INVALID_ID),
                WindowState.restore(WindowOwner.DESKTOP, snapshot.copy(windowId = id)))
        }
    }

    @Test fun namesHaveCodePointBudgetAndPreserveLegitimateUnicode() {
        val emoji = "😀".repeat(80)
        assertNull(WindowState.nameProblem(emoji))
        assertEquals(emoji, WindowState.create(WindowOwner.DESKTOP, emoji).name)
        assertNull(WindowState.nameProblem("  " + "字".repeat(80) + "  "))
        assertNull(WindowState.nameProblem("e\u0301 👨‍👩‍👧‍👦 العربية"))
        assertEquals(WindowNameProblem.TOO_LONG, WindowState.nameProblem("字".repeat(81)))
        assertEquals(WindowNameProblem.TOO_LONG, WindowState.nameProblem("😀".repeat(81)))
        assertEquals(WindowNameProblem.TOO_LONG, WindowState.nameProblem(" ".repeat(10000)))
    }

    @Test fun invalidNamesCannotEnterThroughCreateRenameOrRestore() {
        val state = WindowState.create(WindowOwner.CONSOLE, "Existing")
        for ((name, reason) in listOf(
            "  " to WindowNameProblem.BLANK,
            "x\n" to WindowNameProblem.CONTROL_CHARACTER,
            "\u0000x" to WindowNameProblem.CONTROL_CHARACTER,
            "x\u0085" to WindowNameProblem.CONTROL_CHARACTER,
            "x\u2028y" to WindowNameProblem.CONTROL_CHARACTER,
            "x\u2029y" to WindowNameProblem.CONTROL_CHARACTER,
            "\uD800" to WindowNameProblem.INVALID_UNICODE,
            "\uDC00" to WindowNameProblem.INVALID_UNICODE,
            "\uD800x" to WindowNameProblem.INVALID_UNICODE,
            "a".repeat(81) to WindowNameProblem.TOO_LONG,
        )) {
            assertEquals(reason, WindowState.nameProblem(name))
            assertThrows(IllegalArgumentException::class.java) {
                WindowState.create(WindowOwner.CONSOLE, name)
            }
            assertThrows(IllegalArgumentException::class.java) { state.renamed(name) }
            assertEquals(WindowRestore.Rejected(WindowRestoreProblem.INVALID_NAME),
                WindowState.restore(WindowOwner.CONSOLE, state.snapshot().copy(name = name)))
        }
        assertEquals("Existing", state.name)
    }
}
