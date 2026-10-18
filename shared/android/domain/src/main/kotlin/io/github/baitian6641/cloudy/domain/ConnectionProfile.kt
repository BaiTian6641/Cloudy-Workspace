package io.github.baitian6641.cloudy.domain

import java.util.UUID

enum class ConnectionProtocol(val wireId: String) {
    SSH("ssh"), RDP("rdp"), VNC("vnc"), STREAM("stream"),
}

enum class ConnectionProfileValidationCode {
    INVALID_LABEL, INVALID_PROTOCOL, INVALID_HOST, INVALID_PORT,
    INVALID_ID, INVALID_REVISION, WRONG_OWNER, INVALID_TAGS, INVALID_LAST_USED, INVALID_JUMP, INVALID_KEY_REFERENCE,
}

class ConnectionProfileValidationException(val code: ConnectionProfileValidationCode) :
    IllegalArgumentException(code.name)

/** Validated non-secret endpoint fields; no enrollment, trust, credential or handoff authority. */
class ConnectionProfileDraft private constructor(
    val owner: WindowOwner,
    val label: String,
    val protocol: ConnectionProtocol,
    val host: String,
    val port: Int,
    val tags: Set<String>,
    val favorite: Boolean,
    /** One-hop jump host reference; resolved and validated against the current catalog by the store. */
    val jumpProfileId: String?,
    /** Optional content-URI reference to an app-granted key document; never key bytes. */
    val keyUri: String?,
) {
    override fun toString(): String = "ConnectionProfileDraft(redacted)"

    companion object {
        const val MAX_TAGS = 16
        const val MAX_TAG_UNITS = 48

        fun create(
            owner: WindowOwner, label: String, protocol: ConnectionProtocol, host: String, port: Int,
            tags: Set<String> = emptySet(), favorite: Boolean = false, jumpProfileId: String? = null,
            keyUri: String? = null,
        ): ConnectionProfileDraft {
            if (jumpProfileId != null && !canonicalId(jumpProfileId)) {
                reject(ConnectionProfileValidationCode.INVALID_JUMP)
            }
            if (keyUri != null && !canonicalKeyUri(keyUri)) {
                reject(ConnectionProfileValidationCode.INVALID_KEY_REFERENCE)
            }
            // Endpoint labels intentionally share the existing native-window Unicode/name policy.
            if (WindowState.nameProblem(label) != null) reject(ConnectionProfileValidationCode.INVALID_LABEL)
            if (owner == WindowOwner.CONSOLE && protocol != ConnectionProtocol.SSH) {
                reject(ConnectionProfileValidationCode.INVALID_PROTOCOL)
            }
            if (port !in 1..65535) reject(ConnectionProfileValidationCode.INVALID_PORT)
            val canonicalHost = try {
                EndpointHost.canonicalize(host)
            } catch (_: IllegalArgumentException) {
                reject(ConnectionProfileValidationCode.INVALID_HOST)
            }
            return ConnectionProfileDraft(owner, label.trim(), protocol, canonicalHost, port,
                normalizeTags(tags), favorite, jumpProfileId, keyUri)
        }

        /**
         * Tags are user organization metadata, never trust or credential data. They trim, dedupe
         * case-insensitively (first occurrence wins) and keep user order. The newline-joined store
         * encoding relies on this control-character rejection; do not relax it without a new encoding.
         */
        private fun normalizeTags(tags: Set<String>): Set<String> {
            if (tags.size > MAX_TAGS) reject(ConnectionProfileValidationCode.INVALID_TAGS)
            val ordered = LinkedHashSet<String>()
            val folded = HashSet<String>()
            for (raw in tags) {
                val tag = raw.trim()
                if (tag.isEmpty() || tag.length > MAX_TAG_UNITS) reject(ConnectionProfileValidationCode.INVALID_TAGS)
                var index = 0
                while (index < tag.length) {
                    val char = tag[index++]
                    if (Character.isLowSurrogate(char)) reject(ConnectionProfileValidationCode.INVALID_TAGS)
                    if (Character.isHighSurrogate(char)) {
                        if (index >= tag.length || !Character.isLowSurrogate(tag[index++])) {
                            reject(ConnectionProfileValidationCode.INVALID_TAGS)
                        }
                    } else if (Character.isISOControl(char) || char == '\u2028' || char == '\u2029') {
                        reject(ConnectionProfileValidationCode.INVALID_TAGS)
                    }
                }
                if (folded.add(tag.lowercase())) ordered.add(tag)
            }
            return java.util.Collections.unmodifiableSet(ordered)
        }
    }
}

/** Local profile identity. Storage owns atomic revision comparisons and app-private persistence. */
class ConnectionProfile private constructor(
    val id: String,
    val revision: Long,
    val draft: ConnectionProfileDraft,
    val lastUsedAt: Long,
) {
    val owner: WindowOwner get() = draft.owner
    val label: String get() = draft.label
    val protocol: ConnectionProtocol get() = draft.protocol
    val host: String get() = draft.host
    val port: Int get() = draft.port
    val tags: Set<String> get() = draft.tags
    val favorite: Boolean get() = draft.favorite
    val jumpProfileId: String? get() = draft.jumpProfileId
    val keyUri: String? get() = draft.keyUri

    /** Editable-field replacement keeps runtime metadata such as the last-used marker. */
    fun revised(draft: ConnectionProfileDraft): ConnectionProfile {
        if (draft.owner != owner) reject(ConnectionProfileValidationCode.WRONG_OWNER)
        if (draft.jumpProfileId == id) reject(ConnectionProfileValidationCode.INVALID_JUMP)
        if (revision == Long.MAX_VALUE) reject(ConnectionProfileValidationCode.INVALID_REVISION)
        return ConnectionProfile(id, revision + 1, draft, lastUsedAt)
    }

    /** A recorded connect attempt mutates only the marker; it never edits endpoint fields. */
    fun recordedUse(atEpochSeconds: Long): ConnectionProfile {
        if (atEpochSeconds < 1) reject(ConnectionProfileValidationCode.INVALID_LAST_USED)
        if (revision == Long.MAX_VALUE) reject(ConnectionProfileValidationCode.INVALID_REVISION)
        return ConnectionProfile(id, revision + 1, draft, atEpochSeconds)
    }

    override fun toString(): String = "ConnectionProfile(redacted)"

    companion object {
        private val identifier = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

        fun create(draft: ConnectionProfileDraft): ConnectionProfile =
            ConnectionProfile(UUID.randomUUID().toString(), 1, draft, 0)

        fun restore(id: String, revision: Long, draft: ConnectionProfileDraft, lastUsedAt: Long = 0): ConnectionProfile {
            if (!identifier.matches(id)) reject(ConnectionProfileValidationCode.INVALID_ID)
            if (revision < 1) reject(ConnectionProfileValidationCode.INVALID_REVISION)
            if (lastUsedAt < 0) reject(ConnectionProfileValidationCode.INVALID_LAST_USED)
            return ConnectionProfile(id, revision, draft, lastUsedAt)
        }
    }
}

private val profileIdentifier = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

private fun canonicalId(value: String): Boolean = profileIdentifier.matches(value)

private fun canonicalKeyUri(value: String): Boolean {
    if (value.length !in 8..2048 || !value.startsWith("content://")) return false
    for (char in value) {
        if (Character.isISOControl(char) || char == '\u2028' || char == '\u2029') return false
    }
    return true
}

private fun reject(code: ConnectionProfileValidationCode): Nothing =
    throw ConnectionProfileValidationException(code)
