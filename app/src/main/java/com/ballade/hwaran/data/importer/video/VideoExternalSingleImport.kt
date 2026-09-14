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

        // If this is a series or structured series, delegate to SeriesStructureImporter
        if (boxPurpose == "series" || (boxPurpose == null && SeriesStructureImporter.isOrganizedSeries(context, sourceDoc))) {
            return@withContext SeriesStructureImporter.execute(
                context = context,
                repository = repository,
                rootDoc = sourceDoc,
                isLocalMode = false,
                workspace = workspace,
                isNsfw = isNsfw,
                isCancelled = isCancelled,
                onProgress = onProgress
            )
        }

        // Channel Mode (flat collection)
        val uriStr = uri.toString()
        val existingManga = repository.getRootMangaByUri(uriStr)

        val folderName = sourceDoc.name ?: "Unknown"

        // 1. Extract metadata from .zine/*.json or root *.json
        val parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, sourceDoc)
        val finalTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: existingManga?.title ?: folderName
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() } ?: existingManga?.description ?: "No description added yet."
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: existingManga?.genre

        // 2. Cover resolution
        var coverPath = existingManga?.coverPath ?: ""
        val coverDoc = ZineMetadataExtractor.findCoverInDocumentFolder(sourceDoc, parsedZine?.coverFileName)
        if (coverDoc != null) {
            coverPath = CoverCacheManager.cacheCoverFromUri(context, coverDoc.uri, "video", finalTitle)
                ?: coverDoc.uri.toString()
        }

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
            boxPurpose = boxPurpose ?: "channel",
            boxLabel = null,
            genre = finalTags,
            workspace = existingManga?.workspace ?: workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)
        if (isCancelled()) return@withContext mangaId

        // 3. Insert video chapters
        val rootFiles = sourceDoc.listFiles()
        val videoFiles = rootFiles.filter { file ->
            !file.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(file.name) &&
            VideoImportUtils.videoExtensions.any { ext -> file.name?.lowercase()?.endsWith(".$ext") == true }
        }.sortedWith(compareBy { item ->
            item.name?.replace(Regex("\\d+")) { match ->
                match.value.padStart(10, '0')
            } ?: ""
        })

        val chapterEntities = videoFiles.mapIndexed { index, videoFile ->
            val existingChapter = repository.getChapterByUri(videoFile.uri.toString())
            if (existingChapter == null) {
                ChapterEntity(
                    mangaId = mangaId,
                    title = videoFile.name?.substringBeforeLast(".") ?: "Unknown",
                    folderUri = videoFile.uri.toString(),
                    position = index
                )
            } else {
                existingChapter.copy(mangaId = mangaId, position = index)
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
