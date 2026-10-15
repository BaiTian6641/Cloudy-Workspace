package dev.cloudy.qualification.stream.identity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class IdentityAuthorizationTest {
    @Test public void actualApprovalExpiresAtBoundaryAndReadsNeverExtendIt() {
        IdentityAuthorization lease = new IdentityAuthorization();
        assertFalse(lease.permits(1_000));
        assertTrue(lease.authorize(1_000, 1_005));
        assertTrue(lease.permits(30_999));
        assertFalse(lease.permits(31_000));
        assertFalse(lease.authorize(1_000, 31_000));
        assertFalse(lease.permits(31_001));
    }

    @Test public void cancellationCannotBeReauthorizedByALatePromptCallback() {
        IdentityAuthorization lease = new IdentityAuthorization();
        assertTrue(lease.authorize(100, 100));
        lease.cancel();
        assertFalse(lease.permits(101));
        assertFalse(lease.authorize(102, 102));
        assertTrue(lease.isCancelled());
        IdentityAuthorization replacement = new IdentityAuthorization();
        assertFalse(replacement.permits(102));
        assertTrue(replacement.authorize(103, 103));
    }

    @Test public void FutureNegativeAndClockReversalCannotAuthorize() {
        IdentityAuthorization lease = new IdentityAuthorization();
        assertFalse(lease.authorize(-1, 0));
        assertFalse(lease.authorize(200, 199));
        assertTrue(lease.authorize(200, 200));
        assertFalse(lease.permits(199));
        assertFalse(lease.authorize(Long.MAX_VALUE, 0));
    }
}
