package io.github.baitian6641.cloudy.testing

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.CloudyProfilesActivity
import io.github.baitian6641.cloudy.platform.ConnectionProfileCatalog
import io.github.baitian6641.cloudy.platform.ConnectionProfileVersion
import io.github.baitian6641.cloudy.platform.ConnectionProfiles
import io.github.baitian6641.cloudy.platform.ConnectionProfilesCode
import io.github.baitian6641.cloudy.platform.ConnectionProfilesResult
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Controlled queue ordering around actual Activity recreation; this is not process-death evidence. */
abstract class ProfileRecreationContract(private val expectedOwner: WindowOwner) {
    @get:Rule val compose = createAndroidComposeRule(CloudyProfilesActivity::class.java)

    private val prefix = "profile-recreate-${UUID.randomUUID()}"
    private val queue by lazy { ProfileTestQueue.actual() }
    private lateinit var storeContext: Context
    private var preexistingIds = emptySet<String>()
    private val ownedIds = mutableSetOf<String>()
    private val store get() = ConnectionProfiles(storeContext)

    @Before fun captureOwningAppAndInitialCatalog() {
        compose.activityRule.scenario.onActivity { storeContext = it.applicationContext }
        queue.drain()
        preexistingIds = catalog().profiles.map { it.id }.toSet()
    }

    @After fun settleAdmittedOperationsBeforeScopedCleanup() {
        val closeFailure = try {
            runCatching { compose.activityRule.scenario.close() }.exceptionOrNull()
        } finally { queue.releaseGates() }
        check(closeFailure == null) { "Profile Activity close unproved; preserve fixture" }
        // No scrolling or UI-success prerequisite. A failed fence preserves data for reconciliation.
        queue.drain()
        val current = catalog()
        current.profiles.filter { it.id !in preexistingIds && it.label.startsWith(prefix) }
            .forEach { ownedIds += it.id }
        for (id in ownedIds) {
            val latest = catalog()
            val profile = latest.profiles.singleOrNull { it.id == id } ?: continue
            check(id !in preexistingIds)
            successful { store.delete(ConnectionProfileVersion.create(latest.generation, id, profile.revision)) }
        }
        assertTrue(catalog().profiles.none { it.id in ownedIds })
    }

    @Test fun admittedCreateCommitsOnceAcrossRecreationAndRemainsUncertainUntilExplicitAction() {
        click("profile_new")
        val label = "$prefix-create"
        replace("profile_label", label)
        replace("profile_host", "recreation.invalid")
        recreateWithSaveAdmitted()
        assertPending(label)
        val created = catalog().profiles.single { it.label == label }
        check(created.id !in preexistingIds)
        ownedIds += created.id
        assertEquals(expectedOwner, created.owner)
        assertEquals(1L, created.revision)
        assertOneOwnedProfile(created.id, 1, label)
        refreshAndRecreateAgain()
        assertPending(label)
        assertOneOwnedProfile(created.id, 1, label)
    }

    @Test fun admittedUpdateAdvancesOnlyOnceAndRestoredDraftDoesNotReapplyItself() {
        click("profile_new")
        replace("profile_label", "$prefix-original")
        replace("profile_host", "recreation.invalid")
        click("profile_save")
        queue.drain()
        val original = catalog().profiles.single { it.label == "$prefix-original" }
        check(original.id !in preexistingIds)
        ownedIds += original.id
        assertEquals(1L, original.revision)
        val label = "$prefix-updated"
        replace("profile_label", label)
        recreateWithSaveAdmitted()
        assertPending(label)
        assertOneOwnedProfile(original.id, original.revision + 1, label)
        refreshAndRecreateAgain()
        assertPending(label)
        assertOneOwnedProfile(original.id, original.revision + 1, label)
    }

    private fun recreateWithSaveAdmitted() {
        queue.drain()
        val oldActivity = WeakReference(compose.activity)
        val gate = queue.gate()
        try {
            gate.requireHeld()
            assertEquals(0, queue.queuedTasks)
            click("profile_save")
            // The real Save has entered dispatch, set pending=true and been accepted by the queue.
            compose.onNodeWithTag("profile_save").assertIsNotEnabled()
            assertEquals(1, queue.queuedTasks)
            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.onActivity { replacement ->
                assertTrue(oldActivity.get() !== replacement)
            }
            gate.requireHeld()
            // Replacement onCreate queued its real load after the still-admitted mutation.
            assertEquals(2, queue.queuedTasks)
        } finally { gate.close() }
        queue.drain()
        gate.requireNotExpired()
    }

    private fun refreshAndRecreateAgain() {
        click("profile_refresh")
        queue.drain()
        compose.activityRule.scenario.recreate()
        queue.drain()
    }

    private fun assertPending(label: String) {
        scrollTo("profile_unknown")
        compose.onNodeWithTag("profile_unknown")
            .assertTextEquals(storeContext.getString(io.github.baitian6641.cloudy.platform.R.string.profile_unknown))
            .assertIsDisplayed()
        scrollTo("profile_label")
        compose.onNodeWithTag("profile_label").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(label)),
        )
        scrollTo("profile_save")
        compose.onNodeWithTag("profile_save").assertIsNotEnabled()
    }

    private fun assertOneOwnedProfile(id: String, revision: Long, label: String) {
        val matches = catalog().profiles.filter { it.id == id || it.label.startsWith(prefix) }
        assertEquals(1, matches.size)
        val profile: ConnectionProfile = matches.single()
        assertEquals(id, profile.id)
        assertEquals(revision, profile.revision)
        assertEquals(label, profile.label)
        assertEquals("recreation.invalid", profile.host)
        assertEquals(expectedOwner, profile.owner)
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }

    private fun click(tag: String) { scrollTo(tag); compose.onNodeWithTag(tag).performClick() }
    private fun replace(tag: String, value: String) {
        scrollTo(tag)
        compose.onNodeWithTag(tag).performTextReplacement(value)
    }

    private fun catalog(): ConnectionProfileCatalog = successful { store.load() }

    private fun <T> successful(operation: () -> ConnectionProfilesResult<T>): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        do {
            when (val result = operation()) {
                is ConnectionProfilesResult.Success -> return result.value
                is ConnectionProfilesResult.Failure -> {
                    check(result.code == ConnectionProfilesCode.BUSY) { "Production profile operation failed: ${result.code}" }
                    Thread.yield()
                }
            }
        } while (System.nanoTime() < deadline)
        error("Production profile store remained busy; preserve fixture")
    }
}
