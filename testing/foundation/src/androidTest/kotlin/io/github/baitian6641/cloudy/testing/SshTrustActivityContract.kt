package io.github.baitian6641.cloudy.testing

import android.content.ComponentName
import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Bundle
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustChallenge
import io.github.baitian6641.cloudy.domain.SshTrustEvaluation
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.CloudySshTrustActivity
import io.github.baitian6641.cloudy.platform.SshHostTrustStore
import io.github.baitian6641.cloudy.platform.SshTrustCatalog
import io.github.baitian6641.cloudy.platform.SshTrustStoreCode
import io.github.baitian6641.cloudy.platform.SshTrustStoreResult
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.ref.WeakReference
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real local trust metadata/UI, never a host connection, credential approval or process-death test. */
abstract class SshTrustActivityContract(private val expectedOwner: WindowOwner) {
    @get:Rule val compose = createAndroidComposeRule(CloudySshTrustActivity::class.java)
    private val run = UUID.randomUUID().toString()
    private val peer = SshPeerIdentity.create("ssh-ui-$run.invalid", 22022)
    private val queue by lazy { SshTrustTestQueue.actual() }
    private lateinit var context: Context
    private lateinit var baseline: SshTrustCatalog
    private lateinit var metadata: List<Any>
    private lateinit var receipt: AtomicFile
    private val expected = linkedMapOf<SshPeerIdentity, SshTrustSnapshot>()
    private val store get() = SshHostTrustStore(context)

    @Before fun establishActualOwnerAndCompleteBaseline() {
        compose.activityRule.scenario.onActivity { context = it.applicationContext }
        val packageName = "io.github.baitian6641.cloudy.${expectedOwner.wireId}"
        assertEquals(packageName, context.packageName)
        assertEquals(context.applicationInfo.uid, Process.myUid())
        assertFalse(context.isDeviceProtectedStorage)
        @Suppress("DEPRECATION")
        val activity = context.packageManager.getActivityInfo(ComponentName(context, CloudySshTrustActivity::class.java), 0)
        assertFalse(activity.exported)
        assertEquals(context.packageName, activity.packageName)
        assertEquals(Process.myUid(), activity.applicationInfo.uid)
        queue.drain()
        baseline = catalog()
        assertEquals(expectedOwner, baseline.owner)
        check(baseline.snapshots.none { it.peer == peer })
        lockedDatabase { db ->
            metadata = readMetadata(db)
            check(readSnapshots(db) == baseline.snapshots)
        }
        val directory = File(context.noBackupFilesDir, "ssh-trust-ui-qualification")
        check(!Files.isSymbolicLink(directory.toPath()) && (directory.isDirectory || directory.mkdir()))
        receipt = AtomicFile(File(directory, "$run.json"))
        check(listOf("", ".bak", ".new").none { File(receipt.baseFile.path + it).exists() })
        writeReceipt("BASELINE", null)
    }

    @After fun settleThenRemoveOnlyExactOwnedFixtureRows() {
        val closeFailure = try {
            runCatching { compose.activityRule.scenario.close() }.exceptionOrNull()
        } finally { queue.releaseGates() }
        check(closeFailure == null) { "Trust Activity closure unproved; preserve fixture and receipt" }
        queue.drain()
        check(::baseline.isInitialized && ::metadata.isInitialized && ::receipt.isInitialized) {
            "Trust baseline unproved; preserve fixture"
        }
        // The actual permanent file lock is acquired before opening or reading SQLite. Settled
        // callbacks are essential: this lock alone would not exclude a future queued UI write.
        lockedDatabase { db ->
            db.beginTransaction()
            try {
                check(readMetadata(db) == metadata)
                val before = readSnapshots(db)
                val completeExpected = baseline.snapshots + expected.values
                check(before.size == completeExpected.size && before.toSet() == completeExpected.toSet()) {
                    "Unexpected trust state; preserve fixture and receipt"
                }
                for (snapshot in expected.values) {
                    check(baseline.snapshots.none { it.peer == snapshot.peer })
                    check(db.delete("pins", EXACT_ROW, rowArguments(snapshot)) == 1)
                }
                check(readSnapshots(db) == baseline.snapshots && readMetadata(db) == metadata)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        val restored = catalog()
        check(restored.owner == baseline.owner && restored.generation == baseline.generation &&
            restored.snapshots == baseline.snapshots) { "Trust cleanup unconfirmed; preserve receipt" }
        receipt.delete()
        check(listOf("", ".bak", ".new").none { File(receipt.baseFile.path + it).exists() })
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudySshTrustFixtureRun", run)
            putString("cloudySshTrustFixtureCleanup", "EXACT_BASELINE_RESTORED")
        })
    }

    @Test fun exactPeerKeyStatusAndCancelledThenConfirmedRevokeRetainTombstone() {
        val original = seed()
        refresh()
        inspect(original)
        assertDetails(original)
        click("trust_revoke")
        compose.onNodeWithTag("trust_dialog").assertIsDisplayed()
        compose.onNodeWithTag("trust_cancel").performClick()
        queue.drain()
        assertEquals(original, current())
        inspect(original)
        click("trust_revoke")
        val revoked = revoked(original)
        writeReceipt("REVOKE_INTENT", revoked)
        compose.onNodeWithTag("trust_confirm").performClick()
        queue.drain()
        confirmExpected(revoked)
        assertMessage(io.github.baitian6641.cloudy.platform.R.string.ssh_trust_revoked_saved)
        refresh()
        inspect(revoked)
        assertDetails(revoked)
        compose.onNodeWithTag("trust_revoke").assertDoesNotExist()
    }

    @Test fun frozenRevokeApprovalCannotRevokeAConcurrentlyReplacedKey() {
        val original = seed()
        refresh()
        inspect(original)
        click("trust_revoke")
        compose.onNodeWithTag("trust_dialog").assertIsDisplayed()
        val newKey = key(1)
        val evaluation = successful { store.evaluate(peer, newKey) }
        check(evaluation is SshTrustEvaluation.Changed)
        val replacement = SshTrustSnapshot.create(expectedOwner, baseline.generation, peer, 2, SshTrustStatus.accepted(newKey))
        writeReceipt("REPLACEMENT_INTENT", replacement)
        val saved = successful { store.accept(SshTrustChallenge.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), evaluation)) }
        assertEquals(replacement, saved)
        confirmExpected(replacement)
        // Confirmation still refers to the originally displayed revision and fingerprint.
        compose.onNode(hasText(keyOf(original).fingerprint) and hasAnyAncestor(hasTestTag("trust_dialog")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("trust_confirm").performClick()
        queue.drain()
        assertMessage(io.github.baitian6641.cloudy.platform.R.string.ssh_trust_conflict)
        assertEquals(replacement, current())
        refresh()
        inspect(replacement)
        assertDetails(replacement)
    }

    @Test fun recreatingAnUnconfirmedDialogCancelsApprovalAndDoesNotRestoreSelection() {
        val original = seed()
        refresh()
        inspect(original)
        click("trust_revoke")
        compose.onNodeWithTag("trust_dialog").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        queue.drain()
        compose.onNodeWithTag("trust_list").performScrollToIndex(0)
        compose.onNodeWithTag("trust_dialog").assertDoesNotExist()
        compose.onNodeWithTag("trust_details").assertDoesNotExist()
        assertEquals(original, current())
        refresh()
        inspect(original)
        assertDetails(original)
    }

    @Test fun admittedRevocationCommitsOnceAcrossRecreationAndExplicitRefreshResolvesUncertainty() {
        val original = seed()
        refresh()
        inspect(original)
        click("trust_revoke")
        val revoked = revoked(original)
        writeReceipt("REVOKE_INTENT", revoked)
        val old = WeakReference(compose.activity)
        val gate = queue.gate()
        try {
            assertEquals(0, queue.queuedTasks)
            compose.onNodeWithTag("trust_confirm").performClick()
            assertEquals(1, queue.queuedTasks)
            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.onActivity { assertTrue(old.get() !== it) }
            gate.requireHeld()
            assertEquals(2, queue.queuedTasks)
        } finally { gate.close() }
        queue.drain()
        gate.requireNotExpired()
        confirmExpected(revoked)
        scrollTo("trust_unknown")
        compose.onNodeWithTag("trust_unknown").assertTextEquals(context.getString(
            io.github.baitian6641.cloudy.platform.R.string.ssh_trust_unknown))
        compose.onNodeWithTag("trust_details").assertDoesNotExist()
        refresh()
        compose.onNodeWithTag("trust_list").performScrollToIndex(0)
        compose.onNodeWithTag("trust_unknown").assertDoesNotExist()
        inspect(revoked)
        assertDetails(revoked)
        refresh()
        compose.activityRule.scenario.recreate()
        queue.drain()
        assertEquals(revoked, current())
    }

    private fun seed(): SshTrustSnapshot {
        val evaluation = successful { store.evaluate(peer, key()) }
        check(evaluation is SshTrustEvaluation.Unknown && evaluation.token.generation == baseline.generation)
        val planned = SshTrustSnapshot.create(expectedOwner, baseline.generation, peer, 1, SshTrustStatus.accepted(key()))
        writeReceipt("SEED_INTENT", planned)
        val saved = successful { store.accept(SshTrustChallenge.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), evaluation)) }
        assertEquals(planned, saved)
        confirmExpected(planned)
        return planned
    }

    private fun confirmExpected(planned: SshTrustSnapshot) {
        assertEquals(planned, current())
        expected[planned.peer] = planned
        writeReceipt("CONFIRMED", null)
    }

    private fun revoked(original: SshTrustSnapshot) = SshTrustSnapshot.create(expectedOwner, baseline.generation,
        original.peer, original.revision + 1, SshTrustStatus.revoked(keyOf(original)))
    private fun key(byte: Int = 0) = SshHostKeyIdentity.create("ssh-ed25519", "SHA256:" +
        Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { byte.toByte() }))
    private fun keyOf(snapshot: SshTrustSnapshot): SshHostKeyIdentity = when (val status = snapshot.status) {
        is SshTrustStatus.Accepted -> status.key
        is SshTrustStatus.Revoked -> status.lastKey
    }

    private fun refresh() { click("trust_refresh"); queue.drain() }
    private fun inspect(snapshot: SshTrustSnapshot) {
        val index = catalog().snapshots.indexOf(snapshot)
        check(index >= 0)
        val endpoint = context.getString(io.github.baitian6641.cloudy.platform.R.string.ssh_trust_endpoint,
            snapshot.peer.host, snapshot.peer.port)
        val row = hasTestTag("trust_row_$index") and hasAnyDescendant(hasText(endpoint))
        compose.onNodeWithTag("trust_list").performScrollToNode(row)
        compose.onNode(hasTestTag("trust_open_$index") and hasAnyAncestor(row)).performScrollTo().performClick()
    }

    private fun assertDetails(snapshot: SshTrustSnapshot) {
        val key = keyOf(snapshot)
        val fields = mapOf("trust_host" to snapshot.peer.host, "trust_port" to snapshot.peer.port.toString(),
            "trust_route" to context.getString(io.github.baitian6641.cloudy.platform.R.string.ssh_trust_route_direct), "trust_key_format" to key.keyFormat,
            "trust_fingerprint" to key.fingerprint, "trust_revision" to snapshot.revision.toString(),
            "trust_status" to context.getString(if (snapshot.status is SshTrustStatus.Accepted)
                io.github.baitian6641.cloudy.platform.R.string.ssh_trust_accepted
                else io.github.baitian6641.cloudy.platform.R.string.ssh_trust_revoked))
        for ((tag, value) in fields) { scrollTo(tag); compose.onNodeWithTag(tag).assertTextEquals(value) }
    }

    private fun assertMessage(resource: Int) {
        scrollTo("trust_message")
        compose.onNodeWithTag("trust_message").assertTextEquals(context.getString(resource))
    }
    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("trust_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun click(tag: String) { scrollTo(tag); compose.onNodeWithTag(tag).performClick() }
    private fun catalog(): SshTrustCatalog = successful { store.load() }
    private fun current(): SshTrustSnapshot = catalog().snapshots.single { it.peer == peer }

    private fun <T> successful(operation: () -> SshTrustStoreResult<T>): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        do {
            when (val result = operation()) {
                is SshTrustStoreResult.Success -> return result.value
                is SshTrustStoreResult.Failure -> {
                    check(result.code == SshTrustStoreCode.BUSY) { "Trust operation failed: ${result.code}; preserve fixture" }
                    Thread.yield()
                }
            }
        } while (System.nanoTime() < deadline)
        error("Trust store busy; preserve fixture")
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

    private fun writeReceipt(phase: String, planned: SshTrustSnapshot?) {
        fun encoded(snapshot: SshTrustSnapshot) = JSONObject().apply {
            put("host", snapshot.peer.host); put("port", snapshot.peer.port); put("route", snapshot.peer.route.wireId)
            put("revision", snapshot.revision.toString()); put("state", if (snapshot.status is SshTrustStatus.Accepted) "ACCEPTED" else "REVOKED")
            put("keyFormat", keyOf(snapshot).keyFormat); put("fingerprint", keyOf(snapshot).fingerprint)
        }
        val baselineBytes = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                for (value in metadata) output.writeUTF(value.toString())
                for (snapshot in baseline.snapshots) for (value in rowArguments(snapshot)) output.writeUTF(value)
            }
            bytes.toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(baselineBytes).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
        val bytes = JSONObject().apply {
            put("run", run); put("phase", phase); put("owner", expectedOwner.wireId); put("package", context.packageName)
            put("uid", Process.myUid()); put("generation", baseline.generation); put("baselineSha256", digest)
            put("confirmed", JSONArray(expected.values.map { encoded(it) }))
            put("planned", planned?.let { encoded(it) } ?: JSONObject.NULL)
        }.toString().toByteArray(Charsets.UTF_8)
        check(bytes.size <= 8192)
        for (suffix in listOf("", ".bak", ".new")) check(!Files.isSymbolicLink(File(receipt.baseFile.path + suffix).toPath()))
        val stream = receipt.startWrite()
        try { stream.write(bytes); stream.fd.sync(); receipt.finishWrite(stream) }
        catch (error: Exception) { receipt.failWrite(stream); throw error }
        check(receipt.openRead().use { it.readNBytes(8193) }.contentEquals(bytes))
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudySshTrustFixtureRun", run)
            putString("cloudySshTrustFixturePhase", phase)
        })
    }

    companion object {
        private const val EXACT_ROW = "host=? AND port=? AND route=? AND owner=? AND revision=? AND state=? AND key_format=? AND fingerprint=?"
    }
}
