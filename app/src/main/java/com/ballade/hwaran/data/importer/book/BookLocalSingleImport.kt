package com.ballade.hwaran.data.importer.book

import android.content.Context
import android.graphics.Bitmap
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

object BookLocalSingleImport {

    /**
     * Imports a single Book file (.pdf, .epub, .kf8.images, .kindle.images, .mobi, .azw3, .fb2, etc.)
     * as an independent Book entry in the vault.
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

        val vaultBase = File(context.filesDir, "book_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        try {
            File(vaultBase, ".nomedia").createNewFile()
        } catch (_: Exception) {}

        val fileName = pdfDoc.name ?: "Unknown.pdf"
        val fallbackTitle = fileName.substringBeforeLast(".")

        val destFile = File(vaultBase, fileName)

        // Duplicate check by vault path
        val existingManga = repository.getRootMangaByUri(destFile.absolutePath)
        if (existingManga != null) return@withContext existingManga.id

        if (isCancelled()) return@withContext null

        return@withContext try {
            // Copy Book file to vault
            try {
                context.contentResolver.openInputStream(pdfDoc.uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }

            if (isCancelled()) {
                destFile.delete()
                return@withContext null
            }

            // 1. Parse individual book metadata (EPUB / MOBI / KF8 / HTML contain embedded title, author, cover)
            var novelBook: NovelBook? = null
            val isPdf = fileName.lowercase().endsWith(".pdf")
            if (!isPdf) {
                try {
                    novelBook = NovelParser.parseNovelFromFile(destFile)
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
                ?: "Book"
            val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

            // 2. Cover image resolution
            var coverPath = ""

            // Try embedded cover first (EPUB / MOBI)
            if (novelBook?.coverBitmap != null) {
                val thumbFile = File(vaultBase, "${destFile.name}_thumb.jpg")
                try {
                    thumbFile.outputStream().use { out ->
                        novelBook.coverBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
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
                    val coverName = "${destFile.name}_cover.jpg"
                    val destCoverFile = File(vaultBase, coverName)
                    try {
                        context.contentResolver.openInputStream(coverDoc.uri)?.use { input ->
                            destCoverFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        coverPath = destCoverFile.absolutePath
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // PDF thumbnail generation
            if (coverPath.isEmpty() && isPdf) {
                val thumbFile = File(vaultBase, "${destFile.name}_thumb.jpg")
                coverPath = BookImportUtils.generatePdfThumbnail(context, Uri.fromFile(destFile), thumbFile) ?: ""
            }

            val mangaToInsert = MangaEntity(
                id = 0L,
                title = finalTitle,
                description = finalDescription,
                thoughts = "",
                coverPath = coverPath,
                isNsfw = isNsfw,
                parentUri = destFile.absolutePath,
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
                folderUri = destFile.absolutePath,
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
                    parentUri = destFile.absolutePath,
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
     * Imports an HTML Web-Book folder (like Gutenberg pg*-h folders with images/ and html)
     * as an independent single-title Book entry in the vault.
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
        val vaultBase = File(context.filesDir, "book_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        try {
            File(vaultBase, ".nomedia").createNewFile()
        } catch (_: Exception) {}

        val folderName = folderDoc.name ?: "web_book"
        val destDir = File(vaultBase, folderName)
        if (!destDir.exists()) destDir.mkdirs()

        return@withContext try {
            copyDocumentFolderToLocal(context, folderDoc, destDir, isCancelled)
            if (isCancelled()) {
                destDir.deleteRecursively()
                return@withContext null
            }

            val mainHtmlFile = findMainHtmlFile(destDir) ?: destDir
            val existingManga = repository.getRootMangaByUri(mainHtmlFile.absolutePath)
            if (existingManga != null) return@withContext existingManga.id

            val novelBook = try {
                if (mainHtmlFile.isFile) NovelParser.parseNovelFromFile(mainHtmlFile) else null
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
                ?: "Web Book"
            val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

            var coverPath = ""
            if (novelBook?.coverBitmap != null) {
                val thumbFile = File(destDir, "cover_thumb.jpg")
                try {
                    thumbFile.outputStream().use { out ->
                        novelBook.coverBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    coverPath = thumbFile.absolutePath
                } catch (_: Exception) {}
            }

            if (coverPath.isEmpty()) {
                val coverFile = NovelParser.findCoverInDirectory(destDir)
                if (coverFile != null) {
                    coverPath = coverFile.absolutePath
                }
            }

            if (coverPath.isEmpty() && coverDocOverride != null) {
                val destCover = File(destDir, coverDocOverride.name ?: "cover.jpg")
                try {
                    context.contentResolver.openInputStream(coverDocOverride.uri)?.use { input ->
                        destCover.outputStream().use { output -> input.copyTo(output) }
                    }
                    coverPath = destCover.absolutePath
                } catch (_: Exception) {}
            }

            val mangaToInsert = MangaEntity(
                id = 0L,
                title = finalTitle,
                description = finalDescription,
                thoughts = "",
                coverPath = coverPath,
                isNsfw = isNsfw,
                parentUri = mainHtmlFile.absolutePath,
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
                folderUri = mainHtmlFile.absolutePath,
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
                    parentUri = mainHtmlFile.absolutePath,
                    metadata = entryMeta,
                    forceWriteToFile = false
                )
            }

            com.ballade.hwaran.core.util.HistoryTracker.logEvent(
                "IMPORT",
                finalTitle,
                "Book • $importMode | Source: ${folderDoc.uri}"
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

    private fun copyDocumentFolderToLocal(
        context: Context,
        srcDoc: DocumentFile,
        destDir: File,
        isCancelled: () -> Boolean
    ) {
        if (!destDir.exists()) destDir.mkdirs()
        srcDoc.listFiles().forEach { child ->
            if (isCancelled()) return
            if (child.isDirectory) {
                val childDest = File(destDir, child.name ?: "sub")
                copyDocumentFolderToLocal(context, child, childDest, isCancelled)
            } else {
                val childFile = File(destDir, child.name ?: "file")
                try {
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        childFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun findMainHtmlFile(dir: File): File? {
        val candidates = listOf("index.html", "index.htm", "index.xhtml", "titlepage.html")
        dir.listFiles()?.firstOrNull { f -> f.isFile && candidates.any { c -> f.name.equals(c, ignoreCase = true) } }?.let { return it }
        dir.listFiles()?.firstOrNull { f -> f.isFile && f.name.contains("images", ignoreCase = true) && f.name.endsWith(".html", ignoreCase = true) }?.let { return it }
        return dir.listFiles()?.firstOrNull { f -> f.isFile && (f.name.endsWith(".html", ignoreCase = true) || f.name.endsWith(".htm", ignoreCase = true) || f.name.endsWith(".xhtml", ignoreCase = true)) }
    }
}
