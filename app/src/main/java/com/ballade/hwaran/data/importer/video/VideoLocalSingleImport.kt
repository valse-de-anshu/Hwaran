package com.ballade.hwaran.data.importer.video

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
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

        val vaultBase = File(context.filesDir, "video_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        File(vaultBase, ".nomedia").createNewFile()

        val folderName = sourceDoc.name ?: "Unknown"
        val destination = File(vaultBase, folderName)

        // --- Duplicate check (before touching anything) ---
        val existingManga = repository.getRootMangaByUri(destination.absolutePath)
        if (existingManga != null) return@withContext existingManga.id

        if (!destination.exists()) destination.mkdirs()

        // Gather all files to copy (videos + images for cover)
        val allSourceFiles = sourceDoc.listFiles()?.filter { file ->
            !file.isDirectory && !(file.name?.startsWith(".") == true)
        } ?: emptyList()

        val totalFilesCount = allSourceFiles.size
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

        val copiedFiles = mutableListOf<File>()

        // Copy files one at a time, with cancel check per file
        for (child in allSourceFiles) {
            if (isCancelled()) {
                destination.deleteRecursively()
                return@withContext null
            }
            val name = child.name ?: continue
            val destFile = File(destination, name)
            try {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (isCancelled()) {
                    destFile.delete()
                    destination.deleteRecursively()
                    return@withContext null
                }
                copiedFiles.add(destFile)
                reportProgress()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (isCancelled()) {
            destination.deleteRecursively()
            return@withContext null
        }

        // Determine cover — if no image found, coverPath stays "" and UI shows default icon
        val coverPath: String = run {
            val imageFiles = copiedFiles.filter { file ->
                VideoImportUtils.coverExtensions.any { ext ->
                    file.name.lowercase().endsWith(".$ext")
                }
            }
            VideoImportUtils.findCoverInJavaFiles(imageFiles)?.absolutePath ?: ""
        }

        val mangaToInsert = MangaEntity(
            id = 0L,
            title = folderName,
            description = "No description added yet.",
            thoughts = "No thoughts added.",
            coverPath = coverPath,
            isNsfw = isNsfw,
            parentUri = destination.absolutePath,
            lastModified = destination.lastModified(),
            contentType = 2, // Video
            boxPurpose = boxPurpose,
            boxLabel = null,
            workspace = workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)

        // Chapter entities: one per video file, sorted with existing app sort logic
        val videoFiles = copiedFiles.filter { file ->
            VideoImportUtils.videoExtensions.any { ext ->
                file.name.lowercase().endsWith(".$ext")
            }
        }.sortedWith(compareBy { item ->
            item.name.replace(Regex("\\d+")) { match ->
                match.value.padStart(10, '0')
            }
        })

        val chapterEntities = videoFiles.mapIndexed { index, videoFile ->
            ChapterEntity(
                mangaId = mangaId,
                title = videoFile.nameWithoutExtension,
                folderUri = videoFile.absolutePath,
                position = index
            )
        }

        repository.insertChapters(chapterEntities)

        // History — per-folder independent timestamp
        com.ballade.hwaran.core.util.HistoryTracker.logEvent(
            "IMPORT",
            folderName,
            "Video • $importMode | Source: ${destination.absolutePath}"
        )

        return@withContext mangaId
    }
}
