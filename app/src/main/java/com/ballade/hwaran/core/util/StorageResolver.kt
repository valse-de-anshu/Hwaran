package com.ballade.hwaran.core.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

object StorageResolver {

    /**
     * Resolves a Uri to a direct java.io.File whenever accessible.
     * Works for file:// URIs as well as standard external storage SAF content:// URIs
     * (e.g. content://com.android.externalstorage.documents/tree/primary%3A...).
     * Using native File operations bypasses slow ContentProvider Binder IPC,
     * providing up to 1000x faster scanning for folders with many chapters.
     */
    fun resolveToFile(context: Context, uri: Uri): File? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            return if (file.exists()) file else null
        }

        if (uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents") {
            try {
                // If the URI points to a document within a tree (has /document/ segment),
                // DocumentsContract.getDocumentId yields the specific subfolder/file document ID (e.g. primary:Music/AlbumA).
                // If it is a pure tree root URI, getDocumentId fails, so we fall back to getTreeDocumentId (primary:Music).
                val docId = try {
                    DocumentsContract.getDocumentId(uri)
                } catch (_: Exception) {
                    if (DocumentsContract.isTreeUri(uri)) {
                        try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { null }
                    } else null
                }
                if (!docId.isNullOrBlank()) {
                    if (docId.startsWith("primary:")) {
                        val relativePath = docId.removePrefix("primary:")
                        val primaryRoot = Environment.getExternalStorageDirectory()
                        val file = File(primaryRoot, relativePath)
                        if (file.exists() && file.canRead()) {
                            return file
                        }
                    } else if (docId.contains(":")) {
                        val parts = docId.split(":", limit = 2)
                        val volume = parts[0]
                        val relativePath = parts.getOrNull(1) ?: ""
                        val candidate = File("/storage/$volume/$relativePath")
                        if (candidate.exists() && candidate.canRead()) {
                            return candidate
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val uriStr = uri.toString()
        if (uriStr.startsWith("/")) {
            val f = File(uriStr)
            if (f.exists() && f.canRead()) return f
        }

        return null
    }

    /**
     * Obtains a DocumentFile from a Uri, preferring a high-speed native File-backed
     * DocumentFile whenever the underlying path is accessible, bypassing slow SAF Binder IPC.
     */
    fun resolveDocument(context: Context, uri: Uri, isFile: Boolean = false): DocumentFile? {
        val directFile = resolveToFile(context, uri)
        if (directFile != null) {
            return DocumentFile.fromFile(directFile)
        }

        return if (isFile) {
            if (uri.scheme == "file") {
                uri.path?.let { DocumentFile.fromFile(File(it)) }
            } else {
                DocumentFile.fromSingleUri(context, uri)
            }
        } else {
            if (uri.scheme == "file") {
                uri.path?.let { DocumentFile.fromFile(File(it)) }
            } else {
                DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
            }
        }
    }
}
