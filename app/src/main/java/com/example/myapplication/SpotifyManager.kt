package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.client.CallResult
import com.spotify.android.appremote.api.PlayerApi
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.ListItem
import com.spotify.protocol.types.ListItems
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

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
        const val REQUEST_CODE_SPOTIFY_AUTH = 1337
        const val REQUEST_CODE_SPOTIFY_BROWSER_AUTH = 1338

        /**
         * Esquema y host de redirección registrado en el Dashboard de Spotify.
         * Debe coincidir exactamente con lo definido en AndroidManifest.xml.
         */
        const val REDIRECT_URI = "rocketplayer://spotify-callback"

        /**
         * Client ID por defecto o sugerido. Los usuarios pueden sobreescribirlo
         * en el diálogo de configuración de Spotify en la aplicación.
         */
        const val DEFAULT_CLIENT_ID = ""

        fun isValidClientId(clientId: String): Boolean =
            Regex("[a-fA-F0-9]{32}").matches(clientId.trim())

        private const val PREFS_NAME = "SpotifySettings"
        private const val KEY_CLIENT_ID = "spotify_client_id"
    }

    // Instancia activa del control remoto de Spotify
    private var spotifyAppRemote: SpotifyAppRemote? = null

    // Suscripción al estado del reproductor (canción actual, posición, etc.)
    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var connectionGeneration = 0
    private var connecting = false
    private val authorization = SpotifyAuthorizationGate()
    private val connectionCallbacks = mutableListOf<Pair<(() -> Unit)?, ((Throwable) -> Unit)?>>()

    // Callbacks para comunicar eventos a la Activity o vistas
    var onPlayerStateChanged: ((PlayerState) -> Unit)? = null
    var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    // SharedPreferences para persistir el Client ID personalizado del usuario
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Guarda un nuevo Client ID en SharedPreferences cifrado para futuras conexiones.
     */
    fun saveClientId(clientId: String) {
        require(isValidClientId(clientId)) { context.getString(R.string.spotify_invalid_client_id) }
        if (clientId.trim() != getClientId()) disconnect()
        val encrypted = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, spotifyKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(clientId.trim().toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        }.getOrNull()
        prefs.edit().putString(KEY_CLIENT_ID, encrypted ?: clientId.trim()).apply()
    }

    /**
     * Obtiene el Client ID almacenado o devuelve el por defecto.
     */
    fun getClientId(): String {
        val raw = prefs.getString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID).orEmpty()
            .takeUnless { it == "YOUR_SPOTIFY_CLIENT_ID" }.orEmpty()
        if (raw.isBlank()) return DEFAULT_CLIENT_ID
        return runCatching {
            val decoded = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, spotifyKey(), GCMParameterSpec(128, decoded.copyOfRange(0, 12)))
            String(cipher.doFinal(decoded.copyOfRange(12, decoded.size)), Charsets.UTF_8)
        }.getOrNull() ?: raw
    }

    private fun spotifyKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "rocket_spotify"
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    /** Fingerprints of the installed APK, including release builds and other developers' keys. */
    @Suppress("DEPRECATION")
    fun getSigningFingerprints(): String {
        return runCatching {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                when {
                    signingInfo == null -> emptyArray()
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners.orEmpty()
                    else -> signingInfo.signingCertificateHistory.orEmpty()
                }
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNATURES
                ).signatures.orEmpty()
            }
            signatures.joinToString("\n") { signature ->
                MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
                    .joinToString(":") { "%02X".format(Locale.ROOT, it.toInt() and 0xff) }
            }.ifEmpty { context.getString(R.string.spotify_fingerprint_unavailable) }
        }.getOrElse { context.getString(R.string.spotify_fingerprint_unavailable) }
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

    /** Browse the installed Spotify client's library, including its returned folder hierarchy.
     * The library entry point is supported by the client, but can vary between Spotify versions.
     * Descendants always use IDs supplied by Spotify; no guessed playlist/folder IDs.
     */
    fun browseLibrary(
        parent: ListItem?, offset: Int,
        onResult: (ListItems) -> Unit, onFailure: (Throwable) -> Unit
    ) {
        val remote = spotifyAppRemote
        if (remote?.isConnected != true) {
            onFailure(IllegalStateException(context.getString(R.string.spotify_disconnected_action)))
            return
        }
        val item = parent ?: ListItem("com.spotify.your-library", "com.spotify.your-library", null,
            context.getString(R.string.spotify_library), "", false, true)
        try {
            remote.contentApi.getChildrenOfItem(item, 50, offset)
                .setResultCallback { result ->
                    if (remote === spotifyAppRemote && remote.isConnected) onResult(result)
                    else onFailure(IllegalStateException(context.getString(R.string.spotify_disconnected_action)))
                }.setErrorCallback { onFailure(it) }
        } catch (error: Exception) {
            onFailure(error)
        }
    }

    fun playLibraryItem(item: ListItem) {
        val remote = spotifyAppRemote
        if (remote?.isConnected != true) {
            onError?.invoke(IllegalStateException(context.getString(R.string.spotify_disconnected_action)))
            return
        }
        if (!item.playable) return
        try {
            remote.contentApi.playContentItem(item).setErrorCallback { onError?.invoke(it) }
        } catch (error: Exception) {
            onError?.invoke(error)
        }
    }

    /**
     * Inicia el proceso de conexión con la aplicación de Spotify.
     * Si Spotify requiere autorización explícita del usuario, lanzará
     * automáticamente el flujo de diálogo de autorización de Spotify.
     *
     * @param activity Actividad opcional para lanzar el flujo de autorización visual si es necesario.
     * @param onSuccess Callback ejecutado al conectar exitosamente.
     * @param onFailure Callback ejecutado si la conexión falla o el usuario no autoriza.
     */
    fun connect(
        activity: Activity? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        val clientId = getClientId()

        val validationError = when {
            !isValidClientId(clientId) -> IllegalArgumentException(context.getString(R.string.spotify_invalid_client_id))
            !isSpotifyInstalled() -> IllegalStateException(context.getString(R.string.spotify_not_installed))
            else -> null
        }
        if (validationError != null) {
            onConnectionStatusChanged?.invoke(false)
            onFailure?.invoke(validationError)
            onError?.invoke(validationError)
            return
        }

        // Si ya está conectado, no volver a conectar
        if (isConnected()) {
            onSuccess?.invoke()
            onConnectionStatusChanged?.invoke(true)
            return
        }

        connectionCallbacks.add(onSuccess to onFailure)
        if (connecting) return
        connecting = true
        authorization.reset()
        val generation = ++connectionGeneration
        startConnection(activity, generation, showAuthView = true)
    }

    private fun startConnection(activity: Activity?, generation: Int, showAuthView: Boolean) {
        val connectionParams = ConnectionParams.Builder(getClientId())
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(showAuthView)
            .build()

        Log.d(TAG, "Iniciando conexión con Spotify")

        try {
            SpotifyAppRemote.connect(
                activity ?: context,
                connectionParams,
                object : Connector.ConnectionListener {
                    override fun onConnected(appRemote: SpotifyAppRemote) {
                        if (generation != connectionGeneration) {
                            SpotifyAppRemote.disconnect(appRemote)
                            return
                        }
                        connecting = false
                        spotifyAppRemote = appRemote
                        Log.d(TAG, "¡Conexión con Spotify establecida exitosamente!")

                        // Suscribirse a cambios en el estado de reproducción
                        subscribeToPlayerState()

                        val callbacks = connectionCallbacks.toList()
                        connectionCallbacks.clear()
                        onConnectionStatusChanged?.invoke(true)
                        callbacks.forEach { it.first?.invoke() }
                    }

                    override fun onFailure(throwable: Throwable) {
                        if (generation != connectionGeneration) return
                        if (requiresAuthorization(throwable) && activity != null && authorization.request()) {
                            try {
                                val request = AuthorizationRequest.Builder(
                                    getClientId(), AuthorizationResponse.Type.CODE, REDIRECT_URI
                                ).setScopes(arrayOf("app-remote-control")).build()
                                // Keep the original success callbacks until consent and retry finish.
                                AuthorizationClient.openLoginActivity(activity, REQUEST_CODE_SPOTIFY_AUTH, request)
                            } catch (error: Exception) {
                                failConnection(error)
                            }
                            return
                        }
                        failConnection(throwable)
                    }
                }
            )
        } catch (error: Exception) {
            if (generation == connectionGeneration) failConnection(error)
        }
    }

    fun onAuthorizationResult(activity: Activity, resultCode: Int, data: Intent?) {
        if (!connecting || !authorization.consumeResult()) return
        val response = AuthorizationClient.getResponse(resultCode, data)
        when (response.type) {
            AuthorizationResponse.Type.CODE,
            AuthorizationResponse.Type.TOKEN -> {
                // App Remote uses the consent grant, not a Web API access token.
                // No client secret or token exchange is needed to control the installed app.
                startConnection(activity, connectionGeneration, showAuthView = true)
            }
            AuthorizationResponse.Type.ERROR -> {
                if (authorization.requestBrowser(response.error)) {
                    try {
                        activity.startActivityForResult(
                            Intent(activity, SpotifyBrowserAuthActivity::class.java)
                                .putExtra(SpotifyBrowserAuthActivity.EXTRA_CLIENT_ID, getClientId()),
                            REQUEST_CODE_SPOTIFY_BROWSER_AUTH
                        )
                    } catch (error: Exception) {
                        failConnection(error)
                    }
                } else {
                    failConnection(IllegalStateException("Autorización Spotify: ${response.error}"))
                }
            }
            else -> failConnection(IllegalStateException(context.getString(R.string.spotify_auth_cancelled)))
        }
    }

    fun onBrowserAuthorizationResult(activity: Activity, resultCode: Int, data: Intent?) {
        if (!connecting || !authorization.consumeBrowserResult()) return
        when {
            resultCode != Activity.RESULT_OK ->
                failConnection(IllegalStateException(context.getString(R.string.spotify_auth_cancelled)))
            data?.getBooleanExtra(SpotifyBrowserAuthActivity.EXTRA_APPROVED, false) == true ->
                startConnection(activity, connectionGeneration, showAuthView = true)
            else -> failConnection(IllegalStateException(
                "Autorización Spotify: ${data?.getStringExtra(SpotifyBrowserAuthActivity.EXTRA_ERROR).orEmpty()}"
            ))
        }
    }

    private fun requiresAuthorization(error: Throwable): Boolean =
        error is com.spotify.android.appremote.api.error.UserNotAuthorizedException ||
            error is com.spotify.android.appremote.api.error.AuthenticationFailedException ||
            error.message?.contains("Explicit user authorization", ignoreCase = true) == true ||
            error.message?.contains("user_not_authorized", ignoreCase = true) == true

    private fun failConnection(error: Throwable) {
        Log.e(TAG, "Error al conectar con Spotify", error)
        connecting = false
        authorization.reset()
        playerStateSubscription?.cancel()
        playerStateSubscription = null
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
        spotifyAppRemote = null
        val callbacks = connectionCallbacks.toList()
        connectionCallbacks.clear()
        val failure = if (requiresAuthorization(error)) {
            IllegalStateException(context.getString(R.string.spotify_authorization_failed), error)
        } else error
        onConnectionStatusChanged?.invoke(false)
        callbacks.forEach { it.second?.invoke(failure) }
        onError?.invoke(failure)
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
        playerStateSubscription?.setErrorCallback { error -> onError?.invoke(error) }
    }

    /**
     * Desconecta el control remoto y libera los recursos de IPC.
     */
    fun disconnect() {
        connectionGeneration++
        connecting = false
        authorization.reset()
        connectionCallbacks.clear()
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
    fun play(uriOrUrl: String, activity: Activity? = null) {
        val uri = SpotifyResource.toUri(uriOrUrl)
        if (uri == null) {
            onError?.invoke(IllegalArgumentException(context.getString(R.string.spotify_invalid_resource)))
            return
        }
        if (!isConnected()) {
            connect(activity = activity, onSuccess = {
                command { play(uri) }
            })
        } else {
            command { play(uri) }
        }
    }

    /**
     * Reanuda la reproducción actual en Spotify.
     */
    fun resume() {
        command { resume() }
    }

    /**
     * Pausa la reproducción actual en Spotify.
     */
    fun pause() {
        // Pausing Spotify while switching to radio is unnecessary when disconnected.
        if (isConnected()) command { pause() }
    }

    /**
     * Salta a la siguiente canción en la cola de Spotify.
     */
    fun skipNext() {
        command { skipNext() }
    }

    /**
     * Vuelve a la canción anterior en Spotify.
     */
    fun skipPrevious() {
        command { skipPrevious() }
    }

    /**
     * Cambia la posición temporal (Seek) de la canción actual en milisegundos.
     */
    fun seekTo(positionMs: Long) {
        command { seekTo(positionMs) }
    }

    /**
     * Activa o desactiva el modo aleatorio (Shuffle) en Spotify.
     */
    fun setShuffle(enabled: Boolean) {
        command { setShuffle(enabled) }
    }

    /**
     * Alterna el modo de repetición de canciones en Spotify (Off -> All -> One).
     */
    fun toggleRepeat() {
        command { toggleRepeat() }
    }

    /**
     * Convierte enlaces web de Spotify a URIs reconocidas por la app nativa:
     * Ejemplo:
     * - Entrada: "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"
     * - Salida:  "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"
     */
    fun convertUrlToUri(urlOrUri: String): String {
        return requireNotNull(SpotifyResource.toUri(urlOrUri)) {
            context.getString(R.string.spotify_invalid_resource)
        }
    }

    private fun command(action: PlayerApi.() -> CallResult<*>) {
        val remote = spotifyAppRemote
        if (remote?.isConnected != true) {
            onError?.invoke(IllegalStateException(context.getString(R.string.spotify_disconnected_action)))
            return
        }
        try {
            action(remote.playerApi).setErrorCallback { error -> onError?.invoke(error) }
        } catch (error: Exception) {
            onError?.invoke(error)
        }
    }
}
