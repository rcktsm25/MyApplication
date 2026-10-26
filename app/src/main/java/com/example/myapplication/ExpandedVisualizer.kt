package com.example.myapplication

import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class ExpandedVisualizer(private val activity: AppCompatActivity, private val prefs: SharedPreferences, private val changed: () -> Unit) {
    private var dialog: Dialog? = null
    private var visual: VisualizerView? = null
    private var caption: TextView? = null
    private var playing = false
    private var spotify = false
    private var title = "Visualizador local"
    fun show() {
        if (dialog?.isShowing == true) return
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val windowDialog = Dialog(activity, R.style.Theme_MyApplication)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(PlayerAppearance.background(prefs))
        }
        caption = TextView(activity).apply {
            textSize = 16f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER; setTextColor(PlayerAppearance.accent(prefs))
        }
        box.addView(caption, LinearLayout.LayoutParams(-1, -2))
        visual = VisualizerView(activity).apply { setColors(PlayerAppearance.accent(prefs), 0); configure(prefs) }
        box.addView(visual, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(20); bottomMargin = dp(20) })
        val controls = LinearLayout(activity)
        fun button(text: String, action: () -> Unit) = AppCompatButton(activity).apply {
            this.text = text; isAllCaps = false; setBackgroundResource(R.drawable.panel_button); backgroundTintList = null
            setTextColor(0xFF203044.toInt()); setOnClickListener { action() }
        }
        controls.addView(button("Cambiar estilo") {
            AlertDialog.Builder(activity).setTitle("Visualizador").setSingleChoiceItems(VisualizerView.styles.toTypedArray(), prefs.getInt("visualizer_style", 0)) { picker, which ->
                prefs.edit().putInt("visualizer_style", which).apply(); changed(); visual?.configure(prefs); picker.dismiss()
            }.setNegativeButton("Cancelar", null).show()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        controls.addView(button("Cerrar") { windowDialog.dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        box.addView(controls)
        windowDialog.setContentView(box)
        windowDialog.setOnDismissListener { visual?.setActive(false); visual = null; caption = null; dialog = null }
        dialog = windowDialog
        windowDialog.show()
        windowDialog.window?.let {
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            it.setBackgroundDrawable(ColorDrawable(PlayerAppearance.background(prefs)))
            WindowCompat.getInsetsController(it, box).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        update(title, playing, spotify)
    }
    fun update(track: String, active: Boolean, spotifySource: Boolean) {
        title = track; playing = active; spotify = spotifySource
        visual?.setActive(active && !spotifySource)
        caption?.text = if (spotifySource) "El visualizador está disponible para archivos locales" else "$track${if (!active) " · En pausa" else ""}"
    }
    fun dismiss() { dialog?.dismiss() }
}
