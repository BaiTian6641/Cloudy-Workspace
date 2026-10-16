package io.github.baitian6641.cloudy.domain

import java.util.UUID

enum class WindowOwner(val wireId: String) {
    CONSOLE("console"),
    DESKTOP("desktop"),
}

data class WindowSnapshot(
    val schemaVersion: Int,
    val owner: String,
    val windowId: String,
    val name: String,
)

enum class WindowNameProblem { BLANK, TOO_LONG, CONTROL_CHARACTER, INVALID_UNICODE }

enum class WindowRestoreProblem { UNKNOWN_SCHEMA, WRONG_OWNER, INVALID_ID, INVALID_NAME }

sealed interface WindowRestore {
    data class Restored(val state: WindowState) : WindowRestore
    data class Rejected(val reason: WindowRestoreProblem) : WindowRestore
}

/** Local logical identity only; Activity instances and live operations have separate lifetimes. */
class WindowState private constructor(
    val owner: WindowOwner,
    val windowId: String,
    val name: String,
) {
    fun renamed(name: String): WindowState {
        require(nameProblem(name) == null) { "Invalid window name" }
        return WindowState(owner, windowId, name.trim())
    }

    fun snapshot(): WindowSnapshot = WindowSnapshot(SCHEMA_VERSION, owner.wireId, windowId, name)

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val MAX_CODE_POINTS = 80
        private const val MAX_UTF16_LENGTH = 160
        private val canonicalId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        fun create(owner: WindowOwner, initialName: String): WindowState {
            require(nameProblem(initialName) == null) { "Invalid window name" }
            return WindowState(owner, UUID.randomUUID().toString(), initialName.trim())
        }

        fun restore(expectedOwner: WindowOwner, snapshot: WindowSnapshot): WindowRestore {
            if (snapshot.schemaVersion != SCHEMA_VERSION) {
                return WindowRestore.Rejected(WindowRestoreProblem.UNKNOWN_SCHEMA)
            }
            if (snapshot.owner != expectedOwner.wireId) {
                return WindowRestore.Rejected(WindowRestoreProblem.WRONG_OWNER)
            }
            // UUID.fromString alone accepts shortened groups on some JVMs.
            if (!canonicalId.matches(snapshot.windowId)) {
                return WindowRestore.Rejected(WindowRestoreProblem.INVALID_ID)
            }
            if (nameProblem(snapshot.name) != null) {
                return WindowRestore.Rejected(WindowRestoreProblem.INVALID_NAME)
            }
            return WindowRestore.Restored(
                WindowState(expectedOwner, snapshot.windowId, snapshot.name.trim()),
            )
        }

        fun nameProblem(name: String): WindowNameProblem? {
            // Bound work before trimming/scanning untrusted restored text.
            if (name.length > MAX_UTF16_LENGTH) return WindowNameProblem.TOO_LONG
            var index = 0
            while (index < name.length) {
                val char = name[index]
                if (Character.isHighSurrogate(char)) {
                    if (index + 1 >= name.length || !Character.isLowSurrogate(name[index + 1])) {
                        return WindowNameProblem.INVALID_UNICODE
                    }
                    index += 2
                } else {
                    if (Character.isLowSurrogate(char)) return WindowNameProblem.INVALID_UNICODE
                    // Titles are single-line; Unicode separators are not ISO control characters.
                    if (Character.isISOControl(char) || char == '\u2028' || char == '\u2029') {
                        return WindowNameProblem.CONTROL_CHARACTER
                    }
                    index++
                }
            }
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return WindowNameProblem.BLANK
            // Count accepted text, so ordinary surrounding spaces do not consume its name budget.
            if (trimmed.codePointCount(0, trimmed.length) > MAX_CODE_POINTS) {
                return WindowNameProblem.TOO_LONG
            }
            return null
        }
    }
}
