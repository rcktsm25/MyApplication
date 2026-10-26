package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * Visualizador de osciloscopio que muestra la forma de onda del audio.
 */
class OscilloscopeView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    
    private val wavePaint = Paint().apply {
        color = 0xFFB6EE65.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    private val backgroundPaint = Paint().apply {
        color = 0xFF0A0A0F.toInt()
        style = Paint.Style.FILL
    }
    
    private val path = Path()
    private var waveform = FloatArray(128)
    private var isActive = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isActive) {
                invalidate()
                handler.postDelayed(this, 33) // ~30 FPS
            }
        }
    }
    
    fun setWaveform(data: FloatArray) {
        waveform = data.copyOf()
        if (isActive) {
            handler.removeCallbacks(refreshRunnable)
            handler.post(refreshRunnable)
        }
    }
    
    fun setActive(active: Boolean) {
        isActive = active
        if (active) {
            handler.post(refreshRunnable)
        } else {
            handler.removeCallbacksAndMessages(null)
        }
    }
    
    fun setColor(color: Int) {
        wavePaint.color = color
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val centerY = height / 2f
        val amplitude = height / 2f * 0.8f

        path.reset()
        waveform.forEachIndexed { index, value ->
            val x = (index.toFloat() / (waveform.size - 1)) * width
            val y = centerY + (value * amplitude)
            if (index == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        canvas.drawPath(path, wavePaint)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}
