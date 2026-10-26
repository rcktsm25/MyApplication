package com.example.myapplication

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.util.UnstableApi

/**
 * Widget de pantalla de inicio para RocketPlayer.
 * Muestra controles de reproducción básicos con estética Winamp.
 */
@UnstableApi
class RocketPlayerWidget : AppWidgetProvider() {
    
    companion object {
        const val ACTION_PLAY = "rocketplayer.WIDGET_PLAY"
        const val ACTION_PAUSE = "rocketplayer.WIDGET_PAUSE"
        const val ACTION_NEXT = "rocketplayer.WIDGET_NEXT"
        const val ACTION_PREV = "rocketplayer.WIDGET_PREV"
        
        fun updateWidget(context: Context, isPlaying: Boolean, title: String, artist: String) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RocketPlayerWidget::class.java)
            val views = RemoteViews(context.packageName, R.layout.widget_rocket_player)
            
            views.setTextViewText(R.id.widget_title, title.ifBlank { "RocketPlayer" })
            views.setTextViewText(R.id.widget_artist, artist.ifBlank { "Tu música, a tu manera" })
            views.setImageViewResource(
                R.id.widget_btn_play,
                if (isPlaying) R.drawable.ic_pro_pause else R.drawable.ic_pro_play
            )
            
            // Configurar intents
            val playIntent = Intent(context, RocketPlayerWidget::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            }
            views.setOnClickPendingIntent(
                R.id.widget_btn_play,
                PendingIntent.getBroadcast(context, 0, playIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            
            val nextIntent = Intent(context, RocketPlayerWidget::class.java).apply { action = ACTION_NEXT }
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                PendingIntent.getBroadcast(context, 1, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            
            val prevIntent = Intent(context, RocketPlayerWidget::class.java).apply { action = ACTION_PREV }
            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                PendingIntent.getBroadcast(context, 2, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 3, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            
            appWidgetManager.updateAppWidget(componentName, views)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY -> {
                // Enviar broadcast al servicio para reproducir
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    action = "WIDGET_PLAY"
                }
                context.startService(serviceIntent)
            }
            ACTION_PAUSE -> {
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    action = "WIDGET_PAUSE"
                }
                context.startService(serviceIntent)
            }
            ACTION_NEXT -> {
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    action = "WIDGET_NEXT"
                }
                context.startService(serviceIntent)
            }
            ACTION_PREV -> {
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    action = "WIDGET_PREV"
                }
                context.startService(serviceIntent)
            }
        }
    }
    
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_rocket_player)
            views.setTextViewText(R.id.widget_title, "RocketPlayer")
            views.setTextViewText(R.id.widget_artist, "Toca para abrir")
            
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
