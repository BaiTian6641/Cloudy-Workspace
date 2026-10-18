package io.github.baitian6641.cloudy.platform

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.CodingErrorAction
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import android.content.Context
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

internal enum class FilesTransferState { STAGED, COMMITTING, COMMITTED, CONFLICT, FAILED, UNCERTAIN }

internal enum class FilesJournalCode { INVALID_INPUT, BUSY, CAPACITY, UNAVAILABLE, COMPLETION_UNKNOWN }

internal sealed interface FilesJournalResult<out T> {
    class Success<T>(val value: T) : FilesJournalResult<T>
    class Failure(val code: FilesJournalCode) : FilesJournalResult<Nothing>
}

/** App-private transfer metadata only: no credentials, file content, or local destination. */
internal class FilesTransferRecord internal constructor(
    val id: String, val profileId: String, val profileRevision: Int, val path: String,
    val tempPath: String, val byteCount: Int, val state: FilesTransferState, val replaceConsent: Boolean,
    val createdAtMillis: Long, val updatedAtMillis: Long,
) {
    internal fun next(state: FilesTransferState, replaceConsent: Boolean, updatedAtMillis: Long,
                      byteCount: Int = this.byteCount, path: String = this.path) =
        FilesTransferRecord(id, profileId, profileRevision, path, tempPath, byteCount, state, replaceConsent,
            createdAtMillis, updatedAtMillis)
    override fun toString() = "FilesTransferRecord(${state.name},redacted)"
}

/**
 * Bounded write-ahead journal for staged edits. A record is STAGED after the temp exists and
 * COMMITTING before the remote rename. Loading a COMMITTING record after process death converts
 * it to UNCERTAIN; no operation is ever replayed automatically. Terminal records may be evicted
 * oldest-first when capacity is reached; active/uncertain records are never silently evicted.
 */
internal class FilesTransferJournal(
    private val root: File,
    private val capacity: Int = MAX_RECORDS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val file = File(root, "transfers.journal")
    private val temp = File(root, "transfers.tmp")
    private val lock get() = locks.computeIfAbsent(file.absolutePath) { ReentrantLock() }

    fun load(): FilesJournalResult<List<FilesTransferRecord>> = guarded {
        val loaded = read()
        if (loaded.none { it.state == FilesTransferState.COMMITTING }) {
            return@guarded FilesJournalResult.Success(loaded)
        }
        val settled = loaded.map {
            if (it.state == FilesTransferState.COMMITTING) it.next(FilesTransferState.UNCERTAIN, it.replaceConsent, now())
            else it
        }
        try { write(settled) } catch (_: Exception) {
            return@guarded FilesJournalResult.Failure(FilesJournalCode.COMPLETION_UNKNOWN)
        }
        FilesJournalResult.Success(settled)
    }

    fun begin(profileId: String, profileRevision: Int, path: String, tempPath: String, byteCount: Int):
        FilesJournalResult<FilesTransferRecord> = guarded {
        if (profileId.isEmpty() || profileId.length > 128 || profileRevision < 0 ||
            path.isEmpty() || path.length > MAX_PATH_UNITS || tempPath.isEmpty() || tempPath.length > MAX_PATH_UNITS ||
            byteCount !in 0..MAX_BYTES || path.any { it == '\u0000' } || tempPath.any { it == '\u0000' }) {
            return@guarded FilesJournalResult.Failure(FilesJournalCode.INVALID_INPUT)
        }
        val records = read().toMutableList()
        if (records.size >= capacity) {
            val terminal = records.indexOfFirst { it.state in TERMINAL }
            if (terminal < 0) return@guarded FilesJournalResult.Failure(FilesJournalCode.CAPACITY)
            records.removeAt(terminal)
        }
        val timestamp = now()
        val record = FilesTransferRecord(UUID.randomUUID().toString(), profileId, profileRevision, path,
            tempPath, byteCount, FilesTransferState.STAGED, false, timestamp, timestamp)
        records += record
        write(records)
        FilesJournalResult.Success(record)
    }

    fun transition(id: String, state: FilesTransferState, replaceConsent: Boolean,
                   byteCount: Int? = null, path: String? = null): FilesJournalResult<FilesTransferRecord> = guarded {
        if (id.isEmpty() || id.length > 128 || (byteCount != null && byteCount !in 0..MAX_BYTES) ||
            (path != null && (path.isEmpty() || path.length > MAX_PATH_UNITS || path.any { it == '\u0000' }))) {
            return@guarded FilesJournalResult.Failure(FilesJournalCode.INVALID_INPUT)
        }
        val records = read().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return@guarded FilesJournalResult.Failure(FilesJournalCode.INVALID_INPUT)
        val updated = records[index].next(state, replaceConsent, now(),
            byteCount ?: records[index].byteCount, path ?: records[index].path)
        records[index] = updated
        write(records)
        FilesJournalResult.Success(updated)
    }

    private fun read(): List<FilesTransferRecord> {
        if (!file.exists()) return emptyList()
        if (Files.isSymbolicLink(file.toPath()) || file.length() > MAX_JOURNAL_BYTES) throw IllegalStateException()
        val lines = file.readLines(Charsets.UTF_8)
        if (lines.isEmpty() || lines.first() != HEADER) throw IllegalStateException()
        val records = lines.drop(1).filter { it.isNotEmpty() }.map { line ->
            val fields = line.split('\t')
            if (fields.size != 10) throw IllegalStateException()
            FilesTransferRecord(
                percent(fields[0]), percent(fields[1]), integer(fields[2], 0, Int.MAX_VALUE),
                percent(fields[3]), percent(fields[4]), integer(fields[5], 0, MAX_BYTES),
                FilesTransferState.valueOf(fields[6]), fields[7] == "1", nonNegative(fields[8]), nonNegative(fields[9]),
            ).also {
                if (it.path.isEmpty() || it.tempPath.isEmpty() || percent(fields[0]) != it.id) throw IllegalStateException()
            }
        }
        if (records.size > capacity) throw IllegalStateException()
        return records
    }

    private fun write(records: List<FilesTransferRecord>) {
        if (records.size > capacity) throw IllegalStateException()
        val body = buildString {
            append(HEADER).append('\n')
            for (r in records) {
                append(percentEncode(r.id)).append('\t')
                append(percentEncode(r.profileId)).append('\t')
                append(r.profileRevision).append('\t')
                append(percentEncode(r.path)).append('\t')
                append(percentEncode(r.tempPath)).append('\t')
                append(r.byteCount).append('\t')
                append(r.state.name).append('\t')
                append(if (r.replaceConsent) "1" else "0").append('\t')
                append(r.createdAtMillis).append('\t')
                append(r.updatedAtMillis).append('\n')
            }
        }.toByteArray(Charsets.UTF_8)
        if (body.size > MAX_JOURNAL_BYTES) throw IllegalStateException()
        if (!root.exists() && !root.mkdirs()) throw IllegalStateException()
        check(!Files.isSymbolicLink(root.toPath()) && !Files.isSymbolicLink(file.toPath()) && !Files.isSymbolicLink(temp.toPath()))
        FileOutputStream(temp).use { out ->
            out.write(body)
            out.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        // Directory fsync is best-effort: Android may not permit opening a directory channel.
        runCatching { FileChannel.open(root.toPath(), StandardOpenOption.READ).use { it.force(true) } }
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        }
    }

    private inline fun <T> guarded(block: () -> FilesJournalResult<T>): FilesJournalResult<T> {
        if (!lock.tryLock()) return FilesJournalResult.Failure(FilesJournalCode.BUSY)
        return try { block() } catch (_: Exception) {
            FilesJournalResult.Failure(FilesJournalCode.UNAVAILABLE)
        } finally { lock.unlock() }
    }

    companion object {
        const val MAX_RECORDS = 64
        const val MAX_PATH_UNITS = 4096
        const val MAX_BYTES = 67_108_864
        private const val MAX_JOURNAL_BYTES = 1L shl 20
        private const val HEADER = "cloudy-transfers\t1"
        private val TERMINAL = setOf(FilesTransferState.COMMITTED, FilesTransferState.CONFLICT, FilesTransferState.FAILED)
        private val locks = ConcurrentHashMap<String, ReentrantLock>()

        fun appRoot(context: Context): File = File(context.noBackupFilesDir, "files-transfers-v1")

        private fun integer(value: String, minimum: Int, maximum: Int): Int =
            value.toInt().also { require(it in minimum..maximum) }

        private fun nonNegative(value: String): Long = value.toLong().also { require(it >= 0) }

        private fun percent(value: String): String {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val out = ArrayList<Byte>(bytes.size)
            var at = 0
            while (at < bytes.size) {
                val b = bytes[at]
                if (b == '%'.code.toByte()) {
                    require(at + 2 < bytes.size)
                    out += ((hex(bytes[at + 1]) shl 4) or hex(bytes[at + 2])).toByte()
                    at += 3
                } else { out += b; at++ }
            }
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString()
        }

        private fun hex(value: Byte): Int {
            val c = value.toInt().toChar()
            return when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> throw IllegalArgumentException()
            }
        }

        private fun percentEncode(value: String): String {
            val out = StringBuilder()
            for (b in value.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 255
                if ((c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) ||
                    c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code) out.append(c.toChar())
                else out.append('%').append(HEX[c shr 4]).append(HEX[c and 15])
            }
            return out.toString()
        }
        private val HEX = "0123456789ABCDEF".toCharArray()
    }
}
