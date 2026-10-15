package dev.cloudy.qualification.rdp

import android.view.KeyEvent

/**
 * Bounded US physical-key mapping for the JNI virtual-key-to-scancode path.
 * This does not translate a keyboard layout, committed text or an IME composition.
 * Sources: developer.android.com/reference/android/view/KeyEvent and
 * learn.microsoft.com/en-us/windows/win32/inputdev/virtual-key-codes (2026-10-15).
 * FreeRDP 97eff6dd: winpr/input.h KBD4_* and input/scancode.c select an
 * extended table only when the virtual key includes WinPR KBDEXT (0x100).
 */
object RdpKeyMap {
    private const val EXTENDED = 0x100
    fun virtualKey(androidCode: Int): Int? = when (androidCode) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 0x41 + androidCode - KeyEvent.KEYCODE_A
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> 0x30 + androidCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 0x70 + androidCode - KeyEvent.KEYCODE_F1
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> 0x60 + androidCode - KeyEvent.KEYCODE_NUMPAD_0
        KeyEvent.KEYCODE_DEL -> 0x08 // Android DEL means backspace, not forward delete.
        KeyEvent.KEYCODE_TAB -> 0x09
        KeyEvent.KEYCODE_ENTER -> 0x0D
        KeyEvent.KEYCODE_NUMPAD_ENTER -> 0x0D or EXTENDED
        KeyEvent.KEYCODE_ESCAPE -> 0x1B
        KeyEvent.KEYCODE_SPACE -> 0x20
        KeyEvent.KEYCODE_PAGE_UP -> 0x21 or EXTENDED
        KeyEvent.KEYCODE_PAGE_DOWN -> 0x22 or EXTENDED
        KeyEvent.KEYCODE_MOVE_END -> 0x23 or EXTENDED
        KeyEvent.KEYCODE_MOVE_HOME -> 0x24 or EXTENDED
        KeyEvent.KEYCODE_DPAD_LEFT -> 0x25 or EXTENDED
        KeyEvent.KEYCODE_DPAD_UP -> 0x26 or EXTENDED
        KeyEvent.KEYCODE_DPAD_RIGHT -> 0x27 or EXTENDED
        KeyEvent.KEYCODE_DPAD_DOWN -> 0x28 or EXTENDED
        KeyEvent.KEYCODE_INSERT -> 0x2D or EXTENDED
        KeyEvent.KEYCODE_FORWARD_DEL -> 0x2E or EXTENDED
        KeyEvent.KEYCODE_CAPS_LOCK -> 0x14
        KeyEvent.KEYCODE_NUM_LOCK -> 0x90
        KeyEvent.KEYCODE_SCROLL_LOCK -> 0x91
        KeyEvent.KEYCODE_SHIFT_LEFT -> 0xA0
        KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xA1
        KeyEvent.KEYCODE_CTRL_LEFT -> 0xA2
        KeyEvent.KEYCODE_CTRL_RIGHT -> 0xA3 or EXTENDED
        KeyEvent.KEYCODE_ALT_LEFT -> 0xA4
        KeyEvent.KEYCODE_ALT_RIGHT -> 0xA5 or EXTENDED
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> 0x6A
        KeyEvent.KEYCODE_NUMPAD_ADD -> 0x6B
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> 0x6D
        KeyEvent.KEYCODE_NUMPAD_DOT -> 0x6E
        KeyEvent.KEYCODE_NUMPAD_DIVIDE -> 0x6F or EXTENDED
        // OEM values describe the US ANSI key positions; Shift is forwarded separately.
        KeyEvent.KEYCODE_SEMICOLON -> 0xBA
        KeyEvent.KEYCODE_EQUALS -> 0xBB
        KeyEvent.KEYCODE_COMMA -> 0xBC
        KeyEvent.KEYCODE_MINUS -> 0xBD
        KeyEvent.KEYCODE_PERIOD -> 0xBE
        KeyEvent.KEYCODE_SLASH -> 0xBF
        KeyEvent.KEYCODE_GRAVE -> 0xC0
        KeyEvent.KEYCODE_LEFT_BRACKET -> 0xDB
        KeyEvent.KEYCODE_BACKSLASH -> 0xDC
        KeyEvent.KEYCODE_RIGHT_BRACKET -> 0xDD
        KeyEvent.KEYCODE_APOSTROPHE -> 0xDE
        // VK_SEPARATOR has no type-4 WinPR scan mapping, so keypad comma is unsupported.
        // Do not synthesize shifted sequences for keypad '='/'()'.
        // HOME/BACK, Meta, media and unknown keys remain framework-owned. The caller
        // must also exclude Meta chords because this function has no event modifiers.
        else -> null
    }
}
