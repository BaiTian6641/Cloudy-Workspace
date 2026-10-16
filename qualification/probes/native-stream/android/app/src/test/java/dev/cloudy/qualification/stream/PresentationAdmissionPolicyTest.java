package dev.cloudy.qualification.stream;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PresentationAdmissionPolicyTest {
    @Test public void evidenceFailuresSurviveMissingPrerequisitesAndActiveOperation() {
        for (String code:new String[]{"EVIDENCE_CAPACITY","EVIDENCE_UNFINISHED_CURRENT","EVIDENCE_PRIOR_PROCESS",
                "EVIDENCE_MALFORMED","EVIDENCE_UNAVAILABLE","EVIDENCE_RESTART_REQUIRED"}) {
            assertEquals(code,PresentationAdmissionPolicy.blocker(code,false,false,true,false,false,0,false));
            assertEquals(code,PresentationAdmissionPolicy.blocker(code,true,false,true,false,false,0,false));
        }
    }
    @Test public void retainedOwnerAndCleanupReasonsRemainActionable() {
        for (String code:new String[]{"PROCESS_OWNER","CLEANUP_PENDING","OWNER_NOT_VISIBLE"})
            assertEquals(code,PresentationAdmissionPolicy.blocker(code,false,false,false,false,false,0,false));
        assertEquals("CLEANUP_PENDING",PresentationAdmissionPolicy.blocker("NONE",true,true,true,true,true,12,true));
        assertEquals("OPERATION_ACTIVE",PresentationAdmissionPolicy.blocker("NONE",true,false,true,true,true,12,true));
        assertEquals("NATIVE_PROCESS_USED",PresentationAdmissionPolicy.blocker("NONE",false,false,true,true,true,12,true));
    }
    @Test public void currentFactsResolveOrdinaryBlockersWithoutMaskingUnknownStates() {
        assertEquals("CATALOG_REQUIRED",PresentationAdmissionPolicy.blocker("NONE",false,false,false,true,true,0,true));
        assertEquals("NONE",PresentationAdmissionPolicy.blocker("SURFACE_REQUIRED",false,false,false,true,true,12,true));
        assertEquals("SURFACE_REQUIRED",PresentationAdmissionPolicy.blocker("NONE",false,false,false,true,true,12,false));
        assertEquals("UNKNOWN_FUTURE_STATE",PresentationAdmissionPolicy.blocker("UNKNOWN_FUTURE_STATE",false,false,false,true,true,12,true));
    }
}
