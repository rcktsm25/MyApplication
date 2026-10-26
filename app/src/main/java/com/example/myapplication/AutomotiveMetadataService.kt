package com.example.myapplication

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Servicio para Android Auto / Android Automotive.
 * Permite que el sistema detecte la app como reproductor de música compatible.
 * 
 * Nota: Para Android Auto completo, se requiere configuración adicional en AndroidManifest.xml.
 * Ver comentarios al final de este archivo.
 * 
 * El PlaybackService ya tiene MediaSession configurado, por lo que Android Auto
 * detectará automáticamente la app cuando esté correctamente configurado.
 */
class AutomotiveMetadataService : MediaSessionService() {
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Delegar al PlaybackService principal
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
}

/**
 * INSTRUCCIONES PARA COMPLETAR SOPORTE ANDROID AUTO:
 * 
 * 1. Agrega al AndroidManifest.xml dentro de <application>:
 * 
 * <service
 *     android:name=".AutomotiveMetadataService"
 *     android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.media.browse.MediaBrowserService"/>
 *     </intent-filter>
 * </service>
 * 
 * <meta-data
 *     android:name="com.google.android.gms.car.application"
 *     android:resource="@xml/automotive_app_desc"/>
 * 
 * 2. Crea res/xml/automotive_app_desc.xml:
 * 
 * <?xml version="1.0" encoding="utf-8"?>
 * <automotiveApp>
 *     <uses name="media"/>
 * </automotiveApp>
 * 
 * 3. El PlaybackService ya tiene MediaSession configurado, por lo que
 *    Android Auto detectará automáticamente la app.
 */
