package dev.cloudy.qualification.baseline

import java.util.Collections

internal data class SshJournalData(val attempts: List<SshAttempt>, val trust: Map<String, String>)

/** Schema policy over decoded JSON values, shared by real storage and JVM tests.
 * JSONObject grammar, UTF-8/file limits and atomic storage remain separate boundaries.
 */
internal object SshJournalCodec {
    const val SCHEMA_VERSION = 2

    fun decode(root: Map<String, Any?>): SshJournalData {
        val schema = root["schemaVersion"]
        require(schema is Int && schema in 1..SCHEMA_VERSION) { "SshJournalVersion" }
        val trusted = objectValue(root.required("trustedHosts"))
        require(trusted.size <= SshState.ATTEMPT_LIMIT) { "SshTrustLimit" }
        val trust = trusted.mapValues { (endpoint, value) ->
            require(endpoint.matches(Regex("[0-9a-f]{64}"))) { "SshTrustIdentity" }
            string(value).also { require(SshConfigSpec.validFingerprint(it)) { "SshTrustedFingerprint" } }
        }
        val rows = array(root.required("attempts"))
        require(rows.size <= SshState.ATTEMPT_LIMIT) { "SshAttemptLimit" }
        val attempts = rows.map { value ->
            val row = objectValue(value)
            val operation: SshOperation
            val build: SshBuildIdentity?
            if (schema == 1) {
                require(listOf("operation", "buildIdentity", "provenance").none { it in row }) { "SshLegacyProvenanceFields" }
                operation = SshOperation.SFTP
                build = null
            } else {
                operation = SshOperation.entries.singleOrNull { it.wireValue == string(row.required("operation")) }
                    ?: throw IllegalArgumentException("SshOperation")
                val identity = row.required("buildIdentity")
                build = if (identity == null) null else {
                    val fields = objectValue(identity)
                    require(fields.keys == setOf("versionName", "versionCode", "buildRef")) { "SshBuildFields" }
                    val code = integer(fields.required("versionCode"))
                    require(code in 1L..Int.MAX_VALUE.toLong()) { "SshBuildCode" }
                    SshBuildIdentity(string(fields.required("versionName")), code.toInt(), string(fields.required("buildRef")))
                }
                val expected = if (build == null) "external-legacy" else "recorded-build"
                require(string(row.required("provenance")) == expected && (build != null || operation == SshOperation.SFTP)) {
                    "SshBuildProvenance"
                }
            }
            val detail = objectValue(row.required("details"))
            require(detail.size <= SshState.DETAIL_LIMIT) { "SshDetailLimit" }
            val events = array(row.required("events"))
            require(events.size in 1..SshState.EVENT_LIMIT) { "SshEventLimit" }
            val authenticated = row.required("authenticated")
            require(authenticated is Boolean) { "SshAuthType" }
            SshAttempt(
                id = string(row.required("id")), originProcessId = string(row.required("originProcessId")),
                configIdentity = string(row.required("configIdentity")), operation = operation, buildIdentity = build,
                status = string(row.required("status")), reason = optionalString(row.required("reason")),
                observedHostKey = optionalString(row.required("observedHostKey")), authenticated = authenticated,
                details = detail.mapValues { string(it.value) }, droppedEvents = integer(row.required("droppedEvents")),
                events = events.map {
                    val event = objectValue(it)
                    SshEvent(string(event.required("status")), TransferTime(string(event.required("deviceUtc")),
                        integer(event.required("elapsedRealtimeMs"))), optionalString(event.required("reason")))
                },
            )
        }
        // Defensive model snapshots preserve every legacy field and reject forged
        // transitions without borrowing a build identity from current metadata.
        return SshJournalData(SshState(attempts).snapshot(), Collections.unmodifiableMap(LinkedHashMap(trust)))
    }

    fun encode(processId: String, revision: Long, probeVersion: String, snapshotAtDeviceUtc: String,
        state: SshState, trust: Map<String, String>): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to SCHEMA_VERSION, "probeVersion" to probeVersion,
        "processInstanceId" to processId, "revision" to revision, "snapshotAtDeviceUtc" to snapshotAtDeviceUtc,
        "trustedHosts" to LinkedHashMap(trust),
        "attempts" to state.snapshot().map { attempt ->
            linkedMapOf(
                "id" to attempt.id, "originProcessId" to attempt.originProcessId,
                "configIdentity" to attempt.configIdentity, "operation" to attempt.operation.wireValue,
                "provenance" to attempt.provenance,
                "buildIdentity" to attempt.buildIdentity?.let {
                    linkedMapOf("versionName" to it.versionName, "versionCode" to it.versionCode, "buildRef" to it.buildRef)
                },
                "status" to attempt.status, "reason" to attempt.reason, "observedHostKey" to attempt.observedHostKey,
                "authenticated" to attempt.authenticated, "details" to LinkedHashMap(attempt.details),
                "droppedEvents" to attempt.droppedEvents,
                "events" to attempt.events.map {
                    linkedMapOf("status" to it.status, "reason" to it.reason,
                        "deviceUtc" to it.time.deviceUtc, "elapsedRealtimeMs" to it.time.elapsedRealtimeMs)
                },
            )
        },
        "scope" to "Finite native SSH/SFTP and exec/PTY diagnostic; no automatic reconnect, overwrite or remote deletion. " +
            "Legacy execution builds remain external evidence; historical completion is not a new remote-file audit.",
    )

    private fun Map<String, Any?>.required(key: String): Any? {
        require(containsKey(key)) { "SshJournalMissingField" }
        return get(key)
    }
    private fun string(value: Any?): String = value as? String ?: throw IllegalArgumentException("SshJournalString")
    private fun optionalString(value: Any?): String? = if (value == null) null else string(value)
    private fun integer(value: Any?): Long = when (value) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("SshJournalInteger")
    }
    private fun array(value: Any?): List<*> = value as? List<*> ?: throw IllegalArgumentException("SshJournalArray")
    private fun objectValue(value: Any?): Map<String, Any?> {
        require(value is Map<*, *> && value.keys.all { it is String }) { "SshJournalObject" }
        return value.entries.associate { it.key as String to it.value }
    }
}
