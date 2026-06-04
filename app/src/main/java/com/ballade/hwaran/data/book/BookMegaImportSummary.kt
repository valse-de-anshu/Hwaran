package com.ballade.hwaran.data.book

data class BookMegaImportSummary(
    val total: Int,
    val imported: Int,
    val skipped: Int,
    val skippedFolders: List<String>,
    val isCancelled: Boolean
)
