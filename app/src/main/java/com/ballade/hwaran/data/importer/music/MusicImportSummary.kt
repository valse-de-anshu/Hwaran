package com.ballade.hwaran.data.importer.music

data class MusicImportSummary(
    val imported: Int,
    val skipped: Int,
    val reasons: List<String>
)
