package dev.cloudy.qualification.baseline

import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalJournalCodecTest {
    private val process = UUID(0, 100).toString()
    private val identity = TerminalIdentity(UUID(0, 1).toString(), UUID(0, 2).toString())
    private val time = TransferTime("2026-10-15T13:00:00Z", 10)
    private val key = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32))
    private fun root(): Map<String, Any?> {
        val state = TerminalState()
        state.begin(identity, process, "a".repeat(64), SshBuildIdentity("0.7.0", 9, UUID(0, 101).toString()), time)
        state.update(identity, "trust-required", "independent-host-key-matched", time, key)
        return TerminalJournalCodec.encode(process, 2, time.deviceUtc, state, mapOf("b".repeat(64) to key))
    }
    private fun row(root: Map<String, Any?>): Map<String, Any?> =
        ((root.getValue("attempts") as List<*>).single() as Map<*, *>).entries.associate { it.key as String to it.value }
    private fun rejects(root: Map<String, Any?>) {
        assertThrows(IllegalArgumentException::class.java) { TerminalJournalCodec.decode(root) }
    }

    @Test fun historyRoundTripPreservesExecutingBuildRatherThanCurrentWriterMetadata() {
        val root = root(); val decoded = TerminalJournalCodec.decode(root)
        val later = TerminalJournalCodec.encode(UUID(0, 999).toString(), 1, time.deviceUtc,
            TerminalState(decoded.attempts), decoded.trust)
        assertEquals(decoded, TerminalJournalCodec.decode(later))
        assertEquals("0.7.0", decoded.attempts.single().buildIdentity.versionName)
        assertEquals(process, decoded.attempts.single().originProcessId)
    }

    @Test fun strictSchemaRejectsExtraMissingAndCoercedFields() {
        val root = root()
        root.keys.forEach { rejects(root - it) }
        rejects(root + ("unexpected" to "unreviewed"))
        listOf<Any>("1", 1.0, true, 2).forEach { rejects(root + ("schemaVersion" to it)) }
        listOf<Any>("1", 1.0, true, -1L).forEach { rejects(root + ("revision" to it)) }
        val row = row(root)
        row.keys.forEach { rejects(root + ("attempts" to listOf(row - it))) }
        rejects(root + ("attempts" to listOf(row + ("transcript" to "private"))))
        rejects(root + ("attempts" to listOf(row + ("authenticated" to "false"))))
        rejects(root + ("attempts" to listOf(row + ("droppedEvents" to 0.0))))
    }

    @Test fun duplicateSessionsInvalidTrustAndFalseProviderReportsFailClosed() {
        val root = root()
        rejects(root + ("attempts" to listOf(row(root), row(root))))
        rejects(root + ("trustedHosts" to mapOf("b".repeat(64) to "bad")))
        rejects(root + ("trustedHosts" to mapOf("host" to key)))
        val providers = root + mapOf("providerNamesAtRuntimeCreation" to listOf("AndroidOpenSSL"),
            "providerNamesNow" to listOf("AndroidOpenSSL"), "providerOrderUnchanged" to true)
        assertEquals(TerminalJournalCodec.decode(root), TerminalJournalCodec.decode(providers))
        rejects(providers + ("providerOrderUnchanged" to false))
        rejects(providers - "providerNamesNow")
        rejects(providers + ("providerNamesNow" to listOf("other")))
    }

    @Test fun eventAndBuildSchemaCannotCarryHiddenFields() {
        val root = root(); val row = row(root)
        val events = row.getValue("events") as List<*>
        val first = events.first() as Map<*, *>
        rejects(root + ("attempts" to listOf(row + ("events" to listOf(first + ("command" to "secret"), events.last())))))
        val build = row.getValue("buildIdentity") as Map<*, *>
        val changed = row + ("buildIdentity" to (build + ("sourceCommit" to "unbound")))
        rejects(root + ("attempts" to listOf(changed)))
    }
}
