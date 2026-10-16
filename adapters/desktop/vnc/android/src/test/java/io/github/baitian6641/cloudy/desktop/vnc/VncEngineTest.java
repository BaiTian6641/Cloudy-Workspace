package io.github.baitian6641.cloudy.desktop.vnc;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only backend and numeric-check seam. No JNI/library/server/Android permission evidence. */
public final class VncEngineTest {
    private static VncEngine.Config config() {
        return new VncEngine.Config("test", "127.0.0.1", 5901, new byte[32], 10,
            address -> { if (!address.equals("127.0.0.1")) throw new IllegalArgumentException(); });
    }
    private static class Backend implements VncEngine.Backend {
        volatile VncBridge.Sink sink;
        volatile boolean started, joined = true, refuseFree, createZero;
        volatile int releaseResult = 1;
        final List<String> operations = new CopyOnWriteArrayList<>();
        volatile Thread cleanupThread;
        final CountDownLatch refusedFree = new CountDownLatch(1);
        final CountDownLatch createEntered = new CountDownLatch(1), createProceed = new CountDownLatch(1);
        final CountDownLatch freed = new CountDownLatch(1);
        final boolean blockCreate;
        Backend(boolean block) { blockCreate = block; }
        public void load() {}
        public long create(VncEngine.Config c, byte[] u, byte[] p, VncBridge.Sink s) {
            sink = s; createEntered.countDown();
            if (blockCreate) try { if (!createProceed.await(2, TimeUnit.SECONDS)) throw new IllegalStateException(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
            return createZero ? 0 : 17;
        }
        public boolean start(long h) { started = true; sink.onState(h, 2, 0); return true; }
        public boolean cancel(long h) { cleanupThread=Thread.currentThread(); operations.add("cancel"); return true; }
        public boolean join(long h, int timeout) { operations.add("join"); return joined; }
        public void free(long h) {
            operations.add("free");
            if (refuseFree) { refusedFree.countDown(); throw new IllegalStateException("TEST_ONLY"); }
            freed.countDown();
        }
        public long[] status(long h) { return new long[]{2,0,0,0,0,0,1,1}; }
        public long pointer(long h,int x,int y,int m) { return 1; }
        public long key(long h,int k,boolean d) { return 1; }
        public long release(long h) {
            operations.add("release");
            if (releaseResult >= 0) sink.onInputResult(h,7,releaseResult == 1);
            return 7;
        }
    }
    private static void await(VncEngine engine, VncEngine.State state) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (engine.snapshot().state != state && System.nanoTime() < end) Thread.sleep(5);
        assertEquals(state, engine.snapshot().state);
    }
    private static byte[] secret() { return new byte[]{97}; }
    @Test public void stopDuringCreateRetainsThenJoinsAndWipesCredentials() throws Exception {
        Backend b = new Backend(true); VncEngine e = new VncEngine(null, b);
        byte[] user = secret(), password = secret();
        assertTrue(e.connect(config(), user, password)); assertTrue(b.createEntered.await(2,TimeUnit.SECONDS));
        e.stop(); b.createProceed.countDown(); await(e,VncEngine.State.RETIRED);
        assertFalse(b.started); assertEquals(0,user[0]); assertEquals(0,password[0]);
        assertTrue(b.freed.await(1,TimeUnit.SECONDS));
    }
    @Test public void joinTimeoutAndFreeRefusalNeverReleaseOwnerEarly() throws Exception {
        Backend b = new Backend(false); b.joined = false;
        VncEngine e = new VncEngine(null,b); assertTrue(e.connect(config(),secret(),secret()));
        await(e,VncEngine.State.INITIALIZED); e.stop(); await(e,VncEngine.State.CLEANUP_UNCERTAIN);
        Backend other = new Backend(false); VncEngine second = new VncEngine(null,other);
        byte[] user=secret(), password=secret(); assertFalse(second.connect(config(),user,password));
        assertEquals(0,user[0]); assertEquals(0,password[0]); assertEquals(1,b.freed.getCount());
        b.joined=true; b.refuseFree=true; e.retryCleanup(); assertTrue(b.refusedFree.await(2,TimeUnit.SECONDS));
        await(e,VncEngine.State.CLEANUP_UNCERTAIN);
        assertEquals(VncEngine.State.CLEANUP_UNCERTAIN,e.snapshot().state); assertEquals(1,b.freed.getCount());
        b.refuseFree=false; e.retryCleanup(); await(e,VncEngine.State.RETIRED);
    }
    @Test public void staleAndStoppedCallbacksCannotPublishOrRetainFrames() throws Exception {
        Backend b = new Backend(false); VncEngine e = new VncEngine(null,b);
        assertTrue(e.connect(config(),secret(),secret())); await(e,VncEngine.State.INITIALIZED);
        b.sink.onFrame(99,1,1,new byte[4]); assertNull(e.takeLatestFrame());
        byte[] first=new byte[4], latest=new byte[]{1,2,3,4};
        b.sink.onFrame(17,1,1,first); b.sink.onFrame(17,1,1,latest);
        assertSame(latest,e.takeLatestFrame().rgba); assertNull(e.takeLatestFrame());
        e.stop(); await(e,VncEngine.State.RETIRED);
        b.sink.onFrame(17,1,1,new byte[4]); b.sink.onState(17,2,0);
        assertNull(e.takeLatestFrame()); assertEquals(VncEngine.State.RETIRED,e.snapshot().state);
    }
    @Test public void deliberateStopReleasesBeforeCancelJoinFreeOffCallingThread() throws Exception {
        Backend b=new Backend(false); VncEngine e=new VncEngine(null,b);
        assertTrue(e.connect(config(),secret(),secret())); await(e,VncEngine.State.INITIALIZED);
        Thread caller=Thread.currentThread(); e.stop();
        assertFalse(e.key(65,true)); await(e,VncEngine.State.RETIRED);
        assertEquals(Arrays.asList("release","cancel","join","free"), b.operations);
        assertNotSame(caller,b.cleanupThread);
        assertEquals(VncEngine.ReleaseOutcome.WRITTEN_NOT_ACKNOWLEDGED,e.snapshot().releaseOutcome);
    }
    @Test public void releaseWriteFailureStillCancelsAndDoesNotClaimAck() throws Exception {
        Backend b=new Backend(false); b.releaseResult=0; VncEngine e=new VncEngine(null,b);
        assertTrue(e.connect(config(),secret(),secret())); await(e,VncEngine.State.INITIALIZED);
        e.stop(); await(e,VncEngine.State.RETIRED);
        assertEquals(Arrays.asList("release","cancel","join","free"),b.operations);
        assertEquals(VncEngine.ReleaseOutcome.WRITE_FAILED,e.snapshot().releaseOutcome);
    }
    @Test public void releaseTimeoutIsFiniteAndRetainedCleanupDoesNotReleaseTwice() throws Exception {
        Backend b=new Backend(false); b.releaseResult=-1; b.joined=false; VncEngine e=new VncEngine(null,b);
        assertTrue(e.connect(config(),secret(),secret())); await(e,VncEngine.State.INITIALIZED);
        long before=System.nanoTime(); e.stop(); await(e,VncEngine.State.CLEANUP_UNCERTAIN);
        assertTrue(System.nanoTime()-before >= TimeUnit.MILLISECONDS.toNanos(700));
        assertEquals(VncEngine.ReleaseOutcome.TIMED_OUT,e.snapshot().releaseOutcome);
        assertEquals(Arrays.asList("release","cancel","join"),b.operations);
        b.joined=true; e.retryCleanup(); await(e,VncEngine.State.RETIRED);
        assertEquals(Arrays.asList("release","cancel","join","cancel","join","free"),b.operations);
    }
    @Test public void invalidCredentialsAreWipedWithoutNativeAllocation() {
        Backend b = new Backend(false); VncEngine e = new VncEngine(null,b);
        byte[] malformed = {(byte)0xc0,(byte)0x80}, password=secret();
        assertFalse(e.connect(config(),malformed,password));
        assertTrue(Arrays.equals(new byte[2],malformed)); assertEquals(0,password[0]);
        assertEquals(1,b.createEntered.getCount());
    }
    @Test public void createRejectionReleasesLeaseAndDisposesSecrets() throws Exception {
        Backend b = new Backend(false); b.createZero=true; VncEngine e = new VncEngine(null,b);
        byte[] user=secret(), password=secret(); assertTrue(e.connect(config(),user,password));
        await(e,VncEngine.State.FAILED); assertEquals(0,user[0]); assertEquals(0,password[0]);
        Backend next = new Backend(false); VncEngine replacement = new VncEngine(null,next);
        assertTrue(replacement.connect(config(),secret(),secret())); await(replacement,VncEngine.State.INITIALIZED);
        replacement.stop(); await(replacement,VncEngine.State.RETIRED);
    }
    private static ScheduledThreadPoolExecutor ownerExecutor() {
        return new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "VncTestOwner"); thread.setDaemon(true); return thread;
        });
    }
    private static void latch(CountDownLatch value) {
        try { assertTrue(value.await(2, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private static void fence(ScheduledThreadPoolExecutor executor) {
        CountDownLatch drained = new CountDownLatch(1);
        executor.execute(drained::countDown); latch(drained);
    }
    @Test public void directPollAndQueuedStopShareOneCleanupAuthorization() throws Exception {
        CountDownLatch pollEntered = new CountDownLatch(1), pollProceed = new CountDownLatch(1);
        AtomicBoolean firstStatus = new AtomicBoolean(true);
        Backend backend = new Backend(false) {
            @Override public long[] status(long handle) {
                if (firstStatus.compareAndSet(true, false)) {
                    pollEntered.countDown(); latch(pollProceed);
                }
                // Real native termination is represented only by this clearly test-owned seam.
                return new long[]{3,0,0,0,0,0,1,1};
            }
        };
        backend.joined = false;
        ScheduledThreadPoolExecutor executor = ownerExecutor();
        VncEngine engine = new VncEngine(null, backend, executor);
        try {
            assertTrue(engine.connect(config(), secret(), secret())); latch(pollEntered);
            engine.stop(); // Queued behind the paused poll, whose direct cleanup will finish first.
            CountDownLatch queuedStopDrained = new CountDownLatch(1);
            executor.execute(queuedStopDrained::countDown);
            pollProceed.countDown(); latch(queuedStopDrained);
            assertEquals(VncEngine.State.CLEANUP_UNCERTAIN, engine.snapshot().state);
            assertEquals(Arrays.asList("cancel", "join"), backend.operations);
            assertEquals(1, backend.freed.getCount());
            backend.joined = true;
            engine.retryCleanup(); assertTrue(backend.freed.await(2, TimeUnit.SECONDS));
            await(engine, VncEngine.State.RETIRED);
            assertEquals(Arrays.asList("cancel", "join", "cancel", "join", "free"), backend.operations);
        } finally {
            pollProceed.countDown();
            // Test-only executor owner is retained until all observations are made; failure shutdown
            // does not stand in for a successful native free or release the engine's static lease.
            executor.shutdownNow(); assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
    @Test public void repeatedStopPreservesUncertaintyAndExplicitRetriesCoalesce() throws Exception {
        Backend backend = new Backend(false); backend.joined = false;
        ScheduledThreadPoolExecutor executor = ownerExecutor();
        VncEngine engine = new VncEngine(null, backend, executor);
        CountDownLatch retryGate = new CountDownLatch(1);
        try {
            assertTrue(engine.connect(config(), secret(), secret())); await(engine, VncEngine.State.INITIALIZED);
            engine.stop(); fence(executor);
            assertEquals(VncEngine.State.CLEANUP_UNCERTAIN, engine.snapshot().state);
            engine.stop(); engine.stop(); fence(executor);
            assertEquals(VncEngine.State.CLEANUP_UNCERTAIN, engine.snapshot().state);
            assertEquals(Arrays.asList("release", "cancel", "join"), backend.operations);
            CountDownLatch retryBlocked = new CountDownLatch(1);
            executor.execute(() -> { retryBlocked.countDown(); latch(retryGate); }); latch(retryBlocked);
            engine.retryCleanup(); engine.retryCleanup(); engine.stop();
            retryGate.countDown(); fence(executor);
            // Duplicate requests authorized against the first uncertainty cannot consume the next.
            assertEquals(VncEngine.State.CLEANUP_UNCERTAIN, engine.snapshot().state);
            assertEquals(Arrays.asList("release", "cancel", "join", "cancel", "join"), backend.operations);
            assertEquals(1, backend.freed.getCount());
            engine.stop(); fence(executor);
            assertEquals(VncEngine.State.CLEANUP_UNCERTAIN, engine.snapshot().state);
            backend.joined = true; engine.retryCleanup();
            assertTrue(backend.freed.await(2, TimeUnit.SECONDS)); await(engine, VncEngine.State.RETIRED);
            assertEquals(Arrays.asList("release", "cancel", "join", "cancel", "join", "cancel", "join", "free"), backend.operations);
        } finally {
            retryGate.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

}
