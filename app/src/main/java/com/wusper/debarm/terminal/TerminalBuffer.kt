package com.wusper.debarm.terminal

import java.nio.ByteBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Turns raw PTY bytes into visible text without WebView / xterm.js.
 * Strips CSI/OSC so a Debian prompt is readable, and honours CR/LF/BS.
 */
class TerminalBuffer(private val maxChars: Int = 120_000) {
    private val text = StringBuilder()
    private val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var leftover = ByteArray(0)
    private var escape = 0 // 0 none, 1 ESC, 2 CSI, 3 OSC
    var bytesIn: Long = 0
        private set

    @Synchronized
    fun clear() {
        text.setLength(0)
        leftover = ByteArray(0)
        escape = 0
        bytesIn = 0
    }

    @Synchronized
    fun appendHostMessage(msg: String) {
        if (text.isNotEmpty() && text.last() != '\n') text.append('\n')
        text.append(msg)
        if (!msg.endsWith('\n')) text.append('\n')
        trim()
    }

    @Synchronized
    fun append(bytes: ByteArray): String {
        if (bytes.isEmpty()) return text.toString()
        bytesIn += bytes.size
        val src = if (leftover.isEmpty()) bytes else leftover + bytes
        leftover = ByteArray(0)
        val decoded = decodeUtf8(src)
        var i = 0
        while (i < decoded.length) {
            val ch = decoded[i]
            when (escape) {
                1 -> { // ESC
                    escape = when (ch) {
                        '[' -> 2
                        ']' -> 3
                        '(', ')', '#' -> 1 // still consume the next char
                        else -> 0
                    }
                    // For '(', ')' and '#', stay in 1 for one more, then 0.
                    if (escape == 1 && ch != '[' && ch != ']') {
                        // the introducer itself is consumed; next char ends it
                        escape = 4
                    }
                }
                2 -> { // CSI: skip until a final byte @-~
                    if (ch in '@'..'~') escape = 0
                }
                3 -> { // OSC: skip until BEL or ST (ESC \)
                    if (ch == '\u0007') escape = 0
                    else if (ch == '\\' && text.isNotEmpty()) escape = 0
                    else if (ch == '\u001b') escape = 3
                }
                4 -> escape = 0 // consumed the char after ESC ( ) #
                else -> when (ch) {
                    '\u001b' -> escape = 1
                    '\u0007' -> { /* bell */ }
                    '\r' -> carriageReturn()
                    '\n' -> text.append('\n')
                    '\b' -> backspace()
                    '\t' -> text.append('\t')
                    else -> if (ch.code >= 32 || ch == '\n') text.append(ch)
                }
            }
            i++
        }
        trim()
        return text.toString()
    }

    @Synchronized
    fun snapshot(): String = text.toString()

    private fun carriageReturn() {
        val lastNl = text.lastIndexOf('\n')
        text.setLength(lastNl + 1)
    }

    private fun backspace() {
        if (text.isEmpty()) return
        if (text.last() != '\n') text.deleteCharAt(text.length - 1)
    }

    private fun trim() {
        if (text.length <= maxChars) return
        val drop = text.length - maxChars
        val cut = text.indexOf('\n', drop).let { if (it < 0) drop else it + 1 }
        text.delete(0, cut)
    }

    private fun decodeUtf8(src: ByteArray): String {
        val buf = ByteBuffer.wrap(src)
        val out = decoder.decode(buf)
        val remaining = buf.remaining()
        if (remaining > 0) {
            leftover = ByteArray(remaining)
            buf.get(leftover)
        }
        return out.toString()
    }
}
