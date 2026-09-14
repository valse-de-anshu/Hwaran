package com.ballade.hwaran.data.importer.toon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import java.io.File

object ToonImportUtils {

    val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif")
    val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".webm", ".m4v", ".3gp", ".mov", ".flv")
    val audioExtensions = listOf(".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a")

    fun isToonFolderValid(folderDoc: DocumentFile): Pair<Boolean, String?> {
        val files = folderDoc.listFiles() ?: return false to "Could not read directory content"
        
        val validSubDirs = files.filter { it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
        if (validSubDirs.isNotEmpty()) {
            return true to null
        }
        
        val hasPdf = files.any { !it.isDirectory && it.name?.lowercase()?.endsWith(".pdf") == true }
        val hasVideo = files.any { !it.isDirectory && videoExtensions.any { ext -> it.name?.lowercase()?.endsWith(ext) == true } }
        val hasAudio = files.any { !it.isDirectory && audioExtensions.any { ext -> it.name?.lowercase()?.endsWith(ext) == true } }
        val hasImages = files.any { !it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) && imageExtensions.any { ext -> it.name?.lowercase()?.endsWith(ext) == true } }
        
        if (hasPdf || hasVideo || hasAudio || hasImages) {
            return true to null
        }

        return false to "No readable images or chapters found"
    }

    fun detectStructure(folderDoc: DocumentFile): String {
        val files = folderDoc.listFiles() ?: return "SINGLE"
        val subDirs = files.filter { it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
        val anyChildHasSubDirs = subDirs.any { child ->
            child.listFiles().any { it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
        }
        return if (anyChildHasSubDirs) "MEGA" else "SINGLE"
    }

    /**
     * Finds a dedicated cover image according to Hwaran rules.
     * Checks .zine/ first, then root folder dedicated covers (cover, folder, poster, thumb).
     */
    fun findCoverInFiles(folderDoc: DocumentFile, specifiedCover: String? = null): DocumentFile? {
        return ZineMetadataExtractor.findCoverInDocumentFolder(folderDoc, specifiedCover)
    }

    fun findCoverInFiles(files: Array<DocumentFile>): DocumentFile? {
        return files.find { item ->
            !item.isDirectory && ZineMetadataExtractor.isDedicatedCoverName(item.name)
        }
    }

    fun findCoverInJavaFiles(folder: File, specifiedCover: String? = null): File? {
        return ZineMetadataExtractor.findCoverInFolder(folder, specifiedCover)
    }

    fun findCoverInJavaFiles(files: List<File>): File? {
        return files.find { item ->
            item.isFile && ZineMetadataExtractor.isDedicatedCoverName(item.name)
        }
    }
}
