package com.ballade.hwaran.core.util

import android.content.Context
import android.net.Uri
import com.ballade.hwaran.core.database.entity.ChapterEntity
import java.io.File

object CoverArtResolver {

    private val parentCoverCache = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val NULL_OBJECT = Any()

    /**
     * Resolves an image model suitable for Coil (File, Uri, or String),
     * properly supporting file paths, SAF content:// URIs, chapter thumbnails,
     * and fallback image files in the parent directory.
     */
    fun resolveCoverModel(
        coverPath: String?,
        parentUri: String? = null,
        chapters: List<ChapterEntity>? = null,
        context: Context? = null
    ): Any? {
        val clean = coverPath?.trim()?.let {
            if (it == "android.resource://android/drawable/ic_menu_gallery") "" else it
        } ?: ""

        if (clean.isNotEmpty()) {
            when {
                clean.startsWith("/") -> {
                    val file = File(clean)
                    if (file.exists() && file.length() > 0) return file
                }
                clean.startsWith("file://") -> {
                    val path = Uri.parse(clean).path
                    if (!path.isNullOrBlank()) {
                        val file = File(path)
                        if (file.exists() && file.length() > 0) return file
                    }
                    return Uri.parse(clean)
                }
                clean.startsWith("content://") -> {
                    return Uri.parse(clean)
                }
                else -> return clean
            }
        }

        // Dedicated cover image files in parent directory (cached in-memory to prevent repeated main-thread I/O)
        if (!parentUri.isNullOrBlank()) {
            val cached = parentCoverCache[parentUri]
            if (cached != null) {
                return if (cached === NULL_OBJECT) null else cached
            }

            val directPath = when {
                parentUri.startsWith("file://") -> Uri.parse(parentUri).path
                parentUri.startsWith("/") -> parentUri
                else -> null
            }

            if (!directPath.isNullOrBlank()) {
                val dir = File(directPath)
                if (dir.exists() && dir.isDirectory) {
                    val isCoverFile: (File) -> Boolean = { file ->
                        val n = file.name.lowercase()
                        (n.startsWith("cover.") || n.startsWith("cover_") || n.startsWith("folder.") || n.startsWith("poster.") || n.startsWith("thumb.") || n.startsWith("illus-fpc.") || n.startsWith("artwork.")) &&
                                (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                    }
                    var candidate = dir.listFiles()?.firstOrNull(isCoverFile)
                    if (candidate != null && candidate.exists()) {
                        parentCoverCache[parentUri] = candidate
                        return candidate
                    }

                    // Check common media image subdirectories (e.g., images/cover.jpg, img/poster.png)
                    val subDirs = listOf("images", "img", "covers", "artwork")
                    for (sub in subDirs) {
                        val subFolder = File(dir, sub)
                        if (subFolder.exists() && subFolder.isDirectory) {
                            val subCandidate = subFolder.listFiles()?.firstOrNull(isCoverFile)
                            if (subCandidate != null && subCandidate.exists()) {
                                parentCoverCache[parentUri] = subCandidate
                                return subCandidate
                            }
                        }
                    }

                    // Check parent directory (e.g. Channel/cover.jpg or manhua/Cover.jpg)
                    val parentDir = dir.parentFile
                    if (parentDir != null && parentDir.exists() && parentDir.isDirectory && !com.ballade.hwaran.core.metadata.ZineMetadataExtractor.isRootStorageDir(parentDir)) {
                        val parentCandidate = parentDir.listFiles()?.firstOrNull(isCoverFile)
                        if (parentCandidate != null && parentCandidate.exists()) {
                            parentCoverCache[parentUri] = parentCandidate
                            return parentCandidate
                        }
                    }

                    // Fallback to first image in first chapter
                    if (!chapters.isNullOrEmpty()) {
                        val firstChap = chapters.firstOrNull()
                        if (firstChap != null) {
                            val chapPath = when {
                                firstChap.folderUri.startsWith("file://") -> Uri.parse(firstChap.folderUri).path
                                firstChap.folderUri.startsWith("/") -> firstChap.folderUri
                                else -> null
                            }
                            if (chapPath != null) {
                                val chapDir = File(chapPath)
                                if (chapDir.exists() && chapDir.isDirectory) {
                                    val firstImg = chapDir.listFiles()?.firstOrNull { f ->
                                        val ext = f.extension.lowercase()
                                        f.isFile && (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp")
                                    }
                                    if (firstImg != null) {
                                        parentCoverCache[parentUri] = firstImg
                                        return firstImg
                                    }
                                }
                            }
                        }
                    }
                }
            }
            parentCoverCache[parentUri] = NULL_OBJECT
        }

        return null
    }
}
