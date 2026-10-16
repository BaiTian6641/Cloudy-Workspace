package io.github.baitian6641.cloudy.domain

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

enum class HandoffAction(val wireId: String) {
    OPEN_CONSOLE("open-console"), OPEN_DESKTOP("open-desktop"), OPEN_FILES("open-files"),
}

enum class HandoffValidationCode {
    UNKNOWN_VERSION, INVALID_REQUEST_ID, INVALID_TIMESTAMP, INVALID_LIFETIME,
    INVALID_ACTION, INVALID_RESOURCE_ID,
}

class HandoffValidationException(val code: HandoffValidationCode) :
    IllegalArgumentException(code.name)

enum class HandoffFreshness { CURRENT, NOT_YET_VALID, EXPIRED }

/** Validated syntax and time bounds only: this is not authorization or replay admission. */
class HandoffEnvelope private constructor(
    val version: Int,
    val requestId: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val action: HandoffAction,
    val hostId: String,
    val environmentId: String,
    val workspaceId: String,
    val resourceId: String,
) {
    fun freshAt(now: Instant): HandoffFreshness = when {
        !now.isBefore(expiresAt) -> HandoffFreshness.EXPIRED
        // Duration avoids overflowing extreme Instant values when applying clock skew.
        Duration.between(now, createdAt) > Duration.ofSeconds(5) -> HandoffFreshness.NOT_YET_VALID
        else -> HandoffFreshness.CURRENT
    }

    override fun toString(): String = "HandoffEnvelope(redacted)"

    companion object {
        private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val identifier = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")
        private val timestamp = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})")
        private val minimum = Instant.parse("0000-01-01T00:00:00Z")
        private val maximum = Instant.parse("9999-12-31T23:59:59.999999999Z")

        fun create(
            version: Int, requestId: String, createdAt: String, expiresAt: String,
            action: String, hostId: String, environmentId: String,
            workspaceId: String, resourceId: String,
        ): HandoffEnvelope {
            if (version != 1) reject(HandoffValidationCode.UNKNOWN_VERSION)
            if (!uuid.matches(requestId)) reject(HandoffValidationCode.INVALID_REQUEST_ID)
            val created = parseTimestamp(createdAt)
            val expires = parseTimestamp(expiresAt)
            val lifetime = Duration.between(created, expires)
            if (lifetime <= Duration.ZERO || lifetime > Duration.ofSeconds(120)) {
                reject(HandoffValidationCode.INVALID_LIFETIME)
            }
            val parsedAction = HandoffAction.entries.firstOrNull { it.wireId == action }
                ?: reject(HandoffValidationCode.INVALID_ACTION)
            if (listOf(hostId, environmentId, workspaceId, resourceId).any { !identifier.matches(it) }) {
                reject(HandoffValidationCode.INVALID_RESOURCE_ID)
            }
            return HandoffEnvelope(version, requestId.lowercase(Locale.ROOT), created, expires,
                parsedAction, hostId, environmentId, workspaceId, resourceId)
        }

        private fun parseTimestamp(value: String): Instant {
            // RFC 3339 runtime profile: bounded precision, known offset, strict calendar.
            if (value.length > 40 || !timestamp.matches(value) || value.endsWith("-00:00")) {
                reject(HandoffValidationCode.INVALID_TIMESTAMP)
            }
            val instant = try {
                OffsetDateTime.parse(value).toInstant()
            } catch (_: DateTimeException) {
                reject(HandoffValidationCode.INVALID_TIMESTAMP)
            }
            // Canonical Instant.toString() must remain within the same four-digit-year profile.
            if (instant < minimum || instant > maximum) reject(HandoffValidationCode.INVALID_TIMESTAMP)
            return instant
        }

        private fun reject(code: HandoffValidationCode): Nothing = throw HandoffValidationException(code)
    }
}
