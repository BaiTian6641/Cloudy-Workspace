package io.github.baitian6641.cloudy.desktop.vnc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.InetAddresses;
import android.os.Build;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Single-use, retained native owner. No UI, profile persistence, DNS, reconnect or vault policy.
 * Read snapshots/pull frames from the owning presentation layer. Joining never runs on its thread.
 */
public final class VncEngine {
    public enum State { NEW, STARTING, AUTHENTICATING, INITIALIZED, FIRST_FRAME, STOP_REQUESTED,
        RETIRED, FAILED, CLEANUP_UNCERTAIN }
    public enum ReleaseOutcome { NOT_REQUESTED, NOT_APPLICABLE, REJECTED, WRITTEN_NOT_ACKNOWLEDGED,
        WRITE_FAILED, TERMINAL_BEFORE_RESULT, TIMED_OUT, FAILED }
    public static final class Snapshot {
        public final State state;
        public final int nativeError;
        public final long completedInputSequence, failedInputSequence;
        public final ReleaseOutcome releaseOutcome;
        private Snapshot(State state, int error, long completed, long failed, ReleaseOutcome release) {
            this.state = state; nativeError = error;
            completedInputSequence = completed; failedInputSequence = failed; releaseOutcome = release;
        }
    }
    /** Numeric-only immutable endpoint and exact leaf pin. This is not CA/name trust. */
    public static final class Config {
        private final String profile, address;
        private final int port, duration;
        private final byte[] pin;
        public Config(String profile, String address, int port, byte[] pin, int durationSeconds) {
            this(profile, address, port, pin, durationSeconds, InetAddresses::parseNumericAddress);
        }
        // Package-only platform seam for JVM ownership tests, never used by the public constructor.
        Config(String profile, String address, int port, byte[] pin, int durationSeconds,
                java.util.function.Consumer<String> numericCheck) {
            if (profile == null || !profile.matches("[A-Za-z0-9_-]{1,64}") || address == null ||
                    address.length() > 63 || address.contains("%") || port < 1 || port > 65535 ||
                    pin == null || pin.length != 32 || durationSeconds < 10 || durationSeconds > 600) {
                throw new IllegalArgumentException("VNC_CONFIG");
            }
            try { numericCheck.accept(address); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("VNC_CONFIG"); }
            this.profile = profile; this.address = address; this.port = port;
            this.pin = pin.clone(); duration = durationSeconds;
        }
    }
    /** Ownership of this native-created full RGBA copy transfers to the caller on takeLatestFrame.
     * Keep at most one presented frame; the engine retains at most one pending frame, each <=32MiB.
     * A callback is not evidence of actual presentation. Native scratch/resize memory is additional.
     */
    public static final class Frame {
        public final int width, height;
        public final byte[] rgba;
        private Frame(int width, int height, byte[] rgba) { this.width = width; this.height = height; this.rgba = rgba; }
    }
    // Package-only seam for authored owner tests. Production always uses the real JNI backend.
    interface Backend {
        void load();
        long create(Config c, byte[] user, byte[] password, VncBridge.Sink sink);
        boolean start(long h);
        boolean cancel(long h);
        boolean join(long h, int timeout);
        void free(long h);
        long[] status(long h);
        long pointer(long h, int x, int y, int mask);
        long key(long h, int keysym, boolean down);
        long release(long h);
    }
    private static final Backend JNI = new Backend() {
        public void load() { VncBridge.load(); }
        public long create(Config c, byte[] u, byte[] p, VncBridge.Sink sink) {
            return VncBridge.create(c.profile, c.address, c.port, c.pin, u, p, c.duration, sink);
        }
        public boolean start(long h) { return VncBridge.start(h); }
        public boolean cancel(long h) { return VncBridge.cancel(h); }
        public boolean join(long h, int t) { return VncBridge.join(h, t); }
        public void free(long h) { VncBridge.free(h); }
        public long[] status(long h) { return VncBridge.status(h); }
        public long pointer(long h, int x, int y, int m) { return VncBridge.pointer(h, x, y, m); }
        public long key(long h, int k, boolean d) { return VncBridge.key(h, k, d); }
        public long release(long h) { return VncBridge.releaseInputs(h); }
    };
    private static final AtomicReference<VncEngine> LEASE = new AtomicReference<>();
    private final Context context;
    private final Backend backend;
    private final ScheduledThreadPoolExecutor owner;
    private final AtomicBoolean admitted = new AtomicBoolean(), stop = new AtomicBoolean();
    // Guarded by callbacks. Automatic paths share attempt1; only explicit retry authorizes the next.
    private int cleanupAttempts;
    private boolean retryQueued;
    private final AtomicInteger queuedInputs = new AtomicInteger();
    private final Object callbacks = new Object();
    private final Object attempt = new Object();
    private volatile Snapshot snapshot = new Snapshot(State.NEW, -1, 0, 0, ReleaseOutcome.NOT_REQUESTED);
    private volatile long handle;
    private Frame pending;
    private volatile boolean initialized;
    private boolean firstFrame;
    private boolean releaseAttempted;
    private volatile ReleaseOutcome releaseOutcome = ReleaseOutcome.NOT_REQUESTED;
    private long completed, failed;
    private ScheduledFuture<?> poll;
    public VncEngine(Context context) { this(context.getApplicationContext(), JNI); }
    VncEngine(Context context, Backend backend) {
        this(context, backend, new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "CloudyVncOwner"); thread.setDaemon(true); return thread;
        }));
    }
    // Package-only executor seam for deterministic owner-queue fencing; public callers use owned executor.
    VncEngine(Context context, Backend backend, ScheduledThreadPoolExecutor executor) {
        this.context = context; this.backend = backend; owner = executor;
        owner.setRemoveOnCancelPolicy(true);
        owner.setKeepAliveTime(1, TimeUnit.SECONDS);
        owner.allowCoreThreadTimeOut(true);
    }
    public Snapshot snapshot() { return snapshot; }
    public Frame takeLatestFrame() {
        synchronized (callbacks) { Frame frame = pending; pending = null; return frame; }
    }
    /** Returns admission only. Both credential arrays transfer even on rejection and are wiped.
     * Caller must not mutate them after this call. Strict canonical UTF-8, no NUL; transient only.
     */
    public boolean connect(Config config, byte[] username, byte[] password) {
        if (!admitted.compareAndSet(false, true)) { wipe(username); wipe(password); return false; }
        if (config == null || !validSecret(username, 256) || !validSecret(password, 1024) ||
                !LEASE.compareAndSet(null, this)) {
            wipe(username); wipe(password); publish(State.FAILED); owner.shutdown(); return false;
        }
        publish(State.STARTING);
        try {
            owner.execute(() -> {
                try {
                    if (stop.get()) { finishWithoutHandle(username, password); return; }
                    if (context != null && Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission(
                            Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
                        throw new SecurityException("VNC_PERMISSION");
                    }
                    backend.load();
                    if (stop.get()) { finishWithoutHandle(username, password); return; }
                    long allocated;
                    try { allocated = backend.create(config, username, password, sink(attempt)); }
                    finally { wipe(username); wipe(password); }
                    synchronized (callbacks) { handle = allocated; }
                    if (allocated == 0) { finishWithoutHandle(username, password); return; }
                    if (stop.get() || !backend.start(allocated)) { stop.set(true); cleanup(1); return; }
                    synchronized (callbacks) { if (!stop.get() && !initialized) publish(State.AUTHENTICATING); }
                    poll = owner.scheduleWithFixedDelay(this::pollNative, 100, 100, TimeUnit.MILLISECONDS);
                } catch (RuntimeException | LinkageError error) {
                    if (handle == 0) finishWithoutHandle(username, password); else { stop.set(true); cleanup(1); }
                } finally { wipe(username); wipe(password); }
            });
            return true;
        } catch (RejectedExecutionException error) {
            wipe(username); wipe(password); finishWithoutHandle(username, password); return false;
        }
    }
    private VncBridge.Sink sink(Object token) {
        return new VncBridge.Sink() {
            public void onState(long h, int state, int error) {
                synchronized (callbacks) {
                    if (token != attempt || h != handle || h == 0 || stop.get()) return;
                    snapshot = new Snapshot(snapshot.state, error, completed, failed, releaseOutcome);
                    if (state == 2) { initialized = true; publish(firstFrame ? State.FIRST_FRAME : State.INITIALIZED); }
                }
            }
            public void onFrame(long h, int w, int he, byte[] rgba) {
                synchronized (callbacks) {
                    if (token != attempt || h != handle || h == 0 || stop.get() || rgba == null ||
                            w < 1 || he < 1 || w > 4096 || he > 4096 || (long) w * he > 8388608 ||
                            (long) w * he * 4 != rgba.length) return;
                    pending = new Frame(w, he, rgba); firstFrame = true;
                    if (initialized) publish(State.FIRST_FRAME);
                }
            }
            public void onInputResult(long h, long sequence, boolean written) {
                synchronized (callbacks) {
                    if (token != attempt || h != handle || h == 0 || sequence < 1) return;
                    completed = Math.max(completed, sequence);
                    if (!written) failed = Math.max(failed, sequence);
                    publish(snapshot.state);
                }
            }
        };
    }
    private void pollNative() {
        try {
            long h = handle;
            if (h == 0) return;
            long[] status = backend.status(h);
            if (status == null || status.length != 8 || status[0] < 0 || status[0] > 4) {
                stop.set(true); cleanup(1); return;
            }
            synchronized (callbacks) {
                snapshot = new Snapshot(snapshot.state, (int) status[1], completed, failed, releaseOutcome);
            }
            if (status[0] >= 3 || stop.get()) { stop.set(true); cleanup(1); }
        } catch (RuntimeException | LinkageError error) { stop.set(true); cleanup(1); }
    }
    /** Nonblocking, idempotent request. A failed join/free leaves the handle and lease retained.
     * No input-release write or cancellation can be treated as remote receipt confirmation.
     */
    public void stop() {
        synchronized (callbacks) {
            // Publish the first request in the same critical section as its CAS: poll cleanup
            // must not finish UNCERTAIN between admission of this stop and its state publication.
            if (!stop.compareAndSet(false, true)) return;
            pending = null; if (handle != 0) publish(State.STOP_REQUESTED);
        }
        try { owner.execute(() -> cleanup(1)); }
        catch (RejectedExecutionException error) { /* Retired/no-handle owner has no cleanup to retry. */ }
    }
    /** Explicit retry only, after observing CLEANUP_UNCERTAIN. No automatic retry/reset/reconnect. */
    public void retryCleanup() {
        final int authorizedAttempt;
        synchronized (callbacks) {
            if (snapshot.state != State.CLEANUP_UNCERTAIN || retryQueued) return;
            retryQueued = true;
            authorizedAttempt = cleanupAttempts + 1;
        }
        try { owner.execute(() -> cleanup(authorizedAttempt)); }
        catch (RejectedExecutionException error) { synchronized (callbacks) { retryQueued = false; } }
    }
    private void cleanup(int authorizedAttempt) {
        final long h;
        synchronized (callbacks) {
            // The owner consumes an authorization once before any native call. A stale queued stop
            // cannot turn a direct attempt's uncertainty into a retry or overwrite its outcome.
            if (authorizedAttempt != cleanupAttempts + 1 || handle == 0) return;
            cleanupAttempts = authorizedAttempt;
            retryQueued = false;
            h = handle; pending = null; publish(State.STOP_REQUESTED);
        }
        if (poll != null) { poll.cancel(false); poll = null; }
        try {
            releaseBeforeCancel(h);
            if (!backend.cancel(h) || !backend.join(h, 5000)) { publish(State.CLEANUP_UNCERTAIN); return; }
            backend.free(h);
            synchronized (callbacks) {
                handle = 0; pending = null; LEASE.compareAndSet(this, null); publish(State.RETIRED);
            }
            owner.shutdown();
        } catch (RuntimeException | LinkageError error) { publish(State.CLEANUP_UNCERTAIN); }
    }
    private void releaseBeforeCancel(long h) {
        if (releaseAttempted) return;
        releaseAttempted = true;
        try {
            long[] status = backend.status(h);
            if (!initialized || status == null || status.length != 8 || status[0] != 2) {
                releaseOutcome = ReleaseOutcome.NOT_APPLICABLE; return;
            }
            long sequence = backend.release(h);
            if (sequence <= 0) { releaseOutcome = ReleaseOutcome.REJECTED; return; }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750);
            while (true) {
                synchronized (callbacks) {
                    // No new inputs are admitted after stop, so release is the final sequence.
                    if (completed >= sequence) {
                        releaseOutcome = failed >= sequence ? ReleaseOutcome.WRITE_FAILED :
                            ReleaseOutcome.WRITTEN_NOT_ACKNOWLEDGED;
                        return;
                    }
                }
                status = backend.status(h);
                if (status == null || status.length != 8) { releaseOutcome = ReleaseOutcome.FAILED; return; }
                if (status[0] >= 3) { releaseOutcome = ReleaseOutcome.TERMINAL_BEFORE_RESULT; return; }
                if (System.nanoTime() >= deadline) { releaseOutcome = ReleaseOutcome.TIMED_OUT; return; }
                Thread.sleep(10);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); releaseOutcome = ReleaseOutcome.FAILED;
        } catch (RuntimeException | LinkageError error) { releaseOutcome = ReleaseOutcome.FAILED; }
        finally { publish(snapshot.state); }
    }
    private void finishWithoutHandle(byte[] username, byte[] password) {
        wipe(username); wipe(password); LEASE.compareAndSet(this, null);
        publish(State.FAILED); owner.shutdown();
    }
    public boolean pointer(int x, int y, int buttons) { return input(() -> backend.pointer(handle, x, y, buttons)); }
    public boolean key(int keysym, boolean down) { return input(() -> backend.key(handle, keysym, down)); }
    /** Queue actual tracked key/button release; return value is Java admission only.
     * Snapshot sequence high-water marks are diagnostics, not per-release or host ACK evidence.
     */
    public boolean releaseInputs() { return input(() -> backend.release(handle)); }
    private interface Input { long send(); }
    private boolean input(Input operation) {
        if (stop.get() || !(snapshot.state == State.INITIALIZED || snapshot.state == State.FIRST_FRAME)) return false;
        if (queuedInputs.incrementAndGet() > 64) { queuedInputs.decrementAndGet(); return false; }
        try {
            owner.execute(() -> {
                try { if (!stop.get() && handle != 0 && operation.send() == 0) stop(); }
                catch (RuntimeException | LinkageError error) { stop(); }
                finally { queuedInputs.decrementAndGet(); }
            });
            return true;
        } catch (RejectedExecutionException error) { queuedInputs.decrementAndGet(); return false; }
    }
    private void publish(State state) {
        synchronized (callbacks) { snapshot = new Snapshot(state, snapshot.nativeError, completed, failed, releaseOutcome); }
    }
    private static boolean validSecret(byte[] bytes, int max) {
        if (bytes == null || bytes.length == 0 || bytes.length > max) return false;
        for (byte value : bytes) if (value == 0) return false;
        CharBuffer characters = CharBuffer.allocate(bytes.length);
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            return !decoder.decode(ByteBuffer.wrap(bytes), characters, true).isError() &&
                !decoder.flush(characters).isError();
        } finally { Arrays.fill(characters.array(), '\0'); }
    }
    private static void wipe(byte[] bytes) { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
}
