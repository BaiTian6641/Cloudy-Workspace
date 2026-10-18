package io.github.baitian6641.cloudy.platform

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
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
import io.github.baitian6641.cloudy.ssh.SshFileDigest
import io.github.baitian6641.cloudy.ssh.SshTransferCancellation
import io.github.baitian6641.cloudy.ssh.SshFiles
import io.github.baitian6641.cloudy.ssh.SshFileEditor
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import io.github.baitian6641.cloudy.ssh.SshTransportCloseResult
import io.github.baitian6641.cloudy.ssh.SshTransportCode
import io.github.baitian6641.cloudy.ssh.SshTransportOpenResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

internal enum class SshFilesPhase { IDLE, LOADING, CONNECTING, CONNECTED, READING, MUTATING, RETIRING, FAILED }

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

internal enum class SshFilesMutationKind { DELETE_FILE, DELETE_DIRECTORY, OVERWRITE_RENAME }

/** Presentation copy only; the original bytes/version stay in the model-owned editor context. */
internal class SshEditorView internal constructor(
    val name: String, val path: String, val text: String, val dirty: Boolean, val busy: Boolean,
    val conflict: Boolean, val saveConsent: Boolean, val discardConsent: Boolean, val code: String?,
) {
    internal fun next(text: String = this.text, dirty: Boolean = this.dirty, busy: Boolean = this.busy,
                      conflict: Boolean = this.conflict, saveConsent: Boolean = this.saveConsent,
                      discardConsent: Boolean = this.discardConsent, code: String? = this.code) =
        SshEditorView(name, path, text, dirty, busy, conflict, saveConsent, discardConsent, code)
    override fun toString() = "SshEditorView(redacted)"
}

/** Exact model-issued consent for one destructive/overwrite action on the current listing. */
internal class SshFilesMutationConsent internal constructor(
    val kind: SshFilesMutationKind, val parentPath: String, val entry: SshFileEntry,
    val newName: String?, val targetPath: String?, val epoch: Long,
) { override fun toString() = "SshFilesMutationConsent(redacted)" }

internal enum class SshTransferKind { UPLOAD, DOWNLOAD }

/** Pending upload of an already-verified private remote temp to an existing target. */
internal class SshUploadConsent internal constructor(
    val targetPath: String, val tempPath: String, val recordId: String, val byteCount: Long, val sha256: String,
) { override fun toString() = "SshUploadConsent(redacted)" }

internal class SshTransferView internal constructor(
    val kind: SshTransferKind, val name: String, val busy: Boolean, val canCancel: Boolean,
    val bytes: Long?, val sha256: String?, val code: String?, val consent: SshUploadConsent?,
) {
    internal fun next(name: String = this.name, busy: Boolean = this.busy, canCancel: Boolean = this.canCancel,
                      bytes: Long? = this.bytes, sha256: String? = this.sha256, code: String? = this.code,
                      consent: SshUploadConsent? = this.consent) =
        SshTransferView(kind, name, busy, canCancel, bytes, sha256, code, consent)
    override fun toString() = "SshTransferView(${kind.name},redacted)"
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
    val mutationConsent: SshFilesMutationConsent? = null,
    val editor: SshEditorView? = null,
    val transfers: List<FilesTransferRecord> = emptyList(),
    val transfer: SshTransferView? = null,
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
    private class EditorContext(
        val profileId: String, val profileRevision: Int, val parentPath: String,
        val document: SshFileEditor.Document, val originalText: String,
    ) {
        var staged: SshFileEditor.Staged? = null
        var recordId: String? = null
    }
    private var editorContext: EditorContext? = null
    @Volatile private var transferCancellation: SshTransferCancellation? = null
    @Volatile private var transferProfileId: String = ""


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

    fun onOwnerStopped(token: Any, changingConfigurations: Boolean, pickerInFlight: Boolean = false) {
        main()
        if (cleared || ownerToken !== token) return
        ownerToken = null
        synchronized(registrationGate) { registrationOwner = null; registrationForeground = false }
        invalidateListing()
        clearHandoff()
        // A document picker launches another Activity and stops this owner. That bounded,
        // explicit handoff may preserve the live session until the result (or timeout);
        // every other stop keeps the existing disconnect-on-stop ownership policy.
        val preserve = (changingConfigurations || pickerInFlight) &&
            state.phase in setOf(SshFilesPhase.CONNECTED, SshFilesPhase.READING)
        if (!preserve) {
            disconnect()
            return
        }
        val timeoutMillis = if (pickerInFlight) PICKER_HANDOFF_MILLIS else CONFIGURATION_HANDOFF_MILLIS
        val epoch = attachmentEpoch
        handoffDeadline = SystemClock.elapsedRealtime() + timeoutMillis
        val timeout = Runnable {
            if (!cleared && ownerToken == null && attachmentEpoch == epoch) {
                clearHandoff()
                disconnect()
            }
        }
        handoffTimeout = timeout
        FilesRuntime.main.postDelayed(timeout, timeoutMillis)
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
        loadTransfers()
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
                            selected is FilesResourceResult.Success && catalog is ConnectionProfilesResult.Success -> {
                                state = state.copy(catalog = catalog.value, selected = selected.value.profile,
                                    location = selected.value, path = selected.value.navigationRoot, phase = SshFilesPhase.IDLE)
                                loadTransfers()
                                state
                            }
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
        state = state.copy(registrationCandidate = null, mutationConsent = null)
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
        FilesPositiveObservation.record(FilesPositiveObservation.Event.SESSION_CLAIMED, this, token)
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
                    if (code == null && certain) FilesPositiveObservation.record(
                        FilesPositiveObservation.Event.SAME_SESSION_IDLE, this, token)
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
                            FilesPositiveObservation.record(FilesPositiveObservation.Event.TRANSPORT_ESTABLISHED, token)
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
        if (ownerToken == null || state.phase != SshFilesPhase.CONNECTED || token.cancelled.get() ||
            state.registrationPending || state.editor != null || state.transfer?.busy == true) return
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
                val transfers = journalRecords(token.profile.profile.id)
                publish(token, version) {
                    val context = if (catalog is FilesResourceResult.Success)
                        listingContext(token, path, catalog.value.generation, expectedListingEpoch) else null
                    it.copy(phase = SshFilesPhase.CONNECTED, path = path, entries = result.value,
                        preview = null, code = null, registrationCandidate = context?.candidate,
                        registrationCode = if (it.registrationUncertain) it.registrationCode
                            else (catalog as? FilesResourceResult.Failure)?.code?.name,
                        transfers = transfers)
                }
            }
            is SshFilesResult.Failure -> token.stop(result.code.name)
        }
    }

    fun read(entry: SshFileEntry) {
        main()
        val token = session ?: return
        if (ownerToken == null || state.phase != SshFilesPhase.CONNECTED || token.cancelled.get() ||
            state.registrationPending || state.editor != null || state.transfer?.busy == true ||
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

    private fun mutationReady(token: FilesSession) = ownerToken != null &&
        state.phase == SshFilesPhase.CONNECTED && !token.cancelled.get() && !state.registrationPending &&
        state.mutationConsent == null && state.editor == null && state.transfer?.busy != true

    private fun invalidLeafName(name: String): Boolean = name.isEmpty() ||
        name.length > SshFiles.MAX_NAME_UNITS || name == "." || name == ".." ||
        name.any { it == '/' || it == '\\' || Character.isISOControl(it) }

    // Remote protocol path only; never a local File or destination normalization.
    private fun remoteChildPath(parent: String, name: String) = when {
        parent == "/" -> "/$name"
        parent.endsWith('/') -> parent + name
        else -> "$parent/$name"
    }

    /** Explicit new directory; no overwrite or implicit destination exists. */
    fun createDirectory(name: String) {
        main()
        val token = session ?: return
        if (!mutationReady(token)) return
        if (invalidLeafName(name)) { state = state.copy(code = "INVALID_INPUT"); return }
        val path = state.path
        startMutation(token, path) { transport ->
            transport.files.mkdir(remoteChildPath(path, name), timeoutMillis = 3_000)
        }
    }

    /** Consent is always required before deletion; directory removal never recurses. */
    fun requestDelete(entry: SshFileEntry) {
        main()
        val token = session ?: return
        if (!mutationReady(token) || state.entries.none { it === entry }) return
        state = state.copy(mutationConsent = SshFilesMutationConsent(
            if (entry.kind == SshFileKind.DIRECTORY) SshFilesMutationKind.DELETE_DIRECTORY
            else SshFilesMutationKind.DELETE_FILE,
            state.path, entry, null, null, synchronized(registrationGate) { listingEpoch }), code = null)
    }

    /**
     * Attempts the non-overwriting rename first. A real ALREADY_EXISTS preserves the exact entry
     * and target in an explicit replace consent; no destination is replaced implicitly.
     */
    fun rename(entry: SshFileEntry, newName: String) {
        main()
        val token = session ?: return
        if (!mutationReady(token) || state.entries.none { it === entry } || invalidLeafName(newName)) {
            state = state.copy(code = "INVALID_INPUT")
            return
        }
        invalidateListing()
        val version = generation
        val parentPath = state.path
        val epoch = synchronized(registrationGate) { listingEpoch }
        val from = remoteChildPath(parentPath, entry.name)
        val to = remoteChildPath(parentPath, newName)
        state = state.copy(phase = SshFilesPhase.MUTATING, code = null)
        if (!FilesRuntime.work {
                val transport = token.transport.get()
                if (transport == null) {
                    publish(token, version) { it.copy(phase = SshFilesPhase.CONNECTED, code = "UNAVAILABLE") }
                    return@work
                }
                when (val result = transport.files.rename(from, to, overwrite = false, timeoutMillis = 3_000)) {
                    is SshFilesResult.Success -> refreshAfterMutation(token, version, parentPath, transport)
                    is SshFilesResult.Failure -> publish(token, version) {
                        if (result.code == SshTransportCode.ALREADY_EXISTS) it.copy(phase = SshFilesPhase.CONNECTED,
                            code = null, mutationConsent = if (synchronized(registrationGate) { listingEpoch } == epoch)
                                SshFilesMutationConsent(SshFilesMutationKind.OVERWRITE_RENAME,
                                    parentPath, entry, newName, to, epoch) else null)
                        else it.copy(phase = SshFilesPhase.CONNECTED, code = result.code.name)
                    }
                }
            }) state = state.copy(phase = SshFilesPhase.CONNECTED, code = "BUSY")
    }

    fun confirmMutation() {
        main()
        val consent = state.mutationConsent ?: return
        state = state.copy(mutationConsent = null)
        val token = session ?: return
        val currentEpoch = synchronized(registrationGate) { listingEpoch }
        if (!mutationReady(token) || consent.epoch != currentEpoch || state.path != consent.parentPath ||
            state.entries.none { it === consent.entry }) {
            state = state.copy(code = "INVALID_STATE")
            return
        }
        val parentPath = consent.parentPath
        val source = remoteChildPath(parentPath, consent.entry.name)
        when (consent.kind) {
            SshFilesMutationKind.DELETE_FILE -> startMutation(token, parentPath) { transport ->
                transport.files.removeFile(source, timeoutMillis = 3_000)
            }
            SshFilesMutationKind.DELETE_DIRECTORY -> startMutation(token, parentPath) { transport ->
                transport.files.removeEmptyDirectory(source, timeoutMillis = 3_000)
            }
            SshFilesMutationKind.OVERWRITE_RENAME -> {
                val target = consent.targetPath ?: return
                startMutation(token, parentPath) { transport ->
                    transport.files.rename(source, target, overwrite = true, timeoutMillis = 3_000)
                }
            }
        }
    }

    fun cancelMutation() {
        main()
        state = state.copy(mutationConsent = null)
    }

    private fun startMutation(token: FilesSession, path: String,
                              action: (SshAuthenticatedTransport) -> SshFilesResult<Unit>) {
        invalidateListing()
        val version = generation
        state = state.copy(phase = SshFilesPhase.MUTATING, code = null)
        if (!FilesRuntime.work {
                val transport = token.transport.get()
                if (transport == null) {
                    publish(token, version) { it.copy(phase = SshFilesPhase.CONNECTED, code = "UNAVAILABLE") }
                    return@work
                }
                when (val result = action(transport)) {
                    is SshFilesResult.Success -> refreshAfterMutation(token, version, path, transport)
                    is SshFilesResult.Failure -> publish(token, version) {
                        it.copy(phase = SshFilesPhase.CONNECTED, code = result.code.name)
                    }
                }
            }) state = state.copy(phase = SshFilesPhase.CONNECTED, code = "BUSY")
    }

    /** Re-lists the same directory after a committed mutation. A listing failure is visible but
     * does not invent a mutation result or retire a transport that the engine still retains. */
    private fun refreshAfterMutation(token: FilesSession, version: Long, path: String,
                                     transport: SshAuthenticatedTransport) {
        when (val listed = transport.files.list(path, timeoutMillis = 3_000)) {
            is SshFilesResult.Success -> publish(token, version) {
                it.copy(phase = SshFilesPhase.CONNECTED, path = path, entries = listed.value,
                    preview = null, code = null, registrationCandidate = null, mutationConsent = null)
            }
            is SshFilesResult.Failure -> publish(token, version) {
                it.copy(phase = SshFilesPhase.CONNECTED, code = listed.code.name)
            }
        }
    }

    private fun fileActionReady(token: FilesSession) = ownerToken != null &&
        state.phase == SshFilesPhase.CONNECTED && !token.cancelled.get() && !state.registrationPending &&
        state.mutationConsent == null && state.editor == null && state.transfer?.busy != true && state.transfer?.busy != true && state.editor == null

    private fun journalRecords(profileId: String): List<FilesTransferRecord> {
        val loaded = FilesTransferJournal(FilesTransferJournal.appRoot(app)).load()
        return (loaded as? FilesJournalResult.Success)?.value
            ?.filter { it.profileId == profileId }?.takeLast(TRANSFER_ROWS)?.reversed().orEmpty()
    }

    /** Loads persisted status only; no record is replayed or resumed. */
    fun loadTransfers() {
        main()
        val profile = state.selected?.profile ?: return
        val id = profile.id
        val version = generation
        FilesRuntime.work {
            val records = journalRecords(id)
            FilesRuntime.main.post {
                if (!cleared && generation == version && state.selected?.profile?.id == id)
                    state = state.copy(transfers = records)
            }
        }
    }

    private fun decodeEditorText(bytes: ByteArray): String? {
        val decoded = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { return null }
        return decoded.takeUnless { text -> text.any { it == '\u0000' || (it < ' ' && it != '\n' && it != '\r' && it != '\t') } }
    }

    fun edit(entry: SshFileEntry) {
        main()
        val token = session ?: return
        if (!fileActionReady(token) || state.entries.none { it === entry } || entry.kind != SshFileKind.REGULAR) return
        if ((entry.size ?: 0) > SshFileEditor.MAX_EDIT_BYTES) {
            state = state.copy(code = "EDIT_LIMIT")
            return
        }
        val profile = state.selected?.profile ?: return
        val path = remoteChildPath(state.path, entry.name)
        invalidateListing()
        val version = generation
        state = state.copy(phase = SshFilesPhase.READING, code = null)
        if (!FilesRuntime.work {
                val transport = token.transport.get()
                if (transport == null) {
                    publish(token, version) { it.copy(phase = SshFilesPhase.CONNECTED, code = "UNAVAILABLE") }
                    return@work
                }
                when (val opened = SshFileEditor(transport.files).open(path, SshFileEditor.MAX_EDIT_BYTES, 3_000)) {
                    is SshFilesResult.Failure -> publish(token, version) {
                        it.copy(phase = SshFilesPhase.CONNECTED, code = opened.code.name)
                    }
                    is SshFilesResult.Success -> {
                        val bytes = opened.value.bytes.copyBytes()
                        val text = try { decodeEditorText(bytes) } finally { bytes.fill(0) }
                        if (text == null) {
                            publish(token, version) { it.copy(phase = SshFilesPhase.CONNECTED, code = "NOT_TEXT") }
                        } else {
                            publish(token, version) {
                                editorContext = EditorContext(profile.id, profile.revision.toInt(), it.path, opened.value, text)
                                it.copy(phase = SshFilesPhase.CONNECTED, code = null,
                                    editor = SshEditorView(entry.name, path, text, false, false, false, false, false, null))
                            }
                        }
                    }
                }
            }) state = state.copy(phase = SshFilesPhase.CONNECTED, code = "BUSY")
    }

    fun updateEditor(text: String) {
        main()
        val view = state.editor ?: return
        val context = editorContext ?: return
        if (view.busy || view.saveConsent || view.discardConsent || view.conflict) return
        if (text.toByteArray(Charsets.UTF_8).size > SshFileEditor.MAX_EDIT_BYTES) {
            state = state.copy(editor = view.next(code = "EDIT_LIMIT"))
            return
        }
        state = state.copy(editor = view.next(text = text, dirty = text != context.originalText, code = null))
    }

    fun requestSave() {
        main()
        val view = state.editor ?: return
        if (!view.dirty || view.busy || view.conflict) return
        state = state.copy(editor = view.next(saveConsent = true, code = null))
    }

    fun cancelSaveConsent() {
        main()
        state = state.copy(editor = state.editor?.next(saveConsent = false))
    }

    /** Every save is an explicit remote replace; conflict approval is a second, separate decision. */
    fun confirmSave() {
        main()
        val token = session ?: return
        val view = state.editor ?: return
        val context = editorContext ?: return
        if (!view.saveConsent || view.busy || token.cancelled.get() || state.registrationPending) return
        val text = view.text
        state = state.copy(editor = view.next(saveConsent = false, busy = true, code = null))
        if (!FilesRuntime.work {
                saveWorker(token, context, text)
            }) state = state.copy(editor = state.editor?.next(busy = false, code = "BUSY"))
    }

    private fun saveWorker(token: FilesSession, context: EditorContext, text: String) {
        val version = generation
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > SshFileEditor.MAX_EDIT_BYTES) {
            publish(token, version) { it.copy(editor = it.editor?.next(busy = false, code = "EDIT_LIMIT")) }
            return
        }
        val transport = token.transport.get()
        if (transport == null) {
            publish(token, version) { it.copy(editor = it.editor?.next(busy = false, code = "UNAVAILABLE")) }
            return
        }
        val editor = SshFileEditor(transport.files)
        val staged = when (val result = editor.stage(context.document, bytes)) {
            is SshFilesResult.Failure -> {
                publish(token, version) { it.copy(editor = it.editor?.next(busy = false, code = result.code.name)) }
                return
            }
            is SshFilesResult.Success -> result.value
        }
        val journal = FilesTransferJournal(FilesTransferJournal.appRoot(app))
        val begun = journal.begin(context.profileId, context.profileRevision, context.document.path,
            staged.tempPath, staged.byteCount)
        if (begun is FilesJournalResult.Failure) {
            val removed = editor.discard(staged)
            val uncertain = removed is SshFilesResult.Failure && removed.code != SshTransportCode.NOT_FOUND
            publish(token, version) { it.copy(editor = it.editor?.next(busy = false,
                code = if (uncertain) "CLEANUP_UNCERTAIN" else "JOURNAL_${begun.code.name}")) }
            return
        }
        commitStaged(token, context, editor, staged, (begun as FilesJournalResult.Success).value.id, false, journal)
    }

    fun confirmConflict() {
        main()
        val token = session ?: return
        val view = state.editor ?: return
        val context = editorContext ?: return
        val staged = context.staged ?: return
        val recordId = context.recordId ?: return
        if (view.busy) return
        state = state.copy(editor = view.next(busy = true, conflict = false, saveConsent = false, code = null))
        if (!FilesRuntime.work {
                val transport = token.transport.get()
                if (transport == null) {
                    publish(token, generation) { it.copy(editor = it.editor?.next(busy = false, code = "UNAVAILABLE")) }
                    return@work
                }
                commitStaged(token, context, SshFileEditor(transport.files), staged, recordId, true,
                    FilesTransferJournal(FilesTransferJournal.appRoot(app)))
            }) state = state.copy(editor = state.editor?.next(busy = false, code = "BUSY"))
    }

    private fun commitStaged(token: FilesSession, context: EditorContext, editor: SshFileEditor,
                             staged: SshFileEditor.Staged, recordId: String, replaceChanged: Boolean,
                             journal: FilesTransferJournal) {
        val version = generation
        val pending = journal.transition(recordId, FilesTransferState.COMMITTING, replaceChanged)
        if (pending is FilesJournalResult.Failure) {
            val removed = editor.discard(staged)
            val uncertain = removed is SshFilesResult.Failure && removed.code != SshTransportCode.NOT_FOUND
            publish(token, version) { it.copy(editor = it.editor?.next(busy = false, conflict = false,
                code = if (uncertain) "CLEANUP_UNCERTAIN" else "JOURNAL_${pending.code.name}")) }
            return
        }
        when (val result = editor.commit(staged, replaceChanged, 3_000)) {
            is SshFileEditor.CommitResult.Committed -> {
                val logged = journal.transition(recordId, FilesTransferState.COMMITTED, replaceChanged)
                val editedName = context.document.path.substringAfterLast('/')
                val committedSize = result.version?.size
                val records = journalRecords(context.profileId)
                publish(token, version) {
                    editorContext = null
                    it.copy(phase = SshFilesPhase.CONNECTED, path = context.parentPath, editor = null,
                        entries = it.entries.map { entry ->
                            if (entry.name == editedName && entry.kind == SshFileKind.REGULAR)
                                entry.withSize(committedSize ?: entry.size) else entry
                        },
                        preview = null, code = if (logged is FilesJournalResult.Failure) "JOURNAL_UNAVAILABLE" else null,
                        transfers = records)
                }
            }
            is SshFileEditor.CommitResult.Conflict -> {
                journal.transition(recordId, FilesTransferState.CONFLICT, replaceChanged)
                val records = journalRecords(context.profileId)
                publish(token, version) {
                    editorContext?.apply { this.staged = staged; this.recordId = recordId }
                    it.copy(editor = it.editor?.next(busy = false, conflict = true, saveConsent = false, code = "CONFLICT"),
                        transfers = records)
                }
            }
            is SshFileEditor.CommitResult.Failure -> {
                journal.transition(recordId, FilesTransferState.FAILED, replaceChanged)
                val removed = editor.discard(staged)
                val uncertain = removed is SshFilesResult.Failure && removed.code != SshTransportCode.NOT_FOUND
                if (uncertain) journal.transition(recordId, FilesTransferState.UNCERTAIN, replaceChanged)
                val records = journalRecords(context.profileId)
                publish(token, version) {
                    editorContext?.apply { this.staged = null; this.recordId = null }
                    it.copy(editor = it.editor?.next(busy = false, conflict = false,
                        code = if (uncertain) "CLEANUP_UNCERTAIN" else result.code.name), transfers = records)
                }
            }
        }
    }

    /** Cancels a conflict overwrite; the edited text stays available for another save. */
    fun discardStaged() {
        main()
        val token = session ?: return
        val view = state.editor ?: return
        val context = editorContext ?: return
        val staged = context.staged ?: return
        val recordId = context.recordId
        if (view.busy) return
        state = state.copy(editor = view.next(busy = true, conflict = false, code = null))
        if (!FilesRuntime.work {
                val transport = token.transport.get()
                val journal = FilesTransferJournal(FilesTransferJournal.appRoot(app))
                if (recordId != null) journal.transition(recordId, FilesTransferState.FAILED, false)
                val removed = transport?.files?.let { SshFileEditor(it).discard(staged) }
                val uncertain = removed == null || (removed is SshFilesResult.Failure && removed.code != SshTransportCode.NOT_FOUND)
                if (uncertain && recordId != null) journal.transition(recordId, FilesTransferState.UNCERTAIN, false)
                val records = journalRecords(context.profileId)
                publish(token, generation) {
                    editorContext?.apply { this.staged = null; this.recordId = null }
                    it.copy(editor = it.editor?.next(busy = false, conflict = false,
                        code = if (uncertain) "CLEANUP_UNCERTAIN" else null), transfers = records)
                }
            }) state = state.copy(editor = state.editor?.next(busy = false, code = "BUSY"))
    }

    fun requestCloseEditor() {
        main()
        val view = state.editor ?: return
        val context = editorContext ?: return
        if (view.busy) return
        if (view.dirty || context.staged != null) {
            state = state.copy(editor = view.next(discardConsent = true, saveConsent = false, conflict = false))
        } else closeEditorNow()
    }

    fun cancelCloseEditor() {
        main()
        state = state.copy(editor = state.editor?.next(discardConsent = false))
    }

    fun confirmCloseEditor() {
        main()
        val token = session ?: return
        val view = state.editor ?: return
        val context = editorContext ?: return
        if (!view.discardConsent) return
        val staged = context.staged
        if (staged == null) { closeEditorNow(); return }
        state = state.copy(editor = view.next(discardConsent = false, busy = true, code = null))
        if (!FilesRuntime.work {
                val journal = FilesTransferJournal(FilesTransferJournal.appRoot(app))
                context.recordId?.let { journal.transition(it, FilesTransferState.FAILED, false) }
                val removed = token.transport.get()?.files?.let { SshFileEditor(it).discard(staged) }
                val uncertain = removed == null || (removed is SshFilesResult.Failure && removed.code != SshTransportCode.NOT_FOUND)
                if (uncertain) context.recordId?.let { journal.transition(it, FilesTransferState.UNCERTAIN, false) }
                val records = journalRecords(context.profileId)
                publish(token, generation) {
                    editorContext = null
                    it.copy(editor = null, code = if (uncertain) "CLEANUP_UNCERTAIN" else null, transfers = records)
                }
            }) state = state.copy(editor = state.editor?.next(busy = false, code = "BUSY"))
    }

    private fun closeEditorNow() {
        editorContext = null
        state = state.copy(editor = null)
    }

    private fun transferReady(token: FilesSession) = fileActionReady(token) &&
        state.transfer?.busy != true && state.transfer?.consent == null

    private fun transferLeaf(raw: String?): String {
        val candidate = raw?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        return if (candidate.isEmpty() || candidate.length > SshFiles.MAX_NAME_UNITS ||
            candidate == "." || candidate == ".." || candidate.any { it == '/' || it == '\\' || Character.isISOControl(it) })
            "upload.bin" else candidate
    }

    private fun digestFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32_768)
        try {
            FileInputStream(file).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (count <= 0) throw IllegalStateException()
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        } finally { buffer.fill(0) }
    }

    private fun discardTemp(transport: SshAuthenticatedTransport, path: String): Boolean {
        val result = transport.files.removeFile(path, 3_000)
        return result is SshFilesResult.Success || (result is SshFilesResult.Failure && result.code == SshTransportCode.NOT_FOUND)
    }

    fun upload(sourceUri: String) {
        main()
        val token = session ?: return
        if (!transferReady(token)) return
        val uri = runCatching { sourceUri.toUri() }.getOrNull()
        if (uri == null || uri.scheme != "content") {
            state = state.copy(transfer = SshTransferView(SshTransferKind.UPLOAD, "upload",
                false, false, null, null, "INVALID_INPUT", null))
            return
        }
        val profile = state.selected?.profile ?: return
        val parent = state.path
        val cancellation = SshTransferCancellation()
        transferCancellation = cancellation
        transferProfileId = profile.id
        state = state.copy(transfer = SshTransferView(SshTransferKind.UPLOAD, "upload", true, true, null, null, null, null))
        if (!FilesRuntime.work {
                try { uploadWorker(token, profile.id, profile.revision.toInt(), parent, uri, cancellation) }
                catch (_: Exception) { finishTransfer(token, generation, "UNAVAILABLE") }
            }) {
            transferCancellation = null
            state = state.copy(transfer = state.transfer?.next(busy = false, canCancel = false, code = "BUSY"))
        }
    }

    private fun contentLeaf(uri: Uri): String {
        val fromProvider = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return transferLeaf(fromProvider ?: uri.lastPathSegment)
    }

    private fun uploadWorker(token: FilesSession, profileId: String, revision: Int, parent: String,
                             uri: Uri, cancellation: SshTransferCancellation) {
        val version = generation
        val transport = token.transport.get()
        if (transport == null) {
            finishTransfer(token, version, "UNAVAILABLE")
            return
        }
        val name = contentLeaf(uri)
        val target = remoteChildPath(parent, name)
        val temp = remoteChildPath(parent, ".cloudy-upload-${UUID.randomUUID().toString().replace("-", "").take(16)}")
        publish(token, version) { it.copy(transfer = it.transfer?.next(name = name)) }
        val journal = FilesTransferJournal(FilesTransferJournal.appRoot(app))
        // Journal intent before bytes leave the source; 0 means the byte count is not known yet.
        val begun = journal.begin(profileId, revision, target, temp, 0)
        if (begun is FilesJournalResult.Failure) {
            finishTransfer(token, version, "JOURNAL_${begun.code.name}")
            return
        }
        val recordId = (begun as FilesJournalResult.Success).value.id
        val input = try { app.contentResolver.openInputStream(uri) } catch (_: Exception) { null }
        if (input == null) {
            journal.transition(recordId, FilesTransferState.FAILED, false)
            finishTransfer(token, version, "SOURCE_UNAVAILABLE")
            return
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val counted = DigestInputStream(input, digest)
        val written = try {
            when (val result = transport.files.writeFrom(temp, counted, SshFiles.MAX_STREAM_BYTES,
                cancellation, 30_000)) {
                is SshFilesResult.Failure -> {
                    journal.transition(recordId, FilesTransferState.FAILED, false)
                    finishTransfer(token, version, result.code.name)
                    return
                }
                is SshFilesResult.Success -> result.value
            }
        } finally { counted.close() }
        val localHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        val remoteDigest = when (val result = transport.files.digest(temp, SshFiles.MAX_STREAM_BYTES,
            cancellation, 30_000)) {
            is SshFilesResult.Failure -> {
                discardTemp(transport, temp)
                journal.transition(recordId, FilesTransferState.FAILED, false)
                finishTransfer(token, version, result.code.name)
                return
            }
            is SshFilesResult.Success -> result.value
        }
        if (remoteDigest.byteCount != written || remoteDigest.sha256Hex() != localHash) {
            discardTemp(transport, temp)
            journal.transition(recordId, FilesTransferState.FAILED, false)
            finishTransfer(token, version, "INTEGRITY_MISMATCH")
            return
        }
        if (cancellation.isCancelled()) {
            discardTemp(transport, temp)
            journal.transition(recordId, FilesTransferState.FAILED, false)
            finishTransfer(token, version, "CANCELLED")
            return
        }
        journal.transition(recordId, FilesTransferState.STAGED, false, byteCount = written.toInt())
        when (val existing = transport.files.stat(target, 3_000)) {
            is SshFilesResult.Failure -> {
                if (existing.code == SshTransportCode.NOT_FOUND) {
                    commitUpload(token, profileId, parent, name, target, temp, recordId, written, localHash,
                        overwrite = false, copy = false)
                } else {
                    discardTemp(transport, temp)
                    journal.transition(recordId, FilesTransferState.FAILED, false)
                    finishTransfer(token, version, existing.code.name)
                }
            }
            is SshFilesResult.Success -> {
                transferCancellation = null
                val records = journalRecords(profileId)
                publish(token, version) {
                    it.copy(transfer = SshTransferView(SshTransferKind.UPLOAD, name, false, false, written, localHash,
                        null, SshUploadConsent(target, temp, recordId, written, localHash)),
                        transfers = records)
                }
            }
        }
    }

    fun confirmUploadOverwrite() {
        main()
        val token = session ?: return
        val view = state.transfer ?: return
        val consent = view.consent ?: return
        if (view.busy) return
        val profileId = state.selected?.profile?.id ?: return
        val parent = state.path
        state = state.copy(transfer = view.next(busy = true, canCancel = false, consent = null))
        if (!FilesRuntime.work {
                try {
                    commitUpload(token, profileId, parent, transferLeaf(consent.targetPath),
                        consent.targetPath, consent.tempPath, consent.recordId, consent.byteCount, consent.sha256,
                        overwrite = true, copy = false)
                } catch (_: Exception) { finishTransfer(token, generation, "UNAVAILABLE") }
            }) state = state.copy(transfer = state.transfer?.next(busy = false, code = "BUSY"))
    }

    fun confirmUploadCopy() {
        main()
        val token = session ?: return
        val view = state.transfer ?: return
        val consent = view.consent ?: return
        if (view.busy) return
        val profileId = state.selected?.profile?.id ?: return
        val parent = state.path
        state = state.copy(transfer = view.next(busy = true, canCancel = false, consent = null))
        if (!FilesRuntime.work {
                try {
                    commitUpload(token, profileId, parent, transferLeaf(consent.targetPath),
                        consent.targetPath, consent.tempPath, consent.recordId, consent.byteCount, consent.sha256,
                        overwrite = false, copy = true)
                } catch (_: Exception) { finishTransfer(token, generation, "UNAVAILABLE") }
            }) state = state.copy(transfer = state.transfer?.next(busy = false, code = "BUSY"))
    }

    fun cancelUploadConsent() {
        main()
        val token = session ?: return
        val view = state.transfer ?: return
        val consent = view.consent ?: return
        if (view.busy) return
        state = state.copy(transfer = view.next(busy = true, canCancel = false, consent = null))
        if (!FilesRuntime.work {
              try {
                val transport = token.transport.get()
                val journal = FilesTransferJournal(FilesTransferJournal.appRoot(app))
                val removed = transport?.let { discardTemp(it, consent.tempPath) }
                val uncertain = removed != true
                journal.transition(consent.recordId,
                    if (uncertain) FilesTransferState.UNCERTAIN else FilesTransferState.FAILED, false)
                val records = journalRecords(transferProfileId)
                publish(token, generation) {
                    it.copy(transfer = it.transfer?.next(busy = false, consent = null,
                        code = if (uncertain) "CLEANUP_UNCERTAIN" else "CANCELLED"), transfers = records)
                }
              } catch (_: Exception) { finishTransfer(token, generation, "UNAVAILABLE") }
            }) state = state.copy(transfer = state.transfer?.next(busy = false, code = "BUSY"))
    }

    private fun commitUpload(token: FilesSession, profileId: String, parent: String, name: String,
                             target: String, temp: String, recordId: String, bytes: Long, sha256: String,
                             overwrite: Boolean, copy: Boolean) {
        val version = generation
        val transport = token.transport.get()
        if (transport == null) {
            finishTransfer(token, version, "UNAVAILABLE")
            return
        }
        var finalName = name
        var finalPath = target
        if (copy) {
            var found = false
            for (index in 1..8) {
                val candidate = copyName(name, index)
                val candidatePath = remoteChildPath(parent, candidate)
                when (val stat = transport.files.stat(candidatePath, 3_000)) {
                    is SshFilesResult.Failure -> if (stat.code == SshTransportCode.NOT_FOUND) {
                        finalName = candidate; finalPath = candidatePath; found = true
                        break
                    }
                    is SshFilesResult.Success -> {}
                }
            }
            if (!found) {
                discardTemp(transport, temp)
                FilesTransferJournal(FilesTransferJournal.appRoot(app))
                    .transition(recordId, FilesTransferState.FAILED, false)
                finishTransfer(token, version, "COPY_NAME_UNAVAILABLE")
                return
            }
        }
        val journal = FilesTransferJournal(FilesTransferJournal.appRoot(app))
        if (transferCancellation?.isCancelled() == true) {
            discardTemp(transport, temp)
            journal.transition(recordId, FilesTransferState.FAILED, overwrite, path = finalPath)
            finishTransfer(token, version, "CANCELLED")
            return
        }
        val pending = journal.transition(recordId, FilesTransferState.COMMITTING, overwrite,
            byteCount = bytes.toInt(), path = finalPath)
        if (pending is FilesJournalResult.Failure) {
            discardTemp(transport, temp)
            finishTransfer(token, version, "JOURNAL_${pending.code.name}")
            return
        }
        when (val renamed = transport.files.rename(temp, finalPath, overwrite = overwrite && !copy, timeoutMillis = 30_000)) {
            is SshFilesResult.Success -> {
                journal.transition(recordId, FilesTransferState.COMMITTED, overwrite, byteCount = bytes.toInt(), path = finalPath)
                val listed = transport.files.list(parent, 3_000)
                val committed = (transport.files.stat(finalPath, 3_000) as? SshFilesResult.Success)?.value
                val records = journalRecords(profileId)
                publish(token, version) {
                    var entries = (listed as? SshFilesResult.Success)?.value ?: it.entries
                    if (committed != null && committed.kind == SshFileKind.REGULAR) {
                        entries = if (entries.any { entry -> entry.name == finalName && entry.kind == SshFileKind.REGULAR })
                            entries.map { entry ->
                                if (entry.name == finalName && entry.kind == SshFileKind.REGULAR)
                                    entry.withSize(committed.size) else entry
                            }
                        else entries + SshFileEntry.regular(finalName, committed.size)
                    }
                    it.copy(entries = entries,
                        transfer = SshTransferView(SshTransferKind.UPLOAD, finalName, false, false, bytes, sha256,
                            null, null), preview = null, transfers = records, code = null)
                }
                transferCancellation = null
            }
            is SshFilesResult.Failure -> {
                if (!overwrite && !copy && renamed.code == SshTransportCode.ALREADY_EXISTS) {
                    journal.transition(recordId, FilesTransferState.STAGED, false,
                        byteCount = bytes.toInt(), path = target)
                    transferCancellation = null
                    publish(token, version) {
                        it.copy(transfer = SshTransferView(SshTransferKind.UPLOAD, name, false, false, bytes, sha256,
                            null, SshUploadConsent(target, temp, recordId, bytes, sha256)),
                            transfers = journalRecords(profileId))
                    }
                    return
                }
                journal.transition(recordId, FilesTransferState.FAILED, overwrite, path = finalPath)
                val cleaned = discardTemp(transport, temp)
                finishTransfer(token, version, if (!cleaned) "CLEANUP_UNCERTAIN" else renamed.code.name)
            }
        }
    }

    private fun copyName(name: String, index: Int): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        return "$base (copy${if (index == 1) "" else " $index"})$extension"
    }

    fun download(entry: SshFileEntry, targetUri: String) {
        main()
        val token = session ?: return
        if (!transferReady(token) || state.entries.none { it === entry } || entry.kind != SshFileKind.REGULAR) return
        if ((entry.size ?: 0) > SshFiles.MAX_STREAM_BYTES) {
            state = state.copy(transfer = SshTransferView(SshTransferKind.DOWNLOAD, entry.name, false, false,
                null, null, "LIMIT_EXCEEDED", null))
            return
        }
        val uri = runCatching { targetUri.toUri() }.getOrNull()
        if (uri == null || uri.scheme != "content") {
            state = state.copy(transfer = SshTransferView(SshTransferKind.DOWNLOAD, entry.name, false, false,
                null, null, "INVALID_INPUT", null))
            return
        }
        val profile = state.selected?.profile ?: return
        val remote = remoteChildPath(state.path, entry.name)
        val cancellation = SshTransferCancellation()
        transferCancellation = cancellation
        transferProfileId = profile.id
        state = state.copy(transfer = SshTransferView(SshTransferKind.DOWNLOAD, entry.name, true, true, null, null, null, null))
        if (!FilesRuntime.work {
                try { downloadWorker(token, profile.id, profile.revision.toInt(), entry.name, remote, uri, cancellation) }
                catch (_: Exception) { finishTransfer(token, generation, "UNAVAILABLE") }
            }) {
            transferCancellation = null
            state = state.copy(transfer = state.transfer?.next(busy = false, canCancel = false, code = "BUSY"))
        }
    }

    private fun downloadWorker(token: FilesSession, profileId: String, revision: Int, name: String,
                               remote: String, uri: Uri, cancellation: SshTransferCancellation) {
        val version = generation
        val transport = token.transport.get()
        if (transport == null) { finishTransfer(token, version, "UNAVAILABLE"); return }
        val stat = when (val result = transport.files.stat(remote, 3_000)) {
            is SshFilesResult.Failure -> { finishTransfer(token, version, result.code.name); return }
            is SshFilesResult.Success -> result.value
        }
        if (stat.kind != SshFileKind.REGULAR) { finishTransfer(token, version, "INVALID_REMOTE_DATA"); return }
        val expectedSize = stat.size
        if (expectedSize != null && (expectedSize < 0 || expectedSize > SshFiles.MAX_STREAM_BYTES)) {
            finishTransfer(token, version, "LIMIT_EXCEEDED"); return
        }
        val directory = File(app.cacheDir, "cloudy-transfers")
        val stage = File(directory, "${UUID.randomUUID()}.part")
        val journal = FilesTransferJournal(FilesTransferJournal.appRoot(app))
        val begun = journal.begin(profileId, revision, remote, stage.absolutePath,
            (expectedSize ?: 0L).toInt())
        if (begun is FilesJournalResult.Failure) { finishTransfer(token, version, "JOURNAL_${begun.code.name}"); return }
        val recordId = (begun as FilesJournalResult.Success).value.id
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                journal.transition(recordId, FilesTransferState.FAILED, false)
                finishTransfer(token, version, "STAGING_UNAVAILABLE"); return
            }
            val remoteDigest = when (val result = transport.files.digest(remote, SshFiles.MAX_STREAM_BYTES,
                cancellation, 30_000)) {
                is SshFilesResult.Failure -> {
                    journal.transition(recordId, FilesTransferState.FAILED, false)
                    finishTransfer(token, version, result.code.name); return
                }
                is SshFilesResult.Success -> result.value
            }
            val copied = try {
                FileOutputStream(stage).use { output ->
                    val result = transport.files.readTo(remote, output, SshFiles.MAX_STREAM_BYTES, cancellation, 30_000)
                    if (result is SshFilesResult.Failure) {
                        journal.transition(recordId, FilesTransferState.FAILED, false)
                        finishTransfer(token, version, result.code.name); return
                    }
                    output.fd.sync()
                    (result as SshFilesResult.Success).value
                }
            } catch (_: Exception) {
                journal.transition(recordId, FilesTransferState.FAILED, false)
                finishTransfer(token, version, "STAGING_FAILED"); return
            }
            if (copied != remoteDigest.byteCount || digestFile(stage) != remoteDigest.sha256Hex()) {
                stage.delete()
                journal.transition(recordId, FilesTransferState.FAILED, false)
                finishTransfer(token, version, "INTEGRITY_MISMATCH"); return
            }
            journal.transition(recordId, FilesTransferState.COMMITTING, false)
            val output = try { app.contentResolver.openOutputStream(uri, "wt") } catch (_: Exception) {
                try { app.contentResolver.openOutputStream(uri) } catch (_: Exception) { null }
            }
            if (output == null) {
                stage.delete(); journal.transition(recordId, FilesTransferState.FAILED, false)
                finishTransfer(token, version, "DESTINATION_UNAVAILABLE"); return
            }
            val written = try {
                output.use { sink ->
                    FileInputStream(stage).use { input ->
                        val buffer = ByteArray(32_768)
                        var total = 0L
                        while (true) {
                            if (cancellation.isCancelled()) throw java.io.IOException("TRANSFER_CANCELLED")
                            val count = input.read(buffer)
                            if (count == -1) break
                            if (count <= 0) throw java.io.IOException("TRANSFER_SOURCE")
                            total += count
                            if (total > SshFiles.MAX_STREAM_BYTES) throw java.io.IOException("TRANSFER_LIMIT")
                            sink.write(buffer, 0, count)
                        }
                        total
                    }
                }
            } catch (_: Exception) {
                stage.delete()
                journal.transition(recordId, FilesTransferState.FAILED, false)
                finishTransfer(token, version,
                    if (cancellation.isCancelled()) "CANCELLED" else "DESTINATION_WRITE_FAILED")
                return
            }
            if (written != copied) {
                stage.delete(); journal.transition(recordId, FilesTransferState.FAILED, false)
                finishTransfer(token, version, "DESTINATION_WRITE_FAILED"); return
            }
            journal.transition(recordId, FilesTransferState.COMMITTED, false)
            stage.delete()
            publish(token, version) {
                it.copy(transfer = SshTransferView(SshTransferKind.DOWNLOAD, name, false, false, copied,
                    remoteDigest.sha256Hex(), null, null), transfers = journalRecords(profileId),
                    code = null)
            }
            transferCancellation = null
        } finally {
            if (stage.exists()) stage.delete()
        }
    }

    fun cancelTransfer() {
        main()
        transferCancellation?.cancel()
        state = state.copy(transfer = state.transfer?.next(canCancel = false, code = "CANCELLING"))
    }

    private fun finishTransfer(token: FilesSession, version: Long, code: String?) {
        transferCancellation = null
        val records = journalRecords(transferProfileId)
        publish(token, version) {
            it.copy(transfer = it.transfer?.next(busy = false, canCancel = false, code = code), transfers = records)
        }
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
        editorContext = null
        transferCancellation?.cancel()
        transferCancellation = null
        val token = session ?: return
        state = state.copy(phase = SshFilesPhase.RETIRING, entries = emptyList(), preview = null, code = null,
            editor = null, transfer = null)
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
        FilesPositiveObservation.record(FilesPositiveObservation.Event.MODEL_CLEARED, this)
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
        private const val PICKER_HANDOFF_MILLIS = 120_000L
        private const val REGISTRATION_LIFETIME_MILLIS = 30_000L
        private const val PREVIEW_LIMIT = 65_536
        private const val TRANSFER_ROWS = 8
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
        var observedTransportRetired = false
        try {
            if (!setup.await(18, TimeUnit.SECONDS)) certain = false
            // This lane is never the engine's notification publisher or main thread.
            val connected = transport.get()
            connected?.requestClose()
            if (connected != null) {
                val closed = connected.awaitClosed(3_000)
                observedTransportRetired = closed == SshTransportCloseResult.Retired
                if (!observedTransportRetired) certain = false
            }
            if (handle.get()?.cancellation?.isCancelled == false) handle.get()?.close()
        } catch (_: InterruptedException) { Thread.currentThread().interrupt(); certain = false }
        catch (_: Exception) { certain = false }
        certain = certain && !uncertain.get()
        val released = if (!certain) { FilesRuntime.quarantined.set(true); false }
            else FilesRuntime.current.compareAndSet(this, null)
        // Observation success requires the original published transport and actual capacity release.
        // The observer result never changes product certainty or quarantine semantics.
        FilesPositiveObservation.record(if (certain && observedTransportRetired && released)
            FilesPositiveObservation.Event.TRANSPORT_RETIRED else FilesPositiveObservation.Event.RETIREMENT_UNCERTAIN, this)
        finished?.invoke(if (intentional.get()) null else reason.get(), certain)
    }
}
