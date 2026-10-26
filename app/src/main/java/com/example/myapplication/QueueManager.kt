package com.example.myapplication

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

/**
 * Gestiona la cola de reproducción actual con soporte para reordenamiento y eliminación.
 */
class QueueManager {
    private val queue = mutableListOf<Track>()
    private var currentIndex = -1
    private var onQueueChanged: ((List<Track>, Int) -> Unit)? = null
    
    fun setOnQueueChangedListener(listener: (List<Track>, Int) -> Unit) {
        onQueueChanged = listener
    }
    
    fun getQueue(): List<Track> = queue.toList()
    
    fun getCurrentIndex(): Int = currentIndex
    
    fun getCurrentTrack(): Track? = queue.getOrNull(currentIndex)
    
    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        queue.clear()
        queue.addAll(tracks)
        currentIndex = startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        notifyChanged()
    }
    
    fun addToQueue(track: Track) {
        queue.add(track)
        notifyChanged()
    }
    
    fun addToQueue(tracks: List<Track>) {
        queue.addAll(tracks)
        notifyChanged()
    }
    
    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        queue.removeAt(index)
        when {
            index < currentIndex -> currentIndex--
            index == currentIndex -> currentIndex = currentIndex.coerceAtMost(queue.size - 1)
        }
        if (currentIndex < 0 && queue.isNotEmpty()) currentIndex = 0
        notifyChanged()
    }

    fun moveItem(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val item = queue.removeAt(from)
        queue.add(to, item)
        when {
            from == currentIndex -> currentIndex = to
            to == currentIndex -> {
                if (from < to) currentIndex--
                else currentIndex++
            }
            from < currentIndex && to >= currentIndex -> currentIndex--
            from > currentIndex && to <= currentIndex -> currentIndex++
        }
        notifyChanged()
    }
    
    fun clear() {
        queue.clear()
        currentIndex = -1
        notifyChanged()
    }
    
    fun next(): Track? {
        if (queue.isEmpty()) return null
        currentIndex = (currentIndex + 1) % queue.size
        notifyChanged()
        return queue[currentIndex]
    }
    
    fun previous(): Track? {
        if (queue.isEmpty()) return null
        currentIndex = if (currentIndex > 0) currentIndex - 1 else queue.size - 1
        notifyChanged()
        return queue[currentIndex]
    }
    
    fun setCurrentTrack(track: Track) {
        currentIndex = queue.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
        notifyChanged()
    }
    
    private fun notifyChanged() {
        onQueueChanged?.invoke(queue.toList(), currentIndex)
    }
}

/**
 * Touch helper para drag & drop en la cola de reproducción.
 */
class QueueTouchHelper(
    private val onMove: (Int, Int) -> Unit,
    private val onSwipe: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        onMove(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }
    
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onSwipe(viewHolder.adapterPosition)
    }
}
