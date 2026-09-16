package com.ballade.hwaran.data.importer.book

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.backend.novel.NovelBook
import com.ballade.hwaran.backend.novel.NovelParser
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.metadata.MediaMetadataManager
import com.ballade.hwaran.core.metadata.ParsedZineMetadata
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BookExternalSingleImport {

    /**
     * Imports a single Book file (external URI) as an independent Book entry.
     */
    suspend fun executeSinglePdf(
        context: Context,
        repository: BookImportRepository,
        pdfDoc: DocumentFile,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        importMode: String,
        isCancelled: () -> Boolean,
        metadataOverride: ParsedZineMetadata? = null,
        coverDocOverride: DocumentFile? = null
    ): Long? = withContext(Dispatchers.IO) {

        val uriStr = pdfDoc.uri.toString()

        // Duplicate check by URI
        val existingManga = repository.getRootMangaByUri(uriStr)
        if (existingManga != null) return@withContext existingManga.id

        if (isCancelled()) return@withContext null

        val fileName = pdfDoc.name ?: "Unknown.pdf"
        val fallbackTitle = fileName.substringBeforeLast(".")

        return@withContext try {
            // 1. Resolve individual metadata
            var novelBook: NovelBook? = null
            val isPdf = fileName.lowercase().endsWith(".pdf")
            if (!isPdf) {
                try {
                    novelBook = NovelParser.parseNovel(context, pdfDoc.uri, fileName)
                } catch (_: Exception) {}
            }

            val parsedZine = metadataOverride ?: run {
                try {
                    pdfDoc.parentFile?.let { ZineMetadataExtractor.extractFromDocumentFolder(context, it) }
                } catch (_: Exception) { null }
            }

            val finalTitle = fallbackTitle.ifBlank { fileName }
            val finalAuthor = novelBook?.author ?: parsedZine?.author
            val finalDescription = novelBook?.description?.takeIf { it.isNotBlank() }
                ?: parsedZine?.description?.takeIf { it.isNotBlank() }
                ?: "External Book"
            val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

            // 2. Cover image resolution
            var coverPath = ""

            // Try embedded cover first (EPUB / MOBI)
            if (novelBook?.coverBitmap != null) {
                val vaultBase = File(context.filesDir, "book_vault")
                if (!vaultBase.exists()) vaultBase.mkdirs()
                val thumbFile = File(vaultBase, "${fileName}_thumb.jpg")
                try {
                    thumbFile.outputStream().use { out ->
                        novelBook.coverBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    coverPath = thumbFile.absolutePath
                } catch (_: Exception) {}
            }

            // Dedicated cover from folder if available and no embedded cover
            if (coverPath.isEmpty()) {
                val coverDoc = coverDocOverride ?: run {
                    try {
                        pdfDoc.parentFile?.let { ZineMetadataExtractor.findCoverInDocumentFolder(it, parsedZine?.coverFileName) }
                    } catch (_: Exception) { null }
                }

                if (coverDoc != null) {
                    coverPath = com.ballade.hwaran.core.util.CoverCacheManager.cacheCoverFromUri(context, coverDoc.uri, "book", finalTitle)
                        ?: coverDoc.uri.toString()
                }
            }

            // PDF thumbnail generation
            if (coverPath.isEmpty() && isPdf) {
                val vaultBase = File(context.filesDir, "book_vault")
                if (!vaultBase.exists()) vaultBase.mkdirs()
                val thumbFile = File(vaultBase, "${fileName}_external_thumb.jpg")
                coverPath = BookImportUtils.generatePdfThumbnail(context, pdfDoc.uri, thumbFile) ?: ""
            }

            val mangaToInsert = MangaEntity(
                id = 0L,
                title = finalTitle,
                description = finalDescription,
                thoughts = "",
                coverPath = coverPath,
                isNsfw = isNsfw,
                parentUri = uriStr,
                lastModified = System.currentTimeMillis(),
                contentType = 1, // 1 = Book
                boxPurpose = boxPurpose ?: parsedZine?.type ?: "book",
                genre = finalTags,
                workspace = workspace
            )

            val mangaId = repository.insertManga(mangaToInsert)

            // One Book File = One Chapter
            val chapter = ChapterEntity(
                mangaId = mangaId,
                title = finalTitle,
                folderUri = uriStr,
                position = 0
            )
            repository.insertChapters(listOf(chapter))

            // Cache metadata if available
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

            com.ballade.hwaran.core.util.HistoryTracker.logEvent(
                "IMPORT",
                finalTitle,
                "Book • $importMode | Source: ${pdfDoc.uri}"
            )

            mangaId
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Imports an external HTML Web-Book folder (contains .html/.xhtml and images)
     * as an independent single-title Book entry.
     */
    suspend fun executeWebBookFolder(
        context: Context,
        repository: BookImportRepository,
        folderDoc: DocumentFile,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        importMode: String,
        isCancelled: () -> Boolean,
        metadataOverride: ParsedZineMetadata? = null,
        coverDocOverride: DocumentFile? = null
    ): Long? = withContext(Dispatchers.IO) {
        return@withContext try {
            val folderFiles = folderDoc.listFiles()
            val mainHtmlDoc = folderFiles.firstOrNull { f ->
                !f.isDirectory && f.name?.lowercase()?.let { it.endsWith(".html") || it.endsWith(".htm") || it.endsWith(".xhtml") } == true
            } ?: folderDoc

            val uriStr = mainHtmlDoc.uri.toString()
            val existingManga = repository.getRootMangaByUri(uriStr)
            if (existingManga != null) return@withContext existingManga.id

            val folderName = folderDoc.name ?: "web_book"
            val novelBook = try {
                if (mainHtmlDoc != folderDoc) NovelParser.parseNovel(context, mainHtmlDoc.uri, mainHtmlDoc.name) else null
            } catch (_: Exception) { null }

            val parsedZine = metadataOverride ?: run {
                try {
                    folderDoc.parentFile?.let { ZineMetadataExtractor.extractFromDocumentFolder(context, it) }
                } catch (_: Exception) { null }
            }

            val finalTitle = folderName
            val finalAuthor = novelBook?.author ?: parsedZine?.author
            val finalDescription = novelBook?.description?.takeIf { it.isNotBlank() }
                ?: parsedZine?.description?.takeIf { it.isNotBlank() }
                ?: "External Web Book"
            val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

            var coverPath = ""
            if (novelBook?.coverBitmap != null) {
                val vaultBase = File(context.filesDir, "book_vault")
                if (!vaultBase.exists()) vaultBase.mkdirs()
                val thumbFile = File(vaultBase, "${folderName}_thumb.jpg")
                try {
                    thumbFile.outputStream().use { out ->
                        novelBook.coverBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    coverPath = thumbFile.absolutePath
                } catch (_: Exception) {}
            }

            if (coverPath.isEmpty()) {
                val subCover = coverDocOverride ?: run {
                    folderFiles.firstOrNull { f ->
                        !f.isDirectory && listOf("cover", "poster", "artwork", "thumb", "illus-fpc").any { p ->
                            f.name?.substringBeforeLast(".")?.equals(p, ignoreCase = true) == true
                        }
                    }
                }

                if (subCover != null) {
                    coverPath = com.ballade.hwaran.core.util.CoverCacheManager.cacheCoverFromUri(context, subCover.uri, "book", finalTitle)
                        ?: subCover.uri.toString()
                }
            }

            val mangaToInsert = MangaEntity(
                id = 0L,
                title = finalTitle,
                description = finalDescription,
                thoughts = "",
                coverPath = coverPath,
                isNsfw = isNsfw,
                parentUri = uriStr,
                lastModified = System.currentTimeMillis(),
                contentType = 1, // 1 = Book
                boxPurpose = boxPurpose ?: parsedZine?.type ?: "book",
                genre = finalTags,
                workspace = workspace
            )

            val mangaId = repository.insertManga(mangaToInsert)
            val chapter = ChapterEntity(
                mangaId = mangaId,
                title = finalTitle,
                folderUri = uriStr,
                position = 0
            )
            repository.insertChapters(listOf(chapter))

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

            com.ballade.hwaran.core.util.HistoryTracker.logEvent(
                "IMPORT",
                finalTitle,
                "Book • $importMode | Source: $uriStr"
            )

            mangaId
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Folder entry point - imports each distinct Book file and Web-Book subfolder
     * as an independent single-title Book entry in the library.
     */
    suspend fun execute(
        context: Context,
        repository: BookImportRepository,
        uri: Uri,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        importMode: String = "Single Import",
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit
    ): Long? = withContext(Dispatchers.IO) {
        val sourceDoc = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null
        val files = sourceDoc.listFiles()

        // 1. Standalone book files (.pdf, .epub, .kf8.images, .kindle.images, .mobi, .txt, etc.)
        val directBooks = files.filter { !it.isDirectory && NovelParser.isBookFile(it.name) }

        // 2. Web-Book subfolders (contain .html / .xhtml files)
        val webBookSubDirs = files.filter { child ->
            child.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(child.name) &&
            child.listFiles().any { f -> !f.isDirectory && (f.name?.lowercase()?.let { it.endsWith(".html") || it.endsWith(".htm") || it.endsWith(".xhtml") } == true) }
        }

        // 3. Other subdirectories containing book files
        val nestedBookSubDirs = files.filter { child ->
            child.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(child.name) &&
            !webBookSubDirs.contains(child) &&
            child.listFiles().any { f -> !f.isDirectory && NovelParser.isBookFile(f.name) }
        }

        val totalUnits = directBooks.size + webBookSubDirs.size + nestedBookSubDirs.size
        if (totalUnits == 0) return@withContext null

        val parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, sourceDoc)
        val coverDoc = ZineMetadataExtractor.findCoverInDocumentFolder(sourceDoc, parsedZine?.coverFileName)

        var lastId: Long? = null
        var completed = 0

        // Import direct book files
        directBooks.forEach { bookDoc ->
            if (isCancelled()) return@withContext lastId
            try {
                val res = executeSinglePdf(
                    context = context,
                    repository = repository,
                    pdfDoc = bookDoc,
                    workspace = workspace,
                    isNsfw = isNsfw,
                    boxPurpose = boxPurpose,
                    importMode = importMode,
                    isCancelled = isCancelled,
                    metadataOverride = parsedZine,
                    coverDocOverride = coverDoc
                )
                if (res != null) lastId = res
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            completed++
            onProgress((completed.toFloat() / totalUnits * 100).toInt())
        }

        // Import web-book subdirectories
        webBookSubDirs.forEach { webFolderDoc ->
            if (isCancelled()) return@withContext lastId
            try {
                val res = executeWebBookFolder(
                    context = context,
                    repository = repository,
                    folderDoc = webFolderDoc,
                    workspace = workspace,
                    isNsfw = isNsfw,
                    boxPurpose = boxPurpose,
                    importMode = importMode,
                    isCancelled = isCancelled,
                    metadataOverride = parsedZine,
                    coverDocOverride = coverDoc
                )
                if (res != null) lastId = res
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            completed++
            onProgress((completed.toFloat() / totalUnits * 100).toInt())
        }

        // Import nested book subdirectories
        nestedBookSubDirs.forEach { nestedDirDoc ->
            if (isCancelled()) return@withContext lastId
            val nestedBooks = nestedDirDoc.listFiles().filter { !it.isDirectory && NovelParser.isBookFile(it.name) }
            val nestedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, nestedDirDoc) ?: parsedZine
            val nestedCover = ZineMetadataExtractor.findCoverInDocumentFolder(nestedDirDoc, nestedZine?.coverFileName) ?: coverDoc
            nestedBooks.forEach { nestedBookDoc ->
                if (isCancelled()) return@withContext lastId
                try {
                    val res = executeSinglePdf(
                        context = context,
                        repository = repository,
                        pdfDoc = nestedBookDoc,
                        workspace = workspace,
                        isNsfw = isNsfw,
                        boxPurpose = boxPurpose,
                        importMode = importMode,
                        isCancelled = isCancelled,
                        metadataOverride = nestedZine,
                        coverDocOverride = nestedCover
                    )
                    if (res != null) lastId = res
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            completed++
            onProgress((completed.toFloat() / totalUnits * 100).toInt())
        }

        onProgress(100)
        return@withContext lastId
    }
}
