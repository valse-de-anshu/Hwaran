package com.ballade.hwaran.data.importer.music

import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.util.HistoryTracker
import com.ballade.hwaran.core.database.dao.LibraryDao
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicImportRepository(private val dao: LibraryDao) {

    suspend fun getAlbumByUri(uri: String): MangaEntity? = withContext(Dispatchers.IO) {
        dao.getRootMangaByUri(uri)
    }

    suspend fun insertAlbum(manga: MangaEntity): Long = withContext(Dispatchers.IO) {
        dao.insertManga(manga)
    }

    suspend fun insertTracks(tracks: List<ChapterEntity>) = withContext(Dispatchers.IO) {
        tracks.forEach { dao.insertChapter(it) }
    }

    fun logHistory(title: String, details: String) {
        HistoryTracker.logEvent("IMPORT", title, details)
    }
}
