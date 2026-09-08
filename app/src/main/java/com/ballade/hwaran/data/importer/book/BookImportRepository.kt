package com.ballade.hwaran.data.importer.book

import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.dao.LibraryDao
import com.ballade.hwaran.core.database.entity.MangaEntity

class BookImportRepository(private val libraryDao: LibraryDao) {

    suspend fun insertManga(manga: MangaEntity): Long {
        return libraryDao.insertManga(manga)
    }

    suspend fun insertChapters(chapters: List<ChapterEntity>) {
        libraryDao.insertChapters(chapters)
    }

    suspend fun getRootMangaByUri(uri: String): MangaEntity? {
        return libraryDao.getRootMangaByUri(uri)
    }

    suspend fun getChapterByUri(uri: String): ChapterEntity? {
        return libraryDao.getChapterByUri(uri)
    }
}
