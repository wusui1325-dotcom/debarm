package com.wusper.debarm.core

import android.content.Context
import java.io.File

class DebArmPaths(context: Context) {
    private val ctx = context.applicationContext

    val filesDir: File = ctx.filesDir
    val cacheDir: File = ctx.cacheDir
    val rootfsDir: File = File(filesDir, "rootfs")
    val rootfsPartialDir: File = File(filesDir, "rootfs.partial")
    val readyMarker: File = File(rootfsDir, ".debarm-ready")
    val exchangeDir: File = File(filesDir, "exchange")
    val prootTmpDir: File = File(cacheDir, "proot-tmp")
    val downloadFile: File = File(cacheDir, "rootfs.tar.xz")
    val nativeLibraryDir: File = File(ctx.applicationInfo.nativeLibraryDir)

    val proot: File get() = File(nativeLibraryDir, "libproot.so")
    val prootLoader: File get() = File(nativeLibraryDir, "libproot-loader.so")

    fun ensureWorkDirs() {
        filesDir.mkdirs()
        cacheDir.mkdirs()
        exchangeDir.mkdirs()
        prootTmpDir.mkdirs()
    }
}
