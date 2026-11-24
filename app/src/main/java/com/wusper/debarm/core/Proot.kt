package com.wusper.debarm.core

import java.io.File

/**
 * Builds a host-side PRoot command that enters the Debian aarch64 rootfs
 * and execs a guest command. No QEMU: only native aarch64 ELFs run.
 */
object Proot {
    const val GUEST_PATH =
        "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    fun hostEnv(paths: DebArmPaths): Map<String, String> = mapOf(
        "PROOT_LOADER" to paths.prootLoader.absolutePath,
        "PROOT_TMP_DIR" to paths.prootTmpDir.absolutePath,
        "LD_LIBRARY_PATH" to paths.nativeLibraryDir.absolutePath,
        "TMPDIR" to paths.prootTmpDir.absolutePath,
        "LANG" to "C.UTF-8",
    )

    fun envp(paths: DebArmPaths): Array<String> =
        hostEnv(paths).map { "${it.key}=${it.value}" }.toTypedArray()

    /**
     * @param guestCommand if empty, starts a login shell; otherwise
     *        runs `bash -lc` with the joined command, or a raw argv
     *        starting with an absolute guest path when [raw] is true.
     */
    fun argv(
        paths: DebArmPaths,
        guestCommand: List<String>,
        cwd: String = "/root",
        raw: Boolean = false,
        extraBinds: List<Pair<String, String>> = emptyList(),
        loginShell: Boolean = !raw && guestCommand.isEmpty(),
    ): Array<String> {
        val args = mutableListOf(
            paths.proot.absolutePath,
            "--kill-on-exit",
            "--link2symlink",
            "--root-id",
            "-r", paths.rootfsDir.absolutePath,
            "-w", cwd,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${paths.exchangeDir.absolutePath}:/exchange",
            "-b", "/proc/self/fd:/dev/fd",
            "-b", "/proc/self/fd/0:/dev/stdin",
            "-b", "/proc/self/fd/1:/dev/stdout",
            "-b", "/proc/self/fd/2:/dev/stderr",
        )
        val shm = File(paths.rootfsDir, "tmp")
        if (shm.exists() || shm.mkdirs()) {
            args += listOf("-b", "${shm.absolutePath}:/dev/shm")
        }
        val apex = File("/apex")
        if (apex.exists()) args += listOf("-b", "/apex")
        for ((src, dst) in extraBinds) {
            args += listOf("-b", "$src:$dst")
        }
        // Clean guest environment — do not leak Android JVM vars.
        args += listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "USER=root",
            "LOGNAME=root",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "PATH=$GUEST_PATH",
            "TMPDIR=/tmp",
            "HOSTNAME=debarm",
            "DEBIAN_FRONTEND=noninteractive",
        )
        when {
            loginShell -> args += listOf("/bin/bash", "--login")
            raw -> args += guestCommand
            else -> args += listOf("/bin/bash", "--login", "-c", guestCommand.joinToString(" "))
        }
        return args.toTypedArray()
    }

    fun assertRunnable(paths: DebArmPaths) {
        if (!paths.proot.exists()) {
            throw IllegalStateException("找不到 PRoot：${paths.proot.absolutePath}")
        }
        if (!paths.prootLoader.exists()) {
            throw IllegalStateException("找不到 PRoot loader：${paths.prootLoader.absolutePath}")
        }
        if (!paths.proot.canExecute() || !paths.prootLoader.canExecute()) {
            throw IllegalStateException("PRoot 二進制無法執行，請確認 APK 已正確解出 native 庫。")
        }
    }
}
