package dev.cloudy.qualification.stream.playback;

import java.io.IOException;
import java.util.function.LongSupplier;

/** The actual HTTP call stays cancellable through its entire response body. */
final class PlaybackLease {
    interface Call { void cancel(); }
    private final LongSupplier clock;
    private final long authenticatedAt,deadline;
    private boolean cancelled;
    private Call active;
    PlaybackLease(LongSupplier clock,long authenticatedAt,long duration) {
        this.clock=clock; this.authenticatedAt=authenticatedAt;
        deadline=PlaybackPolicy.deadline(clock.getAsLong(),authenticatedAt,duration);
    }
    synchronized long remaining() throws IOException {
        long now=clock.getAsLong();
        if (!PlaybackPolicy.permits(now,authenticatedAt,deadline,cancelled)) throw new IOException("attempt-unavailable");
        return deadline-now;
    }
    synchronized void register(Call call) throws IOException {
        remaining(); if (active!=null) throw new IOException("call-active"); active=call;
    }
    synchronized void retire(Call call) { if (active==call) active=null; }
    void cancel() {
        Call call;
        synchronized(this) { cancelled=true; call=active; }
        if (call!=null) call.cancel();
    }
}
