package com.ballade.hwaran.data.importer.video

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.metadata.MediaMetadataManager
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import com.ballade.hwaran.core.util.CoverCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoExternalSingleImport {

    suspend fun execute(
        context: Context,
        repository: VideoImportRepository,
        uri: Uri,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        importMode: String = "Single Import",
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit
    ): Long? = withContext(Dispatchers.IO) {

        val sourceDoc = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null

        // Validate: must contain at least one video file
        val (isValid, _) = VideoImportUtils.isVideoFolderValid(sourceDoc)
        if (!isValid) return@withContext null
        if (isCancelled()) return@withContext null

        val uriStr = uri.toString()
        val existingManga = repository.getRootMangaByUri(uriStr)

        val folderName = sourceDoc.name ?: "Unknown"

        // 1. Extract metadata from .zine/*.json or root *.json, checking subfolders if needed
        var parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, sourceDoc)
        if (parsedZine == null) {
            val children = sourceDoc.listFiles()
            for (child in children) {
                if (child.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(child.name)) {
                    parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, child)
                    if (parsedZine != null) break
                }
            }
        }

        val finalTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: existingManga?.title ?: folderName
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() } ?: existingManga?.description ?: "No description added yet."
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: existingManga?.genre

        // 2. Cover resolution (root first, then immediate subfolders)
        var coverPath = existingManga?.coverPath ?: ""
        val coverDoc = VideoImportUtils.findCover(sourceDoc, parsedZine?.coverFileName)
        if (coverDoc != null) {
            coverPath = CoverCacheManager.cacheCoverFromUri(context, coverDoc.uri, "video", finalTitle)
                ?: coverDoc.uri.toString()
        }

        val effectiveBoxPurpose = boxPurpose ?: existingManga?.boxPurpose ?: "series"

        val mangaToInsert = MangaEntity(
            id = existingManga?.id ?: 0L,
            title = finalTitle,
            description = finalDescription,
            thoughts = existingManga?.thoughts ?: "No thoughts added.",
            coverPath = coverPath,
            isNsfw = existingManga?.isNsfw ?: isNsfw,
            parentUri = uriStr,
            lastModified = sourceDoc.lastModified(),
            contentType = 2, // Video
            boxPurpose = effectiveBoxPurpose,
            boxLabel = null,
            genre = finalTags,
            workspace = existingManga?.workspace ?: workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)
        if (isCancelled()) return@withContext mangaId

        // 3. Collect all video files (root + subfolders like Videos/, season 1/, etc.)
        val videoFiles = VideoImportUtils.findVideoFiles(sourceDoc)
        val totalFiles = videoFiles.size

        val chapterEntities = videoFiles.mapIndexed { index, videoFile ->
            val existingChapter = repository.getChapterByUri(videoFile.uri.toString())
            val episodeTitle = videoFile.name?.substringBeforeLast(".") ?: "Unknown"
            if (totalFiles > 0) {
                onProgress(((index + 1).toFloat() / totalFiles * 100).toInt().coerceIn(0, 100))
            }
            if (existingChapter == null) {
                ChapterEntity(
                    mangaId = mangaId,
                    title = episodeTitle,
                    folderUri = videoFile.uri.toString(),
                    position = index
                )
            } else {
                existingChapter.copy(mangaId = mangaId, title = episodeTitle, position = index)
            }
        }

        repository.insertChapters(chapterEntities)

        if (parsedZine != null) {
            MediaMetadataManager.saveMetadata(
                context = context,
                mangaId = mangaId,
                parentUri = uriStr,
                metadata = parsedZine.toEntryMetadata()
            )
        }

        com.ballade.hwaran.core.util.HistoryTracker.logEvent(
            "IMPORT",
            finalTitle,
            "Video • $importMode | Source: $uriStr"
        )

        return@withContext mangaId
    }
}
