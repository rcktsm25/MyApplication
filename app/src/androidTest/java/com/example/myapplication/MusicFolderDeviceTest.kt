package com.example.myapplication

import android.net.Uri
import android.provider.DocumentsContract
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MusicFolderDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tree: Uri = DocumentsContract.buildTreeDocumentUri("com.example.myapplication.test.music", "root")

    @Test fun scansNestedAudioAndIgnoresCoverArtAndCycles() {
        val tracks = MusicFolderScanner(instrumentation.targetContext.contentResolver).scan(tree)
        assertEquals(listOf("A nested.WAV", "B song.wav"), tracks.map { it.title })
        assertEquals(2, tracks.map { it.uri }.distinct().size)
        assertTrue(tracks.all { it.uri.startsWith("content://") && !it.isSpotify })
        assertTrue(tracks.all { it.durationMs in 900..1100 })
        val library = MusicFolderScanner(instrumentation.targetContext.contentResolver).scanLibrary(tree)
        assertEquals(2, library.folders.size)
        assertEquals("root", library.folders.single { it.id == "album" }.parentId)
        assertEquals("album", tracks.first().folderId)
        assertTrue(tracks.first().folderPath.endsWith("/Album"))
    }

    @Test fun emptyFolderReturnsAnEmptyLibrary() {
        val empty = DocumentsContract.buildTreeDocumentUri("com.example.myapplication.test.music", "empty")
        assertTrue(MusicFolderScanner(instrumentation.targetContext.contentResolver).scan(empty).isEmpty())
    }

    @Test fun exoPlayerPlaysAudioFromTheSelectedDocumentUri() {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, "song")
        val ended = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException>()
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(instrumentation.targetContext).build()
            player.volume = 0f
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) ended.countDown()
                }
                override fun onPlayerError(error: PlaybackException) {
                    failure.set(error)
                    ended.countDown()
                }
            })
            player.setMediaItem(MediaItem.Builder().setMediaId(uri.toString()).setUri(uri).build())
            player.prepare()
            player.play()
        }
        try {
            assertTrue("Playback did not complete", ended.await(15, TimeUnit.SECONDS))
            assertNull("Content URI playback failed", failure.get())
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }
}
