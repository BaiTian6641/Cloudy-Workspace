package io.github.baitian6641.cloudy.testing.ime

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch

/** A real local WebView editor isolates the IME fixture from xterm; no product/backend claim. */
class ImeFixtureTestActivity : Activity() {
    val loaded = CountDownLatch(1)
    lateinit var editor: WebView
        private set

    @SuppressLint("SetJavaScriptEnabled") // Only the fixed network-blocked qualification document.
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        editor = WebView(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
            }
        }
        setContentView(editor)
        editor.loadDataWithBaseURL("https://ime-fixture.invalid/", """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; form-action 'none'">
            <style>body{margin:0}textarea{box-sizing:border-box;width:100%;height:80vh;font:20px monospace}</style>
            </head><body><textarea id="editor" autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false"></textarea></body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
    }

    override fun onDestroy() {
        if (::editor.isInitialized) editor.destroy()
        super.onDestroy()
    }
}
