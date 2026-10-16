package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.util.UUID

internal data class ProfileEditorDraft(
    val editorId: String,
    val owner: WindowOwner,
    val generation: String,
    val version: ConnectionProfileVersion?,
    val protocol: ConnectionProtocol,
    val label: TextFieldValue,
    val host: TextFieldValue,
    val port: TextFieldValue,
) {
    override fun toString(): String = "ProfileEditorDraft(redacted)"
}

internal sealed interface ProfileEditorRestore {
    data object Missing : ProfileEditorRestore
    data class Restored(val draft: ProfileEditorDraft?, val pendingMutation: Boolean) : ProfileEditorRestore
    data object Rejected : ProfileEditorRestore
}

/** Local unfinished editing only. Restored uncertainty never grants permission to repeat a mutation. */
internal object ProfileEditorState {
    private const val KEY = "io.github.baitian6641.cloudy.profile-editor.v1"
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val recordKeys = setOf("schema", "owner", "pending", "draft")
    private val draftKeys = setOf("editor", "generation", "protocol", "version", "label", "labelStart", "labelEnd",
        "host", "hostStart", "hostEnd", "port", "portStart", "portEnd")
    private val versionKeys = setOf("versionGeneration", "versionId", "versionRevision")

    fun newDraft(owner: WindowOwner, generation: String): ProfileEditorDraft {
        requireState(uuid.matches(generation))
        return ProfileEditorDraft(UUID.randomUUID().toString(), owner, generation, null,
            ConnectionProtocol.SSH, field(""), field(""), field("22"))
    }

    fun editDraft(owner: WindowOwner, generation: String, profile: ConnectionProfile): ProfileEditorDraft {
        requireState(owner == profile.owner && uuid.matches(generation))
        return ProfileEditorDraft(UUID.randomUUID().toString(), owner, generation,
            ConnectionProfileVersion.create(generation, profile.id, profile.revision), profile.protocol,
            field(profile.label), field(profile.host), field(profile.port.toString()))
    }

    /** Structural checks preserve accepted composition; semantic endpoint checks belong at Save. */
    fun acceptsField(value: TextFieldValue, limit: Int): Boolean {
        if (limit < 0 || value.text.length > limit || value.selection.start !in 0..value.text.length ||
            value.selection.end !in 0..value.text.length) return false
        var index = 0
        while (index < value.text.length) {
            val char = value.text[index++]
            if (Character.isLowSurrogate(char)) return false
            if (Character.isHighSurrogate(char)) {
                if (index >= value.text.length || !Character.isLowSurrogate(value.text[index++])) return false
            } else if (Character.isISOControl(char) || char == '\u2028' || char == '\u2029') return false
        }
        return true
    }

    fun save(destination: Bundle, owner: WindowOwner, draft: ProfileEditorDraft?, pendingMutation: Boolean) {
        draft?.let { validate(it, owner) }
        // Build the complete private record before replacing its destination key.
        val record = Bundle().apply {
            putInt("schema", 1)
            putString("owner", owner.wireId)
            putBoolean("pending", pendingMutation)
            putBundle("draft", draft?.let { value ->
                Bundle().apply {
                    putString("editor", value.editorId)
                    putString("generation", value.generation)
                    putString("protocol", value.protocol.wireId)
                    putBoolean("version", value.version != null)
                    value.version?.let { version ->
                        putString("versionGeneration", version.generation)
                        putString("versionId", version.id)
                        putLong("versionRevision", version.revision)
                    }
                    writeField(this, "label", value.label)
                    writeField(this, "host", value.host)
                    writeField(this, "port", value.port)
                }
            })
        }
        destination.putBundle(KEY, record)
    }

    fun restore(source: Bundle?, owner: WindowOwner): ProfileEditorRestore {
        if (source == null) return ProfileEditorRestore.Missing
        return try {
            if (!source.containsKey(KEY)) return ProfileEditorRestore.Missing
            val record = raw(source, KEY) as? Bundle ?: return ProfileEditorRestore.Rejected
            requireState(record.keySet() == recordKeys)
            requireState(exact<Int>(record, "schema") == 1 && exact<String>(record, "owner") == owner.wireId)
            val pending = exact<Boolean>(record, "pending")
            val encodedDraft = raw(record, "draft")
            val draft = if (encodedDraft == null) null else {
                requireState(encodedDraft is Bundle)
                readDraft(encodedDraft as Bundle, owner)
            }
            ProfileEditorRestore.Restored(draft, pending)
        } catch (_: RuntimeException) {
            ProfileEditorRestore.Rejected
        }
    }

    private fun readDraft(record: Bundle, owner: WindowOwner): ProfileEditorDraft {
        val hasVersion = exact<Boolean>(record, "version")
        requireState(record.keySet() == if (hasVersion) draftKeys + versionKeys else draftKeys)
        val generation = exact<String>(record, "generation")
        val version = if (hasVersion) ConnectionProfileVersion.create(
            exact<String>(record, "versionGeneration"), exact<String>(record, "versionId"),
            exact<Long>(record, "versionRevision"),
        ) else null
        val protocol = ConnectionProtocol.entries.firstOrNull { it.wireId == exact<String>(record, "protocol") }
        requireState(protocol != null)
        return ProfileEditorDraft(exact<String>(record, "editor"), owner, generation, version,
            checkNotNull(protocol), readField(record, "label", 160), readField(record, "host", 1024),
            readField(record, "port", 5)).also { validate(it, owner) }
    }

    private fun validate(draft: ProfileEditorDraft, owner: WindowOwner) {
        requireState(draft.owner == owner && uuid.matches(draft.editorId) && uuid.matches(draft.generation))
        requireState(owner != WindowOwner.CONSOLE || draft.protocol == ConnectionProtocol.SSH)
        draft.version?.let { version ->
            requireState(version.generation == draft.generation)
            ConnectionProfileVersion.create(version.generation, version.id, version.revision)
        }
        validateField(draft.label, 160)
        validateField(draft.host, 1024)
        validateField(draft.port, 5)
    }

    private fun validateField(value: TextFieldValue, limit: Int) {
        requireState(acceptsField(value, limit))
    }

    private fun readField(record: Bundle, key: String, limit: Int): TextFieldValue {
        val text = exact<String>(record, key)
        val start = exact<Int>(record, key + "Start")
        val end = exact<Int>(record, key + "End")
        // TextFieldValue clamps selections: reject bad serialized offsets before constructing it.
        requireState(text.length <= limit && start in 0..text.length && end in 0..text.length)
        return TextFieldValue(text, TextRange(start, end)).also { validateField(it, limit) }
    }

    private fun writeField(destination: Bundle, key: String, value: TextFieldValue) {
        destination.putString(key, value.text)
        destination.putInt(key + "Start", value.selection.start)
        destination.putInt(key + "End", value.selection.end)
    }

    private fun field(text: String): TextFieldValue = TextFieldValue(text, TextRange(text.length))

    @Suppress("DEPRECATION") // Generic access is necessary to reject Bundle's coercive typed defaults.
    private fun raw(bundle: Bundle, key: String): Any? = bundle.get(key)

    private inline fun <reified T> exact(bundle: Bundle, key: String): T =
        raw(bundle, key) as? T ?: throw IllegalArgumentException("INVALID_EDITOR_STATE")

    private fun requireState(valid: Boolean) { require(valid) { "INVALID_EDITOR_STATE" } }
}
