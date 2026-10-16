package io.github.baitian6641.cloudy.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.ssh.SshAuthenticationCheck
import io.github.baitian6641.cloudy.ssh.SshAuthenticationResult
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Per-session password input only: no saved credential, trust mutation or reusable SSH session. */
class CloudySshAuthenticateActivity : ComponentActivity() {
    private lateinit var owner: WindowOwner
    private var interactive = false
    private var disposed = false
    private var operation: AuthenticationOperation? = null
    private var catalog by mutableStateOf<ConnectionProfileCatalog?>(null)
    private var selected by mutableStateOf<SshObservationProfile?>(null)
    private var username by mutableStateOf(TextFieldValue())
    private var password by mutableStateOf(TextFieldValue())
    private var credentialEpoch by mutableLongStateOf(0L)
    private var busy by mutableStateOf(false)
    private var pending = false
    private var message by mutableStateOf<String?>(null)

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!disposed) message = getString(R.string.ssh_auth_permission_return)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        owner = when (packageName) {
            "io.github.baitian6641.cloudy.console" -> WindowOwner.CONSOLE
            "io.github.baitian6641.cloudy.desktop" -> WindowOwner.DESKTOP
            else -> { finish(); return }
        }
        pending = restorePending(savedInstanceState)
        if (pending) message = getString(R.string.ssh_auth_interrupted)
        enableEdgeToEdge()
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        title = getString(R.string.ssh_auth_heading)
        setContent {
            CloudyAppTheme {
                val epoch = credentialEpoch
                SshAuthenticateScreen(catalog, selected, username, password, epoch, busy, message,
                    onSelect = ::selectProfile,
                    onUsername = {
                        if (epoch == credentialEpoch && canInteract()) {
                            if (it.text.length <= SshPasswordCredentials.MAX_USERNAME_UNITS) username = it
                            else message = getString(R.string.ssh_auth_invalid)
                        }
                    },
                    onPassword = {
                        if (epoch == credentialEpoch && canInteract()) {
                            if (it.text.length <= SshPasswordCredentials.MAX_PASSWORD_UNITS) password = it
                            else message = getString(R.string.ssh_auth_invalid)
                        }
                    },
                    onAuthenticate = ::authenticate, onCancel = ::cancel,
                    onRefresh = { if (canInteract()) refresh() }, onClose = { retire(); finish() },
                    offerLocalPermission = Build.VERSION.SDK_INT >= 37 && !hasLocalPermission(),
                    onLocalPermission = { if (canInteract()) requestLocalPermission() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::owner.isInitialized || isFinishing) return
        interactive = true
        refresh()
    }

    override fun onPause() {
        interactive = false
        retire()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        interactive = false
        retire()
        if (::owner.isInitialized) outState.putBundle(STATE_KEY, Bundle().apply {
            putInt("schema", 1); putString("owner", owner.wireId); putBoolean("pending", pending)
        })
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        disposed = true
        retire()
        super.onDestroy()
    }

    private fun canInteract() = interactive && !disposed && !isFinishing && !busy
    private fun clearFields() {
        // Dispose field-internal undo/composition history as well as external editable values.
        // Late callbacks from the disposed composition are fenced by the captured epoch.
        credentialEpoch++
        username = TextFieldValue()
        password = TextFieldValue()
    }
    private fun retire() {
        operation?.cancel()
        operation = null
        clearFields()
        busy = false
    }

    private fun cancel() {
        retire()
        message = getString(if (pending) R.string.ssh_auth_interrupted else R.string.ssh_auth_cancelled)
    }

    private fun selectProfile(profile: ConnectionProfile) {
        if (!canInteract()) return
        val loaded = catalog ?: return
        val exact = loaded.profiles.singleOrNull { it.id == profile.id && it.revision == profile.revision } ?: return
        if (exact.protocol != ConnectionProtocol.SSH) return
        retire()
        selected = SshObservationProfile.create(loaded.generation, exact)
        message = getString(R.string.ssh_auth_ready)
    }

    private fun refresh() {
        if (!canInteract()) return
        retire()
        selected = null
        catalog = null
        val token = AuthenticationOperation(null)
        operation = token
        busy = true
        val app = applicationContext
        if (!AuthenticationRuntime.execute(token) {
                val loaded = try { ConnectionProfiles(app).load() } catch (_: Exception) { null }
                post(token) {
                    when (loaded) {
                        is ConnectionProfilesResult.Success -> {
                            catalog = loaded.value
                            message = getString(if (pending) R.string.ssh_auth_interrupted else R.string.ssh_auth_choose)
                        }
                        is ConnectionProfilesResult.Failure -> message = failure(loaded.code.name)
                        null -> message = failure("UNAVAILABLE")
                    }
                }
            }) rejected(token)
    }

    private fun hasLocalPermission(): Boolean = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun requestLocalPermission() {
        retire()
        message = getString(R.string.ssh_auth_permission_needed)
        if (Build.VERSION.SDK_INT >= 37) permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    private fun authenticate() {
        if (!canInteract()) return
        val started = SystemClock.elapsedRealtime()
        val profile = selected ?: return
        val app = applicationContext
        if (SshLocalNetworkPolicy.shouldRequestPermission(app, profile.peer) && !hasLocalPermission()) {
            requestLocalPermission(); return
        }
        val user = username.text.toCharArray()
        val secret = password.text.toCharArray()
        val credentials = try { SshPasswordCredentials.create(user, secret) }
        catch (_: IllegalArgumentException) { message = getString(R.string.ssh_auth_invalid); return }
        finally { user.fill('\u0000'); secret.fill('\u0000') }
        clearFields()
        val token = AuthenticationOperation(credentials, started + 15_000L)
        operation = token
        busy = true
        val priorPending = pending
        pending = true
        message = getString(R.string.ssh_auth_checking)
        if (!AuthenticationRuntime.execute(token) {
                val result = try {
                    if (!token.active()) Work.Failed(token.stoppedCode())
                    else when (val prepared = SshAuthenticationCoordinator(app).begin(profile, credentials)) {
                        is SshAuthenticationPreparation.Failed -> Work.Failed(prepared.code.name)
                        is SshAuthenticationPreparation.Ready -> {
                            val handle = prepared.handle
                            if (!token.publish(handle)) Work.Failed(token.stoppedCode())
                            else {
                                val resolver = SshAddressResolver(app)
                                try {
                                    val address = token.resolve(resolver, profile)
                                    when {
                                        !token.active() -> Work.Failed(token.stoppedCode())
                                        address is SshAddressResult.Failed -> Work.Failed(address.code.name)
                                        address !is SshAddressResult.Resolved -> Work.Failed("TIMED_OUT")
                                        address.addresses.isEmpty() -> Work.Failed("NO_ADDRESSES")
                                        SshLocalNetworkPolicy.shouldRequestPermission(app, address.addresses.first()) && !hasLocalPermission() -> Work.Permission
                                        else -> {
                                            val remaining = minOf(token.remaining(), handle.expiresAtMillis - SystemClock.elapsedRealtime(), 10_000L)
                                            if (remaining <= 0) Work.Failed("TIMED_OUT")
                                            else when (val checked = AuthenticationRuntime.engine.authenticate(profile.peer,
                                                address.addresses.first(), handle.expectedKey, handle.cancellation, handle::admit, remaining.toInt())) {
                                                SshAuthenticationResult.Success -> Work.Authenticated
                                                is SshAuthenticationResult.Failure -> Work.Failed(checked.code.name)
                                            }
                                        }
                                    }
                                } finally { resolver.close() }
                            }
                        }
                    }
                } catch (_: Exception) { Work.Failed("UNAVAILABLE") }
                finally { token.dispose() }
                post(token) {
                    pending = false
                    when (result) {
                        Work.Authenticated -> message = getString(R.string.ssh_auth_success)
                        Work.Permission -> requestLocalPermission()
                        is Work.Failed -> message = failure(result.code)
                    }
                }
            }) { pending = priorPending; rejected(token) }
    }

    private fun rejected(token: AuthenticationOperation) {
        token.cancel()
        token.dispose()
        if (operation === token) { operation = null; busy = false; message = getString(R.string.ssh_auth_busy) }
    }

    private fun post(token: AuthenticationOperation, complete: () -> Unit): () -> Unit = {
        // Runtime posts this only after disposal and slot release. Stale lifecycle callbacks
        // cannot repopulate input, restore a command or publish a late authentication result.
        if (operation === token && interactive && !disposed && !isFinishing) {
            operation = null
            busy = false
            complete()
        }
    }

    private fun failure(code: String) = getString(R.string.ssh_auth_failure, code)

    @Suppress("DEPRECATION")
    private fun restorePending(source: Bundle?): Boolean {
        if (source == null) return false
        return try {
            if (!source.containsKey(STATE_KEY)) false else {
                val record = source.get(STATE_KEY) as? Bundle ?: return true
                if (record.keySet() != setOf("schema", "owner", "pending") || record.get("schema") != 1 ||
                    record.get("owner") != owner.wireId || record.get("pending") !is Boolean) true
                else record.get("pending") as Boolean
            }
        } catch (_: RuntimeException) { true }
    }

    private sealed interface Work {
        class Failed(val code: String) : Work
        data object Authenticated : Work
        data object Permission : Work
    }

    companion object {
        private const val STATE_KEY = "io.github.baitian6641.cloudy.ssh-auth.v1"
    }
}

/** One private process slot covers preparation, DNS, transport and retirement, including hidden windows. */
private object AuthenticationRuntime {
    val engine = SshAuthenticationCheck()
    private val current = AtomicReference<AuthenticationOperation?>(null)
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1),
        { action -> Thread(action, "CloudySshAuthenticate").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }

    fun execute(token: AuthenticationOperation, action: () -> (() -> Unit)): Boolean {
        if (!current.compareAndSet(null, token)) return false
        return try {
            worker.execute {
                val complete = try { action() } finally { token.dispose(); current.compareAndSet(token, null) }
                main.post { complete() }
            }
            true
        } catch (_: RejectedExecutionException) { current.compareAndSet(token, null); false }
    }
}

/** Short ownership monitor only; neither cancellation nor UI teardown waits for SQLite/network. */
private class AuthenticationOperation(private val credentials: SshPasswordCredentials?,
    private val deadline: Long = SystemClock.elapsedRealtime() + 15_000L) {
    private val gate = Any()
    @Volatile private var cancelled = false
    private var handle: SshAuthenticationHandle? = null
    private var resolution: SshResolutionHandle? = null
    private val resolved = CountDownLatch(1)
    private val answer = AtomicReference<SshAddressResult?>(null)
    fun remaining(): Long = deadline - SystemClock.elapsedRealtime()
    fun active(): Boolean = !cancelled && remaining() > 0
    fun stoppedCode(): String = if (cancelled) "CANCELLED" else "TIMED_OUT"

    fun publish(value: SshAuthenticationHandle): Boolean {
        val accepted = synchronized(gate) { if (!active()) false else { handle = value; true } }
        if (!accepted) value.close()
        return accepted
    }

    fun resolve(resolver: SshAddressResolver, profile: SshObservationProfile): SshAddressResult? {
        val remaining = minOf(remaining(), 5_000L)
        if (!active() || remaining <= 0) return null
        val request = resolver.resolve(profile.peer, remaining) { result -> answer.set(result); resolved.countDown() }
        val accepted = synchronized(gate) { if (!active()) false else { resolution = request; true } }
        if (!accepted) request.cancel()
        resolved.await(remaining, TimeUnit.MILLISECONDS)
        return answer.get()
    }

    fun cancel() {
        val owned = synchronized(gate) { cancelled = true; Pair(handle, resolution) }
        owned.first?.close()
        owned.second?.cancel()
        credentials?.close()
        resolved.countDown()
    }

    fun dispose() {
        val owned = synchronized(gate) { Pair(handle, resolution).also { handle = null; resolution = null } }
        owned.first?.close()
        owned.second?.cancel()
        credentials?.close()
    }
}
