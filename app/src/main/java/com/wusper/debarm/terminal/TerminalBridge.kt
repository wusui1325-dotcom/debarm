package com.wusper.debarm.terminal

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface

/**
 * Bridge called from the inlined xterm page. Every entry point is invoked
 * on the WebView / binder thread, so work is posted to the main looper.
 *
 * Parameter names of the @JavascriptInterface methods must not match the
 * constructor properties: a previous onSize(cols, rows) called onSize(rows,
 * cols), which Kotlin resolved as recursion (the property was shadowed)
 * and WebView reported "Java exception was raised during method invocation".
 */
class TerminalBridge(
    private val handleInput: (ByteArray) -> Unit,
    private val handleSize: (rows: Int, cols: Int) -> Unit,
    private val handleReady: () -> Unit,
    private val handleError: (String) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onInput(b64: String?) {
        if (b64.isNullOrEmpty()) return
        val bytes = try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w(TAG, "onInput decode failed", e)
            return
        }
        if (bytes.isEmpty()) return
        runOnMain {
            try {
                handleInput(bytes)
            } catch (e: Exception) {
                Log.e(TAG, "handleInput failed", e)
            }
        }
    }

    /**
     * JS numbers arrive as doubles. Accepting Double (not Int) avoids a
     * type-mismatch exception on some WebView versions; we truncate here.
     */
    @JavascriptInterface
    fun onSize(cols: Double, rows: Double) {
        val c = cols.toInt()
        val r = rows.toInt()
        if (c <= 0 || r <= 0 || c > 512 || r > 512) return
        runOnMain {
            try {
                handleSize(r, c)
            } catch (e: Exception) {
                Log.e(TAG, "handleSize failed cols=$c rows=$r", e)
            }
        }
    }

    @JavascriptInterface
    fun onReady() {
        runOnMain {
            try {
                handleReady()
            } catch (e: Exception) {
                Log.e(TAG, "handleReady failed", e)
            }
        }
    }

    @JavascriptInterface
    fun onError(message: String?) {
        val msg = message ?: "未知錯誤"
        Log.e(TAG, "page error: $msg")
        runOnMain {
            try {
                handleError(msg)
            } catch (e: Exception) {
                Log.e(TAG, "handleError failed", e)
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else main.post(block)
    }

    companion object {
        private const val TAG = "DebArmBridge"
    }
}
