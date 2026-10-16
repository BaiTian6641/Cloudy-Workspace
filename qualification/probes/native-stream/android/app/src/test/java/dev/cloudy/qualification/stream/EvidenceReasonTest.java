package dev.cloudy.qualification.stream;

import org.junit.Test;
import static org.junit.Assert.*;

public final class EvidenceReasonTest {
    @Test public void finalSlotCapacityAndUnreadableDirectoryAreDifferent() {
        assertEquals(EvidenceReason.NONE,EvidenceReason.capacity(15));
        assertEquals(EvidenceReason.EVIDENCE_CAPACITY,EvidenceReason.capacity(16));
        assertEquals(EvidenceReason.EVIDENCE_UNAVAILABLE,EvidenceReason.capacity(-1));
    }
    @Test public void unfinishedCurrentAndPriorProcessNeverLookLikeCapacity() {
        assertEquals(EvidenceReason.EVIDENCE_UNFINISHED_CURRENT,EvidenceReason.record(false,false,false,"current","current"));
        assertEquals(EvidenceReason.EVIDENCE_PRIOR_PROCESS,EvidenceReason.record(false,false,false,"old","current"));
        assertEquals(EvidenceReason.EVIDENCE_MALFORMED,EvidenceReason.record(false,false,false,"","current"));
        assertEquals(EvidenceReason.EVIDENCE_RESTART_REQUIRED,EvidenceReason.record(true,true,false,"current","current"));
        assertEquals(EvidenceReason.EVIDENCE_PRIOR_PROCESS,EvidenceReason.record(true,true,false,"old","current"));
        assertEquals(EvidenceReason.NONE,EvidenceReason.record(true,true,true,"old","current"));
        assertEquals(EvidenceReason.NONE,EvidenceReason.record(false,false,true,"old","current"));
        assertEquals(EvidenceReason.NONE,EvidenceReason.record(true,false,false,"old","current"));
    }
}
