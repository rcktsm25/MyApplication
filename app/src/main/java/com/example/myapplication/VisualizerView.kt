package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.*

/** Visualizador de audio con múltiples estilos y colores personalizables. Estética Winamp Classic. */
class VisualizerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    companion object {
        val styles = listOf(
            "LED clásico", "Barras sólidas", "Línea de espectro", "Onda de audio",
            "Barras gradiente", "Onda rellena", "Espejo", "Circular",
            "Montaña", "Píxeles", "Tubo", "Anillo"
        )
        val responses = listOf("Suave", "Equilibrada", "Rápida", "Ultra rápida")
        val colorPresets: List<Pair<String, Int>> = listOf(
            "Verde Winamp" to 0xFFB6EE65.toInt(),
            "Naranja clásico" to 0xFFFF8C42.toInt(),
            "Azul hielo" to 0xFF4FC3F7.toInt(),
            "Rojo fuego" to 0xFFFF5252.toInt(),
            "Violeta" to 0xFFBA68C8.toInt(),
            "Cyan" to 0xFF18FFFF.toInt(),
            "Amarillo" to 0xFFFFF176.toInt(),
            "Blanco" to 0xFFE0E0E0.toInt(),
            "Rosa" to 0xFFF48FB1.toInt(),
            "Lima" to 0xFFB2FF59.toInt(),
            "Ámbar" to 0xFFFFD54F.toInt(),
            "Turquesa" to 0xFF64FFDA.toInt(),
        )
        private val COLOR_HIGH: Int = 0xFFE7A367.toInt()
        private val COLOR_MID: Int = 0xFFCEC36F.toInt()
        private val COLOR_ORANGE: Int = 0xFFFF8C42.toInt()
        private val COLOR_WHITE_40: Int = 0xCCFFFFFF.toInt()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val levels = FloatArray(28)
    private val peaks = FloatArray(28)
    private val gradientColors = IntArray(3)
    private var active = false
    private var accent: Int = -4911515
    private var mode = 0
    private var response = 1
    private var showPeaks = true
    private var multicolor = true
    private var grid = true
    private var waveThickness = 2f
    private var glowEffect = false
    private var mirrorMode = false
    private var gradientBars = false

    fun setActive(value: Boolean) {
        if (active != value) { active = value; invalidate() }
    }

    fun setColors(color: Int, dim: Int) {
        if (accent != color) { accent = color; invalidate() }
    }

    fun configure(prefs: SharedPreferences) {
        val nextMode = prefs.getInt("visualizer_style", 0).coerceIn(0, styles.size - 1)
        val nextResponse = prefs.getInt("visualizer_response", 1).coerceIn(0, responses.size - 1)
        val nextPeaks = prefs.getBoolean("visualizer_peaks", true)
        val nextMulticolor = prefs.getBoolean("visualizer_multicolor", true)
        val nextGrid = prefs.getBoolean("visualizer_grid", true)
        val nextThickness = prefs.getFloat("visualizer_thickness", 2f).coerceIn(1f, 6f)
        val nextGlow = prefs.getBoolean("visualizer_glow", false)
        val nextMirror = prefs.getBoolean("visualizer_mirror", false)
        val nextGradient = prefs.getBoolean("visualizer_gradient", false)
        if (mode == nextMode && response == nextResponse && showPeaks == nextPeaks &&
            multicolor == nextMulticolor && grid == nextGrid && waveThickness == nextThickness &&
            glowEffect == nextGlow && mirrorMode == nextMirror && gradientBars == nextGradient) return
        mode = nextMode; response = nextResponse; showPeaks = nextPeaks; multicolor = nextMulticolor
        grid = nextGrid; waveThickness = nextThickness; glowEffect = nextGlow; mirrorMode = nextMirror
        gradientBars = nextGradient
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        if (w <= 0 || h <= 0) return
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        canvas.clipRect(0f, 0f, w, h)

        val density = resources.displayMetrics.density
        val dim: Int = ColorUtils.blendARGB(accent, Color.BLACK, 0.88f)
        val bar: Float = w / 28f
        val smoothing = when (response) { 0 -> 0.18f; 2 -> 0.75f; 3 -> 0.85f; else -> 0.4f }

        for (i in levels.indices) {
            val target = if (active) AudioRuntime.spectrum[i] else 0f
            levels[i] += (target - levels[i]) * smoothing
            peaks[i] = max(levels[i], peaks[i] - when (response) { 0 -> 0.015f; 2 -> 0.065f; 3 -> 0.08f; else -> 0.035f })
        }

        paint.style = Paint.Style.FILL

        when (mode) {
            0 -> drawLedBars(canvas, width = w, height = h, density = density, bar = bar, dim = dim)
            1 -> drawSolidBars(canvas, width = w, height = h, density = density, bar = bar, dim = dim)
            2 -> drawSpectrumLine(canvas, width = w, height = h, density = density, dim = dim)
            3 -> drawWaveform(canvas, width = w, height = h, density = density, dim = dim)
            4 -> drawGradientBars(canvas, width = w, height = h, density = density, bar = bar, dim = dim)
            5 -> drawFilledWave(canvas, width = w, height = h, density = density, dim = dim)
            6 -> drawMirror(canvas, width = w, height = h, density = density, bar = bar, dim = dim)
            7 -> drawCircular(canvas, width = w, height = h, density = density, dim = dim)
            8 -> drawMountain(canvas, width = w, height = h, density = density, dim = dim)
            9 -> drawPixels(canvas, width = w, height = h, density = density, bar = bar, dim = dim)
            10 -> drawTube(canvas, width = w, height = h, density = density, bar = bar, dim = dim)
            11 -> drawRing(canvas, width = w, height = h, density = density, dim = dim)
        }

        canvas.restore()
        if (active || levels.any { it > 0.01f } || peaks.any { it > 0.01f }) postInvalidateDelayed(50)
    }

    private fun colorForLevel(level: Float): Int = if (!multicolor) accent else when {
        level > 0.82f -> COLOR_HIGH
        level > 0.65f -> COLOR_MID
        else -> accent
    }

    private fun drawLedBars(canvas: Canvas, width: Float, height: Float, density: Float, bar: Float, dim: Int) {
        val step = height / 12f
        val gap = (bar * 0.18f).coerceAtMost(density)
        for (i in levels.indices) {
            val left = i * bar + gap
            val right = (i + 1) * bar - gap
            for (segment in 0..11) {
                val lit = (segment + 1) / 12f <= levels[i]
                if (!lit && !grid) continue
                paint.color = if (lit) colorForLevel(segment / 12f) else dim
                canvas.drawRect(left, height - (segment + 1) * step, right, height - segment * step - (step * 0.2f).coerceAtMost(density), paint)
            }
            if (showPeaks && peaks[i] > 0.03f) {
                paint.color = accent
                val y = (height * (1 - peaks[i])).coerceAtLeast(0f)
                canvas.drawRect(left, y, right, (y + density).coerceAtMost(height.toFloat()), paint)
            }
        }
    }

    private fun drawSolidBars(canvas: Canvas, width: Float, height: Float, density: Float, bar: Float, dim: Int) {
        val gap = (bar * 0.18f).coerceAtMost(density)
        for (i in levels.indices) {
            val left = i * bar + gap
            val right = (i + 1) * bar - gap
            if (grid) { paint.color = dim; canvas.drawRect(left, 0f, right, height.toFloat(), paint) }
            paint.color = colorForLevel(levels[i])
            canvas.drawRect(left, height * (1 - levels[i]), right, height.toFloat(), paint)
            if (showPeaks && peaks[i] > 0.03f) {
                paint.color = accent
                val y = (height * (1 - peaks[i])).coerceAtLeast(0f)
                canvas.drawRect(left, y, right, (y + density).coerceAtMost(height.toFloat()), paint)
            }
        }
    }

    private fun drawSpectrumLine(canvas: Canvas, width: Float, height: Float, density: Float, dim: Int) {
        if (grid) {
            paint.color = dim; paint.strokeWidth = density
            for (i in 1..3) canvas.drawLine(0f, height * i / 4f, width.toFloat(), height * i / 4f, paint)
        }
        path.reset()
        for (i in levels.indices) {
            val x = i * width.toFloat() / (levels.size - 1)
            val y = (height - density) * (1 - levels[i])
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = density.coerceAtLeast(waveThickness)
        if (glowEffect) paint.setShadowLayer(waveThickness * 2f, 0f, 0f, accent)
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
    }

    private fun drawWaveform(canvas: Canvas, width: Float, height: Float, density: Float, dim: Int) {
        if (grid) {
            paint.color = dim; paint.strokeWidth = density
            for (i in 1..3) canvas.drawLine(0f, height * i / 4f, width.toFloat(), height * i / 4f, paint)
            for (i in 1..7) canvas.drawLine(width * i / 8f, 0f, width * i / 8f, height.toFloat(), paint)
        }
        val wave = AudioRuntime.waveform
        path.reset()
        for (i in wave.indices) {
            val x = i * width.toFloat() / (wave.size - 1)
            val y = height / 2f - (if (active) wave[i] else 0f) * height * 0.46f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = waveThickness
        if (glowEffect) paint.setShadowLayer(waveThickness * 3f, 0f, 0f, accent)
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
        if (mirrorMode) {
            path.reset()
            for (i in wave.indices) {
                val x = i * width.toFloat() / (wave.size - 1)
                val y = height / 2f + (if (active) wave[i] else 0f) * height * 0.46f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.alpha = 128
            canvas.drawPath(path, paint)
            paint.alpha = 255
        }
    }

    private fun drawGradientBars(canvas: Canvas, width: Float, height: Float, density: Float, bar: Float, dim: Int) {
        val gap = (bar * 0.18f).coerceAtMost(density)
        gradientColors[0] = accent
        gradientColors[1] = ColorUtils.blendARGB(accent, COLOR_ORANGE, 0.5f)
        gradientColors[2] = COLOR_HIGH
        for (i in levels.indices) {
            val left = i * bar + gap
            val right = (i + 1) * bar - gap
            val barHeight = height * levels[i]
            val gradient = LinearGradient(left, height, left, height - barHeight, gradientColors, null, Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(left, height - barHeight, right, height.toFloat(), paint)
            paint.shader = null
            if (grid) {
                paint.color = ColorUtils.blendARGB(accent, Color.BLACK, 0.7f)
                for (s in 1..11) canvas.drawLine(left, height - s * (height / 12f), right, height - s * (height / 12f), paint)
            }
            if (showPeaks && peaks[i] > 0.03f) {
                paint.color = accent
                val y = (height * (1 - peaks[i])).coerceAtLeast(0f)
                canvas.drawRect(left, y, right, (y + density).coerceAtMost(height.toFloat()), paint)
            }
        }
    }

    private fun drawFilledWave(canvas: Canvas, width: Float, height: Float, density: Float, dim: Int) {
        if (grid) {
            paint.color = dim; paint.strokeWidth = density
            for (i in 1..3) canvas.drawLine(0f, height * i / 4f, width.toFloat(), height * i / 4f, paint)
        }
        val wave = AudioRuntime.waveform
        path.reset()
        path.moveTo(0f, height / 2f)
        for (i in wave.indices) {
            val x = i * width.toFloat() / (wave.size - 1)
            val y = height / 2f - (if (active) wave[i] else 0f) * height * 0.46f
            path.lineTo(x, y)
        }
        path.lineTo(width, height / 2f)
        path.close()
        val gradient = LinearGradient(0f, 0f, 0f, height, accent, ColorUtils.blendARGB(accent, Color.BLACK, 0.6f), Shader.TileMode.CLAMP)
        paint.shader = gradient
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = waveThickness
        path.reset()
        for (i in wave.indices) {
            val x = i * width.toFloat() / (wave.size - 1)
            val y = height / 2f - (if (active) wave[i] else 0f) * height * 0.46f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawMirror(canvas: Canvas, width: Float, height: Float, density: Float, bar: Float, dim: Int) {
        val gap = (bar * 0.18f).coerceAtMost(density)
        val midY = height / 2f
        if (grid) {
            paint.color = dim; paint.strokeWidth = density
            canvas.drawLine(0f, midY, width, midY, paint)
        }
        for (i in levels.indices) {
            val left = i * bar + gap
            val right = (i + 1) * bar - gap
            val barH = midY * levels[i]
            paint.color = colorForLevel(levels[i])
            canvas.drawRect(left, midY - barH, right, midY, paint)
            canvas.drawRect(left, midY, right, midY + barH, paint)
            if (showPeaks && peaks[i] > 0.03f) {
                paint.color = accent
                val y = midY - midY * peaks[i]
                canvas.drawRect(left, y, right, y + density, paint)
                canvas.drawRect(left, midY + midY * peaks[i] - density, right, midY + midY * peaks[i], paint)
            }
        }
    }

    private fun drawCircular(canvas: Canvas, width: Float, height: Float, density: Float, dim: Int) {
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) / 2f - density * 2f
        val innerRadius = maxRadius * 0.3f
        if (grid) {
            paint.color = dim; paint.style = Paint.Style.STROKE; paint.strokeWidth = density
            canvas.drawCircle(cx, cy, maxRadius, paint)
            canvas.drawCircle(cx, cy, innerRadius, paint)
        }
        val angleStep = 360f / levels.size
        for (i in levels.indices) {
            val startAngle = i * angleStep - 90f
            val barLength = maxRadius * levels[i]
            val endX = cx + cos(Math.toRadians((startAngle + angleStep * 0.7).toDouble())).toFloat() * (innerRadius + barLength)
            val endY = cy + sin(Math.toRadians((startAngle + angleStep * 0.7).toDouble())).toFloat() * (innerRadius + barLength)
            val startX = cx + cos(Math.toRadians(startAngle.toDouble())).toFloat() * innerRadius
            val startY = cy + sin(Math.toRadians(startAngle.toDouble())).toFloat() * innerRadius
            paint.color = colorForLevel(levels[i])
            paint.strokeWidth = (angleStep * 0.5f).coerceAtLeast(density)
            paint.style = Paint.Style.STROKE
            canvas.drawLine(startX, startY, endX, endY, paint)
            if (showPeaks && peaks[i] > 0.03f) {
                paint.color = accent
                val peakR = innerRadius + maxRadius * peaks[i]
                val px = cx + cos(Math.toRadians((startAngle + angleStep * 0.35).toDouble())).toFloat() * peakR
                val py = cy + sin(Math.toRadians((startAngle + angleStep * 0.35).toDouble())).toFloat() * peakR
                paint.style = Paint.Style.FILL
                canvas.drawCircle(px, py, density, paint)
            }
        }
    }

    private fun drawMountain(canvas: Canvas, width: Float, height: Float, density: Float, dim: Int) {
        if (grid) {
            paint.color = dim; paint.strokeWidth = density
            canvas.drawLine(0f, height, width, height, paint)
        }
        path.reset()
        path.moveTo(0f, height)
        for (i in levels.indices) {
            val x = i * width / (levels.size - 1)
            val y = height - height * levels[i]
            path.lineTo(x, y)
        }
        path.lineTo(width, height)
        path.close()
        val gradient = LinearGradient(0f, 0f, 0f, height, accent, ColorUtils.blendARGB(accent, Color.BLACK, 0.7f), Shader.TileMode.CLAMP)
        paint.shader = gradient
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = waveThickness
        path.reset()
        for (i in levels.indices) {
            val x = i * width / (levels.size - 1)
            val y = height - height * levels[i]
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPixels(canvas: Canvas, width: Float, height: Float, density: Float, bar: Float, dim: Int) {
        val pixelSize = (bar * 0.7f).coerceAtLeast(density * 2f)
        val gap = (bar - pixelSize) / 2f
        val maxPixelsPerBar = 16
        for (i in levels.indices) {
            val left = i * bar + gap
            val litPixels = (levels[i] * maxPixelsPerBar).toInt().coerceIn(0, maxPixelsPerBar)
            for (p in 0 until maxPixelsPerBar) {
                val y = height - (p + 1) * (pixelSize + density)
                if (y < 0) break
                paint.color = if (p < litPixels) colorForLevel(p / maxPixelsPerBar.toFloat()) else dim
                canvas.drawRect(left, y, left + pixelSize, y + pixelSize, paint)
            }
        }
    }

    private fun drawTube(canvas: Canvas, width: Float, height: Float, density: Float, bar: Float, dim: Int) {
        val tubeWidth = (bar * 0.6f).coerceAtLeast(density * 3f)
        val gap = (bar - tubeWidth) / 2f
        for (i in levels.indices) {
            val left = i * bar + gap
            val right = left + tubeWidth
            val barHeight = height * levels[i]
            paint.color = colorForLevel(levels[i])
            paint.style = Paint.Style.FILL
            val rect = RectF(left, height - barHeight, right, height.toFloat())
            canvas.drawRoundRect(rect, tubeWidth / 2f, tubeWidth / 2f, paint)
            if (showPeaks && peaks[i] > 0.03f) {
                paint.color = COLOR_WHITE_40
                val peakY = height * (1 - peaks[i]) - density * 2f
                canvas.drawOval(RectF(left, peakY, right, peakY + tubeWidth), paint)
            }
        }
    }

    private fun drawRing(canvas: Canvas, width: Float, height: Float, density: Float, dim: Int) {
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(width, height) * 0.35f
        if (grid) {
            paint.color = dim; paint.style = Paint.Style.STROKE; paint.strokeWidth = density
            canvas.drawCircle(cx, cy, baseRadius, paint)
        }
        path.reset()
        for (i in levels.indices) {
            val angle = (i * 2 * PI / levels.size) - PI / 2
            val r = baseRadius * (1 + levels[i] * 0.5f)
            val x = cx + cos(angle).toFloat() * r
            val y = cy + sin(angle).toFloat() * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = waveThickness
        if (glowEffect) paint.setShadowLayer(waveThickness * 2f, 0f, 0f, accent)
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
        if (showPeaks) {
            paint.style = Paint.Style.FILL
            for (i in levels.indices) {
                if (peaks[i] < 0.03f) continue
                val angle = (i * 2 * PI / levels.size) - PI / 2
                val r = baseRadius * (1 + peaks[i] * 0.5f)
                val x = cx + cos(angle).toFloat() * r
                val y = cy + sin(angle).toFloat() * r
                paint.color = accent
                canvas.drawCircle(x, y, density * 1.5f, paint)
            }
        }
    }
}
