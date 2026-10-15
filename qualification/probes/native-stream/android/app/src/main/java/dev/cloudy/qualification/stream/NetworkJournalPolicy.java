package dev.cloudy.qualification.stream;

/** Both network lanes share this fail-closed predicate; acknowledgement must bind exact bytes. */
public final class NetworkJournalPolicy {
    private NetworkJournalPolicy() {}
    public static boolean acceptable(boolean wellFormed,boolean closed,boolean evidenceUnavailable,boolean explicitlyReconciled) {
        return wellFormed && (explicitlyReconciled || (closed && !evidenceUnavailable));
    }
}
