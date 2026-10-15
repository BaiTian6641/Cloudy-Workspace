package dev.cloudy.qualification.baseline

import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalStateTest {
    private val process = UUID(0, 100).toString()
    private val build = SshBuildIdentity("0.7.0", 9, UUID(0, 101).toString())
    private val config = "a".repeat(64)
    private val time = TransferTime("2026-10-15T13:00:00Z", 10)
    private val key = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32))
    private fun identity(index: Long) = TerminalIdentity(UUID(0, index).toString(), UUID(1, index).toString())
    private fun started(): TerminalState = TerminalState().also { assertTrue(it.begin(identity(1), process, config, build, time)) }
    private fun opened(state: TerminalState) {
        state.update(identity(1), "host-key-verified", null, time, key)
        state.update(identity(1), "authenticated", null, time, key, true)
        state.update(identity(1), "open", null, time, key, true)
    }

    @Test fun capacityRetainsAllEvidenceAndNeverReusesAnAttempt() {
        val state = TerminalState()
        repeat(TerminalState.ATTEMPT_LIMIT) { index ->
            val id = identity(index + 1L)
            assertTrue(state.begin(id, process, config, build, time))
            assertFalse(state.begin(identity(99), process, config, build, time))
            state.update(id, "failed", "fixture-refusal", time)
            assertFalse(state.begin(id, process, config, build, time))
        }
        val saved = state.snapshot()
        assertFalse(state.begin(identity(99), process, config, build, time))
        assertEquals(saved, state.snapshot())
    }

    @Test fun processRecoveryKeepsOriginalIdentityBuildAndAuthWithoutReplaying() {
        val state = started(); opened(state)
        state.update(identity(1), "open", null, time, details = mapOf("inputBytes" to "12"))
        val original = state.snapshot().single()
        val restored = TerminalState(state.snapshot())
        restored.reconcileInterrupted(TransferTime("2026-10-16T00:00:00Z", 1))
        val row = restored.snapshot().single()
        assertEquals(original.identity, row.identity)
        assertEquals(original.originProcessId, row.originProcessId)
        assertEquals(original.buildIdentity, row.buildIdentity)
        assertEquals(original.details, row.details)
        assertTrue(row.authenticated)
        assertEquals("interrupted", row.status)
        val once = restored.snapshot()
        restored.reconcileInterrupted(time)
        assertEquals(once, restored.snapshot())
    }

    @Test fun staleRendererAndLateWritesCannotMutateHistory() {
        val state = started(); val before = state.snapshot()
        val stale = identity(1).copy(attachmentId = UUID(2, 1).toString())
        assertThrows(IllegalArgumentException::class.java) { state.update(stale, "failed", null, time) }
        assertEquals(before, state.snapshot())
        state.update(identity(1), "interrupted", "owner-no-longer-visible", time)
        val ended = state.snapshot()
        assertThrows(IllegalArgumentException::class.java) { state.update(identity(1), "open", null, time) }
        assertEquals(ended, state.snapshot())
    }

    @Test fun hostIdentityIsImmutableAndAuthenticatedHistoryCannotBecomePreauth() {
        val state = started(); opened(state)
        val different = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
        assertThrows(IllegalArgumentException::class.java) {
            state.update(identity(1), "failed", "rekey-rejected", time, different)
        }
        assertThrows(IllegalArgumentException::class.java) { state.update(identity(1), "trust-required", null, time) }
        assertThrows(IllegalArgumentException::class.java) { state.update(identity(1), "connecting", null, time) }
        state.update(identity(1), "failed", "rekey-rejected", time)
        val row = state.snapshot().single()
        assertEquals(key, row.observedHostKey)
        assertTrue(row.authenticated)
        assertThrows(IllegalArgumentException::class.java) { TerminalState(listOf(row.copy(authenticated = false))) }
    }

    @Test fun immutableSnapshotsAndEventOverflowRetainExplicitBounds() {
        val state = started(); opened(state)
        val details = mutableMapOf("stdoutBytes" to "8")
        state.update(identity(1), "open", null, time, details = details)
        val before = state.snapshot()
        details["stdoutBytes"] = "999"
        repeat(100) { state.update(identity(1), "open", null, time) }
        val row = state.snapshot().single()
        assertEquals("8", row.details["stdoutBytes"])
        assertEquals(5, before.single().events.size)
        assertEquals(TerminalState.EVENT_LIMIT, row.events.size)
        assertEquals(9L, row.droppedEvents)
        assertThrows(UnsupportedOperationException::class.java) { (row.details as MutableMap)["stdoutBytes"] = "9" }
        assertThrows(UnsupportedOperationException::class.java) { (row.events as MutableList).clear() }
        assertEquals(row, TerminalState(listOf(row)).snapshot().single())
    }

    @Test fun journalRejectsTranscriptsInvalidCountsAndContradictoryHistory() {
        val state = started(); val row = state.snapshot().single()
        listOf(mapOf("transcript" to "secret"), mapOf("inputBytes" to "-1"),
            mapOf("stdoutBytes" to "9223372036854775808"), mapOf("stderrBytes" to "1.0"),
            mapOf("kex" to "bad\nname"), mapOf("rendererArrayBuffer" to "yes")).forEach { fields ->
            assertThrows(IllegalArgumentException::class.java) { state.update(identity(1), "connecting", null, time, details = fields) }
        }
        assertThrows(IllegalArgumentException::class.java) { TerminalState(listOf(row, row.copy(identity = identity(2)))) }
        assertThrows(IllegalArgumentException::class.java) { TerminalState(listOf(row.copy(status = "closed"))) }
        assertEquals(row, state.snapshot().single())
    }

    @Test fun rendererIdentityIsCopiedAtAdmissionAndSurvivesProcessRecovery() {
        val renderer = mutableMapOf("rendererProvider" to "org.example.webview", "rendererVersion" to "1.2.3",
            "rendererMessageListener" to "true", "rendererArrayBuffer" to "true")
        val state = TerminalState()
        assertTrue(state.begin(identity(1), process, config, build, time, renderer))
        renderer["rendererVersion"] = "changed-after-admission"
        val restored = TerminalState(state.snapshot()); restored.reconcileInterrupted(time)
        assertEquals("1.2.3", restored.snapshot().single().details["rendererVersion"])
        assertEquals("org.example.webview", restored.snapshot().single().details["rendererProvider"])
    }
}
