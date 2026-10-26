package com.example.myapplication

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

internal object AudioFiles {
    private val extensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "oga", "amr", "aiff", "aif")
    fun isAudio(name: String, mimeType: String): Boolean = mimeType.startsWith("audio/", true) ||
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions
}

/** Directory and metadata reads run exclusively on the library worker. */
internal class MusicFolderScanner(private val resolver: ContentResolver) {
    fun scan(tree: Uri): List<Track> = scanLibrary(tree).tracks
    fun scanLibrary(tree: Uri): MusicLibrary {
        val root = DocumentsContract.getTreeDocumentId(tree)
        val rootName = runCatching {
            resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, root),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME).takeIf { index -> index >= 0 }?.let(it::getString) else null
            }
        }.getOrNull() ?: root.substringAfterLast('/').substringAfterLast(':')
        val pending = ArrayDeque<MusicFolder>()
        val folders = linkedMapOf<String, MusicFolder>()
        val tracks = linkedMapOf<String, Track>()
        pending.add(MusicFolder(root, null, rootName, rootName))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (pending.isNotEmpty()) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val directory = pending.removeFirst()
            if (folders.putIfAbsent(directory.id, directory) != null) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.id)
            val cursor = resolver.query(children, columns, null, null, null)
                ?: throw IllegalStateException("No se pudo leer la carpeta seleccionada.")
            cursor.use {
                while (it.moveToNext()) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val id = it.getString(0) ?: continue
                    val name = it.getString(1) ?: id.substringAfterLast('/')
                    val mime = it.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(MusicFolder(id, directory.id, name, "${directory.path}/$name"))
                    } else if (AudioFiles.isAudio(name, mime)) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        var title = name
                        var artist = "Artista desconocido"
                        var duration = 0L
                        val metadata = MediaMetadataRetriever()
                        try {
                            resolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                                if (fd.declaredLength >= 0) metadata.setDataSource(fd.fileDescriptor, fd.startOffset, fd.declaredLength)
                                else metadata.setDataSource(fd.fileDescriptor)
                                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)?.let { title = it }
                                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank)?.let { artist = it }
                                duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
                            }
                        } catch (_: Exception) { /* Untagged or unsupported metadata must not hide a playable file. */ }
                        finally { runCatching { metadata.release() } }
                        tracks[uri.toString()] = Track(title, artist, uri.toString(), duration, directory.id, directory.path, name)
                    }
                }
            }
        }
        return MusicLibrary(tracks.values.sortedWith(SongOrder.comparator(0)), folders.values.toList())
    }
}
