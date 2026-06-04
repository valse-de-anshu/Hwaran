package com.ballade.hwaran.data.video

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoLocalMegaImport {

    suspend fun execute(
        context: Context,
        repository: VideoImportRepository,
        parentUri: Uri,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): VideoMegaImportSummary = withContext(Dispatchers.IO) {

        var totalCount = 0
        var importedCount = 0
        var skippedCount = 0
        val skippedFolders = mutableListOf<String>()
        var cancelTriggered = false

        val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
        val allChildren = parentDoc?.listFiles() ?: emptyArray()

        // Immediate subfolders only, alphabetical, no hidden
        val candidates = allChildren.filter {
            it.isDirectory && !(it.name?.startsWith(".") == true)
        }.sortedBy { it.name?.lowercase() ?: "" }

        totalCount = candidates.size

        if (totalCount == 0) {
            return@withContext VideoMegaImportSummary(0, 0, 0, emptyList(), false)
        }

        val vaultBase = File(context.filesDir, "video_vault")

        for (index in candidates.indices) {
            if (isCancelled()) {
                cancelTriggered = true
                break
            }

            val child = candidates[index]
            val folderName = child.name ?: "Unknown Folder"

            // Duplicate check — before any import attempt
            val expectedVaultPath = File(vaultBase, folderName).absolutePath
            val existing = repository.getRootMangaByUri(expectedVaultPath)
            if (existing != null) {
                skippedCount++
                skippedFolders.add("$folderName: Already imported")
                onProgress((index + 1).toFloat() / totalCount)
                continue
            }

            // Validate folder contains at least one video
            val (isValid, reason) = VideoImportUtils.isVideoFolderValid(child)
            if (!isValid) {
                skippedCount++
                skippedFolders.add("$folderName: ${reason ?: "Invalid structure"}")
                onProgress((index + 1).toFloat() / totalCount)
                continue
            }

            try {
                val importedId = VideoLocalSingleImport.execute(
                    context = context,
                    repository = repository,
                    uri = child.uri,
                    workspace = workspace,
                    isNsfw = isNsfw,
                    boxPurpose = boxPurpose,
                    importMode = "Mega Import",
                    isCancelled = isCancelled,
                    onProgress = { /* individual file progress ignored in mega mode */ }
                )

                if (importedId != null) {
                    importedCount++
                } else {
                    if (isCancelled()) {
                        cancelTriggered = true
                        break
                    } else {
                        skippedCount++
                        skippedFolders.add("$folderName: Import error")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                skippedCount++
                skippedFolders.add("$folderName: Import error: ${e.message ?: "Unknown"}")
            }

            onProgress((index + 1).toFloat() / totalCount)
        }

        return@withContext VideoMegaImportSummary(
            total = totalCount,
            imported = importedCount,
            skipped = skippedCount,
            skippedFolders = skippedFolders,
            isCancelled = cancelTriggered || isCancelled()
        )
    }
}
