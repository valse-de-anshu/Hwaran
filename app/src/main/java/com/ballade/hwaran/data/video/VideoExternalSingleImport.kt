package com.ballade.hwaran.data.video

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.data.local.MangaEntity
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

        // Duplicate check — no file copy happens, just URI
        val existingManga = repository.getRootMangaByUri(uriStr)
        if (existingManga != null) return@withContext existingManga.id

        val rootFiles = sourceDoc.listFiles() ?: emptyArray()

        // Determine cover — if no image, coverPath = "" and UI shows default icon
        val coverPath: String = run {
            val imageFiles = rootFiles.filter { file ->
                !file.isDirectory &&
                        VideoImportUtils.coverExtensions.any { ext ->
                            file.name?.lowercase()?.endsWith(".$ext") == true
                        }
            }.toTypedArray()
            VideoImportUtils.findCoverInFiles(imageFiles)?.uri?.toString() ?: ""
        }

        val folderName = sourceDoc.name ?: "Unknown"

        val mangaToInsert = MangaEntity(
            id = 0L,
            title = folderName,
            description = "No description added yet.",
            thoughts = "No thoughts added.",
            coverPath = coverPath,
            isNsfw = isNsfw,
            parentUri = uriStr,
            lastModified = sourceDoc.lastModified(),
            contentType = 2, // Video
            boxPurpose = boxPurpose,
            boxLabel = null,
            workspace = workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)

        if (isCancelled()) return@withContext mangaId

        // Chapter entities: one per video file, sorted with existing app sort logic
        val videoFiles = rootFiles.filter { file ->
            !file.isDirectory &&
                    VideoImportUtils.videoExtensions.any { ext ->
                        file.name?.lowercase()?.endsWith(".$ext") == true
                    }
        }.sortedWith(compareBy { item ->
            val name = item.name ?: ""
            name.replace(Regex("\\d+")) { match ->
                match.value.padStart(10, '0')
            }
        })

        val chapterEntities = videoFiles.mapIndexed { index, videoFile ->
            val existingChapter = repository.getChapterByUri(videoFile.uri.toString())
            if (existingChapter == null) {
                ChapterEntity(
                    mangaId = mangaId,
                    title = videoFile.name?.substringBeforeLast(".") ?: videoFile.name ?: "Unknown",
                    folderUri = videoFile.uri.toString(),
                    position = index
                )
            } else if (existingChapter.mangaId != mangaId || existingChapter.position != index) {
                existingChapter.copy(mangaId = mangaId, position = index)
            } else {
                null
            }
        }.filterNotNull()

        repository.insertChapters(chapterEntities)
        onProgress(100)

        // History — per-folder independent timestamp
        com.ballade.hwaran.data.local.HistoryTracker.logEvent(
            "IMPORT",
            folderName,
            "Video • $importMode | Source: $uriStr"
        )

        return@withContext mangaId
    }
}
