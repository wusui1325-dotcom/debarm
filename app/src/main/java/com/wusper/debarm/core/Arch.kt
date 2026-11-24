package com.wusper.debarm.core

import android.os.Build

object Arch {
    const val APP_ABI = "arm64-v8a"
    const val DEBIAN_ARCH = "arm64"
    const val UNAME = "aarch64"

    fun isArm64Device(): Boolean =
        Build.SUPPORTED_64_BIT_ABIS.contains(APP_ABI) ||
            Build.SUPPORTED_ABIS.contains(APP_ABI)

    fun deviceSummary(): String {
        val abis = (Build.SUPPORTED_ABIS ?: emptyArray()).joinToString(", ")
        return "裝置 ABI：$abis · 機型：${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /** Accepted values in a .deb control Architecture field. */
    fun isInstallableDebArch(arch: String): Boolean {
        val a = arch.trim().lowercase()
        return a == "all" || a == "arm64" || a == "aarch64" || a == "arm64-v8a"
    }
}
