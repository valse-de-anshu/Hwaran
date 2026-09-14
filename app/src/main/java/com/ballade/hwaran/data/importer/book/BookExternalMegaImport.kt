package com.ballade.hwaran.data.importer.book

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BookExternalMegaImport {

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
        
        val candidates = mutableListOf<DocumentFile>()
        val rootFiles = parentDoc.listFiles() ?: emptyArray()
        val hasDirectPdf = rootFiles.any { !it.isDirectory && it.name?.lowercase()?.endsWith(".pdf") == true }
        
        if (hasDirectPdf) {
            candidates.add(parentDoc)
        }
        
        val subDirs = rootFiles.filter { it.isDirectory && !com.ballade.hwaran.core.metadata.ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
        candidates.addAll(subDirs)
        
        if (candidates.isEmpty()) {
            return@withContext BookMegaImportSummary(0, 0, 0, emptyList(), false)
        }

        val allPdfs = mutableListOf<DocumentFile>()
        candidates.forEach { folder ->
            val pdfs = folder.listFiles()?.filter { !it.isDirectory && it.name?.lowercase()?.endsWith(".pdf") == true } ?: emptyList()
            allPdfs.addAll(pdfs)
        }
        
        totalCount = allPdfs.size
        if (totalCount == 0) {
            return@withContext BookMegaImportSummary(0, 0, 0, emptyList(), false)
        }

        allPdfs.forEachIndexed { index, pdfDoc ->
            if (isCancelled()) return@withContext BookMegaImportSummary(totalCount, importedCount, skippedCount, skippedFolders, true)
            
            val resultId = BookExternalSingleImport.executeSinglePdf(
                context = context,
                repository = repository,
                pdfDoc = pdfDoc,
                workspace = workspace,
                isNsfw = isNsfw,
                boxPurpose = boxPurpose,
                importMode = "Mega Import",
                isCancelled = isCancelled
            )
            
            if (resultId != null) {
                importedCount++
            } else {
                skippedCount++
                skippedFolders.add("${pdfDoc.name}: Failed or Duplicate")
            }
            
            onProgress((index + 1).toFloat() / totalCount)
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
