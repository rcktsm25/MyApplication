package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Vista personalizada para el visualizador de audio estilo Winamp.
 * Dibuja barras verticales animadas usando Canvas (GPU) en lugar de modificar layoutParams,
 * lo que elimina el layout thrashing y los jank de frames.
 */
class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barCount = 14
    private val barHeightRatios = FloatArray(barCount) { 0.05f }
    private val targetRatios = FloatArray(barCount) { 0.05f }
    private var animating = false
    private var accentColor: Int = 0xFFFF4500.toInt()
    private var dimColor: Int = 0xFF441100.toInt()

    fun isAnimating(): Boolean = animating

    fun setColors(accent: Int, dim: Int) {
        accentColor = accent
        dimColor = dim
        invalidate()
    }

    fun startAnimation() {
        animating = true
        generateTargets()
        invalidate()
    }

    fun stopAnimation() {
        animating = false
        for (i in 0 until barCount) {
            targetRatios[i] = 0.04f
        }
        invalidate()
    }

    private fun generateTargets() {
        for (i in 0 until barCount) {
            targetRatios[i] = Random.nextFloat() * 0.9f + 0.05f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val totalMargin = barCount * 2 * 1f // 1dp margen a cada lado de cada barra (en px)
        val barWidth = (w - totalMargin) / barCount

        for (i in 0 until barCount) {
            // Suavizar movimiento hacia el target
            barHeightRatios[i] += (targetRatios[i] - barHeightRatios[i]) * 0.3f

            val barH = barHeightRatios[i] * h
            val left = i * (barWidth + 2f)
            val top = h - barH
            val right = left + barWidth

            paint.color = if (animating) accentColor else dimColor
            canvas.drawRect(left, top, right, h, paint)
        }

        if (animating) {
            // Generar nuevos targets periódicamente
            if (Math.random() < 0.15) generateTargets()
            postInvalidateDelayed(50)
        } else {
            // Seguir animando hasta que todas las barras lleguen al mínimo
            val allAtMin = barHeightRatios.all { it < 0.06f }
            if (!allAtMin) postInvalidateDelayed(50)
        }
    }
}
