package com.wusper.debarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class DebParserTest {
    @Test
    fun parsesArm64Control() {
        val text = """
            Package: hello
            Version: 2.10-3
            Architecture: arm64
            Depends: libc6 (>= 2.34)
            Description: example package
             The classic greeting.
        """.trimIndent() + "\n"
        val c = DebParser.fromControlText(text)
        assertEquals("hello", c.pkg)
        assertEquals("2.10-3", c.version)
        assertEquals("arm64", c.architecture)
        assertTrue(c.isArm64OrAll)
        assertTrue(c.depends.contains("libc6"))
    }

    @Test
    fun rejectsAmd64() {
        val c = DebParser.fromControlText("Package: steam\nArchitecture: amd64\nVersion: 1\n")
        assertFalse(c.isArm64OrAll)
        assertFalse(Arch.isInstallableDebArch("amd64"))
        assertFalse(Arch.isInstallableDebArch("i386"))
        assertTrue(Arch.isInstallableDebArch("all"))
        assertTrue(Arch.isInstallableDebArch("aarch64"))
    }

    @Test
    fun parsesArDeb() {
        val control = "Package: demo\nVersion: 1.0\nArchitecture: all\nDescription: unit test\n"
        val deb = buildDeb(control)
        val parsed = DebParser.parse(deb.inputStream(), "demo.deb")
        assertEquals("demo", parsed.pkg)
        assertEquals("1.0", parsed.version)
        assertEquals("all", parsed.architecture)
        assertTrue(parsed.isArm64OrAll)
        deb.delete()
    }

    private fun buildDeb(control: String): File {
        val controlTar = tarSingle("control", control.toByteArray())
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(controlTar) }
        val out = File.createTempFile("demo", ".deb")
        out.outputStream().use { os ->
            os.write("!<arch>\n".toByteArray())
            writeAr(os, "debian-binary", "2.0\n".toByteArray())
            writeAr(os, "control.tar.gz", gz.toByteArray())
            writeAr(os, "data.tar.gz", ByteArray(0))
        }
        return out
    }

    private fun writeAr(os: java.io.OutputStream, name: String, data: ByteArray) {
        val header = ByteArray(60)
        java.util.Arrays.fill(header, ' '.code.toByte())
        fun put(off: Int, len: Int, s: String) {
            val b = s.toByteArray()
            System.arraycopy(b, 0, header, off, minOf(len, b.size))
        }
        put(0, 16, name)
        put(16, 12, "0")
        put(28, 6, "0")
        put(34, 6, "0")
        put(40, 8, "100644")
        put(48, 10, data.size.toString())
        header[58] = '`'.code.toByte()
        header[59] = '\n'.code.toByte()
        os.write(header)
        os.write(data)
        if (data.size % 2 == 1) os.write(0)
    }

    private fun tarSingle(name: String, data: ByteArray): ByteArray {
        val block = ByteArray(512 + ((data.size + 511) / 512) * 512 + 1024)
        val n = name.toByteArray()
        System.arraycopy(n, 0, block, 0, n.size)
        val size = String.format("%011o", data.size)
        System.arraycopy(size.toByteArray(), 0, block, 124, 11)
        System.arraycopy("0000644".toByteArray(), 0, block, 100, 7)
        block[156] = '0'.code.toByte()
        // ustar
        System.arraycopy("ustar\u000000".toByteArray(), 0, block, 257, 8)
        // checksum: 8 spaces first, then compute
        System.arraycopy("        ".toByteArray(), 0, block, 148, 8)
        var sum = 0
        for (b in block.copyOf(512)) sum += b.toInt() and 0xff
        val chk = String.format("%06o", sum)
        val chkBytes = (chk + "\u0000 ").toByteArray()
        System.arraycopy(chkBytes, 0, block, 148, 8)
        System.arraycopy(data, 0, block, 512, data.size)
        return block
    }
}
