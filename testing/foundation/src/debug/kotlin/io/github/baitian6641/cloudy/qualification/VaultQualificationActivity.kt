package io.github.baitian6641.cloudy.qualification

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AtomicFile
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import io.github.baitian6641.cloudy.identity.CredentialId
import io.github.baitian6641.cloudy.identity.SensitivePayload
import io.github.baitian6641.cloudy.identity.VaultBinding
import io.github.baitian6641.cloudy.identity.VaultCode
import io.github.baitian6641.cloudy.identity.VaultCodec
import io.github.baitian6641.cloudy.identity.VaultGrant
import io.github.baitian6641.cloudy.identity.VaultOperation
import io.github.baitian6641.cloudy.identity.VaultProfile
import io.github.baitian6641.cloudy.identity.VaultProfiles
import io.github.baitian6641.cloudy.identity.VaultPromptController
import io.github.baitian6641.cloudy.identity.VaultPromptResult
import io.github.baitian6641.cloudy.identity.VaultProtocol
import io.github.baitian6641.cloudy.identity.VaultRole
import io.github.baitian6641.cloudy.identity.VaultStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Debug source only: the app must declare this Activity non-exported. Real human credential
 * approval and the production store are mandatory. Fixture literals are not real credentials.
 * No Intent/saved-state commands, network operations, arbitrary aliases, or secret read API.
 */
class VaultQualificationActivity : FragmentActivity() {
    private lateinit var prompts: VaultPromptController
    private lateinit var status: TextView
    private val buttons = mutableListOf<Button>()
    private val handler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0)
    private val alive = AtomicBoolean(true)
    private val pending = AtomicReference<SensitivePayload?>(null)
    private var ownsActivity = false
    private var ready = false
    private var busy = false
    private var id: CredentialId? = null // Main-thread copy of opaque metadata only.
    private var accountId: CredentialId? = null
    private val deadline = SystemClock.elapsedRealtime() + LIFETIME_MS
    private val expiry = Runnable { closeQualification() }

    // Debug-only fixed qualification labels; never shipped or claimed as localized product UI.
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Explicitly ignore incoming extras and saved state as operation/authentication input.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (!active.compareAndSet(false, true)) { finish(); return }
        ownsActivity = true
        prompts = VaultPromptController(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        column.addView(TextView(this).apply {
            text = "DEBUG VAULT QUALIFICATION\nSynthetic fixture only; no connection.\n" +
                "Save, Use and Delete each request real device-credential approval.\n" +
                "This window expires after ten minutes; expiry never deletes a record."
        })
        status = TextView(this)
        column.addView(status)
        addButton(column, "Save fixture", ::requestSaveFixture)
        addButton(column, "Use / compare exact fixture bytes", ::requestUseFixture)
        addButton(column, "Delete fixture", ::requestDeleteFixture)
        addButton(column, "Finish owned local deletion cleanup", ::requestCleanupFixture)
        column.addView(Button(this).apply { text = "Close"; setOnClickListener { closeQualification() } })
        setContentView(ScrollView(this).apply { addView(column) })
        render("Loading owned fixture metadata; no authentication requested.")
        handler.postDelayed(expiry, LIFETIME_MS)
        worker.execute {
            try {
                // All Activity generations append to the same worker-owned object. A late
                // callback must never overwrite a newer generation with a stale disk snapshot.
                check(!receiptUnavailable)
                val data = processMetadata ?: readMetadata().also { processMetadata = it }
                event(UUID.randomUUID(), Op.OPEN, Phase.RESULT, Code.METADATA_ONLY)
                runOnUiThread {
                    if (alive.get() && !isDestroyed) {
                        id = data.id; accountId = data.accountId; ready = true
                        render("Ready. Recovery loaded metadata only; no grant or plaintext restored.")
                    }
                }
            } catch (_: Exception) { reportFailure() }
        }
    }

    /** Parent instrumentation may call explicit triggers on main; none bypasses a real prompt. */
    fun requestSaveFixture() = request(VaultOperation.SAVE)
    fun requestUseFixture() = request(VaultOperation.USE)
    fun requestDeleteFixture() = request(VaultOperation.DELETE)
    fun qualificationSnapshot(): String = snapshot.get()
    fun requestCloseFixture() = closeQualification()

    private fun request(operation: VaultOperation) {
        mainThread()
        if (!ready || busy || !withinLifetime() ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val ownedId = checkNotNull(id)
        val binding = binding(checkNotNull(accountId))
        val token = generation.get()
        val operationId = UUID.randomUUID()
        busy = true
        render("Awaiting actual credential approval for ${operation.name}.")
        // Persist attribution before entering the framework; a receipt failure admits no prompt.
        worker.execute {
            try {
                event(operationId, Op.valueOf(operation.name), Phase.REQUEST, Code.REQUESTED)
                runOnUiThread {
                    if (!current(token)) { complete(token, "Stale operation rejected."); return@runOnUiThread }
                    prompts.request(operation, ownedId, binding) { result ->
                        worker.execute {
                            try {
                                if (result is VaultPromptResult.Rejected) {
                                    event(operationId, Op.valueOf(operation.name), Phase.CALLBACK,
                                        Code.PROMPT_REJECTED, promptCode = result.code.name)
                                    complete(token, "Prompt ended; no authorization delivered.")
                                } else if (result is VaultPromptResult.Authorized) {
                                    event(operationId, Op.valueOf(operation.name), Phase.CALLBACK, Code.PROMPT_AUTHORIZED)
                                    if (!current(token)) {
                                        event(operationId, Op.valueOf(operation.name), Phase.RESULT, Code.STALE)
                                        complete(token, "Stale operation rejected.")
                                    } else {
                                        execute(operationId, operation, ownedId, binding, result.grant, token)
                                    }
                                }
                            } catch (_: Exception) { reportFailure() }
                        }
                    }
                }
            } catch (_: Exception) { reportFailure() }
        }
    }

    private fun execute(operationId: UUID, operation: VaultOperation, ownedId: CredentialId,
                        binding: VaultBinding, grant: VaultGrant, token: Long) {
        check(!receiptUnavailable)
        val store = VaultStorage(applicationContext)
        val data = checkNotNull(processMetadata)
        val op = Op.valueOf(operation.name)
        when (operation) {
            VaultOperation.SAVE -> {
                // A record is allocated durably before creating a key. Never retry/adopt a prior save.
                if (data.saveAttempted) {
                    event(operationId, op, Phase.RESULT, Code.ALREADY_ATTEMPTED)
                    complete(token, "Save already attempted; retain the record for explicit cleanup.")
                    return
                }
                data.saveAttempted = true
                persist(data)
                VaultProfiles(applicationContext).put(VaultProfile(ownedId, binding))
                val value = fixture()
                pending.set(value)
                val result = try {
                    if (!current(token)) {
                        value.close()
                        event(operationId, op, Phase.RESULT, Code.STALE)
                        complete(token, "Stale operation rejected.")
                        return
                    }
                    store.create(ownedId, binding, grant, value)
                } finally { pending.compareAndSet(value, null); value.close() }
                event(operationId, op, Phase.RESULT, Code.STORE_RESULT, storeCode = result.code,
                    securityLevel = result.keyInfo?.securityLevel,
                    authHardware = result.keyInfo?.authenticationInHardware)
                val replayPayload = fixture()
                val replay = try { store.create(ownedId, binding, grant, replayPayload) }
                    finally { replayPayload.close() }
                event(operationId, op, Phase.REPLAY,
                    if (replay.code == VaultCode.AUTH_REQUIRED) Code.REPLAY_REJECTED else Code.REPLAY_FAILED,
                    storeCode = replay.code, consumers = 0)
                complete(token, "SAVE ${result.code.name}; replay ${replay.code.name}.")
            }
            VaultOperation.USE -> {
                var consumers = 0
                var exact = false
                val result = store.use(ownedId, binding, grant) { decoded ->
                    consumers++
                    check(current(token))
                    val actual = VaultCodec.encodePayload(decoded)
                    try {
                        val expected = fixture().use { VaultCodec.encodePayload(it) }
                        try { exact = actual.contentEquals(expected) } finally { expected.fill(0) }
                    } finally { actual.fill(0) }
                }
                event(operationId, op, Phase.RESULT, Code.STORE_RESULT, storeCode = result.code,
                    consumers = consumers, exact = exact)
                var secondConsumers = 0
                val replay = store.use(ownedId, binding, grant) { secondConsumers++ }
                event(operationId, op, Phase.REPLAY,
                    if (replay.code == VaultCode.AUTH_REQUIRED && secondConsumers == 0)
                        Code.REPLAY_REJECTED else Code.REPLAY_FAILED,
                    storeCode = replay.code, consumers = secondConsumers)
                complete(token, "USE ${result.code.name}; exact=$exact; consumers=$consumers; replay ${replay.code.name}.")
            }
            VaultOperation.DELETE -> {
                val result = store.delete(ownedId, binding, grant)
                event(operationId, op, Phase.RESULT, Code.STORE_RESULT, storeCode = result.code)
                if (result.code == VaultCode.OK) finishDeletion(operationId, store, ownedId)
                complete(token, "DELETE ${result.code.name}; consult cleanup receipt.")
            }
        }
    }

    fun requestCleanupFixture() {
        mainThread()
        if (!ready || busy || !withinLifetime() ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        busy = true
        render("Checking only this fixture's explicit deletion state.")
        val token = generation.get()
        val operationId = UUID.randomUUID()
        val ownedId = checkNotNull(id)
        worker.execute {
            try {
                check(!receiptUnavailable)
                val store = VaultStorage(applicationContext)
                val result = store.retryPendingDeletion(ownedId)
                event(operationId, Op.CLEANUP, Phase.RESULT, Code.STORE_RESULT, storeCode = result.code)
                if (result.code == VaultCode.OK) finishDeletion(operationId, store, ownedId)
                complete(token, "Local cleanup ${result.code.name}; no prompt or decryption.")
            } catch (_: Exception) { reportFailure() }
        }
    }

    private fun finishDeletion(operationId: UUID, store: VaultStorage, ownedId: CredentialId) {
        val purge = store.purgeCompletedDeletion(ownedId)
        event(operationId, Op.CLEANUP, Phase.PURGE, Code.STORE_RESULT, storeCode = purge.code)
        if (purge.code != VaultCode.OK) return
        val absent = store.verifyAbsent(ownedId)
        event(operationId, Op.CLEANUP, Phase.ABSENCE, Code.STORE_RESULT, storeCode = absent.code)
        if (absent.code == VaultCode.OK) {
            // Never enumerate or delete another profile/key. Enrollment is serialized by this actor.
            VaultProfiles(applicationContext).remove(ownedId)
            checkNotNull(processMetadata).completedDeletion = true
            event(operationId, Op.CLEANUP, Phase.RESULT, Code.CLEANUP_COMPLETE)
        }
    }

    override fun onStop() {
        generation.incrementAndGet()
        pending.getAndSet(null)?.close()
        if (::prompts.isInitialized) prompts.cancel()
        super.onStop()
    }
    override fun onDestroy() {
        alive.set(false)
        generation.incrementAndGet()
        handler.removeCallbacks(expiry)
        pending.getAndSet(null)?.close()
        if (::prompts.isInitialized) prompts.cancel()
        if (ownsActivity) active.set(false)
        super.onDestroy()
    }
    private fun closeQualification() {
        mainThread()
        alive.set(false)
        generation.incrementAndGet()
        pending.getAndSet(null)?.close()
        if (::prompts.isInitialized) prompts.cancel()
        finish()
    }
    private fun withinLifetime(): Boolean = alive.get() && SystemClock.elapsedRealtime() < deadline
    private fun current(token: Long): Boolean = generation.get() == token && withinLifetime()
    private fun addButton(column: LinearLayout, label: String, action: () -> Unit) {
        column.addView(Button(this).apply { text = label; setOnClickListener { action() } }.also { buttons.add(it) })
    }
    private fun render(message: String) {
        status.text = message
        buttons.forEach { it.isEnabled = ready && !busy && withinLifetime() }
    }
    private fun complete(token: Long, message: String) = runOnUiThread {
        // A stop revokes this operation but must not strand the same returning Activity as busy.
        if (alive.get() && !isDestroyed) {
            busy = false
            render(if (current(token)) message else "Operation cancelled by lifecycle; no automatic retry.")
        }
    }
    private fun reportFailure() {
        // Called only on the serial worker: malformed/failed persistence poisons this
        // process's receipt owner. Never reload or overwrite a damaged receipt implicitly.
        receiptUnavailable = true
        runOnUiThread {
            if (alive.get() && !isDestroyed) {
                busy = false
                ready = false
                render("QUALIFICATION_UNAVAILABLE; close and retain evidence; no automatic retry.")
            }
        }
    }
    private fun binding(account: CredentialId) = VaultBinding.create(packageName, VaultProtocol.SSH,
        "127.0.0.1", 22, VaultRole.TARGET, account.value)
    private fun fixture(): SensitivePayload {
        val user = "cloudy-fixture-user".toCharArray()
        val domain = "cloudy-fixture-domain".toCharArray()
        val password = "CLOUDY_SYNTHETIC_FIXTURE_1234".toCharArray()
        return try { SensitivePayload.create(user, domain, password) }
        finally { user.fill('\u0000'); domain.fill('\u0000'); password.fill('\u0000') }
    }

    private data class Metadata(val id: CredentialId, val accountId: CredentialId,
                                var saveAttempted: Boolean, var completedDeletion: Boolean,
                                val events: JSONArray)
    private enum class Op { OPEN, SAVE, USE, DELETE, CLEANUP }
    private enum class Phase { REQUEST, CALLBACK, RESULT, REPLAY, PURGE, ABSENCE }
    private enum class Code { METADATA_ONLY, REQUESTED, PROMPT_REJECTED, PROMPT_AUTHORIZED,
        STALE, ALREADY_ATTEMPTED, STORE_RESULT, REPLAY_REJECTED, REPLAY_FAILED, CLEANUP_COMPLETE }

    private fun receipt(): AtomicFile = AtomicFile(File(applicationContext.noBackupFilesDir,
        "cloudy-vault-qualification-v1/receipt.json"))
    private fun readMetadata(): Metadata {
        val file = receipt()
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            check(!File(file.baseFile.path + ".new").exists())
            return Metadata(CredentialId.create(), CredentialId.create(), false, false, JSONArray()).also(::persist)
        }
        val bytes = file.openRead().use { it.readNBytes(MAX_RECEIPT_BYTES + 1) }
        check(bytes.size <= MAX_RECEIPT_BYTES)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        check(root.keys().asSequence().toSet() == ROOT_FIELDS && root.getInt("schema") == 1)
        val data = Metadata(CredentialId.parse(root.getString("recordId")),
            CredentialId.parse(root.getString("accountId")), root.getBoolean("saveAttempted"),
            root.getBoolean("completedDeletion"), root.getJSONArray("events"))
        check(data.events.length() < MAX_EVENTS)
        for (i in 0 until data.events.length()) validateEvent(data.events.getJSONObject(i))
        return data
    }
    private fun event(operationId: UUID, operation: Op, phase: Phase, code: Code,
                      storeCode: VaultCode? = null, promptCode: String? = null,
                      consumers: Int? = null, exact: Boolean? = null,
                      securityLevel: Int? = null, authHardware: Boolean? = null) {
        check(!receiptUnavailable)
        val data = checkNotNull(processMetadata)
        check(data.events.length() < MAX_EVENTS)
        val value = JSONObject().put("operationId", operationId.toString())
            .put("processId", processId.toString()).put("elapsedMs", SystemClock.elapsedRealtime())
            .put("operation", operation.name).put("phase", phase.name).put("code", code.name)
        if (storeCode != null) value.put("storeCode", storeCode.name)
        if (promptCode != null) value.put("promptCode", promptCode)
        if (consumers != null) value.put("consumers", consumers)
        if (exact != null) value.put("exact", exact)
        if (securityLevel != null) value.put("securityLevel", securityLevel)
        if (authHardware != null) value.put("authHardware", authHardware)
        validateEvent(value)
        data.events.put(value)
        persist(data)
    }
    private fun validateEvent(value: JSONObject) {
        check(value.keys().asSequence().toSet().all { it in EVENT_FIELDS })
        UUID.fromString(value.getString("operationId")); UUID.fromString(value.getString("processId"))
        check(value.getLong("elapsedMs") >= 0)
        Op.valueOf(value.getString("operation")); Phase.valueOf(value.getString("phase")); Code.valueOf(value.getString("code"))
        if (value.has("storeCode")) VaultCode.valueOf(value.getString("storeCode"))
        if (value.has("promptCode")) io.github.baitian6641.cloudy.identity.VaultPromptCode.valueOf(value.getString("promptCode"))
        if (value.has("consumers")) check(value.getInt("consumers") in 0..1)
        if (value.has("exact")) value.getBoolean("exact")
        if (value.has("securityLevel")) check(value.getInt("securityLevel") in -2..2)
        if (value.has("authHardware")) value.getBoolean("authHardware")
    }
    private fun persist(data: Metadata) {
        check(!receiptUnavailable)
        val text = JSONObject().put("schema", 1).put("recordId", data.id.value)
            .put("accountId", data.accountId.value).put("saveAttempted", data.saveAttempted)
            .put("completedDeletion", data.completedDeletion).put("events", data.events).toString()
        val bytes = text.toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_RECEIPT_BYTES)
        val file = receipt()
        check(file.baseFile.parentFile!!.isDirectory || file.baseFile.parentFile!!.mkdirs())
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
        val readback = file.openRead().use { it.readNBytes(MAX_RECEIPT_BYTES + 1) }
        check(bytes.contentEquals(readback))
        snapshot.set(text)
    }
    companion object {
        private const val LIFETIME_MS = 600_000L
        private const val MAX_EVENTS = 96
        private const val MAX_RECEIPT_BYTES = 65_536
        private val processId = UUID.randomUUID()
        private val active = AtomicBoolean(false)
        private val worker = Executors.newSingleThreadExecutor()
        // Access exclusively on worker. No grants, callbacks, payloads or authorization live here.
        private var processMetadata: Metadata? = null
        private val snapshot = AtomicReference("NOT_READY")
        private var receiptUnavailable = false
        private val ROOT_FIELDS = setOf("schema", "recordId", "accountId", "saveAttempted", "completedDeletion", "events")
        private val EVENT_FIELDS = setOf("operationId", "processId", "elapsedMs", "operation", "phase", "code",
            "storeCode", "promptCode", "consumers", "exact", "securityLevel", "authHardware")
        private fun mainThread() = check(Looper.myLooper() == Looper.getMainLooper())
    }
}
