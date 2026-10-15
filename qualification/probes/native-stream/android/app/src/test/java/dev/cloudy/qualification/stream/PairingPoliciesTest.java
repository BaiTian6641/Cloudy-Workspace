package dev.cloudy.qualification.stream;

import org.junit.Test;
import static org.junit.Assert.*;

public class PairingPoliciesTest {
    @Test public void pinIsExactlyFourAsciiDigitsWithoutNormalization() {
        assertTrue(PairingPinPolicy.valid(new char[]{'0','0','1','2'}));
        assertFalse(PairingPinPolicy.valid(null)); assertFalse(PairingPinPolicy.valid(new char[]{'1','2','3'}));
        assertFalse(PairingPinPolicy.valid(new char[]{'1','2','3','4','5'}));
        assertFalse(PairingPinPolicy.valid(new char[]{'1','2','3','\0'}));
        assertFalse(PairingPinPolicy.valid(new char[]{'１','2','3','4'}));
    }
    @Test public void cleartextFixtureScopeDoesNotAcceptDnsIpv6OrOtherLoopbackAddresses() {
        assertTrue(PairingPinPolicy.endpoint("fixture_1","127.0.0.1",47989,47984));
        assertFalse(PairingPinPolicy.endpoint("fixture","localhost",47989,47984));
        assertFalse(PairingPinPolicy.endpoint("fixture","::1",47989,47984));
        assertFalse(PairingPinPolicy.endpoint("fixture","127.0.0.2",47989,47984));
        assertFalse(PairingPinPolicy.endpoint("fixture","192.0.2.1",47989,47984));
        assertFalse(PairingPinPolicy.endpoint("fixture","127.0.0.1",47989,47989));
        assertFalse(PairingPinPolicy.endpoint("fixture","127.0.0.1",0,65536));
        assertFalse(PairingPinPolicy.endpoint("bad/id","127.0.0.1",1,2));
    }
    @Test public void secondModelCannotClaimUntilRealOwnerRetires() {
        StreamOperationLease lease=new StreamOperationLease(); Object first=new Object(),second=new Object();
        assertTrue(lease.claim(first)); assertFalse(lease.claim(second)); assertFalse(lease.claim(first));
        lease.release(second); assertFalse(lease.claim(second));
        lease.release(first); assertTrue(lease.claim(second));
    }
    @Test public void promptWaitIsNotClosedButAcknowledgedCancelCanClose() {
        assertFalse(StreamOperationLease.closed(false,true,false));
        assertFalse(StreamOperationLease.closed(true,false,true));
        assertFalse(StreamOperationLease.closed(true,true,true));
        assertTrue(StreamOperationLease.closed(true,true,false));
        assertTrue(StreamOperationLease.closed(false,false,false));
    }
}
