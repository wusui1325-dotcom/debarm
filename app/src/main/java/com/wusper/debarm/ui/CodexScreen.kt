package com.wusper.debarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wusper.debarm.core.CodexCatalog
import com.wusper.debarm.core.EnvUiState
import com.wusper.debarm.core.EnvironmentController

private val CardShape = RoundedCornerShape(18.dp)

@Composable
fun CodexScreen(
    state: EnvUiState,
    workspacePath: String,
    hasStorageAccess: Boolean,
    modifier: Modifier = Modifier,
    onPrepare: () -> Unit,
    onCancel: () -> Unit,
    onLaunch: () -> Unit,
    onLaunchTerminal: () -> Unit,
    onRequestStorage: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val ready = state.phase == EnvUiState.Phase.Ready && state.codexReady
    val busy = state.isBusy

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ChatGPT／Codex", style = MaterialTheme.typography.headlineLarge, color = Cream)
        Text(
            "只跑官方 Linux 桌面版（chatgpt arm64 .deb）。工作區在 App 私有目錄；對裝置儲存申請完全權限，讓內外空間互聯。環境會讓程式以為自己在一部有螢幕的 Debian 電腦上，並可上網。",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )

        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = Paper),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.message, style = MaterialTheme.typography.titleMedium, color = Cream)
                if (state.debianLabel != null) {
                    Text(state.debianLabel, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "官方桌面 ${CodexCatalog.packageName} ${CodexCatalog.version}" +
                        if (state.codexReady) " · 已就緒" else " · 尚未安裝",
                    color = if (state.codexReady) Moss else GoldDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val used = if (state.diskUsedBytes > 0) {
                    "已用 ${EnvironmentController.formatBytes(state.diskUsedBytes)}"
                } else {
                    "已用空間計算中"
                }
                Text(
                    "$used · 剩餘 ${EnvironmentController.formatBytes(state.diskFreeBytes)}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (busy && (state.phase == EnvUiState.Phase.Downloading || state.downloadTotal > 0)) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = state.progressFraction,
                        modifier = Modifier.fillMaxWidth(),
                        color = Gold,
                        trackColor = PaperHigh,
                    )
                }
                if (busy) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("取消")
                    }
                }
            }
        }

        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = Paper),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("工作區（App 私有）", style = MaterialTheme.typography.titleMedium, color = Cream)
                Text(workspacePath, fontFamily = FontFamily.Monospace, color = Gold, style = MaterialTheme.typography.bodySmall)
                Text("在 guest 裡掛載為 /workspace（也是 HOME）。", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(
                    "裝置儲存：" + if (hasStorageAccess) "已授予完全存取，/sdcard 與 /storage 已互聯" else "尚未授予。仍可在私有工作區使用 Codex，但無法寫入共用儲存。",
                    color = if (hasStorageAccess) Moss else GoldDim,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onRequestStorage,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (hasStorageAccess) "重新檢查儲存權限" else "授予裝置儲存完全權限")
                }
            }
        }

        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = Paper),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("官方來源", style = MaterialTheme.typography.titleMedium, color = Cream)
                Text("官方 ChatGPT／Codex Linux 桌面 ${CodexCatalog.version}", color = Cream, style = MaterialTheme.typography.bodySmall)
                Text("產物：${CodexCatalog.assetName}", fontFamily = FontFamily.Monospace, color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("SHA-256：${CodexCatalog.sha256}", fontFamily = FontFamily.Monospace, color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(
                    "來源 ${CodexCatalog.officialDocs}；版本化網址 ${CodexCatalog.primaryUrl}。首次準備會下載約 ${EnvironmentController.formatBytes(CodexCatalog.bytes)} 的官方 .deb（解壓後約 1.5 GB），並安裝 Xvfb／VNC 顯示堆疊。請預留約 4 GB 空間，保持 App 在前景。",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "「啟動官方桌面」會在 PRoot 裡開顯示器，再把畫面傳到本機視窗。這是軟體繪製的 X11，可能較慢；第一次進 App 需要用你的 OpenAI／ChatGPT 帳號登入。終端僅作後援，不再預設啟動命令列。",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (ready) {
            Button(
                onClick = onLaunch,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
            ) {
                Text("啟動官方桌面")
            }
            OutlinedButton(
                onClick = onLaunchTerminal,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("開啟終端（後援）")
            }
        } else {
            Button(
                onClick = onPrepare,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
            ) {
                Text(if (state.phase == EnvUiState.Phase.Error) "重試準備" else "準備並安裝官方桌面套件")
            }
        }

        OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("重新檢查")
        }
        TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("刪除 Debian 環境（保留工作區）", color = Coral)
        }

        if (!state.log.isBlank()) {
            Text("紀錄", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(state.log.takeLast(4000), fontFamily = FontFamily.Monospace, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("發生錯誤") },
            text = { Text(state.error) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("知道了") } },
            containerColor = Paper,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除環境？") },
            text = { Text("會刪除 Debian 與已安裝的官方 Codex CLI，工作區檔案會保留。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("刪除", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
            containerColor = Paper,
        )
    }
}
