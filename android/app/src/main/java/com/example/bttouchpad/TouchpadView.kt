package com.example.bttouchpad

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max

/**
 * Поверхность тачпада:
 *  1 палец — движение курсора, короткий тап — ЛКМ
 *  2 пальца — прокрутка, короткий тап двумя пальцами — ПКМ
 */
@SuppressLint("ViewConstructor")
class TouchpadView(ctx: Context, private val send: (String) -> Unit) : View(ctx) {
    var gain = 1.8f              // чувствительность курсора
    private val scrollStep = 30f // пикселей на один «щелчок» колеса
    private val slop = 24f
    private val tapMs = 250L

    private var lastX = 0f; private var lastY = 0f
    private var startX = 0f; private var startY = 0f
    private var downTime = 0L
    private var moved = false
    private var maxPointers = 0
    private var remX = 0f; private var remY = 0f
    private var accX = 0f; private var accY = 0f

    private val bg = Paint().apply { color = Color.rgb(28, 28, 32) }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY; textSize = 42f; textAlign = Paint.Align.CENTER
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        c.drawText("1 палец — курсор, тап — ЛКМ", width / 2f, height / 2f - 30f, hint)
        c.drawText("2 пальца — прокрутка, тап — ПКМ", width / 2f, height / 2f + 30f, hint)
    }

    private fun avgX(ev: MotionEvent, skip: Int = -1): Float {
        var s = 0f; var n = 0
        for (i in 0 until ev.pointerCount) if (i != skip) { s += ev.getX(i); n++ }
        return s / n
    }
    private fun avgY(ev: MotionEvent, skip: Int = -1): Float {
        var s = 0f; var n = 0
        for (i in 0 until ev.pointerCount) if (i != skip) { s += ev.getY(i); n++ }
        return s / n
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.x; lastY = ev.y; startX = ev.x; startY = ev.y
                downTime = ev.eventTime; moved = false; maxPointers = 1
                remX = 0f; remY = 0f; accX = 0f; accY = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = max(maxPointers, ev.pointerCount)
                lastX = avgX(ev); lastY = avgY(ev)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // чтобы курсор не «прыгал» при отпускании одного из пальцев
                lastX = avgX(ev, ev.actionIndex); lastY = avgY(ev, ev.actionIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                val cx = avgX(ev); val cy = avgY(ev)
                val dx = cx - lastX; val dy = cy - lastY
                lastX = cx; lastY = cy
                if (hypot(cx - startX, cy - startY) > slop) moved = true
                if (ev.pointerCount == 1) move(dx, dy) else scroll(dx, dy)
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && ev.eventTime - downTime < tapMs) {
                    send(if (maxPointers >= 2) "c r" else "c l")
                }
            }
        }
        return true
    }

    private fun move(dx: Float, dy: Float) {
        remX += dx * gain; remY += dy * gain
        val ix = remX.toInt(); val iy = remY.toInt()
        if (ix != 0 || iy != 0) {
            remX -= ix; remY -= iy
            send("m $ix $iy")
        }
    }

    private fun scroll(dx: Float, dy: Float) {
        accX += dx; accY += dy
        val sy = (accY / scrollStep).toInt()
        val sx = (accX / scrollStep).toInt()
        if (sy != 0 || sx != 0) {
            accY -= sy * scrollStep; accX -= sx * scrollStep
            send("s ${-sy} $sx")
        }
    }
}
