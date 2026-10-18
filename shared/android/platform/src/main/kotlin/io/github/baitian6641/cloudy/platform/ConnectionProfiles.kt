package io.github.baitian6641.cloudy.platform

import android.app.Application
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
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.util.Collections
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

enum class ConnectionProfilesCode {
    INVALID_INPUT, WRONG_OWNER, WRONG_THREAD, BUSY, STORE_UNAVAILABLE, COMPLETION_UNKNOWN,
    STALE_GENERATION, CONFLICT, NOT_FOUND, CAPACITY, REVISION_EXHAUSTED,
}

sealed interface ConnectionProfilesResult<out T> {
    data class Success<T>(val value: T) : ConnectionProfilesResult<T>
    data class Failure(val code: ConnectionProfilesCode) : ConnectionProfilesResult<Nothing>
}

/** Editor identity is bound to a specific store lifetime and committed row revision. */
class ConnectionProfileVersion private constructor(val generation: String, val id: String, val revision: Long) {
    override fun toString(): String = "ConnectionProfileVersion(redacted)"
    companion object {
        fun create(generation: String, id: String, revision: Long): ConnectionProfileVersion {
            require(canonicalUuid(generation) && canonicalUuid(id) && revision > 0) { "INVALID_INPUT" }
            return ConnectionProfileVersion(generation, id, revision)
        }
        fun parse(generation: String, id: String, revision: String): ConnectionProfileVersion {
            require(revision.length in 1..19) { "INVALID_INPUT" }
            val number = revision.toLongOrNull()
            require(number != null && number.toString() == revision) { "INVALID_INPUT" }
            return create(generation, id, number)
        }
    }
}

class ConnectionProfileCatalog internal constructor(val generation: String, profiles: List<ConnectionProfile>) {
    val profiles: List<ConnectionProfile> = Collections.unmodifiableList(ArrayList(profiles))
    override fun toString(): String = "ConnectionProfileCatalog(redacted)"
}

class ConnectionProfileEntry internal constructor(val profile: ConnectionProfile, val version: ConnectionProfileVersion) {
    override fun toString(): String = "ConnectionProfileEntry(redacted)"
}

/** Local endpoint metadata only; saving creates neither trust nor a connected remote session. */
class ConnectionProfiles private constructor(private val configuration: Configuration) {
    constructor(context: Context) : this(configuration(context))

    internal constructor(
        root: File, owner: WindowOwner, ownerPackage: String, onMainThread: () -> Boolean,
        capacity: Int = MAX_RECORDS,
    ) : this(Configuration({ root }, owner, ownerPackage, false, onMainThread, capacity, { true }))

    private val owner get() = configuration.owner
    private val root by lazy { configuration.root() }
    private val directory by lazy { File(root, "connection-profiles-v1") }
    private val marker by lazy { File(root, "connection-profiles-v1.instance") }
    private val anchor by lazy { File(root, "connection-profiles-v1.lock") }
    private val database by lazy { File(directory, "profiles.db") }

    init { require(configuration.capacity in 1..MAX_RECORDS) { "INVALID_INPUT" } }

    fun load(): ConnectionProfilesResult<ConnectionProfileCatalog> = operate { _, catalog, _ ->
        ConnectionProfilesResult.Success(catalog)
    }

    fun create(expectedGeneration: String, draft: ConnectionProfileDraft): ConnectionProfilesResult<ConnectionProfileEntry> {
        if (!canonicalUuid(expectedGeneration)) return failure(ConnectionProfilesCode.INVALID_INPUT)
        if (draft.owner != owner) return failure(ConnectionProfilesCode.WRONG_OWNER)
        return operate { db, catalog, attempting ->
            if (catalog.generation != expectedGeneration) return@operate failure(ConnectionProfilesCode.STALE_GENERATION)
            if (catalog.profiles.size >= configuration.capacity) return@operate failure(ConnectionProfilesCode.CAPACITY)
            val profile = ConnectionProfile.create(draft)
            if (!jumpAllowed(catalog.profiles, profile)) return@operate failure(ConnectionProfilesCode.INVALID_INPUT)
            attempting()
            db.insertOrThrow("profiles", null, values(profile))
            ConnectionProfilesResult.Success(entry(catalog.generation, profile))
        }
    }

    fun update(version: ConnectionProfileVersion, draft: ConnectionProfileDraft): ConnectionProfilesResult<ConnectionProfileEntry> {
        if (draft.owner != owner) return failure(ConnectionProfilesCode.WRONG_OWNER)
        return operate { db, catalog, attempting ->
            versionFailure(catalog, version)?.let { return@operate it }
            val old = catalog.profiles.single { it.id == version.id }
            if (old.revision == Long.MAX_VALUE) return@operate failure(ConnectionProfilesCode.REVISION_EXHAUSTED)
            if (draft.jumpProfileId == version.id) return@operate failure(ConnectionProfilesCode.INVALID_INPUT)
            val revised = old.revised(draft)
            if (!jumpAllowed(catalog.profiles, revised)) return@operate failure(ConnectionProfilesCode.INVALID_INPUT)
            if (draft.protocol != ConnectionProtocol.SSH || draft.jumpProfileId != null) {
                if (catalog.profiles.any { it.jumpProfileId == version.id }) {
                    return@operate failure(ConnectionProfilesCode.CONFLICT)
                }
            }
            SshAuthenticationRegistry.invalidateProfile(root, owner, version.id)
            attempting()
            val changed = db.update("profiles", values(revised), "id=? AND revision=?",
                arrayOf(version.id, version.revision.toString()))
            check(changed == 1)
            ConnectionProfilesResult.Success(entry(catalog.generation, revised))
        }
    }

    fun delete(version: ConnectionProfileVersion): ConnectionProfilesResult<Unit> = operate { db, catalog, attempting ->
        versionFailure(catalog, version)?.let { return@operate it }
        if (catalog.profiles.any { it.jumpProfileId == version.id }) return@operate failure(ConnectionProfilesCode.CONFLICT)
        SshAuthenticationRegistry.invalidateProfile(root, owner, version.id)
        attempting()
        check(db.delete("profiles", "id=? AND revision=?", arrayOf(version.id, version.revision.toString())) == 1)
        ConnectionProfilesResult.Success(Unit)
    }

    /** A connect attempt records only the marker mutation; endpoint/trust data is untouched. */
    fun recordUse(version: ConnectionProfileVersion, atEpochSeconds: Long): ConnectionProfilesResult<ConnectionProfileEntry> {
        if (atEpochSeconds < 1) return failure(ConnectionProfilesCode.INVALID_INPUT)
        return operate { db, catalog, attempting ->
            versionFailure(catalog, version)?.let { return@operate it }
            val old = catalog.profiles.single { it.id == version.id }
            if (old.revision == Long.MAX_VALUE) return@operate failure(ConnectionProfilesCode.REVISION_EXHAUSTED)
            val marked = old.recordedUse(atEpochSeconds)
            attempting()
            val changed = db.update("profiles", values(marked), "id=? AND revision=?",
                arrayOf(version.id, version.revision.toString()))
            check(changed == 1)
            ConnectionProfilesResult.Success(entry(catalog.generation, marked))
        }
    }

    private fun versionFailure(catalog: ConnectionProfileCatalog, version: ConnectionProfileVersion): ConnectionProfilesResult.Failure? {
        if (catalog.generation != version.generation) return failure(ConnectionProfilesCode.STALE_GENERATION)
        val profile = catalog.profiles.singleOrNull { it.id == version.id }
            ?: return failure(ConnectionProfilesCode.NOT_FOUND)
        return if (profile.revision != version.revision) failure(ConnectionProfilesCode.CONFLICT) else null
    }

    private fun <T> operate(
        operation: (SQLiteDatabase, ConnectionProfileCatalog, () -> Unit) -> ConnectionProfilesResult<T>,
    ): ConnectionProfilesResult<T> {
        if (configuration.onMainThread()) return failure(ConnectionProfilesCode.WRONG_THREAD)
        if (configuration.deviceProtected) return failure(ConnectionProfilesCode.STORE_UNAVAILABLE)
        if (packageFor(owner) != configuration.ownerPackage) return failure(ConnectionProfilesCode.WRONG_OWNER)
        if (!SshAuthenticationRegistry.lock.tryLock()) return failure(ConnectionProfilesCode.BUSY)
        if (!monitor.tryLock()) {
            SshAuthenticationRegistry.lock.unlock()
            return failure(ConnectionProfilesCode.BUSY)
        }
        var mutationAttempted = false
        return try {
            if (!configuration.validProcess()) return failure(ConnectionProfilesCode.WRONG_OWNER)
            check(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
            checkPaths()
            if (!anchor.exists() && (directory.exists() || marker.exists())) {
                return failure(ConnectionProfilesCode.STORE_UNAVAILABLE)
            }
            RandomAccessFile(anchor, "rw").use { lockFile ->
                val fileLock = lockFile.channel.tryLock() ?: return failure(ConnectionProfilesCode.BUSY)
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
                            migrateIfNeeded(db)
                            val catalog = readCatalog(db, generation)
                            val result = operation(db, catalog) { mutationAttempted = true }
                            if (result is ConnectionProfilesResult.Success) db.setTransactionSuccessful()
                            result
                        } finally {
                            // Success cannot escape before commit. An exception after a mutation is
                            // ambiguous to the caller even if a later reload shows no committed row.
                            db.endTransaction()
                        }
                    }
                }
            }
        } catch (_: OverlappingFileLockException) {
            failure(if (mutationAttempted) ConnectionProfilesCode.COMPLETION_UNKNOWN else ConnectionProfilesCode.BUSY)
        } catch (_: Exception) {
            failure(if (mutationAttempted) ConnectionProfilesCode.COMPLETION_UNKNOWN else ConnectionProfilesCode.STORE_UNAVAILABLE)
        } finally { monitor.unlock(); SshAuthenticationRegistry.lock.unlock() }
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
                    "owner_package TEXT NOT NULL, capacity INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE profiles (id TEXT PRIMARY KEY NOT NULL, revision INTEGER NOT NULL, " +
                    "owner TEXT NOT NULL, label TEXT NOT NULL, protocol TEXT NOT NULL, host TEXT NOT NULL, " +
                    "port INTEGER NOT NULL, tags TEXT NOT NULL, favorite INTEGER NOT NULL, last_used INTEGER NOT NULL, " +
                    "jump_profile_id TEXT NOT NULL DEFAULT '', key_uri TEXT NOT NULL DEFAULT '')")
                db.execSQL("INSERT INTO metadata VALUES (1, 4, ?, ?, ?, ?)",
                    arrayOf<Any>(generation, owner.wireId, configuration.ownerPackage, configuration.capacity))
                db.execSQL("PRAGMA user_version=4")
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

    /**
     * Schema v1 -> v2 adds organization metadata (tags, favorite, last_used) with defaults that
     * preserve every existing row's identity and revision. SQLite DDL is transactional here, so a
     * failed migration rolls back with the caller's operation and the v1 store stays intact.
     */
    private fun migrateIfNeeded(db: SQLiteDatabase) {
        val version = db.rawQuery("PRAGMA user_version", null).use {
            check(it.moveToFirst()); it.getLong(0)
        }
        when (version) {
            4L -> return
            3L -> migrateV3ToV4(db)
            2L -> { migrateV2ToV3(db); migrateV3ToV4(db) }
            1L -> {
                db.execSQL("ALTER TABLE profiles ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN last_used INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE metadata SET version=2")
                db.execSQL("PRAGMA user_version=2")
                migrateV2ToV3(db); migrateV3ToV4(db)
            }
            else -> throw SQLiteDatabaseCorruptException("STORE_UNAVAILABLE")
        }
    }

    /** v3 adds a nullable one-hop jump reference; existing rows keep their identity and revision. */
    private fun migrateV2ToV3(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE profiles ADD COLUMN jump_profile_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE metadata SET version=3")
        db.execSQL("PRAGMA user_version=3")
    }

    /** v4 adds an optional content-URI key reference; it never contains key material. */
    private fun migrateV3ToV4(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE profiles ADD COLUMN key_uri TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE metadata SET version=4")
        db.execSQL("PRAGMA user_version=4")
    }

    private fun readCatalog(db: SQLiteDatabase, generation: String): ConnectionProfileCatalog {
        check(db.rawQuery("PRAGMA quick_check(1)", null).use { it.moveToFirst() && it.getString(0) == "ok" })
        check(db.rawQuery("PRAGMA user_version", null).use { it.moveToFirst() && it.getLong(0) == 4L })
        db.rawQuery("SELECT singleton,version,generation,owner,owner_package,capacity FROM metadata", null).use { cursor ->
            check(cursor.count == 1 && cursor.moveToFirst())
            check(listOf(0, 1, 5).all { cursor.getType(it) == Cursor.FIELD_TYPE_INTEGER })
            check(listOf(2, 3, 4).all { cursor.getType(it) == Cursor.FIELD_TYPE_STRING })
            check(cursor.getLong(0) == 1L && cursor.getLong(1) == 4L)
            check(cursor.getString(2) == generation && cursor.getString(3) == owner.wireId)
            check(cursor.getString(4) == configuration.ownerPackage && cursor.getLong(5) == configuration.capacity.toLong())
        }
        val profiles = mutableListOf<ConnectionProfile>()
        val ids = mutableSetOf<String>()
        db.rawQuery("SELECT id,revision,owner,label,protocol,host,port,tags,favorite,last_used,jump_profile_id,key_uri FROM profiles ORDER BY id", null).use { cursor ->
            check(cursor.count <= configuration.capacity)
            while (cursor.moveToNext()) {
                check(listOf(0, 2, 3, 4, 5, 7).all { cursor.getType(it) == Cursor.FIELD_TYPE_STRING })
                check(listOf(1, 6, 8, 9).all { cursor.getType(it) == Cursor.FIELD_TYPE_INTEGER })
                check(cursor.getType(10) == Cursor.FIELD_TYPE_STRING)
                check(cursor.getType(11) == Cursor.FIELD_TYPE_STRING)
                val id = cursor.getString(0)
                check(canonicalUuid(id) && ids.add(id))
                check(cursor.getString(2) == owner.wireId)
                val protocol = ConnectionProtocol.entries.single { it.wireId == cursor.getString(4) }
                val port = cursor.getLong(6)
                check(port in 1L..65535L)
                val encodedTags = cursor.getString(7)
                val tags = if (encodedTags.isEmpty()) emptySet() else encodedTags.split('\n').toSet()
                val favorite = cursor.getLong(8)
                check(favorite == 0L || favorite == 1L)
                val lastUsed = cursor.getLong(9)
                check(lastUsed >= 0L)
                val jumpId = cursor.getString(10).takeIf { it.isNotEmpty() }
                check(jumpId == null || canonicalUuid(jumpId))
                val keyUri = cursor.getString(11).takeIf { it.isNotEmpty() }
                val draft = ConnectionProfileDraft.create(owner, cursor.getString(3), protocol,
                    cursor.getString(5), port.toInt(), tags, favorite == 1L, jumpId, keyUri)
                check(draft.label == cursor.getString(3) && draft.host == cursor.getString(5))
                // The stored encoding must round-trip through domain normalization exactly.
                check(draft.tags == tags)
                profiles += ConnectionProfile.restore(id, cursor.getLong(1), draft, lastUsed)
            }
        }
        val byId = profiles.associateBy { it.id }
        for (profile in profiles) {
            val jump = profile.jumpProfileId ?: continue
            check(jump != profile.id)
            val hop = byId[jump] ?: throw SQLiteDatabaseCorruptException("STORE_UNAVAILABLE")
            check(hop.protocol == ConnectionProtocol.SSH && hop.jumpProfileId == null)
        }
        return ConnectionProfileCatalog(generation, profiles)
    }

    private fun values(profile: ConnectionProfile): ContentValues = ContentValues().apply {
        put("id", profile.id); put("revision", profile.revision); put("owner", profile.owner.wireId)
        put("label", profile.label); put("protocol", profile.protocol.wireId); put("host", profile.host); put("port", profile.port)
        // Domain validation guarantees tags contain no line separators; the join is reversible.
        put("tags", profile.tags.joinToString("\n"))
        put("favorite", if (profile.favorite) 1L else 0L); put("last_used", profile.lastUsedAt)
        put("jump_profile_id", profile.jumpProfileId ?: "")
        put("key_uri", profile.keyUri ?: "")
    }

    private fun jumpAllowed(profiles: List<ConnectionProfile>, candidate: ConnectionProfile): Boolean {
        val jump = candidate.jumpProfileId ?: return true
        if (jump == candidate.id) return false
        val hop = profiles.singleOrNull { it.id == jump } ?: return false
        return hop.protocol == ConnectionProtocol.SSH && hop.jumpProfileId == null
    }

    private fun entry(generation: String, profile: ConnectionProfile): ConnectionProfileEntry =
        ConnectionProfileEntry(profile, ConnectionProfileVersion.create(generation, profile.id, profile.revision))

    private fun readGeneration(): String {
        check(marker.isFile && marker.length() == 36L)
        return marker.readText(Charsets.US_ASCII).also { check(canonicalUuid(it)) }
    }

    private fun checkPaths() {
        for (path in listOf(anchor, marker, directory, database,
            File(database.path + "-journal"), File(database.path + "-wal"), File(database.path + "-shm"))) {
            check(!Files.isSymbolicLink(path.toPath()))
            if (path.exists()) check(if (path == directory) path.isDirectory else path.isFile)
        }
        // This store never operates in WAL mode; unexpected remnants indicate uncertain state.
        check(!File(database.path + "-wal").exists() && !File(database.path + "-shm").exists())
    }

    private data class Configuration(
        val root: () -> File, val owner: WindowOwner, val ownerPackage: String, val deviceProtected: Boolean,
        val onMainThread: () -> Boolean, val capacity: Int, val validProcess: () -> Boolean,
    )

    companion object {
        const val MAX_RECORDS = 512
        private const val MAX_DATABASE_BYTES = 4L * 1024 * 1024
        private val monitor = ReentrantLock()

        private fun configuration(context: Context): Configuration {
            val suppliedDeviceProtected = context.isDeviceProtectedStorage
            val application = context.applicationContext
            val packageName = application.packageName
            val owner = WindowOwner.entries.singleOrNull { packageFor(it) == packageName } ?: WindowOwner.CONSOLE
            return Configuration({ application.noBackupFilesDir }, owner, packageName,
                suppliedDeviceProtected || application.isDeviceProtectedStorage,
                { Looper.myLooper() == Looper.getMainLooper() }, MAX_RECORDS,
                { application.applicationInfo.uid == Process.myUid() && Application.getProcessName() == packageName })
        }

        private fun packageFor(owner: WindowOwner): String = when (owner) {
            WindowOwner.CONSOLE -> "io.github.baitian6641.cloudy.console"
            WindowOwner.DESKTOP -> "io.github.baitian6641.cloudy.desktop"
        }

        private fun failure(code: ConnectionProfilesCode) = ConnectionProfilesResult.Failure(code)

        private fun syncDirectory(directory: File) {
            val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            try {
                check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode))
                Os.fsync(descriptor)
            } finally { Os.close(descriptor) }
        }
    }
}

private fun canonicalUuid(value: String): Boolean = try {
    value.length == 36 && UUID.fromString(value).let { it.toString() == value && it.version() == 4 && it.variant() == 2 }
} catch (_: IllegalArgumentException) { false }
