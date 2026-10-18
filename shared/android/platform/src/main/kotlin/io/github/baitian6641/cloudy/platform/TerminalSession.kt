package io.github.baitian6641.cloudy.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedTransport
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedTransportFactory
import io.github.baitian6641.cloudy.ssh.SshDirectResult
import io.github.baitian6641.cloudy.ssh.SshInteractivePromptHandler
import io.github.baitian6641.cloudy.ssh.SshJumpChannel
import io.github.baitian6641.cloudy.ssh.SshKeyCredentials
import io.github.baitian6641.cloudy.ssh.SshKeyboardInteractiveCredentials
import io.github.baitian6641.cloudy.ssh.SshOwnedPty
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import io.github.baitian6641.cloudy.ssh.SshSessionCredentials
import io.github.baitian6641.cloudy.ssh.SshPtyAdmission
import io.github.baitian6641.cloudy.ssh.SshPtyOpenResult
import io.github.baitian6641.cloudy.ssh.SshPtyReadResult
import io.github.baitian6641.cloudy.ssh.SshPtySize
import io.github.baitian6641.cloudy.ssh.SshTransportCode
import io.github.baitian6641.cloudy.ssh.SshTransportCloseResult
import io.github.baitian6641.cloudy.ssh.SshTransportOpenResult
import io.github.baitian6641.cloudy.telnet.TelnetAdmission
import io.github.baitian6641.cloudy.telnet.TelnetOpenResult
import io.github.baitian6641.cloudy.telnet.TelnetSession
import io.github.baitian6641.cloudy.telnet.TelnetSize
import io.github.baitian6641.cloudy.terminal.TerminalAdmission
import io.github.baitian6641.cloudy.terminal.TerminalSize
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One explicit attempt owns connect, renderer admission, PTY reads and actual retirement.
 * No callback may acquire a later Activity's renderer; the Activity supplies per-attempt listeners.
 * No service, reconnect, saved secret, duration cap or command replay is implemented here.
 */
internal class TerminalSession(context: Context, private val listener: Listener) {
    interface Listener {
        fun onAuthenticated(host: String)
        fun onSessionOpened(size: TerminalSize)
        /** Worker-only; bounded renderer backpressure may block until cancellation. */
        fun onOutput(bytes: ByteArray, stderr: Boolean)
        /** Preserve the final output until the renderer parser has acknowledged it. */
        fun onOutputEnded()
        /** Main thread, after the actual owner exits. Uncertain cleanup retains UI capacity. */
        fun onEnded(code: String, authenticated: Boolean, cleanupCertain: Boolean)
    }

    private val application = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)
    private val stopped = AtomicReference<String?>()
    private val size = AtomicReference<TerminalSize?>()
    private val geometryLock = Any()
    private val rendererReady = CountDownLatch(1)
    private val resolution = AtomicReference<SshResolutionHandle?>()
    @Volatile private var handle: SshAuthenticationHandle? = null
    @Volatile private var transport: SshAuthenticatedTransport? = null
    @Volatile private var jumpHandle: SshAuthenticationHandle? = null
    @Volatile private var jumpTransport: SshAuthenticatedTransport? = null
    @Volatile private var pty: SshOwnedPty? = null
    @Volatile private var telnet: TelnetSession? = null
    @Volatile private var worker: Thread? = null

    /** Caller transfers both credentials; every rejection path disposes them. */
    fun connect(profile: SshObservationProfile, credentials: SshSessionCredentials,
                jumpProfile: SshObservationProfile? = null,
                jumpCredentials: SshSessionCredentials? = null,
                prompts: SshInteractivePromptHandler? = null) {
        if (!started.compareAndSet(false, true) || stopped.get() != null) {
            credentials.close(); jumpCredentials?.close()
            return
        }
        if (!supported(credentials) || (jumpProfile == null) != (jumpCredentials == null) ||
            (jumpCredentials != null && !supported(jumpCredentials))) {
            credentials.close(); jumpCredentials?.close()
            main.post { listener.onEnded("INVALID_CREDENTIALS", false, true) }
            return
        }
        var code = "CLOSED"
        var authenticated = false
        var cleanupCertain = true
        val owner = Thread({
            var direct: SshJumpChannel? = null
            try {
                if (!active()) return@Thread
                val coordinator = SshAuthenticationCoordinator(application)
                var jumpAddress: InetAddress? = null
                if (jumpProfile != null) {
                    val jumpCredential = checkNotNull(jumpCredentials)
                    when (val prepared = begins(coordinator, jumpProfile, jumpCredential)) {
                        is SshAuthenticationPreparation.Failed -> { code = prepared.code.name; return@Thread }
                        is SshAuthenticationPreparation.Ready -> jumpHandle = prepared.handle
                    }
                    val jumpAuthority = checkNotNull(jumpHandle)
                    if (!active()) return@Thread
                    val address = resolve(jumpProfile) ?: run { code = "NO_ADDRESSES"; return@Thread }
                    if (SshLocalNetworkPolicy.shouldRequestPermission(application, address) && !localPermission()) {
                        code = "LOCAL_NETWORK_REQUIRED"
                        return@Thread
                    }
                    val opened = opens(jumpProfile, address, jumpAuthority, jumpCredential, null, prompts)
                    if (opened is SshTransportOpenResult.Failure) {
                        code = opened.code.name
                        cleanupCertain = transportFailureCertain(opened.code)
                        return@Thread
                    }
                    jumpTransport = (opened as SshTransportOpenResult.Connected).transport
                    jumpAddress = address
                    if (!active()) return@Thread
                    when (val openedDirect = jumpTransport!!.openDirect(
                        profile.profile.host, profile.profile.port, 5_000)) {
                        is SshDirectResult.Failure -> {
                            code = openedDirect.code.name
                            cleanupCertain = transportFailureCertain(openedDirect.code)
                            return@Thread
                        }
                        is SshDirectResult.Opened -> direct = openedDirect.channel
                    }
                    if (!active()) return@Thread
                }
                when (val prepared = begins(coordinator, profile, credentials)) {
                    is SshAuthenticationPreparation.Failed -> { code = prepared.code.name; return@Thread }
                    is SshAuthenticationPreparation.Ready -> handle = prepared.handle
                }
                val authority = checkNotNull(handle)
                if (!active()) return@Thread
                val address = if (direct != null) {
                    checkNotNull(jumpAddress)
                } else {
                    resolve(profile) ?: run { code = "NO_ADDRESSES"; return@Thread }
                }
                if (direct == null && SshLocalNetworkPolicy.shouldRequestPermission(application, address) &&
                    !localPermission()) {
                    code = "LOCAL_NETWORK_REQUIRED"
                    return@Thread
                }
                if (!active()) return@Thread
                val opened = opens(profile, address, authority, credentials, direct, prompts)
                if (opened is SshTransportOpenResult.Failure) {
                    code = opened.code.name
                    cleanupCertain = transportFailureCertain(opened.code)
                    return@Thread
                }
                transport = (opened as SshTransportOpenResult.Connected).transport
                authenticated = true
                if (!active()) return@Thread
                main.post { if (active()) listener.onAuthenticated(profile.profile.host) }
                if (!rendererReady.await(10, TimeUnit.SECONDS)) {
                    code = "RENDERER_READY_TIMEOUT"
                    return@Thread
                }
                if (!active()) return@Thread
                val initial = checkNotNull(size.get())
                when (val result = checkNotNull(transport).openPty(SshPtySize(initial.columns, initial.rows))) {
                    is SshPtyOpenResult.Failure -> { code = result.code.name; return@Thread }
                    is SshPtyOpenResult.Opened -> synchronized(geometryLock) {
                        pty = result.pty
                        // READY precedes channel setup. Reconcile only the latest bounded resize.
                        val latest = checkNotNull(size.get())
                        if (latest != initial) result.pty.offerResize(SshPtySize(latest.columns, latest.rows))
                    }
                }
                if (!active()) return@Thread
                main.post { if (active()) listener.onSessionOpened(initial) }
                val terminal = checkNotNull(pty)
                while (active()) {
                    when (val read = terminal.read(1_000)) {
                        is SshPtyReadResult.Output -> listener.onOutput(read.bytes, read.isStderr)
                        SshPtyReadResult.Idle -> Unit
                        is SshPtyReadResult.End -> {
                            code = read.code.name
                            if (active()) listener.onOutputEnded()
                            break
                        }
                        is SshPtyReadResult.Failure -> { code = read.code.name; break }
                    }
                }
            } catch (_: InterruptedException) {
                code = stopped.get() ?: "CANCELLED"
                // Teardown must be allowed to join actual transport workers after cancellation.
                Thread.interrupted()
            } catch (_: Exception) {
                code = stopped.get() ?: "TERMINAL_FAILED"
            } finally {
                stopped.compareAndSet(null, code)
                Thread.interrupted()
                credentials.close(); jumpCredentials?.close()
                resolution.getAndSet(null)?.cancel()
                pty?.requestClose()
                transport?.requestClose()
                if (transport?.awaitClosed(5_000) is SshTransportCloseResult.Failure) cleanupCertain = false
                handle?.close()
                jumpTransport?.requestClose()
                if (jumpTransport?.awaitClosed(5_000) is SshTransportCloseResult.Failure) cleanupCertain = false
                jumpHandle?.close()
                // Closing the target closes the external channel; closing the jump is independent.
                runCatching { direct?.close() }
                pty = null
                transport = null
                handle = null
                jumpTransport = null
                jumpHandle = null
            }
        }, "cloudy-terminal-owner").apply { isDaemon = true }
        worker = owner
        // A separate notifier joins the owner: a latch set in finally does not prove Thread exit.
        val notifier = Thread({
            owner.join()
            main.post { listener.onEnded(stopped.get() ?: code, authenticated, cleanupCertain) }
        }, "cloudy-terminal-retirement").apply { isDaemon = true }
        try {
            owner.start()
            notifier.start()
        } catch (_: RuntimeException) {
            credentials.close(); jumpCredentials?.close()
            close("OWNER_START_FAILED")
            if (!owner.isAlive) main.post { listener.onEnded("OWNER_START_FAILED", false, true) }
        }
    }

    private fun supported(credential: SshSessionCredentials): Boolean =
        credential is SshPasswordCredentials || credential is SshKeyCredentials ||
            credential is SshKeyboardInteractiveCredentials

    private fun begins(coordinator: SshAuthenticationCoordinator, profile: SshObservationProfile,
                       credential: SshSessionCredentials): SshAuthenticationPreparation =
        when (credential) {
            is SshPasswordCredentials -> coordinator.begin(profile, credential)
            is SshKeyCredentials -> coordinator.beginKey(profile, credential)
            is SshKeyboardInteractiveCredentials -> coordinator.beginKeyboardInteractive(profile, credential)
            else -> SshAuthenticationPreparation.Failed(SshAuthenticationCode.CREDENTIALS_UNAVAILABLE)
        }

    private fun opens(profile: SshObservationProfile, address: InetAddress,
                      authority: SshAuthenticationHandle, credential: SshSessionCredentials,
                      direct: SshJumpChannel?, prompts: SshInteractivePromptHandler?): SshTransportOpenResult {
        val factory = SshAuthenticatedTransportFactory()
        return when (credential) {
            is SshPasswordCredentials -> factory.connectPassword(profile.peer, address,
                authority.expectedKey, authority.cancellation,
                { peer: SshPeerIdentity, key: SshHostKeyIdentity ->
                    val admitted = authority.admit(peer, key)
                    if (admitted is SshPasswordCredentials) admitted else { admitted?.close(); null }
                }, authority::establishSession, authority::releaseSession, direct = direct)
            is SshKeyCredentials -> factory.connectPublicKey(profile.peer, address,
                authority.expectedKey, authority.cancellation,
                { peer: SshPeerIdentity, key: SshHostKeyIdentity ->
                    val admitted = authority.admit(peer, key)
                    if (admitted is SshKeyCredentials) admitted else { admitted?.close(); null }
                }, authority::establishSession, authority::releaseSession, direct = direct)
            is SshKeyboardInteractiveCredentials -> factory.connectKeyboardInteractive(profile.peer, address,
                authority.expectedKey, authority.cancellation,
                { peer: SshPeerIdentity, key: SshHostKeyIdentity ->
                    val admitted = authority.admit(peer, key)
                    if (admitted is SshKeyboardInteractiveCredentials) admitted
                    else { admitted?.close(); null }
                }, authority::establishSession, authority::releaseSession, direct = direct, prompts = prompts,
                timeoutMillis = 40_000)
            else -> SshTransportOpenResult.Failure(SshTransportCode.CREDENTIALS_UNAVAILABLE)
        }
    }

    private fun transportFailureCertain(code: SshTransportCode): Boolean = when (code) {
        SshTransportCode.CLEANUP_UNCERTAIN, SshTransportCode.QUARANTINED,
        SshTransportCode.TIMED_OUT, SshTransportCode.CANCELLED -> false
        else -> true
    }

    /** Plaintext Telnet has no authentication handshake or stored credentials; login is in-band. */
    fun connectTelnet(profile: ConnectionProfile) {
        if (!started.compareAndSet(false, true) || stopped.get() != null) return
        val owner = Thread({
            var endCode = "CLOSED"
            var connected = false
            var cleanupCertain = true
            val ended = CountDownLatch(1)
            try {
                if (!active()) return@Thread
                val address = resolve(profile.host, profile.port) ?: run { endCode = "NO_ADDRESSES"; return@Thread }
                if (SshLocalNetworkPolicy.shouldRequestPermission(application, address) && !localPermission()) {
                    endCode = "LOCAL_NETWORK_REQUIRED"
                    return@Thread
                }
                main.post { if (active()) listener.onAuthenticated(profile.host) }
                if (!rendererReady.await(10, TimeUnit.SECONDS)) {
                    endCode = "RENDERER_READY_TIMEOUT"
                    return@Thread
                }
                if (!active()) return@Thread
                val initial = checkNotNull(size.get())
                val opened = TelnetSession.open(address, profile.port,
                    TelnetSize(initial.columns, initial.rows), object : TelnetSession.Listener {
                        override fun onOutput(bytes: ByteArray) {
                            if (connected) listener.onOutput(bytes, false)
                        }
                        override fun onEnded(code: String) {
                            endCode = code
                            ended.countDown()
                        }
                    })
                if (opened is TelnetOpenResult.Failure) { endCode = opened.code; return@Thread }
                val session = (opened as TelnetOpenResult.Opened).session
                telnet = session
                if (!active()) { session.requestClose(); return@Thread }
                connected = true
                session.start()
                main.post { if (active()) listener.onSessionOpened(initial) }
                ended.await()
            } catch (_: InterruptedException) {
                endCode = stopped.get() ?: "CANCELLED"
                Thread.interrupted()
            } catch (_: Exception) {
                endCode = stopped.get() ?: "TELNET_FAILED"
            } finally {
                stopped.compareAndSet(null, endCode)
                val owned = telnet
                telnet = null
                if (owned != null) {
                    owned.requestClose()
                    if (!owned.awaitClosed(5_000)) cleanupCertain = false
                }
                if (connected) {
                    runCatching { listener.onOutputEnded() }
                    main.post { listener.onEnded(stopped.get() ?: endCode, true, cleanupCertain) }
                } else {
                    main.post { listener.onEnded(stopped.get() ?: endCode, false, cleanupCertain) }
                }
            }
        }, "cloudy-telnet-owner").apply { isDaemon = true }
        worker = owner
        try { owner.start() } catch (_: RuntimeException) {
            close("OWNER_START_FAILED")
            val current = stopped.get() ?: "OWNER_START_FAILED"
            main.post { listener.onEnded(current, false, true) }
        }
    }

    fun openPty(initial: TerminalSize) {
        if (active() && size.compareAndSet(null, initial)) rendererReady.countDown()
    }

    /** Admission is bounded local queueing, not a server acknowledgement. */
    fun offerInput(bytes: ByteArray): TerminalAdmission {
        if (!active()) return TerminalAdmission.STOPPED
        telnet?.let { return telnetAdmission(it.offerInput(bytes)) }
        return admission(pty?.offerInput(bytes))
    }

    fun offerResize(value: TerminalSize): TerminalAdmission = synchronized(geometryLock) {
        if (!active()) return@synchronized TerminalAdmission.STOPPED
        size.set(value)
        // Admission before PTY publication is one last-size-wins slot, never a replay queue.
        pty?.let { admission(it.offerResize(SshPtySize(value.columns, value.rows))) } ?: TerminalAdmission.ACCEPTED
    }

    /** Cancellation closes the real authority now, never queued behind connect or blocked output. */
    fun close(reason: String = "CLOSED") {
        if (!stopped.compareAndSet(null, reason)) return
        rendererReady.countDown()
        resolution.getAndSet(null)?.cancel()
        handle?.close()
        pty?.requestClose()
        telnet?.requestClose()
        transport?.requestClose()
        worker?.interrupt()
    }

    private fun active() = stopped.get() == null

    private fun localPermission() = Build.VERSION.SDK_INT < 37 ||
        application.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun telnetAdmission(value: TelnetAdmission): TerminalAdmission = when (value) {
        TelnetAdmission.ACCEPTED -> TerminalAdmission.ACCEPTED
        TelnetAdmission.FULL -> TerminalAdmission.FULL
        TelnetAdmission.STALE -> TerminalAdmission.STALE
    }

    private fun admission(value: SshPtyAdmission?): TerminalAdmission = when (value) {
        SshPtyAdmission.ACCEPTED -> TerminalAdmission.ACCEPTED
        SshPtyAdmission.FULL -> TerminalAdmission.FULL
        SshPtyAdmission.STOPPED -> TerminalAdmission.STOPPED
        SshPtyAdmission.INVALID_INPUT, null -> TerminalAdmission.STALE
    }

    private fun resolve(profile: SshObservationProfile): InetAddress? =
        resolve(profile.profile.host, profile.profile.port)

    private fun resolve(host: String, port: Int): InetAddress? {
        val resolver = SshAddressResolver(application)
        val answer = AtomicReference<SshAddressResult?>()
        val completed = CountDownLatch(1)
        try {
            val peer = SshPeerIdentity.create(host, port)
            val request = resolver.resolve(peer, 5_000) { result -> answer.set(result); completed.countDown() }
            resolution.set(request)
            if (!active()) request.cancel()
            completed.await(5, TimeUnit.SECONDS)
            return (answer.get() as? SshAddressResult.Resolved)?.addresses?.firstOrNull()
        } finally {
            resolution.getAndSet(null)?.cancel()
            resolver.close()
        }
    }
}
