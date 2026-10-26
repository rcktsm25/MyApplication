package com.example.myapplication

import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

/**
 * Gestiona el crossfade entre canciones para reproducción local.
 * Permite transiciones suaves de 2-10 segundos entre pistas.
 */
class CrossfadeManager(
    private val prefs: SharedPreferences,
    private val getPlayer: () -> Player?
) {
    private var isCrossfading = false
    private var crossfadeRunnable: Runnable? = null
    
    companion object {
        const val PREF_CROSSFADE_ENABLED = "crossfade_enabled"
        const val PREF_CROSSFADE_DURATION = "crossfade_duration_seconds"
        const val DEFAULT_CROSSFADE_DURATION = 5
        const val MIN_CROSSFADE_DURATION = 2
        const val MAX_CROSSFADE_DURATION = 10
    }
    
    fun isEnabled(): Boolean = prefs.getBoolean(PREF_CROSSFADE_ENABLED, false)
    
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_CROSSFADE_ENABLED, enabled).apply()
    }
    
    fun getDurationSeconds(): Int = prefs.getInt(PREF_CROSSFADE_DURATION, DEFAULT_CROSSFADE_DURATION)
        .coerceIn(MIN_CROSSFADE_DURATION, MAX_CROSSFADE_DURATION)
    
    fun setDurationSeconds(seconds: Int) {
        prefs.edit().putInt(PREF_CROSSFADE_DURATION, seconds.coerceIn(MIN_CROSSFADE_DURATION, MAX_CROSSFADE_DURATION)).apply()
    }
    
    /**
     * Verifica si debe iniciar el crossfade basado en la posición actual.
     * Llama este método periódicamente durante la reproducción.
     */
    fun checkAndStartCrossfade() {
        if (!isEnabled() || isCrossfading) return
        
        val player = getPlayer() ?: return
        if (player.playbackState != Player.STATE_READY || !player.playWhenReady) return
        
        val duration = player.duration
        val position = player.currentPosition
        val crossfadeMs = getDurationSeconds() * 1000L
        
        // Iniciar crossfade cuando faltan X segundos para el final
        if (duration > 0 && position >= duration - crossfadeMs && player.repeatMode != Player.REPEAT_MODE_ONE) {
            startCrossfade()
        }
    }
    
    private fun startCrossfade() {
        isCrossfading = true
        val player = getPlayer() ?: run { isCrossfading = false; return }
        
        // Fade out volumen gradualmente
        val durationMs = getDurationSeconds() * 1000L
        val steps = 20
        val stepDuration = durationMs / steps
        val volumeStep = 1f / steps
        
        var currentStep = 0
        crossfadeRunnable = object : Runnable {
            override fun run() {
                currentStep++
                val newVolume = (1f - (currentStep * volumeStep)).coerceIn(0f, 1f)
                player.volume = newVolume
                
                if (currentStep < steps) {
                    player.addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                                // Nueva canción empezó, restaurar volumen
                                player.volume = 1f
                                isCrossfading = false
                                player.removeListener(this)
                            }
                        }
                    })
                } else {
                    player.volume = 1f
                    isCrossfading = false
                }
            }
        }
        
        // Programar el fade
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        for (i in 1..steps) {
            handler.postDelayed({ crossfadeRunnable?.run() }, i * stepDuration)
        }
    }
    
    fun stopCrossfade() {
        isCrossfading = false
        crossfadeRunnable = null
        getPlayer()?.volume = 1f
    }
}
