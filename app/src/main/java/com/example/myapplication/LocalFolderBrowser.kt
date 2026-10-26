package com.example.myapplication

import android.content.Context
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.widget.doAfterTextChanged

internal object LocalFolderBrowser {
    fun show(context: Context, folders: List<MusicFolder>, selected: String?, choose: (String?) -> Unit) {
        if (folders.isEmpty()) return
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        var current = folders.find { it.id == selected } ?: folders.first()
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val search = EditText(context).apply { hint = "Buscar carpeta o ruta"; isSingleLine = true }
        val path = TextView(context).apply { textSize = 12f; setPadding(0, dp(8), 0, dp(8)) }
        val up = AppCompatButton(context).apply { text = "↑ Carpeta superior" }
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        box.addView(search); box.addView(path); box.addView(up)
        box.addView(ScrollView(context).apply { addView(rows) }, LinearLayout.LayoutParams(-1, dp(220)))
        val dialog = AlertDialog.Builder(context).setTitle("Explorar carpetas").setView(box)
            .setPositiveButton("Ver canciones", null).setNeutralButton("Toda mi música") { _, _ -> choose(null) }
            .setNegativeButton("Cerrar", null).create()
        fun render() {
            path.text = current.path
            up.isEnabled = current.parentId != null
            rows.removeAllViews()
            val query = search.text.toString().trim()
            val visible = folders.filter { if (query.isBlank()) it.parentId == current.id else it.path.contains(query, true) }
                .sortedBy { it.path.lowercase(java.util.Locale.ROOT) }
            visible.forEach { folder ->
                rows.addView(AppCompatButton(context).apply {
                    text = "▸ ${if (query.isBlank()) folder.name else folder.path}"
                    isAllCaps = false; gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    minHeight = dp(48); setTextColor(PlayerAppearance.accent(context.getSharedPreferences("RocketSettings", 0)))
                    setOnClickListener { current = folder; search.setText(""); render() }
                }, LinearLayout.LayoutParams(-1, -2))
            }
            if (visible.isEmpty()) rows.addView(TextView(context).apply {
                text = if (query.isBlank()) "No hay subcarpetas. Pulsa Ver canciones para abrir esta carpeta." else "No hay carpetas con ese nombre."
                setPadding(0, dp(12), 0, dp(12))
            })
        }
        up.setOnClickListener { folders.find { it.id == current.parentId }?.let { current = it }; search.setText(""); render() }
        search.doAfterTextChanged { render() }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { choose(current.id); dialog.dismiss() } }
        render(); dialog.show()
    }
}
