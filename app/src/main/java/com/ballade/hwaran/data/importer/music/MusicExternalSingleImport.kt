package com.ballade.hwaran.data.importer.music

import android.content.Context
import android.net.Uri
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
        if (existingAlbum != null) {
            if (existingAlbum.coverPath.isEmpty() || existingAlbum.coverPath.startsWith("content://") || !java.io.File(existingAlbum.coverPath).exists()) {
                val audioFiles = MusicImportUtils.findAudioFiles(folderDoc)
                val newCover = MusicImportUtils.findCoverImage(context, folderDoc, audioFiles, existingAlbum.title)
                if (newCover.isNotEmpty() && newCover != existingAlbum.coverPath) {
                    repository.insertAlbum(existingAlbum.copy(coverPath = newCover))
                }
            }

            val existingTracks = repository.getTracksForAlbum(existingAlbum.id)
            val audioFiles = MusicImportUtils.findAudioFiles(folderDoc)
            val lyricsFiles = MusicImportUtils.findLyricsFiles(folderDoc)

            if (existingTracks.isEmpty() && audioFiles.isNotEmpty()) {
                val tracksToInsert = mutableListOf<ChapterEntity>()
                audioFiles.forEachIndexed { index, audioDoc ->
                    if (isCancelled()) return@withContext existingAlbum.id
                    val trackUri = audioDoc.uri.toString()
                    val fileName = audioDoc.name ?: "Unknown Track"
                    val meta = MusicImportUtils.extractTrackMetadata(context, audioDoc.uri, fileName)

                    val matchingLyricsDoc = MusicImportUtils.findMatchingLyricsDoc(fileName, meta.title, lyricsFiles)
                    val trackLyrics = matchingLyricsDoc?.let { MusicImportUtils.readLyrics(context, it) } ?: meta.lyrics

                    val track = ChapterEntity(
                        mangaId = existingAlbum.id,
                        title = meta.title,
                        folderUri = trackUri,
                        position = index,
                        duration = meta.duration,
                        thumbnailUri = meta.coverPath ?: "",
                        artist = meta.artist,
                        lyrics = trackLyrics
                    )
                    tracksToInsert.add(track)
                }
                if (tracksToInsert.isNotEmpty()) {
                    repository.insertTracks(tracksToInsert)
                }
            } else if (existingTracks.isNotEmpty() && lyricsFiles.isNotEmpty()) {
                existingTracks.forEach { track ->
                    if (track.lyrics.isNullOrBlank()) {
                        val decodedUri = Uri.decode(track.folderUri)
                        val fileName = decodedUri.substringAfterLast('/').substringAfterLast(':')
                        val matchingLyricsDoc = MusicImportUtils.findMatchingLyricsDoc(fileName, track.title, lyricsFiles)
                        val trackLyrics = matchingLyricsDoc?.let { MusicImportUtils.readLyrics(context, it) }
                        if (!trackLyrics.isNullOrBlank()) {
                            repository.updateTrack(track.copy(lyrics = trackLyrics))
                        }
                    }
                }
            }
            return@withContext existingAlbum.id
        }

        if (isCancelled()) return@withContext null

        val audioFiles = MusicImportUtils.findAudioFiles(folderDoc)
        if (audioFiles.isEmpty()) return@withContext null

        val albumTitle = folderDoc.name ?: "Unknown Album"

        // 1. Extract metadata from .zine/*.json or root *.json
        val parsedZine = com.ballade.hwaran.core.metadata.ZineMetadataExtractor.extractFromDocumentFolder(context, folderDoc)
        val finalAlbumTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: albumTitle
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() } ?: "External Music"
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

        // 2. Extract cover
        val coverDoc = com.ballade.hwaran.core.metadata.ZineMetadataExtractor.findCoverInDocumentFolder(folderDoc, parsedZine?.coverFileName)
        val coverUri = if (coverDoc != null) {
            com.ballade.hwaran.core.util.CoverCacheManager.cacheCoverFromUri(context, coverDoc.uri, "music", finalAlbumTitle)
                ?: coverDoc.uri.toString()
        } else {
            MusicImportUtils.findCoverImage(context, folderDoc, audioFiles, finalAlbumTitle)
        }

        // 3. Insert Album
        val albumToInsert = MangaEntity(
            id = 0L,
            title = finalAlbumTitle,
            description = finalDescription,
            thoughts = "",
            coverPath = coverUri,
            isNsfw = false,
            parentUri = uriStr,
            lastModified = System.currentTimeMillis(),
            contentType = 3, // MUSIC
            genre = finalTags,
            workspace = workspace
        )

        val albumId = repository.insertAlbum(albumToInsert)

        if (parsedZine != null) {
            com.ballade.hwaran.core.metadata.MediaMetadataManager.saveMetadata(
                context = context,
                mangaId = albumId,
                parentUri = uriStr,
                metadata = parsedZine.toEntryMetadata(),
                forceWriteToFile = false
            )
        }

        // 4. Scan Lyrics (Case 1: side-by-side, Case 2: lyrics folder alongside)
        val lyricsFiles = MusicImportUtils.findLyricsFiles(folderDoc)

        // 5. Insert Tracks
        val tracksToInsert = mutableListOf<ChapterEntity>()
        audioFiles.forEachIndexed { index, audioDoc ->
            if (isCancelled()) return@withContext albumId
            val trackUri = audioDoc.uri.toString()
            val fileName = audioDoc.name ?: "Unknown Track"
            val meta = MusicImportUtils.extractTrackMetadata(context, audioDoc.uri, fileName)

            val matchingLyricsDoc = MusicImportUtils.findMatchingLyricsDoc(fileName, meta.title, lyricsFiles)
            val trackLyrics = matchingLyricsDoc?.let { MusicImportUtils.readLyrics(context, it) } ?: meta.lyrics

            val track = ChapterEntity(
                mangaId = albumId,
                title = meta.title,
                folderUri = trackUri,
                position = index,
                duration = meta.duration,
                thumbnailUri = meta.coverPath ?: "",
                artist = meta.artist,
                lyrics = trackLyrics
            )
            tracksToInsert.add(track)
            
            onProgress?.invoke(((index + 1).toFloat() / audioFiles.size * 100).toInt())
        }

        if (tracksToInsert.isNotEmpty()) {
            repository.insertTracks(tracksToInsert)
            if (coverUri.isEmpty()) {
                val trackThumb = tracksToInsert.firstOrNull { !it.thumbnailUri.isNullOrBlank() }?.thumbnailUri
                if (!trackThumb.isNullOrBlank()) {
                    repository.insertAlbum(albumToInsert.copy(id = albumId, coverPath = trackThumb))
                }
            }
        }

        // 5. History logging
        repository.logHistory(albumTitle, "Music • Single")

        return@withContext albumId
    }
}
