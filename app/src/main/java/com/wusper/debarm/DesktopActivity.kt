package com.wusper.debarm

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wusper.debarm.core.CodexCatalog
import com.wusper.debarm.core.CodexRuntime
import com.wusper.debarm.core.DebArmPaths
import com.wusper.debarm.terminal.DesktopSession
import com.wusper.debarm.ui.Cream
import com.wusper.debarm.ui.DebArmTheme
import com.wusper.debarm.ui.Gold
import com.wusper.debarm.ui.Ink
import com.wusper.debarm.ui.Muted
import com.wusper.debarm.ui.Paper

/**
 * One-tap official ChatGPT / Codex Linux desktop window.
 * The guest runs Xvfb + x11vnc + noVNC; this activity shows that
 * display in a WebView at http://127.0.0.1:6080.
 *
 * Does not touch the terminal xterm path.
 */
class DesktopActivity : ComponentActivity() {
    private var session: DesktopSession? = null
    private var webView: WebView? = null
    private var started = false
    private val statusText = mutableStateOf("正在啟動顯示伺服器…")
    private val logText = mutableStateOf("")
    private val connected = mutableStateOf(false)
    private val showLog = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val paths = DebArmPaths(this)
        if (!CodexRuntime.isInstalled(paths)) {
            Toast.makeText(this, "尚未安裝官方桌面套件，請先在首頁準備環境。", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            DebArmTheme {
                DesktopScreen(
                    status = statusText.value,
                    log = logText.value,
                    connected = connected.value,
                    showLog = showLog.value,
                    onToggleLog = { showLog.value = !showLog.value },
                    onOpenTerminal = {
                        startActivity(
                            Intent(this, TerminalActivity::class.java)
                                .putExtra(TerminalActivity.EXTRA_LAUNCH_CODEX, true),
                        )
                    },
                    onWebView = { wv -> attachSession(wv, paths) },
                )
            }
        }
    }

    private fun appendLog(chunk: String) {
        val next = (logText.value + chunk).takeLast(8000)
        logText.value = next
        val line = chunk
        when {
            "Xvfb is ready" in line -> statusText.value = "顯示伺服器已就緒，正在啟動 VNC…"
            "starting Xvfb" in line -> statusText.value = "正在啟動顯示伺服器（Xvfb）…"
            "x11vnc is listening" in line -> statusText.value = "VNC 已連線，正在啟動 noVNC…"
            "noVNC is listening" in line -> statusText.value = "畫面通道已就緒，正在啟動官方 ChatGPT…"
            "launching official app" in line -> statusText.value = "正在啟動官方 ChatGPT 桌面版…"
            "chatgpt exited" in line -> {
                if (!connected.value) statusText.value = "官方程式已結束。請看紀錄，或開啟終端後援。"
            }
            "failed" in line.lowercase() || "not found" in line.lowercase() || "not installed" in line.lowercase() -> {
                if (!connected.value) {
                    statusText.value = "啟動失敗：${chunk.trim().lineSequence().firstOrNull().orEmpty()}"
                    showLog.value = true
                }
            }
        }
    }

    private fun attachSession(wv: WebView, paths: DebArmPaths) {
        if (started) return
        started = true
        webView = wv
        statusText.value = "正在啟動顯示伺服器…"
        val session = DesktopSession(
            paths = paths,
            onLog = { appendLog(it) },
            onReady = {
                connected.value = true
                showLog.value = false
                statusText.value = "畫面已連線 · 官方 ChatGPT 桌面"
                wv.loadUrl(NOVNC_URL)
            },
            onExit = { code ->
                if (!connected.value) {
                    statusText.value = if (code >= 0) {
                        "工作階段已結束（exit $code）。請看紀錄。"
                    } else {
                        "工作階段已結束。請看紀錄。"
                    }
                    showLog.value = true
                } else {
                    statusText.value = "官方程式已結束（exit $code）"
                }
            },
        )
        this.session = session
        try {
            session.start()
        } catch (e: Exception) {
            started = false
            this.session = null
            val msg = "無法啟動官方桌面：${e.message}"
            statusText.value = msg
            showLog.value = true
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        session?.close()
        session = null
        webView?.apply {
            loadUrl("about:blank")
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        val NOVNC_URL =
            "http://127.0.0.1:${CodexCatalog.displayPort}/vnc.html?autoconnect=1&reconnect=1&resize=scale&reconnect_delay=2000"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DesktopScreen(
    status: String,
    log: String,
    connected: Boolean,
    showLog: Boolean,
    onToggleLog: () -> Unit,
    onOpenTerminal: () -> Unit,
    onWebView: (WebView) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Text(
            status,
            color = Cream,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Paper)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(Paper)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggleLog) {
                Text(if (showLog) "隱藏紀錄" else "顯示紀錄", color = Gold, fontSize = 13.sp)
            }
            TextButton(onClick = onOpenTerminal) {
                Text("開啟終端（後援）", color = Gold, fontSize = 13.sp)
            }
        }
        if (showLog) {
            Text(
                log.ifBlank { "等候顯示伺服器與官方程式的紀錄…" },
                color = Muted,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (connected) 120.dp else 220.dp)
                    .background(Ink)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(0xFF14120F.toInt())
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = false
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val host = request.url?.host
                            return host != "127.0.0.1" && host != "localhost"
                        }
                    }
                    onWebView(this)
                }
            },
        )
    }
}
