package com.wusper.debarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import com.wusper.debarm.core.StorageAccess
import com.wusper.debarm.ui.CodexScreen
import com.wusper.debarm.ui.DebArmTheme
import com.wusper.debarm.ui.MainScreen

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as DebArmApp
            val state by app.env.state.collectAsState()
            DebArmTheme {
                if (BuildConfig.CODEX_EDITION) {
                    var hasStorage by remember { mutableStateOf(StorageAccess.hasFullAccess(this)) }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                hasStorage = StorageAccess.hasFullAccess(this@MainActivity)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    val legacyPermission = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) {
                        hasStorage = StorageAccess.hasFullAccess(this)
                    }
                    CodexScreen(
                        state = state,
                        workspacePath = app.env.pathsPublic.workspaceDir.absolutePath,
                        hasStorageAccess = hasStorage,
                        modifier = Modifier.fillMaxSize(),
                        onPrepare = { app.env.prepareCodex() },
                        onCancel = { app.env.cancelWork() },
                        onLaunch = {
                            startActivity(
                                Intent(this, TerminalActivity::class.java)
                                    .putExtra(TerminalActivity.EXTRA_LAUNCH_CODEX, true),
                            )
                        },
                        onRequestStorage = {
                            if (Build.VERSION.SDK_INT >= 30) {
                                try {
                                    startActivity(StorageAccess.manageAllFilesIntent(this))
                                } catch (_: Exception) {
                                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                }
                            } else {
                                legacyPermission.launch(StorageAccess.legacyWritePermission)
                            }
                        },
                        onDelete = { app.env.deleteEnvironment() },
                        onRefresh = { app.env.refresh() },
                        onDismissError = { app.env.refresh() },
                    )
                } else {
                    val picker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri != null) app.env.installDeb(uri)
                    }
                    val dropTarget = remember {
                        object : DragAndDropTarget {
                            override fun onDrop(event: DragAndDropEvent): Boolean {
                                val androidEvent = event.toAndroidDragEvent()
                                val clip = androidEvent.clipData ?: return false
                                if (clip.itemCount < 1) return false
                                val uri = clip.getItemAt(0).uri ?: return false
                                try {
                                    contentResolver.takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                    )
                                } catch (_: Exception) {
                                }
                                app.env.installDeb(uri)
                                return true
                            }
                        }
                    }
                    MainScreen(
                        state = state,
                        modifier = Modifier
                            .fillMaxSize()
                            .dragAndDropTarget(
                                shouldStartDragAndDrop = { ev ->
                                    ev.mimeTypes().any {
                                        it.contains("uri") || it.contains("text") ||
                                            it.contains("debian") || it.contains("octet")
                                    } || ev.mimeTypes().isNotEmpty()
                                },
                                target = dropTarget,
                            ),
                        onInstallEnv = { app.env.installEnvironment() },
                        onCancel = { app.env.cancelWork() },
                        onInstallDeb = {
                            picker.launch(
                                arrayOf(
                                    "application/vnd.debian.binary-package",
                                    "application/x-debian-package",
                                    "application/octet-stream",
                                    "*/*",
                                ),
                            )
                        },
                        onOpenTerminal = {
                            startActivity(Intent(this, TerminalActivity::class.java))
                        },
                        onDelete = { app.env.deleteEnvironment() },
                        onRefresh = { app.env.refresh() },
                        onFilter = { app.env.setPackageFilter(it) },
                        onDismissError = { app.env.refresh() },
                    )
                    DisposableEffect(intent) {
                        handleIncoming(intent)
                        onDispose { }
                    }
                }
            }
        }
        if (!BuildConfig.CODEX_EDITION) handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!BuildConfig.CODEX_EDITION) handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            (application as DebArmApp).env.installDeb(uri)
        }
    }
}
