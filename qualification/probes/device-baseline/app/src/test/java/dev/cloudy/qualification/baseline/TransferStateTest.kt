package dev.cloudy.qualification.baseline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure attempt policy; no scheduler, process death or durable Android storage is simulated. */
class TransferStateTest {
    private val hash = "a".repeat(64)
    private var tick = 0L
    private fun time() = TransferTime("test-time-${++tick}", tick)

    private fun verifying(state: TransferState): TransferAttempt {
        val attempt = requireNotNull(state.begin(4, hash, time()))
        assertTrue(state.claim(attempt.id, time()))
        assertTrue(state.progress(attempt.id, 4))
        assertTrue(state.verifying(attempt.id, time()))
        return requireNotNull(state.current())
    }

    @Test
    fun aQueuedAttemptCanBeClaimedOnlyOnceAndProgressCannotMoveBackward() {
        val state = TransferState("process-a")
        val attempt = requireNotNull(state.begin(4, hash, time()))
        assertNull(state.begin(4, hash, time()))
        assertTrue(state.claim(attempt.id, time()))
        assertFalse(state.claim(attempt.id, time()))
        assertTrue(state.progress(attempt.id, 2))
        val saved = state.snapshot()
        val revision = state.revision
        assertFalse(state.progress(attempt.id, 1))
        assertFalse(state.progress(attempt.id, 5))
        assertFalse(state.progress(attempt.id, 2))
        assertFalse(state.verifying(attempt.id, time()))
        assertEquals(saved, state.snapshot())
        assertEquals(revision, state.revision)
    }

    @Test
    fun completionRequiresFullProgressAndAnExactIndependentDigest() {
        val state = TransferState("process-a")
        val attempt = requireNotNull(state.begin(4, hash, time()))
        assertTrue(state.claim(attempt.id, time()))
        assertFalse(state.complete(attempt.id, TransferDigest(4, hash), time()))
        assertTrue(state.progress(attempt.id, 4))
        assertTrue(state.verifying(attempt.id, time()))
        assertFalse(state.complete(attempt.id, TransferDigest(3, hash), time()))
        assertFalse(state.complete(attempt.id, TransferDigest(4, "b".repeat(64)), time()))
        assertNull(state.current()?.verifiedSha256)
        assertEquals("verifying", state.current()?.status)
        assertTrue(state.complete(attempt.id, TransferDigest(4, hash), time()))
        val completed = requireNotNull(state.current())
        assertEquals("completed", completed.status)
        assertEquals(4L, requireNotNull(completed.verifiedLengthBytes))
        assertEquals(hash, completed.verifiedSha256)
    }

    @Test
    fun cancellationBeforeClaimPreventsWorkAndKeepsItsOriginalReason() {
        val state = TransferState("process-a")
        val attempt = requireNotNull(state.begin(4, hash, time()))
        assertTrue(state.stop(attempt.id, "cancelled", "notification-cancel", null, time()))
        assertFalse(state.claim(attempt.id, time()))
        assertFalse(state.fail(attempt.id, "late-network-error", time()))
        assertFalse(state.stop(attempt.id, "interrupted", "later-system-stop", 5, time()))
        assertFalse(state.canStart())
        assertTrue(state.finishStopped(attempt.id, time()))
        assertEquals("cancelled", state.current()?.status)
        assertEquals("notification-cancel", state.current()?.reason)
        assertNull(state.current()?.stopReason)
        assertTrue(state.canStart())
    }

    @Test
    fun cancellationDuringVerificationWinsOverALateSuccess() {
        val state = TransferState("process-a")
        val attempt = verifying(state)
        assertTrue(state.stop(attempt.id, "cancelled", "screen-cancel", null, time()))
        assertFalse(state.complete(attempt.id, TransferDigest(4, hash), time()))
        assertFalse(state.progress(attempt.id, 4))
        assertFalse(state.fail(attempt.id, "late-copy-error", time()))
        assertTrue(state.finishStopped(attempt.id, time()))
        assertFalse(state.complete(attempt.id, TransferDigest(4, hash), time()))
        assertEquals("cancelled", state.current()?.status)
        assertNull(state.current()?.verifiedSha256)
    }

    @Test
    fun recordedCompletionCannotBeRewrittenByALateStopOrFailure() {
        val state = TransferState("process-a")
        val attempt = verifying(state)
        assertTrue(state.complete(attempt.id, TransferDigest(4, hash), time()))
        val completed = state.snapshot()
        val revision = state.revision
        assertFalse(state.stop(attempt.id, "cancelled", "late-cancel", null, time()))
        assertFalse(state.fail(attempt.id, "late-error", time()))
        assertFalse(state.finishStopped(attempt.id, time()))
        assertFalse(state.complete(attempt.id, TransferDigest(4, hash), time()))
        assertEquals(completed, state.snapshot())
        assertEquals(revision, state.revision)
    }

    @Test
    fun callbacksFromAnEarlierAttemptCannotMutateTheNewAttempt() {
        val state = TransferState("process-a")
        val old = verifying(state)
        assertTrue(state.stop(old.id, "interrupted", "network-changed", 7, time()))
        assertTrue(state.finishStopped(old.id, time()))
        val next = requireNotNull(state.begin(4, hash, time()))
        assertNotEquals(old.id, next.id)
        val before = state.snapshot()
        val revision = state.revision
        assertFalse(state.claim(old.id, time()))
        assertFalse(state.progress(old.id, 4))
        assertFalse(state.verifying(old.id, time()))
        assertFalse(state.complete(old.id, TransferDigest(4, hash), time()))
        assertFalse(state.fail(old.id, "late-error", time()))
        assertFalse(state.stop(old.id, "cancelled", "late-cancel", null, time()))
        assertFalse(state.finishStopped(old.id, time()))
        assertEquals(before, state.snapshot())
        assertEquals(revision, state.revision)
        assertEquals("queued", state.current()?.status)
        assertEquals(7, state.find(old.id)?.stopReason)
    }

    @Test
    fun aRestoredClaimInTheSameProcessDoesNotAuthorizeASecondWorker() {
        val original = TransferState("process-a")
        val attempt = requireNotNull(original.begin(4, hash, time()))
        assertTrue(original.claim(attempt.id, time()))
        assertTrue(original.progress(attempt.id, 2))
        val restored = TransferState("process-a", original.snapshot())
        assertFalse(restored.claim(attempt.id, time()))
        assertNull(restored.begin(4, hash, time()))
        assertEquals(2L, restored.current()?.bytesWritten)
        assertEquals(original.snapshot(), restored.snapshot())
    }

    @Test
    fun newProcessRecoveryInterruptsEveryUnfinishedPhaseWithoutReplay() {
        listOf("queued", "running", "verifying", "cancel-requested").forEach { phase ->
            val original = TransferState("process-a")
            val attempt = requireNotNull(original.begin(4, hash, time()))
            if (phase != "queued") assertTrue(original.claim(attempt.id, time()))
            if (phase == "verifying") {
                assertTrue(original.progress(attempt.id, 4))
                assertTrue(original.verifying(attempt.id, time()))
            }
            if (phase == "cancel-requested") {
                assertTrue(original.stop(attempt.id, "cancelled", "user-cancel", null, time()))
            }
            val restored = TransferState("process-b", original.snapshot())
            assertFalse(restored.claim(attempt.id, time()))
            assertTrue(restored.recover(time()))
            assertEquals("interrupted", restored.current()?.status)
            assertEquals("new-process-unfinished-attempt", restored.current()?.reason)
            assertFalse(restored.claim(attempt.id, time()))
            assertFalse(restored.complete(attempt.id, TransferDigest(4, hash), time()))
            assertFalse(restored.recover(time()))
            assertEquals(1, restored.snapshot().size)
            // Recovery merely permits a later explicit action; it creates no attempt.
            assertTrue(restored.canStart())
            val retry = requireNotNull(restored.begin(4, hash, time()))
            assertNotEquals(attempt.id, retry.id)
            assertEquals("process-b", retry.originProcessId)
        }
    }

    @Test
    fun eightAttemptLimitRetainsHistoryInsteadOfEvictingAndReplaying() {
        val state = TransferState("process-a")
        repeat(TransferState.ATTEMPT_LIMIT) {
            val attempt = requireNotNull(state.begin(4, hash, time()))
            assertTrue(state.fail(attempt.id, "test-fixture-unavailable", time()))
        }
        val before = state.snapshot()
        val revision = state.revision
        assertEquals(8, before.size)
        assertEquals(8, before.map { it.id }.distinct().size)
        assertFalse(state.canStart())
        assertNull(state.begin(4, hash, time()))
        assertEquals(before, state.snapshot())
        assertEquals(revision, state.revision)
        assertFalse(TransferState("process-b", before).recover(time()))
    }

    @Test
    fun savedCompletionSurvivesSuccessfulAuditButMismatchRemovesItsSuccess() {
        val original = TransferState("process-a")
        val attempt = verifying(original)
        assertTrue(original.complete(attempt.id, TransferDigest(4, hash), time()))
        val restored = TransferState("process-b", original.snapshot())
        assertFalse(restored.recover(time()))
        restored.auditSavedCompletion(attempt.id, TransferAuditResult.Verified, time())
        val reverified = requireNotNull(restored.current())
        assertEquals("completed", reverified.status)
        assertEquals(4L, requireNotNull(reverified.verifiedLengthBytes))
        assertEquals(hash, reverified.verifiedSha256)
        assertEquals("new-process-file-reverified", reverified.reason)
        restored.auditSavedCompletion(attempt.id, TransferAuditResult.Mismatch, time())
        assertEquals("failed", restored.current()?.status)
        assertEquals("saved-completion-file-mismatch", restored.current()?.reason)
        assertNull(restored.current()?.verifiedLengthBytes)
        assertNull(restored.current()?.verifiedSha256)
        assertFalse(restored.complete(attempt.id, TransferDigest(4, hash), time()))
    }

    @Test
    fun unavailableAuditPreservesHistoricalCompletionUntilAnActualRetryResult() {
        val original = TransferState("process-a")
        val attempt = verifying(original)
        assertTrue(original.complete(attempt.id, TransferDigest(4, hash), time()))
        val restored = TransferState("process-b", original.snapshot())
        val history = restored.snapshot()
        val revision = restored.revision
        listOf("IOException", "NoSuchFileException", "startup-audit-time-limit").forEach { reason ->
            restored.auditSavedCompletion(attempt.id, TransferAuditResult.Unavailable(reason), time())
            assertEquals(history, restored.snapshot())
            assertEquals(revision, restored.revision)
        }
        restored.auditSavedCompletion(attempt.id, TransferAuditResult.Verified, time())
        val retried = requireNotNull(restored.current())
        assertEquals("completed", retried.status)
        assertEquals(hash, retried.verifiedSha256)
        assertEquals(4L, requireNotNull(retried.verifiedLengthBytes))
        assertEquals(history.single().events.size + 1, retried.events.size)
        assertEquals("new-process-file-reverified", retried.reason)
        assertEquals(revision + 1, restored.revision)
    }

    @Test
    fun auditHistoryIsBoundedAndSnapshotsDoNotChangeRetroactively() {
        val state = TransferState("process-a")
        val attempt = verifying(state)
        assertTrue(state.complete(attempt.id, TransferDigest(4, hash), time()))
        val captured = requireNotNull(state.current())
        repeat(40) { state.auditSavedCompletion(attempt.id, TransferAuditResult.Verified, time()) }
        val current = requireNotNull(state.current())
        assertEquals(TransferState.EVENT_LIMIT, current.events.size)
        assertEquals(captured.events.size + 40L - TransferState.EVENT_LIMIT, current.droppedEvents)
        assertEquals(4, captured.events.size)
        assertEquals(0L, captured.droppedEvents)
    }

    @Test
    fun ambiguousDuplicateOrOversizedRestoredHistoryIsRejected() {
        val state = TransferState("process-a")
        val attempt = requireNotNull(state.begin(4, hash, time()))
        assertThrows(IllegalArgumentException::class.java) {
            TransferState("process-b", listOf(attempt, attempt))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferState("process-b", (0..8).map { attempt.copy(id = "test-$it") })
        }
    }
}
