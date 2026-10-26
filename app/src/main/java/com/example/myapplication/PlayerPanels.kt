package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat

/** Native preference cards and audio controls sharing the player's visual language. */
class PlayerPanels(private val context: Context, private val prefs: SharedPreferences) {
    var onExpandVisualizer: (() -> Unit)? = null
    var onThemeApplied: (() -> Unit)? = null
    var visualizerPreview: VisualizerView? = null
        private set
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun accent() = PlayerAppearance.accent(prefs)
    private fun shape(color: Int, radius: Int = 2, border: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); border?.let { setStroke(dp(1), it) }
    }
    private fun label(text: String, size: Float = 13f) = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(0xFF9CAAB7.toInt()); typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setPadding(0, dp(5), 0, dp(9)); setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(10))
            tag = "classic_panel"; background = PlayerAppearance.chassis(this, PlayerAppearance.surface(prefs))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
        box.addView(FrameLayout(context).apply {
            tag = "classic_strip"; setBackgroundResource(R.drawable.winamp_title_strip)
            layoutParams = LinearLayout.LayoutParams(-1, dp(28)).apply { bottomMargin = dp(6) }
            addView(TextView(context).apply {
                tag = "classic_title"; text = title.uppercase(java.util.Locale.ROOT); textSize = 12f; typeface = Typeface.MONOSPACE
                setTextColor(0xFFE0E2EA.toInt()); setBackgroundColor(0xFF282936.toInt()); setPadding(dp(8), 0, dp(8), 0)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        })
        parent.addView(box)
        PlayerAppearance.applySurfaces(box, prefs)
        return box
    }
    private fun button(text: String, click: () -> Unit) = AppCompatButton(context).apply {
        this.text = text; textSize = 12f; typeface = Typeface.MONOSPACE; isAllCaps = false
        setTextColor(0xFF203044.toInt()); setBackgroundResource(R.drawable.panel_button); backgroundTintList = null
        minHeight = 0; minimumHeight = 0; setPadding(dp(12), 0, dp(12), 0)
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(4), 0, dp(4)) }
        setOnClickListener { click() }
    }
    private fun toggle(parent: LinearLayout, text: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
        parent.addView(SwitchCompat(context).apply {
            this.text = text; textSize = 14f; setTextColor(0xFFDFE8F1.toInt()); minHeight = dp(52)
            setPadding(0, dp(4), 0, dp(4)); isChecked = checked; isEnabled = enabled
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent(), 0xFF9CAAB7.toInt()))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(androidx.core.graphics.ColorUtils.blendARGB(accent(), 0xFF1A2129.toInt(), 0.65f), 0xFF394450.toInt()))
            setOnCheckedChangeListener { _, value -> change(value) }
        })
    }
    private fun slider(parent: LinearLayout, title: String, max: Int, value: Int, enabled: Boolean = true, describe: (Int) -> String, change: (Int) -> Unit) {
        val caption = label("$title  ·  ${describe(value)}").apply { setTextColor(if (enabled) 0xFFDFE8F1.toInt() else 0xFF778590.toInt()) }
        parent.addView(caption)
        parent.addView(SeekBar(context, null, 0, R.style.ProSlider).apply {
            this.max = max; progress = value; isEnabled = enabled; contentDescription = title
            progressDrawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.pro_progress)
            thumb = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.pro_thumb)
            splitTrack = false
            progressTintList = ColorStateList.valueOf(accent()); thumbTintList = null
            progressBackgroundTintList = ColorStateList.valueOf(0xFF394450.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(44))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    caption.text = "$title  ·  ${describe(progress)}"
                    if (fromUser) change(progress)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
    }
    private fun choice(parent: LinearLayout, title: String, values: List<String>, selected: Int, enabled: Boolean = true, change: (Int) -> Unit): Spinner {
        parent.addView(label(title).apply { setPadding(0, dp(10), 0, dp(6)) })
        val spinner = Spinner(context).apply {
            adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, values) {
                private fun row(position: Int, recycled: View?): TextView = (recycled as? TextView ?: TextView(context)).apply {
                    text = values[position]; textSize = 14f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(if (enabled) 0xFFE9EFF6.toInt() else 0xFF778590.toInt()); gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10)); minHeight = dp(48)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                }
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = row(position, convertView).apply {
                    setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_pro_down, 0)
                    compoundDrawableTintList = ColorStateList.valueOf(0xFF91A1B0.toInt())
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = row(position, convertView)
            }
            setBackgroundResource(R.drawable.player_screen)
            setPadding(0, 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) }; isEnabled = enabled
            contentDescription = "$title. Toca para elegir"
            setSelection(selected.coerceIn(values.indices))
            var previous = selected
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != previous) { previous = position; change(position) }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        parent.addView(spinner)
        return spinner
    }
    private fun save(settings: DspSettings) {
        AudioRuntime.settings = settings
        prefs.edit().apply {
            putBoolean("eq_enabled", settings.enabled); putFloat("preamp", settings.preamp); putFloat("balance", settings.balance)
            settings.gains.forEachIndexed { i, gain -> putFloat("eq_$i", gain) }
        }.apply()
    }
    fun equalizer(parent: LinearLayout, spotify: Boolean, openSpotify: () -> Unit) {
        parent.removeAllViews()
        if (spotify) {
            val info = card(parent, "El sonido de Spotify")
            info.addView(label("Spotify tiene su propio ecualizador. Ábrelo en Configuración → Reproducción → Ecualizador."))
            info.addView(button("Abrir Spotify", openSpotify))
            info.addView(label("Para ajustar tus archivos locales, elige una canción en Música y vuelve a Sonido."))
            return
        }
        val eq = card(parent, "10 bandas · Audio local")
        toggle(eq, "Activar ecualizador", AudioRuntime.settings.enabled) {
            save(AudioRuntime.settings.copy(enabled = it)); equalizer(parent, spotify, openSpotify)
        }
        val presets = AudioDsp.presets.keys.toList() + "Personalizado"
        val selected = prefs.getString("eq_preset", "Plano") ?: "Plano"
        val presetChoice = choice(eq, "Empieza con un preset", presets, presets.indexOf(selected).coerceAtLeast(0)) { index ->
            AudioDsp.presets[presets[index]]?.let { gains ->
                prefs.edit().putString("eq_preset", presets[index]).apply()
                save(AudioRuntime.settings.copy(enabled = true, gains = gains, preamp = if (gains.any { it > 0 }) -6f else 0f))
                equalizer(parent, spotify, openSpotify)
            }
        }
        eq.addView(label("Graves → Agudos · Desliza para ver las 10 bandas", 11f))
        val sliders = LinearLayout(context)
        AudioDsp.frequencies.forEachIndexed { i, frequency ->
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            val gain = AudioRuntime.settings.gains[i]
            val value = label("${gain.toInt()}", 11f).apply { gravity = Gravity.CENTER; setTextColor(accent()) }
            column.addView(value)
            column.addView(VerticalFader(context).apply {
                progress = gain.toInt() + 12; isEnabled = AudioRuntime.settings.enabled; color = accent()
                contentDescription = "${frequency.toInt()} hercios, ${gain.toInt()} decibelios"
                onChange = { level ->
                    val gains = AudioRuntime.settings.gains.toMutableList().apply { set(i, (level - 12).toFloat()) }
                    value.text = "${if (level > 12) "+" else ""}${level - 12}"; contentDescription = "${frequency.toInt()} hercios, ${level - 12} decibelios"
                    prefs.edit().putString("eq_preset", "Personalizado").apply(); presetChoice.setSelection(presets.lastIndex)
                    save(AudioRuntime.settings.copy(gains = gains))
                }
            }, LinearLayout.LayoutParams(dp(44), dp(112)))
            val hz = if (frequency >= 1000) "${(frequency / 1000).toInt()}k" else frequency.toInt().toString()
            column.addView(label(hz, 11f).apply { gravity = Gravity.CENTER })
            sliders.addView(column, LinearLayout.LayoutParams(dp(48), -2))
        }
        eq.addView(HorizontalScrollView(context).apply { addView(sliders); isHorizontalScrollBarEnabled = true })
        eq.addView(label("Cada banda: de −12 a +12 dB", 11f).apply { gravity = Gravity.CENTER })
        val output = card(parent, "Salida de audio")
        slider(output, "Preamplificador", 18, AudioRuntime.settings.preamp.toInt() + 12, AudioRuntime.settings.enabled,
            { "${it - 12} dB" }) { save(AudioRuntime.settings.copy(preamp = (it - 12).toFloat())) }
        slider(output, "Balance", 200, (AudioRuntime.settings.balance * 100).toInt() + 100, true,
            { when { it == 100 -> "Centro"; it < 100 -> "Izquierda ${100 - it}%"; else -> "Derecha ${it - 100}%" } }) {
            save(AudioRuntime.settings.copy(balance = (it - 100) / 100f))
        }
        output.addView(button("Restablecer sonido") {
            save(DspSettings(enabled = AudioRuntime.settings.enabled)); prefs.edit().putString("eq_preset", "Plano").apply()
            equalizer(parent, spotify, openSpotify)
        })
    }
    private fun palette(parent: LinearLayout, appearance: () -> Unit) {
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(grid)
        fun render() {
            grid.removeAllViews()
            PlayerAppearance.names.chunked(3).forEachIndexed { rowIndex, names ->
                val row = LinearLayout(context)
                names.forEachIndexed { column, name ->
                    val index = rowIndex * 3 + column
                    row.addView(button(name) {
                        prefs.edit().putInt("display_color", index).apply(); appearance(); render()
                    }.apply {
                        textSize = 11f; setTextColor(0xFFE9EFF6.toInt()); setPadding(dp(8), 0, dp(5), 0)
                        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
                        val dot = shape(PlayerAppearance.colors[index], 20).apply { setBounds(0, 0, dp(12), dp(12)) }
                        setCompoundDrawablesRelative(dot, null, null, null); compoundDrawablePadding = dp(5)
                        val selected = prefs.getInt("display_color", 0) == index
                        background = shape(0xFF242532.toInt(), 2, if (selected) PlayerAppearance.colors[index] else 0xFF29333D.toInt())
                        isSelected = selected; contentDescription = "$name${if (selected) ", seleccionado" else ""}"
                    })
                }
                grid.addView(row)
            }
            grid.addView(button(if (prefs.getInt("display_color", 0) == PlayerAppearance.colors.size) "✓ Tu color personalizado" else "Crear mi propio color") {
                customColor { appearance(); render() }
            })
        }
        render()
    }
    private fun customColor(applied: () -> Unit) {
        val hsv = FloatArray(3).also { Color.colorToHSV(accent(), it) }
        var chosen = accent()
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val preview = TextView(context).apply {
            text = "▶  Tu música, tu estilo"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(0xFF102018.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) }
            background = shape(chosen, 2)
        }
        box.addView(preview)
        fun update() { chosen = PlayerAppearance.readable(Color.HSVToColor(hsv)); preview.background = shape(chosen, 2) }
        slider(box, "Tono", 359, hsv[0].toInt(), describe = { "$it°" }) { hsv[0] = it.toFloat(); update() }
        slider(box, "Intensidad", 100, (hsv[1] * 100).toInt(), describe = { "$it%" }) { hsv[1] = it / 100f; update() }
        slider(box, "Brillo", 100, (hsv[2] * 100).toInt(), describe = { "$it%" }) { hsv[2] = it / 100f; update() }
        box.addView(label("La vista previa mantiene el contraste para que todo se lea bien.", 12f))
        val scroll = ScrollView(context).apply { addView(box) }
        AlertDialog.Builder(context).setTitle("Crea tu color").setView(scroll)
            .setPositiveButton("Aplicar") { _, _ ->
                prefs.edit().putInt("custom_accent", chosen).putInt("display_color", PlayerAppearance.colors.size).apply(); applied()
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun backgroundPicker(applied: () -> Unit) {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(8)) }
        box.addView(label("Cambia el fondo y los paneles. Los biseles y las franjas de Winamp se conservan."))
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        box.addView(grid)
        fun render() {
            grid.removeAllViews()
            PlayerAppearance.backgrounds.chunked(2).forEachIndexed { rowIndex, names ->
                val row = LinearLayout(context)
                names.forEachIndexed { column, name ->
                    val index = rowIndex * 2 + column
                    val selected = prefs.getInt("background_color", 0) == index
                    val tile = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        background = PlayerAppearance.chassis(this, PlayerAppearance.surfaces[index])
                        isClickable = true; isFocusable = true; isSelected = selected
                        contentDescription = "$name${if (selected) ", seleccionado" else ""}"
                        setOnClickListener {
                            prefs.edit().putInt("background_color", index).apply(); applied(); render()
                        }
                    }
                    tile.addView(TextView(context).apply {
                        text = "00:00"; textSize = 19f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
                        setTextColor(accent()); setBackgroundResource(R.drawable.player_screen)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(-1, dp(38)))
                    tile.addView(TextView(context).apply {
                        text = "${if (selected) "✓ " else ""}$name"; textSize = 12f; gravity = Gravity.CENTER
                        setTextColor(if (selected) accent() else 0xFFE9EFF6.toInt()); includeFontPadding = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                    row.addView(tile, LinearLayout.LayoutParams(0, dp(86), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
                }
                grid.addView(row)
            }
            grid.addView(button(if (prefs.getInt("background_color", 0) == PlayerAppearance.surfaces.size) "✓ Fondo personalizado" else "Crear mi propio fondo") {
                customBackground { applied(); render() }
            })
        }
        render()
        AlertDialog.Builder(context).setTitle("Fondo y paneles").setView(ScrollView(context).apply { addView(box) })
            .setPositiveButton("Listo", null).show()
    }
    private fun customBackground(applied: () -> Unit) {
        val hsv = FloatArray(3).also { Color.colorToHSV(PlayerAppearance.surface(prefs), it) }
        var chosen = PlayerAppearance.darkSurface(Color.HSVToColor(hsv))
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val preview = TextView(context).apply {
            text = "00:00   CLASSIC"; textSize = 20f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
            setTextColor(accent()); background = PlayerAppearance.chassis(this, chosen)
            layoutParams = LinearLayout.LayoutParams(-1, dp(88))
        }
        box.addView(preview)
        fun update() { chosen = PlayerAppearance.darkSurface(Color.HSVToColor(hsv)); preview.background = PlayerAppearance.chassis(preview, chosen) }
        slider(box, "Tono del fondo", 359, hsv[0].toInt(), describe = { "$it°" }) { hsv[0] = it.toFloat(); update() }
        slider(box, "Intensidad", 65, (hsv[1] * 100).toInt(), describe = { "$it%" }) { hsv[1] = it / 100f; update() }
        slider(box, "Luminosidad", 18, (hsv[2] * 100).toInt() - 12, describe = { "${it + 12}%" }) { hsv[2] = (it + 12) / 100f; update() }
        box.addView(label("Los tonos oscuros mantienen legibles el display y los controles."))
        AlertDialog.Builder(context).setTitle("Tu fondo Classic").setView(ScrollView(context).apply { addView(box) })
            .setPositiveButton("Aplicar") { _, _ ->
                prefs.edit().putInt("custom_background", chosen).putInt("background_color", PlayerAppearance.surfaces.size).apply(); applied()
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun appearanceSettings(parent: LinearLayout, appearance: () -> Unit) {
        val box = card(parent, "Apariencia · Classic")
        box.addView(label("Combina el color del display con el fondo de los paneles."))
        val preview = TextView(context).apply {
            text = "00:00   ROCKETPLAYER"; textSize = 17f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
            contentDescription = "Vista previa de los colores elegidos"
            layoutParams = LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(8) }
        }
        box.addView(preview)
        lateinit var colorButton: AppCompatButton
        lateinit var backgroundButton: AppCompatButton
        fun refresh() {
            appearance()
            preview.setTextColor(accent()); preview.background = PlayerAppearance.chassis(preview, PlayerAppearance.surface(prefs))
            colorButton.text = "Color: ${PlayerAppearance.names.getOrNull(prefs.getInt("display_color", 0)) ?: "Personalizado"}  ›"
            backgroundButton.text = "Fondo: ${PlayerAppearance.backgroundName(prefs)}  ›"
        }
        colorButton = button("") {
            val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
            palette(content, ::refresh)
            AlertDialog.Builder(context).setTitle("Color del display").setView(ScrollView(context).apply { addView(content) })
                .setPositiveButton("Listo", null).show()
        }
        backgroundButton = button("") { backgroundPicker(::refresh) }
        box.addView(colorButton); box.addView(backgroundButton)
        val themes = AppearanceThemes(prefs)
        val themeRow = LinearLayout(context)
        themeRow.addView(button("Guardar tema") {
            val input = EditText(context).apply { hint = "Nombre del tema"; setSingleLine(); setText("Mi tema") }
            AlertDialog.Builder(context).setTitle("Guardar fondo, color y visualizador").setView(input)
                .setPositiveButton("Guardar") { _, _ ->
                    val saved = themes.save(input.text.toString())
                    Toast.makeText(context, "Tema guardado: ${saved.name}", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Cancelar", null).show()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6); topMargin = dp(6) })
        themeRow.addView(button("Mis temas") {
            val saved = themes.list()
            if (saved.isEmpty()) Toast.makeText(context, "Guarda tu primera combinación con Guardar tema", Toast.LENGTH_SHORT).show()
            else AlertDialog.Builder(context).setTitle("Mis temas").setItems(saved.map { it.name }.toTypedArray()) { _, index ->
                val theme = saved[index]
                AlertDialog.Builder(context).setTitle(theme.name).setItems(arrayOf("Aplicar tema", "Eliminar tema")) { _, action ->
                    if (action == 0) { themes.apply(theme); refresh(); onThemeApplied?.invoke() }
                    else AlertDialog.Builder(context).setTitle("Eliminar ${theme.name}").setMessage("Se eliminará esta combinación guardada.")
                        .setPositiveButton("Eliminar") { _, _ -> themes.delete(theme.id) }.setNegativeButton("Cancelar", null).show()
                }.setNegativeButton("Cancelar", null).show()
            }.setNegativeButton("Cerrar", null).show()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { topMargin = dp(6) })
        box.addView(themeRow)
        box.addView(button("Restaurar estilo clásico") {
            prefs.edit().putInt("display_color", 0).putInt("background_color", 0).apply(); refresh()
        })
        refresh()
    }
    fun settings(parent: LinearLayout, spotify: Boolean, configureSpotify: () -> Unit, disconnect: () -> Unit,
                 folder: () -> Unit, refresh: () -> Unit, appearance: () -> Unit, speed: (Float) -> Unit,
                 exportConfig: () -> Unit, importConfig: () -> Unit, showGroupedLibrary: () -> Unit) {
        parent.removeAllViews()
        appearanceSettings(parent, appearance)
        val visual = card(parent, "Visualizador local")
        visualizerPreview = VisualizerView(context).apply {
            setColors(accent(), 0); configure(prefs); setBackgroundResource(R.drawable.player_screen); setPadding(dp(4), dp(4), dp(4), dp(4))
            contentDescription = "Ampliar visualizador local a pantalla completa"
            isFocusable = true; setOnClickListener { onExpandVisualizer?.invoke() }
        }
        visual.addView(visualizerPreview, LinearLayout.LayoutParams(-1, dp(88)).apply { bottomMargin = dp(8) })
        visual.addView(label(if (spotify) "Spotify no permite visualizar su audio. Elige una canción local para ver la animación."
            else "Toca el visualizador para ampliarlo. Reproduce un archivo local para verlo en movimiento.", 12f))
        toggle(visual, "Mostrar visualizador", prefs.getBoolean("spectrum", true)) {
            prefs.edit().putBoolean("spectrum", it).apply(); appearance()
        }
        val barOptions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val responseOptions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val colorOptions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        choice(visual, "Estilo", VisualizerView.styles, prefs.getInt("visualizer_style", 0).coerceIn(0, VisualizerView.styles.size - 1)) {
            prefs.edit().putInt("visualizer_style", it).apply()
            barOptions.visibility = if (it < 2) View.VISIBLE else View.GONE
            responseOptions.visibility = if (it == 3) View.GONE else View.VISIBLE
            appearance()
        }
        visual.addView(responseOptions)
        responseOptions.visibility = if (prefs.getInt("visualizer_style", 0) == 3) View.GONE else View.VISIBLE
        choice(responseOptions, "Respuesta del espectro", VisualizerView.responses, prefs.getInt("visualizer_response", 1).coerceIn(0, VisualizerView.responses.size - 1)) {
            prefs.edit().putInt("visualizer_response", it).apply(); appearance()
        }
        barOptions.visibility = if (prefs.getInt("visualizer_style", 0) < 2) View.VISIBLE else View.GONE
        visual.addView(barOptions)
        toggle(barOptions, "Indicadores de picos", prefs.getBoolean("visualizer_peaks", true)) {
            prefs.edit().putBoolean("visualizer_peaks", it).apply(); appearance()
        }
        toggle(barOptions, "Colores clásicos en los picos", prefs.getBoolean("visualizer_multicolor", true)) {
            prefs.edit().putBoolean("visualizer_multicolor", it).apply(); appearance()
        }
        toggle(visual, "Cuadrícula de fondo", prefs.getBoolean("visualizer_grid", true)) {
            prefs.edit().putBoolean("visualizer_grid", it).apply(); appearance()
        }
        choice(visual, "Color del visualizador", VisualizerView.colorPresets.map { it.first },
            VisualizerView.colorPresets.indexOfFirst { it.second == accent() }.coerceAtLeast(0)) {
            val color = VisualizerView.colorPresets[it].second
            prefs.edit().putInt("visualizer_color", it).apply()
            visualizerPreview?.setColors(color, 0); appearance()
        }
        visual.addView(colorOptions)
        slider(colorOptions, "Grosor de onda", 10, (prefs.getFloat("visualizer_thickness", 2f) * 2).toInt().coerceIn(2, 12), true,
            { v -> "${v / 2f}" }) { v ->
            prefs.edit().putFloat("visualizer_thickness", v / 2f).apply(); appearance()
        }
        toggle(colorOptions, "Efecto brillo (glow)", prefs.getBoolean("visualizer_glow", false)) {
            prefs.edit().putBoolean("visualizer_glow", it).apply(); appearance()
        }
        toggle(colorOptions, "Modo espejo", prefs.getBoolean("visualizer_mirror", false)) {
            prefs.edit().putBoolean("visualizer_mirror", it).apply(); appearance()
        }
        toggle(colorOptions, "Barras con gradiente", prefs.getBoolean("visualizer_gradient", false)) {
            prefs.edit().putBoolean("visualizer_gradient", it).apply(); appearance()
        }
        val screen = card(parent, "Pantalla")
        toggle(screen, "Mantener pantalla encendida", prefs.getBoolean("keep_screen", false)) {
            prefs.edit().putBoolean("keep_screen", it).apply(); appearance()
        }
        toggle(screen, "Tema automático del sistema", prefs.getBoolean("auto_theme_enabled", false)) {
            prefs.edit().putBoolean("auto_theme_enabled", it).apply(); appearance()
        }
        val playback = card(parent, "Reproducción local")
        if (spotify) playback.addView(label("Estos ajustes se activan al escuchar música del teléfono."))
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        choice(playback, "Velocidad", listOf("0.75× · Más despacio", "1× · Normal", "1.25×", "1.5×", "2× · Más rápido"), speeds.indexOf(prefs.getFloat("playback_speed", 1f)).coerceAtLeast(0), !spotify) {
            prefs.edit().putFloat("playback_speed", speeds[it]).apply(); speed(speeds[it])
        }
        toggle(playback, "Transición suave (crossfade)", prefs.getBoolean("crossfade_enabled", false)) {
            prefs.edit().putBoolean("crossfade_enabled", it).apply()
        }
        val minutes = listOf(0, 15, 30, 60)
        val timerSelection = if (AudioRuntime.sleepUntil > 0) prefs.getInt("sleep_choice", 0) else 0
        choice(playback, "Pausar automáticamente", listOf("No pausar", "Dentro de 15 minutos", "Dentro de 30 minutos", "Dentro de 1 hora"), timerSelection, !spotify) {
            prefs.edit().putInt("sleep_choice", it).apply()
            AudioRuntime.sleepUntil = if (minutes[it] == 0) 0 else System.currentTimeMillis() + minutes[it] * 60000L
            Toast.makeText(context, if (minutes[it] == 0) "Temporizador desactivado" else "La música se pausará en ${minutes[it]} minutos", Toast.LENGTH_SHORT).show()
        }
        val audio = card(parent, "Audio")
        toggle(audio, "ReplayGain (normalizar volumen)", prefs.getBoolean("replaygain_enabled", false)) {
            prefs.edit().putBoolean("replaygain_enabled", it).apply()
        }
        choice(audio, "Modo ReplayGain", listOf("Por canción", "Por álbum"), prefs.getInt("replaygain_mode", 0).coerceIn(0, 1)) {
            prefs.edit().putInt("replaygain_mode", it).apply()
        }
        val library = card(parent, "Tu biblioteca")
        library.addView(button("Ver por artista/álbum", showGroupedLibrary))
        library.addView(button("Cambiar carpeta de música", folder))
        library.addView(button("Actualizar archivos de la carpeta", refresh))
        val backup = card(parent, "Configuración")
        backup.addView(button("Exportar configuración", exportConfig))
        backup.addView(button("Importar configuración", importConfig))
        val connection = card(parent, "Spotify")
        connection.addView(button("Configurar conexión", configureSpotify))
        connection.addView(button("Desconectar Spotify", disconnect).apply { setTextColor(0xFF61252C.toInt()) })
        parent.addView(label("RocketPlayer · Classic Audio\nTu música, sin una cuenta adicional.", 12f).apply { gravity = Gravity.CENTER })
    }
}
