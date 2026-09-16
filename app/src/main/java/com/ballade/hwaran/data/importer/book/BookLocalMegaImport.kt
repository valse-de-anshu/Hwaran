package com.ballade.hwaran.data.importer.book

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.backend.novel.NovelParser
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BookLocalMegaImport {

    suspend fun execute(
        context: Context,
        repository: BookImportRepository,
        parentUri: Uri,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): BookMegaImportSummary = withContext(Dispatchers.IO) {

        var totalCount = 0
        var importedCount = 0
        var skippedCount = 0
        val skippedFolders = mutableListOf<String>()

        val parentDoc = DocumentFile.fromTreeUri(context, parentUri) ?: return@withContext BookMegaImportSummary(0, 0, 0, emptyList(), false)
        
        val rootFiles = parentDoc.listFiles() ?: emptyArray()
        val directBooks = rootFiles.filter { !it.isDirectory && NovelParser.isBookFile(it.name) }
        
        val subDirs = rootFiles.filter { it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
        
        val webBookDirs = mutableListOf<DocumentFile>()
        val regularSubDirs = mutableListOf<DocumentFile>()

        subDirs.forEach { dir ->
            val childFiles = dir.listFiles() ?: emptyArray()
            val isWebBook = childFiles.any { !it.isDirectory && (it.name?.lowercase()?.let { n -> n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".xhtml") } == true) }
            if (isWebBook) {
                webBookDirs.add(dir)
            } else {
                regularSubDirs.add(dir)
            }
        }

        val allBooks = mutableListOf<DocumentFile>()
        allBooks.addAll(directBooks)
        regularSubDirs.forEach { folder ->
            val books = folder.listFiles()?.filter { !it.isDirectory && NovelParser.isBookFile(it.name) } ?: emptyList()
            allBooks.addAll(books)
        }
        
        totalCount = allBooks.size + webBookDirs.size
        if (totalCount == 0) {
            return@withContext BookMegaImportSummary(0, 0, 0, emptyList(), false)
        }

        var completed = 0

        // Import standalone book files
        allBooks.forEach { bookDoc ->
            if (isCancelled()) return@withContext BookMegaImportSummary(totalCount, importedCount, skippedCount, skippedFolders, true)
            
            val resultId = try {
                BookLocalSingleImport.executeSinglePdf(
                    context = context,
                    repository = repository,
                    pdfDoc = bookDoc,
                    workspace = workspace,
                    isNsfw = isNsfw,
                    boxPurpose = boxPurpose,
                    importMode = "Mega Import",
                    isCancelled = isCancelled
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }
            
            if (resultId != null) {
                importedCount++
            } else {
                skippedCount++
                skippedFolders.add("${bookDoc.name}: Failed or Duplicate")
            }
            
            completed++
            onProgress(completed.toFloat() / totalCount)
        }

        // Import web-book folders
        webBookDirs.forEach { webFolderDoc ->
            if (isCancelled()) return@withContext BookMegaImportSummary(totalCount, importedCount, skippedCount, skippedFolders, true)
            
            val resultId = try {
                BookLocalSingleImport.executeWebBookFolder(
                    context = context,
                    repository = repository,
                    folderDoc = webFolderDoc,
                    workspace = workspace,
                    isNsfw = isNsfw,
                    boxPurpose = boxPurpose,
                    importMode = "Mega Import",
                    isCancelled = isCancelled
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }

            if (resultId != null) {
                importedCount++
            } else {
                skippedCount++
                skippedFolders.add("${webFolderDoc.name}: Failed or Duplicate")
            }

            completed++
            onProgress(completed.toFloat() / totalCount)
        }

        return@withContext BookMegaImportSummary(
            total = totalCount,
            imported = importedCount,
            skipped = skippedCount,
            skippedFolders = skippedFolders,
            isCancelled = isCancelled()
        )
    }
}
