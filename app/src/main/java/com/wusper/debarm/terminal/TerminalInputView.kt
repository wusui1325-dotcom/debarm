package com.wusper.debarm.terminal

import android.content.Context
import android.text.InputType
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import java.nio.charset.StandardCharsets

/**
 * Focusable IME target that forwards both composing and committed text
 * to the PTY. Gboard often only calls setComposingText for each Latin
 * letter and delays commitText until space/enter — ignoring composing
 * made typed characters invisible in 1.1.2.
 */
class TerminalInputView(context: Context) : View(context) {
    var onInput: (ByteArray) -> Unit = {}

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        contentDescription = "終端輸入"
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Soft-keyboard key events are handled by InputConnection so we
        // don't double-send. Hardware keys (Bluetooth / OTG) come here.
        if (event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0) {
            return super.onKeyDown(keyCode, event)
        }
        val bytes = keyEventToBytes(event) ?: return super.onKeyDown(keyCode, event)
        onInput(bytes)
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        return Connection(this)
    }

    fun showKeyboard() {
        requestFocus()
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.restartInput(this)
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private class Connection(private val host: TerminalInputView) :
        BaseInputConnection(host, true) {

        private val editable = SpannableStringBuilder()
        private var composing = ""

        override fun getEditable() = editable

        private fun send(text: String) {
            if (text.isEmpty()) return
            host.onInput(text.toByteArray(StandardCharsets.UTF_8))
        }

        private fun sendDel(n: Int) {
            repeat(n.coerceAtLeast(0)) { host.onInput(byteArrayOf(0x7f)) }
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val next = text?.toString() ?: ""
            if (next != composing) {
                sendDel(composing.length)
                composing = next
                send(next)
            }
            editable.clear()
            editable.append(next)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val next = text?.toString() ?: ""
            if (composing.isNotEmpty()) {
                sendDel(composing.length)
                composing = ""
            }
            send(next)
            editable.clear()
            finishComposingText()
            return true
        }

        override fun finishComposingText(): Boolean {
            composing = ""
            editable.clear()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (composing.isNotEmpty()) {
                sendDel(composing.length)
                composing = ""
                editable.clear()
            } else {
                sendDel(beforeLength)
            }
            return true
        }

        override fun deleteSurroundingTextInCodePoints(before: Int, after: Int): Boolean {
            return deleteSurroundingText(before, after)
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_DEL && composing.isNotEmpty()) {
                    return deleteSurroundingText(1, 0)
                }
                val bytes = keyEventToBytes(event)
                if (bytes != null) {
                    if (composing.isNotEmpty() && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                        composing = ""
                        editable.clear()
                    }
                    host.onInput(bytes)
                    return true
                }
            }
            return true
        }
    }

    companion object {
        fun keyEventToBytes(event: KeyEvent): ByteArray? {
            val encoded = when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "\r"
                KeyEvent.KEYCODE_DEL -> "\u007f"
                KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
                KeyEvent.KEYCODE_TAB -> "\t"
                KeyEvent.KEYCODE_ESCAPE -> "\u001b"
                KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
                KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
                KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
                KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_HOME -> "\u001b[H"
                KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
                KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
                KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"
                else -> {
                    val uni = event.getUnicodeChar(event.metaState)
                    if (uni == 0) null else String(Character.toChars(uni))
                }
            } ?: return null
            return encoded.toByteArray(StandardCharsets.UTF_8)
        }

        fun diffToBytes(old: String, new: String): ByteArray {
            var prefix = 0
            val max = minOf(old.length, new.length)
            while (prefix < max && old[prefix] == new[prefix]) prefix++
            val out = StringBuilder()
            repeat(old.length - prefix) { out.append('\u007f') }
            out.append(new.substring(prefix))
            return out.toString().toByteArray(StandardCharsets.UTF_8)
        }
    }
}
