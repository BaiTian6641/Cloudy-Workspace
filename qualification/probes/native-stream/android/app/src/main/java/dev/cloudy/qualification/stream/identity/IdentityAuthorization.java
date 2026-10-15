package dev.cloudy.qualification.stream.identity;

import java.util.concurrent.atomic.AtomicBoolean;

/** App approval deadline, independent of the system's cryptographic authorization. */
final class IdentityAuthorization {
    static final long WINDOW_MILLIS = 30_000;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile long authorizedAt = -1;

    boolean authorize(long authenticatedAt, long now) {
        if (cancelled.get() || authenticatedAt < 0 || now < authenticatedAt
                || now - authenticatedAt >= WINDOW_MILLIS) return false;
        authorizedAt = authenticatedAt;
        return !cancelled.get();
    }

    boolean permits(long now) {
        long at = authorizedAt;
        return !cancelled.get() && at >= 0 && now >= at && now - at < WINDOW_MILLIS;
    }

    void cancel() { cancelled.set(true); }
    boolean isCancelled() { return cancelled.get(); }
}
