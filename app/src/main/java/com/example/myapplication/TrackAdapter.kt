package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemTrackBinding

class TrackAdapter(
    private val onTrackClick: (Track, Int) -> Unit
) : ListAdapter<Track, TrackAdapter.ViewHolder>(TrackDiffCallback()) {

    private var accentColor: Int = 0xFFFF4500.toInt()
    private var dimColor: Int = 0xFF8B0000.toInt()
    private var currentPlayingIndex: Int = -1

    /** Actualiza el tema visual de la lista sin parpadeo.
     *  Usa notifyItemRangeChanged para refrescar solo las filas visibles. */
    fun updateTheme(accent: Int, dim: Int) {
        accentColor = accent
        dimColor = dim
        // notifyItemRangeChanged refresca solo las filas visibles, no toda la lista
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
                binding.trackNumber.text = String.format(java.util.Locale.ROOT, "%02d", position + 1)
                binding.root.isSelected = false
            }

            binding.root.setOnClickListener { onTrackClick(track, position) }
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
}

class TrackDiffCallback : DiffUtil.ItemCallback<Track>() {
    override fun areItemsTheSame(oldItem: Track, newItem: Track): Boolean =
        oldItem.uri == newItem.uri

    override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean =
        oldItem == newItem
}
