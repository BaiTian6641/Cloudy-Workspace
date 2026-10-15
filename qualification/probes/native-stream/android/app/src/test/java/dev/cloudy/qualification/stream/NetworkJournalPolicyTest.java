package dev.cloudy.qualification.stream;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NetworkJournalPolicyTest {
    @Test public void previousClosedPairPassesButEitherLaneUnfinishedBlocks() {
        assertTrue(NetworkJournalPolicy.acceptable(true,true,false,false));
        assertFalse(NetworkJournalPolicy.acceptable(true,false,false,false));
        assertFalse(NetworkJournalPolicy.acceptable(true,true,true,false));
    }
    @Test public void exactReceiptAcknowledgesInterruptionButNeverMalformedBytes() {
        assertTrue(NetworkJournalPolicy.acceptable(true,false,false,true));
        assertTrue(NetworkJournalPolicy.acceptable(true,true,true,true));
        assertFalse(NetworkJournalPolicy.acceptable(false,false,false,true));
        assertFalse(NetworkJournalPolicy.acceptable(false,true,false,true));
        // A changed record no longer has an exact matching receipt.
        assertFalse(NetworkJournalPolicy.acceptable(true,false,false,false));
    }
}
