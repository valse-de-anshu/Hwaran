package com.ballade.hwaran.data.importer.toon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.metadata.EntryMetadata
import com.ballade.hwaran.core.metadata.MediaMetadataManager
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

object ToonLocalSingleImport {

    private data class DiscoveredChapter(
        val title: String,
        val folderDoc: DocumentFile,
        val pages: List<DocumentFile>,
        val relativeSubpath: String
    )

    suspend fun execute(
        context: Context,
        repository: ToonImportRepository,
        uri: Uri,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        importMode: String = "Single Import",
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit
    ): Long? = withContext(Dispatchers.IO) {

        val sourceDoc = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null
        val (isValid, _) = ToonImportUtils.isToonFolderValid(sourceDoc)
        if (!isValid) return@withContext null
        if (isCancelled()) return@withContext null

        val vaultBase = File(context.filesDir, "manga_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        File(vaultBase, ".nomedia").createNewFile()

        val folderName = sourceDoc.name ?: "Unknown"

        // 1. Extract metadata from .zine/*.json or root *.json (Priority 1 & 2)
        val parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, sourceDoc)

        // Rule 4: Metadata overrides filesystem hints
        val finalTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: folderName
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() } ?: "No description added yet."
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

        val destination = File(vaultBase, finalTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        if (!destination.exists()) destination.mkdirs()

        // Check if already exists
        val existingManga = repository.getRootMangaByUri(destination.absolutePath)

        val supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
        val isSupportedImage: (DocumentFile) -> Boolean = { file ->
            val name = file.name ?: ""
            !name.startsWith(".") && supportedExtensions.contains(name.substringAfterLast(".", "").lowercase())
        }

        val rootFiles = sourceDoc.listFiles()

        // 2. Discover Media Hierarchy (Rule 2 & Invariant Rule 7)
        // Material -> Volume (optional) -> Chapter -> Page
        val subDirs = rootFiles.filter { it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
        val looseImages = rootFiles.filter {
            !it.isDirectory && isSupportedImage(it) && !ZineMetadataExtractor.isDedicatedCoverName(it.name)
        }

        val discoveredChapters = mutableListOf<DiscoveredChapter>()

        for (subDir in subDirs) {
            val subChildren = subDir.listFiles()
            val childSubDirs = subChildren.filter { it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
            val directImages = subChildren.filter { !it.isDirectory && isSupportedImage(it) && !ZineMetadataExtractor.isDedicatedCoverName(it.name) }

            if (childSubDirs.isNotEmpty()) {
                // SubDir is a Volume grouping multiple chapters
                val volName = subDir.name ?: "Volume"
                for (chapDoc in childSubDirs) {
                    val chapImages = chapDoc.listFiles().filter { !it.isDirectory && isSupportedImage(it) }
                    if (chapImages.isNotEmpty()) {
                        val chapName = chapDoc.name ?: "Chapter"
                        val combinedTitle = if (chapName.contains(volName, ignoreCase = true)) chapName else "$volName - $chapName"
                        discoveredChapters.add(
                            DiscoveredChapter(
                                title = combinedTitle,
                                folderDoc = chapDoc,
                                pages = chapImages,
                                relativeSubpath = "$volName/$chapName"
                            )
                        )
                    }
                }
            } else if (directImages.isNotEmpty()) {
                // SubDir is a direct Chapter
                val chapName = subDir.name ?: "Chapter"
                discoveredChapters.add(
                    DiscoveredChapter(
                        title = chapName,
                        folderDoc = subDir,
                        pages = directImages,
                        relativeSubpath = chapName
                    )
                )
            }
        }

        // If no subfolder chapters were found, but loose images exist directly in root
        if (discoveredChapters.isEmpty() && looseImages.isNotEmpty()) {
            discoveredChapters.add(
                DiscoveredChapter(
                    title = finalTitle,
                    folderDoc = sourceDoc,
                    pages = looseImages,
                    relativeSubpath = ""
                )
            )
        }

        // Sort chapters numerically / naturally
        discoveredChapters.sortWith(compareBy { item ->
            item.title.replace(Regex("\\d+")) { match ->
                match.value.padStart(10, '0')
            }
        })

        // 3. Find dedicated cover image (Rule 5: Strictly artwork, never media item)
        val coverDoc = ZineMetadataExtractor.findCoverInDocumentFolder(sourceDoc, parsedZine?.coverFileName)

        // 4. Copying Files to Vault
        val totalPagesCount = discoveredChapters.sumOf { it.pages.size }
        val totalFilesCount = totalPagesCount + if (coverDoc != null) 1 else 0

        var copiedFilesCount = 0
        var lastReportedProgress = -1

        val reportProgress: () -> Unit = {
            copiedFilesCount++
            if (totalFilesCount > 0) {
                val currentProgress = ((copiedFilesCount.toDouble() / totalFilesCount) * 100).toInt()
                if (currentProgress != lastReportedProgress) {
                    lastReportedProgress = currentProgress
                    onProgress(currentProgress.coerceIn(0, 100))
                }
            }
        }

        // Copy Cover if present
        var finalCoverPath = existingManga?.coverPath ?: ""
        if (coverDoc != null) {
            val coverName = coverDoc.name ?: "cover.jpg"
            val destCoverFile = File(destination, coverName)
            try {
                context.contentResolver.openInputStream(coverDoc.uri)?.use { input ->
                    destCoverFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                finalCoverPath = destCoverFile.absolutePath
                reportProgress()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val semaphore = Semaphore(4)
        val chapterDestinations = mutableListOf<Pair<DiscoveredChapter, File>>()

        coroutineScope {
            discoveredChapters.map { chapter ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (isCancelled()) return@withPermit
                        val targetDir = if (chapter.relativeSubpath.isEmpty()) destination else File(destination, chapter.relativeSubpath)
                        if (!targetDir.exists()) targetDir.mkdirs()

                        synchronized(chapterDestinations) {
                            chapterDestinations.add(chapter to targetDir)
                        }

                        chapter.pages.forEach { pageDoc ->
                            if (isCancelled()) return@forEach
                            ensureActive()
                            val pageName = pageDoc.name ?: return@forEach
                            val destFile = File(targetDir, pageName)
                            try {
                                context.contentResolver.openInputStream(pageDoc.uri)?.use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                if (isCancelled()) {
                                    destFile.delete()
                                } else {
                                    reportProgress()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        if (isCancelled()) {
            destination.deleteRecursively()
            return@withContext null
        }

        // 5. Insert or Update MangaEntity
        val mangaToInsert = MangaEntity(
            id = existingManga?.id ?: 0L,
            title = finalTitle,
            description = finalDescription,
            thoughts = existingManga?.thoughts ?: "No thoughts added.",
            coverPath = finalCoverPath,
            isNsfw = existingManga?.isNsfw ?: isNsfw,
            parentUri = destination.absolutePath,
            lastModified = destination.lastModified(),
            contentType = 0, // 0 = Manga / Toon
            boxPurpose = existingManga?.boxPurpose ?: boxPurpose ?: parsedZine?.type,
            boxLabel = existingManga?.boxLabel,
            genre = finalTags ?: existingManga?.genre,
            workspace = existingManga?.workspace ?: workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)

        // 6. Insert Chapters
        val chapterEntities = chapterDestinations.sortedWith(compareBy { (chap, _) ->
            chap.title.replace(Regex("\\d+")) { match ->
                match.value.padStart(10, '0')
            }
        }).mapIndexed { index, (chap, dir) ->
            val existingChapter = repository.getChapterByUri(dir.absolutePath)
            if (existingChapter == null) {
                ChapterEntity(
                    mangaId = mangaId,
                    title = chap.title,
                    folderUri = dir.absolutePath,
                    position = index
                )
            } else {
                existingChapter.copy(mangaId = mangaId, title = chap.title, position = index)
            }
        }

        repository.insertChapters(chapterEntities)

        // 7. Cache Full Metadata
        val hasZineFolder = destination.listFiles()?.any { it.isDirectory && it.name.equals(".zine", ignoreCase = true) } == true
        val hasExistingJson = destination.listFiles()?.any { it.isFile && it.extension.equals("json", ignoreCase = true) } == true
        if (parsedZine != null || hasZineFolder || hasExistingJson) {
            val meta = parsedZine?.toEntryMetadata() ?: EntryMetadata(
                title = mangaToInsert.title,
                author = "",
                description = mangaToInsert.description,
                type = mangaToInsert.boxPurpose ?: "Manga"
            )
            MediaMetadataManager.saveMetadata(
                context = context,
                mangaId = mangaId,
                parentUri = destination.absolutePath,
                metadata = meta,
                forceWriteToFile = false
            )
        }

        com.ballade.hwaran.core.util.HistoryTracker.logEvent(
            "IMPORT",
            mangaToInsert.title,
            "Toon • $importMode | Source: ${destination.absolutePath}"
        )

        return@withContext mangaId
    }
}
