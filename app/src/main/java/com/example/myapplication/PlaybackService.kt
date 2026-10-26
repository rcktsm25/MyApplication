package com.example.myapplication

import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var saveTicks = 0
    private fun savePosition() {
        val player = mediaSession?.player ?: return
        val uri = player.currentMediaItem?.mediaId ?: return
        if (!uri.startsWith("content://") || player.playbackState != Player.STATE_READY) return
        val prefs = getSharedPreferences("RocketSettings", MODE_PRIVATE)
        val position = player.currentPosition.coerceAtLeast(0)
        if (prefs.getString("resume_local_uri", null) == uri && prefs.getLong("resume_local_position", -1) == position &&
            prefs.getBoolean("resume_local_shuffle", false) == player.shuffleModeEnabled && prefs.getInt("resume_local_repeat", 0) == player.repeatMode) return
        prefs.edit().putString("resume_local_uri", uri).putLong("resume_local_position", position)
            .putBoolean("resume_local_shuffle", player.shuffleModeEnabled).putInt("resume_local_repeat", player.repeatMode).apply()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val timer = object : Runnable {
        override fun run() {
            if (AudioRuntime.sleepUntil > 0 && System.currentTimeMillis() >= AudioRuntime.sleepUntil) {
                mediaSession?.player?.pause()
                AudioRuntime.sleepUntil = 0
            }
            if (++saveTicks % 5 == 0) savePosition()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("RocketSettings", MODE_PRIVATE)
        AudioRuntime.settings = DspSettings(prefs.getBoolean("eq_enabled", false),
            List(10) { prefs.getFloat("eq_$it", 0f) }, prefs.getFloat("preamp", 0f), prefs.getFloat("balance", 0f))
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
                    .setAudioProcessors(arrayOf(PlayerAudioProcessor())).build()
        }
        val player = ExoPlayer.Builder(this, renderers).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { savePosition() }
        })
        handler.post(timer)

        // PendingIntent para abrir MainActivity al tocar la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        savePosition()
        handler.removeCallbacksAndMessages(null)
        AudioRuntime.sleepUntil = 0
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
