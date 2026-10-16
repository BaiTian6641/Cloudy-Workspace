package io.github.baitian6641.cloudy.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustEditToken
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Inspect/revoke existing app-owned pins only; this Activity never enrolls or connects. */
class CloudySshTrustActivity : ComponentActivity() {
    private lateinit var owner: WindowOwner
    private var catalog by mutableStateOf<SshTrustCatalog?>(null)
    private var selected by mutableStateOf<SshTrustSnapshot?>(null)
    private var message by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var pending by mutableStateOf(false)
    private var confirmation by mutableStateOf<Confirmation?>(null)
    private var interactive = false
    private var resumedBefore = false
    private var reloadAfterOperation = false
    private var disposed = false

    private class Confirmation(val snapshot: SshTrustSnapshot, val token: SshTrustEditToken)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        owner = when (packageName) {
            "io.github.baitian6641.cloudy.console" -> WindowOwner.CONSOLE
            "io.github.baitian6641.cloudy.desktop" -> WindowOwner.DESKTOP
            else -> { finish(); return }
        }
        // Only system-delivered saved state is read. Intent extras never select or approve a pin.
        when (val restored = SshTrustUiState.restore(savedInstanceState, owner)) {
            is SshTrustUiRestore.Restored -> {
                pending = restored.pending
                if (pending) message = getString(R.string.ssh_trust_unknown)
            }
            SshTrustUiRestore.Rejected -> message = getString(R.string.ssh_trust_restore_rejected)
            SshTrustUiRestore.Missing -> Unit
        }
        enableEdgeToEdge()
        title = getString(R.string.ssh_trust_heading)
        setContent {
            CloudyAppTheme {
                SshTrustScreen(
                    appName = applicationInfo.loadLabel(packageManager).toString(),
                    catalog = catalog, selected = selected, busy = busy, message = message,
                    uncertain = pending && !busy,
                    onInspect = ::inspect, onRevoke = ::requestRevoke,
                    onRefresh = { if (canInteract()) refresh(explicit = true) },
                    onClose = ::closeManager, onCopyFingerprint = ::copyFingerprint,
                )
                confirmation?.let { request ->
                    AlertDialog(
                        onDismissRequest = { if (confirmation === request) confirmation = null },
                        modifier = Modifier.testTag("trust_dialog"),
                        shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        title = { Text(getString(R.string.ssh_trust_revoke_title), style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                                Text(getString(R.string.ssh_trust_revoke_body), style = MaterialTheme.typography.bodyMedium)
                                // Render the frozen original key, never a token silently refreshed
                                // while the user is deciding whether to revoke it.
                                SshTrustDetails(request.snapshot, fieldTags = false)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val current = confirmation
                                confirmation = null
                                if (current === request && canInteract() && !pending) revoke(request)
                            }, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag("trust_confirm")) {
                                Text(getString(R.string.ssh_trust_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { if (confirmation === request) confirmation = null },
                                shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag("trust_cancel")) {
                                Text(getString(R.string.ssh_trust_cancel))
                            }
                        },
                    )
                }
            }
        }
        // Exactly one initial read; the process queue places it after any admitted predecessor.
        // A restored pending bit remains uncertainty even when this read succeeds.
        refresh(explicit = false)
    }

    override fun onResume() {
        super.onResume()
        if (!::owner.isInitialized || isFinishing) return
        interactive = true
        if (resumedBefore) {
            if (busy) reloadAfterOperation = true else refresh(explicit = false)
        }
        resumedBefore = true
    }

    override fun onPause() {
        interactive = false
        confirmation = null
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // No late callback can admit a mutation after this snapshot without a new resumed UI.
        interactive = false
        confirmation = null
        if (::owner.isInitialized) SshTrustUiState.save(outState, owner, pending)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        disposed = true
        confirmation = null
        super.onDestroy()
    }

    private fun canInteract(): Boolean = interactive && !disposed && !isFinishing && !busy

    private fun refresh(explicit: Boolean, selectPeer: SshPeerIdentity? = null) {
        if (busy || disposed || isFinishing) return
        reloadAfterOperation = false
        confirmation = null
        selected = null
        catalog = null
        val store = SshHostTrustStore(applicationContext)
        dispatch(mutation = false, operation = { store.load() }) { result ->
            when (result) {
                is SshTrustStoreResult.Success -> {
                    catalog = result.value
                    selected = selectPeer?.let { peer -> result.value.snapshots.singleOrNull { it.peer == peer } }
                    if (explicit) {
                        // This acknowledges a fresh inspection, not what caused the saved state.
                        pending = false
                        message = getString(R.string.ssh_trust_refreshed)
                    }
                }
                is SshTrustStoreResult.Failure -> {
                    catalog = null
                    selected = null
                    message = if (pending) getString(R.string.ssh_trust_unknown)
                        else getString(errorResource(result.code))
                }
            }
        }
    }

    private fun inspect(snapshot: SshTrustSnapshot) {
        if (!canInteract()) return
        // The details always belong to the currently loaded immutable catalog, never a launch
        // extra or separately restored selection. Store CAS still detects changes after this read.
        val current = catalog?.snapshots?.singleOrNull { it == snapshot } ?: return
        selected = current
    }

    private fun requestRevoke() {
        if (!canInteract() || pending) return
        val snapshot = selected ?: return
        if (snapshot.status !is SshTrustStatus.Accepted || catalog?.snapshots?.none { it == snapshot } != false) return
        confirmation = Confirmation(snapshot,
            SshTrustEditToken.create(snapshot.owner, snapshot.generation, snapshot.peer, snapshot.revision))
    }

    private fun revoke(request: Confirmation) {
        if (selected != request.snapshot || request.snapshot.status !is SshTrustStatus.Accepted) return
        val store = SshHostTrustStore(applicationContext)
        dispatch(mutation = true, operation = { store.revoke(request.token) }) { result ->
            selected = null
            catalog = null
            when (result) {
                is SshTrustStoreResult.Success -> {
                    message = getString(R.string.ssh_trust_revoked_saved)
                    refresh(explicit = false, selectPeer = result.value.peer)
                }
                is SshTrustStoreResult.Failure -> {
                    // In particular, stale revisions require a fresh read and new visible
                    // confirmation. This callback never replaces the frozen token or retries it.
                    message = getString(errorResource(result.code))
                }
            }
        }
    }

    private fun copyFingerprint() {
        if (!canInteract()) return
        val snapshot = selected ?: return
        if (catalog?.snapshots?.none { it == snapshot } != false) return
        try {
            val clipboard = getSystemService(ClipboardManager::class.java)
            checkNotNull(clipboard).setPrimaryClip(ClipData.newPlainText(
                getString(R.string.ssh_trust_clip_label), trustKey(snapshot.status).fingerprint))
            // The product minimum is API35: Android supplies copy feedback from API33 onward.
            // Do not duplicate it with an app toast, snackbar or unverified success message.
        } catch (_: Exception) { message = getString(R.string.ssh_trust_copy_unavailable) }
    }

    private fun closeManager() {
        confirmation = null
        interactive = false
        // Closing is always available. A queued/admitted revoke retains its immutable token and
        // may still commit; finishing neither cancels that operation nor authorizes a retry.
        finish()
    }

    private fun <T> dispatch(
        mutation: Boolean, operation: () -> SshTrustStoreResult<T>, complete: (SshTrustStoreResult<T>) -> Unit,
    ) {
        val wasPending = pending
        busy = true
        if (mutation) pending = true // Synchronous before queue admission and any state snapshot.
        try {
            operations.execute {
                val result = try { operation() } catch (_: Exception) {
                    SshTrustStoreResult.Failure(if (mutation) SshTrustStoreCode.COMPLETION_UNKNOWN
                        else SshTrustStoreCode.STORE_UNAVAILABLE)
                }
                main.post {
                    if (!disposed && !isFinishing) {
                        busy = false
                        if (mutation) pending = wasPending || result is SshTrustStoreResult.Failure &&
                            result.code == SshTrustStoreCode.COMPLETION_UNKNOWN
                        complete(result)
                        if (reloadAfterOperation && interactive && !busy) refresh(explicit = false)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            busy = false
            if (mutation) pending = wasPending // This submission did not enter the queue.
            message = if (pending) getString(R.string.ssh_trust_unknown) else getString(R.string.ssh_trust_busy)
        }
    }

    private fun errorResource(code: SshTrustStoreCode): Int = when (code) {
        SshTrustStoreCode.WRONG_OWNER -> R.string.ssh_trust_wrong_owner
        SshTrustStoreCode.WRONG_THREAD, SshTrustStoreCode.STORE_UNAVAILABLE -> R.string.ssh_trust_unavailable
        SshTrustStoreCode.BUSY -> R.string.ssh_trust_busy
        SshTrustStoreCode.COMPLETION_UNKNOWN -> R.string.ssh_trust_unknown
        SshTrustStoreCode.STALE_GENERATION, SshTrustStoreCode.CONFLICT, SshTrustStoreCode.NOT_FOUND,
        SshTrustStoreCode.INVALID_STATE -> R.string.ssh_trust_conflict
        SshTrustStoreCode.REVISION_EXHAUSTED -> R.string.ssh_trust_revision_exhausted
        SshTrustStoreCode.CAPACITY -> R.string.ssh_trust_unavailable
    }

    companion object {
        // App-local and bounded across Activity instances/recreation; no service or shared daemon.
        private val operations = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue(16), { action -> Thread(action, "cloudy-ssh-trust").apply { isDaemon = true } })
            .apply { allowCoreThreadTimeOut(true) }
        private val main = Handler(Looper.getMainLooper())
    }
}
