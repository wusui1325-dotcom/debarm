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

    fun officialBinary(paths: DebArmPaths): File? {
        val candidates = listOf(
            File(paths.rootfsDir, "usr/bin/chatgpt"),
            File(paths.rootfsDir, "opt/ChatGPT/chatgpt"),
            File(paths.rootfsDir, "opt/chatgpt/chatgpt"),
            File(paths.rootfsDir, "usr/share/chatgpt/chatgpt"),
            File(paths.rootfsDir, "usr/lib/chatgpt/chatgpt"),
        )
        return candidates.firstOrNull { it.isFile && it.canExecute() && it.length() > 1_000_000L }
            ?: candidates.firstOrNull { it.isFile && it.length() > 1_000_000L }
    }

    fun isInstalled(paths: DebArmPaths): Boolean {
        if (!paths.codexReadyMarker.exists()) return false
        val marker = runCatching { paths.codexReadyMarker.readText() }.getOrDefault("")
        if (!marker.contains(CodexCatalog.version) || !marker.contains(CodexCatalog.packageName)) {
            return false
        }
        if (officialBinary(paths) != null) return true
        val status = File(paths.rootfsDir, "var/lib/dpkg/status")
        if (!status.isFile) return false
        val text = runCatching { status.readText() }.getOrDefault("")
        return text.contains("Package: ${CodexCatalog.packageName}\n") &&
            text.contains("Version: ${CodexCatalog.version}")
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
x11vnc -display :${CodexCatalog.displayNumber} -forever -shared -localhost -rfbport ${CodexCatalog.vncPort} -nopw -xkb -repeat -noxdamage -ncache 0 -wait 10 >/tmp/x11vnc.log 2>&1 &
for i in ${'$'}(seq 1 80); do
  if bash -c 'echo >/dev/tcp/127.0.0.1/${CodexCatalog.vncPort}' 2>/dev/null; then
    echo "debarm: x11vnc is listening"
    break
  fi
  sleep 0.25
done

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
for i in ${'$'}(seq 1 80); do
  if bash -c 'echo >/dev/tcp/127.0.0.1/${CodexCatalog.displayPort}' 2>/dev/null; then
    echo "debarm: noVNC is listening on ${CodexCatalog.displayPort}"
    break
  fi
  sleep 0.25
done
if ! bash -c 'echo >/dev/tcp/127.0.0.1/${CodexCatalog.displayPort}' 2>/dev/null; then
  echo "debarm: websockify failed to listen" >&2
  cat /tmp/websockify.log >&2
  exit 1
fi

CHATGPT=""
for c in /usr/bin/chatgpt /opt/ChatGPT/chatgpt /opt/chatgpt/chatgpt /usr/share/chatgpt/chatgpt /usr/lib/chatgpt/chatgpt; do
  if [ -x "${'$'}c" ]; then CHATGPT="${'$'}c"; break; fi
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
