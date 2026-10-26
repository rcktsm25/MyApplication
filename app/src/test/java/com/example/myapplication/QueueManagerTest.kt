package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

/**
 * Pruebas unitarias para QueueManager.
 */
class QueueManagerTest {
    
    @Test
    fun `setQueue sets current track correctly`() {
        val manager = QueueManager()
        val tracks = listOf(
            Track("Song 1", "Artist 1", "uri1", 180000),
            Track("Song 2", "Artist 2", "uri2", 200000),
            Track("Song 3", "Artist 3", "uri3", 220000)
        )
        
        manager.setQueue(tracks, 1)
        
        assertEquals(3, manager.getQueue().size)
        assertEquals(1, manager.getCurrentIndex())
        assertEquals("Song 2", manager.getCurrentTrack()?.title)
    }
    
    @Test
    fun `removeFromQueue adjusts current index correctly when removing before current`() {
        val manager = QueueManager()
        val tracks = listOf(
            Track("Song 1", "Artist 1", "uri1"),
            Track("Song 2", "Artist 2", "uri2"),
            Track("Song 3", "Artist 3", "uri3")
        )
        manager.setQueue(tracks, 2) // Current: Song 3
        
        manager.removeFromQueue(0) // Remove Song 1
        
        assertEquals(2, manager.getQueue().size)
        assertEquals(1, manager.getCurrentIndex()) // Should decrement
        assertEquals("Song 3", manager.getCurrentTrack()?.title)
    }
    
    @Test
    fun `removeFromQueue adjusts current index when removing current track`() {
        val manager = QueueManager()
        val tracks = listOf(
            Track("Song 1", "Artist 1", "uri1"),
            Track("Song 2", "Artist 2", "uri2"),
            Track("Song 3", "Artist 3", "uri3")
        )
        manager.setQueue(tracks, 1) // Current: Song 2

        manager.removeFromQueue(1) // Remove Song 2

        assertEquals(2, manager.getQueue().size)
        // After removing current track, should point to next available (Song 3 at index 1)
        // But if that's out of bounds, point to last available
        assertEquals(1, manager.getCurrentIndex())
        assertEquals("Song 3", manager.getCurrentTrack()?.title)
    }
    
    @Test
    fun `moveItem swaps tracks correctly`() {
        val manager = QueueManager()
        val tracks = listOf(
            Track("Song 1", "Artist 1", "uri1"),
            Track("Song 2", "Artist 2", "uri2"),
            Track("Song 3", "Artist 3", "uri3")
        )
        manager.setQueue(tracks, 0)
        
        manager.moveItem(0, 2) // Move Song 1 to position 2
        
        val queue = manager.getQueue()
        assertEquals("Song 2", queue[0].title)
        assertEquals("Song 3", queue[1].title)
        assertEquals("Song 1", queue[2].title)
        assertEquals(2, manager.getCurrentIndex()) // Current track moved
    }
    
    @Test
    fun `next cycles through queue`() {
        val manager = QueueManager()
        val tracks = listOf(
            Track("Song 1", "Artist 1", "uri1"),
            Track("Song 2", "Artist 2", "uri2")
        )
        manager.setQueue(tracks, 0)
        
        val nextTrack = manager.next()
        
        assertEquals("Song 2", nextTrack?.title)
        assertEquals(1, manager.getCurrentIndex())
    }
    
    @Test
    fun `next wraps around to beginning`() {
        val manager = QueueManager()
        val tracks = listOf(
            Track("Song 1", "Artist 1", "uri1"),
            Track("Song 2", "Artist 2", "uri2")
        )
        manager.setQueue(tracks, 1) // At last track
        
        val nextTrack = manager.next()
        
        assertEquals("Song 1", nextTrack?.title)
        assertEquals(0, manager.getCurrentIndex())
    }
    
    @Test
    fun `clear empties queue`() {
        val manager = QueueManager()
        val tracks = listOf(Track("Song 1", "Artist 1", "uri1"))
        manager.setQueue(tracks, 0)
        
        manager.clear()
        
        assertTrue(manager.getQueue().isEmpty())
        assertEquals(-1, manager.getCurrentIndex())
        assertNull(manager.getCurrentTrack())
    }
}
