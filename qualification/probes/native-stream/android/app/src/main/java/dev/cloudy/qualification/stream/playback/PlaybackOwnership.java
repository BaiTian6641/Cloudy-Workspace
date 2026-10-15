package dev.cloudy.qualification.stream.playback;

import java.util.concurrent.atomic.AtomicBoolean;

/** A cancelled start cannot release a permit it never acquired or release one twice. */
public final class PlaybackOwnership {
    private final AtomicBoolean held=new AtomicBoolean();
    public void acquired() { if (!held.compareAndSet(false,true)) throw new IllegalStateException("permit-held"); }
    public void release(Runnable actualRelease) { if (held.compareAndSet(true,false)) actualRelease.run(); }
}
