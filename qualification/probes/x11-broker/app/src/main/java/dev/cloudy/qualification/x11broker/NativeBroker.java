package dev.cloudy.qualification.x11broker;

import android.graphics.Bitmap;

/** Narrow JNI boundary for the private P0-S7 broker experiment. */
public final class NativeBroker {
    static { System.loadLibrary("cloudy_x11_broker"); }
    private NativeBroker() { }

    public static native long open(String socketPath, byte[] cookie, boolean redirect);
    public static native void close(long handle);
    /** Eight integers per record: XID, transient parent, override redirect, x, y, width, height, reserved. */
    public static native int[] listWindows(long handle);
    public static native int[] geometry(long handle, int xid);
    public static native boolean render(long handle, int xid, Bitmap bitmap);
    public static native boolean viewable(long handle, int xid);
    public static native boolean requestDelete(long handle, int xid);
    public static native boolean focusWindow(long handle, int xid);
    public static native boolean pointer(long handle, int xid, int localX, int localY, int button, boolean down);
    public static native boolean startDemo(String socketPath, byte[] cookie);
    public static native void stopDemo();
}
