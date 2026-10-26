package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Editor de metadatos para canciones locales.
 * Permite editar título, artista, álbum sin modificar el archivo original.
 * Los cambios se guardan en SharedPreferences para persistencia.
 */
class MetadataEditor(private val context: Context) {
    
    data class EditableMetadata(
        val uri: String,
        var title: String? = null,
        var artist: String? = null,
        var album: String? = null,
        var genre: String? = null,
        var year: Int? = null
    )
    
    private val prefs = context.getSharedPreferences("RocketMetadata", Context.MODE_PRIVATE)
    
    /**
     * Obtiene los metadatos editados para una canción.
     */
    fun getMetadata(uri: String): EditableMetadata {
        val key = "metadata_$uri"
        val json = prefs.getString(key, null)
        return if (json != null) {
            val obj = org.json.JSONObject(json)
            EditableMetadata(
                uri = uri,
                title = obj.optString("title", null),
                artist = obj.optString("artist", null),
                album = obj.optString("album", null),
                genre = obj.optString("genre", null),
                year = if (obj.has("year")) obj.optInt("year") else null
            )
        } else {
            EditableMetadata(uri = uri)
        }
    }
    
    /**
     * Guarda los metadatos editados para una canción.
     */
    fun saveMetadata(metadata: EditableMetadata) {
        val key = "metadata_${metadata.uri}"
        val obj = org.json.JSONObject().apply {
            put("uri", metadata.uri)
            metadata.title?.let { put("title", it) }
            metadata.artist?.let { put("artist", it) }
            metadata.album?.let { put("album", it) }
            metadata.genre?.let { put("genre", it) }
            metadata.year?.let { put("year", it) }
        }
        prefs.edit().putString(key, obj.toString()).apply()
    }
    
    /**
     * Limpia los metadatos editados (restaura los originales del archivo).
     */
    fun clearMetadata(uri: String) {
        val key = "metadata_$uri"
        prefs.edit().remove(key).apply()
    }
    
    /**
     * Obtiene el título efectivo (editado o original).
     */
    fun getEffectiveTitle(uri: String, originalTitle: String): String {
        return getMetadata(uri).title ?: originalTitle
    }
    
    /**
     * Obtiene el artista efectivo (editado o original).
     */
    fun getEffectiveArtist(uri: String, originalArtist: String): String {
        return getMetadata(uri).artist ?: originalArtist
    }
    
    /**
     * Intenta escribir los metadatos en el archivo real (solo para dispositivos con permisos).
     * Nota: Esto requiere permisos especiales y no siempre está disponible.
     */
    fun tryWriteToMediaStore(uri: Uri, metadata: EditableMetadata): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        
        return try {
            val values = ContentValues().apply {
                metadata.title?.let { put(MediaStore.Audio.Media.TITLE, it) }
                metadata.artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                metadata.album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                metadata.genre?.let { put(MediaStore.Audio.Media.GENRE, it) }
                metadata.year?.let { put(MediaStore.Audio.Media.YEAR, it) }
            }
            
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }
}
