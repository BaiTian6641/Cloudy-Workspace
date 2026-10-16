package io.github.baitian6641.cloudy.testing

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.design.CloudyDensity
import io.github.baitian6641.cloudy.design.CloudyDisplayPreferences
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.*
import io.github.baitian6641.cloudy.platform.R
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in actual manager geometry, real private store fixtures; no authentication or network. */
abstract class ProfileGeometryContract(private val expectedOwner: WindowOwner) {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun bottomSavedRowEditAutomaticallyRevealsEditor() = exercise("bottom-edit") { session ->
        val target = session.bottomTarget()
        session.edit(target)
        session.report.put("editorNodeCountAfterEdit", compose.onAllNodesWithTag("profile_label").fetchSemanticsNodes().size)
        session.persist()
        session.capture("after-edit-before-check")
        // Do not scroll after Edit: observing an uncomposed/offscreen editor must fail this case.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("profile_label").fetchSemanticsNodes().isNotEmpty()
        }
        session.capture("after-edit")
        session.visible(compose.onNodeWithTag("profile_label"), compose.onNodeWithTag("profile_screen"), "label-field")
        session.visible(compose.onNode(hasText(context.getString(R.string.profile_editor_edit)) and
            hasAnyAncestor(hasTestTag("profile_editor"))), compose.onNodeWithTag("profile_screen"), "editor-heading")
        compose.onNodeWithTag("profile_label").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, AnnotatedString(target.label)))
    }

    @Test fun longSavedIdentityDeleteCancelPreservesDraftAndStore() = exercise("delete-cancel") { session ->
        val target = session.owned.single()
        session.edit(target)
        // This case qualifies dialog geometry separately from automatic editor navigation.
        session.scroll("profile_label")
        val draft = "Uncommitted geometry draft"
        compose.onNodeWithTag("profile_label").performTextReplacement(draft)
        compose.onNodeWithTag("profile_label").performTextInputSelection(TextRange(2, 8))
        session.scroll("profile_delete")
        compose.onNodeWithTag("profile_delete").performClick()
        compose.onNodeWithTag("profile_dialog").assertExists()
        session.capture("dialog-before-check", dialog = true)
        val texts = listOf(context.getString(R.string.profile_delete_title), context.getString(R.string.profile_delete_body),
            target.label, "${target.protocol.wireId.uppercase()} · ${target.host}:${target.port}",
            context.getString(R.string.profile_revision, target.revision))
        for ((index, text) in texts.withIndex()) {
            val node = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()), useUnmergedTree = true)
            // Collect complete line geometry before attempting ordinary ancestor scrolling.
            session.readableIdentity(node, text, "dialog-identity-$index")
            session.capture("dialog-identity-$index", dialog = true)
        }
        val confirm = compose.onNodeWithTag("profile_confirm")
        session.visible(confirm, compose.onNode(isDialog()), "confirm-action")
        confirm.assertIsEnabled()
        val cancel = compose.onNodeWithTag("profile_cancel")
        session.visible(cancel, compose.onNode(isDialog()), "cancel-action")
        cancel.assertIsEnabled().performClick()
        compose.onNodeWithTag("profile_dialog").assertDoesNotExist()
        session.scroll("profile_label")
        compose.onNodeWithTag("profile_label").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, AnnotatedString(draft))).assert(SemanticsMatcher.expectValue(
            SemanticsProperties.TextSelectionRange, TextRange(2, 8)))
        session.requireExactCatalog()
        session.capture("after-cancel")
    }

    @Test fun editorTypingAndRecreationPreserveViewportAndSelection() = exercise("typing-recreation") { session ->
        val target = session.bottomTarget()
        session.edit(target)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("profile_label").fetchSemanticsNodes().isNotEmpty()
        }
        session.capture("automatic-editor")
        session.visible(compose.onNodeWithTag("profile_label"), compose.onNodeWithTag("profile_screen"), "automatic-label")
        val heading = compose.onNode(hasText(context.getString(R.string.profile_editor_edit)) and
            hasAnyAncestor(hasTestTag("profile_editor")))
        session.visible(heading, compose.onNodeWithTag("profile_screen"), "automatic-heading")
        // This is the last test-directed scroll. All typing/recreation observations are passive.
        session.scroll("profile_port")
        val root = compose.onNodeWithTag("profile_screen")
        val headingBottom = heading.getUnclippedBoundsInRoot().bottom.value * session.density
        assertTrue("Use a short actual window so editor heading is above the port viewport",
            headingBottom <= root.fetchSemanticsNode().boundsInRoot.top + 1f)
        val originalPosition = session.portPosition("before-typing")
        val originalFramework = session.frameworkObservation()
        session.report.put("beforeTypingFramework", JSONArray(originalFramework))
        session.persist()
        session.capture("before-typing")
        val text = "2222"
        val selection = TextRange(1, 3)
        compose.onNodeWithTag("profile_port").performTextReplacement(text)
        compose.onNodeWithTag("profile_port").performTextInputSelection(selection)
        fun assertRetained(stage: String) {
            compose.waitForIdle()
            val actual = session.portPosition(stage)
            assertEquals(originalPosition.size, actual.size)
            originalPosition.zip(actual).forEach { (before, after) ->
                assertTrue("Port viewport jumped at $stage", kotlin.math.abs(before-after) <= 1f)
            }
            val actualFramework = session.frameworkObservation()
            session.report.put("${stage}Framework", JSONArray(actualFramework)); session.persist()
            assertEquals(originalFramework, actualFramework)
            compose.onNodeWithTag("profile_port").assert(SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText, AnnotatedString(text))).assert(SemanticsMatcher.expectValue(
                SemanticsProperties.TextSelectionRange, selection))
            compose.onNodeWithTag("profile_label").assert(SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText, AnnotatedString(target.label)))
            session.requireExactCatalog()
        }
        session.capture("after-typing")
        assertRetained("after-typing")
        checkNotNull(session.scenario).recreate()
        session.queue.drain()
        compose.waitForIdle()
        session.capture("after-recreation")
        assertRetained("after-recreation")
        session.report.put("selectionScope", "Text and selection only; IME composition unqualified")
    }

    private fun exercise(lane: String, body: (Session) -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit profile geometry qualification only", args.getString("cloudyProfileGeometry") == "enabled")
        val scale = checkNotNull(args.getString("cloudyGeometryFontScale"))
        require(scale in setOf("1.3", "2.0"))
        fun dimension(key: String): Int {
            val raw = checkNotNull(args.getString(key)); require(raw.length <= 4 && raw.all { it in '0'..'9' })
            return raw.toInt().also { require(it in 320..2400) }
        }
        val width = dimension("cloudyGeometryWidthPx"); val height = dimension("cloudyGeometryHeightPx")
        val directory = File(context.filesDir, "profile-geometry-review/${UUID.randomUUID()}")
        check(directory.mkdirs())
        val session = Session(directory, lane)
        var failure: Throwable? = null
        try {
            session.prepare(lane == "delete-cancel")
            session.scenario = ActivityScenario.launch(Intent(context, CloudyProfilesActivity::class.java),
                ActivityOptions.makeBasic().setLaunchBounds(Rect(48, 48, 48 + width, 48 + height)).toBundle())
            session.queue.drain(); compose.waitForIdle()
            checkNotNull(session.scenario).onActivity { activity ->
                val bounds = activity.windowManager.currentWindowMetrics.bounds
                val config = activity.resources.configuration
                session.density = activity.resources.displayMetrics.density
                session.report.put("requestedWidthPx", width).put("requestedHeightPx", height)
                    .put("actualBoundsPx", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                    .put("fontScale", config.fontScale.toDouble()).put("density", session.density.toDouble())
                    .put("decorWidthPx", activity.window.decorView.width).put("decorHeightPx", activity.window.decorView.height)
                    .put("activityWidthDp", config.screenWidthDp).put("activityHeightDp", config.screenHeightDp)
                    .put("multiWindow", activity.isInMultiWindowMode)
                session.persist()
                assertEquals(scale.toFloat(), config.fontScale, 0.01f)
                assertTrue(bounds.width() in 1..width + 96 && bounds.height() in 1..height + 96)
            }
            body(session)
            session.requireExactCatalog()
            session.report.put("passed", true)
        } catch (error: Throwable) {
            failure = error
            session.report.put("failureType", error.javaClass.simpleName).put("failure", error.message)
            // Capture current viewport before cleanup even when the editor never composed.
            try { session.capture("failure", compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) }
            catch (_: Throwable) { session.report.put("failureCaptureUnavailable", true) }
        } finally {
            try { session.closeAndRestore() } catch (error: Throwable) {
                session.report.put("cleanupFailure", error.message)
                if (failure == null) failure = error else failure.addSuppressed(error)
            } finally { session.persist() }
        }
        failure?.let { throw it }
    }

    private inner class Session(val directory: File, lane: String) {
        val report = JSONObject().put("lane", lane)
        val owned = mutableListOf<ConnectionProfile>()
        val store = ConnectionProfiles(context)
        val queue = ProfileTestQueue.actual()
        var scenario: ActivityScenario<CloudyProfilesActivity>? = null
        var density = 1f
        private val display = DisplayPreferenceTestOwner(context, expectedOwner)
        private var displayBaseline: CloudyDisplayPreferences? = null
        private lateinit var baseline: ConnectionProfileCatalog
        private val measurements = JSONArray()

        init {
            instrumentation.sendStatus(0, Bundle().apply {
                putString("cloudyProfileGeometryEvidence", directory.absolutePath)
                putString("cloudyProfileGeometryPhase", "BEFORE_MUTATION")
            })
        }
        fun persist() { File(directory, "report.json").writeText(report.toString(2)) }
        fun prepare(longIdentity: Boolean) {
            baseline = success { store.load() }
            report.put("baselineGeneration", baseline.generation).put("baselineRows", rows(baseline.profiles))
            displayBaseline = display.confirmed()
            display.recordBaseline(checkNotNull(displayBaseline), "profile-geometry")
            persist()
            display.density(CloudyDensity.COMFORTABLE)
            val id = UUID.randomUUID().toString().replace("-", "")
            val longLabel = ("Profile $id " + "long readable endpoint identity ".repeat(4)).take(80)
            val longHost = (id + "a".repeat(31)) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(53) + ".invalid"
            assertEquals(80, longLabel.length); assertEquals(253, longHost.length)
            val drafts = if (longIdentity) listOf(ConnectionProfileDraft.create(expectedOwner, longLabel,
                ConnectionProtocol.SSH, longHost, 65535)) else (0 until 16).map {
                ConnectionProfileDraft.create(expectedOwner, "Geometry $id ${it.toString().padStart(2, '0')}",
                    ConnectionProtocol.SSH, "$id-$it.invalid", 22)
            }
            // All schema validation finishes before the first profile mutation.
            for (draft in drafts) {
                requireExactCatalog()
                report.put("pendingCreate", JSONObject().put("label", draft.label).put("host", draft.host)); persist()
                val created = success { store.create(baseline.generation, draft) }.profile
                check(baseline.profiles.none { it.id == created.id } && owned.none { it.id == created.id })
                owned += created
                report.remove("pendingCreate"); report.put("ownedRows", rows(owned)); persist()
            }
        }
        fun bottomTarget(): ConnectionProfile {
            val catalog = success { store.load() }
            val ids = owned.map { it.id }.toSet()
            val index = catalog.profiles.indexOfLast { it.id in ids }
            check(index >= 0) { "Owned target unavailable; preserve fixtures" }
            val precedingOwned = catalog.profiles.take(index).count { it.id in ids }
            report.put("targetCatalogIndex", index).put("catalogTotal", catalog.profiles.size)
                .put("precedingOwnedRows", precedingOwned)
            persist()
            check(precedingOwned >= 15) { "Insufficient owned rows before bottom target" }
            return catalog.profiles[index]
        }
        fun edit(profile: ConnectionProfile) {
            scroll("profile_edit_${profile.id}")
            capture("before-edit")
            compose.onNodeWithTag("profile_edit_${profile.id}").performClick()
            compose.waitForIdle()
        }
        fun scroll(tag: String) {
            compose.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performScrollTo()
        }
        fun portPosition(stage: String): List<Float> {
            val node = compose.onNodeWithTag("profile_port")
            visible(node, compose.onNodeWithTag("profile_screen"), stage)
            val full = node.getUnclippedBoundsInRoot()
            val clipped = node.fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("profile_screen").fetchSemanticsNode().boundsInRoot
            val values = listOf(full.left.value*density, full.top.value*density, full.right.value*density,
                full.bottom.value*density, clipped.left, clipped.top, clipped.right, clipped.bottom,
                viewport.left, viewport.top, viewport.right, viewport.bottom)
            report.put("${stage}PortAndViewportPx", JSONArray(values)); persist()
            return values
        }
        fun frameworkObservation(): List<Float> {
            var value = emptyList<Float>()
            checkNotNull(scenario).onActivity { activity ->
                val bounds = activity.windowManager.currentWindowMetrics.bounds
                val config = activity.resources.configuration
                value = listOf(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
                    config.fontScale, config.densityDpi.toFloat(), config.screenWidthDp.toFloat(), config.screenHeightDp.toFloat())
            }
            return value
        }
        fun capture(name: String, dialog: Boolean = false) {
            val root = if (dialog) compose.onNode(isDialog()) else compose.onNodeWithTag("profile_screen")
            val image = root.captureToImage()
            FileOutputStream(File(directory, "$name.png")).use {
                check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync()
            }
            report.put("${name}RootPx", JSONArray(listOf(image.width, image.height))); persist()
        }
        fun visible(node: SemanticsNodeInteraction, root: SemanticsNodeInteraction, name: String) {
            node.assertIsDisplayed()
            val dp = node.getUnclippedBoundsInRoot()
            val full = androidx.compose.ui.geometry.Rect(dp.left.value * density, dp.top.value * density,
                dp.right.value * density, dp.bottom.value * density)
            val clipped = node.fetchSemanticsNode().boundsInRoot; val viewport = root.fetchSemanticsNode().boundsInRoot
            fun rect(value: androidx.compose.ui.geometry.Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
            measurements.put(JSONObject().put("name", name).put("unclippedPx", rect(full))
                .put("clippedPx", rect(clipped)).put("viewportPx", rect(viewport)))
            report.put("measurements", measurements); persist()
            for ((a,b) in listOf(full.left to clipped.left, full.top to clipped.top,
                full.right to clipped.right, full.bottom to clipped.bottom)) assertTrue(kotlin.math.abs(a-b) <= 1f)
            assertTrue(full.width > 0 && full.height > 0 && full.left >= viewport.left-1 && full.top >= viewport.top-1 &&
                full.right <= viewport.right+1 && full.bottom <= viewport.bottom+1)
        }
        fun lines(node: SemanticsNodeInteraction, text: String): List<TextLayoutResult> {
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            val metrics = JSONArray()
            for (layout in layouts) {
                val lines = JSONArray()
                for (line in 0 until layout.lineCount) lines.put(JSONObject().put("start", layout.getLineStart(line))
                    .put("end", layout.getLineEnd(line)).put("left", layout.getLineLeft(line).toDouble())
                    .put("right", layout.getLineRight(line).toDouble()).put("top", layout.getLineTop(line).toDouble())
                    .put("bottom", layout.getLineBottom(line).toDouble()).put("ellipsized", layout.isLineEllipsized(line)))
                metrics.put(JSONObject().put("text", text).put("width", layout.size.width).put("height", layout.size.height)
                    .put("hasVisualOverflow", layout.hasVisualOverflow).put("lines", lines))
                report.put("lastTextLayouts", metrics); persist()
                assertEquals(text, layout.layoutInput.text.text); assertTrue(layout.lineCount > 0)
                assertFalse(layout.multiParagraph.didExceedMaxLines)
                assertEquals(0, layout.getLineStart(0)); assertEquals(text.length, layout.getLineEnd(layout.lineCount-1))
                for (line in 0 until layout.lineCount) {
                    assertFalse(layout.isLineEllipsized(line))
                    assertTrue(layout.getLineLeft(line) >= -1 && layout.getLineRight(line) <= layout.size.width+1 &&
                        layout.getLineTop(line) >= -1 && layout.getLineBottom(line) <= layout.size.height+1)
                    if (line > 0) assertEquals(layout.getLineEnd(line-1), layout.getLineStart(line))
                }
            }
            return layouts
        }
        fun readableIdentity(node: SemanticsNodeInteraction, text: String, name: String) {
            val layouts = lines(node, text)
            assertEquals(1, layouts.size)
            val layout = layouts.single()
            var ancestor = node.fetchSemanticsNode().parent
            while (ancestor != null && !ancestor.config.contains(SemanticsActions.ScrollBy)) ancestor = ancestor.parent
            val scrollId = ancestor?.id
            val scroll = scrollId?.let { id -> compose.onNode(SemanticsMatcher("identity scroll ancestor") { it.id == id }, useUnmergedTree = true) }
            scroll?.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
            val seen = mutableSetOf<Int>()
            val samples = JSONArray()
            val deadline = System.nanoTime() + 10_000_000_000L
            repeat(64) {
                check(System.nanoTime() < deadline) { "Dialog identity coverage timed out" }
                compose.waitForIdle()
                val dp = node.getUnclippedBoundsInRoot()
                val full = androidx.compose.ui.geometry.Rect(dp.left.value*density, dp.top.value*density,
                    dp.right.value*density, dp.bottom.value*density)
                val clipped = node.fetchSemanticsNode().boundsInRoot
                val viewport = (scroll ?: compose.onNode(isDialog())).fetchSemanticsNode().boundsInRoot
                assertTrue(kotlin.math.abs(full.width-layout.size.width) <= 1f &&
                    kotlin.math.abs(full.height-layout.size.height) <= 1f)
                val visibleLines = JSONArray()
                for (line in 0 until layout.lineCount) {
                    val left = full.left+layout.getLineLeft(line); val right = full.left+layout.getLineRight(line)
                    val top = full.top+layout.getLineTop(line); val bottom = full.top+layout.getLineBottom(line)
                    if (left >= clipped.left-1 && right <= clipped.right+1 && top >= clipped.top-1 && bottom <= clipped.bottom+1 &&
                        left >= viewport.left-1 && right <= viewport.right+1 && top >= viewport.top-1 && bottom <= viewport.bottom+1) {
                        seen += line; visibleLines.put(line)
                    }
                }
                fun rect(r: androidx.compose.ui.geometry.Rect) = JSONArray(listOf(r.left,r.top,r.right,r.bottom))
                samples.put(JSONObject().put("unclippedPx",rect(full)).put("clippedPx",rect(clipped))
                    .put("scrollViewportPx",rect(viewport)).put("fullyVisibleLines",visibleLines))
                report.put(name,JSONObject().put("samples",samples).put("coveredLines",JSONArray(seen.sorted()))
                    .put("requiredLines",layout.lineCount));persist()
                if (seen.size == layout.lineCount) return
                checkNotNull(scroll) { "Incomplete identity visibility with no ordinary dialog scroll ancestor" }
                    .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, maxOf(1f,viewport.height/2)) }
            }
            error("Complete dialog identity line coverage unavailable: $name")
        }
        fun requireExactCatalog() {
            val current = success { store.load() }
            check(current.generation == baseline.generation && rows(current.profiles).toString() ==
                rows(baseline.profiles + owned).toString()) { "Unexpected catalog; preserve fixtures" }
        }
        fun closeAndRestore() {
            try {
                scenario?.close(); queue.drain()
                if (::baseline.isInitialized) {
                    requireExactCatalog()
                    for (profile in owned.toList()) {
                        requireExactCatalog()
                        report.put("pendingCleanupId", profile.id); persist()
                        success { store.delete(ConnectionProfileVersion.create(baseline.generation, profile.id, profile.revision)) }
                        owned.remove(profile); report.put("ownedRows", rows(owned)); report.remove("pendingCleanupId"); persist()
                    }
                    requireExactCatalog()
                    report.put("fixtureCleanup", "EXACT_BASELINE_RESTORED")
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("cloudyProfileGeometryFixtureCleanup", "EXACT_BASELINE_RESTORED")
                    })
                }
            } finally { displayBaseline?.let { display.restore(it, "profile-geometry") } }
        }
    }
    private fun rows(profiles: List<ConnectionProfile>) = JSONArray(profiles.sortedBy { it.id }.map {
        JSONObject().put("id", it.id).put("revision", it.revision).put("owner", it.owner.wireId)
            .put("label", it.label).put("protocol", it.protocol.wireId).put("host", it.host).put("port", it.port)
    })
    private fun <T> success(operation: () -> ConnectionProfilesResult<T>): T {
        val deadline = System.nanoTime() + 15_000_000_000L
        do {
            when (val result = operation()) {
                is ConnectionProfilesResult.Success -> return result.value
                is ConnectionProfilesResult.Failure -> check(result.code == ConnectionProfilesCode.BUSY) {
                    "Profile operation ${result.code}; preserve receipts"
                }
            }
            SystemClock.sleep(15)
        } while (System.nanoTime() < deadline)
        error("Profile store settlement timed out; preserve fixtures")
    }
}
