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

        // If this is a series or structured series, delegate to SeriesStructureImporter
        if (boxPurpose == "series" || (boxPurpose == null && SeriesStructureImporter.isOrganizedSeries(context, sourceDoc))) {
            return@withContext SeriesStructureImporter.execute(
                context = context,
                repository = repository,
                rootDoc = sourceDoc,
                isLocalMode = true,
                workspace = workspace,
                isNsfw = isNsfw,
                isCancelled = isCancelled,
                onProgress = onProgress
            )
        }

        // Otherwise import as Channel (Channel -> Video, flat collection)
        val vaultBase = File(context.filesDir, "video_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        File(vaultBase, ".nomedia").createNewFile()

        val folderName = sourceDoc.name ?: "Unknown"

        // 1. Extract metadata from .zine/*.json or root *.json
        val parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, sourceDoc)
        val finalTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: folderName
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() } ?: "No description added yet."
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

        val safeFolderName = finalTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val destination = File(vaultBase, safeFolderName)
        if (!destination.exists()) destination.mkdirs()

        val existingManga = repository.getRootMangaByUri(destination.absolutePath)
        if (existingManga != null) return@withContext existingManga.id

        // Gather video files to copy (exclude internal metadata and auxiliary)
        val allSourceFiles = sourceDoc.listFiles().filter { file ->
            !file.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(file.name)
        }

        // Dedicated cover image
        val coverDoc = ZineMetadataExtractor.findCoverInDocumentFolder(sourceDoc, parsedZine?.coverFileName)
        val videoSourceFiles = allSourceFiles.filter { file ->
            VideoImportUtils.videoExtensions.any { ext -> file.name?.lowercase()?.endsWith(".$ext") == true }
        }

        val totalFilesCount = videoSourceFiles.size + if (coverDoc != null) 1 else 0
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

        val copiedVideoFiles = mutableListOf<File>()
        for (child in videoSourceFiles) {
            if (isCancelled()) {
                destination.deleteRecursively()
                return@withContext null
            }
            val name = child.name ?: continue
            val destFile = File(destination, name)
            try {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (isCancelled()) {
                    destFile.delete()
                    destination.deleteRecursively()
                    return@withContext null
                }
                copiedVideoFiles.add(destFile)
                reportProgress()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (isCancelled()) {
            destination.deleteRecursively()
            return@withContext null
        }

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
            boxPurpose = boxPurpose ?: "channel",
            boxLabel = null,
            genre = finalTags,
            workspace = workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)

        // Chapter entities: one per video file
        val chapterEntities = copiedVideoFiles.sortedWith(compareBy { item ->
            item.name.replace(Regex("\\d+")) { match ->
                match.value.padStart(10, '0')
            }
        }).mapIndexed { index, videoFile ->
            ChapterEntity(
                mangaId = mangaId,
                title = videoFile.nameWithoutExtension,
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
                metadata = parsedZine.toEntryMetadata()
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
