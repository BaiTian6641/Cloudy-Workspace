package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** Pure adapter bounds/ownership tests, not evidence of a network authentication or retirement. */
class SshRetainedBoundsTest {
    @Test fun ownershipIsIdentityBoundAndRetirementIsMonotonic() {
        val peer = SshPeerIdentity.create("private.invalid", 22)
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", "SHA256:" +
            Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 }))
        val cancellation = SshObservationCancellation()
        val proof = SshAuthenticatedOwnership(peer, key, cancellation)
        val other = SshAuthenticatedOwnership(peer, key, cancellation)
        assertSame(peer, proof.peer)
        assertSame(key, proof.key)
        assertSame(cancellation, proof.cancellation)
        assertNotSame(other, proof)
        assertFalse(proof.isRetired)
        cancellation.cancel()
        assertFalse(proof.isRetired) // A cancellation request is not cleanup proof.
        proof.markRetired(); proof.markRetired()
        assertTrue(proof.isRetired)
        assertEquals("SshAuthenticatedOwnership(redacted)", proof.toString())
    }

    @Test fun remoteNamesRemainBoundedUntrustedLeaves() {
        assertTrue(SshFileLimits.name("normal name Ω.txt"))
        assertTrue(SshFileLimits.name("emoji-\ud83d\ude00"))
        assertTrue(SshFileLimits.name("x".repeat(SshFiles.MAX_NAME_UNITS)))
        for (name in listOf("", ".", "..", "a/b", "a\\b", "\u001b[31m", "x\u0000", "x\n",
            "\ud800", "\udc00", "\u2028", "x".repeat(SshFiles.MAX_NAME_UNITS + 1))) {
            assertFalse("Invalid remote leaf accepted", SshFileLimits.name(name))
        }
    }

    @Test fun remotePathsAreLengthBoundedWithoutNormalizationOrLocalPathClaims() {
        assertTrue(SshFileLimits.path("/remote path/../file"))
        assertTrue(SshFileLimits.path("relative/Ω"))
        assertTrue(SshFileLimits.path("x".repeat(SshFiles.MAX_PATH_UNITS)))
        assertFalse(SshFileLimits.path(""))
        assertFalse(SshFileLimits.path("x".repeat(SshFiles.MAX_PATH_UNITS + 1)))
        assertFalse(SshFileLimits.path("remote\u0000tail"))
        assertFalse(SshFileLimits.path("remote\ud800"))
    }

    @Test fun returnedBytesAreDefensivelyCopiedAndDiagnosticTextIsRedacted() {
        val original = byteArrayOf(0, 1, -1)
        val data = SshFileBytes(original)
        original.fill(8)
        val first = data.copyBytes()
        assertArrayEquals(byteArrayOf(0, 1, -1), first)
        first.fill(9)
        assertArrayEquals(byteArrayOf(0, 1, -1), data.copyBytes())
        assertEquals(3, data.size)
        assertEquals("SshFileBytes(redacted)", data.toString())
        assertEquals("SshFileEntry(redacted)", SshFileEntry("private", SshFileKind.REGULAR, 3).toString())
        assertEquals("SshFilesResult.Success(redacted)", SshFilesResult.Success(data).toString())
    }

    @Test fun idlePolicyIsExplicitAndFiniteDefaultsAreUnchanged() {
        val finite = SshObservationPolicy.create(SshReaderRetirement())
        val retained = SshObservationPolicy.create(SshReaderRetirement(), idleReadTimeoutMillis = 0)
        assertEquals(10_000, finite.channelReadTimeoutMs)
        assertEquals(10_000, finite.channelErrorReadTimeoutMs)
        assertEquals(0, retained.channelReadTimeoutMs)
        assertEquals(0, retained.channelErrorReadTimeoutMs)
    }
}
