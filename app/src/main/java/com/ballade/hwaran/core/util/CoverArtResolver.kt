package com.ballade.hwaran.core.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import java.io.File

object CoverArtResolver {

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
                    val uri = Uri.parse(clean)
                    if (context != null) {
                        try {
                            val cached = CoverCacheManager.cacheCoverFromUri(context, uri, "cover", "cached")
                            if (cached != null) {
                                val cachedFile = File(cached)
                                if (cachedFile.exists() && cachedFile.length() > 0) return cachedFile
                            }
                        } catch (_: Exception) {}
                    }
                    return uri
                }
                else -> return clean
            }
        }

        // Dedicated cover image files in parent directory (strictly cover, folder, poster, thumb)
        if (!parentUri.isNullOrBlank()) {
            if (parentUri.startsWith("/")) {
                val dir = File(parentUri)
                if (dir.exists() && dir.isDirectory) {
                    val candidate = dir.listFiles()?.firstOrNull { file ->
                        val n = file.name.lowercase()
                        (n.startsWith("cover.") || n.startsWith("folder.") || n.startsWith("poster.") || n.startsWith("thumb.")) &&
                                (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                    }
                    if (candidate != null && candidate.exists()) return candidate
                }
            } else if (parentUri.startsWith("content://") && context != null) {
                try {
                    val docDir = DocumentFile.fromTreeUri(context, Uri.parse(parentUri))
                    val candidate = docDir?.listFiles()?.firstOrNull { file ->
                        val n = file.name?.lowercase() ?: ""
                        (n.startsWith("cover.") || n.startsWith("folder.") || n.startsWith("poster.") || n.startsWith("thumb.")) &&
                                (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                    }
                    if (candidate != null) {
                        val cached = CoverCacheManager.cacheCoverFromUri(context, candidate.uri, "parent", "cover")
                        if (cached != null) {
                            val cachedFile = File(cached)
                            if (cachedFile.exists() && cachedFile.length() > 0) return cachedFile
                        }
                        return candidate.uri
                    }
                } catch (_: Exception) {}
            }
        }

        return null
    }
}
