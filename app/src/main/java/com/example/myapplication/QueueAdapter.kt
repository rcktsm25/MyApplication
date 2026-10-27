package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemQueueBinding

/**
 * Adapter para la cola de reproducción con soporte para drag & drop.
 */
class QueueAdapter(
    private val onTrackClick: (Track, Int) -> Unit,
    private val onRemoveTrack: (Int) -> Unit
) : ListAdapter<Track, QueueAdapter.ViewHolder>(TrackDiff()) {

    private var accentColor: Int = 0xFFFF4500.toInt()
    private var currentPlayingIndex: Int = -1

    /** Actualiza el tema visual de la cola sin parpadeo.
     *  Usa notifyItemRangeChanged para refrescar solo las filas visibles. */
    fun updateTheme(accent: Int) {
        accentColor = accent
        // notifyItemRangeChanged refresca solo las filas visibles, no toda la lista
        if (currentList.isNotEmpty()) {
            notifyItemRangeChanged(0, currentList.size)
        }
    }

    fun setCurrentTrack(index: Int) {
        val oldIndex = currentPlayingIndex
        currentPlayingIndex = index
        if (oldIndex in 0 until itemCount) notifyItemChanged(oldIndex)
        if (index in 0 until itemCount) notifyItemChanged(index)
    }

    inner class ViewHolder(private val binding: ItemQueueBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(track: Track, position: Int, isPlaying: Boolean) {
            binding.queueTitle.text = track.title.replace(Regex("(?i)\\.(wav|mp3|m4a|aac|flac|ogg|opus)$"), "")
            binding.queueArtist.text = track.artist
            binding.queueDuration.text = if (track.durationMs > 0) {
                String.format(java.util.Locale.ROOT, "%d:%02d", track.durationMs / 60000, track.durationMs / 1000 % 60)
            } else "—"
            binding.queueNumber.text = String.format(java.util.Locale.ROOT, "%02d", position + 1)

            if (isPlaying) {
                binding.queueTitle.setTextColor(0xFFFFFFFF.toInt())
                binding.queueArtist.setTextColor(0xFFB1C2CE.toInt())
                binding.queueNumber.setTextColor(accentColor)
                binding.queueNumber.text = "▶"
                binding.root.isSelected = true
            } else {
                binding.queueTitle.setTextColor(accentColor)
                binding.queueArtist.setTextColor(0xFF91A1B0.toInt())
                binding.queueNumber.setTextColor(accentColor)
                binding.root.isSelected = false
            }

            binding.root.setOnClickListener { onTrackClick(track, position) }
            binding.btnRemoveQueue.setOnClickListener { onRemoveTrack(adapterPosition) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQueueBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, position == currentPlayingIndex)
    }
}

class TrackDiff : DiffUtil.ItemCallback<Track>() {
    override fun areItemsTheSame(oldItem: Track, newItem: Track): Boolean =
        oldItem.uri == newItem.uri

    override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean =
        oldItem == newItem
}
