package com.ballade.hwaran.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.PdfMarkerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class PdfViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)

    fun saveLastPage(mangaId: Long, page: Int) {
        if (mangaId == -1L) return
        viewModelScope.launch(Dispatchers.IO) {
            val manga = database.libraryDao().getMangaById(mangaId)
            if (manga != null) {
                val now = System.currentTimeMillis()
                database.libraryDao().insertManga(manga.copy(lastReadPage = page, lastModified = now))
                if (manga.parentMangaId != null) {
                    database.libraryDao().getMangaById(manga.parentMangaId)?.let { parent ->
                        database.libraryDao().insertManga(parent.copy(lastReadPage = page, lastModified = now))
                    }
                }
            }
        }
    }

    fun incrementOpenCount(mangaId: Long) {
        if (mangaId == -1L) return
        viewModelScope.launch(Dispatchers.IO) {
            val manga = database.libraryDao().getMangaById(mangaId)
            if (manga != null) {
                val now = System.currentTimeMillis()
                database.libraryDao().insertManga(manga.copy(openCount = manga.openCount + 1, lastModified = now))
                if (manga.parentMangaId != null) {
                    database.libraryDao().getMangaById(manga.parentMangaId)?.let { parent ->
                        database.libraryDao().insertManga(parent.copy(openCount = parent.openCount + 1, lastModified = now))
                    }
                }
            }
        }
    }

    fun getMarkers(mangaId: Long): Flow<List<PdfMarkerEntity>> {
        if (mangaId == -1L) return emptyFlow()
        return database.annotationDao().getMarkersForPdf(mangaId)
    }

    fun addMarker(marker: PdfMarkerEntity, onInserted: ((PdfMarkerEntity) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = database.annotationDao().insertMarker(marker)
            val inserted = marker.copy(id = newId)
            withContext(Dispatchers.Main) {
                onInserted?.invoke(inserted)
            }
        }
    }

    fun deleteMarker(marker: PdfMarkerEntity, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (marker.id != 0L) {
                database.annotationDao().deleteMarkerById(marker.id)
            } else {
                val currentMarkers = database.annotationDao().getMarkersForPdf(marker.mangaId).firstOrNull() ?: emptyList()
                val match = currentMarkers.find { it.page == marker.page && abs(it.x1 - marker.x1) < 0.05f && abs(it.y1 - marker.y1) < 0.05f }
                if (match != null) {
                    database.annotationDao().deleteMarkerById(match.id)
                }
            }
            withContext(Dispatchers.Main) {
                onDeleted?.invoke()
            }
        }
    }

    fun clearMarkersForPage(mangaId: Long, page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            database.annotationDao().deleteMarkersForPage(mangaId, page)
        }
    }
}
