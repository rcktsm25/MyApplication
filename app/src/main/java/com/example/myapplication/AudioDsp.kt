package com.example.myapplication

import kotlin.math.*

/** Immutable snapshots cross the UI/audio-thread boundary. */
data class DspSettings(
    val enabled: Boolean = false,
    val gains: List<Float> = List(10) { 0f },
    val preamp: Float = 0f,
    val balance: Float = 0f
)

object AudioRuntime {
    @Volatile var settings = DspSettings()
    @Volatile var spectrum = FloatArray(28)
    @Volatile var waveform = FloatArray(128)
    @Volatile var sampleRate = 0
    @Volatile var channels = 0
    @Volatile var sleepUntil = 0L
}

/** Ten constant-Q peaking filters, one independent history per PCM channel. */
class AudioDsp(private val sampleRate: Int, private val channels: Int) {
    companion object {
        val frequencies = doubleArrayOf(60.0, 170.0, 310.0, 600.0, 1000.0, 3000.0, 6000.0, 12000.0, 14000.0, 16000.0)
        val presets = linkedMapOf(
            "Plano" to List(10) { 0f },
            "Rock" to listOf(4f, 3f, 1f, -1f, -2f, 1f, 3f, 4f, 4f, 3f),
            "Pop" to listOf(-1f, 2f, 4f, 5f, 3f, 0f, -1f, -2f, -2f, -1f),
            "Jazz" to listOf(3f, 2f, 0f, 2f, -2f, -2f, 0f, 2f, 3f, 4f),
            "Clásica" to listOf(4f, 3f, 2f, 1f, -1f, -2f, -1f, 0f, 2f, 4f),
            "Electrónica" to listOf(5f, 4f, 1f, 0f, -2f, 1f, 2f, 4f, 3f, 2f),
            "Hip-Hop" to listOf(5f, 4f, 2f, 0f, -1f, 1f, 2f, 3f, 3f, 2f),
            "R&B" to listOf(4f, 3f, 1f, 0f, -1f, 0f, 1f, 3f, 4f, 4f),
            "Voces" to listOf(-3f, -2f, 0f, 2f, 3f, 3f, 1f, 0f, -1f, -2f),
            "Podcast" to listOf(-2f, -1f, 1f, 3f, 4f, 4f, 2f, 0f, -1f, -2f),
            "Graves" to listOf(6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Agudos" to listOf(0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 5f, 6f),
            "Latin" to listOf(4f, 3f, 2f, 1f, -1f, 0f, 2f, 3f, 3f, 2f),
            "Reggaetón" to listOf(5f, 4f, 2f, 0f, -2f, 1f, 2f, 3f, 3f, 2f),
            "Metal" to listOf(5f, 3f, 0f, -1f, -2f, 0f, 2f, 4f, 4f, 5f),
            "Ska" to listOf(3f, 2f, 0f, -1f, -2f, 0f, 2f, 3f, 4f, 4f),
        )
    }
    private val coefficients = Array(10) { doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0) }
    private val z1 = Array(channels) { DoubleArray(10) }
    private val z2 = Array(channels) { DoubleArray(10) }
    private var current = DspSettings()
    private var gain = 1.0
    private var left = 1.0
    private var right = 1.0

    fun configure(settings: DspSettings) {
        if (settings == current) return
        current = settings
        gain = if (settings.enabled) 10.0.pow(settings.preamp.coerceIn(-12f, 6f) / 20.0) else 1.0
        val balance = settings.balance.coerceIn(-1f, 1f)
        left = if (balance > 0) 1.0 - balance else 1.0
        right = if (balance < 0) 1.0 + balance else 1.0
        frequencies.forEachIndexed { i, frequency ->
            val db = if (settings.enabled && frequency < sampleRate * 0.48)
                settings.gains.getOrElse(i) { 0f }.coerceIn(-12f, 12f).toDouble() else 0.0
            val a = 10.0.pow(db / 40)
            val w = 2 * PI * frequency.coerceAtMost(sampleRate * 0.48) / sampleRate
            val alpha = sin(w) / (2 * 1.4)
            val a0 = 1 + alpha / a
            coefficients[i] = doubleArrayOf((1 + alpha * a) / a0, -2 * cos(w) / a0,
                (1 - alpha * a) / a0, -2 * cos(w) / a0, (1 - alpha / a) / a0)
        }
        reset()
    }

    fun reset() { z1.forEach { it.fill(0.0) }; z2.forEach { it.fill(0.0) } }

    fun process(sample: Float, channel: Int): Float {
        var value = sample.toDouble() * gain
        if (current.enabled) {
            for (i in 0..9) {
                val c = coefficients[i]
                val output = c[0] * value + z1[channel][i]
                z1[channel][i] = c[1] * value - c[3] * output + z2[channel][i]
                z2[channel][i] = c[2] * value - c[4] * output
                value = output
            }
        }
        if (channels >= 2) value *= if (channel == 0) left else if (channel == 1) right else 1.0
        return value.coerceIn(-1.0, 1.0).toFloat()
    }
}

/** A real 512-point FFT; no microphone permission or synthetic random bars. */
class AudioSpectrum {
    private val samples = DoubleArray(512)
    var waveform = FloatArray(128)
        private set
    private var cursor = 0
    private var skip = 0
    fun add(sample: Float, sampleRate: Int): FloatArray? {
        if (skip > 0) { skip--; return null }
        samples[cursor++] = sample.toDouble()
        if (cursor < samples.size) return null
        cursor = 0
        // Publish an immutable PCM snapshot before the FFT window changes the samples.
        waveform = FloatArray(128) { i -> ((samples[i * 4] + samples[i * 4 + 1] + samples[i * 4 + 2] + samples[i * 4 + 3]) / 4).toFloat().coerceIn(-1f, 1f) }
        skip = (sampleRate / 20 - samples.size).coerceAtLeast(0)
        val real = DoubleArray(512) { samples[it] * (0.5 - 0.5 * cos(2 * PI * it / 511)) }
        val imag = DoubleArray(512)
        var j = 0
        for (i in 1 until 512) {
            var bit = 256
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = real[i]; real[i] = real[j]; real[j] = t }
        }
        var size = 2
        while (size <= 512) {
            for (start in 0 until 512 step size) for (k in 0 until size / 2) {
                val angle = -2 * PI * k / size
                val b = start + k + size / 2
                val a = start + k
                val tr = cos(angle) * real[b] - sin(angle) * imag[b]
                val ti = sin(angle) * real[b] + cos(angle) * imag[b]
                real[b] = real[a] - tr; imag[b] = imag[a] - ti
                real[a] += tr; imag[a] += ti
            }
            size *= 2
        }
        return FloatArray(28) { band ->
            val low = (40.0 * 500.0.pow(band / 28.0) * 512 / sampleRate).toInt().coerceIn(1, 255)
            val high = (40.0 * 500.0.pow((band + 1) / 28.0) * 512 / sampleRate).toInt().coerceIn(low, 255)
            val amplitude = (low..high).maxOf { hypot(real[it], imag[it]) } / 128
            ((20 * log10(amplitude.coerceAtLeast(1e-6)) + 65) / 65).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
