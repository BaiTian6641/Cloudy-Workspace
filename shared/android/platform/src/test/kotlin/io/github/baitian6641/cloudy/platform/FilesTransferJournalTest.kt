package io.github.baitian6641.cloudy.platform

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class FilesTransferJournalTest {
    private fun root() = Files.createTempDirectory("cloudy-transfers").toFile()
    private fun journal(root: File, capacity: Int = 8, now: () -> Long = { 1L }) =
        FilesTransferJournal(root, capacity, now)

    @Test fun stagedAndCommittedRecordsSurviveReload() {
        val root = root()
        val first = journal(root).begin("profile-1", 3, "/home/user/notes.txt",
            "/home/user/.cloudy-stage-abc", 12)
        val record = (first as FilesJournalResult.Success).value
        assertEquals(FilesTransferState.STAGED, record.state)
        val staged = journal(root).load() as FilesJournalResult.Success
        assertEquals(FilesTransferState.STAGED, staged.value.single().state)
        assertTrue((journal(root).transition(record.id, FilesTransferState.COMMITTED, true) as
            FilesJournalResult.Success).value.replaceConsent)
        val reloaded = journal(root).load() as FilesJournalResult.Success
        assertEquals(FilesTransferState.COMMITTED, reloaded.value.single().state)
        assertEquals("/home/user/.cloudy-stage-abc", reloaded.value.single().tempPath)
        assertEquals(3, reloaded.value.single().profileRevision)
    }

    @Test fun transitionCanReviseVerifiedPathAndByteCount() {
        val root = root()
        val record = (journal(root).begin("p", 1, "/intended", "/intended/.stage", 0) as
            FilesJournalResult.Success).value
        journal(root).transition(record.id, FilesTransferState.COMMITTING, false,
            byteCount = 123, path = "/final (copy).txt")
        val loaded = (journal(root).load() as FilesJournalResult.Success).value.single()
        assertEquals(FilesTransferState.UNCERTAIN, loaded.state)
        assertEquals(123, loaded.byteCount)
        assertEquals("/final (copy).txt", loaded.path)
        assertEquals("/intended/.stage", loaded.tempPath)
    }

    @Test fun committingAfterProcessDeathBecomesUncertainAndIsPersisted() {
        val root = root()
        val record = (journal(root).begin("profile-1", 1, "/a", "/a/.stage", 1) as
            FilesJournalResult.Success).value
        journal(root).transition(record.id, FilesTransferState.COMMITTING, false)
        val restarted = journal(root).load() as FilesJournalResult.Success
        assertEquals(FilesTransferState.UNCERTAIN, restarted.value.single().state)
        // A second load must not lose the settled state.
        val again = journal(root).load() as FilesJournalResult.Success
        assertEquals(FilesTransferState.UNCERTAIN, again.value.single().state)
    }

    @Test fun capacityEvictsOnlyTerminalRecordsAndRefusesAllActive() {
        val root = root()
        val j = journal(root, capacity = 2)
        val a = (j.begin("p", 1, "/a", "/a.tmp", 1) as FilesJournalResult.Success).value
        j.transition(a.id, FilesTransferState.COMMITTED, false)
        val b = (j.begin("p", 1, "/b", "/b.tmp", 1) as FilesJournalResult.Success).value
        val c = (j.begin("p", 1, "/c", "/c.tmp", 1) as FilesJournalResult.Success).value
        val ids = (j.load() as FilesJournalResult.Success).value.map { it.id }
        assertFalse(ids.contains(a.id)); assertTrue(ids.contains(b.id)); assertTrue(ids.contains(c.id))
        // b and c are active; an additional record must be refused rather than evicting them.
        assertEquals(FilesJournalCode.CAPACITY,
            (j.begin("p", 1, "/d", "/d.tmp", 1) as FilesJournalResult.Failure).code)
    }

    @Test fun arbitraryUnicodePathsRoundTripThroughPercentEncoding() {
        val root = root()
        val j = journal(root)
        val path = "/naïve dir/100%\tfile\n.txt"
        val temp = "/naïve dir/.cloudy-stage-\u0001"
        val record = (j.begin("p", 1, path, temp, 0) as FilesJournalResult.Success).value
        val loaded = (journal(root).load() as FilesJournalResult.Success).value.single()
        assertEquals(path, loaded.path)
        assertEquals(temp, loaded.tempPath)
        assertEquals(record.id, loaded.id)
        assertFalse(root.listFiles()!!.any { it.name == "transfers.tmp" })
    }

    @Test fun malformedJournalIsRejectedWithoutOverwritingIt() {
        val root = root()
        root.mkdirs()
        val file = File(root, "transfers.journal")
        file.writeText("cloudy-transfers\t1\nbroken", Charsets.UTF_8)
        val before = file.readBytes()
        assertEquals(FilesJournalCode.UNAVAILABLE,
            (journal(root).load() as FilesJournalResult.Failure).code)
        assertArrayEquals(before, file.readBytes())
        assertEquals(FilesJournalCode.UNAVAILABLE,
            (journal(root).begin("p", 1, "/a", "/a.tmp", 1) as FilesJournalResult.Failure).code)
        assertArrayEquals(before, file.readBytes())
    }
}
