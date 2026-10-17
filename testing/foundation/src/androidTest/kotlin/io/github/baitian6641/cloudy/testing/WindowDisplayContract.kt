package io.github.baitian6641.cloudy.testing

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.design.CloudyAppearance
import io.github.baitian6641.cloudy.design.CloudyDensity
import io.github.baitian6641.cloudy.design.CloudyDisplayPreferences
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.domain.WindowSnapshot
import io.github.baitian6641.cloudy.platform.CloudyWindowActivity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real preference recomposition, not Activity recreation, physical keyboard or touch qualification. */
abstract class WindowDisplayContract<A : CloudyWindowActivity>(
    activityClass: Class<A>, private val expectedOwner: WindowOwner, private val captureEvidence: Boolean,
) {
    @get:Rule val compose = createAndroidComposeRule(activityClass)

    private data class WindowObservation(
        val snapshot: WindowSnapshot, val generation: String, val taskId: Int,
        val title: String, val taskLabel: String?, val taskRoot: Boolean,
    )

    @Test fun liveAppearanceAndDensityPreserveTheWindowAndNativeDraft() {
        lateinit var context: Context
        compose.activityRule.scenario.onActivity { context = it.applicationContext }
        val owner = DisplayPreferenceTestOwner(context, expectedOwner)
        var baseline: CloudyDisplayPreferences? = null
        val observations = JSONArray()
        val evidence = if (captureEvidence) File(context.filesDir, "display-review/${UUID.randomUUID()}") else null
        try {
            baseline = owner.confirmed()
            owner.recordBaseline(checkNotNull(baseline), "workspace")
            val initial = observe()
            assertEquals(expectedOwner.wireId, initial.snapshot.owner)
            assertTrue(initial.taskRoot)
            expandAdvancedToNameEditor()
            val text = "Unapplied display draft Ω"
            val selection = TextRange(2, 9)
            compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement(text)
            compose.onNode(hasSetTextAction()).performTextInputSelection(selection)
            for (appearance in listOf(CloudyAppearance.LIGHT, CloudyAppearance.DARK)) {
                owner.appearance(appearance)
                for (density in CloudyDensity.entries) {
                    owner.density(density)
                    compose.waitForIdle()
                    assertEquals(initial, observe())
                    assertDraft(text, selection)
                    val preferences = owner.confirmed()
                    assertEquals(appearance, preferences.appearance)
                    assertEquals(density, preferences.density)
                    if (evidence != null) {
                        check(evidence.isDirectory || evidence.mkdirs())
                        // Capture the actual viewport at the top of the workspace. Offscreen controls
                        // are not claimed visible; metrics below report their clipped root bounds.
                        compose.onNodeWithText(initial.snapshot.name).performScrollTo()
                        compose.waitForIdle()
                        val frame = capture(context, evidence, preferences)
                        observations.put(frame)
                        File(evidence, "report.json").writeText(JSONObject().apply {
                            put("owner", expectedOwner.wireId)
                            put("scope", "Actual workspace viewport; synthetic local draft only; no physical-input or full-layout acceptance")
                            put("frames", observations)
                        }.toString(2))
                        // Scrolling for capture does not itself replace the native field state.
                        assertDraft(text, selection)
                    }
                }
            }
            assertEquals(initial, observe())
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("cloudyDisplayLane", "workspace")
                putInt("cloudyDisplayConfigurations", 6)
                putInt("cloudyDisplayCaptures", observations.length())
                evidence?.let { putString("cloudyDisplayEvidence", it.absolutePath) }
            })
        } finally {
            try { baseline?.let { owner.restore(it, "workspace") } }
            finally { compose.activityRule.scenario.close() }
        }
    }


    /** The workspace home keeps the name editor under Advanced; expand it before editing. */
    private fun expandAdvancedToNameEditor() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home_advanced").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("home_advanced").performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            try {
                compose.onNode(hasSetTextAction()).fetchSemanticsNode()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun observe(): WindowObservation {
        lateinit var value: WindowObservation
        compose.activityRule.scenario.onActivity { activity ->
            val task = activity.getSystemService(ActivityManager::class.java).appTasks.mapNotNull { it.taskInfo }
                .single { it.taskId == activity.taskId }
            value = WindowObservation(activity.windowContext.snapshot(), activity.activityGeneration,
                activity.taskId, activity.title.toString(), task.taskDescription?.label, activity.isTaskRoot)
        }
        return value
    }

    private fun assertDraft(text: String, selection: TextRange) {
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, AnnotatedString(text))).assert(SemanticsMatcher.expectValue(
            SemanticsProperties.TextSelectionRange, selection))
    }

    private fun capture(context: Context, directory: File, preferences: CloudyDisplayPreferences): JSONObject {
        val name = "${preferences.appearance.name.lowercase()}-${preferences.density.name.lowercase()}"
        val image = compose.onRoot().captureToImage()
        val screenshot = File(directory, "$name.png")
        FileOutputStream(screenshot).use { output ->
            check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
            output.fd.sync()
        }
        val configuration = context.resources.configuration
        val pixels = context.resources.displayMetrics
        val controls = JSONArray()
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
            val button = compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            fun bounds(rect: androidx.compose.ui.geometry.Rect) = JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))
            controls.put(JSONObject().apply {
                put("label", label); put("buttonClippedBoundsPx", bounds(button)); put("textClippedBoundsPx", bounds(text))
            })
        }
        return JSONObject().apply {
            put("appearance", preferences.appearance.name); put("densityPreference", preferences.density.name)
            put("screenshot", screenshot.name); put("widthPx", image.width); put("heightPx", image.height)
            put("scrollPosition", "Committed window heading scrolled into view; viewport only")
            put("fontScale", configuration.fontScale.toDouble()); put("densityDpi", configuration.densityDpi)
            put("displayDensity", pixels.density.toDouble()); put("screenWidthDp", configuration.screenWidthDp)
            put("screenHeightDp", configuration.screenHeightDp); put("uiMode", configuration.uiMode)
            put("controls", controls)
        }
    }
}
