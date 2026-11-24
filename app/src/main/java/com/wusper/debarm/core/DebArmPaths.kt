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
    val workspaceDir: File = File(filesDir, "workspace")
    val codexDownloadFile: File = File(cacheDir, CodexCatalog.assetName)
    val codexReadyMarker: File = File(filesDir, ".debarm-codex-ready")
    val prootTmpDir: File = File(cacheDir, "proot-tmp")
    val downloadFile: File = File(cacheDir, "rootfs.tar.xz")
    val nativeLibraryDir: File = File(ctx.applicationInfo.nativeLibraryDir)
    /** Staged SONAMEs that Android jniLibs cannot ship (e.g. libtalloc.so.2). */
    val hostLibDir: File = File(filesDir, "proot-lib")

    val proot: File get() = File(nativeLibraryDir, "libproot.so")
    val prootLoader: File get() = File(nativeLibraryDir, "libproot-loader.so")

    fun ensureWorkDirs() {
        filesDir.mkdirs()
        cacheDir.mkdirs()
        exchangeDir.mkdirs()
        workspaceDir.mkdirs()
        prootTmpDir.mkdirs()
        stageHostLibs()
    }

    /**
     * PRoot is shipped unpatched (NEEDED: libtalloc.so.2). Android only
     * extracts jniLibs named lib*.so, so we copy that library under the
     * SONAME the ELF actually asks for and put the directory first on
     * LD_LIBRARY_PATH. Avoids patchelf rewriting program headers.
     */
    fun stageHostLibs() {
        hostLibDir.mkdirs()
        copyIfNeeded(File(nativeLibraryDir, "libtalloc.so"), File(hostLibDir, "libtalloc.so.2"))
        copyIfNeeded(
            File(nativeLibraryDir, "libandroid-shmem.so"),
            File(hostLibDir, "libandroid-shmem.so"),
        )
    }

    private fun copyIfNeeded(src: File, dest: File) {
        if (!src.exists()) return
        if (dest.exists() && dest.length() == src.length() && dest.lastModified() >= src.lastModified()) {
            return
        }
        src.copyTo(dest, overwrite = true)
        dest.setReadable(true, false)
    }
}
