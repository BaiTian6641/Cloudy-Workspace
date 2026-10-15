package dev.cloudy.qualification.vnc;

/** Preserve sub-detent scrolling; reject malformed/unbounded events before changing remainder. */
public final class VncWheel {
    private float vertical,horizontal;
    public int[] add(float v,float h) {
        if (!Float.isFinite(v) || !Float.isFinite(h) || Math.abs(v)>16 || Math.abs(h)>16) return null;
        float nextV=vertical+v,nextH=horizontal+h;
        int stepsV=(int)nextV,stepsH=(int)nextH;
        vertical=nextV-stepsV; horizontal=nextH-stepsH;
        return new int[]{stepsV,stepsH};
    }
    public void clear() { vertical=0; horizontal=0; }
}
