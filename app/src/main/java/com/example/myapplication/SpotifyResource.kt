package com.example.myapplication

import java.net.URI

/** Shared parsing for the playlist and App Remote; never interpret another host as Spotify. */
internal object SpotifyResource {
    private val types = setOf("track", "album", "artist", "playlist", "episode", "show")
    private val idPattern = Regex("[A-Za-z0-9]{22}")

    fun isSpotify(value: String): Boolean {
        val uri = parse(value) ?: return false
        return uri.scheme.equals("spotify", ignoreCase = true) ||
            (uri.scheme?.lowercase() in setOf("http", "https") &&
                uri.host.equals("open.spotify.com", ignoreCase = true))
    }

    fun toUri(value: String): String? {
        val uri = parse(value) ?: return null
        val parts = if (uri.scheme.equals("spotify", ignoreCase = true)) {
            uri.schemeSpecificPart.split(":")
        } else {
            if (!isSpotify(value) || uri.userInfo != null) return null
            uri.path.orEmpty().trim('/').split('/').toMutableList().apply {
                if (firstOrNull()?.startsWith("intl-") == true) removeAt(0)
                if (firstOrNull() == "embed") removeAt(0)
            }
        }
        // Older shared playlist URIs include the owner's username.
        val resource = if (parts.size == 4 && parts[0] == "user" && parts[2] == "playlist") {
            parts.takeLast(2)
        } else parts
        if (resource.size != 2 || resource[0] !in types || !idPattern.matches(resource[1])) return null
        return "spotify:${resource[0]}:${resource[1]}"
    }

    private fun parse(value: String): URI? = runCatching { URI(value.trim()) }.getOrNull()
}
