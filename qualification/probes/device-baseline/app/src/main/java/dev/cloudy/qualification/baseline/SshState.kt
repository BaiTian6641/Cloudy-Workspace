package dev.cloudy.qualification.baseline

import java.util.Collections
import java.util.UUID

internal enum class SshTrustDecision { UNKNOWN, MATCH, REJECT }

internal enum class SshOperation(val wireValue: String, val totalTimeoutSeconds: Long) {
    SFTP("sftp", 60), EXEC_PTY("exec-pty", 180);
}

internal data class SshBuildIdentity(val versionName: String, val versionCode: Int, val buildRef: String) {
    init {
        require(versionName.length in 5..40 && versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?"))) { "SshBuildVersion" }
        require(versionCode > 0 && canonicalSshUuid(buildRef)) { "SshBuildIdentity" }
    }
}

private fun canonicalSshUuid(value: String): Boolean = try { UUID.fromString(value).toString() == value }
    catch (_: IllegalArgumentException) { false }

internal data class SshTrust(val expected: String, val observed: String, val trusted: String?) {
    val decision: SshTrustDecision = when {
        !SshConfigSpec.validFingerprint(expected) || !SshConfigSpec.validFingerprint(observed) -> SshTrustDecision.REJECT
        observed != expected -> SshTrustDecision.REJECT
        trusted == null -> SshTrustDecision.UNKNOWN
        trusted != observed -> SshTrustDecision.REJECT
        else -> SshTrustDecision.MATCH
    }
    val canAcceptUnknown: Boolean get() = decision == SshTrustDecision.UNKNOWN
}

internal data class SshEvent(val status: String, val time: TransferTime, val reason: String?)
internal data class SshAttempt(
    val id: String,
    val originProcessId: String,
    val configIdentity: String,
    val operation: SshOperation,
    val buildIdentity: SshBuildIdentity?,
    val status: String,
    val reason: String? = null,
    val observedHostKey: String? = null,
    val authenticated: Boolean = false,
    val details: Map<String, String> = emptyMap(),
    val events: List<SshEvent> = emptyList(),
    val droppedEvents: Long = 0,
) {
    // A trust prompt ends this unauthenticated connection attempt. Enrollment
    // never resumes it; the runtime requires another explicit Start action.
    val terminal: Boolean get() = status != "running"
    // A legacy row has no executing-build field. Its original external manifest
    // owns provenance; rewriting the journal must not borrow the current build.
    val provenance: String get() = if (buildIdentity == null) "external-legacy" else "recorded-build"
}

/** The serial runtime owns this history. A terminal row is not evidence that
 * its worker exited or its journal committed; runtime admission checks both.
 */
internal class SshState(restored: List<SshAttempt> = emptyList()) {
    companion object {
        const val ATTEMPT_LIMIT = 8
        const val EVENT_LIMIT = 32
        private val statuses = setOf("running", "trust-required", "host-key-rejected", "completed", "failed", "cancelled", "interrupted")
        const val DETAIL_LIMIT = 24
        private val commonDetailKeys = setOf("kex", "hostSignature", "cipherC2S", "cipherS2C", "macC2S", "macS2C",
            "compressionC2S", "compressionS2C", "hostVerificationCount")
        private val sftpDetailKeys = commonDetailKeys + setOf("sftpVersion", "sentinelBytes", "sentinelSha256", "uploadName",
            "uploadBytes", "uploadSha256", "readbackBytes", "readbackSha256", "collisionStatus", "collisionBytes",
            "collisionSha256", "uploadAcknowledgedBytes")
        private val execDetailKeys = commonDetailKeys + setOf("execStdoutBytes", "execStdoutSha256", "execStderrBytes",
            "execStderrSha256", "execExitStatus", "ptyInitialCols", "ptyInitialRows", "ptyResizedCols", "ptyResizedRows",
            "ptyStdoutBytes", "ptyStdoutSha256", "ptyStderrBytes", "ptyStderrSha256", "ptyInputBytes", "ptyCompletionMarker")
        private fun reason(value: String?) { require(value == null || value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,99}"))) { "SshReason" } }
        private fun time(value: TransferTime) {
            require(value.elapsedRealtimeMs >= 0 && value.deviceUtc.length in 1..64 && value.deviceUtc.all { it.code in 32..126 }) { "SshEventTime" }
        }
        private fun details(operation: SshOperation, values: Map<String, String>) {
            val allowed = if (operation == SshOperation.SFTP) sftpDetailKeys else execDetailKeys
            require(values.size <= DETAIL_LIMIT && values.keys.all { it in allowed } &&
                values.values.all { it.length in 1..128 && it.all { character -> character.code in 32..126 } }) { "SshDetails" }
            values["uploadName"]?.let {
                require(it.startsWith("cloudy-") && it.endsWith(".bin") && canonicalSshUuid(it.removePrefix("cloudy-").removeSuffix(".bin"))) { "SshUploadName" }
            }
            if (operation == SshOperation.EXEC_PTY) {
                values.filterKeys { it.endsWith("Sha256") }.values.forEach {
                    require(it.matches(Regex("[0-9a-f]{64}"))) { "SshProtocolHash" }
                }
                fun bounded(name: String, range: LongRange) {
                    values[name]?.let {
                        val number = it.toLongOrNull()
                        require(it.matches(Regex("0|[1-9][0-9]*")) && number != null && number in range) { "SshProtocolCount" }
                    }
                }
                listOf("execStdoutBytes", "execStderrBytes", "ptyStdoutBytes", "ptyStderrBytes").forEach { bounded(it, 0L..4096L) }
                listOf("ptyInitialCols", "ptyInitialRows", "ptyResizedCols", "ptyResizedRows").forEach { bounded(it, 1L..1000L) }
                bounded("execExitStatus", 0L..255L)
                bounded("ptyInputBytes", 0L..12L)
                values["ptyCompletionMarker"]?.let { require(it == "CLOUDY_PTY_DONE_V1") { "SshProtocolMarker" } }
            }
        }
        private fun validate(attempt: SshAttempt) {
            require(canonicalSshUuid(attempt.id) && canonicalSshUuid(attempt.originProcessId) &&
                attempt.configIdentity.matches(Regex("[0-9a-f]{64}"))) { "SshAttemptIdentity" }
            require(attempt.buildIdentity != null || attempt.operation == SshOperation.SFTP) { "SshLegacyOperation" }
            require(attempt.status in statuses && attempt.events.size in 1..EVENT_LIMIT && attempt.droppedEvents >= 0) { "SshAttemptHistory" }
            require(attempt.observedHostKey == null || SshConfigSpec.validFingerprint(attempt.observedHostKey)) { "SshObservedFingerprint" }
            require(!attempt.authenticated || attempt.observedHostKey != null) { "SshAuthRequiresHost" }
            require(attempt.status !in setOf("trust-required", "host-key-rejected") || !attempt.authenticated) { "SshTrustBeforeAuth" }
            require(attempt.status != "completed" || attempt.authenticated) { "SshCompletionRequiresAuth" }
            require(attempt.status !in setOf("trust-required", "host-key-rejected") || attempt.observedHostKey != null) { "SshTrustRequiresHost" }
            reason(attempt.reason)
            details(attempt.operation, attempt.details)
            attempt.events.forEach { require(it.status in statuses) { "SshEventStatus" }; reason(it.reason); time(it.time) }
            require(attempt.events.dropLast(1).all { it.status == "running" }) { "SshTerminalHistory" }
            require(attempt.events.last().status == attempt.status && attempt.events.last().reason == attempt.reason) { "SshEventCurrentState" }
        }
        private fun freeze(attempt: SshAttempt) = attempt.copy(
            details = Collections.unmodifiableMap(LinkedHashMap(attempt.details)),
            events = Collections.unmodifiableList(ArrayList(attempt.events)),
        )
    }

    private val attempts = restored.map { validate(it); freeze(it) }.toMutableList()
    init {
        require(attempts.size <= ATTEMPT_LIMIT && attempts.map { it.id }.distinct().size == attempts.size) { "SshAttemptLimit" }
        require(attempts.dropLast(1).all { it.terminal }) { "SshUnfinishedHistory" }
        val firstRecorded = attempts.indexOfFirst { it.buildIdentity != null }
        require(firstRecorded == -1 || attempts.drop(firstRecorded).all { it.buildIdentity != null }) { "SshLegacyHistoryOrder" }
    }
    fun snapshot(): List<SshAttempt> = attempts.toList()

    fun begin(id: String, process: String, configIdentity: String, operation: SshOperation,
        buildIdentity: SshBuildIdentity, time: TransferTime): SshAttempt? {
        if (attempts.size >= ATTEMPT_LIMIT || attempts.any { !it.terminal || it.id == id }) return null
        val attempt = SshAttempt(id, process, configIdentity, operation, buildIdentity, "running",
            events = listOf(SshEvent("running", time, null)))
        validate(attempt)
        return freeze(attempt).also(attempts::add)
    }

    fun update(id: String, status: String, reason: String? = null, fingerprint: String? = null,
        authenticated: Boolean? = null, details: Map<String, String>? = null, time: TransferTime): Boolean {
        val old = attempts.lastOrNull()?.takeIf { it.id == id && !it.terminal } ?: return false
        require(old.observedHostKey == null || fingerprint == null || old.observedHostKey == fingerprint) { "SshObservedHostChanged" }
        val next = old.copy(status = status, reason = reason, observedHostKey = fingerprint ?: old.observedHostKey,
            authenticated = old.authenticated || authenticated == true, details = old.details + (details ?: emptyMap()))
        if (next == old) return false
        attempts[attempts.lastIndex] = transition(next, time)
        return true
    }

    fun reconcileInterrupted(time: TransferTime): Boolean {
        var changed = false
        attempts.indices.forEach { index ->
            val old = attempts[index]
            if (!old.terminal) {
                attempts[index] = transition(old.copy(status = "interrupted", reason = "new-process-unfinished-attempt"), time)
                changed = true
            }
        }
        return changed
    }

    private fun transition(next: SshAttempt, time: TransferTime): SshAttempt {
        val all = next.events + SshEvent(next.status, time, next.reason)
        val dropped = (all.size - EVENT_LIMIT).coerceAtLeast(0).toLong()
        val attempt = next.copy(events = all.takeLast(EVENT_LIMIT),
            droppedEvents = if (Long.MAX_VALUE - next.droppedEvents < dropped) Long.MAX_VALUE else next.droppedEvents + dropped)
        validate(attempt)
        return freeze(attempt)
    }
}
