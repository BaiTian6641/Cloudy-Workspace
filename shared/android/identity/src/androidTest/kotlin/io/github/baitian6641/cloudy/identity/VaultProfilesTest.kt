package io.github.baitian6641.cloudy.identity

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real Android AtomicFile, not a framework stub or a secret-storage qualification. */
class VaultProfilesTest {
    private val owner = "io.cloudy.console"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "vault-profile-test-${UUID.randomUUID()}")
    private fun store(packageName: String = owner, main: Boolean = false) =
        VaultProfiles(directory, packageName) { main }
    private fun profile(id: CredentialId = CredentialId.create(), host: String = "example.org") =
        VaultProfile(id, VaultBinding.create(owner, VaultProtocol.SSH, host, 22,
            VaultRole.TARGET, CredentialId.create().value))
    private fun bytes() = File(directory, "profiles.bin").readBytes()
    private fun replace(raw: ByteArray) = File(directory, "profiles.bin").writeBytes(raw)
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun reloadAndRemovalPreserveOnlyExpectedMetadata() {
        val first = profile()
        val second = profile(host = "[FE80::1%Eth0]")
        store().put(first); store().put(second)
        val loaded = store().list()
        assertEquals(listOf(first.id, second.id), loaded.map { it.id })
        assertEquals(first.binding, loaded[0].binding)
        assertEquals(second.binding, loaded[1].binding)
        assertEquals("VaultProfile(redacted)", loaded[0].toString())
        store().remove(first.id)
        assertEquals(listOf(second.id), store().list().map { it.id })
    }

    @Test fun existingBindingCannotBeOverwrittenOrReadByAnotherOwner() {
        val original = profile()
        store().put(original)
        val before = bytes()
        store().put(original)
        assertArrayEquals(before, bytes())
        assertThrows(IllegalStateException::class.java) { store().put(profile(original.id, "other.org")) }
        assertThrows(IllegalStateException::class.java) { store("io.cloudy.desktop").list() }
        assertArrayEquals(before, bytes())
        assertThrows(IllegalStateException::class.java) { store(main = true).list() }
    }

    @Test fun malformedTrailingUtf8AndDuplicateRecordsFailWithoutRecreatingStorage() {
        store().put(profile())
        val original = bytes()
        val duplicate = original.copyOf(12) + original.copyOfRange(12, original.size) +
            original.copyOfRange(12, original.size)
        ByteBuffer.wrap(duplicate).putInt(8, 2)
        for (bad in listOf(original + 0.toByte(), original.copyOf(7), duplicate,
                original.copyOf().also { it[16] = 0xff.toByte() },
                original.copyOf().also { ByteBuffer.wrap(it).putInt(12, Int.MAX_VALUE) })) {
            replace(bad)
            assertThrows(IllegalStateException::class.java) { store().list() }
            assertThrows(IllegalStateException::class.java) { store().put(profile()) }
            assertThrows(IllegalStateException::class.java) { store().remove(CredentialId.create()) }
            assertArrayEquals(bad, bytes())
        }
    }

    @Test fun capacityRejectsSixtyFifthButAllowsIdempotentExistingProfile() {
        val profiles = List(64) { profile() }
        for (profile in profiles) store().put(profile)
        val before = bytes()
        assertEquals(64, store().list().size)
        store().put(profiles[0])
        assertThrows(IllegalStateException::class.java) { store().put(profile()) }
        assertArrayEquals(before, bytes())
    }

    @Test fun unexplainedNewFileIsUnavailableNotAnEmptyReplacement() {
        assertTrue(directory.mkdirs())
        val partial = File(directory, "profiles.bin.new")
        partial.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(IllegalStateException::class.java) { store().list() }
        assertThrows(IllegalStateException::class.java) { store().put(profile()) }
        assertArrayEquals(byteArrayOf(1, 2, 3), partial.readBytes())
        assertFalse(File(directory, "profiles.bin").exists())
    }
}
