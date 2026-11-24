package com.wusper.debarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wusper.debarm.core.DebPackage
import com.wusper.debarm.core.EnvUiState
import com.wusper.debarm.core.EnvironmentController

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: EnvUiState,
    modifier: Modifier = Modifier,
    onInstallEnv: () -> Unit,
    onCancel: () -> Unit,
    onInstallDeb: () -> Unit,
    onOpenTerminal: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val ready = state.phase == EnvUiState.Phase.Ready
    val busy = state.isBusy

    Column(
        modifier = modifier
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("DebArm", style = MaterialTheme.typography.headlineLarge, color = Cream)
                Text("在 Android 上原生執行 arm64 .deb", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRefresh, enabled = !busy) {
                Icon(Icons.Outlined.Refresh, contentDescription = "重新整理", tint = Muted)
            }
        }
        Spacer(Modifier.height(16.dp))

        StatusCard(state)

        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.phase) {
                EnvUiState.Phase.NeedSetup, EnvUiState.Phase.Error -> {
                    Button(onClick = onInstallEnv, colors = goldButton()) {
                        Text("安裝 Debian 環境")
                    }
                }
                EnvUiState.Phase.Downloading, EnvUiState.Phase.Extracting, EnvUiState.Phase.Configuring -> {
                    OutlinedButton(onClick = onCancel) { Text("取消") }
                }
                else -> {}
            }
            FilledTonalButton(
                onClick = onOpenTerminal,
                enabled = ready,
            ) {
                Icon(Icons.Outlined.Terminal, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("開啟終端")
            }
            FilledTonalButton(
                onClick = onInstallDeb,
                enabled = ready && !busy,
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("安裝 .deb")
            }
        }

        AnimatedVisibility(visible = !state.error.isNullOrBlank()) {
            ErrorCard(state.error.orEmpty(), onDismissError)
        }

        if (ready) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已安裝套件", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy,
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Coral)
                    Spacer(Modifier.width(4.dp))
                    Text("刪除環境", color = Coral)
                }
            }
            OutlinedTextField(
                value = state.packageFilter,
                onValueChange = onFilter,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜尋套件名稱") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    cursorColor = Gold,
                ),
            )
            Spacer(Modifier.height(8.dp))
            val q = state.packageFilter.trim()
            val rows = state.packages.filter { pkg ->
                q.isEmpty() || pkg.name.contains(q, ignoreCase = true)
            }
            val visible = if (q.isEmpty()) rows.filter { it.manual } else rows
            Text(
                if (q.isEmpty()) "預設只顯示手動安裝的套件（搜尋可看全部 ${state.packages.size} 個）"
                else "找到 ${visible.size} 個套件",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visible, key = { it.name }) { pkg ->
                    PackageRow(pkg)
                }
                if (state.log.isNotBlank()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text("紀錄", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.log.takeLast(4000),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Muted,
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            if (state.log.isNotBlank()) {
                Text(
                    state.log.takeLast(4000),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
            } else {
                HelpCopy(Modifier.weight(1f))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除 Debian 環境？") },
            text = { Text("會移除本機 rootfs 與已安裝的套件，無法復原。APK 本身不會被卸載。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("刪除", color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun StatusCard(state: EnvUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(statusTitle(state.phase), style = MaterialTheme.typography.titleMedium, color = Gold)
            Spacer(Modifier.height(4.dp))
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            if (state.debianLabel != null) {
                Spacer(Modifier.height(6.dp))
                Text(state.debianLabel, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "已用 ${EnvironmentController.formatBytes(state.diskUsedBytes)} · 剩餘 ${EnvironmentController.formatBytes(state.diskFreeBytes)}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.phase == EnvUiState.Phase.Downloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    color = Gold,
                    trackColor = PaperHigh,
                )
            } else if (state.isBusy && state.phase != EnvUiState.Phase.Checking) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Gold,
                    trackColor = PaperHigh,
                )
            }
            if (state.lastInstalled != null && state.phase == EnvUiState.Phase.Ready) {
                Spacer(Modifier.height(6.dp))
                Text("剛剛裝好：${state.lastInstalled}", color = Moss, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = PaperHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("出錯了", style = MaterialTheme.typography.titleMedium, color = Coral)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text("關閉", color = Gold) }
        }
    }
}

@Composable
private fun PackageRow(pkg: DebPackage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Paper)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(pkg.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pkg.version, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Text(
            if (pkg.manual) "手動" else "依賴",
            style = MaterialTheme.typography.bodySmall,
            color = if (pkg.manual) Gold else Muted,
        )
    }
}

@Composable
private fun HelpCopy(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("怎麼用", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "1. 先安裝 Debian 環境（只需一次，不需 root）。\n" +
                "2. 用「安裝 .deb」選檔，或把套件拖進這個畫面（平板特別方便）。\n" +
                "3. 開終端執行剛裝好的指令，例如 hello、ffmpeg、python3。\n\n" +
                "只接受 arm64 或 Architecture: all 的套件，不會模擬 x86。第一版以命令列程式為主，圖形介面的 .deb 尚不支援。",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun statusTitle(phase: EnvUiState.Phase): String = when (phase) {
    EnvUiState.Phase.Checking -> "檢查中"
    EnvUiState.Phase.NeedSetup -> "尚未安裝環境"
    EnvUiState.Phase.Downloading -> "下載中"
    EnvUiState.Phase.Extracting -> "解壓中"
    EnvUiState.Phase.Configuring -> "設定中"
    EnvUiState.Phase.Ready -> "就緒"
    EnvUiState.Phase.InstallingDeb -> "正在安裝套件"
    EnvUiState.Phase.Deleting -> "正在刪除"
    EnvUiState.Phase.Error -> "發生錯誤"
}

@Composable
private fun goldButton() = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
