package com.example.bttouchpad

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Невидимое поле ввода (1×1 px). Когда оно в фокусе и показана системная клавиатура телефона,
 * всё, что вы набираете, уходит на ПК:
 *   "t <hex-кодпоинты>" — набрать текст,  "p KEY_xxx" — нажать клавишу.
 */
class KeyCaptureView(ctx: Context, private val send: (String) -> Unit) : View(ctx) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // без подсказок/автозамены/заглавных — клавиатура шлёт буквы сразу, как есть
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_ENTER_ACTION or
            EditorInfo.IME_ACTION_NONE
        return PcConnection(this, send)
    }
}

private class PcConnection(v: View, private val send: (String) -> Unit) : BaseInputConnection(v, false) {
    private var composing = ""

    private fun typeText(s: String) {
        if (s.isEmpty()) return
        val sb = StringBuilder("t")
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            sb.append(' ').append(Integer.toHexString(cp))
            i += Character.charCount(cp)
        }
        send(sb.toString())
    }

    private fun backspace(n: Int) {
        repeat(n) { send("p KEY_BACKSPACE") }
    }

    /** Заменяет «подчёркнутое» слово: стирает только отличающийся хвост и допечатывает новый. */
    private fun replaceComposing(new: String) {
        var common = 0
        while (common < composing.length && common < new.length && composing[common] == new[common]) common++
        val removed = composing.substring(common)
        backspace(removed.codePointCount(0, removed.length))
        typeText(new.substring(common))
        composing = new
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        replaceComposing(text.toString())
        return true
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        if (composing.isNotEmpty()) {
            replaceComposing(text.toString())
            composing = ""
        } else {
            typeText(text.toString())
        }
        return true
    }

    override fun finishComposingText(): Boolean {
        composing = ""
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (composing.isNotEmpty()) composing = composing.dropLast(minOf(beforeLength, composing.length))
        backspace(beforeLength)
        repeat(afterLength) { send("p KEY_DELETE") }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> send("p KEY_BACKSPACE")
            KeyEvent.KEYCODE_FORWARD_DEL -> send("p KEY_DELETE")
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> send("p KEY_ENTER")
            KeyEvent.KEYCODE_TAB -> send("p KEY_TAB")
            KeyEvent.KEYCODE_ESCAPE -> send("p KEY_ESC")
            KeyEvent.KEYCODE_DPAD_LEFT -> send("p KEY_LEFT")
            KeyEvent.KEYCODE_DPAD_RIGHT -> send("p KEY_RIGHT")
            KeyEvent.KEYCODE_DPAD_UP -> send("p KEY_UP")
            KeyEvent.KEYCODE_DPAD_DOWN -> send("p KEY_DOWN")
            else -> {
                val u = event.unicodeChar
                if (u > 0 && !event.isCtrlPressed) typeText(String(Character.toChars(u)))
            }
        }
        return true
    }
}
