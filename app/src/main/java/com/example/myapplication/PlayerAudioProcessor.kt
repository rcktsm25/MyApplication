package com.example.myapplication

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class PlayerAudioProcessor : BaseAudioProcessor() {
    private var dsp: AudioDsp? = null
    private var spectrum = AudioSpectrum()
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        dsp = AudioDsp(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        AudioRuntime.sampleRate = inputAudioFormat.sampleRate
        AudioRuntime.channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val processor = dsp ?: return
        processor.configure(AudioRuntime.settings)
        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        while (inputBuffer.remaining() >= inputAudioFormat.channelCount * 2) {
            var mono = 0f
            for (channel in 0 until inputAudioFormat.channelCount) {
                val value = processor.process(inputBuffer.short / 32768f, channel)
                mono += value
                output.putShort((value * 32768).toInt().coerceIn(-32768, 32767).toShort())
            }
            spectrum.add(mono / inputAudioFormat.channelCount, inputAudioFormat.sampleRate)?.let { AudioRuntime.spectrum = it; AudioRuntime.waveform = spectrum.waveform }
        }
        output.flip()
    }
    override fun onFlush() { dsp?.reset(); spectrum = AudioSpectrum(); AudioRuntime.spectrum = FloatArray(28); AudioRuntime.waveform = FloatArray(128) }
    override fun onReset() { dsp = null; AudioRuntime.sampleRate = 0; AudioRuntime.channels = 0 }
}
