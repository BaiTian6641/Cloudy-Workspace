package io.github.baitian6641.cloudy.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HandoffEnvelopeTest {
    private fun envelope(
        version: Int = 1, requestId: String = "13571113-2468-4ACE-8BAD-001122334455",
        createdAt: String = "2026-10-14T18:00:00Z",
        expiresAt: String = "2026-10-14T18:02:00Z",
        action: String = "open-console", hostId: String = "demo-host",
        environmentId: String = "demo-linux", workspaceId: String = "demo-fpga",
        resourceId: String = "registered-project-root",
    ) = HandoffEnvelope.create(version, requestId, createdAt, expiresAt, action,
        hostId, environmentId, workspaceId, resourceId)

    private fun rejected(code: HandoffValidationCode, block: () -> Unit) {
        val error = assertThrows(HandoffValidationException::class.java, block)
        assertEquals(code, error.code)
        assertEquals(code.name, error.message)
        assertNull(error.cause)
    }

    @Test fun normalizesUuidOffsetsAndNanosecondsWithoutLosingResourceIdentity() {
        val value = envelope(createdAt = "2026-10-14T13:00:00.123456789-05:00",
            expiresAt = "2026-10-14T18:02:00.123456789+00:00")
        assertEquals("13571113-2468-4ace-8bad-001122334455", value.requestId)
        assertEquals(Instant.parse("2026-10-14T18:00:00.123456789Z"), value.createdAt)
        assertEquals("registered-project-root", value.resourceId)
        val roundTrip = envelope(createdAt = value.createdAt.toString(), expiresAt = value.expiresAt.toString())
        assertEquals(value.createdAt, roundTrip.createdAt)
        assertEquals(value.expiresAt, roundTrip.expiresAt)
        assertFalse(value.toString().contains(value.requestId))
        assertFalse(value.toString().contains(value.hostId))
    }

    @Test fun acceptsExactlyTheThreeWireActions() {
        for (action in HandoffAction.entries) assertEquals(action, envelope(action = action.wireId).action)
        for (action in listOf("OPEN_CONSOLE", "open-console ", "execute", "")) {
            rejected(HandoffValidationCode.INVALID_ACTION) { envelope(action = action) }
        }
        rejected(HandoffValidationCode.UNKNOWN_VERSION) { envelope(version = 2) }
    }

    @Test fun uuidRequiresAllCanonicalGroupsAndHexDigits() {
        for (id in listOf("1-1-1-1-1", "", "g3571113-2468-4ace-8bad-001122334455",
                "13571113-2468-4ace-8bad-001122334455\n", "a".repeat(1000))) {
            rejected(HandoffValidationCode.INVALID_REQUEST_ID) { envelope(requestId = id) }
        }
    }

    @Test fun identifiersAreBoundedAsciiAndEveryFieldIsValidated() {
        assertEquals("A" + "._-0".repeat(31) + "abc", envelope(resourceId = "A" + "._-0".repeat(31) + "abc").resourceId)
        for (id in listOf("", "a".repeat(129), "../root", "/tmp/root", "a b", "é", "a\n", "_root")) {
            rejected(HandoffValidationCode.INVALID_RESOURCE_ID) { envelope(hostId = id) }
            rejected(HandoffValidationCode.INVALID_RESOURCE_ID) { envelope(environmentId = id) }
            rejected(HandoffValidationCode.INVALID_RESOURCE_ID) { envelope(workspaceId = id) }
            rejected(HandoffValidationCode.INVALID_RESOURCE_ID) { envelope(resourceId = id) }
        }
    }

    @Test fun malformedAndUnrepresentableTimestampsAreRejectedAtEitherBoundary() {
        for (time in listOf("2026-02-29T18:00:00Z", "2024-02-30T18:00:00Z",
                "2026-10-14T24:00:00Z", "2026-10-14T18:00:60Z", "2026-10-14t18:00:00z",
                "2026-10-14T18:00Z", "2026-10-14T18:00:00", "2026-10-14T18:00:00-00:00",
                "2026-10-14T18:00:00.1234567890Z", "2026-10-14T18:00:00+05:60",
                "2026-10-14T18:00:00Z\n", "+10000-01-01T00:00:00Z",
                "0000-01-01T00:00:00+00:01", "9999-12-31T23:59:59-00:01",
                "x".repeat(1000))) {
            rejected(HandoffValidationCode.INVALID_TIMESTAMP) { envelope(createdAt = time) }
            rejected(HandoffValidationCode.INVALID_TIMESTAMP) { envelope(expiresAt = time) }
        }
    }

    @Test fun leapCalendarAndCanonicalYearEdgesRoundTrip() {
        for ((created, expires) in listOf(
            "2024-02-29T23:59:59Z" to "2024-03-01T00:00:00Z",
            "0000-01-01T00:00:00Z" to "0000-01-01T00:00:00.000000001Z",
            "9999-12-31T23:59:59Z" to "9999-12-31T23:59:59.999999999Z",
        )) {
            val value = envelope(createdAt = created, expiresAt = expires)
            assertEquals(value.createdAt, envelope(createdAt = value.createdAt.toString(),
                expiresAt = value.expiresAt.toString()).createdAt)
        }
    }

    @Test fun lifetimeIncludes120SecondsButRequiresPositiveNanoseconds() {
        envelope(expiresAt = "2026-10-14T18:00:00.000000001Z")
        envelope()
        for (expiry in listOf("2026-10-14T18:00:00Z", "2026-10-14T17:59:59Z",
                "2026-10-14T18:02:00.000000001Z")) {
            rejected(HandoffValidationCode.INVALID_LIFETIME) { envelope(expiresAt = expiry) }
        }
    }

    @Test fun freshnessChecksExactClockSkewExpiryAndExtremeInstants() {
        val value = envelope()
        assertEquals(HandoffFreshness.NOT_YET_VALID, value.freshAt(value.createdAt.minusSeconds(5).minusNanos(1)))
        assertEquals(HandoffFreshness.CURRENT, value.freshAt(value.createdAt.minusSeconds(5)))
        assertEquals(HandoffFreshness.CURRENT, value.freshAt(value.createdAt))
        assertEquals(HandoffFreshness.CURRENT, value.freshAt(value.expiresAt.minusNanos(1)))
        assertEquals(HandoffFreshness.EXPIRED, value.freshAt(value.expiresAt))
        assertEquals(HandoffFreshness.NOT_YET_VALID, value.freshAt(Instant.MIN))
        assertEquals(HandoffFreshness.EXPIRED, value.freshAt(Instant.MAX))
    }
}
