package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioDspTest {
    @Test fun bypassPreservesSamplesAndStereoIndependence() {
        val dsp = AudioDsp(48000, 2)
        for (i in 0..500) {
            val sample = (sin(i / 8.0) * 0.7).toFloat()
            assertEquals(sample, dsp.process(sample, 0), 0.000001f)
            assertEquals(-sample, dsp.process(-sample, 1), 0.000001f)
        }
    }
    @Test fun hardBalanceMutesOnlyTheOppositeChannel() {
        val dsp = AudioDsp(44100, 2)
        dsp.configure(DspSettings(balance = 1f))
        assertEquals(0f, dsp.process(0.5f, 0), 0f)
        assertEquals(0.5f, dsp.process(0.5f, 1), 0.000001f)
        dsp.configure(DspSettings(balance = -1f))
        assertEquals(0.5f, dsp.process(0.5f, 0), 0.000001f)
        assertEquals(0f, dsp.process(0.5f, 1), 0f)
    }
    @Test fun sixDbPreampCutApproximatelyHalvesAmplitude() {
        val dsp = AudioDsp(48000, 1)
        dsp.configure(DspSettings(enabled = true, preamp = -6f))
        assertEquals(0.25059f, dsp.process(0.5f, 0), 0.0001f)
    }
    private fun rms(frequency: Double, settings: DspSettings): Double {
        val dsp = AudioDsp(48000, 1).apply { configure(settings) }
        var sum = 0.0
        for (i in 0 until 48000) {
            val sample = (0.1 * sin(2 * PI * frequency * i / 48000)).toFloat()
            val output = dsp.process(sample, 0)
            if (i >= 24000) sum += output * output
        }
        return sqrt(sum / 24000)
    }
    @Test fun bandChangesItsFrequencyWithoutBoostingUnrelatedBass() {
        val gains = List(10) { if (it == 4) 6f else 0f }
        val settings = DspSettings(enabled = true, gains = gains)
        val centerRatio = rms(1000.0, settings) / rms(1000.0, DspSettings())
        val lowRatio = rms(60.0, settings) / rms(60.0, DspSettings())
        assertEquals(10.0.pow(6.0 / 20), centerRatio, 0.02)
        assertEquals(1.0, lowRatio, 0.02)
    }
    @Test fun lowSampleRatesAndMaximumBoostStayFiniteAndBounded() {
        val dsp = AudioDsp(8000, 2).apply { configure(DspSettings(true, List(10) { 12f }, 6f)) }
        for (i in 0..16000) for (channel in 0..1) {
            val output = dsp.process(sin(i.toDouble()).toFloat(), channel)
            assertTrue(output.isFinite()); assertTrue(output in -1f..1f)
        }
    }
    @Test fun spectrumDetectsSoundAndSilence() {
        val silence = AudioSpectrum()
        var quiet: FloatArray? = null
        repeat(512) { quiet = silence.add(0f, 48000) ?: quiet }
        assertTrue(quiet!!.all { it == 0f })
        val sound = AudioSpectrum()
        var loud: FloatArray? = null
        repeat(512) { loud = sound.add(sin(2 * PI * 1000 * it / 48000).toFloat(), 48000) ?: loud }
        assertTrue(loud!!.max() > 0.8f)
        assertTrue(loud!!.all { it.isFinite() && it in 0f..1f })
    }
    @Test fun waveformPreservesPcmPolarityAmplitudeAndPublishedSnapshots() {
        val analyzer = AudioSpectrum()
        repeat(512) { analyzer.add(if (it < 256) 0.75f else -0.5f, 8000) }
        val snapshot = analyzer.waveform
        assertEquals(0.75f, snapshot.max(), 0.00001f)
        assertEquals(-0.5f, snapshot.min(), 0.00001f)
        assertEquals(0.125f, snapshot.average().toFloat(), 0.00001f)
        repeat(512) { analyzer.add(0f, 8000) }
        assertTrue(analyzer.waveform.all { it == 0f })
        assertEquals(0.75f, snapshot.max(), 0.00001f)
        assertNotSame(snapshot, analyzer.waveform)
    }

}
