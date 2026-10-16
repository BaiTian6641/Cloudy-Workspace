package dev.cloudy.qualification.vnc;

import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** One retained owner serializes native calls and journal writes. Callbacks never wait on it. */
public final class VncSession implements VncBridge.Sink {
    private static final AtomicReference<VncSession> LEASE = new AtomicReference<>();
    private static final AtomicBoolean JOURNAL_BLOCKED = new AtomicBoolean();
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledThreadPoolExecutor owner = new ScheduledThreadPoolExecutor(1);
    private final AtomicBoolean noticePending = new AtomicBoolean();
    private final AtomicReference<Frame> pendingFrame = new AtomicReference<>();
    private final AtomicInteger pendingInputs = new AtomicInteger();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicLong callbackFrames = new AtomicLong(), convertedFrames = new AtomicLong(), drawnFrames = new AtomicLong();
    private final AtomicLong resultSequence = new AtomicLong(), failedSequence = new AtomicLong();
    private volatile Runnable listener;
    private volatile VncSnapshot snapshot = new VncSnapshot("idle", "not-qualified", false, false, new long[6], 0, 0);
    private volatile VncDiagnostics diagnostics = new VncDiagnostics();
    private volatile long handle;
    private volatile boolean admission, closed, stopRequested;
    private boolean stopping, cancelled, joined, started, freed, journalFailed;
    private long releaseSequence, releaseDeadline, deadline, startElapsed;
    private long[] nativeStatus = new long[8];
    private String state = "idle", detail = "not-qualified", reason = "none", releaseResult = "none";
    private File journal;
    private JSONObject record;
    // Bitmap access is main-thread only. A software Canvas completes consumption before mutation.
    private Bitmap bitmap;
    private long bitmapSequence, lastDrawn;
    private final AtomicLong frameSequence = new AtomicLong();
    private static final class Frame {
        final long handle, sequence; final int width, height; final byte[] rgba;
        Frame(long handle, long sequence, int width, int height, byte[] rgba) { this.handle=handle; this.sequence=sequence; this.width=width; this.height=height; this.rgba=rgba; }
    }
    public VncSession(Context context) {
        this.context = context.getApplicationContext();
        owner.setRemoveOnCancelPolicy(true);
        owner.scheduleWithFixedDelay(() -> guarded(this::poll), 100, 100, TimeUnit.MILLISECONDS);
    }
    public VncSnapshot snapshot() { return snapshot; }
    public void setListener(Runnable value) { listener = value; if (value != null) notifyChanged(); }
    private void notifyChanged() {
        if (!noticePending.compareAndSet(false, true)) return;
        main.post(() -> {
            noticePending.set(false);
            applyLatestFrame();
            Runnable current = listener;
            if (current != null) current.run();
        });
    }
    public void load() { begin(null, null); }
    /** Ownership of both arrays transfers even when work is rejected. */
    public void connect(byte[] username, byte[] password) { begin(username, password); }
    private synchronized void begin(byte[] username, byte[] password) {
        if (closed || snapshot.busy) { wipe(username); wipe(password); return; }
        if (JOURNAL_BLOCKED.get() || !LEASE.compareAndSet(null, this)) {
            wipe(username); wipe(password);
            snapshot=new VncSnapshot("unavailable",JOURNAL_BLOCKED.get() ? "journal-blocked" : "another-owner",false,false,new long[6],0,0);
            notifyChanged(); return;
        }
        final VncDiagnostics attemptDiagnostics=new VncDiagnostics();
        attemptDiagnostics.mark(VncDiagnostics.Time.ACCEPTED,SystemClock.elapsedRealtime());
        diagnostics=attemptDiagnostics;
        stopRequested=false;
        snapshot = new VncSnapshot("starting", "worker-queued", true, false, new long[6], 0, 0);
        pendingFrame.set(null); bitmap = null; bitmapSequence=0; lastDrawn=0;
        notifyChanged();
        boolean queued=enqueue(() -> {
            try {
                reset();
                attemptDiagnostics.mark(VncDiagnostics.Time.WORKER,SystemClock.elapsedRealtime());
                try { reserveJournal(username == null ? "load" : "session"); }
                catch (Exception failure) { journalFailed=true; JOURNAL_BLOCKED.set(true); state="blocked"; detail="journal-failure"; throw failure; }
                VncBridge.load();
                if (username == null) { state="load-passed"; detail="zero-connections"; finishWithoutHandle(); return; }
                if (closed || stopRequested) { state="cancelled"; finishWithoutHandle(); return; }
                if (Build.VERSION.SDK_INT>=37 && context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)!=PackageManager.PERMISSION_GRANTED) throw new SecurityException();
                VncProfile profile = VncProfile.read(context);
                try {
                    handle = VncBridge.create(profile.id, profile.address, profile.port, profile.pin, username, password, profile.duration, new VncBridge.Sink() {
                        // Captured attempt identity rejects callbacks after native address reuse.
                        @Override public void onState(long id,int value,int error) { if (diagnostics==attemptDiagnostics) VncSession.this.onState(id,value,error); }
                        @Override public void onFrame(long id,int width,int height,byte[] rgba) { if (diagnostics==attemptDiagnostics) VncSession.this.onFrame(id,width,height,rgba); }
                        @Override public void onInputResult(long id,long sequence,boolean written) { if (diagnostics==attemptDiagnostics) VncSession.this.onInputResult(id,sequence,written); }
                    });
                } finally { wipe(profile.pin); wipe(username); wipe(password); }
                if (handle == 0) throw new IllegalStateException();
                deadline = SystemClock.elapsedRealtime() + profile.duration * 1000L;
                if (closed || stopRequested) { stopping=true; reason="cancelled-before-start"; }
                else { attemptDiagnostics.mark(VncDiagnostics.Time.NATIVE_START,SystemClock.elapsedRealtime()); started = VncBridge.start(handle); if (!started) { stopping=true; reason="start-rejected"; } }
                state = started ? "connecting" : "closing";
                persist(false); poll();
            } catch (Exception | LinkageError failure) {
                fail(journalFailed ? "journal-failure" : "setup-failure");
                if (handle == 0) finishWithoutHandle();
            } finally { wipe(username); wipe(password); }
        });
        if (!queued) {
            wipe(username); wipe(password); LEASE.compareAndSet(this,null);
            snapshot=new VncSnapshot("unavailable","owner-retired",false,false,new long[6],0,0); notifyChanged();
        }
    }
    private boolean enqueue(Runnable action) {
        try { owner.execute(action); return true; }
        catch (RejectedExecutionException retired) { return false; }
    }
    private void reset() {
        stopping=false; cancelled=false; joined=false; started=false; freed=false; journalFailed=false; journal=null; record=null;
        releaseSequence=0; deadline=0; releaseDeadline=0; resultSequence.set(0); failedSequence.set(0);
        initialized.set(false); callbackFrames.set(0); convertedFrames.set(0); drawnFrames.set(0); nativeStatus=new long[8];
        state="starting"; detail="none"; reason="none"; releaseResult="none"; startElapsed=diagnostics.time(VncDiagnostics.Time.ACCEPTED);
    }
    private void reserveJournal(String type) throws Exception {
        File directory = new File(context.getFilesDir(), "vnc-journal");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException();
        File[] files = directory.listFiles();
        if (files == null || files.length >= 16) throw new IllegalStateException();
        // A prior process may have died with a live native worker. Never silently adopt,
        // repair, overwrite or declare such an attempt closed after process restoration.
        for (File previous:files) {
            if (!previous.isFile() || !previous.getName().endsWith(".json") || previous.length()>8192) throw new IllegalStateException();
            byte[] bytes=Files.readAllBytes(previous.toPath());
            if (bytes.length>8192 || !new JSONObject(new String(bytes,StandardCharsets.UTF_8)).getBoolean("closed")) throw new IllegalStateException();
        }
        String id=UUID.randomUUID().toString(); journal=new File(directory,id+".json");
        if (journal.exists()) throw new IllegalStateException();
        record=new JSONObject(); record.put("schema",1); record.put("attemptId",id); record.put("kind",type);
        record.put("version",BuildConfig.VERSION_NAME); record.put("buildRef",BuildConfig.QUALIFICATION_BUILD_REF);
        record.put("nativeBuildRef",BuildConfig.NATIVE_BUILD_REF); record.put("startedElapsedMs",startElapsed);
        persist(false);
        if (journalFailed) throw new IllegalStateException();
    }
    private void persist(boolean complete) {
        if (journalFailed || journal == null || record == null) return;
        AtomicFile atomic = new AtomicFile(journal); FileOutputStream out=null;
        try {
            record.put("state",state); record.put("detail",detail); record.put("reason",reason);
            record.put("closed",complete); record.put("threadStarted",started); record.put("joined",joined);
            record.put("freed",freed); record.put("elapsedMs",SystemClock.elapsedRealtime()-startElapsed);
            record.put("connectionsInitialized",initialized.get() ? 1 : 0);
            record.put("nativeState",nativeStatus[0]); record.put("nativeError",nativeStatus[1]);
            record.put("nativeFrameCallbacks",nativeStatus[2]); record.put("javaFrameCallbacks",callbackFrames.get());
            record.put("convertedFrames",convertedFrames.get()); record.put("uniqueFrameDraws",drawnFrames.get());
            record.put("admittedInputs",nativeStatus[3]); record.put("completedInputs",nativeStatus[4]); record.put("successfulWrites",nativeStatus[5]);
            record.put("width",nativeStatus[6]); record.put("height",nativeStatus[7]); record.put("releaseResult",releaseResult);
            JSONObject milestones=new JSONObject(), markers=new JSONObject();
            for (VncDiagnostics.Time time:VncDiagnostics.Time.values()) {
                long observed=diagnostics.time(time); milestones.put(time.name(),observed<0 ? JSONObject.NULL : observed);
            }
            for (VncDiagnostics.Marker marker:VncDiagnostics.Marker.values()) markers.put(marker.name(),diagnostics.countOf(marker));
            record.put("startupElapsedRealtimeMillis",milestones).put("f8FrameView",markers)
                    .put("f8WriteConfirmation","unavailable-no-per-key-sequence-correlation");
            out=atomic.startWrite(); out.write(record.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out);
        } catch (Exception error) {
            if (out != null) atomic.failWrite(out);
            journalFailed=true; JOURNAL_BLOCKED.set(true); admission=false; stopping=true; reason="journal-failure"; state="blocked"; detail="journal-failure";
        }
    }
    private void finishWithoutHandle() {
        admission=false; persist(true); publish(false);
        if (!journalFailed) LEASE.compareAndSet(this,null);
        if (closed) owner.shutdown();
    }
    public void disconnect() { requestStop("operator"); }
    public void hidden() { requestStop("not-visible"); }
    public void close() { closed=true; requestStop("destroyed"); }
    private void requestStop(String why) {
        if (snapshot.busy) diagnostics.mark(VncDiagnostics.Time.STOP,SystemClock.elapsedRealtime());
        admission=false; stopRequested=true;
        if (owner.isShutdown()) return;
        enqueue(() -> guarded(() -> {
            if (handle==0) { if (closed && !snapshot.busy) owner.shutdown(); return; }
            stopping=true; reason=why;
            releaseOnOwner(); poll();
        }));
    }
    public void releaseInput() {
        admission=false;
        enqueue(() -> guarded(this::releaseOnOwner));
    }
    private void releaseOnOwner() {
        if (handle==0 || releaseSequence!=0 || nativeStatus[0]!=2) return;
        releaseSequence=VncBridge.releaseInputs(handle);
        releaseDeadline=SystemClock.elapsedRealtime()+750;
        releaseResult=releaseSequence>0 ? "pending" : "rejected";
        if (releaseSequence==0) { stopping=true; reason="release-rejected"; }
    }
    public boolean key(int keysym, boolean down) { return submit(() -> VncBridge.key(handle,keysym,down)); }
    public boolean pointer(int x,int y,int mask) { return submit(() -> VncBridge.pointer(handle,x,y,mask)); }
    private interface Input { long send(); }
    private boolean submit(Input input) {
        if (!admission || closed) return false;
        if (pendingInputs.incrementAndGet()>64) { pendingInputs.decrementAndGet(); requestStop("input-backpressure"); return false; }
        boolean queued=enqueue(() -> { try {
            if (admission && handle!=0 && input.send()==0) fail("input-rejected");
        } catch (RuntimeException error) { fail("input-exception"); }
        finally { pendingInputs.decrementAndGet(); } });
        if (!queued) pendingInputs.decrementAndGet();
        return queued;
    }
    private void guarded(Runnable action) { try { action.run(); } catch (Exception | LinkageError error) { fail("owner-failure"); } }
    private void fail(String why) { admission=false; stopping=true; state="failed"; detail=why; if (reason.equals("none")) reason=why; persist(false); publish(true); }
    private void poll() {
        if (handle==0) return;
        nativeStatus=VncBridge.status(handle);
        if (nativeStatus.length!=8) throw new IllegalStateException();
        long now=SystemClock.elapsedRealtime();
        if (deadline>0 && now>=deadline && !stopping) { stopping=true; admission=false; reason="deadline"; releaseOnOwner(); }
        if (releaseSequence>0) {
            if (failedSequence.get()>=releaseSequence) { releaseResult="write-failed"; releaseSequence=0; stopping=true; reason="release-write-failed"; }
            else if (resultSequence.get()>=releaseSequence) { releaseResult="written-not-host-confirmed"; releaseSequence=0; }
            else if (now>=releaseDeadline) { releaseResult="timeout"; releaseSequence=0; stopping=true; reason="release-timeout"; }
        }
        boolean terminal=nativeStatus[0]>=3;
        if (terminal) { if (releaseSequence>0) { releaseResult="terminal-before-confirmation"; releaseSequence=0; } stopping=true; admission=false; if (reason.equals("none")) reason="native-terminal"; }
        if (stopping && VncLifetime.mayCancel(releaseSequence,now,releaseDeadline,terminal)) {
            admission=false;
            if (!cancelled && !terminal) { diagnostics.mark(VncDiagnostics.Time.STOP,SystemClock.elapsedRealtime()); persist(false); VncBridge.cancel(handle); cancelled=true; }
            if (terminal || cancelled || !started) {
                if (VncBridge.join(handle,0)) {
                    diagnostics.mark(VncDiagnostics.Time.JOINED,SystemClock.elapsedRealtime());
                    joined=true; nativeStatus=VncBridge.status(handle); state=nativeStatus[1]==0 ? "closed" : "native-terminal";
                    detail="native-error-"+nativeStatus[1]; persist(false);
                    if (!VncLifetime.mayFree(started,joined)) throw new IllegalStateException();
                    VncBridge.free(handle); freed=true; handle=0; pendingFrame.set(null);
                    persist(true); publish(false);
                    if (!journalFailed) LEASE.compareAndSet(this,null);
                    if (closed) owner.shutdown();
                    return;
                }
                state="cleanup-pending";
            }
        } else if (!stopping) {
            state=nativeStatus[0]==2 ? "connected" : "connecting";
            admission=nativeStatus[0]==2 && releaseSequence==0 && !stopRequested && !closed;
        }
        publish(true);
    }
    public void marker(VncDiagnostics.Marker marker) { if (snapshot.busy) diagnostics.count(marker); }
    private void publish(boolean busy) {
        snapshot=new VncSnapshot(journalFailed ? "blocked" : state,journalFailed ? "journal-failure" : busy && !stopping ? diagnostics.phase() : detail,busy,handle!=0 && nativeStatus[0]==2 && !stopping,new long[]{callbackFrames.get(),convertedFrames.get(),drawnFrames.get(),nativeStatus[3],nativeStatus[4],nativeStatus[5]},(int)nativeStatus[6],(int)nativeStatus[7]);
        notifyChanged();
    }
    @Override public void onState(long id,int value,int error) { if (id==handle) { if (value==2) { initialized.set(true); diagnostics.mark(VncDiagnostics.Time.CONNECTED,SystemClock.elapsedRealtime()); } notifyChanged(); } }
    @Override public void onInputResult(long id,long sequence,boolean written) {
        if (id!=handle) return;
        if (!written) failedSequence.accumulateAndGet(sequence,Math::max);
        resultSequence.accumulateAndGet(sequence,Math::max);
    }
    @Override public void onFrame(long id,int width,int height,byte[] rgba) {
        if (id!=handle || !VncFramePolicy.valid(width,height,rgba.length)) return;
        diagnostics.mark(VncDiagnostics.Time.FRAME,SystemClock.elapsedRealtime());
        callbackFrames.incrementAndGet(); pendingFrame.set(new Frame(id,frameSequence.incrementAndGet(),width,height,rgba)); notifyChanged();
    }
    private void applyLatestFrame() {
        Frame frame=pendingFrame.getAndSet(null);
        if (frame==null || frame.handle!=handle) return;
        try {
            if (bitmap==null || bitmap.getWidth()!=frame.width || bitmap.getHeight()!=frame.height) bitmap=Bitmap.createBitmap(frame.width,frame.height,Bitmap.Config.ARGB_8888);
            int[] row=new int[frame.width];
            for (int y=0;y<frame.height;y++) { VncFramePolicy.row(frame.rgba,y*frame.width*4,row); bitmap.setPixels(row,0,frame.width,0,y,frame.width,1); }
            bitmapSequence=frame.sequence; convertedFrames.incrementAndGet(); diagnostics.mark(VncDiagnostics.Time.CONVERTED,SystemClock.elapsedRealtime());
        } catch (RuntimeException | OutOfMemoryError error) { requestStop("presentation-failure"); }
    }
    public boolean draw(Canvas canvas,RectF destination) {
        if (bitmap==null) return false;
        canvas.drawBitmap(bitmap,null,destination,null);
        if (lastDrawn!=bitmapSequence) { lastDrawn=bitmapSequence; drawnFrames.incrementAndGet(); diagnostics.mark(VncDiagnostics.Time.DRAWN,SystemClock.elapsedRealtime()); }
        return true;
    }
    private static void wipe(byte[] value) { if (value!=null) Arrays.fill(value,(byte)0); }
}
