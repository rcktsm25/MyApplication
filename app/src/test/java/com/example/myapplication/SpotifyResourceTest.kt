package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class SpotifyResourceTest {
    private val id = "37i9dQZF1DXcBWIGoYBM5M"

    @Test
    fun sharedPlaylistLinksIgnoreTrackingAndLocale() {
        val expected = "spotify:playlist:$id"
        assertEquals(expected, SpotifyResource.toUri("https://open.spotify.com/playlist/$id?si=abc"))
        assertEquals(expected, SpotifyResource.toUri("https://open.spotify.com/intl-es/playlist/$id?si=abc"))
        assertEquals(expected, SpotifyResource.toUri("https://open.spotify.com/embed/playlist/$id"))
        assertEquals(expected, SpotifyResource.toUri("  spotify:playlist:$id  "))
        assertEquals(expected, SpotifyResource.toUri("spotify:user:someone:playlist:$id"))
    }

    @Test
    fun supportsPlayableResourceTypes() {
        for (type in listOf("track", "album", "artist", "playlist", "episode", "show")) {
            assertEquals("spotify:$type:$id", SpotifyResource.toUri("https://open.spotify.com/$type/$id"))
        }
    }

    @Test
    fun otherHostsAndRadioQueryParametersAreNotSpotify() {
        for (url in listOf(
            "https://example.com/playlist/$id",
            "https://open.spotify.com.evil.test/playlist/$id",
            "https://radio.example/stream?ref=spotify.com",
            "https://open.spotify.com@evil.test/playlist/$id",
            "ftp://open.spotify.com/playlist/$id"
        )) {
            assertFalse(url, Track("Radio", "", url).isSpotify)
            assertNull(url, SpotifyResource.toUri(url))
        }
    }

    @Test
    fun unsupportedOrIncompleteResourcesAreRejected() {
        for (url in listOf(
            "", "not a URL", "spotify:", "spotify:track:", "spotify:track:bad-id",
            "spotify:search:hello", "https://open.spotify.com",
            "https://open.spotify.com/intl-es", "https://open.spotify.com/track",
            "https://open.spotify.com/user/$id",
            "https://open.spotify.com/playlist/$id/extra"
        )) {
            assertNull(url, SpotifyResource.toUri(url))
        }
    }

    @Test
    fun playlistAndPlayerUseTheSameClassification() {
        assertTrue(Track("Spotify", "", " https://OPEN.SPOTIFY.COM/intl-es/playlist/$id ").isSpotify)
        assertTrue(Track("Spotify", "", "spotify:playlist:$id").isSpotify)
        // Invalid Spotify links are still classified as Spotify so they can be rejected,
        // instead of being handed to ExoPlayer as radio streams.
        assertTrue(Track("Invalid", "", "https://open.spotify.com/playlist/invalid").isSpotify)
    }

    @Test
    fun clientIdMustBeConfiguredBeforeConnecting() {
        assertFalse(SpotifyManager.isValidClientId(""))
        assertFalse(SpotifyManager.isValidClientId("YOUR_SPOTIFY_CLIENT_ID"))
        assertFalse(SpotifyManager.isValidClientId("z".repeat(32)))
        assertTrue(SpotifyManager.isValidClientId("0123456789abcdef0123456789abcdef"))
    }
}
