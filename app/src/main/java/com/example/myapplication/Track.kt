package com.example.myapplication

/**
 * Representa una pista o estación en la lista de reproducción de RocketPlayer.
 *
 * @param title Título de la pista o nombre de la estación.
 * @param artist Artista o descripción del género.
 * @param uri Dirección URI del recurso (streaming HTTP, archivo o URI de Spotify).
 */
data class Track(
    val title: String,
    val artist: String,
    val uri: String,
    val durationMs: Long = 0,
    val folderId: String = "",
    val folderPath: String = "",
    val fileName: String = title
) {
    /**
     * Determina si la pista proviene de Spotify (URI `spotify:...` o enlace `open.spotify.com`).
     */
    val isSpotify: Boolean
        get() = SpotifyResource.isSpotify(uri)
}
