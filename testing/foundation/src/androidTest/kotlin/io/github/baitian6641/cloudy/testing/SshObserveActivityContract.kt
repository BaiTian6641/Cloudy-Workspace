package io.github.baitian6641.cloudy.testing

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustChallenge
import io.github.baitian6641.cloudy.domain.SshTrustEvaluation
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.CloudySshObserveActivity
import io.github.baitian6641.cloudy.platform.ConnectionProfileCatalog
import io.github.baitian6641.cloudy.platform.ConnectionProfileVersion
import io.github.baitian6641.cloudy.platform.ConnectionProfiles
import io.github.baitian6641.cloudy.platform.ConnectionProfilesCode
import io.github.baitian6641.cloudy.platform.ConnectionProfilesResult
import io.github.baitian6641.cloudy.platform.R
import io.github.baitian6641.cloudy.platform.SshHostTrustStore
import io.github.baitian6641.cloudy.platform.SshTrustCatalog
import io.github.baitian6641.cloudy.platform.SshTrustStoreCode
import io.github.baitian6641.cloudy.platform.SshTrustStoreResult
import java.io.File
import java.util.UUID
import java.nio.file.Files
import java.lang.reflect.Modifier
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Opt-in real owning-app UI and SSH loopback observation. The host supplies a controlled server
 * (optionally USB-reversed) and its independently known public key. No test starts a listener,
 * grants LAN permission, authenticates or clears app data. Save cases enroll only the exact
 * observed fixture pin and restore it with guarded test-only SQL; product tombstone policy stays intact.
 */
abstract class SshObserveActivityContract(private val expectedOwner: WindowOwner) {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun realSignedKeyCanBeComparedAndCancelledWithoutTrustMutation() = exercise("cancel")
    @Test fun explicitFirstUseSaveThenFreshCheckMatchesWithoutAuthentication() = exercise("save")
    @Test fun staleFirstUseConfirmationRefusesCompetingTrustCas() = exercise("stale")

    private fun exercise(mode: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val run = args.getString("cloudySshObserveRun")
        // No Activity rule launches the product before this assumption or argument validation.
        assumeTrue("Explicit SSH observation qualification only", run != null)
        require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(checkNotNull(run)))
        val portText = checkNotNull(args.getString("cloudySshObservePort"))
        require(portText.length in 1..5 && portText.all { it in '0'..'9' })
        val port = portText.toInt().also { require(it in 1..65535 && it.toString() == portText) }
        val expectedKey = SshHostKeyIdentity.create(checkNotNull(args.getString("cloudySshObserveKeyFormat")),
            checkNotNull(args.getString("cloudySshObserveFingerprint")))
        val context = instrumentation.targetContext
        assertEquals("io.github.baitian6641.cloudy.${expectedOwner.wireId}", context.packageName)
        assertEquals(Process.myUid(), context.applicationInfo.uid)
        assertFalse(context.isDeviceProtectedStorage)
        @Suppress("DEPRECATION")
        val activityInfo = context.packageManager.getActivityInfo(ComponentName(context, CloudySshObserveActivity::class.java), 0)
        assertFalse(activityInfo.exported)
        assertEquals(Process.myUid(), activityInfo.applicationInfo.uid)
        val peer = SshPeerIdentity.create("127.0.0.1", port)
        val profileStore = ConnectionProfiles(context)
        val trustStore = SshHostTrustStore(context)
        val baseline = profile { profileStore.load() }
        val trustBaseline = trust { trustStore.load() }
        assertTrue("Fixture peer already has trust; preserve it", trustBaseline.snapshots.none { it.peer == peer })
        val trustFixture = SshObserveTrustFixture(context, expectedOwner, trustBaseline)
        val label = "Observe ${expectedOwner.wireId} $run $mode"
        assertTrue(baseline.profiles.none { it.label == label })
        val directory = File(context.noBackupFilesDir, "ssh-observe-qualification")
        check(!Files.isSymbolicLink(directory.toPath()) && (directory.isDirectory || directory.mkdirs()))
        val suffix = if (mode == "cancel") "" else "-$mode"
        val receiptPath = File(directory, "$run-${expectedOwner.wireId}$suffix.json")
        check(listOf("", ".bak", ".new").none {
            val path = File(receiptPath.path + it)
            path.exists() || Files.isSymbolicLink(path.toPath())
        }) { "Existing fixture receipt; preserve it" }
        val file = AtomicFile(receiptPath)
        val receipt = JSONObject().put("run", run).put("owner", expectedOwner.wireId)
            .put("package", context.packageName).put("uid", Process.myUid())
            .put("transport", "Controlled loopback; host must label any USB reverse separately")
            .put("profileBaseline", profiles(baseline)).put("trustBaseline", trusts(trustBaseline))
            .put("trustBaselineMetadata", JSONArray(trustFixture.baselineMetadata())).put("mode", mode)
            .put("phase", "CREATING").put("pendingLabel", label).put("pendingHost", peer.host).put("pendingPort", port)
        fun persist() {
            val output = file.startWrite()
            try { output.write(receipt.toString(2).toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (error: Throwable) { file.failWrite(output); throw error }
        }
        persist() // Durable test receipt precedes the first profile mutation.
        instrumentation.sendStatus(0, Bundle().apply {
            putString("cloudySshObserveReceipt", receiptPath.absolutePath)
            putString("cloudySshObservePhase", "BASELINE_BEFORE_MUTATION")
        })
        var owned: ConnectionProfile? = null
        var scenario: ActivityScenario<CloudySshObserveActivity>? = null
        var expectedTrust: SshTrustSnapshot? = null
        try {
            owned = profile { profileStore.create(baseline.generation,
                ConnectionProfileDraft.create(expectedOwner, label, ConnectionProtocol.SSH, peer.host, peer.port)) }.profile
            val fixture = checkNotNull(owned)
            receipt.put("phase", "READY").put("ownedId", fixture.id).put("ownedRevision", fixture.revision)
                .put("ownedProfile", profiles(baseline.generation, listOf(fixture)))
            persist()
            scenario = ActivityScenario.launch(Intent(context, CloudySshObserveActivity::class.java))
            settle()
            compose.waitForIdle()
            val current = profile { profileStore.load() }
            val index = current.profiles.filter { it.protocol == ConnectionProtocol.SSH }.indexOfFirst { it.id == fixture.id }
            check(index >= 0)
            compose.onNodeWithTag("observe_profiles").performScrollToNode(hasTestTag("observe_select_$index"))
            compose.onNodeWithTag("observe_select_$index").performScrollTo().assertIsEnabled().performClick()
            checkKey()
            compose.waitUntil(25_000) { compose.onAllNodesWithTag("observe_dialog").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("observe_host").assertTextEquals(peer.host)
            compose.onNodeWithTag("observe_port").assertTextEquals(peer.port.toString())
            compose.onNodeWithTag("observe_route").assertTextEquals(context.getString(R.string.ssh_observe_direct))
            compose.onNodeWithTag("observe_key_format").assertTextEquals(expectedKey.keyFormat)
            assertTrue("Observed digest differs from independent reference",
                compose.onNodeWithTag("observe_fingerprint").fetchSemanticsNode()
                    .config[SemanticsProperties.Text].single().text == expectedKey.fingerprint)
            compose.onNodeWithTag("observe_expected_revision").assertTextEquals("0")
            if (mode == "cancel") {
                compose.onNodeWithTag("observe_dialog_cancel").assertIsEnabled().performClick()
                compose.onNodeWithTag("observe_dialog").assertDoesNotExist()
                assertTrue("Cancel mutated trust", trusts(trustBaseline) == trusts(trust { trustStore.load() }))
                checkNotNull(scenario).recreate()
                settle(); compose.waitForIdle()
                compose.onNodeWithTag("observe_dialog").assertDoesNotExist()
                compose.onNodeWithTag("observe_check").assertIsNotEnabled()
                assertTrue("Recreation mutated trust", trusts(trustBaseline) == trusts(trust { trustStore.load() }))
                receipt.put("observedExpectedKey", true).put("cancelAndRecreatePassed", true)
            } else {
                // Derive the only deletable row from the absent baseline plus independent fixture
                // key, not from whatever a later read happens to return. Persist BEFORE admission.
                val planned = SshTrustSnapshot.create(expectedOwner, trustBaseline.generation, peer, 1,
                    SshTrustStatus.accepted(expectedKey))
                expectedTrust = planned
                val expectedCatalog = trusts(expectedOwner, trustBaseline.generation, trustBaseline.snapshots + planned)
                receipt.put("phase", "TRUST_MUTATION_PLANNED").put("expectedTrustCatalog", expectedCatalog)
                persist()
                if (mode == "stale") {
                    val evaluation = trust { trustStore.evaluate(peer, expectedKey) }
                    check(evaluation is SshTrustEvaluation.Unknown) { "Competing trust baseline changed" }
                    val competing = SshTrustChallenge.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), evaluation)
                    val accepted = trust { trustStore.accept(competing) }
                    assertTrue("Competing CAS differs from planned fixture", accepted == planned)
                    receipt.put("phase", "COMPETING_CAS_COMMITTED"); persist()
                }
                compose.onNodeWithTag("observe_confirm").assertIsEnabled().performClick()
                settle(); compose.waitForIdle()
                compose.onNodeWithTag("observe_dialog").assertDoesNotExist()
                val message = if (mode == "stale") context.getString(R.string.ssh_observe_failure,
                    SshTrustStoreCode.CONFLICT.name) else context.getString(R.string.ssh_observe_saved)
                awaitMessage(message)
                assertTrue("Trust differs from exact planned first-use row",
                    trusts(trust { trustStore.load() }) == expectedCatalog)
                assertTrue("Profile fixture changed during trust approval",
                    profiles(profile { profileStore.load() }) == profiles(baseline.generation, baseline.profiles + fixture))
                if (mode == "save") {
                    checkKey() // Explicit fresh real observation; no old connection continuation.
                    awaitMessage(context.getString(R.string.ssh_observe_match))
                    settle(); compose.waitForIdle()
                    compose.onNodeWithTag("observe_dialog").assertDoesNotExist()
                    assertTrue("Fresh Match rewrote trust", trusts(trust { trustStore.load() }) == expectedCatalog)
                    receipt.put("firstUseSavedThenFreshMatch", true)
                } else receipt.put("staleCasRefusedWithoutOverwrite", true)
                receipt.put("observedExpectedKey", true)
            }
            receipt.put("caseOutcome", "PASSED"); persist()
        } catch (error: Throwable) {
            receipt.put("caseOutcome", "FAILED").put("failureType", error.javaClass.simpleName)
            try { persist() } catch (writeError: Throwable) { error.addSuppressed(writeError) }
            throw error
        } finally {
            // A failed/unknown create is not guessed by endpoint text. Exact baseline-plus-owned
            // comparison must succeed before any deletion; uncertain work preserves the receipt.
            scenario?.close()
            settle()
            val current = profile { profileStore.load() }
            assertTrue("Unexpected profile state; preserve fixtures",
                profiles(baseline.generation, baseline.profiles + listOfNotNull(owned)) == profiles(current))
            // Settlement above excludes queued writes. Permanent lock + exact whole-catalog and
            // metadata checks then permit only baseline or the independently planned owned row.
            receipt.put("phase", "RESTORING_TRUST"); persist()
            trustFixture.removeOnlyExpected(expectedTrust)
            assertTrue("Trust cleanup unconfirmed", trusts(trustBaseline) == trusts(trust { trustStore.load() }))
            owned?.let { fixture ->
                receipt.put("phase", "DELETING"); persist()
                profile { profileStore.delete(ConnectionProfileVersion.create(baseline.generation, fixture.id, fixture.revision)) }
            }
            assertEquals(profiles(baseline), profiles(profile { profileStore.load() }))
            assertTrue("Trust baseline changed", trusts(trustBaseline) == trusts(trust { trustStore.load() }))
            receipt.put("phase", "EXACT_BASELINE_RESTORED"); persist()
            instrumentation.sendStatus(0, Bundle().apply {
                putString("cloudySshObserveCleanup", "EXACT_BASELINE_RESTORED")
            })
        }
    }

    private fun profiles(catalog: ConnectionProfileCatalog): String = profiles(catalog.generation, catalog.profiles)
    private fun profiles(generation: String, values: List<ConnectionProfile>): String = JSONObject()
        .put("generation", generation).put("rows", JSONArray(values.sortedBy { it.id }.map {
            JSONObject().put("id", it.id).put("revision", it.revision).put("owner", it.owner.wireId)
                .put("label", it.label).put("protocol", it.protocol.wireId).put("host", it.host).put("port", it.port)
        })).toString()
    private fun trusts(catalog: SshTrustCatalog): String = trusts(catalog.owner, catalog.generation, catalog.snapshots)
    private fun trusts(owner: WindowOwner, generation: String, values: List<SshTrustSnapshot>): String =
        JSONObject().put("owner", owner.wireId).put("generation", generation).put("rows", JSONArray(values.sortedBy {
            "${it.peer.host}/${it.peer.port}/${it.peer.route.wireId}"
        }.map {
            val status = it.status
            val key = when (status) { is SshTrustStatus.Accepted -> status.key; is SshTrustStatus.Revoked -> status.lastKey }
            JSONObject().put("owner", it.owner.wireId).put("host", it.peer.host).put("port", it.peer.port)
                .put("route", it.peer.route.wireId).put("revision", it.revision)
                .put("revoked", status is SshTrustStatus.Revoked).put("format", key.keyFormat).put("fingerprint", key.fingerprint)
        })).toString()

    private fun checkKey() {
        compose.onNodeWithTag("observe_profiles").performScrollToNode(hasTestTag("observe_check"))
        compose.onNodeWithTag("observe_check").performScrollTo().assertIsEnabled().performClick()
    }
    private fun awaitMessage(expected: String) {
        compose.onNodeWithTag("observe_profiles").performScrollToNode(hasTestTag("observe_message"))
        compose.waitUntil(25_000) {
            val node = compose.onAllNodesWithTag("observe_message").fetchSemanticsNodes().singleOrNull()
            node != null && node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].singleOrNull()?.text == expected
        }
    }

    private fun <T> profile(operation: () -> ConnectionProfilesResult<T>): T {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        do {
            when (val result = operation()) {
                is ConnectionProfilesResult.Success -> return result.value
                is ConnectionProfilesResult.Failure -> check(result.code == ConnectionProfilesCode.BUSY) { "PROFILE_FIXTURE_DENIED" }
            }
            SystemClock.sleep(15)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("PROFILE_FIXTURE_BUSY")
    }
    private fun <T> trust(operation: () -> SshTrustStoreResult<T>): T {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        do {
            when (val result = operation()) {
                is SshTrustStoreResult.Success -> return result.value
                is SshTrustStoreResult.Failure -> check(result.code == SshTrustStoreCode.BUSY) { "TRUST_FIXTURE_DENIED" }
            }
            SystemClock.sleep(15)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("TRUST_FIXTURE_BUSY")
    }
    private fun settle() {
        val field = CloudySshObserveActivity::class.java.getDeclaredField("operations")
        check(Modifier.isStatic(field.modifiers) && field.type == ThreadPoolExecutor::class.java)
        field.isAccessible = true
        val executor = field.get(null) as ThreadPoolExecutor
        check(executor.corePoolSize == 1 && executor.maximumPoolSize == 1 && executor.queue is ArrayBlockingQueue<*>)
        check(executor.queue.size + executor.queue.remainingCapacity() == 16)
        repeat(2) {
            val worker = CountDownLatch(1)
            executor.execute { worker.countDown() }
            check(worker.await(20, TimeUnit.SECONDS)) { "OBSERVE_WORK_UNSETTLED" }
            val main = CountDownLatch(1)
            check(Handler(Looper.getMainLooper()).post { main.countDown() })
            check(main.await(5, TimeUnit.SECONDS)) { "OBSERVE_CALLBACK_UNSETTLED" }
        }
        check(executor.queue.isEmpty()) { "OBSERVE_QUEUE_UNSETTLED" }
    }
}
