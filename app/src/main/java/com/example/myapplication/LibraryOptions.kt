package com.example.myapplication

import java.util.Locale

internal object SongOrder {
    val names = listOf("Nombre · A–Z", "Artista · A–Z", "Duración · menor a mayor")
    fun comparator(mode: Int): Comparator<Track> {
        val byName = compareBy<Track> { it.title.lowercase(Locale.ROOT) }.thenBy { it.uri }
        return when (mode) {
            1 -> compareBy<Track> { it.artist.lowercase(Locale.ROOT) }.then(byName)
            2 -> compareBy<Track> { it.durationMs.takeIf { value -> value > 0 } ?: Long.MAX_VALUE }.then(byName)
            else -> byName
        }
    }
}

internal data class MusicFolder(val id: String, val parentId: String?, val name: String, val path: String)
internal data class MusicLibrary(val tracks: List<Track>, val folders: List<MusicFolder>)
internal data class ResumePoint(val uri: String, val positionMs: Long)
internal fun restorePoint(tracks: List<Track>, point: ResumePoint?): Pair<Int, Long> {
    val index = tracks.indexOfFirst { it.uri == point?.uri }
    if (index < 0) return 0 to 0L
    val duration = tracks[index].durationMs
    return index to point!!.positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost((duration - 1).coerceAtLeast(0)) else it }
}
