package dev.cloudy.qualification.x11broker;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.system.Os;

import com.termux.x11.CmdEntryPoint;
import com.termux.x11.LorieView;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Process-local owner for one private display; no authority enters intents, logs or saved state. */
public final class X11Environment {
    private static final Object LOCK = new Object();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static X11Environment current;

    private final File runtime;
    private final File temporary;
    private final String socketPath;
    private final byte[] cookie;
    private final CmdEntryPoint server;
    private LorieView inputView;
    private ParcelFileDescriptor inputConnection;
    private long controller;

    private X11Environment(File runtime, File temporary, byte[] cookie, CmdEntryPoint server) {
        this.runtime = runtime;
        this.temporary = temporary;
        this.socketPath = new File(temporary, ".X11-unix/X0").getAbsolutePath();
        this.cookie = cookie;
        this.server = server;
    }

    public static boolean isRunning() {
        synchronized (LOCK) { return current != null && current.controller != 0; }
    }

    public static String start(Context context) throws Exception {
        synchronized (LOCK) {
            if (current != null && current.controller != 0) return "Server already running";
            File runtime = new File(context.getFilesDir(), "x11-runtime");
            copyAssets(context, runtime);
            File temporary = new File(runtime, "tmp");
            File sockets = new File(temporary, ".X11-unix");
            if (!sockets.isDirectory() && !sockets.mkdirs()) throw new IOException("X11_TMP_UNAVAILABLE");
            byte[] cookie = new byte[16];
            RANDOM.nextBytes(cookie);
            File authority = new File(temporary, "Xauthority");
            writeAuthority(authority, cookie);
            Os.setenv("TMPDIR", temporary.getAbsolutePath(), true);
            Os.setenv("XKB_CONFIG_ROOT", new File(runtime, "usr/share/X11/xkb").getAbsolutePath(), true);
            // libXlorie creates an Android choreographer synchronously; construction belongs to
            // the main thread, while assets and bounded broker retries remain on the worker.
            CmdEntryPoint server = constructServerOnMain(new String[] {
                ":0", "-auth", authority.getAbsolutePath(), "-nolisten", "tcp", "-xstartup", "",
            });
            X11Environment created = new X11Environment(runtime, temporary, cookie, server);
            current = created;
            try {
                long handle = 0;
                for (int attempt = 0; attempt < 50 && handle == 0; ++attempt) {
                    handle = NativeBroker.open(created.socketPath, cookie, true);
                    if (handle == 0) TimeUnit.MILLISECONDS.sleep(100);
                }
                if (handle == 0) throw new IOException("X11_BROKER_CONNECT_FAILED");
                created.controller = handle;
                created.connectInputOnMain();
                return "Server ready with authenticated Composite broker and engine input";
            } catch (Exception failure) {
                created.controller = 0;
                current = null;
                server.reportFatalError("Cloudy broker startup failed");
                cookieFill(cookie, (byte) 0);
                throw failure;
            }
        }
    }

    private static CmdEntryPoint constructServerOnMain(String[] args) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) return new CmdEntryPoint(args);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<CmdEntryPoint> server = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        new Handler(Looper.getMainLooper()).post(() -> {
            try { server.set(new CmdEntryPoint(args)); }
            catch (RuntimeException error) { failure.set(error); }
            finally { completed.countDown(); }
        });
        if (!completed.await(5, TimeUnit.SECONDS)) throw new IOException("X11_MAIN_START_TIMEOUT");
        if (failure.get() != null) throw failure.get();
        if (server.get() == null) throw new IOException("X11_MAIN_START_FAILED");
        return server.get();
    }

    private void connectInputOnMain() throws Exception {
        runMain(() -> {
            inputView = new LorieView();
            inputConnection = server.getXConnection();
            if (inputConnection == null) throw new IllegalStateException("X11_INPUT_CONNECTION_FAILED");
            inputView.connectTo(inputConnection);
        });
    }

    private static void runMain(Runnable action) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
            return;
        }
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        new Handler(Looper.getMainLooper()).post(() -> {
            try { action.run(); }
            catch (RuntimeException error) { failure.set(error); }
            finally { completed.countDown(); }
        });
        if (!completed.await(5, TimeUnit.SECONDS)) throw new IOException("X11_MAIN_INPUT_TIMEOUT");
        if (failure.get() != null) throw failure.get();
    }

    public static boolean startDemo() {
        X11Environment owner = owner();
        byte[] cookie = owner.cookie.clone();
        try { return NativeBroker.startDemo(owner.socketPath, cookie); }
        finally { cookieFill(cookie, (byte) 0); }
    }

    public static void stopDemo() { NativeBroker.stopDemo(); }

    public static void pointer(int xid, int localX, int localY, int button, boolean down) {
        X11Environment owner = owner();
        android.util.Log.i("CloudyX11Broker", "Pointer task entered for XID " + xid);
        int[] geometry = NativeBroker.geometry(owner.controller, xid);
        if (geometry == null) {
            android.util.Log.w("CloudyX11Broker", "Pointer geometry unavailable for XID " + xid);
            return;
        }
        int rootX = geometry[0] + localX;
        int rootY = geometry[1] + localY;
        android.util.Log.i("CloudyX11Broker", "Pointer XID " + xid + " root=" + rootX + "," + rootY +
            " button=" + button + " down=" + down + " priorInputMs=" + LorieView.getLastInputTimestamp());
        new Handler(Looper.getMainLooper()).post(() -> {
            if (owner.inputView != null) {
                // Termux EVENT_MOUSE applies coordinates only with detail 0; clicks alone do not move.
                owner.inputView.sendMouse(rootX, rootY, 0, false);
                if (button > 0) owner.inputView.sendMouse(rootX, rootY, button, down);
            }
        });
    }

    public static void key(int scanCode, int keyCode, boolean down) {
        X11Environment owner = owner();
        owner.inputView.sendKey(scanCode, keyCode, down);
    }

    public static void text(String value) {
        X11Environment owner = owner();
        owner.inputView.sendText(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static int[] listWindows() {
        X11Environment owner = owner();
        return NativeBroker.listWindows(owner.controller);
    }

    public static long openBroker() {
        X11Environment owner = owner();
        byte[] cookie = owner.cookie.clone();
        try { return NativeBroker.open(owner.socketPath, cookie, false); }
        finally { cookieFill(cookie, (byte) 0); }
    }

    public static byte[] cookieForSsh() { return owner().cookie.clone(); }

    public static String socketPath() { return owner().socketPath; }

    public static void stop() {
        X11Environment owner;
        synchronized (LOCK) {
            owner = current;
            current = null;
        }
        if (owner == null) return;
        NativeBroker.stopDemo();
        try {
            runMain(() -> {
                if (owner.inputConnection != null) {
                    try { owner.inputConnection.close(); }
                    catch (IOException ignored) { }
                }
                if (owner.inputView != null) owner.inputView.destroy();
            });
        } catch (Exception failure) {
            throw new IllegalStateException("X11_INPUT_STOP_FAILED", failure);
        }
        owner.inputConnection = null;
        owner.inputView = null;
        if (owner.controller != 0) NativeBroker.close(owner.controller);
        owner.controller = 0;
        owner.server.reportFatalError("Cloudy broker owner stopped");
        cookieFill(owner.cookie, (byte) 0);
    }

    private static X11Environment owner() {
        synchronized (LOCK) {
            if (current == null || current.controller == 0) throw new IllegalStateException("X11_NOT_RUNNING");
            return current;
        }
    }

    private static void writeAuthority(File file, byte[] cookie) throws IOException {
        String hostname = Os.uname().nodename;
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(file))) {
            writeField(output, 256); // FamilyLocal
            writeBytes(output, hostname.getBytes("UTF-8"));
            writeBytes(output, "0".getBytes("US-ASCII"));
            writeBytes(output, "MIT-MAGIC-COOKIE-1".getBytes("US-ASCII"));
            writeBytes(output, cookie);
        }
        if (!file.setReadable(true, true)) throw new IOException("X11_AUTHORITY_PERMISSIONS");
    }

    private static void writeField(DataOutputStream output, int value) throws IOException {
        output.writeShort(value);
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static void copyAssets(Context context, File destination) throws IOException {
        File marker = new File(destination, ".complete-v1");
        if (marker.isFile()) return;
        AssetManager assets = context.getAssets();
        copyAssetDirectory(assets, "runtime", destination);
        try (FileOutputStream output = new FileOutputStream(marker)) { output.write(1); }
    }

    private static void copyAssetDirectory(AssetManager assets, String path, File destination) throws IOException {
        String[] children = assets.list(path);
        if (children == null || children.length == 0) {
            copyAssetFile(assets, path, destination);
            return;
        }
        if (!destination.isDirectory() && !destination.mkdirs()) throw new IOException("X11_ASSET_DIRECTORY");
        for (String child : children) {
            if (child.contains("/") || child.contains("..")) throw new IOException("X11_ASSET_PATH");
            copyAssetDirectory(assets, path + "/" + child, new File(destination, child));
        }
    }

    private static void copyAssetFile(AssetManager assets, String path, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("X11_ASSET_PARENT");
        try (InputStream input = assets.open(path); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                total += read;
                if (total > 32 * 1024 * 1024) throw new IOException("X11_ASSET_TOO_LARGE");
                output.write(buffer, 0, read);
            }
        }
    }

    private static void cookieFill(byte[] value, byte fill) {
        for (int index = 0; index < value.length; ++index) value[index] = fill;
    }
}
