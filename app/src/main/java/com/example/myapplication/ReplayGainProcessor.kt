package com.example.myapplication

import android.content.SharedPreferences
import kotlin.math.pow

/**
 * Procesador de ReplayGain para normalización de volumen entre canciones.
 * Lee las etiquetas ReplayGain de los archivos de audio y ajusta el volumen automáticamente.
 */
class ReplayGainProcessor(private val prefs: SharedPreferences) {
    
    companion object {
        const val PREF_REPLAYGAIN_ENABLED = "replaygain_enabled"
        const val PREF_REPLAYGAIN_MODE = "replaygain_mode" // 0: track, 1: album
        const val PREF_REPLAYGAIN_PREGAIN = "replaygain_pregain_db"
        
        // Etiquetas comunes de ReplayGain en archivos de audio
        const val TAG_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
        const val TAG_ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
        const val TAG_TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
        const val TAG_ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"
    }
    
    data class ReplayGainInfo(
        val trackGainDb: Float = 0f,
        val albumGainDb: Float = 0f,
        val trackPeak: Float = 1f,
        val albumPeak: Float = 1f
    )
    
    fun isEnabled(): Boolean = prefs.getBoolean(PREF_REPLAYGAIN_ENABLED, false)
    
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_REPLAYGAIN_ENABLED, enabled).apply()
    }
    
    fun getMode(): Int = prefs.getInt(PREF_REPLAYGAIN_MODE, 0).coerceIn(0, 1)
    
    fun setMode(mode: Int) {
        prefs.edit().putInt(PREF_REPLAYGAIN_MODE, mode.coerceIn(0, 1)).apply()
    }
    
    fun getPreGainDb(): Float = prefs.getFloat(PREF_REPLAYGAIN_PREGAIN, 0f)
    
    fun setPreGainDb(db: Float) {
        prefs.edit().putFloat(PREF_REPLAYGAIN_PREGAIN, db).apply()
    }
    
    /**
     * Calcula el factor de volumen basado en la información de ReplayGain.
     * @param info Información de ReplayGain de la canción
     * @return Factor de volumen (0.0 - 1.0)
     */
    fun calculateVolumeFactor(info: ReplayGainInfo): Float {
        if (!isEnabled()) return 1f
        
        val gainDb = when (getMode()) {
            0 -> info.trackGainDb // Track mode
            1 -> info.albumGainDb // Album mode
            else -> 0f
        } + getPreGainDb()
        
        // Convertir dB a factor lineal
        val factor = 10f.pow(gainDb / 20f)
        
        // Prevenir clipping usando el peak
        val peak = when (getMode()) {
            0 -> info.trackPeak
            1 -> info.albumPeak
            else -> 1f
        }
        
        return (factor * peak).coerceIn(0f, 1f)
    }
    
    /**
     * Extrae la información de ReplayGain de las etiquetas del archivo.
     * Nota: Esto requiere que los metadatos del archivo ya estén cargados.
     */
    fun extractFromMetadata(metadata: Map<String, String>): ReplayGainInfo {
        return ReplayGainInfo(
            trackGainDb = metadata[TAG_TRACK_GAIN]?.parseFloatWithUnit() ?: 0f,
            albumGainDb = metadata[TAG_ALBUM_GAIN]?.parseFloatWithUnit() ?: 0f,
            trackPeak = metadata[TAG_TRACK_PEAK]?.toFloatOrNull() ?: 1f,
            albumPeak = metadata[TAG_ALBUM_PEAK]?.toFloatOrNull() ?: 1f
        )
    }
    
    private fun String.parseFloatWithUnit(): Float? {
        // Formato común: "-6.5 dB" o "-6.5"
        return this.replace(Regex("[^\\d.-]"), "").toFloatOrNull()
    }
}
