package io.github.baitian6641.cloudy.testing

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.identity.CredentialId
import io.github.baitian6641.cloudy.identity.VaultCode
import io.github.baitian6641.cloudy.identity.VaultPromptCode
import io.github.baitian6641.cloudy.qualification.VaultQualificationActivity
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in real-device qualification, never default CI. Parent must obtain human readiness before
 * invoking save/use/delete: each launches the actual credential prompt. No test-created grants,
 * secret reads, automatic enrollment/deletion, or authentication on Activity restoration.
 */
@RunWith(AndroidJUnit4::class)
class VaultManualQualificationTest {
    @Test fun explicitlySelectedManualOperation() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Manual vault qualification requires explicit opt-in",
            args.getString("cloudyVaultManual") == "enabled")
        val action = args.getString("cloudyVaultAction") ?: "observe"
        demand(action in ACTIONS, "UNKNOWN_ACTION")
        demand(android.os.Looper.myLooper() != android.os.Looper.getMainLooper(), "TEST_MAIN_THREAD")
        val deadline = SystemClock.elapsedRealtime() + MAX_WAIT_MS
        var scenario: ActivityScenario<VaultQualificationActivity>? = null
        val activity = AtomicReference<VaultQualificationActivity?>(null)
        try {
            val running = ActivityScenario.launch(VaultQualificationActivity::class.java)
            scenario = running
            awaitReady(running, activity, deadline)
            val initial = receipt(checkNotNull(activity.get()).qualificationSnapshot())
            val baseline = initial.getJSONArray("events").length()
            demand(baseline > 0, "MISSING_OPEN_EVENT")
            val last = initial.getJSONArray("events").getJSONObject(baseline - 1)
            demand(last.getString("operation") == "OPEN" && last.getString("code") == "METADATA_ONLY",
                "NOT_FRESH_METADATA_OPEN")
            if (action == "observe") {
                running.recreate()
                awaitReady(running, activity, deadline)
                val observed = await(deadline) {
                    val value = receipt(checkNotNull(activity.get()).qualificationSnapshot())
                    val fresh = newEvents(value, baseline)
                    demand(fresh.all { it.getString("operation") == "OPEN" &&
                        it.getString("phase") == "RESULT" && it.getString("code") == "METADATA_ONLY" },
                        "AUTOMATIC_OPERATION_ON_RECREATE")
                    if (fresh.size == 1) value else null
                }
                // A short bounded observation catches queued automatic work; this is not process death.
                demand(SystemClock.elapsedRealtime() + 300 < deadline, "VAULT_MANUAL_TIMEOUT")
                Thread.sleep(300)
                val settled = receipt(checkNotNull(activity.get()).qualificationSnapshot())
                demand(newEvents(settled, baseline).size == 1, "UNEXPECTED_RECREATE_EVENTS")
                demand(settled.getJSONArray("events").toString() == observed.getJSONArray("events").toString(),
                    "AUTOMATIC_OPERATION_AFTER_RECREATE")
                sendReceipt(settled)
            } else {
                running.onActivity {
                    when (action) {
                        "save" -> it.requestSaveFixture()
                        "use" -> it.requestUseFixture()
                        "delete" -> it.requestDeleteFixture()
                        "cleanup" -> it.requestCleanupFixture()
                    }
                }
                var operationId: String? = null
                val result = await(deadline) {
                    val value = receipt(checkNotNull(activity.get()).qualificationSnapshot())
                    val fresh = newEvents(value, baseline)
                    if (fresh.isEmpty()) return@await null
                    if (operationId == null) {
                        operationId = fresh.first().getString("operationId")
                        demand(fresh.first().getString("operation") == action.uppercase(java.util.Locale.ROOT),
                            "UNRELATED_OPERATION")
                    }
                    demand(fresh.all { it.getString("operationId") == operationId }, "AMBIGUOUS_OPERATION")
                    rejectTerminalFailures(fresh)
                    if (satisfied(action, value, fresh)) value else null
                }
                sendReceipt(result)
            }
        } catch (_: Exception) {
            sendBestEffort(activity.get())
            throw AssertionError("VAULT_MANUAL_QUALIFICATION_FAILED")
        } catch (failure: AssertionError) {
            sendBestEffort(activity.get())
            throw failure
        } finally {
            // Real lifecycle cancellation only; no synthetic acknowledgement or automatic deletion.
            scenario?.close()
        }
    }

    private fun awaitReady(scenario: ActivityScenario<VaultQualificationActivity>,
                           activity: AtomicReference<VaultQualificationActivity?>, deadline: Long) {
        await(deadline) {
            var ready = false
            scenario.onActivity {
                activity.set(it)
                // Snapshot publication precedes the main-thread ready callback. Observe the native
                // ready control too so a trigger cannot race and silently be ignored.
                ready = findSaveButton(it.findViewById(android.R.id.content))?.isEnabled == true
            }
            if (ready && checkNotNull(activity.get()).qualificationSnapshot() != "NOT_READY") true else null
        }
    }
    private fun findSaveButton(view: View): Button? {
        if (view is Button && view.text.toString() == "Save fixture") return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            val found = findSaveButton(view.getChildAt(i))
            if (found != null) return found
        }
        return null
    }
    private fun <T : Any> await(deadline: Long, poll: () -> T?): T {
        while (SystemClock.elapsedRealtime() < deadline) {
            poll()?.let { return it }
            Thread.sleep(100)
        }
        throw AssertionError("VAULT_MANUAL_TIMEOUT")
    }
    private fun newEvents(value: JSONObject, baseline: Int): List<JSONObject> {
        val events = value.getJSONArray("events")
        demand(events.length() >= baseline, "RECEIPT_HISTORY_REPLACED")
        return (baseline until events.length()).map { events.getJSONObject(it) }
    }
    private fun rejectTerminalFailures(events: List<JSONObject>) {
        for (event in events) {
            demand(event.getString("code") !in setOf("PROMPT_REJECTED", "STALE", "ALREADY_ATTEMPTED", "REPLAY_FAILED"),
                "TERMINAL_REJECTION")
            if (event.has("storeCode")) {
                val expected = if (event.getString("phase") == "REPLAY") "AUTH_REQUIRED" else "OK"
                demand(event.getString("storeCode") == expected, "STORE_OPERATION_FAILED")
            }
        }
    }
    private fun satisfied(action: String, value: JSONObject, events: List<JSONObject>): Boolean {
        val operation = action.uppercase(java.util.Locale.ROOT)
        val result = events.singleOrNull { it.getString("operation") == operation &&
            it.getString("phase") == "RESULT" && it.getString("code") == "STORE_RESULT" }
        if (result == null) return false
        demand(result.getString("storeCode") == "OK", "STORE_RESULT_NOT_OK")
        if (action == "save" || action == "use") {
            demand(events.count { it.getString("phase") == "REQUEST" } == 1, "MISSING_REQUEST")
            demand(events.count { it.getString("code") == "PROMPT_AUTHORIZED" } == 1, "MISSING_REAL_APPROVAL")
            if (action == "save") {
                demand(result.has("securityLevel") && result.has("authHardware"), "MISSING_ACTUAL_KEY_INFO")
                result.getInt("securityLevel"); result.getBoolean("authHardware")
            } else {
                demand(result.getInt("consumers") == 1 && result.getBoolean("exact"), "EXACT_CONSUMPTION_FAILED")
            }
            val replay = events.singleOrNull { it.getString("phase") == "REPLAY" } ?: return false
            demand(replay.getString("code") == "REPLAY_REJECTED" &&
                replay.getString("storeCode") == "AUTH_REQUIRED" && replay.getInt("consumers") == 0,
                "REPLAY_NOT_REJECTED")
            return true
        }
        if (action == "delete") {
            demand(events.count { it.getString("phase") == "REQUEST" } == 1 &&
                events.count { it.getString("code") == "PROMPT_AUTHORIZED" } == 1, "MISSING_REAL_APPROVAL")
        }
        val purge = events.singleOrNull { it.getString("phase") == "PURGE" } ?: return false
        val absence = events.singleOrNull { it.getString("phase") == "ABSENCE" } ?: return false
        demand(purge.getString("storeCode") == "OK" && absence.getString("storeCode") == "OK", "CLEANUP_FAILED")
        return value.getBoolean("completedDeletion") && events.count {
            it.getString("code") == "CLEANUP_COMPLETE"
        } == 1
    }

    /** Validate every field before forwarding the already-sanitized harness receipt. */
    private fun receipt(text: String): JSONObject {
        demand(text.toByteArray(Charsets.UTF_8).size <= 65_536, "RECEIPT_SIZE")
        val root = JSONObject(text)
        demand(root.keys().asSequence().toSet() == ROOT_FIELDS && root.getInt("schema") == 1, "RECEIPT_SCHEMA")
        CredentialId.parse(root.getString("recordId")); CredentialId.parse(root.getString("accountId"))
        root.getBoolean("saveAttempted"); root.getBoolean("completedDeletion")
        val events = root.getJSONArray("events")
        demand(events.length() <= 96, "EVENT_LIMIT")
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            demand(event.keys().asSequence().toSet().all { it in EVENT_FIELDS }, "EVENT_SCHEMA")
            UUID.fromString(event.getString("operationId")); UUID.fromString(event.getString("processId"))
            demand(event.getLong("elapsedMs") >= 0, "EVENT_CLOCK")
            demand(event.getString("operation") in OPERATIONS && event.getString("phase") in PHASES &&
                event.getString("code") in CODES, "EVENT_CODE")
            if (event.has("storeCode")) VaultCode.valueOf(event.getString("storeCode"))
            if (event.has("promptCode")) VaultPromptCode.valueOf(event.getString("promptCode"))
            if (event.has("consumers")) demand(event.getInt("consumers") in 0..1, "EVENT_COUNTER")
            if (event.has("securityLevel")) demand(event.getInt("securityLevel") in -2..2, "KEY_LEVEL")
            if (event.has("authHardware")) event.getBoolean("authHardware")
            if (event.has("exact")) event.getBoolean("exact")
        }
        return root
    }
    private fun sendReceipt(value: JSONObject) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0,
            Bundle().apply { putString("cloudyVaultReceipt", value.toString()) })
    }
    private fun sendBestEffort(activity: VaultQualificationActivity?) {
        try { if (activity != null) sendReceipt(receipt(activity.qualificationSnapshot())) }
        catch (_: Exception) { /* Never forward unvalidated data or raw exceptions. */ }
        catch (_: AssertionError) { /* Retain the original bounded test failure. */ }
    }
    private fun demand(value: Boolean, code: String) { if (!value) throw AssertionError(code) }
    companion object {
        private const val MAX_WAIT_MS = 150_000L
        private val ACTIONS = setOf("observe", "save", "use", "delete", "cleanup")
        private val ROOT_FIELDS = setOf("schema", "recordId", "accountId", "saveAttempted", "completedDeletion", "events")
        private val EVENT_FIELDS = setOf("operationId", "processId", "elapsedMs", "operation", "phase", "code",
            "storeCode", "promptCode", "consumers", "exact", "securityLevel", "authHardware")
        private val OPERATIONS = setOf("OPEN", "SAVE", "USE", "DELETE", "CLEANUP")
        private val PHASES = setOf("REQUEST", "CALLBACK", "RESULT", "REPLAY", "PURGE", "ABSENCE")
        private val CODES = setOf("METADATA_ONLY", "REQUESTED", "PROMPT_REJECTED", "PROMPT_AUTHORIZED", "STALE",
            "ALREADY_ATTEMPTED", "STORE_RESULT", "REPLAY_REJECTED", "REPLAY_FAILED", "CLEANUP_COMPLETE")
    }
}
