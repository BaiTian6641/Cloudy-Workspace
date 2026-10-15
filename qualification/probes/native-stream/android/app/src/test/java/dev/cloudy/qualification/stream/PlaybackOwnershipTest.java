package dev.cloudy.qualification.stream;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PlaybackOwnershipTest {
    @Test public void createdSnapshotCannotRetireWorkerThatCanStillPublishEngine() {
        assertFalse(PlaybackCompletionPolicy.nativeSafe(false,false,true,false));
        assertFalse(PlaybackCompletionPolicy.nativeSafe(true,false,true,true));
        // Cancelled before native admission still waits for the worker and prompt.
        assertTrue(PlaybackCompletionPolicy.nativeSafe(true,false,false,false));
    }
    @Test public void completedRunNeedsActualNativeRetirementThenSurfaceAndEvidence() {
        assertFalse(PlaybackCompletionPolicy.nativeSafe(true,true,false,false));
        boolean retired=PlaybackCompletionPolicy.nativeSafe(true,true,true,false);
        assertTrue(retired);
        assertFalse(PlaybackCompletionPolicy.mayReleaseLease(retired,false,false));
        assertFalse(PlaybackCompletionPolicy.mayReleaseLease(retired,true,false));
        assertFalse(PlaybackCompletionPolicy.mayReleaseLease(false,true,true));
        assertTrue(PlaybackCompletionPolicy.mayReleaseLease(retired,true,true));
    }
    @Test public void recreatedSurfaceCannotCancelOrRetireOldSurfaceOwner() {
        assertTrue(PlaybackCompletionPolicy.sameSurface(12,12,7,7));
        assertFalse(PlaybackCompletionPolicy.sameSurface(12,12,8,7));
        assertFalse(PlaybackCompletionPolicy.sameSurface(13,12,7,7));
        assertFalse(PlaybackCompletionPolicy.sameSurface(0,0,7,7));
    }
    @Test public void processLeaseRemainsExclusiveUntilWholeRetirementChain() {
        StreamOperationLease lease=new StreamOperationLease(); Object oldOwner=new Object(),newOwner=new Object();
        assertTrue(lease.claim(oldOwner));
        if (PlaybackCompletionPolicy.mayReleaseLease(true,false,true)) lease.release(oldOwner);
        assertFalse(lease.claim(newOwner));
        if (PlaybackCompletionPolicy.mayReleaseLease(true,true,false)) lease.release(oldOwner);
        assertFalse(lease.claim(newOwner));
        if (PlaybackCompletionPolicy.mayReleaseLease(true,true,true)) lease.release(oldOwner);
        assertTrue(lease.claim(newOwner));
    }
}
