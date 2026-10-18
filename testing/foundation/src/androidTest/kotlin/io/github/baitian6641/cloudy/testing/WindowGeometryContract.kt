package io.github.baitian6641.cloudy.testing

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.view.WindowInsets
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.design.CloudyDensity
import io.github.baitian6641.cloudy.design.CloudyDisplayPreferences
import io.github.baitian6641.cloudy.domain.WindowSnapshot
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.CloudyWindowActivity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in actual OS geometry only. The host owns system font changes and exact restoration. */
abstract class WindowGeometryContract<A : CloudyWindowActivity>(
    private val activityClass: Class<A>, private val expectedOwner: WindowOwner,
) {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun actualWindowBoundsAndScaledDraftKeepEssentialActionsReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        // Skip before obtaining the preference owner or launching any product Activity.
        assumeTrue("Explicit geometry qualification only", arguments.getString("cloudyGeometry") == "enabled")
        val scale = checkNotNull(arguments.getString("cloudyGeometryFontScale"))
        require(scale in setOf("1.3", "2.0"))
        fun dimension(key: String): Int {
            val raw = checkNotNull(arguments.getString(key))
            require(raw.length <= 4 && raw.all { it in '0'..'9' })
            return raw.toInt().also { require(it in 320..2400) }
        }
        val width = dimension("cloudyGeometryWidthPx")
        val height = dimension("cloudyGeometryHeightPx")
        val density = CloudyDensity.valueOf(checkNotNull(arguments.getString("cloudyGeometryDensity")))
        val requested = Rect(48, 48, 48 + width, 48 + height)
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "geometry-review/${UUID.randomUUID()}")
        check(directory.mkdirs())
        // Publish the exact test-owned evidence location before launch or preference mutation.
        instrumentation.sendStatus(0, Bundle().apply {
            putString("cloudyGeometryPhase", "BEFORE_LAUNCH")
            putString("cloudyGeometryEvidence", directory.absolutePath)
            putString("cloudyGeometryPackage", context.packageName)
        })
        var scenario: ActivityScenario<A>? = null
        var owner: DisplayPreferenceTestOwner? = null
        var baseline: CloudyDisplayPreferences? = null
        val report = JSONObject().apply {
            put("scope", "Actual OS window; clipped semantics, not hit targets; no physical-input qualification")
            put("requestedBoundsPx", rect(requested))
            put("requestedFontScale", scale)
            put("requestedDensity", density.name)
        }
        fun persist() { File(directory, "report.json").writeText(report.toString(2)) }
        try {
            scenario = ActivityScenario.launch(Intent(context, activityClass), ActivityOptions.makeBasic()
                .setLaunchBounds(requested).toBundle())
            val running = checkNotNull(scenario)
            val preferenceOwner = DisplayPreferenceTestOwner(context, expectedOwner)
            owner = preferenceOwner
            val savedBaseline = preferenceOwner.confirmed()
            baseline = savedBaseline
            preferenceOwner.recordBaseline(savedBaseline, "geometry")
            report.put("baselineAppearance", savedBaseline.appearance.name)
            report.put("baselineDensity", savedBaseline.density.name)
            persist()
            compose.waitForIdle()
            var initialSnapshot: WindowSnapshot? = null
            var initialGeneration: String? = null
            var initialTask = -1
            var initialTitle: String? = null
            var committedName = ""
            var windowDensity = 0f
            running.onActivity { activity ->
                val bounds = activity.windowManager.currentWindowMetrics.bounds
                val config = activity.resources.configuration
                val decor = activity.window.decorView
                windowDensity = activity.resources.displayMetrics.density
                initialSnapshot = activity.windowContext.snapshot()
                initialGeneration = activity.activityGeneration
                initialTask = activity.taskId
                initialTitle = activity.title.toString()
                committedName = activity.windowContext.name
                val insets = activity.windowManager.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                report.put("actualBoundsPx", rect(bounds))
                report.put("decorWidthPx", decor.width).put("decorHeightPx", decor.height)
                report.put("systemInsetsPx", JSONArray(listOf(insets.left, insets.top, insets.right, insets.bottom)))
                report.put("fontScale", config.fontScale.toDouble()).put("densityDpi", config.densityDpi)
                report.put("displayDensity", windowDensity.toDouble())
                report.put("activityWidthDp", config.screenWidthDp).put("activityHeightDp", config.screenHeightDp)
                report.put("multiWindow", activity.isInMultiWindowMode).put("taskId", initialTask)
                report.put("activityGeneration", initialGeneration).put("windowId", activity.windowContext.windowId)
                persist()
                assertEquals(scale.toFloat(), config.fontScale, 0.01f)
                assertTrue("OS ignored/clamped width beyond qualification bound", bounds.width() <= width + 96)
                assertTrue("OS ignored/clamped height beyond qualification bound", bounds.height() <= height + 96)
                assertTrue(bounds.width() > 0 && bounds.height() > 0 && decor.width > 0 && decor.height > 0)
                assertEquals(expectedOwner.wireId, activity.windowContext.snapshot().owner)
            }
            preferenceOwner.density(density)
            compose.waitForIdle()
            val draft = "Geometry draft Ω unsaved"
            val selection = TextRange(2, 10)
            expandAdvancedToNameEditor()
            compose.onNodeWithTag("home_window_name").performScrollTo().performTextReplacement(draft)
            compose.onNodeWithTag("home_window_name").performTextInputSelection(selection)
            fun assertPreserved() {
                compose.onNodeWithTag("home_window_name").assert(SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText, AnnotatedString(draft))).assert(SemanticsMatcher.expectValue(
                    SemanticsProperties.TextSelectionRange, selection))
                running.onActivity {
                    assertEquals(initialSnapshot, it.windowContext.snapshot())
                    assertEquals(initialGeneration, it.activityGeneration)
                    assertEquals(initialTask, it.taskId)
                    assertEquals(initialTitle, it.title.toString())
                }
            }
            fun capture(name: String) {
                compose.waitForIdle()
                val image = compose.onRoot().captureToImage()
                FileOutputStream(File(directory, "$name.png")).use {
                    check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync()
                }
                report.put("${name}RootWidthPx", image.width).put("${name}RootHeightPx", image.height)
            }
            // The committed heading is in the fixed toolbar; it is visible without scrolling.
            val heading = compose.onNodeWithText(committedName)
            if (heading.closestScrollableAncestor() != null) heading.performScrollTo()
            heading.assertIsDisplayed()
            capture("top")
            val controls = JSONArray()
            report.put("controlsAtIndividualScrollPositions", controls)
            for (resource in listOf(
                io.github.baitian6641.cloudy.design.R.string.cloudy_saved_credentials,
                io.github.baitian6641.cloudy.design.R.string.cloudy_connection_profiles,
                io.github.baitian6641.cloudy.design.R.string.cloudy_ssh_host_keys,
                io.github.baitian6641.cloudy.design.R.string.cloudy_display_settings,
                io.github.baitian6641.cloudy.design.R.string.cloudy_save_name,
                io.github.baitian6641.cloudy.design.R.string.cloudy_new_window,
                io.github.baitian6641.cloudy.design.R.string.cloudy_close_window,
            )) {
                val label = context.getString(resource)
                val action = compose.onNode(hasText(label) and hasClickAction())
                val scrollable = action.closestScrollableAncestor() != null
                if (scrollable) action.performScrollTo()
                action.assertIsDisplayed().assertIsEnabled()
                val labelInteraction = compose.onNodeWithText(label, useUnmergedTree = true)
                val labelNode = labelInteraction.fetchSemanticsNode()
                val unclippedDp = labelInteraction.getUnclippedBoundsInRoot()
                val unclipped = androidx.compose.ui.geometry.Rect(
                    unclippedDp.left.value * windowDensity, unclippedDp.top.value * windowDensity,
                    unclippedDp.right.value * windowDensity, unclippedDp.bottom.value * windowDensity,
                )
                val clipped = labelNode.boundsInRoot
                val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
                // Text layout overflow alone misses ancestor clipping. Compare actual geometry
                // in root pixels as well; tolerance covers subpixel density-rounding only.
                val tolerance = 1f
                report.put("lastLabelMeasurement", JSONObject().apply {
                    put("label", label).put("labelClippedBoundsPx", rect(clipped))
                    put("labelUnclippedBoundsPx", rect(unclipped)).put("rootViewportBoundsPx", rect(viewport))
                    put("boundsTolerancePx", tolerance.toDouble())
                })
                persist()
                for ((actual, full) in listOf(clipped.left to unclipped.left, clipped.top to unclipped.top,
                    clipped.right to unclipped.right, clipped.bottom to unclipped.bottom)) {
                    assertTrue("Essential label clipped: $label", kotlin.math.abs(actual - full) <= tolerance)
                }
                assertTrue("Essential label outside viewport: $label",
                    unclipped.width > 0 && unclipped.height > 0 &&
                        unclipped.left >= viewport.left - tolerance && unclipped.top >= viewport.top - tolerance &&
                        unclipped.right <= viewport.right + tolerance && unclipped.bottom <= viewport.bottom + tolerance)
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                val layoutEvidence = JSONArray()
                for (layout in layouts) {
                    val input = layout.layoutInput
                    val constraints = input.constraints
                    val lines = JSONArray()
                    for (line in 0 until layout.lineCount) {
                        lines.put(JSONObject().apply {
                            put("index", line).put("topPx", layout.getLineTop(line).toDouble())
                            put("bottomPx", layout.getLineBottom(line).toDouble())
                            put("leftPx", layout.getLineLeft(line).toDouble())
                            put("rightPx", layout.getLineRight(line).toDouble())
                            put("start", layout.getLineStart(line)).put("end", layout.getLineEnd(line))
                            put("ellipsized", layout.isLineEllipsized(line))
                        })
                    }
                    layoutEvidence.put(JSONObject().apply {
                        put("sizeWidthPx", layout.size.width).put("sizeHeightPx", layout.size.height)
                        put("didOverflowWidth", layout.didOverflowWidth)
                        put("didOverflowHeight", layout.didOverflowHeight)
                        put("hasVisualOverflow", layout.hasVisualOverflow)
                        put("didExceedMaxLines", layout.multiParagraph.didExceedMaxLines)
                        put("multiParagraphWidthPx", layout.multiParagraph.width.toDouble())
                        put("multiParagraphHeightPx", layout.multiParagraph.height.toDouble())
                        put("lineCount", layout.lineCount).put("lines", lines)
                        put("maxLines", input.maxLines).put("softWrap", input.softWrap)
                        put("overflow", input.overflow.toString())
                        put("constraintsMinWidthPx", constraints.minWidth)
                        put("constraintsMaxWidthPx", constraints.maxWidth)
                        put("constraintsMinHeightPx", constraints.minHeight)
                        put("constraintsMaxHeightPx", constraints.maxHeight)
                        put("fontSize", input.style.fontSize.toString())
                        put("lineHeight", input.style.lineHeight.toString())
                        put("layoutDensity", input.density.density.toDouble())
                        put("layoutFontScale", input.density.fontScale.toDouble())
                    })
                }
                report.put("lastLabelTextLayout", JSONObject().put("label", label).put("layouts", layoutEvidence))
                persist()
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("cloudyGeometryLabelLayout", label)
                    putString("cloudyGeometryLayoutEvidence", layoutEvidence.toString())
                })
                // Pinned simple-String semantics reconstructs a MultiParagraph at max constraints,
                // paired with the cached measured size. Its raw overflow may describe that container;
                // qualify complete line geometry against the actual text node instead.
                for (layout in layouts) {
                    assertTrue("Text layout width differs from actual node: $label",
                        kotlin.math.abs(layout.size.width - unclipped.width) <= tolerance)
                    assertTrue("Text layout height differs from actual node: $label",
                        kotlin.math.abs(layout.size.height - unclipped.height) <= tolerance)
                    assertTrue("Essential label exceeded max lines: $label", !layout.multiParagraph.didExceedMaxLines)
                    assertTrue("Essential label has no lines: $label", layout.lineCount > 0)
                    assertEquals("Essential label layout text differs", label, layout.layoutInput.text.text)
                    assertEquals("Essential label start missing", 0, layout.getLineStart(0))
                    assertEquals("Essential label end missing", label.length, layout.getLineEnd(layout.lineCount - 1))
                    for (line in 0 until layout.lineCount) {
                        assertTrue("Essential label ellipsized: $label", !layout.isLineEllipsized(line))
                        assertTrue("Essential label line outside measured node: $label",
                            layout.getLineLeft(line) >= -tolerance &&
                                layout.getLineRight(line) <= layout.size.width + tolerance &&
                                layout.getLineTop(line) >= -tolerance &&
                                layout.getLineBottom(line) <= layout.size.height + tolerance)
                        if (line > 0) assertEquals("Essential label character gap", layout.getLineEnd(line - 1),
                            layout.getLineStart(line))
                    }
                }
                controls.put(JSONObject().apply {
                    put("label", label)
                    put("fixedToolbarAction", !scrollable)
                    put("buttonClippedBoundsPx", rect(action.fetchSemanticsNode().boundsInRoot))
                    put("labelClippedBoundsPx", rect(clipped))
                    put("labelUnclippedBoundsPx", rect(unclipped))
                    put("rootViewportBoundsPx", rect(viewport))
                    put("boundsTolerancePx", tolerance.toDouble())
                    put("lineBoundsFit", true)
                    put("layouts", layoutEvidence)
                })
                assertPreserved()
                persist()
            }
            capture("final-actions")
            assertPreserved()
            report.put("controlsAtIndividualScrollPositions", controls).put("passed", true)
            persist()
            instrumentation.sendStatus(0, Bundle().apply {
                putString("cloudyGeometryPhase", "MEASURED_PASS")
                putString("cloudyGeometryEvidence", directory.absolutePath)
            })
        } finally {
            try {
                baseline?.let { checkNotNull(owner).restore(it, "geometry") }
            } finally {
                try { scenario?.close() } finally { persist() }
            }
        }
    }

    /**
     * Fixed toolbar actions sit outside the workspace scroller, so `performScrollTo` would fail
     * on them even though they are already on screen. Find the real scrolling ancestor and scroll
     * only when there is one; visibility/enabled/bounds assertions stay unchanged.
     */
    private fun SemanticsNodeInteraction.closestScrollableAncestor(): SemanticsNode? {
        var ancestor = fetchSemanticsNode().parent
        while (ancestor != null) {
            if (ancestor.config.contains(SemanticsActions.ScrollBy)) return ancestor
            ancestor = ancestor.parent
        }
        return null
    }

    private fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
    private fun rect(value: androidx.compose.ui.geometry.Rect) =
        JSONArray(listOf(value.left, value.top, value.right, value.bottom))

    /** The workspace home keeps the name editor under Advanced; expand it before editing. */
    private fun expandAdvancedToNameEditor() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home_advanced").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("home_advanced").performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithTag("home_window_name").fetchSemanticsNode()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }
}
