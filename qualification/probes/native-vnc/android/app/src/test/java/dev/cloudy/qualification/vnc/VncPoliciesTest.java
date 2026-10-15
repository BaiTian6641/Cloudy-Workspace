package dev.cloudy.qualification.vnc;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class VncPoliciesTest {
    @Test public void frameBoundsUseLongAndExactLength() {
        assertTrue(VncFramePolicy.valid(4096,2048,33_554_432));
        assertFalse(VncFramePolicy.valid(4096,2049,33_570_816));
        assertFalse(VncFramePolicy.valid(Integer.MAX_VALUE,Integer.MAX_VALUE,4));
        assertFalse(VncFramePolicy.valid(0,10,0));
        assertFalse(VncFramePolicy.valid(-1,10,40));
        assertFalse(VncFramePolicy.valid(2,1,7));
    }
    @Test public void rgbaConversionDoesNotAssumeNativeIntByteOrder() {
        int[] pixels=new int[3];
        VncFramePolicy.row(new byte[]{(byte)255,0,0,0,0,(byte)255,0,0,0,0,(byte)255,0},0,pixels);
        assertArrayEquals(new int[]{0xffff0000,0xff00ff00,0xff0000ff},pixels);
    }
    @Test public void focusLossCannotRearmOldPhysicalButton() {
        VncPointerState state=new VncPointerState();
        assertEquals(1,state.press(1,1)); state.clear();
        assertEquals(0,state.update(1)); assertEquals(0,state.update(0));
        assertEquals(1,state.press(1,1));
    }
    @Test public void freshSecondaryDoesNotAdoptOldPrimaryAndDuplicatesAreStable() {
        VncPointerState state=new VncPointerState();
        assertEquals(4,state.press(2,3)); assertEquals(4,state.press(2,3));
        assertEquals(5,state.press(1,3)); assertEquals(1,state.update(1));
        assertEquals(3,state.press(4,5)); assertEquals(0,state.clear());
    }
    @Test public void x11SpecialsAndUnicodeAreNotWindowsKeys() {
        assertEquals(Integer.valueOf(0xff08),VncKeyMap.special(KeyEvent.KEYCODE_DEL));
        assertEquals(Integer.valueOf(0xffff),VncKeyMap.special(KeyEvent.KEYCODE_FORWARD_DEL));
        assertEquals(Integer.valueOf(0xff8d),VncKeyMap.special(KeyEvent.KEYCODE_NUMPAD_ENTER));
        assertEquals(Integer.valueOf(0xffe4),VncKeyMap.special(KeyEvent.KEYCODE_CTRL_RIGHT));
        assertNull(VncKeyMap.special(KeyEvent.KEYCODE_META_LEFT));
        assertNull(VncKeyMap.special(KeyEvent.KEYCODE_UNKNOWN));
        assertEquals(Integer.valueOf(0xe9),VncKeyMap.unicode(0xe9));
        assertEquals(Integer.valueOf(0x0101f642),VncKeyMap.unicode(0x1f642));
        assertNull(VncKeyMap.unicode(0xd800)); assertNull(VncKeyMap.unicode(0x80000061));
        assertNull(VncKeyMap.unicode(0)); assertNull(VncKeyMap.unicode(0x110000));
    }
    @Test public void fractionalWheelDoesNotInventDetentsOrRetainAcrossFocusLoss() {
        VncWheel wheel=new VncWheel();
        assertArrayEquals(new int[]{0,0},wheel.add(0.4f,-0.4f));
        assertNull(wheel.add(Float.NaN,0)); assertNull(wheel.add(100,0));
        assertArrayEquals(new int[]{1,-1},wheel.add(0.7f,-0.7f));
        wheel.clear(); assertArrayEquals(new int[]{0,0},wheel.add(0.9f,-0.9f));
    }
    @Test public void cancelDoesNotAuthorizeFreeAndReleaseHasBoundedWait() {
        assertFalse(VncLifetime.mayFree(true,false)); assertTrue(VncLifetime.mayFree(true,true));
        assertTrue(VncLifetime.mayFree(false,false));
        assertFalse(VncLifetime.mayCancel(12,100,850,false));
        assertTrue(VncLifetime.mayCancel(12,850,850,false));
        assertTrue(VncLifetime.mayCancel(12,100,850,true));
    }
}
