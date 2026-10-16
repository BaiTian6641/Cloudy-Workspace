package io.github.baitian6641.cloudy.testing

import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import io.github.baitian6641.cloudy.domain.*
import io.github.baitian6641.cloudy.platform.*
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONArray
import org.json.JSONObject

/** Narrow test-only row removal, after real Activity/engine/worker settlement; never a product reset. */
internal class FilesLocationsFixture(private val context: Context, private val owner: WindowOwner,
                                     run: String, case: String) {
    private val store = FilesResourceStore(context)
    private val root = context.noBackupFilesDir
    private val wasInitialized = File(root, "files-resources-v1.lock").exists()
    private val baseline = load()
    private val metadata = locked { rows(it, "SELECT * FROM metadata") }
    private val owned = linkedMapOf<String, FilesResourceRegistration>()
    private var planned: FilesResourceDraft? = null
    private val receiptFile = File(root, "ssh-files-qualification/$run-${owner.wireId}-$case-locations.json")
    private val receipt = JSONObject().put("schema", 1).put("run", run).put("owner", owner.wireId).put("case", case)
        .put("uid", Process.myUid()).put("package", context.packageName).put("catalogPreviouslyInitialized", wasInitialized)
        .put("generation", baseline.generation).put("baselineMetadata", JSONArray(metadata))
        .put("baselineRows", JSONArray(baseline.registrations.map(::values))).put("phase", "BASELINE")

    init {
        check(context.packageName == "io.github.baitian6641.cloudy.${owner.wireId}" && context.applicationInfo.uid == Process.myUid())
        // AtomicFile may recover an earlier .bak or .new. Preserve the entire receipt family
        // instead of letting a repeated run silently replace interrupted cleanup evidence.
        check(listOf("", ".bak", ".new").none { suffix ->
            val path = File(receiptFile.path + suffix)
            path.exists() || Files.isSymbolicLink(path.toPath())
        })
        save("BASELINE")
    }
    fun plan(label: String, profile: SshObservationProfile, pin: SshTrustSnapshot, path: String, account: String) {
        assertExpected()
        planned = FilesResourceDraft.create(owner, label, profile, pin, path, account)
        receipt.put("plannedDraft", JSONObject().put("label", label).put("profileId", profile.profile.id)
            .put("profileGeneration", profile.generation).put("profileRevision", profile.profile.revision)
            .put("trustGeneration", pin.generation).put("trustRevision", pin.revision)
            .put("root", path).put("account", account))
        save("REGISTRATION_PLANNED")
    }
    fun captureCreated(): FilesResourceRegistration {
        val draft = checkNotNull(planned)
        val current = load()
        val prior = baseline.registrations + owned.values
        check(current.generation == baseline.generation && current.registrations.size == prior.size + 1)
        check(prior.all { it in current.registrations })
        val created = current.registrations.single { it !in prior }
        check(created.draft == draft && created.state == FilesResourceState.PRIVATE && created.revision == 1L)
        check(prior.none { it.reference.resourceId == created.reference.resourceId })
        owned[created.reference.resourceId] = created
        planned = null
        save("REGISTRATION_CAPTURED")
        assertExpected()
        return created
    }
    fun expectChange(row: FilesResourceRegistration, shared: Boolean): FilesResourceRegistration {
        assertExpected()
        check(owned[row.reference.resourceId] == row)
        val next = if (shared) row.share() else row.revoke()
        receipt.put("plannedChange", JSONArray(values(next)))
        save("STATE_CHANGE_PLANNED")
        return next
    }
    fun confirmChange(next: FilesResourceRegistration) {
        val prior = checkNotNull(owned[next.reference.resourceId])
        val expected = baseline.registrations + owned.values.filter { it.reference.resourceId != prior.reference.resourceId } + next
        val current = load()
        check(current.generation == baseline.generation && current.registrations.toSet() == expected.toSet() &&
            current.registrations.size == expected.size)
        owned[next.reference.resourceId] = next
        save("STATE_CHANGE_CONFIRMED")
    }
    fun changeThroughStore(row: FilesResourceRegistration, shared: Boolean): FilesResourceRegistration {
        val next = expectChange(row, shared)
        val result = if (shared) store.share(row.version()) else store.revoke(row.version())
        check(result is FilesResourceResult.Success && result.value == next)
        confirmChange(next)
        return next
    }
    fun assertExpected() {
        val current = load()
        val expected = baseline.registrations + owned.values
        check(current.generation == baseline.generation && current.registrations.size == expected.size &&
            current.registrations.toSet() == expected.toSet()) { "LOCATIONS_CHANGED_PRESERVE_FIXTURE" }
    }
    fun restore() {
        try {
            settle()
            assertExpected()
            locked { db ->
                db.beginTransaction()
                try {
                    check(rows(db, "SELECT * FROM metadata") == metadata)
                    val expected = (baseline.registrations + owned.values).sortedBy { it.reference.resourceId }.map(::values)
                    check(rows(db, "SELECT $COLUMNS FROM resources ORDER BY resource_id") == expected)
                    for (row in owned.values) {
                        check(db.delete("resources", COLUMNS.split(',').joinToString(" AND ") { "$it=?" },
                            values(row).map { it.toString() }.toTypedArray()) == 1)
                    }
                    check(rows(db, "SELECT $COLUMNS FROM resources ORDER BY resource_id") ==
                        baseline.registrations.sortedBy { it.reference.resourceId }.map(::values))
                    check(rows(db, "SELECT * FROM metadata") == metadata)
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            check(load().registrations == baseline.registrations)
            save("EXACT_RESOURCE_BASELINE_RESTORED")
        } catch (_: Throwable) {
            try { save("UNCERTAIN_PRESERVE_RESOURCE_FIXTURE") } catch (_: Throwable) { }
            throw AssertionError("LOCATIONS_CLEANUP_UNCONFIRMED")
        }
    }
    private fun load(): FilesResourceCatalog {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            when (val result = store.load()) {
                is FilesResourceResult.Success -> return result.value
                is FilesResourceResult.Failure -> check(result.code == FilesResourceCode.BUSY) { "LOCATIONS_STORE_UNAVAILABLE" }
            }
            SystemClock.sleep(15)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("LOCATIONS_STORE_BUSY")
    }
    private fun save(phase: String) {
        receipt.put("phase", phase).put("expectedOwnedRows", JSONArray(owned.values.map(::values)))
        val atomic = AtomicFile(receiptFile)
        val output = atomic.startWrite()
        try { output.write(receipt.toString(2).toByteArray()); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
        Os.chmod(receiptFile.path, 384)
    }
    private fun <T> locked(action: (SQLiteDatabase) -> T): T {
        val coordination = Class.forName("io.github.baitian6641.cloudy.platform.SshAuthenticationRegistry")
            .getDeclaredField("lock").apply { isAccessible = true }.get(null) as ReentrantLock
        val monitor = FilesResourceStore::class.java.getDeclaredField("monitor").apply { isAccessible = true }.get(null) as ReentrantLock
        check(coordination.tryLock())
        try {
            check(monitor.tryLock())
            try {
                val anchor = File(root, "files-resources-v1.lock")
                val marker = File(root, "files-resources-v1.instance")
                val directory = File(root, "files-resources-v1")
                val database = File(directory, "resources.db")
                for (file in listOf(root, anchor, marker, directory, database, File(database.path + "-journal"),
                        File(database.path + "-wal"), File(database.path + "-shm"))) {
                    check(!Files.isSymbolicLink(file.toPath()))
                    if (!file.exists()) {
                        check(file.name.endsWith("-journal") || file.name.endsWith("-wal") || file.name.endsWith("-shm"))
                        continue
                    }
                    val stat = Os.lstat(file.path)
                    check(stat.st_uid == Process.myUid())
                    check(if (file == root || file == directory) OsConstants.S_ISDIR(stat.st_mode) else OsConstants.S_ISREG(stat.st_mode))
                }
                check(anchor.length() == 36L && marker.length() == 36L && database.length() in 1..4L * 1024 * 1024)
                check(!File(database.path + "-wal").exists() && !File(database.path + "-shm").exists())
                FileChannel.open(anchor.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
                    val lock = checkNotNull(channel.tryLock())
                    lock.use {
                        val generation = anchor.readBytes()
                        check(generation.contentEquals(marker.readBytes()) && generation.toString(Charsets.US_ASCII) == baseline.generation)
                        val params = SQLiteDatabase.OpenParams.Builder().setOpenFlags(SQLiteDatabase.OPEN_READWRITE)
                            .setJournalMode("DELETE").setSynchronousMode("EXTRA")
                            .setErrorHandler(DatabaseErrorHandler { throw SQLiteDatabaseCorruptException("LOCATIONS_FIXTURE_UNAVAILABLE") }).build()
                        return SQLiteDatabase.openDatabase(database, params).use { db ->
                            check(db.rawQuery("PRAGMA quick_check(1)", null).use { it.moveToFirst() && it.getString(0) == "ok" })
                            val result = action(db)
                            check(generation.contentEquals(anchor.readBytes()) && generation.contentEquals(marker.readBytes()))
                            result
                        }
                    }
                }
            } finally { monitor.unlock() }
        } finally { coordination.unlock() }
    }
    companion object {
        const val NESTED_ROOT = "./nested"
        const val NESTED_NAME = "nested.txt"
        const val NESTED_TEXT = "Cloudy registered nested SFTP\n"
        private const val COLUMNS = "host_id,environment_id,workspace_id,resource_id,revision,state,owner,label,profile_generation," +
            "profile_id,profile_revision,profile_label,host,port,trust_generation,trust_revision,key_format,fingerprint,navigation_root,account_name"
        private fun values(row: FilesResourceRegistration): List<Any> {
            val key = (row.acceptedTrust.status as SshTrustStatus.Accepted).key
            val profile = row.profile.profile
            return listOf(row.reference.hostId, row.reference.environmentId, row.reference.workspaceId, row.reference.resourceId,
                row.revision, row.state.name, row.owner.wireId, row.label, row.profile.generation, profile.id, profile.revision,
                profile.label, profile.host, profile.port.toLong(), row.acceptedTrust.generation, row.acceptedTrust.revision,
                key.keyFormat, key.fingerprint, row.navigationRoot, row.accountName)
        }
        private fun rows(db: SQLiteDatabase, query: String): List<List<Any>> = db.rawQuery(query, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        else -> error("LOCATIONS_INVALID_DATABASE_TYPE")
                    }
                })
            }
        }
        fun settle() {
            check(Looper.myLooper() != Looper.getMainLooper())
            val executor = Class.forName("io.github.baitian6641.cloudy.platform.CloudyFilesLocationsActivity")
                .getDeclaredField("operations").apply { isAccessible = true }.get(null) as ThreadPoolExecutor
            repeat(2) {
                val worker = CountDownLatch(1)
                executor.execute { worker.countDown() }
                check(worker.await(20, TimeUnit.SECONDS))
                val main = CountDownLatch(1)
                check(Handler(Looper.getMainLooper()).post { main.countDown() })
                check(main.await(5, TimeUnit.SECONDS))
            }
            SshFilesFixture.settle()
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (executor.activeCount != 0 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
            check(executor.queue.isEmpty() && executor.activeCount == 0)
            val registry = Class.forName("io.github.baitian6641.cloudy.platform.SshAuthenticationRegistry")
                .getDeclaredField("attempts").apply { isAccessible = true }.get(null) as Set<*>
            check(registry.isEmpty()) { "LOCATIONS_AUTH_OWNERS_UNSETTLED" }
        }
    }
}
