package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.net.DnsResolver
import android.net.InetAddresses
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.Closeable
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

enum class SshAddressFailure {
    CANCELLED, TIMED_OUT, UNAVAILABLE, NO_ADDRESSES, BUSY, CLOSED, UNSUPPORTED_SCOPE,
}

sealed class SshAddressResult {
    /** Numeric transport candidates only. They neither replace logical peer identity nor grant trust. */
    class Resolved internal constructor(addresses: List<InetAddress>) : SshAddressResult() {
        val addresses: List<InetAddress> = Collections.unmodifiableList(ArrayList(addresses))
        override fun toString(): String = "SshAddressResult.Resolved(redacted)"
    }
    class Failed(val code: SshAddressFailure) : SshAddressResult() {
        override fun toString(): String = "SshAddressResult.Failed($code)"
    }
}

fun interface SshResolutionHandle { fun cancel() }

/**
 * Cancellable transport lookup using the application's default network without changing it.
 * All terminal callbacks are posted once to the main thread; they must remain nonblocking.
 * Cancel/close cannot retract an already selected terminal result: the consuming controller must
 * also bind callbacks to its live attempt. No Activity is retained and no network permission added.
 */
class SshAddressResolver(context: Context) : Closeable {
    private val app = context.applicationContext
    private val gate = Any()
    private var closed = false
    private val requests = mutableSetOf<Request>()

    fun resolve(
        peer: SshPeerIdentity,
        timeoutMillis: Long = 5_000,
        callback: (SshAddressResult) -> Unit,
    ): SshResolutionHandle {
        require(timeoutMillis in 1..30_000) { "Invalid resolution deadline" }
        val request = Request(peer.host, SystemClock.elapsedRealtime() + timeoutMillis, callback)
        val failure = synchronized(gate) {
            when {
                closed -> SshAddressFailure.CLOSED
                !Runtime.slots.tryAcquire() -> SshAddressFailure.BUSY
                else -> {
                    request.admitted = true
                    requests += request
                    // Install work before close can select cancellation and remove the timer.
                    Runtime.handler.postDelayed(request.timeout, timeoutMillis)
                    Runtime.handler.post { request.start() }
                    null
                }
            }
        }
        if (failure != null) request.finish(SshAddressResult.Failed(failure))
        return request
    }

    override fun close() {
        val active = synchronized(gate) { closed = true; requests.toList() }
        active.forEach { it.cancel() }
    }

    private inner class Request(
        private val host: String,
        private val deadline: Long,
        private val callback: (SshAddressResult) -> Unit,
    ) : SshResolutionHandle {
        private val terminal = AtomicBoolean(false)
        private val signal = CancellationSignal()
        var admitted = false
        val timeout = Runnable { finish(SshAddressResult.Failed(SshAddressFailure.TIMED_OUT)) }

        fun start() {
            if (terminal.get()) return
            if (SystemClock.elapsedRealtime() >= deadline) { timeout.run(); return }
            if ('%' in host) {
                finish(SshAddressResult.Failed(SshAddressFailure.UNSUPPORTED_SCOPE)); return
            }
            try {
                // Canonical IPv4/expanded IPv6 cannot enter the hostname path. This Android
                // parser explicitly performs no DNS; do not use getByName or reverse lookup.
                if (':' in host || host.all { it in '0'..'9' || it == '.' }) {
                    answer(listOf(InetAddresses.parseNumericAddress(host)), 0)
                } else {
                    resolver().query(null, host, DnsResolver.FLAG_EMPTY, Runtime.executor, signal,
                        object : DnsResolver.Callback<List<InetAddress>> {
                            override fun onAnswer(answer: List<InetAddress>, rcode: Int) { this@Request.answer(answer, rcode) }
                            override fun onError(error: DnsResolver.DnsException) {
                                finish(SshAddressResult.Failed(SshAddressFailure.UNAVAILABLE))
                            }
                        })
                }
            } catch (_: RuntimeException) {
                // Never publish provider messages, endpoint names or exception transcripts.
                finish(SshAddressResult.Failed(SshAddressFailure.UNAVAILABLE))
            }
        }

        private fun answer(addresses: List<InetAddress>, rcode: Int) {
            if (terminal.get()) return
            if (rcode != 0) { finish(SshAddressResult.Failed(SshAddressFailure.UNAVAILABLE)); return }
            // Strip provider-attached hostnames without reverse lookup. Preserve numeric scope
            // if a network answer supplies it; explicit scoped logical literals are denied above.
            val bounded = addresses.take(MAX_CANDIDATES).map { address ->
                if (address is Inet6Address) Inet6Address.getByAddress(null, address.address, address.scopeId)
                else InetAddress.getByAddress(address.address)
            }.distinctBy { it.hostAddress }
            finish(if (bounded.isEmpty()) SshAddressResult.Failed(SshAddressFailure.NO_ADDRESSES)
                else SshAddressResult.Resolved(bounded))
        }

        override fun cancel() { finish(SshAddressResult.Failed(SshAddressFailure.CANCELLED)) }

        fun finish(result: SshAddressResult) {
            // Result selection is synchronous even when cancel originates off the resolver lane.
            // A late provider response, queued start or deadline can never select another result.
            if (!terminal.compareAndSet(false, true)) return
            val selected = if (result is SshAddressResult.Resolved && SystemClock.elapsedRealtime() >= deadline)
                SshAddressResult.Failed(SshAddressFailure.TIMED_OUT) else result
            Runtime.handler.removeCallbacks(timeout)
            synchronized(gate) {
                requests.remove(this)
                if (admitted) { admitted = false; Runtime.slots.release() }
            }
            Runtime.handler.post {
                try { signal.cancel() } catch (_: RuntimeException) {
                    // Cancellation failure cannot resurrect a completed request or kill the lane.
                } finally { Runtime.main.post { callback(selected) } }
            }
        }
    }

    @Suppress("DEPRECATION") // min35 fallback only; API37 constructor is guarded explicitly.
    private fun resolver(): DnsResolver = if (Build.VERSION.SDK_INT >= 37)
        DnsResolver(app, Runtime.thread.looper) else DnsResolver.getInstance()

    private object Runtime {
        // One process-wide lane, never a permanent thread per request/Activity. Admission bounds
        // active lookups; each terminal path removes its timer and cancels the platform request.
        val slots = Semaphore(16)
        val thread = HandlerThread("CloudySshDns").apply { start() }
        val handler = Handler(thread.looper)
        val main = Handler(android.os.Looper.getMainLooper())
        val executor = Executor { command -> handler.post(command) }
    }

    companion object { const val MAX_CANDIDATES = 16 }
}
