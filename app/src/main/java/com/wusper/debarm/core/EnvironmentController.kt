package com.wusper.debarm.core

import android.app.Application
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

data class EnvUiState(
    val phase: Phase = Phase.Checking,
    val message: String = "正在檢查環境…",
    val downloadRead: Long = 0,
    val downloadTotal: Long = 0,
    val error: String? = null,
    val debianLabel: String? = null,
    val diskUsedBytes: Long = 0,
    val diskFreeBytes: Long = 0,
    val packages: List<DebPackage> = emptyList(),
    val log: String = "",
    val lastInstalled: String? = null,
    val packageFilter: String = "",
    val codexReady: Boolean = false,
    val codexVersion: String? = null,
) {
    enum class Phase {
        Checking, NeedSetup, Downloading, Extracting, Configuring,
        Ready, InstallingDeb, Deleting, Error,
    }

    val isBusy: Boolean
        get() = phase == Phase.Downloading ||
            phase == Phase.Extracting ||
            phase == Phase.Configuring ||
            phase == Phase.InstallingDeb ||
            phase == Phase.Deleting ||
            phase == Phase.Checking

    val progressFraction: Float
        get() = if (downloadTotal > 0) {
            (downloadRead.toFloat() / downloadTotal.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

class EnvironmentController(private val app: Application) {
    private val paths = DebArmPaths(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cancelled = AtomicBoolean(false)
    private val _state = MutableStateFlow(EnvUiState())
    val state: StateFlow<EnvUiState> = _state.asStateFlow()

    val pathsPublic: DebArmPaths get() = paths

    fun refresh() {
        scope.launch { refreshNow() }
    }

    fun setPackageFilter(query: String) {
        _state.update { it.copy(packageFilter = query) }
    }

    fun installEnvironment() {
        if (_state.value.isBusy && _state.value.phase != EnvUiState.Phase.NeedSetup &&
            _state.value.phase != EnvUiState.Phase.Error &&
            _state.value.phase != EnvUiState.Phase.Ready
        ) return
        cancelled.set(false)
        scope.launch { runInstallEnvironment() }
    }

    fun prepareCodex() {
        if (_state.value.isBusy && _state.value.phase != EnvUiState.Phase.NeedSetup &&
            _state.value.phase != EnvUiState.Phase.Error &&
            _state.value.phase != EnvUiState.Phase.Ready
        ) return
        cancelled.set(false)
        scope.launch { runPrepareCodex() }
    }

    fun installDeb(uri: Uri) {
        cancelled.set(false)
        scope.launch { runInstallDeb(uri) }
    }

    fun deleteEnvironment() {
        cancelled.set(true)
        scope.launch { runDelete() }
    }

    fun cancelWork() {
        cancelled.set(true)
        appendLog("正在取消…")
    }

    private fun isCancelled() = cancelled.get()

    private suspend fun refreshNow() {
        paths.ensureWorkDirs()
        val free = freeBytes()
        val used = _state.value.diskUsedBytes
        if (!Arch.isArm64Device()) {
            _state.update {
                it.copy(
                    phase = EnvUiState.Phase.Error,
                    error = "這支 App 只在 arm64-v8a 裝置上原生執行 aarch64 二進制，不模擬 x86。\n${Arch.deviceSummary()}",
                    message = "裝置架構不相容",
                    diskUsedBytes = used,
                    diskFreeBytes = free,
                )
            }
            return
        }
        val codexReady = CodexRuntime.isInstalled(paths)
        val codexVersion = if (codexReady) CodexCatalog.version else null
        if (isReady()) {
            val label = readDebianLabel()
            val pkgs = runCatching { PackageQuery.list(paths) }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    phase = EnvUiState.Phase.Ready,
                    message = "Debian 環境已就緒，可安裝 .deb 或開啟終端。",
                    error = null,
                    debianLabel = label,
                    diskUsedBytes = used,
                    diskFreeBytes = free,
                    packages = pkgs,
                    codexReady = codexReady,
                    codexVersion = codexVersion,
                )
            }
        } else {
            _state.update {
                it.copy(
                    phase = EnvUiState.Phase.NeedSetup,
                    message = "尚未安裝 Debian aarch64 環境。第一次約需下載 34 MB，解壓後約佔 150–250 MB。",
                    error = null,
                    debianLabel = null,
                    diskUsedBytes = used,
                    diskFreeBytes = free,
                    packages = emptyList(),
                    codexReady = false,
                    codexVersion = null,
                )
            }
        }
        updateDiskAsync()
    }

    private fun isReady(): Boolean {
        if (!paths.readyMarker.exists()) return false
        val dpkg = File(paths.rootfsDir, "usr/bin/dpkg")
        val bash = File(paths.rootfsDir, "usr/bin/bash")
        return dpkg.exists() && bash.exists()
    }

    private fun readDebianLabel(): String {
        val os = File(paths.rootfsDir, "etc/os-release")
        if (!os.exists()) return "Debian aarch64"
        val map = os.readLines()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1).trim('"')
            }.toMap()
        val pretty = map["PRETTY_NAME"] ?: "Debian"
        return "$pretty · aarch64"
    }

    private suspend fun runInstallEnvironment() {
        try {
            paths.ensureWorkDirs()
            if (isReady()) {
                refreshNow()
                return
            }
            if (freeBytes() < 400L * 1024 * 1024) {
                throw IOException("可用空間不足 400 MB，請先清出空間再安裝環境。目前剩餘 ${formatBytes(freeBytes())}。")
            }
            Proot.assertRunnable(paths)

            var lastError: Exception? = null
            for (image in RootfsCatalog.all) {
                try {
                    downloadAndExtract(image)
                    lastError = null
                    break
                } catch (e: Exception) {
                    lastError = e
                    appendLog("來源失敗（${image.name}）：${e.message}")
                    if (isCancelled()) break
                }
            }
            if (lastError != null) throw lastError
            if (isCancelled()) throw IOException("已取消")
            configureRootfs()
            refreshNow()
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    phase = EnvUiState.Phase.Error,
                    error = e.message ?: e.toString(),
                    message = "安裝環境失敗",
                )
            }
            updateDiskAsync()
        }
    }

    private fun downloadAndExtract(image: RootfsCatalog.Image) {
        _state.update {
            it.copy(
                phase = EnvUiState.Phase.Downloading,
                message = "正在下載 ${image.label}",
                error = null,
                downloadRead = 0,
                downloadTotal = image.bytes,
            )
        }
        appendLog("下載 ${image.url}")
        if (paths.downloadFile.exists() &&
            runCatching { Sha256.of(paths.downloadFile) }.getOrNull()?.let { Sha256.equal(it, image.sha256) } == true
        ) {
            appendLog("已有校驗相符的快取，略過下載。")
        } else {
            Downloader.download(
                url = image.url,
                dest = paths.downloadFile,
                expectedSha256 = image.sha256,
                expectedBytes = image.bytes,
                cancelled = ::isCancelled,
            ) { read, total ->
                _state.update {
                    it.copy(
                        downloadRead = read,
                        downloadTotal = if (total > 0) total else it.downloadTotal,
                        message = "正在下載 ${image.label}（${formatBytes(read)}" +
                            if (total > 0) " / ${formatBytes(total)}）" else "）",
                    )
                }
            }
        }
        if (isCancelled()) throw IOException("已取消")

        _state.update {
            it.copy(
                phase = EnvUiState.Phase.Extracting,
                message = "正在解壓 rootfs…",
            )
        }
        if (paths.rootfsPartialDir.exists()) paths.rootfsPartialDir.deleteRecursively()
        paths.rootfsPartialDir.mkdirs()
        SafeTarXz.extract(paths.downloadFile, paths.rootfsPartialDir, image.stripComponents) { name, count ->
            _state.update { it.copy(message = "正在解壓 rootfs…（$count 項）$name") }
        }
        if (isCancelled()) throw IOException("已取消")
        if (paths.rootfsDir.exists()) paths.rootfsDir.deleteRecursively()
        if (!paths.rootfsPartialDir.renameTo(paths.rootfsDir)) {
            paths.rootfsPartialDir.copyRecursively(paths.rootfsDir, overwrite = true)
            paths.rootfsPartialDir.deleteRecursively()
        }
        updateDiskAsync()
    }

    private fun configureRootfs() {
        _state.update {
            it.copy(phase = EnvUiState.Phase.Configuring, message = "正在設定 Debian 環境…")
        }
        File(paths.rootfsDir, "tmp").mkdirs()
        File(paths.rootfsDir, "root").mkdirs()
        File(paths.rootfsDir, "var/tmp").mkdirs()
        File(paths.rootfsDir, "etc/resolv.conf").writeText(
            "nameserver 1.1.1.1\nnameserver 8.8.8.8\nnameserver 2606:4700:4700::1111\n",
        )
        File(paths.rootfsDir, "etc/hosts").writeText(
            "127.0.0.1\tlocalhost debarm\n::1\tlocalhost ip6-localhost ip6-loopback\n",
        )
        File(paths.rootfsDir, "etc/hostname").writeText("debarm\n")
        val policy = File(paths.rootfsDir, "usr/sbin/policy-rc.d")
        policy.parentFile?.mkdirs()
        policy.writeText("#!/bin/sh\nexit 101\n")
        policy.setExecutable(true, false)
        File(paths.rootfsDir, "root/歡迎.txt").writeText(
            """歡迎使用 DebArm。
這是跑在 Android 上、不需 root 的 Debian aarch64 環境。
二進制以 aarch64 ELF 原生執行（PRoot），不會用 QEMU 去擬 x86。

常用指令：
  uname -m          # 應顯示 aarch64
  apt-get update
  apt-get install hello
  hello

把 .deb 放到 /exchange 或用 App 的「安裝 .deb」。
""",
        )
        // Sanity check: first that PRoot itself starts, then a raw guest exec
        // (avoid bash --login so a broken profile cannot mask a loader crash).
        Proot.assertRunnable(paths)
        val hostHelp = HostProcess.run(
            argv = arrayOf(paths.proot.absolutePath, "--help"),
            env = Proot.hostEnv(paths),
            timeoutMs = 10_000,
            cancelled = ::isCancelled,
        )
        if (hostHelp.output.isNotBlank()) {
            appendLog(hostHelp.output.trim().lineSequence().take(6).joinToString("\n") + "\n")
        }
        if (hostHelp.exitCode == 139) {
            throw IOException(
                "PRoot 本身無法啟動（exit 139，SIGSEGV）。\n${hostHelp.output}",
            )
        }
        val probe = HostProcess.run(
            argv = Proot.argv(paths, listOf("/usr/bin/uname", "-m"), raw = true, cwd = "/"),
            env = Proot.hostEnv(paths),
            timeoutMs = 30_000,
            cancelled = ::isCancelled,
        )
        appendLog(probe.output.trim())
        if (probe.exitCode != 0) {
            val sig = if (probe.exitCode == 139) "，SIGSEGV" else ""
            throw IOException(
                "環境已解壓，但無法執行 guest 指令（exit ${probe.exitCode}$sig）。\n${probe.output}",
            )
        }
        val uname = probe.output.lineSequence().firstOrNull()?.trim().orEmpty()
        if (uname.isNotEmpty() && uname != "aarch64") {
            throw IOException("預期 uname -m 為 aarch64，實際得到「$uname」。拒絕在模擬或錯誤架構上繼續。")
        }
        paths.readyMarker.writeText("debarm-ready ${RootfsCatalog.primary.name}\n")
        appendLog("環境已就緒。")
    }

    private suspend fun runPrepareCodex() {
        try {
            paths.ensureWorkDirs()
            CodexRuntime.ensureWorkspace(paths)
            if (!Arch.isArm64Device()) {
                throw IOException("這支 App 只在 arm64-v8a 裝置上原生執行 aarch64 二進制，不模擬 x86。\n${Arch.deviceSummary()}")
            }
            if (freeBytes() < 700L * 1024 * 1024) {
                throw IOException("可用空間不足 700 MB（Debian 環境與官方 Codex CLI 約需此空間）。目前剩餘 ${formatBytes(freeBytes())}。")
            }
            Proot.assertRunnable(paths)
            if (!isReady()) {
                var lastError: Exception? = null
                for (image in RootfsCatalog.all) {
                    try {
                        downloadAndExtract(image)
                        lastError = null
                        break
                    } catch (e: Exception) {
                        lastError = e
                        appendLog("來源失敗（${image.name}）：${e.message}")
                        if (isCancelled()) break
                    }
                }
                if (lastError != null) throw lastError
                if (isCancelled()) throw IOException("已取消")
                configureRootfs()
            }
            CodexRuntime.ensureGuestUser(paths)
            if (!CodexRuntime.isInstalled(paths)) {
                installOfficialCodex()
            }
            refreshNow()
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    phase = EnvUiState.Phase.Error,
                    error = e.message ?: e.toString(),
                    message = "準備 Codex 失敗",
                )
            }
            updateDiskAsync()
        }
    }

    private fun installOfficialCodex() {
        _state.update {
            it.copy(
                phase = EnvUiState.Phase.Downloading,
                message = "正在下載官方 Codex CLI ${CodexCatalog.version}…",
                error = null,
                downloadRead = 0,
                downloadTotal = CodexCatalog.bytes,
            )
        }
        appendLog("下載官方 Codex ${CodexCatalog.version}（${CodexCatalog.assetName}）")
        val haveCache = paths.codexDownloadFile.exists() &&
            runCatching { Sha256.of(paths.codexDownloadFile) }.getOrNull()
                ?.let { Sha256.equal(it, CodexCatalog.sha256) } == true
        if (haveCache) {
            appendLog("已有校驗相符的官方 Codex 快取，略過下載。")
        } else {
            val used = Downloader.downloadWithFallback(
                urls = CodexCatalog.urls,
                dest = paths.codexDownloadFile,
                expectedSha256 = CodexCatalog.sha256,
                expectedBytes = CodexCatalog.bytes,
                cancelled = ::isCancelled,
            ) { read, total ->
                _state.update {
                    it.copy(
                        downloadRead = read,
                        downloadTotal = if (total > 0) total else it.downloadTotal,
                        message = "正在下載官方 Codex CLI（${formatBytes(read)}" +
                            if (total > 0) " / ${formatBytes(total)}）" else "）",
                    )
                }
            }
            appendLog("已由 $used 完成下載並通過 SHA-256 校驗。")
        }
        if (isCancelled()) throw IOException("已取消")
        _state.update {
            it.copy(phase = EnvUiState.Phase.Extracting, message = "正在安裝官方 Codex CLI…")
        }
        val dest = CodexRuntime.officialBinary(paths)
        SafeTarGz.extractCodexBinary(paths.codexDownloadFile, dest)
        val alt = File(paths.rootfsDir, "usr/bin/codex")
        if (!alt.exists()) {
            alt.parentFile?.mkdirs()
            runCatching {
                Files.createSymbolicLink(alt.toPath(), File(CodexCatalog.guestBinaryPath).toPath())
            }
        }
        paths.codexReadyMarker.writeText(
            "codex ${CodexCatalog.version} ${CodexCatalog.sha256}\n",
        )
        appendLog("已安裝官方 Codex CLI ${CodexCatalog.version} 到 ${CodexCatalog.guestBinaryPath}")
    }

    private suspend fun runInstallDeb(uri: Uri) {
        try {
            if (!isReady()) {
                throw IOException("請先安裝 Debian 環境，再匯入 .deb。")
            }
            _state.update {
                it.copy(
                    phase = EnvUiState.Phase.InstallingDeb,
                    message = "正在讀取套件…",
                    error = null,
                    lastInstalled = null,
                )
            }
            paths.exchangeDir.mkdirs()
            val displayName = queryDisplayName(uri) ?: "incoming.deb"
            val safe = displayName.replace(Regex("[^A-Za-z0-9._+-]"), "_")
                .ifEmpty { "incoming.deb" }
            val dest = File(paths.exchangeDir, safe)
            withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("無法讀取所選檔案。")
            }
            val control = DebParser.parse(dest)
            appendLog("套件：${control.pkg} ${control.version}（${control.architecture}）")
            if (!control.isArm64OrAll) {
                dest.delete()
                throw IOException(
                    "拒絕安裝：這是 ${control.architecture} 套件。DebArm 只原生執行 arm64 / all 的 .deb，不會用 QEMU 跑 x86。",
                )
            }
            _state.update { it.copy(message = "正在以 dpkg 安裝 ${control.pkg}…") }
            val install = HostProcess.run(
                argv = Proot.argv(
                    paths,
                    listOf("dpkg -i '/exchange/${dest.name}' || apt-get install -f -y"),
                ),
                env = Proot.hostEnv(paths),
                timeoutMs = 15 * 60 * 1000L,
                cancelled = ::isCancelled,
                onChunk = { appendLog(it) },
            )
            if (install.exitCode != 0) {
                _state.update { it.copy(message = "dpkg 未完全成功，嘗試 apt-get update 後再修復依賴…") }
                val repair = HostProcess.run(
                    argv = Proot.argv(
                        paths,
                        listOf("apt-get update && apt-get install -f -y && dpkg -i '/exchange/${dest.name}'"),
                    ),
                    env = Proot.hostEnv(paths),
                    timeoutMs = 15 * 60 * 1000L,
                    cancelled = ::isCancelled,
                    onChunk = { appendLog(it) },
                )
                if (repair.exitCode != 0) {
                    throw IOException("安裝失敗（exit ${repair.exitCode}）。請看下方紀錄。")
                }
            }
            val pkgs = runCatching { PackageQuery.list(paths) }.getOrDefault(_state.value.packages)
            _state.update {
                it.copy(
                    phase = EnvUiState.Phase.Ready,
                    message = "已安裝 ${control.pkg} ${control.version}",
                    lastInstalled = control.pkg,
                    packages = pkgs,
                    diskFreeBytes = freeBytes(),
                    debianLabel = readDebianLabel(),
                )
            }
            updateDiskAsync()
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    phase = if (isReady()) EnvUiState.Phase.Ready else EnvUiState.Phase.Error,
                    error = e.message ?: e.toString(),
                    message = "安裝 .deb 失敗",
                )
            }
        }
    }

    private suspend fun runDelete() {
        _state.update { it.copy(phase = EnvUiState.Phase.Deleting, message = "正在刪除環境…") }
        runCatching { paths.rootfsDir.deleteRecursively() }
        runCatching { paths.rootfsPartialDir.deleteRecursively() }
        runCatching { paths.downloadFile.delete() }
        runCatching { paths.codexDownloadFile.delete() }
        runCatching { paths.codexReadyMarker.delete() }
        runCatching { paths.exchangeDir.listFiles()?.forEach { it.delete() } }
        cancelled.set(false)
        refreshNow()
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        val cursor = app.contentResolver.query(uri, null, null, null, null) ?: return uri.lastPathSegment
        cursor.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) return it.getString(idx)
        }
        return uri.lastPathSegment
    }

    @Synchronized
    private fun appendLog(chunk: String) {
        if (chunk.isEmpty()) return
        _state.update { st ->
            val next = (st.log + chunk).let { if (it.length > 20_000) it.takeLast(16_000) else it }
            st.copy(log = if (next.endsWith('\n') || chunk.endsWith('\n')) next else next)
        }
    }

    private fun freeBytes(): Long = try {
        StatFs(paths.filesDir.absolutePath).availableBytes
    } catch (_: Exception) {
        0L
    }

    private fun updateDiskAsync() {
        scope.launch {
            val used = dirSize(paths.rootfsDir)
            val free = freeBytes()
            _state.update { it.copy(diskUsedBytes = used, diskFreeBytes = free) }
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        var total = 0L
        val root = dir.toPath()
        try {
            java.nio.file.Files.walk(root).use { stream ->
                stream.forEach { p ->
                    try {
                        if (java.nio.file.Files.isRegularFile(
                                p,
                                java.nio.file.LinkOption.NOFOLLOW_LINKS,
                            )
                        ) {
                            total += java.nio.file.Files.size(p)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        return total
    }

    companion object {
        @Volatile
        private var instance: EnvironmentController? = null

        fun get(app: Application): EnvironmentController =
            instance ?: synchronized(this) {
                instance ?: EnvironmentController(app).also { instance = it }
            }

        fun formatBytes(n: Long): String {
            if (n < 1024) return "$n B"
            val kb = n / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB".format(mb)
            return "%.2f GB".format(mb / 1024.0)
        }
    }
}
