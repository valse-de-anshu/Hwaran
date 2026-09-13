package com.ballade.hwaran.data.importer.music

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicExternalMegaImport {

    suspend fun execute(
        context: Context,
        repository: MusicImportRepository,
        parentDoc: DocumentFile,
        workspace: String?,
        isCancelled: () -> Boolean,
        onProgress: ((Float) -> Unit)? = null
    ): MusicImportSummary = withContext(Dispatchers.IO) {

        val files = parentDoc.listFiles() ?: emptyArray()
        val childFolders = files.filter { 
            it.isDirectory && 
            !it.name.orEmpty().startsWith(".") &&
            !MusicImportUtils.isAuxiliaryFolder(it.name.orEmpty())
        }.sortedBy { it.name?.lowercase() ?: "" }

        var importedCount = 0
        var skippedCount = 0
        val reasons = mutableListOf<String>()

        // Check if parentDoc itself directly contains audio files
        val parentAudioFiles = MusicImportUtils.findAudioFiles(parentDoc)
        if (parentAudioFiles.isNotEmpty()) {
            try {
                val parentAlbumId = MusicExternalSingleImport.execute(
                    context = context,
                    repository = repository,
                    folderDoc = parentDoc,
                    workspace = workspace,
                    isCancelled = isCancelled,
                    onProgress = null
                )
                if (parentAlbumId != null) {
                    importedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (childFolders.isEmpty() && parentAudioFiles.isEmpty()) {
            return@withContext MusicImportSummary(0, 0, listOf("No audio files found in Mega Import"))
        }

        childFolders.forEachIndexed { index, folder ->
            if (isCancelled()) {
                reasons.add("Import cancelled by user")
                return@withContext MusicImportSummary(importedCount, skippedCount, reasons)
            }

            try {
                // Pre-check if it has audio files before importing
                val audioFiles = MusicImportUtils.findAudioFiles(folder)
                if (audioFiles.isEmpty()) {
                    skippedCount++
                    reasons.add("${folder.name ?: "Unknown"}: No audio files found")
                } else {
                    val albumId = MusicExternalSingleImport.execute(
                        context = context,
                        repository = repository,
                        folderDoc = folder,
                        workspace = workspace,
                        isCancelled = isCancelled,
                        onProgress = null // Don't show inner progress for mega import to avoid jumping progress bar
                    )

                    if (albumId != null) {
                        importedCount++
                    } else {
                        // Could be duplicate or cancelled
                        skippedCount++
                        reasons.add("${folder.name ?: "Unknown"}: Already imported or failed")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                skippedCount++
                reasons.add("${folder.name ?: "Unknown"}: Error - ${e.message}")
            }

            onProgress?.invoke((index + 1).toFloat() / childFolders.size)
        }

        // We also want to log mega import history
        repository.logHistory(parentDoc.name ?: "Unknown Mega Folder", "Music • Mega Import")

        return@withContext MusicImportSummary(importedCount, skippedCount, reasons)
    }
}
