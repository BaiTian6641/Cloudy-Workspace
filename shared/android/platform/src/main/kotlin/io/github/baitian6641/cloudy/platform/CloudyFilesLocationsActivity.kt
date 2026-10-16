package io.github.baitian6641.cloudy.platform

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.domain.FilesResourceState
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Own-app metadata manager. Registration comes only from the authenticated listing screen. */
class CloudyFilesLocationsActivity : ComponentActivity() {
    private lateinit var owner: WindowOwner
    private var catalog by mutableStateOf<FilesResourceCatalog?>(null)
    private var selected by mutableStateOf<FilesResourceRegistration?>(null)
    private var confirmation by mutableStateOf<FilesLocationConfirmation?>(null)
    private var busy by mutableStateOf(false)
    private var pending by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var disposed = false
    private var resumed = false
    private var resumedBefore = false
    private var reloadAfterOperation = false
    private val admission = Any()
    // Main lifecycle updates and worker admission share only this short gate; SQLite never does.
    private var foreground = false
    private var epoch = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        owner = when (packageName) {
            "io.github.baitian6641.cloudy.console" -> WindowOwner.CONSOLE
            "io.github.baitian6641.cloudy.desktop" -> WindowOwner.DESKTOP
            else -> { finish(); return }
        }
        if (savedInstanceState != null) {
            val restored = try {
                val value = savedInstanceState.getBundle(STATE)
                if (value == null) null else {
                    @Suppress("DEPRECATION")
                    val valid = value.keySet() == setOf("schema", "owner", "pending") &&
                        value.get("schema") == 1 && value.get("owner") == owner.wireId && value.get("pending") is Boolean
                    if (valid) value.getBoolean("pending") else null
                }
            } catch (_: Exception) { null }
            if (restored == null) message = getString(R.string.files_locations_restore_rejected)
            else {
                pending = restored
                if (pending) message = getString(R.string.files_locations_unknown)
            }
        }
        enableEdgeToEdge()
        title = getString(R.string.files_locations_heading)
        setContent {
            CloudyAppTheme {
                FilesLocationsScreen(
                    applicationInfo.loadLabel(packageManager).toString(),
                    getString(if (owner == WindowOwner.CONSOLE) R.string.files_locations_counterpart_desktop
                        else R.string.files_locations_counterpart_console), catalog, selected, confirmation,
                    busy, pending, message,
                    onInspect = ::inspect,
                    onShare = { propose(FilesLocationAction.SHARE) },
                    onRevoke = { propose(FilesLocationAction.REVOKE) },
                    onConfirm = ::confirm,
                    onCancel = { confirmation = null },
                    onOpen = ::openLocation,
                    onRefresh = { if (canInteract()) refresh(explicit = true) },
                    onClose = { invalidate(); finish() },
                )
            }
        }
        refresh(explicit = false)
    }

    override fun onResume() {
        super.onResume()
        if (!::owner.isInitialized || isFinishing) return
        resumed = true
        updateForeground(hasWindowFocus())
        if (resumedBefore) {
            if (busy) reloadAfterOperation = true else refresh(explicit = false)
        }
        resumedBefore = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        updateForeground(resumed && hasFocus && !isFinishing)
        if (!hasFocus) confirmation = null
    }

    override fun onPause() {
        resumed = false
        invalidate()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        resumed = false
        invalidate()
        if (::owner.isInitialized) outState.putBundle(STATE, Bundle().apply {
            putInt("schema", 1); putString("owner", owner.wireId); putBoolean("pending", pending)
        })
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        disposed = true
        invalidate()
        super.onDestroy()
    }

    private fun updateForeground(value: Boolean) = synchronized(admission) {
        if (foreground != value) { foreground = value; epoch++ }
    }

    private fun invalidate() {
        updateForeground(false)
        confirmation = null
    }

    private fun canInteract(): Boolean = resumed && hasWindowFocus() && !disposed && !isFinishing && !busy

    private fun inspect(row: FilesResourceRegistration) {
        if (!canInteract() || confirmation != null) return
        selected = catalog?.registrations?.singleOrNull { it == row }
    }

    private fun propose(action: FilesLocationAction) {
        if (!canInteract() || pending) return
        val row = selected ?: return
        if (catalog?.registrations?.none { it == row } != false || row.state == FilesResourceState.REVOKED ||
            action == FilesLocationAction.SHARE && row.state != FilesResourceState.PRIVATE) return
        updateForeground(true)
        confirmation = FilesLocationConfirmation(row, action, synchronized(admission) { epoch })
    }

    private fun confirm(request: FilesLocationConfirmation) {
        if (confirmation !== request || !canInteract() || pending || selected != request.registration) return
        confirmation = null
        val store = FilesResourceStore(applicationContext)
        dispatch(guardEpoch = request.epoch, changesState = true, operation = {
            when (request.action) {
                FilesLocationAction.SHARE -> store.share(request.registration.version())
                FilesLocationAction.REVOKE -> store.revoke(request.registration.version())
            }
        }) { result ->
            selected = null
            catalog = null
            when (result) {
                is FilesResourceResult.Success -> {
                    message = getString(if (request.action == FilesLocationAction.SHARE)
                        R.string.files_locations_shared_saved else R.string.files_locations_revoked_saved)
                    refresh(explicit = false, selectId = result.value.reference.resourceId)
                }
                is FilesResourceResult.Failure -> message = getString(errorResource(result.code))
            }
        }
    }

    private fun openLocation() {
        if (!canInteract() || pending || confirmation != null) return
        val row = selected ?: return
        if (row.state == FilesResourceState.REVOKED || catalog?.registrations?.none { it == row } != false) return
        updateForeground(true)
        val expectedEpoch = synchronized(admission) { epoch }
        val context = applicationContext
        dispatch(guardEpoch = expectedEpoch, changesState = true,
            operation = { FilesLocationTickets.issue(context, row) }) { result ->
            when (result) {
                is FilesResourceResult.Success -> {
                    // Issuance is not delivery. An old Activity cannot launch after foreground loss.
                    val current = synchronized(admission) { foreground && epoch == expectedEpoch }
                    if (current && canInteract()) {
                        try {
                            startActivity(Intent(this, CloudySshFilesActivity::class.java)
                                .putExtra(CloudySshFilesActivity.LOCATION_TICKET, result.value))
                            message = getString(R.string.files_locations_open_requested)
                        } catch (_: Exception) { message = getString(R.string.files_locations_open_unavailable) }
                    } else {
                        pending = true
                        message = getString(R.string.files_locations_unknown)
                    }
                }
                is FilesResourceResult.Failure -> message = getString(errorResource(result.code))
            }
        }
    }

    private fun refresh(explicit: Boolean, selectId: String? = null) {
        if (busy || disposed || isFinishing) return
        reloadAfterOperation = false
        confirmation = null
        selected = null
        catalog = null
        val store = FilesResourceStore(applicationContext)
        dispatch(operation = { store.load() }) { result ->
            when (result) {
                is FilesResourceResult.Success -> {
                    catalog = result.value
                    selected = result.value.registrations.singleOrNull { it.reference.resourceId == selectId }
                    if (explicit) {
                        pending = false
                        message = getString(R.string.files_locations_refreshed)
                    }
                }
                is FilesResourceResult.Failure -> message = getString(errorResource(result.code))
            }
        }
    }

    private fun <T> dispatch(
        guardEpoch: Long? = null, changesState: Boolean = false,
        operation: () -> FilesResourceResult<T>, complete: (FilesResourceResult<T>) -> Unit,
    ) {
        val wasPending = pending
        busy = true
        if (changesState) pending = true // Before enqueue/saved-state capture, never a replay token.
        try {
            operations.execute {
                val admitted = guardEpoch == null || synchronized(admission) { foreground && epoch == guardEpoch }
                val result = if (!admitted) FilesResourceResult.Failure(FilesResourceCode.INVALID_STATE)
                else try { operation() } catch (_: Exception) {
                    FilesResourceResult.Failure(if (changesState) FilesResourceCode.COMPLETION_UNKNOWN
                        else FilesResourceCode.STORE_UNAVAILABLE)
                }
                main.post {
                    if (!disposed && !isFinishing) {
                        busy = false
                        if (changesState) {
                            val departed = guardEpoch != null && synchronized(admission) { epoch != guardEpoch || !foreground }
                            pending = wasPending || admitted && departed ||
                                result is FilesResourceResult.Failure && result.code == FilesResourceCode.COMPLETION_UNKNOWN
                        }
                        complete(result)
                        if (reloadAfterOperation && resumed && !busy) refresh(explicit = false)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            busy = false
            pending = wasPending
            message = getString(if (pending) R.string.files_locations_unknown else R.string.files_locations_busy)
        }
    }

    private fun errorResource(code: FilesResourceCode): Int = when (code) {
        FilesResourceCode.COMPLETION_UNKNOWN -> R.string.files_locations_unknown
        FilesResourceCode.BUSY -> R.string.files_locations_busy
        FilesResourceCode.STALE_GENERATION, FilesResourceCode.CONFLICT, FilesResourceCode.NOT_FOUND,
        FilesResourceCode.INVALID_STATE, FilesResourceCode.STALE_BINDING -> R.string.files_locations_conflict
        FilesResourceCode.REVISION_EXHAUSTED -> R.string.files_locations_revision_exhausted
        FilesResourceCode.INVALID_INPUT, FilesResourceCode.WRONG_OWNER, FilesResourceCode.WRONG_THREAD,
        FilesResourceCode.STORE_UNAVAILABLE, FilesResourceCode.CAPACITY -> R.string.files_locations_unavailable
    }

    companion object {
        private const val STATE = "cloudy-files-locations-v1"
        private val operations = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue(16), { action -> Thread(action, "cloudy-files-locations").apply { isDaemon = true } })
            .apply { allowCoreThreadTimeOut(true) }
        private val main = Handler(Looper.getMainLooper())
    }
}

internal enum class FilesLocationAction { SHARE, REVOKE }
internal class FilesLocationConfirmation(
    val registration: FilesResourceRegistration, val action: FilesLocationAction, val epoch: Long,
)
