package com.wusper.debarm.terminal

import android.os.Handler
import android.os.Looper
import com.wusper.debarm.core.CodexRuntime
import com.wusper.debarm.core.DebArmPaths
import com.wusper.debarm.core.Proot
import com.wusper.debarm.core.Pty
import java.io.ByteArrayOutputStream
import com.wusper.debarm.core.CodexCatalog
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Runs the official ChatGPT desktop launcher (Xvfb + x11vnc + noVNC)
 * in a PTY and reports text logs plus when the display port is up.
 */
class DesktopSession(
    private val paths: DebArmPaths,
    private val screenWidth: Int = com.wusper.debarm.core.CodexCatalog.screenWidth,
    private val screenHeight: Int = com.wusper.debarm.core.CodexCatalog.screenHeight,
    private val onLog: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onExit: (code: Int) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var masterFd = -1
    private var pid = -1
    private val main = Handler(Looper.getMainLooper())
    private val writeLock = Any()
    private val readyOnce = AtomicBoolean(false)

    val isLive: Boolean
        get() = running.get() && masterFd >= 0

    fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            paths.ensureWorkDirs()
            Proot.assertRunnable(paths)
            Pty.ensureLoaded()
            val argv = CodexRuntime.displayArgv(paths, screenWidth, screenHeight)
            val ids = Pty.nativeStart(
                argv = argv,
                envp = Proot.envp(paths),
                cwd = paths.filesDir.absolutePath,
                rows = 40,
                cols = 120,
            )
            synchronized(writeLock) {
                masterFd = ids[0]
                pid = ids[1]
            }
        } catch (e: Exception) {
            running.set(false)
            synchronized(writeLock) {
                masterFd = -1
                pid = -1
            }
            throw e
        }
        thread(name = "debarm-desktop-read", isDaemon = true) {
            val buf = ByteArray(32 * 1024)
            val pending = ByteArrayOutputStream(8 * 1024)
            fun flush() {
                if (pending.size() == 0) return
                val text = String(pending.toByteArray(), StandardCharsets.UTF_8)
                pending.reset()
                main.post { onLog(text) }
            }
            try {
                while (running.get()) {
                    val n = Pty.nativeRead(masterFd, buf)
                    if (n < 0) break
                    if (n == 0) continue
                    pending.write(buf, 0, n)
                    if (n < buf.size || pending.size() >= 4 * 1024) flush()
                }
            } catch (_: Exception) {
            } finally {
                flush()
                val code = runCatching { Pty.nativeWaitpid(pid, false) }.getOrDefault(-1)
                main.post { onExit(code) }
                close()
            }
        }
        thread(name = "debarm-desktop-port", isDaemon = true) {
            var attempts = 0
            // Do not load noVNC until both x11vnc (5900) and websockify (6080 + vnc.html)
            // are actually accepting connections. HTTP on 6080 alone is not enough:
            // the page can render while the WebSocket proxy to 5900 still fails.
            while (running.get() && attempts < 180) {
                if (isDisplayStackReady()) {
                    try {
                        Thread.sleep(400)
                    } catch (_: InterruptedException) {
                        return@thread
                    }
                    if (running.get() && isDisplayStackReady() && readyOnce.compareAndSet(false, true)) {
                        main.post { onReady() }
                    }
                    return@thread
                }
                attempts++
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    return@thread
                }
            }
        }
    }

    fun close() {
        running.set(false)
        val p: Int
        val fd: Int
        synchronized(writeLock) {
            p = pid
            fd = masterFd
            pid = -1
            masterFd = -1
        }
        if (p > 0) {
            Pty.nativeKill(p, 15)
            Thread.sleep(80)
            Pty.nativeKill(p, 9)
            Pty.nativeWaitpid(p, false)
        }
        if (fd >= 0) Pty.nativeClose(fd)
    }

    companion object {
        fun isPortOpen(host: String = "127.0.0.1", port: Int = CodexCatalog.displayPort): Boolean {
            return try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 250)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        fun isNovncHttpReady(host: String = "127.0.0.1", port: Int = CodexCatalog.displayPort): Boolean {
            return try {
                val url = URL("http://$host:$port/vnc.html")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 400
                conn.readTimeout = 400
                conn.instanceFollowRedirects = false
                conn.requestMethod = "GET"
                try {
                    conn.responseCode in 200..399
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                false
            }
        }

        fun isDisplayStackReady(): Boolean {
            return isPortOpen("127.0.0.1", CodexCatalog.vncPort) &&
                isPortOpen("127.0.0.1", CodexCatalog.displayPort) &&
                isNovncHttpReady()
        }
    }
}
