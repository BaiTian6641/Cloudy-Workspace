package io.github.baitian6641.cloudy.domain

import java.util.UUID

enum class ConnectionProtocol(val wireId: String) {
    SSH("ssh"), RDP("rdp"), VNC("vnc"), STREAM("stream"),
}

enum class ConnectionProfileValidationCode {
    INVALID_LABEL, INVALID_PROTOCOL, INVALID_HOST, INVALID_PORT,
    INVALID_ID, INVALID_REVISION, WRONG_OWNER,
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
) {
    override fun toString(): String = "ConnectionProfileDraft(redacted)"

    companion object {
        fun create(
            owner: WindowOwner, label: String, protocol: ConnectionProtocol, host: String, port: Int,
        ): ConnectionProfileDraft {
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
            return ConnectionProfileDraft(owner, label.trim(), protocol, canonicalHost, port)
        }
    }
}

/** Local profile identity. Storage owns atomic revision comparisons and app-private persistence. */
class ConnectionProfile private constructor(
    val id: String,
    val revision: Long,
    val draft: ConnectionProfileDraft,
) {
    val owner: WindowOwner get() = draft.owner
    val label: String get() = draft.label
    val protocol: ConnectionProtocol get() = draft.protocol
    val host: String get() = draft.host
    val port: Int get() = draft.port

    fun revised(draft: ConnectionProfileDraft): ConnectionProfile {
        if (draft.owner != owner) reject(ConnectionProfileValidationCode.WRONG_OWNER)
        if (revision == Long.MAX_VALUE) reject(ConnectionProfileValidationCode.INVALID_REVISION)
        return ConnectionProfile(id, revision + 1, draft)
    }

    override fun toString(): String = "ConnectionProfile(redacted)"

    companion object {
        private val identifier = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

        fun create(draft: ConnectionProfileDraft): ConnectionProfile =
            ConnectionProfile(UUID.randomUUID().toString(), 1, draft)

        fun restore(id: String, revision: Long, draft: ConnectionProfileDraft): ConnectionProfile {
            if (!identifier.matches(id)) reject(ConnectionProfileValidationCode.INVALID_ID)
            if (revision < 1) reject(ConnectionProfileValidationCode.INVALID_REVISION)
            return ConnectionProfile(id, revision, draft)
        }
    }
}

private fun reject(code: ConnectionProfileValidationCode): Nothing =
    throw ConnectionProfileValidationException(code)
