package com.ballade.hwaran.backend.book

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.database.entity.PdfMarkerEntity
import com.ballade.hwaran.core.util.HistoryTracker
import com.ballade.hwaran.data.importer.book.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookBackend(private val application: Application) {
    private val database = AppDatabase.getDatabase(application)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val bookRepository = BookImportRepository(database.libraryDao())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(0)
    val importProgress: StateFlow<Int> = _importProgress.asStateFlow()

    fun getBooksFlow(workspace: String?, isNsfw: Boolean): Flow<List<MangaEntity>> {
        return database.mediaDao().getAllManga().map { list ->
            list.filter {
                it.contentType == 1 &&
                it.isNsfw == isNsfw &&
                it.workspace == workspace
            }
        }
    }

    suspend fun getBookById(id: Long): MangaEntity? {
        return withContext(Dispatchers.IO) {
            database.mediaDao().getMangaById(id)
        }
    }

    fun getMarkersFlow(mangaId: Long): Flow<List<PdfMarkerEntity>> {
        return database.annotationDao().getMarkersForPdf(mangaId)
    }

    fun addMarker(marker: PdfMarkerEntity) {
        scope.launch {
            database.annotationDao().insertMarker(marker)
        }
    }

    fun deleteMarker(marker: PdfMarkerEntity) {
        scope.launch {
            database.annotationDao().deleteMarker(marker)
        }
    }

    fun updateReadingProgress(mangaId: Long, page: Int) {
        scope.launch {
            val manga = database.mediaDao().getMangaById(mangaId) ?: return@launch
            val now = System.currentTimeMillis()
            database.mediaDao().insertManga(manga.copy(lastReadPage = page, openCount = manga.openCount + 1, lastModified = now))
            if (manga.parentMangaId != null) {
                database.mediaDao().getMangaById(manga.parentMangaId)?.let { parent ->
                    database.mediaDao().insertManga(parent.copy(lastReadPage = page, openCount = parent.openCount + 1, lastModified = now))
                }
            }
            HistoryTracker.logEvent("READ_BOOK", manga.title, "mangaId:$mangaId|page:$page")
        }
    }

    fun updateBookMetadata(mangaId: Long, title: String, thoughts: String?, coverPath: String?) {
        scope.launch {
            val manga = database.mediaDao().getMangaById(mangaId) ?: return@launch
            database.mediaDao().insertManga(
                manga.copy(
                    title = title,
                    thoughts = thoughts ?: manga.thoughts,
                    coverPath = coverPath ?: manga.coverPath
                )
            )
        }
    }

    fun deleteBook(mangaId: Long) {
        scope.launch {
            val manga = database.mediaDao().getMangaById(mangaId) ?: return@launch
            database.mediaDao().deleteManga(manga)
            HistoryTracker.logEvent("DELETE", manga.title, "Book Deleted")
        }
    }

    fun importBook(uri: Uri, isFile: Boolean = false, isLocalMode: Boolean, isNsfw: Boolean, workspace: String?, onComplete: () -> Unit = {}) {
        scope.launch {
            _isImporting.value = true
            _importProgress.value = 0
            try {
                if (isFile) {
                    val pdfDoc = DocumentFile.fromSingleUri(application, uri)
                    if (pdfDoc != null) {
                        if (isLocalMode) {
                            BookLocalSingleImport.executeSinglePdf(
                                context = application,
                                repository = bookRepository,
                                pdfDoc = pdfDoc,
                                workspace = workspace,
                                isNsfw = isNsfw,
                                boxPurpose = null,
                                importMode = "Single Import",
                                isCancelled = { false }
                            )
                        } else {
                            BookExternalSingleImport.executeSinglePdf(
                                context = application,
                                repository = bookRepository,
                                pdfDoc = pdfDoc,
                                workspace = workspace,
                                isNsfw = isNsfw,
                                boxPurpose = null,
                                importMode = "Single Import",
                                isCancelled = { false }
                            )
                        }
                    }
                } else {
                    val doc = DocumentFile.fromTreeUri(application, uri)
                    if (doc != null) {
                        val structure = BookImportUtils.detectImportMode(doc)
                        if (isLocalMode) {
                            if (structure == "SINGLE") {
                                BookLocalSingleImport.execute(
                                    context = application,
                                    repository = bookRepository,
                                    uri = uri,
                                    workspace = workspace,
                                    isNsfw = isNsfw,
                                    boxPurpose = null,
                                    isCancelled = { false },
                                    onProgress = { progress -> _importProgress.value = progress }
                                )
                            } else {
                                BookLocalMegaImport.execute(
                                    context = application,
                                    repository = bookRepository,
                                    parentUri = uri,
                                    workspace = workspace,
                                    isNsfw = isNsfw,
                                    boxPurpose = null,
                                    isCancelled = { false }
                                ) { progress ->
                                    _importProgress.value = (progress * 100).toInt()
                                }
                            }
                        } else {
                            if (structure == "SINGLE") {
                                BookExternalSingleImport.execute(
                                    context = application,
                                    repository = bookRepository,
                                    uri = uri,
                                    workspace = workspace,
                                    isNsfw = isNsfw,
                                    boxPurpose = null,
                                    isCancelled = { false },
                                    onProgress = { progress -> _importProgress.value = progress }
                                )
                            } else {
                                BookExternalMegaImport.execute(
                                    context = application,
                                    repository = bookRepository,
                                    parentUri = uri,
                                    workspace = workspace,
                                    isNsfw = isNsfw,
                                    boxPurpose = null,
                                    isCancelled = { false }
                                ) { progress ->
                                    _importProgress.value = (progress * 100).toInt()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
                _importProgress.value = 0
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }
}
