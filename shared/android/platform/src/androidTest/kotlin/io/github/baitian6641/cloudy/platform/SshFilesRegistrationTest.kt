package io.github.baitian6641.cloudy.platform

import android.app.Application
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.FilesResourceDraft
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.io.File
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Rejection/owner policy only. No constructed metadata is a listing or authenticated proof. */
@RunWith(AndroidJUnit4::class)
class SshFilesRegistrationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { action() }
    private fun uuid() = UUID.randomUUID().toString()
    private fun draft(): FilesResourceDraft {
        val profile = SshObservationProfile.create(uuid(), ConnectionProfile.create(ConnectionProfileDraft.create(
            WindowOwner.CONSOLE, "Policy metadata", ConnectionProtocol.SSH, "example.invalid", 22)))
        val key = SshHostKeyIdentity.create("ssh-ed25519", "SHA256:" +
            Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 }))
        val pin = SshTrustSnapshot.create(WindowOwner.CONSOLE, uuid(), profile.peer, 1, SshTrustStatus.accepted(key))
        return FilesResourceDraft.create(WindowOwner.CONSOLE, "Policy only", profile, pin, ".", "exact-account")
    }
    private fun candidate(draft: FilesResourceDraft) = FilesListingCandidate(draft.profile, draft.acceptedTrust,
        draft.navigationRoot, draft.accountName, SystemClock.elapsedRealtime() + 30_000)
    private fun field(model: SshFilesSessionModel, name: String): Any? =
        model.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(model)

    @Test fun constructedMetadataCannotCreateAListingOpportunityOrRegistration() {
        val proposal = candidate(draft())
        main {
            val model = SshFilesSessionModel(app)
            val owner = Any()
            model.onOwnerStarted(owner)
            model.onOwnerForeground(owner, true)
            try {
                model.register(proposal, "Cannot authorize")
                model.register(proposal, "Cannot replay")
                model.cancelRegistration(proposal)
                assertNull(model.state.registrationCandidate)
                assertNull(model.state.registrationSaved)
                assertFalse(model.state.registrationPending)
                assertNull(model.state.registrationCode)
            } finally { model.onOwnerStopped(owner, false) }
        }
    }

    @Test fun staleWindowCannotRestoreForegroundOrStopTheCurrentOwner() {
        main {
            val model = SshFilesSessionModel(app)
            val first = Any()
            val stale = Any()
            model.onOwnerStarted(first)
            model.onOwnerForeground(first, true)
            try {
                model.onOwnerForeground(first, false)
                model.onOwnerForeground(stale, true)
                model.onOwnerStopped(stale, false)
                model.onOwnerStarted(stale)
                assertSame(first, field(model, "registrationOwner"))
                assertEquals(false, field(model, "registrationForeground"))
                model.onOwnerForeground(first, true)
                assertEquals(true, field(model, "registrationForeground"))
            } finally { model.onOwnerStopped(first, false) }
            assertNull(field(model, "registrationOwner"))
            model.onOwnerForeground(first, true)
            assertEquals(false, field(model, "registrationForeground"))
        }
    }

    @Test fun restoredUncertaintyIsNotAWriteAndSurvivesOwnerReplacement() {
        val proposal = candidate(draft())
        main {
            val model = SshFilesSessionModel(app)
            val original = Any()
            val replacement = Any()
            model.onOwnerStarted(original)
            model.onOwnerForeground(original, true)
            model.restoreRegistrationUncertainty()
            model.onOwnerStopped(original, true)
            model.onOwnerStarted(replacement)
            model.onOwnerForeground(replacement, true)
            try {
                model.register(proposal, "No restored approval")
                assertTrue(model.state.registrationUncertain)
                assertFalse(model.state.registrationPending)
                assertNull(model.state.registrationSaved)
                assertNull(model.state.registrationCandidate)
                assertEquals("COMPLETION_UNKNOWN", model.state.registrationCode)
            } finally { model.onOwnerStopped(replacement, false) }
        }
    }

    @Test fun privateTicketsDenyMainThreadAndUnsupportedOwningPackageBeforeStorage() {
        val registration = FilesResourceRegistration.create(uuid(), draft())
        val database = File(app.noBackupFilesDir, "files-resources-v1/resources.db")
        val existed = database.exists()
        main {
            assertEquals(FilesResourceCode.WRONG_THREAD,
                (FilesLocationTickets.issue(app, registration) as FilesResourceResult.Failure).code)
            assertEquals(FilesResourceCode.WRONG_THREAD,
                (FilesLocationTickets.consume(app, uuid()) as FilesResourceResult.Failure).code)
        }
        // This class belongs to standalone platform AndroidTest, not the two product callers.
        assertFalse(app.packageName in setOf("io.github.baitian6641.cloudy.console", "io.github.baitian6641.cloudy.desktop"))
        assertEquals(FilesResourceCode.WRONG_OWNER,
            (FilesLocationTickets.issue(app, registration) as FilesResourceResult.Failure).code)
        assertEquals(FilesResourceCode.WRONG_OWNER,
            (FilesLocationTickets.consume(app, uuid()) as FilesResourceResult.Failure).code)
        assertEquals(existed, database.exists())
    }
}
