package io.github.baitian6641.cloudy.console

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.CloudyTerminalActivity
import io.github.baitian6641.cloudy.platform.R as platformR
import io.github.baitian6641.cloudy.testing.SshAuthenticateFixture
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real journey: WorkspaceActivity home -> search -> owned host selection -> Connect ->
 * the TerminalActivity that home actually launched -> real SSH -> retirement -> draft recreation.
 * The test never constructs a terminal Intent and never calls a private production function.
 * No Compose test clock controls the WebView's compositor or native input dispatcher.
 */
@RunWith(AndroidJUnit4::class)
class ConsoleTerminalActivityTest {
    private lateinit var automation: UiAutomation
    private lateinit var instrumentation: Instrumentation
    private var control = "none"

    @Test fun explicitSessionExecutesInputAndRetiresWithoutRestoringCredentials() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        val run = InstrumentationRegistry.getArguments().getString("cloudyTerminalRun")
        assumeTrue("Requires an owned real loopback SSH fixture", run != null)
        val context = instrumentation.targetContext
        // Preserve actual enabled accessibility services; this is not a spoken-service qualification.
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        var fixture: SshAuthenticateFixture? = null
        var workspace: ActivityScenario<WorkspaceActivity>? = null
        var terminal: Activity? = null
        var ownedProfileId: String? = null
        var homeConnectActivated = false
        var markerAccepted = false
        var passed = false
        var stage = "SETUP"
        try {
            val owned = SshAuthenticateFixture.open(context, WindowOwner.CONSOLE, checkNotNull(run), "success")
            fixture = owned
            owned.seed()
            ownedProfileId = owned.profileId
            val witness = File(context.noBackupFilesDir, "ssh-authenticate-fixture/$run-terminal-proof.json")
            check(!witness.exists() && !Files.isSymbolicLink(witness.toPath()))
            stage = "HOME_LAUNCH"
            workspace = ActivityScenario.launch(WorkspaceActivity::class.java)
            stage = "HOME_SEARCH"
            // The real Termius-style home is the entry point; the host is found only through its search.
            revealHome("home_search")
            setText("home_search", owned.profileLabel)
            waitFor(20_000) {
                val rows = nodes("home_row")
                rows.size == 1 && owned.profileLabel in textOf(rows.single())
            }
            stage = "HOME_SELECT"
            val row = revealHome("home_row")
            check(owned.profileLabel in textOf(row)) { "HOME_SEARCH_NOT_OWNED" }
            clickHome("home_row")
            waitFor(15_000) { node("home_connect")?.isEnabled == true }
            check(owned.profileLabel in textOf(revealHome("home_detail"))) { "HOME_SELECTION_NOT_OWNED" }
            check(resumedTerminals().isEmpty()) { "TERMINAL_PREEXISTING" }
            stage = "HOME_CONNECT"
            clickHome("home_connect")
            homeConnectActivated = true
            owned.phase("HOME_CONNECT_ACTIVATED")
            stage = "TERMINAL_LAUNCH"
            val launched = awaitTerminal(owned.profileId)
            terminal = launched
            secure(launched)
            stage = "TERMINAL_SELECTED"
            // Initial navigation must settle on the owned row without any test refresh or rebind.
            waitFor(30_000) { node("terminal_selected")?.let { owned.profileLabel in textOf(it) } == true }
            check(launched.intent.getStringExtra(CloudyTerminalActivity.EXTRA_PROFILE_ID) == owned.profileId) {
                "TERMINAL_LAUNCH_TARGET_MISMATCH"
            }
            stage = "LEDGER"
            // Account for the marker without refreshing the initial selection. Authentication below
            // still rejects an initial profile snapshot made stale by an early navigation.
            owned.acceptHomeOpenMarker()
            markerAccepted = true
            stage = "CREDENTIAL_DRAFT"
            // First authentication is entered immediately; recreation happens only after this session.
            credentials(owned)
            stage = "AUTHENTICATING"
            click("terminal_connect_button")
            waitFor(20_000) { node("terminal_live_status")?.text?.toString() ==
                context.getString(platformR.string.terminal_status_ready) }
            owned.phase("TERMINAL_PTY_OPEN")
            secure(launched)
            stage = "COMMAND"
            val connection = inputConnection(launched)
            commit(launched, connection, "printf '%s' 'CLOUDY_TERMINAL_$run' > /tmp/cloudy-terminal-$run.proof; " +
                "printf 'CLOUDY_TERMINAL_ROUNDTRIP\\n'")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
            owned.phase("TERMINAL_COMMAND_COMMITTED")
            stage = "REMOTE_WITNESS"
            waitFor(10_000) { witness.exists() }
            check(witness.isFile && !Files.isSymbolicLink(witness.toPath()) && witness.length() in 1..4096)
            val evidence = JSONObject(witness.readText())
            val expectedHash = MessageDigest.getInstance("SHA-256")
                .digest("CLOUDY_TERMINAL_$run".toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            check(evidence.getInt("schema") == 1 && evidence.getString("run") == run &&
                evidence.getString("sha256") == expectedHash)
            owned.phase("TERMINAL_REMOTE_EFFECT_WITNESSED")
            stage = "SURFACE"
            check(captureWindow(launched, File(context.noBackupFilesDir,
                "ssh-authenticate-qualification/$run-terminal-surface.png")) == PixelCopy.SUCCESS)
            stage = "REMOTE_EXIT"
            commit(launched, connection, "exit")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
            waitFor(20_000) { node("terminal_username")?.isEnabled == true }
            check(node("terminal_live_status") == null && reveal("terminal_password").isEnabled)
            for (tag in listOf("terminal_username", "terminal_password")) check(empty(reveal(tag)))
            secure(launched)
            owned.phase("TERMINAL_OWNER_RETIRED")
            File(context.noBackupFilesDir, "ssh-authenticate-qualification/$run-terminal-final-state.json")
                .writeText(JSONObject().put("schema", 1).put("run", run)
                    .put("status", node("terminal_status")?.text?.toString()).toString(2))
            stage = "CREDENTIAL_DRAFT_RECREATE"
            // Only now: type a draft and recreate, proving it is neither restored nor auto-connected.
            credentials(owned)
            val recreated = recreateTerminal(launched, owned.profileId)
            terminal = recreated
            secure(recreated)
            waitFor(30_000) { node("terminal_profile_${owned.profileId}")?.isVisibleToUser == true }
            click("terminal_profile_${owned.profileId}")
            waitFor(10_000) { node("terminal_selected")?.let { owned.profileLabel in textOf(it) } == true }
            for (tag in listOf("terminal_username", "terminal_password")) check(empty(reveal(tag)))
            check(reveal("terminal_connect_button").isEnabled == false) { "TERMINAL_AUTO_CONNECT_BUTTON" }
            check(node("terminal_live_status") == null) { "TERMINAL_AUTO_CONNECTED" }
            owned.phase("TERMINAL_DRAFT_NOT_RESTORED")
            passed = true
        } catch (failure: Throwable) {
            // Neither an accessibility tree nor exception messages may publish password text.
            File(context.noBackupFilesDir, "ssh-authenticate-qualification/$run-terminal-failure.json")
                .writeText(JSONObject().put("stage", stage).put("type", failure.javaClass.simpleName)
                    .put("control", control).toString(2))
            runCatching { fixture?.phase("FAILED") }
        } finally {
            var activitiesClosed = false
            try {
                closeTerminal(ownedProfileId, terminal)
                workspace?.close()
                activitiesClosed = true
            } finally {
                val owned = fixture
                if (owned != null) {
                    if (activitiesClosed) {
                        // A failed journey after Connect still cleans up only when the sole mutation
                        // is the exact expected marker; otherwise restore's guard preserves the store.
                        if (homeConnectActivated && !markerAccepted) runCatching { owned.acceptHomeOpenMarker() }
                        owned.restore(passed)
                    } else {
                        runCatching { owned.phase("ACTIVITY_CLOSE_UNCONFIRMED_PRESERVE") }
                    }
                }
            }
        }
        check(passed) { "TERMINAL_QUALIFICATION_FAILED_SEE_PRIVATE_PHASE_RECEIPT" }
    }

    private fun credentials(fixture: SshAuthenticateFixture) {
        setText("terminal_username", fixture.username)
        setText("terminal_password", fixture.password)
        waitFor(10_000) { node("terminal_connect_button")?.isEnabled == true }
    }

    private fun setText(tag: String, value: String) {
        val field = reveal(tag)
        check(field.isEnabled && field.isEditable)
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
    }

    private fun click(tag: String) {
        control = tag
        reveal(tag)
        waitFor(10_000) { node(tag)?.isEnabled == true }
        clickNode(checkNotNull(node(tag)))
    }

    private fun clickHome(tag: String) {
        control = tag
        revealHome(tag)
        waitFor(10_000) { node(tag)?.isEnabled == true }
        clickNode(checkNotNull(node(tag)))
    }

    private fun clickNode(target: AccessibilityNodeInfo) {
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        check(!bounds.isEmpty)
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }

    private fun empty(field: AccessibilityNodeInfo) = field.text.isNullOrEmpty() || field.isShowingHintText

    private fun textOf(node: AccessibilityNodeInfo): String {
        val text = StringBuilder()
        fun visit(current: AccessibilityNodeInfo) {
            current.text?.let { text.append(it).append('\n') }
            current.contentDescription?.let { text.append(it).append('\n') }
            for (index in 0 until current.childCount) current.getChild(index)?.let { visit(it) }
        }
        visit(node)
        return text.toString()
    }

    private fun node(tag: String): AccessibilityNodeInfo? {
        val root = automation.rootInActiveWindow ?: return null
        if (root.packageName?.toString() != PACKAGE) return null
        val tagKey = TAG_KEY
        fun find(current: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            // Compose1.12 exposes test tags as requested extra data, not eager extras.
            if (tagKey in current.availableExtraData) current.refreshWithExtraData(tagKey, Bundle())
            if (current.extras.getCharSequence(tagKey)?.toString() == tag) return current
            for (index in 0 until current.childCount) current.getChild(index)?.let { child ->
                find(child)?.let { return it }
            }
            return null
        }
        return find(root)
    }

    private fun nodes(tag: String): List<AccessibilityNodeInfo> {
        val root = automation.rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() != PACKAGE) return emptyList()
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun collect(current: AccessibilityNodeInfo) {
            if (TAG_KEY in current.availableExtraData) current.refreshWithExtraData(TAG_KEY, Bundle())
            if (current.extras.getCharSequence(TAG_KEY)?.toString() == tag) found += current
            for (index in 0 until current.childCount) current.getChild(index)?.let { collect(it) }
        }
        collect(root)
        return found
    }

    private fun reveal(tag: String): AccessibilityNodeInfo {
        control = tag
        for (index in 0 until 12) {
            val scroll = node("terminal_connect") ?: break
            if (!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break
        }
        var found: AccessibilityNodeInfo? = null
        waitFor(10_000) {
            found = node(tag)?.takeIf { it.isVisibleToUser }
            if (found == null) node("terminal_connect")?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            found != null
        }
        return checkNotNull(found)
    }

    /** Scrolls the real home detail pane when the action is below the fold of a short window. */
    private fun revealHome(tag: String): AccessibilityNodeInfo {
        control = tag
        var found: AccessibilityNodeInfo? = null
        waitFor(15_000) {
            found = node(tag)?.takeIf { it.isVisibleToUser }
            if (found == null) {
                // Compose composes every home row (no lazy list), so climb from the target itself
                // when it is off-screen, falling back to the detail pane's scroll container.
                var scroller: AccessibilityNodeInfo? = node(tag) ?: node("home_detail")
                while (scroller != null) {
                    val current = scroller
                    if (current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }) {
                        current.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        break
                    }
                    scroller = current.parent
                }
            }
            found != null
        }
        return checkNotNull(found)
    }

    private fun waitFor(timeout: Long, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!predicate()) {
            check(SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(20)
        }
    }

    private fun secure(activity: Activity) {
        instrumentation.runOnMainSync {
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    private fun resumedActivities(): List<Activity> {
        val resumed = mutableListOf<Activity>()
        instrumentation.runOnMainSync {
            resumed += ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        }
        return resumed
    }

    private fun resumedTerminals(): List<Activity> = resumedActivities().filterIsInstance<CloudyTerminalActivity>()

    private fun foregroundTerminal(profileId: String): Activity? = resumedTerminals().singleOrNull {
        it.intent.getStringExtra(CloudyTerminalActivity.EXTRA_PROFILE_ID) == profileId
    }

    /** Finds the terminal instance the home actually opened; never creates a replacement Intent. */
    private fun awaitTerminal(profileId: String): Activity {
        var found: Activity? = null
        waitFor(30_000) {
            found = foregroundTerminal(profileId)
            found != null
        }
        return checkNotNull(found)
    }

    private fun recreateTerminal(activity: Activity, profileId: String): Activity {
        instrumentation.runOnMainSync { activity.recreate() }
        var fresh: Activity? = null
        waitFor(30_000) {
            val resumed = foregroundTerminal(profileId)
            if (resumed != null && resumed !== activity) fresh = resumed
            fresh != null
        }
        return checkNotNull(fresh)
    }

    private fun closeTerminal(profileId: String?, activity: Activity?) {
        if (profileId == null) {
            check(activity == null)
            return
        }
        fun ownedActivities(): List<Activity> {
            val owned = mutableListOf<Activity>()
            instrumentation.runOnMainSync {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                for (stage in Stage.values()) {
                    if (stage == Stage.DESTROYED) continue
                    owned += monitor.getActivitiesInStage(stage).filter {
                        it is CloudyTerminalActivity &&
                            it.intent.getStringExtra(CloudyTerminalActivity.EXTRA_PROFILE_ID) == profileId
                    }
                }
            }
            return owned
        }
        // An unrelated terminal may be present when setup fails. Its lifecycle is not ours to end.
        val closing = (ownedActivities() + listOfNotNull(activity)).distinct()
        instrumentation.runOnMainSync {
            for (candidate in closing) {
                check(candidate is CloudyTerminalActivity &&
                    candidate.intent.getStringExtra(CloudyTerminalActivity.EXTRA_PROFILE_ID) == profileId)
                if (!candidate.isFinishing && !candidate.isDestroyed) candidate.finish()
            }
        }
        waitFor(30_000) { ownedActivities().isEmpty() && closing.all { it.isDestroyed } }
    }

    private fun inputConnection(activity: Activity): InputConnection {
        var connection: InputConnection? = null
        waitFor(5_000) {
            instrumentation.runOnMainSync {
                val web = checkNotNull(webView(activity.window.decorView))
                web.requestFocus()
                connection = web.onCreateInputConnection(EditorInfo())
            }
            connection != null
        }
        return checkNotNull(connection)
    }

    private fun commit(activity: Activity, connection: InputConnection, command: String) {
        javascript(activity, "window.cloudyQualificationCommit=false;" +
            "document.addEventListener('input',()=>{window.cloudyQualificationCommit=true;},{once:true});")
        // Chromium's boolean is fire-and-forget. Observe DOM input, then the actual remote effect.
        val submitted = FutureTask { connection.commitText(command, 1); Unit }
        check(checkNotNull(connection.handler).post(submitted))
        submitted.get(5, TimeUnit.SECONDS)
        waitFor(5_000) { javascript(activity, "window.cloudyQualificationCommit===true") == "true" }
    }

    private fun javascript(activity: Activity, script: String): String {
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        instrumentation.runOnMainSync {
            checkNotNull(webView(activity.window.decorView)).evaluateJavascript(script) {
                result.set(it)
                done.countDown()
            }
        }
        check(done.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun captureWindow(activity: Activity, path: File): Int {
        val done = CountDownLatch(1)
        val result = AtomicInteger(-1)
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height,
                Bitmap.Config.ARGB_8888)
            PixelCopy.request(activity.window, bitmap, {
                result.set(it)
                done.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        if (!done.await(5, TimeUnit.SECONDS)) return -1
        try {
            if (result.get() == PixelCopy.SUCCESS) path.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            return result.get()
        } finally { bitmap.recycle() }
    }

    private fun webView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) webView(view.getChildAt(index))?.let { return it }
        return null
    }

    private companion object {
        const val PACKAGE = "io.github.baitian6641.cloudy.console"
        const val TAG_KEY = "androidx.compose.ui.semantics.testTag"
    }
}
