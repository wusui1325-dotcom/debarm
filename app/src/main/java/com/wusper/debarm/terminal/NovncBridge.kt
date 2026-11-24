package com.wusper.debarm.terminal

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Called from the injected noVNC skin. Method names must not collide
 * with constructor property names (see TerminalBridge / 1.1.8).
 */
class NovncBridge(
    private val connectedCallback: () -> Unit,
    private val fullscreenCallback: () -> Unit,
    private val pointerUpCallback: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val connectedOnce = AtomicBoolean(false)

    @JavascriptInterface
    fun onConnected() {
        if (connectedOnce.compareAndSet(false, true)) {
            main.post { connectedCallback() }
        }
    }

    @JavascriptInterface
    fun requestFullscreen() {
        main.post { fullscreenCallback() }
    }

    @JavascriptInterface
    fun onPointerUp() {
        main.post { pointerUpCallback() }
    }
}
