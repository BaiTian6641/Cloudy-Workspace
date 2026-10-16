package io.github.baitian6641.cloudy.testing

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.CloudyProfilesActivity
import io.github.baitian6641.cloudy.platform.ConnectionProfileCatalog
import io.github.baitian6641.cloudy.platform.ConnectionProfileEntry
import io.github.baitian6641.cloudy.platform.ConnectionProfileVersion
import io.github.baitian6641.cloudy.platform.ConnectionProfiles
import io.github.baitian6641.cloudy.platform.ConnectionProfilesCode
import io.github.baitian6641.cloudy.platform.ConnectionProfilesResult
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real per-app production storage and Activity; no transport, process-loss or physical-input claim. */
abstract class ProfileActivityContract(private val expectedOwner: WindowOwner) {
    @get:Rule val compose = createAndroidComposeRule(CloudyProfilesActivity::class.java)

    private val prefix = "profile-ui-${UUID.randomUUID()}"
    private var preexistingIds = emptySet<String>()
    private val ownedIds = mutableSetOf<String>()
    private lateinit var storeContext: Context
    private val store get() = ConnectionProfiles(storeContext)

    @Before fun recordPreexistingProfiles() {
        compose.activityRule.scenario.onActivity { storeContext = it.applicationContext }
        awaitIdle()
        preexistingIds = catalog().profiles.map { it.id }.toSet()
    }

    @After fun removeOnlyThisTestsCreatedProfiles() {
        val queue = ProfileTestQueue.actual()
        val closeFailure = try {
            runCatching { compose.activityRule.scenario.close() }.exceptionOrNull()
        } finally { queue.releaseGates() }
        check(closeFailure == null) { "Profile Activity close unproved; preserve fixture" }
        // Cleanup cannot depend on the same scrolling/semantics that a failed UI case may break.
        queue.drain()
        // Discover an interrupted UI create by its unique label, while excluding every initial ID.
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

    @Test fun createUpdateAndConfirmedDeleteUseTheRealOwningAppStore() {
        val first = createThroughUi("create", if (expectedOwner == WindowOwner.DESKTOP) ConnectionProtocol.RDP else ConnectionProtocol.SSH)
        assertEquals(expectedOwner, first.owner)
        replace("profile_label", "$prefix-updated")
        replace("profile_host", "updated.invalid")
        replace("profile_port", "2222")
        click("profile_save")
        awaitMessage(io.github.baitian6641.cloudy.platform.R.string.profile_updated)
        val updated = awaitProfile(first.id) { it.revision == 2L && it.label == "$prefix-updated" }
        assertEquals("updated.invalid", updated.host)
        assertEquals(2222, updated.port)
        assertEquals(first.protocol, updated.protocol)
        click("profile_delete")
        dialogShown()
        assertEquals(updated.revision, profile(first.id).revision)
        confirm()
        awaitMessage(io.github.baitian6641.cloudy.platform.R.string.profile_deleted)
        awaitAbsent(first.id)
    }

    @Test fun unsavedDraftAndNativeSelectionSurviveRecreationWithoutSaving() {
        click("profile_new")
        val label = "$prefix-unsaved"
        replace("profile_label", label)
        replace("profile_host", "unfinished.invalid")
        replace("profile_port", "2222")
        val selection = TextRange(2, 9)
        scrollTo("profile_label")
        compose.onNodeWithTag("profile_label").performTextInputSelection(selection)
        compose.activityRule.scenario.recreate()
        awaitIdle()
        assertField("profile_label", label)
        compose.onNodeWithTag("profile_label").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection),
        )
        assertField("profile_host", "unfinished.invalid")
        assertField("profile_port", "2222")
        assertTrue(catalog().profiles.none { it.label.startsWith(prefix) })
    }

    @Test fun conflictRetainsDraftThenExplicitReviewAndConfirmationAdvanceTheReviewedRevision() {
        val first = createThroughUi("conflict")
        val desired = "$prefix-retained"
        replace("profile_label", desired)
        replace("profile_host", "retained.invalid")
        val external = externalUpdate(first, "$prefix-external", "external.invalid")
        click("profile_save")
        awaitMessage(io.github.baitian6641.cloudy.platform.R.string.profile_conflict)
        assertField("profile_label", desired)
        assertEquals(external.profile.revision, profile(first.id).revision)
        click("profile_review")
        awaitIdle()
        assertReviewed(external.profile)
        assertField("profile_host", "retained.invalid")
        click("profile_reapply")
        dialogShown()
        assertEquals(external.profile.label, profile(first.id).label)
        confirm()
        awaitMessage(io.github.baitian6641.cloudy.platform.R.string.profile_updated)
        val result = awaitProfile(first.id) { it.revision == external.profile.revision + 1 && it.label == desired }
        assertEquals("retained.invalid", result.host)
    }

    @Test fun anotherEditAfterReviewMakesConfirmedReapplyFailWithoutOverwriting() {
        val first = createThroughUi("second-conflict")
        val retained = "$prefix-retained"
        replace("profile_label", retained)
        val second = externalUpdate(first, "$prefix-external", "external.invalid")
        click("profile_save")
        awaitMessage(io.github.baitian6641.cloudy.platform.R.string.profile_conflict)
        click("profile_review")
        awaitIdle()
        assertReviewed(second.profile)
        val third = externalUpdate(second.profile, "$prefix-newer", "newer.invalid")
        click("profile_reapply")
        dialogShown()
        confirm()
        awaitMessage(io.github.baitian6641.cloudy.platform.R.string.profile_conflict)
        assertField("profile_label", retained)
        val saved = profile(first.id)
        assertEquals(third.profile.revision, saved.revision)
        assertEquals(third.profile.label, saved.label)
        assertEquals(third.profile.host, saved.host)
    }

    @Test fun savingDraftForExternallyDeletedRowDoesNotRecreateIt() {
        val first = createThroughUi("deleted")
        replace("profile_label", "$prefix-retained")
        val current = catalog()
        successful { store.delete(ConnectionProfileVersion.create(current.generation, first.id, first.revision)) }
        click("profile_save")
        awaitMessage(io.github.baitian6641.cloudy.platform.R.string.profile_not_found)
        assertField("profile_label", "$prefix-retained")
        assertTrue(catalog().profiles.none { it.id == first.id || it.label.startsWith(prefix) })
    }

    @Test fun cancellingDeleteConfirmationPreservesSavedRowAndUnsavedDraft() {
        val first = createThroughUi("cancel-delete")
        val retained = "$prefix-unsaved"
        replace("profile_label", retained)
        click("profile_delete")
        dialogShown()
        compose.onNodeWithTag("profile_cancel").performClick()
        compose.waitForIdle()
        assertField("profile_label", retained)
        val saved = profile(first.id)
        assertEquals(first.label, saved.label)
        assertEquals(first.revision, saved.revision)
        assertEquals(first.host, saved.host)
    }

    private fun createThroughUi(suffix: String, protocol: ConnectionProtocol = ConnectionProtocol.SSH): ConnectionProfile {
        val label = "$prefix-$suffix"
        click("profile_new")
        replace("profile_label", label)
        replace("profile_host", "fixture.invalid")
        replace("profile_port", if (protocol == ConnectionProtocol.RDP) "3389" else "22")
        if (protocol != ConnectionProtocol.SSH) click("profile_protocol_${protocol.wireId}")
        click("profile_save")
        // Let the app's nonblocking writer and its follow-up read finish before test store I/O.
        awaitMessage(io.github.baitian6641.cloudy.platform.R.string.profile_saved)
        var created: ConnectionProfile? = null
        compose.waitUntil(TIMEOUT_MILLIS) {
            created = catalog().profiles.singleOrNull { it.label == label }
            created != null
        }
        val result = checkNotNull(created)
        check(result.id !in preexistingIds)
        ownedIds += result.id
        assertField("profile_label", label)
        assertEquals(1L, result.revision)
        return result
    }

    private fun externalUpdate(profile: ConnectionProfile, label: String, host: String): ConnectionProfileEntry {
        val generation = catalog().generation
        val draft = ConnectionProfileDraft.create(expectedOwner, label, profile.protocol, host, profile.port)
        return successful { store.update(ConnectionProfileVersion.create(generation, profile.id, profile.revision), draft) }
    }

    private fun assertReviewed(profile: ConnectionProfile) {
        val editor = hasAnyAncestor(hasTestTag("profile_editor"))
        val endpoint = compose.activity.getString(io.github.baitian6641.cloudy.platform.R.string.profile_endpoint,
            compose.activity.getString(io.github.baitian6641.cloudy.platform.R.string.profile_protocol_ssh), profile.host, profile.port)
        val revision = compose.activity.getString(io.github.baitian6641.cloudy.platform.R.string.profile_revision, profile.revision)
        for (text in listOf(profile.label, endpoint, revision)) {
            compose.onNode(hasText(text) and editor).performScrollTo().assertIsDisplayed()
        }
        scrollTo("profile_reapply")
        compose.onNodeWithTag("profile_reapply").assertIsDisplayed()
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

    private fun assertField(tag: String, value: String) {
        scrollTo(tag)
        compose.onNodeWithTag(tag).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)),
        )
    }

    private fun awaitIdle() {
        scrollTo("profile_refresh")
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("profile_refresh").fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
    }

    private fun awaitMessage(resource: Int) {
        awaitIdle()
        scrollTo("profile_message")
        compose.onNodeWithTag("profile_message").assertTextEquals(compose.activity.getString(resource))
    }

    private fun dialogShown() { compose.onNodeWithTag("profile_dialog").assertIsDisplayed() }
    private fun confirm() { compose.onNodeWithTag("profile_confirm").performClick() }
    private fun catalog(): ConnectionProfileCatalog = successful { store.load() }
    private fun profile(id: String): ConnectionProfile = catalog().profiles.single { it.id == id }

    private fun awaitProfile(id: String, predicate: (ConnectionProfile) -> Boolean): ConnectionProfile {
        var result: ConnectionProfile? = null
        compose.waitUntil(TIMEOUT_MILLIS) {
            result = catalog().profiles.singleOrNull { it.id == id && predicate(it) }
            result != null
        }
        return checkNotNull(result)
    }

    private fun awaitAbsent(id: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { catalog().profiles.none { it.id == id } }
    }

    private fun <T> successful(operation: () -> ConnectionProfilesResult<T>): T {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS)
        do {
            when (val result = operation()) {
                is ConnectionProfilesResult.Success -> return result.value
                is ConnectionProfilesResult.Failure -> {
                    check(result.code == ConnectionProfilesCode.BUSY) { "Production profile operation failed: ${result.code}" }
                    Thread.yield()
                }
            }
        } while (System.nanoTime() < deadline)
        error("Production profile store remained busy")
    }

    companion object { private const val TIMEOUT_MILLIS = 15_000L }
}
