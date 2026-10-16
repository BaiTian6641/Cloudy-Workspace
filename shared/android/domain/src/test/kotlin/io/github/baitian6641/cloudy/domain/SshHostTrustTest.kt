package io.github.baitian6641.cloudy.domain

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SshHostTrustTest {
    private val owner = WindowOwner.CONSOLE
    private val generation = "12345678-1234-4234-8234-123456789abc"
    private val nextGeneration = "12345678-1234-4234-b234-123456789abc"
    private val attempt = "12345678-1234-4234-9234-123456789abc"
    private val presentation = "12345678-1234-4234-a234-123456789abc"
    private val peer = SshPeerIdentity.create("example.org", 22)
    private val formats = listOf("ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521", "ssh-rsa")
    private fun fingerprint(byte: Int = 0): String = "SHA256:" +
        Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { byte.toByte() })
    private fun key(byte: Int = 0, format: String = "ssh-ed25519") = SshHostKeyIdentity.create(format, fingerprint(byte))
    private fun snapshot(status: SshTrustStatus = SshTrustStatus.accepted(key()), revision: Long = 1) =
        SshTrustSnapshot.create(owner, generation, peer, revision, status)
    private fun evaluate(observed: SshHostKeyIdentity = key(), current: SshTrustSnapshot? = null) =
        SshHostTrust.evaluate(owner, generation, peer, observed, current)
    private fun challenge(evaluation: SshTrustEvaluation) = SshTrustChallenge.create(attempt, presentation, evaluation)

    private fun rejected(code: SshTrustValidationCode, block: () -> Unit) {
        val error = assertThrows(SshTrustValidationException::class.java, block)
        assertEquals(code, error.code)
        assertEquals(code.name, error.message)
        assertNull(error.cause)
        assertEquals(0, error.suppressed.size)
    }

    @Test fun peerIdentityUsesCanonicalLogicalHostExplicitPortAndTypedDirectRoute() {
        val alias = SshPeerIdentity.create("EXAMPLE.org.", 22, SshTrustRoute.Direct)
        assertEquals(peer, alias)
        assertEquals(peer.hashCode(), alias.hashCode())
        assertEquals("example.org", alias.host)
        assertEquals("direct", alias.route.wireId)
        assertEquals(listOf(SshTrustRoute.Direct), SshTrustRoute.entries)
        assertNotEquals(peer, SshPeerIdentity.create("example.org", 2222))
        assertNotEquals(peer, SshPeerIdentity.create("other.example", 22))
        assertEquals(SshPeerIdentity.create("[2001:DB8::1]", 22), SshPeerIdentity.create("2001:db8:0:0:0:0:0:1", 22))
        assertNotEquals(SshPeerIdentity.create("fe80::1%Eth_0", 22), SshPeerIdentity.create("fe80::1%eth_0", 22))
        assertEquals("xn--bcher-kva.example", SshPeerIdentity.create("bücher.example", 22).host)
        for (host in listOf("user@example.org", "https://example.org", "[::1]:22", "01.2.3.4", "x".repeat(1025))) {
            rejected(SshTrustValidationCode.INVALID_HOST) { SshPeerIdentity.create(host, 22) }
        }
        for (port in listOf(Int.MIN_VALUE, 0, 65536, Int.MAX_VALUE)) {
            rejected(SshTrustValidationCode.INVALID_PORT) { SshPeerIdentity.create("example.org", port) }
        }
        assertEquals(1, SshPeerIdentity.create("example.org", 1).port)
        assertEquals(65535, SshPeerIdentity.create("example.org", 65535).port)
    }

    @Test fun exactKeyFormatsAcceptOnlyCanonicalSha256AndKeepFullKeyIdentity() {
        for (format in formats) {
            val value = key(format = format)
            assertEquals(format, value.keyFormat)
            assertEquals("SHA256:" + "A".repeat(43), value.fingerprint)
            assertEquals(value, SshHostKeyIdentity.create(format, value.fingerprint))
            assertEquals(value.hashCode(), SshHostKeyIdentity.create(format, value.fingerprint).hashCode())
        }
        // ssh-rsa identifies the key encoding here, and grants no signature-algorithm permission.
        assertNotEquals(key(), key(format = "ssh-rsa"))
        assertNotEquals(key(), key(1))
        val standard = fingerprint(251)
        assertTrue(standard.contains('+') || standard.contains('/'))
        assertEquals(standard, SshHostKeyIdentity.create("ssh-ed25519", standard).fingerprint)
    }

    @Test fun unsupportedFormatsAndFingerprintAliasesAreRejectedBeforeTheyBecomePins() {
        for (format in listOf("", "SSH-ED25519", "ssh-ed25519 ", "rsa-sha2-256", "ssh-dss",
                "ssh-ed25519-cert-v01@openssh.com", "sk-ssh-ed25519@openssh.com", "x".repeat(10000))) {
            rejected(SshTrustValidationCode.UNSUPPORTED_KEY_FORMAT) { key(format = format) }
        }
        for (text in listOf("", "sha256:" + "A".repeat(43), fingerprint() + "=", fingerprint() + "\n",
                "SHA256:" + "A".repeat(42), "SHA256:" + "A".repeat(44), "SHA256:" + "A".repeat(42) + "B",
                "SHA256:" + "a".repeat(64), "MD5:00:11:22", fingerprint(251).replace('+', '-').replace('/', '_'),
                "SHA256:" + "x".repeat(10000))) {
            rejected(SshTrustValidationCode.INVALID_FINGERPRINT) { SshHostKeyIdentity.create("ssh-ed25519", text) }
        }
    }

    @Test fun statusesSnapshotsAndTokensHaveBoundedValidatedValueIdentity() {
        val accepted = SshTrustStatus.accepted(key())
        val revoked = SshTrustStatus.revoked(key())
        assertEquals(accepted, SshTrustStatus.accepted(key()))
        assertEquals(accepted.hashCode(), SshTrustStatus.accepted(key()).hashCode())
        assertEquals(revoked, SshTrustStatus.revoked(key()))
        assertNotEquals(accepted, revoked)
        assertNotEquals(revoked, SshTrustStatus.revoked(key(1)))
        assertEquals(snapshot(), snapshot())
        assertEquals(snapshot().hashCode(), snapshot().hashCode())
        assertNotEquals(snapshot(), snapshot(revoked))
        val absent = SshTrustEditToken.create(owner, generation, peer, 0)
        assertEquals(0L, absent.expectedRevision)
        assertEquals(absent, SshTrustEditToken.create(owner, generation, peer, 0))
        assertNotEquals(absent, SshTrustEditToken.create(owner, generation, peer, 1))
        assertNotEquals(absent, SshTrustEditToken.create(WindowOwner.DESKTOP, generation, peer, 0))
        assertNotEquals(absent, SshTrustEditToken.create(owner, nextGeneration, peer, 0))
        assertNotEquals(absent, SshTrustEditToken.create(owner, generation, SshPeerIdentity.create("other.example", 22), 0))
        for (revision in listOf(Long.MIN_VALUE, -1L, 0L)) {
            rejected(SshTrustValidationCode.INVALID_REVISION) { snapshot(revision = revision) }
        }
        rejected(SshTrustValidationCode.INVALID_REVISION) { SshTrustEditToken.create(owner, generation, peer, -1) }
        assertEquals(Long.MAX_VALUE, snapshot(revision = Long.MAX_VALUE).revision)
        assertEquals(Long.MAX_VALUE, SshTrustEditToken.create(owner, generation, peer, Long.MAX_VALUE).expectedRevision)
    }

    @Test fun generationIdentifiersAreStrictCanonicalV4AcrossValuesAndAbsentEvaluations() {
        for (id in listOf("", "1-1-1-1-1", generation.uppercase(), generation.replace("4234", "1234"),
                generation.replace("8234", "7234"), "$generation ", "x".repeat(10000))) {
            rejected(SshTrustValidationCode.INVALID_GENERATION) { SshTrustSnapshot.create(owner, id, peer, 1, SshTrustStatus.accepted(key())) }
            rejected(SshTrustValidationCode.INVALID_GENERATION) { SshTrustEditToken.create(owner, id, peer, 0) }
            rejected(SshTrustValidationCode.INVALID_GENERATION) { SshHostTrust.evaluate(owner, id, peer, key(), null) }
        }
    }

    @Test fun absentPeerIsUnknownWithExactZeroRevisionBindingAndObservedKey() {
        val observed = key(1)
        val result = evaluate(observed)
        assertTrue(result is SshTrustEvaluation.Unknown)
        assertNull(result.current)
        assertEquals(SshTrustEditToken.create(owner, generation, peer, 0), result.token)
        assertEquals(observed, result.observedKey)
        assertEquals(result, evaluate(observed))
        assertEquals(result.hashCode(), evaluate(observed).hashCode())
    }

    @Test fun acceptedPeerMatchesOnlyTheExactFormatAndDigestIncludingCrossTypeChanges() {
        val current = snapshot(revision = 7)
        val matching = evaluate(current = current)
        assertTrue(matching is SshTrustEvaluation.Match)
        assertEquals(current, matching.current)
        assertEquals(7L, matching.token.expectedRevision)
        for (observed in listOf(key(1)) + formats.drop(1).map { key(format = it) }) {
            val result = evaluate(observed, current)
            assertTrue(result is SshTrustEvaluation.Changed)
            assertEquals(current, result.current)
            assertEquals(observed, result.observedKey)
            assertEquals(7L, result.token.expectedRevision)
        }
    }

    @Test fun tombstoneIsAlwaysRevokedEvenWhenTheOriginalKeyReturns() {
        val current = snapshot(SshTrustStatus.revoked(key()), 8)
        for (observed in listOf(key(), key(1), key(format = "ssh-rsa"))) {
            val result = evaluate(observed, current)
            assertTrue(result is SshTrustEvaluation.Revoked)
            assertEquals(current, result.current)
            assertEquals(8L, result.token.expectedRevision)
            assertEquals(key(), (checkNotNull(result.current).status as SshTrustStatus.Revoked).lastKey)
        }
    }

    @Test fun wrongSnapshotContextIsRejectedRatherThanBecomingFirstUse() {
        val current = snapshot()
        rejected(SshTrustValidationCode.WRONG_OWNER) {
            SshHostTrust.evaluate(WindowOwner.DESKTOP, generation, peer, key(), current)
        }
        rejected(SshTrustValidationCode.STALE_GENERATION) {
            SshHostTrust.evaluate(owner, nextGeneration, peer, key(), current)
        }
        rejected(SshTrustValidationCode.WRONG_PEER) {
            SshHostTrust.evaluate(owner, generation, SshPeerIdentity.create("example.org", 2222), key(), current)
        }
    }

    @Test fun challengesDeriveReasonOldKeyAndOriginalCasTokenWithoutGrantingApproval() {
        val first = challenge(evaluate(key(1)))
        assertEquals(SshTrustChallengeReason.FIRST_USE, first.reason)
        assertEquals(0L, first.token.expectedRevision)
        assertNull(first.previousKey)
        val replacement = challenge(evaluate(key(1), snapshot(revision = 3)))
        assertEquals(SshTrustChallengeReason.REPLACEMENT, replacement.reason)
        assertEquals(3L, replacement.token.expectedRevision)
        assertEquals(key(), replacement.previousKey)
        val reenrollment = challenge(evaluate(key(), snapshot(SshTrustStatus.revoked(key()), 4)))
        assertEquals(SshTrustChallengeReason.REENROLLMENT, reenrollment.reason)
        assertEquals(4L, reenrollment.token.expectedRevision)
        assertEquals(key(), reenrollment.previousKey)
        for (value in listOf(first, replacement, reenrollment)) {
            assertEquals(owner, value.token.owner)
            assertEquals(generation, value.token.generation)
            assertEquals(peer, value.token.peer)
            assertEquals(attempt, value.attemptId)
            assertEquals(presentation, value.presentationGeneration)
        }
        assertEquals(first, challenge(evaluate(key(1))))
        assertEquals(first.hashCode(), challenge(evaluate(key(1))).hashCode())
        assertNotEquals(first, SshTrustChallenge.create(nextGeneration, presentation, evaluate(key(1))))
        assertNotEquals(first, SshTrustChallenge.create(attempt, nextGeneration, evaluate(key(1))))
        assertNotEquals(first, challenge(evaluate(key(2))))
    }

    @Test fun challengeValidationRejectsMatchingKeysMalformedIdsAndInconsistentInternalProposals() {
        rejected(SshTrustValidationCode.INVALID_CHALLENGE) { challenge(evaluate(current = snapshot())) }
        val unknown = evaluate()
        rejected(SshTrustValidationCode.INVALID_ATTEMPT_ID) { SshTrustChallenge.create("1-1-1-1-1", presentation, unknown) }
        rejected(SshTrustValidationCode.INVALID_PRESENTATION_GENERATION) { SshTrustChallenge.create(attempt, presentation.uppercase(), unknown) }
        val positiveToken = SshTrustEditToken.create(owner, generation, peer, 1)
        rejected(SshTrustValidationCode.INVALID_CHALLENGE) {
            challenge(SshTrustEvaluation.Unknown.create(positiveToken, key()))
        }
        val revoked = snapshot(SshTrustStatus.revoked(key()))
        rejected(SshTrustValidationCode.INVALID_CHALLENGE) {
            challenge(SshTrustEvaluation.Changed.create(positiveToken, key(1), revoked))
        }
        rejected(SshTrustValidationCode.INVALID_CHALLENGE) {
            challenge(SshTrustEvaluation.Changed.create(positiveToken, key(), snapshot()))
        }
    }

    @Test fun ordinaryStringRepresentationsRevealNoEndpointsDigestsOrBindingIds() {
        val values = listOf(peer, key(), SshTrustStatus.accepted(key()), SshTrustStatus.revoked(key()), snapshot(),
            SshTrustEditToken.create(owner, generation, peer, 0), evaluate(), evaluate(current = snapshot()),
            evaluate(key(1), snapshot()), evaluate(current = snapshot(SshTrustStatus.revoked(key()))), challenge(evaluate()))
        for (value in values) {
            val text = value.toString()
            assertTrue(text.endsWith("(redacted)"))
            for (privateValue in listOf(peer.host, key().fingerprint, generation, attempt, presentation)) {
                assertFalse(text.contains(privateValue))
            }
        }
    }
}
