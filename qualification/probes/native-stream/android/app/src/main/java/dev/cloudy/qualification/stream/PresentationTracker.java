package dev.cloudy.qualification.stream;

import java.util.ArrayList;
import java.util.List;

/** Bounded observations, not a visual-pass claim. No View, pixels, listener or disk ownership. */
public final class PresentationTracker {
    public enum Edge { AVAILABLE, ADMISSION, FIRST_UPDATE, VISIBILITY, DESTRUCTION, RETIREMENT }
    public static final int MAX_SAMPLES=16;
    public static final class Geometry {
        public final int width,height,visibleWidth,visibleHeight,visibleLeft,visibleTop;
        public final boolean attached,shown,hardware;
        public Geometry(int width,int height,int visibleWidth,int visibleHeight,boolean attached,boolean shown,boolean hardware) {
            this(width,height,visibleWidth,visibleHeight,attached,shown,hardware,0,0);
        }
        public Geometry(int width,int height,int visibleWidth,int visibleHeight,boolean attached,boolean shown,boolean hardware,int visibleLeft,int visibleTop) {
            this.visibleLeft=visibleLeft;this.visibleTop=visibleTop;
            this.width=Math.max(0,width);this.height=Math.max(0,height);
            this.visibleWidth=Math.max(0,Math.min(this.width,visibleWidth));
            this.visibleHeight=Math.max(0,Math.min(this.height,visibleHeight));
            this.attached=attached;this.shown=shown;this.hardware=hardware;
        }
        public boolean admissible() { return width>0 && height>0 && visibleWidth>0 && visibleHeight>0 && attached && shown && hardware; }
        boolean same(Geometry other) {
            return other!=null && width==other.width && height==other.height && visibleWidth==other.visibleWidth
                    && visibleHeight==other.visibleHeight && visibleLeft==other.visibleLeft && visibleTop==other.visibleTop && attached==other.attached && shown==other.shown && hardware==other.hardware;
        }
    }
    public static final class Sample {
        public final Edge edge;
        public final long elapsedMillis;
        public final Geometry geometry;
        Sample(Edge edge,long elapsedMillis,Geometry geometry) { this.edge=edge;this.elapsedMillis=elapsedMillis;this.geometry=geometry; }
    }
    public static final class Snapshot {
        public final long updates,geometryChanges,omittedSamples,firstUpdateElapsedMillis,lastUpdateElapsedMillis;
        public final boolean admitted,retired;
        public final Geometry latest;
        public final Sample admission,firstUpdate,retirement;
        public final List<Sample> samples;
        Snapshot(PresentationTracker tracker) {
            updates=tracker.updates;geometryChanges=tracker.changes;omittedSamples=tracker.omitted;
            firstUpdateElapsedMillis=tracker.firstUpdateElapsedMillis;lastUpdateElapsedMillis=tracker.lastUpdateElapsedMillis;
            admitted=tracker.admitted;retired=tracker.retired;latest=tracker.latest;
            admission=tracker.admission;firstUpdate=tracker.firstUpdate;retirement=tracker.retirement;
            samples=List.copyOf(tracker.samples);
        }
    }
    private final ArrayList<Sample> samples=new ArrayList<>();
    private Geometry latest=new Geometry(0,0,0,0,false,false,false);
    private Sample admission,firstUpdate,retirement;
    private long updates,changes,omitted;
    private long firstUpdateElapsedMillis=-1,lastUpdateElapsedMillis=-1;
    private boolean admitted,retired,destroyed;
    public synchronized boolean begin(long elapsedMillis) {
        if (admitted || destroyed || retired || !latest.admissible()) return false;
        admitted=true;updates=0;changes=0;omitted=0;firstUpdateElapsedMillis=-1;lastUpdateElapsedMillis=-1;samples.clear();
        admission=new Sample(Edge.ADMISSION,elapsedMillis,latest);append(admission);return true;
    }
    public synchronized void observe(Edge edge,long elapsedMillis,Geometry geometry) {
        if (retired || geometry==null || edge==Edge.ADMISSION) return;
        if (destroyed && edge!=Edge.RETIREMENT) return;
        boolean changed=!geometry.same(latest);latest=geometry;
        if (changed && admitted) changes=increment(changes);
        if (edge==Edge.DESTRUCTION) destroyed=true;
        if (edge==Edge.FIRST_UPDATE && (!admitted || updates==0 || firstUpdate!=null)) return;
        if (edge==Edge.VISIBILITY && !changed) return;
        Sample sample=new Sample(edge,elapsedMillis,geometry);
        if (edge==Edge.FIRST_UPDATE) firstUpdate=sample;
        if (edge==Edge.RETIREMENT) { retired=true;retirement=sample; }
        if (admitted) append(sample);
    }
    /** O(1), no allocation: only first-update sampling requires geometry work. */
    public synchronized boolean updated(long elapsedMillis) {
        if (!admitted || retired || destroyed || elapsedMillis<0 || elapsedMillis<admission.elapsedMillis || elapsedMillis<lastUpdateElapsedMillis) return false;
        if (updates==0) firstUpdateElapsedMillis=elapsedMillis;
        lastUpdateElapsedMillis=elapsedMillis;
        updates=increment(updates);return updates==1;
    }
    public synchronized Geometry latest() { return latest; }
    public synchronized Snapshot snapshot() { return new Snapshot(this); }
    private void append(Sample sample) { if (samples.size()<MAX_SAMPLES) samples.add(sample);else omitted=increment(omitted); }
    private static long increment(long value) { return value==Long.MAX_VALUE ? value : value+1; }
}
