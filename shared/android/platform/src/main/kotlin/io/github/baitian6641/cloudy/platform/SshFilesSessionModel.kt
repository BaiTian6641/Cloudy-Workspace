package io.github.baitian6641.cloudy.platform

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.FilesResourceDraft
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedOwnership
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedTransport
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedTransportFactory
import io.github.baitian6641.cloudy.ssh.SshFileEntry
import io.github.baitian6641.cloudy.ssh.SshFileKind
import io.github.baitian6641.cloudy.ssh.SshFilesResult
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import io.github.baitian6641.cloudy.ssh.SshTransportCloseResult
import io.github.baitian6641.cloudy.ssh.SshTransportCode
import io.github.baitian6641.cloudy.ssh.SshTransportOpenResult
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class SshFilesPhase { IDLE, LOADING, CONNECTING, CONNECTED, READING, RETIRING, FAILED }

internal data class SshFilesPreview(val name: String, val byteCount: Int, val text: String, val binary: Boolean) {
    override fun toString() = "SshFilesPreview(redacted)"
}

/** Opaque opportunity; register accepts only the exact current model-issued object. */
internal class FilesListingCandidate internal constructor(
    val profile: SshObservationProfile, val acceptedTrust: SshTrustSnapshot,
    val navigationRoot: String, val accountName: String, val expiresAtMillis: Long,
) {
    override fun toString() = "FilesListingCandidate(redacted)"
}

internal data class SshFilesSessionState(
    val catalog: ConnectionProfileCatalog? = null,
    val selected: SshObservationProfile? = null,
    val phase: SshFilesPhase = SshFilesPhase.IDLE,
    val path: String = ".",
    val entries: List<SshFileEntry> = emptyList(),
    val preview: SshFilesPreview? = null,
    val code: String? = null,
    val permissionNeeded: Boolean = false,
    val fingerprint: String? = null,
    val location: FilesResourceRegistration? = null,
    val registrationCandidate: FilesListingCandidate? = null,
    val registrationPending: Boolean = false,
    val registrationUncertain: Boolean = false,
    val registrationCode: String? = null,
    val registrationSaved: FilesResourceRegistration? = null,
) {
    override fun toString() = "SshFilesSessionState(${phase.name},redacted)"
}

/** Foreground-only owning caller. Configuration retains this model, never a saved command/password. */
internal class SshFilesSessionModel(private val app: Application) : ViewModel() {
    var state by mutableStateOf(SshFilesSessionState())
        private set
    private var generation = 0L
    private var cleared = false
    private var session: FilesSession? = null
    // Opaque instance identity only: retaining an Activity/Context here would leak its window.
    private var ownerToken: Any? = null
    private var attachmentEpoch = 0L
    private var handoffDeadline = 0L
    private var handoffTimeout: Runnable? = null
    // Worker admission reads this short gate, never Compose state or an Activity reference.
    private val registrationGate = Any()
    private var registrationOwner: Any? = null
    private var registrationForeground = false
    private var listingEpoch = 0L
    private var listingCandidate: ListingContext? = null
    private var candidateExpiry: Runnable? = null
    private var registrationOperation = 0L
    private class ListingContext(val candidate: FilesListingCandidate, val session: FilesSession,
        val proof: SshAuthenticatedOwnership, val handle: SshAuthenticationHandle,
        val owner: Any, val epoch: Long, val storeGeneration: String)


    fun onOwnerStarted(token: Any) {
        main()
        require(token !is Context) { "INVALID_OWNER_TOKEN" }
        if (cleared || (ownerToken != null && ownerToken !== token)) return
        // Handler delivery can be delayed by suspend/main congestion. A late attachment cannot
        // revive the expired exemption even when its timeout has not run yet.
        if (handoffDeadline != 0L && SystemClock.elapsedRealtime() >= handoffDeadline) disconnect()
        clearHandoff()
        ownerToken = token
        synchronized(registrationGate) { registrationOwner = token }
    }

    /** Focus/lifecycle loss invalidates consent, while a published visible session may survive. */
    fun onOwnerForeground(token: Any, foreground: Boolean) {
        main()
        if (cleared || ownerToken !== token) return
        synchronized(registrationGate) {
            registrationOwner = token
            registrationForeground = foreground
        }
        if (!foreground) invalidateListing()
    }

    fun onOwnerStopped(token: Any, changingConfigurations: Boolean) {
        main()
        if (cleared || ownerToken !== token) return
        ownerToken = null
        synchronized(registrationGate) { registrationOwner = null; registrationForeground = false }
        invalidateListing()
        clearHandoff()
        if (!changingConfigurations || state.phase !in setOf(SshFilesPhase.CONNECTED, SshFilesPhase.READING)) {
            disconnect()
            return
        }
        val epoch = attachmentEpoch
        handoffDeadline = SystemClock.elapsedRealtime() + CONFIGURATION_HANDOFF_MILLIS
        val timeout = Runnable {
            if (!cleared && ownerToken == null && attachmentEpoch == epoch) {
                clearHandoff()
                disconnect()
            }
        }
        handoffTimeout = timeout
        FilesRuntime.main.postDelayed(timeout, CONFIGURATION_HANDOFF_MILLIS)
    }

    private fun clearHandoff() {
        attachmentEpoch++
        handoffTimeout?.let { FilesRuntime.main.removeCallbacks(it) }
        handoffTimeout = null
        handoffDeadline = 0L
    }

    private fun main() { check(Looper.myLooper() == Looper.getMainLooper()) { "WRONG_THREAD" } }
    private fun idle() = state.phase in setOf(SshFilesPhase.IDLE, SshFilesPhase.FAILED)
    private fun permitted() = Build.VERSION.SDK_INT < 37 ||
        app.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    fun refreshProfiles() {
        main()
        if (cleared || !idle() || session != null || state.registrationPending) return
        invalidateListing()
        val version = ++generation
        state = SshFilesSessionState(phase = SshFilesPhase.LOADING,
            registrationPending = state.registrationPending, registrationUncertain = state.registrationUncertain,
            registrationCode = state.registrationCode, registrationSaved = state.registrationSaved)
        if (!FilesRuntime.work {
                val loaded = try { ConnectionProfiles(app).load() } catch (_: Exception) { null }
                FilesRuntime.main.post {
                    if (!cleared && generation == version && session == null) {
                        state = when (loaded) {
                            is ConnectionProfilesResult.Success -> state.copy(catalog = loaded.value, phase = SshFilesPhase.IDLE, code = null)
                            is ConnectionProfilesResult.Failure -> state.copy(catalog = null, phase = SshFilesPhase.FAILED, code = loaded.code.name)
                            null -> state.copy(catalog = null, phase = SshFilesPhase.FAILED, code = "UNAVAILABLE")
                        }
                    }
                }
            }) state = state.copy(phase = SshFilesPhase.FAILED, code = "BUSY")
    }

    fun selectProfile(profile: ConnectionProfile) {
        main()
        if (cleared || ownerToken == null || !idle() || session != null || state.registrationPending) return
        val catalog = state.catalog ?: return
        // Only the exact catalog object is a selectable row; the coordinator rechecks persisted data.
        val exact = catalog.profiles.singleOrNull { it === profile } ?: return
        if (exact.protocol != ConnectionProtocol.SSH) return
        invalidateListing()
        generation++
        state = state.copy(location = null, selected = SshObservationProfile.create(catalog.generation, exact),
            path = ".", entries = emptyList(), preview = null, code = null, fingerprint = null,
            permissionNeeded = false, phase = SshFilesPhase.IDLE)
    }

    /** Restored metadata is uncertainty only, never approval, a candidate or a replayable write. */
    fun restoreRegistrationUncertainty() {
        main()
        if (cleared) return
        invalidateListing()
        state = state.copy(registrationUncertain = true, registrationCode = "COMPLETION_UNKNOWN")
    }

    /** Consume only on a new destination's first start; process restore never calls this. */
    fun selectLocation(ticket: String) {
        main()
        if (cleared || ownerToken == null || !idle() || session != null || state.registrationPending) return
        invalidateListing()
        val version = ++generation
        state = state.copy(phase = SshFilesPhase.LOADING, selected = null, location = null,
            entries = emptyList(), preview = null, fingerprint = null, code = null)
        if (!FilesRuntime.work {
                val selected = FilesLocationTickets.consume(app, ticket)
                val catalog = if (selected is FilesResourceResult.Success) ConnectionProfiles(app).load() else null
                FilesRuntime.main.post {
                    if (!cleared && generation == version && session == null) {
                        state = when {
                            selected is FilesResourceResult.Success && catalog is ConnectionProfilesResult.Success ->
                                state.copy(catalog = catalog.value, selected = selected.value.profile,
                                    location = selected.value, path = selected.value.navigationRoot, phase = SshFilesPhase.IDLE)
                            selected is FilesResourceResult.Failure -> state.copy(phase = SshFilesPhase.FAILED, code = selected.code.name)
                            else -> state.copy(phase = SshFilesPhase.FAILED, code = "STORE_UNAVAILABLE")
                        }
                    }
                }
            }) state = state.copy(phase = SshFilesPhase.FAILED, code = "BUSY")
    }

    /** Cancel consumes this opportunity without touching persistence or a different listing. */
    fun cancelRegistration(candidate: FilesListingCandidate) {
        main()
        val matching = synchronized(registrationGate) { listingCandidate?.candidate === candidate }
        if (matching) invalidateListing()
    }

    /** Explicit consent only. Worker admission, not queue submission, chooses lifecycle winner. */
    fun register(candidate: FilesListingCandidate, label: String) {
        main()
        if (cleared || state.registrationPending || state.registrationUncertain) return
        val context = synchronized(registrationGate) {
            listingCandidate?.takeIf { it.candidate === candidate && registrationForeground &&
                registrationOwner === it.owner && ownerToken === it.owner }
        } ?: return
        val operation = ++registrationOperation
        state = state.copy(registrationPending = true, registrationCandidate = null,
            registrationCode = null, registrationSaved = null)
        if (!FilesRuntime.work {
                val result = registerWorker(context, label)
                FilesRuntime.main.post {
                    if (!cleared && operation == registrationOperation) {
                        invalidateListing()
                        state = when (result) {
                            is FilesResourceResult.Success -> state.copy(registrationPending = false,
                                registrationUncertain = false, registrationSaved = result.value, registrationCode = null)
                            is FilesResourceResult.Failure -> state.copy(registrationPending = false,
                                registrationUncertain = result.code == FilesResourceCode.COMPLETION_UNKNOWN,
                                registrationCode = result.code.name)
                        }
                    }
                }
            }) {
            invalidateListing()
            state = state.copy(registrationPending = false, registrationCode = "BUSY")
        }
    }

    private fun registerWorker(context: ListingContext, label: String): FilesResourceResult<FilesResourceRegistration> {
        if (!SshAuthenticationRegistry.lock.tryLock()) return FilesResourceResult.Failure(FilesResourceCode.BUSY)
        try {
            // Storage happens outside the short gate so main can revoke foreground consent.
            val store = FilesResourceStore(app)
            val catalog = store.load()
            if (catalog is FilesResourceResult.Failure) return catalog
            if ((catalog as FilesResourceResult.Success).value.generation != context.storeGeneration) {
                return FilesResourceResult.Failure(FilesResourceCode.STALE_GENERATION)
            }
            val admitted = synchronized(registrationGate) {
                val now = SystemClock.elapsedRealtime()
                val valid = listingCandidate === context && listingEpoch == context.epoch &&
                    registrationOwner === context.owner && registrationForeground &&
                    now >= 0 && now < context.candidate.expiresAtMillis &&
                    !context.session.cancelled.get() && context.session.transport.get()?.ownership === context.proof &&
                    context.handle.matchesEstablished(context.proof)
                // Every attempted approval is consumed, including stale/expired proposals.
                if (listingCandidate === context) listingCandidate = null
                valid
            }
            if (!admitted) return FilesResourceResult.Failure(FilesResourceCode.INVALID_STATE)
            val candidate = context.candidate
            val draft = try { FilesResourceDraft.create(candidate.profile.profile.owner, label, candidate.profile,
                candidate.acceptedTrust, candidate.navigationRoot, candidate.accountName) }
            catch (_: IllegalArgumentException) { return FilesResourceResult.Failure(FilesResourceCode.INVALID_INPUT) }
            // Foreground loss after the short admission point cannot roll back an admitted CAS.
            return store.create(context.storeGeneration, draft)
        } catch (_: Exception) { return FilesResourceResult.Failure(FilesResourceCode.STORE_UNAVAILABLE) }
        finally { SshAuthenticationRegistry.lock.unlock() }
    }

    private fun invalidateListing() {
        synchronized(registrationGate) { listingEpoch++; listingCandidate = null }
        candidateExpiry?.let { FilesRuntime.main.removeCallbacks(it) }
        candidateExpiry = null
        state = state.copy(registrationCandidate = null)
    }

    /** Called only by genuine successful listing publication on main, never by UI supplied data. */
    private fun listingContext(token: FilesSession, path: String, storeGeneration: String,
        expectedListingEpoch: Long): ListingContext? {
        if (state.registrationPending || state.registrationUncertain) return null
        val handle = token.handle.get() ?: return null
        val proof = token.transport.get()?.ownership ?: return null
        if (proof.isRetired || handle.cancellation.isCancelled || token.cancelled.get()) return null
        val now = SystemClock.elapsedRealtime()
        if (now < 0 || now > Long.MAX_VALUE - REGISTRATION_LIFETIME_MILLIS) return null
        val draft = try { FilesResourceDraft.create(token.profile.profile.owner, token.profile.profile.label,
            token.profile, handle.acceptedTrust, path, handle.accountName) }
        catch (_: IllegalArgumentException) { return null }
        val context = synchronized(registrationGate) {
            val owner = registrationOwner
            if (owner == null || !registrationForeground || ownerToken !== owner || listingEpoch != expectedListingEpoch) return null
            val candidate = FilesListingCandidate(draft.profile, draft.acceptedTrust, path,
                draft.accountName, now + REGISTRATION_LIFETIME_MILLIS)
            ListingContext(candidate, token, proof, handle, owner, listingEpoch, storeGeneration)
                .also { listingCandidate = it }
        }
        val expiry = Runnable {
            if (!cleared && synchronized(registrationGate) { listingCandidate === context }) {
                invalidateListing()
                state = state.copy(registrationCode = "REGISTRATION_EXPIRED")
            }
        }
        candidateExpiry = expiry
        FilesRuntime.main.postDelayed(expiry, REGISTRATION_LIFETIME_MILLIS)
        return context
    }

    /** Takes ownership on every path, including invalid selection, permission and Busy. */
    fun connect(credentials: SshPasswordCredentials) {
        if (Looper.myLooper() != Looper.getMainLooper()) { credentials.close(); return }
        val profile = state.selected
        if (cleared || ownerToken == null || !idle() || session != null || profile == null || state.registrationPending) { credentials.close(); return }
        if (SshLocalNetworkPolicy.shouldRequestPermission(app, profile.peer) && !permitted()) {
            credentials.close()
            state = state.copy(permissionNeeded = true, code = "PERMISSION_REQUIRED")
            return
        }
        invalidateListing()
        val token = FilesSession(profile, credentials, SystemClock.elapsedRealtime() + 15_000L, state.location,
            synchronized(registrationGate) { listingEpoch })
        if (!FilesRuntime.claim(token)) {
            credentials.close()
            state = state.copy(code = if (FilesRuntime.quarantined.get()) "QUARANTINED" else "BUSY")
            return
        }
        val version = ++generation
        session = token
        token.stopping = {
            FilesRuntime.main.post {
                if (!cleared && session === token && generation == version)
                    state = state.copy(phase = SshFilesPhase.RETIRING, entries = emptyList(), preview = null)
            }
        }
        token.finished = { code, certain ->
            FilesRuntime.main.post {
                if (!cleared && session === token && generation == version) {
                    if (certain) session = null
                    invalidateListing()
                    state = state.copy(phase = if (code == null && certain) SshFilesPhase.IDLE else SshFilesPhase.FAILED,
                        entries = emptyList(), preview = null, fingerprint = null,
                        code = if (certain) code else "CLEANUP_UNCERTAIN",
                        permissionNeeded = certain && code == "PERMISSION_REQUIRED")
                }
            }
        }
        state = state.copy(phase = SshFilesPhase.CONNECTING, entries = emptyList(), preview = null,
            code = null, permissionNeeded = false, fingerprint = null)
        if (!FilesRuntime.work { connectWorker(token, version) }) {
            token.setup.countDown()
            token.stop("BUSY")
        }
    }

    private fun connectWorker(token: FilesSession, version: Long) {
        try {
            if (!token.active()) { token.reason.compareAndSet(null, "TIMED_OUT"); return }
            val credentials = token.credentials.getAndSet(null) ?: return
            val prepared = try {
                val coordinator = SshAuthenticationCoordinator(app)
                val location = token.location
                if (location == null) coordinator.begin(token.profile, credentials)
                else coordinator.beginRegistered(location, credentials)
            }
            catch (error: Exception) { credentials.close(); throw error }
            if (prepared is SshAuthenticationPreparation.Failed) { token.reason.set(prepared.code.name); return }
            val handle = (prepared as SshAuthenticationPreparation.Ready).handle
            token.handle.set(handle)
            if (!token.active()) { handle.close(); return }
            val resolver = SshAddressResolver(app)
            token.resolver.set(resolver)
            val answer = AtomicReference<SshAddressResult?>()
            val ready = CountDownLatch(1)
            val budget = minOf(5_000L, token.remaining())
            if (budget <= 0) { token.reason.set("TIMED_OUT"); return }
            resolver.resolve(token.profile.peer, budget) { result -> answer.set(result); ready.countDown() }
            if (!ready.await(budget, TimeUnit.MILLISECONDS)) { token.reason.set("TIMED_OUT"); return }
            resolver.close()
            token.resolver.compareAndSet(resolver, null)
            if (!token.active()) return
            when (val resolved = answer.get()) {
                is SshAddressResult.Failed -> token.reason.set(resolved.code.name)
                is SshAddressResult.Resolved -> {
                    val address = resolved.addresses.firstOrNull()
                    if (address == null) { token.reason.set("NO_ADDRESSES"); return }
                    if (SshLocalNetworkPolicy.shouldRequestPermission(app, address) && !permitted()) {
                        token.reason.set("PERMISSION_REQUIRED")
                        publish(token, version) { it.copy(permissionNeeded = true) }
                        return
                    }
                    val remaining = minOf(token.remaining(), handle.expiresAtMillis - SystemClock.elapsedRealtime(), 10_000L)
                    if (remaining <= 0) { token.reason.set("TIMED_OUT"); return }
                    val result = FilesRuntime.factory.connectPassword(token.profile.peer, address, handle.expectedKey,
                        handle.cancellation, handle::admit, establish = handle::establishSession,
                        retired = { proof ->
                            try { handle.releaseSession(proof) }
                            catch (_: Exception) { token.uncertain.set(true) }
                            // Notification must exit before awaitClosed joins it. A separate lane owns that wait.
                            token.stop(if (token.intentional.get()) null else "CONNECTION_CLOSED")
                        }, timeoutMillis = remaining.toInt())
                    when (result) {
                        is SshTransportOpenResult.Failure -> {
                            token.reason.set(result.code.name)
                            // TIMED_OUT can also mean factory awaitClosed exhausted its budget
                            // before a transport was published. Without that transport we cannot
                            // prove its retirement; preserve capacity rather than infer closure.
                            if (result.code in setOf(SshTransportCode.CLEANUP_UNCERTAIN,
                                    SshTransportCode.QUARANTINED, SshTransportCode.TIMED_OUT))
                                token.uncertain.set(true)
                        }
                        is SshTransportOpenResult.Connected -> {
                            token.transport.set(result.transport)
                            if (!token.active()) { result.transport.requestClose(); return }
                            // Authentication has promoted, but the initial directory operation
                            // still owns the worker. Publish its busy state before enabling browsing.
                            publish(token, version) { it.copy(phase = SshFilesPhase.READING, fingerprint = handle.expectedKey.fingerprint) }
                            val initialPath = token.location?.navigationRoot ?: "."
                            val listing = result.transport.files.list(initialPath, timeoutMillis = 3_000)
                            deliverList(token, version, initialPath, listing, token.initialListingEpoch)
                        }
                    }
                }
                null -> token.reason.set("UNAVAILABLE")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            token.reason.set("CANCELLED")
        } catch (_: Exception) {
            token.reason.set("UNAVAILABLE")
        } finally {
            token.resolver.getAndSet(null)?.close()
            token.credentials.getAndSet(null)?.close()
            token.setup.countDown()
            if (token.transport.get() == null || token.cancelled.get()) token.stop(token.reason.get())
        }
    }

    fun list(path: String) {
        main()
        val token = session ?: return
        if (ownerToken == null || state.phase != SshFilesPhase.CONNECTED || token.cancelled.get() || state.registrationPending) return
        invalidateListing()
        val version = generation
        val listing = synchronized(registrationGate) { listingEpoch }
        state = state.copy(phase = SshFilesPhase.READING, preview = null, code = null)
        if (!FilesRuntime.work {
                try { deliverList(token, version, path, checkNotNull(token.transport.get()).files.list(path, timeoutMillis = 3_000), listing) }
                catch (_: Exception) { token.stop("UNAVAILABLE") }
            }) state = state.copy(phase = SshFilesPhase.CONNECTED, code = "BUSY")
    }

    private fun deliverList(token: FilesSession, version: Long, path: String,
        result: SshFilesResult<List<SshFileEntry>>, expectedListingEpoch: Long) {
        when (result) {
            is SshFilesResult.Success -> {
                // Listing is genuine, but metadata-store availability is separate from browsing.
                val catalog = FilesResourceStore(app).load()
                publish(token, version) {
                    val context = if (catalog is FilesResourceResult.Success)
                        listingContext(token, path, catalog.value.generation, expectedListingEpoch) else null
                    it.copy(phase = SshFilesPhase.CONNECTED, path = path, entries = result.value,
                        preview = null, code = null, registrationCandidate = context?.candidate,
                        registrationCode = if (it.registrationUncertain) it.registrationCode
                            else (catalog as? FilesResourceResult.Failure)?.code?.name)
                }
            }
            is SshFilesResult.Failure -> token.stop(result.code.name)
        }
    }

    fun read(entry: SshFileEntry) {
        main()
        val token = session ?: return
        if (ownerToken == null || state.phase != SshFilesPhase.CONNECTED || token.cancelled.get() || state.registrationPending ||
            state.entries.none { it === entry } || entry.kind != SshFileKind.REGULAR) return
        if ((entry.size ?: 0) > PREVIEW_LIMIT) { state = state.copy(code = "PREVIEW_LIMIT"); return }
        val path = if (state.path.endsWith('/')) state.path + entry.name else state.path + "/" + entry.name
        invalidateListing()
        val version = generation
        state = state.copy(phase = SshFilesPhase.READING, preview = null, code = null)
        if (!FilesRuntime.work {
                try {
                    when (val result = checkNotNull(token.transport.get()).files.read(path, PREVIEW_LIMIT, 3_000)) {
                        is SshFilesResult.Failure -> token.stop(result.code.name)
                        is SshFilesResult.Success -> {
                            val bytes = result.value.copyBytes()
                            val preview = try { preview(entry.name, bytes) } finally { bytes.fill(0) }
                            publish(token, version) { it.copy(phase = SshFilesPhase.CONNECTED, preview = preview) }
                        }
                    }
                } catch (_: Exception) { token.stop("UNAVAILABLE") }
            }) state = state.copy(phase = SshFilesPhase.CONNECTED, code = "BUSY")
    }

    private fun publish(token: FilesSession, version: Long, change: (SshFilesSessionState) -> SshFilesSessionState) {
        FilesRuntime.main.post {
            if (!cleared && session === token && generation == version && !token.cancelled.get() &&
                token.handle.get()?.cancellation?.isCancelled != true && token.transport.get()?.ownership?.isRetired != true)
                state = change(state)
        }
    }

    fun disconnect() {
        main()
        invalidateListing()
        val token = session ?: return
        state = state.copy(phase = SshFilesPhase.RETIRING, entries = emptyList(), preview = null, code = null)
        token.intentional.set(true)
        token.stop(null)
    }

    fun pauseAuthentication(owner: Any) {
        main()
        // An obsolete window cannot cancel a replacement owner's newly submitted attempt.
        if (ownerToken !== owner) return
        synchronized(registrationGate) { registrationForeground = false }
        invalidateListing()
        // UI publication is the visibility boundary. A pause winning before Connected cancels
        // even a just-promoted worker proof; its late publication is fenced by token cancellation.
        if (state.phase == SshFilesPhase.CONNECTING) disconnect()
    }

    fun permissionResult() {
        main()
        // Permission completion never replays credentials, resolution or connection.
        if (session == null) state = state.copy(permissionNeeded = false, code = "EXPLICIT_CONNECT_REQUIRED")
    }

    override fun onCleared() {
        cleared = true
        ownerToken = null
        synchronized(registrationGate) { registrationOwner = null; registrationForeground = false }
        invalidateListing()
        clearHandoff()
        session?.intentional?.set(true)
        session?.stop(null)
        super.onCleared()
    }

    companion object {
        private const val CONFIGURATION_HANDOFF_MILLIS = 5_000L
        private const val REGISTRATION_LIFETIME_MILLIS = 30_000L
        private const val PREVIEW_LIMIT = 65_536
        private fun preview(name: String, bytes: ByteArray): SshFilesPreview {
            val decoded = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) { null }
            val binary = decoded == null || decoded.any { it == '\u0000' || (it < ' ' && it != '\n' && it != '\r' && it != '\t') }
            val text = if (!binary) checkNotNull(decoded) else bytes.take(256).joinToString(" ") {
                (it.toInt() and 255).toString(16).padStart(2, '0')
            }
            return SshFilesPreview(name, bytes.size, text, binary)
        }
    }
}

/** One process-owned factory survives Activity/ViewModel replacement, including quarantine. */
private object FilesRuntime {
    val main = Handler(Looper.getMainLooper())
    val factory = SshAuthenticatedTransportFactory()
    val current = AtomicReference<FilesSession?>()
    val quarantined = AtomicBoolean(false)
    private val worker = lane("CloudySshFiles")
    // Only already-cancelled retirement work may queue; credential/connection work never does.
    private val retirement = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1),
        { task -> Thread(task, "CloudySshFilesRetire").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private fun lane(name: String) = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, SynchronousQueue(),
        { task -> Thread(task, name).apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    fun work(action: () -> Unit): Boolean = try { worker.execute(action); true } catch (_: RejectedExecutionException) { false }
    fun claim(token: FilesSession) = !quarantined.get() && current.compareAndSet(null, token)
    fun retire(token: FilesSession) {
        if (!token.retirementStarted.compareAndSet(false, true)) return
        try { retirement.execute { token.retire() } }
        catch (_: RejectedExecutionException) {
            quarantined.set(true)
            token.finished?.invoke("CLEANUP_UNCERTAIN", false)
        }
    }
}

private class FilesSession(val profile: SshObservationProfile, credentials: SshPasswordCredentials,
    val deadline: Long, val location: FilesResourceRegistration?, val initialListingEpoch: Long) {
    val credentials = AtomicReference<SshPasswordCredentials?>(credentials)
    val handle = AtomicReference<SshAuthenticationHandle?>()
    val resolver = AtomicReference<SshAddressResolver?>()
    val transport = AtomicReference<SshAuthenticatedTransport?>()
    val setup = CountDownLatch(1)
    val cancelled = AtomicBoolean(false)
    val intentional = AtomicBoolean(false)
    val uncertain = AtomicBoolean(false)
    val reason = AtomicReference<String?>()
    val retirementStarted = AtomicBoolean(false)
    @Volatile var stopping: (() -> Unit)? = null
    @Volatile var finished: ((String?, Boolean) -> Unit)? = null
    fun remaining() = deadline - SystemClock.elapsedRealtime()
    fun active() = !cancelled.get() && remaining() > 0 && handle.get()?.cancellation?.isCancelled != true
    fun stop(code: String?) {
        if (code != null) reason.compareAndSet(null, code)
        if (cancelled.compareAndSet(false, true)) stopping?.invoke()
        credentials.getAndSet(null)?.close()
        handle.get()?.close()
        resolver.get()?.close()
        transport.get()?.requestClose()
        FilesRuntime.retire(this)
    }
    fun retire() {
        var certain = true
        try {
            if (!setup.await(18, TimeUnit.SECONDS)) certain = false
            // This lane is never the engine's notification publisher or main thread.
            val connected = transport.get()
            connected?.requestClose()
            if (connected != null && connected.awaitClosed(3_000) != SshTransportCloseResult.Retired) certain = false
            if (handle.get()?.cancellation?.isCancelled == false) handle.get()?.close()
        } catch (_: InterruptedException) { Thread.currentThread().interrupt(); certain = false }
        catch (_: Exception) { certain = false }
        certain = certain && !uncertain.get()
        if (!certain) FilesRuntime.quarantined.set(true)
        else FilesRuntime.current.compareAndSet(this, null)
        finished?.invoke(if (intentional.get()) null else reason.get(), certain)
    }
}
