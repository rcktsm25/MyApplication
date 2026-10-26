package com.example.myapplication

import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.ActivityMainBinding
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Song-level selection, deliberately without playlists or modifications to the Spotify account. */
class SpotifyLibraryBrowser(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val authorize: () -> Unit,
    private val play: (Track) -> Unit
) {
    private val api = SpotifyWebApi(activity)
    private val prefs = activity.getSharedPreferences("RocketSettings", 0)
    private val worker = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private var generation = 0
    private var destroyed = false
    private var loaded = false
    private var busy = false
    private var needsAuthorization = false
    private var accentColor = 0xFFB6EE65.toInt()
    private var query = ""
    private var nextOffset: Int? = null
    private val songs = mutableListOf<Track>()
    private var selectedUri: String? = null
    private val adapter = TrackAdapter { track, _ -> play(track) }

    init {
        adapter.updateTheme(0xFFB6EE65.toInt(), 0xFF727B68.toInt())
        binding.recyclerSpotify.layoutManager = LinearLayoutManager(activity)
        binding.recyclerSpotify.adapter = adapter
        binding.spotifyConnect.setOnClickListener {
            if (api.isAuthorized() && !needsAuthorization) load(false) else authorize()
        }
        binding.spotifySearchButton.setOnClickListener { search() }
        binding.spotifySearch.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
        binding.spotifyLiked.setOnClickListener {
            binding.spotifySearch.setText(""); query = ""; load(false)
        }
        binding.browserMore.setOnClickListener { load(true) }
        binding.spotifySort.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(activity).setTitle("Ordenar canciones cargadas")
                .setSingleChoiceItems(SongOrder.names.toTypedArray(), prefs.getInt("spotify_sort", 0)) { dialog, which ->
                    prefs.edit().putInt("spotify_sort", which).apply(); submitSongs(); dialog.dismiss()
                }.setNegativeButton("Cancelar", null).show()
        }
    }
    fun show() {
        binding.spotifyBrowser.visibility = View.VISIBLE
        updateAction()
        if (!loaded && !busy) load(false)
    }
    fun updateTheme(accent: Int) {
        accentColor = accent
        adapter.updateTheme(accent, 0xFF728391.toInt())
        updateAction()
    }
    private fun updateAction() {
        val likedColor = if (binding.spotifyLiked.isSelected) accentColor else 0xFF203044.toInt()
        binding.spotifyLiked.setTextColor(likedColor)
        binding.spotifyLiked.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(likedColor)
        binding.spotifyConnect.text = if (api.isAuthorized() && !needsAuthorization) "Actualizar" else "Conectar Spotify"
        binding.spotifyConnect.isEnabled = !busy
        binding.spotifyConnect.alpha = if (busy) 0.5f else 1f
        binding.browserLoading.visibility = if (busy) View.VISIBLE else View.GONE
    }
    fun hide() { binding.spotifyBrowser.visibility = View.GONE }
    fun authorized() { loaded = false; needsAuthorization = false; load(false) }
    fun clear() {
        generation++; task?.cancel(true); songs.clear(); loaded = false; busy = false
        adapter.submitList(emptyList()); binding.browserMore.visibility = View.GONE
        binding.browserMessage.text = "Tus canciones, aquí mismo. Conecta Spotify para buscar o escuchar tus Me gusta."
        updateAction()
    }
    fun select(uri: String?) {
        selectedUri = uri
        adapter.setCurrentTrack(adapter.currentList.indexOfFirst { it.uri == uri })
    }
    private fun submitSongs() {
        adapter.submitList(songs.sortedWith(SongOrder.comparator(prefs.getInt("spotify_sort", 0)))) { select(selectedUri) }
    }
    fun destroy() { destroyed = true; generation++; task?.cancel(true); worker.shutdownNow() }
    private fun search() {
        query = binding.spotifySearch.text.toString().trim()
        (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.spotifySearch.windowToken, 0)
        binding.spotifySearch.clearFocus()
        load(false)
    }
    private fun load(append: Boolean) {
        if (destroyed || (append && busy)) return
        if (!api.isAuthorized()) { clear(); return }
        val request = ++generation
        task?.cancel(true)
        val offset = if (append) nextOffset ?: return else 0
        val requestedQuery = query
        if (!append) { songs.clear(); adapter.submitList(emptyList()) }
        busy = true
        binding.browserMore.visibility = View.GONE
        binding.browserMessage.text = "Cargando canciones…"
        binding.spotifyLiked.isSelected = query.isBlank()
        updateAction()
        task = worker.submit {
            val result = runCatching { api.songs(requestedQuery, offset) }
            activity.runOnUiThread {
                if (destroyed || generation != request) return@runOnUiThread
                busy = false
                result.fold({ page ->
                    loaded = true
                    val ids = songs.map { it.uri }.toMutableSet()
                    songs.addAll(page.tracks.filter { ids.add(it.uri) })
                    nextOffset = page.nextOffset?.takeIf { it > offset && (requestedQuery.isBlank() || it <= 1000) }
                    submitSongs()
                    binding.browserMessage.text = if (songs.isEmpty()) "No hay canciones disponibles. Prueba otra búsqueda."
                        else "${songs.size} canciones  ·  ${if (requestedQuery.isBlank()) "Tus Me gusta" else "Resultados de búsqueda"}"
                    binding.browserMore.visibility = if (nextOffset != null) View.VISIBLE else View.GONE
                }, { error ->
                    needsAuthorization = error is SpotifyWebApi.ApiError && error.status in listOf(400, 401)
                    binding.browserMessage.text = error.localizedMessage ?: "No se pudo cargar Spotify. Reintenta la búsqueda."
                    if (append) binding.browserMore.visibility = View.VISIBLE
                })
                updateAction()
            }
        }
    }
}
