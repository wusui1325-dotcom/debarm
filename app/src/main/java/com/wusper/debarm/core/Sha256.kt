package com.wusper.debarm.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    fun of(file: File): String = file.inputStream().use { of(it) }

    fun of(stream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    fun equal(a: String, b: String): Boolean =
        a.trim().lowercase() == b.trim().lowercase()
}
