package io.github.baitian6641.cloudy.terminal

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

/** Test-only host of the actual packaged renderer; never part of either product APK. */
class TerminalRendererTestActivity : Activity() {
    val ready = CountDownLatch(1)
    val inputs = LinkedBlockingQueue<ByteArray>()
    val failures = LinkedBlockingQueue<String>()
    lateinit var renderer: TerminalWebViewBridge
        private set

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        renderer = TerminalWebViewBridge(this,
            TerminalIdentity(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
            object : TerminalWebViewBridge.Listener {
                override fun onReady(size: TerminalSize) { ready.countDown() }
                override fun onInput(bytes: ByteArray): TerminalAdmission {
                    inputs.add(bytes.copyOf())
                    return TerminalAdmission.ACCEPTED
                }
                override fun onResize(size: TerminalSize) = TerminalAdmission.ACCEPTED
                override fun onFailure(reason: String) { failures.add(reason) }
            })
        setContentView(renderer.view)
        renderer.start()
    }

    override fun onDestroy() {
        if (::renderer.isInitialized) renderer.dispose()
        super.onDestroy()
    }
}
