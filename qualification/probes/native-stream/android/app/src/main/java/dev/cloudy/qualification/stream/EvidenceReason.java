package dev.cloudy.qualification.stream;

/** Stable bounded UI/journal codes. Capacity never grants permission to delete records. */
enum EvidenceReason {
    NONE, EVIDENCE_CAPACITY, EVIDENCE_UNFINISHED_CURRENT, EVIDENCE_PRIOR_PROCESS,
    EVIDENCE_MALFORMED, EVIDENCE_UNAVAILABLE, EVIDENCE_RESTART_REQUIRED;
    static EvidenceReason capacity(int count) {
        return count<0 ? EVIDENCE_UNAVAILABLE : count>=16 ? EVIDENCE_CAPACITY : NONE;
    }
    static EvidenceReason record(boolean closed,boolean error,boolean acknowledged,String process,String current) {
        if (NetworkJournalPolicy.acceptable(true,closed,error,acknowledged)) return NONE;
        if (process==null || process.isEmpty()) return EVIDENCE_MALFORMED;
        if (error) return process.equals(current) ? EVIDENCE_RESTART_REQUIRED : EVIDENCE_PRIOR_PROCESS;
        return process.equals(current) ? EVIDENCE_UNFINISHED_CURRENT : EVIDENCE_PRIOR_PROCESS;
    }
}
