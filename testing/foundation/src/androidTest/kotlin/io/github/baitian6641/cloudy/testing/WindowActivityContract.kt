package io.github.baitian6641.cloudy.testing

import android.app.ActivityManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.domain.WindowSnapshot
import io.github.baitian6641.cloudy.platform.CloudyWindowActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Shared app androidTest source only: no production hooks, process-loss or physical-input claim. */
abstract class WindowActivityContract<A : CloudyWindowActivity>(
    activityClass: Class<A>,
    private val expectedOwner: WindowOwner,
    private val appNameResource: Int,
    private val defaultNameResource: Int,
) {
    @get:Rule val compose = createAndroidComposeRule(activityClass)

    private data class Observation(
        val snapshot: WindowSnapshot,
        val generation: String,
        val taskId: Int,
        val title: String,
        val taskLabel: String?,
        val taskRoot: Boolean,
    )

    private fun observe(): Observation {
        lateinit var result: Observation
        compose.activityRule.scenario.onActivity { activity ->
            val manager = activity.getSystemService(ActivityManager::class.java)
            val ownTask = manager.appTasks.mapNotNull { it.taskInfo }
                .single { it.taskId == activity.taskId }
            result = Observation(activity.windowContext.snapshot(), activity.activityGeneration,
                activity.taskId, activity.title.toString(),
                checkNotNull(ownTask.taskDescription).label, activity.isTaskRoot)
        }
        return result
    }

    private fun string(resource: Int): String = compose.activity.getString(resource)

    private fun applyName() {
        compose.onNodeWithText(string(
            io.github.baitian6641.cloudy.design.R.string.cloudy_save_name,
        )).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun assertCommittedHeading(name: String) {
        compose.onNode(
            hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit),
        ).performScrollTo().assertIsDisplayed()
    }

    /** The workspace home keeps window/name tools under Advanced; expand before editing. */
    private fun expandAdvanced() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home_advanced").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("home_advanced").performScrollTo().performClick()
        compose.waitForIdle()
        waitForNameEditor()
    }

    private fun waitForNameEditor() {
        compose.waitUntil(10_000) {
            try {
                compose.onNode(hasSetTextAction()).fetchSemanticsNode()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test fun committedContextAndUnsavedDraftSurviveActivityRecreation() {
        val initial = observe()
        expandAdvanced()
        assertEquals(expectedOwner.wireId, initial.snapshot.owner)
        assertEquals(string(defaultNameResource), initial.snapshot.name)
        assertTrue(initial.taskRoot)

        val committedName = "Committed 日本語"
        compose.onNode(hasSetTextAction()).performTextReplacement(committedName)
        applyName()
        val committed = observe()
        val expectedTitle = "$committedName · ${string(appNameResource)}"
        assertEquals(initial.snapshot.windowId, committed.snapshot.windowId)
        assertEquals(committedName, committed.snapshot.name)
        assertEquals(expectedTitle, committed.title)
        assertEquals(expectedTitle, committed.taskLabel)
        assertCommittedHeading(committedName)

        val draft = "Unapplied draft Ω"
        val selection = TextRange(2, 8)
        compose.onNode(hasSetTextAction()).performTextReplacement(draft)
        compose.onNode(hasSetTextAction()).performTextInputSelection(selection)
        assertCommittedHeading(committedName)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        // Advanced expansion is saveable; the editor returns once the home finishes reloading.
        waitForNameEditor()

        val restored = observe()
        assertEquals(committed.snapshot, restored.snapshot)
        assertEquals(committed.taskId, restored.taskId)
        assertNotEquals(committed.generation, restored.generation)
        assertTrue(restored.taskRoot)
        // Metadata assertions do not establish actual Googlebook system-caption pixels.
        assertEquals(expectedTitle, restored.title)
        assertEquals(expectedTitle, restored.taskLabel)
        assertCommittedHeading(committedName)
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)),
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
    }

    @Test fun invalidNameLeavesCommittedIdentityAndSystemLabelsUnchanged() {
        val before = observe()
        expandAdvanced()
        compose.onNode(hasSetTextAction()).performTextReplacement("   ")
        applyName()
        compose.onNodeWithText(string(
            io.github.baitian6641.cloudy.platform.R.string.cloudy_name_blank,
        )).performScrollTo().assertIsDisplayed()
        val after = observe()
        assertEquals(before, after)
    }

    @Test fun hostKeyManagerOpensFromTheWorkspaceAndReturnsToTheSameWindow() {
        val before = observe()
        expandAdvanced()
        compose.onNodeWithText(string(
            io.github.baitian6641.cloudy.design.R.string.cloudy_ssh_host_keys,
        )).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("trust_close").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("trust_close").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(before.snapshot, observe().snapshot)
        assertCommittedHeading(before.snapshot.name)
    }
}
