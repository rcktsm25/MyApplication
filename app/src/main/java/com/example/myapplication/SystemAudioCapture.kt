package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Captura de audio del sistema usando AudioPlaybackCapture API (Android 10+).
 * Permite que el osciloscopio muestre audio REAL de Spotify u otras apps.
 *
 * Flujo:
 * 1. El usuario acepta el diálogo de MediaProjection (una sola vez por sesión)
 * 2. Se crea un AudioRecord con AudioPlaybackCapture configuration
 * 3. El audio capturado se procesa en tiempo real para generar spectrum + waveform
 * 4. Los datos se inyectan en AudioRuntime para que el visualizador los use
 */
class SystemAudioCapture(private val context: Context) {

    companion object {
        private const val TAG = "SystemAudioCapture"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4
        val CAPTURE_REQUEST_CODE = 1001
    }

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var captureScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isCapturing = false

    /**
     * Inicia el flujo de captura: solicita permiso MediaProjection al usuario.
     * Debe llamarse desde una Activity para recibir el resultado.
     */
    fun startCaptureRequest(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "AudioPlaybackCapture solo disponible en Android 10+")
            return
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(intent, CAPTURE_REQUEST_CODE)
    }

    /**
     * Maneja el resultado del permiso MediaProjection.
     * Llamar desde Activity.onActivityResult()
     */
    fun handleCaptureResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != CAPTURE_REQUEST_CODE) return false

        if (resultCode == Activity.RESULT_OK && data != null) {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            startAudioCapture()
            return true
        }
        Log.w(TAG, "Permiso de captura de audio denegado por el usuario")
        return false
    }

    /**
     * Inicia la captura de audio del sistema y procesamiento en tiempo real.
     */
    private fun startAudioCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection no disponible")
            return
        }

        captureScope.launch {
            try {
                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()

                val playbackConfig = android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                    .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                    .build()

                audioRecord = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(playbackConfig)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(BUFFER_SIZE)
                    .build()

                audioRecord?.startRecording()
                isCapturing = true
                Log.i(TAG, "Captura de audio del sistema iniciada (${SAMPLE_RATE}Hz, mono, 16-bit)")

                // Buffer para leer audio PCM
                val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder())
                val shortBuffer = buffer.asShortBuffer()

                while (isCapturing) {
                    shortBuffer.clear()
                    val read = audioRecord?.read(buffer, BUFFER_SIZE, AudioRecord.READ_NON_BLOCKING) ?: 0

                    if (read > 0) {
                        // Procesar audio capturado
                        shortBuffer.position(0)
                        shortBuffer.limit(read / 2) // shorts leídos

                        val samples = FloatArray(read / 2)
                        var sum = 0f
                        for (i in 0 until samples.size) {
                            val sample = shortBuffer.get(i) / 32768f
                            samples[i] = sample
                            sum += sample * sample
                        }

                        // Calcular RMS para nivel de señal
                        val rms = kotlin.math.sqrt(sum / samples.size)

                        // Generar spectrum simulado basado en RMS (FFT real requeriría lib externa)
                        val spectrum = FloatArray(28)
                        for (i in spectrum.indices) {
                            // Simular frecuencias basadas en RMS con variación
                            spectrum[i] = (rms * (1f + kotlin.math.sin(System.currentTimeMillis() / 500.0 + i * 0.5).toFloat() * 0.3f)).coerceIn(0f, 1f)
                        }

                        // Generar waveform desde samples reales
                        val waveform = FloatArray(128)
                        val step = samples.size / waveform.size
                        for (i in waveform.indices) {
                            waveform[i] = samples.getOrNull(i * step) ?: 0f
                        }

                        // Inyectar en AudioRuntime para que el visualizador lo use
                        withContext(Dispatchers.Main) {
                            AudioRuntime.spectrum = spectrum
                            AudioRuntime.waveform = waveform
                        }
                    }

                    delay(20) // 50fps de actualización
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error durante captura de audio: ${e.message}", e)
                stopCapture()
            }
        }
    }

    /**
     * Detiene la captura de audio del sistema.
     */
    fun stopCapture() {
        isCapturing = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        mediaProjection?.stop()
        mediaProjection = null
        captureScope.cancel()
        captureScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Log.i(TAG, "Captura de audio del sistema detenida")
    }

    /**
     * @return true si la captura de audio está activa.
     */
    fun isActive() = isCapturing

    /**
     * Limpia recursos al destruir la app.
     */
    fun destroy() {
        stopCapture()
    }
}

// Desarrollado por rcktsm
