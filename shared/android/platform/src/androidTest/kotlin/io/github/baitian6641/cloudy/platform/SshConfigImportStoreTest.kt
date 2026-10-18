package io.github.baitian6641.cloudy.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SQLite import from the declared non-secret SSH-config subset. */
@RunWith(AndroidJUnit4::class)
class SshConfigImportStoreTest {
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(application.noBackupFilesDir, "ssh-import-test-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val store = ConnectionProfiles(root, WindowOwner.CONSOLE, "io.github.baitian6641.cloudy.console", { false })

    @After fun cleanup() { check(root.deleteRecursively()) }

    @Test fun createsOnlyDeclaredProfilesAndPreservesUnsupportedAsReported() {
        val preview = SshConfigImporter.parse("""
            Host imported-alpha
              HostName alpha.example
              Port 2222
            Host imported-beta
              HostName beta.example
              User alice
              IdentityFile ~/.ssh/private_key
              ProxyJump bastion
        """.trimIndent(), WindowOwner.CONSOLE)
        assertEquals(2, preview.entries.size)
        val result = SshConfigImporter.importProfiles(store, WindowOwner.CONSOLE, preview)
        assertEquals(2, result.created)
        assertNull(result.failure)
        val loaded = (store.load() as ConnectionProfilesResult.Success).value.profiles
        assertEquals(listOf("imported-alpha", "imported-beta"), loaded.map { it.label })
        assertEquals(listOf("alpha.example", "beta.example"), loaded.map { it.host })
        assertEquals(listOf(2222, 22), loaded.map { it.port })
        assertTrue(loaded.all { "imported" in it.tags })
        assertTrue(loaded.all { it.keyUri == null && it.jumpProfileId == null })
        assertTrue(preview.issues.any { it.directive == "user" })
        assertTrue(preview.issues.any { it.directive == "identityfile" })
        assertTrue(preview.issues.any { it.directive == "proxyjump" })
    }

    @Test fun existingLabelsAreSkippedAndExistingProfilesPreserved() {
        val original = SshConfigImporter.parse("Host taken\n  HostName taken.example\n",
            WindowOwner.CONSOLE)
        assertEquals(1, SshConfigImporter.importProfiles(store, WindowOwner.CONSOLE, original).created)
        val preview = SshConfigImporter.parse("""
            Host taken
              HostName replaced.example
            Host new-one
              HostName new.example
        """.trimIndent(), WindowOwner.CONSOLE, setOf("taken"))
        assertEquals(listOf("new-one"), preview.entries.map { it.alias })
        val result = SshConfigImporter.importProfiles(store, WindowOwner.CONSOLE, preview)
        assertEquals(1, result.created)
        val loaded = (store.load() as ConnectionProfilesResult.Success).value.profiles
        assertEquals(setOf("taken", "new-one"), loaded.map { it.label }.toSet())
        assertEquals("taken.example", loaded.single { it.label == "taken" }.host)
    }
}
