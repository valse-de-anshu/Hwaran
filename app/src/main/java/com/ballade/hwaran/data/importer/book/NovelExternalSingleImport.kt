package com.ballade.hwaran.data.importer.book

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.metadata.MediaMetadataManager
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import com.ballade.hwaran.core.util.CoverCacheManager
import com.ballade.hwaran.core.util.HistoryTracker
import com.ballade.hwaran.core.util.StorageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object NovelExternalSingleImport {

    private val textExtensions = setOf("txt", "md", "markdown", "html", "htm", "xhtml", "epub")

    private fun naturalSortKey(name: String): String {
        return name.lowercase().replace(Regex("\\d+")) { match ->
            match.value.padStart(10, '0')
        }
    }

    /**
     * Imports an external Novel folder containing text/markdown chapter files
     * (either in a "novel chapter" / "chapters" subfolder or directly) as a single
     * Novel entry (contentType = 4, boxPurpose = "novel") with all chapters indexed.
     */
    suspend fun execute(
        context: Context,
        repository: BookImportRepository,
        uri: Uri,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String? = "novel",
        importMode: String = "Single Import",
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit = {}
    ): Long? = withContext(Dispatchers.IO) {
        val sourceDoc = StorageResolver.resolveDocument(context, uri, isFile = false) ?: return@withContext null
        if (isCancelled()) return@withContext null

        val uriStr = uri.toString()
        val directFile = StorageResolver.resolveToFile(context, uri)
        val existingManga = repository.getRootMangaByUri(uriStr)
            ?: directFile?.let { repository.getRootMangaByUri(it.absolutePath) }

        val folderName = if (sourceDoc.isFile) {
            sourceDoc.name?.substringBeforeLast(".") ?: "Novel"
        } else {
            sourceDoc.name ?: "Unknown"
        }

        // 1. Metadata resolution (.zine/ or entry.json or metadata.json)
        val parsedZine = if (directFile != null && directFile.isDirectory) {
            ZineMetadataExtractor.extractFromFolder(directFile)
        } else if (!sourceDoc.isFile) {
            ZineMetadataExtractor.extractFromDocumentFolder(context, sourceDoc)
        } else null

        val finalTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: existingManga?.title ?: folderName
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() }
            ?: existingManga?.description
            ?: "No description added yet."
        val finalAuthor = parsedZine?.author?.takeIf { it.isNotBlank() }
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: existingManga?.genre

        // 2. Cover resolution (folder, .zine, or parent folder)
        var coverPath = existingManga?.coverPath ?: ""
        val coverDoc = if (directFile != null && directFile.isDirectory) {
            val coverFile = ZineMetadataExtractor.findCoverInFolder(directFile, parsedZine?.coverFileName)
            coverFile?.let { DocumentFile.fromFile(it) }
        } else if (!sourceDoc.isFile) {
            ZineMetadataExtractor.findCoverInDocumentFolder(sourceDoc, parsedZine?.coverFileName)
        } else null

        if (coverDoc != null) {
            coverPath = CoverCacheManager.cacheCoverFromUri(context, coverDoc.uri, "novel", finalTitle)
                ?: coverDoc.uri.toString()
        }

        val mangaToInsert = MangaEntity(
            id = existingManga?.id ?: 0L,
            title = finalTitle,
            description = finalDescription,
            thoughts = existingManga?.thoughts ?: "",
            coverPath = coverPath,
            isNsfw = existingManga?.isNsfw ?: isNsfw,
            parentUri = uriStr,
            lastModified = sourceDoc.lastModified(),
            contentType = 4, // 4 = Novel
            boxPurpose = "novel",
            boxLabel = null,
            genre = finalTags,
            workspace = existingManga?.workspace ?: workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)
        if (isCancelled()) return@withContext mangaId

        // 3. Collect novel chapter files
        val chapterDocList = mutableListOf<DocumentFile>()

        if (directFile != null && directFile.isDirectory) {
            val files = directFile.listFiles() ?: emptyArray()
            val chapterSubdir = files.firstOrNull { f ->
                f.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(f.name) &&
                (f.name.equals("novel chapter", ignoreCase = true) ||
                 f.name.equals("novel_chapter", ignoreCase = true) ||
                 f.name.equals("chapters", ignoreCase = true) ||
                 f.name.equals("chapter", ignoreCase = true))
            }

            val targetDir = chapterSubdir ?: directFile
            val chapterFiles = (targetDir.listFiles() ?: emptyArray())
                .filter { f ->
                    f.isFile && !ZineMetadataExtractor.isInternalOrAuxiliary(f.name) &&
                    (f.name.lowercase().substringAfterLast(".", "") in textExtensions) &&
                    !f.name.equals("entry.json", ignoreCase = true) &&
                    !f.name.equals("metadata.json", ignoreCase = true) &&
                    !f.name.equals("history.json", ignoreCase = true)
                }
                .sortedWith(compareBy { naturalSortKey(it.name) })

            chapterDocList.addAll(chapterFiles.map { DocumentFile.fromFile(it) })
        } else if (!sourceDoc.isFile) {
            val children = sourceDoc.listFiles()
            val chapterSubdir = children.firstOrNull { f ->
                f.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(f.name) &&
                (f.name?.equals("novel chapter", ignoreCase = true) == true ||
                 f.name?.equals("novel_chapter", ignoreCase = true) == true ||
                 f.name?.equals("chapters", ignoreCase = true) == true ||
                 f.name?.equals("chapter", ignoreCase = true) == true)
            }

            val targetDoc = chapterSubdir ?: sourceDoc
            val chapterFiles = targetDoc.listFiles()
                .filter { f ->
                    !f.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(f.name) &&
                    (f.name?.lowercase()?.substringAfterLast(".", "") in textExtensions) &&
                    f.name?.equals("entry.json", ignoreCase = true) != true &&
                    f.name?.equals("metadata.json", ignoreCase = true) != true &&
                    f.name?.equals("history.json", ignoreCase = true) != true
                }
                .sortedWith(compareBy { naturalSortKey(it.name ?: "") })

            chapterDocList.addAll(chapterFiles)
        }

        val totalChapters = chapterDocList.size
        val chapterEntities = chapterDocList.mapIndexed { index, doc ->
            if (totalChapters > 0) {
                onProgress(((index + 1).toFloat() / totalChapters * 100).toInt().coerceIn(0, 100))
            }
            val rawName = doc.name ?: "Chapter ${index + 1}"
            val cleanTitle = rawName.substringBeforeLast(".")
                .replace("_", " ")
                .trim()
                .ifBlank { "Chapter ${index + 1}" }

            val uriString = doc.uri.toString()
            val existingChapter = repository.getChapterByUri(uriString)
            if (existingChapter == null) {
                ChapterEntity(
                    mangaId = mangaId,
                    title = cleanTitle,
                    folderUri = uriString,
                    position = index
                )
            } else {
                existingChapter.copy(mangaId = mangaId, title = cleanTitle, position = index)
            }
        }

        if (chapterEntities.isNotEmpty()) {
            repository.insertChapters(chapterEntities)
        }

        if (parsedZine != null || finalAuthor != null) {
            val entryMeta = parsedZine?.toEntryMetadata() ?: com.ballade.hwaran.core.metadata.EntryMetadata(
                title = finalTitle,
                author = finalAuthor ?: "",
                description = finalDescription,
                tags = parsedZine?.tags ?: emptyList()
            )
            MediaMetadataManager.saveMetadata(
                context = context,
                mangaId = mangaId,
                parentUri = uriStr,
                metadata = entryMeta,
                forceWriteToFile = false
            )
        }

        HistoryTracker.logEvent(
            "IMPORT",
            finalTitle,
            "Novel • $importMode | Chapters: ${chapterEntities.size} | Source: $uriStr"
        )

        mangaId
    }
}
