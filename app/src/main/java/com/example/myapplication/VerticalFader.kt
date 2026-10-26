package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.roundToInt

/** Vertical fader with a real value, keyboard and TalkBack adjustments. */
class VerticalFader(context: Context) : View(context) {
    var progress: Int = 12
        set(value) { field = value.coerceIn(0, 24); invalidate() }
    var onChange: (Int) -> Unit = {}
    var color: Int = 0xFFB6EE65.toInt()
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isFocusable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        val x = width / 2f
        val density = resources.displayMetrics.density
        val top = 14 * density
        val bottom = height - top
        paint.strokeCap = Paint.Cap.SQUARE
        paint.color = 0xFF11121C.toInt(); paint.strokeWidth = 7 * density
        canvas.drawLine(x, top, x, bottom, paint)
        paint.color = if (isEnabled) color else 0xFF667070.toInt(); paint.strokeWidth = 3 * density
        canvas.drawLine(x, top, x, bottom, paint)
        val y = bottom - progress / 24f * (bottom - top)
        paint.color = 0xFF868696.toInt(); paint.strokeWidth = density
        for (i in 0..4) {
            val tick = top + (bottom - top) * i / 4
            canvas.drawLine(x - 11 * density, tick, x - 7 * density, tick, paint)
        }
        val half = 8 * density
        paint.color = 0xFF111722.toInt()
        canvas.drawRect(x - half, y - 8 * density, x + half, y + 8 * density, paint)
        paint.color = 0xFFDBE6E9.toInt()
        canvas.drawRect(x - half, y - 8 * density, x + half - density, y + 7 * density, paint)
        paint.color = 0xFF8C9DA8.toInt()
        canvas.drawRect(x - half + 2 * density, y - 6 * density, x + half - 2 * density, y + 6 * density, paint)
        paint.color = 0xFF273340.toInt()
        canvas.drawRect(x - 3 * density, y - 4 * density, x + 3 * density, y + 4 * density, paint)

    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val margin = 14 * resources.displayMetrics.density
                progress = ((height - margin - event.y) / (height - 2 * margin) * 24).roundToInt()
                onChange(progress); return true
            }
            MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false); performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false); return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (isEnabled && keyCode in listOf(android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN)) {
            progress += if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) 1 else -1; onChange(progress); return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 24f, progress.toFloat())
        if (isEnabled) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        }
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (isEnabled) when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { progress++; onChange(progress); return true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { progress--; onChange(progress); return true }
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                progress = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)?.roundToInt() ?: progress
                onChange(progress); return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }
}
