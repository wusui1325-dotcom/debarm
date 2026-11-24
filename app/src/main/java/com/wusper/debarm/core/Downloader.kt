package com.wusper.debarm.core

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Downloader {
    fun download(
        url: String,
        dest: File,
        expectedSha256: String,
        expectedBytes: Long = 0,
        cancelled: () -> Boolean = { false },
        onProgress: (read: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        if (tmp.exists()) tmp.delete()
        var read = 0L
        var connection: HttpURLConnection? = null
        try {
            connection = open(url)
            val totalHeader = connection.contentLengthLong
            val total = if (totalHeader > 0) totalHeader else expectedBytes
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) throw IOException("下載已取消")
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
        } finally {
            connection?.disconnect()
        }
        if (cancelled()) {
            tmp.delete()
            throw IOException("下載已取消")
        }
        val sha = Sha256.of(tmp)
        if (!Sha256.equal(sha, expectedSha256)) {
            tmp.delete()
            throw IOException("校驗失敗：下載的 SHA-256 是 $sha，預期 $expectedSha256")
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        onProgress(dest.length(), dest.length())
    }

    private fun open(url: String, redirects: Int = 0): HttpURLConnection {
        if (redirects > 8) throw IOException("重新導向次數過多")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "DebArm/1.0 (Android; +https://github.com/wusui1325-dotcom/debarm)")
        conn.connect()
        val code = conn.responseCode
        if (code in 300..399) {
            val loc = conn.getHeaderField("Location") ?: throw IOException("重新導向沒有 Location")
            conn.disconnect()
            val next = if (loc.startsWith("http")) loc else URL(URL(url), loc).toString()
            if (!next.startsWith("https://")) throw IOException("拒絕非 HTTPS 重新導向")
            return open(next, redirects + 1)
        }
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("下載失敗：HTTP $code")
        }
        return conn
    }
}
