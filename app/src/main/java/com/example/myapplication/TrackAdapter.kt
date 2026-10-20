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

    fun updateTheme(accent: Int, dim: Int) {
        accentColor = accent
        dimColor = dim
        notifyDataSetChanged()
    }

    fun setCurrentTrack(index: Int) {
        val oldIndex = currentPlayingIndex
        currentPlayingIndex = index
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (index >= 0) notifyItemChanged(index)
    }

    inner class ViewHolder(private val binding: ItemTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(track: Track, position: Int, isPlaying: Boolean) {
            binding.trackTitle.text = track.title
            binding.trackArtist.text = track.artist
            binding.imgSpotifyIcon.visibility =
                if (track.isSpotify) android.view.View.VISIBLE else android.view.View.GONE

            if (isPlaying) {
                binding.trackTitle.setTextColor(accentColor)
                binding.trackArtist.setTextColor(accentColor)
                binding.trackNumber.setTextColor(accentColor)
                binding.trackNumber.text = "▶"
                binding.root.setBackgroundColor(0x22FF4500.toInt())
            } else {
                binding.trackTitle.setTextColor(0xFFE0E0E0.toInt())
                binding.trackArtist.setTextColor(dimColor)
                binding.trackNumber.setTextColor(dimColor)
                binding.trackNumber.text = String.format("%02d", position + 1)
                binding.root.setBackgroundColor(0x00000000.toInt())
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
