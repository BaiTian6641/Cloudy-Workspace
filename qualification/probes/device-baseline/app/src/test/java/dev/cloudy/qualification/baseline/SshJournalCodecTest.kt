package dev.cloudy.qualification.baseline

import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Schema migration of typed JSON values. These fixtures are unit-test inputs,
 * not device evidence; Android JSONObject and AtomicFile need runtime checks.
 */
class SshJournalCodecTest {
    private val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32))
    private val process = "11111111-1111-4111-8111-111111111111"
    private val newProcess = "22222222-2222-4222-8222-222222222222"
    private val build = SshBuildIdentity("0.6.0", 8, "49607be8-a053-4f7f-8d22-3764b7e63136")
    private val trust = mapOf("a".repeat(64) to fingerprint)
    private val time = TransferTime("2026-10-15T12:00:00Z", 200L)

    private fun legacyRow(index: Int, status: String): Map<String, Any?> = linkedMapOf(
        "id" to UUID(0L, index.toLong()).toString(), "originProcessId" to process,
        "configIdentity" to "b".repeat(64), "status" to status, "reason" to "fixture-case",
        "observedHostKey" to fingerprint, "authenticated" to (status == "completed"),
        "details" to linkedMapOf("hostVerificationCount" to "1", "sftpVersion" to "3"),
        "droppedEvents" to 0L,
        "events" to listOf(mapOf("status" to "running", "reason" to null,
            "deviceUtc" to "2026-10-15T11:00:00Z", "elapsedRealtimeMs" to 100L),
            mapOf("status" to status, "reason" to "fixture-case",
                "deviceUtc" to "2026-10-15T11:00:01Z", "elapsedRealtimeMs" to 101L)),
    )
    private fun legacy(): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to 1, "probeVersion" to "0.5.0", "processInstanceId" to process,
        "revision" to 40L, "snapshotAtDeviceUtc" to "2026-10-15T11:00:02Z", "trustedHosts" to trust,
        "attempts" to listOf("trust-required", "completed", "failed", "host-key-rejected", "failed")
            .mapIndexed { index, status -> legacyRow(index + 1, status) },
    )
    private fun encode(state: SshState, savedTrust: Map<String, String> = trust) =
        SshJournalCodec.encode(newProcess, 1L, "0.6.0", time.deviceUtc, state, savedTrust)
    private fun currentJournal(): Map<String, Any?> {
        val state = SshState()
        requireNotNull(state.begin(UUID(0, 6).toString(), newProcess, "b".repeat(64), SshOperation.EXEC_PTY, build, time))
        return encode(state)
    }
    private fun row(root: Map<String, Any?>, index: Int = 0): Map<String, Any?> {
        val value = (root.getValue("attempts") as List<*>)[index] as Map<*, *>
        return value.entries.associate { it.key as String to it.value }
    }
    private fun changedRow(root: Map<String, Any?>, changes: Map<String, Any?>): Map<String, Any?> =
        root + ("attempts" to listOf(row(root) + changes))
    private fun rejects(root: Map<String, Any?>) {
        assertThrows(IllegalArgumentException::class.java) { SshJournalCodec.decode(root) }
    }

    @Test fun fiveLegacyRowsAndTrustMigrateWithoutRelabelingAnyExecutingBuild() {
        val original = legacy()
        val restored = SshJournalCodec.decode(original)
        assertEquals(5, restored.attempts.size)
        assertEquals(trust, restored.trust)
        restored.attempts.forEach {
            assertEquals(SshOperation.SFTP, it.operation)
            assertNull(it.buildIdentity)
            assertEquals("external-legacy", it.provenance)
        }
        val rewritten = encode(SshState(restored.attempts), restored.trust)
        assertEquals(2, rewritten["schemaVersion"])
        repeat(5) { index ->
            assertEquals(row(original, index), row(rewritten, index) - setOf("operation", "buildIdentity", "provenance"))
        }
        assertEquals(restored, SshJournalCodec.decode(rewritten))
    }

    @Test fun newExecAttemptKeepsItsBuildWhileLegacyEvidenceAndTrustStayUnchanged() {
        val before = SshJournalCodec.decode(legacy())
        val state = SshState(before.attempts)
        val next = requireNotNull(state.begin(UUID(0, 6).toString(), newProcess, "b".repeat(64),
            SshOperation.EXEC_PTY, build, time))
        state.update(next.id, "running", "pty-awaiting-resize", fingerprint, true,
            mapOf("execExitStatus" to "17", "execStdoutBytes" to "24", "execStdoutSha256" to "c".repeat(64),
                "ptyInitialCols" to "80", "ptyInitialRows" to "24"), time)
        val after = SshJournalCodec.decode(encode(state))
        assertEquals(before.attempts, after.attempts.take(5))
        assertEquals(before.trust, after.trust)
        assertEquals(build, after.attempts.last().buildIdentity)
        assertEquals(SshOperation.EXEC_PTY, after.attempts.last().operation)
        assertEquals("recorded-build", after.attempts.last().provenance)
        val laterWriter = encode(state) + ("probeVersion" to "99.0.0")
        assertEquals(after, SshJournalCodec.decode(laterWriter))
    }

    @Test fun schemaTwoRequiresExplicitMatchingProvenanceAndExactBuildFields() {
        val root = currentJournal()
        listOf("operation", "buildIdentity", "provenance").forEach {
            rejects(root + ("attempts" to listOf(row(root) - it)))
        }
        rejects(changedRow(root, mapOf("buildIdentity" to null)))
        rejects(changedRow(root, mapOf("buildIdentity" to null, "provenance" to "external-legacy")))
        rejects(changedRow(root, mapOf("provenance" to "external-legacy")))
        rejects(changedRow(root, mapOf("operation" to "unknown")))
        val fields = row(root)["buildIdentity"] as Map<*, *>
        rejects(changedRow(root, mapOf("buildIdentity" to fields + ("unexpected" to "value"))))
        rejects(changedRow(root, mapOf("buildIdentity" to fields - "buildRef")))
    }

    @Test fun legacySchemaCannotSmuggleCurrentOperationOrBuildClaims() {
        val root = legacy()
        listOf(mapOf("operation" to "exec-pty"), mapOf("buildIdentity" to null),
            mapOf("provenance" to "external-legacy")).forEach { rejects(changedRow(root, it)) }
        val rewritten = encode(SshState(SshJournalCodec.decode(root).attempts))
        rejects(changedRow(rewritten, mapOf("operation" to "exec-pty")))
        rejects(changedRow(rewritten, mapOf("provenance" to "recorded-build")))
    }

    @Test fun schemaAndSecurityFieldsRejectNumericAndBooleanCoercion() {
        val root = currentJournal()
        listOf<Any>(0, 3, "2", 2.0, 2L, true).forEach { rejects(root + ("schemaVersion" to it)) }
        listOf<Any>("0", 0.0, false).forEach { rejects(changedRow(root, mapOf("droppedEvents" to it))) }
        rejects(changedRow(root, mapOf("authenticated" to "false")))
        val identity = row(root)["buildIdentity"] as Map<*, *>
        listOf<Any>("8", 8.0, 0, Long.MAX_VALUE).forEach {
            rejects(changedRow(root, mapOf("buildIdentity" to identity + ("versionCode" to it))))
        }
        rejects(root + ("trustedHosts" to mapOf("a".repeat(64) to 123)))
    }

    @Test fun recoveryPreservesOldHistoryAndNeverStartsAnExtraAttempt() {
        val before = SshJournalCodec.decode(legacy())
        val state = SshState(before.attempts)
        val active = requireNotNull(state.begin(UUID(0, 6).toString(), newProcess, "b".repeat(64),
            SshOperation.EXEC_PTY, build, time))
        state.update(active.id, "running", "pty-awaiting-finish", fingerprint, true, time = time)
        val recovered = SshState(SshJournalCodec.decode(encode(state)).attempts)
        assertTrue(recovered.reconcileInterrupted(TransferTime("later-process", 1L)))
        assertFalse(recovered.reconcileInterrupted(time))
        val roundTrip = SshJournalCodec.decode(encode(recovered))
        assertEquals(before.attempts, roundTrip.attempts.take(5))
        assertEquals(6, roundTrip.attempts.size)
        assertEquals("interrupted", roundTrip.attempts.last().status)
        assertEquals(active.id, roundTrip.attempts.last().id)
        assertEquals(build, roundTrip.attempts.last().buildIdentity)
        assertEquals(newProcess, roundTrip.attempts.last().originProcessId)
    }

    @Test fun decodedTrustAndDetailsCannotBeRewrittenThroughCallerCollections() {
        val details = linkedMapOf<String, Any?>("sftpVersion" to "3")
        val mutableTrust = trust.toMutableMap()
        val root = changedRow(legacy(), mapOf("details" to details)) + ("trustedHosts" to mutableTrust)
        val decoded = SshJournalCodec.decode(root)
        mutableTrust.clear()
        details["sftpVersion"] = "9"
        assertEquals(trust, decoded.trust)
        assertEquals("3", decoded.attempts.first().details["sftpVersion"])
        assertThrows(UnsupportedOperationException::class.java) { (decoded.trust as MutableMap).clear() }
    }

    @Test fun decodedHistoryStillEnforcesIdentityEventAndLaneBounds() {
        val root = currentJournal()
        rejects(root + ("attempts" to List(9) { row(root) }))
        rejects(root + ("attempts" to listOf(row(root), row(root))))
        rejects(changedRow(root, mapOf("details" to mapOf("uploadName" to "cloudy-${UUID(0, 6)}.bin"))))
        rejects(changedRow(root, mapOf("events" to emptyList<Any>())))
        rejects(changedRow(root, mapOf("reason" to "not-current-event")))
    }

    @Test fun allEightAttemptsRemainPresentAndRecoveryDoesNotCreateCapacity() {
        val legacy = SshJournalCodec.decode(legacy())
        val state = SshState(legacy.attempts)
        repeat(3) { index ->
            val active = requireNotNull(state.begin(UUID(0, (index + 6).toLong()).toString(), newProcess,
                "b".repeat(64), SshOperation.EXEC_PTY, build, time))
            assertTrue(state.update(active.id, "failed", "fixture-failed", time = time))
        }
        val restored = SshState(SshJournalCodec.decode(encode(state)).attempts)
        assertFalse(restored.reconcileInterrupted(time))
        assertNull(restored.begin(UUID(0, 9).toString(), newProcess, "b".repeat(64), SshOperation.EXEC_PTY, build, time))
        assertEquals(state.snapshot(), restored.snapshot())
        assertEquals(legacy.attempts, restored.snapshot().take(5))
    }
}
