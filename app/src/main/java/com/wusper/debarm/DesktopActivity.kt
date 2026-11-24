package com.wusper.debarm

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wusper.debarm.core.CodexCatalog
import com.wusper.debarm.core.CodexRuntime
import com.wusper.debarm.core.DebArmPaths
import com.wusper.debarm.terminal.DesktopSession
import com.wusper.debarm.terminal.NovncBridge
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
    private val main = Handler(Looper.getMainLooper())
    private val statusText = mutableStateOf("正在啟動顯示伺服器…")
    private val logText = mutableStateOf("")
    private val stackReady = mutableStateOf(false)
    private val connected = mutableStateOf(false)
    private val showChrome = mutableStateOf(true)
    private val showLog = mutableStateOf(true)
    private val menuOpen = mutableStateOf(false)

    private val novncBridge = NovncBridge(
        connectedCallback = { onNovncConnected() },
        fullscreenCallback = { enterImmersive() },
    )

    private val pollConnected = object : Runnable {
        override fun run() {
            val wv = webView ?: return
            if (connected.value) return
            injectNovncSkin(wv)
            wv.evaluateJavascript(NOVNC_CONNECTED_PROBE) { result ->
                if (result == "\"1\"" || result == "1") {
                    onNovncConnected()
                } else if (!isDestroyed && !connected.value) {
                    main.postDelayed(this, 400)
                }
            }
        }
    }

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
        val (screenW, screenH) = chooseDisplaySize(this)
        setContent {
            DebArmTheme {
                DesktopScreen(
                    status = statusText.value,
                    log = logText.value,
                    stackReady = stackReady.value,
                    connected = connected.value,
                    showChrome = showChrome.value,
                    showLog = showLog.value,
                    menuOpen = menuOpen.value,
                    onToggleLog = { showLog.value = !showLog.value },
                    onToggleMenu = { menuOpen.value = !menuOpen.value },
                    onCloseMenu = { menuOpen.value = false },
                    onEnterImmersive = { enterImmersive() },
                    onExitImmersive = { exitImmersive() },
                    onOpenTerminal = {
                        startActivity(
                            Intent(this, TerminalActivity::class.java)
                                .putExtra(TerminalActivity.EXTRA_LAUNCH_CODEX, true),
                        )
                    },
                    onLeave = { finish() },
                    onWebView = { wv -> attachSession(wv, paths, screenW, screenH) },
                )
            }
        }
    }

    private fun enterImmersive() {
        showChrome.value = false
        menuOpen.value = false
        applyImmersiveMode(true)
    }

    private fun exitImmersive() {
        menuOpen.value = false
        showChrome.value = true
        applyImmersiveMode(false)
    }

    private fun applyImmersiveMode(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun onNovncConnected() {
        if (connected.value) return
        connected.value = true
        showLog.value = false
        menuOpen.value = false
        statusText.value = "畫面已連線 · 官方 ChatGPT 桌面"
        enterImmersive()
    }

    /**
     * Official ChatGPT / Electron / Chromium writes a lot of stderr in PRoot
     * (D-Bus UPower, "Sign in to ChatGPT to check remote control
     * authorization", "Workspace routing is unavailable", HTTP 432
     * /wham/usage, and any line containing "failed"). Those are expected
     * before login and must never be treated as a fatal launch failure.
     * Only the start-script's own "debarm:" fatal lines count, and not
     * the "first start failed, retrying" attempt.
     */
    private fun isFatalStartupLine(chunk: String): Boolean {
        return chunk.lineSequence().any { raw ->
            val line = raw.trim()
            if (!line.startsWith("debarm:")) return@any false
            val lower = line.lowercase()
            if ("retrying" in lower) return@any false
            "failed to" in lower ||
                "is not installed" in lower ||
                "not found" in lower ||
                "stopped before" in lower
        }
    }

    private fun appendLog(chunk: String) {
        val next = (logText.value + chunk).takeLast(8000)
        logText.value = next
        if (connected.value) return
        val line = chunk
        when {
            "starting Xvfb" in line -> statusText.value = "正在啟動顯示伺服器（Xvfb）…"
            "Xvfb is ready" in line -> statusText.value = "Xvfb 已就緒，正在啟動 x11vnc…"
            "x11vnc is listening" in line -> statusText.value = "x11vnc 已就緒，正在啟動 websockify…"
            "noVNC is listening" in line || "display stack ready" in line ->
                statusText.value = "websockify 已就緒，正在連上畫面…"
            "launching official app" in line ->
                if (!stackReady.value) statusText.value = "畫面通道已就緒，正在啟動官方 ChatGPT…"
            "chatgpt exited" in line ->
                statusText.value = "官方程式已結束。請看紀錄，或開啟終端後援。"
            isFatalStartupLine(line) -> {
                // Real start-script failure. Expected Electron/D-Bus noise
                // never reaches here. After the display stack is already
                // up, leave the desktop visible and let onExit report a
                // process death if it actually happens.
                if (!stackReady.value) {
                    statusText.value = "啟動失敗：${
                        chunk.trim().lineSequence()
                            .firstOrNull { it.trim().startsWith("debarm:") }
                            .orEmpty()
                    }"
                    showLog.value = true
                    showChrome.value = true
                    applyImmersiveMode(false)
                }
            }
        }
    }

    private fun attachSession(wv: WebView, paths: DebArmPaths, screenW: Int, screenH: Int) {
        if (started) return
        started = true
        webView = wv
        wv.addJavascriptInterface(novncBridge, "DebArmDesktop")
        statusText.value = "正在啟動顯示伺服器…"
        val session = DesktopSession(
            paths = paths,
            screenWidth = screenW,
            screenHeight = screenH,
            onLog = { appendLog(it) },
            onReady = {
                stackReady.value = true
                showLog.value = false
                statusText.value = "正在連上畫面…"
                wv.loadUrl(NOVNC_URL)
                main.postDelayed(pollConnected, 200)
            },
            onExit = { code ->
                main.removeCallbacks(pollConnected)
                if (!connected.value) {
                    statusText.value = if (code >= 0) {
                        "工作階段已結束（exit $code）。請看紀錄。"
                    } else {
                        "工作階段已結束。請看紀錄。"
                    }
                    showLog.value = true
                    showChrome.value = true
                    applyImmersiveMode(false)
                } else {
                    statusText.value = "官方程式已結束（exit $code）"
                    showChrome.value = true
                    applyImmersiveMode(false)
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
            showChrome.value = true
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        main.removeCallbacks(pollConnected)
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
            "http://127.0.0.1:${CodexCatalog.displayPort}/vnc.html" +
                "?autoconnect=true&reconnect=true&reconnect_delay=2000" +
                "&resize=scale&encrypt=0&host=127.0.0.1" +
                "&port=${CodexCatalog.displayPort}&path=websockify"

        private const val NOVNC_CONNECTED_PROBE =
            "(function(){try{return document.documentElement.classList.contains('noVNC_connected')?'1':'0';}catch(e){return '0';}})()"

        // Hide noVNC chrome / Connecting overlay, fill the viewport, and
        // neuter the Fullscreen API so WebView cannot throw a red error.
        val NOVNC_SKIN_JS = """
            (function(){
              if (!document.documentElement) return;
              var css = document.getElementById('debarm-novnc-skin');
              if (!css) {
                css = document.createElement('style');
                css.id = 'debarm-novnc-skin';
                css.textContent = [
                  '#noVNC_transition,#noVNC_connect_dlg,#noVNC_control_bar_anchor,',
                  '#noVNC_control_bar,#noVNC_control_bar_hint,#noVNC_hint_anchor,',
                  '#noVNC_status,#noVNC_fullscreen_button{display:none!important;',
                  'visibility:hidden!important;opacity:0!important;pointer-events:none!important;}',
                  'html,body,#noVNC_container,#noVNC_screen{margin:0;padding:0;width:100%;',
                  'height:100%;background:#14120F!important;overflow:hidden;}',
                  '#noVNC_container{border-radius:0!important;}',
                  '#noVNC_container canvas,#noVNC_screen canvas{',
                  'object-fit:contain!important;object-position:center!important;}'
                ].join('');
                (document.head||document.documentElement).appendChild(css);
              }
              function appFs(){
                try{ if (window.DebArmDesktop) DebArmDesktop.requestFullscreen(); }catch(e){}
              }
              function patchFs(){
                try{ if (window.UI && UI.toggleFullscreen) UI.toggleFullscreen = appFs; }catch(e){}
                try{
                  var el = document.documentElement;
                  el.requestFullscreen = appFs;
                  el.webkitRequestFullscreen = appFs;
                  el.mozRequestFullScreen = appFs;
                  if (document.body) document.body.msRequestFullscreen = appFs;
                }catch(e){}
                var btn = document.getElementById('noVNC_fullscreen_button');
                if (btn) {
                  btn.onclick = function(ev){
                    if (ev){ ev.preventDefault(); ev.stopPropagation(); }
                    appFs();
                    return false;
                  };
                }
              }
              function fit(){
                try{
                  if (window.UI && UI.rfb) {
                    UI.rfb.scaleViewport = true;
                    UI.rfb.clipViewport = false;
                    UI.rfb.resizeSession = false;
                  }
                }catch(e){}
              }
              patchFs();
              fit();
              if (!window.__debarmFsTimer) {
                window.__debarmFsTimer = setInterval(function(){ patchFs(); fit(); }, 250);
                setTimeout(function(){ try{ clearInterval(window.__debarmFsTimer); }catch(e){} }, 12000);
              }
              if (!window.__debarmResizeHook) {
                window.__debarmResizeHook = true;
                window.addEventListener('resize', fit);
              }
              if (!window.__debarmErrorHook) {
                window.__debarmErrorHook = true;
                window.addEventListener('error', function(ev){
                  var msg = (ev && ev.message) ? String(ev.message) : '';
                  if (msg.indexOf('Fullscreen') !== -1) {
                    ev.preventDefault();
                    ev.stopImmediatePropagation();
                    appFs();
                    return true;
                  }
                }, true);
              }
              function notify(){
                try{ if (window.DebArmDesktop) DebArmDesktop.onConnected(); }catch(e){}
              }
              if (document.documentElement.classList.contains('noVNC_connected')) {
                notify();
                return;
              }
              if (!window.__debarmObs) {
                window.__debarmObs = new MutationObserver(function(){
                  if (document.documentElement.classList.contains('noVNC_connected')) {
                    try{ window.__debarmObs.disconnect(); }catch(e){}
                    notify();
                  }
                });
                window.__debarmObs.observe(document.documentElement, {attributes:true, attributeFilter:['class']});
              }
            })();
        """.trimIndent()

        fun injectNovncSkin(wv: WebView) {
            wv.evaluateJavascript(NOVNC_SKIN_JS, null)
        }

        fun chooseDisplaySize(activity: Activity): Pair<Int, Int> {
            // Use the physical display so Xvfb's aspect matches the
            // immersive WebView. A second short-side cap would also keep
            // the ratio, but real-metrics avoids letterboxing after the
            // status bar is hidden.
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            var rawW = metrics.widthPixels
            var rawH = metrics.heightPixels
            if (rawW <= 0 || rawH <= 0) {
                if (Build.VERSION.SDK_INT >= 30) {
                    val bounds: Rect = activity.windowManager.currentWindowMetrics.bounds
                    rawW = bounds.width()
                    rawH = bounds.height()
                }
            }
            val maxLong = 1920
            val longSide = maxOf(rawW, rawH).coerceAtLeast(1)
            val scale = minOf(1f, maxLong.toFloat() / longSide)
            var w = (rawW * scale).toInt().and(0x7FFFFFFE).coerceAtLeast(1024)
            var h = (rawH * scale).toInt().and(0x7FFFFFFE).coerceAtLeast(640)
            if (w % 2 != 0) w -= 1
            if (h % 2 != 0) h -= 1
            return w to h
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DesktopScreen(
    status: String,
    log: String,
    stackReady: Boolean,
    connected: Boolean,
    showChrome: Boolean,
    showLog: Boolean,
    menuOpen: Boolean,
    onToggleLog: () -> Unit,
    onToggleMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onEnterImmersive: () -> Unit,
    onExitImmersive: () -> Unit,
    onOpenTerminal: () -> Unit,
    onLeave: () -> Unit,
    onWebView: (WebView) -> Unit,
) {
    val immersive = connected && !showChrome
    BackHandler(enabled = connected) {
        when {
            menuOpen -> onCloseMenu()
            showLog && immersive -> onToggleLog()
            immersive -> onExitImmersive()
            else -> onLeave()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                if (newProgress >= 30) {
                                    DesktopActivity.injectNovncSkin(view)
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val host = request.url?.host
                                return host != "127.0.0.1" && host != "localhost"
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                DesktopActivity.injectNovncSkin(view)
                            }
                        }
                        addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                                if (v is WebView) {
                                    DesktopActivity.injectNovncSkin(v)
                                    v.evaluateJavascript(
                                        "try{window.dispatchEvent(new Event('resize'));}catch(e){}",
                                        null,
                                    )
                                }
                            }
                        }
                        onWebView(this)
                    }
                },
        )
        if (!immersive) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding(),
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
                        Text("開啟終端", color = Gold, fontSize = 13.sp)
                    }
                    if (connected) {
                        TextButton(onClick = onEnterImmersive) {
                            Text("進入全螢幕", color = Gold, fontSize = 13.sp)
                        }
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
                            .height(if (connected) 120.dp else 180.dp)
                            .background(Ink.copy(alpha = 0.92f))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
        if (stackReady && !connected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink),
                contentAlignment = Alignment.Center,
            ) {
                // Always a short connecting message — official Electron
                // stderr must not replace this with「啟動失敗」。
                Text("正在連上畫面…", color = Cream, fontSize = 15.sp)
            }
        }
        if (immersive) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                ) {
                    Text(
                        "選單",
                        color = Cream,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Paper.copy(alpha = 0.82f))
                            .clickable(onClick = onToggleMenu)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                    if (menuOpen) {
                        Column(
                            Modifier
                                .padding(top = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Paper.copy(alpha = 0.94f))
                                .padding(vertical = 4.dp),
                        ) {
                            MenuRow("退出全螢幕", onExitImmersive)
                            MenuRow(if (showLog) "隱藏紀錄" else "顯示紀錄", onToggleLog)
                            MenuRow("開啟終端", onOpenTerminal)
                        }
                    }
                }
                if (showLog) {
                    Text(
                        log.ifBlank { "（沒有新的紀錄）" },
                        color = Muted,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .background(Ink.copy(alpha = 0.88f))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Gold,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
