package com.wusper.debarm.core

import java.io.File

/**
 * Builds a host-side PRoot command that enters the Debian aarch64 rootfs
 * and execs a guest command. No QEMU: only native aarch64 ELFs run.
 */
object Proot {
    const val GUEST_PATH =
        "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    private val ANDROID_HOST_ENV_KEYS = listOf(
        "ANDROID_DATA",
        "ANDROID_ROOT",
        "ANDROID_ART_ROOT",
        "ANDROID_I18N_ROOT",
        "ANDROID_TZDATA_ROOT",
        "ANDROID_RUNTIME_ROOT",
        "ANDROID_ASSETS",
        "ANDROID_STORAGE",
        "EXTERNAL_STORAGE",
    )

    fun hostEnv(paths: DebArmPaths): Map<String, String> {
        paths.stageHostLibs()
        val map = mutableMapOf(
            "PROOT_LOADER" to paths.prootLoader.absolutePath,
            "PROOT_TMP_DIR" to paths.prootTmpDir.absolutePath,
            "LD_LIBRARY_PATH" to
                "${paths.hostLibDir.absolutePath}:${paths.nativeLibraryDir.absolutePath}",
            "TMPDIR" to paths.prootTmpDir.absolutePath,
            "LANG" to "C.UTF-8",
            "PATH" to "/system/bin:/system/xbin:/vendor/bin",
        )
        // Host-side Android libc/linker expect these; do not leak them into
        // the guest (guest env is built with env -i below).
        for (key in ANDROID_HOST_ENV_KEYS) {
            System.getenv(key)?.let { map[key] = it }
        }
        return map
    }

    fun envp(paths: DebArmPaths): Array<String> =
        hostEnv(paths).map { "${it.key}=${it.value}" }.toTypedArray()

    /**
     * @param guestCommand if empty, starts a login shell; otherwise
     *        runs `bash -lc` with the joined command, or a raw argv
     *        starting with an absolute guest path when [raw] is true.
     * @param changeId if null, uses --root-id (uid 0). Otherwise
     *        `-i uid:gid` (e.g. "1000:1000") so the guest looks like
     *        a regular Linux user.
     * @param kernelRelease if set, passed as `-k` so `uname -r` looks
     *        like a desktop Debian kernel instead of the Android one.
     */
    fun argv(
        paths: DebArmPaths,
        guestCommand: List<String>,
        cwd: String = "/root",
        raw: Boolean = false,
        extraBinds: List<Pair<String, String>> = emptyList(),
        loginShell: Boolean = !raw && guestCommand.isEmpty(),
        changeId: String? = null,
        kernelRelease: String? = null,
        bindApex: Boolean = true,
        extraGuestEnv: Map<String, String> = emptyMap(),
    ): Array<String> {
        val args = mutableListOf(
            paths.proot.absolutePath,
            "--kill-on-exit",
            "--link2symlink",
        )
        if (changeId.isNullOrBlank()) {
            args += "--root-id"
        } else {
            args += listOf("-i", changeId)
        }
        if (!kernelRelease.isNullOrBlank()) {
            args += listOf("-k", kernelRelease)
        }
        args += listOf(
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
        if (bindApex && apex.exists()) args += listOf("-b", "/apex")
        for ((src, dst) in extraBinds) {
            args += listOf("-b", "$src:$dst")
        }
        // Clean guest environment — do not leak Android JVM vars.
        val guestEnv = linkedMapOf(
            "HOME" to "/root",
            "USER" to "root",
            "LOGNAME" to "root",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "PATH" to GUEST_PATH,
            "TMPDIR" to "/tmp",
            "HOSTNAME" to "debarm",
            "DEBIAN_FRONTEND" to "noninteractive",
        )
        guestEnv.putAll(extraGuestEnv)
        args += listOf("/usr/bin/env", "-i")
        for ((k, v) in guestEnv) {
            args += "$k=$v"
        }
        when {
            loginShell -> args += listOf("/bin/bash", "--login")
            raw -> args += guestCommand
            else -> args += listOf("/bin/bash", "--login", "-c", guestCommand.joinToString(" "))
        }
        return args.toTypedArray()
    }

    fun assertRunnable(paths: DebArmPaths) {
        paths.stageHostLibs()
        if (!paths.proot.exists()) {
            throw IllegalStateException("找不到 PRoot：${paths.proot.absolutePath}")
        }
        if (!paths.prootLoader.exists()) {
            throw IllegalStateException("找不到 PRoot loader：${paths.prootLoader.absolutePath}")
        }
        if (!paths.proot.canExecute() || !paths.prootLoader.canExecute()) {
            throw IllegalStateException("PRoot 二進制無法執行，請確認 APK 已正確解出 native 庫。")
        }
        val talloc = File(paths.hostLibDir, "libtalloc.so.2")
        if (!talloc.exists()) {
            throw IllegalStateException("找不到 libtalloc.so.2（應從 nativeLibraryDir 的 libtalloc.so 複製）。")
        }
    }
}
