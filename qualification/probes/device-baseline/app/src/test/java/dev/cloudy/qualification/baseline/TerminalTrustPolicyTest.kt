package dev.cloudy.qualification.baseline

import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalTrustPolicyTest {
    private val key = fingerprint(1)
    private val otherKey = fingerprint(2)
    private val spec = TerminalConfigSpec("192.0.2.1", 2222, "fixture", key)
    private val identity = TerminalIdentity(UUID(0, 1).toString(), UUID(0, 2).toString())
    private val process = UUID(0, 3).toString()
    private val build = SshBuildIdentity("0.7.0", 9, UUID(0, 4).toString())
    private val time = TransferTime("2026-10-15T13:00:00Z", 10)

    private fun fingerprint(value: Int) = "SHA256:" + Base64.getEncoder().withoutPadding()
        .encodeToString(ByteArray(32) { value.toByte() })

    private fun pending(config: TerminalConfigSpec = spec, id: TerminalIdentity = identity,
        observed: String = config.expectedHostKeySha256): TerminalAttempt {
        val state = TerminalState()
        check(state.begin(id, process, config.configIdentity, build, time))
        state.update(id, "trust-required", "independent-host-key-matched", time, observed)
        return state.snapshot().single()
    }

    private fun ticket(row: TerminalAttempt = pending(), config: TerminalConfigSpec = spec) =
        TerminalTrustTicket(row.identity, config.configIdentity, config.endpointId,
            requireNotNull(row.observedHostKey))

    @Test fun matchingDisplayedUnknownKeyIsReturnedWithoutChangingHistory() {
        val row = pending()
        val approved = TerminalTrustPolicy.requireApproved(ticket(row), spec, row, null)
        assertEquals(key, approved)
        assertEquals("trust-required", row.status)
        assertEquals(false, row.authenticated)
        assertEquals(2, row.events.size)
    }

    @Test fun oldDialogCannotApproveAChangedEndpointLoginOrReference() {
        val oldTicket = ticket()
        val changed = listOf(spec.copy(host = "192.0.2.2"), spec.copy(port = 2223),
            spec.copy(username = "different"), spec.copy(expectedHostKeySha256 = otherKey))
        changed.forEach { current ->
            assertThrows(IllegalArgumentException::class.java) {
                TerminalTrustPolicy.requireApproved(oldTicket, current, pending(current), null)
            }
        }
    }

    @Test fun newAttemptForTheSameEndpointAndKeyNeedsItsOwnApproval() {
        val oldTicket = ticket()
        val replacement = pending(id = identity.copy(sessionId = UUID(0, 8).toString()))
        assertThrows(IllegalArgumentException::class.java) {
            TerminalTrustPolicy.requireApproved(oldTicket, spec, replacement, null)
        }
        assertEquals(key, TerminalTrustPolicy.requireApproved(ticket(replacement), spec, replacement, null))
    }

    @Test fun replacedRendererCannotReuseTheOldAttachmentConsent() {
        val oldTicket = ticket()
        val replacement = pending(id = identity.copy(attachmentId = UUID(0, 9).toString()))
        assertThrows(IllegalArgumentException::class.java) {
            TerminalTrustPolicy.requireApproved(oldTicket, spec, replacement, null)
        }
    }

    @Test fun previouslyEnrolledOrChangedStoredKeyCannotBeAcceptedAsUnknown() {
        val row = pending()
        listOf(key, otherKey, "malformed").forEach { stored ->
            assertThrows(IllegalArgumentException::class.java) {
                TerminalTrustPolicy.requireApproved(ticket(row), spec, row, stored)
            }
        }
    }

    @Test fun approvalMustStillReferToTheCurrentConfigurationOfTheRow() {
        val row = pending()
        val changed = spec.copy(username = "different")
        // Even a ticket constructed for the new settings cannot authorize an
        // earlier handshake whose row still belongs to the old configuration.
        val inconsistent = ticket(row, changed)
        assertThrows(IllegalArgumentException::class.java) {
            TerminalTrustPolicy.requireApproved(inconsistent, changed, row, null)
        }
    }

    @Test fun displayedFingerprintCannotOverrideTheIndependentReference() {
        val mismatched = pending(observed = otherKey)
        assertThrows(IllegalArgumentException::class.java) {
            TerminalTrustPolicy.requireApproved(ticket(mismatched), spec, mismatched, null)
        }
    }

    @Test fun malformedObservedKeyAndTicketCannotBecomeAValidApprovalPair() {
        // Defense at the policy boundary also rejects a malformed direct model
        // value; normal journal restoration rejects this row even earlier.
        val malformed = pending().copy(observedHostKey = "SHA256:invalid")
        assertThrows(IllegalArgumentException::class.java) {
            TerminalTrustPolicy.requireApproved(ticket(malformed), spec, malformed, null)
        }
        val wrongEndpoint = ticket().copy(endpointId = "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) {
            TerminalTrustPolicy.requireApproved(wrongEndpoint, spec, pending(), null)
        }
    }

    @Test fun endedAuthenticatedOrMissingKeyRowsCannotBeTrustPrompts() {
        val row = pending()
        val approval = ticket(row)
        listOf(row.copy(status = "failed"), row.copy(status = "host-key-rejected"),
            row.copy(authenticated = true), row.copy(observedHostKey = null)).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                TerminalTrustPolicy.requireApproved(approval, spec, invalid, null)
            }
        }
    }
}
