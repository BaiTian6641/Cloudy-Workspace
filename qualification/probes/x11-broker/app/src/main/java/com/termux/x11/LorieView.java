package com.termux.x11;

import android.os.ParcelFileDescriptor;
import android.view.Surface;

/** Minimal public-API input adapter for the pinned engine; root rendering remains with Cloudy XCB. */
@SuppressWarnings("unused")
public final class LorieView {
    private long nativeContext;
    private final MainActivity activity = null;
    private long imeResetCount;
    private long viewportSerial;

    public LorieView() {
        nativeContext = nativeInit();
        if (nativeContext == 0) throw new IllegalStateException("X11_INPUT_INIT_FAILED");
        setClipboardSyncEnabled(nativeContext, false, false);
    }

    public void connectTo(ParcelFileDescriptor connection) {
        connect(nativeContext, connection.getFd());
    }

    public void sendMouse(float x, float y, int button, boolean down) {
        sendMouseEvent(nativeContext, x, y, button, down, false);
    }

    public void sendText(byte[] utf8) { sendTextEvent(nativeContext, utf8); }
    public void sendKey(int scanCode, int keyCode, boolean down) {
        sendKeyEvent(nativeContext, scanCode, keyCode, down);
    }

    public void destroy() {
        if (nativeContext != 0) nativeDestroy(nativeContext);
        nativeContext = 0;
    }

    void resetIme() { ++imeResetCount; }
    void setRendererViewport(int x, int y, int w, int h, float left, float top, float width, float height) {
        ++viewportSerial;
    }
    void setClipboardText(String text) { throw new UnsupportedOperationException("X11 clipboard disabled"); }
    void requestClipboard() { throw new UnsupportedOperationException("X11 clipboard disabled"); }
    private void onSyncReply(int serial) { /* Renderer sync is not an input/product acknowledgment. */ }

    private native long nativeInit();
    private native void nativeDestroy(long ptr);
    private native void surfaceChanged(long ptr, Surface surface);
    private native void setFiltering(long ptr, int filtering);
    private native void setClipboardSyncEnabled(long ptr, boolean enabled, boolean ignored);
    private native void sendClipboardAnnounce(long ptr);
    private native void sendClipboardEvent(long ptr, byte[] text);
    private native void sendWindowChange(long ptr, int width, int height, int framerate, String name);
    private native void setViewport(long ptr, int x, int y, int width, int height, int expectedWidth,
                                    int expectedHeight, int hiddenBottom);
    private native void setRendererZoom(long ptr, int percent);
    private native void setZoomAnchor(long ptr, float sourceX, float sourceY, float fracX, float fracY);
    private native void clearZoomAnchor(long ptr);
    private native void setFollowCursorPan(long ptr, boolean enabled);
    private native long getCursorPosition(long ptr);
    private native void sendSync(long ptr, int serial);
    private static native void connect(long ptr, int fd);
    private static native boolean connected(long ptr);
    private static native void startLogcat(long ptr, int fd);
    private native void sendMouseEvent(long ptr, float x, float y, int whichButton, boolean buttonDown,
                                       boolean relative);
    private native void sendTouchEvent(long ptr, int action, int id, int x, int y);
    private native void sendStylusEvent(long ptr, float x, float y, int pressure, int tiltX, int tiltY,
                                        int orientation, int buttons, boolean eraser, boolean mouseMode);
    private native void requestStylusEnabled(long ptr, boolean enabled);
    private native void sendLockKeysState(long ptr, int state);
    private native boolean sendKeyEvent(long ptr, int scanCode, int keyCode, boolean keyDown);
    private native void sendTextEvent(long ptr, byte[] text);
    private static native boolean requestConnection(long ptr);
    public static native long getLastInputTimestamp();
    public static native void markUserActivity();
}
