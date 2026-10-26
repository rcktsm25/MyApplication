package com.example.myapplication

import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences

/**
 * Maneja el modo oscuro/claro automático basado en el tema del sistema.
 */
class SystemThemeHandler(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    companion object {
        const val PREF_AUTO_THEME = "auto_theme_enabled"
        const val PREF_USE_SYSTEM_THEME = "use_system_theme"
    }
    
    fun isAutoThemeEnabled(): Boolean = prefs.getBoolean(PREF_AUTO_THEME, false)
    
    fun setAutoTheme(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_THEME, enabled).apply()
    }
    
    fun shouldUseSystemTheme(): Boolean = prefs.getBoolean(PREF_USE_SYSTEM_THEME, true)
    
    /**
     * Determina si el sistema está en modo oscuro.
     */
    fun isSystemDarkMode(): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }
    
    /**
     * Obtiene el color de fondo apropiado según el tema del sistema.
     */
    fun getSystemBackgroundColor(): Int {
        return if (isSystemDarkMode()) {
            0xFF14141D.toInt() // Fondo oscuro
        } else {
            0xFFF5F5F5.toInt() // Fondo claro
        }
    }
    
    /**
     * Obtiene el color de texto apropiado según el tema del sistema.
     */
    fun getSystemTextColor(): Int {
        return if (isSystemDarkMode()) {
            0xFFE0E2EA.toInt() // Texto claro
        } else {
            0xFF1A1A1A.toInt() // Texto oscuro
        }
    }
    
    /**
     * Aplica el tema del sistema si está habilitado.
     */
    fun applySystemThemeIfEnabled(applyBackground: (Int) -> Unit, applyTextColor: (Int) -> Unit) {
        if (isAutoThemeEnabled() && shouldUseSystemTheme()) {
            applyBackground(getSystemBackgroundColor())
            applyTextColor(getSystemTextColor())
        }
    }
}
