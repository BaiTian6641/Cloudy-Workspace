package dev.cloudy.qualification.baseline

/** A native dialog approves its displayed attempt/key, never a later pending trust request. */
internal object TerminalTrustPolicy {
    fun requireApproved(ticket: TerminalTrustTicket, spec: TerminalConfigSpec,
        row: TerminalAttempt, storedFingerprint: String?): String {
        require(row.status == "trust-required" && !row.authenticated) { "TerminalTrustNotPending" }
        require(row.configIdentity == spec.configIdentity) { "TerminalTrustConfigurationChanged" }
        val observed = requireNotNull(row.observedHostKey) { "TerminalTrustMissingObservedKey" }
        require(ticket == TerminalTrustTicket(row.identity, spec.configIdentity, spec.endpointId, observed)) {
            "TerminalTrustApprovalChanged"
        }
        // Equality alone cannot enroll a reference mismatch, malformed key or
        // already enrolled endpoint. Runtime persists the returned key before
        // admitting a separate explicit connection; this policy performs no I/O.
        require(SshTrust(spec.expectedHostKeySha256, observed, storedFingerprint).canAcceptUnknown) {
            "TerminalTrustNotUnknown"
        }
        return observed
    }
}
