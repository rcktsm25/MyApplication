package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** Segmented clock drawn at any display density; text remains available to accessibility. */
class DigitalClockView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatTextView(context, attrs) {
    private val segmentPaint = Paint()
    private val digits = intArrayOf(0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f)

    override fun onDraw(canvas: Canvas) {
        val value = text.toString()
        val units = value.sumOf { if (it == ':') 7 else 23 }.coerceAtLeast(1)
        val scale = minOf((width - paddingLeft - paddingRight) / units.toFloat(),
            (height - paddingTop - paddingBottom) / 38f)
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), (height - 38f * scale) / 2f)
        canvas.scale(scale, scale)
        value.forEach { char ->
            if (char == ':') {
                segmentPaint.color = currentTextColor
                canvas.drawRect(1f, 11f, 4f, 14f, segmentPaint)
                canvas.drawRect(1f, 25f, 4f, 28f, segmentPaint)
                canvas.translate(7f, 0f)
            } else {
                val mask = char.digitToIntOrNull()?.let { digits[it] } ?: 0
                val segments = arrayOf(
                    floatArrayOf(3f, 1f, 17f, 4f), floatArrayOf(17f, 4f, 20f, 18f),
                    floatArrayOf(17f, 21f, 20f, 35f), floatArrayOf(3f, 35f, 17f, 38f),
                    floatArrayOf(0f, 21f, 3f, 35f), floatArrayOf(0f, 4f, 3f, 18f),
                    floatArrayOf(3f, 18f, 17f, 21f)
                )
                segments.forEachIndexed { index, bounds ->
                    segmentPaint.color = if (mask and (1 shl index) != 0) currentTextColor else 0xFF14200C.toInt()
                    canvas.drawRect(bounds[0], bounds[1], bounds[2], bounds[3], segmentPaint)
                }
                canvas.translate(23f, 0f)
            }
        }
        canvas.restore()
    }
}
