package com.wusper.debarm.core

import java.io.File

/**
 * Guest-side layout so official Codex CLI believes it is on a Debian
 * computer: a normal user, a desktop-like kernel release, a private
 * workspace as HOME, and bind-mounted device storage.
 */
object CodexRuntime {
    fun ensureWorkspace(paths: DebArmPaths) {
        paths.workspaceDir.mkdirs()
        File(paths.workspaceDir, ".codex").mkdirs()
        val readme = File(paths.workspaceDir, "README.txt")
        if (!readme.exists()) {
            readme.writeText(
                """這是 Codex 的工作區（位於 App 私有目錄）。
外接與共用儲存已掛到 /sdcard 與 /storage（需授予裝置儲存權限）。
""",
            )
        }
    }

    fun ensureGuestUser(paths: DebArmPaths) {
        val root = paths.rootfsDir
        File(root, "home/user").mkdirs()
        val passwd = File(root, "etc/passwd")
        if (passwd.exists()) {
            val text = passwd.readText()
            if (!text.lineSequence().any { it.startsWith("user:") }) {
                passwd.appendText("user:x:1000:1000:User:/workspace:/bin/bash\n")
            }
        } else {
            passwd.parentFile?.mkdirs()
            passwd.writeText(
                "root:x:0:0:root:/root:/bin/bash\n" +
                    "user:x:1000:1000:User:/workspace:/bin/bash\n",
            )
        }
        val group = File(root, "etc/group")
        if (group.exists()) {
            val text = group.readText()
            if (!text.lineSequence().any { it.startsWith("user:") }) {
                group.appendText("user:x:1000:\n")
            }
        }
        val hostname = File(root, "etc/hostname")
        hostname.writeText("debian\n")
        val hosts = File(root, "etc/hosts")
        if (!hosts.exists() || !hosts.readText().contains("debian")) {
            hosts.writeText(
                "127.0.0.1\tlocalhost debian\n::1\tlocalhost ip6-localhost ip6-loopback\n",
            )
        }
    }

    fun storageBinds(paths: DebArmPaths): List<Pair<String, String>> {
        val binds = mutableListOf(
            paths.workspaceDir.absolutePath to "/workspace",
            paths.workspaceDir.absolutePath to "/home/user",
        )
        for (p in listOf("/sdcard", "/storage", "/storage/emulated/0", "/mnt/sdcard")) {
            if (File(p).exists()) binds += p to p
        }
        return binds
    }

    fun guestEnv(): Map<String, String> = mapOf(
        "HOME" to "/workspace",
        "USER" to CodexCatalog.guestUser,
        "LOGNAME" to CodexCatalog.guestUser,
        "SHELL" to "/bin/bash",
        "LANG" to "C.UTF-8",
        "LC_ALL" to "C.UTF-8",
        "TERM" to "xterm-256color",
        "COLORTERM" to "truecolor",
        "PATH" to Proot.GUEST_PATH,
        "TMPDIR" to "/tmp",
        "HOSTNAME" to "debian",
        "CODEX_HOME" to "/workspace/.codex",
        "DEBIAN_FRONTEND" to "noninteractive",
    )

    fun argv(paths: DebArmPaths): Array<String> {
        ensureWorkspace(paths)
        ensureGuestUser(paths)
        // --no-daemon: the packaged CLI can start a background app-server
        // from a complete tree, but under PRoot that daemon (bwrap, unix
        // sockets, a second process) is unreliable. The flag also lets a
        // leftover bare 0.172.0 binary from 1.0.7 open the TUI at all.
        return Proot.argv(
            paths = paths,
            guestCommand = listOf(guestBinary(paths), "--no-daemon"),
            cwd = "/workspace",
            raw = true,
            extraBinds = storageBinds(paths),
            changeId = CodexCatalog.guestUidGid,
            kernelRelease = CodexCatalog.kernelRelease,
            bindApex = false,
            extraGuestEnv = guestEnv(),
        )
    }

    fun packageRoot(paths: DebArmPaths): File =
        File(paths.rootfsDir, CodexCatalog.packageRoot.removePrefix("/"))

    fun officialBinary(paths: DebArmPaths): File =
        File(paths.rootfsDir, CodexCatalog.guestBinaryPath.removePrefix("/"))

    fun legacyBinary(paths: DebArmPaths): File =
        File(paths.rootfsDir, CodexCatalog.legacyBinaryPath.removePrefix("/"))

    fun guestBinary(paths: DebArmPaths): String =
        if (isPackaged(paths)) CodexCatalog.guestBinaryPath else CodexCatalog.legacyBinaryPath

    fun isPackaged(paths: DebArmPaths): Boolean {
        val json = File(packageRoot(paths), "codex-package.json")
        val bin = officialBinary(paths)
        return json.isFile && bin.isFile && bin.length() > 1_000_000L
    }

    fun isInstalled(paths: DebArmPaths): Boolean {
        if (!paths.codexReadyMarker.exists()) return false
        if (!paths.codexReadyMarker.readText().contains(CodexCatalog.version)) return false
        if (isPackaged(paths)) return true
        val bare = legacyBinary(paths)
        return bare.isFile && bare.length() > 1_000_000L
    }
}
