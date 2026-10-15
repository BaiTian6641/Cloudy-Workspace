package com.freerdp.freerdpcore.services;

import android.content.Context;
import android.graphics.Bitmap;

/** Authored ABI adapter for the pinned upstream symbol-name JNI, not its profile/UI layer.
 * Method names/descriptors must match the native source. The process-wide callback object
 * constructed by JNI owns no Activity. RdpSession owns the one live native instance. */
public final class LibFreeRDP {
    public interface Sink {
        void event(long instance, String name, int width, int height, int bpp);
        void graphics(long instance, int x, int y, int width, int height);
        void pointer(long instance, int[] pixels, int width, int height, int hotX, int hotY);
        boolean authenticate(long instance, StringBuilder user, StringBuilder domain, StringBuilder password);
        int verify(long instance, byte[] pem, String host, long port, long flags);
        void callbackFailure(long instance, String operation, String failureClass, int[] dimensions);
    }
    private static volatile Sink sink;
    private static boolean loaded;

    // Required by JNI_OnLoad; this object deliberately has no session or UI state.
    public LibFreeRDP() { }

    public static synchronized void load() {
        if (loaded) return;
        // Explicit loads invoke WinPR's own JNI_OnLoad rather than only ELF dependency loading.
        System.loadLibrary("winpr3");
        System.loadLibrary("freerdp3");
        System.loadLibrary("freerdp-client3");
        System.loadLibrary("freerdp-android");
        loaded = true;
    }
    public static void setSink(Sink value) { sink = value; }
    private static void failed(Sink current, long id, String operation, Throwable error, int... dimensions) {
        // Retain the catch site's owner and instance. A newer global sink must never
        // receive an old callback's exception, message, credentials or pixel buffer.
        if (current != null) {
            try { current.callbackFailure(id, operation, error.getClass().getSimpleName(), dimensions); }
            catch (Throwable ignored) { /* Never leave a pending exception on a native callback. */ }
        }
    }
    private static void event(long id, String name, int width, int height, int bpp) {
        Sink current = sink;
        if (current == null) return; // A stale callback cannot acquire a later session.
        try { current.event(id, name, width, height, bpp); }
        catch (Throwable error) { failed(current, id, name, error, width, height, bpp); }
    }
    private static void event(long id, String name) { event(id, name, 0, 0, 0); }
    private static void OnPreConnect(long id) { event(id, "pre-connect"); }
    private static void OnConnectionSuccess(long id) { event(id, "connected"); }
    private static void OnConnectionFailure(long id) { event(id, "native-failure"); }
    private static void OnDisconnecting(long id) { event(id, "disconnecting"); }
    private static void OnDisconnected(long id) { event(id, "disconnected"); }
    private static void OnSettingsChanged(long id, int w, int h, int bpp) { event(id, "settings", w, h, bpp); }
    private static void OnGraphicsResize(long id, int w, int h, int bpp) { event(id, "resize", w, h, bpp); }
    private static void OnGraphicsUpdate(long id, int x, int y, int w, int h) {
        Sink current = sink;
        if (current == null) return;
        try { current.graphics(id, x, y, w, h); }
        catch (Throwable error) { failed(current, id, "graphics-update", error, x, y, w, h); }
    }
    private static boolean OnAuthenticate(long id, StringBuilder user, StringBuilder domain, StringBuilder password) {
        Sink current = sink;
        if (current == null) return false;
        try { return current.authenticate(id, user, domain, password); }
        catch (Throwable error) { failed(current, id, "authenticate", error); return false; }
    }
    private static boolean OnGatewayAuthenticate(long id, StringBuilder u, StringBuilder d, StringBuilder p) {
        event(id, "unsupported-gateway"); return false;
    }
    private static int OnVerifyCertificateEx(long id, String host, long port, String cn, String subject,
            String issuer, String fingerprint, long flags) {
        event(id, "unexpected-certificate-route"); return 0;
    }
    private static int OnVerifyX509Certificate(long id, byte[] pem, String host, long port, long flags) {
        Sink current = sink;
        if (current == null) return 0;
        try { return current.verify(id, pem, host, port, flags); }
        catch (Throwable error) { failed(current, id, "verify-x509", error); return 0; }
    }

    private static int OnVerifyChangedCertificateEx(long id, String host, long port, String cn, String subject,
            String issuer, String fingerprint, String oldSubject, String oldIssuer, String oldFingerprint, long flags) {
        // A stored identity change must not inherit approval from an earlier probe session.
        event(id, "changed-certificate-rejected"); return 0;
    }
    private static boolean OnExperimentalFeature(long id, int feature) {
        event(id, "unsupported-experimental"); return false;
    }
    private static void OnRemoteClipboardChanged(long id, String data) { event(id, "unsupported-clipboard"); }
    private static void OnRemoteClipboardImageChanged(long id, byte[] data) { event(id, "unsupported-clipboard"); }
    private static void OnPointerSet(long id, int[] pixels, int w, int h, int hotX, int hotY) {
        Sink current = sink;
        if (current == null) return;
        try { current.pointer(id, pixels, w, h, hotX, hotY); }
        catch (Throwable error) { failed(current, id, "pointer-set", error, w, h, hotX, hotY); }
    }
    private static void OnPointerSetNull(long id) { event(id, "pointer-null"); }
    private static void OnPointerSetDefault(long id) { event(id, "pointer-default"); }
    private static void OnRailWindowUpdate(long id, long window, int w, int h, int[] pixels) { event(id, "unsupported-rail"); }
    private static void OnRailWindowMove(long id, long window, int x, int y, int w, int h) { event(id, "unsupported-rail"); }
    private static void OnRailWindowHide(long id, long window) { event(id, "unsupported-rail"); }
    private static void OnRailWindowDestroy(long id, long window) { event(id, "unsupported-rail"); }
    private static void OnRailSessionEnd(long id) { event(id, "unsupported-rail"); }
    private static void OnRailMonitoredDesktop(long id, long[] windows, long active) { event(id, "unsupported-rail"); }

    public static native boolean freerdp_has_h264();
    public static native boolean freerdp_has_camera_redirection();
    public static native String freerdp_get_jni_version();
    public static native String freerdp_get_version();
    public static native String freerdp_get_build_revision();
    public static native String freerdp_get_build_config();
    public static native long freerdp_new(Context context);
    public static native void freerdp_free(long instance);
    public static native boolean freerdp_join(long instance, int timeoutMillis);
    public static native boolean freerdp_parse_arguments(long instance, String[] args);
    public static native boolean freerdp_set_external_certificate_management(long instance);
    public static native boolean freerdp_connect(long instance);
    public static native boolean freerdp_disconnect(long instance);
    public static native boolean freerdp_update_graphics(long instance, Bitmap bitmap, int x, int y, int width, int height);
    public static native boolean freerdp_is_relative_mouse_supported(long instance);
    public static native boolean freerdp_send_relative_cursor_event(long instance, int dx, int dy, int flags);
    public static native boolean freerdp_send_cursor_event(long instance, int x, int y, int flags);
    public static native boolean freerdp_send_key_event(long instance, int virtualKey, boolean down);
    public static native boolean freerdp_send_unicodekey_event(long instance, int codeUnit, boolean down);
    public static native boolean freerdp_is_unicode_input_supported(long instance);
    public static native String freerdp_get_last_error_string(long instance);
}
