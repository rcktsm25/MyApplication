package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * SpotifyManager — Controlador Integral de Integración con Spotify App Remote
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ¿CÓMO FUNCIONA ESTA INTEGRACIÓN?
 * ──────────────────────────────────────────────────────────────────────────────
 * Esta clase utiliza el SDK oficial "Spotify App Remote for Android".
 * A diferencia de hacer peticiones HTTP tradicionales (Web API), el SDK de App
 * Remote se conecta directamente a la aplicación oficial de Spotify instalada
 * en el teléfono del usuario utilizando un canal IPC (Inter-Process Communication).
 *
 * VENTAJAS DE ESTE ENFOQUE:
 * 1. Reproducción Nativa: La música se reproduce a través del motor de audio
 *    oficial de Spotify (mejor calidad de audio y menor consumo de batería).
 * 2. Suscripción en Tiempo Real: Recibimos eventos automáticos cada vez que
 *    cambia la canción, el artista, la carátula, la duración o el estado de pausa.
 * 3. Control Bidireccional: Desde la interfaz retro de RocketPlayer podemos
 *    enviar órdenes (Play, Pause, Siguiente, Anterior, Seek, Shuffle, Repeat)
 *    que Spotify ejecuta de inmediato.
 *
 * REQUISITOS PREVIOS PARA EL DESARROLLADOR:
 * 1. Crear una aplicación en el Dashboard de Desarrolladores de Spotify:
 *    https://developer.spotify.com/dashboard
 * 2. Registrar el Client ID obtenido en los ajustes de esta app.
 * 3. Configurar en el Dashboard de Spotify:
 *    - Package Name: com.example.myapplication
 *    - Redirect URI: rocketplayer://spotify-callback
 *    - Huella SHA-1 de Debug de tu entorno de compilación.
 */
class SpotifyManager(private val context: Context) {

    companion object {
        private const val TAG = "SpotifyManager"

        /**
         * Esquema y host de redirección registrado en el Dashboard de Spotify.
         * Debe coincidir exactamente con lo definido en AndroidManifest.xml.
         */
        const val REDIRECT_URI = "rocketplayer://spotify-callback"

        /**
         * Client ID por defecto o sugerido. Los usuarios pueden sobreescribirlo
         * en el diálogo de configuración de Spotify en la aplicación.
         */
        const val DEFAULT_CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID"

        private const val PREFS_NAME = "SpotifySettings"
        private const val KEY_CLIENT_ID = "spotify_client_id"
    }

    // Instancia activa del control remoto de Spotify
    private var spotifyAppRemote: SpotifyAppRemote? = null

    // Suscripción al estado del reproductor (canción actual, posición, etc.)
    private var playerStateSubscription: Subscription<PlayerState>? = null

    // Callbacks para comunicar eventos a la Activity o vistas
    var onPlayerStateChanged: ((PlayerState) -> Unit)? = null
    var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    // SharedPreferences para persistir el Client ID personalizado del usuario
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Obtiene el Client ID almacenado o devuelve el por defecto.
     */
    fun getClientId(): String {
        return prefs.getString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID) ?: DEFAULT_CLIENT_ID
    }

    /**
     * Guarda un nuevo Client ID en SharedPreferences para futuras conexiones.
     */
    fun saveClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    /**
     * Verifica si la app oficial de Spotify está instalada en el dispositivo móvil.
     * Utiliza el método nativo de validación del SDK de Spotify.
     */
    fun isSpotifyInstalled(): Boolean {
        return SpotifyAppRemote.isSpotifyInstalled(context)
    }

    /**
     * Abre Google Play Store directamente en la ficha de Spotify para que el usuario
     * pueda instalar la aplicación si no la tiene instalada.
     */
    fun openSpotifyInPlayStore() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=com.spotify.music")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Si el dispositivo no tiene Play Store instalada, abrir vía navegador web
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }

    /**
     * Indica si existe actualmente una conexión establecida con Spotify.
     */
    fun isConnected(): Boolean {
        return spotifyAppRemote?.isConnected == true
    }

    /**
     * Inicia el proceso de conexión con la aplicación de Spotify.
     *
     * @param onSuccess Callback ejecutado al conectar exitosamente.
     * @param onFailure Callback ejecutado si la conexión falla o el usuario no autoriza.
     */
    fun connect(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        val clientId = getClientId()

        // Si ya está conectado, no volver a conectar
        if (isConnected()) {
            onSuccess?.invoke()
            onConnectionStatusChanged?.invoke(true)
            return
        }

        // Construir los parámetros de conexión requeridos por el SDK
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true) // Muestra la pantalla de autorización de Spotify si hace falta
            .build()

        Log.d(TAG, "Iniciando conexión con Spotify usando Client ID: $clientId")

        SpotifyAppRemote.connect(
            context,
            connectionParams,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    spotifyAppRemote = appRemote
                    Log.d(TAG, "¡Conexión con Spotify establecida exitosamente!")

                    // Suscribirse a cambios en el estado de reproducción
                    subscribeToPlayerState()

                    onSuccess?.invoke()
                    onConnectionStatusChanged?.invoke(true)
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e(TAG, "Error al conectar con Spotify", throwable)
                    spotifyAppRemote = null
                    onFailure?.invoke(throwable)
                    onError?.invoke(throwable)
                    onConnectionStatusChanged?.invoke(false)
                }
            }
        )
    }

    /**
     * Se suscribe al flujo de eventos del reproductor de Spotify (canción, artista, progreso).
     * Cada vez que Spotify cambia de canción o pausa, este callback se dispara automáticamente.
     */
    private fun subscribeToPlayerState() {
        val playerApi = spotifyAppRemote?.playerApi ?: return

        // Cancelar suscripción anterior si existiera
        playerStateSubscription?.cancel()

        playerStateSubscription = playerApi.subscribeToPlayerState()
        playerStateSubscription?.setEventCallback { playerState ->
            if (playerState != null) {
                // Notificar al listener registrado (MainActivity)
                onPlayerStateChanged?.invoke(playerState)
            }
        }
    }

    /**
     * Desconecta el control remoto y libera los recursos de IPC.
     */
    fun disconnect() {
        playerStateSubscription?.cancel()
        playerStateSubscription = null

        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
        onConnectionStatusChanged?.invoke(false)
        Log.d(TAG, "Spotify desconectado.")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Controles de Reproducción
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Reproduce una URI de Spotify (canción, álbum, artista o playlist).
     * También acepta enlaces web de Spotify (open.spotify.com/...) convirtiéndolos
     * automáticamente al formato de URI nativo (`spotify:track:...`).
     */
    fun play(uriOrUrl: String) {
        val uri = convertUrlToUri(uriOrUrl)
        if (!isConnected()) {
            connect(onSuccess = {
                spotifyAppRemote?.playerApi?.play(uri)
            })
        } else {
            spotifyAppRemote?.playerApi?.play(uri)
        }
    }

    /**
     * Reanuda la reproducción actual en Spotify.
     */
    fun resume() {
        spotifyAppRemote?.playerApi?.resume()
    }

    /**
     * Pausa la reproducción actual en Spotify.
     */
    fun pause() {
        spotifyAppRemote?.playerApi?.pause()
    }

    /**
     * Salta a la siguiente canción en la cola de Spotify.
     */
    fun skipNext() {
        spotifyAppRemote?.playerApi?.skipNext()
    }

    /**
     * Vuelve a la canción anterior en Spotify.
     */
    fun skipPrevious() {
        spotifyAppRemote?.playerApi?.skipPrevious()
    }

    /**
     * Cambia la posición temporal (Seek) de la canción actual en milisegundos.
     */
    fun seekTo(positionMs: Long) {
        spotifyAppRemote?.playerApi?.seekTo(positionMs)
    }

    /**
     * Activa o desactiva el modo aleatorio (Shuffle) en Spotify.
     */
    fun setShuffle(enabled: Boolean) {
        spotifyAppRemote?.playerApi?.setShuffle(enabled)
    }

    /**
     * Alterna el modo de repetición de canciones en Spotify (Off -> All -> One).
     */
    fun toggleRepeat() {
        spotifyAppRemote?.playerApi?.toggleRepeat()
    }

    /**
     * Convierte enlaces web de Spotify a URIs reconocidas por la app nativa:
     * Ejemplo:
     * - Entrada: "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"
     * - Salida:  "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"
     */
    fun convertUrlToUri(urlOrUri: String): String {
        val trimmed = urlOrUri.trim()
        if (trimmed.startsWith("spotify:")) {
            return trimmed
        }

        try {
            val parsed = Uri.parse(trimmed)
            val segments = parsed.pathSegments
            if (segments.size >= 2) {
                val type = segments[0] // "track", "playlist", "album", "artist"
                val id = segments[1]
                return "spotify:$type:$id"
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo parsear URL a URI de Spotify: $urlOrUri", e)
        }

        return trimmed
    }
}
