package com.ballade.hwaran.data.importer.video

import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import java.io.File

object VideoImportUtils {

    // Supported video formats per Hwaran Import Rules (Rule 1)
    val videoExtensions = setOf(
        "mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "flv", "ts", "wmv", "asf"
    )

    val coverExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

    /**
     * A folder is valid for video import only if it contains at least one
     * supported video file, either directly or in subdirectories (seasons).
     * Internal metadata folders (.zine) are ignored.
     */
    fun isVideoFolderValid(doc: DocumentFile): Pair<Boolean, String?> {
        val files = doc.listFiles()
        if (files.isEmpty()) {
            return Pair(false, "Empty folder")
        }

        val hasDirectVideo = files.any { file ->
            !file.isDirectory && videoExtensions.any { ext ->
                file.name?.lowercase()?.endsWith(".$ext") == true
            }
        }
        if (hasDirectVideo) return Pair(true, null)

        val hasVideoInSubfolders = files.any { dir ->
            dir.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(dir.name) &&
            dir.listFiles().any { file ->
                !file.isDirectory && videoExtensions.any { ext ->
                    file.name?.lowercase()?.endsWith(".$ext") == true
                }
            }
        }
        if (hasVideoInSubfolders) return Pair(true, null)

        return Pair(false, "No video files found")
    }

    /**
     * Find a dedicated cover image in a list of DocumentFiles.
     * Priority: .zine/ cover -> root named covers (cover.*, poster.*, thumb.*, folder.*).
     */
    fun findCoverInFiles(folderDoc: DocumentFile, specifiedCover: String? = null): DocumentFile? {
        return ZineMetadataExtractor.findCoverInDocumentFolder(folderDoc, specifiedCover)
    }

    fun findCoverInFiles(files: Array<DocumentFile>): DocumentFile? {
        return files.find { item ->
            !item.isDirectory && ZineMetadataExtractor.isDedicatedCoverName(item.name)
        }
    }

    /**
     * Find a cover image among local Java File objects.
     */
    fun findCoverInJavaFiles(folder: File, specifiedCover: String? = null): File? {
        return ZineMetadataExtractor.findCoverInFolder(folder, specifiedCover)
    }

    fun findCoverInJavaFiles(files: List<File>): File? {
        return files.find { item ->
            item.isFile && ZineMetadataExtractor.isDedicatedCoverName(item.name)
        }
    }
}
