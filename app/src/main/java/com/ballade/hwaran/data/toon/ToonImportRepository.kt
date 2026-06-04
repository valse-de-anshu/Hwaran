package com.ballade.hwaran.data.toon

import com.ballade.hwaran.data.local.LibraryDao
import com.ballade.hwaran.data.local.MangaEntity
import com.ballade.hwaran.data.local.ChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToonImportRepository(private val libraryDao: LibraryDao) {

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

    suspend fun insertChapter(chapter: ChapterEntity): Long {
        return withContext(Dispatchers.IO) {
            libraryDao.insertChapter(chapter)
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
