package com.example.myapplication

import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Visualizador expandido a pantalla completa con animaciones suaves.
 * Se muestra al tocar el visualizador pequeño en la pestaña de configuración.
 * Permite cambiar estilo y color del visualizador en tiempo real.
 * 
 * Mejoras aplicadas:
 * - Pantalla completa real: oculta status bar + navigation bar con modo inmersivo
 * - Animaciones de transición entre estilos (fade + scale)
 * - Controles accesibles con botones estilizados Winamp
 * - Soporta audio local Y Spotify (con AudioPlaybackCapture)
 */
internal class ExpandedVisualizer(private val activity: AppCompatActivity, private val prefs: SharedPreferences, private val changed: () -> Unit) {
    private var dialog: Dialog? = null
    private var visual: VisualizerView? = null
    private var caption: TextView? = null
    private var playing = false
    private var spotify = false
    private var title = "Visualizador local"
    private var currentStyle = 0
    private var currentColor = 0

    /** Mostrar el visualizador en pantalla completa real */
    fun show() {
        if (dialog?.isShowing == true) return
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

        currentStyle = prefs.getInt("visualizer_style", 0)
        currentColor = prefs.getInt("visualizer_color", 0)

        val windowDialog = Dialog(activity, R.style.Theme_MyApplication)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(PlayerAppearance.background(prefs))
            alpha = 0f
        }

        // Título con nombre de la canción
        caption = TextView(activity).apply {
            textSize = 16f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setTextColor(PlayerAppearance.accent(prefs))
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        box.addView(caption, LinearLayout.LayoutParams(-1, -2))

        // Visualizador que ocupa todo el espacio restante
        visual = VisualizerView(activity).apply {
            setColors(PlayerAppearance.accent(prefs), 0)
            configure(prefs)
            alpha = 0f
            scaleX = 0.95f
            scaleY = 0.95f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
        box.addView(visual, LinearLayout.LayoutParams(-1, 0, 1f))

        // Controles: cambiar estilo, cambiar color, cerrar
        val controls = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            gravity = Gravity.CENTER
        }

        // Botón: Cambiar estilo
        controls.addView(createButton("Estilo", dp(48)) {
            AlertDialog.Builder(activity).setTitle("Estilo del visualizador")
                .setSingleChoiceItems(VisualizerView.styles.toTypedArray(), currentStyle) { picker, which ->
                    currentStyle = which
                    prefs.edit().putInt("visualizer_style", which).apply()
                    visual?.animate()
                        ?.alpha(0f)
                        ?.setDuration(100)
                        ?.withEndAction {
                            changed()
                            visual?.configure(prefs)
                            visual?.animate()?.alpha(1f)?.setDuration(200)?.start()
                        }
                        ?.start()
                    picker.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })

        // Botón: Cambiar color
        controls.addView(createButton("Color", dp(48)) {
            AlertDialog.Builder(activity).setTitle("Color del visualizador")
                .setSingleChoiceItems(VisualizerView.colorPresets.map { it.first }.toTypedArray(), currentColor) { picker, which ->
                    currentColor = which
                    val color = VisualizerView.colorPresets[which].second
                    prefs.edit().putInt("visualizer_color", which).apply()
                    visual?.animate()
                        ?.alpha(0.5f)
                        ?.setDuration(100)
                        ?.withEndAction {
                            visual?.setColors(color, 0)
                            changed()
                            visual?.animate()?.alpha(1f)?.setDuration(200)?.start()
                        }
                        ?.start()
                    picker.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })

        // Botón: Cerrar
        controls.addView(createButton("Cerrar", dp(48)) { 
            box.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction { windowDialog.dismiss() }
                .start()
        }, LinearLayout.LayoutParams(0, -2, 1f))

        box.addView(controls)

        windowDialog.setContentView(box)
        windowDialog.setOnDismissListener {
            visual?.setActive(false)
            visual = null
            caption = null
            dialog = null
        }
        dialog = windowDialog
        windowDialog.show()

        box.animate()
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Pantalla completa real: ocultar barras del sistema
        windowDialog.window?.let { win ->
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            win.setBackgroundDrawable(ColorDrawable(PlayerAppearance.background(prefs)))
            WindowCompat.getInsetsController(win, box).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            win.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }

        update(title, playing, spotify)
    }

    /** Actualizar el texto y estado del visualizador */
    fun update(track: String, active: Boolean, spotifySource: Boolean) {
        title = track
        playing = active
        spotify = spotifySource
        // Funciona con audio local Y con Spotify (AudioPlaybackCapture)
        visual?.setActive(active)
        caption?.text = if (spotifySource)
            "$track${if (!active) " · Spotify en pausa" else " · Spotify"}"
        else
            "$track${if (!active) " · En pausa" else ""}"
    }

    /** Cerrar el diálogo de pantalla completa */
    fun dismiss() { dialog?.dismiss() }

    /** Crear botón estilizado para los controles del visualizador */
    private fun createButton(text: String, height: Int, onClick: () -> Unit) =
        AppCompatButton(activity).apply {
            this.text = text
            isAllCaps = false
            setBackgroundResource(R.drawable.panel_button)
            backgroundTintList = null
            setTextColor(0xFF203044.toInt())
            minimumHeight = height
            setOnClickListener { onClick() }
        }
}

// Desarrollado por rcktsm
