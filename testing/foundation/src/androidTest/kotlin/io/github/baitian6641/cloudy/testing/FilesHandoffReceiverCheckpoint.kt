package io.github.baitian6641.cloudy.testing

import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.*
import io.github.baitian6641.cloudy.platform.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONArray
import org.json.JSONObject

/** Test-only retained ownership. It never imports current rows as the original baseline. */
internal class FilesHandoffReceiverCheckpoint private constructor(
    private val context: Context, private val owner: WindowOwner, private val run: String,
    private val direction: String, private val baseline: List<Table>,
    private val retained: List<Table>?, private val version: String, private val code: Long,
) {
    private data class Table(val generation: String, val metadata: List<List<Any>>, val rows: List<List<Any>>)
    private var journal: File? = null
    private val file get() = File(context.noBackupFilesDir, "ssh-files-qualification/$run-${owner.wireId}-handoff-checkpoint-v2.json")

    /** Only the live PREPARE object can publish; row authority comes from actual UI owners. */
    fun seal(ssh: SshFilesFixture, resources: FilesLocationsFixture): String {
        check(retained == null)
        FilesLocationsFixture.settle()
        val expected = ssh.retainedHandoffRows() + listOf(resources.retainedHandoffRow())
        val current = locked { databases -> readAll(databases) }
        validateDelta(baseline, current, expected)
        validateOwnedLabels(current)
        // Correlates the completed preparation snapshot within the same boot only. Parent
        // separately proves boot continuity; elapsed time grants no authentication lifetime.
        val preparedAt = SystemClock.elapsedRealtime().also { check(it > 0) }
        val payload = JSONObject().put("schema", 2).put("case", "handoff-receiver")
            .put("preparedAtElapsedMillis", preparedAt).put("run", run).put("direction", direction)
            .put("owner", owner.wireId).put("package", context.packageName).put("uid", Process.myUid())
            .put("version", version).put("code", code).put("baseline", tablesJson(baseline))
            .put("retained", tablesJson(current))
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        check(bytes.size in 1..MAX_BYTES)
        requirePrivateDirectory(file.parentFile!!)
        check(listOf("", ".bak", ".new").none { File(file.path + it).exists() || Files.isSymbolicLink(File(file.path + it).toPath()) })
        // A partial export remains visible and is never overwritten or treated as cleanup authority.
        FileChannel.open(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            Os.chmod(file.path, 384)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) check(channel.write(buffer) > 0)
            channel.force(true)
        }
        FileChannel.open(file.parentFile!!.toPath(), StandardOpenOption.READ).use { it.force(true) }
        return digest(bytes)
    }

    private fun validateOwnedLabels(tables: List<Table>) {
        val profile = tables[0].rows.single { it !in baseline[0].rows }
        val resource = tables[2].rows.single { it !in baseline[2].rows }
        check(profile[2] == owner.wireId && profile[3] == "Files ${owner.wireId} $run handoff-receiver")
        check(resource[6] == owner.wireId && resource[7] == "Location $run A")
    }

    fun verify() {
        FilesLocationsFixture.settle()
        locked { databases -> check(readAll(databases) == checkNotNull(retained)) { "RETAINED_STATE_CHANGED" } }
        startJournal("verify")
        status("VERIFIED_RETAINED")
    }

    fun cleanup() {
        FilesLocationsFixture.settle()
        locked { databases ->
            val expected = checkNotNull(retained)
            // All THREE stores are checked under coordination, monitors and permanent file locks
            // before the first deletion. A partial prior cleanup is deliberately refused.
            check(readAll(databases) == expected) { "RETAINED_STATE_CHANGED" }
            startJournal("cleanup")
            val owned = expected.indices.map { index -> expected[index].rows.single { it !in baseline[index].rows } }
            for (index in listOf(2, 1, 0)) {
                status("REMOVAL_PLANNED_$index")
                val db = databases[index]
                db.beginTransaction()
                try {
                    check(read(index, db) == expected[index])
                    when (index) {
                        2 -> FilesLocationsFixture.removeRetainedHandoffRow(db, baseline[index].rows, owned[index])
                        1 -> SshObserveTrustFixture.removeRetainedHandoffPin(db, baseline[index].rows, owned[index])
                        0 -> SshFilesFixture.removeRetainedHandoffProfile(db, baseline[index].rows, owned[index])
                    }
                    check(read(index, db) == baseline[index])
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                // Commit failure or interrupted receipt preserves the checkpoint and remaining rows.
                status("REMOVAL_CONFIRMED_$index")
            }
            check(readAll(databases) == baseline)
        }
        status("CLEANED_EXACT")
    }

    private fun startJournal(operation: String) {
        check(journal == null)
        val target = File(file.path + ".$operation.receipt")
        requirePrivateDirectory(target.parentFile!!)
        check(listOf("", ".bak", ".new").none { File(target.path + it).exists() || Files.isSymbolicLink(File(target.path + it).toPath()) })
        FileChannel.open(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            Os.chmod(target.path, 384)
            val bytes = JSONObject().put("schema", 1).put("run", run).put("package", context.packageName)
                .put("uid", Process.myUid()).put("phase", "STARTED").toString().toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) check(channel.write(buffer) > 0)
            channel.force(true)
        }
        journal = target
    }

    private fun status(phase: String) {
        // Separate mutable step receipt leaves the immutable cleanup authority unchanged.
        // On interruption, the last planned/confirmed state stays for parent recovery.
        journal?.let { target ->
            check(!Files.isSymbolicLink(target.toPath()))
            val atomic = AtomicFile(target)
            val output = atomic.startWrite()
            try {
                output.write(JSONObject().put("schema", 1).put("run", run).put("package", context.packageName)
                    .put("uid", Process.myUid()).put("phase", phase).toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output); Os.chmod(target.path, 384)
            } catch (error: Throwable) { atomic.failWrite(output); throw error }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudyFilesHandoffReceiverPhase", phase)
            putString("cloudyFilesHandoffRun", run)
            putString("cloudyFilesHandoffReceiverPackage", context.packageName)
        })
    }

    private fun readAll(databases: List<SQLiteDatabase>) = databases.mapIndexed(::read)
    private fun read(index: Int, db: SQLiteDatabase): Table {
        check(db.rawQuery("PRAGMA quick_check(1)", null).use { it.moveToFirst() && it.getString(0) == "ok" })
        check(db.rawQuery("PRAGMA user_version", null).use { it.moveToFirst() && it.getLong(0) == 1L })
        check(db.rawQuery("PRAGMA journal_mode", null).use { it.moveToFirst() && it.getString(0) == "delete" })
        check(db.rawQuery("PRAGMA synchronous", null).use { it.moveToFirst() && it.getLong(0) == 3L })
        val metadataColumns = "singleton,version,generation,owner,owner_package," + if (index == 0) "capacity" else "owner_uid,capacity"
        for ((table, columns) in listOf("metadata" to metadataColumns, TABLES[index] to COLUMNS[index])) {
            check(db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            } == columns.split(','))
        }
        val metadata = rows(db, "SELECT $metadataColumns FROM metadata")
        check(metadata.size == 1)
        val m = metadata.single()
        check(m.size == if (index == 0) 6 else 7)
        check(m[0] == 1L && m[1] == 1L && m[2] is String && UUID.matches(m[2] as String))
        check(m[3] == owner.wireId && m[4] == context.packageName)
        if (index != 0) check(m[5] == Process.myUid().toLong())
        check(m.last() == CAPACITIES[index].toLong())
        return Table(m[2] as String, metadata, rows(db, "SELECT ${COLUMNS[index]} FROM ${TABLES[index]} ORDER BY ${ORDERS[index]}"))
            .also { validateTable(index, it, owner, context.packageName, Process.myUid().toLong()) }
    }

    private fun <T> locked(action: (List<SQLiteDatabase>) -> T): T {
        val coordination = Class.forName("io.github.baitian6641.cloudy.platform.SshAuthenticationRegistry")
            .getDeclaredField("lock").apply { isAccessible = true }.get(null) as ReentrantLock
        check(coordination.tryLock())
        val monitors = mutableListOf<ReentrantLock>()
        val channels = mutableListOf<FileChannel>()
        val locks = mutableListOf<java.nio.channels.FileLock>()
        val databases = mutableListOf<SQLiteDatabase>()
        try {
            for (index in TABLES.indices) {
                val monitor = Class.forName("io.github.baitian6641.cloudy.platform.${CLASSES[index]}")
                    .getDeclaredField("monitor").apply { isAccessible = true }.get(null) as ReentrantLock
                check(monitor.tryLock()); monitors.add(monitor)
                val root = context.noBackupFilesDir
                val directory = File(root, DIRECTORIES[index])
                val anchor = File(root, DIRECTORIES[index] + ".lock")
                val marker = File(root, DIRECTORIES[index] + ".instance")
                val database = File(directory, DATABASES[index])
                for (path in listOf(root, directory, anchor, marker, database, File(database.path + "-journal"),
                                    File(database.path + "-wal"), File(database.path + "-shm"))) {
                    check(!Files.isSymbolicLink(path.toPath()))
                    if (!path.exists()) {
                        check(path.name.endsWith("-journal") || path.name.endsWith("-wal") || path.name.endsWith("-shm")); continue
                    }
                    val stat = Os.lstat(path.path)
                    check(stat.st_uid == Process.myUid())
                    check(if (path == root || path == directory) OsConstants.S_ISDIR(stat.st_mode) else OsConstants.S_ISREG(stat.st_mode))
                }
                check(anchor.length() == 36L && marker.length() == 36L && database.length() in 1..4L * 1024 * 1024)
                check(!File(database.path + "-wal").exists() && !File(database.path + "-shm").exists() && !File(database.path + "-journal").exists())
                val channel = FileChannel.open(anchor.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
                channels.add(channel); locks.add(checkNotNull(channel.tryLock()))
                check(anchor.readBytes().contentEquals(marker.readBytes()))
                val expectedGeneration = baseline.getOrNull(index)?.generation
                if (expectedGeneration != null) check(marker.readText(Charsets.US_ASCII) == expectedGeneration)
                val params = SQLiteDatabase.OpenParams.Builder().setOpenFlags(SQLiteDatabase.OPEN_READWRITE)
                    .setJournalMode("DELETE").setSynchronousMode("EXTRA")
                    .setErrorHandler(DatabaseErrorHandler { throw SQLiteDatabaseCorruptException("HANDOFF_FIXTURE_UNAVAILABLE") }).build()
                databases.add(SQLiteDatabase.openDatabase(database, params))
            }
            val result = action(databases)
            for (index in TABLES.indices) {
                val root = context.noBackupFilesDir
                val anchor = File(root, DIRECTORIES[index] + ".lock")
                val marker = File(root, DIRECTORIES[index] + ".instance")
                check(anchor.readBytes().contentEquals(marker.readBytes()))
                check(marker.readText(Charsets.US_ASCII) == read(index, databases[index]).generation)
            }
            return result
        } finally {
            // Attempt every release even if a preceding close fails; no lock survives a
            // local exception merely because an unrelated SQLite close was unsuccessful.
            var cleanupFailure: Throwable? = null
            fun release(action: () -> Unit) { try { action() } catch (error: Throwable) { if (cleanupFailure == null) cleanupFailure = error } }
            databases.asReversed().forEach { release { it.close() } }
            locks.asReversed().forEach { release { it.release() } }
            channels.asReversed().forEach { release { it.close() } }
            monitors.asReversed().forEach { release { it.unlock() } }
            release { coordination.unlock() }
            cleanupFailure?.let { throw it }
        }
    }

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024
        private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        private val TABLES = listOf("profiles", "pins", "resources")
        private val DIRECTORIES = listOf("connection-profiles-v1", "ssh-host-trust-v1", "files-resources-v1")
        private val DATABASES = listOf("profiles.db", "trust.db", "resources.db")
        private val CLASSES = listOf("ConnectionProfiles", "SshHostTrustStore", "FilesResourceStore")
        private val CAPACITIES = listOf(ConnectionProfiles.MAX_RECORDS, SshHostTrustStore.MAX_RECORDS, FilesResourceStore.MAX_RECORDS)
        private val ORDERS = listOf("id", "host,port,route", "resource_id")
        internal val COLUMNS = listOf("id,revision,owner,label,protocol,host,port",
            "host,port,route,owner,revision,state,key_format,fingerprint",
            "host_id,environment_id,workspace_id,resource_id,revision,state,owner,label,profile_generation,profile_id,profile_revision,profile_label,host,port,trust_generation,trust_revision,key_format,fingerprint,navigation_root,account_name")

        fun begin(context: Context, owner: WindowOwner, run: String, direction: String): FilesHandoffReceiverCheckpoint {
            identity(context, owner, run, direction)
            val version = version(context)
            val empty = FilesHandoffReceiverCheckpoint(context, owner, run, direction, emptyList(), null, version.first, version.second)
            check(listOf("", ".bak", ".new").none { File(empty.file.path + it).exists() || Files.isSymbolicLink(File(empty.file.path + it).toPath()) })
            // Existing stores must satisfy their public model validation; never initialize a
            // missing store just to create a baseline. locked() first proves all files exist.
            val baseline = empty.locked { empty.readAll(it) }
            check(ConnectionProfiles(context).load() is ConnectionProfilesResult.Success)
            check(SshHostTrustStore(context).load() is SshTrustStoreResult.Success)
            check(FilesResourceStore(context).load() is FilesResourceResult.Success)
            check(empty.locked { empty.readAll(it) } == baseline)
            return FilesHandoffReceiverCheckpoint(context, owner, run, direction, baseline, null, version.first, version.second)
        }

        fun resume(context: Context, owner: WindowOwner, run: String, direction: String, hash: String): FilesHandoffReceiverCheckpoint {
            identity(context, owner, run, direction)
            check(Regex("[0-9a-f]{64}").matches(hash))
            val v = version(context)
            val candidate = FilesHandoffReceiverCheckpoint(context, owner, run, direction, emptyList(), null, v.first, v.second)
            requirePrivateDirectory(candidate.file.parentFile!!)
            check(listOf(".bak", ".new").none { File(candidate.file.path + it).exists() || Files.isSymbolicLink(File(candidate.file.path + it).toPath()) })
            val stat = Os.lstat(candidate.file.path)
            check(!Files.isSymbolicLink(candidate.file.toPath()) && OsConstants.S_ISREG(stat.st_mode) &&
                stat.st_uid == Process.myUid() && stat.st_mode and 511 == 384 && stat.st_size in 1..MAX_BYTES.toLong())
            val bytes = FileChannel.open(candidate.file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
                check(channel.size() == stat.st_size)
                val buffer = ByteBuffer.allocate(stat.st_size.toInt())
                while (buffer.hasRemaining()) check(channel.read(buffer) > 0)
                check(channel.read(ByteBuffer.allocate(1)) == -1)
                buffer.array()
            }
            check(digest(bytes) == hash)
            val value = RetainedCheckpointJson.decode(bytes)
            RetainedCheckpointMetadata.validate(value, run, direction, owner.wireId, context.packageName,
                Process.myUid().toLong(), v.first, v.second, SystemClock.elapsedRealtime())
            val baseline = decodeTables(value["baseline"])
            val retained = decodeTables(value["retained"])
            for (tables in listOf(baseline, retained)) tables.forEachIndexed { index, table ->
                validateTable(index, table, owner, context.packageName, Process.myUid().toLong())
            }
            validateDelta(baseline, retained, retained.indices.map { index -> retained[index].rows.single { it !in baseline[index].rows } })
            return FilesHandoffReceiverCheckpoint(context, owner, run, direction, baseline, retained, v.first, v.second).also { resumed ->
                resumed.validateOwnedLabels(retained)
                resumed.locked { databases -> check(resumed.readAll(databases) == retained) { "RETAINED_STATE_CHANGED" } }
            }
        }

        private fun identity(context: Context, owner: WindowOwner, run: String, direction: String) {
            check(UUID.matches(run) && !context.isDeviceProtectedStorage && context.packageName == "io.github.baitian6641.cloudy.${owner.wireId}" &&
                context.applicationInfo.uid == Process.myUid() && android.app.Application.getProcessName() == context.packageName)
            check(direction == if (owner == WindowOwner.CONSOLE) "desktop-to-console" else "console-to-desktop")
            check(InstrumentationRegistry.getArguments().getString("cloudySshFilesRun") == run)
        }
        private fun version(context: Context): Pair<String, Long> = context.packageManager.getPackageInfo(context.packageName, 0)
            .let { checkNotNull(it.versionName) to it.longVersionCode }
        private fun requirePrivateDirectory(directory: File) {
            check(!Files.isSymbolicLink(directory.toPath()))
            val stat = Os.lstat(directory.path)
            check(OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid == Process.myUid() && stat.st_mode and 511 == 448)
        }
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        internal fun rows(db: SQLiteDatabase, query: String): List<List<Any>> = db.rawQuery(query, null).use { cursor ->
            check(cursor.count <= 512)
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                when (cursor.getType(index)) {
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(index).also { check(it.length <= 4096) }
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                    else -> error("CHECKPOINT_SQL_TYPE")
                }
            }) }
        }
        private fun tablesJson(tables: List<Table>) = JSONArray(tables.map { table -> JSONObject()
            .put("generation", table.generation).put("metadata", JSONArray(table.metadata)).put("rows", JSONArray(table.rows)) })
        private fun decodeTables(value: Any?): List<Table> {
            check(value is List<*> && value.size == 3)
            return value.mapIndexed { index, raw ->
                check(raw is Map<*, *> && raw.keys == setOf("generation", "metadata", "rows"))
                val generation = raw["generation"]; check(generation is String && UUID.matches(generation))
                fun matrix(value: Any?, width: Int, maximum: Int): List<List<Any>> {
                    check(value is List<*> && value.size <= maximum)
                    return value.map { row -> check(row is List<*> && row.size == width)
                        row.map { field -> check(field is String || field is Long); check(field !is String || field.length <= 4096); field } }
                }
                Table(generation, matrix(raw["metadata"], if (index == 0) 6 else 7, 1),
                    matrix(raw["rows"], COLUMNS[index].split(',').size, CAPACITIES[index]))
            }
        }
        /** Raw SQL DTOs preserve exact cells, but import still uses every public domain
         * factory/canonical comparison used by the product readers. Column order is pinned. */
        private fun validateTable(index: Int, table: Table, owner: WindowOwner, packageName: String, uid: Long) {
            val metadata = table.metadata.single()
            check(metadata.size == if (index == 0) 6 else 7)
            check(metadata == if (index == 0) listOf(1L, 1L, table.generation, owner.wireId, packageName, CAPACITIES[index].toLong())
                else listOf(1L, 1L, table.generation, owner.wireId, packageName, uid, CAPACITIES[index].toLong()))
            check(table.rows.size <= CAPACITIES[index])
            val identities = mutableSetOf<String>()
            for (row in table.rows) {
                val integers = when (index) { 0 -> setOf(1, 6); 1 -> setOf(1, 4, 5); else -> setOf(4, 10, 13, 15) }
                check(row.size == COLUMNS[index].split(',').size)
                row.forEachIndexed { column, field -> check(if (column in integers) field is Long else field is String) }
                fun string(column: Int) = (row[column] as String).also { check(it.length <= 4096) }
                fun number(column: Int) = row[column] as Long
                fun port(column: Int) = number(column).also { check(it in 1L..65535L) }.toInt()
                when (index) {
                    0 -> {
                        check(string(2) == owner.wireId && identities.add(string(0)))
                        val protocol = ConnectionProtocol.entries.single { it.wireId == string(4) }
                        val profile = ConnectionProfile.restore(string(0), number(1), ConnectionProfileDraft.create(owner,
                            string(3), protocol, string(5), port(6)))
                        check(listOf(profile.id, profile.revision, profile.owner.wireId, profile.label,
                            profile.protocol.wireId, profile.host, profile.port.toLong()) == row)
                    }
                    1 -> {
                        check(string(2) == "direct" && string(3) == owner.wireId)
                        val peer = SshPeerIdentity.create(string(0), port(1))
                        check(identities.add("${peer.host}/${peer.port}/${peer.route.wireId}"))
                        val key = SshHostKeyIdentity.create(string(6), string(7))
                        val status = when (number(5)) { 0L -> SshTrustStatus.accepted(key); 1L -> SshTrustStatus.revoked(key); else -> error("CHECKPOINT_TRUST_STATE") }
                        val pin = SshTrustSnapshot.create(owner, table.generation, peer, number(4), status)
                        check(listOf(pin.peer.host, pin.peer.port.toLong(), pin.peer.route.wireId, pin.owner.wireId,
                            pin.revision, number(5), key.keyFormat, key.fingerprint) == row)
                    }
                    else -> {
                        check(string(6) == owner.wireId)
                        val reference = FilesResourceReference.restore(string(0), string(1), string(2), string(3))
                        check(listOf(reference.hostId, reference.environmentId, reference.workspaceId, reference.resourceId).all { identities.add(it) })
                        val profile = ConnectionProfile.restore(string(9), number(10), ConnectionProfileDraft.create(owner,
                            string(11), ConnectionProtocol.SSH, string(12), port(13)))
                        val observed = SshObservationProfile.create(string(8), profile)
                        val key = SshHostKeyIdentity.create(string(16), string(17))
                        val pin = SshTrustSnapshot.create(owner, string(14), observed.peer, number(15), SshTrustStatus.accepted(key))
                        val saved = FilesResourceRegistration.restore(table.generation, reference, number(4), FilesResourceState.valueOf(string(5)),
                            FilesResourceDraft.create(owner, string(7), observed, pin, string(18), string(19)))
                        check(listOf(reference.hostId, reference.environmentId, reference.workspaceId, reference.resourceId,
                            saved.revision, saved.state.name, saved.owner.wireId, saved.label, saved.profile.generation,
                            profile.id, profile.revision, profile.label, profile.host, profile.port.toLong(), pin.generation,
                            pin.revision, key.keyFormat, key.fingerprint, saved.navigationRoot, saved.accountName) == row)
                    }
                }
            }
        }

        private fun validateDelta(before: List<Table>, after: List<Table>, owned: List<List<Any>>) {
            check(before.size == 3 && after.size == 3 && owned.size == 3)
            for (index in before.indices) {
                check(before[index].generation == after[index].generation && before[index].metadata == after[index].metadata)
                check(before[index].rows.distinct().size == before[index].rows.size && after[index].rows.distinct().size == after[index].rows.size)
                check(owned[index] !in before[index].rows && after[index].rows.size == before[index].rows.size + 1 &&
                    after[index].rows.toSet() == (before[index].rows + listOf(owned[index])).toSet())
            }
            val p = owned[0]; val t = owned[1]; val r = owned[2]
            check(p[6] is Long && p[6] as Long in 1L..65535L)
            check(p[1] == 1L && p[4] == "ssh" && p[5] == "127.0.0.1" && t[2] == "direct" && t[4] == 1L && t[5] == 0L)
            check(r[4] == 2L && r[5] == "SHARED" && r[18] == FilesLocationsFixture.NESTED_ROOT)
            check(p[2] == before[0].metadata.single()[3])
            check(p[2] == t[3] && p[2] == r[6] && p[5] == t[0] && p[6] == t[1])
            check(r[8] == before[0].generation && r[9] == p[0] && r[10] == p[1] && r[11] == p[3] && r[12] == p[5] && r[13] == p[6])
            check(r[14] == before[1].generation && r[15] == t[4] && r[16] == t[6] && r[17] == t[7])
            val owner = WindowOwner.entries.single { it.wireId == p[2] }
            val profile = ConnectionProfile.restore(p[0] as String, p[1] as Long, ConnectionProfileDraft.create(owner,
                p[3] as String, ConnectionProtocol.SSH, p[5] as String, (p[6] as Long).toInt()))
            val pin = SshTrustSnapshot.create(owner, before[1].generation, SshPeerIdentity.create(t[0] as String, (t[1] as Long).toInt()),
                t[4] as Long, SshTrustStatus.accepted(SshHostKeyIdentity.create(t[6] as String, t[7] as String)))
            FilesResourceRegistration.restore(before[2].generation, FilesResourceReference.restore(r[0] as String, r[1] as String, r[2] as String, r[3] as String),
                r[4] as Long, FilesResourceState.SHARED, FilesResourceDraft.create(owner, r[7] as String,
                    SshObservationProfile.create(before[0].generation, profile), pin, r[18] as String, r[19] as String))
        }
    }
}

/** Small strict JSON reader: bounded nesting, duplicate keys, exact integers and strict UTF8. */
internal object RetainedCheckpointJson {
    fun decode(bytes: ByteArray): Map<String, Any?> {
        check(bytes.size in 1..8 * 1024 * 1024)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val reader = Reader(text)
        val value = reader.value(0); reader.space(); check(reader.position == text.length && value is Map<*, *>)
        @Suppress("UNCHECKED_CAST") return value as Map<String, Any?>
    }
    private class Reader(private val text: String) {
        var position = 0
        private var valuesRead = 0
        fun space() { while (position < text.length && text[position] in " \n\r\t") position++ }
        fun value(depth: Int): Any? {
            check(depth <= 16 && ++valuesRead <= 65_536); space(); check(position < text.length)
            return when (text[position]) {
                '{' -> {
                    position++; space(); val result = linkedMapOf<String, Any?>()
                    if (take('}')) result else {
                        do {
                            space(); val key = string(); check(key !in result && result.size < 512)
                            space(); check(take(':')); result[key] = value(depth + 1); space()
                        } while (take(','))
                        check(take('}')); result
                    }
                }
                '[' -> {
                    position++; space(); val result = mutableListOf<Any?>()
                    if (take(']')) result else {
                        do { check(result.size < 512); result.add(value(depth + 1)); space() } while (take(','))
                        check(take(']')); result
                    }
                }
                '"' -> string()
                else -> {
                    val start = position
                    if (take('-')) check(position < text.length)
                    if (!take('0')) { check(position < text.length && text[position] in '1'..'9'); while (position < text.length && text[position] in '0'..'9') position++ }
                    text.substring(start, position).toLong()
                }
            }
        }
        private fun take(char: Char): Boolean = if (position < text.length && text[position] == char) { position++; true } else false
        private fun string(): String {
            check(take('"'))
            val result = StringBuilder()
            while (position < text.length && text[position] != '"') {
                val char = text[position++]; check(char >= ' ')
                if (char != '\\') result.append(char) else {
                    check(position < text.length)
                    when (val escape = text[position++]) {
                        '"', '\\', '/' -> result.append(escape)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            check(position + 4 <= text.length && text.substring(position, position + 4).all { it in "0123456789abcdefABCDEF" })
                            result.append(text.substring(position, position + 4).toInt(16).toChar()); position += 4
                        }
                        else -> error("CHECKPOINT_STRING_ESCAPE")
                    }
                }
                check(result.length <= 4096)
            }
            check(take('"'))
            var i = 0
            while (i < result.length) {
                val char = result[i++]
                check(!Character.isLowSurrogate(char))
                if (Character.isHighSurrogate(char)) check(i < result.length && Character.isLowSurrogate(result[i++]))
            }
            return result.toString()
        }
    }
}

/** Pure fixed identity/time contract, shared by import and local negative-case tests. */
internal object RetainedCheckpointMetadata {
    fun validate(value: Map<String, Any?>, run: String, direction: String, owner: String,
                 packageName: String, uid: Long, version: String, code: Long, nowElapsedMillis: Long) {
        check(value.keys == setOf("schema", "case", "preparedAtElapsedMillis", "run", "direction", "owner",
            "package", "uid", "version", "code", "baseline", "retained"))
        check(value["schema"] == 2L && value["case"] == "handoff-receiver" && value["run"] == run &&
            value["direction"] == direction && value["owner"] == owner && value["package"] == packageName &&
            value["uid"] == uid && value["version"] == version && value["code"] == code)
        val preparedAt = value["preparedAtElapsedMillis"]
        // No TTL or reboot inference: this rejects impossible/future local correlation only.
        check(preparedAt is Long && preparedAt > 0 && preparedAt <= nowElapsedMillis)
    }
}
