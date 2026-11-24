package com.wusper.debarm.terminal

import android.webkit.JavascriptInterface
import android.util.Base64

class TerminalBridge(
    private val onInput: (ByteArray) -> Unit,
    private val onSize: (rows: Int, cols: Int) -> Unit,
    private val onReady: () -> Unit,
) {
    @JavascriptInterface
    fun onInput(b64: String) {
        val bytes = try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (_: Exception) {
            return
        }
        if (bytes.isNotEmpty()) onInput(bytes)
    }

    @JavascriptInterface
    fun onSize(cols: Int, rows: Int) {
        if (cols > 0 && rows > 0) onSize(rows, cols)
    }

    @JavascriptInterface
    fun onReady() {
        onReady.invoke()
    }
}
