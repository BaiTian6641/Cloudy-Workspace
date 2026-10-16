package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.HandoffEnvelope
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Two explicit instrumentation invocations; default discovery skips before creating any fixture. */
@RunWith(AndroidJUnit4::class)
class HandoffReplayProcessTest {
    @Test fun reservationSurvivesASeparateProcess() {
        val phase = InstrumentationRegistry.getArguments().getString("cloudyReplayPhase")
        assumeTrue("Requires separate seed/verify processes", phase == "seed" || phase == "verify")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val fixture = File(base.noBackupFilesDir, "replay-process-qualification")
        val previousPid = File(fixture, "seed.pid")
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = fixture
        }
        val initial = Instant.parse("2026-10-16T08:00:00Z")
        var now = initial
        val first = envelope("13571113-2468-4ace-8bad-001122334455", initial)
        val ledger = HandoffReplayLedger(context, { now }, 4096)
        if (phase == "seed") {
            // Never erase an interrupted predecessor to make this run appear fresh.
            assertFalse("Previous process fixture must be inspected first", fixture.exists())
            assertTrue(fixture.mkdir())
            assertEquals(HandoffReplayResult.RESERVED, ledger.reserve(first))
            FileOutputStream(previousPid).use { stream ->
                stream.write(Process.myPid().toString().toByteArray(Charsets.US_ASCII))
                stream.fd.sync()
            }
        } else {
            assertTrue("Seed must already exist", previousPid.isFile)
            assertNotEquals("Must execute in another process", previousPid.readText().toInt(), Process.myPid())
            assertEquals(HandoffReplayResult.DUPLICATE, ledger.reserve(first))
            now = initial.plusSeconds(121)
            val second = envelope("24682224-1357-4ace-8bad-001122334455", now)
            assertEquals(HandoffReplayResult.RESERVED, ledger.reserve(second))
            assertEquals(HandoffReplayResult.EXPIRED, ledger.reserve(first))
            now = initial
            assertEquals(HandoffReplayResult.CLOCK_ROLLBACK, ledger.reserve(first))
            // Remove only this standalone test-owned fixture after all assertions succeed.
            assertTrue(fixture.deleteRecursively())
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("cloudyReplayPhase", phase)
            putInt("cloudyReplayPid", Process.myPid())
        })
    }

    private fun envelope(id: String, created: Instant): HandoffEnvelope = HandoffEnvelope.create(
        1, id, created.toString(), created.plusSeconds(120).toString(), "open-console",
        "test-host", "test-environment", "test-workspace", "test-resource",
    )
}
