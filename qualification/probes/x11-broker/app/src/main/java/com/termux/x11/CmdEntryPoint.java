package com.termux.x11;

import android.os.ParcelFileDescriptor;

import java.util.concurrent.atomic.AtomicLong;

/** Public-API in-process server startup; excludes upstream shell context creation and broadcasts. */
@SuppressWarnings("unused")
public final class CmdEntryPoint {
    static { System.loadLibrary("Xlorie"); }

    public interface Listener { void onServerSignal(long generation, String documentTag); }
    private final AtomicLong signals = new AtomicLong();
    private volatile Listener listener;
    private volatile String documentTag;

    public CmdEntryPoint(String[] args) {
        if (!start(args)) throw new IllegalStateException("X_SERVER_START_REJECTED");
    }

    public void setListener(Listener value) { listener = value; }
    public boolean isConnected() { return connected(); }

    void setDocumentTag(String tag) {
        documentTag = tag;
        signal();
    }

    void sendBroadcast() { signal(); }
    void sendBroadcastDelayed() { signal(); }

    private void signal() {
        long generation = signals.incrementAndGet();
        Listener current = listener;
        if (current != null) current.onServerSignal(generation, documentTag);
    }

    public native boolean start(String[] args);
    public native ParcelFileDescriptor getXConnection();
    public native ParcelFileDescriptor getLogcatOutput();
    public native void reportFatalError(String message);
    private static native boolean connected();
}
