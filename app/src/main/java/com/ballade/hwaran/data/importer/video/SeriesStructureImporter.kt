package com.ballade.hwaran.data.importer.video

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.metadata.MediaMetadataManager
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import com.ballade.hwaran.core.util.CoverCacheManager
import com.ballade.hwaran.core.util.HistoryTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SeriesStructureImporter
 *
 * Implements Hwaran Import & Metadata Rules for Video Series:
 *
 * Folder structure:
 *   Series
 *   └── Season
 *       └── Episode
 *           └── Video
 *
 * Rules:
 * - Video files: .mp4, .mkv, .webm, .avi, etc.
 * - Seasons group episodes.
 * - If a series only has videos without season folders:
 *     Series -> Season 1 (inferred) -> Episode -> Video.
 * - Metadata priority: .zine JSON > root JSON > filesystem hints.
 * - Covers are never media items.
 * - .zine and .json are internal only.
 * - Never flatten structured media.
 */
object SeriesStructureImporter {

    /**
     * Returns true if the folder contains at least one subfolder with video files.
     */
    fun isOrganizedSeries(context: Context, doc: DocumentFile): Boolean {
        val children = doc.listFiles() ?: return false
        return children.any { child ->
            child.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(child.name) && hasVideoFiles(child)
        }
    }

    /**
     * Imports a series folder (structured with seasons or single folder with videos).
     */
    suspend fun execute(
        context: Context,
        repository: VideoImportRepository,
        rootDoc: DocumentFile,
        isLocalMode: Boolean,
        workspace: String?,
        isNsfw: Boolean,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit
    ): Long? = withContext(Dispatchers.IO) {

        val rootFolderName = rootDoc.name ?: "Unknown Series"
        if (isCancelled()) return@withContext null

        // 1. Extract metadata from .zine/*.json or root *.json (Rule 4: Metadata overrides filesystem)
        val parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, rootDoc)
        val finalTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: rootFolderName
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() } ?: "No description added yet."
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

        // 2. Prepare vault directory (Local Mode Only)
        val rootDestinationPath = if (isLocalMode) {
            val vaultBase = File(context.filesDir, "video_vault")
            if (!vaultBase.exists()) vaultBase.mkdirs()
            File(vaultBase, ".nomedia").createNewFile()

            val safeFolderName = finalTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val dest = File(vaultBase, safeFolderName)
            if (!dest.exists()) dest.mkdirs()
            dest.absolutePath
        } else {
            rootDoc.uri.toString()
        }

        // Duplicate check
        val existingRoot = repository.getRootMangaByUri(rootDestinationPath)
        if (existingRoot != null) return@withContext existingRoot.id

        // 3. Scan children (ignoring internal metadata folders and json)
        val children = rootDoc.listFiles().toList()
        val subfolders = children.filter {
            it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) && hasVideoFiles(it)
        }
        val rootLevelVideos = children.filter { file ->
            !file.isDirectory && VideoImportUtils.videoExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        }

        // 4. Total files count for progress reporting
        val totalFiles = subfolders.sumOf { countVideoFiles(it) } + rootLevelVideos.size
        var processedFiles = 0

        fun reportProgress() {
            processedFiles++
            if (totalFiles > 0) {
                onProgress(((processedFiles.toDouble() / totalFiles) * 100).toInt().coerceIn(0, 100))
            }
        }

        // 5. Dedicated Cover Detection (Rule 5: Artwork only, never media item)
        val rootCoverDoc = ZineMetadataExtractor.findCoverInDocumentFolder(rootDoc, parsedZine?.coverFileName)
        val rootCoverPath: String = if (rootCoverDoc != null) {
            if (isLocalMode) {
                val destBase = File(rootDestinationPath)
                val destFile = File(destBase, rootCoverDoc.name ?: "cover.jpg")
                try {
                    context.contentResolver.openInputStream(rootCoverDoc.uri)?.use { input ->
                        destFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    destFile.absolutePath
                } catch (e: Exception) { "" }
            } else {
                CoverCacheManager.cacheCoverFromUri(context, rootCoverDoc.uri, "series", finalTitle)
                    ?: rootCoverDoc.uri.toString()
            }
        } else ""

        // 6. Create root MangaEntity for Series
        val rootManga = MangaEntity(
            id = 0L,
            title = finalTitle,
            description = finalDescription,
            thoughts = "No thoughts added.",
            coverPath = rootCoverPath,
            isNsfw = isNsfw,
            parentUri = rootDestinationPath,
            lastModified = if (isLocalMode) File(rootDestinationPath).lastModified() else rootDoc.lastModified(),
            contentType = 2, // Video
            boxPurpose = "series",
            boxLabel = null,
            parentMangaId = null,
            genre = finalTags,
            workspace = workspace
        )
        val rootId = repository.insertManga(rootManga)
        val createdContainers = mutableListOf<Pair<Long, String>>()

        if (isCancelled()) return@withContext null

        // 7. Hierarchy Implementation:
        // Case A: Subfolders exist -> Create a Season container for each subfolder
        if (subfolders.isNotEmpty()) {
            subfolders.sortedWith(compareBy { folder ->
                folder.name?.replace(Regex("\\d+")) { it.value.padStart(10, '0') } ?: ""
            }).forEachIndexed { idx, subfolder ->
                if (isCancelled()) return@withContext null

                val containerName = subfolder.name ?: "Season ${idx + 1}"

                val containerDestPath = if (isLocalMode) {
                    val containerDestDir = File(rootDestinationPath, containerName)
                    if (!containerDestDir.exists()) containerDestDir.mkdirs()
                    containerDestDir.absolutePath
                } else {
                    subfolder.uri.toString()
                }

                // Copy video files from the subfolder (Local Mode only)
                val chapterEntities = if (isLocalMode) {
                    val videoFiles = copyVideoFiles(
                        context = context,
                        sourceDir = subfolder,
                        destDir = File(containerDestPath),
                        isCancelled = isCancelled,
                        onFileCopied = { reportProgress() }
                    ) ?: return@withContext null

                    videoFiles.mapIndexed { epIdx, videoFile ->
                        ChapterEntity(
                            mangaId = 0L,
                            title = videoFile.nameWithoutExtension,
                            folderUri = videoFile.absolutePath,
                            position = epIdx
                        )
                    }
                } else {
                    val videos = subfolder.listFiles().filter { f ->
                        !f.isDirectory && VideoImportUtils.videoExtensions.any { ext -> f.name?.lowercase()?.endsWith(".$ext") == true }
                    }.sortedWith(compareBy { f ->
                        f.name?.replace(Regex("\\d+")) { it.value.padStart(10, '0') } ?: ""
                    })

                    videos.mapIndexed { epIdx, videoDoc ->
                        ChapterEntity(
                            mangaId = 0L,
                            title = videoDoc.name?.substringBeforeLast(".") ?: "Unknown",
                            folderUri = videoDoc.uri.toString(),
                            position = epIdx
                        )
                    }
                }

                // Detect season cover
                val containerCoverDoc = ZineMetadataExtractor.findCoverInDocumentFolder(subfolder)
                val containerCoverPath = if (containerCoverDoc != null) {
                    if (isLocalMode) {
                        val destFile = File(containerDestPath, containerCoverDoc.name ?: "cover.jpg")
                        try {
                            context.contentResolver.openInputStream(containerCoverDoc.uri)?.use { input ->
                                destFile.outputStream().use { out -> input.copyTo(out) }
                            }
                            destFile.absolutePath
                        } catch (_: Exception) { "" }
                    } else {
                        CoverCacheManager.cacheCoverFromUri(context, containerCoverDoc.uri, "series_container", containerName)
                            ?: containerCoverDoc.uri.toString()
                    }
                } else ""

                // Create child MangaEntity (Season container)
                val containerManga = MangaEntity(
                    id = 0L,
                    title = containerName,
                    description = "No description added yet.",
                    thoughts = "No thoughts added.",
                    coverPath = containerCoverPath,
                    isNsfw = isNsfw,
                    parentUri = containerDestPath,
                    lastModified = if (isLocalMode) File(containerDestPath).lastModified() else subfolder.lastModified(),
                    contentType = 2, // Video
                    boxPurpose = "series",
                    boxLabel = containerName,
                    parentMangaId = rootId,
                    workspace = workspace
                )
                val containerId = repository.insertManga(containerManga)
                createdContainers.add(containerId to containerName)

                // Associate chapters with containerId and insert
                repository.insertChapters(chapterEntities.map { it.copy(mangaId = containerId) })
            }

            // Loose videos alongside season folders -> "Specials" container
            if (rootLevelVideos.isNotEmpty() && !isCancelled()) {
                val specialsContainerName = "Specials"
                val specialsDestPath = if (isLocalMode) {
                    val dir = File(rootDestinationPath, specialsContainerName)
                    if (!dir.exists()) dir.mkdirs()
                    dir.absolutePath
                } else {
                    rootDestinationPath
                }

                val looseChapters = if (isLocalMode) {
                    val copied = mutableListOf<File>()
                    for (src in rootLevelVideos) {
                        if (isCancelled()) return@withContext null
                        val destFile = File(specialsDestPath, src.name ?: continue)
                        try {
                            context.contentResolver.openInputStream(src.uri)?.use { input ->
                                destFile.outputStream().use { out -> input.copyTo(out) }
                            }
                            copied.add(destFile)
                            reportProgress()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    copied.sortedWith(compareBy { f ->
                        f.name.replace(Regex("\\d+")) { it.value.padStart(10, '0') }
                    }).mapIndexed { epIdx, file ->
                        ChapterEntity(
                            mangaId = 0L,
                            title = file.nameWithoutExtension,
                            folderUri = file.absolutePath,
                            position = epIdx
                        )
                    }
                } else {
                    rootLevelVideos.sortedWith(compareBy { f ->
                        f.name?.replace(Regex("\\d+")) { it.value.padStart(10, '0') } ?: ""
                    }).mapIndexed { epIdx, doc ->
                        ChapterEntity(
                            mangaId = 0L,
                            title = doc.name?.substringBeforeLast(".") ?: "Unknown",
                            folderUri = doc.uri.toString(),
                            position = epIdx
                        )
                    }
                }

                val specialsManga = MangaEntity(
                    id = 0L,
                    title = specialsContainerName,
                    description = "Root level episodes",
                    thoughts = "",
                    coverPath = rootCoverPath,
                    isNsfw = isNsfw,
                    parentUri = specialsDestPath,
                    lastModified = System.currentTimeMillis(),
                    contentType = 2,
                    boxPurpose = "series",
                    boxLabel = specialsContainerName,
                    parentMangaId = rootId,
                    workspace = workspace
                )
                val specialsId = repository.insertManga(specialsManga)
                repository.insertChapters(looseChapters.map { it.copy(mangaId = specialsId) })
            }

        } else if (rootLevelVideos.isNotEmpty()) {
            // Case B: No season folders exist!
            // Rule: "If a series only has videos without season folders: Series -> Season 1 (inferred) -> Episode -> Video."
            val inferredSeasonName = "Season 1"
            val seasonDestPath = if (isLocalMode) {
                val dir = File(rootDestinationPath, inferredSeasonName)
                if (!dir.exists()) dir.mkdirs()
                dir.absolutePath
            } else {
                rootDestinationPath
            }

            val seasonChapters = if (isLocalMode) {
                val copied = mutableListOf<File>()
                for (src in rootLevelVideos) {
                    if (isCancelled()) return@withContext null
                    val destFile = File(seasonDestPath, src.name ?: continue)
                    try {
                        context.contentResolver.openInputStream(src.uri)?.use { input ->
                            destFile.outputStream().use { out -> input.copyTo(out) }
                        }
                        copied.add(destFile)
                        reportProgress()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                copied.sortedWith(compareBy { f ->
                    f.name.replace(Regex("\\d+")) { it.value.padStart(10, '0') }
                }).mapIndexed { epIdx, file ->
                    ChapterEntity(
                        mangaId = 0L,
                        title = file.nameWithoutExtension,
                        folderUri = file.absolutePath,
                        position = epIdx
                    )
                }
            } else {
                rootLevelVideos.sortedWith(compareBy { f ->
                    f.name?.replace(Regex("\\d+")) { it.value.padStart(10, '0') } ?: ""
                }).mapIndexed { epIdx, doc ->
                    ChapterEntity(
                        mangaId = 0L,
                        title = doc.name?.substringBeforeLast(".") ?: "Unknown",
                        folderUri = doc.uri.toString(),
                        position = epIdx
                    )
                }
            }

            val season1Manga = MangaEntity(
                id = 0L,
                title = inferredSeasonName,
                description = "Season 1",
                thoughts = "",
                coverPath = rootCoverPath,
                isNsfw = isNsfw,
                parentUri = seasonDestPath,
                lastModified = System.currentTimeMillis(),
                contentType = 2,
                boxPurpose = "series",
                boxLabel = inferredSeasonName,
                parentMangaId = rootId,
                workspace = workspace
            )
            val season1Id = repository.insertManga(season1Manga)
            createdContainers.add(season1Id to inferredSeasonName)
            repository.insertChapters(seasonChapters.map { it.copy(mangaId = season1Id) })
        }

        // 8. Cache metadata
        if (parsedZine != null) {
            MediaMetadataManager.saveMetadata(
                context = context,
                mangaId = rootId,
                parentUri = rootDestinationPath,
                metadata = parsedZine.toEntryMetadata(),
                forceWriteToFile = false
            )
        }

        HistoryTracker.logEvent(
            "IMPORT",
            finalTitle,
            "Video • Series Structure Import | Containers: ${createdContainers.size} | Source: $rootDestinationPath"
        )

        val firstChildId = createdContainers.minByOrNull { it.second.lowercase() }?.first
        return@withContext firstChildId ?: rootId
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun hasVideoFiles(dir: DocumentFile): Boolean {
        return dir.listFiles().any { file ->
            !file.isDirectory && VideoImportUtils.videoExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        }
    }

    private fun countVideoFiles(dir: DocumentFile): Int {
        return dir.listFiles().count { file ->
            !file.isDirectory && VideoImportUtils.videoExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        }
    }

    private suspend fun copyVideoFiles(
        context: Context,
        sourceDir: DocumentFile,
        destDir: File,
        isCancelled: () -> Boolean,
        onFileCopied: () -> Unit
    ): List<File>? {
        val videoFiles = mutableListOf<File>()
        val sources = sourceDir.listFiles().filter { file ->
            !file.isDirectory && VideoImportUtils.videoExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        }

        for (src in sources) {
            if (isCancelled()) return null
            val destFile = File(destDir, src.name ?: continue)
            try {
                context.contentResolver.openInputStream(src.uri)?.use { input ->
                    destFile.outputStream().use { it2 -> input.copyTo(it2) }
                }
                videoFiles.add(destFile)
                onFileCopied()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return videoFiles.sortedWith(compareBy { f ->
            f.name.replace(Regex("\\d+")) { it.value.padStart(10, '0') }
        })
    }
}
