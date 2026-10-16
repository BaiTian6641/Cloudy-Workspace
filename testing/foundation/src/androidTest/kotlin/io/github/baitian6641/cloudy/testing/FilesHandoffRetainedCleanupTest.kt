package io.github.baitian6641.cloudy.testing

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Real in-memory SQLite predicate tests; not receiver preparation or device acceptance. */
@RunWith(AndroidJUnit4::class)
class FilesHandoffRetainedCleanupTest {
    @Test fun exactOwnedRowsAreRemovedWithoutDeletingBaselineRows() {
        for (index in 0..2) database(index) { db, baseline, owned ->
            remove(index, db, listOf(baseline), owned)
            assertEquals(listOf(baseline), read(index, db))
        }
    }
    @Test fun changedOwnedTupleOrExtraRowIsRejectedWithoutMutation() {
        for (index in 0..2) database(index) { db, baseline, owned ->
            val wrong = owned.toMutableList().also { it[it.lastIndex] = "changed" }
            val original = read(index, db)
            assertThrows(Exception::class.java) { remove(index, db, listOf(baseline), wrong) }
            assertEquals(original, read(index, db))
            assertThrows(Exception::class.java) { remove(index, db, emptyList(), owned) }
            assertEquals(original, read(index, db))
        }
    }
    @Test fun partialPriorCleanupIsRejectedRatherThanRecreated() {
        for (index in 0..2) database(index) { db, baseline, owned ->
            remove(index, db, listOf(baseline), owned)
            assertThrows(Exception::class.java) { remove(index, db, listOf(baseline), owned) }
            assertEquals(listOf(baseline), read(index, db))
        }
    }
    private fun database(index: Int, action: (SQLiteDatabase, List<Any>, List<Any>) -> Unit) {
        SQLiteDatabase.create(null).use { db ->
            val integers = when (index) { 0 -> setOf(1, 6); 1 -> setOf(1, 4, 5); else -> setOf(4, 10, 13, 15) }
            val columns = FilesHandoffReceiverCheckpoint.COLUMNS[index].split(',')
            db.execSQL("CREATE TABLE ${tables[index]} (" + columns.mapIndexed { column, name ->
                "$name ${if (column in integers) "INTEGER" else "TEXT"} NOT NULL"
            }.joinToString(",") + ")")
            val owned: List<Any> = when (index) {
                0 -> listOf("owned", 1L, "console", "label", "ssh", "127.0.0.1", 22L)
                1 -> listOf("127.0.0.1", 22L, "direct", "console", 1L, 0L, "key", "fingerprint")
                else -> List(20) { if (it in integers) 1L else "field$it" }.toMutableList().also {
                    it[4] = 2L; it[5] = "SHARED"; it[18] = FilesLocationsFixture.NESTED_ROOT
                }
            }
            val baseline = owned.toMutableList().also { it[0] = "baseline" }
            for (row in listOf(baseline, owned)) db.execSQL("INSERT INTO ${tables[index]} VALUES (" +
                columns.joinToString(",") { "?" } + ")", row.toTypedArray())
            action(db, baseline, owned)
        }
    }
    private fun read(index: Int, db: SQLiteDatabase) = FilesHandoffReceiverCheckpoint.rows(db,
        "SELECT ${FilesHandoffReceiverCheckpoint.COLUMNS[index]} FROM ${tables[index]} ORDER BY ${orders[index]}")
    private fun remove(index: Int, db: SQLiteDatabase, baseline: List<List<Any>>, owned: List<Any>) {
        when (index) {
            0 -> SshFilesFixture.removeRetainedHandoffProfile(db, baseline, owned)
            1 -> SshObserveTrustFixture.removeRetainedHandoffPin(db, baseline, owned)
            2 -> FilesLocationsFixture.removeRetainedHandoffRow(db, baseline, owned)
        }
    }
    companion object {
        private val tables = listOf("profiles", "pins", "resources")
        private val orders = listOf("id", "host,port,route", "resource_id")
    }
}
