package com.wusper.debarm.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Device-wide storage access for interconnecting the app-private
 * workspace with shared / external volumes. This is user-granted
 * all-files access, not root or accessibility control.
 */
object StorageAccess {
    fun hasFullAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun manageAllFilesIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:${context.packageName}")
        return intent
    }

    const val legacyWritePermission: String = Manifest.permission.WRITE_EXTERNAL_STORAGE
}
