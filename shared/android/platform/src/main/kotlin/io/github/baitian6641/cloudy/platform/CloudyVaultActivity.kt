package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.identity.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Local credential operations only. No remote connection or saved authorization. */
open class CloudyVaultActivity : FragmentActivity() {
    private lateinit var prompts: VaultPromptController
    private var generation = 0L
    private var pending: SensitivePayload? = null
    private var username by mutableStateOf("")
    private var domain by mutableStateOf("")
    private var password by mutableStateOf("")
    private var host by mutableStateOf("")
    private var port by mutableStateOf("")
    private var protocol by mutableStateOf(VaultProtocol.SSH)
    private var role by mutableStateOf(VaultRole.TARGET)
    private var message by mutableStateOf("")
    private var rows by mutableStateOf<List<Row>>(emptyList())
    private var busy by mutableStateOf(false)
    private var confirmation by mutableStateOf<Row?>(null)
    private data class Row(val id: CredentialId, val profile: VaultProfile?, val state: VaultRecordState?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        prompts = VaultPromptController(this)
        setContent { CloudyAppTheme { Screen() } }
    }

    override fun onStart() { super.onStart(); refresh() }
    override fun onStop() {
        generation++
        clearSecrets()
        pending?.close(); pending = null
        confirmation = null
        prompts.cancel()
        super.onStop()
    }
    override fun onDestroy() { clearSecrets(); pending?.close(); pending = null; super.onDestroy() }
    private fun clearSecrets() { username = ""; domain = ""; password = "" }

    private fun work(action: () -> String) {
        if (!occupied.compareAndSet(false, true)) {
            message = getString(R.string.vault_busy); return
        }
        busy = true
        val token = generation
        worker.execute {
            val result = try { action() } catch (_: Exception) { getString(R.string.vault_unavailable) }
            occupied.set(false)
            runOnUiThread {
                busy = false
                if (token == generation && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) message = result
            }
        }
    }

    private fun refresh() {
        val token = generation
        work {
        val profiles = VaultProfiles(applicationContext).list()
        val inspection = VaultStorage(applicationContext).inspect()
        check(inspection.code == VaultCode.OK)
        val next = (profiles.map { it.id } + inspection.records.map { it.id }).distinct().map { id ->
            Row(id, profiles.find { it.id == id }, inspection.records.find { it.id == id }?.state)
        }
        runOnUiThread { if (token == generation && !isDestroyed) rows = next }
        getString(R.string.vault_refreshed)
        }
    }

    private fun authorize(operation: VaultOperation, profile: VaultProfile, payload: SensitivePayload? = null) {
        if (!occupied.compareAndSet(false, true)) { payload?.close(); message = getString(R.string.vault_busy); return }
        busy = true
        val token = generation
        pending = payload
        prompts.request(operation, profile.id, profile.binding) { result ->
            if (result !is VaultPromptResult.Authorized || token != generation) {
                payload?.close(); if (pending === payload) pending = null
                occupied.set(false); busy = false
                if (token == generation && !isDestroyed) message = getString(R.string.vault_authorization_ended)
            } else {
                worker.execute {
                    val outcome = try {
                        val store = VaultStorage(applicationContext)
                        val profiles = VaultProfiles(applicationContext)
                        val value = when (operation) {
                            VaultOperation.SAVE -> {
                                profiles.put(profile)
                                store.create(profile.id, profile.binding, result.grant, checkNotNull(payload))
                            }
                            VaultOperation.USE -> store.use(profile.id, profile.binding, result.grant) { decoded ->
                                // The real store has authenticated and decoded the payload.
                                // No plaintext leaves this consumer or becomes UI feedback.
                                decoded.close()
                            }
                            VaultOperation.DELETE -> store.delete(profile.id, profile.binding, result.grant)
                        }
                        getString(R.string.vault_operation_result, value.code.name)
                    } catch (_: Exception) { getString(R.string.vault_unavailable) }
                    finally { payload?.close(); occupied.set(false) }
                    runOnUiThread {
                        if (pending === payload) pending = null
                        busy = false
                        if (token == generation && !isDestroyed) message = outcome
                    }
                }
            }
        }
    }

    private fun enroll() {
        var payload: SensitivePayload? = null
        val user = username.toCharArray(); val accountDomain = domain.toCharArray(); val secret = password.toCharArray()
        try {
            val binding = VaultBinding.create(packageName, protocol, host, port.toInt(), role, CredentialId.create().value)
            payload = SensitivePayload.create(user, accountDomain, secret)
            clearSecrets()
            authorize(VaultOperation.SAVE, VaultProfile(CredentialId.create(), binding), payload)
        } catch (_: Exception) { payload?.close(); message = getString(R.string.vault_invalid) }
        finally { user.fill('\u0000'); accountDomain.fill('\u0000'); secret.fill('\u0000') }
    }

    @Composable private fun Screen() {
        LocalView.current.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState()).padding(CloudyControlMetrics.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                Text(stringResource(R.string.vault_heading), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.vault_local_only), style = MaterialTheme.typography.bodyMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    VaultProtocol.entries.forEach { value -> FilterChip(protocol == value, { protocol = value }, { Text(value.name) }, enabled = !busy,
                        shape = RoundedCornerShape(CloudyControlMetrics.chipRadius),
                        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) }
                }
                Field(R.string.vault_host, host, 1024) { host = it }
                Field(R.string.vault_port, port, 5) { port = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    VaultRole.entries.forEach { value -> FilterChip(role == value, { role = value }, { Text(stringResource(if (value == VaultRole.TARGET) R.string.vault_target else R.string.vault_gateway)) }, enabled = !busy,
                        shape = RoundedCornerShape(CloudyControlMetrics.chipRadius),
                        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) }
                }
                Field(R.string.vault_username, username, SensitivePayload.MAX_IDENTITY_CHARS) { username = it }
                Field(R.string.vault_domain, domain, SensitivePayload.MAX_IDENTITY_CHARS) { domain = it }
                Field(R.string.vault_password, password, SensitivePayload.MAX_PASSWORD_CHARS, true) { password = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Button(::enroll, enabled = !busy, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) { Text(stringResource(R.string.vault_save)) }
                    OutlinedButton({ prompts.cancel(); pending?.close(); pending = null },
                        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) { Text(stringResource(R.string.vault_cancel)) }
                    OutlinedButton(::refresh, enabled = !busy, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) { Text(stringResource(R.string.vault_refresh)) }
                }
                rows.forEach { row ->
                    HorizontalDivider()
                    val binding = row.profile?.binding
                    Text(if (binding == null) getString(R.string.vault_missing_profile, row.id.value.take(8))
                        else "${binding.protocol.name} · ${binding.host}:${binding.port} · ${binding.role.name} · ${row.id.value.take(8)}",
                        style = MaterialTheme.typography.bodyLarge)
                    Text(row.state?.name ?: stringResource(R.string.vault_missing_record), style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        OutlinedButton({ row.profile?.let { authorize(VaultOperation.USE, it) } },
                            enabled = !busy && row.profile != null && row.state == VaultRecordState.READY,
                            shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                            modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) { Text(stringResource(R.string.vault_check)) }
                        OutlinedButton({ confirmation = row }, enabled = !busy && row.profile != null && row.state != null,
                            shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                            modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) { Text(stringResource(R.string.vault_delete)) }
                        if (row.profile != null && row.state == null) {
                            OutlinedButton(onClick = {
                                work {
                                    // The same process admission gate and serial worker own
                                    // enrollment and removal: no UI create can race the
                                    // verified absence snapshot and metadata-only removal.
                                    val absence = VaultStorage(applicationContext).verifyAbsent(row.id)
                                    if (absence.code == VaultCode.OK) {
                                        VaultProfiles(applicationContext).remove(row.id)
                                    }
                                    getString(R.string.vault_operation_result, absence.code.name)
                                }
                            }, enabled = !busy, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                                Text(stringResource(R.string.vault_remove_unused))
                            }
                        }
                        if (row.state == VaultRecordState.DELETE_PENDING || row.state == VaultRecordState.DELETED) {
                            OutlinedButton({ work {
                                val store = VaultStorage(applicationContext)
                                val result = store.retryPendingDeletion(row.id)
                                if (result.code == VaultCode.OK) {
                                    val purge = store.purgeCompletedDeletion(row.id)
                                    if (purge.code == VaultCode.OK) VaultProfiles(applicationContext).remove(row.id)
                                    getString(R.string.vault_operation_result, purge.code.name)
                                } else getString(R.string.vault_operation_result, result.code.name)
                            } }, enabled = !busy, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) { Text(stringResource(R.string.vault_cleanup)) }
                        }
                    }
                }
            }
        }
        confirmation?.let { row -> AlertDialog(onDismissRequest = { confirmation = null },
            shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.vault_delete), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Text((row.profile?.binding?.let { "${it.protocol.name} · ${it.host}:${it.port} · ${it.role.name}\n" } ?: "") + stringResource(R.string.vault_delete_warning),
                        style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton({ confirmation = null; row.profile?.let { authorize(VaultOperation.DELETE, it) } },
                shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) { Text(stringResource(R.string.vault_delete)) } },
            dismissButton = { TextButton({ confirmation = null }, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) { Text(stringResource(R.string.vault_keep)) } }) }
    }

    @Composable private fun Field(label: Int, value: String, limit: Int, secret: Boolean = false, change: (String) -> Unit) {
        OutlinedTextField(value, { if (it.length <= limit) change(it) else message = getString(R.string.vault_too_long) },
            label = { Text(stringResource(label)) }, singleLine = true, enabled = !busy,
            textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth())
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor()
        private val occupied = AtomicBoolean(false)
    }
}
