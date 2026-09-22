package com.ballade.hwaran.data.importer.video

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.metadata.MediaMetadataManager
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoLocalSingleImport {

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

        val folderName = sourceDoc.name ?: "Unknown"
        val finalTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: folderName
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() } ?: "No description added yet."
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

        val vaultBase = File(context.filesDir, "video_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        File(vaultBase, ".nomedia").createNewFile()

        val safeFolderName = finalTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val destination = File(vaultBase, safeFolderName)
        if (!destination.exists()) destination.mkdirs()

        val existingManga = repository.getRootMangaByUri(destination.absolutePath)
        if (existingManga != null) return@withContext existingManga.id

        // 2. Discover all video and subtitle files (root + subfolders like Videos/, season 1/, etc.)
        val videoSourceFiles = VideoImportUtils.findVideoFiles(sourceDoc)
        val subtitleSourceFiles = VideoImportUtils.findSubtitleFiles(sourceDoc)
        val coverDoc = VideoImportUtils.findCover(sourceDoc, parsedZine?.coverFileName)

        val totalFilesCount = videoSourceFiles.size + subtitleSourceFiles.size + if (coverDoc != null) 1 else 0
        var copiedFilesCount = 0
        var lastReportedProgress = -1

        val reportProgress: () -> Unit = {
            copiedFilesCount++
            if (totalFilesCount > 0) {
                val current = ((copiedFilesCount.toDouble() / totalFilesCount) * 100).toInt()
                if (current != lastReportedProgress) {
                    lastReportedProgress = current
                    onProgress(current.coerceIn(0, 100))
                }
            }
        }

        // Copy cover
        var coverPath = ""
        if (coverDoc != null) {
            val destCoverFile = File(destination, coverDoc.name ?: "cover.jpg")
            try {
                context.contentResolver.openInputStream(coverDoc.uri)?.use { input ->
                    destCoverFile.outputStream().use { output -> input.copyTo(output) }
                }
                coverPath = destCoverFile.absolutePath
                reportProgress()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val copiedVideoFiles = mutableListOf<Pair<File, String>>()
        for ((index, child) in videoSourceFiles.withIndex()) {
            if (isCancelled()) {
                destination.deleteRecursively()
                return@withContext null
            }
            val originalName = child.name ?: "video_$index.mp4"
            var destFile = File(destination, originalName)
            if (destFile.exists()) {
                destFile = File(destination, "${index + 1}_$originalName")
            }
            try {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (isCancelled()) {
                    destFile.delete()
                    destination.deleteRecursively()
                    return@withContext null
                }
                val originalTitle = child.name?.substringBeforeLast(".") ?: "Unknown"
                copiedVideoFiles.add(destFile to originalTitle)
                reportProgress()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Copy subtitle files into destination vault
        for (subDoc in subtitleSourceFiles) {
            if (isCancelled()) {
                destination.deleteRecursively()
                return@withContext null
            }
            val subName = subDoc.name ?: "subtitle.srt"
            val destSubFile = File(destination, subName)
            try {
                context.contentResolver.openInputStream(subDoc.uri)?.use { input ->
                    destSubFile.outputStream().use { output -> input.copyTo(output) }
                }
                reportProgress()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (isCancelled()) {
            destination.deleteRecursively()
            return@withContext null
        }

        val effectiveBoxPurpose = boxPurpose ?: existingManga?.boxPurpose ?: "series"

        val mangaToInsert = MangaEntity(
            id = 0L,
            title = finalTitle,
            description = finalDescription,
            thoughts = "No thoughts added.",
            coverPath = coverPath,
            isNsfw = isNsfw,
            parentUri = destination.absolutePath,
            lastModified = destination.lastModified(),
            contentType = 2, // Video
            boxPurpose = effectiveBoxPurpose,
            boxLabel = null,
            genre = finalTags,
            workspace = workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)

        // Chapter entities: one per copied video file
        val chapterEntities = copiedVideoFiles.mapIndexed { index, (videoFile, originalTitle) ->
            ChapterEntity(
                mangaId = mangaId,
                title = originalTitle,
                folderUri = videoFile.absolutePath,
                position = index
            )
        }

        repository.insertChapters(chapterEntities)

        if (parsedZine != null) {
            MediaMetadataManager.saveMetadata(
                context = context,
                mangaId = mangaId,
                parentUri = destination.absolutePath,
                metadata = parsedZine.toEntryMetadata(),
                forceWriteToFile = false
            )
        }

        com.ballade.hwaran.core.util.HistoryTracker.logEvent(
            "IMPORT",
            finalTitle,
            "Video • $importMode | Source: ${destination.absolutePath}"
        )

        return@withContext mangaId
    }
}
