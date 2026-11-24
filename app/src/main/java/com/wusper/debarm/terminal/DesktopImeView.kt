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

/**
 * Hidden native IME target for the official desktop WebView.
 * Composing and committed text are forwarded as Unicode into the
 * noVNC / x11vnc session (not typed only in this view). The view is
 * not clickable so touch-as-mouse still hits the canvas.
 */
class DesktopImeView(context: Context) : View(context) {
    var onText: (String) -> Unit = {}
    var onSpecial: (String) -> Unit = {}
    var onDismiss: () -> Unit = {}

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = false
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = "桌面輸入"
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            hideKeyboard()
            onDismiss()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0) {
            return super.onKeyDown(keyCode, event)
        }
        val special = specialName(event) ?: run {
            val uni = event.getUnicodeChar(event.metaState)
            if (uni != 0) {
                onText(String(Character.toChars(uni)))
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        onSpecial(special)
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
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
            if (!hasFocus()) requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.restartInput(this)
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    private class Connection(private val host: DesktopImeView) :
        BaseInputConnection(host, true) {

        private val editable = SpannableStringBuilder()
        private var composing = ""

        override fun getEditable() = editable

        private fun sendText(text: String) {
            if (text.isEmpty()) return
            host.onText(text)
        }

        private fun sendDel(n: Int) {
            repeat(n.coerceAtLeast(0)) { host.onSpecial("Backspace") }
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val next = text?.toString() ?: ""
            if (next != composing) {
                sendDel(composing.codePointCount(0, composing.length))
                composing = next
                sendText(next)
            }
            editable.clear()
            editable.append(next)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            var next = text?.toString() ?: ""
            if (composing.isNotEmpty()) {
                sendDel(composing.codePointCount(0, composing.length))
                composing = ""
            }
            if (next == "\n" || next == "\r" || next == "\r\n") {
                host.onSpecial("Enter")
            } else if (next.contains('\n') || next.contains('\r')) {
                next = next.replace("\r\n", "\n").replace('\r', '\n')
                val parts = next.split('\n')
                parts.forEachIndexed { i, part ->
                    if (part.isNotEmpty()) sendText(part)
                    if (i < parts.lastIndex) host.onSpecial("Enter")
                }
            } else {
                sendText(next)
            }
            editable.clear()
            finishComposingText()
            return true
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            if (composing.isNotEmpty()) {
                composing = ""
                editable.clear()
            }
            host.onSpecial("Enter")
            return true
        }

        override fun finishComposingText(): Boolean {
            composing = ""
            editable.clear()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (composing.isNotEmpty()) {
                sendDel(composing.codePointCount(0, composing.length))
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
                val special = specialName(event)
                if (special != null) {
                    if (event.keyCode == KeyEvent.KEYCODE_ENTER && composing.isNotEmpty()) {
                        composing = ""
                        editable.clear()
                    }
                    host.onSpecial(special)
                    return true
                }
                val uni = event.getUnicodeChar(event.metaState)
                if (uni != 0) {
                    sendText(String(Character.toChars(uni)))
                    return true
                }
            }
            return true
        }
    }

    companion object {
        fun specialName(event: KeyEvent): String? = when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "Enter"
            KeyEvent.KEYCODE_DEL -> "Backspace"
            KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
            KeyEvent.KEYCODE_TAB -> "Tab"
            KeyEvent.KEYCODE_ESCAPE -> "Escape"
            KeyEvent.KEYCODE_DPAD_UP -> "ArrowUp"
            KeyEvent.KEYCODE_DPAD_DOWN -> "ArrowDown"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "ArrowRight"
            KeyEvent.KEYCODE_DPAD_LEFT -> "ArrowLeft"
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_HOME -> "Home"
            KeyEvent.KEYCODE_MOVE_END -> "End"
            KeyEvent.KEYCODE_PAGE_UP -> "PageUp"
            KeyEvent.KEYCODE_PAGE_DOWN -> "PageDown"
            else -> null
        }
    }
}
