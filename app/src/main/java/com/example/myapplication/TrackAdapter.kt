package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.myapplication.databinding.ItemTrackBinding

/**
 * Adapter profesional para la lista de tracks con:
 * - Coil para carga asíncrona de portadas de álbumes
 * - DiffUtil con payload para rendimiento óptimo
 * - Sombras y elevación Material Design
 * - Placeholders y transiciones suaves
 */
class TrackAdapter(
    private val onTrackClick: (Track, Int) -> Unit
) : ListAdapter<Track, TrackAdapter.ViewHolder>(TrackDiffCallback()) {

    private var accentColor: Int = 0xFFFF4500.toInt()
    private var dimColor: Int = 0xFF8B0000.toInt()
    private var currentPlayingIndex: Int = -1

    /** Actualiza el tema visual de la lista sin parpadeo */
    fun updateTheme(accent: Int, dim: Int) {
        accentColor = accent
        dimColor = dim
        if (currentList.isNotEmpty()) {
            notifyItemRangeChanged(0, currentList.size)
        }
    }

    fun setCurrentTrack(index: Int) {
        if (currentPlayingIndex == index) return
        val oldIndex = currentPlayingIndex
        currentPlayingIndex = index
        if (oldIndex in 0 until itemCount) notifyItemChanged(oldIndex)
        if (index in 0 until itemCount) notifyItemChanged(index)
    }

    inner class ViewHolder(private val binding: ItemTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(track: Track, position: Int, isPlaying: Boolean) {
            binding.trackTitle.text = if (track.isSpotify) track.title else track.title.replace(Regex("(?i)\\.(wav|mp3|m4a|aac|flac|ogg|opus)$"), "")
            binding.trackArtist.text = if (track.artist == "Artista desconocido" && track.folderPath.isNotBlank()) track.folderPath else track.artist
            binding.trackDuration.text = if (track.durationMs > 0) String.format(java.util.Locale.ROOT, "%d:%02d", track.durationMs / 60000, track.durationMs / 1000 % 60) else "—"
            binding.imgSpotifyIcon.visibility =
                if (track.isSpotify) android.view.View.VISIBLE else android.view.View.GONE

            // Cargar portada del álbum con Coil (placeholder + transición suave)
            binding.imgAlbumArt.load(track.albumArtUrl) {
                placeholder(R.drawable.player_screen)
                error(R.drawable.player_screen)
                crossfade(true)
                crossfade(300)
                transformations(RoundedCornersTransformation(8f))
            }

            updatePlayingState(isPlaying)

            binding.root.setOnClickListener { onTrackClick(track, position) }
        }

        /** Actualizar solo el estado de reproducción sin rebindear todo */
        fun updatePlayingState(isPlaying: Boolean) {
            if (isPlaying) {
                binding.trackTitle.setTextColor(0xFFFFFFFF.toInt())
                binding.trackArtist.setTextColor(0xFFB1C2CE.toInt())
                binding.trackNumber.setTextColor(accentColor)
                binding.trackNumber.text = "▶"
                binding.root.isSelected = true
            } else {
                binding.trackTitle.setTextColor(accentColor)
                binding.trackArtist.setTextColor(0xFF91A1B0.toInt())
                binding.trackNumber.setTextColor(accentColor)
                binding.trackNumber.text = String.format(java.util.Locale.ROOT, "%02d", adapterPosition + 1)
                binding.root.isSelected = false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, position == currentPlayingIndex)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty() || payloads[0] != "STATE_CHANGED") {
            holder.bind(getItem(position), position, position == currentPlayingIndex)
        } else {
            holder.updatePlayingState(position == currentPlayingIndex)
        }
    }
}

/**
 * DiffUtil para tracks - compara por URI para identificar items únicos.
 * Solo refresca las filas que realmente cambiaron, optimizando scroll en listas grandes.
 */
class TrackDiffCallback : DiffUtil.ItemCallback<Track>() {
    override fun areItemsTheSame(oldItem: Track, newItem: Track): Boolean =
        oldItem.uri == newItem.uri

    override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean =
        oldItem == newItem

    /**
     * Optimización: payload parcial para cambios de estado (playing/pausa)
     * Evita rebindear toda la fila cuando solo cambia el estado de reproducción.
     */
    override fun getChangePayload(oldItem: Track, newItem: Track): Any? {
        return if (oldItem.uri == newItem.uri && oldItem != newItem) {
            // Solo cambió el estado de reproducción, no need full rebind
            "STATE_CHANGED"
        } else null
    }
}
