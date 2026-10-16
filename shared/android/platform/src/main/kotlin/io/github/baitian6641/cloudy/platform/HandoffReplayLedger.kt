package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Looper
import android.system.Os
import android.system.OsConstants
import io.github.baitian6641.cloudy.domain.HandoffEnvelope
import io.github.baitian6641.cloudy.domain.HandoffFreshness
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

enum class HandoffReplayResult {
    RESERVED, DUPLICATE, EXPIRED, NOT_YET_VALID, CLOCK_ROLLBACK, CAPACITY_REACHED,
    BUSY, WRONG_THREAD, STORE_UNAVAILABLE,
}

/**
 * Durable consumption only, without resource authorization or navigation. UUIDs remain reserved
 * until a later successful admission prunes their original expiry; uniqueness is not indefinite.
 * Commit-before-effect provides at-most-once admission, including possible lost effects on death.
 */
class HandoffReplayLedger internal constructor(
    context: Context,
    private val clock: () -> Instant,
    private val capacity: Int,
) {
    constructor(context: Context) : this(context, Instant::now, 4096)

    private val application = context.applicationContext
    // getApplicationContext can discard the storage domain of a supplied ContextImpl.
    private val deviceProtected = context.isDeviceProtectedStorage || application.isDeviceProtectedStorage
    private val root by lazy { application.noBackupFilesDir }
    private val directory by lazy { File(root, "handoff-replay-v1") }
    private val marker by lazy { File(root, "handoff-replay-v1.instance") }
    private val database by lazy { File(directory, "requests.db") }

    init { require(capacity in 1..4096) }

    fun reserve(envelope: HandoffEnvelope): HandoffReplayResult {
        if (Looper.myLooper() == Looper.getMainLooper()) return HandoffReplayResult.WRONG_THREAD
        if (deviceProtected) return HandoffReplayResult.STORE_UNAVAILABLE
        if (!monitor.tryLock()) return HandoffReplayResult.BUSY
        return try {
            // A separate, never-replaced inode serializes bootstrap and admission across processes.
            val lockPath = File(root, "handoff-replay-v1.lock")
            if (!lockPath.exists() && (directory.exists() || marker.exists())) {
                return HandoffReplayResult.STORE_UNAVAILABLE
            }
            RandomAccessFile(lockPath, "rw").use { lockFile ->
                val fileLock = lockFile.channel.tryLock() ?: return HandoffReplayResult.BUSY
                fileLock.use {
                    if (lockFile.length() == 0L) {
                        check(!directory.exists() && !marker.exists())
                        val newInstance = UUID.randomUUID().toString()
                        lockFile.write(newInstance.toByteArray(Charsets.US_ASCII))
                        lockFile.fd.sync()
                        syncDirectory(root)
                        initialize(newInstance)
                    }
                    check(lockFile.length() == 36L && directory.isDirectory)
                    val anchor = ByteArray(36)
                    lockFile.seek(0)
                    lockFile.readFully(anchor)
                    val instance = readInstance()
                    check(anchor.toString(Charsets.US_ASCII) == instance)
                    check(database.isFile && database.length() in 1..MAX_DATABASE_BYTES)
                    open(create = false).use { db ->
                        check(db.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
                            cursor.moveToFirst() && cursor.getString(0) == "ok"
                        })
                        validateMetadata(db, instance)
                        transact(db, envelope)
                    }
                }
            }
        } catch (_: OverlappingFileLockException) {
            HandoffReplayResult.BUSY
        } catch (_: Exception) {
            // No filenames, submitted IDs, platform diagnostics, or nested causes cross this API.
            HandoffReplayResult.STORE_UNAVAILABLE
        } finally {
            monitor.unlock()
        }
    }

    private fun initialize(instance: String) {
        // Either artifact without its counterpart means interrupted/lost state, never a fresh store.
        check(directory.mkdir())
        syncDirectory(root)
        FileOutputStream(marker).use { stream ->
            stream.write(instance.toByteArray(Charsets.US_ASCII))
            stream.fd.sync()
        }
        syncDirectory(root)
        open(create = true).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE metadata (singleton INTEGER PRIMARY KEY CHECK(singleton=1), " +
                    "version INTEGER NOT NULL, instance TEXT NOT NULL, capacity INTEGER NOT NULL, " +
                    "high_seconds INTEGER, high_nanos INTEGER)")
                db.execSQL("CREATE TABLE requests (request_id TEXT PRIMARY KEY NOT NULL, " +
                    "expires_seconds INTEGER NOT NULL, expires_nanos INTEGER NOT NULL)")
                db.execSQL("INSERT INTO metadata VALUES (1, 1, ?, ?, NULL, NULL)", arrayOf<Any>(instance, capacity))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        syncDirectory(directory)
    }

    private fun readInstance(): String {
        check(marker.isFile && marker.length() == 36L)
        val value = marker.readText(Charsets.US_ASCII)
        check(value.length == 36 && UUID.fromString(value).toString() == value)
        return value
    }

    private fun open(create: Boolean): SQLiteDatabase {
        val flags = SQLiteDatabase.OPEN_READWRITE or
            (if (create) SQLiteDatabase.CREATE_IF_NECESSARY else 0)
        val params = SQLiteDatabase.OpenParams.Builder()
            .setOpenFlags(flags)
            .setJournalMode("DELETE")
            .setSynchronousMode("EXTRA")
            // Android's default handler deletes corrupt databases; replay state must never reset.
            .setErrorHandler(DatabaseErrorHandler { throw SQLiteDatabaseCorruptException("Replay store unavailable") })
            .build()
        val db = SQLiteDatabase.openDatabase(database, params)
        try {
            check(db.rawQuery("PRAGMA journal_mode", null).use { it.moveToFirst() && it.getString(0) == "delete" })
            check(db.rawQuery("PRAGMA synchronous", null).use { it.moveToFirst() && it.getLong(0) == 3L })
            check(db.setMaximumSize(MAX_DATABASE_BYTES) <= MAX_DATABASE_BYTES)
            return db
        } catch (error: Exception) {
            db.close()
            throw error
        }
    }

    private fun validateMetadata(db: SQLiteDatabase, instance: String) {
        var high: Instant? = null
        db.rawQuery("SELECT singleton, version, instance, capacity, high_seconds, high_nanos FROM metadata", null)
            .use { cursor ->
                check(cursor.count == 1 && cursor.moveToFirst())
                check(listOf(0, 1, 3).all { cursor.getType(it) == Cursor.FIELD_TYPE_INTEGER })
                check(cursor.getType(2) == Cursor.FIELD_TYPE_STRING)
                check(cursor.getLong(0) == 1L && cursor.getLong(1) == 1L)
                check(cursor.getString(2) == instance && cursor.getLong(3) == capacity.toLong())
                check(cursor.isNull(4) == cursor.isNull(5))
                if (!cursor.isNull(4)) {
                    check(cursor.getType(4) == Cursor.FIELD_TYPE_INTEGER && cursor.getType(5) == Cursor.FIELD_TYPE_INTEGER)
                    check(cursor.getLong(5) in 0L..999_999_999L)
                    high = Instant.ofEpochSecond(cursor.getLong(4), cursor.getLong(5))
                }
            }
        db.rawQuery("SELECT request_id, expires_seconds, expires_nanos FROM requests", null).use { cursor ->
            check(cursor.count <= capacity)
            check((high == null) == (cursor.count == 0))
            while (cursor.moveToNext()) {
                check(cursor.getType(0) == Cursor.FIELD_TYPE_STRING)
                check(cursor.getType(1) == Cursor.FIELD_TYPE_INTEGER && cursor.getType(2) == Cursor.FIELD_TYPE_INTEGER)
                val id = cursor.getString(0)
                check(id.length == 36 && UUID.fromString(id).toString() == id)
                check(cursor.getLong(2) in 0L..999_999_999L)
                check(Instant.ofEpochSecond(cursor.getLong(1), cursor.getLong(2)) > checkNotNull(high))
            }
        }
    }

    private fun transact(db: SQLiteDatabase, envelope: HandoffEnvelope): HandoffReplayResult {
        db.beginTransaction()
        val result: HandoffReplayResult
        try {
            result = admit(db, envelope)
            if (result == HandoffReplayResult.RESERVED) db.setTransactionSuccessful()
        } finally {
            // Returning RESERVED is possible only after the durable transaction has committed.
            db.endTransaction()
        }
        return result
    }

    private fun admit(db: SQLiteDatabase, envelope: HandoffEnvelope): HandoffReplayResult {
        val now = clock()
        // This is the last successful admission, not the most recent rejected observation.
        val previous = db.rawQuery("SELECT high_seconds, high_nanos FROM metadata WHERE singleton=1", null)
            .use { cursor ->
                check(cursor.moveToFirst())
                if (cursor.isNull(0)) null else Instant.ofEpochSecond(cursor.getLong(0), cursor.getLong(1))
            }
        if (previous != null && now < previous) return HandoffReplayResult.CLOCK_ROLLBACK
        when (envelope.freshAt(now)) {
            HandoffFreshness.EXPIRED -> return HandoffReplayResult.EXPIRED
            HandoffFreshness.NOT_YET_VALID -> return HandoffReplayResult.NOT_YET_VALID
            HandoffFreshness.CURRENT -> Unit
        }
        val duplicate = db.rawQuery("SELECT 1 FROM requests WHERE request_id=?", arrayOf(envelope.requestId))
            .use { it.moveToFirst() }
        if (duplicate) return HandoffReplayResult.DUPLICATE
        val expiryArguments = arrayOf(now.epochSecond.toString(), now.epochSecond.toString(), now.nano.toString())
        val active = db.rawQuery("SELECT count(*) FROM requests WHERE NOT ($EXPIRED)", expiryArguments)
            .use { cursor -> check(cursor.moveToFirst()); cursor.getInt(0) }
        if (active >= capacity) return HandoffReplayResult.CAPACITY_REACHED
        // Pruning and high-water advance are inseparable: rollback cannot resurrect pruned envelopes.
        db.delete("requests", EXPIRED, expiryArguments)
        db.execSQL("INSERT INTO requests VALUES (?, ?, ?)",
            arrayOf<Any>(envelope.requestId, envelope.expiresAt.epochSecond, envelope.expiresAt.nano))
        db.execSQL("UPDATE metadata SET high_seconds=?, high_nanos=? WHERE singleton=1",
            arrayOf<Any>(now.epochSecond, now.nano))
        return HandoffReplayResult.RESERVED
    }

    companion object {
        private val monitor = ReentrantLock()
        private const val MAX_DATABASE_BYTES = 2L * 1024 * 1024
        private const val EXPIRED = "expires_seconds < ? OR (expires_seconds = ? AND expires_nanos <= ?)"

        private fun syncDirectory(directory: File) {
            // O_DIRECTORY is not in the public Android SDK; check the opened descriptor itself.
            val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            try {
                check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode))
                Os.fsync(descriptor)
            } finally { Os.close(descriptor) }
        }
    }
}
