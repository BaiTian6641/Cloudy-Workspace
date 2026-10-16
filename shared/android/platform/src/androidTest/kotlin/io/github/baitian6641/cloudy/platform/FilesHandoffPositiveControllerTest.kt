package io.github.baitian6641.cloudy.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import org.json.JSONTokener
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** External controller only. Neither product is instrumented by this test; parent
 * proves that condition, exact APKs, host attempts and old-operation retirement.
 * No UI tree, editable value, raw exception detail or credential is emitted. */
@RunWith(AndroidJUnit4::class)
class FilesHandoffPositiveControllerTest {
    @Test fun consoleToDesktop() = exercise("console-to-desktop")
    @Test fun desktopToConsole() = exercise("desktop-to-console")

    private fun exercise(direction: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val run = args.getString("cloudyFilesPositiveRun")
        assumeTrue("Explicit positive-controller run/direction only", run != null &&
            args.getString("cloudyFilesPositiveDirection") == direction)
        var stage = ControllerStage.PREFLIGHT
        try {
            check(UUID.matches(checkNotNull(run)))
            check(args.getString("cloudyFilesPositiveParentPreflight") == "reviewed-normal-products")
            val context = instrumentation.targetContext
            check(context.packageName !in PRODUCTS && context.applicationInfo.uid == Process.myUid() &&
                !context.isDeviceProtectedStorage && android.app.Application.getProcessName() == context.packageName)
            stage = ControllerStage.PRIVATE_CONFIG
            val config = privateConfig(context.noBackupFilesDir, run, direction)
            val sender = if (direction == "console-to-desktop") PRODUCTS[0] else PRODUCTS[1]
            val receiver = PRODUCTS.single { it != sender }
            stage = ControllerStage.UI_AUTOMATION
            val ui = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            ui.serviceInfo = ui.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            val controls = Controls(ui)
            fun phase(value: String) = instrumentation.sendStatus(0, Bundle().apply {
                putString("cloudyFilesPositivePhase", value)
                putString("cloudyFilesPositiveRun", run)
                putString("cloudyFilesPositiveDirection", direction)
                putLong("cloudyFilesPositiveElapsedMillis", SystemClock.elapsedRealtime())
            })
            // Only the sender's exported MAIN/LAUNCHER entry is navigated explicitly.
            // Receiver visibility must result from the genuine ordinary Open operation.
            stage = ControllerStage.SENDER_LAUNCH
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(sender, "$sender.WorkspaceActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            stage = ControllerStage.SENDER_LOCATIONS
            controls.click(sender, "Saved file locations", scroll = true)
            stage = ControllerStage.SENDER_DISCOVERY
            controls.click(sender, "Locations in the other app", scroll = true)
            stage = ControllerStage.REFRESH_PAGE
            controls.click(sender, "Refresh first page")
            val label = "Location $run A"
            stage = ControllerStage.SELECT_ROW
            controls.rowAction(sender, label, "Select")
            stage = ControllerStage.ASSERT_SELECTED_CARD
            controls.requireSectionText(sender, "Selected shared location", label, scroll = true)
            stage = ControllerStage.BEFORE_OPEN_STATUS
            phase("BEFORE_SINGLE_OPEN")
            // Exactly one action; a false result/timeout never retries or rolls back admission.
            stage = ControllerStage.SINGLE_OPEN
            controls.click(sender, "Request Open in counterpart")
            stage = ControllerStage.RECEIVER_TITLE
            controls.requireText(receiver, "Remote files over SSH")
            stage = ControllerStage.RECEIVER_PROFILE
            controls.requireSectionText(receiver, "Selected endpoint",
                "Files ${receiver.substringAfterLast('.')} $run handoff-receiver", scroll = true)
            stage = ControllerStage.RECEIVER_RESOURCE
            controls.requireSectionText(receiver, "Selected endpoint", "Registered location: $label")
            stage = ControllerStage.RECEIVER_ROOT
            controls.requireSectionText(receiver, "Selected endpoint", "Starting directory: ./nested")
            stage = ControllerStage.RECEIVER_ACCOUNT
            controls.requireFixedAccount(receiver, config.getValue("username"))
            phase("RECEIVER_AUTHENTICATION_VISIBLE")
            stage = ControllerStage.RECEIVER_PASSWORD
            controls.password(receiver, config.getValue("password"))
            stage = ControllerStage.RECEIVER_CONNECT
            controls.click(receiver, "Connect to remote files", scroll = true)
            stage = ControllerStage.RECEIVER_CONNECTED
            controls.requireText(receiver, "Connected to remote files", scroll = true)
            phase("CONNECTED_UI_VISIBLE")
            stage = ControllerStage.NESTED_READ
            controls.rowAction(receiver, "nested.txt", "Read preview")
            stage = ControllerStage.ASSERT_PREVIEW
            controls.requireText(receiver, "Cloudy registered nested SFTP\n", scroll = true)
            phase("NESTED_PREVIEW_VISIBLE")
            stage = ControllerStage.DISCONNECT
            controls.click(receiver, "Disconnect")
            stage = ControllerStage.RECEIVER_IDLE
            controls.requireText(receiver, "Ready to connect", scroll = true)
            phase("DISCONNECT_UI_IDLE_HOST_RETIREMENT_PENDING")
            phase("BEFORE_PLANNED_RECEIVER_CLOSE")
            stage = ControllerStage.RECEIVER_CLOSE
            controls.click(receiver, "Close")
            // Closing the newly dispatched task should reveal the existing sender.
            // Never launch another sender document to manufacture a cleanup result.
            stage = ControllerStage.SENDER_RETURN
            controls.requireText(sender, "Shared locations in the other app")
            stage = ControllerStage.SENDER_REFRESH_ENABLED
            controls.requireActionable(sender, "Refresh first page", scroll = true)
            phase("SENDER_RETURNED_REFRESH_ENABLED_BEFORE_PLANNED_CLOSE")
            stage = ControllerStage.DISCOVERY_CLOSE
            controls.click(sender, "Close")
            stage = ControllerStage.SENDER_LOCATIONS_RETURN
            controls.requireText(sender, "Saved file locations")
            stage = ControllerStage.LOCATIONS_CLOSE
            controls.click(sender, "Close")
            stage = ControllerStage.WORKSPACE_CLOSE
            controls.click(sender, "Close window", scroll = true)
            stage = ControllerStage.FINAL_STATUS
            phase("CONTROLLER_FINISHED_PARENT_SETTLEMENT_REQUIRED")
        } catch (error: Throwable) {
            // Preserve uncertain products/admission/config. Parent reviews the first failure;
            // no finally disconnect, second Open, process kill or credential-bearing diagnostic.
            throw ControllerFailure.capture(stage, error)
        }
    }

    /** Parent provisions ONLY this controller-owned private file; secrets are not arguments.
     * Flat JSON: schema="cloudy-files-positive-v1", run, direction, username, password.
     * Each key/value must be quoted, unique and exact; no additional field is accepted. */
    private fun privateConfig(root: File, run: String, direction: String): Map<String, String> {
        val directory = File(root, "files-handoff-controller")
        for (path in listOf(root, directory)) {
            val stat = Os.lstat(path.path)
            check(!Files.isSymbolicLink(path.toPath()) && OsConstants.S_ISDIR(stat.st_mode) &&
                stat.st_uid == Process.myUid() && stat.st_mode and 511 == 448)
        }
        val file = File(directory, "$run.json")
        val stat = Os.lstat(file.path)
        check(!Files.isSymbolicLink(file.toPath()) && OsConstants.S_ISREG(stat.st_mode) &&
            stat.st_uid == Process.myUid() && stat.st_mode and 511 == 384 && stat.st_size in 1..32_768)
        val bytes = FileChannel.open(file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            check(channel.size() == stat.st_size)
            val buffer = ByteBuffer.allocate(stat.st_size.toInt())
            while (buffer.hasRemaining()) check(channel.read(buffer) > 0)
            check(channel.read(ByteBuffer.allocate(1)) == -1)
            buffer.array()
        }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        bytes.fill(0)
        val json = JSONTokener(text)
        check(json.nextClean() == '{')
        val result = mutableMapOf<String, String>()
        do {
            check(json.nextClean() == '"')
            val key = json.nextString('"'); check(key !in result && result.size < 5)
            check(json.nextClean() == ':' && json.nextClean() == '"')
            result[key] = json.nextString('"')
            val separator = json.nextClean()
            if (separator == '}') break
            check(separator == ',')
        } while (true)
        check(json.nextClean() == '\u0000' && result.keys == setOf("schema", "run", "direction", "username", "password"))
        check(result["schema"] == "cloudy-files-positive-v1" && result["run"] == run && result["direction"] == direction)
        val user = result.getValue("username"); val password = result.getValue("password")
        check(user.length in 1..256 && user.none { Character.isISOControl(it) || Character.isSurrogate(it) })
        check(password.length in 1..4096 && password.none { Character.isSurrogate(it) })
        return result
    }

    private class Controls(private val ui: UiAutomation) {
        private fun root(packageName: String): AccessibilityNodeInfo? {
            val roots = ui.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive && it.isFocused }
                .mapNotNull { it.root }.filter { it.packageName?.toString() == packageName && it.isVisibleToUser }
            check(roots.size <= 1)
            return roots.singleOrNull()
        }
        private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo, depth: Int) {
                check(depth <= 40 && result.size < 2048)
                if (!node.isVisibleToUser) return
                result.add(node)
                for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it, depth + 1) }
            }
            visit(root, 0)
            return result
        }
        private fun matches(node: AccessibilityNodeInfo, text: String): Boolean =
            !node.isPassword && !node.isEditable && node.text?.toString() == text
        private fun await(packageName: String, scroll: Boolean = false,
                          diagnostic: (() -> ControllerSelectorSnapshot)? = null,
                          select: (List<AccessibilityNodeInfo>) -> AccessibilityNodeInfo?): AccessibilityNodeInfo {
            val deadline = SystemClock.elapsedRealtime() + 20_000
            var scrolls = 0
            var backwards = true
            var nextScroll = SystemClock.elapsedRealtime() + 500
            while (SystemClock.elapsedRealtime() < deadline) {
                val root = root(packageName)
                if (root != null) {
                    val all = nodes(root)
                    select(all)?.let { check(it.windowId == root.windowId); return it }
                    if (scroll && SystemClock.elapsedRealtime() >= nextScroll && scrolls < 60) {
                        val containers = all.filter { it.isScrollable && it.isEnabled }
                        check(containers.size <= 1)
                        val moved = containers.singleOrNull()?.performAction(if (backwards)
                            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        if (backwards && (moved == false || scrolls >= 23)) backwards = false
                        scrolls++; nextScroll = SystemClock.elapsedRealtime() + 250
                    }
                }
                SystemClock.sleep(100)
            }
            // Sampling is passive and cannot replace the primary timeout if accessibility fails.
            val snapshot = try { diagnostic?.invoke() } catch (_: Throwable) {
                ControllerSelectorSnapshot.unavailable()
            }
            throw ControllerSelectorTimeout(snapshot)
        }
        private fun unique(all: List<AccessibilityNodeInfo>, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            val matches = all.filter(predicate); check(matches.size <= 1)
            return matches.singleOrNull()
        }
        fun requireText(packageName: String, text: String, scroll: Boolean = false) {
            await(packageName, scroll) { unique(it) { node -> matches(node, text) } }
        }
        /** Selected labels also remain in discovery/profile rows. Scope to the
         * nearest semantic card containing the unique heading, never the whole list. */
        fun requireSectionText(packageName: String, heading: String, text: String, scroll: Boolean = false) {
            await(packageName, scroll, diagnostic = { sectionSnapshot(packageName, heading, text) }) { all ->
                val anchor = unique(all) { matches(it, heading) && it.isHeading } ?: return@await null
                var section = anchor.parent
                repeat(8) {
                    val current = section ?: return@await null
                    if (current.isScrollable || current.parent == null) return@await null
                    val found = nodes(current).filter { matches(it, text) }
                    check(found.size <= 1)
                    if (found.size == 1) return@await found.single()
                    section = current.parent
                }
                null
            }
        }
        /** One passive timeout sample. Selector arguments remain in memory only. */
        private fun sectionSnapshot(packageName: String, heading: String, text: String): ControllerSelectorSnapshot {
            val windows = ui.windows
            if (windows.size > 64) return ControllerSelectorSnapshot.unavailable()
            val application = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val expected = application.mapNotNull { window -> window.root?.let { window to it } }
                .filter { (_, node) -> node.packageName?.toString() == packageName && node.isVisibleToUser }
            val counts = listOf(application.size, expected.size, expected.count { it.first.isActive },
                expected.count { it.first.isFocused }, expected.count { it.first.isActive && it.first.isFocused })
            val eligible = expected.filter { it.first.isActive && it.first.isFocused }
            if (eligible.size != 1) return ControllerSelectorSnapshot.create(counts, 0, 0, 0,
                emptyList(), ControllerSelectorStop.ROOT_INELIGIBLE)
            val all = nodes(eligible.single().second)
            val titles = all.filter { matches(it, heading) }
            val headingCount = titles.count { it.isHeading }
            val targets = all.count { matches(it, text) }
            if (titles.size != 1) return ControllerSelectorSnapshot.create(counts, titles.size, headingCount,
                targets, emptyList(), if (titles.isEmpty()) ControllerSelectorStop.NO_TITLE else ControllerSelectorStop.AMBIGUOUS)
            var section = titles.single().parent
            val ancestors = mutableListOf<ControllerSelectorAncestor>()
            repeat(8) {
                val current = section ?: return ControllerSelectorSnapshot.create(counts, titles.size, headingCount,
                    targets, ancestors, ControllerSelectorStop.NO_PARENT)
                val parent = current.parent
                val found = nodes(current).count { matches(it, text) }
                ancestors.add(ControllerSelectorAncestor(current.isScrollable, parent != null, found))
                val stop = when {
                    current.isScrollable -> ControllerSelectorStop.SCROLL_BOUNDARY
                    parent == null -> ControllerSelectorStop.ROOT_BOUNDARY
                    found > 1 -> ControllerSelectorStop.AMBIGUOUS
                    found == 1 -> ControllerSelectorStop.TARGET_FOUND
                    else -> null
                }
                if (stop != null) return ControllerSelectorSnapshot.create(counts, titles.size, headingCount,
                    targets, ancestors, stop)
                section = parent
            }
            return ControllerSelectorSnapshot.create(counts, titles.size, headingCount, targets,
                ancestors, ControllerSelectorStop.DEPTH_LIMIT)
        }
        private fun actionable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            var candidate: AccessibilityNodeInfo? = node
            repeat(8) {
                val current = candidate ?: return null
                if (current.isClickable) return current.takeIf { it.isEnabled }
                candidate = current.parent
            }
            return null
        }
        fun requireActionable(packageName: String, text: String, scroll: Boolean = false) {
            await(packageName, scroll) { all -> unique(all) { matches(it, text) }?.let(::actionable) }
        }
        fun click(packageName: String, text: String, scroll: Boolean = false) {
            val action = await(packageName, scroll) { all ->
                unique(all) { item -> matches(item, text) }?.let(::actionable)
            }
            check(action.packageName?.toString() == packageName && action.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        fun rowAction(packageName: String, label: String, action: String) {
            val button = await(packageName, true) { all ->
                val labelNode = unique(all) { matches(it, label) } ?: return@await null
                var row: AccessibilityNodeInfo? = labelNode.parent
                repeat(6) {
                    val current = row ?: return@await null
                    val choices = nodes(current).filter { matches(it, action) }
                    if (choices.size == 1) return@await actionable(choices.single())
                    if (choices.size > 1) error("CONTROLLER_AMBIGUOUS_ROW")
                    row = current.parent
                }
                null
            }
            check(button.packageName?.toString() == packageName && button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        fun requireFixedAccount(packageName: String, username: String) {
            await(packageName, true) { all -> unique(all) { node ->
                !node.isPassword && node.text?.toString() == username &&
                    node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
            } }
        }
        fun password(packageName: String, secret: String) {
            val node = await(packageName, true) { unique(it) { item -> item.isPassword && item.isEnabled &&
                item.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SET_TEXT } } }
            check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, secret)
            }))
        }
    }
    companion object {
        private val PRODUCTS = listOf("io.github.baitian6641.cloudy.console", "io.github.baitian6641.cloudy.desktop")
        private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }
}

/** Fixed diagnostic vocabulary; never serialize selector arguments or original causes. */
internal enum class ControllerStage {
    PREFLIGHT, PRIVATE_CONFIG, UI_AUTOMATION, SENDER_LAUNCH, SENDER_LOCATIONS, SENDER_DISCOVERY, REFRESH_PAGE,
    SELECT_ROW, ASSERT_SELECTED_CARD, BEFORE_OPEN_STATUS, SINGLE_OPEN, RECEIVER_TITLE, RECEIVER_PROFILE,
    RECEIVER_RESOURCE, RECEIVER_ROOT, RECEIVER_ACCOUNT, RECEIVER_PASSWORD, RECEIVER_CONNECT, RECEIVER_CONNECTED,
    NESTED_READ, ASSERT_PREVIEW, DISCONNECT, RECEIVER_IDLE, RECEIVER_CLOSE, SENDER_RETURN,
    SENDER_REFRESH_ENABLED, SENDER_LOCATIONS_RETURN, WORKSPACE_CLOSE, FINAL_STATUS, DISCOVERY_CLOSE,
    LOCATIONS_CLOSE
}
internal class ControllerFailure private constructor(message: String) : AssertionError(message) {
    companion object {
        fun capture(stage: ControllerStage, error: Throwable): ControllerFailure {
            if (error is ControllerFailure) return error
            val category = when (error) {
                is SecurityException -> "SECURITY"
                is java.io.IOException -> "IO"
                is IllegalArgumentException -> "ARGUMENT"
                is IllegalStateException -> "STATE"
                is AssertionError -> "ASSERTION"
                else -> "OTHER"
            }
            val frame = error.stackTrace.firstOrNull {
                (it.className == "io.github.baitian6641.cloudy.platform.FilesHandoffPositiveControllerTest" ||
                    it.className.startsWith("io.github.baitian6641.cloudy.platform.FilesHandoffPositiveControllerTest\$")) &&
                    it.fileName == "FilesHandoffPositiveControllerTest.kt" && it.lineNumber in 1..100_000
            }
            val source = if (frame == null) "UNKNOWN" else "CONTROLLER"
            val diagnostic = (error as? ControllerSelectorTimeout)?.snapshot?.encode()?.let { ":$it" } ?: ""
            return ControllerFailure("FILES_POSITIVE_CONTROLLER_FAILED_PRESERVE_STATE:CONTROLLER:" +
                "${stage.name}:$category:$source:${frame?.lineNumber ?: 0}$diagnostic")
        }
    }
}

/** Fixed vocabulary and clamped counters are the entire serialized observation surface. */
internal enum class ControllerSelectorStop {
    ROOT_INELIGIBLE, NO_TITLE, NO_PARENT, SCROLL_BOUNDARY, ROOT_BOUNDARY,
    DEPTH_LIMIT, TARGET_FOUND, AMBIGUOUS, UNAVAILABLE
}
internal data class ControllerSelectorAncestor(val scrollable: Boolean, val hasParent: Boolean, val matches: Int)
internal class ControllerSelectorSnapshot private constructor(private val encoded: String) {
    fun encode(): String = encoded
    companion object {
        fun unavailable() = create(emptyList(), 0, 0, 0, emptyList(), ControllerSelectorStop.UNAVAILABLE)
        fun create(windows: List<Int>, titles: Int, headings: Int, targets: Int,
                   ancestors: List<ControllerSelectorAncestor>, stop: ControllerSelectorStop): ControllerSelectorSnapshot {
            fun count(value: Int) = value.coerceIn(0, 2048)
            val roots = (0 until 5).joinToString(",") { count(windows.getOrElse(it) { 0 }).toString() }
            val chain = ancestors.take(8).mapIndexed { depth, value ->
                "$depth,${if (value.scrollable) 1 else 0},${if (value.hasParent) 1 else 0},${count(value.matches)}"
            }.joinToString(";").ifEmpty { "NONE" }
            return ControllerSelectorSnapshot("SELECTOR_V1:W=$roots:T=${count(titles)},${count(headings)},${count(targets)}:" +
                "A=$chain:S=${stop.name}")
        }
    }
}
internal class ControllerSelectorTimeout(val snapshot: ControllerSelectorSnapshot?) :
    IllegalStateException("CONTROLLER_SELECTOR_TIMEOUT")
