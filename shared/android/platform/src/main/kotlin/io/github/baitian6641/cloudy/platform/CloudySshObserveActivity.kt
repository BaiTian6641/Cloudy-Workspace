package io.github.baitian6641.cloudy.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshObservationAttempt
import io.github.baitian6641.cloudy.domain.SshObservationChallenge
import io.github.baitian6641.cloudy.domain.SshObservationCompletion
import io.github.baitian6641.cloudy.domain.SshObservationPresentation
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.SshObservationResult as PresentationResult
import io.github.baitian6641.cloudy.domain.SshTrustEvaluation
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.ssh.SshHostKeyObserver
import io.github.baitian6641.cloudy.ssh.SshObservationCancellation
import io.github.baitian6641.cloudy.ssh.SshObservationResult as TransportResult
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Real signed-key observation and explicit pin CAS only. No credential or authentication path. */
class CloudySshObserveActivity : ComponentActivity() {
    private lateinit var owner: WindowOwner
    private lateinit var policy: SshObservationPresentation
    private var resolver: SshAddressResolver? = null
    private var resolution: SshResolutionHandle? = null
    private var cancellation: SshObservationCancellation? = null
    private var operation: Any? = null
    private var expiry: Runnable? = null
    private var interactive = false
    private var disposed = false
    private var catalog by mutableStateOf<ConnectionProfileCatalog?>(null)
    private var selected by mutableStateOf<SshObservationProfile?>(null)
    private var challenge by mutableStateOf<SshObservationChallenge?>(null)
    private var busy by mutableStateOf(false)
    private var pending by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // A system prompt can pause the Activity. Permission is never a replayable Check command.
        if (!disposed) message = getString(R.string.ssh_observe_permission_return)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        owner = when (packageName) {
            "io.github.baitian6641.cloudy.console" -> WindowOwner.CONSOLE
            "io.github.baitian6641.cloudy.desktop" -> WindowOwner.DESKTOP
            else -> { finish(); return }
        }
        policy = SshObservationPresentation(owner)
        enableEdgeToEdge()
        when (val restored = ObserveSavedState.restore(savedInstanceState, owner)) {
            null -> message = getString(R.string.ssh_observe_restore_rejected)
            true -> { pending = true; message = getString(R.string.ssh_observe_unknown) }
            false -> Unit
        }
        title = getString(R.string.ssh_observe_heading)
        setContent {
            CloudyAppTheme {
                SshObserveScreen(catalog, selected, challenge, busy, pending, message,
                    onSelect = ::selectProfile, onCheck = ::checkKey, onCancel = ::cancelCheck,
                    onSave = ::saveTrust, onRefresh = { if (canInteract()) refresh(explicit = true) },
                    onClose = { retire(); finish() },
                    offerLocalPermission = Build.VERSION.SDK_INT >= 37 && !hasLocalPermission(),
                    onLocalPermission = { if (canInteract() && !pending) requestLocalPermission() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::policy.isInitialized || isFinishing) return
        interactive = true
        policy.resume()
        resolver = SshAddressResolver(applicationContext)
        refresh(explicit = false)
    }

    override fun onPause() {
        interactive = false
        if (::policy.isInitialized) policy.pause()
        retire()
        resolver?.close(); resolver = null
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        interactive = false
        if (::policy.isInitialized) policy.pause()
        retire()
        if (::owner.isInitialized) ObserveSavedState.save(outState, owner, pending)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        disposed = true
        retire()
        resolver?.close(); resolver = null
        if (::policy.isInitialized) policy.close()
        super.onDestroy()
    }

    private fun canInteract() = interactive && !disposed && !isFinishing && !busy

    private fun retire() {
        operation = null
        resolution?.cancel(); resolution = null
        cancellation?.cancel(); cancellation = null
        expiry?.let { main.removeCallbacks(it) }; expiry = null
        if (::policy.isInitialized) policy.cancel()
        challenge = null
        busy = false
    }

    private fun cancelCheck() {
        retire()
        message = getString(if (pending) R.string.ssh_observe_unknown else R.string.ssh_observe_cancelled)
    }

    private fun selectProfile(profile: io.github.baitian6641.cloudy.domain.ConnectionProfile) {
        if (!canInteract() || pending) return
        val saved = catalog ?: return
        val exact = saved.profiles.singleOrNull { it.id == profile.id && it.revision == profile.revision } ?: return
        if (exact.protocol != ConnectionProtocol.SSH) return
        retire()
        selected = SshObservationProfile.create(saved.generation, exact)
        policy.select(selected)
        message = getString(R.string.ssh_observe_ready)
    }

    private fun refresh(explicit: Boolean) {
        if (!canInteract()) return
        retire()
        selected = null
        policy.select(null)
        catalog = null
        val marker = Any()
        operation = marker
        busy = true
        enqueue(marker, {
            val profiles = ConnectionProfiles(applicationContext).load()
            val trust = SshHostTrustStore(applicationContext).load()
            when {
                profiles is ConnectionProfilesResult.Failure -> Work.Failed(profiles.code.name)
                trust is SshTrustStoreResult.Failure -> Work.Failed(trust.code.name)
                profiles is ConnectionProfilesResult.Success -> Work.Catalog(profiles.value)
                else -> Work.Failed("STORE_UNAVAILABLE")
            }
        }) { result ->
            when (result) {
                is Work.Catalog -> {
                    catalog = result.value
                    if (explicit) { pending = false; message = getString(R.string.ssh_observe_refreshed) }
                    else if (pending) message = getString(R.string.ssh_observe_unknown)
                }
                is Work.Failed -> message = failure(result.code)
                else -> message = failure("UNAVAILABLE")
            }
        }
    }

    private fun hasLocalPermission(): Boolean = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun requestLocalPermission() {
        retire()
        message = getString(R.string.ssh_observe_permission_needed)
        if (Build.VERSION.SDK_INT >= 37) permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    private fun checkKey() {
        if (!canInteract() || pending) return
        val profile = selected ?: return
        retire()
        policy.select(profile)
        if (SshLocalNetworkPolicy.shouldRequestPermission(applicationContext, profile.peer) && !hasLocalPermission()) {
            requestLocalPermission(); return
        }
        val ticket = when (val result = policy.begin(SystemClock.elapsedRealtime())) {
            is PresentationResult.Success -> result.value
            is PresentationResult.Failure -> { message = failure(result.code.name); return }
        }
        val marker = Any()
        operation = marker
        busy = true
        message = getString(R.string.ssh_observe_checking)
        val activeResolver = resolver ?: run { retire(); message = failure("UNAVAILABLE"); return }
        resolution = activeResolver.resolve(profile.peer, timeoutMillis = 5_000) { result ->
            if (operation !== marker || !interactive || disposed) return@resolve
            if (SystemClock.elapsedRealtime() >= ticket.expiresAtMillis) {
                retire(); message = failure("EXPIRED"); return@resolve
            }
            resolution = null
            when (result) {
                is SshAddressResult.Failed -> { retire(); message = failure(result.code.name) }
                is SshAddressResult.Resolved -> {
                    val address = result.addresses.firstOrNull()
                    if (address == null) { retire(); message = failure("NO_ADDRESSES") }
                    else if (SshLocalNetworkPolicy.shouldRequestPermission(applicationContext, address) && !hasLocalPermission()) requestLocalPermission()
                    else observe(marker, ticket, result.addresses)
                }
            }
        }
    }

    private fun observe(marker: Any, ticket: SshObservationAttempt, addresses: List<InetAddress>) {
        val stop = SshObservationCancellation()
        cancellation = stop
        val currentPolicy = policy
        val app = applicationContext
        enqueue(marker, {
            val transportDeadline = minOf(ticket.expiresAtMillis, SystemClock.elapsedRealtime() + 10_000)
            var observedResult: TransportResult? = null
            for (address in addresses) {
                if (stop.isCancelled || currentPolicy.currentAttempt !== ticket) return@enqueue Work.Failed("CANCELLED")
                val remaining = transportDeadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) return@enqueue Work.Failed("TIMED_OUT")
                if (SshLocalNetworkPolicy.shouldRequestPermission(app, address) && !hasLocalPermission()) return@enqueue Work.LocalPermissionRequired
                // Only connect failure may advance to the next resolved numeric address. Every
                // candidate shares one transport budget; observed keys/policy/cleanup stop fallback.
                val observed = observer.observe(ticket.profile.peer, address, stop, timeoutMillis = remaining.toInt())
                observedResult = observed
                if (observed !is TransportResult.Failure || observed.code.name != "CONNECT_FAILED") break
            }
            when (val observed = observedResult) {
                null -> Work.Failed("NO_ADDRESSES")
                is TransportResult.Failure -> Work.Failed(observed.code.name)
                is TransportResult.SignedKeyObserved -> {
                    if (stop.isCancelled || currentPolicy.currentAttempt !== ticket) Work.Failed("CANCELLED")
                    else {
                        val current = loadBinding(app, ticket.profile)
                        if (current != ticket.profile) Work.Failed("PROFILE_CHANGED")
                        else when (val evaluated = SshHostTrustStore(app).evaluate(observed.peer, observed.key)) {
                            is SshTrustStoreResult.Success -> Work.Evaluated(evaluated.value)
                            is SshTrustStoreResult.Failure -> Work.Failed(evaluated.code.name)
                        }
                    }
                }
            }
        }) { result ->
            cancellation = null
            when (result) {
                is Work.Evaluated -> when (val completed = policy.complete(ticket, result.value, SystemClock.elapsedRealtime())) {
                    is PresentationResult.Failure -> { retire(); message = failure(completed.code.name) }
                    is PresentationResult.Success -> when (val value = completed.value) {
                        is SshObservationCompletion.Matched -> { retire(); message = getString(R.string.ssh_observe_match) }
                        is SshObservationCompletion.Challenge -> {
                            challenge = value.presentation
                            message = getString(R.string.ssh_observe_comparison)
                            val expires = Runnable {
                                if (challenge === value.presentation) {
                                    retire(); message = getString(R.string.ssh_observe_expired)
                                }
                            }
                            expiry = expires
                            main.postDelayed(expires, maxOf(1, value.presentation.expiresAtMillis - SystemClock.elapsedRealtime()))
                        }
                    }
                }
                Work.LocalPermissionRequired -> requestLocalPermission()
                is Work.Failed -> { retire(); message = failure(result.code) }
                else -> { retire(); message = failure("UNAVAILABLE") }
            }
        }
    }

    private fun saveTrust(value: SshObservationChallenge) {
        if (!canInteract() || pending || challenge !== value) return
        val marker = Any()
        operation = marker
        challenge = null
        expiry?.let { main.removeCallbacks(it) }; expiry = null
        busy = true
        pending = true // Before admission: recreation records uncertainty, never a command.
        val currentPolicy = policy
        val app = applicationContext
        enqueue(marker, {
            val current = loadBinding(app, value.profile)
            if (current == null) Work.NotAdmitted("PROFILE_CHANGED")
            else when (val admitted = currentPolicy.approve(value.challenge.presentationGeneration, current, SystemClock.elapsedRealtime())) {
                is PresentationResult.Failure -> Work.NotAdmitted(admitted.code.name)
                is PresentationResult.Success -> {
                    // Pause can win until approve executes here. Once admitted, the exact CAS may
                    // finish after pause; lifecycle loss cannot promise a database rollback.
                    when (val saved = SshHostTrustStore(app).accept(admitted.value.challenge)) {
                        is SshTrustStoreResult.Success -> Work.Saved
                        is SshTrustStoreResult.Failure -> Work.Failed(saved.code.name)
                    }
                }
            }
        }, mutation = true) { result ->
            pending = result is Work.Failed && result.code == SshTrustStoreCode.COMPLETION_UNKNOWN.name
            when (result) {
                Work.Saved -> message = getString(R.string.ssh_observe_saved)
                is Work.NotAdmitted -> { policy.cancel(); message = failure(result.code) }
                is Work.Failed -> message = if (pending) getString(R.string.ssh_observe_unknown) else failure(result.code)
                else -> { pending = true; message = getString(R.string.ssh_observe_unknown) }
            }
        }
    }

    private fun enqueue(marker: Any, work: () -> Work, mutation: Boolean = false, complete: (Work) -> Unit) {
        try {
            operations.execute {
                val result = try { work() } catch (_: Exception) {
                    Work.Failed(if (mutation) "COMPLETION_UNKNOWN" else "UNAVAILABLE")
                }
                main.post {
                    // Stale/disposed callbacks never repopulate selection or revive approval.
                    // A stale mutation leaves its saved pending bit for explicit reconciliation.
                    if (operation === marker && interactive && !disposed && !isFinishing) {
                        operation = null
                        busy = false
                        complete(result)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            if (mutation) pending = false // This request never entered the queue; earlier uncertainty blocks Save.
            retire()
            message = failure("BUSY")
        }
    }

    private fun failure(code: String) = getString(R.string.ssh_observe_failure, code)

    private sealed interface Work {
        class Catalog(val value: ConnectionProfileCatalog) : Work
        class Evaluated(val value: SshTrustEvaluation) : Work
        class Failed(val code: String) : Work
        class NotAdmitted(val code: String) : Work
        data object Saved : Work
        data object LocalPermissionRequired : Work
    }

    companion object {
        private val main = Handler(Looper.getMainLooper())
        private val operations = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16),
            { action -> Thread(action, "CloudySshObserve").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }
        // Preserve the engine's quarantine across windows; a new Activity cannot reset uncertain cleanup.
        private val observer = SshHostKeyObserver()

        private fun loadBinding(context: android.content.Context, expected: SshObservationProfile): SshObservationProfile? =
            when (val loaded = ConnectionProfiles(context).load()) {
                is ConnectionProfilesResult.Failure -> null
                is ConnectionProfilesResult.Success -> loaded.value.profiles.singleOrNull {
                    it.id == expected.profile.id && it.protocol == ConnectionProtocol.SSH
                }?.let { SshObservationProfile.create(loaded.value.generation, it) }
            }
    }
}

/** Only schema/owner/pending survive; no selection, key, attempt, deadline or approval is restored. */
private object ObserveSavedState {
    private const val KEY = "io.github.baitian6641.cloudy.ssh-observe.v1"
    fun save(destination: Bundle, owner: WindowOwner, pending: Boolean) {
        destination.putBundle(KEY, Bundle().apply {
            putInt("schema", 1); putString("owner", owner.wireId); putBoolean("pending", pending)
        })
    }
    @Suppress("DEPRECATION")
    fun restore(source: Bundle?, owner: WindowOwner): Boolean? {
        if (source == null) return false
        return try {
            if (!source.containsKey(KEY)) false else {
                val record = source.get(KEY) as? Bundle ?: return null
                if (record.keySet() != setOf("schema", "owner", "pending") || record.get("schema") != 1 ||
                    record.get("owner") != owner.wireId || record.get("pending") !is Boolean) null
                else record.get("pending") as Boolean
            }
        } catch (_: RuntimeException) { null }
    }
}
