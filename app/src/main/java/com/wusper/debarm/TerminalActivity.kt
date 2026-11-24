package com.wusper.debarm

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class TerminalActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LAUNCH_CODEX = "launch_codex"
    }

    private var session: TerminalSession? = null
    private var webView: WebView? = null
    private var inputView: TerminalInputView? = null
    private var commandField: EditText? = null
    private var ctrlOn = false
    private var started = false
    private var jsReady = false
    private val pendingOutput = ArrayList<String>()
    private val pendingInput = ArrayList<ByteArray>()
    private var requestBarFocus: (() -> Unit)? = null

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
                            iv.onInput = { writeToPty(it) }
                            attachSession(wv, paths)
                        },
                        onTap = { requestBarFocus?.invoke() },
                    )
                    TerminalInputBar(
                        onFieldReady = { field ->
                            commandField = field
                            requestBarFocus = {
                                field.requestFocus()
                                field.post {
                                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                    imm.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                }
                            }
                            field.post { requestBarFocus?.invoke() }
                        },
                        onSubmit = { line -> submitLine(line) },
                    )
                    ExtraKeys(
                        ctrlOn = ctrl,
                        onToggleCtrl = {
                            ctrl = !ctrl
                            ctrlOn = ctrl
                        },
                        onShowKeyboard = { requestBarFocus?.invoke() },
                        onSend = { data -> writeToPty(data) },
                        onSubmit = { submitFromField() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { requestBarFocus?.invoke() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && TerminalInputView.isEnterKey(event)) {
            submitFromField()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Send the command box text plus CR, or CR alone if the box is empty. */
    private fun submitFromField() {
        val field = commandField
        val line = field?.text?.toString() ?: ""
        field?.setText("")
        submitLine(line)
    }

    private fun submitLine(line: String) {
        val payload = if (line.isEmpty()) {
            TerminalInputView.CR
        } else {
            (line + "\r").toByteArray(StandardCharsets.UTF_8)
        }
        if (!writeToPty(payload)) {
            Toast.makeText(this, "無法送到終端。請再試一次，或關閉後重新開啟終端。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeToPty(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val data = if (ctrlOn && bytes.size == 1) {
            val c = bytes[0].toInt() and 0xff
            if (c in 64..127) byteArrayOf((c and 31).toByte()) else bytes
        } else {
            bytes
        }
        val s = session
        if (s == null) {
            pendingInput.add(data.copyOf())
            return false
        }
        val ok = s.write(data)
        if (!ok) {
            pendingInput.add(data.copyOf())
        }
        return ok
    }

    private fun flushPendingInput() {
        if (pendingInput.isEmpty()) return
        val queued = ArrayList(pendingInput)
        pendingInput.clear()
        queued.forEach { writeToPty(it) }
    }

    private fun pushToTerminal(wv: WebView, b64: String) {
        if (!jsReady) {
            pendingOutput.add(b64)
            return
        }
        val payload = JSONObject.quote(b64)
        wv.evaluateJavascript("window.DebArmTerm && DebArmTerm.writeBase64($payload)", null)
    }

    private fun attachSession(wv: WebView, paths: com.wusper.debarm.core.DebArmPaths) {
        if (started) return
        started = true
        val launchCodex = intent.getBooleanExtra(EXTRA_LAUNCH_CODEX, BuildConfig.CODEX_EDITION)
        val session = TerminalSession(
            paths = paths,
            launchCodex = launchCodex,
            onOutput = { b64 -> pushToTerminal(wv, b64) },
            onExit = { code ->
                val msg = if (code >= 0) "工作階段已結束（$code）" else "工作階段已結束"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            },
        )
        this.session = session
        val bridge = TerminalBridge(
            onInput = { bytes -> writeToPty(bytes) },
            onSize = { rows, cols -> session.resize(rows, cols) },
            onReady = {
                jsReady = true
                pendingOutput.forEach { pushToTerminal(wv, it) }
                pendingOutput.clear()
                wv.postDelayed({
                    wv.evaluateJavascript("window.DebArmTerm && DebArmTerm.fit()", null)
                }, 40)
                wv.post { requestBarFocus?.invoke() }
            },
        )
        wv.addJavascriptInterface(bridge, "DebArm")
        wv.loadUrl("file:///android_asset/terminal/index.html")
        try {
            session.start(24, 80)
            flushPendingInput()
        } catch (e: Exception) {
            started = false
            this.session = null
            Toast.makeText(this, "無法啟動終端：${e.message}", Toast.LENGTH_LONG).show()
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
        inputView = null
        commandField = null
        requestBarFocus = null
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerminalView(
    modifier: Modifier,
    onReady: (WebView, TerminalInputView) -> Unit,
    onTap: () -> Unit,
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
                alpha = 0f
            }
            webView.setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN) onTap()
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
private fun TerminalInputBar(
    onFieldReady: (EditText) -> Unit,
    onSubmit: (String) -> Unit,
) {
    var field by remember { mutableStateOf<EditText?>(null) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Paper)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .padding(end = 8.dp),
            factory = { ctx ->
                val pad = (10 * ctx.resources.displayMetrics.density).toInt()
                EditText(ctx).apply {
                    hint = "輸入指令後按送出或 Enter"
                    isSingleLine = true
                    maxLines = 1
                    imeOptions = EditorInfo.IME_ACTION_SEND or
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                        EditorInfo.IME_FLAG_NO_FULLSCREEN
                    inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    setPadding(pad, pad, pad, pad)
                    setTextColor(0xFF1A1712.toInt())
                    setHintTextColor(0x731A1712)
                    setBackgroundColor(0xEBffffff.toInt())
                    importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
                    setOnEditorActionListener { v, actionId, event ->
                        val fromIme = actionId == EditorInfo.IME_ACTION_SEND ||
                            actionId == EditorInfo.IME_ACTION_GO ||
                            actionId == EditorInfo.IME_ACTION_DONE ||
                            actionId == EditorInfo.IME_ACTION_NEXT ||
                            actionId == EditorInfo.IME_ACTION_UNSPECIFIED
                        val fromKey = event != null &&
                            event.action == KeyEvent.ACTION_DOWN &&
                            TerminalInputView.isEnterKey(event)
                        if (fromIme || fromKey) {
                            val line = v.text?.toString() ?: ""
                            v.text = null
                            onSubmit(line)
                            true
                        } else {
                            false
                        }
                    }
                    setOnKeyListener { v, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN &&
                            (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                        ) {
                            val line = (v as EditText).text?.toString() ?: ""
                            v.setText("")
                            onSubmit(line)
                            true
                        } else {
                            false
                        }
                    }
                    field = this
                    onFieldReady(this)
                }
            },
        )
        Button(
            onClick = {
                val line = field?.text?.toString() ?: ""
                field?.setText("")
                onSubmit(line)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(48.dp),
        ) {
            Text("送出", fontSize = 15.sp)
        }
    }
}

@Composable
private fun ExtraKeys(
    ctrlOn: Boolean,
    onToggleCtrl: () -> Unit,
    onShowKeyboard: () -> Unit,
    onSend: (ByteArray) -> Unit,
    onSubmit: () -> Unit,
) {
    val keys = listOf(
        KeySpec("鍵盤", special = "kbd"),
        KeySpec("送出", special = "enter"),
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
                        "enter" -> onSubmit()
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
