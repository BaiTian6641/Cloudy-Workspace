package dev.cloudy.qualification.baseline

internal data class TerminalJournalData(val attempts: List<TerminalAttempt>, val trust: Map<String, String>)

/** Pure schema validation; Android parsing/storage and remote execution are separate boundaries. */
internal object TerminalJournalCodec {
    fun encode(process: String, revision: Long, time: String, state: TerminalState,
        trust: Map<String, String>): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to 1, "processInstanceId" to process, "revision" to revision,
        "snapshotAtDeviceUtc" to time, "trustedHosts" to trust.toMap(),
        "attempts" to state.snapshot().map { row -> linkedMapOf(
            "sessionId" to row.identity.sessionId, "attachmentId" to row.identity.attachmentId,
            "originProcessId" to row.originProcessId, "configIdentity" to row.configIdentity,
            "buildIdentity" to mapOf("versionName" to row.buildIdentity.versionName,
                "versionCode" to row.buildIdentity.versionCode, "buildRef" to row.buildIdentity.buildRef),
            "status" to row.status, "reason" to row.reason, "observedHostKey" to row.observedHostKey,
            "authenticated" to row.authenticated, "details" to row.details.toMap(),
            "droppedEvents" to row.droppedEvents,
            "events" to row.events.map { mapOf("status" to it.status, "reason" to it.reason,
                "deviceUtc" to it.time.deviceUtc, "elapsedRealtimeMs" to it.time.elapsedRealtimeMs) },
        ) },
    )

    fun decode(root: Map<String, Any?>): TerminalJournalData {
        val requiredRoot = setOf("schemaVersion", "processInstanceId", "revision", "snapshotAtDeviceUtc", "trustedHosts", "attempts")
        val providerFields = setOf("providerNamesAtRuntimeCreation", "providerNamesNow", "providerOrderUnchanged")
        require(root.keys == requiredRoot || root.keys == requiredRoot + providerFields) { "TerminalJournalFields" }
        require(integer(root.required("schemaVersion")) == 1L) { "TerminalJournalVersion" }
        require(canonicalTerminalUuid(string(root.required("processInstanceId"))))
        require(integer(root.required("revision")) >= 0)
        val snapshotTime = string(root.required("snapshotAtDeviceUtc"))
        require(snapshotTime.length in 1..64 && snapshotTime.all { it.code in 32..126 })
        if ("providerOrderUnchanged" in root) {
            fun providers(key: String): List<String> = array(root.required(key)).map { string(it) }.also { values ->
                require(values.size in 1..32 && values.all { value ->
                    value.length in 1..128 && value.all { it.code in 32..126 }
                })
            }
            require(root["providerOrderUnchanged"] is Boolean)
            require(root["providerOrderUnchanged"] == (providers("providerNamesAtRuntimeCreation") == providers("providerNamesNow")))
        }
        val trust = obj(root.required("trustedHosts")).mapValues { (key, value) ->
            require(key.matches(Regex("[0-9a-f]{64}")))
            string(value).also { require(SshConfigSpec.validFingerprint(it)) }
        }
        require(trust.size <= TerminalState.ATTEMPT_LIMIT)
        val rows = array(root.required("attempts"))
        require(rows.size <= TerminalState.ATTEMPT_LIMIT)
        val attempts = rows.map { value ->
            val row = obj(value)
            require(row.keys == setOf("sessionId", "attachmentId", "originProcessId", "configIdentity", "buildIdentity",
                "status", "reason", "observedHostKey", "authenticated", "details", "events", "droppedEvents"))
            val build = obj(row.required("buildIdentity"))
            require(build.keys == setOf("versionName", "versionCode", "buildRef"))
            val code = integer(build.required("versionCode")); require(code in 1L..Int.MAX_VALUE)
            val events = array(row.required("events")); require(events.size in 1..TerminalState.EVENT_LIMIT)
            TerminalAttempt(
                TerminalIdentity(string(row.required("sessionId")), string(row.required("attachmentId"))),
                string(row.required("originProcessId")), string(row.required("configIdentity")),
                SshBuildIdentity(string(build.required("versionName")), code.toInt(), string(build.required("buildRef"))),
                string(row.required("status")), nullableString(row.required("reason")),
                nullableString(row.required("observedHostKey")),
                row.required("authenticated") as? Boolean ?: throw IllegalArgumentException("TerminalJournalBoolean"),
                obj(row.required("details")).mapValues { string(it.value) },
                events.map { item -> val event = obj(item)
                    require(event.keys == setOf("status", "reason", "deviceUtc", "elapsedRealtimeMs"))
                    TerminalEvent(string(event.required("status")), nullableString(event.required("reason")),
                        TransferTime(string(event.required("deviceUtc")), integer(event.required("elapsedRealtimeMs"))))
                }, integer(row.required("droppedEvents")),
            )
        }
        return TerminalJournalData(TerminalState(attempts).snapshot(), trust.toMap())
    }

    private fun Map<String, Any?>.required(key: String): Any? {
        require(containsKey(key)) { "TerminalJournalMissingField" }; return get(key)
    }
    private fun string(value: Any?): String = value as? String ?: throw IllegalArgumentException("TerminalJournalString")
    private fun nullableString(value: Any?): String? = value?.let(::string)
    private fun integer(value: Any?): Long = when (value) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("TerminalJournalInteger")
    }
    private fun array(value: Any?): List<*> = value as? List<*> ?: throw IllegalArgumentException("TerminalJournalArray")
    private fun obj(value: Any?): Map<String, Any?> {
        require(value is Map<*, *> && value.keys.all { it is String }) { "TerminalJournalObject" }
        return value.entries.associate { it.key as String to it.value }
    }
}
