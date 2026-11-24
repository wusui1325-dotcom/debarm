package com.wusper.debarm.terminal

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import java.nio.charset.StandardCharsets

/**
 * Focusable view that owns the Android IME and forwards committed text
 * and keys to the PTY. xterm.js's helper textarea is off-screen with
 * zero size, so WebView will not show a soft keyboard on its own.
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

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val bytes = keyEventToBytes(event)
            if (bytes != null) {
                onInput(bytes)
                return true
            }
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val bytes = keyEventToBytes(event)
        if (bytes != null) {
            onInput(bytes)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = -1
        outAttrs.initialSelEnd = -1
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    onInput(text.toString().toByteArray(StandardCharsets.UTF_8))
                }
                finishComposingText()
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                // Wait for commitText so CJK composition is sent as a whole.
                return true
            }

            override fun finishComposingText(): Boolean = true

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength.coerceAtLeast(0)) { onInput(byteArrayOf(0x7f)) }
                return true
            }

            override fun deleteSurroundingTextInCodePoints(before: Int, after: Int): Boolean {
                return deleteSurroundingText(before, after)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val bytes = keyEventToBytes(event)
                    if (bytes != null) {
                        onInput(bytes)
                        return true
                    }
                }
                return true
            }
        }
    }

    fun showKeyboard() {
        requestFocus()
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
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
    }
}
