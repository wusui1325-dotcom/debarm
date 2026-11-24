package com.wusper.debarm.core

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import io.airlift.compress.zstd.ZstdInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

data class DebControl(
    val pkg: String,
    val version: String,
    val architecture: String,
    val description: String,
    val depends: String,
    val raw: String,
) {
    val isArm64OrAll: Boolean get() = Arch.isInstallableDebArch(architecture)
}

/**
 * Minimal .deb (ar) reader: locates control.tar.* and parses the control file.
 * Used to reject x86 / foreign-arch packages before they enter the rootfs.
 */
object DebParser {
    private const val MAGIC = "!<arch>\n"

    fun parse(file: File): DebControl = file.inputStream().use { parse(it, file.name) }

    fun parse(stream: InputStream, name: String = "package.deb"): DebControl {
        val magic = ByteArray(8)
        if (stream.read(magic) != 8 || String(magic, Charsets.US_ASCII) != MAGIC) {
            throw IOException("這不是 Debian 套件（缺少 ar 檔頭）：$name")
        }
        while (true) {
            val header = ByteArray(60)
            val n = readFully(stream, header)
            if (n == 0) break
            if (n != 60) throw IOException("套件 ar 檔頭不完整：$name")
            val fileName = String(header, 0, 16, Charsets.US_ASCII).trim()
            val size = String(header, 48, 10, Charsets.US_ASCII).trim().toLongOrNull()
                ?: throw IOException("套件成員大小無效：$fileName")
            val payload = readExact(stream, size.toInt())
            if (size % 2L == 1L) {
                // ar even-padding
                stream.read()
            }
            val lower = fileName.trimEnd('/').lowercase()
            if (lower.startsWith("control.tar")) {
                return parseControlTar(payload, lower)
            }
        }
        throw IOException("套件裡找不到 control.tar：$name")
    }

    private fun parseControlTar(bytes: ByteArray, name: String): DebControl {
        val raw = ByteArrayInputStream(bytes)
        val unpacked: InputStream = when {
            name.endsWith(".xz") -> XZCompressorInputStream(raw)
            name.endsWith(".gz") || name.endsWith(".gzip") -> GzipCompressorInputStream(raw)
            name.endsWith(".bz2") -> BZip2CompressorInputStream(raw)
            name.endsWith(".zst") || name.endsWith(".zstd") -> ZstdInputStream(raw)
            else -> raw
        }
        unpacked.use { input ->
            TarArchiveInputStream(input).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val en = entry.name.trimStart('.', '/')
                    if (!entry.isDirectory && (en == "control" || en.endsWith("/control"))) {
                        val text = tar.readBytes().toString(Charsets.UTF_8)
                        return fromControlText(text)
                    }
                    entry = tar.nextEntry
                }
            }
        }
        throw IOException("control.tar 裡找不到 control 檔")
    }

    fun fromControlText(text: String): DebControl {
        val folded = foldControl(text)
        fun field(key: String): String {
            val prefix = "$key:"
            return folded.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                ?.substring(prefix.length)
                ?.trim()
                .orEmpty()
        }
        val pkg = field("Package")
        if (pkg.isEmpty()) throw IOException("control 缺少 Package 欄")
        return DebControl(
            pkg = pkg,
            version = field("Version"),
            architecture = field("Architecture").ifEmpty { "unknown" },
            description = field("Description").lineSequence().firstOrNull().orEmpty(),
            depends = field("Depends"),
            raw = text,
        )
    }

    private fun foldControl(text: String): List<String> {
        val out = mutableListOf<String>()
        for (line in text.split('\n')) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (out.isNotEmpty()) out[out.lastIndex] = out.last() + "\n" + line
            } else if (line.isNotEmpty()) {
                out += line
            }
        }
        return out
    }

    private fun readFully(stream: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = stream.read(buf, off, buf.size - off)
            if (n < 0) return off
            off += n
        }
        return off
    }

    private fun readExact(stream: InputStream, size: Int): ByteArray {
        val out = ByteArray(size)
        var off = 0
        while (off < size) {
            val n = stream.read(out, off, size - off)
            if (n < 0) throw IOException("套件讀取途中結束")
            off += n
        }
        return out
    }
}
