package com.ballade.hwaran.data.importer.toon

data class ToonMegaImportSummary(
    val total: Int,
    val imported: Int,
    val skipped: Int,
    val skippedFolders: List<String>,
    val isCancelled: Boolean
)
