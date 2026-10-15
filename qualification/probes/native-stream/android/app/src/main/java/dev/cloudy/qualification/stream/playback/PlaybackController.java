package dev.cloudy.qualification.stream.playback;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.preferences.PreferenceConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Actual upstream connection/codec/audio. Parent retains process, Activity and Surface ownership. */
public final class PlaybackController implements NvConnection.CloudyPlaybackControl,NvConnectionListener {
    public interface Guard { void check() throws IOException; }
    public enum Phase { CREATED, PREPARING, STARTING, STREAMING, STOPPING, CLEANUP_PENDING, RETIRED }
    public enum Failure { NONE, OWNER_OR_AUTHORIZATION, START, NATIVE_TERMINATED, DECODER, HTTP, CLEANUP, UNAVAILABLE }
    public enum StopReason { NONE, OPERATOR, DEADLINE_OR_AUTHORIZATION, SURFACE_LOST, NATIVE_TERMINATED, FAILURE }
    public static final class Result {
        public final Phase phase;
        public final Failure failure;
        public final StopReason stopReason;
        public final boolean started,cleanupComplete,glRendererKnown,hardwareReported;
        public final int nativeStartResult,terminationCode,width,height,format;
        public final long decodeUnitSubmissions,outputBuffersReleasedForRender,frameRenderedCallbacks,httpCalls,httpBytes,stageCallbacks;
        public final String codecName;
        private Result(PlaybackController owner) {
            phase=owner.phase; failure=owner.failure.get(); stopReason=owner.stopReason.get();
            started=owner.connectionStarted.get(); cleanupComplete=owner.retired.get(); glRendererKnown=owner.glKnown;
            nativeStartResult=owner.nativeStartResult; terminationCode=owner.terminationCode;
            MediaCodecDecoderRenderer decoder=owner.decoder;
            long[] counts=decoder==null ? new long[6] : decoder.cloudyMetrics();
            decodeUnitSubmissions=counts[0]; outputBuffersReleasedForRender=counts[1]; frameRenderedCallbacks=counts[2];
            width=(int)counts[3]; height=(int)counts[4]; format=(int)counts[5];
            String name=decoder==null ? "unavailable" : decoder.cloudyCodecName();
            codecName=name.length()<=128 ? name : "unavailable";
            hardwareReported=decoder!=null && decoder.cloudyHardwareReported();
            httpCalls=owner.httpCalls.get(); httpBytes=owner.httpBytes.get(); stageCallbacks=owner.stages.get();
        }
    }
    private static final PlaybackProcessLease PROCESS=new PlaybackProcessLease();
    private final PlaybackProfile profile;
    private final LimelightCryptoProvider provider;
    private final Guard owner;
    private final PlaybackLease lease;
    private final AtomicBoolean used=new AtomicBoolean(),cancelled=new AtomicBoolean(),retired=new AtomicBoolean(),connectionStarted=new AtomicBoolean();
    private final AtomicReference<Failure> failure=new AtomicReference<>(Failure.NONE);
    private final AtomicReference<StopReason> stopReason=new AtomicReference<>(StopReason.NONE);
    private final AtomicLong httpCalls=new AtomicLong(),httpBytes=new AtomicLong(),stages=new AtomicLong();
    private final CountDownLatch stop=new CountDownLatch(1),runFinished=new CountDownLatch(1);
    private volatile Thread runThread;
    private final Object cleanupLock=new Object(),engineLock=new Object();
    private volatile Result finalResult;
    private volatile Phase phase=Phase.CREATED;
    private volatile NvConnection connection;
    private volatile MediaCodecDecoderRenderer decoder;
    private volatile ScheduledExecutorService watchdog;
    private volatile Activity activity;
    private volatile boolean glKnown;
    private volatile int nativeStartResult=Integer.MIN_VALUE,terminationCode;
    public PlaybackController(PlaybackProfile profile,LimelightCryptoProvider qualifiedProvider,Guard owner,
            long authenticatedAtElapsedMillis,long durationMillis) {
        if (profile==null || qualifiedProvider==null || owner==null) throw new IllegalArgumentException("owner-required");
        this.profile=profile; provider=qualifiedProvider; this.owner=owner;
        lease=new PlaybackLease(SystemClock::elapsedRealtime,authenticatedAtElapsedMillis,durationMillis);
    }
    public static boolean processActive() { return PROCESS.active(); }
    public static boolean requiresFreshProcess() { return PROCESS.requiresFreshProcess(); }
    public boolean retired() { return retired.get(); }
    public Result snapshot() { Result terminal=finalResult; return terminal!=null ? terminal : new Result(this); }
    public void cancel() { stop(StopReason.OPERATOR); }
    public void surfaceLost() { stop(StopReason.SURFACE_LOST); }
    private void stop(StopReason reason) {
        synchronized(engineLock) {
            if (retired.get()) return;
            stopReason.compareAndSet(StopReason.NONE,reason); cancelled.set(true); lease.cancel();
            NvConnection current=connection;
            if (current!=null) current.cloudyCancel();
        }
        stop.countDown();
    }
    private void fail(Failure value) { failure.compareAndSet(Failure.NONE,value); stop(StopReason.FAILURE); }
    /** Blocking serialized-worker API. Surface ownership does not transfer to this class. */
    public Result run(Activity activity,Surface surface) {
        workerRequired();
        if (!used.compareAndSet(false,true)) throw new IllegalStateException("single-use");
        if (!PROCESS.claim(this)) { failure.set(Failure.UNAVAILABLE); retired.set(true); phase=Phase.RETIRED; return snapshot(); }
        runThread=Thread.currentThread();
        this.activity=activity;
        try {
            check();
            if (activity==null || surface==null || !surface.isValid()) throw new IllegalArgumentException("surface-required");
            phase=Phase.PREPARING;
            watchdog=Executors.newSingleThreadScheduledExecutor();
            watchdog.scheduleWithFixedDelay(() -> {
                try { check(); }
                catch (IOException | RuntimeException unavailable) { stop(StopReason.DEADLINE_OR_AUTHORIZATION); }
                // Core resets ConnectionInterrupted during LiStartConnection. A cancel that
                // races entry is reasserted both here and at every native stage callback.
                synchronized(engineLock) {
                    NvConnection current=connection;
                    if (!retired.get() && cancelled.get() && current!=null) current.cloudyRecheckCancellation();
                }
            },0,50,TimeUnit.MILLISECONDS);
            String renderer=GlRendererProbe.query(); glKnown=!renderer.isEmpty();
            MediaCodecHelper.initialize(activity.getApplicationContext(),renderer);
            PreferenceConfiguration prefs=new PreferenceConfiguration();
            prefs.width=1280; prefs.height=720; prefs.fps=60; prefs.bitrate=10000;
            prefs.videoFormat=PreferenceConfiguration.FormatOption.FORCE_H264;
            prefs.framePacing=PreferenceConfiguration.FRAME_PACING_MIN_LATENCY;
            prefs.enableHdr=false; prefs.enablePerfOverlay=false; prefs.fullRange=false;
            MediaCodecDecoderRenderer video=new MediaCodecDecoderRenderer(activity,prefs,
                    error -> fail(Failure.DECODER),0,false,false,renderer,text -> { /* No peer/video text crosses to UI. */ });
            video.cloudySetRenderTarget(surface); decoder=video;
            if (!video.isAvcSupported()) throw new IllegalStateException("avc-unavailable");
            NvConnection engine=new NvConnection(activity.getApplicationContext(),
                    new ComputerDetails.AddressTuple(profile.address,profile.httpPort),profile.httpsPort,
                    profile.id,profile.configuration(),provider,profile.certificate);
            synchronized(engineLock) { connection=engine; if (cancelled.get()) engine.cloudyCancel(); }
            check();
            phase=Phase.STARTING;
            nativeStartResult=engine.cloudyStartBlocking(new AndroidAudioRenderer(activity.getApplicationContext(),false),video,this,this);
            if (nativeStartResult!=0) fail(Failure.START);
            else {
                phase=Phase.STREAMING;
                stop.await();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); fail(Failure.OWNER_OR_AUTHORIZATION);
        } catch (Exception | LinkageError unavailable) { fail(Failure.START); }
        finally { try { retryCleanupBlocking(); } finally { runThread=null; runFinished.countDown(); } }
        return snapshot();
    }
    /** A failed/hung cleanup retains the process lease and Surface ownership; parent must not replace it. */
    public boolean retryCleanupBlocking() {
        workerRequired();
        if (retired.get()) return true;
        if (!used.get()) return false;
        if (runFinished.getCount()!=0 && Thread.currentThread()!=runThread) { cancel(); return false; }
        synchronized(cleanupLock) {
            if (retired.get()) return true;
            stop(StopReason.OPERATOR); phase=Phase.STOPPING;
            try {
                MediaCodecDecoderRenderer video=decoder;
                if (video!=null) video.prepareForStop();
                NvConnection engine=connection;
                if (engine!=null) engine.cloudyStopBlocking();
                if (video!=null) { video.cloudyRetire(); }
                ScheduledExecutorService timer=watchdog;
                if (timer!=null) {
                    timer.shutdown();
                    boolean interrupted=false;
                    for (;;) {
                        try { if (timer.awaitTermination(100,TimeUnit.MILLISECONDS)) break; }
                        catch (InterruptedException ignored) { interrupted=true; }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                }
                synchronized(engineLock) {
                    phase=Phase.RETIRED; retired.set(true); finalResult=new Result(this);
                    connection=null; decoder=null; activity=null;
                    PROCESS.release(this);
                }
                return true;
            } catch (Exception | LinkageError incomplete) {
                failure.compareAndSet(Failure.NONE,Failure.CLEANUP); phase=Phase.CLEANUP_PENDING; return false;
            }
        }
    }
    private static void workerRequired() { if (Looper.myLooper()==Looper.getMainLooper()) throw new IllegalStateException("worker-required"); }
    @Override public void check() throws IOException {
        lease.remaining(); owner.check();
        Activity current=activity;
        if (current!=null && Build.VERSION.SDK_INT>=37 && current.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)!=PackageManager.PERMISSION_GRANTED) throw new IOException("network-permission-required");
        try { provider.getClientCertificate(); }
        catch (RuntimeException unavailable) { throw new IOException("identity-unavailable"); }
    }
    @Override public void verifyLeaf(X509Certificate leaf) throws CertificateException { profile.verifyLeaf(leaf); }
    @Override public void verifySessionUrl(String url) throws IOException { check(); profile.verifySessionUrl(url); }
    @Override public ResponseBody execute(OkHttpClient original,Request request) throws IOException {
        check();
        if (!request.url().isHttps() || !profile.address.equals(request.url().host()) || request.url().port()!=profile.httpsPort
                || !"GET".equals(request.method()) || !PlaybackPolicy.requestPath(request.url().encodedPath())) throw new IOException("request-rejected");
        long remaining=lease.remaining();
        OkHttpClient client=original.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .callTimeout(remaining,TimeUnit.MILLISECONDS).connectTimeout(Math.min(5000,remaining),TimeUnit.MILLISECONDS)
                .readTimeout(remaining,TimeUnit.MILLISECONDS).writeTimeout(Math.min(5000,remaining),TimeUnit.MILLISECONDS).build();
        okhttp3.Call call=client.newCall(request); PlaybackLease.Call active=call::cancel; lease.register(active);
        httpCalls.incrementAndGet();
        try (Response response=call.execute()) {
            check();
            ResponseBody body=response.body();
            if (!response.isSuccessful() || body==null || body.contentLength()>1_048_576) throw new IOException("response-rejected");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[8192];
            try (InputStream input=body.byteStream()) {
                for (int size;(size=input.read(buffer))!=-1;) {
                    check(); if (size>1_048_576-bytes.size()) throw new IOException("body-limit"); bytes.write(buffer,0,size);
                }
            }
            check(); httpBytes.addAndGet(bytes.size());
            return ResponseBody.create(bytes.toByteArray(),MediaType.get("application/xml; charset=utf-8"));
        } catch (IOException rejected) {
            failure.compareAndSet(Failure.NONE,Failure.HTTP);
            throw new IOException("controlled-http-failed");
        } finally { lease.retire(active); }
    }
    @Override public void stageStarting(String ignored) {
        stages.incrementAndGet();
        synchronized(engineLock) {
            NvConnection current=connection;
            if (!retired.get() && current!=null) current.cloudyRecheckCancellation();
        }
    }
    @Override public void stageComplete(String ignored) { stages.incrementAndGet(); }
    @Override public void stageFailed(String ignored,int portFlags,int errorCode) { terminationCode=errorCode; fail(Failure.START); }
    @Override public void connectionStarted() { connectionStarted.set(true); if (cancelled.get()) stop(stopReason.get()); }
    @Override public void connectionTerminated(int errorCode) { terminationCode=errorCode; failure.compareAndSet(Failure.NONE,Failure.NATIVE_TERMINATED); stop(StopReason.NATIVE_TERMINATED); }
    @Override public void connectionStatusUpdate(int status) { stages.incrementAndGet(); }
    @Override public void displayMessage(String ignored) { stages.incrementAndGet(); }
    @Override public void displayTransientMessage(String ignored) { stages.incrementAndGet(); }
    // This preview negotiates no controllers/HDR. Observe unsupported callbacks without fake effects.
    @Override public void rumble(short number,short low,short high) { stages.incrementAndGet(); }
    @Override public void rumbleTriggers(short number,short left,short right) { stages.incrementAndGet(); }
    @Override public void setHdrMode(boolean enabled,byte[] metadata) { if (enabled) fail(Failure.DECODER); }
    @Override public void setMotionEventState(short number,byte motion,short rate) { stages.incrementAndGet(); }
    @Override public void setControllerLED(short number,byte r,byte g,byte b) { stages.incrementAndGet(); }
}
