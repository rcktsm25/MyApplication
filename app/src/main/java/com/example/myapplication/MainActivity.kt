package com.example.myapplication

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null

    private lateinit var trackTitle: TextView
    private lateinit var playbackTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvStereo: TextView
    private lateinit var tvMono: TextView
    private var currentSkinIndex = 0
    private val skins = listOf("ROCKET", "CLASSIC", "MATRIX", "CYBER", "GOLD")
    private val handler = Handler(Looper.getMainLooper())
    private var visualizerSpeed = 80L

    private val playlist = mutableListOf(
        Track("SomaFM: Groove Salad", "Ambient/Downtempo", "https://somafm.com/groovesalad130.mp3"),
        Track("SomaFM: DEF CON Radio", "Music for Hackers", "https://somafm.com/defcon130.mp3"),
        Track("Lofi Girl", "Study Beats", "https://stream.zeno.fm/0r0xa792kwzuv"),
        Track("KEXP Seattle", "Indie Radio", "https://kexp-mp3-128.streamguys1.com/kexp128.mp3"),
        Track("Jazz24", "Classic Jazz", "https://live.jazz24.org/jazz24-mp3")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        trackTitle = findViewById(R.id.track_title)
        playbackTime = findViewById(R.id.playback_time)
        seekBar = findViewById(R.id.seek_bar)
        recyclerView = findViewById(R.id.recycler_playlist)
        tvStereo = findViewById(R.id.tv_stereo)
        tvMono = findViewById(R.id.tv_mono)

        // Enable Marquee
        trackTitle.isSelected = true

        findViewById<ImageButton>(R.id.btn_theme).setOnClickListener { toggleSkin() }
        findViewById<ImageButton>(R.id.btn_speed).setOnClickListener { toggleVisualizerSpeed() }
        findViewById<ImageButton>(R.id.btn_add_stream).setOnClickListener { showAddStreamDialog() }

        // Load saved skin
        val savedSkin = getSharedPreferences("RocketSettings", MODE_PRIVATE).getString("selected_skin", "ROCKET") ?: "ROCKET"
        applySkin(savedSkin)

        setupRecyclerView()
        setupControls()
        checkPermissions()
    }

    private fun showAddStreamDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Add Streaming Station")
        
        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.setPadding(32, 16, 32, 16)

        val nameInput = android.widget.EditText(this)
        nameInput.hint = "Station Name (e.g. My Radio)"
        container.addView(nameInput)

        val urlInput = android.widget.EditText(this)
        urlInput.hint = "Streaming URL (https://...)"
        container.addView(urlInput)

        builder.setView(container)

        builder.setPositiveButton("CONNECT") { _, _ ->
            val name = nameInput.text.toString().ifEmpty { "Custom Stream" }
            val url = urlInput.text.toString()
            if (url.isNotEmpty()) {
                val newTrack = Track(name, "User Added", url)
                playlist.add(newTrack)
                recyclerView.adapter?.notifyItemInserted(playlist.size - 1)
                
                controller?.addMediaItem(
                    MediaItem.Builder()
                        .setUri(url)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(name).setArtist("Streaming").build())
                        .build()
                )
            }
        }
        builder.setNegativeButton("CANCEL", null)
        builder.show()
    }

    private fun toggleSkin() {
        currentSkinIndex = (currentSkinIndex + 1) % skins.size
        applySkin(skins[currentSkinIndex])
    }

    private fun toggleVisualizerSpeed() {
        visualizerSpeed = when(visualizerSpeed) {
            80L -> 40L // Turbo
            40L -> 150L // Chill
            else -> 80L // Normal
        }
        val speedName = when(visualizerSpeed) {
            40L -> "TURBO"
            150L -> "CHILL"
            else -> "NORMAL"
        }
        android.widget.Toast.makeText(this, "Visualizer Speed: $speedName", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun applySkin(skinName: String) {
        val prefs = getSharedPreferences("RocketSettings", MODE_PRIVATE)
        prefs.edit().putString("selected_skin", skinName).apply()

        val accentColor: Int
        val dimColor: Int
        val titleText: String
        val textColor: Int

        when (skinName) {
            "CLASSIC" -> {
                accentColor = ContextCompat.getColor(this, R.color.classic_accent)
                dimColor = ContextCompat.getColor(this, R.color.classic_accent_dim)
                titleText = "WINAMP 5.0"
                textColor = accentColor
            }
            "MATRIX" -> {
                accentColor = ContextCompat.getColor(this, R.color.matrix_accent)
                dimColor = ContextCompat.getColor(this, R.color.matrix_accent_dim)
                titleText = "NEO PLAYER"
                textColor = accentColor
            }
            "CYBER" -> {
                accentColor = ContextCompat.getColor(this, R.color.cyber_accent)
                dimColor = ContextCompat.getColor(this, R.color.cyber_accent_dim)
                titleText = "CYBER_ROCKET"
                textColor = ContextCompat.getColor(this, R.color.cyber_text)
            }
            "GOLD" -> {
                accentColor = ContextCompat.getColor(this, R.color.gold_accent)
                dimColor = ContextCompat.getColor(this, R.color.gold_accent_dim)
                titleText = "LUXURY ROCKET"
                textColor = ContextCompat.getColor(this, R.color.gold_text)
            }
            else -> { // ROCKET
                accentColor = ContextCompat.getColor(this, R.color.rocket_accent)
                dimColor = ContextCompat.getColor(this, R.color.rocket_accent_dim)
                titleText = "ROCKETPLAYER"
                textColor = ContextCompat.getColor(this, R.color.rocket_gold)
            }
        }

        // Apply to UI
        findViewById<View>(R.id.title_bar).setBackgroundColor(dimColor)
        findViewById<TextView>(R.id.app_title_text).apply {
            text = titleText
            setTextColor(textColor)
        }
        
        playbackTime.setTextColor(accentColor)
        trackTitle.setTextColor(textColor)
        findViewById<View>(R.id.playlist_header).setBackgroundColor(dimColor)
        
        // Save the index for next toggle
        currentSkinIndex = skins.indexOf(skinName)
        
        updateUI()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = TrackAdapter(playlist) { track ->
            playTrack(track)
        }
    }

    private fun playTrack(track: Track) {
        val player = controller ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build())
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    private fun setupControls() {
        findViewById<ImageButton>(R.id.btn_play).setOnClickListener { controller?.play() }
        findViewById<ImageButton>(R.id.btn_pause).setOnClickListener { controller?.pause() }
        findViewById<ImageButton>(R.id.btn_stop).setOnClickListener { controller?.stop() }
        findViewById<ImageButton>(R.id.btn_prev).setOnClickListener { controller?.seekToPrevious() }
        findViewById<ImageButton>(R.id.btn_next).setOnClickListener { controller?.seekToNext() }

        val shuffleBtn = findViewById<android.widget.ToggleButton>(R.id.btn_shuffle)
        shuffleBtn.setOnCheckedChangeListener { _, isChecked ->
            controller?.shuffleModeEnabled = isChecked
        }

        val repeatBtn = findViewById<android.widget.ToggleButton>(R.id.btn_repeat)
        repeatBtn.setOnCheckedChangeListener { _, isChecked ->
            controller?.repeatMode = if (isChecked) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }

        val volumeBar = findViewById<SeekBar>(R.id.volume_bar)
        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyVolumeAndBalance()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val balanceBar = findViewById<SeekBar>(R.id.balance_bar)
        balanceBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyVolumeAndBalance()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) controller?.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            val player = controller
            if (player != null) {
                if (player.mediaItemCount == 0) {
                    // Load initial playlist into controller
                    val mediaItems = playlist.map { track ->
                        MediaItem.Builder()
                            .setUri(track.uri)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build())
                            .build()
                    }
                    player.setMediaItems(mediaItems)
                    player.prepare()
                }
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) startTimer()
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateUI()
                    }
                })
            }
            updateUI()
        }, MoreExecutors.directExecutor())
    }

    private fun startTimer() {
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                updateUI()
                handler.postDelayed(this, visualizerSpeed)
            }
        })
    }

    override fun onStop() {
        MediaController.releaseFuture(controllerFuture)
        super.onStop()
    }

    private fun applyVolumeAndBalance() {
        val player = controller ?: return
        val volume = findViewById<SeekBar>(R.id.volume_bar).progress / 100f
        // In a full implementation, we would use an AudioProcessor for balance
        player.volume = volume
    }

    private fun updateUI() {
        val player = controller ?: return
        val metadata = player.currentMediaItem?.mediaMetadata
        
        val defaultTitle = if (skins[currentSkinIndex] == "CLASSIC") "WINAMP: READY" else getString(R.string.no_track)
        trackTitle.text = metadata?.title ?: defaultTitle
        
        // Handle streaming duration (Media3 returns Long.MAX_VALUE or C.TIME_UNSET for live)
        val duration = player.duration
        if (duration > 0 && duration < Long.MAX_VALUE / 2) {
            seekBar.max = duration.toInt()
            seekBar.progress = player.currentPosition.toInt()
            seekBar.isEnabled = true
        } else {
            seekBar.max = 0
            seekBar.progress = 0
            seekBar.isEnabled = false // Disable seeking for live streams
        }
        
        playbackTime.text = formatTime(player.currentPosition)

        val bitrateInfo = findViewById<TextView>(R.id.bitrate_info)
        if (duration < 0 || duration > Long.MAX_VALUE / 2) {
            bitrateInfo.text = "LIVE STREAM"
        } else {
            bitrateInfo.text = "FLAC 24-bit / 96kHz"
        }

        // Status Indicators (Mocked for effect)
        val accentColor: Int
        val dimColor = ContextCompat.getColor(this, R.color.rocket_text_dim)

        accentColor = when(skins[currentSkinIndex]) {
            "CLASSIC" -> ContextCompat.getColor(this, R.color.classic_accent)
            "MATRIX" -> ContextCompat.getColor(this, R.color.matrix_accent)
            "CYBER" -> ContextCompat.getColor(this, R.color.cyber_accent)
            "GOLD" -> ContextCompat.getColor(this, R.color.gold_accent)
            else -> ContextCompat.getColor(this, R.color.rocket_accent)
        }
        
        tvStereo.setTextColor(if (player.isPlaying) accentColor else dimColor)
        tvMono.setTextColor(dimColor)

        if (player.isPlaying) {
            animateVisualizer(accentColor)
        } else {
            resetVisualizer()
        }
    }

    private fun animateVisualizer(color: Int) {
        val visualizer = findViewById<ViewGroup>(R.id.visualizer) ?: return
        for (i in 0 until visualizer.childCount) {
            val bar = visualizer.getChildAt(i)
            bar.setBackgroundColor(color)
            val params = bar.layoutParams
            val minHeight = 2
            val maxHeight = 58
            params.height = (minHeight..maxHeight).random().toPx()
            bar.layoutParams = params
        }
    }

    private fun resetVisualizer() {
        val visualizer = findViewById<ViewGroup>(R.id.visualizer) ?: return
        val dimColor = ContextCompat.getColor(this, R.color.rocket_text_dim)
        for (i in 0 until visualizer.childCount) {
            val bar = visualizer.getChildAt(i)
            bar.setBackgroundColor(dimColor)
            val params = bar.layoutParams
            params.height = 2.toPx()
            bar.layoutParams = params
        }
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 100)
        }
    }
}
