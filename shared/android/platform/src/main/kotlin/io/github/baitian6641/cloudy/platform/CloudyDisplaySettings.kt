package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.baitian6641.cloudy.design.CloudyAppearance
import io.github.baitian6641.cloudy.design.CloudyDensity
import io.github.baitian6641.cloudy.design.CloudyDisplayPreferences
import io.github.baitian6641.cloudy.domain.TerminalRightClick
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

enum class CloudyDisplayNotice { SAVED, RECOVERED_VALUES, WRITE_UNCONFIRMED, UNAVAILABLE, BUSY }

@Immutable
data class CloudyDisplayState(
    val preferences: CloudyDisplayPreferences = CloudyDisplayPreferences(),
    val terminalRightClick: TerminalRightClick = TerminalRightClick.PASTE,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val ready: Boolean = false,
    val notice: CloudyDisplayNotice? = null,
    val persistenceUncertain: Boolean = false,
    val recoveredValues: Boolean = false,
)

internal enum class DisplayStorageCode { WRONG_OWNER, WRONG_THREAD, WRONG_STORAGE, UNAVAILABLE, WRITE_UNCONFIRMED }
internal sealed interface DisplayStorageResult {
    data class Success(val preferences: CloudyDisplayPreferences, val terminalRightClick: TerminalRightClick,
                       val recovered: Boolean) : DisplayStorageResult
    data class Failure(val code: DisplayStorageCode, val runtimePreferences: CloudyDisplayPreferences? = null,
                       val recovered: Boolean = false) : DisplayStorageResult
}

/**
 * Small real preference adapter. The internal name override isolates actual app-private test files;
 * production reaches it only through the allowlisted owning-app process state below.
 * No schema envelope, credentials, endpoints or operation authority is stored: only two enum keys.
 */
internal class CloudyDisplayPreferencesStorage(context: Context, preferencesName: String) {
    private val suppliedDeviceProtected = context.isDeviceProtectedStorage
    private val application = context.applicationContext
    private val suppliedPackage = context.packageName
    private val preferences: SharedPreferences by lazy { application.getSharedPreferences(preferencesName, Context.MODE_PRIVATE) }

    init { require(Regex("[A-Za-z0-9._-]{1,80}").matches(preferencesName)) { "DISPLAY_INVALID_NAME" } }

    fun load(): DisplayStorageResult = guarded { readCurrent() }
    fun setAppearance(value: CloudyAppearance): DisplayStorageResult = write("appearance", value.name)
    fun setDensity(value: CloudyDensity): DisplayStorageResult = write("density", value.name)
    fun setTerminalRightClick(value: TerminalRightClick): DisplayStorageResult =
        write("terminalRightClick", value.name)

    private fun write(key: String, value: String): DisplayStorageResult = guarded {
        var attempted = false
        try {
            // Editors modify only the explicitly selected field. A second window's change to
            // another field must not be overwritten by an older whole-settings snapshot.
            val editor = preferences.edit().putString(key, value)
            attempted = true
            val committed = editor.commit()
            val current = readCurrent()
            if (committed) current else DisplayStorageResult.Failure(DisplayStorageCode.WRITE_UNCONFIRMED,
                current.preferences, current.recovered)
        } catch (_: Exception) {
            val runtime = try { readCurrent() } catch (_: Exception) { null }
            DisplayStorageResult.Failure(if (attempted) DisplayStorageCode.WRITE_UNCONFIRMED else DisplayStorageCode.UNAVAILABLE,
                runtime?.preferences, runtime?.recovered ?: false)
        }
    }

    private fun readCurrent(): DisplayStorageResult.Success {
        val values = preferences.all
        val appearanceValue = values["appearance"]
        val densityValue = values["density"]
        val rightClickValue = values["terminalRightClick"]
        val appearance = CloudyAppearance.entries.firstOrNull { it.name == appearanceValue }
        val density = CloudyDensity.entries.firstOrNull { it.name == densityValue }
        val rightClick = TerminalRightClick.entries.firstOrNull { it.name == rightClickValue }
        val recovered = (values.containsKey("appearance") && appearance == null) ||
            (values.containsKey("density") && density == null) ||
            (values.containsKey("terminalRightClick") && rightClick == null)
        // Invalid known values remain on disk until that particular user choice is saved.
        // Unrelated/unknown keys are never deleted or interpreted as another kind of state.
        return DisplayStorageResult.Success(CloudyDisplayPreferences(appearance ?: CloudyAppearance.SYSTEM,
            density ?: CloudyDensity.COMFORTABLE), rightClick ?: TerminalRightClick.PASTE, recovered)
    }

    private fun guarded(action: () -> DisplayStorageResult): DisplayStorageResult {
        if (Looper.myLooper() == Looper.getMainLooper()) return DisplayStorageResult.Failure(DisplayStorageCode.WRONG_THREAD)
        if (suppliedDeviceProtected || application.isDeviceProtectedStorage) return DisplayStorageResult.Failure(DisplayStorageCode.WRONG_STORAGE)
        if (application.packageName != suppliedPackage || application.applicationInfo.uid != Process.myUid()) {
            return DisplayStorageResult.Failure(DisplayStorageCode.WRONG_OWNER)
        }
        return try { action() } catch (_: Exception) { DisplayStorageResult.Failure(DisplayStorageCode.UNAVAILABLE) }
    }
}

/** One observable owner per owning application process; no service, sibling state or polling. */
class CloudyDisplaySettings private constructor(context: Context) {
    private val storage = CloudyDisplayPreferencesStorage(context, PREFERENCES_NAME)
    private val mutable = mutableStateOf(CloudyDisplayState())
    private var operationPending = false
    val state: State<CloudyDisplayState> get() = mutable

    init { refresh() }

    fun refresh() = onMain {
        if (operationPending) return@onMain
        submit(mutation = false) { storage.load() }
    }

    fun setAppearance(value: CloudyAppearance) = onMain {
        if (!mutable.value.ready || mutable.value.loading || mutable.value.saving) return@onMain
        submit(mutation = true) { storage.setAppearance(value) }
    }

    fun setDensity(value: CloudyDensity) = onMain {
        if (!mutable.value.ready || mutable.value.loading || mutable.value.saving) return@onMain
        submit(mutation = true) { storage.setDensity(value) }
    }

    fun setTerminalRightClick(value: TerminalRightClick) = onMain {
        if (!mutable.value.ready || mutable.value.loading || mutable.value.saving) return@onMain
        submit(mutation = true) { storage.setTerminalRightClick(value) }
    }

    private fun submit(mutation: Boolean, operation: () -> DisplayStorageResult) {
        val old = mutable.value
        operationPending = true
        mutable.value = old.copy(loading = !mutation, saving = mutation)
        try {
            operations.execute {
                val result = try { operation() } catch (_: Exception) {
                    DisplayStorageResult.Failure(if (mutation) DisplayStorageCode.WRITE_UNCONFIRMED else DisplayStorageCode.UNAVAILABLE)
                }
                main.post {
                    operationPending = false
                    val previous = mutable.value
                    mutable.value = when (result) {
                        is DisplayStorageResult.Success -> {
                            // A cached SharedPreferences read cannot resolve a prior failed disk
                            // commit. Only a later explicit successful commit clears uncertainty.
                            val uncertain = previous.persistenceUncertain && !mutation
                            CloudyDisplayState(result.preferences, result.terminalRightClick,
                                loading = false, saving = false, ready = true,
                                notice = when {
                                    uncertain -> CloudyDisplayNotice.WRITE_UNCONFIRMED
                                    result.recovered -> CloudyDisplayNotice.RECOVERED_VALUES
                                    mutation -> CloudyDisplayNotice.SAVED
                                    else -> null
                                }, persistenceUncertain = uncertain, recoveredValues = result.recovered)
                        }
                        is DisplayStorageResult.Failure -> previous.copy(
                            preferences = result.runtimePreferences ?: previous.preferences,
                            loading = false, saving = false, ready = result.runtimePreferences != null,
                            notice = if (previous.persistenceUncertain || result.code == DisplayStorageCode.WRITE_UNCONFIRMED)
                                CloudyDisplayNotice.WRITE_UNCONFIRMED else CloudyDisplayNotice.UNAVAILABLE,
                            persistenceUncertain = previous.persistenceUncertain || result.code == DisplayStorageCode.WRITE_UNCONFIRMED,
                            recoveredValues = result.recovered,
                        )
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            operationPending = false
            mutable.value = old.copy(loading = false, saving = false,
                notice = if (old.persistenceUncertain) CloudyDisplayNotice.WRITE_UNCONFIRMED else CloudyDisplayNotice.BUSY)
        }
    }

    companion object {
        const val PREFERENCES_NAME = "cloudy-display-v1"
        private var instance: CloudyDisplaySettings? = null
        private var instancePackage: String? = null
        private val operations = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16),
            { action -> Thread(action, "cloudy-display").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }
        private val main = Handler(Looper.getMainLooper())

        fun get(context: Context): CloudyDisplaySettings {
            require(!context.isDeviceProtectedStorage) { "DISPLAY_WRONG_STORAGE" }
            val application = context.applicationContext
            require(!application.isDeviceProtectedStorage && application.packageName == context.packageName &&
                application.applicationInfo.uid == Process.myUid()) { "DISPLAY_WRONG_OWNER" }
            val name = application.packageName
            require(name == "io.github.baitian6641.cloudy.console" || name == "io.github.baitian6641.cloudy.desktop") {
                "DISPLAY_WRONG_OWNER"
            }
            return synchronized(this) {
                require(instancePackage == null || instancePackage == name) { "DISPLAY_WRONG_OWNER" }
                instance ?: CloudyDisplaySettings(application).also { instance = it; instancePackage = name }
            }
        }

        private fun onMain(action: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
        }
    }
}
