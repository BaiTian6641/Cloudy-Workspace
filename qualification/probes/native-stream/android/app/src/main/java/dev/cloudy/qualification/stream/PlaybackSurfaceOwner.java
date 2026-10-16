package dev.cloudy.qualification.stream;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import android.view.Surface;
import java.util.concurrent.atomic.AtomicLong;

/** One main-thread handoff; the old Activity is retained only until real decoder retirement. */
public final class PlaybackSurfaceOwner {
    private static final AtomicLong GENERATION=new AtomicLong();
    private final long generation=GENERATION.incrementAndGet();
    private final Surface surface;
    private final SurfaceTexture texture;
    private volatile Activity activity;
    private Runnable detach;
    private volatile boolean detached,released;
    private long operation;
    private final PresentationTracker presentation=new PresentationTracker();
    public PlaybackSurfaceOwner(Activity activity,SurfaceTexture texture,Runnable detach) {
        main(); this.activity=activity; this.texture=texture; this.detach=detach; surface=new Surface(texture);
    }
    public boolean bind(long token) { main(); if (token<=0 || operation!=0 || detached || released || !surface.isValid() || !presentation.begin(SystemClock.elapsedRealtime())) return false; operation=token; return true; }
    public boolean ready() { return !detached && !released && surface.isValid() && presentation.latest().admissible(); }
    public long generation() { return generation; }
    public boolean matches(long token,long expectedGeneration) { return PlaybackCompletionPolicy.sameSurface(token,operation,generation,expectedGeneration); }
    public boolean bound() { return operation!=0; }
    public boolean detached() { return detached; }
    public boolean released() { return released; }
    public Activity activity() { return activity; }
    public Surface surface() { return surface; }
    public void destroyed() {
        main();detached=true;
        PresentationTracker.Geometry old=presentation.latest();
        presentation.observe(PresentationTracker.Edge.DESTRUCTION,SystemClock.elapsedRealtime(),
                new PresentationTracker.Geometry(old.width,old.height,0,0,false,false,old.hardware));
    }
    public void observePresentation(PresentationTracker.Edge edge,PresentationTracker.Geometry geometry) {
        main();if (!released) presentation.observe(edge,SystemClock.elapsedRealtime(),geometry);
    }
    public boolean textureUpdated() { main();return !released && presentation.updated(SystemClock.elapsedRealtime()); }
    public String presentationSummary() {
        PresentationTracker.Snapshot snapshot=presentation.snapshot();
        return "textureUpdates="+snapshot.updates+" · visible="+snapshot.latest.visibleWidth+"×"+snapshot.latest.visibleHeight
                +" · attached="+snapshot.latest.attached+" · shown="+snapshot.latest.shown+" · hardwareWindow="+snapshot.latest.hardware;
    }
    /** Worker-safe: immutable aggregate data only; never calls a View off main. */
    public JSONObject presentationJson() throws JSONException {
        PresentationTracker.Snapshot snapshot=presentation.snapshot();
        JSONArray samples=new JSONArray();for (PresentationTracker.Sample sample:snapshot.samples) samples.put(sampleJson(sample));
        return new JSONObject().put("surfaceGeneration",generation).put("textureUpdates",snapshot.updates)
                .put("firstUpdateElapsedMillis",snapshot.firstUpdateElapsedMillis).put("lastUpdateElapsedMillis",snapshot.lastUpdateElapsedMillis)
                .put("geometryChanges",snapshot.geometryChanges).put("omittedSamples",snapshot.omittedSamples)
                .put("surfaceDetached",detached).put("surfaceReleased",released)
                .put("coordinateSpace","root-view").put("doesNotProveOtherWindowOcclusionOrVisiblePixels",true)
                .put("admitted",snapshot.admitted).put("retired",snapshot.retired).put("latest",geometryJson(snapshot.latest))
                .put("admission",sampleJson(snapshot.admission)).put("firstUpdate",sampleJson(snapshot.firstUpdate))
                .put("retirement",sampleJson(snapshot.retirement)).put("samples",samples);
    }
    private static Object sampleJson(PresentationTracker.Sample sample) throws JSONException {
        return sample==null ? JSONObject.NULL : new JSONObject().put("edge",sample.edge.name())
                .put("elapsedMillis",sample.elapsedMillis).put("geometry",geometryJson(sample.geometry));
    }
    private static JSONObject geometryJson(PresentationTracker.Geometry geometry) throws JSONException {
        return new JSONObject().put("width",geometry.width).put("height",geometry.height)
                .put("visibleLeft",geometry.visibleLeft).put("visibleTop",geometry.visibleTop)
                .put("visibleWidth",geometry.visibleWidth).put("visibleHeight",geometry.visibleHeight)
                .put("attached",geometry.attached).put("shown",geometry.shown).put("hardwareAccelerated",geometry.hardware);
    }
    public void requestDetach() { main(); if (!detached && detach!=null) detach.run(); }
    public void release(boolean actualRetirement) {
        main();
        if (released) return;
        if (!detached || (bound() && !actualRetirement)) throw new IllegalStateException("surface-still-owned");
        presentation.observe(PresentationTracker.Edge.RETIREMENT,SystemClock.elapsedRealtime(),presentation.latest());
        surface.release(); texture.release(); released=true; activity=null; detach=null;
    }
    private static void main() { if (Looper.myLooper()!=Looper.getMainLooper()) throw new IllegalStateException("main-required"); }
}
