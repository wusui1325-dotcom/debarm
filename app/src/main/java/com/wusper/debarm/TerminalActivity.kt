package com.wusper.debarm

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wusper.debarm.terminal.TerminalBridge
import com.wusper.debarm.terminal.TerminalInputView
import com.wusper.debarm.terminal.TerminalSession
import com.wusper.debarm.ui.DebArmTheme
import com.wusper.debarm.ui.Gold
import com.wusper.debarm.ui.Ink
import com.wusper.debarm.ui.Paper

class TerminalActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LAUNCH_CODEX = "launch_codex"
    }

    private var session: TerminalSession? = null
    private var webView: WebView? = null
    private var inputView: TerminalInputView? = null
    private var ctrlOn = false
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
        )
        enableEdgeToEdge()
        val paths = (application as DebArmApp).env.pathsPublic
        setContent {
            DebArmTheme {
                var ctrl by remember { mutableStateOf(false) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Ink)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    TerminalView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onReady = { wv, iv ->
                            webView = wv
                            inputView = iv
                            iv.onInput = { writePossiblyCtrl(it) }
                            attachSession(wv, paths)
                            iv.post { iv.showKeyboard() }
                        },
                    )
                    ExtraKeys(
                        ctrlOn = ctrl,
                        onToggleCtrl = {
                            ctrl = !ctrl
                            ctrlOn = ctrl
                        },
                        onShowKeyboard = { inputView?.showKeyboard() },
                        onSend = { data -> writePossiblyCtrl(data) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inputView?.showKeyboard()
    }

    private fun writePossiblyCtrl(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (ctrlOn && bytes.size == 1) {
            val c = bytes[0].toInt() and 0xff
            if (c in 64..127) {
                session?.write(byteArrayOf((c and 31).toByte()))
                return
            }
        }
        session?.write(bytes)
    }

    private fun attachSession(wv: WebView, paths: com.wusper.debarm.core.DebArmPaths) {
        if (started) return
        val launchCodex = intent.getBooleanExtra(EXTRA_LAUNCH_CODEX, BuildConfig.CODEX_EDITION)
        val session = TerminalSession(
            paths = paths,
            launchCodex = launchCodex,
            onOutput = { b64 ->
                wv.evaluateJavascript("window.DebArmTerm && DebArmTerm.writeBase64('$b64')", null)
            },
            onExit = { code ->
                val msg = if (code >= 0) "工作階段已結束（$code）" else "工作階段已結束"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            },
        )
        this.session = session
        val bridge = TerminalBridge(
            onInput = { bytes -> writePossiblyCtrl(bytes) },
            onSize = { rows, cols -> session.resize(rows, cols) },
            onReady = {
                if (!started) {
                    started = true
                    session.start(24, 80)
                    wv.postDelayed({
                        wv.evaluateJavascript("window.DebArmTerm && DebArmTerm.fit()", null)
                    }, 40)
                    inputView?.showKeyboard()
                }
            },
        )
        wv.addJavascriptInterface(bridge, "DebArm")
        wv.loadUrl("file:///android_asset/terminal/index.html")
    }

    override fun onDestroy() {
        session?.close()
        session = null
        webView?.apply {
            loadUrl("about:blank")
            destroy()
        }
        webView = null
        inputView = null
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerminalView(
    modifier: Modifier,
    onReady: (WebView, TerminalInputView) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val webView = WebView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0xFF14120F.toInt())
                isFocusable = false
                isFocusableInTouchMode = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = true
                }
            }
            val input = TerminalInputView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(1, 1)
            }
            webView.setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN) input.showKeyboard()
                false
            }
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(webView)
                addView(input)
                onReady(webView, input)
            }
        },
    )
}

@Composable
private fun ExtraKeys(
    ctrlOn: Boolean,
    onToggleCtrl: () -> Unit,
    onShowKeyboard: () -> Unit,
    onSend: (ByteArray) -> Unit,
) {
    val keys = listOf(
        KeySpec("鍵盤", special = "kbd"),
        KeySpec("Esc", "\u001b"),
        KeySpec("Tab", "\t"),
        KeySpec("Ctrl", special = "ctrl"),
        KeySpec("^C", "\u0003"),
        KeySpec("^D", "\u0004"),
        KeySpec("↑", "\u001b[A"),
        KeySpec("↓", "\u001b[B"),
        KeySpec("←", "\u001b[D"),
        KeySpec("→", "\u001b[C"),
        KeySpec("Home", "\u001b[H"),
        KeySpec("End", "\u001b[F"),
        KeySpec("-", "-"),
        KeySpec("/", "/"),
        KeySpec("|", "|"),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(Paper)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        keys.forEach { key ->
            val selected = key.special == "ctrl" && ctrlOn
            TextButton(
                onClick = {
                    when (key.special) {
                        "ctrl" -> onToggleCtrl()
                        "kbd" -> onShowKeyboard()
                        else -> onSend(key.text.encodeToByteArray())
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selected) Ink else Gold,
                    containerColor = if (selected) Gold else Color.Transparent,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                Text(key.label, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

private data class KeySpec(val label: String, val text: String = "", val special: String? = null)
