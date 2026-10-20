package com.example.myapplication

import android.Manifest
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.spotify.protocol.types.PlayerState
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // ── ViewBinding ──────────────────────────────────────────────────────────
    private lateinit var binding: ActivityMainBinding

    // ── Spotify Controller & Estado ───────────────────────────────────────────
    private lateinit var spotifyManager: SpotifyManager
    private var isSpotifyMode = false

    // ── Media3 ───────────────────────────────────────────────────────────────
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (::controllerFuture.isInitialized && controllerFuture.isDone) {
            try {
                controllerFuture.get()
            } catch (e: Exception) {
                null
            }
        } else null

    // ── Skins ────────────────────────────────────────────────────────────────
    private var currentSkinIndex = 0
    private val skins = listOf("ROCKET", "CLASSIC", "MATRIX", "CYBER", "GOLD")
    private lateinit var prefs: SharedPreferences

    // ── Playlist ─────────────────────────────────────────────────────────────
    private val playlist = mutableListOf(
        Track("SomaFM: Groove Salad", "Ambient/Downtempo", "https://somafm.com/groovesalad130.mp3"),
        Track("SomaFM: DEF CON Radio", "Music for Hackers", "https://somafm.com/defcon130.mp3"),
        Track("Lofi Girl", "Study Beats", "https://stream.zeno.fm/0r0xa792kwzuv"),
        Track("Spotify: Synthwave Retro", "Retrowave & Beats", "spotify:playlist:37i9dQZF1DXdLEN7aqioXM"),
        Track("Spotify: Lo-Fi Study", "Chill Instrumental", "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"),
        Track("KEXP Seattle", "Indie Radio", "https://kexp-mp3-128.streamguys1.com/kexp128.mp3"),
        Track("Jazz24", "Classic Jazz", "https://live.jazz24.org/jazz24-mp3")
    )
    private lateinit var trackAdapter: TrackAdapter

    // ── SeekBar state (evitar seekTo continuo mientras se arrastra) ───────────
    private var userSeekInProgress = false

    // ── Pista actual en el controller ────────────────────────────────────────
    private var currentTrackIndex: Int = -1

    // ── Progreso continuo de reproducción (Corrección de Segundero / SeekBar) ─
    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            val player = controller
            if (player != null && player.isPlaying && !userSeekInProgress) {
                val duration = player.duration
                if (duration > 0 && duration < Long.MAX_VALUE / 2) {
                    binding.seekBar.max = duration.toInt()
                    binding.seekBar.progress = player.currentPosition.toInt()
                    binding.seekBar.isEnabled = true
                }
                binding.playbackTime.text = formatTime(player.currentPosition)
            }
            if (player?.isPlaying == true) {
                progressHandler.postDelayed(this, 1000)
            }
        }
    }

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        prefs = getSharedPreferences("RocketSettings", MODE_PRIVATE)

        // Verificar sesión activa; si no existe, redirigir a LoginActivity
        val loggedUser = prefs.getString("logged_username", null)
        val isRemembered = prefs.getBoolean("is_logged_in", false)
        if (loggedUser == null && !isRemembered) {
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Marquee en el título de pista
        binding.trackTitle.isSelected = true

        // Botones de la barra de título
        binding.btnSpotify.setOnClickListener { showSpotifyDialog() }
        binding.btnTheme.setOnClickListener { toggleSkin() }
        binding.btnSpeed.setOnClickListener { toggleVisualizerSpeed() }
        binding.btnLogout.setOnClickListener { showLogoutDialog() }
        binding.btnAddStream.setOnClickListener { showAddStreamDialog() }

        // Inicializar integración con Spotify
        spotifyManager = SpotifyManager(this)
        setupSpotify()

        // Cargar datos, inicializar lista y controles antes de aplicar el tema
        loadSavedStreams()
        setupRecyclerView()
        setupControls()

        // Restaurar skin guardado
        val savedSkin = prefs.getString("selected_skin", "ROCKET") ?: "ROCKET"
        applySkin(savedSkin)

        checkPermissions()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistencia de streams del usuario
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadSavedStreams() {
        val count = prefs.getInt("saved_stream_count", 0)
        for (i in 0 until count) {
            val title = prefs.getString("stream_title_$i", null) ?: continue
            val artist = prefs.getString("stream_artist_$i", "") ?: ""
            val uri = prefs.getString("stream_uri_$i", null) ?: continue
            playlist.add(Track(title, artist, uri))
        }
    }

    private fun saveUserStreams() {
        // Guardar solo los streams añadidos por el usuario (a partir de las 7 pistas predeterminadas)
        val defaultCount = 7
        val userStreams = playlist.drop(defaultCount)
        val editor = prefs.edit()
        editor.putInt("saved_stream_count", userStreams.size)
        userStreams.forEachIndexed { i, track ->
            editor.putString("stream_title_$i", track.title)
            editor.putString("stream_artist_$i", track.artist)
            editor.putString("stream_uri_$i", track.uri)
        }
        editor.apply()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Diálogo: Añadir stream o recurso de Spotify
    // ─────────────────────────────────────────────────────────────────────────

    private fun showAddStreamDialog() {
        val nameInput = android.widget.EditText(this).apply {
            hint = getString(R.string.dialog_name_hint)
        }
        val urlInput = android.widget.EditText(this).apply {
            hint = getString(R.string.dialog_url_hint)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI or android.text.InputType.TYPE_CLASS_TEXT
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
            addView(nameInput)
            addView(urlInput)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_title))
            .setView(container)
            .setPositiveButton(getString(R.string.dialog_ok)) { _, _ ->
                val url = urlInput.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this, getString(R.string.error_empty_url), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val isSpotify = url.startsWith("spotify:") || url.contains("spotify.com")
                val defaultName = if (isSpotify) "Spotify Stream" else getString(R.string.stream_default_name)
                val defaultArtist = if (isSpotify) "Spotify" else getString(R.string.stream_user_label)
                val name = nameInput.text.toString().trim().ifEmpty { defaultName }

                val newTrack = Track(name, defaultArtist, url)
                playlist.add(newTrack)
                trackAdapter.submitList(playlist.toList())

                // Solo agregar a ExoPlayer si es un flujo de audio estándar (no Spotify)
                if (!isSpotify) {
                    controller?.addMediaItem(
                        MediaItem.Builder()
                            .setUri(url)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(name)
                                    .setArtist(defaultArtist)
                                    .build()
                            )
                            .build()
                    )
                }

                saveUserStreams()
                Toast.makeText(this, getString(R.string.toast_stream_added), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Integración Spotify — Callbacks y Diálogo de Conexión
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Configura los callbacks de eventos de SpotifyManager:
     * - onConnectionStatusChanged: actualiza el icono y estado en la interfaz.
     * - onPlayerStateChanged: actualiza el display retro con la canción y tiempo de Spotify.
     * - onError: muestra errores en pantalla.
     */
    private fun setupSpotify() {
        spotifyManager.onConnectionStatusChanged = { isConnected ->
            runOnUiThread {
                if (isConnected) {
                    binding.btnSpotify.imageTintList = null
                    Toast.makeText(this, getString(R.string.spotify_toast_connected), Toast.LENGTH_SHORT).show()
                } else {
                    val colors = getAccentColor()
                    binding.btnSpotify.imageTintList = android.content.res.ColorStateList.valueOf(colors)
                    binding.tvSpotifyBadge.visibility = android.view.View.GONE
                }
            }
        }

        spotifyManager.onPlayerStateChanged = { playerState ->
            runOnUiThread {
                val track = playerState.track
                if (track != null) {
                    isSpotifyMode = true
                    binding.tvSpotifyBadge.visibility = android.view.View.VISIBLE
                    binding.trackTitle.text = "${track.name} — ${track.artist.name}"
                    binding.playbackTime.text = formatTime(playerState.playbackPosition)

                    if (track.duration > 0 && !userSeekInProgress) {
                        binding.seekBar.max = track.duration.toInt()
                        binding.seekBar.progress = playerState.playbackPosition.toInt()
                        binding.seekBar.isEnabled = true
                    }

                    binding.bitrateInfo.text = "SPOTIFY"
                    binding.sampleRateInfo.text = "320 kbps"

                    val accentColor = getAccentColor()
                    val dimColor = ContextCompat.getColor(this, R.color.rocket_text_dim)

                    if (!playerState.isPaused) {
                        binding.visualizer.startAnimation()
                        binding.tvStereo.setTextColor(accentColor)
                    } else {
                        binding.visualizer.stopAnimation()
                        binding.tvStereo.setTextColor(dimColor)
                    }
                }
            }
        }

        spotifyManager.onError = { error ->
            runOnUiThread {
                showStatus("Spotify: ${error.localizedMessage ?: getString(R.string.spotify_toast_error)}")
            }
        }
    }

    /**
     * Despliega el diálogo de gestión de conexión con Spotify.
     * Permite ingresar o editar el Client ID de Spotify Developer y muestra
     * los datos técnicos necesarios para configurar la aplicación en Spotify Dashboard.
     */
    private fun showSpotifyDialog() {
        if (!spotifyManager.isSpotifyInstalled()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.spotify_dialog_title))
                .setMessage(getString(R.string.spotify_not_installed) + "\n\n" + getString(R.string.spotify_install_prompt))
                .setPositiveButton(getString(R.string.spotify_install_button)) { _, _ ->
                    spotifyManager.openSpotifyInPlayStore()
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
            return
        }

        val isConnected = spotifyManager.isConnected()
        val clientIdInput = android.widget.EditText(this).apply {
            hint = getString(R.string.spotify_client_id_hint)
            setText(spotifyManager.getClientId())
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        val infoText = android.widget.TextView(this).apply {
            text = """
                Estado: ${if (isConnected) "● CONECTADO" else "○ DESCONECTADO"}
                
                Parámetros para Spotify Developer Dashboard:
                • Package: com.example.myapplication
                • Redirect URI: ${SpotifyManager.REDIRECT_URI}
                • SHA-1 (Debug):
                  26:28:A4:37:81:DA:A7:ED:A7:28:41:2E:6D:50:8B:CB:DA:A3:F0:CB
            """.trimIndent()
            textSize = 11f
            setPadding(0, 0, 0, 16)
            setTextIsSelectable(true)
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(infoText)
            addView(clientIdInput)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.spotify_dialog_title))
            .setView(container)

        if (isConnected) {
            builder.setPositiveButton(getString(R.string.spotify_btn_disconnect)) { _, _ ->
                spotifyManager.disconnect()
                isSpotifyMode = false
                binding.tvSpotifyBadge.visibility = android.view.View.GONE
                Toast.makeText(this, getString(R.string.spotify_toast_disconnected), Toast.LENGTH_SHORT).show()
            }
        } else {
            builder.setPositiveButton(getString(R.string.spotify_btn_connect)) { _, _ ->
                val enteredId = clientIdInput.text.toString().trim()
                if (enteredId.isNotEmpty()) {
                    spotifyManager.saveClientId(enteredId)
                }
                Toast.makeText(this, getString(R.string.spotify_status_connecting), Toast.LENGTH_SHORT).show()
                spotifyManager.connect(
                    activity = this,
                    onSuccess = {
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.spotify_toast_connected), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            Toast.makeText(this, "${getString(R.string.spotify_toast_error)}: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }

        builder.setNegativeButton(getString(R.string.dialog_cancel), null)
        builder.show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Skin / Theme
    // ─────────────────────────────────────────────────────────────────────────

    private fun toggleSkin() {
        currentSkinIndex = (currentSkinIndex + 1) % skins.size
        applySkin(skins[currentSkinIndex])
    }

    private fun applySkin(skinName: String) {
        prefs.edit().putString("selected_skin", skinName).apply()
        currentSkinIndex = skins.indexOf(skinName).coerceAtLeast(0)

        data class SkinColors(val accent: Int, val dim: Int, val title: String, val textColor: Int)

        val colors = when (skinName) {
            "CLASSIC" -> SkinColors(
                ContextCompat.getColor(this, R.color.classic_accent),
                ContextCompat.getColor(this, R.color.classic_accent_dim),
                "WINAMP 5.0",
                ContextCompat.getColor(this, R.color.classic_accent)
            )
            "MATRIX" -> SkinColors(
                ContextCompat.getColor(this, R.color.matrix_accent),
                ContextCompat.getColor(this, R.color.matrix_accent_dim),
                "NEO PLAYER",
                ContextCompat.getColor(this, R.color.matrix_accent)
            )
            "CYBER" -> SkinColors(
                ContextCompat.getColor(this, R.color.cyber_accent),
                ContextCompat.getColor(this, R.color.cyber_accent_dim),
                "CYBER_ROCKET",
                ContextCompat.getColor(this, R.color.cyber_text)
            )
            "GOLD" -> SkinColors(
                ContextCompat.getColor(this, R.color.gold_accent),
                ContextCompat.getColor(this, R.color.gold_accent_dim),
                "LUXURY ROCKET",
                ContextCompat.getColor(this, R.color.gold_text)
            )
            else -> SkinColors(  // ROCKET (default)
                ContextCompat.getColor(this, R.color.rocket_accent),
                ContextCompat.getColor(this, R.color.rocket_accent_dim),
                "ROCKETPLAYER",
                ContextCompat.getColor(this, R.color.rocket_gold)
            )
        }

        // Aplicar a la UI
        binding.titleBar.setBackgroundColor(colors.dim)

        val loggedUser = prefs.getString("logged_username", null)
        val titleText = if (loggedUser.isNullOrEmpty()) colors.title else "${colors.title} • [$loggedUser]"
        binding.appTitleText.apply {
            text = titleText
            setTextColor(colors.textColor)
        }
        binding.playbackTime.setTextColor(colors.accent)
        binding.trackTitle.setTextColor(colors.textColor)
        binding.playlistHeader.setBackgroundColor(colors.dim)
        binding.btnAddStream.backgroundTintList = android.content.res.ColorStateList.valueOf(colors.dim)
        binding.visualizer.setColors(colors.accent, colors.dim)
        binding.btnLogout.imageTintList = android.content.res.ColorStateList.valueOf(colors.textColor)

        // Tint del botón de Spotify: si está conectado permanece en su color original (verde)
        if (::spotifyManager.isInitialized && spotifyManager.isConnected()) {
            binding.btnSpotify.imageTintList = null
        } else {
            binding.btnSpotify.imageTintList = android.content.res.ColorStateList.valueOf(colors.textColor)
        }

        // Actualizar colores del adapter si está inicializado
        if (::trackAdapter.isInitialized) {
            trackAdapter.updateTheme(colors.accent, colors.dim)
        }

        updateUI()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Diálogo de Cerrar Sesión
    // ─────────────────────────────────────────────────────────────────────────

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_logout_title))
            .setMessage(getString(R.string.dialog_logout_message))
            .setPositiveButton(getString(R.string.dialog_logout_confirm)) { _, _ ->
                stopProgressUpdates()
                if (::spotifyManager.isInitialized) {
                    spotifyManager.disconnect()
                }
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                prefs.edit().apply {
                    putBoolean("is_logged_in", false)
                    remove("logged_username")
                    apply()
                }
                controller?.stop()
                val intent = android.content.Intent(this, LoginActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Velocidad del visualizador (ahora controla el visualizador custom)
    // ─────────────────────────────────────────────────────────────────────────

    private fun toggleVisualizerSpeed() {
        // La velocidad se gestiona internamente en VisualizerView,
        // pero mantenemos la opción para el toast (futuro: exponer velocidad)
        val toastText = getString(R.string.toast_speed_turbo)
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView / Playlist
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        trackAdapter = TrackAdapter { track, index ->
            playTrackAtIndex(index)
        }
        binding.recyclerPlaylist.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.recyclerPlaylist.adapter = trackAdapter
        trackAdapter.submitList(playlist.toList())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reproducción — Modo Híbrido (ExoPlayer para Radio / Spotify para Streaming)
    // ─────────────────────────────────────────────────────────────────────────

    private fun playTrackAtIndex(index: Int) {
        if (index < 0 || index >= playlist.size) return
        val track = playlist[index]
        currentTrackIndex = index
        trackAdapter.setCurrentTrack(index)

        if (track.isSpotify) {
            // Activar modo Spotify
            isSpotifyMode = true
            controller?.pause()
            stopProgressUpdates()
            binding.tvSpotifyBadge.visibility = android.view.View.VISIBLE
            binding.trackTitle.text = "${track.title} — ${track.artist}"

            if (!spotifyManager.isConnected()) {
                Toast.makeText(this, getString(R.string.spotify_status_connecting), Toast.LENGTH_SHORT).show()
                spotifyManager.connect(
                    activity = this,
                    onSuccess = {
                        runOnUiThread {
                            spotifyManager.play(track.uri, this)
                        }
                    },
                    onFailure = {
                        runOnUiThread {
                            showSpotifyDialog()
                        }
                    }
                )
            } else {
                spotifyManager.play(track.uri, this)
            }
        } else {
            // Modo Radio / ExoPlayer
            isSpotifyMode = false
            if (::spotifyManager.isInitialized) {
                spotifyManager.pause()
            }
            binding.tvSpotifyBadge.visibility = android.view.View.GONE
            val player = controller ?: return

            // Mapear el índice relativo en la lista de pistas que maneja ExoPlayer
            val radioIndex = playlist.take(index + 1).count { !it.isSpotify } - 1
            if (radioIndex >= 0 && radioIndex < player.mediaItemCount) {
                player.seekToDefaultPosition(radioIndex)
                player.play()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Controles de reproducción
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupControls() {
        binding.btnPlay.setOnClickListener {
            if (isSpotifyMode) {
                controller?.pause()
                spotifyManager.resume()
            } else {
                if (::spotifyManager.isInitialized) spotifyManager.pause()
                controller?.play()
            }
        }
        binding.btnPause.setOnClickListener {
            if (isSpotifyMode) {
                spotifyManager.pause()
            } else {
                controller?.pause()
            }
        }
        binding.btnStop.setOnClickListener {
            if (isSpotifyMode) {
                spotifyManager.pause()
                binding.visualizer.stopAnimation()
                isSpotifyMode = false
                binding.tvSpotifyBadge.visibility = android.view.View.GONE
            }
            controller?.stop()
        }
        binding.btnPrev.setOnClickListener {
            if (isSpotifyMode) {
                spotifyManager.skipPrevious()
            } else {
                controller?.seekToPrevious()
            }
        }
        binding.btnNext.setOnClickListener {
            if (isSpotifyMode) {
                spotifyManager.skipNext()
            } else {
                controller?.seekToNext()
            }
        }

        binding.btnShuffle.setOnCheckedChangeListener { _, isChecked ->
            if (isSpotifyMode) {
                spotifyManager.setShuffle(isChecked)
            } else {
                controller?.shuffleModeEnabled = isChecked
            }
        }
        binding.btnRepeat.setOnCheckedChangeListener { _, isChecked ->
            if (isSpotifyMode) {
                spotifyManager.toggleRepeat()
            } else {
                controller?.repeatMode =
                    if (isChecked) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
        }

        // Volume
        binding.volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) controller?.volume = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Balance (comentario real: necesita AudioProcessor para implementación completa)
        binding.balanceBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // SeekBar — Soporta tanto posición de ExoPlayer como de Spotify
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.playbackTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeekInProgress = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeekInProgress = false
                seekBar?.let {
                    if (isSpotifyMode) {
                        spotifyManager.seekTo(it.progress.toLong())
                    } else {
                        controller?.seekTo(it.progress.toLong())
                    }
                }
            }
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ciclo de vida — Media3 Controller
    // ─────────────────────────────────────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            val player = controller ?: return@addListener

            if (player.mediaItemCount == 0) {
                // Solo cargar en ExoPlayer las pistas de radio/HTTP (las de Spotify las gestiona SpotifyManager)
                val radioTracks = playlist.filter { !it.isSpotify }
                val mediaItems = radioTracks.map { track ->
                    MediaItem.Builder()
                        .setUri(track.uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .build()
                        )
                        .build()
                }
                player.setMediaItems(mediaItems)
                player.prepare()
            }

            player.addListener(object : Player.Listener {
                // BUG CORREGIDO: cancelar el visualizador cuando la reproducción se pause/detenga
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isSpotifyMode) return
                    if (isPlaying) {
                        binding.visualizer.startAnimation()
                        startProgressUpdates()
                        hideStatus()
                    } else {
                        binding.visualizer.stopAnimation()
                        stopProgressUpdates()
                    }
                    updateUI()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (!isSpotifyMode) {
                        val exoIndex = controller?.currentMediaItemIndex ?: -1
                        var count = 0
                        var fullIndex = -1
                        for (i in playlist.indices) {
                            if (!playlist[i].isSpotify) {
                                if (count == exoIndex) {
                                    fullIndex = i
                                    break
                                }
                                count++
                            }
                        }
                        currentTrackIndex = fullIndex
                        trackAdapter.setCurrentTrack(currentTrackIndex)
                        updateUI()
                    }
                }

                // BUG NUEVO: Manejo de errores de reproducción/red
                override fun onPlayerError(error: PlaybackException) {
                    stopProgressUpdates()
                    val msg = when {
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                            getString(R.string.error_network)
                        else -> getString(R.string.error_playback)
                    }
                    showStatus(msg)
                    binding.visualizer.stopAnimation()
                }
            })

            updateUI()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        stopProgressUpdates()
        // BUG CORREGIDO: detener el visualizador al ir al background
        binding.visualizer.stopAnimation()
        MediaController.releaseFuture(controllerFuture)
        super.onStop()
    }

    override fun onDestroy() {
        stopProgressUpdates()
        if (::spotifyManager.isInitialized) {
            spotifyManager.disconnect()
        }
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Update
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateUI() {
        if (isSpotifyMode) return // La interfaz de Spotify se gestiona mediante onPlayerStateChanged
        val player = controller ?: return
        val metadata = player.currentMediaItem?.mediaMetadata

        val defaultTitle = if (skins[currentSkinIndex] == "CLASSIC") "WINAMP: READY"
                           else getString(R.string.no_track)
        binding.trackTitle.text = metadata?.title ?: defaultTitle

        // SeekBar — respetar si el usuario está arrastrando
        val duration = player.duration
        if (!userSeekInProgress) {
            if (duration > 0 && duration < Long.MAX_VALUE / 2) {
                binding.seekBar.max = duration.toInt()
                binding.seekBar.progress = player.currentPosition.toInt()
                binding.seekBar.isEnabled = true
            } else {
                binding.seekBar.max = 100
                binding.seekBar.progress = 0
                binding.seekBar.isEnabled = false
            }
            binding.playbackTime.text = formatTime(player.currentPosition)
        }

        // Info de bitrate / live stream
        binding.bitrateInfo.text = if (duration < 0 || duration > Long.MAX_VALUE / 2) "LIVE"
                                   else "128 kbps"

        // Indicadores STEREO / MONO
        val accentColor = getAccentColor()
        val dimColor = ContextCompat.getColor(this, R.color.rocket_text_dim)

        binding.tvStereo.setTextColor(if (player.isPlaying) accentColor else dimColor)
        binding.tvMono.setTextColor(dimColor)

        // Visualizador: solo iniciar si está reproduciendo (el stop lo maneja el listener)
        if (player.isPlaying && !binding.visualizer.isAnimating()) {
            binding.visualizer.startAnimation()
        }
    }

    private fun getAccentColor(): Int = when (skins[currentSkinIndex]) {
        "CLASSIC" -> ContextCompat.getColor(this, R.color.classic_accent)
        "MATRIX"  -> ContextCompat.getColor(this, R.color.matrix_accent)
        "CYBER"   -> ContextCompat.getColor(this, R.color.cyber_accent)
        "GOLD"    -> ContextCompat.getColor(this, R.color.gold_accent)
        else      -> ContextCompat.getColor(this, R.color.rocket_accent)
    }

    private fun showStatus(message: String) {
        binding.tvStatus.text = message
        binding.tvStatus.visibility = android.view.View.VISIBLE
    }

    private fun hideStatus() {
        binding.tvStatus.visibility = android.view.View.GONE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        // Permiso de audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            // Notificaciones (Android 13+)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    /**
     * Recibe la respuesta del flujo de autenticación de Spotify.
     * Al concederse el token de autorización, conecta automáticamente el SpotifyAppRemote.
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SpotifyManager.REQUEST_CODE_SPOTIFY_AUTH) {
            val response = com.spotify.sdk.android.auth.AuthorizationClient.getResponse(resultCode, data)
            when (response.type) {
                com.spotify.sdk.android.auth.AuthorizationResponse.Type.TOKEN -> {
                    spotifyManager.connect(
                        activity = this,
                        onSuccess = {
                            runOnUiThread {
                                Toast.makeText(this, getString(R.string.spotify_toast_connected), Toast.LENGTH_SHORT).show()
                                if (currentTrackIndex in playlist.indices && playlist[currentTrackIndex].isSpotify) {
                                    spotifyManager.play(playlist[currentTrackIndex].uri, this)
                                }
                            }
                        },
                        onFailure = { error ->
                            runOnUiThread {
                                showStatus("Spotify: ${error.localizedMessage}")
                            }
                        }
                    )
                }
                com.spotify.sdk.android.auth.AuthorizationResponse.Type.ERROR -> {
                    showStatus("Error auth Spotify: ${response.error}")
                }
                else -> {
                    // Cancelado por el usuario
                }
            }
        }
    }

    companion object {
        private const val REQ_PERMISSIONS = 100
    }
}
