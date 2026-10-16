package io.github.baitian6641.cloudy.platform

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Looper
import android.os.Process
import android.system.Os
import android.system.OsConstants
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshHostTrust
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustChallenge
import io.github.baitian6641.cloudy.domain.SshTrustChallengeReason
import io.github.baitian6641.cloudy.domain.SshTrustEditToken
import io.github.baitian6641.cloudy.domain.SshTrustEvaluation
import io.github.baitian6641.cloudy.domain.SshTrustRoute
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.util.Collections
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

enum class SshTrustStoreCode {
    WRONG_OWNER, WRONG_THREAD, BUSY, STORE_UNAVAILABLE, COMPLETION_UNKNOWN,
    STALE_GENERATION, CONFLICT, NOT_FOUND, CAPACITY, REVISION_EXHAUSTED, INVALID_STATE,
}

sealed interface SshTrustStoreResult<out T> {
    data class Success<T>(val value: T) : SshTrustStoreResult<T>
    data class Failure(val code: SshTrustStoreCode) : SshTrustStoreResult<Nothing>
}

class SshTrustCatalog internal constructor(
    val owner: WindowOwner, val generation: String, snapshots: List<SshTrustSnapshot>,
) {
    val snapshots: List<SshTrustSnapshot> = Collections.unmodifiableList(ArrayList(snapshots))
    override fun toString(): String = "SshTrustCatalog(redacted)"
}

class SshTrustLookup internal constructor(
    val owner: WindowOwner, val generation: String, val peer: SshPeerIdentity, val current: SshTrustSnapshot?,
) {
    override fun toString(): String = "SshTrustLookup(redacted)"
}

/**
 * App-owned pin persistence only. A challenge is metadata, not foreground/lifecycle approval;
 * the caller must obtain that approval. Saving never starts or resumes authentication.
 * Revoked rows retain their last key, count toward capacity and have no delete/reset path.
 */
class SshHostTrustStore private constructor(private val configuration: Configuration) {
    constructor(context: Context) : this(configuration(context))

    internal constructor(
        root: File, owner: WindowOwner, ownerPackage: String, onMainThread: () -> Boolean,
        capacity: Int = MAX_RECORDS,
    ) : this(Configuration({ root }, owner, ownerPackage, Process.myUid(), false, onMainThread, capacity, { true }))

    private val owner get() = configuration.owner
    private val root by lazy { configuration.root() }
    private val directory by lazy { File(root, "ssh-host-trust-v1") }
    private val marker by lazy { File(root, "ssh-host-trust-v1.instance") }
    private val anchor by lazy { File(root, "ssh-host-trust-v1.lock") }
    private val database by lazy { File(directory, "trust.db") }

    init { require(configuration.capacity in 1..MAX_RECORDS) { "INVALID_INPUT" } }

    fun load(): SshTrustStoreResult<SshTrustCatalog> = operate { _, catalog, _ -> SshTrustStoreResult.Success(catalog) }

    fun lookup(peer: SshPeerIdentity): SshTrustStoreResult<SshTrustLookup> = operate { _, catalog, _ ->
        SshTrustStoreResult.Success(SshTrustLookup(owner, catalog.generation, peer,
            catalog.snapshots.singleOrNull { it.peer == peer }))
    }

    fun evaluate(peer: SshPeerIdentity, observedKey: SshHostKeyIdentity): SshTrustStoreResult<SshTrustEvaluation> =
        operate { _, catalog, _ ->
            SshTrustStoreResult.Success(SshHostTrust.evaluate(owner, catalog.generation, peer, observedKey,
                catalog.snapshots.singleOrNull { it.peer == peer }))
        }

    fun accept(challenge: SshTrustChallenge): SshTrustStoreResult<SshTrustSnapshot> {
        if (challenge.token.owner != owner) return failure(SshTrustStoreCode.WRONG_OWNER)
        return operate { db, catalog, attempting ->
            val token = challenge.token
            if (token.generation != catalog.generation) return@operate failure(SshTrustStoreCode.STALE_GENERATION)
            val previous = catalog.snapshots.singleOrNull { it.peer == token.peer }
            if (token.expectedRevision == 0L) {
                if (previous != null) return@operate failure(SshTrustStoreCode.CONFLICT)
                if (challenge.reason != SshTrustChallengeReason.FIRST_USE || challenge.previousKey != null) {
                    return@operate failure(SshTrustStoreCode.INVALID_STATE)
                }
                if (catalog.snapshots.size >= configuration.capacity) return@operate failure(SshTrustStoreCode.CAPACITY)
            } else {
                if (previous == null) return@operate failure(SshTrustStoreCode.NOT_FOUND)
                if (previous.revision != token.expectedRevision) return@operate failure(SshTrustStoreCode.CONFLICT)
                val expectedReason = when (previous.status) {
                    is SshTrustStatus.Accepted -> SshTrustChallengeReason.REPLACEMENT
                    is SshTrustStatus.Revoked -> SshTrustChallengeReason.REENROLLMENT
                }
                if (challenge.reason != expectedReason || challenge.previousKey != key(previous.status)) {
                    return@operate failure(SshTrustStoreCode.INVALID_STATE)
                }
                if (previous.status is SshTrustStatus.Accepted && challenge.observedKey == key(previous.status)) {
                    return@operate failure(SshTrustStoreCode.INVALID_STATE)
                }
                if (previous.revision == Long.MAX_VALUE) return@operate failure(SshTrustStoreCode.REVISION_EXHAUSTED)
            }
            val saved = SshTrustSnapshot.create(owner, catalog.generation, token.peer,
                (previous?.revision ?: 0L) + 1, SshTrustStatus.accepted(challenge.observedKey))
            attempting()
            if (previous == null) db.insertOrThrow("pins", null, values(saved))
            else check(db.update("pins", values(saved), CAS_WHERE, casArguments(previous)) == 1)
            SshTrustStoreResult.Success(saved)
        }
    }

    fun revoke(token: SshTrustEditToken): SshTrustStoreResult<SshTrustSnapshot> {
        if (token.owner != owner) return failure(SshTrustStoreCode.WRONG_OWNER)
        return operate { db, catalog, attempting ->
            if (token.generation != catalog.generation) return@operate failure(SshTrustStoreCode.STALE_GENERATION)
            if (token.expectedRevision == 0L) return@operate failure(SshTrustStoreCode.NOT_FOUND)
            val previous = catalog.snapshots.singleOrNull { it.peer == token.peer }
                ?: return@operate failure(SshTrustStoreCode.NOT_FOUND)
            if (previous.revision != token.expectedRevision) return@operate failure(SshTrustStoreCode.CONFLICT)
            if (previous.status is SshTrustStatus.Revoked) return@operate failure(SshTrustStoreCode.INVALID_STATE)
            if (previous.revision == Long.MAX_VALUE) return@operate failure(SshTrustStoreCode.REVISION_EXHAUSTED)
            val saved = SshTrustSnapshot.create(owner, catalog.generation, token.peer,
                previous.revision + 1, SshTrustStatus.revoked(key(previous.status)))
            attempting()
            check(db.update("pins", values(saved), CAS_WHERE, casArguments(previous)) == 1)
            SshTrustStoreResult.Success(saved)
        }
    }

    private fun <T> operate(
        operation: (SQLiteDatabase, SshTrustCatalog, () -> Unit) -> SshTrustStoreResult<T>,
    ): SshTrustStoreResult<T> {
        if (configuration.onMainThread()) return failure(SshTrustStoreCode.WRONG_THREAD)
        if (configuration.deviceProtected) return failure(SshTrustStoreCode.STORE_UNAVAILABLE)
        if (packageFor(owner) != configuration.ownerPackage) return failure(SshTrustStoreCode.WRONG_OWNER)
        if (!monitor.tryLock()) return failure(SshTrustStoreCode.BUSY)
        var mutationAttempted = false
        return try {
            // Check process identity before evaluating the lazy private storage root.
            if (!configuration.validProcess()) return failure(SshTrustStoreCode.WRONG_OWNER)
            check(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
            checkPaths()
            if (!anchor.exists() && (directory.exists() || marker.exists())) return failure(SshTrustStoreCode.STORE_UNAVAILABLE)
            RandomAccessFile(anchor, "rw").use { lockFile ->
                val fileLock = lockFile.channel.tryLock() ?: return failure(SshTrustStoreCode.BUSY)
                fileLock.use {
                    if (lockFile.length() == 0L) {
                        check(!directory.exists() && !marker.exists())
                        val generation = UUID.randomUUID().toString()
                        lockFile.write(generation.toByteArray(Charsets.US_ASCII))
                        lockFile.fd.sync()
                        syncDirectory(root)
                        initialize(generation)
                    }
                    checkPaths()
                    check(lockFile.length() == 36L && directory.isDirectory)
                    val generation = readGeneration()
                    val bytes = ByteArray(36)
                    lockFile.seek(0)
                    lockFile.readFully(bytes)
                    check(bytes.toString(Charsets.US_ASCII) == generation)
                    check(database.isFile && database.length() in 1..MAX_DATABASE_BYTES)
                    open(create = false).use { db ->
                        db.beginTransaction()
                        try {
                            val catalog = readCatalog(db, generation)
                            val result = operation(db, catalog) { mutationAttempted = true }
                            if (result is SshTrustStoreResult.Success) db.setTransactionSuccessful()
                            result
                        } finally {
                            // The caller never sees success until commit. Any exception after an
                            // attempted write is uncertain and must be reconciled by a fresh read.
                            db.endTransaction()
                        }
                    }
                }
            }
        } catch (_: OverlappingFileLockException) {
            failure(if (mutationAttempted) SshTrustStoreCode.COMPLETION_UNKNOWN else SshTrustStoreCode.BUSY)
        } catch (_: Exception) {
            failure(if (mutationAttempted) SshTrustStoreCode.COMPLETION_UNKNOWN else SshTrustStoreCode.STORE_UNAVAILABLE)
        } finally { monitor.unlock() }
    }

    private fun initialize(generation: String) {
        check(directory.mkdir())
        syncDirectory(root)
        FileOutputStream(marker).use { it.write(generation.toByteArray(Charsets.US_ASCII)); it.fd.sync() }
        syncDirectory(root)
        open(create = true).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE metadata (singleton INTEGER PRIMARY KEY CHECK(singleton=1), " +
                    "version INTEGER NOT NULL, generation TEXT NOT NULL, owner TEXT NOT NULL, " +
                    "owner_package TEXT NOT NULL, owner_uid INTEGER NOT NULL, capacity INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE pins (host TEXT NOT NULL, port INTEGER NOT NULL, route TEXT NOT NULL, " +
                    "owner TEXT NOT NULL, revision INTEGER NOT NULL, state INTEGER NOT NULL, " +
                    "key_format TEXT NOT NULL, fingerprint TEXT NOT NULL, PRIMARY KEY(host,port,route))")
                db.execSQL("INSERT INTO metadata VALUES (1,1,?,?,?,?,?)", arrayOf<Any>(generation, owner.wireId,
                    configuration.ownerPackage, configuration.uid, configuration.capacity))
                db.execSQL("PRAGMA user_version=1")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        syncDirectory(directory)
    }

    private fun open(create: Boolean): SQLiteDatabase {
        val flags = SQLiteDatabase.OPEN_READWRITE or (if (create) SQLiteDatabase.CREATE_IF_NECESSARY else 0)
        val parameters = SQLiteDatabase.OpenParams.Builder().setOpenFlags(flags)
            .setJournalMode("DELETE").setSynchronousMode("EXTRA")
            .setErrorHandler(DatabaseErrorHandler { throw SQLiteDatabaseCorruptException("STORE_UNAVAILABLE") }).build()
        val db = SQLiteDatabase.openDatabase(database, parameters)
        try {
            check(db.rawQuery("PRAGMA journal_mode", null).use { it.moveToFirst() && it.getString(0) == "delete" })
            check(db.rawQuery("PRAGMA synchronous", null).use { it.moveToFirst() && it.getLong(0) == 3L })
            check(db.setMaximumSize(MAX_DATABASE_BYTES) <= MAX_DATABASE_BYTES)
            return db
        } catch (error: Exception) { db.close(); throw error }
    }

    private fun readCatalog(db: SQLiteDatabase, generation: String): SshTrustCatalog {
        check(db.rawQuery("PRAGMA quick_check(1)", null).use { it.moveToFirst() && it.getString(0) == "ok" })
        check(db.rawQuery("PRAGMA user_version", null).use { it.moveToFirst() && it.getLong(0) == 1L })
        db.rawQuery("SELECT singleton,version,generation,owner,owner_package,owner_uid,capacity FROM metadata", null).use { cursor ->
            check(cursor.count == 1 && cursor.moveToFirst())
            check(listOf(0, 1, 5, 6).all { cursor.getType(it) == Cursor.FIELD_TYPE_INTEGER })
            check(listOf(2, 3, 4).all { cursor.getType(it) == Cursor.FIELD_TYPE_STRING })
            check(cursor.getLong(0) == 1L && cursor.getLong(1) == 1L)
            check(cursor.getString(2) == generation && cursor.getString(3) == owner.wireId)
            check(cursor.getString(4) == configuration.ownerPackage && cursor.getLong(5) == configuration.uid.toLong())
            check(cursor.getLong(6) == configuration.capacity.toLong())
        }
        val snapshots = mutableListOf<SshTrustSnapshot>()
        val peers = mutableSetOf<SshPeerIdentity>()
        db.rawQuery("SELECT host,port,route,owner,revision,state,key_format,fingerprint FROM pins ORDER BY host,port,route", null).use { cursor ->
            check(cursor.count <= configuration.capacity)
            while (cursor.moveToNext()) {
                check(listOf(0, 2, 3, 6, 7).all { cursor.getType(it) == Cursor.FIELD_TYPE_STRING })
                check(listOf(1, 4, 5).all { cursor.getType(it) == Cursor.FIELD_TYPE_INTEGER })
                val port = cursor.getLong(1)
                check(port in 1L..65535L && cursor.getString(2) == SshTrustRoute.Direct.wireId)
                check(cursor.getString(3) == owner.wireId)
                val peer = SshPeerIdentity.create(cursor.getString(0), port.toInt(), SshTrustRoute.Direct)
                check(peer.host == cursor.getString(0) && peers.add(peer))
                val key = SshHostKeyIdentity.create(cursor.getString(6), cursor.getString(7))
                check(key.keyFormat == cursor.getString(6) && key.fingerprint == cursor.getString(7))
                val status = when (cursor.getLong(5)) {
                    0L -> SshTrustStatus.accepted(key)
                    1L -> SshTrustStatus.revoked(key)
                    else -> error("STORE_UNAVAILABLE")
                }
                snapshots += SshTrustSnapshot.create(owner, generation, peer, cursor.getLong(4), status)
            }
        }
        return SshTrustCatalog(owner, generation, snapshots)
    }

    private fun values(snapshot: SshTrustSnapshot): ContentValues = ContentValues().apply {
        put("host", snapshot.peer.host); put("port", snapshot.peer.port); put("route", snapshot.peer.route.wireId)
        put("owner", snapshot.owner.wireId); put("revision", snapshot.revision)
        put("state", if (snapshot.status is SshTrustStatus.Accepted) 0 else 1)
        put("key_format", key(snapshot.status).keyFormat); put("fingerprint", key(snapshot.status).fingerprint)
    }

    private fun casArguments(snapshot: SshTrustSnapshot): Array<String> = arrayOf(
        snapshot.peer.host, snapshot.peer.port.toString(), snapshot.peer.route.wireId, snapshot.owner.wireId,
        snapshot.revision.toString(), if (snapshot.status is SshTrustStatus.Accepted) "0" else "1",
        key(snapshot.status).keyFormat, key(snapshot.status).fingerprint,
    )

    private fun key(status: SshTrustStatus): SshHostKeyIdentity = when (status) {
        is SshTrustStatus.Accepted -> status.key
        is SshTrustStatus.Revoked -> status.lastKey
    }

    private fun readGeneration(): String {
        check(marker.isFile && marker.length() == 36L)
        return marker.readText(Charsets.US_ASCII).also { value ->
            check(value.length == 36 && UUID.fromString(value).let { it.toString() == value && it.version() == 4 && it.variant() == 2 })
        }
    }

    private fun checkPaths() {
        for (path in listOf(anchor, marker, directory, database,
            File(database.path + "-journal"), File(database.path + "-wal"), File(database.path + "-shm"))) {
            check(!Files.isSymbolicLink(path.toPath()))
            if (path.exists()) check(if (path == directory) path.isDirectory else path.isFile)
        }
        check(!File(database.path + "-wal").exists() && !File(database.path + "-shm").exists())
    }

    private data class Configuration(
        val root: () -> File, val owner: WindowOwner, val ownerPackage: String, val uid: Int,
        val deviceProtected: Boolean, val onMainThread: () -> Boolean, val capacity: Int, val validProcess: () -> Boolean,
    )

    companion object {
        const val MAX_RECORDS = 512
        private const val MAX_DATABASE_BYTES = 4L * 1024 * 1024
        private const val CAS_WHERE = "host=? AND port=? AND route=? AND owner=? AND revision=? AND state=? AND key_format=? AND fingerprint=?"
        private val monitor = ReentrantLock()

        private fun configuration(context: Context): Configuration {
            val suppliedDeviceProtected = context.isDeviceProtectedStorage
            val application = context.applicationContext
            val packageName = application.packageName
            val owner = WindowOwner.entries.singleOrNull { packageFor(it) == packageName } ?: WindowOwner.CONSOLE
            val uid = Process.myUid()
            return Configuration({ application.noBackupFilesDir }, owner, packageName, uid,
                suppliedDeviceProtected || application.isDeviceProtectedStorage,
                { Looper.myLooper() == Looper.getMainLooper() }, MAX_RECORDS,
                { application.applicationInfo.uid == uid && Process.myUid() == uid })
        }

        private fun packageFor(owner: WindowOwner): String = when (owner) {
            WindowOwner.CONSOLE -> "io.github.baitian6641.cloudy.console"
            WindowOwner.DESKTOP -> "io.github.baitian6641.cloudy.desktop"
        }
        private fun failure(code: SshTrustStoreCode) = SshTrustStoreResult.Failure(code)
        private fun syncDirectory(directory: File) {
            val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            try { check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)); Os.fsync(descriptor) }
            finally { Os.close(descriptor) }
        }
    }
}
