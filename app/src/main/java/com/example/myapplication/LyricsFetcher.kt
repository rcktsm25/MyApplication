package com.example.myapplication

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Buscador de letras de canciones usando la API de LRCLib (gratuita, sin API key).
 * Soporta búsqueda sincronizada (time-tagged) y letras estáticas.
 */
class LyricsFetcher(private val context: Context) {
    
    data class Lyrics(
        val text: String,
        val isSynchronized: Boolean = false,
        val lines: List<LyricLine> = emptyList()
    )
    
    data class LyricLine(
        val timeMs: Long,
        val text: String
    )
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Busca la letra de una canción.
     * @param title Título de la canción
     * @param artist Artista (opcional pero recomendado)
     * @param durationMs Duración en milisegundos (opcional, mejora la precisión)
     */
    fun fetch(
        title: String,
        artist: String? = null,
        durationMs: Long? = null,
        onComplete: (Lyrics?) -> Unit
    ) {
        coroutineScope.launch {
            val lyrics = try {
                searchLyrics(title, artist, durationMs)
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                onComplete(lyrics)
            }
        }
    }
    
    private suspend fun searchLyrics(title: String, artist: String?, durationMs: Long?): Lyrics? {
        val query = buildQuery(title, artist)
        val url = URL("https://lrclib.net/api/search?track_name=$query")
        
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "RocketPlayer/1.0 (Android)")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        return try {
            if (connection.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                parseResponse(response, durationMs)
            } else null
        } finally {
            connection.disconnect()
        }
    }
    
    private fun buildQuery(title: String, artist: String?): String {
        val cleanTitle = title.replace(Regex("\\.(mp3|wav|flac|m4a|aac|ogg|opus)$", RegexOption.IGNORE_CASE), "")
        val query = if (artist != null && artist != "Artista desconocido") {
            "$cleanTitle $artist"
        } else {
            cleanTitle
        }
        return Uri.encode(query)
    }
    
    private fun parseResponse(json: String, durationMs: Long?): Lyrics? {
        val jsonArray = org.json.JSONArray(json)
        if (jsonArray.length() == 0) return null
        
        // Buscar la mejor coincidencia
        val bestMatch = (0 until jsonArray.length()).map { jsonArray.getJSONObject(it) }
            .filter { obj ->
                // Filtrar por duración si está disponible
                durationMs?.let { duration ->
                    val trackDuration = obj.optLong("duration", 0) * 1000
                    val diff = Math.abs(trackDuration - duration)
                    diff < 10000 // Tolerancia de 10 segundos
                } ?: true
            }
            .minByOrNull { obj ->
                // Priorizar letras sincronizadas
                val hasSync = obj.optString("syncedLyrics", "").isNotEmpty()
                if (hasSync) 0 else 1
            } ?: return null
        
        val syncedLyrics = bestMatch.optString("syncedLyrics", "")
        val plainLyrics = bestMatch.optString("plainLyrics", "")
        
        return if (syncedLyrics.isNotEmpty()) {
            val lines = parseSyncedLyrics(syncedLyrics)
            Lyrics(syncedLyrics, isSynchronized = true, lines = lines)
        } else if (plainLyrics.isNotEmpty()) {
            Lyrics(plainLyrics, isSynchronized = false)
        } else null
    }
    
    private fun parseSyncedLyrics(synced: String): List<LyricLine> {
        val pattern = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        return synced.lines().mapNotNull { line ->
            val match = pattern.matchEntire(line.trim()) ?: return@mapNotNull null
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val milliseconds = match.groupValues[3].padEnd(3, '0').take(3).toLong()
            val text = match.groupValues[4].trim()
            val timeMs = minutes * 60000 + seconds * 1000 + milliseconds
            LyricLine(timeMs, text)
        }
    }
    
    fun cancel() {
        coroutineScope.cancel()
    }
}
