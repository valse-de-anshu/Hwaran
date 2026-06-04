package com.ballade.hwaran.ui.screens

import android.content.Context
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfLinkData(
    val uri: String?,
    val destPageIdx: Int?,
    val bounds: RectF // normalized 0f..1f
)

object PdfLinkExtractor {
    
    // Memory cache: "pdfId_pageIndex" -> List<PdfLinkData>
    private val linkCache = mutableMapOf<String, List<PdfLinkData>>()

    suspend fun getLinksForPage(
        context: Context,
        pdfId: String,
        pfd: ParcelFileDescriptor,
        pageIndex: Int
    ): List<PdfLinkData> = withContext(Dispatchers.IO) {
        val cacheKey = "${pdfId}_$pageIndex"
        linkCache[cacheKey]?.let { return@withContext it }

        val result = mutableListOf<PdfLinkData>()
        var pdfiumCore: PdfiumCore? = null
        var doc: PdfDocument? = null
        try {
            pdfiumCore = PdfiumCore(context)
            // Use duplicate to avoid closing the original pfd being used by PdfRenderer
            val pfdDup = pfd.dup()
            doc = pdfiumCore.newDocument(pfdDup)
            
            pdfiumCore.openPage(doc, pageIndex)
            val links = pdfiumCore.getPageLinks(doc, pageIndex)
            
            val pageWidth = pdfiumCore.getPageWidthPoint(doc, pageIndex)
            val pageHeight = pdfiumCore.getPageHeightPoint(doc, pageIndex)

            for (link in links) {
                // Bounds are returned in page points (usually origin at bottom-left).
                // We normalize them to 0f..1f and invert the Y-axis so 0 is top.
                val bounds = link.bounds
                val normalizedBounds = RectF(
                    bounds.left / pageWidth,
                    1f - (bounds.top / pageHeight),
                    bounds.right / pageWidth,
                    1f - (bounds.bottom / pageHeight)
                )
                
                result.add(
                    PdfLinkData(
                        uri = link.uri,
                        destPageIdx = link.destPageIdx,
                        bounds = normalizedBounds
                    )
                )
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                if (doc != null && pdfiumCore != null) {
                    pdfiumCore.closeDocument(doc)
                }
            } catch (e: Exception) {}
        }

        linkCache[cacheKey] = result
        return@withContext result
    }
    
    fun clearCache() {
        linkCache.clear()
    }
}
