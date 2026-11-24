package com.wusper.debarm.core

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * Extracts a .tar.gz into [dest], refusing path traversal.
 * Used for the official Codex musl tarball (typically a single binary).
 */
object SafeTarGz {
    fun extract(
        archive: File,
        dest: File,
        onProgress: (entryName: String, count: Long) -> Unit = { _, _ -> },
    ) {
        dest.mkdirs()
        val destCanon = dest.canonicalFile
        var count = 0L
        FileInputStream(archive).use { raw ->
            GZIPInputStream(raw).use { gz ->
                TarArchiveInputStream(gz, "UTF-8").use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        extractEntry(tar, entry, destCanon)
                        count++
                        onProgress(entry.name, count)
                        entry = tar.nextEntry
                    }
                }
            }
        }
        onProgress("完成", count)
    }

    fun extractCodexBinary(archive: File, destFile: File) {
        val tmp = File(destFile.parentFile, ".codex-extract").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            extract(archive, tmp)
            val binary = findBinary(tmp)
                ?: throw IOException("官方壓縮檔裡找不到 Codex 可執行檔。")
            destFile.parentFile?.mkdirs()
            if (destFile.exists()) destFile.delete()
            binary.copyTo(destFile, overwrite = true)
            destFile.setExecutable(true, false)
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun findBinary(dir: File): File? {
        val files = dir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .toList()
        val named = files.firstOrNull {
            it.name == "codex" || it.name.startsWith("codex-")
        }
        return named ?: files.maxByOrNull { it.length() }
    }

    private fun extractEntry(
        tar: TarArchiveInputStream,
        entry: TarArchiveEntry,
        destCanon: File,
    ) {
        val rel = entry.name.trim().removePrefix("./")
        if (rel.isEmpty() || rel.split('/').any { it == ".." }) {
            if (rel.split('/').any { it == ".." }) {
                throw IOException("壓縮檔含有非法路徑：${entry.name}")
            }
            return
        }
        val out = File(destCanon, rel).canonicalFile
        if (!out.path.startsWith(destCanon.path + File.separator) && out != destCanon) {
            throw IOException("壓縮檔路徑跳出目標目錄：${entry.name}")
        }
        when {
            entry.isDirectory -> out.mkdirs()
            entry.isSymbolicLink -> {
                out.parentFile?.mkdirs()
                if (out.exists()) out.delete()
                Files.createSymbolicLink(out.toPath(), File(entry.linkName).toPath())
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
