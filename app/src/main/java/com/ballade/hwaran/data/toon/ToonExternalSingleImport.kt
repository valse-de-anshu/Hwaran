package com.ballade.hwaran.data.toon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.data.local.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ToonExternalSingleImport {

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

        val uriStr = uri.toString()
        val existingManga = repository.getRootMangaByUri(uriStr)

        val rootFiles = sourceDoc.listFiles() ?: emptyArray()
        
        // Determine cover
        var coverPath = existingManga?.coverPath ?: ""
        if (coverPath.isEmpty() || existingManga == null) {
            val potentialCover = ToonImportUtils.findCoverInFiles(rootFiles)
            if (potentialCover != null) {
                coverPath = potentialCover.uri.toString()
            } else {
                val imageExts = ToonImportUtils.imageExtensions
                val coverFile = rootFiles.find { item -> 
                    val name = item.name?.lowercase() ?: ""
                    name.contains("cover") || imageExts.any { name.endsWith(it) } 
                }
                if (coverFile != null) {
                    coverPath = coverFile.uri.toString()
                }
            }
        }

        val mangaToInsert = MangaEntity(
            id = existingManga?.id ?: 0L,
            title = existingManga?.title ?: sourceDoc.name ?: "Unknown",
            description = existingManga?.description ?: "No description added yet.",
            thoughts = existingManga?.thoughts ?: "No thoughts added.",
            coverPath = coverPath,
            isNsfw = existingManga?.isNsfw ?: isNsfw,
            parentUri = uriStr,
            lastModified = sourceDoc.lastModified(),
            contentType = 0,
            boxPurpose = existingManga?.boxPurpose ?: boxPurpose,
            boxLabel = existingManga?.boxLabel,
            workspace = existingManga?.workspace ?: workspace
        )
        
        val mangaId = repository.insertManga(mangaToInsert)
        if (isCancelled()) return@withContext mangaId

        val subDirs = rootFiles.filter { it.isDirectory }
        val imageFiles = rootFiles.filter { file -> 
            !file.isDirectory && ToonImportUtils.imageExtensions.any { file.name?.lowercase()?.endsWith(it) == true } 
        }

        val chaptersToProcess = if (subDirs.isNotEmpty()) {
            subDirs
        } else if (imageFiles.isNotEmpty()) {
            listOf(sourceDoc)
        } else {
            emptyList()
        }.sortedWith(compareBy { item ->
            val name = item.name ?: ""
            name.replace(Regex("\\d+")) { match ->
                match.value.padStart(10, '0')
            }
        })

        val chapterEntities = mutableListOf<ChapterEntity>()
        chaptersToProcess.forEachIndexed { index, chapterItem ->
            val chapterUriStr = chapterItem.uri.toString()
            val existingChapter = repository.getChapterByUri(chapterUriStr)
            if (existingChapter == null) {
                chapterEntities.add(
                    ChapterEntity(
                        mangaId = mangaId,
                        title = chapterItem.name ?: "Unknown",
                        folderUri = chapterUriStr,
                        position = index
                    )
                )
            } else if (existingChapter.mangaId != mangaId || existingChapter.position != index) {
                chapterEntities.add(existingChapter.copy(mangaId = mangaId, position = index))
            }
        }
        
        repository.insertChapters(chapterEntities)
        onProgress(100)
        
        com.ballade.hwaran.data.local.HistoryTracker.logEvent("IMPORT", mangaToInsert.title, "Toon • $importMode | Source: ${uri.toString()}")
        
        return@withContext mangaId
    }
}
