package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema v1/v2 to v3 migration against hand-built stores. Runs on a real Android SQLite stack;
 * hosted CI compiles this without a device. Fixture roots never confer product identity.
 */
@RunWith(AndroidJUnit4::class)
class ConnectionProfilesMigrationTest {
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(application.noBackupFilesDir, "profiles-migration-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val directory get() = File(root, "connection-profiles-v1")
    private val database get() = File(directory, "profiles.db")
    private val marker get() = File(root, "connection-profiles-v1.instance")
    private val anchor get() = File(root, "connection-profiles-v1.lock")
    private val ownerPackage = "io.github.baitian6641.cloudy.console"
    private val generation = UUID.randomUUID().toString()
    private val legacyId = UUID.randomUUID().toString()

    private fun store() = ConnectionProfiles(root, WindowOwner.CONSOLE, ownerPackage, { false }, 512)

    private fun <T> success(result: ConnectionProfilesResult<T>): T = when (result) {
        is ConnectionProfilesResult.Success -> result.value
        is ConnectionProfilesResult.Failure -> throw AssertionError("Expected success: ${result.code}")
    }

    private fun denied(code: ConnectionProfilesCode, result: ConnectionProfilesResult<*>) {
        assertTrue(result is ConnectionProfilesResult.Failure)
        assertEquals(code, (result as ConnectionProfilesResult.Failure).code)
    }

    /** Rebuilds the exact v1 on-disk layout the previous schema wrote, with one real row. */
    private fun writeV1Store() {
        anchor.writeText(generation, Charsets.US_ASCII)
        marker.writeText(generation, Charsets.US_ASCII)
        check(directory.mkdir())
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            db.execSQL("CREATE TABLE metadata (singleton INTEGER PRIMARY KEY CHECK(singleton=1), " +
                "version INTEGER NOT NULL, generation TEXT NOT NULL, owner TEXT NOT NULL, " +
                "owner_package TEXT NOT NULL, capacity INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE profiles (id TEXT PRIMARY KEY NOT NULL, revision INTEGER NOT NULL, " +
                "owner TEXT NOT NULL, label TEXT NOT NULL, protocol TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL)")
            db.execSQL("INSERT INTO metadata VALUES (1, 1, ?, ?, ?, ?)",
                arrayOf<Any>(generation, WindowOwner.CONSOLE.wireId, ownerPackage, 512))
            db.execSQL("INSERT INTO profiles VALUES (?, 3, ?, 'Legacy host', 'ssh', 'legacy.example', 22)",
                arrayOf<Any>(legacyId, WindowOwner.CONSOLE.wireId))
            db.execSQL("PRAGMA user_version=1")
        }
    }

    /** Rebuilds the exact v2 layout (organization metadata, no jump reference). */
    private fun writeV2Store() {
        writeV1Store()
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE profiles ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE profiles ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profiles ADD COLUMN last_used INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE profiles SET tags='work', favorite=1, last_used=1782200000")
            db.execSQL("UPDATE metadata SET version=2")
            db.execSQL("PRAGMA user_version=2")
        }
    }

    /** v3 adds the jump reference but not the v4 key reference. */
    private fun writeV3Store() {
        writeV2Store()
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE profiles ADD COLUMN jump_profile_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE metadata SET version=3")
            db.execSQL("PRAGMA user_version=3")
        }
    }

    @After fun cleanup() { check(root.deleteRecursively()) }

    @Test fun v3RowsSurviveV4MigrationWithNoKeyReference() {
        writeV3Store()
        val migrated = success(store().load()).profiles.single()
        assertEquals(linkedSetOf("work"), migrated.tags)
        assertEquals(true, migrated.favorite)
        assertEquals(1_782_200_000L, migrated.lastUsedAt)
        assertEquals(null, migrated.jumpProfileId)
        assertEquals(null, migrated.keyUri)
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { check(it.moveToFirst()); assertEquals(4L, it.getLong(0)) }
            db.rawQuery("SELECT version FROM metadata", null).use { check(it.moveToFirst()); assertEquals(4L, it.getLong(0)) }
        }
    }

    @Test fun v2RowsSurviveLaterMigrationsWithNoJumpOrKeyReference() {
        writeV2Store()
        val migrated = success(store().load()).profiles.single()
        assertEquals(linkedSetOf("work"), migrated.tags)
        assertEquals(true, migrated.favorite)
        assertEquals(1_782_200_000L, migrated.lastUsedAt)
        assertEquals(null, migrated.jumpProfileId)
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { check(it.moveToFirst()); assertEquals(4L, it.getLong(0)) }
            db.rawQuery("SELECT version FROM metadata", null).use { check(it.moveToFirst()); assertEquals(4L, it.getLong(0)) }
        }
    }

    @Test fun v1RowsSurviveMigrationWithDefaultMetadataAndStoreKeepsWorking() {
        writeV1Store()
        val loaded = success(store().load())
        val migrated = loaded.profiles.single()
        assertEquals(legacyId, migrated.id)
        assertEquals(3L, migrated.revision)
        assertEquals("Legacy host", migrated.label)
        assertEquals("legacy.example", migrated.host)
        assertEquals(22, migrated.port)
        assertEquals(emptySet<String>(), migrated.tags)
        assertEquals(false, migrated.favorite)
        assertEquals(0L, migrated.lastUsedAt)
        // The migration is durable and the header/markers are untouched.
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { check(it.moveToFirst()); assertEquals(4L, it.getLong(0)) }
            db.rawQuery("SELECT version FROM metadata", null).use {
                check(it.moveToFirst()); assertEquals(4L, it.getLong(0))
            }
        }
        assertEquals(generation, success(store().load()).generation)
        // v2 CRUD operates on the migrated store: edit fields, organization and use marker.
        val edited = success(store().update(migrated.let {
            ConnectionProfileVersion.create(loaded.generation, it.id, it.revision)
        }, ConnectionProfileDraft.create(WindowOwner.CONSOLE, "Legacy host", ConnectionProtocol.SSH,
            "legacy.example", 22, linkedSetOf("work", "lab"), true)))
        assertEquals(4L, edited.profile.revision)
        assertEquals(linkedSetOf("work", "lab"), edited.profile.tags)
        assertEquals(true, edited.profile.favorite)
        val used = success(store().recordUse(edited.version, 1_782_200_000L))
        assertEquals(5L, used.profile.revision)
        assertEquals(1_782_200_000L, used.profile.lastUsedAt)
        val reloaded = success(store().load()).profiles.single()
        assertEquals(linkedSetOf("work", "lab"), reloaded.tags)
        assertEquals(true, reloaded.favorite)
        assertEquals(1_782_200_000L, reloaded.lastUsedAt)
        // Editing fields after a use marker keeps the marker.
        val renamed = success(store().update(used.version, ConnectionProfileDraft.create(
            WindowOwner.CONSOLE, "Renamed legacy", ConnectionProtocol.SSH, "legacy.example", 22)))
        assertEquals(1_782_200_000L, renamed.profile.lastUsedAt)
        assertEquals(emptySet<String>(), renamed.profile.tags)
        assertEquals(false, renamed.profile.favorite)
    }

    @Test fun recordUseRejectsStaleIdentityAndInvalidMarkers() {
        writeV1Store()
        val catalog = success(store().load())
        val profile = catalog.profiles.single()
        val version = ConnectionProfileVersion.create(catalog.generation, profile.id, profile.revision)
        denied(ConnectionProfilesCode.INVALID_INPUT, store().recordUse(version, 0L))
        denied(ConnectionProfilesCode.INVALID_INPUT, store().recordUse(version, -1L))
        val missing = ConnectionProfileVersion.create(catalog.generation, UUID.randomUUID().toString(), 1)
        denied(ConnectionProfilesCode.NOT_FOUND, store().recordUse(missing, 10L))
        val used = success(store().recordUse(version, 10L))
        denied(ConnectionProfilesCode.CONFLICT, store().recordUse(version, 20L))
        denied(ConnectionProfilesCode.STALE_GENERATION,
            store().recordUse(ConnectionProfileVersion.create(UUID.randomUUID().toString(), profile.id, 4L), 30L))
        assertEquals(10L, used.profile.lastUsedAt)
        assertEquals(4L, used.profile.revision)
    }
}
