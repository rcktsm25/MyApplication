package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Gestor de exportación e importación de configuración.
 * Permite hacer backup de temas, ecualizador y preferencias.
 */
class ConfigBackupManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("RocketSettings", Context.MODE_PRIVATE)
    private val themesPrefs = context.getSharedPreferences("RocketThemes", Context.MODE_PRIVATE)
    private val metadataPrefs = context.getSharedPreferences("RocketMetadata", Context.MODE_PRIVATE)
    
    data class BackupData(
        val version: Int = 1,
        val settings: Map<String, Any?>,
        val themes: String?,
        val metadata: Map<String, String>
    )
    
    /**
     * Exporta toda la configuración a un archivo JSON.
     */
    fun exportToFile(file: File): Boolean {
        return try {
            val backup = createBackup()
            val json = serializeBackup(backup)
            file.writeText(json)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Importa configuración desde un archivo JSON.
     */
    fun importFromFile(file: File): Boolean {
        return try {
            val json = file.readText()
            val backup = deserializeBackup(json)
            restoreBackup(backup)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Exporta configuración a JSON string.
     */
    fun exportToJson(): String {
        val backup = createBackup()
        return serializeBackup(backup)
    }
    
    /**
     * Importa configuración desde JSON string.
     */
    fun importFromJson(json: String): Boolean {
        return try {
            val backup = deserializeBackup(json)
            restoreBackup(backup)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun createBackup(): BackupData {
        val settings = mutableMapOf<String, Any?>()
        
        // Exportar preferencias de apariencia
        val appearanceKeys = listOf(
            "display_color", "custom_accent", "background_color", "custom_background",
            "visualizer_style", "visualizer_response", "spectrum", "visualizer_peaks",
            "visualizer_multicolor", "visualizer_grid", "keep_screen", "playback_speed"
        )
        appearanceKeys.forEach { key ->
            when {
                prefs.contains(key) -> {
                    val value = prefs.all[key]
                    settings[key] = value
                }
            }
        }
        
        // Exportar ecualizador
        val eqKeys = listOf("eq_enabled", "preamp", "balance") + 
            (0 until 10).map { "eq_$it" }
        eqKeys.forEach { key ->
            if (prefs.contains(key)) {
                settings[key] = prefs.all[key]
            }
        }
        
        // Exportar temas
        val themes = themesPrefs.getString("appearance_themes", null)
        
        // Exportar metadatos editados
        val metadata = metadataPrefs.all.mapValues { it.value.toString() }
        
        return BackupData(
            version = 1,
            settings = settings,
            themes = themes,
            metadata = metadata
        )
    }
    
    private fun restoreBackup(backup: BackupData) {
        // Restaurar configuración
        prefs.edit().apply {
            backup.settings.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }
        }.apply()
        
        // Restaurar temas
        backup.themes?.let {
            themesPrefs.edit().putString("appearance_themes", it).apply()
        }
        
        // Restaurar metadatos
        metadataPrefs.edit().apply {
            backup.metadata.forEach { (key, value) ->
                putString(key, value)
            }
        }.apply()
    }
    
    private fun serializeBackup(backup: BackupData): String {
        val json = JSONObject()
        json.put("version", backup.version)
        
        // Settings
        val settingsObj = JSONObject()
        backup.settings.forEach { (key, value) ->
            value?.let { settingsObj.put(key, it) }
        }
        json.put("settings", settingsObj)
        
        // Themes
        backup.themes?.let { json.put("themes", it) }
        
        // Metadata
        val metadataObj = JSONObject()
        backup.metadata.forEach { (key, value) ->
            metadataObj.put(key, value)
        }
        json.put("metadata", metadataObj)
        
        return json.toString(2)
    }
    
    private fun deserializeBackup(json: String): BackupData {
        val obj = JSONObject(json)
        val version = obj.optInt("version", 1)
        
        // Settings
        val settingsObj = obj.optJSONObject("settings") ?: JSONObject()
        val settings = mutableMapOf<String, Any?>()
        settingsObj.keys().forEach { key ->
            settings[key] = settingsObj.opt(key)
        }
        
        // Themes
        val themes = obj.optString("themes", null)
        
        // Metadata
        val metadataObj = obj.optJSONObject("metadata") ?: JSONObject()
        val metadata = mutableMapOf<String, String>()
        metadataObj.keys().forEach { key ->
            metadata[key] = metadataObj.optString(key)
        }
        
        return BackupData(version, settings, themes, metadata)
    }
}
