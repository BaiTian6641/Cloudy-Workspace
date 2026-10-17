package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import android.os.Parcel
import android.text.SpannableString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileEditorStateTest {
    private val owner = WindowOwner.CONSOLE
    private val generation = UUID.randomUUID().toString()
    private val key = "io.github.baitian6641.cloudy.profile-editor.v1"
    private fun fresh() = ProfileEditorState.newDraft(owner, generation)
    private fun saved(draft: ProfileEditorDraft? = fresh(), pending: Boolean = false): Bundle = Bundle().also {
        ProfileEditorState.save(it, owner, draft, pending)
    }
    private fun record(source: Bundle): Bundle = checkNotNull(source.getBundle(key))
    private fun fields(source: Bundle): Bundle = checkNotNull(record(source).getBundle("draft"))
    private fun restored(source: Bundle): ProfileEditorRestore.Restored =
        ProfileEditorState.restore(source, owner) as ProfileEditorRestore.Restored

    private fun parcelRoundTrip(source: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(source)
            parcel.setDataPosition(0)
            return checkNotNull(parcel.readBundle(javaClass.classLoader))
        } finally { parcel.recycle() }
    }

    @Test fun missingStateIsDistinctFromExplicitClosedEditorAndPendingUncertainty() {
        assertEquals(ProfileEditorRestore.Missing, ProfileEditorState.restore(null, owner))
        assertEquals(ProfileEditorRestore.Missing, ProfileEditorState.restore(Bundle(), owner))
        assertEquals(ProfileEditorRestore.Restored(null, false), restored(saved(null)))
        assertEquals(ProfileEditorRestore.Restored(null, true), restored(parcelRoundTrip(saved(null, true))))
    }

    @Test fun newEditorRoundTripsIncompleteFieldsAndStableIdentityAsPrimitives() {
        val original = fresh()
        assertEquals(owner, original.owner)
        assertEquals(generation, original.generation)
        assertNull(original.version)
        assertEquals("", original.label.text)
        assertEquals("", original.host.text)
        assertEquals("22", original.port.text)
        assertEquals(ConnectionProtocol.SSH, original.protocol)
        assertEquals(4, UUID.fromString(original.editorId).version())
        assertNotEquals(original.editorId, fresh().editorId)
        val incomplete = original.copy(label = TextFieldValue("  "), host = TextFieldValue("https://unfinished/"),
            port = TextFieldValue("abc"))
        val result = restored(parcelRoundTrip(saved(incomplete, true)))
        assertEquals(incomplete, result.draft)
        assertTrue(result.pendingMutation)
    }

    @Test fun editingRetainsExactOriginalStoreVersionWithoutAcceptingAnyMutation() {
        val profile = ConnectionProfile.restore("12345678-1234-4234-8234-123456789abc", Long.MAX_VALUE,
            ConnectionProfileDraft.create(owner, "Existing", ConnectionProtocol.SSH, "example.org", 2222))
        val original = ProfileEditorState.editDraft(owner, generation, profile)
        val result = checkNotNull(restored(parcelRoundTrip(saved(original))).draft)
        assertEquals(original.editorId, result.editorId)
        assertEquals(profile.label, result.label.text)
        assertEquals(profile.host, result.host.text)
        assertEquals("2222", result.port.text)
        val version = checkNotNull(result.version)
        assertEquals(generation, version.generation)
        assertEquals(profile.id, version.id)
        assertEquals(Long.MAX_VALUE, version.revision)
        assertThrows(IllegalArgumentException::class.java) {
            ProfileEditorState.editDraft(WindowOwner.DESKTOP, generation, profile)
        }
    }

    @Test fun selectionsSurviveAndCompositionIsNeverRestored() {
        val composing = TextFieldValue("A😀bc", TextRange(5, 1), TextRange(1, 3))
        assertTrue(ProfileEditorState.acceptsField(composing, 160))
        assertEquals(TextRange(1, 3), composing.composition)
        val result = checkNotNull(restored(parcelRoundTrip(saved(fresh().copy(label = composing)))).draft)
        assertEquals(composing.text, result.label.text)
        assertEquals(TextRange(5, 1), result.label.selection)
        assertNull(result.label.composition)
    }

    @Test fun liveAndSavedFieldsShareStructuralUnicodeAndWholeEditLimits() {
        for ((text, limit) in listOf("😀".repeat(80) to 160, "x".repeat(1024) to 1024, "abcde" to 5,
                "العربية e\u0301" to 160, "" to 0)) {
            assertTrue(ProfileEditorState.acceptsField(TextFieldValue(text), limit))
        }
        for (text in listOf("\uD800", "\uDC00", "a\uD800b", "x\n", "x\r", "\u0000", "\u0085", "x\u2028", "x\u2029")) {
            assertFalse(ProfileEditorState.acceptsField(TextFieldValue(text), 160))
            val state = saved()
            fields(state).putString("label", text)
            assertEquals(ProfileEditorRestore.Rejected, ProfileEditorState.restore(state, owner))
        }
        assertFalse(ProfileEditorState.acceptsField(TextFieldValue("😀".repeat(81)), 160))
        assertFalse(ProfileEditorState.acceptsField(TextFieldValue("x".repeat(1025)), 1024))
        assertFalse(ProfileEditorState.acceptsField(TextFieldValue("123456"), 5))
        assertFalse(ProfileEditorState.acceptsField(TextFieldValue(""), -1))
        val draft = fresh().copy(label = TextFieldValue("😀".repeat(80)), host = TextFieldValue("x".repeat(1024)),
            port = TextFieldValue("abcde"))
        assertEquals(draft, restored(parcelRoundTrip(saved(draft))).draft)
    }

    @Test fun malformedSaveNeverPartiallyReplacesExistingRecord() {
        val original = fresh()
        val destination = saved(original, true)
        val previous = record(destination)
        val invalids = listOf(original.copy(label = TextFieldValue("x".repeat(161))),
            original.copy(host = TextFieldValue("x".repeat(1025))), original.copy(port = TextFieldValue("123456")),
            original.copy(label = TextFieldValue("x\n")), original.copy(editorId = "1-1-1-1-1"),
            original.copy(generation = "12345678-1234-4234-8234-123456789ABC"), original.copy(owner = WindowOwner.DESKTOP),
            original.copy(protocol = ConnectionProtocol.RDP), original.copy(version =
                ConnectionProfileVersion.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1)))
        for (invalid in invalids) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                ProfileEditorState.save(destination, owner, invalid, false)
            }
            assertEquals("INVALID_EDITOR_STATE", error.message)
            assertNull(error.cause)
            assertSame(previous, record(destination))
            assertEquals(ProfileEditorRestore.Restored(original, true), restored(destination))
        }
    }

    @Test fun wrongOwnerSchemaAndOuterRecordTypesAreRejectedAllOrNothing() {
        assertEquals(ProfileEditorRestore.Rejected, ProfileEditorState.restore(saved(), WindowOwner.DESKTOP))
        val changes: List<(Bundle) -> Unit> = listOf(
            { it.putString("schema", "2") }, { it.putLong("schema", 2) }, { it.putInt("schema", 3) },
            { it.putInt("pending", 1) }, { it.remove("pending") }, { it.putString("owner", "CONSOLE") },
            { it.putString("unexpected", "ignored?") }, { it.putString("draft", "not a record") },
        )
        for (change in changes) {
            val state = saved()
            change(record(state))
            assertEquals(ProfileEditorRestore.Rejected, ProfileEditorState.restore(parcelRoundTrip(state), owner))
        }
        val wrongRoot = Bundle().apply { putString(key, "not a record") }
        assertEquals(ProfileEditorRestore.Rejected, ProfileEditorState.restore(wrongRoot, owner))
    }

    @Test fun fieldTypesUnknownKeysAndInvalidSelectionCannotBeCoercedOrClamped() {
        val changes: List<(Bundle) -> Unit> = listOf(
            { it.putString("extra", "value") }, { it.remove("label") }, { it.putInt("host", 123) },
            { it.putCharSequence("label", SpannableString("text")) }, { it.putString("port", null) },
            { it.putLong("labelStart", 0) }, { it.putInt("labelStart", -1) }, { it.putInt("hostEnd", 1) },
            { it.putString("protocol", "SSH") }, { it.putString("protocol", "rdp") },
            { it.putString("label", "x".repeat(161)) }, { it.putString("host", "x".repeat(1025)) },
            { it.putString("port", "123456") }, { it.putBoolean("version", true) },
            { it.putString("versionId", UUID.randomUUID().toString()) },
            { it.putString("generation", "1-1-1-1-1") }, { it.putString("editor", "not-a-uuid") },
        )
        for (change in changes) {
            val state = saved()
            change(fields(state))
            assertEquals(ProfileEditorRestore.Rejected, ProfileEditorState.restore(parcelRoundTrip(state), owner))
        }
    }

    @Test fun persistedVersionRequiresMatchingGenerationCanonicalIdAndLongRevision() {
        val profile = ConnectionProfile.create(ConnectionProfileDraft.create(owner, "Existing", ConnectionProtocol.SSH, "example.org", 22))
        val original = ProfileEditorState.editDraft(owner, generation, profile)
        val changes: List<(Bundle) -> Unit> = listOf(
            { it.putString("versionGeneration", UUID.randomUUID().toString()) },
            { it.putString("versionId", "12345678-1234-1234-8234-123456789abc") },
            { it.putLong("versionRevision", 0) }, { it.putInt("versionRevision", 1) },
            { it.putString("versionRevision", "1") }, { it.putBoolean("version", false) },
        )
        for (change in changes) {
            val state = saved(original)
            change(fields(state))
            assertEquals(ProfileEditorRestore.Rejected, ProfileEditorState.restore(parcelRoundTrip(state), owner))
        }
    }

    @Test fun tagsAndFavoriteRoundTripAndLegacySchemaRestoresWithDefaults() {
        val organized = fresh().copy(label = TextFieldValue("Workstation"),
            tags = TextFieldValue("work, rack", TextRange(4)), favorite = true)
        val result = checkNotNull(restored(parcelRoundTrip(saved(organized))).draft)
        assertEquals(organized, result)
        assertEquals("work, rack", result.tags.text)
        assertEquals(TextRange(4), result.tags.selection)
        assertTrue(result.favorite)

        // A schema-1 in-progress edit from before organization fields restores with defaults.
        val legacy = Bundle().apply {
            putBundle(key, Bundle().apply {
            putInt("schema", 1)
            putString("owner", owner.wireId)
            putBoolean("pending", false)
            putBundle("draft", Bundle().apply {
                putString("editor", UUID.randomUUID().toString())
                putString("generation", generation)
                putString("protocol", ConnectionProtocol.SSH.wireId)
                putBoolean("version", false)
                putString("label", "Legacy edit"); putInt("labelStart", 3); putInt("labelEnd", 3)
                putString("host", "legacy.example"); putInt("hostStart", 0); putInt("hostEnd", 0)
                putString("port", "22"); putInt("portStart", 2); putInt("portEnd", 2)
            })
            })
        }
        val legacyResult = checkNotNull(restored(parcelRoundTrip(legacy)).draft)
        assertEquals("Legacy edit", legacyResult.label.text)
        assertEquals("", legacyResult.tags.text)
        assertFalse(legacyResult.favorite)
        assertNull(legacyResult.version)

        // A schema-1 record carrying organization keys is foreign and rejects rather than guesses.
        val foreign = Bundle(legacy).apply { fields(this).putBoolean("favorite", true) }
        assertEquals(ProfileEditorRestore.Rejected, ProfileEditorState.restore(parcelRoundTrip(foreign), owner))
    }

    @Test fun desktopProtocolsRestoreButConsoleCannotAdoptThemAndTextRemainsRedacted() {
        for (protocol in ConnectionProtocol.entries) {
            val draft = ProfileEditorState.newDraft(WindowOwner.DESKTOP, generation).copy(protocol = protocol,
                label = TextFieldValue("private label"), host = TextFieldValue("private.example"))
            val state = Bundle().also { ProfileEditorState.save(it, WindowOwner.DESKTOP, draft, false) }
            val result = ProfileEditorState.restore(parcelRoundTrip(state), WindowOwner.DESKTOP) as ProfileEditorRestore.Restored
            assertEquals(protocol, checkNotNull(result.draft).protocol)
            assertEquals("ProfileEditorDraft(redacted)", draft.toString())
            assertFalse(result.toString().contains("private"))
        }
    }
}
