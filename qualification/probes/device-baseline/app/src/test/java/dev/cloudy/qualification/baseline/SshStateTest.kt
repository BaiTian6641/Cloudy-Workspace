package dev.cloudy.qualification.baseline

import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure policy coverage: terminal history does not assert socket exit or durable storage. */
class SshStateTest {
    private val process = UUID.randomUUID().toString()
    private val config = "a".repeat(64)
    private val build = SshBuildIdentity("0.6.0", 8, "49607be8-a053-4f7f-8d22-3764b7e63136")
    private val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32))
    private val different = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
    private var tick = 0L
    private fun time() = TransferTime("test-${++tick}", tick)
    private fun begin(state: SshState): SshAttempt = requireNotNull(state.begin(UUID.randomUUID().toString(), process, config, SshOperation.SFTP, build, time()))
    private fun current(state: SshState) = state.snapshot().last()
    private fun running(): SshAttempt = begin(SshState())

    @Test fun unknownTrustRequiresExactIndependentReferenceAndCannotBootstrapChangedKeys() {
        assertEquals(SshTrustDecision.UNKNOWN, SshTrust(fingerprint, fingerprint, null).decision)
        assertTrue(SshTrust(fingerprint, fingerprint, null).canAcceptUnknown)
        assertEquals(SshTrustDecision.MATCH, SshTrust(fingerprint, fingerprint, fingerprint).decision)
        listOf(SshTrust(fingerprint, different, null), SshTrust(different, different, fingerprint),
            SshTrust(fingerprint, fingerprint, different), SshTrust("invalid", fingerprint, null),
            SshTrust(fingerprint, "invalid", null), SshTrust(fingerprint, fingerprint, "invalid")).forEach {
            assertEquals(SshTrustDecision.REJECT, it.decision)
            assertFalse(it.canAcceptUnknown)
        }
        assertFalse(SshTrust(fingerprint, fingerprint, fingerprint).canAcceptUnknown)
    }

    @Test fun onlyOneAttemptCanRunAndEightAttemptsAreRetainedWithoutEviction() {
        val state = SshState()
        repeat(8) {
            val active = begin(state)
            assertNull(state.begin(UUID.randomUUID().toString(), process, config, SshOperation.SFTP, build, time()))
            assertTrue(state.update(active.id, "failed", "fixture-unavailable", time = time()))
            assertNull(state.begin(active.id, process, config, SshOperation.SFTP, build, time()))
        }
        val before = state.snapshot()
        assertEquals(8, before.size)
        assertEquals(8, before.map { it.id }.distinct().size)
        assertNull(state.begin(UUID.randomUUID().toString(), process, config, SshOperation.SFTP, build, time()))
        assertEquals(before, state.snapshot())
    }

    @Test fun firstTrustEndsTheUnauthenticatedAttemptAndNeedsANewExplicitBegin() {
        val state = SshState()
        val first = begin(state)
        assertTrue(state.update(first.id, "trust-required", "independent-reference-matches", fingerprint, time = time()))
        assertTrue(current(state).terminal)
        assertFalse(current(state).authenticated)
        assertFalse(state.update(first.id, "running", "late-auth", authenticated = true, time = time()))
        assertFalse(state.update(first.id, "completed", authenticated = true, time = time()))
        assertEquals(1, state.snapshot().size)
        assertNotNull(begin(state))
    }

    @Test fun cancellationWinsOverLateCompletionAndStaleCallbacksCannotMutateTheNextAttempt() {
        val state = SshState()
        val old = begin(state)
        state.update(old.id, "running", "authenticated", fingerprint, true, time = time())
        assertTrue(state.update(old.id, "cancelled", "user-cancel", time = time()))
        val cancelled = current(state)
        assertFalse(state.update(old.id, "completed", "late-result", time = time()))
        begin(state)
        val before = state.snapshot()
        listOf("running", "completed", "failed", "cancelled").forEach {
            assertFalse(state.update(old.id, it, "late-callback", time = time()))
        }
        assertEquals(cancelled, state.snapshot().first())
        assertEquals(before, state.snapshot())
    }

    @Test fun committedCompletionCannotBeRewrittenByLateCancellation() {
        val state = SshState()
        val active = begin(state)
        state.update(active.id, "completed", "verified", fingerprint, true, time = time())
        val before = state.snapshot()
        assertFalse(state.update(active.id, "cancelled", "late-cancel", time = time()))
        assertFalse(state.update(active.id, "failed", "late-failure", time = time()))
        assertEquals(before, state.snapshot())
    }

    @Test fun authenticationCannotPrecedeHostObservationOrBeUsedWithTrustRejection() {
        val state = SshState()
        val active = begin(state)
        val before = state.snapshot()
        assertThrows(IllegalArgumentException::class.java) { state.update(active.id, "running", authenticated = true, time = time()) }
        assertThrows(IllegalArgumentException::class.java) { state.update(active.id, "completed", time = time()) }
        listOf("trust-required", "host-key-rejected").forEach {
            assertThrows(IllegalArgumentException::class.java) { state.update(active.id, it, time = time()) }
            assertThrows(IllegalArgumentException::class.java) { state.update(active.id, it, fingerprint = fingerprint, authenticated = true, time = time()) }
        }
        assertEquals(before, state.snapshot())
    }

    @Test fun observedHostIsImmutableAndAuthenticationIsHistoricalMonotonicEvidence() {
        val state = SshState()
        val active = begin(state)
        state.update(active.id, "running", "authenticated", fingerprint, true, time = time())
        val before = state.snapshot()
        assertThrows(IllegalArgumentException::class.java) { state.update(active.id, "running", fingerprint = different, time = time()) }
        assertEquals(before, state.snapshot())
        state.update(active.id, "failed", "connection-closed", authenticated = false, time = time())
        assertTrue(current(state).authenticated)
        assertEquals(fingerprint, current(state).observedHostKey)
    }

    @Test fun newProcessRecoveryInterruptsOnlyUnfinishedWorkWithoutCreatingAnAttempt() {
        val original = SshState()
        val terminal = begin(original)
        original.update(terminal.id, "trust-required", "unknown-host", fingerprint, time = time())
        val active = begin(original)
        original.update(active.id, "running", "authenticated", fingerprint, true, time = time())
        val restored = SshState(original.snapshot())
        assertTrue(restored.reconcileInterrupted(time()))
        assertEquals(original.snapshot().first(), restored.snapshot().first())
        assertEquals(2, restored.snapshot().size)
        assertEquals("interrupted", current(restored).status)
        assertEquals("new-process-unfinished-attempt", current(restored).reason)
        assertEquals(process, current(restored).originProcessId)
        assertTrue(current(restored).authenticated)
        val before = restored.snapshot()
        assertFalse(restored.reconcileInterrupted(time()))
        assertFalse(restored.update(active.id, "running", "replay", time = time()))
        assertEquals(before, restored.snapshot())
    }

    @Test fun eventRetentionIsBoundedAndRepeatingAnObservationDoesNotInventHistory() {
        val state = SshState()
        val active = begin(state)
        repeat(70) { state.update(active.id, "running", "stage-$it", time = time()) }
        val current = current(state)
        assertEquals(32, current.events.size)
        assertEquals(39L, current.droppedEvents)
        assertEquals("stage-38", current.events.first().reason)
        assertEquals("stage-69", current.events.last().reason)
        assertFalse(state.update(active.id, "running", "stage-69", time = time()))
        assertEquals(current, current(state))
        val restored = SshState(listOf(current.copy(droppedEvents = Long.MAX_VALUE)))
        assertTrue(restored.update(active.id, "cancelled", "user-cancel", time = time()))
        assertEquals(Long.MAX_VALUE, current(restored).droppedEvents)
        assertEquals(32, current(restored).events.size)
    }

    @Test fun mutableCallerCollectionsCannotRewriteRestoredOrUpdatedEvidence() {
        val row = running()
        val details = linkedMapOf("kex" to "ecdh-sha2-nistp256")
        val events = row.events.toMutableList()
        val state = SshState(listOf(row.copy(details = details, events = events)))
        details["kex"] = "changed"
        events.clear()
        assertEquals("ecdh-sha2-nistp256", current(state).details["kex"])
        assertEquals(1, current(state).events.size)
        val progress = linkedMapOf("uploadAcknowledgedBytes" to "64")
        state.update(row.id, "running", "upload-progress", details = progress, time = time())
        progress["uploadAcknowledgedBytes"] = "0"
        assertEquals("64", current(state).details["uploadAcknowledgedBytes"])
        assertThrows(UnsupportedOperationException::class.java) { (current(state).details as MutableMap)["kex"] = "changed" }
        assertThrows(UnsupportedOperationException::class.java) { (current(state).events as MutableList).clear() }
    }

    @Test fun detailsAllowOnlyBoundedSanitizedEvidenceAndFixedUploadNames() {
        val state = SshState()
        val row = begin(state)
        val invalid = listOf(mapOf("password" to "secret"), mapOf("kex" to "x".repeat(129)),
            mapOf("kex" to "a\nb"), mapOf("kex" to ""), mapOf("uploadName" to "other-file.bin"),
            mapOf("uploadName" to "cloudy-1-1-1-1-1.bin"))
        val before = state.snapshot()
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { state.update(row.id, "running", "stage", details = it, time = time()) } }
        assertEquals(before, state.snapshot())
        assertTrue(state.update(row.id, "running", "upload-created", details = mapOf("uploadName" to "cloudy-${row.id}.bin"), time = time()))
    }

    @Test fun restorationRejectsInvalidIdentityStatusBoundsAndFalsifiedCurrentEvent() {
        val row = running()
        val bad = listOf(row.copy(id = "1-1-1-1-1"), row.copy(originProcessId = "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA"),
            row.copy(configIdentity = "A".repeat(64)), row.copy(status = "unknown"),
            row.copy(events = emptyList()), row.copy(events = List(33) { row.events.first() }),
            row.copy(droppedEvents = -1), row.copy(reason = "not-the-last-event"),
            row.copy(reason = "line\nbreak"), row.copy(observedHostKey = "invalid"),
            row.copy(events = listOf(SshEvent("running", TransferTime("test", -1), null))))
        bad.forEach { assertThrows(IllegalArgumentException::class.java) { SshState(listOf(it)) } }
        assertThrows(IllegalArgumentException::class.java) { SshState(listOf(row, row)) }
        assertThrows(IllegalArgumentException::class.java) { SshState(List(9) { row.copy(id = UUID.randomUUID().toString()) }) }
    }

    @Test fun restoredHistoryCannotResumeATerminalAttemptOrHideEarlierUnfinishedWork() {
        val row = running()
        val forged = row.copy(events = listOf(SshEvent("failed", time(), "failed"), row.events.last()))
        assertThrows(IllegalArgumentException::class.java) { SshState(listOf(forged)) }
        val later = row.copy(id = UUID.randomUUID().toString(), status = "failed", reason = "failed",
            events = listOf(SshEvent("failed", time(), "failed")))
        assertThrows(IllegalArgumentException::class.java) { SshState(listOf(row, later)) }
        assertEquals(listOf(later, row), SshState(listOf(later, row)).snapshot())
    }

    @Test fun operationAndExecutingBuildSurviveProgressRecoveryAndLateCallbacks() {
        val state = SshState()
        val attempt = requireNotNull(state.begin(UUID.randomUUID().toString(), process, config,
            SshOperation.EXEC_PTY, build, time()))
        assertEquals("recorded-build", attempt.provenance)
        state.update(attempt.id, "running", "pty-awaiting-resize", fingerprint, true,
            mapOf("ptyInitialCols" to "80", "ptyInitialRows" to "24"), time())
        val restored = SshState(state.snapshot())
        assertTrue(restored.reconcileInterrupted(time()))
        assertFalse(restored.update(attempt.id, "completed", time = time()))
        assertEquals(SshOperation.EXEC_PTY, current(restored).operation)
        assertEquals(build, current(restored).buildIdentity)
        assertEquals("interrupted", current(restored).status)
        assertEquals("80", current(restored).details["ptyInitialCols"])
    }

    @Test fun legacyProvenanceIsExplicitAndCannotAppearAfterRecordedBuilds() {
        val row = running().copy(buildIdentity = null)
        assertEquals("external-legacy", row.provenance)
        val legacy = SshState(listOf(row))
        legacy.reconcileInterrupted(time())
        assertNull(current(legacy).buildIdentity)
        assertEquals(SshOperation.SFTP, current(legacy).operation)
        begin(legacy)
        assertEquals(row.id, legacy.snapshot().first().id)
        assertEquals(build, current(legacy).buildIdentity)
        assertThrows(IllegalArgumentException::class.java) { SshState(listOf(row.copy(operation = SshOperation.EXEC_PTY))) }
        val recorded = running().let { it.copy(status = "failed", reason = "failed",
            events = listOf(SshEvent("failed", time(), "failed"))) }
        assertThrows(IllegalArgumentException::class.java) { SshState(listOf(recorded, row)) }
    }

    @Test fun executingBuildIdentityRejectsAmbiguousVersionsCodesAndReferences() {
        listOf("", "0.6", "0.6.0\n", "secret path", "1".repeat(41)).forEach {
            assertThrows(IllegalArgumentException::class.java) { build.copy(versionName = it) }
        }
        assertThrows(IllegalArgumentException::class.java) { build.copy(versionCode = 0) }
        assertThrows(IllegalArgumentException::class.java) { build.copy(buildRef = "1-1-1-1-1") }
        assertThrows(IllegalArgumentException::class.java) { build.copy(buildRef = build.buildRef.uppercase()) }
    }

    @Test fun operationSpecificDetailsRejectCrossLaneOrUnboundedProtocolData() {
        val state = SshState()
        val attempt = requireNotNull(state.begin(UUID.randomUUID().toString(), process, config,
            SshOperation.EXEC_PTY, build, time()))
        val invalid = listOf(mapOf("uploadName" to "cloudy-${attempt.id}.bin"),
            mapOf("ptyStdoutBytes" to "4097"), mapOf("ptyInputBytes" to "13"),
            mapOf("execExitStatus" to "-1"), mapOf("ptyInitialCols" to "0"),
            mapOf("ptyInitialRows" to "024"), mapOf("execStderrBytes" to "999999999999999999999"),
            mapOf("execStdoutSha256" to "x".repeat(64)), mapOf("ptyCompletionMarker" to "unknown"))
        val before = state.snapshot()
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) { state.update(attempt.id, "running", "protocol", details = it, time = time()) }
        }
        assertEquals(before, state.snapshot())
        // Unexpected but bounded observed values remain useful failure evidence;
        // the engine, not this serialization model, accepts the fixture protocol.
        assertTrue(state.update(attempt.id, "running", "protocol", details = mapOf("execExitStatus" to "2",
            "ptyInitialCols" to "79", "execStdoutSha256" to "b".repeat(64)), time = time()))
        val sftp = SshState()
        val old = begin(sftp)
        assertThrows(IllegalArgumentException::class.java) {
            sftp.update(old.id, "running", "protocol", details = mapOf("execExitStatus" to "17"), time = time())
        }
    }
}
