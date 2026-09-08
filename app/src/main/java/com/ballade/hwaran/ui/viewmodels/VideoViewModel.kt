package com.ballade.hwaran.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ballade.hwaran.core.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)

    fun saveLastPosition(chapterId: Long, positionMs: Long, durationMs: Long) {
        if (chapterId == -1L) return
        viewModelScope.launch(Dispatchers.IO) {
            val chapter = database.trackDao().getChapterById(chapterId)
            if (chapter != null) {
                database.trackDao().insertChapter(
                    chapter.copy(
                        position = positionMs.toInt(),
                        duration = durationMs
                    )
                )
                val manga = database.libraryDao().getMangaById(chapter.mangaId)
                if (manga != null) {
                    database.libraryDao().insertManga(manga.copy(lastReadTitle = chapter.title))
                }
            }
        }
    }

    fun incrementOpenCount(chapterId: Long) {
        if (chapterId == -1L) return
        viewModelScope.launch(Dispatchers.IO) {
            val chapter = database.trackDao().getChapterById(chapterId)
            if (chapter != null) {
                database.trackDao().insertChapter(chapter.copy(openCount = chapter.openCount + 1))
                val manga = database.libraryDao().getMangaById(chapter.mangaId)
                if (manga != null) {
                    database.libraryDao().insertManga(manga.copy(openCount = manga.openCount + 1))
                }
            }
        }
    }
}
