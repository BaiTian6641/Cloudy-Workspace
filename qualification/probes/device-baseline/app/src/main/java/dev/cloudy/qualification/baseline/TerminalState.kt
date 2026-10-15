package dev.cloudy.qualification.baseline

import java.util.Collections

internal data class TerminalEvent(val status: String, val reason: String?, val time: TransferTime)

internal data class TerminalAttempt(
    val identity: TerminalIdentity,
    val originProcessId: String,
    val configIdentity: String,
    val buildIdentity: SshBuildIdentity,
    val status: String,
    val reason: String?,
    val observedHostKey: String?,
    val authenticated: Boolean,
    val details: Map<String, String>,
    val events: List<TerminalEvent>,
    val droppedEvents: Long = 0,
) {
    val terminal: Boolean get() = status in TerminalState.TERMINAL_STATUSES
}

/** Only the process IO owner mutates history. No terminal data or commands enter this model. */
internal class TerminalState(restored: List<TerminalAttempt> = emptyList()) {
    private val rows = restored.map { validate(it); freeze(it) }.toMutableList()

    init {
        require(rows.size <= ATTEMPT_LIMIT && rows.map { it.identity.sessionId }.distinct().size == rows.size)
        require(rows.dropLast(1).all { it.terminal }) { "TerminalUnfinishedHistory" }
    }

    fun snapshot(): List<TerminalAttempt> = rows.toList()

    fun begin(identity: TerminalIdentity, process: String, config: String, build: SshBuildIdentity,
        time: TransferTime, rendererDetails: Map<String, String> = emptyMap()): Boolean {
        if (rows.size >= ATTEMPT_LIMIT || rows.lastOrNull()?.terminal == false ||
            rows.any { it.identity.sessionId == identity.sessionId }) return false
        val row = TerminalAttempt(identity, process, config, build, "connecting", null, null, false,
            rendererDetails, listOf(TerminalEvent("connecting", null, time)))
        validate(row); rows.add(freeze(row)); return true
    }

    fun update(identity: TerminalIdentity, status: String, reason: String?, time: TransferTime,
        observed: String? = null, authenticated: Boolean = false, details: Map<String, String> = emptyMap()) {
        val old = rows.lastOrNull() ?: throw IllegalStateException("TerminalMissingAttempt")
        require(old.identity == identity && !old.terminal) { "TerminalStaleUpdate" }
        // The first host identity is immutable, even if a later rekey fails.
        require(observed == null || old.observedHostKey == null || observed == old.observedHostKey)
        val events = old.events + TerminalEvent(status, reason, time)
        val excess = (events.size - EVENT_LIMIT).coerceAtLeast(0)
        val row = old.copy(status = status, reason = reason,
            observedHostKey = old.observedHostKey ?: observed,
            authenticated = old.authenticated || authenticated, details = old.details + details,
            events = events.takeLast(EVENT_LIMIT), droppedEvents = Math.addExact(old.droppedEvents, excess.toLong()))
        validate(row); rows[rows.lastIndex] = freeze(row)
    }

    fun reconcileInterrupted(time: TransferTime) {
        rows.lastOrNull()?.takeUnless { it.terminal }?.let {
            update(it.identity, "interrupted", "new-process-unfinished-session", time)
        }
    }

    companion object {
        const val ATTEMPT_LIMIT = 8
        const val EVENT_LIMIT = 96
        val TERMINAL_STATUSES = setOf("trust-required", "host-key-rejected", "closed", "interrupted", "failed")
        private val activeStatuses = setOf("connecting", "host-key-verified", "authenticated", "open", "closing")
        private val countKeys = setOf("hostVerificationCount", "inputBytes", "stdoutBytes", "stderrBytes",
            "ptyInitialCols", "ptyInitialRows", "ptyLastCols", "ptyLastRows")
        private val detailKeys = countKeys + setOf("kex", "hostSignature", "cipherC2S", "cipherS2C",
            "macC2S", "macS2C", "compressionC2S", "compressionS2C", "rendererProvider", "rendererVersion",
            "rendererMessageListener", "rendererArrayBuffer")

        private fun validate(row: TerminalAttempt) {
            require(canonicalTerminalUuid(row.originProcessId) && row.configIdentity.matches(Regex("[0-9a-f]{64}")))
            require(row.status in activeStatuses + TERMINAL_STATUSES)
            require(row.events.size in 1..EVENT_LIMIT && row.droppedEvents >= 0)
            require(row.observedHostKey == null || SshConfigSpec.validFingerprint(row.observedHostKey))
            require(!row.authenticated || row.observedHostKey != null)
            require(row.status !in setOf("authenticated", "open", "closing", "closed") || row.authenticated)
            require(row.status !in setOf("trust-required", "host-key-rejected") ||
                (!row.authenticated && row.observedHostKey != null))
            require(row.details.keys.all { it in detailKeys } && row.details.values.all {
                it.length in 1..128 && it.all { c -> c.code in 32..126 }
            }) { "TerminalDetailFields" }
            row.details.filterKeys { it in countKeys }.forEach { (_, value) ->
                require(value.matches(Regex("0|[1-9][0-9]*")) && value.toLongOrNull()?.let { it >= 0 } == true)
            }
            listOf("rendererMessageListener", "rendererArrayBuffer").forEach { key ->
                require(row.details[key] == null || row.details[key] in setOf("true", "false"))
            }
            fun reason(value: String?) {
                require(value == null || value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,99}")))
            }
            reason(row.reason)
            row.events.forEach {
                require(it.status in activeStatuses + TERMINAL_STATUSES)
                require(it.status !in setOf("authenticated", "open", "closing", "closed") || row.authenticated)
                require(it.status != "host-key-verified" || row.observedHostKey != null)
                reason(it.reason)
                require(it.time.elapsedRealtimeMs >= 0 && it.time.deviceUtc.length in 1..64 &&
                    it.time.deviceUtc.all { c -> c.code in 32..126 })
            }
            require(row.events.dropLast(1).none { it.status in TERMINAL_STATUSES })
            require(row.droppedEvents != 0L || row.events.first().status == "connecting")
            val order = listOf("connecting", "host-key-verified", "authenticated", "open", "closing")
            row.events.zipWithNext().forEach { (before, after) ->
                require(after.status in TERMINAL_STATUSES || order.indexOf(after.status) >= order.indexOf(before.status))
            }
            require(row.events.last().status == row.status && row.events.last().reason == row.reason)
        }

        private fun freeze(row: TerminalAttempt) = row.copy(
            details = Collections.unmodifiableMap(LinkedHashMap(row.details)),
            events = Collections.unmodifiableList(ArrayList(row.events)),
        )
    }
}
