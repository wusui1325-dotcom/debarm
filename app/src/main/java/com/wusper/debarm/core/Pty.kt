package com.wusper.debarm.core

import java.io.IOException

/**
 * Thin JNI wrapper around posix_openpt + fork/exec.
 * The native library is [debarm-pty]; load it once from [ensureLoaded].
 */
object Pty {
    init {
        ensureLoaded()
    }

    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        System.loadLibrary("debarm-pty")
        loaded = true
    }

    @Throws(IOException::class)
    external fun nativeStart(
        argv: Array<String>,
        envp: Array<String>,
        cwd: String?,
        rows: Int,
        cols: Int,
    ): IntArray

    @Throws(IOException::class)
    external fun nativeRead(fd: Int, buf: ByteArray): Int

    @Throws(IOException::class)
    external fun nativeWrite(fd: Int, buf: ByteArray, off: Int, len: Int): Int

    external fun nativeSetWinsize(fd: Int, rows: Int, cols: Int)

    external fun nativeKill(pid: Int, sig: Int)

    external fun nativeWaitpid(pid: Int, hang: Boolean): Int

    external fun nativeClose(fd: Int)
}
