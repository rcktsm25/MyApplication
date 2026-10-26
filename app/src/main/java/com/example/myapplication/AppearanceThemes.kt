package com.example.myapplication

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Only appearance preferences belong in a theme; never account or library data. */
internal class AppearanceThemes(private val prefs: SharedPreferences) {
    data class Theme(val id: String, val name: String, val values: JSONObject)
    private val integers = mapOf("display_color" to 0, "custom_accent" to PlayerAppearance.colors[0],
        "background_color" to 0, "custom_background" to PlayerAppearance.surfaces[0],
        "visualizer_style" to 0, "visualizer_response" to 1)
    private val booleans = mapOf("spectrum" to true, "visualizer_peaks" to true, "visualizer_multicolor" to true, "visualizer_grid" to true)
    fun list(): List<Theme> = runCatching {
        val array = JSONArray(prefs.getString("appearance_themes", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { Theme(it.getString("id"), it.getString("name"), it.getJSONObject("values")) } }
    }.getOrDefault(emptyList())
    private fun write(themes: List<Theme>) {
        val array = JSONArray()
        themes.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("values", it.values)) }
        prefs.edit().putString("appearance_themes", array.toString()).apply()
    }
    fun save(name: String): Theme {
        val existing = list()
        val base = name.trim().take(40).ifBlank { "Mi tema" }
        var unique = base
        var suffix = 2
        while (existing.any { it.name.equals(unique, true) }) { unique = "$base (${suffix++})" }
        val values = JSONObject()
        integers.forEach { (key, default) -> values.put(key, prefs.getInt(key, default)) }
        booleans.forEach { (key, default) -> values.put(key, prefs.getBoolean(key, default)) }
        val theme = Theme(UUID.randomUUID().toString(), unique, values)
        write(existing + theme)
        return theme
    }
    fun apply(theme: Theme) {
        prefs.edit().apply {
            integers.forEach { (key, default) -> putInt(key, theme.values.optInt(key, default)) }
            booleans.forEach { (key, default) -> putBoolean(key, theme.values.optBoolean(key, default)) }
        }.apply()
    }
    fun delete(id: String) { write(list().filterNot { it.id == id }) }
}
