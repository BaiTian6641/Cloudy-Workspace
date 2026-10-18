package io.github.baitian6641.cloudy.platform

import io.github.baitian6641.cloudy.domain.TerminalRightClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Policy boundaries only; no Android ClipboardManager, UI or physical-input claim. */
class TerminalClipboardPolicyTest {
    @Test fun validatesWholeTextAgainstTheSharedPasteBound() {
        val exact = "a".repeat(TerminalClipboardPolicy.MAX_TEXT_BYTES)
        assertEquals(exact, (TerminalClipboardPolicy.validate(exact) as TerminalClipboardRead.Text).value)
        assertEquals(TerminalClipboardPolicy.MAX_TEXT_BYTES,
            (TerminalClipboardPolicy.validate(exact) as TerminalClipboardRead.Text).utf8.size)
        assertRefusal(TerminalClipboardRefusal.TOO_LARGE,
            TerminalClipboardPolicy.validate(exact + "b"))
        assertRefusal(TerminalClipboardRefusal.EMPTY, TerminalClipboardPolicy.validate(""))
        assertRefusal(TerminalClipboardRefusal.UNSUPPORTED, TerminalClipboardPolicy.validate(null))
        // Multi-byte text is measured in encoded bytes, not UTF-16 units.
        assertRefusal(TerminalClipboardRefusal.TOO_LARGE,
            TerminalClipboardPolicy.validate("\u6f22".repeat(TerminalClipboardPolicy.MAX_TEXT_BYTES / 3 + 1)))
    }

    @Test fun malformedUnicodeIsRefusedWithoutReplacement() {
        assertRefusal(TerminalClipboardRefusal.MALFORMED, TerminalClipboardPolicy.validate("\uD83D"))
        assertRefusal(TerminalClipboardRefusal.MALFORMED, TerminalClipboardPolicy.validate("\uDE00"))
        val emoji = "\uD83D\uDE00"
        assertEquals(emoji, (TerminalClipboardPolicy.validate(emoji) as TerminalClipboardRead.Text).value)
        assertEquals(4, (TerminalClipboardPolicy.validate(emoji) as TerminalClipboardRead.Text).utf8.size)
    }

    @Test fun consequentialControlsAndLineEndingsRequirePreview() {
        for (text in listOf("one\ntwo", "one\r\ntwo", "one\rtwo", "one\ttwo", "one\u001b[2J",
            "one\u007f", "\u0085", "\u2028", "\u2029")) {
            assertTrue(text, TerminalClipboardPolicy.requiresPreview(text))
        }
        // Every C0, C1 and DEL boundary requires review; printable neighbours do not.
        for (code in listOf(0x00, 0x09, 0x1B, 0x1F, 0x7F, 0x80, 0x85, 0x9B, 0x9F)) {
            assertTrue(code.toString(16), TerminalClipboardPolicy.requiresPreview(code.toChar().toString()))
        }
        for (code in listOf(0x20, 0x7E, 0xA0, 0x2027)) {
            assertFalse(code.toString(16), TerminalClipboardPolicy.requiresPreview(code.toChar().toString()))
        }
        for (text in listOf("plain text", "e\u0301\u6f22👍", "a b", "a;b", "a|b")) {
            assertFalse(text, TerminalClipboardPolicy.requiresPreview(text))
        }
    }


    @Test fun escapedPreviewShowsControlsAndKeepsLiteralBackslashesDistinct() {
        val text = "run\\x1B\u001b\u0007\u007f\u0085\u2028"
        val preview = TerminalClipboardPolicy.preview(text, 64)
        assertEquals("run\\\\x1B\\x1B\\x07\\x7F\\x85\\u2028", preview.display)
        assertEquals(text.length, preview.shownCharacters)
        assertEquals(text.length, preview.totalCharacters)
        assertFalse(preview.truncated)
        // A literal backslash and a control escape never render identically.
        assertEquals("\\\\", TerminalClipboardPolicy.preview("\\", 2).display)
        assertEquals("\\x1B", TerminalClipboardPolicy.preview("\u001b", 2).display)
        assertEquals("\\u2028", TerminalClipboardPolicy.preview("\u2028", 2).display)
    }

    @Test fun previewBoundsWithoutSplittingSurrogatePairsAndCountsTheOriginal() {
        val text = "abcd\uD83D\uDE00ef"
        val cut = TerminalClipboardPolicy.preview(text, 5)
        assertEquals("abcd", cut.display)
        assertEquals(4, cut.shownCharacters)
        assertEquals(text.length, cut.totalCharacters)
        assertTrue(cut.truncated)

        val pair = TerminalClipboardPolicy.preview(text, 6)
        assertEquals("abcd\uD83D\uDE00", pair.display)
        assertEquals(6, pair.shownCharacters)
        assertEquals(text.length, pair.totalCharacters)
        assertTrue(pair.truncated)

        val whole = TerminalClipboardPolicy.preview(text, text.length)
        assertEquals(text, whole.display)
        assertEquals(text.length, whole.shownCharacters)
        assertFalse(whole.truncated)

        val empty = TerminalClipboardPolicy.preview("", 2)
        assertEquals("", empty.display)
        assertEquals(0, empty.shownCharacters)
        assertEquals(0, empty.totalCharacters)
        assertFalse(empty.truncated)
    }

    @Test fun diagnosticsRedactClipboardText() {
        val read = TerminalClipboardPolicy.validate("hunter2 secret")
        assertFalse(read.toString().contains("hunter2"))
        val preview = TerminalClipboardPolicy.preview("hunter2 secret", 32)
        assertFalse(preview.toString().contains("hunter2"))
        // Only the explicit preview surface may expose the admitted text.
        assertTrue(preview.display.contains("hunter2"))
    }

    @Test fun rightClickPolicyIsUnknownUntilStorageConfirmsIt() {
        assertEquals(TerminalRightClick.PASTE, TerminalClipboardPolicy.confirmedRightClick(
            ready = true, loading = false, saving = false, recoveredValues = false,
            persistenceUncertain = false, value = TerminalRightClick.PASTE))
        assertEquals(TerminalRightClick.CONTEXT_MENU, TerminalClipboardPolicy.confirmedRightClick(
            ready = true, loading = false, saving = false, recoveredValues = false,
            persistenceUncertain = false, value = TerminalRightClick.CONTEXT_MENU))
        for (state in listOf(
            booleanArrayOf(false, false, false, false, false),
            booleanArrayOf(true, true, false, false, false),
            booleanArrayOf(true, false, true, false, false),
            booleanArrayOf(true, false, false, true, false),
            booleanArrayOf(true, false, false, false, true),
        )) {
            assertNull(TerminalClipboardPolicy.confirmedRightClick(state[0], state[1], state[2],
                state[3], state[4], TerminalRightClick.PASTE))
        }
    }

    private fun assertRefusal(expected: TerminalClipboardRefusal, result: TerminalClipboardRead) {
        assertEquals(expected, (result as TerminalClipboardRead.Refused).reason)
    }
}
