package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Passive qualification only. No observer outcome changes a product operation. */
internal object FilesPositiveObservation {
    enum class Event {
        SENDER_CREATED, SENDER_DESTROYED, SUBMIT_ACCEPTED, CALL_FINISHED, CALL_RETIRED,
        RECEIVER_CREATED, RECEIVER_DESTROYED, DISPATCH_STARTED, DISPATCH_RETIRED,
        SESSION_CLAIMED, TRANSPORT_ESTABLISHED, TRANSPORT_RETIRED, RETIREMENT_UNCERTAIN,
        SAME_SESSION_IDLE, MODEL_CLEARED,
    }
    private val lock = Any()
    private var started = false
    private var stopped = false
    private var failed = false
    private var sequence = 0
    private val identities = IdentityHashMap<Any, Int>()
    private val queue = ArrayBlockingQueue<FilesPositiveRecord>(32)

    fun start(context: Context, event: Event, first: Any, second: Any? = null) {
        try {
            synchronized(lock) {
                if (!started) {
                    started = true
                    val application = context.applicationContext
                    Thread({ collect(application) }, "CloudyFilesObservation").apply { isDaemon = true; start() }
                }
            }
            record(event, first, second)
        } catch (_: Exception) { invalidate() }
    }

    fun record(event: Event, first: Any, second: Any? = null) {
        try {
            synchronized(lock) {
                if (!started || stopped) return
                fun token(value: Any): Int = identities[value] ?: run {
                    check(identities.size < 12)
                    (identities.size + 1).also { identities[value] = it }
                }
                val a = token(first)
                val b = second?.let(::token) ?: 0
                check(sequence < 32)
                val item = FilesPositiveRecord(++sequence, SystemClock.elapsedRealtime(), event.name, a, b)
                if (!queue.offer(item)) failed = true
            }
        } catch (_: Exception) { invalidate() }
    }

    private fun invalidate() { synchronized(lock) { failed = true } }

    private fun collect(context: Context) {
        try {
            val directory = File(context.noBackupFilesDir, "files-positive-observation")
            if (!directory.exists()) return
            val attributes = Os.lstat(directory.path)
            check(OsConstants.S_ISDIR(attributes.st_mode) && attributes.st_uid == Process.myUid() &&
                attributes.st_mode and 511 == 448)
            val configFile = File(directory, "config.json")
            val descriptor = Os.open(configFile.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            val bytes = FileInputStream(descriptor).use { input ->
                val stat = Os.fstat(descriptor)
                check(OsConstants.S_ISREG(stat.st_mode) && stat.st_uid == Process.myUid() &&
                    stat.st_mode and 511 == 384 && stat.st_size in 1..2048)
                input.readNBytes(2049).also { check(it.size <= 2048) }
            }
            val config = FilesPositiveConfig.parse(bytes, SystemClock.elapsedRealtime())
            // O_EXCL is the permanent one-shot claim. A prior claim is never removed or replaced.
            val marker = Os.open(File(directory, "claimed-${config.processNonce}").path, OsConstants.O_WRONLY or OsConstants.O_CREAT or
                OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
            Os.close(marker)
            val destination = File(directory, "receipt-${config.processNonce}.json")
            check(!destination.exists() && !File(directory, "receipt-${config.processNonce}.json.bak").exists() &&
                !File(directory, "receipt-${config.processNonce}.json.new").exists())
            val ledger = FilesPositiveLedger(config)
            val localNonce = UUID.randomUUID().toString()
            val pid = Process.myPid()
            val processStart = Process.getStartElapsedRealtime()
            while (true) {
                val remaining = config.deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    ledger.invalidate()
                    write(destination, ledger.encode(pid, processStart, localNonce, sealed = true))
                    break
                }
                val item = queue.poll(minOf(remaining, 250), TimeUnit.MILLISECONDS)
                if (item != null) ledger.add(item)
                val invalid = synchronized(lock) { failed }
                if (invalid) ledger.invalidate()
                val ended = item?.event == if (config.role == "SENDER") "SENDER_DESTROYED" else "RECEIVER_DESTROYED"
                // A complete snapshot is bounded; there is no append log or unbounded writer queue.
                if (item != null || invalid) {
                    write(destination, ledger.encode(pid, processStart, localNonce, sealed = ended || invalid || ledger.invalid))
                }
                if (ended || invalid || ledger.invalid) break
            }
        } catch (_: Exception) {
            // Missing/partial receipts invalidate qualification. Never change product behavior or log data.
        } finally {
            synchronized(lock) { stopped = true; identities.clear(); queue.clear() }
        }
    }

    private fun write(destination: File, encoded: ByteArray) {
        val staging = File(destination.parentFile, destination.name + ".stage")
        commitFilesPositiveSnapshot(encoded, open = {
            val descriptor = Os.open(staging.path, OsConstants.O_WRONLY or OsConstants.O_CREAT or
                OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
            try { FileOutputStream(descriptor) }
            catch (error: Exception) { Os.close(descriptor); throw error }
        }, prepare = { stream ->
            Os.fchmod(stream.fd, 384)
            val stat = Os.fstat(stream.fd)
            check(OsConstants.S_ISREG(stat.st_mode) && stat.st_uid == Process.myUid() && stat.st_mode and 511 == 384)
        }, sync = { it.fd.sync() }, publish = { Os.rename(staging.path, destination.path) })
    }

}


/** Narrow publication sequence shared with focused file-failure tests. No cleanup removes uncertain
 * staging. Checked same-directory rename is last; there is no required fallible work after it.
 * File sync is checked, but this observer does not promise directory/power-loss durability. */
internal fun commitFilesPositiveSnapshot(encoded: ByteArray, open: () -> FileOutputStream,
    prepare: (FileOutputStream) -> Unit, sync: (FileOutputStream) -> Unit, publish: () -> Unit) {
    require(encoded.size <= 16_384)
    open().use { stream -> prepare(stream); stream.write(encoded); sync(stream) }
    publish()
}

internal data class FilesPositiveRecord(val sequence: Int, val elapsed: Long, val event: String,
    val first: Int, val second: Int)

/** Deliberately narrow ASCII config grammar: no escaped strings, duplicate/extra fields or coercions. */
internal data class FilesPositiveConfig(val run: String, val role: String, val processNonce: String, val deadline: Long) {
    companion object {
        fun parse(bytes: ByteArray, now: Long): FilesPositiveConfig {
            require(bytes.size in 1..2048 && bytes.all { it.toInt() in 32..126 || it.toInt() in setOf(9, 10, 13) })
            val text = bytes.toString(Charsets.US_ASCII)
            val fields = linkedMapOf<String, String>()
            val pair = Regex("[ \t\r\n]*\"([A-Za-z][A-Za-z0-9]*)\"[ \t\r\n]*:[ \t\r\n]*(\"[A-Za-z0-9_-]+\"|[0-9]+)[ \t\r\n]*")
            var cursor = 0
            fun whitespace() { while (cursor < text.length && text[cursor] in " \t\r\n") cursor++ }
            whitespace(); require(cursor < text.length && text[cursor++] == '{')
            while (true) {
                val match = pair.find(text, cursor)
                require(match != null && match.range.first == cursor)
                require(fields.put(match.groupValues[1], match.groupValues[2]) == null)
                cursor = match.range.last + 1
                require(cursor < text.length)
                when (text[cursor++]) { '}' -> break; ',' -> Unit; else -> error("CONFIG") }
            }
            whitespace(); require(cursor == text.length)
            require(fields.keys == setOf("schema", "run", "role", "operation", "processNonce", "deadlineElapsedMillis"))
            require(fields.getValue("schema") == "1" && fields.getValue("operation") == "\"OPEN_FILES\"")
            fun quoted(key: String): String {
                val value = fields.getValue(key)
                require(value.startsWith('"') && value.endsWith('"'))
                return value.substring(1, value.length - 1)
            }
            val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            val run = quoted("run"); val nonce = quoted("processNonce"); val role = quoted("role")
            require(uuid.matches(run) && uuid.matches(nonce) && role in setOf("SENDER", "RECEIVER"))
            val deadlineText = fields.getValue("deadlineElapsedMillis")
            require(Regex("[1-9][0-9]{0,15}").matches(deadlineText))
            val deadline = deadlineText.toLong()
            require(now >= 0 && deadline > now && deadline - now <= 300_000)
            return FilesPositiveConfig(run, role, nonce, deadline)
        }
    }
}

/** Evidence structure only; parent correlates the original-token chain with UI and real host effects. */
internal class FilesPositiveLedger(private val config: FilesPositiveConfig) {
    private val records = mutableListOf<FilesPositiveRecord>()
    var invalid = false
        private set
    fun invalidate() { invalid = true }
    fun add(item: FilesPositiveRecord) {
        val names = if (config.role == "SENDER") setOf("SENDER_CREATED", "SENDER_DESTROYED", "SUBMIT_ACCEPTED",
            "CALL_FINISHED", "CALL_RETIRED") else setOf("RECEIVER_CREATED", "RECEIVER_DESTROYED", "DISPATCH_STARTED",
            "DISPATCH_RETIRED", "SESSION_CLAIMED", "TRANSPORT_ESTABLISHED", "TRANSPORT_RETIRED",
            "RETIREMENT_UNCERTAIN", "SAME_SESSION_IDLE", "MODEL_CLEARED")
        if (records.size >= 32 || item.sequence != records.size + 1 || item.event !in names ||
            item.first !in 1..12 || item.second !in 0..12 || item.elapsed < 0 || item.elapsed >= config.deadline ||
            (records.lastOrNull()?.elapsed ?: 0) > item.elapsed) { invalid = true; return }
        records.add(item)
        if (item.event == "RETIREMENT_UNCERTAIN") invalid = true
    }
    fun originalChainComplete(): Boolean {
        if (invalid) return false
        fun one(name: String) = records.filter { it.event == name }.singleOrNull()
        fun ordered(vararg items: FilesPositiveRecord) = items.asList().zipWithNext().all { (a, b) -> a.sequence < b.sequence }
        if (config.role == "SENDER") {
            val created = one("SENDER_CREATED") ?: return false
            val accepted = one("SUBMIT_ACCEPTED") ?: return false
            val finished = one("CALL_FINISHED") ?: return false
            val retired = one("CALL_RETIRED") ?: return false
            val destroyed = one("SENDER_DESTROYED")
            return accepted.first == created.first && accepted.second != 0 &&
                finished.first == accepted.second && retired.first == accepted.second &&
                ordered(created, accepted, finished, retired) &&
                (destroyed == null || destroyed.first == created.first && destroyed.sequence > retired.sequence) &&
                records.count { it.event == "SENDER_DESTROYED" } <= 1
        }
        val created = one("RECEIVER_CREATED") ?: return false
        val dispatch = one("DISPATCH_STARTED") ?: return false
        val dispatchRetired = one("DISPATCH_RETIRED") ?: return false
        val claimed = one("SESSION_CLAIMED") ?: return false
        val established = one("TRANSPORT_ESTABLISHED") ?: return false
        val retired = one("TRANSPORT_RETIRED") ?: return false
        val idle = one("SAME_SESSION_IDLE") ?: return false
        val destroyed = one("RECEIVER_DESTROYED")
        return created.second != 0 && claimed.first == created.second && claimed.second != 0 &&
            established.first == claimed.second && retired.first == claimed.second &&
            idle.first == created.second && idle.second == claimed.second &&
            dispatchRetired.first == dispatch.first && ordered(dispatch, dispatchRetired) &&
            ordered(created, claimed, established, retired, idle) &&
            records.none { it.event == "MODEL_CLEARED" } &&
            (destroyed == null || destroyed.first == created.first && destroyed.second == created.second &&
                destroyed.sequence > idle.sequence) && records.count { it.event == "RECEIVER_DESTROYED" } <= 1
    }
    fun encode(pid: Int, processStart: Long, localNonce: String, sealed: Boolean = false): ByteArray {
        require(pid > 0 && processStart >= 0 && UUID.fromString(localNonce).toString() == localNonce)
        val events = records.joinToString(",") { "{\"sequence\":${it.sequence},\"elapsed\":${it.elapsed}," +
            "\"event\":\"${it.event}\",\"first\":${it.first},\"second\":${it.second}}" }
        return ("{\"schema\":1,\"run\":\"${config.run}\",\"role\":\"${config.role}\"," +
            "\"operation\":\"OPEN_FILES\",\"processNonce\":\"${config.processNonce}\",\"localNonce\":\"$localNonce\"," +
            "\"pid\":$pid,\"processStartElapsedMillis\":$processStart,\"deadlineElapsedMillis\":${config.deadline}," +
            "\"invalid\":$invalid,\"sealed\":$sealed,\"originalOperationChainComplete\":${originalChainComplete()},\"events\":[$events]}\n").toByteArray(Charsets.UTF_8)
    }
}
