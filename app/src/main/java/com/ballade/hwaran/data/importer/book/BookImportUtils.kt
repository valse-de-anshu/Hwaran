package com.ballade.hwaran.data.importer.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import java.io.File

object BookImportUtils {

    val bookExtensions = com.ballade.hwaran.backend.novel.NovelParser.BOOK_EBOOK_EXTENSIONS

    fun isBookFolderValid(folderDoc: DocumentFile): Pair<Boolean, String?> {
        val files = folderDoc.listFiles() ?: return false to "Could not read directory content"
        
        // Valid if contains any Book file (.pdf, .epub, .kf8, .mobi, .html, etc.) or subdirectories
        val hasBook = files.any { !it.isDirectory && com.ballade.hwaran.backend.novel.NovelParser.isBookFile(it.name) }
        if (hasBook) return true to null
        
        val validSubDirs = files.filter { it.isDirectory && !com.ballade.hwaran.core.metadata.ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
        if (validSubDirs.isNotEmpty()) return true to null

        return false to "No book files found (.pdf, .epub, .kf8, .mobi, .html, etc.)"
    }

    fun detectImportMode(selectedFolder: DocumentFile): String {
        val files = selectedFolder.listFiles() ?: return "SINGLE"
        
        // Rule: If selected folder contains >=1 book directly -> Single Folder (import folder / book)
        val hasDirectBook = files.any { !it.isDirectory && com.ballade.hwaran.backend.novel.NovelParser.isBookFile(it.name) }
        if (hasDirectBook) {
            return "SINGLE"
        }
        
        // Else if: no book directly + child folders -> Mega Import
        val hasChildFolders = files.any { it.isDirectory && !com.ballade.hwaran.core.metadata.ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
        if (hasChildFolders) {
            return "MEGA"
        }
        
        return "SINGLE"
    }

    fun generatePdfThumbnail(context: Context, pdfUri: Uri, cacheFile: File): String? {
        try {
            val pfd = if (pdfUri.scheme == "content") {
                context.contentResolver.openFileDescriptor(pdfUri, "r")
            } else {
                ParcelFileDescriptor.open(File(pdfUri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            }
            if (pfd != null) {
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val width = page.width * 2
                    val height = page.height * 2
                    val maxDim = 800
                    val scale = if (width > maxDim || height > maxDim) maxDim.toFloat() / maxOf(width, height) else 1f
                    val w = (width * scale).toInt()
                    val h = (height * scale).toInt()
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    cacheFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    
                    renderer.close()
                    pfd.close()
                    return cacheFile.absolutePath
                }
                renderer.close()
                pfd.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
