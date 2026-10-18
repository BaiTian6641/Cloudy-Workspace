package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.identity.VaultProfiles
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** User-visible home messages; every state is honest about the operation it describes. */
internal enum class HomeMessage {
    TERMINAL_NOT_INTEGRATED, RDP_NOT_INTEGRATED, STREAM_NOT_INTEGRATED,
    STORE_UNAVAILABLE, BUSY, CONFLICT_REFRESHED, ROUTE_UNAVAILABLE,
}

internal data class WorkspaceHomeState(
    val loading: Boolean = true,
    val rows: List<HomeRow> = emptyList(),
    val storeUnavailable: Boolean = false,
    val message: HomeMessage? = null,
)

/**
 * Loads profile/trust/vault metadata for one window's home surface. Reads are generation-fenced
 * to this window's foreground lifetime; mutations serialize through the stores and always re-read
 * instead of assuming a write's visible effect. Only non-secret metadata is ever loaded here.
 */
internal class WorkspaceHomeController(context: Context, private val owner: WindowOwner) {
    private val application = context.applicationContext
    private val profiles = ConnectionProfiles(application)
    private val trust = SshHostTrustStore(application)
    private val mutable = mutableStateOf(WorkspaceHomeState())
    private var generation = 0L

    val state: State<WorkspaceHomeState> get() = mutable

    fun refresh() {
        val token = ++generation
        execute {
            val result = loadRows()
            main.post {
                if (token != generation) return@post
                mutable.value = when (result) {
                    is LoadResult.Loaded -> WorkspaceHomeState(loading = false, rows = result.rows)
                    LoadResult.Busy -> mutable.value.copy(loading = false, message = HomeMessage.BUSY)
                    LoadResult.Unavailable -> WorkspaceHomeState(loading = false, storeUnavailable = true,
                        message = HomeMessage.STORE_UNAVAILABLE)
                }
            }
        }
    }

    fun toggleFavorite(row: HomeRow) {
        val current = row.profile
        val draft = ConnectionProfileDraft.create(owner, current.label, current.protocol,
            current.host, current.port, current.tags, !current.favorite, current.jumpProfileId,
            current.keyUri)
        mutate { profiles.update(row.version, draft) }
    }

    /** Publish the marker's new revision before a destination reads its authentication snapshot. */
    fun recordUse(row: HomeRow, atEpochSeconds: Long, complete: () -> Unit) {
        mutate(complete = complete) { profiles.recordUse(row.version, atEpochSeconds) }
    }

    fun report(message: HomeMessage?) {
        deliver { it.copy(message = message) }
    }

    /** A stopped window drops every in-flight delivery; the stores are unaffected. */
    fun leave() {
        // Fence first: queued worker results must not land after this window stops.
        generation++
        deliver { it.copy(message = null) }
    }

    private fun mutate(
        complete: (() -> Unit)? = null,
        operation: () -> ConnectionProfilesResult<ConnectionProfileEntry>,
    ) {
        val token = generation
        execute {
            val result = operation()
            val failure = when (result) {
                is ConnectionProfilesResult.Success -> null
                is ConnectionProfilesResult.Failure -> result.code
            }
            val reloaded = loadRows()
            main.post {
                if (token != generation) return@post
                val canComplete = complete != null && result is ConnectionProfilesResult.Success &&
                    reloaded is LoadResult.Loaded && reloaded.rows.any {
                        it.version.generation == result.value.version.generation &&
                            it.version.id == result.value.version.id &&
                            it.version.revision == result.value.version.revision
                    }
                val message = when (failure) {
                    null -> if (complete != null && reloaded is LoadResult.Loaded && !canComplete)
                        HomeMessage.CONFLICT_REFRESHED else null
                    ConnectionProfilesCode.BUSY -> HomeMessage.BUSY
                    ConnectionProfilesCode.CONFLICT, ConnectionProfilesCode.STALE_GENERATION,
                    ConnectionProfilesCode.NOT_FOUND -> HomeMessage.CONFLICT_REFRESHED
                    else -> HomeMessage.STORE_UNAVAILABLE
                }
                val current = mutable.value
                mutable.value = when (reloaded) {
                    is LoadResult.Loaded -> current.copy(loading = false, rows = reloaded.rows,
                        storeUnavailable = false, message = message)
                    LoadResult.Busy -> current.copy(message = HomeMessage.BUSY)
                    LoadResult.Unavailable -> current.copy(storeUnavailable = true,
                        message = HomeMessage.STORE_UNAVAILABLE)
                }
                if (canComplete) complete?.invoke()
            }
        }
    }

    private fun loadRows(): LoadResult {
        val catalog = when (val result = profiles.load()) {
            is ConnectionProfilesResult.Success -> result.value
            is ConnectionProfilesResult.Failure -> return when (result.code) {
                ConnectionProfilesCode.BUSY -> LoadResult.Busy
                else -> LoadResult.Unavailable
            }
        }
        // Trust/vault reads are independent: their failure degrades only their own columns.
        val trustCatalog = when (val result = trust.load()) {
            is SshTrustStoreResult.Success -> result.value
            is SshTrustStoreResult.Failure -> null
        }
        val vault = try { VaultProfiles(application).list() } catch (_: Exception) { null }
        return LoadResult.Loaded(WorkspaceHomeOrganizer.rows(catalog, trustCatalog, vault))
    }

    private sealed interface LoadResult {
        data class Loaded(val rows: List<HomeRow>) : LoadResult
        data object Busy : LoadResult
        data object Unavailable : LoadResult
    }

    private fun execute(task: () -> Unit) {
        try {
            operations.execute(task)
        } catch (_: RejectedExecutionException) {
            deliver { it.copy(loading = false, message = HomeMessage.BUSY) }
        }
    }

    private inline fun deliver(crossinline update: (WorkspaceHomeState) -> WorkspaceHomeState) {
        val apply = { mutable.value = update(mutable.value) }
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else main.post { apply() }
    }

    companion object {
        private val operations = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(32),
            { action -> Thread(action, "cloudy-home").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }
        private val main = Handler(Looper.getMainLooper())
    }
}
