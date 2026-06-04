package com.ballade.hwaran.data.book

import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.data.local.LibraryDao
import com.ballade.hwaran.data.local.MangaEntity

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
