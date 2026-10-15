package dev.cloudy.qualification.stream.playback;

/** No reset: the retained native core has a detached termination callback without a join ABI. */
final class PlaybackProcessLease {
    private Object owner;
    private boolean used;
    synchronized boolean claim(Object candidate) {
        if (candidate==null || used || owner!=null) return false;
        used=true; owner=candidate; return true;
    }
    synchronized void release(Object candidate) { if (owner==candidate) owner=null; }
    synchronized boolean active() { return owner!=null; }
    synchronized boolean requiresFreshProcess() { return used; }
}
