package com.ballade.hwaran.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ballade.hwaran.data.local.AppDatabase
import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = LibraryRepository(application, database)

    private val _images = MutableStateFlow<List<String>>(emptyList())
    val images: StateFlow<List<String>> = _images

    private val _chapter = MutableStateFlow<ChapterEntity?>(null)
    val chapter: StateFlow<ChapterEntity?> = _chapter
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _allChapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val allChapters: StateFlow<List<ChapterEntity>> = _allChapters

    private val _mangaTitle = MutableStateFlow<String?>("")
    val mangaTitle = _mangaTitle.asStateFlow()

    private val _startPage = MutableStateFlow(0)
    val startPage = _startPage.asStateFlow()

    val nextChapterId = MutableStateFlow<Long?>(null)
    val prevChapterId = MutableStateFlow<Long?>(null)

    private var chaptersJob: Job? = null

    fun loadChapter(chapterId: Long) {
        if (chapterId == -1L) return
        _isLoading.value = true
        viewModelScope.launch {
            val chapterEntity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { database.libraryDao().getChapterById(chapterId) }
            _chapter.value = chapterEntity

            if (chapterEntity != null) {
                val mangaEntity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { database.libraryDao().getMangaById(chapterEntity.mangaId) }
                _mangaTitle.value = mangaEntity?.title

                if (mangaEntity != null) {
                    if (mangaEntity.lastReadTitle == chapterEntity.title) {
                        _startPage.value = (mangaEntity.lastReadPage ?: 1).coerceAtLeast(1) - 1
                    } else {
                        _startPage.value = 0
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        database.libraryDao().insertManga(mangaEntity.copy(
                            lastReadTitle = chapterEntity.title,
                            openCount = mangaEntity.openCount + 1
                        ))
                        database.libraryDao().insertChapter(chapterEntity.copy(openCount = chapterEntity.openCount + 1))
                    }
                    // Log Toon Read Event
                    com.ballade.hwaran.data.local.HistoryTracker.logEvent(
                        "READ_TOON",
                        chapterEntity.title,
                        "mangaId:${mangaEntity.id}|chapterId:${chapterEntity.id}|fallback:Toon: ${mangaEntity.title}"
                    )
                }

                // Fetch image URIs using absolute paths pointing to Vault
                _images.value = repository.getChapterImages(chapterEntity.folderUri)
                _isLoading.value = false // Only show content after images list is ready
                
                // Fetch all chapters to determine next/prev
                chaptersJob?.cancel()
                chaptersJob = viewModelScope.launch {
                    database.libraryDao().getChaptersForManga(chapterEntity.mangaId).collect { list ->
                        val sortedList = list.sortedWith(compareBy<ChapterEntity> { 
                            Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloat() ?: Float.MAX_VALUE 
                        }.thenBy {
                            it.title.replace(Regex("\\d+")) { matchResult ->
                                matchResult.value.padStart(10, '0')
                            }
                        })
                        _allChapters.value = sortedList
                        
                        // We must find the current chapter index to calculate next/prev
                        val currentIndex = sortedList.indexOfFirst { it.id == chapterId }
                        if (currentIndex != -1) {
                            // list is ordered naturally. So index+1 is next chapter.
                            prevChapterId.value = sortedList.getOrNull(currentIndex - 1)?.id
                            nextChapterId.value = sortedList.getOrNull(currentIndex + 1)?.id
                        } else {
                            prevChapterId.value = null
                            nextChapterId.value = null
                        }
                    }
                }
            } else {
                _isLoading.value = false
            }
        }
    }

    fun loadExternalImage(uri: String) {
        _isLoading.value = true
        viewModelScope.launch {
            _images.value = listOf(uri)
            _chapter.value = ChapterEntity(id = -1L, mangaId = -1L, title = Uri.parse(uri).lastPathSegment ?: "Image", folderUri = uri)
            _mangaTitle.value = "External Image"
            _isLoading.value = false
        }
    }

    fun saveLastPage(page: Int) {
        val currentChapter = _chapter.value ?: return
        if (currentChapter.mangaId == -1L) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val manga = database.libraryDao().getMangaById(currentChapter.mangaId)
            if (manga != null) {
                database.libraryDao().insertManga(manga.copy(lastReadPage = page))
            }
        }
    }
}
