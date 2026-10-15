package dev.cloudy.qualification.vnc;

import android.view.KeyEvent;

/** RFB/X11 keysyms, never Windows virtual keys. Text follows Android's physical layout. */
public final class VncKeyMap {
    private VncKeyMap() {}
    public static Integer special(int code) {
        if (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12) return 0xffbe + code - KeyEvent.KEYCODE_F1;
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) return 0xffb0 + code - KeyEvent.KEYCODE_NUMPAD_0;
        switch (code) {
            case KeyEvent.KEYCODE_DEL: return 0xff08;
            case KeyEvent.KEYCODE_FORWARD_DEL: return 0xffff;
            case KeyEvent.KEYCODE_TAB: return 0xff09;
            case KeyEvent.KEYCODE_ENTER: return 0xff0d;
            case KeyEvent.KEYCODE_ESCAPE: return 0xff1b;
            case KeyEvent.KEYCODE_MOVE_HOME: return 0xff50;
            case KeyEvent.KEYCODE_MOVE_END: return 0xff57;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 0xff51;
            case KeyEvent.KEYCODE_DPAD_UP: return 0xff52;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 0xff53;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 0xff54;
            case KeyEvent.KEYCODE_PAGE_UP: return 0xff55;
            case KeyEvent.KEYCODE_PAGE_DOWN: return 0xff56;
            case KeyEvent.KEYCODE_INSERT: return 0xff63;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return 0xffe1;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return 0xffe2;
            case KeyEvent.KEYCODE_CTRL_LEFT: return 0xffe3;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return 0xffe4;
            case KeyEvent.KEYCODE_ALT_LEFT: return 0xffe9;
            case KeyEvent.KEYCODE_ALT_RIGHT: return 0xffea;
            case KeyEvent.KEYCODE_CAPS_LOCK: return 0xffe5;
            case KeyEvent.KEYCODE_NUM_LOCK: return 0xff7f;
            case KeyEvent.KEYCODE_SCROLL_LOCK: return 0xff14;
            case KeyEvent.KEYCODE_BREAK: return 0xff13;
            case KeyEvent.KEYCODE_SYSRQ: return 0xff61;
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return 0xff8d;
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY: return 0xffaa;
            case KeyEvent.KEYCODE_NUMPAD_ADD: return 0xffab;
            case KeyEvent.KEYCODE_NUMPAD_COMMA: return 0xffac;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return 0xffad;
            case KeyEvent.KEYCODE_NUMPAD_DOT: return 0xffae;
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE: return 0xffaf;
            case KeyEvent.KEYCODE_NUMPAD_EQUALS: return 0xffbd;
            default: return null;
        }
    }
    public static Integer unicode(int point) {
        if (point < 0x20 || point > 0x10ffff || (point >= 0xd800 && point <= 0xdfff)
                || (point >= 0x7f && point < 0xa0)) return null;
        return point <= 0xff ? point : 0x01000000 | point;
    }
}
