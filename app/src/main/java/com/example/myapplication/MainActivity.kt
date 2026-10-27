package com.example.myapplication

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

@UnstableApi
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var spotify: SpotifyManager
    private lateinit var browser: SpotifyLibraryBrowser
    private lateinit var panels: PlayerPanels
    private lateinit var localAdapter: TrackAdapter
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: MediaController? = null
    private val localTracks = mutableListOf<Track>()
    private var localFolders = emptyList<MusicFolder>()
    private var selectedFolder: String? = null
    private lateinit var expandedVisualizer: ExpandedVisualizer
    private val folderWorker = Executors.newSingleThreadExecutor()
    private var folderTask: Future<*>? = null
    private var folderGeneration = 0
    private var libraryReady = false
    private var selectedTab = 0
    private var spotifyMode = false
    private var playbackRequest = 0
    private var pendingLocal: Track? = null
    private var active = false
    private var seeking = false
    private var volumeTracking = false
    private var spotifyPosition = 0L
    private var spotifyDuration = 0L
    private var spotifyUpdatedAt = 0L
    private var spotifyPlaying = false
    private var spotifyShuffle = false
    private var spotifyRepeat = 0
    private var spotifyTrackUri: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val progress = object : Runnable {
        override fun run() { if (active) { renderPlayback(); handler.postDelayed(this, 250) } }
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("music_folder_uri", uri.toString()).apply()
            loadFolder(uri)
        } catch (_: SecurityException) { status(getString(R.string.folder_permission_error)) }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val catalogAuthorization = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            browser.authorized(); status("Biblioteca conectada. Ya puedes elegir una canción.")
        } else status(result.data?.getStringExtra("error") ?: "Conexión cancelada. Puedes seguir usando tu música local.")
    }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { if (!spotifyMode) { renderLocalMetadata(); renderPlayback() } }
        override fun onPlayerError(error: PlaybackException) {
            if (!spotifyMode) status(if (error.errorCode in listOf(PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)) getString(R.string.folder_read_error)
                else "No se pudo reproducir este archivo: ${error.errorCodeName}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Features integradas (12 módulos)
    // ═══════════════════════════════════════════════════════════════════════
    private val queueManager = QueueManager()
    private lateinit var queueAdapter: QueueAdapter
    private lateinit var crossfadeManager: CrossfadeManager
    private lateinit var metadataEditor: MetadataEditor
    private lateinit var replayGainProcessor: ReplayGainProcessor
    private lateinit var systemThemeHandler: SystemThemeHandler
    private lateinit var configBackupManager: ConfigBackupManager
    private lateinit var displayGestureHandler: DisplayGestureHandler
    private var oscilloscopeView: OscilloscopeView? = null
    private var particleVisualizer: ParticleVisualizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        androidx.core.view.WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val padding = (8 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            binding.playerUnit.visibility = if (insets.isVisible(WindowInsetsCompat.Type.ime())) View.GONE else View.VISIBLE
            insets
        }
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            binding.main.orientation = LinearLayout.HORIZONTAL
            binding.playerUnit.layoutParams = LinearLayout.LayoutParams(0, -1, 0.46f)
            binding.libraryUnit.layoutParams = LinearLayout.LayoutParams(0, -1, 0.54f).apply { marginStart = padding }
        }
        prefs = getSharedPreferences("RocketSettings", MODE_PRIVATE)
        if (!prefs.getBoolean("songs_only_v2", false)) {
            prefs.edit().apply {
                remove("logged_username"); remove("is_logged_in")
                for (i in 0 until prefs.getInt("saved_stream_count", 0)) {
                    remove("stream_title_$i"); remove("stream_artist_$i"); remove("stream_uri_$i")
                }
                remove("saved_stream_count"); putBoolean("songs_only_v2", true)
            }.apply()
        }
        spotifyMode = savedInstanceState?.getBoolean("spotify_mode") ?: prefs.getBoolean("spotify_mode", false)

        // ── Inicializar features integradas ──
        crossfadeManager = CrossfadeManager(prefs) { player }
        metadataEditor = MetadataEditor(this)
        replayGainProcessor = ReplayGainProcessor(prefs)
        systemThemeHandler = SystemThemeHandler(this, prefs)
        configBackupManager = ConfigBackupManager(this)
        displayGestureHandler = DisplayGestureHandler(
            onSwipeUp = { status("Subiendo volumen") },
            onSwipeDown = { status("Bajando volumen") },
            onDoubleTap = { expandedVisualizer.show() },
            onLongPress = { showMetadataEditor() },
            onVolumeChanged = { vol ->
                val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                    (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * vol / 100f).toInt(), 0)
            }
        )

        // Cola de reproducción
        queueAdapter = QueueAdapter(
            onTrackClick = { track, _ -> playLocal(track) },
            onRemoveTrack = { position ->
                val track = queueManager.getQueue().getOrNull(position)
                track?.let { queueManager.removeFromQueue(position) }
            }
        )
        queueManager.setOnQueueChangedListener { tracks, index ->
            queueAdapter.submitList(tracks)
            queueAdapter.setCurrentTrack(index)
        }
        binding.recyclerQueue?.layoutManager = LinearLayoutManager(this)
        binding.recyclerQueue?.adapter = queueAdapter
        val touchHelper = ItemTouchHelper(QueueTouchHelper(
            onMove = { from, to -> queueManager.moveItem(from, to) },
            onSwipe = { position -> queueManager.removeFromQueue(position) }
        ))
        binding.recyclerQueue?.let { touchHelper.attachToRecyclerView(it) }

        // Gestos en el display
        displayGestureHandler.attachToView(binding.meterRow,
            getVolume = { binding.volumeBar.progress },
            maxVolume = 100)

        // Tema del sistema
        if (systemThemeHandler.isAutoThemeEnabled()) {
            systemThemeHandler.applySystemThemeIfEnabled(
                applyBackground = { binding.main.setBackgroundColor(it) },
                applyTextColor = { binding.trackTitle.setTextColor(it) }
            )
        }

        spotify = SpotifyManager(this)
        panels = PlayerPanels(this, prefs)
        expandedVisualizer = ExpandedVisualizer(this, prefs) { applyAppearance(); if (selectedTab == 3) showTab(3) }
        panels.onExpandVisualizer = { expandedVisualizer.show() }
        panels.onThemeApplied = { showTab(3) }
        binding.meterRow.setOnClickListener { expandedVisualizer.show() }
        binding.meterRow.contentDescription = "Ampliar visualizador local a pantalla completa"
        binding.meterRow.isFocusable = true
        selectedFolder = prefs.getString("selected_music_folder", null)
        localAdapter = TrackAdapter { track, _ -> playLocal(track) }
        binding.recyclerPlaylist.layoutManager = LinearLayoutManager(this)
        binding.recyclerPlaylist.adapter = localAdapter
        browser = SpotifyLibraryBrowser(this, binding, ::authorizeCatalog, ::playSpotifySong)
        bindSpotify()
        bindControls()
        binding.trackTitle.isSelected = true
        binding.tabLocal.setOnClickListener { showTab(0) }
        binding.tabSpotify.setOnClickListener { showTab(1) }
        binding.tabEq.setOnClickListener { showTab(2) }
        binding.tabSettings.setOnClickListener { showTab(3) }
        binding.tabQueue?.setOnClickListener { showTab(4) }
        binding.btnFolder.setOnClickListener { chooseFolder() }
        binding.btnExplore.setOnClickListener {
            if (localFolders.isEmpty()) chooseFolder() else LocalFolderBrowser.show(this, localFolders, selectedFolder) {
                selectedFolder = it; prefs.edit().putString("selected_music_folder", it).apply(); filterLocal()
            }
        }
        binding.btnSortLocal.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Ordenar canciones").setSingleChoiceItems(SongOrder.names.toTypedArray(), prefs.getInt("local_sort", 0)) { dialog, which ->
                prefs.edit().putInt("local_sort", which).apply(); filterLocal(); dialog.dismiss()
            }.setNegativeButton("Cancelar", null).show()
        }
        binding.btnFolder.setOnLongClickListener { refreshFolder(); true }
        binding.localSearch.doAfterTextChanged { filterLocal() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedTab != 0) showTab(0) else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
        applyAppearance()
        showTab(savedInstanceState?.getInt("tab") ?: 0)
        prefs.getString("music_folder_uri", null)?.let { loadFolder(Uri.parse(it)) }
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun showTab(tab: Int) {
        selectedTab = tab
        updateNavigation()
        binding.sectionTitle.text = listOf("BIBLIOTECA LOCAL", "CANCIONES · SPOTIFY", "ECUALIZADOR", "CONFIGURACIÓN", "COLA")[tab]
        // Animación de transición entre pestañas (fade-in suave)
        fadeIn(binding.localPanel, tab == 0)
        if (tab == 1) {
            fadeIn(binding.spotifyBrowser, true)
            showSpotifyVisualizer()
        } else {
            fadeIn(binding.spotifyBrowser, false)
            hideSpotifyVisualizer()
        }
        fadeIn(binding.eqPanel, tab == 2)
        fadeIn(binding.settingsPanel, tab == 3)
        if (tab == 4) {
            updateQueuePanel()
            fadeIn(binding.queuePanel, true)
        } else {
            fadeIn(binding.queuePanel, false)
        }
        if (tab == 2) panels.equalizer(binding.eqContent, spotifyMode, ::openSpotify)
        if (tab == 3) panels.settings(binding.settingsContent, spotifyMode, ::configureSpotify, ::disconnectSpotify,
            ::chooseFolder, ::refreshFolder, ::applyAppearance, { player?.playbackParameters = PlaybackParameters(it) },
            ::exportConfig, ::importConfig, ::showGroupedLibrary)
        PlayerAppearance.applySurfaces(binding.main, prefs)
    }

    // Animación de fade-in/fade-out para transiciones suaves entre pestañas
    private fun fadeIn(view: View?, visible: Boolean) {
        if (view == null) return
        if (visible) {
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(150).start()
        } else {
            view.animate().alpha(0f).setDuration(100).withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f // Reset para próxima vez
            }.start()
        }
    }

    private fun updateNavigation() {
        val tabs = listOfNotNull(binding.tabLocal, binding.tabSpotify, binding.tabEq, binding.tabSettings, binding.tabQueue)
        tabs.forEachIndexed { index, button ->
            val color = if (index == selectedTab) accent() else 0xFF203044.toInt()
            button.isSelected = index == selectedTab
            button.setTint(color)
        }
    }

    private fun accent() = PlayerAppearance.accent(prefs)

    private fun applyAppearance() {
        binding.main.setBackgroundColor(PlayerAppearance.background(prefs))
        PlayerAppearance.applySurfaces(binding.main, prefs)
        binding.playbackTime.setTextColor(accent())
        binding.btnPlay.backgroundTintList = null
        binding.trackTitle.setTextColor(accent())
        listOf(binding.seekBar, binding.volumeBar).forEach {
            it.progressTintList = android.content.res.ColorStateList.valueOf(accent())
            it.thumbTintList = null
        }
        binding.visualizer.setColors(accent(), 0xFF243017.toInt())
        oscilloscopeView?.setColor(accent())
        localAdapter.updateTheme(accent(), 0xFF657553.toInt())
        if (prefs.getBoolean("keep_screen", false)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        browser.updateTheme(accent())
        fun tintControls(view: View) {
            when (view) {
                is SeekBar -> {
                    view.progressTintList = android.content.res.ColorStateList.valueOf(accent())
                    view.thumbTintList = null
                }
                is androidx.appcompat.widget.SwitchCompat -> {
                    view.thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent(), 0xFF9CAAB7.toInt()))
                    view.trackTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(androidx.core.graphics.ColorUtils.blendARGB(accent(), 0xFF1A2129.toInt(), 0.65f), 0xFF394450.toInt()))
                }
                is VisualizerView -> { view.setColors(accent(), 0); view.configure(prefs) }
                is VerticalFader -> view.color = accent()
            }
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) tintControls(view.getChildAt(i))
        }
        tintControls(binding.main)
        updateNavigation()
        renderPlayback()
    }

    // ── Osciloscopio en pestaña Spotify ──
    private fun showSpotifyVisualizer() {
        if (binding.oscilloscopeContainer?.visibility != View.VISIBLE) {
            binding.oscilloscopeContainer?.visibility = View.VISIBLE
            if (oscilloscopeView == null) {
                oscilloscopeView = OscilloscopeView(this).apply {
                    setColor(accent())
                    (binding.oscilloscopeContainer as? android.view.ViewGroup)?.addView(this,
                        android.widget.LinearLayout.LayoutParams(-1, 120))
                }
            }
            oscilloscopeView?.setActive(!spotifyMode && active)
        }
    }
    private fun hideSpotifyVisualizer() {
        binding.oscilloscopeContainer?.visibility = View.GONE
        oscilloscopeView?.setActive(false)
    }

    private fun chooseFolder() {
        showTab(0)
        folderPicker.launch(prefs.getString("music_folder_uri", null)?.let(Uri::parse))
    }
    private fun refreshFolder() {
        val uri = prefs.getString("music_folder_uri", null)
        if (uri == null) chooseFolder() else loadFolder(Uri.parse(uri))
    }
    private fun loadFolder(uri: Uri) {
        val generation = ++folderGeneration
        folderTask?.cancel(true)
        binding.localMessage.text = "Leyendo carpeta y subcarpetas…"
        binding.localLoading.visibility = View.VISIBLE
        folderTask = folderWorker.submit {
            val result = runCatching { MusicFolderScanner(applicationContext.contentResolver).scanLibrary(uri) }
            runOnUiThread {
                if (isDestroyed || generation != folderGeneration) return@runOnUiThread
                binding.localLoading.visibility = View.GONE
                result.fold({ library ->
                    localTracks.clear(); localTracks.addAll(library.tracks); localFolders = library.folders
                    if (localFolders.none { it.id == selectedFolder }) selectedFolder = null
                    libraryReady = true
                    filterLocal(); syncLocalQueue()
                }, {
                    binding.localMessage.text = "No se puede leer esta carpeta. Pulsa CARPETA y vuelve a seleccionarla."
                })
            }
        }
    }
    private fun visibleLocalTracks(): List<Track> {
        val query = binding.localSearch.text.toString().trim()
        val folder = localFolders.find { it.id == selectedFolder }
        return localTracks.filter {
            (folder == null || it.folderId == folder.id || it.folderPath.startsWith(folder.path + "/")) &&
                (query.isBlank() || listOf(it.title, it.artist, it.fileName, it.folderPath).any { value -> value.contains(query, true) })
        }.sortedWith(SongOrder.comparator(prefs.getInt("local_sort", 0)))
    }
    private fun filterLocal() {
        val visible = visibleLocalTracks()
        localAdapter.submitList(visible) { markLocalTrack() }
        val folderName = localFolders.find { it.id == selectedFolder }?.path ?: "Toda mi música"
        binding.btnSortLocal.text = when (prefs.getInt("local_sort", 0)) { 1 -> "Artista ↑"; 2 -> "Duración ↑"; else -> "Nombre ↑" }
        binding.localMessage.text = when {
            localFolders.isEmpty() -> "Elige una carpeta del teléfono."
            visible.isEmpty() -> "Sin canciones · $folderName"
            else -> "${visible.size} canciones · $folderName"
        }
    }
    private fun markLocalTrack() {
        localAdapter.setCurrentTrack(if (spotifyMode) -1 else localAdapter.currentList.indexOfFirst { it.uri == player?.currentMediaItem?.mediaId })
    }
    private fun syncLocalQueue(queue: List<Track> = localTracks.sortedWith(SongOrder.comparator(prefs.getInt("local_sort", 0)))) {
        val controller = player ?: return
        if (!libraryReady) return
        val old = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
        if (old != queue.map { it.uri }) {
            val currentIndex = queue.indexOfFirst { it.uri == controller.currentMediaItem?.mediaId }
            val saved = if (old.isEmpty()) prefs.getString("resume_local_uri", null)?.let { ResumePoint(it, prefs.getLong("resume_local_position", 0)) } else null
            val restored = restorePoint(queue, saved)
            val index = if (currentIndex >= 0) currentIndex else restored.first
            val position = if (currentIndex >= 0) controller.currentPosition else restored.second
            val resume = currentIndex >= 0 && controller.playWhenReady && !spotifyMode
            controller.pause()
            if (queue.isEmpty()) controller.clearMediaItems()
            else {
                controller.setMediaItems(queue.map {
                    MediaItem.Builder().setMediaId(it.uri).setUri(it.uri).setMediaMetadata(
                        MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).build()).build()
                }, index.coerceAtLeast(0), position)
                if (old.isEmpty()) {
                    controller.shuffleModeEnabled = prefs.getBoolean("resume_local_shuffle", false)
                    controller.repeatMode = prefs.getInt("resume_local_repeat", Player.REPEAT_MODE_OFF)
                }
                controller.prepare(); controller.playWhenReady = resume
            }
        }
        pendingLocal?.let { pendingLocal = null; playLocal(it) }
        // Actualizar cola de reproducción
        val currentIndex = queue.indexOfFirst { it.uri == player?.currentMediaItem?.mediaId }
        queueManager.setQueue(queue, currentIndex.coerceAtLeast(0))
    }
    private fun setSource(useSpotify: Boolean) {
        val changed = spotifyMode != useSpotify
        spotifyMode = useSpotify
        prefs.edit().putBoolean("spotify_mode", useSpotify).apply()
        if (useSpotify) AudioRuntime.sleepUntil = 0
        if (changed && selectedTab in 2..3) showTab(selectedTab)
        markLocalTrack()
    }
    private fun playLocal(track: Track) {
        playbackRequest++
        setSource(false)
        spotify.pause()
        val controller = player
        if (controller == null) { pendingLocal = track; return }
        val queue = visibleLocalTracks()
        val index = queue.indexOfFirst { it.uri == track.uri }
        if (index < 0) return
        syncLocalQueue(queue)
        controller.seekToDefaultPosition(index)
        controller.playbackParameters = PlaybackParameters(prefs.getFloat("playback_speed", 1f))
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
        status("Reproduciendo archivo local")
        // Aplicar ReplayGain si está habilitado
        applyReplayGain(track)
    }
    private fun applyReplayGain(track: Track) {
        if (!replayGainProcessor.isEnabled()) return
        val metadata = metadataEditor.getMetadata(track.uri)
        val gainInfo = ReplayGainProcessor.ReplayGainInfo(
            trackGainDb = prefs.getFloat("replaygain_track_${track.uri}", 0f),
            albumGainDb = prefs.getFloat("replaygain_album_${track.uri}", 0f),
            trackPeak = prefs.getFloat("replaygain_peak_${track.uri}", 1f),
            albumPeak = prefs.getFloat("replaygain_album_peak_${track.uri}", 1f)
        )
        val factor = replayGainProcessor.calculateVolumeFactor(gainInfo)
        player?.volume = factor
    }
    private fun playSpotifySong(track: Track) {
        val request = ++playbackRequest
        pendingLocal = null
        if (!SpotifyManager.isValidClientId(spotify.getClientId())) { configureSpotify(); return }
        player?.pause(); setSource(true)
        binding.trackTitle.text = track.title; binding.trackArtist.text = track.artist
        status("Conectando reproducción de Spotify…")
        spotify.connect(this, onSuccess = {
            runOnUiThread {
                if (!isDestroyed && request == playbackRequest && spotifyMode) {
                    spotify.play(track.uri, this); browser.select(track.uri); status("Reproduciendo en Spotify")
                }
            }
        })
    }
    private fun bindSpotify() {
        spotify.onPlayerStateChanged = { state -> runOnUiThread {
            if (!isDestroyed) {
                spotifyPosition = state.playbackPosition; spotifyDuration = state.track?.duration ?: 0
                spotifyPlaying = !state.isPaused; spotifyUpdatedAt = SystemClock.elapsedRealtime()
                spotifyShuffle = state.playbackOptions.isShuffling; spotifyRepeat = state.playbackOptions.repeatMode
                spotifyTrackUri = state.track?.uri
                if (spotifyMode) {
                    binding.trackTitle.text = state.track?.name ?: "Spotify"
                    binding.trackArtist.text = state.track?.artist?.name.orEmpty()
                    browser.select(spotifyTrackUri); renderPlayback()
                    // Actualizar espectro para visualizadores
                    state.track?.let { track ->
                        AudioRuntime.spectrum = FloatArray(28) { 0.1f + (it * 0.02f) }
                        oscilloscopeView?.setWaveform(AudioRuntime.waveform)
                    }
                }
            }
        } }
        spotify.onConnectionStatusChanged = { connected -> runOnUiThread {
            if (!isDestroyed && !connected && spotifyMode) { spotifyPlaying = false; renderPlayback() }
        } }
        spotify.onError = { error -> runOnUiThread {
            if (!isDestroyed) status("Spotify: ${error.localizedMessage ?: "No se pudo conectar"}")
        } }
    }
    private fun authorizeCatalog() {
        if (!SpotifyManager.isValidClientId(spotify.getClientId())) { configureSpotify(); return }
        catalogAuthorization.launch(Intent(this, SpotifyWebAuthActivity::class.java))
    }
    private fun configureSpotify() {
        val input = EditText(this).apply { setText(spotify.getClientId()); hint = "Client ID de Spotify"; isSingleLine = true }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(36, 20, 36, 12)
            addView(TextView(this@MainActivity).apply {
                text = "Conexión: ${if (spotify.isConnected()) "activa" else "desconectada"}\n\nConfiguración de Spotify Developer:\nPaquete: $packageName\nRedirect: ${SpotifyManager.REDIRECT_URI}\nSHA-1: ${spotify.getSigningFingerprints()}\n\nActiva Web API para buscar canciones."
                textSize = 12f; setTextIsSelectable(true)
            })
            addView(input)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Conexión de Spotify").setView(container)
            .setPositiveButton("Guardar y conectar", null).setNegativeButton("Cancelar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val id = input.text.toString().trim()
                if (!SpotifyManager.isValidClientId(id)) { input.error = getString(R.string.spotify_invalid_client_id); return@setOnClickListener }
                if (id != spotify.getClientId()) { SpotifyWebApi(this).disconnect(); browser.clear() }
                spotify.saveClientId(id); dialog.dismiss(); authorizeCatalog()
            }
        }
        dialog.show()
    }
    private fun disconnectSpotify() {
        playbackRequest++; spotify.pause(); spotify.disconnect(); SpotifyWebApi(this).disconnect(); browser.clear()
        setSource(false); renderLocalMetadata(); status("Spotify desconectado de RocketPlayer")
    }
    private fun openSpotify() {
        val intent = packageManager.getLaunchIntentForPackage("com.spotify.music")
        if (intent != null) startActivity(intent) else spotify.openSpotifyInPlayStore()
    }

    // ── Cola de reproducción ──
    private fun updateQueuePanel() {
        val tracks = queueManager.getQueue()
        val index = queueManager.getCurrentIndex()
        queueAdapter.submitList(tracks)
        queueAdapter.setCurrentTrack(index)
        binding.queueInfo?.text = "${tracks.size} canciones${if (index >= 0) " · Reproduciendo: ${tracks.getOrNull(index)?.title}" else ""}"
    }

    // ── Metadatos ──
    private fun showMetadataEditor() {
        val currentTrack = player?.currentMediaItem?.let { item ->
            localTracks.find { it.uri == item.mediaId }
        }
        if (currentTrack == null) {
            Toast.makeText(this, "No hay canción seleccionada", Toast.LENGTH_SHORT).show()
            return
        }
        val metadata = metadataEditor.getMetadata(currentTrack.uri)
        val dialogView = layoutInflater.inflate(R.layout.dialog_metadata_editor, null)
        dialogView.findViewById<EditText>(R.id.edit_title)?.setText(metadata.title ?: currentTrack.title)
        dialogView.findViewById<EditText>(R.id.edit_artist)?.setText(metadata.artist ?: currentTrack.artist)
        dialogView.findViewById<EditText>(R.id.edit_album)?.setText(metadata.album ?: "")
        dialogView.findViewById<EditText>(R.id.edit_genre)?.setText(metadata.genre ?: "")

        AlertDialog.Builder(this)
            .setTitle(R.string.metadata_edit_title)
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val newMetadata = metadata.copy(
                    title = dialogView.findViewById<EditText>(R.id.edit_title)?.text?.toString(),
                    artist = dialogView.findViewById<EditText>(R.id.edit_artist)?.text?.toString(),
                    album = dialogView.findViewById<EditText>(R.id.edit_album)?.text?.toString(),
                    genre = dialogView.findViewById<EditText>(R.id.edit_genre)?.text?.toString()
                )
                metadataEditor.saveMetadata(newMetadata)
                Toast.makeText(this, R.string.metadata_saved, Toast.LENGTH_SHORT).show()
                renderLocalMetadata()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Biblioteca agrupada ──
    private fun showGroupedLibrary() {
        val dialogView = layoutInflater.inflate(R.layout.panel_grouped_library, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_grouped)
        val headerText = dialogView.findViewById<TextView>(R.id.grouped_title)
        val btnAlbums = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_group_albums)
        val btnArtists = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_group_artists)

        val groupedAdapter = TrackAdapter { track, _ -> playLocal(track) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = groupedAdapter
        groupedAdapter.updateTheme(accent(), 0xFF657553.toInt())

        btnAlbums.setOnClickListener { groupByAlbum(headerText, groupedAdapter) }
        btnArtists.setOnClickListener { groupByArtist(headerText, groupedAdapter) }

        AlertDialog.Builder(this)
            .setTitle("Biblioteca agrupada")
            .setView(dialogView)
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun groupByArtist(header: TextView, adapter: TrackAdapter) {
        val artists = mutableMapOf<String, MutableList<Track>>()
        localTracks.forEach { track ->
            val artist = if (track.artist == "Artista desconocido") "Sin Artista" else track.artist
            artists.getOrPut(artist) { mutableListOf() }.add(track)
        }
        val sorted = artists.entries.sortedBy { it.key }
        header.text = "${sorted.size} artistas · ${localTracks.size} canciones"
        adapter.submitList(sorted.flatMap { it.value.sortedBy { t -> t.title } })
    }

    private fun groupByAlbum(header: TextView, adapter: TrackAdapter) {
        val albums = mutableMapOf<String, MutableList<Track>>()
        localTracks.forEach { track ->
            val album = metadataEditor.getMetadata(track.uri).album ?: "Álbum Desconocido"
            albums.getOrPut(album) { mutableListOf() }.add(track)
        }
        val sorted = albums.entries.sortedBy { it.key }
        header.text = "${sorted.size} álbumes · ${localTracks.size} canciones"
        adapter.submitList(sorted.flatMap { it.value.sortedBy { t -> t.title } })
    }

    private fun groupByFlat(header: TextView, adapter: TrackAdapter) {
        header.text = "${localTracks.size} canciones"
        adapter.submitList(localTracks.sortedWith(SongOrder.comparator(prefs.getInt("local_sort", 0))))
    }

    // ── Export/Import config ──
    private fun exportConfig() {
        val file = java.io.File(getExternalFilesDir(null), "rocketplayer_config_${System.currentTimeMillis()}.json")
        val success = configBackupManager.exportToFile(file)
        if (success) Toast.makeText(this, "Configuración exportada: ${file.name}", Toast.LENGTH_LONG).show()
        else Toast.makeText(this, "Error al exportar configuración", Toast.LENGTH_SHORT).show()
    }

    private fun importConfig() {
        val file = java.io.File(getExternalFilesDir(null), "rocketplayer_config_${System.currentTimeMillis()}.json")
        if (!file.exists()) {
            Toast.makeText(this, "No hay archivo de configuración para importar", Toast.LENGTH_SHORT).show()
            return
        }
        val success = configBackupManager.importFromFile(file)
        if (success) {
            Toast.makeText(this, "Configuración importada. Reinicia la app para aplicar cambios.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Error al importar configuración", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindControls() {
        binding.btnPlay.setOnClickListener {
            val playing = if (spotifyMode) spotifyPlaying && spotify.isConnected() else player?.isPlaying == true
            if (playing) {
                playbackRequest++; pendingLocal = null
                if (spotifyMode) spotify.pause() else player?.pause()
                return@setOnClickListener
            }
            if (spotifyMode) {
                val request = ++playbackRequest
                spotify.connect(this, onSuccess = { if (request == playbackRequest && spotifyMode) spotify.resume() })
            } else {
                val controller = player
                if (controller == null || controller.mediaItemCount == 0) chooseFolder() else {
                    spotify.pause(); if (controller.playbackState == Player.STATE_IDLE) controller.prepare(); controller.play()
                }
            }
        }
        binding.btnStop.setOnClickListener {
            playbackRequest++; pendingLocal = null
            if (spotifyMode) { spotify.pause(); spotify.seekTo(0) } else { player?.pause(); player?.seekTo(0) }
        }
        binding.btnPrev.setOnClickListener { if (spotifyMode) spotify.skipPrevious() else player?.seekToPrevious() }
        binding.btnNext.setOnClickListener { if (spotifyMode) spotify.skipNext() else player?.seekToNext() }
        binding.btnShuffle.setOnClickListener {
            if (spotifyMode) spotify.setShuffle(!spotifyShuffle) else player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        }
        binding.btnRepeat.setOnClickListener {
            if (spotifyMode) spotify.toggleRepeat() else player?.let {
                it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
            }
        }
        binding.volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                        (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * value / 100f).toInt(), 0)
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { volumeTracking = true }
            override fun onStopTrackingTouch(bar: SeekBar?) { volumeTracking = false }
        })
        binding.seekBar.max = 1000
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) binding.playbackTime.text = time(duration() * value / 1000)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(bar: SeekBar?) {
                val position = duration() * (bar?.progress ?: 0) / 1000
                if (spotifyMode) { spotifyPosition = position; spotifyUpdatedAt = SystemClock.elapsedRealtime(); spotify.seekTo(position) }
                else player?.seekTo(position)
                seeking = false
            }
        })
    }
    private fun duration(): Long = (if (spotifyMode) spotifyDuration else player?.duration ?: 0).coerceAtLeast(0)
    private fun renderLocalMetadata() {
        if (spotifyMode) return
        binding.trackTitle.text = player?.mediaMetadata?.title?.toString()?.replace(Regex("(?i)\\.(wav|mp3|m4a|aac|flac|ogg|opus)$"), "") ?: "Todo empieza con una canción"
        binding.trackArtist.text = player?.mediaMetadata?.artist ?: "Elige música del teléfono o de Spotify"
        markLocalTrack()
    }
    private fun renderPlayback() {
        if (!::prefs.isInitialized) return
        val duration = duration()
        val playing = if (spotifyMode) spotifyPlaying && spotify.isConnected() else player?.isPlaying == true
        val position = if (spotifyMode) spotifyPosition + if (playing) SystemClock.elapsedRealtime() - spotifyUpdatedAt else 0
            else player?.currentPosition ?: 0
        if (!seeking) {
            binding.playbackTime.text = time(position.coerceIn(0, duration))
            binding.seekBar.progress = if (duration > 0) (position.coerceIn(0, duration) * 1000 / duration).toInt() else 0
        }
        binding.seekBar.isEnabled = duration > 0 && (!spotifyMode || spotify.isConnected())
        binding.totalTime.text = if (duration > 0) time(duration) else "--:--"
        binding.sourceBadge.text = if (spotifyMode) "● SPOTIFY" else "● DISPOSITIVO"
        binding.sourceBadge.setTextColor(if (playing) accent() else 0xFFB8BCCC.toInt())
        val showSpectrum = active && playing && !spotifyMode && prefs.getBoolean("spectrum", true)
        binding.visualizer.setActive(showSpectrum)
        panels.visualizerPreview?.setActive(showSpectrum && selectedTab == 3)
        expandedVisualizer.update(binding.trackTitle.text.toString(), active && playing, spotifyMode)
        binding.visualizer.visibility = if (!spotifyMode && prefs.getBoolean("spectrum", true)) View.VISIBLE else View.INVISIBLE
        binding.audioInfo.text = if (spotifyMode) { if (playing) "STREAM · ESTÉREO" else "STREAM · EN PAUSA" }
            else if (AudioRuntime.sampleRate > 0) "${AudioRuntime.sampleRate / 1000f} kHz · ${if (AudioRuntime.channels == 1) "Mono" else "Estéreo"}" else "AUDIO LOCAL"
        if (binding.btnPlay.tag != playing) {
            binding.btnPlay.tag = playing
            binding.btnPlay.setImageResource(if (playing) R.drawable.ic_pro_pause else R.drawable.ic_pro_play)
            binding.btnPlay.contentDescription = if (playing) "Pausar" else "Reproducir"
        }
        binding.btnPlay.isSelected = playing
        binding.btnPlay.imageTintList = android.content.res.ColorStateList.valueOf(if (playing) accent() else 0xFF203044.toInt())
        binding.playbackState.text = when { playing -> "▶ PLAY"; duration > 0 -> "Ⅱ PAUSA"; else -> "■ LISTO" }
        binding.playbackState.setTextColor(accent())
        val shuffle = if (spotifyMode) spotifyShuffle else player?.shuffleModeEnabled == true
        binding.btnShuffle.isSelected = shuffle
        binding.btnShuffle.contentDescription = if (shuffle) "Desactivar reproducción aleatoria" else "Activar reproducción aleatoria"
        val repeatLabel = if (spotifyMode) when (spotifyRepeat) { com.spotify.protocol.types.Repeat.ALL -> "TODO"; com.spotify.protocol.types.Repeat.ONE -> "1"; else -> "—" }
            else when (player?.repeatMode) { Player.REPEAT_MODE_ALL -> "TODO"; Player.REPEAT_MODE_ONE -> "1"; else -> "—" }
        if (binding.btnRepeat.tag != repeatLabel) {
            binding.btnRepeat.tag = repeatLabel
            binding.btnRepeat.setImageResource(if (repeatLabel == "1") R.drawable.ic_pro_repeat_one else R.drawable.ic_pro_repeat)
        }
        binding.btnRepeat.isSelected = repeatLabel != "—"
        binding.btnRepeat.contentDescription = "Repetición: ${if (repeatLabel == "—") "desactivada" else if (repeatLabel == "1") "una canción" else "todas las canciones"}. Toca para cambiar"
        listOf(binding.btnShuffle, binding.btnRepeat).forEach {
            val color = if (it.isSelected) accent() else 0xFF203044.toInt()
            it.imageTintList = android.content.res.ColorStateList.valueOf(color)
        }
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        if (!volumeTracking) binding.volumeBar.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 /
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        binding.volumeValue.text = "${binding.volumeBar.progress}%"

        // Actualizar osciloscopio si está visible
        if (spotifyMode && playing) {
            oscilloscopeView?.setWaveform(AudioRuntime.waveform)
        }

        // Crossfade check para audio local
        if (!spotifyMode) {
            crossfadeManager.checkAndStartCrossfade()
        }
    }
    private val dismissStatus = Runnable { binding.tvStatus.visibility = View.GONE }
    private fun status(text: String) {
        binding.tvStatus.text = text; binding.tvStatus.visibility = View.VISIBLE
        handler.removeCallbacks(dismissStatus); handler.postDelayed(dismissStatus, 8000)
    }
    private fun time(ms: Long): String = String.format(Locale.ROOT, "%02d:%02d", ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)

    override fun onStart() {
        super.onStart(); active = true
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        controllerFuture = future
        future.addListener({
            if (!active || controllerFuture !== future) return@addListener
            runCatching { future.get() }.onSuccess { controller ->
                player = controller; controller.addListener(playerListener)
                controller.volume = 1f
                controller.playbackParameters = PlaybackParameters(prefs.getFloat("playback_speed", 1f))
                if ((0 until controller.mediaItemCount).any { !controller.getMediaItemAt(it).mediaId.startsWith("content://") }) controller.clearMediaItems()
                syncLocalQueue(); renderLocalMetadata()
            }.onFailure { status("No se pudo iniciar el motor de audio.") }
        }, MoreExecutors.directExecutor())
        handler.post(progress)
        if (spotifyMode && SpotifyManager.isValidClientId(spotify.getClientId()) && !spotify.isConnected()) spotify.connect(this)
    }
    override fun onStop() {
        active = false; handler.removeCallbacks(progress); binding.visualizer.setActive(false); panels.visualizerPreview?.setActive(false); expandedVisualizer.update(binding.trackTitle.text.toString(), false, spotifyMode)
        oscilloscopeView?.setActive(false)
        player?.removeListener(playerListener); player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }; controllerFuture = null
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", selectedTab); outState.putBoolean("spotify_mode", spotifyMode)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        expandedVisualizer.dismiss()
        handler.removeCallbacksAndMessages(null)
        folderGeneration++; folderTask?.cancel(true); folderWorker.shutdownNow(); browser.destroy()
        spotify.onError = null; spotify.onPlayerStateChanged = null; spotify.onConnectionStatusChanged = null
        spotify.disconnect(); super.onDestroy()
    }
    @Deprecated("Spotify SDK activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SpotifyManager.REQUEST_CODE_SPOTIFY_AUTH) spotify.onAuthorizationResult(this, resultCode, data)
        if (requestCode == SpotifyManager.REQUEST_CODE_SPOTIFY_BROWSER_AUTH) spotify.onBrowserAuthorizationResult(this, resultCode, data)
    }
}
