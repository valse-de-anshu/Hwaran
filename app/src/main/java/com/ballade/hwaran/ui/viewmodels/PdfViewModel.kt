package com.ballade.hwaran.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.PdfMarkerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class PdfViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)

    fun saveLastPage(mangaId: Long, page: Int) {
        if (mangaId == -1L) return
        viewModelScope.launch(Dispatchers.IO) {
            val manga = database.libraryDao().getMangaById(mangaId)
            if (manga != null) {
                database.libraryDao().insertManga(manga.copy(lastReadPage = page))
            }
        }
    }

    fun incrementOpenCount(mangaId: Long) {
        if (mangaId == -1L) return
        viewModelScope.launch(Dispatchers.IO) {
            val manga = database.libraryDao().getMangaById(mangaId)
            if (manga != null) {
                database.libraryDao().insertManga(manga.copy(openCount = manga.openCount + 1))
            }
        }
    }

    fun getMarkers(mangaId: Long): Flow<List<PdfMarkerEntity>> {
        if (mangaId == -1L) return emptyFlow()
        return database.annotationDao().getMarkersForPdf(mangaId)
    }

    fun addMarker(marker: PdfMarkerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            database.annotationDao().insertMarker(marker)
        }
    }

    fun deleteMarker(marker: PdfMarkerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            database.annotationDao().deleteMarker(marker)
        }
    }
}
