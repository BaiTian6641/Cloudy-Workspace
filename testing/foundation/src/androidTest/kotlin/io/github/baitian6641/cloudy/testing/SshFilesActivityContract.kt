package io.github.baitian6641.cloudy.testing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Process
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.platform.CloudyFilesLocationsActivity
import io.github.baitian6641.cloudy.platform.CloudyProfilesActivity
import io.github.baitian6641.cloudy.platform.CloudySshFilesActivity
import io.github.baitian6641.cloudy.platform.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real installed-product retained SFTP. Exceptions are sanitized before any JUnit publication. */
abstract class SshFilesActivityContract(private val expectedOwner: WindowOwner) {
    @get:Rule val compose = createEmptyComposeRule()
    private var substage = "NONE"

    @Test fun realReadOnlyListReadAndExplicitDisconnect() = exercise("basic")
    @Test fun establishedRecreationRetainsSameConnectionWithoutReplay() = exercise("recreate")
    @Test fun visibleFrameworkFocusLossPreservesEstablishedConnection() = exercise("focus")
    @Test fun hiddenOwnerRetiresAndReturnDoesNotReconnect() = exercise("hide")
    @Test fun realProfileEditRetiresEstablishedConnection() = exercise("profile")
    @Test fun heldRealReadDisconnectCannotPublishLatePreview() = exercise("hold")

    @Test fun realListedDirectoryRegistersPrivateAndOrdinarySessionSurvives() = exerciseLocations("register")
    @Test fun registrationFocusLossCancelsFrozenProposalWithoutMutation() = exerciseLocations("registration-focus")
    @Test fun registeredLocationUsesFreshAccountAndInitialDirectoryThenRevocationRetires() = exerciseLocations("registered-location")

    @Test fun actualHandoffReceiverPhase() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("cloudyFilesHandoffReceiverPhase")
        assumeTrue("Explicit retained receiver phase only", phase != null)
        var stage = "PHASE_ARGUMENTS"
        try {
            val run = checkNotNull(args.getString("cloudySshFilesRun"))
            val direction = checkNotNull(args.getString("cloudyFilesHandoffDirection"))
            check(direction == if (expectedOwner == WindowOwner.CONSOLE) "desktop-to-console" else "console-to-desktop")
            when (phase) {
                "prepare" -> {
                    check(args.getString("cloudyFilesHandoffCheckpointSha256") == null)
                    stage = "INITIALIZE"
                    exerciseLocations("handoff-receiver")
                }
                "verify", "cleanup" -> {
                    // This is parent authorization to start inspection, never proof that the
                    // previous ordinary process/host actually retired. Parent records that outside.
                    check(args.getString("cloudyFilesHandoffExternalRetirement") == "parent-verified")
                    stage = "CHECKPOINT_RESUME"
                    val checkpoint = FilesHandoffReceiverCheckpoint.resume(
                        InstrumentationRegistry.getInstrumentation().targetContext, expectedOwner, run, direction,
                        checkNotNull(args.getString("cloudyFilesHandoffCheckpointSha256")))
                    stage = if (phase == "verify") "VERIFY" else "CLEANUP"
                    if (phase == "verify") checkpoint.verify() else checkpoint.cleanup()
                }
                else -> error("UNSUPPORTED_RECEIVER_PHASE")
            }
        } catch (error: Throwable) {
            throw HandoffReceiverFailure.capture(stage, error, phase ?: "unknown")
        }
    }

    private fun exercise(case: String) {
        val run = InstrumentationRegistry.getArguments().getString("cloudySshFilesRun")
        // The empty Compose rule launches nothing: skip precedes private/store/Activity access.
        assumeTrue("Explicit owning-app retained SFTP qualification only", run != null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as Application
        var fixture: SshFilesFixture? = null
        var scenario: ActivityScenario<CloudySshFilesActivity>? = null
        var observer: CredentialStateObserver? = null
        var cover: SameTaskCover? = null
        var failed = false
        var stage = "INITIALIZE"
        fun record(error: Throwable, at: String) {
            failed = true
            val name = error.javaClass.simpleName.takeIf { Regex("[A-Za-z0-9_$]{1,128}").matches(it) } ?: "OTHER"
            val detailed = "${at}_$substage".takeIf { Regex("[A-Z_]{1,64}").matches(it) } ?: at
            try { fixture?.recordFailure(detailed, name) } catch (_: Throwable) { }
            try { fixture?.let { diagnostics(it, scenario) } } catch (_: Throwable) { }
        }
        try {
            check(context.packageName == "io.github.baitian6641.cloudy.${expectedOwner.wireId}" &&
                context.applicationInfo.uid == Process.myUid() && !context.isDeviceProtectedStorage)
            @Suppress("DEPRECATION")
            val info = context.packageManager.getActivityInfo(ComponentName(context, CloudySshFilesActivity::class.java), 0)
            check(!info.exported && info.applicationInfo.uid == Process.myUid())
            val owned = SshFilesFixture.open(context, expectedOwner, checkNotNull(run), case)
            fixture = owned
            stage = "SEED"
            owned.seed()
            val saves = CredentialStateObserver(owned.username, owned.password)
            observer = saves
            application.registerActivityLifecycleCallbacks(saves)
            stage = "LAUNCH"
            val live = ActivityScenario.launch<CloudySshFilesActivity>(Intent(context, CloudySshFilesActivity::class.java))
            scenario = live
            secure(live)
            stage = "SELECT_INPUT"
            clickEnabled("ssh_files_profile_${owned.profileId}", "PROFILE")
            input(owned)
            owned.phase("CASE_START")
            stage = "CONNECT"
            clickEnabled("ssh_files_connect", "CONNECT")
            helloRow() // Actual returned entry, not just the early Connected publication.
            val original = identity(live)
            check(!retired(original.proof)) { "PROOF_RETIRED_BEFORE_OPERATION" }
            check(compose.onAllNodesWithTag("ssh_files_username").fetchSemanticsNodes().isEmpty())
            check(compose.onAllNodesWithTag("ssh_files_password").fetchSemanticsNodes().isEmpty())
            owned.phase("REAL_CONNECTED_LISTING_CONFIRMED")
            when (case) {
                "basic" -> {
                    stage = "READ_FIRST"
                    readHello()
                    owned.phase("FIRST_READ_CONFIRMED")
                    stage = "REFRESH_DIRECTORY"
                    clickEnabled("ssh_files_refresh", "REFRESH")
                    helloRow()
                    stage = "READ_SECOND"
                    readHello()
                    check(identity(live).proof === original.proof) { "CONNECTION_NOT_REUSED" }
                    stage = "EXPLICIT_DISCONNECT"
                    disconnect()
                    SshFilesFixture.settle()
                    check(retired(original.proof))
                    phase(R.string.ssh_files_idle)
                    emptyInputAndUndo()
                    owned.phase("FRAMEWORK_UNDO_REDO_CLEARED_INPUT_CONFIRMED")
                    owned.phase("READ_REUSE_AND_EXPLICIT_RETIREMENT_CONFIRMED")
                }
                "recreate" -> {
                    stage = "READ_BEFORE_RECREATE"
                    readHello()
                    owned.phase("ESTABLISHED_RECREATING")
                    stage = "RECREATE"
                    live.recreate()
                    secure(live)
                    phase(R.string.ssh_files_connected)
                    val replacement = identity(live)
                    check(replacement.model === original.model && replacement.proof === original.proof) { "LIVE_OWNER_REPLACED" }
                    check(!retired(original.proof))
                    check(saves.saved.get() >= 1 && saves.clean.get()) { "SAVED_CREDENTIAL_FOUND" }
                    check(compose.onAllNodesWithTag("ssh_files_username").fetchSemanticsNodes().isEmpty())
                    check(compose.onAllNodesWithTag("ssh_files_password").fetchSemanticsNodes().isEmpty())
                    stage = "READ_AFTER_RECREATE"
                    readHello()
                    owned.phase("SAME_MODEL_PROOF_AND_POST_RECREATE_READ_CONFIRMED")
                }
                "focus" -> {
                    stage = "FRAMEWORK_FOCUS_INJECTION"
                    owned.phase("FRAMEWORK_FOCUS_CALLBACK_INJECTION_NOT_PHYSICAL")
                    live.onActivity { activity ->
                        check(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        activity.onWindowFocusChanged(false)
                    }
                    phase(R.string.ssh_files_connected)
                    check(identity(live).proof === original.proof && !retired(original.proof))
                    live.onActivity { it.onWindowFocusChanged(true) }
                    stage = "READ_AFTER_FOCUS"
                    readHello()
                    owned.phase("FRAMEWORK_VISIBLE_FOCUS_POLICY_AND_REUSE_CONFIRMED")
                }
                "hide" -> {
                    stage = "HIDE_SAME_TASK"
                    substage = "LAUNCH_COVER"
                    // A separate NEW_TASK test Activity need not hide a desktop window. Cover
                    // this exact task and require actual lifecycle callbacks from the original.
                    lateinit var ownedCover: SameTaskCover
                    live.onActivity { activity ->
                        ownedCover = SameTaskCover(activity)
                        cover = ownedCover
                        application.registerActivityLifecycleCallbacks(ownedCover)
                        activity.startActivity(Intent(activity, CloudyProfilesActivity::class.java))
                    }
                    owned.phase("SAME_TASK_NATIVE_COVER_LAUNCHED")
                    substage = "WAIT_REAL_STOP"
                    compose.waitUntil(10_000) { ownedCover.stopped.get() && ownedCover.coverResumed.get() }
                    check(!ownedCover.invalid.get()) { "COVER_IDENTITY_INVALID" }
                    owned.phase("ORIGINAL_ACTUAL_STOP_AND_OWNED_COVER_CONFIRMED")
                    SshFilesFixture.settle()
                    check(retired(original.proof)) { "HIDDEN_OWNER_NOT_RETIRED" }
                    stage = "RETURN_SAME_ACTIVITY"
                    substage = "FINISH_COVER"
                    instrumentation.runOnMainSync { ownedCover.finishOwned() }
                    substage = "WAIT_REAL_RETURN"
                    compose.waitUntil(10_000) { ownedCover.returned.get() && ownedCover.coverDestroyed.get() }
                    live.onActivity { check(ownedCover.isOriginal(it)) { "ORIGINAL_ACTIVITY_REPLACED" } }
                    check(!ownedCover.invalid.get()) { "COVER_LIFECYCLE_INVALID" }
                    owned.phase("OWNED_COVER_FINISHED_ORIGINAL_ACTUAL_RESUME_CONFIRMED")
                    secure(live)
                    phase(R.string.ssh_files_idle)
                    absentPreview(live)
                    check(!hasCurrentProof(live)) { "HIDDEN_OWNER_RECONNECTED" }
                    emptyInputAndUndo()
                    owned.phase("FRAMEWORK_UNDO_REDO_CLEARED_INPUT_CONFIRMED")
                    owned.phase("FRAMEWORK_STOP_RETIREMENT_AND_NO_REPLAY_CONFIRMED")
                }
                "profile" -> {
                    stage = "REAL_PROFILE_CHANGE"
                    owned.mutateProfile()
                    SshFilesFixture.settle()
                    check(retired(original.proof)) { "PROFILE_CHANGE_NOT_RETIRED" }
                    phase(R.string.ssh_files_failed)
                    absentPreview(live)
                    check(!hasCurrentProof(live))
                    owned.phase("REAL_PROFILE_INVALIDATION_RETIREMENT_CONFIRMED")
                }
                "hold" -> {
                    stage = "ACTUAL_READ_HOLD"
                    owned.phase("READ_REQUEST_PLANNED")
                    clickHelloRead()
                    owned.awaitActualReadHold()
                    stage = "DISCONNECT_HELD_READ"
                    disconnect()
                    SshFilesFixture.settle()
                    check(retired(original.proof)) { "HELD_OPERATION_NOT_RETIRED" }
                    phase(R.string.ssh_files_idle)
                    absentPreview(live)
                    check(!hasCurrentProof(live))
                    // A further framework/main idle must not publish a late read into the retired UI.
                    compose.waitForIdle()
                    absentPreview(live)
                    owned.phase("ACTUAL_HELD_READ_DISCONNECT_NO_LATE_PREVIEW_CONFIRMED")
                }
            }
            stage = "ASSERT_BASELINE"
            check(saves.clean.get()) { "SAVED_STATE_RETAINED_CREDENTIAL" }
            owned.assertUnchanged()
            owned.recordUiEvidence(saves.saved.get(), saves.clean.get(), case == "focus")
            owned.phase("CASE_ASSERTIONS_CONFIRMED")
        } catch (error: Throwable) { record(error, stage) }
        finally {
            var closed = true
            cover?.let { ownedCover ->
                try {
                    instrumentation.runOnMainSync { ownedCover.finishOwned() }
                    compose.waitUntil(10_000) { ownedCover.coverDestroyed.get() }
                }
                catch (error: Throwable) { closed = false; record(error, "CLOSE_OWNED_COVER") }
                try { application.unregisterActivityLifecycleCallbacks(ownedCover) }
                catch (error: Throwable) { closed = false; record(error, "UNREGISTER_COVER") }
                ownedCover.dispose()
            }
            try { scenario?.close() } catch (error: Throwable) { closed = false; record(error, "CLOSE_ACTIVITY") }
            observer?.let { saves ->
                try { application.unregisterActivityLifecycleCallbacks(saves) }
                catch (error: Throwable) { record(error, "UNREGISTER_OBSERVER") }
                saves.dispose()
            }
            try {
                check(closed) { "ACTIVITY_CLOSE_UNCONFIRMED" }
                fixture?.restore(passed = !failed)
            } catch (error: Throwable) { record(error, "RESTORE_PRESERVE_FIXTURE") }
        }
        if (failed) throw AssertionError("SSH_FILES_QUALIFICATION_FAILED_SEE_PRIVATE_PHASE_RECEIPT")
    }

    /** New positive cases use actual engine proofs only; reflection observes identity/retirement. */
    private fun exerciseLocations(case: String) {
        val run = InstrumentationRegistry.getArguments().getString("cloudySshFilesRun")
        assumeTrue("Explicit owning-app retained SFTP qualification only", run != null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as Application
        var fixture: SshFilesFixture? = null
        var locations: FilesLocationsFixture? = null
        var checkpoint: FilesHandoffReceiverCheckpoint? = null
        val scenarios = mutableListOf<ActivityScenario<CloudySshFilesActivity>>()
        var manager: ActivityScenario<CloudyFilesLocationsActivity>? = null
        val windows = OwnedLocationWindows(checkNotNull(run), context.packageName)
        var saves: CredentialStateObserver? = null
        var failed = false
        var stage = "INITIALIZE"
        var firstFailure: HandoffReceiverFailure? = null
        fun failure(error: Throwable, at: String) {
            failed = true
            if (case == "handoff-receiver" && firstFailure == null) firstFailure = HandoffReceiverFailure.capture(at, error)
            val name = if (case == "handoff-receiver") "HANDOFF_FAILURE" else
                error.javaClass.simpleName.takeIf { Regex("[A-Za-z0-9_$]{1,128}").matches(it) } ?: "OTHER"
            try { fixture?.recordFailure(at, name) } catch (_: Throwable) { }
        }
        app.registerActivityLifecycleCallbacks(windows)
        try {
            if (case == "handoff-receiver") {
                stage = "CHECKPOINT_BEGIN"
                checkpoint = FilesHandoffReceiverCheckpoint.begin(context, expectedOwner, run,
                    checkNotNull(InstrumentationRegistry.getArguments().getString("cloudyFilesHandoffDirection")))
            }
            if (case == "handoff-receiver") stage = "FILES_FIXTURE_OPEN"
            val owned = SshFilesFixture.open(context, expectedOwner, run, case)
            fixture = owned
            if (case == "handoff-receiver") stage = "FILES_FIXTURE_SEED"
            owned.seed()
            if (case == "handoff-receiver") stage = "LOCATIONS_FIXTURE_OPEN"
            val resource = FilesLocationsFixture(context, expectedOwner, run, case)
            locations = resource
            saves = CredentialStateObserver(owned.username, owned.password)
            app.registerActivityLifecycleCallbacks(checkNotNull(saves))
            fun ordinary(): ActivityScenario<CloudySshFilesActivity> {
                val live = ActivityScenario.launch<CloudySshFilesActivity>(Intent(context, CloudySshFilesActivity::class.java)
                    .putExtra(OwnedLocationWindows.OWNER, run))
                scenarios.add(live)
                secure(live)
                clickEnabled("ssh_files_profile_${owned.profileId}", "PROFILE")
                input(owned)
                clickEnabled("ssh_files_connect", "CONNECT")
                helloRow()
                return live
            }
            stage = "CONNECT_AND_LIST"
            owned.phase("CASE_START")
            val first = ordinary()
            val initial = identity(first)
            compose.onNodeWithTag("ssh_files_list").performScrollToNode(hasTestTag("ssh_files_path"))
            compose.onNodeWithTag("ssh_files_path").performTextReplacement(FilesLocationsFixture.NESTED_ROOT)
            clickEnabled("ssh_files_list_path", "LIST")
            remoteRow(FilesLocationsFixture.NESTED_NAME)
            owned.phase("ACTUAL_NONDEFAULT_DIRECTORY_LISTED")
            if (case == "registration-focus") {
                stage = "CANCEL_PROPOSAL"
                clickEnabled("ssh_location_register", "REGISTER")
                clickEnabled("ssh_location_cancel", "CANCEL")
                resource.assertExpected()
                check(identity(first).proof === initial.proof && !retired(initial.proof))
                clickEnabled("ssh_files_refresh", "REFRESH")
                remoteRow(FilesLocationsFixture.NESTED_NAME)
                stage = "FOCUS_INVALIDATES_PROPOSAL"
                clickEnabled("ssh_location_register", "REGISTER")
                first.onActivity { it.onWindowFocusChanged(false) }
                compose.waitForIdle()
                check(compose.onAllNodesWithTag("ssh_location_confirmation").fetchSemanticsNodes().isEmpty())
                first.onActivity { it.onWindowFocusChanged(true) }
                resource.assertExpected()
                check(identity(first).proof === initial.proof && !retired(initial.proof))
                owned.phase("CANCEL_AND_FRAMEWORK_FOCUS_PROPOSAL_DENIAL_CONFIRMED")
            } else {
                stage = "REGISTER_ACTUAL_LISTING"
                val firstRow = registerLocation(owned, resource, "Location $run A")
                owned.phase("REAL_PRIVATE_REGISTRATION_CONFIRMED")
                if (case == "handoff-receiver") {
                    stage = "RETIRE_PREPARATION_CONNECTION"
                    disconnect()
                    SshFilesFixture.settle()
                    first.close()
                    stage = "SHARE_ACTUAL_REGISTERED_LOCATION"
                    manager = launchManager(context, run)
                    inspectLocation(firstRow)
                    val shared = resource.expectChange(firstRow, true)
                    managerClick("locations_share")
                    managerClick("locations_confirm")
                    waitManagerRevision(shared.revision)
                    resource.confirmChange(shared)
                    owned.phase("RETAINED_SHARED_RESOURCE_CONFIRMED")
                } else if (case == "register") {
                    stage = "ORDINARY_RESOURCE_INDEPENDENCE"
                    val shared = resource.changeThroughStore(firstRow, true)
                    resource.changeThroughStore(shared, false)
                    check(identity(first).proof === initial.proof && !retired(initial.proof))
                    readRemote(FilesLocationsFixture.NESTED_NAME, FilesLocationsFixture.NESTED_TEXT)
                    check(identity(first).proof === initial.proof && !retired(initial.proof))
                    owned.phase("ORDINARY_LIVE_PROOF_SURVIVES_RESOURCE_STORE_CHANGES")
                    disconnect()
                    SshFilesFixture.settle()
                    first.close()
                    stage = "SECOND_REAL_REGISTRATION"
                    val second = ordinary()
                    awaitEnabled("ssh_files_path", "PATH")
                    compose.onNodeWithTag("ssh_files_path").performTextReplacement(FilesLocationsFixture.NESTED_ROOT)
                    clickEnabled("ssh_files_list_path", "LIST")
                    remoteRow(FilesLocationsFixture.NESTED_NAME)
                    val secondRow = registerLocation(owned, resource, "Location $run B")
                    disconnect()
                    SshFilesFixture.settle()
                    second.close()
                    stage = "MANAGER_REAL_SHARE_REVOKE"
                    manager = launchManager(context, run)
                    inspectLocation(secondRow)
                    managerClick("locations_share")
                    managerClick("locations_cancel")
                    resource.assertExpected()
                    val next = resource.expectChange(secondRow, true)
                    managerClick("locations_share")
                    managerClick("locations_confirm")
                    waitManagerRevision(next.revision)
                    resource.confirmChange(next)
                    val revoked = resource.expectChange(next, false)
                    managerClick("locations_revoke")
                    managerClick("locations_confirm")
                    waitManagerRevision(revoked.revision)
                    resource.confirmChange(revoked)
                    owned.phase("MANAGER_CANCEL_SHARE_AND_REVOKE_CONFIRMED")
                } else {
                    stage = "RETIRE_BEFORE_REGISTERED_OPEN"
                    disconnect()
                    SshFilesFixture.settle()
                    first.close()
                    manager = launchManager(context, run)
                    inspectLocation(firstRow)
                    stage = "MANAGER_TICKET_OPEN"
                    checkNotNull(manager).onActivity { windows.armOpen(it) }
                    managerClick("locations_open")
                    compose.waitUntil(10_000) { windows.opened.get() != null }
                    val opened = checkNotNull(windows.opened.get())
                    stage = "FRESH_REGISTERED_AUTHENTICATION"
                    // Username is frozen by the registered destination; never replace it as an ordinary input.
                    awaitEnabled("ssh_files_username", "USERNAME")
                    check(compose.onNodeWithTag("ssh_files_username").fetchSemanticsNode()
                        .config[SemanticsProperties.EditableText].text == owned.username) { "REGISTERED_ACCOUNT_MISMATCH" }
                    awaitEnabled("ssh_files_password", "PASSWORD")
                    compose.onNodeWithTag("ssh_files_password").performTextReplacement(owned.password)
                    clickEnabled("ssh_files_connect", "CONNECT")
                    remoteRow(FilesLocationsFixture.NESTED_NAME)
                    val bound = AtomicReference<Identity>()
                    instrumentation.runOnMainSync {
                        val model = model(opened)
                        bound.set(Identity(model, checkNotNull(proof(model))))
                        val state = model.javaClass.getMethod("getState").invoke(model)
                        check(state.javaClass.getMethod("getPath").invoke(state) == FilesLocationsFixture.NESTED_ROOT)
                    }
                    readRemote(FilesLocationsFixture.NESTED_NAME, FilesLocationsFixture.NESTED_TEXT)
                    owned.phase("REAL_TICKET_FRESH_ACCOUNT_NONDEFAULT_LIST_AND_READ_CONFIRMED")
                    stage = "LIVE_REGISTERED_RESOURCE_REVOKE"
                    resource.changeThroughStore(firstRow, false)
                    SshFilesFixture.settle()
                    check(retired(checkNotNull(bound.get()).proof))
                    instrumentation.runOnMainSync {
                        val state = model(opened).javaClass.getMethod("getState").invoke(model(opened))
                        check(state.javaClass.getMethod("getPreview").invoke(state) == null)
                    }
                    phase(R.string.ssh_files_failed)
                    owned.phase("ACTUAL_RESOURCE_REVOCATION_RETIRED_REGISTERED_OWNER")
                }
            }
            resource.assertExpected()
            owned.assertUnchanged()
            check(checkNotNull(saves).clean.get())
            owned.recordUiEvidence(checkNotNull(saves).saved.get(), checkNotNull(saves).clean.get(), case == "registration-focus")
            owned.phase("CASE_ASSERTIONS_CONFIRMED")
        } catch (error: Throwable) { failure(error, stage) }
        finally {
            var closed = true
            try { instrumentation.runOnMainSync { windows.finishOwned() } }
            catch (error: Throwable) { closed = false; failure(error, "CLOSE_OWNED_WINDOWS") }
            try {
                manager?.close()
                scenarios.asReversed().forEach { it.close() }
                compose.waitUntil(10_000) {
                    val destroyed = AtomicBoolean()
                    instrumentation.runOnMainSync { destroyed.set(windows.allDestroyed()) }
                    destroyed.get()
                }
            } catch (error: Throwable) { closed = false; failure(error, "CLOSE_SCENARIOS") }
            try { saves?.let { observer -> app.unregisterActivityLifecycleCallbacks(observer); observer.dispose() } }
            catch (error: Throwable) {
                if (case != "handoff-receiver") throw error
                failure(error, "CLOSE_OBSERVER")
            }
            try {
                check(closed)
                if (case == "handoff-receiver") {
                    // Any failure retains original receipts and exact rows, including partially
                    // prepared state. A later process must import the immutable checkpoint.
                    check(!failed)
                    val hash = checkNotNull(checkpoint).seal(checkNotNull(fixture), checkNotNull(locations))
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("cloudyFilesHandoffReceiverPhase", "PREPARED_RETIRED")
                        putString("cloudyFilesHandoffCheckpointSha256", hash)
                        putString("cloudyFilesHandoffRun", run)
                        putString("cloudyFilesHandoffReceiverPackage", context.packageName)
                    })
                } else {
                    locations?.restore() // Resources before their exact profile/trust parents.
                    fixture?.restore(!failed)
                }
            } catch (error: Throwable) { failure(error, "RESTORE_PRESERVE_FIXTURE") }
            try { app.unregisterActivityLifecycleCallbacks(windows); windows.dispose() }
            catch (error: Throwable) {
                if (case != "handoff-receiver") throw error
                failure(error, "CLOSE_CALLBACKS")
            }
        }
        firstFailure?.let { throw it }
        if (failed) throw AssertionError("SSH_FILES_LOCATIONS_QUALIFICATION_FAILED_SEE_PRIVATE_RECEIPT")
    }

    private fun registerLocation(fixture: SshFilesFixture, resources: FilesLocationsFixture, label: String): FilesResourceRegistration {
        resources.plan(label, fixture.observedProfile, fixture.acceptedPin, FilesLocationsFixture.NESTED_ROOT, fixture.username)
        clickEnabled("ssh_location_register", "REGISTER")
        awaitEnabled("ssh_location_label", "LOCATION_LABEL")
        compose.onNodeWithTag("ssh_location_label").performTextReplacement(label)
        clickEnabled("ssh_location_confirm", "CONFIRM")
        compose.waitUntil(10_000) {
            try { scroll("ssh_location_saved"); compose.onNodeWithTag("ssh_location_saved").fetchSemanticsNode(); true }
            catch (_: AssertionError) { false }
        }
        return resources.captureCreated()
    }
    private fun remoteRow(name: String): String {
        phase(R.string.ssh_files_connected)
        val matcher = SemanticsMatcher("returned remote entry") { node ->
            node.config.contains(SemanticsProperties.TestTag) && node.config[SemanticsProperties.TestTag].matches(Regex("ssh_files_entry_[0-9]{1,3}"))
        } and hasAnyDescendant(hasText(name))
        var tag: String? = null
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithTag("ssh_files_list").performScrollToNode(matcher)
                tag = compose.onNode(matcher).fetchSemanticsNode().config[SemanticsProperties.TestTag]
                true
            } catch (_: AssertionError) { false }
        }
        return checkNotNull(tag)
    }
    private fun readRemote(name: String, expected: String) {
        val index = remoteRow(name).substringAfterLast('_')
        clickEnabled("ssh_files_read_$index", "READ")
        compose.waitUntil(10_000) {
            try {
                scroll("ssh_files_preview_content")
                compose.onNodeWithTag("ssh_files_preview_content").fetchSemanticsNode()
                    .config[SemanticsProperties.Text].single().text == expected
            } catch (_: AssertionError) { false }
        }
        phase(R.string.ssh_files_connected)
    }
    private fun launchManager(context: Context, run: String): ActivityScenario<CloudyFilesLocationsActivity> =
        ActivityScenario.launch(Intent(context, CloudyFilesLocationsActivity::class.java).putExtra(OwnedLocationWindows.OWNER, run))
    private fun managerClick(tag: String) {
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithTag("locations_list").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).performScrollTo().assertIsEnabled()
                true
            } catch (_: AssertionError) { false }
        }
        compose.onNodeWithTag(tag).performClick()
    }
    private fun inspectLocation(row: FilesResourceRegistration) {
        val matcher = SemanticsMatcher("owned location row") { node ->
            node.config.contains(SemanticsProperties.TestTag) && node.config[SemanticsProperties.TestTag].matches(Regex("locations_row_[0-9]{1,3}"))
        } and hasAnyDescendant(hasText(row.label))
        compose.waitUntil(10_000) {
            try { compose.onNodeWithTag("locations_list").performScrollToNode(matcher); compose.onNode(matcher).fetchSemanticsNode(); true }
            catch (_: AssertionError) { false }
        }
        val index = compose.onNode(matcher).fetchSemanticsNode().config[SemanticsProperties.TestTag].substringAfterLast('_')
        managerClick("locations_inspect_$index")
    }
    private fun waitManagerRevision(revision: Long) {
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithTag("locations_list").performScrollToNode(hasTestTag("locations_detail_revision"))
                compose.onNodeWithTag("locations_detail_revision").fetchSemanticsNode().config[SemanticsProperties.Text].single().text == revision.toString()
            } catch (_: AssertionError) { false }
        }
    }

    private class OwnedLocationWindows(private val run: String, private val ownerPackage: String) : Application.ActivityLifecycleCallbacks {
        private val owned = mutableListOf<Activity>()
        private var managerTask: Int? = null
        private var expectingOpen = false
        val opened = AtomicReference<CloudySshFilesActivity?>()
        fun armOpen(manager: CloudyFilesLocationsActivity) {
            check(manager in owned)
            managerTask = manager.taskId
            expectingOpen = true
        }
        fun finishOwned() { owned.asReversed().forEach { if (!it.isDestroyed && !it.isFinishing) it.finish() } }
        fun allDestroyed(): Boolean = owned.all { it.isDestroyed }
        fun dispose() { owned.clear(); opened.set(null) }
        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            if (activity.packageName != ownerPackage || activity !is CloudySshFilesActivity && activity !is CloudyFilesLocationsActivity) return
            if (activity.intent.getStringExtra(OWNER) == run) owned.add(activity)
            else if (expectingOpen && activity is CloudySshFilesActivity && activity.taskId == managerTask) {
                check(opened.get() == null)
                expectingOpen = false
                owned.add(activity)
                opened.set(activity)
            }
        }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        companion object { const val OWNER = "cloudy.testing.owned.files.window" }
    }

    private fun input(fixture: SshFilesFixture) {
        awaitEnabled("ssh_files_username", "USERNAME")
        substage = "EDIT_USERNAME"
        compose.onNodeWithTag("ssh_files_username").performTextReplacement(fixture.username)
        awaitEnabled("ssh_files_password", "PASSWORD")
        substage = "EDIT_PASSWORD"
        compose.onNodeWithTag("ssh_files_password").performTextReplacement(fixture.password)
    }
    private fun helloRow(): String {
        phase(R.string.ssh_files_connected)
        substage = "WAIT_ENTRY_ROW"
        val matcher = SemanticsMatcher("indexed remote entry") { node ->
            node.config.contains(SemanticsProperties.TestTag) &&
                node.config[SemanticsProperties.TestTag].matches(Regex("ssh_files_entry_[0-9]{1,3}"))
        } and hasAnyDescendant(hasText(SshFilesFixture.HELLO_NAME))
        var tag: String? = null
        compose.waitUntil(15_000) {
            try {
                compose.onNodeWithTag("ssh_files_list").performScrollToNode(matcher)
                tag = compose.onNode(matcher).fetchSemanticsNode().config[SemanticsProperties.TestTag]
                true
            } catch (_: AssertionError) { false }
        }
        return checkNotNull(tag)
    }
    private fun clickHelloRead() {
        val index = helloRow().substringAfterLast('_').toInt()
        clickEnabled("ssh_files_read_$index", "READ")
    }
    private fun readHello() {
        clickHelloRead()
        substage = "WAIT_PREVIEW"
        compose.waitUntil(10_000) {
            try {
                scroll("ssh_files_preview_content")
                val text = compose.onNodeWithTag("ssh_files_preview_content").fetchSemanticsNode()
                    .config[SemanticsProperties.Text].single().text
                check(text == SshFilesFixture.HELLO_TEXT) { "EXACT_PREVIEW_MISMATCH" }
                true
            } catch (_: AssertionError) { false }
        }
        phase(R.string.ssh_files_connected)
    }
    private fun disconnect() {
        compose.onNodeWithTag("ssh_files_disconnect").assertIsEnabled().performClick()
    }
    private fun absentPreview(scenario: ActivityScenario<CloudySshFilesActivity>) {
        scenario.onActivity { activity ->
            val model = model(activity)
            val state = model.javaClass.getMethod("getState").invoke(model)
            check(state.javaClass.getMethod("getPreview").invoke(state) == null) { "LATE_PREVIEW_IN_MODEL" }
        }
        check(compose.onAllNodesWithTag("ssh_files_preview").fetchSemanticsNodes().isEmpty()) { "LATE_PREVIEW_VISIBLE" }
    }
    private fun emptyInputAndUndo() {
        for (tag in listOf("ssh_files_username", "ssh_files_password")) {
            awaitEnabled(tag, "EMPTY_INPUT")
            substage = "CHECK_UNDO"
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithTag(tag).performKeyInput {
                keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft)
            }
            check(compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty()) {
                "INPUT_RECOVERED_BY_UNDO"
            }
            compose.onNodeWithTag(tag).performKeyInput {
                keyDown(Key.CtrlLeft); keyDown(Key.ShiftLeft); pressKey(Key.Z); keyUp(Key.ShiftLeft); keyUp(Key.CtrlLeft)
            }
            substage = "CHECK_REDO"
            check(compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty()) {
                "INPUT_RECOVERED_AFTER_RETIREMENT"
            }
        }
        scroll("ssh_files_connect")
        compose.onNodeWithTag("ssh_files_connect").assertIsNotEnabled()
    }
    private fun phase(label: Int) {
        substage = "WAIT_PHASE"
        val expected = InstrumentationRegistry.getInstrumentation().targetContext.getString(label)
        compose.waitUntil(15_000) {
            try {
                scroll("ssh_files_phase")
                compose.onNodeWithTag("ssh_files_phase").fetchSemanticsNode().config[SemanticsProperties.Text].single().text == expected
            } catch (_: AssertionError) { false }
        }
    }
    private fun scroll(tag: String) {
        compose.onNodeWithTag("ssh_files_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun awaitEnabled(tag: String, control: String) {
        // A recreated window can have the same live session before Android delivers its real
        // focus callback. Wait for the actual UI guard; never manufacture readiness or focus.
        substage = "WAIT_${control}_ENABLED"
        compose.waitUntil(10_000) {
            try {
                scroll(tag)
                compose.onNodeWithTag(tag).assertIsEnabled()
                true
            } catch (_: AssertionError) { false }
        }
    }
    private fun clickEnabled(tag: String, control: String) {
        awaitEnabled(tag, control)
        substage = "CLICK_$control"
        compose.onNodeWithTag(tag).assertIsEnabled().performClick()
    }
    /** Fixed enums/booleans only: no semantics tree, text field, source message or cause. */
    private fun diagnostics(fixture: SshFilesFixture, scenario: ActivityScenario<CloudySshFilesActivity>?) {
        if (scenario == null) { fixture.phase("DIAG_ACTIVITY_ABSENT"); return }
        var values = listOf("DIAG_ACTIVITY_UNAVAILABLE")
        scenario.onActivity { activity ->
            fun boolean(value: Boolean?) = when (value) { true -> "TRUE"; false -> "FALSE"; null -> "UNKNOWN" }
            fun activityFlag(name: String): Boolean? = try {
                val delegate = activity.javaClass.getDeclaredField(name + "\$delegate").apply { isAccessible = true }.get(activity)
                delegate.javaClass.getMethod("getValue").apply { isAccessible = true }.invoke(delegate) as? Boolean
            } catch (_: Throwable) { null }
            val model = try { model(activity) } catch (_: Throwable) { null }
            val phase = try {
                val state = checkNotNull(model).javaClass.getMethod("getState").invoke(model)
                (state.javaClass.getMethod("getPhase").invoke(state) as? Enum<*>)?.name
                    ?.takeIf { it in setOf("IDLE", "LOADING", "CONNECTING", "CONNECTED", "READING", "RETIRING", "FAILED") }
            } catch (_: Throwable) { null }
            val proofState = try {
                val proof = model?.let { proof(it) }
                if (proof == null) "ABSENT" else boolean(retired(proof))
            } catch (_: Throwable) { "UNKNOWN" }
            values = listOf("DIAG_PHASE_${phase ?: "UNKNOWN"}",
                "DIAG_WINDOW_FOCUS_${boolean(activity.hasWindowFocus())}",
                "DIAG_RESUMED_${boolean(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}",
                "DIAG_FIELD_INTERACTIVE_${boolean(activityFlag("interactive"))}",
                "DIAG_PROOF_RETIRED_$proofState")
        }
        values.forEach { fixture.phase(it) }
    }
    private fun secure(scenario: ActivityScenario<CloudySshFilesActivity>) {
        scenario.onActivity { activity ->
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            check(activity.window.decorView.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS)
        }
    }
    private class Identity(val model: Any, val proof: Any)
    private fun model(activity: CloudySshFilesActivity): Any =
        checkNotNull(activity.javaClass.getDeclaredField("model").apply { isAccessible = true }.get(activity))
    private fun proof(model: Any): Any? {
        val session = model.javaClass.getDeclaredField("session").apply { isAccessible = true }.get(model) ?: return null
        val transport = (session.javaClass.getDeclaredField("transport").apply { isAccessible = true }.get(session) as AtomicReference<*>).get() ?: return null
        return transport.javaClass.getMethod("getOwnership").invoke(transport)
    }
    private fun identity(scenario: ActivityScenario<CloudySshFilesActivity>): Identity {
        var identity: Identity? = null
        scenario.onActivity { activity -> val model = model(activity); identity = Identity(model, checkNotNull(proof(model))) }
        return checkNotNull(identity)
    }
    private fun hasCurrentProof(scenario: ActivityScenario<CloudySshFilesActivity>): Boolean {
        var current = false
        scenario.onActivity { current = proof(model(it)) != null }
        return current
    }
    private fun retired(proof: Any): Boolean = proof.javaClass.getMethod("isRetired").invoke(proof) as Boolean

    /** Test-lifetime ownership of one real opaque cover, never a synthetic lifecycle call. */
    private class SameTaskCover(original: CloudySshFilesActivity) : Application.ActivityLifecycleCallbacks {
        private var original: CloudySshFilesActivity? = original
        private var cover: Activity? = null
        private val task = original.taskId
        private val ownerPackage = original.packageName
        val stopped = AtomicBoolean()
        val returned = AtomicBoolean()
        val coverResumed = AtomicBoolean()
        val coverDestroyed = AtomicBoolean()
        val invalid = AtomicBoolean()
        fun isOriginal(activity: Activity) = activity === original
        fun finishOwned() { cover?.let { if (!it.isDestroyed && !it.isFinishing) it.finish() } }
        fun dispose() { original = null; cover = null }
        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            if (activity !is CloudyProfilesActivity || activity.packageName != ownerPackage) return
            if (cover != null) { invalid.set(true); return }
            cover = activity
            if (activity.taskId != task) invalid.set(true)
            val attributes = activity.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent, android.R.attr.windowIsFloating))
            try { if (attributes.getBoolean(0, false) || attributes.getBoolean(1, false)) invalid.set(true) }
            finally { attributes.recycle() }
        }
        override fun onActivityResumed(activity: Activity) {
            if (activity === cover) coverResumed.set(true)
            if (activity === original && stopped.get()) returned.set(true)
        }
        override fun onActivityStopped(activity: Activity) {
            if (activity === original) {
                if (activity.isChangingConfigurations) invalid.set(true)
                stopped.set(true)
            }
        }
        override fun onActivityDestroyed(activity: Activity) {
            if (activity === cover) coverDestroyed.set(true)
            if (activity === original) invalid.set(true)
        }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    /** Scan actual marshalled framework saves without retaining Bundles or publishing input. */
    private class CredentialStateObserver(username: String, password: String) : Application.ActivityLifecycleCallbacks {
        private val needles = listOf(username, password).flatMap { value ->
            listOf(value.toByteArray(Charsets.UTF_8), value.toByteArray(Charsets.UTF_16LE), value.toByteArray(Charsets.UTF_16BE))
        }
        val saved = AtomicInteger()
        val clean = AtomicBoolean(true)
        @Suppress("DEPRECATION")
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            if (activity !is CloudySshFilesActivity) return
            try {
                val record = outState.getBundle("io.github.baitian6641.cloudy.ssh-files.v1")
                if (record == null || record.keySet() != setOf("schema", "owner", "pending", "registrationUncertain") ||
                    record.get("schema") != 2 || record.get("owner") != activity.packageName.substringAfterLast('.') ||
                    record.get("pending") !is Boolean || record.get("registrationUncertain") !is Boolean) clean.set(false)
                val parcel = Parcel.obtain()
                val bytes = try { parcel.writeBundle(outState); parcel.marshall() } finally { parcel.recycle() }
                try { if (bytes.size > 1_048_576 || needles.any { contains(bytes, it) }) clean.set(false) }
                finally { bytes.fill(0) }
            } catch (_: Throwable) { clean.set(false) }
            saved.incrementAndGet()
        }
        private fun contains(bytes: ByteArray, needle: ByteArray): Boolean {
            if (needle.isEmpty()) return false
            for (start in 0..(bytes.size - needle.size)) if (needle.indices.all { bytes[start + it] == needle[it] }) return true
            return false
        }
        fun dispose() { needles.forEach { it.fill(0) } }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
