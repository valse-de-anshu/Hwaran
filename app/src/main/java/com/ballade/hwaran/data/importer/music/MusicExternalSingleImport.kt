package com.ballade.hwaran.data.importer.music

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicExternalSingleImport {

    suspend fun execute(
        context: Context,
        repository: MusicImportRepository,
        folderDoc: DocumentFile,
        workspace: String?,
        isCancelled: () -> Boolean,
        onProgress: ((Int) -> Unit)? = null
    ): Long? = withContext(Dispatchers.IO) {

        val uriStr = folderDoc.uri.toString()

        // 1. Duplicate check
        val existingAlbum = repository.getAlbumByUri(uriStr)
        if (existingAlbum != null) return@withContext existingAlbum.id

        if (isCancelled()) return@withContext null

        val audioFiles = MusicImportUtils.findAudioFiles(folderDoc)
        if (audioFiles.isEmpty()) return@withContext null

        val albumTitle = folderDoc.name ?: "Unknown Album"

        // 2. Extract cover
        val coverUri = MusicImportUtils.findCoverImage(context, folderDoc, audioFiles, albumTitle)

        // 3. Insert Album
        val albumToInsert = MangaEntity(
            id = 0L,
            title = albumTitle,
            description = "External Music",
            thoughts = "",
            coverPath = coverUri,
            isNsfw = false,
            parentUri = uriStr,
            lastModified = System.currentTimeMillis(),
            contentType = 3, // MUSIC
            workspace = workspace
        )

        val albumId = repository.insertAlbum(albumToInsert)

        // 4. Insert Tracks
        val tracksToInsert = mutableListOf<ChapterEntity>()
        audioFiles.forEachIndexed { index, audioDoc ->
            if (isCancelled()) return@withContext albumId
            val trackUri = audioDoc.uri.toString()
            val fileName = audioDoc.name ?: "Unknown Track"
            val meta = MusicImportUtils.extractTrackMetadata(context, audioDoc.uri, fileName)

            val track = ChapterEntity(
                mangaId = albumId,
                title = meta.title,
                folderUri = trackUri,
                position = index,
                duration = meta.duration,
                thumbnailUri = meta.coverPath ?: "",
                artist = meta.artist
            )
            tracksToInsert.add(track)
            
            onProgress?.invoke(((index + 1).toFloat() / audioFiles.size * 100).toInt())
        }

        if (tracksToInsert.isNotEmpty()) {
            repository.insertTracks(tracksToInsert)
        }

        // 5. History logging
        repository.logHistory(albumTitle, "Music • Single")

        return@withContext albumId
    }
}
