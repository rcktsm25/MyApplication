package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class LibraryOptionsTest {
    private val tracks = listOf(Track("Zeta", "Ana", "z", 120000), Track("Alfa", "Zoe", "a", 240000), Track("Beta", "Luis", "b"))
    @Test fun orderingUsesArtistAndDurationAndKeepsUnknownDurationsLast() {
        assertEquals(listOf("Alfa", "Beta", "Zeta"), tracks.sortedWith(SongOrder.comparator(0)).map { it.title })
        assertEquals(listOf("Zeta", "Beta", "Alfa"), tracks.sortedWith(SongOrder.comparator(1)).map { it.title })
        assertEquals(listOf("Zeta", "Alfa", "Beta"), tracks.sortedWith(SongOrder.comparator(2)).map { it.title })
    }
    @Test fun restoreFindsTheSongAfterReorderingAndBoundsItsPosition() {
        assertEquals(1 to 32000L, restorePoint(tracks, ResumePoint("a", 32000)))
        assertEquals(0 to 119999L, restorePoint(tracks, ResumePoint("z", 999999)))
        assertEquals(1 to 0L, restorePoint(tracks, ResumePoint("a", -1)))
        assertEquals(0 to 0L, restorePoint(tracks, ResumePoint("removed", 1000)))
        assertEquals(0 to 0L, restorePoint(emptyList(), ResumePoint("a", 1000)))
    }
}
