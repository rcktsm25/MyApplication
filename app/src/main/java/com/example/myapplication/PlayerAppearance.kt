package com.example.myapplication

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils

object PlayerAppearance {
    // Keep the original three positions so existing preferences survive the redesign.
    val names = listOf("Clásico", "Ámbar", "Hielo", "Lavanda", "Rosa", "Azul", "Turquesa", "Coral", "Menta", "Perla", "Limón", "Melocotón")
    val colors = listOf("#70FF20", "#FFC66D", "#88DBEE", "#C1AFF9", "#F5A4D3", "#8CBAFF",
        "#68DCC8", "#FFAAAC", "#A3E8BC", "#E9EFF7", "#E6EB83", "#F7BD9B").map(Color::parseColor)
    val backgrounds = listOf("Grafito", "Negro", "Azul noche", "Violeta", "Bosque", "Vino", "Petróleo", "Cobre")
    val surfaces = listOf("#373849", "#202125", "#253449", "#3D304B", "#293F37", "#472C38", "#243F46", "#44372D").map(Color::parseColor)
    fun surface(prefs: SharedPreferences): Int = surfaces.getOrNull(prefs.getInt("background_color", 0))
        ?: darkSurface(prefs.getInt("custom_background", surfaces[0]))
    fun background(prefs: SharedPreferences) = ColorUtils.blendARGB(surface(prefs), Color.BLACK, 0.72f)
    fun backgroundName(prefs: SharedPreferences) = backgrounds.getOrNull(prefs.getInt("background_color", 0)) ?: "Personalizado"
    fun darkSurface(color: Int): Int {
        val hsv = FloatArray(3).also { Color.colorToHSV(color, it) }
        hsv[1] = hsv[1].coerceIn(0f, 0.65f)
        hsv[2] = hsv[2].coerceIn(0.12f, 0.30f)
        return Color.HSVToColor(hsv)
    }
    fun chassis(view: View, color: Int): LayerDrawable {
        fun solid(value: Int) = GradientDrawable().apply { setColor(value) }
        val density = view.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        return LayerDrawable(arrayOf(
            solid(ColorUtils.blendARGB(color, Color.BLACK, 0.75f)),
            solid(ColorUtils.blendARGB(color, Color.WHITE, 0.40f)),
            solid(ColorUtils.blendARGB(color, Color.BLACK, 0.4f)),
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                ColorUtils.blendARGB(color, Color.WHITE, 0.025f), ColorUtils.blendARGB(color, Color.BLACK, 0.08f)))
        )).apply {
            setLayerInset(1, 0, 0, dp(2), dp(2))
            setLayerInset(2, dp(2), dp(2), dp(3), dp(3))
            setLayerInset(3, dp(4), dp(4), dp(5), dp(5))
        }
    }
    fun applySurfaces(root: View, prefs: SharedPreferences) {
        val color = surface(prefs)
        val titleColor = ColorUtils.blendARGB(color, Color.BLACK, 0.25f)
        fun visit(view: View) {
            when (view.tag) {
                "classic_panel" -> view.background = chassis(view, color)
                "classic_title" -> view.setBackgroundColor(titleColor)
                "classic_strip" -> {
                    val strip = androidx.appcompat.content.res.AppCompatResources.getDrawable(view.context, R.drawable.winamp_title_strip)!!.mutate() as LayerDrawable
                    (strip.findDrawableByLayerId(R.id.classic_strip_base) as GradientDrawable).setColor(titleColor)
                    (strip.findDrawableByLayerId(R.id.classic_strip_middle) as GradientDrawable).setColor(titleColor)
                    view.background = strip
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }
    fun accent(prefs: SharedPreferences): Int {
        val selection = prefs.getInt("display_color", 0)
        return colors.getOrNull(selection) ?: readable(prefs.getInt("custom_accent", colors[0]))
    }
    /** Preserve the chosen hue while guaranteeing readable small labels on the darkest card surface. */
    fun readable(color: Int): Int {
        val opaque = ColorUtils.setAlphaComponent(color, 255)
        for (step in 0..100) {
            val candidate = ColorUtils.blendARGB(opaque, Color.WHITE, step / 100f)
            if (ColorUtils.calculateContrast(candidate, 0xFF303D42.toInt()) >= 4.5) return candidate
        }
        return Color.WHITE
    }
}
