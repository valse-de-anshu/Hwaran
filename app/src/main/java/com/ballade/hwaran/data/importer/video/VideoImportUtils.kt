package com.ballade.hwaran.data.importer.video

import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import java.io.File

object VideoImportUtils {

    // Supported video formats per Hwaran Import Rules (Rule 1)
    val videoExtensions = setOf(
        "mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "flv", "ts", "wmv", "asf"
    )

    val subtitleExtensions = setOf("srt", "vtt", "ass", "ssa", "sub")

    val coverExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

    fun naturalSortKey(name: String): String {
        return name.lowercase().replace(Regex("\\d+")) { match ->
            match.value.padStart(10, '0')
        }
    }

    /**
     * Recursively collect all supported video files from a DocumentFile folder
     * (up to maxDepth, default 3). Internal (.zine) and auxiliary folders are excluded.
     */
    fun findVideoFiles(folderDoc: DocumentFile, maxDepth: Int = 3): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        fun search(current: DocumentFile, depth: Int) {
            if (depth > maxDepth) return
            val files = current.listFiles()
            // 1. Direct video files in current folder
            val videos = files.filter { file ->
                !file.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(file.name) &&
                videoExtensions.any { ext -> file.name?.lowercase()?.endsWith(".$ext") == true }
            }.sortedWith(compareBy { naturalSortKey(it.name ?: "") })
            result.addAll(videos)

            // 2. Subfolders
            val subfolders = files.filter { dir ->
                dir.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(dir.name)
            }.sortedWith(compareBy { naturalSortKey(it.name ?: "") })

            for (sub in subfolders) {
                search(sub, depth + 1)
            }
        }
        search(folderDoc, 1)
        return result
    }

    /**
     * Recursively collect all subtitle files (.srt, .vtt, .ass, etc.) from a DocumentFile folder.
     */
    fun findSubtitleFiles(folderDoc: DocumentFile, maxDepth: Int = 3): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        fun search(current: DocumentFile, depth: Int) {
            if (depth > maxDepth) return
            val files = current.listFiles()
            val subs = files.filter { file ->
                !file.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(file.name) &&
                subtitleExtensions.any { ext -> file.name?.lowercase()?.endsWith(".$ext") == true }
            }.sortedWith(compareBy { naturalSortKey(it.name ?: "") })
            result.addAll(subs)

            val subfolders = files.filter { dir ->
                dir.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(dir.name)
            }
            for (sub in subfolders) {
                search(sub, depth + 1)
            }
        }
        search(folderDoc, 1)
        return result
    }

    /**
     * Recursively collect all supported video files from a local File directory.
     */
    fun findVideoFilesInDir(folder: File, maxDepth: Int = 3): List<File> {
        val result = mutableListOf<File>()
        fun search(current: File, depth: Int) {
            if (depth > maxDepth || !current.exists() || !current.isDirectory) return
            val files = current.listFiles() ?: return
            val videos = files.filter { file ->
                file.isFile && !ZineMetadataExtractor.isInternalOrAuxiliary(file.name) &&
                videoExtensions.any { ext -> file.extension.lowercase() == ext }
            }.sortedWith(compareBy { naturalSortKey(it.name) })
            result.addAll(videos)

            val subfolders = files.filter { dir ->
                dir.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(dir.name)
            }.sortedWith(compareBy { naturalSortKey(it.name) })

            for (sub in subfolders) {
                search(sub, depth + 1)
            }
        }
        search(folder, 1)
        return result
    }

    /**
     * A folder is valid for video import only if it contains at least one
     * supported video file, either directly or in subdirectories.
     * Internal metadata folders (.zine) are ignored.
     */
    fun isVideoFolderValid(doc: DocumentFile): Pair<Boolean, String?> {
        val videos = findVideoFiles(doc)
        return if (videos.isNotEmpty()) {
            Pair(true, null)
        } else {
            Pair(false, "No video files found")
        }
    }

    /**
     * Find a dedicated cover image in a DocumentFile folder.
     * Priority: root folder (.zine/ or dedicated cover) -> immediate subfolders.
     */
    fun findCover(folderDoc: DocumentFile, specifiedCover: String? = null): DocumentFile? {
        val rootCover = ZineMetadataExtractor.findCoverInDocumentFolder(folderDoc, specifiedCover)
        if (rootCover != null) return rootCover

        val children = folderDoc.listFiles()
        for (child in children) {
            if (child.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(child.name)) {
                val subCover = ZineMetadataExtractor.findCoverInDocumentFolder(child, specifiedCover)
                if (subCover != null) return subCover
            }
        }
        return null
    }

    /**
     * Find a dedicated cover image in a list of DocumentFiles.
     * Priority: .zine/ cover -> root named covers (cover.*, poster.*, thumb.*, folder.*).
     */
    fun findCoverInFiles(folderDoc: DocumentFile, specifiedCover: String? = null): DocumentFile? {
        return findCover(folderDoc, specifiedCover)
    }

    fun findCoverInFiles(files: Array<DocumentFile>): DocumentFile? {
        return files.find { item ->
            !item.isDirectory && ZineMetadataExtractor.isDedicatedCoverName(item.name)
        }
    }

    /**
     * Find a cover image among local Java File objects.
     */
    fun findCoverInFolder(folder: File, specifiedCover: String? = null): File? {
        val rootCover = ZineMetadataExtractor.findCoverInFolder(folder, specifiedCover)
        if (rootCover != null) return rootCover

        val children = folder.listFiles() ?: emptyArray()
        for (child in children) {
            if (child.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(child.name)) {
                val subCover = ZineMetadataExtractor.findCoverInFolder(child, specifiedCover)
                if (subCover != null) return subCover
            }
        }
        return null
    }

    fun findCoverInJavaFiles(folder: File, specifiedCover: String? = null): File? {
        return findCoverInFolder(folder, specifiedCover)
    }

    fun findCoverInJavaFiles(files: List<File>): File? {
        return files.find { item ->
            item.isFile && ZineMetadataExtractor.isDedicatedCoverName(item.name)
        }
    }

    /**
     * Detects whether the selected folder is a SINGLE series/channel folder
     * or a MEGA folder containing multiple video folders.
     */
    fun detectImportMode(selectedFolder: DocumentFile): String {
        val files = selectedFolder.listFiles()
        if (files.isEmpty()) return "SINGLE"

        // 1. Direct video files at root -> SINGLE
        val hasDirectVideos = files.any { file ->
            !file.isDirectory && videoExtensions.any { ext -> file.name?.lowercase()?.endsWith(".$ext") == true }
        }
        if (hasDirectVideos) return "SINGLE"

        // 2. Dedicated cover image at root -> SINGLE
        val hasRootCover = files.any { file ->
            !file.isDirectory && ZineMetadataExtractor.isDedicatedCoverName(file.name)
        }
        if (hasRootCover) return "SINGLE"

        // 3. Child directories
        val childDirs = files.filter {
            it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name)
        }

        if (childDirs.size <= 1) return "SINGLE"

        // If any child is named "videos" or "video" -> SINGLE
        if (childDirs.any { it.name.equals("videos", true) || it.name.equals("video", true) }) {
            return "SINGLE"
        }

        // Multiple subdirectories with videos -> MEGA
        val validVideoChildren = childDirs.count { isVideoFolderValid(it).first }
        if (validVideoChildren > 1) {
            return "MEGA"
        }

        return "SINGLE"
    }
}
