package dev.cloudy.qualification.baseline

import java.util.UUID

internal data class TransferTime(val deviceUtc: String, val elapsedRealtimeMs: Long)
internal data class TransferEvent(val status: String, val time: TransferTime, val reason: String?)

internal data class TransferAttempt(
    val id: String,
    val originProcessId: String,
    val expectedLengthBytes: Long,
    val expectedSha256: String,
    val status: String,
    val bytesWritten: Long,
    val claimed: Boolean,
    val verifiedLengthBytes: Long? = null,
    val verifiedSha256: String? = null,
    val reason: String? = null,
    val stopReason: Int? = null,
    val stopOutcome: String? = null,
    val events: List<TransferEvent> = emptyList(),
    val droppedEvents: Long = 0,
) {
    val terminal: Boolean get() = status in setOf("completed", "cancelled", "interrupted", "failed")
}

/** Caller holds the process state lock. Cancellation is a monotonic decision;
 * old attempts and repeated OS deliveries cannot restart or complete work.
 */
internal class TransferState(private val processId: String, restored: List<TransferAttempt> = emptyList()) {
    companion object { const val ATTEMPT_LIMIT = 8; const val EVENT_LIMIT = 24 }
    private val attempts = restored.toMutableList()
    var revision = 0L
        private set
    init { require(restored.size <= ATTEMPT_LIMIT && restored.map { it.id }.distinct().size == restored.size) }
    fun snapshot(): List<TransferAttempt> = attempts.toList()
    fun current(): TransferAttempt? = attempts.lastOrNull()
    fun find(id: String): TransferAttempt? = attempts.find { it.id == id }
    fun canStart(): Boolean = attempts.size < ATTEMPT_LIMIT && (current()?.terminal != false)

    fun recover(time: TransferTime): Boolean {
        var changed = false
        attempts.indices.forEach { index ->
            val old = attempts[index]
            if (!old.terminal) {
                attempts[index] = transition(old, "interrupted", time, "new-process-unfinished-attempt")
                changed = true
            }
        }
        return changed
    }

    fun auditSavedCompletion(id: String, fileMatches: Boolean, time: TransferTime) {
        val index = attempts.indexOfFirst { it.id == id }
        if (index < 0 || attempts[index].status != "completed") return
        val old = attempts[index]
        attempts[index] = if (fileMatches) transition(old, "completed", time, "new-process-file-reverified")
            else transition(old.copy(verifiedLengthBytes = null, verifiedSha256 = null), "failed", time, "saved-completion-file-mismatch")
    }

    fun begin(length: Long, hash: String, time: TransferTime): TransferAttempt? {
        if (!canStart()) return null
        require(length in 1..TransferConfigSpec.MAX_BYTES && hash.matches(Regex("[0-9a-f]{64}")))
        val attempt = TransferAttempt(UUID.randomUUID().toString(), processId, length, hash, "queued", 0, false,
            events = listOf(TransferEvent("queued", time, null)))
        attempts.add(attempt)
        revision++
        return attempt
    }

    fun claim(id: String, time: TransferTime): Boolean = updateCurrent(id) { old ->
        if (old.status != "queued" || old.claimed || old.originProcessId != processId) null
        else transition(old.copy(claimed = true), "running", time)
    }

    fun progress(id: String, bytes: Long): Boolean = updateCurrent(id) { old ->
        if (old.status != "running" || bytes < old.bytesWritten || bytes > old.expectedLengthBytes) null
        else old.copy(bytesWritten = bytes).also { if (it != old) revision++ }
    }

    fun verifying(id: String, time: TransferTime): Boolean = updateCurrent(id) { old ->
        if (old.status != "running" || old.bytesWritten != old.expectedLengthBytes) null
        else transition(old, "verifying", time)
    }

    fun complete(id: String, verified: TransferDigest, time: TransferTime): Boolean = updateCurrent(id) { old ->
        if (old.status != "verifying" || old.stopOutcome != null || verified.lengthBytes != old.expectedLengthBytes ||
            verified.sha256 != old.expectedSha256) null
        else transition(old.copy(verifiedLengthBytes = verified.lengthBytes, verifiedSha256 = verified.sha256), "completed", time)
    }

    fun stop(id: String, outcome: String, reason: String, stopReason: Int?, time: TransferTime): Boolean {
        require(outcome == "cancelled" || outcome == "interrupted")
        return updateCurrent(id) { old ->
            if (old.terminal || old.stopOutcome != null) null else transition(
                old.copy(stopOutcome = outcome, stopReason = stopReason), "cancel-requested", time, reason)
        }
    }

    fun finishStopped(id: String, time: TransferTime): Boolean = updateCurrent(id) { old ->
        val outcome = old.stopOutcome
        if (old.terminal || outcome == null) null else transition(old, outcome, time, old.reason)
    }

    fun fail(id: String, reason: String, time: TransferTime): Boolean = updateCurrent(id) { old ->
        if (old.terminal || old.stopOutcome != null) null else transition(old, "failed", time, reason)
    }

    private fun updateCurrent(id: String, change: (TransferAttempt) -> TransferAttempt?): Boolean {
        val old = current()?.takeIf { it.id == id } ?: return false
        val next = change(old) ?: return false
        attempts[attempts.lastIndex] = next
        return next != old
    }

    private fun transition(old: TransferAttempt, status: String, time: TransferTime, reason: String? = null): TransferAttempt {
        val all = old.events + TransferEvent(status, time, reason)
        revision++
        return old.copy(status = status, reason = reason, events = all.takeLast(EVENT_LIMIT),
            droppedEvents = old.droppedEvents + (all.size - EVENT_LIMIT).coerceAtLeast(0))
    }
}
