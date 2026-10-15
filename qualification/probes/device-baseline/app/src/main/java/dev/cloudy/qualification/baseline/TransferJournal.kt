package dev.cloudy.qualification.baseline

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.UUID

/** One process IO executor owns this file. Endpoints, certificates and exception
 * messages are deliberately absent. A successful write requires bounded readback.
 */
internal class TransferJournal(directory: File) {
    companion object { const val BYTE_LIMIT = 128 * 1024 }
    private val file = AtomicFile(File(directory, "transfer-journal.json"))

    fun read(): List<TransferAttempt> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val bytes = file.openRead().use { it.readNBytes(BYTE_LIMIT + 1) }
        require(bytes.size <= BYTE_LIMIT) { "JournalByteLimit" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val root = JSONObject(text)
        require(number(root, "schemaVersion") == 1L && string(root, "fixtureId") == TransferConfigSpec.FIXTURE_ID) { "JournalSchema" }
        val rows = root.getJSONArray("attempts")
        require(rows.length() <= TransferState.ATTEMPT_LIMIT) { "JournalAttemptLimit" }
        return (0 until rows.length()).map { parseAttempt(rows.getJSONObject(it)) }.also { attempts ->
            require(attempts.map { it.id }.distinct().size == attempts.size) { "JournalDuplicateAttempt" }
        }
    }

    fun encode(processId: String, processStartedAt: String, revision: Long, attempts: List<TransferAttempt>): JSONObject = JSONObject()
        .put("schemaVersion", 1).put("probeVersion", BuildConfig.VERSION_NAME)
        .put("recordingProcessInstanceId", processId).put("processStartedAtDeviceUtc", processStartedAt)
        .put("snapshotAtDeviceUtc", Instant.now().toString()).put("revision", revision)
        .put("fixtureId", TransferConfigSpec.FIXTURE_ID).put("jobId", TransferRuntime.JOB_ID)
        .put("attemptLimit", TransferState.ATTEMPT_LIMIT).put("eventLimitPerAttempt", TransferState.EVENT_LIMIT)
        .put("payloadByteLimitPerAttempt", TransferConfigSpec.MAX_BYTES)
        .put("attempts", JSONArray(attempts.map(::attemptJson)))
        .put("scope", "Finite diagnostic HTTPS download; not SFTP or product qualification. Private parts are retained, never published or automatically deleted. Completed requires a separate file reread of expected length and SHA-256. Missing terminal callbacks are not reconstructed; unfinished attempts become interrupted in a new process. No retry or resume.")

    fun write(value: JSONObject) {
        val bytes = value.toString(2).toByteArray(Charsets.UTF_8)
        if (bytes.size > BYTE_LIMIT) throw IOException("JournalByteLimit")
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        val readback = file.openRead().use { it.readNBytes(bytes.size + 1) }
        if (!readback.contentEquals(bytes)) throw IOException("JournalReadbackMismatch")
    }

    private fun attemptJson(a: TransferAttempt): JSONObject = JSONObject()
        .put("attemptId", a.id).put("originProcessInstanceId", a.originProcessId)
        .put("expectedLengthBytes", a.expectedLengthBytes).put("expectedSha256", a.expectedSha256)
        .put("status", a.status).put("bytesWritten", a.bytesWritten).put("claimed", a.claimed)
        .put("verifiedLengthBytes", a.verifiedLengthBytes ?: JSONObject.NULL)
        .put("verifiedSha256", a.verifiedSha256 ?: JSONObject.NULL)
        .put("reason", a.reason ?: JSONObject.NULL).put("stopReason", a.stopReason ?: JSONObject.NULL)
        .put("stopOutcome", a.stopOutcome ?: JSONObject.NULL)
        .put("relativePartFile", "transfer-payloads/${a.id}.part")
        .put("events", JSONArray(a.events.map { event -> JSONObject()
            .put("status", event.status).put("deviceUtc", event.time.deviceUtc)
            .put("elapsedRealtimeMs", event.time.elapsedRealtimeMs).put("reason", event.reason ?: JSONObject.NULL)
        })).put("droppedEventCount", a.droppedEvents)

    private fun parseAttempt(row: JSONObject): TransferAttempt {
        fun optionalString(key: String): String? = if (nullValue(row, key)) null else string(row, key)
        val id = string(row, "attemptId")
        val origin = string(row, "originProcessInstanceId")
        require(UUID.fromString(id).toString() == id && UUID.fromString(origin).toString() == origin) { "JournalIdentity" }
        require(string(row, "relativePartFile") == "transfer-payloads/$id.part") { "JournalPartPath" }
        val length = number(row, "expectedLengthBytes")
        val hash = string(row, "expectedSha256")
        val bytes = number(row, "bytesWritten")
        val status = string(row, "status")
        val statuses = setOf("queued", "running", "verifying", "cancel-requested", "completed", "cancelled", "interrupted", "failed")
        require(length in 1..TransferConfigSpec.MAX_BYTES && bytes in 0..length && hash.matches(Regex("[0-9a-f]{64}")) && status in statuses) { "JournalValues" }
        val events = row.getJSONArray("events")
        require(events.length() in 1..TransferState.EVENT_LIMIT) { "JournalEventLimit" }
        val parsedEvents = (0 until events.length()).map { index ->
            val event = events.getJSONObject(index)
            val whenUtc = string(event, "deviceUtc")
            Instant.parse(whenUtc)
            val elapsed = number(event, "elapsedRealtimeMs")
            val eventStatus = string(event, "status")
            val reason = if (nullValue(event, "reason")) null else string(event, "reason")
            require(elapsed >= 0 && eventStatus in statuses && (reason == null || reason.matches(Regex("[A-Za-z0-9_-]{1,100}")))) { "JournalEvent" }
            TransferEvent(eventStatus, TransferTime(whenUtc, elapsed), reason)
        }
        val verifiedBytes = if (nullValue(row, "verifiedLengthBytes")) null else number(row, "verifiedLengthBytes")
        val verifiedHash = optionalString("verifiedSha256")
        val reason = optionalString("reason")
        val stopOutcome = optionalString("stopOutcome")
        require(reason == null || reason.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "JournalReason" }
        require(stopOutcome == null || stopOutcome in setOf("cancelled", "interrupted")) { "JournalStop" }
        require(status != "completed" || (verifiedBytes == length && verifiedHash == hash && bytes == length && stopOutcome == null)) { "JournalCompletion" }
        require(status == "completed" || (verifiedBytes == null && verifiedHash == null)) { "JournalVerification" }
        val dropped = number(row, "droppedEventCount")
        require(dropped >= 0 && parsedEvents.last().status == status) { "JournalHistory" }
        val claimed = row.get("claimed")
        require(claimed is Boolean) { "JournalBooleanType" }
        val stopReason = if (nullValue(row, "stopReason")) null else number(row, "stopReason").also {
            require(it in 0..Int.MAX_VALUE.toLong()) { "JournalStopReason" }
        }.toInt()
        require(status !in setOf("running", "verifying", "completed") || claimed) { "JournalClaim" }
        require(status != "queued" || !claimed) { "JournalQueuedClaim" }
        require(status != "cancel-requested" || stopOutcome != null) { "JournalPendingStop" }
        return TransferAttempt(id, origin, length, hash, status, bytes, claimed,
            verifiedBytes, verifiedHash, reason, stopReason,
            stopOutcome, parsedEvents, dropped)
    }

    // JSONObject getters coerce strings/numbers. Persisted control state must
    // retain its JSON types; malformed state is an explicit initialization error.
    private fun string(row: JSONObject, key: String): String = row.get(key).let {
        require(it is String) { "JournalStringType" }; it
    }
    private fun number(row: JSONObject, key: String): Long = row.get(key).let {
        require(it is Int || it is Long) { "JournalNumberType" }; (it as Number).toLong()
    }
    private fun nullValue(row: JSONObject, key: String): Boolean = row.get(key) === JSONObject.NULL
}
