package dev.cloudy.qualification.stream.playback;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackPolicyTest {
    @Test public void secondPlaybackIsUnavailableEvenAfterRealOwnerRetires() {
        PlaybackProcessLease process=new PlaybackProcessLease(); Object first=new Object(),second=new Object();
        assertFalse(process.requiresFreshProcess()); assertTrue(process.claim(first));
        assertTrue(process.active()); assertFalse(process.claim(second));
        process.release(second); assertTrue(process.active());
        process.release(first); assertFalse(process.active());
        assertTrue(process.requiresFreshProcess()); assertFalse(process.claim(first)); assertFalse(process.claim(second));
    }
    @Test public void originalCallbackClipsWholeAttemptWithoutRefreshingTime() {
        assertEquals(31_000,PlaybackPolicy.deadline(1500,1000,30_000));
        assertTrue(PlaybackPolicy.permits(30_999,1000,31_000,false));
        assertFalse(PlaybackPolicy.permits(31_000,1000,31_000,false));
        assertFalse(PlaybackPolicy.permits(999,1000,31_000,false));
        assertFalse(PlaybackPolicy.permits(1500,1000,31_000,true));
        assertThrows(IllegalArgumentException.class,() -> PlaybackPolicy.deadline(1000,1001,30_000));
        assertThrows(IllegalArgumentException.class,() -> PlaybackPolicy.deadline(31_000,1000,30_000));
        assertThrows(IllegalArgumentException.class,() -> PlaybackPolicy.deadline(Long.MAX_VALUE,Long.MAX_VALUE,30_000));
        assertThrows(IllegalArgumentException.class,() -> PlaybackPolicy.deadline(1000,1000,30_001));
    }
    @Test public void sessionUrlCannotRebindPortHostOrProtocol() {
        assertTrue(PlaybackPolicy.sessionUrl("192.0.2.1",48010,"rtspenc://192.0.2.1:48010"));
        assertTrue(PlaybackPolicy.sessionUrl("192.0.2.1",48010,"rtsp://192.0.2.1:48010/"));
        assertFalse(PlaybackPolicy.sessionUrl("192.0.2.1",48010,"rtspenc://192.0.2.2:48010"));
        assertFalse(PlaybackPolicy.sessionUrl("192.0.2.1",48010,"rtspenc://192.0.2.1:48011"));
        assertFalse(PlaybackPolicy.sessionUrl("192.0.2.1",48010,"https://192.0.2.1:48010"));
        assertFalse(PlaybackPolicy.sessionUrl("192.0.2.1",48010,"rtsp://user@192.0.2.1:48010"));
        assertFalse(PlaybackPolicy.sessionUrl("192.0.2.1",48010,"rtsp://192.0.2.1:48010/?secret=x"));
        assertFalse(PlaybackPolicy.sessionUrl("192.0.2.1",48010,null));
    }
    @Test public void noCancelUnpairLookupOrCleartextPathCanEnterPlaybackHttp() {
        assertTrue(PlaybackPolicy.requestPath("/serverinfo")); assertTrue(PlaybackPolicy.requestPath("/launch"));
        assertTrue(PlaybackPolicy.requestPath("/resume")); assertFalse(PlaybackPolicy.requestPath("/cancel"));
        assertFalse(PlaybackPolicy.requestPath("/unpair")); assertFalse(PlaybackPolicy.requestPath("/applist"));
        assertFalse(PlaybackPolicy.requestPath("/launch/")); assertFalse(PlaybackPolicy.requestPath("/%6caunch"));
    }
    @Test public void onlyBoundedNumericVersionsReachNativeVersionDiagnostics() {
        assertTrue(PlaybackPolicy.serverVersion("7.1.431.0",false));
        assertTrue(PlaybackPolicy.serverVersion(null,true)); assertFalse(PlaybackPolicy.serverVersion(null,false));
        assertFalse(PlaybackPolicy.serverVersion("7.1.peer-name.0",false));
        assertFalse(PlaybackPolicy.serverVersion("7.1.431.0\n",false));
        assertFalse(PlaybackPolicy.serverVersion("7.1.9999999999.0",false));
        assertFalse(PlaybackPolicy.serverVersion("7.1",false));
    }
    @Test public void failedUnacquiredOrRepeatedCleanupCannotInflatePermit() {
        PlaybackOwnership ownership=new PlaybackOwnership(); AtomicInteger releases=new AtomicInteger();
        ownership.release(releases::incrementAndGet); assertEquals(0,releases.get());
        ownership.acquired(); assertThrows(IllegalStateException.class,ownership::acquired);
        ownership.release(releases::incrementAndGet); ownership.release(releases::incrementAndGet);
        assertEquals(1,releases.get());
    }
    @Test public void leaseKeepsBodyCancellableUntilMatchingRetirement() throws Exception {
        AtomicLong clock=new AtomicLong(1000); PlaybackLease lease=new PlaybackLease(clock::get,900,30_000);
        AtomicInteger cancels=new AtomicInteger(); PlaybackLease.Call active=cancels::incrementAndGet;
        lease.register(active); lease.retire(() -> {}); lease.cancel(); assertEquals(1,cancels.get());
        assertThrows(IOException.class,() -> lease.register(active)); lease.retire(active); lease.cancel(); assertEquals(1,cancels.get());
    }
    @Test public void tricklingResponsesAndNewCallsCannotRenewDeadline() throws Exception {
        AtomicLong clock=new AtomicLong(1000); PlaybackLease lease=new PlaybackLease(clock::get,1000,1000);
        PlaybackLease.Call call=() -> {}; lease.register(call); clock.set(1999); assertEquals(1,lease.remaining());
        lease.retire(call); clock.set(2000); assertThrows(IOException.class,() -> lease.register(call));
        assertThrows(IOException.class,lease::remaining);
    }
}
