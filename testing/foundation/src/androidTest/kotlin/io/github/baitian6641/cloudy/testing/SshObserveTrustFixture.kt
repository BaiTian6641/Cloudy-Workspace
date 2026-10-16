package io.github.baitian6641.cloudy.testing

import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Process
import android.system.Os
import android.system.OsConstants
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.SshHostTrustStore
import io.github.baitian6641.cloudy.platform.SshTrustCatalog
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

/** Test-only exact fixture removal. Caller MUST close Activity and settle worker/main callbacks first. */
internal class SshObserveTrustFixture(
    private val context: Context, private val expectedOwner: WindowOwner, private val baseline: SshTrustCatalog,
) {
    private val metadata: List<Any> = lockedDatabase { db ->
        check(readSnapshots(db) == baseline.snapshots)
        readMetadata(db)
    }

    fun baselineMetadata(): List<Any> = metadata.toList()

    fun removeOnlyExpected(expected: SshTrustSnapshot?) {
        check(expected == null || (expected.owner == expectedOwner && expected.generation == baseline.generation &&
            baseline.snapshots.none { it.peer == expected.peer }))
        lockedDatabase { db ->
            db.beginTransaction()
            try {
                check(readMetadata(db) == metadata)
                val before = readSnapshots(db)
                if (before != baseline.snapshots) {
                    val planned = checkNotNull(expected) { "Unexpected trust mutation; preserve fixture" }
                    val complete = baseline.snapshots + planned
                    check(before.size == complete.size && before.toSet() == complete.toSet()) {
                        "Unexpected trust row; preserve fixture and receipt"
                    }
                    check(db.delete("pins", EXACT_ROW, rowArguments(planned)) == 1)
                }
                check(readSnapshots(db) == baseline.snapshots && readMetadata(db) == metadata)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }

    private fun keyOf(snapshot: SshTrustSnapshot): SshHostKeyIdentity = when (val status = snapshot.status) {
        is SshTrustStatus.Accepted -> status.key
        is SshTrustStatus.Revoked -> status.lastKey
    }
    private fun <T> lockedDatabase(block: (SQLiteDatabase) -> T): T {
        check(context.packageName == "io.github.baitian6641.cloudy.${expectedOwner.wireId}" &&
            context.applicationInfo.uid == Process.myUid() && !context.isDeviceProtectedStorage)
        val root = context.noBackupFilesDir
        val anchor = File(root, "ssh-host-trust-v1.lock")
        val marker = File(root, "ssh-host-trust-v1.instance")
        val directory = File(root, "ssh-host-trust-v1")
        val database = File(directory, "trust.db")
        for (path in listOf(root, directory, anchor, marker, database, File(database.path + "-journal"),
                File(database.path + "-wal"), File(database.path + "-shm"))) {
            check(!Files.isSymbolicLink(path.toPath()))
            if (path.exists()) {
                val info = Os.lstat(path.path)
                check(info.st_uid == Process.myUid())
                check(if (path == root || path == directory) OsConstants.S_ISDIR(info.st_mode) else OsConstants.S_ISREG(info.st_mode))
            }
        }
        check(anchor.isFile && anchor.length() == 36L && marker.isFile && marker.length() == 36L)
        check(database.isFile && database.length() in 1..(4L * 1024 * 1024))
        check(!File(database.path + "-wal").exists() && !File(database.path + "-shm").exists())
        FileChannel.open(anchor.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val lock = channel.tryLock() ?: error("Trust fixture lock unavailable; preserve fixture")
            lock.use {
                val anchorBytes = anchor.readBytes()
                val markerBytes = marker.readBytes()
                check(anchorBytes.toString(Charsets.US_ASCII) == baseline.generation && anchorBytes.contentEquals(markerBytes))
                val parameters = SQLiteDatabase.OpenParams.Builder().setOpenFlags(SQLiteDatabase.OPEN_READWRITE)
                    .setJournalMode("DELETE").setSynchronousMode("EXTRA")
                    .setErrorHandler(DatabaseErrorHandler { throw SQLiteDatabaseCorruptException("TRUST_FIXTURE_UNAVAILABLE") }).build()
                val result = SQLiteDatabase.openDatabase(database, parameters).use { db ->
                    check(db.rawQuery("PRAGMA quick_check(1)", null).use { it.moveToFirst() && it.getString(0) == "ok" })
                    check(db.rawQuery("PRAGMA user_version", null).use { it.moveToFirst() && it.getLong(0) == 1L })
                    check(db.rawQuery("PRAGMA journal_mode", null).use { it.moveToFirst() && it.getString(0) == "delete" })
                    check(db.rawQuery("PRAGMA synchronous", null).use { it.moveToFirst() && it.getLong(0) == 3L })
                    for ((table, columns) in mapOf("metadata" to listOf("singleton", "version", "generation", "owner",
                            "owner_package", "owner_uid", "capacity"), "pins" to listOf("host", "port", "route", "owner",
                            "revision", "state", "key_format", "fingerprint"))) {
                        val actual = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                            buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                        }
                        check(actual == columns)
                    }
                    block(db)
                }
                check(anchorBytes.contentEquals(anchor.readBytes()) && markerBytes.contentEquals(marker.readBytes()))
                return result
            }
        }
    }

    private fun readMetadata(db: SQLiteDatabase): List<Any> = db.rawQuery(
        "SELECT singleton,version,generation,owner,owner_package,owner_uid,capacity FROM metadata", null,
    ).use { cursor ->
        check(cursor.count == 1 && cursor.moveToFirst())
        check(listOf(0, 1, 5, 6).all { cursor.getType(it) == Cursor.FIELD_TYPE_INTEGER })
        check(listOf(2, 3, 4).all { cursor.getType(it) == Cursor.FIELD_TYPE_STRING })
        val values = listOf(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3),
            cursor.getString(4), cursor.getLong(5), cursor.getLong(6))
        check(values == listOf(1L, 1L, baseline.generation, expectedOwner.wireId, context.packageName,
            Process.myUid().toLong(), SshHostTrustStore.MAX_RECORDS.toLong()))
        values
    }

    private fun readSnapshots(db: SQLiteDatabase): List<SshTrustSnapshot> = db.rawQuery(
        "SELECT host,port,route,owner,revision,state,key_format,fingerprint FROM pins ORDER BY host,port,route", null,
    ).use { cursor ->
        check(cursor.count <= SshHostTrustStore.MAX_RECORDS)
        val result = mutableListOf<SshTrustSnapshot>()
        while (cursor.moveToNext()) {
            check(listOf(0, 2, 3, 6, 7).all { cursor.getType(it) == Cursor.FIELD_TYPE_STRING })
            check(listOf(1, 4, 5).all { cursor.getType(it) == Cursor.FIELD_TYPE_INTEGER })
            check(cursor.getLong(1) in 1L..65535L && cursor.getString(2) == "direct" && cursor.getString(3) == expectedOwner.wireId)
            val identity = SshPeerIdentity.create(cursor.getString(0), cursor.getLong(1).toInt())
            check(identity.host == cursor.getString(0))
            val key = SshHostKeyIdentity.create(cursor.getString(6), cursor.getString(7))
            val status = when (cursor.getLong(5)) {
                0L -> SshTrustStatus.accepted(key)
                1L -> SshTrustStatus.revoked(key)
                else -> error("Invalid trust fixture state; preserve fixture")
            }
            result += SshTrustSnapshot.create(expectedOwner, baseline.generation, identity, cursor.getLong(4), status)
        }
        check(result.map { it.peer }.toSet().size == result.size)
        result
    }

    private fun rowArguments(snapshot: SshTrustSnapshot): Array<String> = arrayOf(snapshot.peer.host,
        snapshot.peer.port.toString(), snapshot.peer.route.wireId, snapshot.owner.wireId, snapshot.revision.toString(),
        if (snapshot.status is SshTrustStatus.Accepted) "0" else "1", keyOf(snapshot).keyFormat, keyOf(snapshot).fingerprint)

    companion object {
        private const val EXACT_ROW = "host=? AND port=? AND route=? AND owner=? AND revision=? AND state=? AND key_format=? AND fingerprint=?"
        internal fun removeRetainedHandoffPin(db: SQLiteDatabase, baseline: List<List<Any>>, owned: List<Any>) {
            val columns = FilesHandoffReceiverCheckpoint.COLUMNS[1]
            val before = FilesHandoffReceiverCheckpoint.rows(db, "SELECT $columns FROM pins ORDER BY host,port,route")
            check(owned.size == 8 && owned[2] == "direct" && owned[4] == 1L && owned[5] == 0L)
            check(owned !in baseline && before.size == baseline.size + 1 && before.toSet() == (baseline + listOf(owned)).toSet())
            check(db.delete("pins", columns.split(',').joinToString(" AND ") { "$it=?" }, owned.map { it.toString() }.toTypedArray()) == 1)
            check(FilesHandoffReceiverCheckpoint.rows(db, "SELECT $columns FROM pins ORDER BY host,port,route") == baseline)
        }
    }

}
