package io.github.baitian6641.cloudy.testing.ime

import android.content.Context
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * schema1 private protocol. Single writer per file: the client owns lease/requests,
 * the receiver owns results, the service owns state. The lease.lock directory is the
 * exclusive reservation that makes replace publication safe.
 */
internal object ImeFixtureProtocol {
    const val SCHEMA = 1
    const val DIRECTORY_NAME = "ime-qualification"
    const val LOCK_NAME = "lease.lock"
    const val ACTION = "io.github.baitian6641.cloudy.testing.ime.COMMAND"
    const val EXTRA_REQUEST = "request"
    const val SERVICE_CLASS = "io.github.baitian6641.cloudy.testing.ime.QualificationImeService"
    const val RECEIVER_CLASS = "io.github.baitian6641.cloudy.testing.ime.QualificationImeReceiver"
    const val MAX_TEXT_BYTES = 64 * 1024
    const val MAX_REQUESTS = 64
    const val MAX_REQUEST_AGE_MILLIS = 20_000L

    // Worst-case JSON escaping of 64 KiB can exceed 6x; this envelope is deliberately larger.
    private const val MAX_REQUEST_FILE_BYTES = 512 * 1024
    private const val MAX_SMALL_FILE_BYTES = 64 * 1024
    private const val DIRECTORY_MODE = 448 // 0700
    private const val FILE_MODE = 384 // 0600

    fun directory(context: Context): File {
        val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)
        if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            try {
                Os.mkdir(directory.path, DIRECTORY_MODE)
            } catch (failure: ErrnoException) {
                if (failure.errno != OsConstants.EEXIST) throw IOException("IME_QUALIFICATION_DIRECTORY")
            }
        }
        verifyDirectory(directory)
        return directory
    }

    fun leaseFile(context: Context): File = File(directory(context), "lease.json")
    fun stateFile(context: Context): File = File(directory(context), "state.json")
    fun sessionResultFile(context: Context): File = File(directory(context), "session-result.json")
    fun requestFile(context: Context, run: String, request: String): File =
        File(directory(context), "$run-$request.request.json")
    fun resultFile(context: Context, run: String, request: String): File =
        File(directory(context), "$run-$request.result.json")
    fun stagingFile(file: File): File = File(file.parentFile, "${file.name}.new")

    fun acquireLock(context: Context): Boolean {
        val lock = File(directory(context), LOCK_NAME)
        return try {
            Os.mkdir(lock.path, DIRECTORY_MODE)
            true
        } catch (failure: ErrnoException) {
            if (failure.errno != OsConstants.EEXIST) throw IOException("IME_QUALIFICATION_LOCK")
            false
        }
    }

    fun releaseLock(context: Context): Boolean {
        val lock = File(directory(context), LOCK_NAME)
        return try {
            if (!Files.exists(lock.toPath(), LinkOption.NOFOLLOW_LINKS)) return true
            if (Files.isSymbolicLink(lock.toPath())) return false
            if (!Files.isDirectory(lock.toPath(), LinkOption.NOFOLLOW_LINKS)) return false
            verifyDirectory(lock)
            Files.delete(lock.toPath())
            true
        } catch (failure: IOException) {
            false
        }
    }

    fun isCanonicalUuid(value: String?): Boolean {
        if (value == null) return false
        return try {
            val uuid = UUID.fromString(value)
            uuid.version() == 4 && uuid.variant() == 2 && uuid.toString() == value
        } catch (failure: IllegalArgumentException) {
            false
        }
    }

    /** Strict UTF-8; unpaired surrogates are rejected, never replaced. */
    fun utf8(text: String): ByteArray {
        val buffer = try {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text))
        } catch (failure: CharacterCodingException) {
            throw IOException("IME_QUALIFICATION_TEXT_ENCODING")
        }
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    fun decode(bytes: ByteArray): String {
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (failure: CharacterCodingException) {
            throw IOException("IME_QUALIFICATION_TEXT_ENCODING")
        }
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 255
                append("0123456789abcdef"[value ushr 4])
                append("0123456789abcdef"[value and 15])
            }
        }
    }

    fun existsNoFollow(file: File): Boolean {
        if (Files.isSymbolicLink(file.toPath())) throw IOException("IME_QUALIFICATION_SYMLINK")
        return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)
    }

    fun resultExists(context: Context, run: String, request: String): Boolean =
        existsNoFollow(resultFile(context, run, request))

    // ---- lease -------------------------------------------------------------------------

    fun readLease(context: Context): LeaseSnapshot? {
        val value = readJson(leaseFile(context), MAX_SMALL_FILE_BYTES) ?: return null
        if (value.requiredInt("schema") != SCHEMA) throw IOException("IME_QUALIFICATION_SCHEMA")
        val run = value.requiredString("run")
        if (!isCanonicalUuid(run)) throw IOException("IME_QUALIFICATION_RUN")
        val component = value.requiredString("component")
        val ownerPid = value.requiredInt("ownerPid")
        val previousDefault = value.requiredString("previousDefault")
        if (ownerPid <= 0 || component != "${context.packageName}/$SERVICE_CLASS" ||
            previousDefault.isEmpty() || previousDefault.any { it.isWhitespace() || it < ' ' }
        ) throw IOException("IME_QUALIFICATION_LEASE")
        if (!value.has("previousEnabled") || !value.has("previousSubtype")) {
            throw IOException("IME_QUALIFICATION_LEASE")
        }
        val previousEnabled = if (value.isNull("previousEnabled")) null else value.requiredString("previousEnabled")
        val previousSubtype = if (value.isNull("previousSubtype")) null else value.requiredString("previousSubtype")
        val array = value.opt("requests") as? JSONArray ?: throw IOException("IME_QUALIFICATION_REQUEST_IDS")
        if (array.length() > MAX_REQUESTS) throw IOException("IME_QUALIFICATION_REQUEST_LIMIT")
        val requests = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val id = array.opt(index) as? String ?: throw IOException("IME_QUALIFICATION_REQUEST_ID")
            if (!isCanonicalUuid(id) || requests.contains(id)) throw IOException("IME_QUALIFICATION_REQUEST_ID")
            requests.add(id)
        }
        return LeaseSnapshot(run, component, ownerPid, previousDefault, previousEnabled, previousSubtype, requests)
    }

    fun writeLease(context: Context, lease: LeaseSnapshot, replace: Boolean) {
        if (lease.requests.size > MAX_REQUESTS) throw IOException("IME_QUALIFICATION_REQUEST_LIMIT")
        val value = JSONObject()
            .put("schema", SCHEMA)
            .put("run", lease.run)
            .put("component", lease.component)
            .put("ownerPid", lease.ownerPid)
            .put("previousDefault", lease.previousDefault)
            .put("previousEnabled", lease.previousEnabled ?: JSONObject.NULL)
            .put("previousSubtype", lease.previousSubtype ?: JSONObject.NULL)
            .put("requests", JSONArray(lease.requests))
        writeAtomic(leaseFile(context), value, replace)
    }

    // ---- state -------------------------------------------------------------------------

    fun readState(context: Context): ImeState? {
        val value = readJson(stateFile(context), MAX_SMALL_FILE_BYTES) ?: return null
        if (value.requiredInt("schema") != SCHEMA) throw IOException("IME_QUALIFICATION_SCHEMA")
        val instance = value.requiredString("instance")
        if (!isCanonicalUuid(instance)) throw IOException("IME_QUALIFICATION_INSTANCE")
        val generation = value.requiredLong("generation")
        if (generation <= 0) throw IOException("IME_QUALIFICATION_GENERATION")
        val active = value.requiredBoolean("active")
        val target = if (!active) null else ImeTargetState(
            packageName = value.requiredString("package"),
            clientPid = value.requiredInt("clientPid"),
            clientUid = value.requiredInt("clientUid"),
            fieldId = value.requiredInt("fieldId"),
            inputType = value.requiredInt("inputType"),
            imeOptions = value.requiredInt("imeOptions"),
            hasConnection = value.requiredBoolean("hasConnection"),
        )
        return ImeState(instance, generation, active, target)
    }

    fun writeState(context: Context, instance: String, generation: Long, target: ImeTargetState?) {
        if (generation <= 0) throw IOException("IME_QUALIFICATION_GENERATION")
        val value = JSONObject()
            .put("schema", SCHEMA)
            .put("instance", instance)
            .put("generation", generation)
            .put("active", target != null)
        if (target != null) {
            value.put("package", target.packageName)
            value.put("clientPid", target.clientPid)
            value.put("clientUid", target.clientUid)
            value.put("fieldId", target.fieldId)
            value.put("inputType", target.inputType)
            value.put("imeOptions", target.imeOptions)
            value.put("hasConnection", target.hasConnection)
        }
        writeAtomic(stateFile(context), value, replace = true)
    }

    // ---- request / result ---------------------------------------------------------------

    fun readRequest(context: Context, lease: LeaseSnapshot, request: String): FixtureRequest? {
        val value = readJson(requestFile(context, lease.run, request), MAX_REQUEST_FILE_BYTES) ?: return null
        if (value.requiredInt("schema") != SCHEMA) throw IOException("IME_QUALIFICATION_SCHEMA")
        if (value.requiredString("run") != lease.run) throw IOException("IME_QUALIFICATION_RUN")
        if (value.requiredString("request") != request) throw IOException("IME_QUALIFICATION_REQUEST_ID")
        val instance = value.requiredString("instance")
        if (!isCanonicalUuid(instance)) throw IOException("IME_QUALIFICATION_INSTANCE")
        val generation = value.requiredLong("generation")
        if (generation <= 0) throw IOException("IME_QUALIFICATION_GENERATION")
        val operation = ImeFixtureOperation.from(value.requiredString("operation"))
            ?: throw IOException("IME_QUALIFICATION_OPERATION")
        val text = if (value.has("text")) {
            value.opt("text") as? String ?: throw IOException("IME_QUALIFICATION_TEXT_TYPE")
        } else null
        when (operation) {
            ImeFixtureOperation.COMMIT, ImeFixtureOperation.COMPOSE ->
                if (text == null) throw IOException("IME_QUALIFICATION_TEXT_MISSING")
            else -> if (text != null) throw IOException("IME_QUALIFICATION_TEXT_UNEXPECTED")
        }
        if (text != null && utf8(text).size > MAX_TEXT_BYTES) throw IOException("IME_QUALIFICATION_TEXT_BOUNDS")
        return FixtureRequest(
            schema = SCHEMA,
            run = lease.run,
            request = request,
            instance = instance,
            generation = generation,
            targetPackage = value.requiredString("targetPackage"),
            clientPid = value.requiredInt("clientPid"),
            fieldId = value.requiredInt("fieldId"),
            expiresElapsedMillis = value.requiredLong("expiresElapsedMillis"),
            operation = operation,
            text = text,
        )
    }

    fun writeRequest(context: Context, request: FixtureRequest) {
        if (request.text != null && utf8(request.text).size > MAX_TEXT_BYTES) {
            throw IOException("IME_QUALIFICATION_TEXT_BOUNDS")
        }
        val value = JSONObject()
            .put("schema", SCHEMA)
            .put("run", request.run)
            .put("request", request.request)
            .put("instance", request.instance)
            .put("generation", request.generation)
            .put("targetPackage", request.targetPackage)
            .put("clientPid", request.clientPid)
            .put("fieldId", request.fieldId)
            .put("expiresElapsedMillis", request.expiresElapsedMillis)
            .put("operation", request.operation.wire)
        if (request.text != null) value.put("text", request.text)
        writeAtomic(requestFile(context, request.run, request.request), value, replace = false)
    }

    /** Immutable: never replaces an existing result, and never redispatches it. */
    fun writeResult(context: Context, request: FixtureRequest, result: ImeDispatchResult) {
        if ((result.outcome == ImeFixtureResult.Outcome.SUBMITTED) != (result.reason == null)) {
            throw IOException("IME_QUALIFICATION_RESULT")
        }
        val value = JSONObject()
            .put("schema", SCHEMA)
            .put("run", request.run)
            .put("request", request.request)
            .put("instance", request.instance)
            .put("generation", request.generation)
            .put("operation", request.operation.wire)
            .put("outcome", result.outcome.name)
            .put("reason", result.reason?.name ?: JSONObject.NULL)
        if (result.textLength != null) value.put("textLength", result.textLength)
        if (result.textSha256 != null) value.put("textSha256", result.textSha256)
        writeAtomic(resultFile(context, request.run, request.request), value, replace = false)
    }

    fun readResult(context: Context, run: String, request: String): ParsedResult? {
        val value = readJson(resultFile(context, run, request), MAX_SMALL_FILE_BYTES) ?: return null
        if (value.requiredInt("schema") != SCHEMA) throw IOException("IME_QUALIFICATION_SCHEMA")
        if (value.requiredString("run") != run) throw IOException("IME_QUALIFICATION_RUN")
        if (value.requiredString("request") != request) throw IOException("IME_QUALIFICATION_REQUEST_ID")
        val instance = value.requiredString("instance")
        if (!isCanonicalUuid(instance)) throw IOException("IME_QUALIFICATION_INSTANCE")
        val generation = value.requiredLong("generation")
        if (generation <= 0) throw IOException("IME_QUALIFICATION_GENERATION")
        val operation = ImeFixtureOperation.from(value.requiredString("operation"))
            ?: throw IOException("IME_QUALIFICATION_OPERATION")
        val outcome = try {
            ImeFixtureResult.Outcome.valueOf(value.requiredString("outcome"))
        } catch (failure: IllegalArgumentException) {
            throw IOException("IME_QUALIFICATION_OUTCOME")
        }
        val reason = if (value.isNull("reason")) null else try {
            ImeFixtureResult.Reason.valueOf(value.requiredString("reason"))
        } catch (failure: IllegalArgumentException) {
            throw IOException("IME_QUALIFICATION_REASON")
        }
        if ((outcome == ImeFixtureResult.Outcome.SUBMITTED) != (reason == null)) {
            throw IOException("IME_QUALIFICATION_RESULT")
        }
        return ParsedResult(run, request, instance, generation, operation, outcome, reason)
    }

    fun writeSessionResult(
        context: Context,
        run: String,
        component: String,
        restored: Boolean,
        cleanupCode: String,
        requests: List<String>,
        requestsRetired: Boolean,
        leaseRetired: Boolean,
        lockRetired: Boolean,
        shellRetired: Boolean,
        cleanupConfirmed: Boolean,
    ) {
        if (requests.size > MAX_REQUESTS) throw IOException("IME_QUALIFICATION_REQUEST_LIMIT")
        val value = JSONObject()
            .put("schema", SCHEMA)
            .put("run", run)
            .put("component", component)
            .put("restored", restored)
            .put("cleanupCode", cleanupCode)
            .put("requests", JSONArray(requests))
            .put("requestsRetired", requestsRetired)
            .put("leaseRetired", leaseRetired)
            .put("lockRetired", lockRetired)
            .put("shellRetired", shellRetired)
            .put("cleanupConfirmed", cleanupConfirmed)
        // Fixed evidence filename, atomically replaced; no text or raw settings.
        writeAtomic(sessionResultFile(context), value, replace = true)
    }

    // ---- files -------------------------------------------------------------------------

    fun deleteOwned(file: File): Boolean {
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return true
        if (Files.isSymbolicLink(file.toPath())) throw IOException("IME_QUALIFICATION_SYMLINK")
        Files.delete(file.toPath())
        return true
    }

    /** Final and deterministic staging names form one owned atomic record. */
    fun deleteRecord(file: File): Boolean {
        val staging = deleteOwned(stagingFile(file))
        val published = deleteOwned(file)
        return staging && published
    }

    fun recordAbsent(file: File): Boolean =
        !existsNoFollow(file) && !existsNoFollow(stagingFile(file))

    private fun readJson(file: File, limit: Int): JSONObject? {
        val bytes = readBytes(file, limit) ?: return null
        val text = decode(bytes)
        return try {
            JSONObject(text)
        } catch (failure: JSONException) {
            // No cause: parser messages can quote payload text.
            throw IOException("IME_QUALIFICATION_JSON")
        }
    }

    // JSONObject getters coerce strings/numbers. Authority fields must retain their JSON types.
    private fun JSONObject.requiredString(key: String): String =
        opt(key) as? String ?: throw IOException("IME_QUALIFICATION_STRING_TYPE")

    private fun JSONObject.requiredInt(key: String): Int =
        opt(key) as? Int ?: throw IOException("IME_QUALIFICATION_INTEGER_TYPE")

    private fun JSONObject.requiredLong(key: String): Long = when (val value = opt(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IOException("IME_QUALIFICATION_INTEGER_TYPE")
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        opt(key) as? Boolean ?: throw IOException("IME_QUALIFICATION_BOOLEAN_TYPE")

    private fun readBytes(file: File, limit: Int): ByteArray? {
        val fd = try {
            Os.open(
                file.path,
                OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK or
                    OsConstants.O_CLOEXEC,
                0,
            )
        } catch (failure: ErrnoException) {
            if (failure.errno == OsConstants.ENOENT) return null
            throw IOException("IME_QUALIFICATION_OPEN")
        }
        try {
            val stat = Os.fstat(fd)
            if (!OsConstants.S_ISREG(stat.st_mode)) throw IOException("IME_QUALIFICATION_TYPE")
            if (stat.st_uid != Process.myUid()) throw IOException("IME_QUALIFICATION_OWNER")
            if ((stat.st_mode and 511) != FILE_MODE) throw IOException("IME_QUALIFICATION_MODE")
            if (stat.st_size < 0 || stat.st_size > limit) throw IOException("IME_QUALIFICATION_BOUNDS")
            val bytes = ByteArray(stat.st_size.toInt())
            var offset = 0
            while (offset < bytes.size) {
                val count = Os.read(fd, bytes, offset, bytes.size - offset)
                if (count <= 0) throw IOException("IME_QUALIFICATION_SHORT_READ")
                offset += count
            }
            if (Os.read(fd, ByteArray(1), 0, 1) != 0) throw IOException("IME_QUALIFICATION_BOUNDS")
            return bytes
        } finally {
            runCatching { Os.close(fd) }
        }
    }

    private fun writeAtomic(target: File, value: JSONObject, replace: Boolean) {
        if (!replace && existsNoFollow(target)) throw IOException("IME_QUALIFICATION_EXISTS")
        val temporary = stagingFile(target)
        val fd = try {
            Os.open(temporary.path, OsConstants.O_WRONLY or OsConstants.O_CREAT or
                OsConstants.O_EXCL or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, FILE_MODE)
        } catch (failure: ErrnoException) {
            throw IOException("IME_QUALIFICATION_STAGING")
        }
        var primary: Throwable? = null
        try {
            FileOutputStream(fd).use { output ->
                output.write(utf8(value.toString()))
                output.fd.sync()
            }
            if (!replace && existsNoFollow(target)) throw IOException("IME_QUALIFICATION_EXISTS")
            // The exclusive lease and per-record writer, not ATOMIC_MOVE alone, exclude replacement races.
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                deleteOwned(temporary)
            } catch (cleanup: Exception) {
                if (primary != null) primary.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun verifyDirectory(directory: File) {
        val fd = try {
            Os.open(
                directory.path,
                OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or OsConstants.O_NOFOLLOW or
                    OsConstants.O_CLOEXEC,
                0,
            )
        } catch (failure: ErrnoException) {
            throw IOException("IME_QUALIFICATION_DIRECTORY")
        }
        try {
            val stat = Os.fstat(fd)
            if (!OsConstants.S_ISDIR(stat.st_mode) ||
                stat.st_uid != Process.myUid() ||
                (stat.st_mode and 511) != DIRECTORY_MODE
            ) {
                throw IOException("IME_QUALIFICATION_DIRECTORY")
            }
        } finally {
            runCatching { Os.close(fd) }
        }
    }

}

internal data class LeaseSnapshot(
    val run: String,
    val component: String,
    val ownerPid: Int,
    val previousDefault: String,
    val previousEnabled: String?,
    val previousSubtype: String?,
    val requests: List<String>,
)

internal data class ImeTargetState(
    val packageName: String,
    val clientPid: Int,
    val clientUid: Int,
    val fieldId: Int,
    val inputType: Int,
    val imeOptions: Int,
    val hasConnection: Boolean,
)

internal data class ImeState(
    val instance: String,
    val generation: Long,
    val active: Boolean,
    val target: ImeTargetState?,
)

internal enum class ImeFixtureOperation(val wire: String) {
    COMMIT("commit"),
    COMPOSE("compose"),
    FINISH_COMPOSITION("finishComposition"),
    ENTER("enter"),
    INTERRUPT("interrupt"),
    RESTORE_PREVIOUS("restorePrevious");

    companion object {
        fun from(value: String): ImeFixtureOperation? = entries.firstOrNull { it.wire == value }
    }
}

internal data class FixtureRequest(
    val schema: Int,
    val run: String,
    val request: String,
    val instance: String,
    val generation: Long,
    val targetPackage: String,
    val clientPid: Int,
    val fieldId: Int,
    val expiresElapsedMillis: Long,
    val operation: ImeFixtureOperation,
    val text: String?,
)

internal data class ImeDispatchResult(
    val outcome: ImeFixtureResult.Outcome,
    val reason: ImeFixtureResult.Reason?,
    val textLength: Int? = null,
    val textSha256: String? = null,
)

internal data class ParsedResult(
    val run: String,
    val request: String,
    val instance: String,
    val generation: Long,
    val operation: ImeFixtureOperation,
    val outcome: ImeFixtureResult.Outcome,
    val reason: ImeFixtureResult.Reason?,
)
