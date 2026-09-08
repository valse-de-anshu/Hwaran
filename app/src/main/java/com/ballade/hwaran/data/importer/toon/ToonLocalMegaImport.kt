package com.ballade.hwaran.data.importer.toon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ToonLocalMegaImport {

    suspend fun execute(
        context: Context,
        repository: ToonImportRepository,
        parentUri: Uri,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit // Overall progress from 0f to 1f
    ): ToonMegaImportSummary = withContext(Dispatchers.IO) {

        var totalCount = 0
        var importedCount = 0
        var skippedCount = 0
        val skippedFolders = mutableListOf<String>()
        var cancelTriggered = false

        val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
        val allChildren = parentDoc?.listFiles() ?: emptyArray()
        val candidates = allChildren.filter {
            it.isDirectory && !(it.name?.startsWith(".") == true)
        }.sortedBy { it.name?.lowercase() ?: "" }
        
        totalCount = candidates.size

        if (totalCount == 0) {
            return@withContext ToonMegaImportSummary(0, 0, 0, emptyList(), false)
        }

        for (index in candidates.indices) {
            if (isCancelled()) {
                cancelTriggered = true
                break
            }

            val child = candidates[index]
            val folderName = child.name ?: "Unknown Folder"

            val vaultBase = java.io.File(context.filesDir, "manga_vault")
            val expectedPath = java.io.File(vaultBase, folderName).absolutePath

            val existing = repository.getRootMangaByUri(expectedPath)
            if (existing != null) {
                skippedCount++
                skippedFolders.add("$folderName: Already imported")
                onProgress((index + 1).toFloat() / totalCount)
                continue
            }

            val (isValid, reason) = ToonImportUtils.isToonFolderValid(child)
            if (isValid) {
                try {
                    val importedId = ToonLocalSingleImport.execute(
                        context = context,
                        repository = repository,
                        uri = child.uri,
                        workspace = workspace,
                        isNsfw = isNsfw,
                        boxPurpose = boxPurpose,
                        importMode = "Mega Import",
                        isCancelled = isCancelled,
                        onProgress = { /* Ignoring individual file progress for mega import */ }
                    )
                    
                    if (importedId != null) {
                        importedCount++
                    } else {
                        // If it returned null but wasn't cancelled, it failed
                        if (isCancelled()) {
                            cancelTriggered = true
                            break
                        } else {
                            skippedCount++
                            skippedFolders.add("$folderName: Import failed")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    skippedCount++
                    skippedFolders.add("$folderName: ${e.message ?: "Unknown error"}")
                }
            } else {
                skippedCount++
                skippedFolders.add("$folderName: ${reason ?: "Invalid structure"}")
            }

            onProgress((index + 1).toFloat() / totalCount)
        }

        return@withContext ToonMegaImportSummary(
            total = totalCount,
            imported = importedCount,
            skipped = skippedCount,
            skippedFolders = skippedFolders,
            isCancelled = cancelTriggered || isCancelled()
        )
    }
}
