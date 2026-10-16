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
import io.github.baitian6641.cloudy.domain.FilesResourceDraft
import io.github.baitian6641.cloudy.domain.FilesResourceReference
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.domain.FilesResourceState
import io.github.baitian6641.cloudy.domain.FilesResourceVersion
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshObservationProfile
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

enum class FilesResourceCode {
    INVALID_INPUT, WRONG_OWNER, WRONG_THREAD, BUSY, STORE_UNAVAILABLE, COMPLETION_UNKNOWN,
    STALE_GENERATION, CONFLICT, NOT_FOUND, CAPACITY, REVISION_EXHAUSTED, INVALID_STATE, STALE_BINDING,
}

sealed interface FilesResourceResult<out T> {
    class Success<T>(val value: T) : FilesResourceResult<T> {
        override fun toString(): String = "FilesResourceResult.Success(redacted)"
    }
    data class Failure(val code: FilesResourceCode) : FilesResourceResult<Nothing>
}

class FilesResourceCatalog internal constructor(
    val owner: WindowOwner, val generation: String, registrations: List<FilesResourceRegistration>,
) {
    val registrations: List<FilesResourceRegistration> = Collections.unmodifiableList(ArrayList(registrations))
    override fun toString(): String = "FilesResourceCatalog(redacted)"
}

/**
 * Receiver-owned navigation metadata only. Persistence does not prove listing, user consent,
 * a live lease or remote authorization. Revoked tombstones are retained without delete/reset.
 * Lock order is authentication coordination, this store monitor, permanent file lock, SQLite.
 */
class FilesResourceStore private constructor(private val configuration: Configuration) {
    constructor(context: Context) : this(configuration(context))

    internal constructor(
        root: File, owner: WindowOwner, ownerPackage: String, onMainThread: () -> Boolean,
        profiles: ConnectionProfiles, trust: SshHostTrustStore,
        capacity: Int = MAX_RECORDS, validProcess: () -> Boolean = { true },
    ) : this(Configuration({ root }, owner, ownerPackage, Process.myUid(), false, onMainThread,
        capacity, validProcess, { profiles }, { trust }))

    private val owner get() = configuration.owner
    private val root by lazy { configuration.root() }
    private val directory by lazy { File(root, "files-resources-v1") }
    private val marker by lazy { File(root, "files-resources-v1.instance") }
    private val anchor by lazy { File(root, "files-resources-v1.lock") }
    private val database by lazy { File(directory, "resources.db") }

    init { require(configuration.capacity in 1..MAX_RECORDS) { "INVALID_INPUT" } }

    fun load(): FilesResourceResult<FilesResourceCatalog> = operate { _, catalog, _ ->
        FilesResourceResult.Success(catalog)
    }

    fun create(expectedGeneration: String, draft: FilesResourceDraft): FilesResourceResult<FilesResourceRegistration> {
        if (!canonicalUuid(expectedGeneration)) return failure(FilesResourceCode.INVALID_INPUT)
        if (draft.owner != owner) return failure(FilesResourceCode.WRONG_OWNER)
        return operate { db, catalog, attempting ->
            if (catalog.generation != expectedGeneration) return@operate failure(FilesResourceCode.STALE_GENERATION)
            if (catalog.registrations.size >= configuration.capacity) return@operate failure(FilesResourceCode.CAPACITY)
            bindingFailure(draft)?.let { return@operate failure(it) }
            val created = FilesResourceRegistration.create(catalog.generation, draft)
            val usedIds = catalog.registrations.flatMap { ids(it.reference) }.toSet()
            check(ids(created.reference).none { it in usedIds })
            attempting()
            db.insertOrThrow("resources", null, values(created))
            FilesResourceResult.Success(created)
        }
    }

    fun share(version: FilesResourceVersion): FilesResourceResult<FilesResourceRegistration> =
        change(version, FilesResourceState.SHARED)

    fun revoke(version: FilesResourceVersion): FilesResourceResult<FilesResourceRegistration> =
        change(version, FilesResourceState.REVOKED)

    private fun change(version: FilesResourceVersion, next: FilesResourceState): FilesResourceResult<FilesResourceRegistration> =
        operate { db, catalog, attempting ->
            if (version.generation != catalog.generation) return@operate failure(FilesResourceCode.STALE_GENERATION)
            val old = catalog.registrations.singleOrNull { it.reference.resourceId == version.resourceId }
                ?: return@operate failure(FilesResourceCode.NOT_FOUND)
            if (old.revision != version.revision) return@operate failure(FilesResourceCode.CONFLICT)
            if (old.state == FilesResourceState.REVOKED ||
                (next == FilesResourceState.SHARED && old.state != FilesResourceState.PRIVATE)) {
                return@operate failure(FilesResourceCode.INVALID_STATE)
            }
            if (old.revision == Long.MAX_VALUE) return@operate failure(FilesResourceCode.REVISION_EXHAUSTED)
            // Revoke remains available even after its bound profile/pin has changed.
            if (next == FilesResourceState.SHARED) bindingFailure(old.draft)?.let { return@operate failure(it) }
            val saved = if (next == FilesResourceState.SHARED) old.share() else old.revoke()
            // Invalidate exact resource owners before a possible write, including an uncertain
            // commit. Ordinary sessions and other registrations sharing this peer stay independent.
            SshAuthenticationRegistry.invalidateResource(root, owner, catalog.generation, old.reference.resourceId)
            attempting()
            check(db.update("resources", values(saved), CAS_WHERE,
                arrayOf(old.reference.resourceId, old.revision.toString(), old.state.name)) == 1)
            FilesResourceResult.Success(saved)
        }

    fun resolve(
        reference: FilesResourceReference, requiredState: FilesResourceState = FilesResourceState.SHARED,
    ): FilesResourceResult<FilesResourceRegistration> {
        if (requiredState == FilesResourceState.REVOKED) return failure(FilesResourceCode.INVALID_STATE)
        return operate { _, catalog, _ ->
            val registration = catalog.registrations.singleOrNull { it.reference == reference }
                ?: return@operate failure(FilesResourceCode.NOT_FOUND)
            if (registration.state == FilesResourceState.REVOKED || registration.state != requiredState) {
                return@operate failure(FilesResourceCode.INVALID_STATE)
            }
            bindingFailure(registration.draft)?.let { return@operate failure(it) }
            FilesResourceResult.Success(registration)
        }
    }

    private fun bindingFailure(draft: FilesResourceDraft): FilesResourceCode? {
        val profiles = when (val result = configuration.profiles().load()) {
            is ConnectionProfilesResult.Success -> result.value
            is ConnectionProfilesResult.Failure -> return if (result.code == ConnectionProfilesCode.BUSY)
                FilesResourceCode.BUSY else FilesResourceCode.STORE_UNAVAILABLE
        }
        val observed = draft.profile
        val current = profiles.profiles.singleOrNull { it.id == observed.profile.id }
            ?: return FilesResourceCode.STALE_BINDING
        if (current.protocol != ConnectionProtocol.SSH || profiles.generation != observed.generation || SshObservationProfile.create(profiles.generation, current) != observed) {
            return FilesResourceCode.STALE_BINDING
        }
        val lookup = when (val result = configuration.trust().lookup(observed.peer)) {
            is SshTrustStoreResult.Success -> result.value
            is SshTrustStoreResult.Failure -> return if (result.code == SshTrustStoreCode.BUSY)
                FilesResourceCode.BUSY else FilesResourceCode.STORE_UNAVAILABLE
        }
        return if (lookup.owner != owner || lookup.current != draft.acceptedTrust ||
            lookup.current?.status !is SshTrustStatus.Accepted) FilesResourceCode.STALE_BINDING else null
    }

    private fun <T> operate(
        operation: (SQLiteDatabase, FilesResourceCatalog, () -> Unit) -> FilesResourceResult<T>,
    ): FilesResourceResult<T> {
        if (configuration.onMainThread()) return failure(FilesResourceCode.WRONG_THREAD)
        if (configuration.deviceProtected) return failure(FilesResourceCode.STORE_UNAVAILABLE)
        if (packageFor(owner) != configuration.ownerPackage) return failure(FilesResourceCode.WRONG_OWNER)
        if (!SshAuthenticationRegistry.lock.tryLock()) return failure(FilesResourceCode.BUSY)
        if (!monitor.tryLock()) {
            SshAuthenticationRegistry.lock.unlock()
            return failure(FilesResourceCode.BUSY)
        }
        var mutationAttempted = false
        return try {
            // Check process identity before evaluating the lazy private storage root.
            if (!configuration.validProcess()) return failure(FilesResourceCode.WRONG_OWNER)
            check(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
            checkPaths()
            if (!anchor.exists() && (directory.exists() || marker.exists())) return failure(FilesResourceCode.STORE_UNAVAILABLE)
            RandomAccessFile(anchor, "rw").use { lockFile ->
                val fileLock = lockFile.channel.tryLock() ?: return failure(FilesResourceCode.BUSY)
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
                            if (result is FilesResourceResult.Success) db.setTransactionSuccessful()
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
            failure(if (mutationAttempted) FilesResourceCode.COMPLETION_UNKNOWN else FilesResourceCode.BUSY)
        } catch (_: Exception) {
            failure(if (mutationAttempted) FilesResourceCode.COMPLETION_UNKNOWN else FilesResourceCode.STORE_UNAVAILABLE)
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
                    "owner_package TEXT NOT NULL, owner_uid INTEGER NOT NULL, capacity INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE resources (host_id TEXT NOT NULL, environment_id TEXT NOT NULL, " +
                    "workspace_id TEXT NOT NULL, resource_id TEXT PRIMARY KEY NOT NULL, revision INTEGER NOT NULL, " +
                    "state TEXT NOT NULL, owner TEXT NOT NULL, label TEXT NOT NULL, profile_generation TEXT NOT NULL, " +
                    "profile_id TEXT NOT NULL, profile_revision INTEGER NOT NULL, profile_label TEXT NOT NULL, " +
                    "host TEXT NOT NULL, port INTEGER NOT NULL, trust_generation TEXT NOT NULL, trust_revision INTEGER NOT NULL, " +
                    "key_format TEXT NOT NULL, fingerprint TEXT NOT NULL, navigation_root TEXT NOT NULL, account_name TEXT NOT NULL)")
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

    private fun readCatalog(db: SQLiteDatabase, generation: String): FilesResourceCatalog {
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
        validateColumns(db, "metadata", listOf("singleton", "version", "generation", "owner", "owner_package", "owner_uid", "capacity"),
            setOf("singleton", "version", "owner_uid", "capacity"))
        validateColumns(db, "resources", COLUMNS.split(','), setOf("revision", "profile_revision", "port", "trust_revision"))
        val registrations = mutableListOf<FilesResourceRegistration>()
        val allIds = mutableSetOf<String>()
        db.rawQuery("SELECT $COLUMNS FROM resources ORDER BY resource_id", null).use { cursor ->
            check(cursor.count <= configuration.capacity)
            while (cursor.moveToNext()) {
                val integers = setOf(4, 10, 13, 15)
                for (index in 0 until 20) check(cursor.getType(index) ==
                    if (index in integers) Cursor.FIELD_TYPE_INTEGER else Cursor.FIELD_TYPE_STRING)
                for (index in 0 until 20) if (index !in integers) check(cursor.getString(index).length <= 4096)
                val reference = FilesResourceReference.restore(cursor.getString(0), cursor.getString(1),
                    cursor.getString(2), cursor.getString(3))
                check(ids(reference).all { allIds.add(it) })
                check(cursor.getString(6) == owner.wireId)
                val port = cursor.getLong(13)
                check(port in 1L..65535L)
                val profile = ConnectionProfile.restore(cursor.getString(9), cursor.getLong(10),
                    ConnectionProfileDraft.create(owner, cursor.getString(11), ConnectionProtocol.SSH,
                        cursor.getString(12), port.toInt()))
                val observed = SshObservationProfile.create(cursor.getString(8), profile)
                val key = SshHostKeyIdentity.create(cursor.getString(16), cursor.getString(17))
                val trust = SshTrustSnapshot.create(owner, cursor.getString(14), observed.peer,
                    cursor.getLong(15), SshTrustStatus.accepted(key))
                val draft = FilesResourceDraft.create(owner, cursor.getString(7), observed, trust,
                    cursor.getString(18), cursor.getString(19))
                val saved = FilesResourceRegistration.restore(generation, reference, cursor.getLong(4),
                    FilesResourceState.valueOf(cursor.getString(5)), draft)
                // Domain factories may normalize labels/hosts: stored rows must already be canonical.
                val canonical = values(saved)
                COLUMNS.split(',').forEachIndexed { index, column ->
                    if (index in integers) check(canonical.getAsLong(column) == cursor.getLong(index))
                    else check(canonical.getAsString(column) == cursor.getString(index))
                }
                registrations += saved
            }
        }
        return FilesResourceCatalog(owner, generation, registrations)
    }

    private fun validateColumns(db: SQLiteDatabase, table: String, names: List<String>, integers: Set<String>) {
        // Table names are fixed code constants, never receiver/sender submitted identifiers.
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            check(cursor.count == names.size)
            var index = 0
            while (cursor.moveToNext()) {
                val name = names[index++]
                check(cursor.getString(1) == name)
                check(cursor.getString(2) == if (name in integers) "INTEGER" else "TEXT")
            }
        }
    }

    private fun values(registration: FilesResourceRegistration): ContentValues = ContentValues().apply {
        val draft = registration.draft
        val profile = draft.profile.profile
        val trust = draft.acceptedTrust
        val key = (trust.status as SshTrustStatus.Accepted).key
        put("host_id", registration.reference.hostId); put("environment_id", registration.reference.environmentId)
        put("workspace_id", registration.reference.workspaceId); put("resource_id", registration.reference.resourceId)
        put("revision", registration.revision); put("state", registration.state.name); put("owner", draft.owner.wireId)
        put("label", draft.label); put("profile_generation", draft.profile.generation); put("profile_id", profile.id)
        put("profile_revision", profile.revision); put("profile_label", profile.label); put("host", profile.host); put("port", profile.port)
        put("trust_generation", trust.generation); put("trust_revision", trust.revision)
        put("key_format", key.keyFormat); put("fingerprint", key.fingerprint)
        put("navigation_root", draft.navigationRoot); put("account_name", draft.accountName)
    }

    private fun ids(reference: FilesResourceReference): List<String> = listOf(reference.hostId,
        reference.environmentId, reference.workspaceId, reference.resourceId)

    private fun canonicalUuid(value: String): Boolean = try {
        value.length == 36 && UUID.fromString(value).let { it.toString() == value && it.version() == 4 && it.variant() == 2 }
    } catch (_: IllegalArgumentException) { false }

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
        val profiles: () -> ConnectionProfiles, val trust: () -> SshHostTrustStore,
    )

    companion object {
        const val MAX_RECORDS = 512
        private const val MAX_DATABASE_BYTES = 4L * 1024 * 1024
        private const val CAS_WHERE = "resource_id=? AND revision=? AND state=?"
        private const val COLUMNS = "host_id,environment_id,workspace_id,resource_id,revision,state,owner,label," +
            "profile_generation,profile_id,profile_revision,profile_label,host,port,trust_generation,trust_revision," +
            "key_format,fingerprint,navigation_root,account_name"
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
                { application.applicationInfo.uid == uid && Process.myUid() == uid && Application.getProcessName() == packageName },
                { ConnectionProfiles(application) }, { SshHostTrustStore(application) })
        }

        private fun packageFor(owner: WindowOwner): String = when (owner) {
            WindowOwner.CONSOLE -> "io.github.baitian6641.cloudy.console"
            WindowOwner.DESKTOP -> "io.github.baitian6641.cloudy.desktop"
        }
        private fun failure(code: FilesResourceCode) = FilesResourceResult.Failure(code)
        private fun syncDirectory(directory: File) {
            val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            try { check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)); Os.fsync(descriptor) }
            finally { Os.close(descriptor) }
        }
    }
}
