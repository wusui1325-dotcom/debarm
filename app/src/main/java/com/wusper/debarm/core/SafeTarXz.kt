package com.wusper.debarm.core

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files

/**
 * Extracts a .tar.xz into [dest], refusing path traversal and skipping
 * device nodes (the guest will bind the Android /dev instead).
 */
object SafeTarXz {
    fun extract(
        archive: File,
        dest: File,
        stripComponents: Int,
        onProgress: (entryName: String, count: Long) -> Unit = { _, _ -> },
    ) {
        dest.mkdirs()
        val destCanon = dest.canonicalFile
        var count = 0L
        FileInputStream(archive).use { raw ->
            XZInputStream(raw).use { xz ->
                TarArchiveInputStream(xz, "UTF-8").use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        val stripped = strip(entry.name, stripComponents)
                        if (stripped != null) {
                            extractEntry(tar, entry, destCanon, stripped, stripComponents)
                            count++
                            if (count % 200L == 0L) onProgress(stripped, count)
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
        onProgress("完成", count)
    }

    private fun strip(name: String, strip: Int): String? {
        val clean = name.trim().removePrefix("./")
        if (clean.isEmpty()) return null
        val parts = clean.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.size <= strip) return null
        return parts.drop(strip).joinToString("/")
    }

    private fun extractEntry(
        tar: TarArchiveInputStream,
        entry: TarArchiveEntry,
        destCanon: File,
        rel: String,
        stripComponents: Int,
    ) {
        if (rel.split('/').any { it == ".." }) {
            throw IOException("壓縮檔含有非法路徑：$rel")
        }
        val out = File(destCanon, rel).canonicalFile
        if (!out.path.startsWith(destCanon.path + File.separator) && out != destCanon) {
            throw IOException("壓縮檔路徑跳出目標目錄：$rel")
        }
        when {
            entry.isDirectory -> out.mkdirs()
            entry.isSymbolicLink -> {
                out.parentFile?.mkdirs()
                if (out.exists()) out.delete()
                Files.createSymbolicLink(out.toPath(), File(entry.linkName).toPath())
            }
            entry.isLink -> {
                out.parentFile?.mkdirs()
                val candidates = listOfNotNull(
                    strip(entry.linkName, stripComponents),
                    strip(entry.linkName, 0),
                    entry.linkName.trim().removePrefix("./"),
                ).distinct()
                val target = candidates.map { File(destCanon, it) }.firstOrNull { it.exists() }
                if (out.exists()) out.delete()
                if (target != null) {
                    try {
                        Files.createLink(out.toPath(), target.toPath())
                    } catch (_: Exception) {
                        target.copyTo(out, overwrite = true)
                    }
                }
            }
            entry.isFIFO || entry.isBlockDevice || entry.isCharacterDevice -> {
                // Skip; /dev is bound from the host at runtime.
            }
            entry.isFile -> {
                out.parentFile?.mkdirs()
                out.outputStream().use { dest -> tar.copyTo(dest) }
                if (entry.mode and 0b001001001 != 0) {
                    out.setExecutable(true, false)
                }
            }
        }
    }
}
