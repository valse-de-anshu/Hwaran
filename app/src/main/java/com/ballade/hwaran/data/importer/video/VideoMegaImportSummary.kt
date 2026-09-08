package com.ballade.hwaran.data.importer.video

data class VideoMegaImportSummary(
    val total: Int,
    val imported: Int,
    val skipped: Int,
    val skippedFolders: List<String>,
    val isCancelled: Boolean = false
)
