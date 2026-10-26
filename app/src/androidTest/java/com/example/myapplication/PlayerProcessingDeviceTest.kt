package com.example.myapplication

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerProcessingDeviceTest {
    @Test fun pcmPipelineAppliesPreampAndBalance() {
        val saved = AudioRuntime.settings
        val processor = PlayerAudioProcessor()
        try {
            AudioRuntime.settings = DspSettings(enabled = true, preamp = -6f, balance = 1f)
            processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
            processor.flush()
            val samples = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
            repeat(1024) { samples.putShort(16384); samples.putShort(16384) }
            samples.flip(); processor.queueInput(samples)
            val output = processor.output.order(ByteOrder.nativeOrder())
            assertEquals(4096, output.remaining())
            repeat(1024) {
                assertEquals(0, output.short.toInt())
                assertEquals(8211.0, output.short.toDouble(), 2.0)
            }
            assertEquals(0, samples.remaining())
        } finally { processor.reset(); AudioRuntime.settings = saved }
    }
    @Test fun pkceMatchesRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SpotifyWebApi.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun authorizedCatalogReturnsIndividualSearchResultsAndSavedSongs() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("spotifyIntegration") == "true")
        val api = SpotifyWebApi(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("Authorize the song catalog in RocketPlayer first", api.isAuthorized())
        val songs = api.songs("Daft Punk", 0)
        assertTrue("Search returned no songs", songs.tracks.isNotEmpty())
        assertTrue(songs.tracks.size <= 10)
        assertTrue(songs.tracks.all { it.uri.startsWith("spotify:track:") && it.title.isNotBlank() })
        val saved = api.songs("", 0)
        assertTrue(saved.tracks.all { it.uri.startsWith("spotify:track:") })
        assertTrue(saved.tracks.size <= 50)
    }
}
