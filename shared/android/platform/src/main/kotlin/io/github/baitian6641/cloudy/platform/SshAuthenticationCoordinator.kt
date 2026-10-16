package io.github.baitian6641.cloudy.platform

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import io.github.baitian6641.cloudy.domain.*
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedOwnership
import io.github.baitian6641.cloudy.ssh.SshObservationCancellation
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

enum class SshAuthenticationCode {
    WRONG_THREAD, WRONG_OWNER, BUSY, STORE_UNAVAILABLE, PROFILE_CHANGED, TRUST_REQUIRED, CAPACITY, CREDENTIALS_UNAVAILABLE,
}

sealed interface SshAuthenticationPreparation {
    class Ready(val handle: SshAuthenticationHandle) : SshAuthenticationPreparation {
        override fun toString() = "SshAuthenticationPreparation.Ready(redacted)"
    }
    data class Failed(val code: SshAuthenticationCode) : SshAuthenticationPreparation
}

/**
 * Lock order: coordination -> store monitor -> permanent file lock -> SQLite. No network,
 * cryptography or arbitrary callback executes under coordination. Invalidation only cancels
 * the signal and disposes credentials that have not crossed the one-time admission boundary.
 */
internal object SshAuthenticationRegistry {
    val lock = ReentrantLock()
    val attempts: MutableSet<SshAuthenticationHandle> = ConcurrentHashMap.newKeySet()
    // At most one authentication-expiry task per entry; successful promotion cancels that task.
    // Established entries retain capacity until engine-proven retirement, even after cancellation.
    val expiry = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "cloudy-auth-expiry").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    fun invalidateProfile(root: File, owner: WindowOwner, id: String) {
        check(lock.isHeldByCurrentThread)
        attempts.filter { it.root == root.absoluteFile && it.profile.profile.owner == owner && it.profile.profile.id == id }
            .forEach { it.invalidate() }
    }
    fun invalidatePeer(root: File, owner: WindowOwner, peer: SshPeerIdentity) {
        check(lock.isHeldByCurrentThread)
        attempts.filter { it.root == root.absoluteFile && it.profile.profile.owner == owner && it.profile.peer == peer }
            .forEach { it.invalidate() }
    }
}

/** Finite per-session authority; no vault grant, saved password or network operation is created. */
class SshAuthenticationCoordinator private constructor(
    private val root: () -> File,
    private val owner: WindowOwner?,
    private val profiles: ConnectionProfiles,
    private val trust: SshHostTrustStore,
    private val validProcess: () -> Boolean,
    private val onMain: () -> Boolean,
    private val clock: () -> Long,
    private val expiryScheduler: ScheduledThreadPoolExecutor,
) {
    constructor(context: Context) : this(context.applicationContext, context.isDeviceProtectedStorage)

    private constructor(application: Context, suppliedDeviceProtected: Boolean) : this(
        { application.noBackupFilesDir }, owner(application.packageName),
        ConnectionProfiles(application), SshHostTrustStore(application),
        { !suppliedDeviceProtected && !application.isDeviceProtectedStorage &&
            application.applicationInfo.uid == Process.myUid() && Application.getProcessName() == application.packageName },
        { Looper.myLooper() == Looper.getMainLooper() }, SystemClock::elapsedRealtime, SshAuthenticationRegistry.expiry,
    )

    internal constructor(root: File, owner: WindowOwner, profiles: ConnectionProfiles, trust: SshHostTrustStore,
        onMainThread: () -> Boolean = { false },
        expiryScheduler: ScheduledThreadPoolExecutor = SshAuthenticationRegistry.expiry,
        clock: () -> Long = SystemClock::elapsedRealtime) : this(
        { root }, owner, profiles, trust, { true }, onMainThread, clock, expiryScheduler,
    )

    /** Ownership transfers on every outcome, including validation and capacity failures. */
    fun begin(profile: SshObservationProfile, credentials: SshPasswordCredentials): SshAuthenticationPreparation {
        fun failed(code: SshAuthenticationCode): SshAuthenticationPreparation {
            credentials.close()
            return SshAuthenticationPreparation.Failed(code)
        }
        try {
            if (credentials.isClosed) return failed(SshAuthenticationCode.CREDENTIALS_UNAVAILABLE)
            if (onMain()) return failed(SshAuthenticationCode.WRONG_THREAD)
            if (!validProcess() || owner == null || profile.profile.owner != owner) return failed(SshAuthenticationCode.WRONG_OWNER)
        } catch (_: Exception) { return failed(SshAuthenticationCode.STORE_UNAVAILABLE) }
        if (!SshAuthenticationRegistry.lock.tryLock()) return failed(SshAuthenticationCode.BUSY)
        try {
            // A delayed timer (including device suspend) cannot turn expiry into reusable authority.
            SshAuthenticationRegistry.attempts.filter { it.shouldRetire() }.forEach { it.close() }
            if (SshAuthenticationRegistry.attempts.size >= 16) return failed(SshAuthenticationCode.CAPACITY)
            val catalog = (profiles.load() as? ConnectionProfilesResult.Success)?.value
                ?: return failed(SshAuthenticationCode.STORE_UNAVAILABLE)
            val current = catalog.profiles.singleOrNull { it.id == profile.profile.id }
            if (current == null || current.protocol != ConnectionProtocol.SSH ||
                SshObservationProfile.create(catalog.generation, current) != profile) {
                return failed(SshAuthenticationCode.PROFILE_CHANGED)
            }
            val lookup = (trust.lookup(profile.peer) as? SshTrustStoreResult.Success)?.value
                ?: return failed(SshAuthenticationCode.STORE_UNAVAILABLE)
            val snapshot = lookup.current ?: return failed(SshAuthenticationCode.TRUST_REQUIRED)
            if (snapshot.status !is SshTrustStatus.Accepted) return failed(SshAuthenticationCode.TRUST_REQUIRED)
            val now = clock()
            if (now < 0 || now > Long.MAX_VALUE - LIFETIME_MILLIS) return failed(SshAuthenticationCode.STORE_UNAVAILABLE)
            val handle = SshAuthenticationHandle(root().absoluteFile, profile, snapshot, now + LIFETIME_MILLIS,
                credentials, profiles, trust, onMain, clock, expiryScheduler)
            SshAuthenticationRegistry.attempts.add(handle)
            try { handle.armExpiry() } catch (_: Exception) {
                handle.close()
                return failed(SshAuthenticationCode.STORE_UNAVAILABLE)
            }
            return SshAuthenticationPreparation.Ready(handle)
        } catch (_: Exception) {
            return failed(SshAuthenticationCode.STORE_UNAVAILABLE)
        } finally { SshAuthenticationRegistry.lock.unlock() }
    }

    companion object {
        private const val LIFETIME_MILLIS = 45_000L
        private fun owner(packageName: String) = WindowOwner.entries.singleOrNull {
            packageName == when (it) {
                WindowOwner.CONSOLE -> "io.github.baitian6641.cloudy.console"
                WindowOwner.DESKTOP -> "io.github.baitian6641.cloudy.desktop"
            }
        }
    }
}

class SshAuthenticationHandle internal constructor(
    internal val root: File,
    val profile: SshObservationProfile,
    private val expected: SshTrustSnapshot,
    val expiresAtMillis: Long,
    credentials: SshPasswordCredentials,
    private val profiles: ConnectionProfiles,
    private val trust: SshHostTrustStore,
    private val onMain: () -> Boolean,
    private val clock: () -> Long,
    private val expiryScheduler: ScheduledThreadPoolExecutor,
) : AutoCloseable {
    val expectedKey: SshHostKeyIdentity = (expected.status as SshTrustStatus.Accepted).key
    val cancellation = SshObservationCancellation()
    private val ownership = Any()
    private var pending: SshPasswordCredentials? = credentials
    private var expiryTask: ScheduledFuture<*>? = null
    private enum class Phase { PREPARED, ADMITTED, ESTABLISHED, RETIRED }
    @Volatile private var phase = Phase.PREPARED
    private var established: SshAuthenticatedOwnership? = null
    @Volatile private var closed = false

    internal fun shouldRetire(): Boolean = synchronized(ownership) {
        // A hidden/cancelled established transport is still owned. Neither expiry nor another
        // begin may release its capacity while actual socket/Reader/helper retirement is unknown.
        established == null && (closed || cancellation.isCancelled || clock() >= expiresAtMillis)
    }

    internal fun armExpiry() = synchronized(ownership) {
        check(!closed)
        // Publication is serialized with close; scheduling failure is handled before Ready escapes.
        expiryTask = expiryScheduler.schedule({ expireAuthentication() },
            (expiresAtMillis - clock()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
    }

    /** A cancellation winning the signal monitor prevents transfer. After transfer it can only
     * stop transport; bytes already admitted cannot be retracted. No retry authority is returned. */
    fun admit(peer: SshPeerIdentity, key: SshHostKeyIdentity): SshPasswordCredentials? {
        if (onMain()) { close(); return null }
        if (!SshAuthenticationRegistry.lock.tryLock()) { close(); return null }
        try {
            if (closed || phase != Phase.PREPARED || cancellation.isCancelled || peer != profile.peer || key != expectedKey) {
                invalidate(); return null
            }
            val now = clock()
            if (now < 0 || now >= expiresAtMillis) { invalidate(); return null }
            val catalog = (profiles.load() as? ConnectionProfilesResult.Success)?.value
            val current = catalog?.profiles?.singleOrNull { it.id == profile.profile.id }
            val pin = (trust.lookup(peer) as? SshTrustStoreResult.Success)?.value?.current
            if (catalog == null || current == null || SshObservationProfile.create(catalog.generation, current) != profile || pin != expected) {
                invalidate(); return null
            }
            return cancellation.tryIfActive {
                // Recheck deadline after storage reads, immediately before ownership changes.
                val gateTime = clock()
                if (gateTime < 0 || gateTime >= expiresAtMillis) null
                else synchronized(ownership) {
                    if (closed || pending?.isClosed != false) null
                    else pending.also { pending = null; phase = Phase.ADMITTED }
                }
            }.also { if (it == null) invalidate() }
        } catch (_: Exception) {
            invalidate(); return null
        } finally { SshAuthenticationRegistry.lock.unlock() }
    }

    /**
     * Promote only an engine-issued authenticated proof, under the same exact-state gate used
     * for credential admission. This consumes no new secret and performs no transport work.
     * A duplicate/invalid promotion fails closed but cannot discard an already established owner.
     */
    fun establishSession(proof: SshAuthenticatedOwnership): Boolean {
        if (onMain()) { close(); return false }
        if (!SshAuthenticationRegistry.lock.tryLock()) { close(); return false }
        try {
            if (closed || phase != Phase.ADMITTED || proof.isRetired ||
                proof.cancellation !== cancellation || proof.peer != profile.peer || proof.key != expectedKey) {
                invalidate(); return false
            }
            val now = clock()
            if (now < 0 || now >= expiresAtMillis) { invalidate(); return false }
            val catalog = (profiles.load() as? ConnectionProfilesResult.Success)?.value
            val current = catalog?.profiles?.singleOrNull { it.id == profile.profile.id }
            val pin = (trust.lookup(profile.peer) as? SshTrustStoreResult.Success)?.value?.current
            if (catalog == null || current == null || current.protocol != ConnectionProtocol.SSH ||
                SshObservationProfile.create(catalog.generation, current) != profile || pin != expected) {
                invalidate(); return false
            }
            val promoted = cancellation.tryIfActive {
                synchronized(ownership) {
                    val gateTime = clock()
                    if (closed || phase != Phase.ADMITTED || proof.isRetired || gateTime < 0 || gateTime >= expiresAtMillis) false
                    else {
                        established = proof
                        phase = Phase.ESTABLISHED
                        // cancel(false) alone is insufficient: a task already running will also
                        // phase-check under ownership before deciding to expire authentication.
                        expiryTask?.cancel(false)
                        expiryTask = null
                        true
                    }
                }
            } == true
            if (!promoted) invalidate()
            return promoted
        } catch (_: Exception) {
            invalidate(); return false
        } finally { SshAuthenticationRegistry.lock.unlock() }
    }

    /** Exact object identity plus the engine's monotonic retirement flag are both required.
     * This path needs no SQLite/coordination lock and cannot release a different transport. */
    fun releaseSession(proof: SshAuthenticatedOwnership) {
        val release = synchronized(ownership) {
            if (established !== proof || !proof.isRetired) false
            else {
                established = null
                phase = Phase.RETIRED
                closed = true
                true
            }
        }
        if (release) {
            cancellation.cancel()
            SshAuthenticationRegistry.attempts.remove(this)
        }
    }

    /** An expiry task may already be executing when promotion cancels its Future. Choose its
     * winner under ownership, release that monitor, then signal cancellation: never reverse
     * the coordination -> cancellation -> ownership order by cancelling while ownership is held. */
    private fun expireAuthentication() {
        val expire = synchronized(ownership) {
            if (established != null || phase == Phase.RETIRED) false
            else {
                closed = true
                phase = Phase.RETIRED
                expiryTask = null
                pending?.close()
                pending = null
                true
            }
        }
        if (expire) {
            cancellation.cancel()
            SshAuthenticationRegistry.attempts.remove(this)
        }
    }

    internal fun invalidate() {
        check(SshAuthenticationRegistry.lock.isHeldByCurrentThread)
        close()
    }

    override fun close() {
        // Never waits for a store/SQLite operation, including when called accidentally on main.
        // Cancel wins the signal monitor before disposal competes with admission/promotion.
        cancellation.cancel()
        val remove = synchronized(ownership) {
            closed = true
            expiryTask?.cancel(false)
            expiryTask = null
            pending?.close()
            pending = null
            if (established == null) { phase = Phase.RETIRED; true } else false
        }
        if (remove) SshAuthenticationRegistry.attempts.remove(this)
    }
    override fun toString() = "SshAuthenticationHandle(redacted)"
}
