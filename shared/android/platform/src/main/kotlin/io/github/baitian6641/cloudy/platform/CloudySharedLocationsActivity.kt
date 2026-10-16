package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import io.github.baitian6641.cloudy.domain.FilesSharedDescriptor
import io.github.baitian6641.cloudy.domain.WindowOwner

/** Explicit visible sender only. No automatic discovery, submit, credentials or restoration replay. */
class CloudySharedLocationsActivity : ComponentActivity() {
    private lateinit var owner: WindowOwner
    private lateinit var client: FilesHandoffClient
    private var page by mutableStateOf<FilesDiscoveryReply?>(null)
    private var selected by mutableStateOf<FilesSharedDescriptor?>(null)
    private var message by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var pending by mutableStateOf(false)
    private var resumed by mutableStateOf(false)
    private var disposed = false
    private var operation: Operation? = null
    private var pendingBeforeOperation = false
    private enum class Operation { REFRESH, NEXT, SUBMIT }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        owner = when (packageName) {
            "io.github.baitian6641.cloudy.console" -> WindowOwner.CONSOLE
            "io.github.baitian6641.cloudy.desktop" -> WindowOwner.DESKTOP
            else -> { finish(); return }
        }
        FilesPositiveObservation.start(this, FilesPositiveObservation.Event.SENDER_CREATED, this)
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
            pending = restored ?: true
            message = getString(if (restored == null) R.string.shared_locations_restore_rejected
                else if (pending) R.string.shared_locations_unknown else R.string.shared_locations_explicit_refresh)
        }
        // Client owns actual synchronous-call retirement even after this Activity departs.
        client = FilesHandoffClient(this, ::canInteractNow, ::reported)
        enableEdgeToEdge()
        title = getString(R.string.shared_locations_heading)
        setContent {
            CloudyAppTheme {
                SharedLocationsScreen(
                    counterpartName = getString(if (owner == WindowOwner.CONSOLE)
                        R.string.shared_locations_desktop else R.string.shared_locations_console),
                    page = page, selected = selected, busy = busy, uncertain = pending, message = message,
                    interactive = resumed && LocalWindowInfo.current.isWindowFocused,
                    onRefresh = { discover(first = true) }, onNext = { discover(first = false) },
                    onSelect = ::select, onOpen = ::open,
                    onClose = { depart(); finish() },
                )
            }
        }
        // No onCreate/onResume read: a visible deliberate Refresh starts the first binding.
    }

    override fun onResume() { super.onResume(); resumed = true }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) depart()
    }
    override fun onPause() { resumed = false; depart(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        resumed = false
        depart()
        if (::owner.isInitialized) outState.putBundle(STATE, Bundle().apply {
            putInt("schema", 1); putString("owner", owner.wireId); putBoolean("pending", pending)
        })
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        FilesPositiveObservation.record(FilesPositiveObservation.Event.SENDER_DESTROYED, this)
        disposed = true; depart(); super.onDestroy()
    }

    private fun canInteractNow(): Boolean = resumed && hasWindowFocus() && !disposed && !isFinishing
    private fun canStart(): Boolean = canInteractNow() && !busy

    private fun depart() {
        page = null
        selected = null
        // An accepted Open stays conservatively uncertain until a definitive retired response
        // is observed here. abandon(false) alone cannot prove that no submission can occur.
        if (operation == Operation.SUBMIT) pending = true
        if (::client.isInitialized && client.abandon()) pending = true
        if (pending) message = getString(R.string.shared_locations_unknown)
    }

    private fun discover(first: Boolean) {
        if (!canStart() || !first && pending) return
        val after = if (first) "" else page?.nextAfterResourceId?.takeIf { it.isNotEmpty() } ?: return
        pendingBeforeOperation = pending
        operation = if (first) Operation.REFRESH else Operation.NEXT
        busy = true
        page = null
        selected = null
        message = getString(R.string.shared_locations_loading)
        val accepted = try { client.discover(after) } catch (_: Exception) { false }
        if (!accepted && operation != null) notQueued()
    }

    private fun select(descriptor: FilesSharedDescriptor) {
        if (!canStart() || pending) return
        selected = page?.locations?.singleOrNull { it === descriptor }
    }

    private fun open() {
        if (!canStart() || pending) return
        val descriptor = selected ?: return
        if (page?.locations?.none { it === descriptor } != false) return
        pendingBeforeOperation = pending
        operation = Operation.SUBMIT
        pending = true // Before client queue admission or any saved-state callback.
        busy = true
        page = null
        selected = null
        message = getString(R.string.shared_locations_sending)
        val accepted = try { client.submit(descriptor) } catch (_: Exception) { false }
        if (!accepted && operation != null) notQueued()
    }

    private fun notQueued() {
        busy = false
        operation = null
        pending = pendingBeforeOperation
        message = getString(if (pending) R.string.shared_locations_unknown else R.string.shared_locations_busy)
    }

    private fun reported(result: FilesClientResult, retired: Boolean) {
        if (disposed || isFinishing) return
        val completed = operation
        busy = !retired
        if (!canInteractNow()) {
            page = null
            selected = null
            if (completed == Operation.SUBMIT || result is FilesClientResult.Failure && result.mayHaveSubmitted) pending = true
            if (retired) operation = null
            message = getString(if (pending) R.string.shared_locations_unknown else R.string.shared_locations_explicit_refresh)
            return
        }
        when (result) {
            is FilesClientResult.Discovery -> {
                if (retired && result.reply.status == FilesDiscoveryStatus.OK) {
                    page = result.reply
                    selected = null
                    // Explicit first-page Refresh acknowledges new observation, never retries
                    // a previous envelope or establishes what an uncertain submission did.
                    if (completed == Operation.REFRESH) pending = false
                    message = getString(R.string.shared_locations_refreshed)
                } else message = getString(discoveryMessage(result.reply.status))
            }
            is FilesClientResult.Submission -> {
                page = null
                selected = null
                pending = result.reply.consumption == FilesConsumption.UNKNOWN ||
                    result.reply.status == FilesSubmitStatus.ADMITTED_DELIVERY_UNCONFIRMED
                message = getString(submitMessage(result.reply.status))
            }
            is FilesClientResult.Failure -> {
                page = null
                selected = null
                pending = pendingBeforeOperation || result.mayHaveSubmitted ||
                    completed == Operation.SUBMIT && (!retired || result.code == FilesClientCode.UNCONFIRMED)
                message = getString(if (pending) R.string.shared_locations_unknown else failureMessage(result.code))
            }
        }
        if (retired) operation = null
    }

    private fun discoveryMessage(status: FilesDiscoveryStatus): Int = when (status) {
        FilesDiscoveryStatus.OK -> R.string.shared_locations_refreshed
        FilesDiscoveryStatus.UNSUPPORTED_VERSION -> R.string.shared_locations_version
        FilesDiscoveryStatus.DENIED -> R.string.shared_locations_denied
        FilesDiscoveryStatus.INVALID_INPUT -> R.string.shared_locations_invalid
        FilesDiscoveryStatus.BUSY -> R.string.shared_locations_busy
        FilesDiscoveryStatus.UNAVAILABLE -> R.string.shared_locations_unavailable
    }
    private fun failureMessage(code: FilesClientCode): Int = when (code) {
        FilesClientCode.BUSY -> R.string.shared_locations_busy
        FilesClientCode.UNAVAILABLE -> R.string.shared_locations_unavailable
        FilesClientCode.DENIED -> R.string.shared_locations_denied
        FilesClientCode.INVALID_RESPONSE -> R.string.shared_locations_invalid_response
        FilesClientCode.UNCONFIRMED -> R.string.shared_locations_unconfirmed_read
        FilesClientCode.CANCELLED -> R.string.shared_locations_cancelled
    }
    private fun submitMessage(status: FilesSubmitStatus): Int = when (status) {
        FilesSubmitStatus.DISPATCH_REQUESTED -> R.string.shared_locations_dispatch_requested
        FilesSubmitStatus.DENIED -> R.string.shared_locations_denied
        FilesSubmitStatus.INVALID_ENVELOPE -> R.string.shared_locations_invalid
        FilesSubmitStatus.UNSUPPORTED_ACTION -> R.string.shared_locations_unsupported
        FilesSubmitStatus.RESOURCE_UNAVAILABLE -> R.string.shared_locations_stale
        FilesSubmitStatus.EXPIRED -> R.string.shared_locations_expired
        FilesSubmitStatus.NOT_YET_VALID -> R.string.shared_locations_clock
        FilesSubmitStatus.DUPLICATE -> R.string.shared_locations_duplicate
        FilesSubmitStatus.CLOCK_ROLLBACK -> R.string.shared_locations_clock
        FilesSubmitStatus.CAPACITY -> R.string.shared_locations_capacity
        FilesSubmitStatus.BUSY -> R.string.shared_locations_busy
        FilesSubmitStatus.ADMISSION_UNCONFIRMED -> R.string.shared_locations_unknown
        FilesSubmitStatus.ADMITTED_NOT_DISPATCHED -> R.string.shared_locations_reserved_not_opened
        FilesSubmitStatus.ADMITTED_DELIVERY_UNCONFIRMED -> R.string.shared_locations_reserved_unknown
    }

    companion object { private const val STATE = "cloudy-shared-locations-v1" }
}
