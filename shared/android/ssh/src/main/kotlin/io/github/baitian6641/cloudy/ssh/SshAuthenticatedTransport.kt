package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.DirectConnection
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.PublicKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

enum class SshTransportCode {
    INVALID_INPUT, BUSY, QUARANTINED, CANCELLED, TIMED_OUT, CONNECT_FAILED,
    ALGORITHM_REJECTED, KEY_REJECTED, ADMISSION_DENIED, CREDENTIALS_UNAVAILABLE,
    PASSWORD_CHANGE_REFUSED, KEY_UNREADABLE, KEY_FORMAT_UNSUPPORTED, KEY_PASSPHRASE_REQUIRED,
    KEYBOARD_INTERACTIVE_UNSUPPORTED,
    AUTHENTICATION_FAILED, OWNERSHIP_REJECTED, CLOSED,
    OPERATION_FAILED, LIMIT_EXCEEDED, INVALID_REMOTE_DATA, CLEANUP_UNCERTAIN,
}
class SshJumpChannel internal constructor(internal val connection: DirectConnection) : AutoCloseable {
    override fun close() { runCatching { connection.close() } }
    override fun toString(): String = "SshJumpChannel(redacted)"
}

sealed interface SshDirectResult {
    class Opened(val channel: SshJumpChannel) : SshDirectResult
    class Failure(val code: SshTransportCode) : SshDirectResult {
        override fun toString() = "SshDirectResult.Failure(${code.name})"
    }
}
sealed interface SshTransportOpenResult {
    class Connected(val transport: SshAuthenticatedTransport) : SshTransportOpenResult {
        override fun toString() = "SshTransportOpenResult.Connected(redacted)"
    }
    class Failure(val code: SshTransportCode) : SshTransportOpenResult {
        override fun toString() = "SshTransportOpenResult.Failure(${code.name})"
    }
}
sealed interface SshTransportCloseResult {
    data object Retired : SshTransportCloseResult
    class Failure(val code: SshTransportCode) : SshTransportCloseResult {
        override fun toString() = "SshTransportCloseResult.Failure(${code.name})"
    }
}

/** One retained connection per factory. Uncertain cleanup permanently quarantines this owner. */
class SshAuthenticatedTransportFactory {
    private val admission = Any()
    private val busy = AtomicBoolean(false)
    private val quarantined = AtomicBoolean(false)
    private var previous: SshRetainedState? = null

    fun connectPassword(peer: SshPeerIdentity, address: InetAddress, expectedKey: SshHostKeyIdentity,
        cancellation: SshObservationCancellation,
        admit: (SshPeerIdentity, SshHostKeyIdentity) -> SshSessionCredentials?,
        establish: (SshAuthenticatedOwnership) -> Boolean,
        retired: (SshAuthenticatedOwnership) -> Unit,
        timeoutMillis: Int = 10_000, direct: SshJumpChannel? = null): SshTransportOpenResult = connect(
        peer, address, expectedKey, cancellation,
        { p, k ->
            // A password route never authenticates key material even if a caller supplies it.
            val admitted = admit(p, k)
            if (admitted is SshPasswordCredentials) admitted else { admitted?.close(); null }
        },
        establish, retired, timeoutMillis, direct)

    /** One public-key attempt. No password, keyboard-interactive or agent fallback is offered. */
    fun connectPublicKey(peer: SshPeerIdentity, address: InetAddress, expectedKey: SshHostKeyIdentity,
        cancellation: SshObservationCancellation,
        admit: (SshPeerIdentity, SshHostKeyIdentity) -> SshKeyCredentials?,
        establish: (SshAuthenticatedOwnership) -> Boolean,
        retired: (SshAuthenticatedOwnership) -> Unit,
        timeoutMillis: Int = 10_000, direct: SshJumpChannel? = null): SshTransportOpenResult = connect(
        peer, address, expectedKey, cancellation, { p, k -> admit(p, k) }, establish, retired, timeoutMillis, direct)

    /** One single-prompt keyboard-interactive attempt; no password/key/agent fallback. */
    fun connectKeyboardInteractive(peer: SshPeerIdentity, address: InetAddress, expectedKey: SshHostKeyIdentity,
        cancellation: SshObservationCancellation,
        admit: (SshPeerIdentity, SshHostKeyIdentity) -> SshKeyboardInteractiveCredentials?,
        establish: (SshAuthenticatedOwnership) -> Boolean,
        retired: (SshAuthenticatedOwnership) -> Unit,
        timeoutMillis: Int = 10_000, direct: SshJumpChannel? = null,
        prompts: SshInteractivePromptHandler? = null): SshTransportOpenResult = connect(
        peer, address, expectedKey, cancellation, { p, k -> admit(p, k) }, establish, retired,
        timeoutMillis, direct, prompts)

    private fun connect(peer: SshPeerIdentity, address: InetAddress, expectedKey: SshHostKeyIdentity,
        cancellation: SshObservationCancellation,
        admit: (SshPeerIdentity, SshHostKeyIdentity) -> SshSessionCredentials?,
        establish: (SshAuthenticatedOwnership) -> Boolean,
        retired: (SshAuthenticatedOwnership) -> Unit,
        timeoutMillis: Int, direct: SshJumpChannel?,
        prompts: SshInteractivePromptHandler? = null): SshTransportOpenResult {
        fun failed(code: SshTransportCode) = SshTransportOpenResult.Failure(code)
        if (timeoutMillis !in 1..45_000) return failed(SshTransportCode.INVALID_INPUT)
        val denied = synchronized(admission) {
            // Retirement notification itself must exit before this factory is reused.
            if (previous?.fullyRetired() == true) { previous = null; busy.set(false) }
            when {
                quarantined.get() -> SshTransportCode.QUARANTINED
                !busy.compareAndSet(false, true) -> SshTransportCode.BUSY
                else -> null
            }
        }
        if (denied != null) return failed(denied)
        if (Thread.currentThread().isInterrupted) cancellation.cancel()
        if (cancellation.isCancelled) { busy.set(false); return failed(SshTransportCode.CANCELLED) }
        val state = SshRetainedState(cancellation, timeoutMillis, retired) { certain ->
            if (!certain) quarantined.set(true)
        }
        synchronized(admission) { previous = state }
        if (!state.attach()) { busy.set(false); return failed(SshTransportCode.BUSY) }
        if (direct != null) state.retainDirect(direct.connection)
        var credentials: SshSessionCredentials? = null
        var material: SshCredentialMaterial? = null
        var method: SshStrictPasswordMethod? = null
        var credentialFailure: SshTransportCode? = null
        var result: SshTransportOpenResult? = null
        try {
            state.start()
            val numeric = checkNotNull(address.hostAddress)
            val client = object : SSHClient(SshObservationPolicy.create(state.reader, idleReadTimeoutMillis = 0)) {
                override fun doKex() { state.reader.expected.set(true); super.doKex() }
            }
            state.client = client
            client.connectTimeout = timeoutMillis
            client.timeout = timeoutMillis
            client.socketFactory = state.socketFactory
            client.transport.setDisconnectListener { _, _ ->
                // Wake retirement on idle peer loss too. Never retain a server message or
                // perform shutdown on Reader while SSHJ holds its transport locks.
                state.stop(when {
                    state.proof != null -> SshTransportCode.OPERATION_FAILED
                    state.keyObserved.get() -> SshTransportCode.AUTHENTICATION_FAILED
                    else -> SshTransportCode.CONNECT_FAILED
                })
            }
            client.addAlgorithmsVerifier { algorithms ->
                val accepted = algorithms.keyExchangeAlgorithm == SshObservationPolicy.KEX &&
                    algorithms.signatureAlgorithm == SshObservationPolicy.SIGNATURE &&
                    algorithms.client2ServerCipherAlgorithm == SshObservationPolicy.CIPHER &&
                    algorithms.server2ClientCipherAlgorithm == SshObservationPolicy.CIPHER &&
                    algorithms.client2ServerMACAlgorithm == SshObservationPolicy.MAC &&
                    algorithms.server2ClientMACAlgorithm == SshObservationPolicy.MAC &&
                    algorithms.client2ServerCompressionAlgorithm == "none" && algorithms.server2ClientCompressionAlgorithm == "none"
                if (!accepted) state.stop(SshTransportCode.ALGORITHM_REJECTED)
                accepted && state.active()
            }
            client.addHostKeyVerifier(object : HostKeyVerifier {
                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = listOf(SshObservationPolicy.SIGNATURE)
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    state.reader.record(Thread.currentThread())
                    // This also verifies immediate post-auth rekey: SSHJ marks its transport
                    // authenticated on Reader before auth() returns to this owning worker.
                    // Through a direct-tcpip channel the target is addressed by its configured
                    // identity; a local numeric resolution is neither needed nor authoritative.
                    val expectedHost = if (direct != null) peer.host else numeric
                    val accepted = try {
                        hostname == expectedHost && port == peer.port &&
                            SshObservedKeyCodec.encode(key) == expectedKey
                    } catch (_: Exception) { false }
                    if (!accepted) state.stop(SshTransportCode.KEY_REJECTED)
                    if (accepted) state.keyObserved.set(true)
                    return accepted && state.active()
                }
            })
            if (state.active()) {
                val channel = direct
                if (channel != null) client.connectVia(channel.connection) else client.connect(numeric, peer.port)
            }
            if (state.active() && state.keyObserved.get()) {
                // Platform coordination is entered outside the cancellation monitor/Reader.
                credentials = admit(peer, expectedKey)
                material = credentials?.take()
                val owned = material
                if (credentials == null) state.stop(SshTransportCode.ADMISSION_DENIED)
                else if (owned == null) state.stop(SshTransportCode.CREDENTIALS_UNAVAILABLE)
                else if (state.active()) {
                    when (owned) {
                        is SshPasswordCredentials.Material -> {
                            val finder = SshOneReleasePasswordFinder(owned, cancellation, state::active)
                            val strict = SshStrictPasswordMethod(finder, state::active)
                            method = strict
                            try { client.auth(String(owned.username), strict) } finally { owned.close() }
                        }
                        is SshKeyCredentials.Material -> {
                            val provider = try {
                                SshKeyAuthentication.provider(owned, cancellation, state::active)
                            } catch (failure: SshKeyException) {
                                credentialFailure = when (failure.failure) {
                                    SshKeyFailure.UNSUPPORTED_FORMAT -> SshTransportCode.KEY_FORMAT_UNSUPPORTED
                                    SshKeyFailure.PASSPHRASE_REQUIRED -> SshTransportCode.KEY_PASSPHRASE_REQUIRED
                                    SshKeyFailure.UNREADABLE -> SshTransportCode.KEY_UNREADABLE
                                }
                                null
                            }
                            if (provider != null && state.active()) {
                                // The passphrase is released only for this parse and wiped here.
                                try {
                                    client.auth(String(owned.username), AuthPublickey(provider))
                                } finally {
                                    owned.passphrase?.fill('\u0000')
                                }
                            }
                        }
                        is SshKeyboardInteractiveCredentials.Material -> {
                            val responder = SshOneReleaseKeyboardInteractiveResponder(
                                owned, cancellation, state::active, prompts)
                            try {
                                client.auth(String(owned.username), AuthKeyboardInteractive(responder))
                            } finally {
                                responder.eraseResponse()
                            }
                            if (responder.unsupported.get()) {
                                credentialFailure = SshTransportCode.KEYBOARD_INTERACTIVE_UNSUPPORTED
                            }
                        }
                        else -> state.stop(SshTransportCode.CREDENTIALS_UNAVAILABLE)
                    }
                    if (client.isAuthenticated && state.active()) {
                        if (direct == null) state.prepareRetainedReads()
                        val proof = SshAuthenticatedOwnership(peer, expectedKey, cancellation)
                        state.proof = proof
                        if (establish(proof) && state.retain()) {
                            result = SshTransportOpenResult.Connected(SshAuthenticatedTransport(state, proof))
                        } else state.stop(SshTransportCode.OWNERSHIP_REJECTED)
                    }
                }
            }
        } catch (_: IOException) {
            // Never infer wrong password or parse upstream/server exception text.
        } catch (_: Exception) {
            state.stop(SshTransportCode.OWNERSHIP_REJECTED)
        } finally {
            material?.close()
            credentials?.close()
            if (Thread.currentThread().isInterrupted) cancellation.cancel()
            if (result == null) {
                if (method?.passwordChangeRefused?.get() == true) state.stop(SshTransportCode.PASSWORD_CHANGE_REFUSED)
                credentialFailure?.let { state.stop(it) }
                if (!state.active()) state.expireIfNeeded()
                state.stop(if (state.keyObserved.get()) SshTransportCode.AUTHENTICATION_FAILED else SshTransportCode.CONNECT_FAILED)
            }
            state.setupFinished.countDown()
        }
        if (result != null && state.active()) return checkNotNull(result)
        state.expireIfNeeded()
        return when (val closed = state.awaitClosed(3_000)) {
            SshTransportCloseResult.Retired -> failed(state.reason())
            is SshTransportCloseResult.Failure -> failed(closed.code)
        }
    }
}

/** Raw SSHJ objects remain private. Public operations are bounded, read-only and synchronous-worker APIs. */
class SshAuthenticatedTransport internal constructor(private val state: SshRetainedState,
    val ownership: SshAuthenticatedOwnership) {
    val files = SshReadOnlyFiles(this)
    /** Opens one direct-tcpip channel for a dependent transport; the jump retains closing ownership. */
    fun openDirect(host: String, port: Int, timeoutMillis: Int = 5_000): SshDirectResult =
        state.openDirect(host, port, timeoutMillis)
    /** Worker-only setup. One PTY per transport; closing it closes linked files too. */
    fun openPty(size: SshPtySize): SshPtyOpenResult = state.openPty(size)
    fun requestClose() { state.stop(SshTransportCode.CLOSED) }
    fun awaitClosed(timeoutMillis: Long = 3_000): SshTransportCloseResult = state.awaitClosed(timeoutMillis)
    internal fun <T> withFiles(timeoutMillis: Int, action: (SFTPClient) -> T): SshFilesResult<T> =
        state.withFiles(timeoutMillis, action)
    override fun toString() = "SshAuthenticatedTransport(redacted)"
}

/** No I/O occurs in cancellation callbacks. One owner wakes, closes the socket and joins actual threads. */
internal class SshRetainedState(
    private val cancellation: SshObservationCancellation, timeoutMillis: Int,
    private val retired: (SshAuthenticatedOwnership) -> Unit,
    private val finished: (Boolean) -> Unit,
) {
    val reader = SshReaderRetirement()
    val keyObserved = AtomicBoolean(false)
    val setupFinished = CountDownLatch(1)
    @Volatile var client: SSHClient? = null
    @Volatile var proof: SshAuthenticatedOwnership? = null
    private val gate = Any()
    private val wake = Semaphore(0)
    private var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())
    private var operation: CountDownLatch? = null
    private var pty: SshOwnedPty? = null
    private var direct: DirectConnection? = null
    private var external: DirectConnection? = null

    fun retainDirect(connection: DirectConnection) { external = connection }

    fun openDirect(host: String, port: Int, timeoutMillis: Int): SshDirectResult {
        if (host.isEmpty() || host.length > 255 || port !in 1..65535 || timeoutMillis !in 1..45_000) {
            return SshDirectResult.Failure(SshTransportCode.INVALID_INPUT)
        }
        val done = CountDownLatch(1)
        val rejected = synchronized(gate) {
            if (!active()) { expireIfNeeded(); reason() }
            else if (operation != null) SshTransportCode.BUSY
            else {
                operation = done
                deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())
                null
            }
        }
        if (rejected != null) return SshDirectResult.Failure(rejected)
        wake.release()
        return try {
            val channel = checkNotNull(client).newDirectConnection(host, port)
            synchronized(gate) {
                if (!active()) {
                    runCatching { channel.close() }
                    expireIfNeeded()
                    SshDirectResult.Failure(reason())
                } else {
                    direct = channel
                    deadline = 0
                    SshDirectResult.Opened(SshJumpChannel(channel))
                }
            }
        } catch (_: Exception) {
            if (Thread.currentThread().isInterrupted) cancellation.cancel()
            stop(SshTransportCode.OPERATION_FAILED)
            SshDirectResult.Failure(reason())
        } finally {
            synchronized(gate) { operation = null; deadline = 0 }
            done.countDown()
            wake.release()
        }
    }

    fun openPty(size: SshPtySize): SshPtyOpenResult {
        val owned = synchronized(gate) {
            if (!active()) return SshPtyOpenResult.Failure(reason())
            if (proof == null || pty != null) return SshPtyOpenResult.Failure(SshTransportCode.BUSY)
            // Publish ownership before the first channel-open request can block or fail.
            SshOwnedPty(checkNotNull(client), size, ::active, ::reason, ::stop).also { pty = it }
        }
        return owned.open()
    }
    private val terminal = CountDownLatch(1)
    private val stopped = AtomicReference<SshTransportCode?>(null)
    private val socket = AtomicReference<Socket?>(null)
    private val closeFailed = AtomicBoolean(false)
    private val sftpReader = AtomicReference<Thread?>(null)
    private val sftpChannel = AtomicReference<SshTrackedSftpFactory?>(null)
    private val certain = AtomicBoolean(false)
    private val resourceCertain = AtomicBoolean(false)
    private val cleanupStarted = CountDownLatch(1)
    private val owner = Thread({ own() }, "cloudy-ssh-retained-owner").apply { isDaemon = true }
    // This notification waiter performs no transport I/O. It publishes retirement only after
    // the actual owner Thread has exited; awaitClosed also joins this final callback publisher.
    private val notification = Thread({
        var success = false
        try {
            cleanupStarted.await()
            TimeUnit.SECONDS.timedJoin(owner, 3)
            success = !owner.isAlive && resourceCertain.get()
            if (success) proof?.let { it.markRetired(); retired(it) }
        } catch (_: Exception) { success = false }
        finally { certain.set(success); finished(success); terminal.countDown() }
    }, "cloudy-ssh-retired-notification").apply { isDaemon = true }

    fun attach(): Boolean = cancellation.attach { wake.release() }
    fun fullyRetired(): Boolean = certain.get() && terminal.count == 0L && !owner.isAlive && !notification.isAlive
    fun start() {
        try { owner.start(); notification.start() }
        catch (_: Exception) {
            stop(SshTransportCode.CLEANUP_UNCERTAIN)
            if (!owner.isAlive) cancellation.detach()
            finished(false)
            terminal.countDown()
            throw IOException("TRANSPORT_OWNER_UNAVAILABLE")
        }
    }
    fun active(): Boolean {
        if (cancellation.isCancelled) return false
        if (stopped.get() != null) return false
        return synchronized(gate) { deadline == 0L || System.nanoTime() - deadline < 0 }
    }
    fun reason(): SshTransportCode = when {
        cancellation.isCancelled -> SshTransportCode.CANCELLED
        else -> stopped.get() ?: SshTransportCode.CLOSED
    }
    fun stop(code: SshTransportCode) { stopped.compareAndSet(null, code); wake.release() }
    fun prepareRetainedReads() {
        // SocketClient.onConnect applies the authentication socket timeout. Channel read
        // policy alone cannot keep a quiet retained transport alive; clear SO_TIMEOUT too.
        checkNotNull(socket.get()).soTimeout = 0
    }
    fun expireIfNeeded() {
        synchronized(gate) {
            if (deadline != 0L && System.nanoTime() - deadline >= 0) stop(SshTransportCode.TIMED_OUT)
            else if (cancellation.isCancelled) stop(SshTransportCode.CANCELLED)
        }
    }
    fun retain(): Boolean = synchronized(gate) {
        if (!active()) false else { deadline = 0; wake.release(); true }
    }

    fun <T> withFiles(timeoutMillis: Int, action: (SFTPClient) -> T): SshFilesResult<T> {
        if (timeoutMillis !in 1..45_000) return SshFilesResult.Failure(SshTransportCode.INVALID_INPUT)
        val done = CountDownLatch(1)
        val admitted = synchronized(gate) {
            if (!active()) {
                // Preserve the actual terminal cause, including changed-key refusal on
                // Reader, when callers observe retirement through a later operation.
                expireIfNeeded()
                reason()
            }
            else if (operation != null) SshTransportCode.BUSY
            else { operation = done; deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong()); null }
        }
        if (admitted != null) return SshFilesResult.Failure(admitted)
        wake.release()
        return try {
            val ssh = checkNotNull(client)
            val tracked = SshTrackedSftpFactory(ssh)
            sftpChannel.set(tracked)
            var engine: SshOwnedSftp? = null
            val value = try {
                val owned = SshOwnedSftp(tracked)
                engine = owned
                sftpReader.set(owned.packetReaderThread)
                owned.setTimeoutMs(timeoutMillis)
                owned.init()
                action(SFTPClient(owned))
            } finally {
                try { if (engine != null) engine.close() else tracked.close() }
                finally {
                    val packetReader = sftpReader.get()
                    val left = synchronized(gate) { deadline - System.nanoTime() }
                    if (packetReader != null && packetReader.isAlive && left > 0) TimeUnit.NANOSECONDS.timedJoin(packetReader, left)
                    if (packetReader?.isAlive == true || !tracked.isClosed()) throw IOException("SFTP_READER_UNSETTLED")
                    sftpReader.compareAndSet(packetReader, null)
                    sftpChannel.compareAndSet(tracked, null)
                }
            }
            synchronized(gate) {
                if (!active()) {
                    expireIfNeeded()
                    SshFilesResult.Failure(reason())
                } else {
                    // Completion and deadline retirement share the owner gate. A stale timer
                    // observation cannot close an operation that already completed in time.
                    deadline = 0
                    SshFilesResult.Success(value)
                }
            }
        } catch (bounded: SshFileBoundException) {
            stop(bounded.code)
            SshFilesResult.Failure(bounded.code)
        } catch (_: Exception) {
            if (Thread.currentThread().isInterrupted) cancellation.cancel()
            if (!active() && stopped.get() == null && !cancellation.isCancelled) stop(SshTransportCode.TIMED_OUT)
            stop(SshTransportCode.OPERATION_FAILED)
            SshFilesResult.Failure(reason())
        } finally {
            synchronized(gate) { operation = null; deadline = 0 }
            done.countDown()
            wake.release()
        }
    }

    fun awaitClosed(timeoutMillis: Long): SshTransportCloseResult {
        if (timeoutMillis !in 1..45_000) return SshTransportCloseResult.Failure(SshTransportCode.INVALID_INPUT)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        try {
            if (!terminal.await(timeoutMillis, TimeUnit.MILLISECONDS)) return SshTransportCloseResult.Failure(SshTransportCode.TIMED_OUT)
            val remaining = deadline - System.nanoTime()
            if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(notification, remaining)
            return if (certain.get() && !owner.isAlive && !notification.isAlive) SshTransportCloseResult.Retired
                else SshTransportCloseResult.Failure(SshTransportCode.CLEANUP_UNCERTAIN)
        } catch (_: InterruptedException) {
            cancellation.cancel(); Thread.currentThread().interrupt()
            return SshTransportCloseResult.Failure(SshTransportCode.CANCELLED)
        }
    }

    private fun own() {
        var interrupted = false
        var cleanupCertain = false
        try {
            while (stopped.get() == null && !cancellation.isCancelled) {
                val left = synchronized(gate) { if (deadline == 0L) Long.MAX_VALUE else deadline - System.nanoTime() }
                if (left <= 0) { expireIfNeeded(); continue }
                if (left == Long.MAX_VALUE) wake.acquire() else wake.tryAcquire(left, TimeUnit.NANOSECONDS)
            }
        } catch (_: InterruptedException) { interrupted = true; cancellation.cancel() }
        finally {
            cleanupStarted.countDown()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            synchronized(gate) { pty }?.stopQueues()
            // A dependent target transport loses its channel with the jump owner.
            try { external?.close() } catch (_: Exception) { closeFailed.set(true) }
            try { direct?.close() } catch (_: Exception) { closeFailed.set(true) }
            try { socket.get()?.close() } catch (_: Exception) { closeFailed.set(true) }
            val closed = AtomicBoolean(false)
            val helper = Thread({
                try {
                    try { client?.close() } finally {
                        try { sftpChannel.get()?.close() }
                        finally { synchronized(gate) { pty }?.closeChannel() }
                    }
                    closed.set(true)
                } catch (_: Exception) { closeFailed.set(true) }
            }, "cloudy-ssh-retained-close").apply { isDaemon = true }
            try {
                // Factory publication must finish before we choose the client/proof to retire.
                var left = end - System.nanoTime()
                val setupDone = left > 0 && setupFinished.await(left, TimeUnit.NANOSECONDS)
                helper.start()
                val readerDone = reader.awaitAndJoin(end)
                val active = synchronized(gate) { operation }
                left = end - System.nanoTime()
                val operationDone = active == null || (left > 0 && active.await(left, TimeUnit.NANOSECONDS))
                val packetReader = sftpReader.get()
                left = end - System.nanoTime()
                if (packetReader != null && packetReader.isAlive && left > 0) TimeUnit.NANOSECONDS.timedJoin(packetReader, left)
                val sftpDone = packetReader?.isAlive != true && sftpChannel.get()?.isClosed() != false
                left = end - System.nanoTime()
                if (left > 0) TimeUnit.NANOSECONDS.timedJoin(helper, left)
                val ptyDone = synchronized(gate) { pty }?.awaitResources(end) ?: true
                cleanupCertain = ptyDone && setupDone && readerDone && operationDone && sftpDone && !helper.isAlive && closed.get() &&
                    socket.get()?.isClosed != false && !closeFailed.get()
            } catch (_: Exception) { cleanupCertain = false }
            finally {
                cancellation.detach()
                resourceCertain.set(cleanupCertain)
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
    }

    val socketFactory = object : SocketFactory() {
        override fun createSocket(): Socket {
            if (!active()) throw IOException("TRANSPORT_STOPPED")
            val created = Socket()
            if (!socket.compareAndSet(null, created)) { created.close(); throw IOException("SECOND_SOCKET") }
            if (!active()) { created.close(); throw IOException("TRANSPORT_STOPPED") }
            return created
        }
        override fun createSocket(host: String, port: Int): Socket = refuse()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = refuse()
        override fun createSocket(host: InetAddress, port: Int): Socket = refuse()
        override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket = refuse()
        private fun refuse(): Nothing = throw IOException("UNEXPECTED_SOCKET_PATH")
    }
}
