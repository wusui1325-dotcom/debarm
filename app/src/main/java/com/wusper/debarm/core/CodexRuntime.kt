package com.wusper.debarm.core

import java.io.File

/**
 * Guest-side layout so the official ChatGPT / Codex desktop app believes
 * it is on a Debian computer: a normal user, a desktop-like kernel
 * release, a private workspace as HOME, bind-mounted device storage,
 * and an X11 display forwarded into the Android WebView over localhost VNC.
 */
object CodexRuntime {
    fun ensureWorkspace(paths: DebArmPaths) {
        paths.workspaceDir.mkdirs()
        File(paths.workspaceDir, ".codex").mkdirs()
        File(paths.workspaceDir, ".config").mkdirs()
        File(paths.workspaceDir, ".cache").mkdirs()
        File(paths.workspaceDir, ".local/share").mkdirs()
        val readme = File(paths.workspaceDir, "README.txt")
        if (!readme.exists()) {
            readme.writeText(
                """這是官方 ChatGPT／Codex 桌面版的工作區（位於 App 私有目錄）。
外接與共用儲存已掛到 /sdcard 與 /storage（需授予裝置儲存權限）。
桌面版會在這個工作區裡寫入設定與專案檔。
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

    /**
     * Official entry from chatgpt_26.1120.41243_arm64.deb (dpkg-deb -c):
     *   /usr/bin/chatgpt -> ../lib/chatgpt/codex-launcher   (63-byte wrapper)
     *   /usr/lib/chatgpt/codex-launcher                     exec's ./ChatGPT
     *   /usr/lib/chatgpt/ChatGPT                            323,947,816-byte ELF
     *   desktop Exec=chatgpt %U
     * A previous check required length > 1 MB and looked for lowercase
     * usr/lib/chatgpt/chatgpt, so a completed dpkg still looked "missing".
     */
    fun officialBinary(paths: DebArmPaths): File? {
        val root = paths.rootfsDir
        val known = listOf(
            "usr/bin/chatgpt",
            "usr/lib/chatgpt/codex-launcher",
            "usr/lib/chatgpt/ChatGPT",
            "usr/lib/chatgpt/chatgpt",
            "opt/ChatGPT/chatgpt",
            "opt/ChatGPT/ChatGPT",
            "opt/chatgpt/chatgpt",
            "usr/share/chatgpt/chatgpt",
        )
        for (rel in known) {
            resolveGuestFile(root, rel)?.let { return it }
        }
        desktopExec(root)?.let { return it }
        dpkgListedBinary(root)?.let { return it }
        return null
    }

    private fun resolveGuestFile(root: File, rel: String): File? {
        val f = File(root, rel.removePrefix("/").removePrefix("./"))
        if (!f.exists()) return null
        val real = try { f.canonicalFile } catch (_: Exception) { f }
        return if (real.isFile && real.length() > 0L) f else null
    }

    private fun desktopExec(root: File): File? {
        val desktop = File(root, "usr/share/applications/chatgpt.desktop")
        if (!desktop.isFile) return null
        val text = runCatching { desktop.readText() }.getOrDefault("")
        val exec = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Exec=") }
            ?.removePrefix("Exec=")
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?: return null
        val rel = when {
            exec.startsWith("/") -> exec.removePrefix("/")
            else -> "usr/bin/$exec"
        }
        return resolveGuestFile(root, rel)
    }

    private fun dpkgListedBinary(root: File): File? {
        val list = File(root, "var/lib/dpkg/info/${CodexCatalog.packageName}.list")
        if (!list.isFile) return null
        val lines = runCatching { list.readLines() }.getOrDefault(emptyList())
        val preferred = lines.map { it.trim() }.filter { path ->
            path.endsWith("/chatgpt") || path.endsWith("/ChatGPT") ||
                path.endsWith("/codex-launcher")
        }
        for (path in preferred) {
            resolveGuestFile(root, path)?.let { return it }
        }
        return null
    }

    fun dpkgHasOfficialPackage(paths: DebArmPaths): Boolean {
        val status = File(paths.rootfsDir, "var/lib/dpkg/status")
        if (!status.isFile) return false
        val text = runCatching { status.readText() }.getOrDefault("")
        val block = text.split("\n\n").firstOrNull { stanza ->
            stanza.lineSequence().any { it == "Package: ${CodexCatalog.packageName}" }
        } ?: return false
        val versionOk = block.lineSequence().any {
            it == "Version: ${CodexCatalog.version}" || it.startsWith("Version: ${CodexCatalog.version}")
        }
        val stateOk = block.lineSequence().any { line ->
            line.startsWith("Status:") && (
                line.contains("installed") || line.contains("unpacked") ||
                    line.contains("half-configured")
            )
        }
        return versionOk && stateOk
    }

    fun rememberInstalled(paths: DebArmPaths) {
        paths.codexReadyMarker.writeText(
            "${CodexCatalog.packageName} ${CodexCatalog.version} ${CodexCatalog.sha256}\n",
        )
        ensureStartScript(paths)
        ensureWorkspace(paths)
        ensureGuestUser(paths)
    }

    fun isInstalled(paths: DebArmPaths): Boolean {
        val binary = officialBinary(paths)
        if (binary != null) {
            val marker = runCatching { paths.codexReadyMarker.readText() }.getOrDefault("")
            if (!marker.contains(CodexCatalog.version) || !marker.contains(CodexCatalog.packageName)) {
                runCatching { rememberInstalled(paths) }
            }
            return true
        }
        return false
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
        "XDG_CONFIG_HOME" to "/workspace/.config",
        "XDG_DATA_HOME" to "/workspace/.local/share",
        "XDG_CACHE_HOME" to "/workspace/.cache",
        "XDG_RUNTIME_DIR" to "/tmp/runtime-user",
        "DEBIAN_FRONTEND" to "noninteractive",
    )

    fun displayEnv(): Map<String, String> = guestEnv() + mapOf(
        "DISPLAY" to ":${CodexCatalog.displayNumber}",
        "LIBGL_ALWAYS_SOFTWARE" to "1",
        "GALLIUM_DRIVER" to "llvmpipe",
        "MESA_GL_VERSION_OVERRIDE" to "3.3",
        "ELECTRON_OZONE_PLATFORM_HINT" to "x11",
        "GDK_BACKEND" to "x11",
    )

    fun ensureStartScript(paths: DebArmPaths) {
        val dest = File(paths.rootfsDir, "usr/local/bin/debarm-start-chatgpt")
        dest.parentFile?.mkdirs()
        dest.writeText(START_SCRIPT)
        dest.setExecutable(true, false)
    }

    /** Workspace login shell — fallback / troubleshooting, not the musl CLI. */
    fun shellArgv(paths: DebArmPaths): Array<String> {
        ensureWorkspace(paths)
        ensureGuestUser(paths)
        return Proot.argv(
            paths = paths,
            guestCommand = emptyList(),
            cwd = "/workspace",
            loginShell = true,
            extraBinds = storageBinds(paths),
            changeId = CodexCatalog.guestUidGid,
            kernelRelease = CodexCatalog.kernelRelease,
            bindApex = false,
            extraGuestEnv = guestEnv(),
        )
    }

    /** Official desktop app on Xvfb, published to localhost noVNC. */
    fun displayArgv(paths: DebArmPaths): Array<String> {
        ensureWorkspace(paths)
        ensureGuestUser(paths)
        ensureStartScript(paths)
        return Proot.argv(
            paths = paths,
            guestCommand = listOf("/bin/bash", CodexCatalog.startScriptPath),
            cwd = "/workspace",
            raw = true,
            extraBinds = storageBinds(paths),
            changeId = CodexCatalog.guestUidGid,
            kernelRelease = CodexCatalog.kernelRelease,
            bindApex = false,
            extraGuestEnv = displayEnv(),
        )
    }

    /** Fallback terminal uses a login shell, never the musl CLI. */
    fun argv(paths: DebArmPaths): Array<String> = shellArgv(paths)

    private val START_SCRIPT = """#!/bin/bash
set +e
export DISPLAY=:${CodexCatalog.displayNumber}
export HOME=/workspace
export USER=${CodexCatalog.guestUser}
export LOGNAME=${CodexCatalog.guestUser}
export XDG_RUNTIME_DIR=/tmp/runtime-user
export XDG_CONFIG_HOME=/workspace/.config
export XDG_DATA_HOME=/workspace/.local/share
export XDG_CACHE_HOME=/workspace/.cache
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export ELECTRON_OZONE_PLATFORM_HINT=x11
export GDK_BACKEND=x11
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
mkdir -p /tmp/runtime-user /tmp/.X11-unix /workspace /tmp/xdg
chmod 700 /tmp/runtime-user 2>/dev/null
chmod 1777 /tmp/.X11-unix 2>/dev/null

wait_tcp() {
  local port="${'$'}1"
  local name="${'$'}2"
  local tries="${'$'}{3:-80}"
  local i
  for i in ${'$'}(seq 1 "${'$'}tries"); do
    if bash -c "echo >/dev/tcp/127.0.0.1/${'$'}port" 2>/dev/null; then
      echo "debarm: ${'$'}name is listening"
      return 0
    fi
    if command -v python3 >/dev/null 2>&1 && python3 -c "import socket;s=socket.create_connection(('127.0.0.1', int('${'$'}port')),0.2);s.close()" 2>/dev/null; then
      echo "debarm: ${'$'}name is listening"
      return 0
    fi
    sleep 0.25
  done
  return 1
}

# Fresh display each launch; PRoot --kill-on-exit also reaps children.
pkill -f "Xvfb :${CodexCatalog.displayNumber}" >/dev/null 2>&1
pkill -f "x11vnc.*rfbport ${CodexCatalog.vncPort}" >/dev/null 2>&1
pkill -f "websockify.*${CodexCatalog.displayPort}" >/dev/null 2>&1
sleep 0.4

if command -v dbus-launch >/dev/null 2>&1 && [ -z "${'$'}{DBUS_SESSION_BUS_ADDRESS:-}" ]; then
  eval "${'$'}(dbus-launch --sh-syntax)"
  export DBUS_SESSION_BUS_ADDRESS DBUS_SESSION_BUS_PID
fi

echo "debarm: starting Xvfb on :${CodexCatalog.displayNumber}"
if ! command -v Xvfb >/dev/null 2>&1; then
  echo "debarm: Xvfb is not installed" >&2
  exit 1
fi
Xvfb :${CodexCatalog.displayNumber} -screen 0 ${CodexCatalog.screenWidth}x${CodexCatalog.screenHeight}x24 -ac +extension GLX +extension RANDR +extension RENDER -nolisten tcp -noreset >/tmp/xvfb.log 2>&1 &
for i in ${'$'}(seq 1 80); do
  if command -v xdpyinfo >/dev/null 2>&1 && xdpyinfo -display :${CodexCatalog.displayNumber} >/dev/null 2>&1; then
    echo "debarm: Xvfb is ready"
    break
  fi
  if [ -S /tmp/.X11-unix/X${CodexCatalog.displayNumber} ]; then
    echo "debarm: Xvfb is ready"
    break
  fi
  sleep 0.25
done
if [ ! -S /tmp/.X11-unix/X${CodexCatalog.displayNumber} ]; then
  echo "debarm: Xvfb failed to start" >&2
  cat /tmp/xvfb.log >&2
  exit 1
fi

if command -v openbox >/dev/null 2>&1; then
  openbox >/tmp/openbox.log 2>&1 &
  sleep 0.3
fi

echo "debarm: starting x11vnc"
if ! command -v x11vnc >/dev/null 2>&1; then
  echo "debarm: x11vnc is not installed" >&2
  exit 1
fi
start_x11vnc() {
  # Bind IPv4 127.0.0.1 only. -localhost can resolve to ::1, which
  # leaves 127.0.0.1:5900 closed while websockify still serves HTTP.
  # -noshm is required under PRoot (MIT-SHM usually fails and x11vnc exits).
  x11vnc -display :${CodexCatalog.displayNumber} \
    -forever -shared -nopw \
    -listen 127.0.0.1 -noipv6 -rfbport ${CodexCatalog.vncPort} \
    -xkb -repeat -noxdamage -ncache 0 -noshm -noxrecord \
    -o /tmp/x11vnc.log ${'$'}@ >/tmp/x11vnc.stdout 2>&1 &
}
start_x11vnc
if ! wait_tcp ${CodexCatalog.vncPort} x11vnc 80; then
  echo "debarm: x11vnc first start failed, retrying with -auth guess" >&2
  cat /tmp/x11vnc.log >&2 2>/dev/null
  cat /tmp/x11vnc.stdout >&2 2>/dev/null
  pkill -f "x11vnc.*rfbport ${CodexCatalog.vncPort}" >/dev/null 2>&1
  sleep 0.4
  start_x11vnc -auth guess
  if ! wait_tcp ${CodexCatalog.vncPort} x11vnc 80; then
    echo "debarm: x11vnc failed to listen on 127.0.0.1:${CodexCatalog.vncPort}" >&2
    echo "debarm: ---- x11vnc.log ----" >&2
    cat /tmp/x11vnc.log >&2 2>/dev/null
    echo "debarm: ---- x11vnc.stdout ----" >&2
    cat /tmp/x11vnc.stdout >&2 2>/dev/null
    exit 1
  fi
fi

NOVNC_WEB=""
for d in /usr/share/novnc /usr/share/novnc/noVNC /usr/share/python3-novnc /usr/share/novnc/utils/../; do
  if [ -f "${'$'}d/vnc.html" ] || [ -f "${'$'}d/vnc_lite.html" ]; then
    NOVNC_WEB="${'$'}d"
    break
  fi
done
if [ -z "${'$'}NOVNC_WEB" ]; then
  echo "debarm: noVNC web files not found" >&2
  exit 1
fi

WS=""
if command -v websockify >/dev/null 2>&1; then
  WS="websockify"
elif python3 -c 'import websockify' >/dev/null 2>&1; then
  WS="python3 -m websockify"
fi
if [ -z "${'$'}WS" ]; then
  echo "debarm: websockify is not installed" >&2
  exit 1
fi

echo "debarm: starting websockify on ${CodexCatalog.displayPort} (web=${'$'}NOVNC_WEB)"
${'$'}WS --web="${'$'}NOVNC_WEB" 127.0.0.1:${CodexCatalog.displayPort} 127.0.0.1:${CodexCatalog.vncPort} >/tmp/websockify.log 2>&1 &
if ! wait_tcp ${CodexCatalog.displayPort} noVNC 80; then
  echo "debarm: websockify failed to listen on 127.0.0.1:${CodexCatalog.displayPort}" >&2
  echo "debarm: ---- websockify.log ----" >&2
  cat /tmp/websockify.log >&2 2>/dev/null
  echo "debarm: ---- x11vnc.log ----" >&2
  cat /tmp/x11vnc.log >&2 2>/dev/null
  exit 1
fi
# Confirm the VNC backend is still up before the WebView is allowed to connect.
if ! wait_tcp ${CodexCatalog.vncPort} x11vnc 8; then
  echo "debarm: x11vnc stopped before noVNC was ready" >&2
  cat /tmp/x11vnc.log >&2 2>/dev/null
  exit 1
fi
echo "debarm: display stack ready (Xvfb=:${CodexCatalog.displayNumber} vnc=127.0.0.1:${CodexCatalog.vncPort} novnc=127.0.0.1:${CodexCatalog.displayPort})"

CHATGPT=""
for c in /usr/bin/chatgpt /usr/lib/chatgpt/codex-launcher /usr/lib/chatgpt/ChatGPT /usr/lib/chatgpt/chatgpt /opt/ChatGPT/chatgpt /opt/ChatGPT/ChatGPT /opt/chatgpt/chatgpt /usr/share/chatgpt/chatgpt; do
  if [ -e "${'$'}c" ]; then CHATGPT="${'$'}c"; break; fi
done
if [ -z "${'$'}CHATGPT" ]; then
  CHATGPT="${'$'}(command -v chatgpt 2>/dev/null || true)"
fi
if [ -z "${'$'}CHATGPT" ]; then
  echo "debarm: official chatgpt binary not found" >&2
  exit 127
fi

echo "debarm: launching official app ${'$'}CHATGPT"
"${'$'}CHATGPT" \
  --no-sandbox \
  --disable-gpu \
  --ozone-platform=x11 \
  --disable-dev-shm-usage \
  --disable-setuid-sandbox \
  --disable-seccomp-filter-sandbox \
  --in-process-gpu \
  "$@"
STATUS=${'$'}?
echo "debarm: chatgpt exited ${'$'}STATUS"
exit ${'$'}STATUS
"""
}
