package com.ballade.hwaran.data.video

import androidx.documentfile.provider.DocumentFile
import java.io.File

object VideoImportUtils {

    val videoExtensions = setOf("mp4", "mkv", "webm", "mov")
    val coverExtensions = setOf("jpg", "jpeg", "png", "gif")

    /**
     * A folder is valid for video import only if it contains at least one
     * supported video file. Pure image-only or empty folders are skipped.
     */
    fun isVideoFolderValid(doc: DocumentFile): Pair<Boolean, String?> {
        val files = doc.listFiles()
        if (files == null || files.isEmpty()) {
            return Pair(false, "Empty folder")
        }
        val hasVideo = files.any { file ->
            !file.isDirectory &&
                    videoExtensions.any { ext ->
                        file.name?.lowercase()?.endsWith(".$ext") == true
                    }
        }
        return if (hasVideo) {
            Pair(true, null)
        } else {
            Pair(false, "No video files")
        }
    }

    /**
     * Find a cover image in a list of DocumentFiles.
     * Priority: named covers (cover.*, poster.*, thumb.*, folder.*) → first image file.
     */
    fun findCoverInFiles(files: Array<DocumentFile>): DocumentFile? {
        val namedCover = files.find { file ->
            val name = file.name?.lowercase() ?: return@find false
            !file.isDirectory &&
                    coverExtensions.any { name.endsWith(".$it") } &&
                    (name.startsWith("cover") || name.startsWith("poster") ||
                            name.startsWith("thumb") || name.startsWith("folder"))
        }
        if (namedCover != null) return namedCover

        return files.firstOrNull { file ->
            !file.isDirectory &&
                    coverExtensions.any { ext ->
                        file.name?.lowercase()?.endsWith(".$ext") == true
                    }
        }
    }

    /**
     * Find a cover image among local Java File objects.
     * Priority: named covers → first image file.
     */
    fun findCoverInJavaFiles(files: List<File>): File? {
        val namedCover = files.find { file ->
            val name = file.name.lowercase()
            coverExtensions.any { name.endsWith(".$it") } &&
                    (name.startsWith("cover") || name.startsWith("poster") ||
                            name.startsWith("thumb") || name.startsWith("folder"))
        }
        if (namedCover != null) return namedCover

        return files.firstOrNull { file ->
            coverExtensions.any { ext -> file.name.lowercase().endsWith(".$ext") }
        }
    }
}
