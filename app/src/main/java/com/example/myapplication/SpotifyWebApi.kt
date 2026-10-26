package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Read-only catalog access. Playback remains in the official Spotify app. */
class SpotifyWebApi(context: Context) {
    private val context = context.applicationContext
    private val tokenFile = java.io.File(this.context.noBackupFilesDir, "spotify_catalog.enc")
    class ApiError(val status: Int, message: String) : Exception(message)
    data class Page(val tracks: List<Track>, val nextOffset: Int?)

    companion object {
        private val tokenLock = Any()
        const val STATE_PREFIX = "rocket-catalog-"
        fun challenge(verifier: String): String = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun isAuthorized(): Boolean = synchronized(tokenLock) { readToken()?.optString("refresh_token").orEmpty().isNotBlank() }
    fun disconnect() = synchronized(tokenLock) { tokenFile.delete(); Unit }

    fun exchange(code: String, verifier: String, clientId: String) = synchronized(tokenLock) {
        val token = request("https://accounts.spotify.com/api/token", form = mapOf(
            "grant_type" to "authorization_code", "code" to code, "redirect_uri" to SpotifyManager.REDIRECT_URI,
            "client_id" to clientId, "code_verifier" to verifier))
        require(token.optString("access_token").isNotBlank()) { "Spotify no devolvió un permiso válido." }
        token.put("expires_at", System.currentTimeMillis() + token.optLong("expires_in", 3600) * 1000)
        token.put("client_id", clientId)
        saveToken(token)
    }

    fun songs(query: String, offset: Int): Page {
        val path = if (query.isBlank()) "/me/tracks?limit=50&offset=$offset"
            else "/search?type=track&limit=10&offset=$offset&q=${encode(query)}"
        var token = accessToken(false)
        val response = try { request("https://api.spotify.com/v1$path", token) }
            catch (error: ApiError) {
                if (error.status != 401) throw error
                token = accessToken(true)
                request("https://api.spotify.com/v1$path", token)
            }
        val page = if (query.isBlank()) response else response.getJSONObject("tracks")
        val items = page.optJSONArray("items") ?: org.json.JSONArray()
        val tracks = (0 until items.length()).mapNotNull { i ->
            val entry = items.optJSONObject(i) ?: return@mapNotNull null
            val track = if (query.isBlank()) entry.optJSONObject("track") else entry
            track ?: return@mapNotNull null
            val uri = track.optString("uri")
            if (!uri.startsWith("spotify:track:") || track.optBoolean("is_local") ||
                (track.has("is_playable") && !track.optBoolean("is_playable"))) return@mapNotNull null
            val artists = track.optJSONArray("artists")
            val artist = (0 until (artists?.length() ?: 0)).mapNotNull { artists?.optJSONObject(it)?.optString("name") }.joinToString(", ")
            Track(track.optString("name", "Canción"), artist, uri, track.optLong("duration_ms", 0))
        }
        val hasNext = !page.isNull("next") && page.optString("next").isNotBlank() && items.length() > 0
        return Page(tracks, if (hasNext) offset + items.length() else null)
    }

    private fun accessToken(force: Boolean): String = synchronized(tokenLock) {
        var token = readToken() ?: throw ApiError(401, "Conecta tu biblioteca de Spotify para ver las canciones.")
        val client = SpotifyManager(context).getClientId()
        if (token.optString("client_id") != client) {
            disconnect(); throw ApiError(401, "La configuración cambió. Vuelve a conectar Spotify.")
        }
        if (force || token.optLong("expires_at") < System.currentTimeMillis() + 60000) {
            val refresh = token.optString("refresh_token")
            val updated = try { request("https://accounts.spotify.com/api/token", form = mapOf(
                "grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to client)) }
            catch (error: ApiError) { if (error.status == 400 || error.status == 401) disconnect(); throw error }
            if (updated.optString("refresh_token").isBlank()) updated.put("refresh_token", refresh)
            updated.put("client_id", client)
            updated.put("expires_at", System.currentTimeMillis() + updated.optLong("expires_in", 3600) * 1000)
            saveToken(updated); token = updated
        }
        token.getString("access_token")
    }

    private fun request(url: String, token: String? = null, form: Map<String, String>? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000; connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (form != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(form.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = when (status) {
                    400, 401 -> "El permiso de Spotify caducó o no es válido. Vuelve a conectar la biblioteca."
                    403 -> "Spotify no permite acceder al catálogo con esta configuración. Comprueba Web API y el acceso de tu cuenta en Spotify Developer Dashboard."
                    429 -> "Spotify pide esperar ${connection.getHeaderField("Retry-After") ?: "unos"} segundos antes de volver a buscar."
                    else -> "Spotify no respondió correctamente (HTTP $status). Inténtalo de nuevo."
                }
                throw ApiError(status, message)
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("rocket_catalog", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("rocket_catalog", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun readToken(): JSONObject? = runCatching {
        if (!tokenFile.exists()) return null
        val bytes = tokenFile.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }.getOrNull()
    private fun saveToken(token: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val temporary = java.io.File(tokenFile.parentFile, "spotify_catalog.tmp")
        temporary.writeBytes(cipher.iv + cipher.doFinal(token.toString().toByteArray(Charsets.UTF_8)))
        check(temporary.renameTo(tokenFile)) { "No se pudo guardar el permiso de Spotify." }
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
