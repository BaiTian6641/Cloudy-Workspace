package io.github.baitian6641.cloudy.terminal

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** Owns exactly one local renderer attachment. SSH ownership and trust remain in native code. */
@SuppressLint("SetJavaScriptEnabled") // Required only for the origin-fenced, allowlisted local terminal.
class TerminalWebViewBridge(
    context: Context,
    private val identity: TerminalIdentity,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady(size: TerminalSize)
        fun onInput(bytes: ByteArray): TerminalAdmission
        fun onResize(size: TerminalSize): TerminalAdmission
        /** One explicit local action at a time; never remote bytes and never a clipboard read on its own. */
        fun onUiRequest(request: TerminalUiRequest)
        fun onFailure(reason: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val drainPosted = AtomicBoolean(false)
    private val flow = TerminalFlow { SystemClock.elapsedRealtime() }
    private val fontScale = context.resources.configuration.fontScale
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private val accessibilityChanged = AccessibilityManager.AccessibilityServicesStateChangeListener {
        if (ready && !closed.get()) {
            view.evaluateJavascript("window.cloudyTerminalUi?.screenReaderMode(${screenReaderRequired()})", null)
        }
    }
    val view: WebView = WebView(context).apply {
        // Give each native editor an identity before attachment/IME binding, never by restarting
        // a focused connection later. Different terminal attachments must not share field IDs.
        id = View.generateViewId()
        // WebView forces its CSS layout viewport height to zero for WRAP_CONTENT, even
        // with an exact native measure. AndroidView's default child params would select it.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    val providerSummary: String
    val qualificationDetails: Map<String, String>
    private val featuresSupported: Boolean
    private var started = false
    private var disposed = false
    @Volatile private var ready = false
    private var startedAt = 0L
    private var inboundSequence = 0
    private var outboundSequence = 0
    private var proxy: JavaScriptReplyProxy? = null

    private val loader = WebViewAssetLoader.Builder().setDomain(HOST)
        .addPathHandler(PREFIX, WebViewAssetLoader.PathHandler { path ->
            val mime = ASSETS[path]
            if (mime == null) denied() else {
                try {
                    WebResourceResponse(mime, "UTF-8", 200, "OK", RESPONSE_HEADERS,
                        context.assets.open("terminal/$path"))
                } catch (_: IOException) { denied() }
            }
        }).build()

    init {
        checkMain()
        accessibility?.addAccessibilityServicesStateChangeListener(accessibilityChanged)
        val provider = WebViewCompat.getCurrentWebViewPackage(context)
        val messageFeature = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        val binaryFeature = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
        featuresSupported = messageFeature && binaryFeature
        qualificationDetails = mapOf("rendererProvider" to (provider?.packageName ?: "unavailable"),
            "rendererVersion" to (provider?.versionName ?: "unknown"),
            "rendererMessageListener" to messageFeature.toString(), "rendererArrayBuffer" to binaryFeature.toString())
        providerSummary = "${provider?.packageName ?: "unavailable"}/${provider?.versionName ?: "unknown"}; " +
            "listener=$messageFeature; arrayBuffer=$binaryFeature"
        view.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            domStorageEnabled = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            setGeolocationEnabled(false)
            textZoom = 100 // Apply the Android font scale once, in xterm's cell measurements.
            setSupportZoom(false)
        }
        view.contentDescription = "Cloudy SSH terminal; local renderer"
        // DOM textarea focus alone does not give Android keyboard focus to the WebView.
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setDownloadListener { _, _, _, _, _ -> fail("renderer-download-blocked") }
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true // Never log remote bytes.
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
            override fun onCreateWindow(v: WebView, dialog: Boolean, gesture: Boolean, message: Message): Boolean = false
            override fun onShowFileChooser(v: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                callback.onReceiveValue(null)
                return true
            }
            override fun onJsAlert(v: WebView, url: String, message: String, result: JsResult): Boolean {
                result.cancel()
                return true
            }
            override fun onJsConfirm(v: WebView, url: String, message: String, result: JsResult): Boolean {
                result.cancel()
                return true
            }
        }
        view.webViewClient = createWebViewClient()
        if (featuresSupported && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(view, BRIDGE, setOf(ORIGIN)) { _, message, origin, isMainFrame, reply ->
                receive(message, origin, isMainFrame, reply)
            }
        }
    }

    // WebKit 1.17.1 lint reports every Kotlin base constructor despite this actual override.
    // Upstream fix: androidx/androidx commit 8003b908c7bfaecd6636512ad5cb1720a142ad51.
    // Suppress only that defective check on this factory; renderer loss still disposes and returns true.
    @SuppressLint("MissingOnRenderProcessGone")
    private fun createWebViewClient(): WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse {
            val uri = request.url
            if (request.method != "GET" || !allowedResource(uri)) return denied()
            return loader.shouldInterceptRequest(uri) ?: denied()
        }
        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
            fail("renderer-navigation-blocked")
            return true
        }
        override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
            fail("renderer-resource-error")
        }
        override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
            fail("renderer-process-gone")
            dispose()
            return true
        }
    }

    /** Keep native focus and the renderer's supported focus API in agreement. */
    fun focusRenderer() {
        checkMain()
        if (closed.get()) return
        view.requestFocus()
        view.evaluateJavascript("window.cloudyTerminalUi?.focus()", null)
    }

    /**
     * Sends one local edit/search command to the current ready renderer. Returns the outbound
     * sequence that the renderer echoes for results, or 0 when the attachment refuses the command;
     * callers must not replay a refused command on a later target.
     */
    fun sendUiCommand(command: TerminalUiCommand): Int {
        checkMain()
        if (closed.get() || !ready) return 0
        val payload = try {
            TerminalUiProtocol.encodeCommand(command)
        } catch (_: IllegalArgumentException) {
            return 0
        }
        return try {
            val sequence = nextSequence()
            send(TerminalFrame(TerminalFrame.UI_COMMAND, sequence, payload))
            sequence
        } catch (_: Exception) {
            fail("renderer-interaction-delivery-failure")
            0
        }
    }

    fun start() {
        checkMain()
        if (started || closed.get()) return
        started = true
        if (!featuresSupported) { fail("renderer-binary-api-unavailable"); return }
        if (!fontScale.isFinite() || fontScale <= 0f) { fail("renderer-font-scale-invalid"); return }
        startedAt = SystemClock.elapsedRealtime()
        main.postDelayed(deadline, 250)
        val screenReader = if (screenReaderRequired()) 1 else 0
        view.loadUrl("$ORIGIN${PREFIX}index.html#${identity.sessionId}/${identity.attachmentId}/$fontScale/$screenReader")
    }

    /** Transport workers may block here; no network, blocking wait or byte decoding occurs on the UI thread. */
    fun sendOutput(bytes: ByteArray, isStderr: Boolean) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "terminal-output-on-main" }
        if (!ready || closed.get()) throw TerminalFlowClosed("renderer-not-ready-or-closed")
        try {
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(bytes.size, offset + TerminalLimits.OUTPUT_CHUNK_BYTES)
                flow.put(bytes.copyOfRange(offset, end), isStderr)
                scheduleDrain()
                offset = end
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            fail("renderer-output-interrupted")
            throw TerminalFlowClosed("renderer-output-interrupted")
        } catch (error: TerminalFlowClosed) {
            fail(error.reason)
            throw error
        }
    }

    /** After all native stream producers exit, retain the last bytes until parser completion. */
    fun finishOutput() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "terminal-output-on-main" }
        try {
            flow.awaitDrained()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            fail("renderer-output-interrupted")
            throw TerminalFlowClosed("renderer-output-interrupted")
        } catch (error: TerminalFlowClosed) {
            fail(error.reason)
            throw error
        }
    }

    /** Safe from any thread; wake blocked SSH readers before any main-thread disposal. */
    fun close() {
        val wasOpen = closed.compareAndSet(false, true)
        flow.close()
        main.removeCallbacks(deadline)
        if (wasOpen) notifyClosed()
    }

    // xterm's screenReaderMode changes its input dispatch; forcing it on drops Android insertText.
    // Enable it for an actual spoken-feedback/touch-exploration service, never disable system a11y.
    private fun screenReaderRequired(): Boolean =
        accessibility?.isTouchExplorationEnabled == true ||
            accessibility?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)?.isNotEmpty() == true

    fun dispose() {
        checkMain()
        close()
        if (disposed) return
        disposed = true
        accessibility?.removeAccessibilityServicesStateChangeListener(accessibilityChanged)
        proxy = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(view, BRIDGE)
        }
        view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    private fun receive(message: WebMessageCompat, origin: Uri, isMainFrame: Boolean, reply: JavaScriptReplyProxy) {
        checkMain()
        if (closed.get()) return
        try {
            require(started && isMainFrame && origin.toString() == ORIGIN) { "terminal-origin" }
            require(message.type == WebMessageCompat.TYPE_ARRAY_BUFFER) { "terminal-message-type" }
            val frame = TerminalFrame.decode(message.arrayBuffer, identity)
            require(inboundSequence < Int.MAX_VALUE && frame.sequence == inboundSequence + 1) { "terminal-message-order" }
            inboundSequence = frame.sequence
            if (!ready) {
                require(frame.type == TerminalFrame.READY) { "terminal-ready-required" }
                if (SystemClock.elapsedRealtime() - startedAt >= TerminalLimits.RENDERER_DEADLINE_MILLIS) {
                    fail("renderer-ready-timeout")
                    return
                }
                val size = frame.size()
                proxy = reply
                ready = true
                listener.onReady(size)
                focusRenderer()
                return
            }
            when (frame.type) {
                TerminalFrame.INPUT -> sendAdmission(frame, listener.onInput(frame.payload))
                TerminalFrame.RESIZE -> sendAdmission(frame, listener.onResize(frame.size()))
                TerminalFrame.ACK -> {
                    val payload = ByteBuffer.wrap(frame.payload).order(ByteOrder.BIG_ENDIAN)
                    flow.acknowledge(payload.int, payload.int)
                }
                TerminalFrame.UI_REQUEST ->
                    listener.onUiRequest(TerminalUiProtocol.decodeRequest(frame.sequence, frame.payload))
                TerminalFrame.ERROR -> fail("renderer-script-failure")
                else -> fail("renderer-operation-rejected")
            }
        } catch (error: TerminalFlowClosed) { fail(error.reason) }
        catch (_: Exception) { fail("renderer-message-or-listener-failure") }
    }

    private fun sendAdmission(frame: TerminalFrame, result: TerminalAdmission) {
        send(TerminalFrame(TerminalFrame.ADMISSION, nextSequence(), TerminalFrame.admissionPayload(frame, result)))
    }

    private fun nextSequence(): Int {
        check(outboundSequence < Int.MAX_VALUE) { "terminal-sequence-exhausted" }
        return outboundSequence + 1
    }

    private fun send(frame: TerminalFrame) {
        checkMain()
        if (closed.get()) return
        // Allocate only for an actual post. A concurrent close may discard a claimed chunk,
        // but must not leave a sequence hole before the final CLOSED notification.
        check(frame.sequence == outboundSequence + 1)
        outboundSequence = frame.sequence
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)) {
            checkNotNull(proxy).postMessage(frame.encode(identity))
        } else fail("renderer-binary-api-unavailable")
    }

    private fun scheduleDrain() {
        if (!closed.get() && drainPosted.compareAndSet(false, true)) main.post(drain)
    }

    private val drain = Runnable {
        try {
            if (!closed.get()) {
                // Four frames per turn bound main-thread work; there is at most one queued drain.
                repeat(4) {
                    if (!flow.hasQueued() || closed.get()) return@repeat
                    val sequence = nextSequence()
                    val chunk = flow.claim(sequence) ?: return@repeat
                    send(TerminalFrame(TerminalFrame.OUTPUT, sequence, chunk.bytes, chunk.isStderr))
                }
            }
        } catch (error: TerminalFlowClosed) { fail(error.reason) }
        catch (_: Exception) { fail("renderer-output-delivery-failure") }
        finally {
            drainPosted.set(false)
            if (flow.hasQueued()) scheduleDrain()
        }
    }

    private val deadline = object : Runnable {
        override fun run() {
            if (closed.get()) return
            if (!ready && SystemClock.elapsedRealtime() - startedAt >= TerminalLimits.RENDERER_DEADLINE_MILLIS) {
                fail("renderer-ready-timeout")
            } else {
                val reason = flow.expiredReason()
                if (reason != null) fail(reason) else main.postDelayed(this, 250)
            }
        }
    }

    private fun fail(reason: String) {
        if (!closed.compareAndSet(false, true)) return
        flow.close(reason)
        main.removeCallbacks(deadline)
        notifyClosed()
        // Always queue, including renderer callbacks: listener disposal must not reenter WebView delivery.
        main.post { listener.onFailure(reason) }
    }

    private fun notifyClosed() {
        main.post {
            if (!disposed) {
                try {
                    proxy?.let { current ->
                        val sequence = nextSequence()
                        outboundSequence = sequence
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)) {
                            current.postMessage(TerminalFrame(TerminalFrame.CLOSED, sequence, ByteArray(0)).encode(identity))
                        }
                    }
                } catch (_: Exception) { /* Native admission is already closed; disposal remains independent. */ }
            }
        }
    }

    private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) { "terminal-main-thread-required" } }

    companion object {
        private const val HOST = "appassets.androidplatform.net"
        private const val ORIGIN = "https://$HOST"
        private const val PREFIX = "/assets/terminal/"
        private const val BRIDGE = "cloudyTerminal"
        private val ASSETS = mapOf("index.html" to "text/html", "terminal.js" to "text/javascript",
            "terminal.css" to "text/css", "vendor/xterm.js" to "text/javascript",
            "vendor/xterm.css" to "text/css", "vendor/addon-fit.js" to "text/javascript",
            "vendor/addon-search.js" to "text/javascript")
        private val RESPONSE_HEADERS = mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff",
            "Content-Security-Policy" to "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
                "img-src 'none'; font-src 'none'; connect-src 'none'; frame-src 'none'; object-src 'none'; " +
                "base-uri 'none'; form-action 'none'; frame-ancestors 'none'")

        private fun allowedResource(uri: Uri): Boolean = uri.scheme == "https" && uri.host == HOST &&
            uri.port == -1 && uri.userInfo == null && uri.query == null &&
            ASSETS.containsKey(uri.encodedPath?.removePrefix(PREFIX)) && uri.encodedPath?.startsWith(PREFIX) == true

        private fun denied(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
            RESPONSE_HEADERS, ByteArrayInputStream(ByteArray(0)))
    }
}
