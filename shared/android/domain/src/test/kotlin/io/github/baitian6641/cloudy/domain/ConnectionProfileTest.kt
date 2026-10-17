package io.github.baitian6641.cloudy.domain

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProfileTest {
    private fun draft(
        owner: WindowOwner = WindowOwner.CONSOLE,
        label: String = "  Local endpoint  ", protocol: ConnectionProtocol = ConnectionProtocol.SSH,
        host: String = "EXAMPLE.org.", port: Int = 22,
        tags: Set<String> = emptySet(), favorite: Boolean = false,
    ) = ConnectionProfileDraft.create(owner, label, protocol, host, port, tags, favorite)

    private fun rejected(code: ConnectionProfileValidationCode, block: () -> Unit) {
        val error = assertThrows(ConnectionProfileValidationException::class.java, block)
        assertEquals(code, error.code)
        assertEquals(code.name, error.message)
        assertNull(error.cause)
        assertEquals(0, error.suppressed.size)
    }

    @Test fun newProfilesHaveIndependentCanonicalV4IdsAndValidatedDelegatingFields() {
        val first = ConnectionProfile.create(draft())
        val second = ConnectionProfile.create(draft())
        val id = UUID.fromString(first.id)
        assertEquals(id.toString(), first.id)
        assertEquals(4, id.version())
        assertEquals(2, id.variant())
        assertNotEquals(first.id, second.id)
        assertEquals(1L, first.revision)
        assertEquals(WindowOwner.CONSOLE, first.owner)
        assertEquals("Local endpoint", first.label)
        assertEquals(ConnectionProtocol.SSH, first.protocol)
        assertEquals("example.org", first.host)
        assertEquals(22, first.port)
        assertEquals(first.host, first.draft.host)
    }

    @Test fun protocolPolicyIsExplicitForEachAppAndWireIdsStayDistinct() {
        assertEquals(listOf("ssh", "rdp", "vnc", "stream"), ConnectionProtocol.entries.map { it.wireId })
        for (protocol in ConnectionProtocol.entries) {
            assertEquals(protocol, draft(owner = WindowOwner.DESKTOP, protocol = protocol).protocol)
            if (protocol != ConnectionProtocol.SSH) {
                rejected(ConnectionProfileValidationCode.INVALID_PROTOCOL) { draft(protocol = protocol) }
            }
        }
    }

    @Test fun labelsIntentionallyShareExistingWindowUnicodeAndLengthPolicy() {
        for (label in listOf("  Work  ", "字".repeat(80), "😀".repeat(80), "e\u0301 👨‍👩‍👧‍👦 العربية")) {
            assertNull(WindowState.nameProblem(label))
            assertEquals(label.trim(), draft(label = label).label)
        }
        for (label in listOf("", "  ", "字".repeat(81), "😀".repeat(81), "x\n", "x\u0085",
                "x\u2028y", "x\u2029y", "\uD800", "\uDC00", "a".repeat(161))) {
            assertTrue(WindowState.nameProblem(label) != null)
            rejected(ConnectionProfileValidationCode.INVALID_LABEL) { draft(label = label) }
        }
    }

    @Test fun hostErrorsAreSanitizedAndPortsKeepExactRange() {
        assertEquals("xn--bcher-kva.example", draft(host = "bücher.example").host)
        assertEquals("fe80:0:0:0:0:0:0:1%Eth_0", draft(host = "[FE80::1%Eth_0]").host)
        for (host in listOf("", "https://private.example", "user@private.example", "01.2.3.4", "[::1]:22")) {
            rejected(ConnectionProfileValidationCode.INVALID_HOST) { draft(host = host) }
        }
        assertEquals(1, draft(port = 1).port)
        assertEquals(65535, draft(port = 65535).port)
        for (port in listOf(Int.MIN_VALUE, -1, 0, 65536, Int.MAX_VALUE)) {
            rejected(ConnectionProfileValidationCode.INVALID_PORT) { draft(port = port) }
        }
    }

    @Test fun revisionPreservesIdentityAndDoesNotModifyTheOriginal() {
        val first = ConnectionProfile.create(draft())
        val second = first.revised(draft(label = "Renamed", host = "other.example", port = 2222))
        assertEquals(first.id, second.id)
        assertEquals(1L, first.revision)
        assertEquals(2L, second.revision)
        assertEquals("Local endpoint", first.label)
        assertEquals("Renamed", second.label)
        assertEquals("other.example", second.host)
        assertEquals(2222, second.port)
        assertEquals(first.owner, second.owner)
        rejected(ConnectionProfileValidationCode.WRONG_OWNER) {
            first.revised(draft(owner = WindowOwner.DESKTOP))
        }
    }

    @Test fun restoredIdsRequireCanonicalVersion4AndRfcVariant() {
        val id = "12345678-1234-4234-8234-123456789abc"
        val restored = ConnectionProfile.restore(id, 7, draft())
        assertEquals(id, restored.id)
        assertEquals(7L, restored.revision)
        for (invalid in listOf("", "1-1-1-1-1", id.uppercase(), id.replace("4234", "1234"),
                id.replace("8234", "7234"), "$id ", "g2345678-1234-4234-8234-123456789abc", "a".repeat(1000))) {
            rejected(ConnectionProfileValidationCode.INVALID_ID) { ConnectionProfile.restore(invalid, 1, draft()) }
        }
    }

    @Test fun revisionCannotBeNonpositiveOrWrapAround() {
        val id = ConnectionProfile.create(draft()).id
        for (revision in listOf(Long.MIN_VALUE, -1L, 0L)) {
            rejected(ConnectionProfileValidationCode.INVALID_REVISION) { ConnectionProfile.restore(id, revision, draft()) }
        }
        val last = ConnectionProfile.restore(id, Long.MAX_VALUE, draft())
        rejected(ConnectionProfileValidationCode.INVALID_REVISION) { last.revised(draft()) }
        val penultimate = ConnectionProfile.restore(id, Long.MAX_VALUE - 1, draft())
        assertEquals(Long.MAX_VALUE, penultimate.revised(draft()).revision)
    }

    @Test fun tagsNormalizeDedupeAndValidate() {
        val tagged = draft(tags = linkedSetOf("  Work ", "work", "Lab ", "lab", "🧪"))
        assertEquals(linkedSetOf("Work", "Lab", "🧪"), tagged.tags)
        assertEquals(linkedSetOf("Work", "Lab", "🧪").toList(), tagged.tags.toList())
        val sixteen = (1..16).map { "t$it" }.toSet()
        assertEquals(16, draft(tags = sixteen).tags.size)
        rejected(ConnectionProfileValidationCode.INVALID_TAGS) {
            draft(tags = (1..17).map { "t$it" }.toSet())
        }
        for (tags in listOf(setOf(" "), setOf("ok", "  "), setOf("a".repeat(49)),
                setOf("x\ny"), setOf("x\u2028y"), setOf("x\u2029y"), setOf("\uD800"), setOf("\uDC00"))) {
            rejected(ConnectionProfileValidationCode.INVALID_TAGS) { draft(tags = tags) }
        }
        assertEquals("a".repeat(48), draft(tags = setOf("a".repeat(48))).tags.single())
        assertEquals(emptySet<String>(), draft().tags)
        assertEquals(false, draft().favorite)
        assertEquals(true, draft(favorite = true).favorite)
    }

    @Test fun lastUsedMarkerStartsNeverAndRecordsUseWithoutEditingEndpoint() {
        val profile = ConnectionProfile.create(draft(tags = setOf("work"), favorite = true))
        assertEquals(0L, profile.lastUsedAt)
        val used = profile.recordedUse(1_782_200_000L)
        assertEquals(profile.id, used.id)
        assertEquals(2L, used.revision)
        assertEquals(1_782_200_000L, used.lastUsedAt)
        assertEquals(profile.draft.label, used.label)
        assertEquals(setOf("work"), used.tags)
        assertEquals(true, used.favorite)
        // Editing endpoint fields keeps the runtime marker; recording use keeps edited fields.
        val renamed = used.revised(draft(label = "Renamed", tags = emptySet(), favorite = false))
        assertEquals(1_782_200_000L, renamed.lastUsedAt)
        assertEquals(3L, renamed.revision)
        assertEquals(emptySet<String>(), renamed.tags)
        assertEquals(false, renamed.favorite)
        // A newer recording replaces the marker; the previous value is not retained.
        assertEquals(1_782_200_100L, renamed.recordedUse(1_782_200_100L).lastUsedAt)
        rejected(ConnectionProfileValidationCode.INVALID_LAST_USED) { profile.recordedUse(0L) }
        rejected(ConnectionProfileValidationCode.INVALID_LAST_USED) { profile.recordedUse(-5L) }
        rejected(ConnectionProfileValidationCode.INVALID_LAST_USED) {
            ConnectionProfile.restore(profile.id, 1, profile.draft, -1L)
        }
        val last = ConnectionProfile.restore(profile.id, Long.MAX_VALUE, profile.draft, 9L)
        rejected(ConnectionProfileValidationCode.INVALID_REVISION) { last.recordedUse(10L) }
        assertEquals(9L, last.lastUsedAt)
    }

    @Test fun stringRepresentationsDoNotRevealEndpointOrLocalIdentifiers() {
        val value = ConnectionProfile.create(draft(label = "Private label", host = "private.example",
            tags = setOf("secret-team")))
        assertEquals("ConnectionProfile(redacted)", value.toString())
        assertEquals("ConnectionProfileDraft(redacted)", value.draft.toString())
        for (text in listOf(value.toString(), value.draft.toString())) {
            assertFalse(text.contains(value.id))
            assertFalse(text.contains(value.label))
            assertFalse(text.contains(value.host))
            assertFalse(text.contains("secret-team"))
        }
    }
}
