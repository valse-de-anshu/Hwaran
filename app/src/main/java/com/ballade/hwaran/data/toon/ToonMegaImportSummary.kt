package com.ballade.hwaran.data.toon

data class ToonMegaImportSummary(
    val total: Int,
    val imported: Int,
    val skipped: Int,
    val skippedFolders: List<String>,
    val isCancelled: Boolean
)
