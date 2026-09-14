package com.ballade.hwaran.data.importer.toon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object ToonImportUtils {

    val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif")
    val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".webm", ".m4v", ".3gp", ".mov", ".flv")
    val audioExtensions = listOf(".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a")

    fun isToonFolderValid(folderDoc: DocumentFile): Pair<Boolean, String?> {
        val files = folderDoc.listFiles() ?: return false to "Could not read directory content"
        
        val hasSubDirs = files.any { it.isDirectory && !(it.name?.startsWith(".") == true) }
        if (hasSubDirs) {
            return true to null
        }
        
        val hasPdf = files.any { !it.isDirectory && it.name?.lowercase()?.endsWith(".pdf") == true }
        val hasVideo = files.any { !it.isDirectory && videoExtensions.any { ext -> it.name?.lowercase()?.endsWith(ext) == true } }
        val hasAudio = files.any { !it.isDirectory && audioExtensions.any { ext -> it.name?.lowercase()?.endsWith(ext) == true } }
        val hasImages = files.any { !it.isDirectory && imageExtensions.any { ext -> it.name?.lowercase()?.endsWith(ext) == true } }
        
        if (hasPdf || hasVideo || hasAudio || hasImages) {
            return true to null
        }

        return false to "No readable images or chapters found"
    }

    fun detectStructure(folderDoc: DocumentFile): String {
        val files = folderDoc.listFiles() ?: return "SINGLE"
        val subDirs = files.filter { it.isDirectory && !(it.name?.startsWith(".") == true) }
        val anyChildHasSubDirs = subDirs.any { child ->
            child.listFiles().any { it.isDirectory && !(it.name?.startsWith(".") == true) }
        }
        return if (anyChildHasSubDirs) "MEGA" else "SINGLE"
    }

    private val dedicatedCoverNames = setOf(
        "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
        "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
        "poster.jpg", "poster.jpeg", "poster.png", "poster.webp",
        "thumb.jpg", "thumb.jpeg", "thumb.png", "thumb.webp"
    )

    /**
     * Finds a dedicated cover image among the list of files.
     * Strictly matches dedicated names: cover, folder, poster, thumb.
     */
    fun findCoverInFiles(files: Array<DocumentFile>): DocumentFile? {
        return files.find { item ->
            val itemName = item.name?.lowercase() ?: ""
            itemName in dedicatedCoverNames
        }
    }

    fun findCoverInJavaFiles(files: List<File>): File? {
        return files.find { item ->
            val itemName = item.name.lowercase()
            itemName in dedicatedCoverNames
        }
    }
}
