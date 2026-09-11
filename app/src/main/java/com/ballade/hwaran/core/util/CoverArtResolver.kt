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
                clean.startsWith("content://") -> return Uri.parse(clean)
                clean.startsWith("file://") -> return Uri.parse(clean)
                clean.startsWith("/") -> {
                    val file = File(clean)
                    if (file.exists()) return file
                }
                else -> return clean
            }
        }

        // 1. Fallback: check chapter thumbnails
        val chapterThumb = chapters?.firstOrNull { !it.thumbnailUri.isNullOrBlank() }?.thumbnailUri?.trim()
        if (!chapterThumb.isNullOrEmpty()) {
            when {
                chapterThumb.startsWith("content://") -> return Uri.parse(chapterThumb)
                chapterThumb.startsWith("file://") -> return Uri.parse(chapterThumb)
                chapterThumb.startsWith("/") -> {
                    val file = File(chapterThumb)
                    if (file.exists()) return file
                }
                else -> return chapterThumb
            }
        }

        // 2. Fallback: check parent directory for cover image files
        if (!parentUri.isNullOrBlank()) {
            if (parentUri.startsWith("/")) {
                val dir = File(parentUri)
                if (dir.exists() && dir.isDirectory) {
                    val candidate = dir.listFiles()?.firstOrNull { file ->
                        val n = file.name.lowercase()
                        (n.startsWith("cover.") || n.startsWith("folder.") || n.startsWith("poster.") ||
                                n.startsWith("thumb.") || n.startsWith("001.") || n.startsWith("01.")) &&
                                (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                    }
                    if (candidate != null && candidate.exists()) return candidate
                }
            } else if (parentUri.startsWith("content://") && context != null) {
                try {
                    val docDir = DocumentFile.fromTreeUri(context, Uri.parse(parentUri))
                    val candidate = docDir?.listFiles()?.firstOrNull { file ->
                        val n = file.name?.lowercase() ?: ""
                        (n.startsWith("cover.") || n.startsWith("folder.") || n.startsWith("poster.") ||
                                n.startsWith("thumb.") || n.startsWith("001.") || n.startsWith("01.")) &&
                                (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                    }
                    if (candidate != null) return candidate.uri
                } catch (_: Exception) {}
            }
        }

        return null
    }
}
