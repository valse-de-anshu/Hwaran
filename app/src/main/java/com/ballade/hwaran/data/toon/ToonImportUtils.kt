package com.ballade.hwaran.data.toon

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

    /**
     * Finds a cover image among the list of files.
     * Looks for common names like cover.jpg, folder.png, poster.jpg, etc.
     */
    fun findCoverInFiles(files: Array<DocumentFile>): DocumentFile? {
        return files.find { item ->
            val itemName = item.name?.lowercase() ?: ""
            itemName == "cover.jpg" || itemName == "cover.jpeg" || itemName == "cover.png" || itemName == "cover.gif" ||
            itemName == "folder.jpg" || itemName == "folder.jpeg" || itemName == "folder.png" || itemName == "folder.gif" ||
            itemName == "poster.jpg" || itemName == "poster.png" || itemName == "poster.gif"
        }
    }

    fun findCoverInJavaFiles(files: List<File>): File? {
        return files.find { item ->
            val itemName = item.name.lowercase()
            itemName == "cover.jpg" || itemName == "cover.jpeg" || itemName == "cover.png" || itemName == "cover.gif" ||
            itemName == "folder.jpg" || itemName == "folder.jpeg" || itemName == "folder.png" || itemName == "folder.gif" ||
            itemName == "poster.jpg" || itemName == "poster.png" || itemName == "poster.gif"
        }
    }
}
