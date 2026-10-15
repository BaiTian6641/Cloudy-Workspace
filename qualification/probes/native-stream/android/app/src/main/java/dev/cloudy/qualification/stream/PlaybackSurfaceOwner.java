package dev.cloudy.qualification.stream;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Looper;
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
    public PlaybackSurfaceOwner(Activity activity,SurfaceTexture texture,Runnable detach) {
        main(); this.activity=activity; this.texture=texture; this.detach=detach; surface=new Surface(texture);
    }
    public boolean bind(long token) { main(); if (operation!=0 || detached || released || !surface.isValid()) return false; operation=token; return true; }
    public boolean ready() { return !detached && !released && surface.isValid(); }
    public long generation() { return generation; }
    public boolean matches(long token,long expectedGeneration) { return PlaybackCompletionPolicy.sameSurface(token,operation,generation,expectedGeneration); }
    public boolean bound() { return operation!=0; }
    public boolean detached() { return detached; }
    public boolean released() { return released; }
    public Activity activity() { return activity; }
    public Surface surface() { return surface; }
    public void destroyed() { main(); detached=true; }
    public void requestDetach() { main(); if (!detached && detach!=null) detach.run(); }
    public void release(boolean actualRetirement) {
        main();
        if (released) return;
        if (!detached || (bound() && !actualRetirement)) throw new IllegalStateException("surface-still-owned");
        surface.release(); texture.release(); released=true; activity=null; detach=null;
    }
    private static void main() { if (Looper.myLooper()!=Looper.getMainLooper()) throw new IllegalStateException("main-required"); }
}
