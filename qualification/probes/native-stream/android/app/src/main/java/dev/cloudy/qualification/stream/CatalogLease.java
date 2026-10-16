package dev.cloudy.qualification.stream;

import java.io.IOException;
import java.util.function.LongSupplier;

/** One immutable authorization deadline; cancellation never extends or replaces it. */
final class CatalogLease {
    interface ActiveCall { void cancel(); }
    private final LongSupplier clock;
    private final long deadline;
    private boolean cancelled;
    private ActiveCall active;

    CatalogLease(LongSupplier clock, long authenticatedAt, long durationMillis) {
        this.clock = clock;
        long now = clock.getAsLong();
        if (authenticatedAt < 0 || authenticatedAt > now || durationMillis <= 0
                || durationMillis > 30_000 || authenticatedAt > Long.MAX_VALUE - durationMillis) {
            throw new IllegalArgumentException("invalid-authorization-window");
        }
        deadline = authenticatedAt + durationMillis;
    }
    synchronized long remaining() throws IOException {
        long now = clock.getAsLong();
        if (cancelled || now >= deadline || now < deadline - 30_000) {
            throw new IOException("attempt-unavailable");
        }
        return deadline - now;
    }
    synchronized void register(ActiveCall call) throws IOException {
        remaining();
        if (active != null) throw new IOException("call-already-active");
        active = call;
    }
    synchronized void retire(ActiveCall call) {
        if (active == call) active = null;
    }
    void cancel() {
        ActiveCall call;
        synchronized (this) { cancelled = true; call = active; }
        if (call != null) call.cancel();
    }
}
