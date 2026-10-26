package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Visualizador de partículas reactivas al audio.
 */
class ParticleVisualizer(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    
    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var alpha: Float,
        var color: Int
    )
    
    private val particles = mutableListOf<Particle>()
    private val paint = Paint().apply {
        isAntiAlias = true
    }
    
    private var spectrum = FloatArray(28) { 0f }
    private var isActive = false
    private var accentColor = 0xFFB6EE65.toInt()
    
    init {
        initializeParticles(50)
    }
    
    private fun initializeParticles(count: Int) {
        particles.clear()
        for (i in 0 until count) {
            particles.add(Particle(
                x = Random.nextFloat() * 1000,
                y = Random.nextFloat() * 1000,
                vx = (Random.nextFloat() - 0.5f) * 2f,
                vy = (Random.nextFloat() - 0.5f) * 2f,
                size = Random.nextFloat() * 4 + 2,
                alpha = Random.nextFloat() * 0.5f + 0.3f,
                color = accentColor
            ))
        }
    }
    
    fun setSpectrum(data: FloatArray) {
        spectrum = data.copyOf()
        if (isActive) invalidate()
    }
    
    fun setActive(active: Boolean) {
        isActive = active
        if (active) postInvalidateOnAnimation()
    }
    
    fun setColor(color: Int) {
        accentColor = color
        particles.forEach { it.color = color }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isActive) return
        
        val energy = spectrum.average().toFloat()
        
        particles.forEach { particle ->
            // Actualizar posición basada en energía del audio
            particle.vx += (Random.nextFloat() - 0.5f) * energy * 2
            particle.vy += (Random.nextFloat() - 0.5f) * energy * 2
            
            // Limitar velocidad
            val maxSpeed = 5f
            particle.vx = particle.vx.coerceIn(-maxSpeed, maxSpeed)
            particle.vy = particle.vy.coerceIn(-maxSpeed, maxSpeed)
            
            particle.x += particle.vx
            particle.y += particle.vy
            
            // Envolver en los bordes
            if (particle.x < 0) particle.x = width.toFloat()
            if (particle.x > width) particle.x = 0f
            if (particle.y < 0) particle.y = height.toFloat()
            if (particle.y > height) particle.y = 0f
            
            // Actualizar tamaño y alpha basado en energía
            particle.size = (2f + energy * 6f).coerceIn(1f, 10f)
            particle.alpha = (0.2f + energy * 0.6f).coerceIn(0.1f, 0.9f)
            
            // Dibujar partícula
            paint.color = particle.color
            paint.alpha = (particle.alpha * 255).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.size, paint)
        }
        
        if (isActive) postInvalidateOnAnimation()
    }
}
