package com.ballade.hwaran.data.importer.book

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
     * Imports a single PDF file (external URI) as an independent Book entry.
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

        // 1. Resolve metadata (override or from parent)
        val parsedZine = metadataOverride ?: run {
            try {
                pdfDoc.parentFile?.let { ZineMetadataExtractor.extractFromDocumentFolder(context, it) }
            } catch (_: Exception) { null }
        }

        // Rule 4: Metadata overrides filesystem hints
        val finalTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() } ?: "External Book"
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ")

        // 2. Cover image resolution (Rule 5: check dedicated cover first, then thumbnail)
        val coverDoc = coverDocOverride ?: run {
            try {
                pdfDoc.parentFile?.let { ZineMetadataExtractor.findCoverInDocumentFolder(it, parsedZine?.coverFileName) }
            } catch (_: Exception) { null }
        }

        var coverPath = ""
        if (coverDoc != null) {
            coverPath = com.ballade.hwaran.core.util.CoverCacheManager.cacheCoverFromUri(context, coverDoc.uri, "book", finalTitle)
                ?: coverDoc.uri.toString()
        }

        if (coverPath.isEmpty()) {
            val vaultBase = File(context.filesDir, "book_vault")
            if (!vaultBase.exists()) vaultBase.mkdirs()
            val thumbFile = File(vaultBase, "${fallbackTitle}_external_thumb.jpg")
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

        // One PDF = One Chapter
        val chapter = ChapterEntity(
            mangaId = mangaId,
            title = finalTitle,
            folderUri = uriStr,
            position = 0
        )
        repository.insertChapters(listOf(chapter))

        // Cache full metadata
        if (parsedZine != null) {
            MediaMetadataManager.saveMetadata(
                context = context,
                mangaId = mangaId,
                parentUri = uriStr,
                metadata = parsedZine.toEntryMetadata()
            )
        }

        com.ballade.hwaran.core.util.HistoryTracker.logEvent(
            "IMPORT",
            finalTitle,
            "Book • $importMode | Source: $uriStr"
        )

        return@withContext mangaId
    }

    /**
     * Folder entry point - handles importing PDFs from a folder.
     * Applies .zine JSON or root JSON metadata to the imported Book.
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
        val pdfs = files.filter { !it.isDirectory && it.name?.lowercase()?.endsWith(".pdf") == true }

        if (pdfs.isEmpty()) return@withContext null

        // 1. Extract metadata from .zine/*.json or root *.json
        val parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, sourceDoc)
        val coverDoc = ZineMetadataExtractor.findCoverInDocumentFolder(sourceDoc, parsedZine?.coverFileName)

        var lastId: Long? = null
        pdfs.forEachIndexed { index, pdfDoc ->
            if (isCancelled()) return@withContext lastId
            lastId = executeSinglePdf(
                context = context,
                repository = repository,
                pdfDoc = pdfDoc,
                workspace = workspace,
                isNsfw = isNsfw,
                boxPurpose = boxPurpose,
                importMode = importMode,
                isCancelled = isCancelled,
                metadataOverride = parsedZine,
                coverDocOverride = coverDoc
            )
            onProgress(((index + 1).toFloat() / pdfs.size * 100).toInt())
        }

        onProgress(100)
        return@withContext lastId
    }
}
