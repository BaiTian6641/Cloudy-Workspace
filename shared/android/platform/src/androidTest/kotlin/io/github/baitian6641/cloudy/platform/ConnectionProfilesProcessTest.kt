package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in phases require the parent to stop the seed process before launching verification. */
@RunWith(AndroidJUnit4::class)
class ConnectionProfilesProcessTest {
    @Test fun committedProfileAndEditTokenSurviveProcessRestart() {
        val phase = InstrumentationRegistry.getArguments().getString("cloudyProfilesPhase")
        assumeTrue("Requires separate seed/verify processes", phase == "seed" || phase == "verify")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.noBackupFilesDir, "profiles-process-qualification")
        val receipt = File(root, "seed.receipt")
        val store = ConnectionProfiles(root, WindowOwner.CONSOLE,
            "io.github.baitian6641.cloudy.console", { false })
        if (phase == "seed") {
            assertFalse("Inspect an earlier fixture before reusing it", root.exists())
            assertTrue(root.mkdir())
            val catalog = success(store.load())
            assertTrue(catalog.profiles.isEmpty())
            val entry = success(store.create(catalog.generation, draft("Process seed")))
            val text = listOf(Process.myPid().toString(), entry.version.generation,
                entry.version.id, entry.version.revision.toString()).joinToString("\n")
            FileOutputStream(receipt).use { stream ->
                stream.write(text.toByteArray(Charsets.US_ASCII))
                stream.fd.sync()
            }
        } else {
            assertTrue("A successful seed must already exist", receipt.isFile)
            val parts = receipt.readLines(Charsets.US_ASCII)
            assertEquals(4, parts.size)
            assertNotEquals("Must execute in another process", parts[0].toInt(), Process.myPid())
            val token = ConnectionProfileVersion.parse(parts[1], parts[2], parts[3])
            val catalog = success(store.load())
            assertEquals(token.generation, catalog.generation)
            assertEquals(1, catalog.profiles.size)
            val saved = catalog.profiles.single()
            assertEquals(token.id, saved.id)
            assertEquals(token.revision, saved.revision)
            assertEquals("Process seed", saved.label)
            assertEquals("example.invalid", saved.host)
            val updated = success(store.update(token, draft("After restart")))
            assertEquals(2L, updated.profile.revision)
            assertEquals(ConnectionProfilesResult.Failure(ConnectionProfilesCode.CONFLICT),
                store.update(token, draft("Stale edit")))
            success(store.delete(updated.version))
            assertEquals(ConnectionProfilesResult.Failure(ConnectionProfilesCode.NOT_FOUND),
                store.update(token, draft("Deleted edit")))
            assertTrue(success(store.load()).profiles.isEmpty())
            // Cleanup is scoped to this standalone fixture and occurs only after every assertion.
            assertTrue(root.deleteRecursively())
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("cloudyProfilesPhase", phase)
            putInt("cloudyProfilesPid", Process.myPid())
        })
    }

    private fun draft(label: String) = ConnectionProfileDraft.create(WindowOwner.CONSOLE,
        label, ConnectionProtocol.SSH, "EXAMPLE.invalid.", 22)

    private fun <T> success(result: ConnectionProfilesResult<T>): T = when (result) {
        is ConnectionProfilesResult.Success -> result.value
        is ConnectionProfilesResult.Failure -> throw AssertionError(result.code.name)
    }
}
