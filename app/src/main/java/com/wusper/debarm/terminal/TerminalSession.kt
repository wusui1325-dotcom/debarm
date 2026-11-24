package com.wusper.debarm.terminal

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.wusper.debarm.core.DebArmPaths
import com.wusper.debarm.core.CodexRuntime
import com.wusper.debarm.core.Proot
import com.wusper.debarm.core.Pty
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TerminalSession(
    private val paths: DebArmPaths,
    private val launchCodex: Boolean = false,
    private val onOutput: (b64: String) -> Unit,
    private val onExit: (code: Int) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var masterFd = -1
    private var pid = -1
    private val main = Handler(Looper.getMainLooper())

    fun start(rows: Int, cols: Int) {
        if (!running.compareAndSet(false, true)) return
        paths.ensureWorkDirs()
        Proot.assertRunnable(paths)
        Pty.ensureLoaded()
        val argv = if (launchCodex) CodexRuntime.argv(paths) else Proot.argv(paths, emptyList(), loginShell = true)
        val ids = Pty.nativeStart(
            argv = argv,
            envp = Proot.envp(paths),
            cwd = paths.filesDir.absolutePath,
            rows = rows,
            cols = cols,
        )
        masterFd = ids[0]
        pid = ids[1]
        thread(name = "debarm-pty-read", isDaemon = true) {
            val buf = ByteArray(32 * 1024)
            val pending = ByteArrayOutputStream(32 * 1024)
            fun flush() {
                if (pending.size() == 0) return
                val b64 = Base64.encodeToString(pending.toByteArray(), Base64.NO_WRAP)
                pending.reset()
                main.post { onOutput(b64) }
            }
            try {
                while (running.get()) {
                    val n = Pty.nativeRead(masterFd, buf)
                    if (n < 0) break
                    if (n == 0) continue
                    pending.write(buf, 0, n)
                    // A short read usually means the burst ended (e.g. a prompt).
                    // Long full buffers are flushed in chunks for apt-like output.
                    if (n < buf.size || pending.size() >= 16 * 1024) {
                        flush()
                    }
                }
            } catch (_: Exception) {
            } finally {
                flush()
                val code = runCatching { Pty.nativeWaitpid(pid, false) }.getOrDefault(-1)
                main.post { onExit(code) }
                close()
            }
        }
    }

    fun write(bytes: ByteArray) {
        if (!running.get() || masterFd < 0 || bytes.isEmpty()) return
        var off = 0
        while (off < bytes.size) {
            val n = Pty.nativeWrite(masterFd, bytes, off, bytes.size - off)
            if (n <= 0) break
            off += n
        }
    }

    fun resize(rows: Int, cols: Int) {
        if (masterFd >= 0) Pty.nativeSetWinsize(masterFd, rows, cols)
    }

    fun close() {
        if (!running.getAndSet(false) && masterFd < 0) return
        running.set(false)
        val p = pid
        val fd = masterFd
        pid = -1
        masterFd = -1
        if (p > 0) {
            Pty.nativeKill(p, 15)
            Thread.sleep(80)
            Pty.nativeKill(p, 9)
            Pty.nativeWaitpid(p, false)
        }
        if (fd >= 0) Pty.nativeClose(fd)
    }
}
