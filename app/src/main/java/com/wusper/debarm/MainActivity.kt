package com.wusper.debarm

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            DebArmTheme {
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
            }
            DisposableEffect(intent) {
                handleIncoming(intent)
                onDispose { }
            }
        }
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
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
