package com.example.myapplication

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemTrackBinding

/**
 * Vista que agrupa canciones por álbum o artista.
 */
class GroupedLibraryView(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    
    data class Group(
        val name: String,
        val tracks: List<Track>
    )
    
    private var adapter: TrackAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var headerText: TextView? = null
    
    init {
        orientation = VERTICAL
    }
    
    fun setup(adapter: TrackAdapter) {
        this.adapter = adapter
        
        headerText = TextView(context).apply {
            textSize = 16f
            setPadding(16, 16, 16, 8)
        }
        addView(headerText)
        
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }
    
    fun setGroupingMode(mode: GroupingMode) {
        when (mode) {
            GroupingMode.ALBUM -> groupByAlbum()
            GroupingMode.ARTIST -> groupByArtist()
            GroupingMode.NONE -> showFlatList()
        }
    }
    
    private fun groupByAlbum() {
        val albums = mutableMapOf<String, MutableList<Track>>()
        adapter?.currentList?.forEach { track ->
            val album = "Álbum Desconocido" // Aquí se leería del metadata
            albums.getOrPut(album) { mutableListOf() }.add(track)
        }
        
        val groups = albums.map { (name, tracks) ->
            Group(name, tracks.sortedBy { it.title })
        }.sortedBy { it.name }
        
        renderGroups(groups)
    }
    
    private fun groupByArtist() {
        val artists = mutableMapOf<String, MutableList<Track>>()
        adapter?.currentList?.forEach { track ->
            val artist = if (track.artist == "Artista desconocido") "Sin Artista" else track.artist
            artists.getOrPut(artist) { mutableListOf() }.add(track)
        }
        
        val groups = artists.map { (name, tracks) ->
            Group(name, tracks.sortedBy { it.title })
        }.sortedBy { it.name }
        
        renderGroups(groups)
    }
    
    private fun renderGroups(groups: List<Group>) {
        // Esta implementación requeriría un RecyclerView con múltiples view types
        // Por ahora mostramos el primer grupo
        if (groups.isNotEmpty()) {
            headerText?.text = "${groups[0].name} (${groups[0].tracks.size} canciones)"
            adapter?.submitList(groups[0].tracks)
        }
    }
    
    private fun showFlatList() {
        headerText?.text = "Todas las canciones"
        adapter?.submitList(adapter?.currentList ?: emptyList())
    }
}

enum class GroupingMode {
    ALBUM, ARTIST, NONE
}
