package com.ballade.hwaran.data.video

import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.data.local.LibraryDao
import com.ballade.hwaran.data.local.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoImportRepository(private val libraryDao: LibraryDao) {

    suspend fun getRootMangaByUri(uri: String): MangaEntity? {
        return withContext(Dispatchers.IO) {
            libraryDao.getRootMangaByUri(uri)
        }
    }

    suspend fun getChapterByUri(uri: String): ChapterEntity? {
        return withContext(Dispatchers.IO) {
            libraryDao.getChapterByUri(uri)
        }
    }

    suspend fun insertManga(manga: MangaEntity): Long {
        return withContext(Dispatchers.IO) {
            libraryDao.insertManga(manga)
        }
    }

    suspend fun insertChapters(chapters: List<ChapterEntity>) {
        return withContext(Dispatchers.IO) {
            chapters.forEach {
                libraryDao.insertChapter(it)
            }
        }
    }
}
