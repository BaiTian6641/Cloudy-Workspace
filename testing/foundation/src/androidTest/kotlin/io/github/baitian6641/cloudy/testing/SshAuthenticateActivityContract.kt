package io.github.baitian6641.cloudy.testing

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Process
import android.view.WindowManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.CloudySshAuthenticateActivity
import io.github.baitian6641.cloudy.platform.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Opt-in installed-product UI using a real leader-owned SSH fixture. No listeners, credentials in
 * arguments, vault prompts or fake auth results. Private metadata receipts survive for collection.
 * Every thrown test failure is sanitized: Compose failures can otherwise dump password semantics.
 */
abstract class SshAuthenticateActivityContract(private val expectedOwner: WindowOwner) {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun realPasswordAuthenticationSucceedsAndRetiresInput() = exercise("success")
    @Test fun realServerDenialIsUnconfirmedWithoutRetry() = exercise("denial")
    @Test fun realPasswordChangeRequestIsRefusedWithoutChange() = exercise("change")
    @Test fun heldRealAuthenticationCanBeCancelledWithoutReplay() = exercise("cancel")
    @Test fun credentialDraftAndHeldAttemptAreNotRestoredOrReplayed() = exercise("recreate")

    private fun exercise(case: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val run = InstrumentationRegistry.getArguments().getString("cloudySshAuthenticateRun")
        // Empty Compose rule: skip precedes private fixture reads, store operations and launch.
        assumeTrue("Explicit owning-app SSH authentication qualification only", run != null)
        var fixture: SshAuthenticateFixture? = null
        var scenario: ActivityScenario<CloudySshAuthenticateActivity>? = null
        var failure = false
        var savedState: CredentialStateObserver? = null
        val context = instrumentation.targetContext
        val application = context.applicationContext as Application
        try {
            check(context.packageName == "io.github.baitian6641.cloudy.${expectedOwner.wireId}" &&
                context.applicationInfo.uid == Process.myUid() && !context.isDeviceProtectedStorage)
            @Suppress("DEPRECATION")
            val info = context.packageManager.getActivityInfo(ComponentName(context, CloudySshAuthenticateActivity::class.java), 0)
            check(!info.exported && info.applicationInfo.uid == Process.myUid())
            val owned = SshAuthenticateFixture.open(context, expectedOwner, checkNotNull(run), case)
            fixture = owned
            owned.seed()
            val observer = CredentialStateObserver(owned.username, owned.password)
            savedState = observer
            application.registerActivityLifecycleCallbacks(observer)
            scenario = ActivityScenario.launch(Intent(context, CloudySshAuthenticateActivity::class.java))
            SshAuthenticateFixture.settle()
            secure(checkNotNull(scenario))
            select(owned)
            input(owned)
            if (case == "recreate") {
                owned.phase("UNSUBMITTED_DRAFT_RECREATING")
                checkNotNull(scenario).recreate()
                SshAuthenticateFixture.settle()
                secure(checkNotNull(scenario))
                check(compose.onAllNodesWithTag("ssh_auth_username").fetchSemanticsNodes().isEmpty())
                check(compose.onAllNodesWithTag("ssh_auth_password").fetchSemanticsNodes().isEmpty())
                check(observer.saved.get() >= 1 && observer.clean.get()) { "AUTH_DRAFT_SAVED_STATE_RETAINED" }
                select(owned)
                emptyInput()
                input(owned) // Explicit fresh user entry, not restored or replayed state.
            }
            owned.phase("CASE_START") // Append-only phase array retains this even if host polling is later.
            scroll("ssh_auth_authenticate")
            compose.onNodeWithTag("ssh_auth_authenticate").assertIsEnabled().performClick()
            if (case == "cancel" || case == "recreate") {
                owned.awaitActualServerRequest() // Parent confirms actual new server request, not UI busy.
                if (case == "cancel") {
                    scroll("ssh_auth_cancel")
                    compose.onNodeWithTag("ssh_auth_cancel").assertIsEnabled().performClick()
                } else checkNotNull(scenario).recreate()
                SshAuthenticateFixture.settle()
                secure(checkNotNull(scenario))
                if (case == "recreate") {
                    // Its startup read may meet the still-retiring old capacity-one operation.
                    // Either honest transient is allowed; explicit Refresh below must retain uncertainty.
                    status(context.getString(R.string.ssh_auth_interrupted), context.getString(R.string.ssh_auth_busy))
                } else status(context.getString(R.string.ssh_auth_interrupted))
                if (case == "recreate") {
                    check(observer.saved.get() >= 2 && observer.clean.get()) { "AUTH_PENDING_STATE_RETAINED_INPUT" }
                    check(compose.onAllNodesWithTag("ssh_auth_username").fetchSemanticsNodes().isEmpty())
                    check(compose.onAllNodesWithTag("ssh_auth_password").fetchSemanticsNodes().isEmpty())
                } else {
                    emptyInput()
                    undoRedoCannotRecoverInput()
                    owned.recordInputHistoryCheck()
                }
                // Refresh reads only; the host event range independently checks no new request.
                scroll("ssh_auth_refresh")
                compose.onNodeWithTag("ssh_auth_refresh").assertIsEnabled().performClick()
                SshAuthenticateFixture.settle()
                status(context.getString(R.string.ssh_auth_interrupted))
                check(compose.onAllNodesWithTag("ssh_auth_authenticate").fetchSemanticsNodes().isEmpty())
                owned.phase("CANCEL_OR_RECREATE_NO_REPLAY_CONFIRMED")
            } else {
                val expected = when (case) {
                    "success" -> context.getString(R.string.ssh_auth_success)
                    "change" -> context.getString(R.string.ssh_auth_failure, "PASSWORD_CHANGE_REFUSED")
                    else -> context.getString(R.string.ssh_auth_failure, "AUTHENTICATION_FAILED")
                }
                status(expected)
                SshAuthenticateFixture.settle()
                emptyInput()
                if (case == "success") {
                    undoRedoCannotRecoverInput()
                    owned.recordInputHistoryCheck()
                }
                owned.phase("EXACT_RESULT_AND_INPUT_RETIREMENT_CONFIRMED")
            }
            owned.assertUnchanged()
            owned.recordVerification(observer.saved.get(), observer.clean.get())
            owned.phase("CASE_ASSERTIONS_CONFIRMED")
        } catch (_: Throwable) {
            failure = true
            try { fixture?.phase("FAILED") } catch (_: Throwable) { /* Preserve original private receipt if write fails. */ }
        } finally {
            var activityClosed = true
            try { scenario?.close() } catch (_: Throwable) { failure = true; activityClosed = false }
            savedState?.let {
                try { application.unregisterActivityLifecycleCallbacks(it) } catch (_: Throwable) { failure = true }
                it.dispose()
            }
            try {
                check(activityClosed) { "AUTH_ACTIVITY_CLOSE_UNCONFIRMED" }
                fixture?.restore(passed = !failure)
            } catch (_: Throwable) {
                failure = true
                try {
                    fixture?.phase("FAILED")
                    fixture?.phase("UNSETTLED_OR_CHANGED_PRESERVE_FIXTURE")
                } catch (_: Throwable) { }
            }
        }
        // Never attach original causes: framework assertion text can contain input semantics.
        if (failure) throw AssertionError("SSH_AUTHENTICATE_QUALIFICATION_FAILED_SEE_PRIVATE_PHASE_RECEIPT")
    }

    private fun select(fixture: SshAuthenticateFixture) {
        scroll("ssh_auth_profile_${fixture.profileId}")
        compose.onNodeWithTag("ssh_auth_profile_${fixture.profileId}").assertIsEnabled().performClick()
        compose.waitForIdle()
    }
    private fun input(fixture: SshAuthenticateFixture) {
        scroll("ssh_auth_username")
        compose.onNodeWithTag("ssh_auth_username").performTextReplacement(fixture.username)
        scroll("ssh_auth_password")
        compose.onNodeWithTag("ssh_auth_password").performTextReplacement(fixture.password)
    }
    private fun emptyInput() {
        for (tag in listOf("ssh_auth_username", "ssh_auth_password")) {
            scroll(tag)
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            check(node.config[SemanticsProperties.EditableText].text.isEmpty()) { "AUTH_INPUT_NOT_RETIRED" }
        }
        scroll("ssh_auth_authenticate")
        compose.onNodeWithTag("ssh_auth_authenticate").assertIsNotEnabled()
    }
    private fun undoRedoCannotRecoverInput() {
        for (tag in listOf("ssh_auth_username", "ssh_auth_password")) {
            scroll(tag)
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithTag(tag).performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.Z)
                keyUp(Key.CtrlLeft)
            }
            check(compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty()) {
                "CLEARED_INPUT_RECOVERED_BY_UNDO"
            }
            compose.onNodeWithTag(tag).performKeyInput {
                keyDown(Key.CtrlLeft)
                keyDown(Key.ShiftLeft)
                pressKey(Key.Z)
                keyUp(Key.ShiftLeft)
                keyUp(Key.CtrlLeft)
            }
            check(compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty()) {
                "CLEARED_INPUT_RECOVERED_BY_REDO"
            }
        }
    }
    private fun scroll(tag: String) {
        compose.onNodeWithTag("ssh_auth_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun status(vararg expected: String) {
        scroll("ssh_auth_status")
        compose.waitUntil(20_000) {
            val node = compose.onAllNodesWithTag("ssh_auth_status").fetchSemanticsNodes().singleOrNull()
            val text = node?.config?.get(SemanticsProperties.Text)?.singleOrNull()?.text
            text != null && expected.contains(text)
        }
    }
    private fun secure(scenario: ActivityScenario<CloudySshAuthenticateActivity>) {
        scenario.onActivity { activity ->
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            check(activity.window.decorView.importantForAutofill == android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS)
        }
    }

    /** Observe real framework saves; retain only booleans/counts, never a Bundle or credential bytes. */
    private class CredentialStateObserver(username: String, password: String) : Application.ActivityLifecycleCallbacks {
        private val needles = listOf(username, password).flatMap {
            listOf(it.toByteArray(Charsets.UTF_8), it.toByteArray(Charsets.UTF_16LE), it.toByteArray(Charsets.UTF_16BE))
        }
        val saved = AtomicInteger()
        val clean = AtomicBoolean(true)
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            if (activity !is CloudySshAuthenticateActivity) return
            try {
                val record = outState.getBundle("io.github.baitian6641.cloudy.ssh-auth.v1")
                if (record == null || record.keySet() != setOf("schema", "owner", "pending")) clean.set(false)
                val parcel = Parcel.obtain()
                val bytes = try { parcel.writeBundle(outState); parcel.marshall() } finally { parcel.recycle() }
                try {
                    if (bytes.size > 1_048_576 || needles.any { needle -> contains(bytes, needle) }) clean.set(false)
                } finally { bytes.fill(0) }
            } catch (_: Throwable) { clean.set(false) }
            saved.incrementAndGet()
        }
        private fun contains(bytes: ByteArray, needle: ByteArray): Boolean {
            if (needle.isEmpty()) return false
            for (start in 0..(bytes.size - needle.size)) {
                if (needle.indices.all { bytes[start + it] == needle[it] }) return true
            }
            return false
        }
        fun dispose() { needles.forEach { it.fill(0) } }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
