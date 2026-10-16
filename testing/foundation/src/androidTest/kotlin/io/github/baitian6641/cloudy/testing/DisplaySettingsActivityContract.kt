package io.github.baitian6641.cloudy.testing

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.design.CloudyAppearance
import io.github.baitian6641.cloudy.design.CloudyDensity
import io.github.baitian6641.cloudy.design.CloudyDisplayPreferences
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.CloudyDisplaySettings
import io.github.baitian6641.cloudy.platform.CloudyDisplayNotice
import io.github.baitian6641.cloudy.platform.CloudyDisplaySettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Actual owning-app preferences/UI; recreation is not process death or physical-input evidence. */
abstract class DisplaySettingsActivityContract(private val expectedOwner: WindowOwner) {
    @get:Rule val compose = createAndroidComposeRule(CloudyDisplaySettingsActivity::class.java)

    @Test fun everyNativeChoicePersistsAndSurvivesActivityRecreation() {
        lateinit var context: Context
        compose.activityRule.scenario.onActivity { context = it.applicationContext }
        val owner = DisplayPreferenceTestOwner(context, expectedOwner)
        var baseline: CloudyDisplayPreferences? = null
        try {
            baseline = owner.confirmed()
            owner.recordBaseline(checkNotNull(baseline), "settings")
            for (appearance in CloudyAppearance.entries) {
                compose.onNodeWithTag("display_appearance_${appearance.name}").performScrollTo().performClick()
                owner.awaitPreferences { it.appearance == appearance }
                compose.onNodeWithTag("display_appearance_${appearance.name}").assertIsSelected()
            }
            for (density in CloudyDensity.entries) {
                compose.onNodeWithTag("display_density_${density.name}").performScrollTo().performClick()
                owner.awaitPreferences { it.density == density }
                compose.onNodeWithTag("display_density_${density.name}").assertIsSelected()
            }
            val latest = owner.confirmed()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertEquals(latest, owner.confirmed())
            compose.onNodeWithTag("display_appearance_${latest.appearance.name}")
                .performScrollTo().assertIsSelected()
            compose.onNodeWithTag("display_density_${latest.density.name}")
                .performScrollTo().assertIsSelected()
        } finally {
            try {
                baseline?.let { owner.restore(it, "settings") }
            } finally { compose.activityRule.scenario.close() }
        }
    }
}

/** Only the real process owner is used; no replacement store, preference reset or fallback value. */
internal class DisplayPreferenceTestOwner(context: Context, expectedOwner: WindowOwner) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val packageName = context.packageName
    private val owner = CloudyDisplaySettings.get(context.applicationContext)

    init {
        assertEquals("io.github.baitian6641.cloudy.${expectedOwner.wireId}", context.packageName)
        assertEquals(Process.myUid(), context.applicationInfo.uid)
        assertFalse(context.isDeviceProtectedStorage)
        @Suppress("DEPRECATION")
        val activity = context.packageManager.getActivityInfo(ComponentName(context, CloudyDisplaySettingsActivity::class.java), 0)
        assertFalse(activity.exported)
        assertEquals(context.packageName, activity.packageName)
        assertEquals(Process.myUid(), activity.applicationInfo.uid)
    }

    fun confirmed(): CloudyDisplayPreferences = awaitPreferences { true }

    fun awaitPreferences(predicate: (CloudyDisplayPreferences) -> Boolean): CloudyDisplayPreferences {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        do {
            var preferences: CloudyDisplayPreferences? = null
            instrumentation.runOnMainSync {
                val state = owner.state.value
                // Never use a default/unknown/error snapshot as a baseline or a confirmed write.
                check(!state.persistenceUncertain) { "Display persistence uncertain; preserve state and evidence" }
                if (!state.loading && !state.saving) {
                    check(state.ready && !state.recoveredValues &&
                        state.notice in setOf(null, CloudyDisplayNotice.SAVED)) {
                        "Display preferences unavailable or recovered; preserve state and evidence"
                    }
                    if (predicate(state.preferences)) preferences = state.preferences
                }
            }
            preferences?.let { return it }
            SystemClock.sleep(15)
        } while (System.nanoTime() < deadline)
        error("Display preference confirmation timed out; preserve state and evidence")
    }

    fun appearance(value: CloudyAppearance) {
        confirmed()
        instrumentation.runOnMainSync { owner.setAppearance(value) }
        awaitPreferences { it.appearance == value }
    }

    fun density(value: CloudyDensity) {
        confirmed()
        instrumentation.runOnMainSync { owner.setDensity(value) }
        awaitPreferences { it.density == value }
    }

    fun recordBaseline(baseline: CloudyDisplayPreferences, lane: String) {
        assertEquals(baseline, confirmed())
        instrumentation.sendStatus(0, Bundle().apply {
            putString("cloudyDisplayLane", lane)
            putString("cloudyDisplayPackage", packageName)
            putString("cloudyDisplayBaselineAppearance", baseline.appearance.name)
            putString("cloudyDisplayBaselineDensity", baseline.density.name)
            putString("cloudyDisplayPhase", "BASELINE_BEFORE_MUTATION")
        })
    }

    fun restore(baseline: CloudyDisplayPreferences, lane: String) {
        // Every restoration is an explicit normal mutation, never all-preference deletion/reset.
        appearance(baseline.appearance)
        density(baseline.density)
        assertEquals(baseline, confirmed())
        instrumentation.sendStatus(0, Bundle().apply {
            putString("cloudyDisplayLane", lane)
            putString("cloudyDisplayRestoration", "EXACT_BASELINE_CONFIRMED")
            putString("cloudyDisplayPackage", packageName)
            putString("cloudyDisplayRestoredAppearance", baseline.appearance.name)
            putString("cloudyDisplayRestoredDensity", baseline.density.name)
        })
    }

    companion object { private const val TIMEOUT_NANOS = 15_000_000_000L }
}
