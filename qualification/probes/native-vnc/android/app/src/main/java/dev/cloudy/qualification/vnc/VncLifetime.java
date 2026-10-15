package dev.cloudy.qualification.vnc;

/** A completed cancel request is not evidence that a worker can be freed. */
public final class VncLifetime {
    private VncLifetime() {}
    public static boolean mayFree(boolean started, boolean joinConfirmed) { return !started || joinConfirmed; }
    public static boolean mayCancel(long releaseSequence, long now, long releaseDeadline, boolean terminal) {
        return terminal || releaseSequence == 0 || now >= releaseDeadline;
    }
}
