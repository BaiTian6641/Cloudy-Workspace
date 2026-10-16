package dev.cloudy.qualification.vnc;

import java.util.concurrent.atomic.AtomicLongArray;

/** One instance per attempt: no reset can erase a concurrently arriving milestone. */
public final class VncDiagnostics {
    public enum Time { ACCEPTED, WORKER, NATIVE_START, CONNECTED, FRAME, CONVERTED, DRAWN, STOP, JOINED }
    public enum Marker { DOWN_SEEN, UP_SEEN, UP_UNOWNED, REJECT_FOCUS, REJECT_WINDOW, REJECT_CONNECTION, REJECT_META, DOWN_QUEUED, UP_QUEUED, QUEUE_REJECTED }
    private final AtomicLongArray times=new AtomicLongArray(Time.values().length);
    private final AtomicLongArray markers=new AtomicLongArray(Marker.values().length);
    public VncDiagnostics() { for (Time time:Time.values()) times.set(time.ordinal(),-1); }
    public void mark(Time time,long elapsed) { if (elapsed>=0) times.compareAndSet(time.ordinal(),-1,elapsed); }
    public long time(Time time) { return times.get(time.ordinal()); }
    public void count(Marker marker) {
        int index=marker.ordinal(); long old;
        do { old=markers.get(index); if (old>=4000) return; } while (!markers.compareAndSet(index,old,old+1));
    }
    public long countOf(Marker marker) { return markers.get(marker.ordinal()); }
    public String phase() {
        if (time(Time.STOP)>=0) return "stopping";
        if (time(Time.DRAWN)>=0) return "frame-drawn";
        if (time(Time.CONVERTED)>=0) return "waiting-first-draw";
        if (time(Time.FRAME)>=0) return "waiting-frame-conversion";
        if (time(Time.CONNECTED)>=0) return "waiting-first-frame";
        if (time(Time.NATIVE_START)>=0) return "connection-initialization";
        if (time(Time.WORKER)>=0) return "local-setup";
        return "worker-queued";
    }
}
