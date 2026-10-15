package dev.cloudy.qualification.stream.pairing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public final class AttemptLeaseTest {
    @Test public void callbackTimeBoundsAllWorkWithoutExtendingOnReads() throws Exception {
        AtomicLong clock = new AtomicLong(1000);
        AttemptLease lease = new AttemptLease(clock::get, 900, 30_000);
        assertEquals(29_900, lease.remaining());
        clock.set(30_899); assertEquals(1, lease.remaining());
        clock.set(30_900); assertThrows(IOException.class, lease::remaining);
        assertThrows(IllegalArgumentException.class, () -> new AttemptLease(clock::get, 30_901, 30_000));
        assertThrows(IllegalArgumentException.class, () -> new AttemptLease(clock::get, 0, 30_001));
    }
    @Test public void cancelledAttemptCannotRegisterANewCall() {
        AttemptLease lease = new AttemptLease(() -> 1, 1, 30_000);
        lease.cancel();
        assertThrows(IOException.class, () -> lease.register(() -> { }));
    }
    @Test public void cancellationReachesRegisteredCallUntilBodyRetires() throws Exception {
        AttemptLease lease = new AttemptLease(() -> 1, 1, 30_000);
        AtomicInteger cancellations = new AtomicInteger();
        AttemptLease.ActiveCall call = cancellations::incrementAndGet;
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        Thread consumer = new Thread(() -> {
            try {
                lease.register(call); registered.countDown(); releaseBody.await();
            } catch (Exception failure) { throw new AssertionError(failure); }
            finally { lease.retire(call); }
        });
        consumer.start();
        if (!registered.await(2, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("registration-timeout");
        try { lease.cancel(); assertEquals(1, cancellations.get()); }
        finally { releaseBody.countDown(); consumer.join(2000); }
        assertEquals(false, consumer.isAlive());
        lease.cancel(); assertEquals(1, cancellations.get());
    }
    @Test public void unrelatedRetirementCannotHideTheActiveCall() throws Exception {
        AttemptLease lease = new AttemptLease(() -> 1, 1, 30_000);
        AtomicInteger cancelled = new AtomicInteger();
        AttemptLease.ActiveCall active = cancelled::incrementAndGet;
        lease.register(active);
        assertThrows(IOException.class, () -> lease.register(() -> { }));
        lease.retire(() -> { }); lease.cancel();
        assertEquals(1, cancelled.get());
    }
}
