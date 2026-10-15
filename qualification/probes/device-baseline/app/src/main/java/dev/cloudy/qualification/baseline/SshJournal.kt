package dev.cloudy.qualification.baseline

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Instant

/** One IO owner serializes evidence and explicit trust; no endpoint or key material is stored. */
internal class SshJournal(directory: File) {
    private val file = AtomicFile(File(directory, "ssh-journal.json"))

    data class Restored(val attempts: List<SshAttempt>, val trust: Map<String, String>)

    fun read(): Restored {
        checkFiles()
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            return Restored(emptyList(), emptyMap())
        }
        val bytes = file.openRead().use { it.readNBytes(LIMIT + 1) }
        require(bytes.size <= LIMIT) { "SshJournalLimit" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        SshJournalGuard.check(text)
        val root = JSONObject(text)
        require(root.get("schemaVersion") == 1) { "SshJournalVersion" }
        val trusted = root.getJSONObject("trustedHosts")
        require(trusted.length() <= SshState.ATTEMPT_LIMIT) { "SshTrustLimit" }
        val trust = trusted.keys().asSequence().associateWith { key ->
            require(key.matches(Regex("[0-9a-f]{64}"))) { "SshTrustIdentity" }
            strictString(trusted, key).also { require(SshConfigSpec.validFingerprint(it)) }
        }
        val rows = root.getJSONArray("attempts")
        require(rows.length() <= SshState.ATTEMPT_LIMIT) { "SshAttemptLimit" }
        val attempts = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val detail = row.getJSONObject("details")
            require(detail.length() <= 32) { "SshDetailLimit" }
            val events = row.getJSONArray("events")
            require(events.length() in 1..SshState.EVENT_LIMIT) { "SshEventLimit" }
            require(row.get("authenticated") is Boolean) { "SshAuthType" }
            val dropped = row.get("droppedEvents")
            require(dropped is Int || dropped is Long) { "SshDroppedEventsType" }
            SshAttempt(
                id = strictString(row, "id"), originProcessId = strictString(row, "originProcessId"),
                configIdentity = strictString(row, "configIdentity"), status = strictString(row, "status"),
                reason = optionalString(row, "reason"), observedHostKey = optionalString(row, "observedHostKey"),
                authenticated = row.getBoolean("authenticated"),
                details = detail.keys().asSequence().associateWith { strictString(detail, it) },
                droppedEvents = dropped.toString().toLong(), events = (0 until events.length()).map { n ->
                    val event = events.getJSONObject(n)
                    val elapsed = event.get("elapsedRealtimeMs")
                    require(elapsed is Long || elapsed is Int) { "SshEventTime" }
                    SshEvent(strictString(event, "status"), TransferTime(
                        strictString(event, "deviceUtc"), elapsed.toString().toLong()), optionalString(event, "reason"))
                }
            )
        }
        // The pure model validates all restored transitions, identifiers and bounds.
        SshState(attempts)
        return Restored(attempts, trust)
    }

    fun encode(processId: String, revision: Long, state: SshState, trust: Map<String, String>): JSONObject =
        JSONObject().put("schemaVersion", 1).put("probeVersion", BuildConfig.VERSION_NAME)
            .put("processInstanceId", processId).put("revision", revision)
            .put("snapshotAtDeviceUtc", Instant.now().toString())
            .put("trustedHosts", JSONObject(trust))
            .put("attempts", JSONArray(state.snapshot().map { a ->
                JSONObject().put("id", a.id).put("originProcessId", a.originProcessId)
                    .put("configIdentity", a.configIdentity).put("status", a.status)
                    .put("reason", a.reason ?: JSONObject.NULL)
                    .put("observedHostKey", a.observedHostKey ?: JSONObject.NULL)
                    .put("authenticated", a.authenticated).put("details", JSONObject(a.details))
                    .put("droppedEvents", a.droppedEvents)
                    .put("events", JSONArray(a.events.map { e ->
                        JSONObject().put("status", e.status).put("reason", e.reason ?: JSONObject.NULL)
                            .put("deviceUtc", e.time.deviceUtc).put("elapsedRealtimeMs", e.time.elapsedRealtimeMs)
                    }))
            }))
            .put("scope", "Finite native SSH/SFTP diagnostic; no automatic reconnect, overwrite or remote deletion. Historical completed attempts are observations, not a new audit of remote files.")

    fun write(value: JSONObject) {
        checkFiles()
        val bytes = value.toString(2).toByteArray(Charsets.UTF_8)
        if (bytes.size > LIMIT) throw IOException("SshJournalLimit")
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        if (!file.openRead().use { it.readNBytes(bytes.size + 1) }.contentEquals(bytes)) {
            throw IOException("SshJournalReadback")
        }
    }

    private fun checkFiles() {
        // Private fixture paths may be provisioned by run-as. Refuse special files before open;
        // the byte cap remains necessary for normal files that grow after this check.
        listOf(file.baseFile, File(file.baseFile.path + ".bak"), File(file.baseFile.path + ".new")).forEach {
            val path = it.toPath()
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= LIMIT) {
                    "SshJournalFileTypeOrSize"
                }
            }
        }
    }

    private fun strictString(o: JSONObject, key: String): String =
        (o.get(key) as? String) ?: throw IllegalArgumentException("SshJournalString")
    private fun optionalString(o: JSONObject, key: String): String? =
        if (o.get(key) == JSONObject.NULL) null else strictString(o, key)

    companion object { const val LIMIT = 128 * 1024 }
}
