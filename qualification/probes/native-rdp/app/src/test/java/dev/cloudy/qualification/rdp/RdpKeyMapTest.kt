package dev.cloudy.qualification.rdp

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure constant mapping checks; no Android KeyEvent construction or runtime stubs. */
class RdpKeyMapTest {
    @Test
    fun desktopRangeBoundariesAndInteriorKeysMapToWindowsValues() {
        assertEquals(0x41, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_A))
        assertEquals(0x4D, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_M))
        assertEquals(0x5A, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_Z))
        assertEquals(0x30, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_0))
        assertEquals(0x39, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_9))
        assertEquals(0x70, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_F1))
        assertEquals(0x7B, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_F12))
    }

    @Test
    fun androidBackspaceAndCursorHomeAreNotDeleteOrSystemHome() {
        assertEquals(0x08, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_DEL))
        assertEquals(0x12E, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(0x124, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_MOVE_HOME))
        assertNull(RdpKeyMap.virtualKey(KeyEvent.KEYCODE_HOME))
        assertNull(RdpKeyMap.virtualKey(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun modifierSidesArePreservedAndMetaRemainsLocal() {
        assertEquals(0xA0, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(0xA1, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_SHIFT_RIGHT))
        assertEquals(0xA2, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(0x1A3, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_CTRL_RIGHT))
        assertEquals(0xA4, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_ALT_LEFT))
        assertEquals(0x1A5, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_ALT_RIGHT))
        assertNull(RdpKeyMap.virtualKey(KeyEvent.KEYCODE_META_LEFT))
        assertNull(RdpKeyMap.virtualKey(KeyEvent.KEYCODE_META_RIGHT))
    }

    @Test
    fun numericKeypadDoesNotCollapseIntoTopRowOrOemPunctuation() {
        assertEquals(0x60, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_0))
        assertEquals(0x69, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_9))
        assertNotEquals(RdpKeyMap.virtualKey(KeyEvent.KEYCODE_0), RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_0))
        assertEquals(0x6B, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_ADD))
        assertEquals(0x6E, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_DOT))
        assertEquals(0x16F, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_DIVIDE))
        assertNotEquals(RdpKeyMap.virtualKey(KeyEvent.KEYCODE_SLASH), RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_DIVIDE))
        assertEquals(0x0D, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_ENTER))
        assertEquals(0x10D, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_ENTER))
    }

    @Test
    fun navigationUsesWinprExtendedTableInsteadOfTheKeypadTable() {
        assertEquals(0x125, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(0x128, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(0x121, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(0x12D, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_INSERT))
        assertEquals(0x64, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_4))
        assertNotEquals(RdpKeyMap.virtualKey(KeyEvent.KEYCODE_DPAD_LEFT), RdpKeyMap.virtualKey(KeyEvent.KEYCODE_NUMPAD_4))
    }

    @Test
    fun usOemKeysUsePositionValuesRatherThanCharacterCodes() {
        assertEquals(0xBA, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_SEMICOLON))
        assertEquals(0xBB, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_EQUALS))
        assertEquals(0xC0, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_GRAVE))
        assertEquals(0xDC, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_BACKSLASH))
        assertEquals(0xDE, RdpKeyMap.virtualKey(KeyEvent.KEYCODE_APOSTROPHE))
    }

    @Test
    fun unsupportedKeysNeverPretendToBeRemoteInput() {
        for (code in intArrayOf(-1, Int.MAX_VALUE, KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.KEYCODE_NUMPAD_COMMA, KeyEvent.KEYCODE_NUMPAD_EQUALS,
            KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN, KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_VOLUME_UP)) {
            assertNull("Unexpected mapping for Android key $code", RdpKeyMap.virtualKey(code))
        }
    }
}
