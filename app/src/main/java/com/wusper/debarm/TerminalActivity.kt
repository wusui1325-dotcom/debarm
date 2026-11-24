package com.wusper.debarm

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
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
import com.wusper.debarm.terminal.TerminalBuffer
import com.wusper.debarm.terminal.TerminalInputView
import com.wusper.debarm.terminal.TerminalSession
import com.wusper.debarm.ui.Cream
import com.wusper.debarm.ui.DebArmTheme
import com.wusper.debarm.ui.Gold
import com.wusper.debarm.ui.Ink
import com.wusper.debarm.ui.Muted
import com.wusper.debarm.ui.Paper
import java.nio.charset.StandardCharsets

/**
 * Terminal screen. Output is rendered by a native TextView, not xterm.js.
 *
 * 1.1.0–1.1.4 painted PTY output only through a WebView calling into
 * xterm.js via evaluateJavascript. If the page's relative scripts failed
 * to load (common with file:///android_asset and
 * allowFileAccessFromFileURLs=false, especially on Xiaomi WebView), or if
 * the first flush ran on the JavascriptInterface background thread, the
 * canvas stayed the page background colour (#14120F) with no prompt. That
 * matches the 1.1.4 screenshots: a black terminal, Send appearing to do
 * nothing because echo never became visible.
 */
class TerminalActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LAUNCH_CODEX = "launch_codex"
    }

    private var session: TerminalSession? = null
    private var commandField: EditText? = null
    private var outputView: TextView? = null
    private var scroller: ScrollView? = null
    private var ctrlOn = false
    private var started = false
    private val pendingInput = ArrayList<ByteArray>()
    private val buffer = TerminalBuffer()
    private val statusText = mutableStateOf("正在啟動終端…")
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
                val status by statusText
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Ink)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    Text(
                        status,
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Paper)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    TerminalOutputView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onReady = { scroll, tv ->
                            scroller = scroll
                            outputView = tv
                            if (!started) attachSession(paths)
                        },
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
                        onSend = { data -> writeToPty(itOrToast(data)) },
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

    private fun itOrToast(data: ByteArray): ByteArray = data

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
            statusText.value = "無法寫入 PTY。請再試一次，或關閉後重新開啟終端。"
            Toast.makeText(this, statusText.value, Toast.LENGTH_SHORT).show()
        } else if (line.isNotEmpty()) {
            // If the shell echoes, we will also see the line from PTY output.
            // If echo is off, still show what we sent so the screen is not silent.
            statusText.value = "已送出 ${line.length} 字元 + Enter · 已收 ${buffer.bytesIn} 字元"
        } else {
            statusText.value = "已送出 Enter · 已收 ${buffer.bytesIn} 字元"
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
        if (!ok) pendingInput.add(data.copyOf())
        return ok
    }

    private fun flushPendingInput() {
        if (pendingInput.isEmpty()) return
        val queued = ArrayList(pendingInput)
        pendingInput.clear()
        queued.forEach { writeToPty(it) }
    }

    private fun appendOutput(bytes: ByteArray) {
        val snapshot = buffer.append(bytes)
        val tv = outputView
        if (tv == null) return
        tv.text = snapshot
        scroller?.post { scroller?.fullScroll(ScrollView.FOCUS_DOWN) }
        statusText.value = "PTY 已連線 · 已收 ${buffer.bytesIn} 字元"
    }

    private fun appendHost(msg: String) {
        buffer.appendHostMessage(msg)
        outputView?.text = buffer.snapshot()
        scroller?.post { scroller?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun attachSession(paths: com.wusper.debarm.core.DebArmPaths) {
        if (started) return
        started = true
        appendHost("正在啟動 PTY 與登入 shell…")
        statusText.value = "正在啟動 PTY…"
        val launchCodex = intent.getBooleanExtra(EXTRA_LAUNCH_CODEX, BuildConfig.CODEX_EDITION)
        val session = TerminalSession(
            paths = paths,
            launchCodex = launchCodex,
            onOutput = { b64 ->
                val bytes = try {
                    Base64.decode(b64, Base64.NO_WRAP)
                } catch (_: Exception) {
                    return@TerminalSession
                }
                appendOutput(bytes)
            },
            onExit = { code ->
                val msg = if (code >= 0) "工作階段已結束（exit $code）" else "工作階段已結束"
                statusText.value = msg
                appendHost(msg)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            },
        )
        this.session = session
        try {
            session.start(24, 80)
            flushPendingInput()
            statusText.value = if (session.isLive) {
                "PTY 已啟動，等候 shell 輸出…"
            } else {
                "PTY 啟動後未進入連線狀態"
            }
            // If no bytes arrive shortly, say so on-screen instead of staying blank.
            outputView?.postDelayed({
                if (buffer.bytesIn == 0L && session.isLive) {
                    appendHost("PTY 已啟動，但 2 秒內沒有收到任何輸出。請在下方輸入 uname -m 後按「送出」。")
                    statusText.value = "PTY 已啟動但尚無輸出 · 請按送出測試"
                }
            }, 2000)
        } catch (e: Exception) {
            started = false
            this.session = null
            val msg = "無法啟動終端：${e.message}"
            statusText.value = msg
            appendHost(msg)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        session?.close()
        session = null
        commandField = null
        outputView = null
        scroller = null
        requestBarFocus = null
        super.onDestroy()
    }
}

@Composable
private fun TerminalOutputView(
    modifier: Modifier,
    onReady: (ScrollView, TextView) -> Unit,
) {
    AndroidView(
        modifier = modifier.background(Ink),
        factory = { ctx ->
            val tv = TextView(ctx).apply {
                setTextColor(0xFFF6F1E8.toInt())
                setBackgroundColor(0xFF12100D.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.MONOSPACE
                setPadding(
                    (8 * ctx.resources.displayMetrics.density).toInt(),
                    (8 * ctx.resources.displayMetrics.density).toInt(),
                    (8 * ctx.resources.displayMetrics.density).toInt(),
                    (8 * ctx.resources.displayMetrics.density).toInt(),
                )
                setTextIsSelectable(true)
                gravity = Gravity.TOP or Gravity.START
                text = ""
            }
            val scroll = ScrollView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0xFF12100D.toInt())
                isFillViewport = true
                addView(
                    tv,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            onReady(scroll, tv)
            scroll
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
