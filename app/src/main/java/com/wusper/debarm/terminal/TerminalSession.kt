package com.wusper.debarm.terminal

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.wusper.debarm.core.CodexRuntime
import com.wusper.debarm.core.DebArmPaths
import com.wusper.debarm.core.Proot
import com.wusper.debarm.core.Pty
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
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
    private val writeLock = Any()
    private val pendingWrites = ArrayDeque<ByteArray>()

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
        synchronized(writeLock) {
            masterFd = ids[0]
            pid = ids[1]
            while (pendingWrites.isNotEmpty()) {
                writeUnlocked(pendingWrites.removeFirst())
            }
        }
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
        if (bytes.isEmpty()) return
        synchronized(writeLock) {
            if (masterFd < 0) {
                pendingWrites.addLast(bytes.copyOf())
                return
            }
            writeUnlocked(bytes)
        }
    }

    private fun writeUnlocked(bytes: ByteArray) {
        val fd = masterFd
        if (fd < 0) return
        var off = 0
        while (off < bytes.size) {
            val n = Pty.nativeWrite(fd, bytes, off, bytes.size - off)
            if (n <= 0) break
            off += n
        }
    }

    fun resize(rows: Int, cols: Int) {
        val fd = masterFd
        if (fd >= 0) Pty.nativeSetWinsize(fd, rows, cols)
    }

    fun close() {
        running.set(false)
        val p: Int
        val fd: Int
        synchronized(writeLock) {
            pendingWrites.clear()
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
}
