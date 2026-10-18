package io.github.baitian6641.cloudy.console

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
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
import io.github.baitian6641.cloudy.testing.ime.ImeQualificationSession
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
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
    private var ime: ImeQualificationSession? = null
    private var control = "none"
    private var lastPointer: JSONObject? = null
    private var cancellation: File? = null
    private var settling = false

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
            val cancel = File(context.noBackupFilesDir, "ssh-authenticate-fixture/$run-cancel")
            cancellation = cancel
            check(!cancel.exists() && !Files.isSymbolicLink(cancel.toPath()))
            owned.seed()
            ownedProfileId = owned.profileId
            val witness = File(context.noBackupFilesDir, "ssh-authenticate-fixture/$run-terminal-proof.json")
            check(!witness.exists() && !Files.isSymbolicLink(witness.toPath()))
            stage = "IME_SETUP"
            val allowDeviceChange = InstrumentationRegistry.getArguments()
                .getString("cloudyAllowDeviceImeChange")?.toBooleanStrictOrNull() == true
            ime = ImeQualificationSession.open(instrumentation, automation, checkNotNull(run),
                allowDeviceChange = allowDeviceChange,
                cancellation = { cancellation?.exists() == true })
            stage = "HOME_LAUNCH"
            workspace = ActivityScenario.launch(WorkspaceActivity::class.java)
            stage = "HOME_SEARCH"
            // The real Termius-style home is the entry point; the host is found only through its search.
            revealHome("home_search")
            // The first catalog publication composes the list asynchronously; entering a query
            // before that publication can be cleared by the same reflow. Wait for real rows.
            waitFor(15_000) { nodes("home_row").isNotEmpty() }
            setText("home_search", owned.profileLabel)
            waitFor(20_000) {
                val rows = nodes("home_row")
                rows.size == 1 && owned.profileLabel in textOf(rows.single())
            }
            stage = "HOME_SELECT"
            val row = revealHome("home_row")
            check(owned.profileLabel in textOf(row)) { "HOME_SEARCH_NOT_OWNED" }
            clickHome("home_row")
            revealHome("home_connect")
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
            val proofPath = checkNotNull(InstrumentationRegistry.getArguments().getString("cloudyTerminalProofPath"))
            check(proofPath.startsWith("/") && proofPath.length <= 4096 &&
                '\u0000' !in proofPath && '\n' !in proofPath && '\r' !in proofPath)
            val quotedProof = "'" + proofPath.replace("'", "'\"'\"'") + "'"
            commit(launched, "(umask 077; set -C; printf '%s' 'CLOUDY_TERMINAL_$run' > $quotedProof) && " +
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
            if (InstrumentationRegistry.getArguments().getString("cloudyTerminalInteractions") == "true") {
                qualifyInteractions(launched, owned, checkNotNull(run)) { stage = it }
            }
            stage = "REMOTE_EXIT"
            commit(launched, "exit")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
            waitFor(20_000) { node("terminal_username")?.isEnabled == true }
            val credentialTag = if (authMethod() == "keyboard-interactive") {
                "terminal_interactive_response"
            } else {
                "terminal_password"
            }
            check(node("terminal_live_status") == null && reveal(credentialTag).isEnabled)
            for (tag in listOf("terminal_username", credentialTag)) check(empty(reveal(tag)))
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
            runCatching {
                val live = node("terminal_live_status")?.text?.toString()
                val report = JSONObject().put("stage", stage).put("type", failure.javaClass.simpleName)
                    .put("control", control).put("lastPointer", lastPointer)
                    .put("status", node("terminal_status")?.text?.toString())
                    .put("liveStatus", live)
                    .put("connectEnabled", node("terminal_connect_button")?.isEnabled)
                val active = terminal
                if (live != null && active != null) runCatching {
                    // Public state only, after authentication fields have left the surface.
                    report.put("rendererState", JSONObject(javascript(active, """
                        (() => {
                          const textarea = document.querySelector('.xterm-helper-textarea');
                          return {screenReaderFlag:location.hash.split('/').pop(),
                            documentFocused:document.hasFocus(), textareaFocused:document.activeElement === textarea,
                            readOnly:textarea?.readOnly, valueLength:textarea?.value.length,
                            warning:document.getElementById('feedback')?.dataset.warning,
                            status:document.getElementById('status')?.textContent};
                        })()
                    """.trimIndent())))
                    instrumentation.runOnMainSync {
                        report.put("nativeWindowFocused", active.hasWindowFocus())
                            .put("nativeRendererFocused", webView(active.window.decorView)?.hasFocus())
                    }
                    report.put("failurePixelCopy", captureWindow(active, File(context.noBackupFilesDir,
                        "ssh-authenticate-qualification/$run-terminal-interaction-failure.png")))
                }.onFailure { report.put("rendererObservationFailure", it.javaClass.simpleName) }
                File(context.noBackupFilesDir, "ssh-authenticate-qualification/$run-terminal-failure.json")
                    .writeText(report.toString(2))
            }
            runCatching {
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("cloudyTerminalFailureStage", stage)
                    putString("cloudyTerminalFailureType", failure.javaClass.simpleName)
                })
            }
            if (stage.startsWith("HOME_") || stage == "TERMINAL_LAUNCH") runCatching {
                // This owned Home has no credential fields; capture layout, never an auth tree.
                var home: Activity? = null
                workspace?.onActivity { home = it }
                home?.let { captureWindow(it, File(context.noBackupFilesDir,
                    "ssh-authenticate-qualification/$run-terminal-interaction-failure.png")) }
            }
            runCatching { fixture?.phase("FAILED") }
        } finally {
            settling = true // Controller cancellation must not interrupt owned restoration.
            var activitiesClosed = false
            try {
                closeTerminal(ownedProfileId, terminal)
                workspace?.close()
                activitiesClosed = true
            } finally {
                try {
                    val owned = fixture
                    if (owned != null) {
                        if (activitiesClosed) {
                            // Restore only the exact expected marker; uncertain stores remain untouched.
                            if (homeConnectActivated && !markerAccepted) runCatching { owned.acceptHomeOpenMarker() }
                            owned.restore(passed)
                        } else {
                            runCatching { owned.phase("ACTIVITY_CLOSE_UNCONFIRMED_PRESERVE") }
                        }
                    }
                } finally {
                    // The client refuses restoration while its owned editor remains active.
                    // IME cleanup must never prevent the profile/trust restoration above.
                    ime?.close()
                }
            }
        }
        check(passed) { "TERMINAL_QUALIFICATION_FAILED_SEE_PRIVATE_PHASE_RECEIPT" }
        check(ime?.cleanupConfirmed == true) {
            "TERMINAL_IME_CLEANUP_UNCONFIRMED_${ime?.closeReason?.name ?: "NONE"}"
        }
    }

    /** Real native controls and Android clipboard, with independently captured host bytes. */
    private fun qualifyInteractions(activity: Activity, fixture: SshAuthenticateFixture, run: String,
                                    setStage: (String) -> Unit) {
        val context = instrumentation.targetContext
        val root = File(context.noBackupFilesDir, "ssh-authenticate-fixture")
        val configFile = File(root, "$run-interactions.json")
        val info = Os.lstat(configFile.path)
        check(OsConstants.S_ISREG(info.st_mode) && info.st_uid == android.os.Process.myUid() &&
            info.st_mode and 511 == 384 && info.st_size in 1..65536)
        val config = JSONObject(configFile.readText())
        check(config.keys().asSequence().toSet() == setOf("schema", "run", "captureRun", "captureCommand", "pasteText"))
        check(config.getInt("schema") == 1 && config.getString("run") == run)
        val captureRun = config.getString("captureRun")
        check(java.util.UUID.fromString(captureRun).let { it.version() == 4 && it.toString() == captureRun })
        val command = config.getString("captureCommand")
        check(command.length in 1..4096 && command.none { Character.isISOControl(it) } &&
            command.startsWith("python3 -I ") && command.endsWith("--run $captureRun"))
        val paste = config.getString("pasteText")
        check(paste.length == 20_000 && paste.all { it == 'B' })
        val readyMarker = "CLOUDY_CAPTURE_READY_$captureRun"
        val label = "Cloudy qualification $run"
        lateinit var clipboard: ClipboardManager
        var previousClip: ClipData? = null
        var ownedClipboard = false
        var restoredClipboard = false
        val localResult = JSONObject().put("schema", 1).put("run", run)
        fun seedClip(value: String, richText: Boolean = false) {
            instrumentation.runOnMainSync {
                check(activity.hasWindowFocus())
                clipboard.setPrimaryClip(if (richText) ClipData.newHtmlText(label, value, "<b>HTML must not be pasted</b>")
                    else ClipData.newPlainText(label, value))
            }
            ownedClipboard = true
        }
        try {
            instrumentation.runOnMainSync {
                check(activity.hasWindowFocus())
                clipboard = checkNotNull(activity.getSystemService(ClipboardManager::class.java))
                previousClip = clipboard.primaryClip
            }
            setStage("INTERACTION_CAPTURE_START")
            commit(activity, command)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
            waitFor(10_000) {
                javascript(activity, "document.querySelector('.xterm-rows')?.textContent.includes(" +
                    JSONObject.quote(readyMarker) + ")===true") == "true"
            }
            fixture.phase("TERMINAL_CAPTURE_READY")

            setStage("INTERACTION_MENU_KEYS")
            val now = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_F10, 0, KeyEvent.META_SHIFT_ON))
            instrumentation.sendKeySync(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_F10, 0, KeyEvent.META_SHIFT_ON))
            val menuTags = listOf("terminal_copy", "terminal_paste", "terminal_select_all",
                "terminal_find", "terminal_menu_focus", "terminal_menu_close")
            waitFor(10_000) { menuTags.any { node(it)?.isFocused == true } }
            // These must remain local: the host capture later excludes both bytes.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_A)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
            waitFor(10_000) { node("terminal_copy") == null }
            check(node("terminal_live_status") != null)
            fixture.phase("TERMINAL_MENU_KEYS_LOCAL")

            setStage("INTERACTION_PREVIEW_CANCEL")
            seedClip("cancel\nnot-sent", richText = true)
            click("terminal_edit")
            waitFor(10_000) { node("terminal_copy")?.isFocused == true }
            click("terminal_paste")
            waitFor(10_000) { node("terminal_paste_insert")?.isFocused == true }
            val previewText = textOf(reveal("terminal_paste_preview"))
            check("cancel" in previewText && "not-sent" in previewText && "HTML must not be pasted" !in previewText)
            check(captureWindow(activity, File(context.noBackupFilesDir,
                "ssh-authenticate-qualification/$run-terminal-paste-preview.png")) == PixelCopy.SUCCESS)
            click("terminal_paste_cancel")
            waitFor(10_000) { node("terminal_paste_preview") == null }
            fixture.phase("TERMINAL_PASTE_CANCELLED")

            setStage("INTERACTION_LARGE_PASTE")
            seedClip(paste)
            click("terminal_edit")
            click("terminal_paste")
            check(node("terminal_paste_preview") == null)
            fixture.phase("TERMINAL_LARGE_PASTE_REQUESTED")
            val witness = File(root, "$run-interaction-proof.json")
            waitFor(25_000) { witness.exists() }
            check(witness.isFile && !Files.isSymbolicLink(witness.toPath()) && witness.length() in 1..4096)
            val proof = JSONObject(witness.readText())
            val expectedHash = MessageDigest.getInstance("SHA-256").digest(paste.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            check(proof.getInt("schema") == 1 && proof.getString("run") == run &&
                proof.getString("captureRun") == captureRun && proof.getBoolean("matched") &&
                proof.getInt("bytes") == 20_000 && proof.getString("sha256") == expectedHash)
            localResult.put("menuAndCancelSentNoBytes", true).put("largePasteBytes", 20_000)
            fixture.phase("TERMINAL_INTERACTION_BYTES_WITNESSED")

            setStage("INTERACTION_FIND")
            click("terminal_edit")
            click("terminal_find")
            setText("terminal_find_query", readyMarker)
            click("terminal_find_next")
            reveal("terminal_find_count")
            waitFor(10_000) { node("terminal_find_count")?.text?.toString() ==
                context.getString(platformR.string.terminal_search_result, 1, 1) }
            check(captureWindow(activity, File(context.noBackupFilesDir,
                "ssh-authenticate-qualification/$run-terminal-search.png")) == PixelCopy.SUCCESS)
            click("terminal_find_close")
            waitFor(10_000) { node("terminal_find_query") == null }
            localResult.put("actualFindCount", 1)

            setStage("INTERACTION_COPY")
            click("terminal_edit")
            click("terminal_select_all")
            click("terminal_edit")
            click("terminal_copy")
            waitFor(10_000) { node("terminal_local_notice")?.text?.toString() ==
                context.getString(platformR.string.terminal_copied) }
            instrumentation.runOnMainSync {
                check(activity.hasWindowFocus())
                val copied = checkNotNull(clipboard.primaryClip)
                check(copied.itemCount == 1 && copied.getItemAt(0).text?.contains(readyMarker) == true)
                check(copied.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
            }
            localResult.put("copiedActualRemoteMarker", true).put("sensitiveHint", true)
            fixture.phase("TERMINAL_CLIPBOARD_AND_FIND_OBSERVED")
        } catch (failure: Throwable) {
            // This opt-in lane has only synthetic clipboard content. Capture its actual local
            // surface before cleanup changes focus; never dump credential fields or a full tree.
            runCatching {
                localResult.put("failureControl", control)
                    .put("sourceLine", failure.stackTrace.firstOrNull { it.className.startsWith(javaClass.name) }?.lineNumber)
                    .put("lastPointer", lastPointer)
                    .put("menuVisible", node("terminal_menu_overlay")?.isVisibleToUser == true)
                    .put("previewExists", node("terminal_paste_preview") != null)
                    .put("previewVisible", node("terminal_paste_preview")?.isVisibleToUser == true)
                    .put("localNotice", node("terminal_local_notice")?.text?.toString())
                    .put("failurePixelCopy", captureWindow(activity, File(context.noBackupFilesDir,
                        "ssh-authenticate-qualification/$run-terminal-interaction-failure.png")))
            }
            throw failure
        } finally {
            if (ownedClipboard) instrumentation.runOnMainSync {
                if (activity.hasWindowFocus()) {
                    val current = clipboard.primaryClip
                    val ours = current?.description?.label?.toString() == label ||
                        (current?.description?.label?.toString() == "Cloudy terminal text" &&
                            current.itemCount == 1 && current.getItemAt(0).text?.contains(readyMarker) == true)
                    if (ours) {
                        if (previousClip == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(previousClip!!)
                        restoredClipboard = true
                    }
                }
            }
            localResult.put("ownedClipboardRestored", restoredClipboard)
            File(context.noBackupFilesDir, "ssh-authenticate-qualification/$run-terminal-interactions.json")
                .writeText(localResult.toString(2))
        }
        check(restoredClipboard) { "CLIPBOARD_RESTORE_UNCONFIRMED" }
    }

    private fun authMethod(): String =
        InstrumentationRegistry.getArguments().getString("cloudyTerminalAuth") ?: "password"

    private fun credentials(fixture: SshAuthenticateFixture) {
        setText("terminal_username", fixture.username)
        if (authMethod() == "keyboard-interactive") {
            click("terminal_auth_interactive_method")
            waitFor(10_000) { node("terminal_interactive_response") != null }
            check(node("terminal_connect_button")?.isEnabled == false) {
                "TERMINAL_INTERACTIVE_CONNECT_WITHOUT_RESPONSE"
            }
            setText("terminal_interactive_response", fixture.password)
            waitFor(10_000) { node("terminal_connect_button")?.isEnabled == true }
            return
        }
        // Exercise the product method selector without selecting a key document: the key surface
        // must be reachable and must not enable Connect until a key is actually chosen.
        click("terminal_auth_key_method")
        waitFor(10_000) {
            node("terminal_key_choose")?.isVisibleToUser == true && node("terminal_key_passphrase") != null
        }
        check(node("terminal_connect_button")?.isEnabled == false) { "TERMINAL_KEY_CONNECT_WITHOUT_KEY" }
        click("terminal_auth_password_method")
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
        clickVisibleEnabled(tag)
    }

    private fun clickHome(tag: String) {
        control = tag
        revealHome(tag)
        clickVisibleEnabled(tag)
    }

    private fun clickVisibleEnabled(tag: String) {
        // A down event during an active scroll can stop the animation instead of clicking.
        // Use the platform's event-idle barrier, then acquire the current visible bounds.
        automation.waitForIdle(250, 5_000)
        var target: AccessibilityNodeInfo? = null
        waitFor(10_000) {
            target = node(tag)?.takeIf { candidate ->
                val bounds = Rect()
                candidate.getBoundsInScreen(bounds)
                candidate.isEnabled && candidate.isVisibleToUser && !bounds.isEmpty
            }
            target != null
        }
        // Keep the node that actually satisfied readiness. A second root lookup can race
        // a transient system clipboard window and return null before any pointer is injected.
        clickNode(checkNotNull(target))
    }

    private fun clickNode(target: AccessibilityNodeInfo) {
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        lastPointer = JSONObject().put("control", control).put("left", bounds.left).put("top", bounds.top)
            .put("right", bounds.right).put("bottom", bounds.bottom)
        check(!bounds.isEmpty)
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            // AOSP UiAutomator uses a 100ms regular press, not same-timestamp down/up.
            // This is the injected gesture duration, not a product readiness delay.
            if (action == MotionEvent.ACTION_UP) SystemClock.sleep(100)
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                val accepted = automation.injectInputEvent(event, true)
                lastPointer?.put(if (action == MotionEvent.ACTION_DOWN) "downAccepted" else "upAccepted", accepted)
                check(accepted)
            } finally { event.recycle() }
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
            if (found == null) showOnScreen(node(tag),
                node("terminal_search_overlay") ?: node("terminal_paste_overlay") ?:
                    node("terminal_menu_overlay") ?: node("terminal_connect"))
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
            if (found == null) showOnScreen(node(tag), node("home_detail"))
            found != null
        }
        return checkNotNull(found)
    }

    private fun showOnScreen(target: AccessibilityNodeInfo?, fallback: AccessibilityNodeInfo?) {
        if (target?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id) == true) return
        // Skip exhausted nodes; an advertised but refused action must not hide a usable ancestor.
        var scroller = target ?: fallback
        while (scroller != null) {
            val current = scroller
            if (current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } &&
                current.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return
            scroller = current.parent
        }
    }

    private fun waitFor(timeout: Long, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (true) {
            if (!settling) check(cancellation?.exists() != true) { "CONTROLLER_CANCELLED" }
            if (predicate()) return
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

    /** Focuses the actual renderer; the fixture owns the OS-served connection. */
    private fun focusTerminal(activity: Activity): WebView {
        var web: WebView? = null
        instrumentation.runOnMainSync {
            val candidate = checkNotNull(webView(activity.window.decorView))
            candidate.requestFocus()
            web = candidate
        }
        val editor = checkNotNull(web)
        waitFor(5_000) {
            javascript(activity,
                "document.hasFocus() && document.activeElement?.classList?.contains('xterm-helper-textarea') === true") == "true"
        }
        instrumentation.runOnMainSync {
            check(editor.hasWindowFocus() && editor.hasFocus()) { "TERMINAL_EDITOR_NOT_FOCUSED" }
        }
        return editor
    }

    private fun commit(activity: Activity, command: String) {
        javascript(activity, """
            (() => {
              const expected = ${JSONObject.quote(command)};
              window.cloudyQualificationCommit = false;
              function observe(event) {
                if (event.data !== expected && event.target?.value !== expected) return;
                window.cloudyQualificationCommit = true;
                document.removeEventListener("input", observe, true);
              }
              document.addEventListener("input", observe, true);
            })()
        """.trimIndent())
        // Submission is not delivery: preserve the exact edit witness before native Enter.
        val result = checkNotNull(ime).commit(focusTerminal(activity), command)
        check(result.submitted) { "IME_NOT_SUBMITTED_${result.reason?.name ?: "UNKNOWN"}" }
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
