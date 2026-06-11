package com.ballade.hwaran.data.video

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.data.local.HistoryTracker
import com.ballade.hwaran.data.local.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SeriesStructureImporter
 *
 * Detects organized series folder structures and auto-creates containers,
 * producing the same internal layout as the manual "+ Container" workflow:
 *
 *   Root cover MangaEntity (parentMangaId = null, boxPurpose = "series")
 *   └── Container A  (parentMangaId = root.id, boxLabel = folderName)
 *       └── chapters: video files
 *   └── Container B  (parentMangaId = root.id, boxLabel = folderName)
 *       └── chapters: video files
 *
 * Detection rule:
 *   Does the selected folder contain at least one SUBFOLDER with video files?
 *   YES → structured import (containers auto-created from subfolders)
 *   NO  → delegate to VideoLocalSingleImport (flat series)
 *
 * Root-level loose videos (present alongside subfolders):
 *   → Placed in a "Needs Label" temporary container to surface the issue clearly.
 *
 * Container naming:
 *   Uses the EXACT folder name — no season/year/type inference ever.
 */
object SeriesStructureImporter {

    /**
     * Returns true if the DocumentFile represents a folder that should be imported
     * as an organized series (has at least one subfolder containing video files).
     */
    fun isOrganizedSeries(context: Context, doc: DocumentFile): Boolean {
        val children = doc.listFiles() ?: return false
        return children.any { child ->
            child.isDirectory && hasVideoFiles(child)
        }
    }

    /**
     * Imports a pre-organized series folder into the vault.
     *
     * @param rootUri   SAF URI of the top-level series folder
     * @param workspace Target workspace name
     * @param isNsfw    NSFW flag
     * @param isCancelled Cancellation check callback
     * @param onProgress Progress callback 0–100
     * @return Root MangaEntity id on success, null on failure/cancel
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

        // ── 1. Prepare vault directory (Local Mode Only) ────────────────────────
        val rootDestinationPath = if (isLocalMode) {
            val vaultBase = File(context.filesDir, "video_vault")
            if (!vaultBase.exists()) vaultBase.mkdirs()
            File(vaultBase, ".nomedia").createNewFile()
            
            val dest = File(vaultBase, rootFolderName)
            if (!dest.exists()) dest.mkdirs()
            dest.absolutePath
        } else {
            rootDoc.uri.toString()
        }

        // Duplicate check: if already imported, return existing id
        val existingRoot = repository.getRootMangaByUri(rootDestinationPath)
        if (existingRoot != null) return@withContext existingRoot.id

        // ── 2. Scan children ─────────────────────────────────────────────────────
        val children = rootDoc.listFiles()?.toList() ?: emptyList()
        val subfolders = children.filter { it.isDirectory && hasVideoFiles(it) }
        val rootLevelVideos = children.filter { file ->
            !file.isDirectory &&
            VideoImportUtils.videoExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        }

        // ── 3. Count total files for progress reporting ──────────────────────────
        val totalFiles = subfolders.sumOf { countFiles(it) } + rootLevelVideos.size
        var processedFiles = 0

        fun reportProgress() {
            processedFiles++
            if (totalFiles > 0) {
                onProgress(((processedFiles.toDouble() / totalFiles) * 100).toInt().coerceIn(0, 100))
            }
        }

        // ── 4. Detect cover (image in root folder) ───────────────────────────────
        val rootImages = children.filter { file ->
            !file.isDirectory &&
            VideoImportUtils.coverExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        }
        val rootCoverPath: String = if (rootImages.isNotEmpty()) {
            val first = rootImages.first()
            if (isLocalMode) {
                val destBase = File(rootDestinationPath)
                val coverDestDir = File(destBase, "_cover")
                if (!coverDestDir.exists()) coverDestDir.mkdirs()
                val destFile = File(coverDestDir, first.name ?: "cover.jpg")
                try {
                    context.contentResolver.openInputStream(first.uri)?.use { input ->
                        destFile.outputStream().use { it2 -> input.copyTo(it2) }
                    }
                    destFile.absolutePath
                } catch (e: Exception) { "" }
            } else {
                first.uri.toString()
            }
        } else ""

        // ── 5. Create root MangaEntity ────────────────────────────────────────────
        val rootManga = MangaEntity(
            id = 0L,
            title = rootFolderName,
            description = "No description added yet.",
            thoughts = "No thoughts added.",
            coverPath = rootCoverPath,
            isNsfw = isNsfw,
            parentUri = rootDestinationPath,
            lastModified = if (isLocalMode) File(rootDestinationPath).lastModified() else rootDoc.lastModified(),
            contentType = 2, // Video
            boxPurpose = "series",
            boxLabel = null,
            parentMangaId = null,
            workspace = workspace
        )
        val rootId = repository.insertManga(rootManga)
        val createdContainers = mutableListOf<Pair<Long, String>>()

        if (isCancelled()) return@withContext null

        // ── 6. Create a container + chapters for each subfolder ──────────────────
        subfolders.sortedWith(compareBy { folder ->
            folder.name?.replace(Regex("\\d+")) { it.value.padStart(10, '0') }
        }).forEachIndexed { idx, subfolder ->
            if (isCancelled()) return@withContext null

            val containerName = subfolder.name ?: "Container ${idx + 1}"
            
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
                ) ?: return@withContext null // cancelled
                
                videoFiles.mapIndexed { epIdx, videoFile ->
                    ChapterEntity(
                        mangaId = 0L, // Temp, updated below
                        title = videoFile.nameWithoutExtension,
                        folderUri = videoFile.absolutePath,
                        position = epIdx
                    )
                }
            } else {
                val videos = subfolder.listFiles()?.filter { f -> 
                    !f.isDirectory && VideoImportUtils.videoExtensions.any { ext -> f.name?.lowercase()?.endsWith(".$ext") == true }
                }?.sortedWith(compareBy { f ->
                    f.name?.replace(Regex("\\d+")) { it.value.padStart(10, '0') } ?: ""
                }) ?: emptyList()
                
                videos.mapIndexed { epIdx, videoDoc ->
                    ChapterEntity(
                        mangaId = 0L, // Temp, updated below
                        title = videoDoc.name?.substringBeforeLast(".") ?: "Unknown",
                        folderUri = videoDoc.uri.toString(),
                        position = epIdx
                    )
                }
            }

            // Detect cover image for this container
            val containerCoverPath = if (isLocalMode) detectAndCopyCover(context, subfolder, File(containerDestPath)) else {
                subfolder.listFiles()?.firstOrNull { f ->
                    !f.isDirectory && VideoImportUtils.coverExtensions.any { ext -> f.name?.lowercase()?.endsWith(".$ext") == true }
                }?.uri?.toString() ?: ""
            }

            // Create child MangaEntity (container)
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

        // ── 7. Root-level loose videos → "⚠ Needs Label" container ──────────────
        if (rootLevelVideos.isNotEmpty() && !isCancelled()) {
            val needsLabelPath = if (isLocalMode) {
                val needsLabelDir = File(rootDestinationPath, "Needs Label")
                if (!needsLabelDir.exists()) needsLabelDir.mkdirs()
                needsLabelDir.absolutePath
            } else {
                rootDoc.uri.toString()
            }

            val chapterEntities = if (isLocalMode) {
                val copiedVideos = rootLevelVideos.mapNotNull { srcFile ->
                    if (isCancelled()) return@withContext null
                    val destFile = File(needsLabelPath, srcFile.name ?: return@mapNotNull null)
                    try {
                        context.contentResolver.openInputStream(srcFile.uri)?.use { input ->
                            destFile.outputStream().use { it2 -> input.copyTo(it2) }
                        }
                        reportProgress()
                        destFile
                    } catch (e: Exception) { null }
                }
                copiedVideos.sortedWith(compareBy { f ->
                    f.name.replace(Regex("\\d+")) { it.value.padStart(10, '0') }
                }).mapIndexed { idx, videoFile ->
                    ChapterEntity(
                        mangaId = 0L,
                        title = videoFile.nameWithoutExtension,
                        folderUri = videoFile.absolutePath,
                        position = idx
                    )
                }
            } else {
                val sortedVids = rootLevelVideos.sortedWith(compareBy { f ->
                    f.name?.replace(Regex("\\d+")) { it.value.padStart(10, '0') } ?: ""
                })
                sortedVids.mapIndexed { idx, videoDoc ->
                    ChapterEntity(
                        mangaId = 0L,
                        title = videoDoc.name?.substringBeforeLast(".") ?: "Unknown",
                        folderUri = videoDoc.uri.toString(),
                        position = idx
                    )
                }
            }

            val needsLabelManga = MangaEntity(
                id = 0L,
                title = "Needs Label",
                description = "These videos were found at the root level without an organized folder. Please label or reorganize them.",
                thoughts = "No thoughts added.",
                coverPath = "",
                isNsfw = isNsfw,
                parentUri = needsLabelPath,
                lastModified = if (isLocalMode) File(needsLabelPath).lastModified() else rootDoc.lastModified(),
                contentType = 2,
                boxPurpose = "series",
                boxLabel = "⚠ Needs Label",
                parentMangaId = rootId,
                workspace = workspace
            )
            val needsLabelId = repository.insertManga(needsLabelManga)

            repository.insertChapters(chapterEntities.map { it.copy(mangaId = needsLabelId) })
        }

        // ── 8. Log import ─────────────────────────────────────────────────────────
        HistoryTracker.logEvent(
            "IMPORT",
            rootFolderName,
            "Video • Series Structure Import | Containers: ${subfolders.size} | Source: $rootDestinationPath"
        )

        // Land the user on the first container (alphabetically) if subfolders exist, otherwise land on root
        val firstChildId = createdContainers.minByOrNull { it.second.lowercase() }?.first
        return@withContext firstChildId ?: rootId
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun hasVideoFiles(dir: DocumentFile): Boolean {
        return dir.listFiles()?.any { file ->
            !file.isDirectory &&
            VideoImportUtils.videoExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        } == true
    }

    private fun countFiles(dir: DocumentFile): Int {
        return dir.listFiles()?.count { !it.isDirectory } ?: 0
    }

    private suspend fun copyVideoFiles(
        context: Context,
        sourceDir: DocumentFile,
        destDir: File,
        isCancelled: () -> Boolean,
        onFileCopied: () -> Unit
    ): List<File>? {
        val videoFiles = mutableListOf<File>()
        val sources = sourceDir.listFiles()?.filter { file ->
            !file.isDirectory &&
            VideoImportUtils.videoExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        } ?: return videoFiles

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

    private fun detectAndCopyCover(
        context: Context,
        sourceDir: DocumentFile,
        destDir: File
    ): String {
        val coverSrc = sourceDir.listFiles()?.firstOrNull { file ->
            !file.isDirectory &&
            VideoImportUtils.coverExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        } ?: return ""
        val destFile = File(destDir, coverSrc.name ?: "cover.jpg")
        return try {
            context.contentResolver.openInputStream(coverSrc.uri)?.use { input ->
                destFile.outputStream().use { it2 -> input.copyTo(it2) }
            }
            destFile.absolutePath
        } catch (e: Exception) { "" }
    }
}
