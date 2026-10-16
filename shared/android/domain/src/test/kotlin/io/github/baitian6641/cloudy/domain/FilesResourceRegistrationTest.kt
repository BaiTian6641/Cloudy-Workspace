package io.github.baitian6641.cloudy.domain

import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesResourceRegistrationTest {
    private val generation = "12345678-1234-4234-8234-123456789abc"
    private val otherGeneration = "12345678-1234-4234-9234-123456789abc"
    private val profile = SshObservationProfile.create(generation, ConnectionProfile.create(
        ConnectionProfileDraft.create(WindowOwner.CONSOLE, "Own endpoint", ConnectionProtocol.SSH, "example.invalid", 22)))
    private fun trust(owner: WindowOwner = WindowOwner.CONSOLE, peer: SshPeerIdentity = profile.peer,
                      revision: Long = 1, revoked: Boolean = false): SshTrustSnapshot {
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", "SHA256:" +
            Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32)))
        return SshTrustSnapshot.create(owner, generation, peer, revision,
            if (revoked) SshTrustStatus.revoked(key) else SshTrustStatus.accepted(key))
    }
    private fun draft(label: String = "Files", root: String = ".", account: String = "receiver-user",
                      owner: WindowOwner = WindowOwner.CONSOLE, observed: SshObservationProfile = profile,
                      pin: SshTrustSnapshot = trust()) = FilesResourceDraft.create(owner, label, observed, pin, root, account)
    private fun rejected(code: FilesResourceValidationCode, action: () -> Unit) {
        val error = assertThrows(FilesResourceValidationException::class.java, action)
        assertEquals(code, error.code)
        assertEquals(code.name, error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
    }

    @Test fun createGeneratesDistinctCanonicalOpaqueTupleAndPrivateFirstRevision() {
        val row = FilesResourceRegistration.create(generation, draft())
        val ids = listOf(row.reference.hostId, row.reference.environmentId, row.reference.workspaceId, row.reference.resourceId)
        assertEquals(4, ids.toSet().size)
        ids.forEach { id ->
            val parsed = UUID.fromString(id)
            assertEquals(id, parsed.toString())
            assertEquals(4, parsed.version())
            assertEquals(2, parsed.variant())
            assertNotEquals(profile.profile.id, id)
        }
        assertNotEquals(row.reference, FilesResourceRegistration.create(generation, row.draft).reference)
        assertEquals(FilesResourceState.PRIVATE, row.state)
        assertEquals(1L, row.revision)
        assertEquals(profile, row.profile)
        assertEquals(trust(), row.acceptedTrust)
        assertEquals(FilesResourceVersion.create(generation, row.reference.resourceId, 1), row.version())
    }

    @Test fun shareIsExplicitOnceAndRevocationRetainsBindingsWithoutRevival() {
        val original = FilesResourceRegistration.create(generation, draft())
        val shared = original.share()
        val revoked = shared.revoke()
        assertEquals(FilesResourceState.PRIVATE, original.state)
        assertEquals(FilesResourceState.SHARED, shared.state)
        assertEquals(2L, shared.revision)
        assertEquals(FilesResourceState.REVOKED, revoked.state)
        assertEquals(3L, revoked.revision)
        assertEquals(original.reference, revoked.reference)
        assertEquals(original.draft, revoked.draft)
        assertEquals(original.generation, revoked.generation)
        rejected(FilesResourceValidationCode.INVALID_STATE) { shared.share() }
        rejected(FilesResourceValidationCode.INVALID_STATE) { revoked.share() }
        rejected(FilesResourceValidationCode.INVALID_STATE) { revoked.revoke() }
        assertEquals(2L, original.revoke().revision)
        assertEquals(FilesResourceState.REVOKED, original.revoke().state)
    }

    @Test fun positiveRestorationAndOverflowAreMetadataChecksNotTransitionAuthority() {
        val row = FilesResourceRegistration.create(generation, draft())
        for (state in FilesResourceState.entries) {
            val restored = FilesResourceRegistration.restore(generation, row.reference, Long.MAX_VALUE, state, row.draft)
            assertEquals(Long.MAX_VALUE, restored.version().revision)
            if (state == FilesResourceState.PRIVATE) rejected(FilesResourceValidationCode.INVALID_REVISION) { restored.share() }
            if (state != FilesResourceState.REVOKED) rejected(FilesResourceValidationCode.INVALID_REVISION) { restored.revoke() }
        }
        for (revision in listOf(0L, -1L, Long.MIN_VALUE)) {
            rejected(FilesResourceValidationCode.INVALID_REVISION) {
                FilesResourceRegistration.restore(generation, row.reference, revision, FilesResourceState.PRIVATE, row.draft)
            }
            rejected(FilesResourceValidationCode.INVALID_REVISION) { FilesResourceVersion.create(generation, row.reference.resourceId, revision) }
        }
    }

    @Test fun everyTuplePositionRejectsAliasesWrongVersionVariantAndDuplicateIds() {
        val ref = FilesResourceReference.createNew()
        val ids = listOf(ref.hostId, ref.environmentId, ref.workspaceId, ref.resourceId)
        for (position in ids.indices) {
            for (bad in listOf("", "../root", generation.uppercase(), "12345678-1234-1234-8234-123456789abc",
                    "12345678-1234-4234-7234-123456789abc", "0".repeat(129))) {
                val values = ids.toMutableList().apply { this[position] = bad }
                rejected(FilesResourceValidationCode.INVALID_ID) {
                    FilesResourceReference.restore(values[0], values[1], values[2], values[3])
                }
            }
        }
        rejected(FilesResourceValidationCode.INVALID_ID) { FilesResourceReference.restore(ref.hostId, ref.hostId, ref.workspaceId, ref.resourceId) }
        assertEquals(ref, FilesResourceReference.restore(ref.hostId, ref.environmentId, ref.workspaceId, ref.resourceId))
    }

    @Test fun storeGenerationAndEditVersionRemainSeparateFromTupleIdentity() {
        val row = FilesResourceRegistration.create(generation, draft())
        for (bad in listOf("", generation.uppercase(), "12345678-1234-1234-8234-123456789abc")) {
            rejected(FilesResourceValidationCode.INVALID_GENERATION) { FilesResourceRegistration.create(bad, row.draft) }
            rejected(FilesResourceValidationCode.INVALID_GENERATION) { FilesResourceVersion.create(bad, row.reference.resourceId, 1) }
        }
        rejected(FilesResourceValidationCode.INVALID_ID) { FilesResourceVersion.create(generation, "resource", 1) }
        assertNotEquals(row.version(), FilesResourceVersion.create(otherGeneration, row.reference.resourceId, 1))
        assertNotEquals(row.version(), row.share().version())
    }

    @Test fun rootAndAccountPreserveExactSpellingIncludingSpacesAndUnicodeNormalization() {
        val value = draft(label = "  Navigation  ", root = " ./dir/../ other/ ", account = "  User-e\u0301  ")
        assertEquals("Navigation", value.label)
        assertEquals(" ./dir/../ other/ ", value.navigationRoot)
        assertEquals("  User-e\u0301  ", value.accountName)
        assertNotEquals(value, draft(label = "Navigation", root = value.navigationRoot, account = "  User-é  "))
        assertEquals(" ", draft(root = " ", account = " ").accountName)
    }

    @Test fun accountMatchesCredentialUsernameUnitsAndWellFormedTextRules() {
        assertEquals(256, draft(account = "a".repeat(256)).accountName.length)
        assertEquals(256, draft(account = "\uD801\uDC00".repeat(128)).accountName.length)
        for (bad in listOf("", "a".repeat(257), "a\u0000b", "a\u0085b", "a\nb", "a\u2028b", "a\u2029b", "\uD800", "\uDC00", "\uD800x")) {
            rejected(FilesResourceValidationCode.INVALID_ACCOUNT) { draft(account = bad) }
        }
    }

    @Test fun rootBoundsAndMalformedTextRejectWithoutLocalPathNormalization() {
        val root = "./" + "a".repeat(4094)
        assertEquals(root, draft(root = root).navigationRoot)
        for (bad in listOf("", "a".repeat(4097), "/a\u0000b", "/a\u007fb", "/a\u2028b", "/a\u2029b", "/\uD800", "/\uDC00")) {
            rejected(FilesResourceValidationCode.INVALID_ROOT) { draft(root = bad) }
        }
        assertEquals("../../outside", draft(root = "../../outside").navigationRoot)
    }

    @Test fun labelUsesExistingUnicodeNameLimitsAndTrimming() {
        assertEquals(160, draft(label = "\uD801\uDC00".repeat(80)).label.length)
        assertEquals("a".repeat(80), draft(label = "a".repeat(80)).label)
        for (bad in listOf("", " ", "a".repeat(81), " ".repeat(160) + "a", "a\nb", "a\u2029b", "\uD800")) {
            rejected(FilesResourceValidationCode.INVALID_LABEL) { draft(label = bad) }
        }
    }

    @Test fun exactOwnerAndPeerAndAcceptedTrustAreRequired() {
        rejected(FilesResourceValidationCode.WRONG_OWNER) { draft(owner = WindowOwner.DESKTOP) }
        rejected(FilesResourceValidationCode.WRONG_OWNER) { draft(pin = trust(owner = WindowOwner.DESKTOP)) }
        rejected(FilesResourceValidationCode.WRONG_PEER) { draft(pin = trust(peer = SshPeerIdentity.create("other.invalid", 22))) }
        rejected(FilesResourceValidationCode.WRONG_PEER) { draft(pin = trust(peer = SshPeerIdentity.create("example.invalid", 2222))) }
        rejected(FilesResourceValidationCode.INVALID_TRUST) { draft(pin = trust(revoked = true)) }
        val desktopProfile = SshObservationProfile.create(generation, ConnectionProfile.create(ConnectionProfileDraft.create(
            WindowOwner.DESKTOP, "Desktop files", ConnectionProtocol.SSH, "example.invalid", 22)))
        assertEquals(WindowOwner.DESKTOP, draft(owner = WindowOwner.DESKTOP, observed = desktopProfile,
            pin = trust(owner = WindowOwner.DESKTOP)).owner)
    }

    @Test fun equalityRetainsPrivateAccountProfileTrustGenerationAndEveryTupleLevel() {
        val value = draft()
        val row = FilesResourceRegistration.create(generation, value)
        val restored = FilesResourceRegistration.restore(generation, row.reference, 1, FilesResourceState.PRIVATE, draft())
        assertEquals(row, restored)
        assertEquals(row.hashCode(), restored.hashCode())
        assertNotEquals(value, draft(account = "other-user"))
        assertNotEquals(value, draft(pin = trust(revision = 2)))
        assertNotEquals(value, draft(observed = SshObservationProfile.create(otherGeneration, profile.profile)))
        assertNotEquals(row, FilesResourceRegistration.restore(otherGeneration, row.reference, 1, row.state, value))
        assertNotEquals(row, row.share())
        assertNotEquals(row.reference, FilesResourceReference.restore(UUID.randomUUID().toString(),
            row.reference.environmentId, row.reference.workspaceId, row.reference.resourceId))
    }

    @Test fun stringsAreRedactedAndThereIsNoDataClassCopyBypass() {
        val row = FilesResourceRegistration.create(generation, draft(root = "/private-root", account = "private-account"))
        val values = listOf(row, row.draft, row.reference, row.version())
        for (value in values) {
            assertTrue(value.toString().contains("redacted"))
            for (secret in listOf("private-root", "private-account", "example.invalid", generation, row.reference.resourceId))
                assertFalse(value.toString().contains(secret))
            assertFalse(value.javaClass.methods.any { it.name == "copy" || it.name.startsWith("component") })
        }
    }
}
