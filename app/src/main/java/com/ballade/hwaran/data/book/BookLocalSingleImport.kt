package com.ballade.hwaran.data.book

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.data.local.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BookLocalSingleImport {

    /**
     * Imports a single PDF file as an independent Book entry.
     */
    suspend fun executeSinglePdf(
        context: Context,
        repository: BookImportRepository,
        pdfDoc: DocumentFile,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        importMode: String,
        isCancelled: () -> Boolean
    ): Long? = withContext(Dispatchers.IO) {

        val vaultBase = File(context.filesDir, "book_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        File(vaultBase, ".nomedia").createNewFile()

        val fileName = pdfDoc.name ?: "Unknown.pdf"
        val title = fileName.substringBeforeLast(".")
        val destFile = File(vaultBase, fileName)

        // Duplicate check by vault path
        val existingManga = repository.getRootMangaByUri(destFile.absolutePath)
        if (existingManga != null) return@withContext existingManga.id

        if (isCancelled()) return@withContext null

        // Copy PDF to vault
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

        // Generate Thumbnail
        val thumbFile = File(vaultBase, "${title}_thumb.jpg")
        val coverPath = BookImportUtils.generatePdfThumbnail(context, Uri.fromFile(destFile), thumbFile) ?: ""

        val mangaToInsert = MangaEntity(
            id = 0L,
            title = title,
            description = "Book",
            thoughts = "",
            coverPath = coverPath,
            isNsfw = isNsfw,
            parentUri = destFile.absolutePath,
            lastModified = System.currentTimeMillis(),
            contentType = 1, // 1 = Book
            boxPurpose = boxPurpose,
            workspace = workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)

        // One PDF = One Chapter (The PDF itself)
        val chapter = ChapterEntity(
            mangaId = mangaId,
            title = title,
            folderUri = destFile.absolutePath,
            position = 0
        )
        repository.insertChapters(listOf(chapter))

        com.ballade.hwaran.data.local.HistoryTracker.logEvent(
            "IMPORT",
            title,
            "Book • $importMode | Source: ${pdfDoc.uri}"
        )

        return@withContext mangaId
    }

    /**
     * Legacy entry point - handles importing PDFs from a folder.
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
        val files = sourceDoc.listFiles() ?: emptyArray()
        val pdfs = files.filter { !it.isDirectory && it.name?.lowercase()?.endsWith(".pdf") == true }
        
        var lastId: Long? = null
        pdfs.forEachIndexed { index, pdfDoc ->
            if (isCancelled()) return@withContext lastId
            lastId = executeSinglePdf(context, repository, pdfDoc, workspace, isNsfw, boxPurpose, importMode, isCancelled)
            onProgress(((index + 1).toFloat() / pdfs.size * 100).toInt())
        }
        
        onProgress(100)
        return@withContext lastId
    }
}
