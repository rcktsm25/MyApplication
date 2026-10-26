package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class AudioFilesTest {
    @Test fun recognizesAudioWhenProviderReturnsGenericMimeType() {
        for (name in listOf("Song.MP3", "song.flac", "song.m4a", "song.wav", "song.opus", "song.ogg")) {
            assertTrue(name, AudioFiles.isAudio(name, "application/octet-stream"))
        }
    }

    @Test fun recognizesAudioWithoutExtension() {
        assertTrue(AudioFiles.isAudio("Recording", "audio/mpeg"))
    }

    @Test fun ignoresCoverArtPlaylistsAndOtherDocuments() {
        for (name in listOf("cover.jpg", "lyrics.txt", "list.m3u", "archive.mp3.zip", "video.mp4")) {
            assertFalse(name, AudioFiles.isAudio(name, "application/octet-stream"))
        }
    }
}
