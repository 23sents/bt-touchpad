package com.example.bttouchpad

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout

/**
 * Экранная клавиатура. Отправляет на ПК НАЖАТИЯ ФИЗИЧЕСКИХ КЛАВИШ (KEY_A и т.д.),
 * поэтому язык ввода определяется раскладкой на самом компьютере.
 * Shift / Ctrl / Alt / Win — «залипают» на одно следующее нажатие (Ctrl+C = Ctrl, потом C).
 * Кнопка EN/RU меняет только подписи букв.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class KeyboardView(
    ctx: Context,
    private val send: (String) -> Unit,
    private val onHide: () -> Unit
) : LinearLayout(ctx) {

    private class Key(
        val en: String, val code: String, val ru: String? = null,
        val w: Float = 1f, val mod: Boolean = false
    )

    private val dp = ctx.resources.displayMetrics.density
    private val normal = Color.rgb(58, 58, 64)
    private val pressed = Color.rgb(100, 100, 112)
    private val accent = Color.rgb(60, 120, 220)

    private var ruLabels = false
    private val activeMods = linkedSetOf<String>()
    private val modButtons = HashMap<String, Button>()
    private val letterButtons = ArrayList<Pair<Button, Key>>()

    private fun k(en: String, code: String, ru: String? = null, w: Float = 1f, mod: Boolean = false) =
        Key(en, code, ru, w, mod)
    private fun l(ch: String, ru: String) = Key(ch, "KEY_" + ch.uppercase(), ru)

    private val rows: List<List<Key>> = listOf(
        listOf(k("Esc", "KEY_ESC", w = 1.3f), k("`", "KEY_GRAVE", "ё"),
            k("1", "KEY_1"), k("2", "KEY_2"), k("3", "KEY_3"), k("4", "KEY_4"), k("5", "KEY_5"),
            k("6", "KEY_6"), k("7", "KEY_7"), k("8", "KEY_8"), k("9", "KEY_9"), k("0", "KEY_0"),
            k("-", "KEY_MINUS"), k("=", "KEY_EQUAL"), k("⌫", "KEY_BACKSPACE", w = 1.6f)),
        listOf(k("Tab", "KEY_TAB", w = 1.4f),
            l("q", "й"), l("w", "ц"), l("e", "у"), l("r", "к"), l("t", "е"), l("y", "н"),
            l("u", "г"), l("i", "ш"), l("o", "щ"), l("p", "з"),
            k("[", "KEY_LEFTBRACE", "х"), k("]", "KEY_RIGHTBRACE", "ъ"), k("\\", "KEY_BACKSLASH")),
        listOf(l("a", "ф"), l("s", "ы"), l("d", "в"), l("f", "а"), l("g", "п"), l("h", "р"),
            l("j", "о"), l("k", "л"), l("l", "д"),
            k(";", "KEY_SEMICOLON", "ж"), k("'", "KEY_APOSTROPHE", "э"),
            k("Enter", "KEY_ENTER", w = 1.8f)),
        listOf(k("Shift", "KEY_LEFTSHIFT", w = 2f, mod = true),
            l("z", "я"), l("x", "ч"), l("c", "с"), l("v", "м"), l("b", "и"), l("n", "т"), l("m", "ь"),
            k(",", "KEY_COMMA", "б"), k(".", "KEY_DOT", "ю"), k("/", "KEY_SLASH")),
        listOf(k("Ctrl", "KEY_LEFTCTRL", w = 1.3f, mod = true),
            k("Win", "KEY_LEFTMETA", w = 1.2f, mod = true),
            k("Alt", "KEY_LEFTALT", w = 1.2f, mod = true),
            k("␣", "KEY_SPACE", w = 4f),
            k("←", "KEY_LEFT"), k("↑", "KEY_UP"), k("↓", "KEY_DOWN"), k("→", "KEY_RIGHT"),
            k("EN", "LANG", w = 1.2f), k("▼", "HIDE"))
    )

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(24, 24, 27))
        val m = (2 * dp).toInt()
        setPadding(m, m, m, m)
        for (row in rows) {
            val r = LinearLayout(ctx).apply { orientation = HORIZONTAL }
            for (key in row) {
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, key.w)
                lp.setMargins(m, m, m, m)
                r.addView(makeButton(key), lp)
            }
            addView(r, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun bg(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = 6 * dp
    }

    private fun restoreBg(b: Button, key: Key) {
        b.background = bg(if (key.mod && key.code in activeMods) accent else normal)
    }

    private fun makeButton(key: Key): Button = Button(context).apply {
        text = key.en
        setAllCaps(false)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0, 0, 0, 0)
        background = bg(normal)
        if (key.ru != null) letterButtons.add(this to key)
        if (key.mod) modButtons[key.code] = this
        setOnTouchListener { v, ev -> handle(v as Button, key, ev); true }
    }

    private fun releaseKey(key: Key) {
        send("k ${key.code} 0")
        activeMods.forEach { send("k $it 0") }
        activeMods.clear()
        for ((code, b) in modButtons) b.background = bg(normal)
    }

    private fun handle(b: Button, key: Key, ev: MotionEvent) {
        val special = key.code == "LANG" || key.code == "HIDE"
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                b.background = bg(pressed)
                if (!special && !key.mod) {
                    activeMods.forEach { send("k $it 1") }
                    send("k ${key.code} 1")
                }
            }
            MotionEvent.ACTION_UP -> {
                when {
                    key.code == "HIDE" -> { restoreBg(b, key); onHide(); return }
                    key.code == "LANG" -> {
                        ruLabels = !ruLabels
                        b.text = if (ruLabels) "RU" else "EN"
                        for ((lb, lk) in letterButtons) lb.text = if (ruLabels) lk.ru else lk.en
                    }
                    key.mod -> if (!activeMods.remove(key.code)) activeMods.add(key.code)
                    else -> releaseKey(key)
                }
                restoreBg(b, key)
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!special && !key.mod) releaseKey(key)
                restoreBg(b, key)
            }
        }
    }
}
