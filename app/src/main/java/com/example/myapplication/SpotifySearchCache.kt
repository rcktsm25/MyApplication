package com.example.myapplication

import android.util.LruCache

/**
 * Caché en memoria para resultados de búsqueda de Spotify.
 * Evita requests repetidos a la API cuando el usuario busca lo mismo.
 * TTL de 5 minutos por entrada, máximo 20 entradas en memoria.
 */
class SpotifySearchCache {

    data class CacheEntry(
        val tracks: List<Track>,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Máximo 20 búsquedas cacheadas, TTL de 5 minutos
    private val cache = LruCache<String, CacheEntry>(20)
    private val ttlMs = 5 * 60 * 1000L

    /**
     * Obtiene el resultado cacheado si existe y no ha expirado.
     * @param query Término de búsqueda
     * @return Lista de tracks o null si no hay caché válido
     */
    fun get(query: String): List<Track>? {
        val key = normalizeQuery(query)
        val entry = cache.get(key) ?: return null
        if (System.currentTimeMillis() - entry.timestamp > ttlMs) {
            cache.remove(key)
            return null
        }
        return entry.tracks
    }

    /**
     * Guarda un resultado de búsqueda en la caché.
     * @param query Término de búsqueda
     * @param tracks Lista de tracks encontrada
     */
    fun put(query: String, tracks: List<Track>) {
        val key = normalizeQuery(query)
        if (tracks.isNotEmpty()) {
            cache.put(key, CacheEntry(tracks))
        }
    }

    /**
     * Limpia toda la caché (útil al desconectar Spotify).
     */
    fun clear() {
        cache.evictAll()
    }

    /** Normaliza el query para evitar duplicados por diferencias de mayúsculas/espacios. */
    private fun normalizeQuery(query: String) = query.trim().lowercase()
}

// Desarrollado por rcktsm
