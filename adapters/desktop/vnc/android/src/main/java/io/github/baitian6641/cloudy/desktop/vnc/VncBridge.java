package io.github.baitian6641.cloudy.desktop.vnc;

/** Authored ABI for the patched client. This is not an Activity or a fake session. */
final class VncBridge {
    private VncBridge() {}
    public interface Sink {
        // All callbacks run synchronously on the native worker. They must return
        // promptly and never wait for join/free or a main-thread round trip.
        // Marshal owned frame/status copies to UI; join off callback/main threads.
        /** State: 0 created, 1 connecting, 2 authenticated/initialized, 3 stopped, 4 failed. */
        void onState(long handle, int state, int error);
        /** Owned Java copy, RGBA8888 with opaque alpha; width*height*4 bytes. */
        void onFrame(long handle, int width, int height, byte[] rgba);
        /** Real upstream send return; true does not establish host receipt. */
        void onInputResult(long handle, long sequence, boolean written);
    }
    public static synchronized void load() {
        System.loadLibrary("crypto");
        System.loadLibrary("ssl");
        System.loadLibrary("cloudy-vnc-jni");
    }
    /**
     * One process-owned instance. Numeric IPv4/IPv6 only: resolve DNS through a
     * separately bounded platform operation before this ABI. profileId is a stable
     * nonsecret identifier for the immutable (numeric address, port, leaf pin)
     * profile. Pass canonical UTF-8 username/password bytes, then wipe caller-owned
     * password bytes. durationSeconds is 10..600. Credentials are memory-only.
     */
    public static native long create(String profileId, String numericAddress, int port,
        byte[] leafSha256, byte[] usernameUtf8, byte[] passwordUtf8, int durationSeconds, Sink sink);
    /** One start per allocated instance; never reconnect a started handle. */
    public static native boolean start(long handle);
    /** Nonblocking cancellation; shuts down the published socket, but does not free. */
    public static native boolean cancel(long handle);
    /** Off callback/main thread. false retains ownership; timeout is 0..5000ms. */
    public static native boolean join(long handle, int timeoutMillis);
    /** Throws IllegalStateException unless never started or successfully joined. */
    public static native void free(long handle);
    /** [state,error,frames,admittedInputs,completedInputs,successfulWrites,width,height]. */
    public static native long[] status(long handle);
    /** Positive sequence means queue admission; zero means rejected. No relative emulation. */
    public static native long pointer(long handle, int x, int y, int buttonMask);
    /** X11/RFB keysym; caller maps physical keys or Unicode per standard RFB. */
    public static native long key(long handle, int keysym, boolean down);
    /** Queue tracked button/key releases before a planned cancellation; observe result first. */
    public static native long releaseInputs(long handle);
}
