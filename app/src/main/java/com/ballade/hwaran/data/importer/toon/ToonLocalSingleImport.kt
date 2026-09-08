package com.ballade.hwaran.data.importer.toon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
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
        val (isValid, reason) = ToonImportUtils.isToonFolderValid(sourceDoc)
        if (!isValid) return@withContext null
        if (isCancelled()) return@withContext null

        val vaultBase = File(context.filesDir, "manga_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        File(vaultBase, ".nomedia").createNewFile()

        val mangaName = sourceDoc.name ?: "Unknown"
        val destination = File(vaultBase, mangaName)
        if (!destination.exists()) destination.mkdirs()

        // Check if exists
        val existingManga = repository.getRootMangaByUri(destination.absolutePath)

        val supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
        val isSupportedFile: (DocumentFile) -> Boolean = { file ->
            val name = file.name ?: ""
            !name.startsWith(".") && supportedExtensions.contains(name.substringAfterLast(".", "").lowercase())
        }

        val rootFiles = sourceDoc.listFiles() ?: emptyArray()
        val chapterDocs = rootFiles.filter { it.isDirectory && !(it.name?.startsWith(".") == true) }
        val looseFiles = rootFiles.filter { !it.isDirectory && isSupportedFile(it) }

        val chapterFilesMap = mutableMapOf<DocumentFile, List<DocumentFile>>()
        var totalFilesCount = looseFiles.size
        
        chapterDocs.forEach { doc ->
            val filesInChapter = doc.listFiles()?.filter { !it.isDirectory && isSupportedFile(it) } ?: emptyList()
            chapterFilesMap[doc] = filesInChapter
            totalFilesCount += filesInChapter.size
        }

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

        val semaphore = Semaphore(4)
        val copiedLooseFiles = mutableListOf<File>()
        val copiedChapters = mutableListOf<File>()

        coroutineScope {
            looseFiles.map { child ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (isCancelled()) return@withPermit
                        ensureActive()
                        val name = child.name ?: return@withPermit
                        val destFile = File(destination, name)
                        try {
                            context.contentResolver.openInputStream(child.uri)?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (isCancelled()) {
                                destFile.delete()
                            } else {
                                synchronized(copiedLooseFiles) { copiedLooseFiles.add(destFile) }
                                reportProgress()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }.awaitAll()
        }

        coroutineScope {
            chapterDocs.map { chapterDoc ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (isCancelled()) return@withPermit
                        val name = chapterDoc.name ?: return@withPermit
                        val chapterDest = File(destination, name)
                        chapterDest.mkdirs()
                        if (!isCancelled()) {
                            synchronized(copiedChapters) { copiedChapters.add(chapterDest) }
                        }
                        
                        val children = chapterFilesMap[chapterDoc] ?: emptyList()
                        children.forEach { child ->
                            if (isCancelled()) return@forEach
                            ensureActive()
                            val childName = child.name ?: return@forEach
                            val destFile = File(chapterDest, childName)
                            try {
                                context.contentResolver.openInputStream(child.uri)?.use { input ->
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

        // Determine cover
        var coverPath = existingManga?.coverPath ?: ""
        if (coverPath.isEmpty() || existingManga == null) {
            val potentialCover = ToonImportUtils.findCoverInJavaFiles(copiedLooseFiles)
            if (potentialCover != null) {
                coverPath = potentialCover.absolutePath
            } else {
                val coverFile = copiedLooseFiles.find { it.name.lowercase().contains("cover") }
                if (coverFile != null) {
                    coverPath = coverFile.absolutePath
                } else if (copiedLooseFiles.isNotEmpty()) {
                    coverPath = copiedLooseFiles.first().absolutePath
                }
            }
        }

        val mangaToInsert = MangaEntity(
            id = existingManga?.id ?: 0L,
            title = existingManga?.title ?: mangaName,
            description = existingManga?.description ?: "No description added yet.",
            thoughts = existingManga?.thoughts ?: "No thoughts added.",
            coverPath = coverPath,
            isNsfw = existingManga?.isNsfw ?: isNsfw,
            parentUri = destination.absolutePath,
            lastModified = destination.lastModified(),
            contentType = 0, // 0 = Manga
            boxPurpose = existingManga?.boxPurpose ?: boxPurpose,
            boxLabel = existingManga?.boxLabel,
            workspace = existingManga?.workspace ?: workspace
        )
        
        val mangaId = repository.insertManga(mangaToInsert)

        // Insert Chapters
        val chaptersToProcess = if (copiedChapters.isNotEmpty()) {
            copiedChapters
        } else if (copiedLooseFiles.isNotEmpty()) {
            listOf(destination)
        } else {
            emptyList()
        }.sortedWith(compareBy { item ->
            item.name.replace(Regex("\\d+")) { match ->
                match.value.padStart(10, '0')
            }
        })

        val chapterEntities = mutableListOf<ChapterEntity>()
        chaptersToProcess.forEachIndexed { index, chapterItem ->
            val existingChapter = repository.getChapterByUri(chapterItem.absolutePath)
            if (existingChapter == null) {
                chapterEntities.add(
                    ChapterEntity(
                        mangaId = mangaId,
                        title = chapterItem.name,
                        folderUri = chapterItem.absolutePath,
                        position = index
                    )
                )
            } else if (existingChapter.mangaId != mangaId || existingChapter.position != index) {
                chapterEntities.add(existingChapter.copy(mangaId = mangaId, position = index))
            }
        }
        
        repository.insertChapters(chapterEntities)
        
        com.ballade.hwaran.core.util.HistoryTracker.logEvent("IMPORT", mangaToInsert.title, "Toon • $importMode | Source: ${destination.absolutePath}")
        
        return@withContext mangaId
    }
}
