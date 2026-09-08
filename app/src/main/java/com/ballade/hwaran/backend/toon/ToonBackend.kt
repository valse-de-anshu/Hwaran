package com.ballade.hwaran.backend.toon

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.HistoryTracker
import com.ballade.hwaran.data.importer.toon.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ToonBackend(private val application: Application) {
    private val database = AppDatabase.getDatabase(application)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val toonRepository = ToonImportRepository(database.libraryDao())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(0)
    val importProgress: StateFlow<Int> = _importProgress.asStateFlow()

    fun getToonsFlow(workspace: String?, isNsfw: Boolean): Flow<List<MangaEntity>> {
        return database.mediaDao().getAllManga().map { list ->
            list.filter {
                it.contentType == 0 &&
                it.isNsfw == isNsfw &&
                it.workspace == workspace
            }
        }
    }

    suspend fun getToonById(id: Long): MangaEntity? {
        return withContext(Dispatchers.IO) {
            database.mediaDao().getMangaById(id)
        }
    }

    fun getChaptersFlow(mangaId: Long): Flow<List<ChapterEntity>> {
        return database.trackDao().getChaptersForManga(mangaId).map { list ->
            list.sortedWith(compareBy<ChapterEntity> {
                Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloat() ?: Float.MAX_VALUE
            }.thenBy {
                it.title.replace(Regex("\\d+")) { matchResult ->
                    matchResult.value.padStart(10, '0')
                }
            })
        }
    }

    suspend fun getChapterById(chapterId: Long): ChapterEntity? {
        return withContext(Dispatchers.IO) {
            database.trackDao().getChapterById(chapterId)
        }
    }

    fun updateReadingProgress(mangaId: Long, chapterTitle: String, page: Int) {
        scope.launch {
            val manga = database.mediaDao().getMangaById(mangaId) ?: return@launch
            database.mediaDao().insertManga(manga.copy(lastReadTitle = chapterTitle, lastReadPage = page, openCount = manga.openCount + 1))
            HistoryTracker.logEvent("READ_TOON", chapterTitle, "mangaId:$mangaId|page:$page")
        }
    }

    fun updateToonMetadata(mangaId: Long, title: String, thoughts: String?, coverPath: String?) {
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

    fun deleteToon(mangaId: Long) {
        scope.launch {
            val manga = database.mediaDao().getMangaById(mangaId) ?: return@launch
            database.trackDao().deleteChaptersByMangaId(mangaId)
            database.mediaDao().deleteManga(manga)
            HistoryTracker.logEvent("DELETE", manga.title, "Toon Deleted")
        }
    }

    fun importToon(uri: Uri, isLocalMode: Boolean, isNsfw: Boolean, workspace: String?, onComplete: () -> Unit = {}) {
        scope.launch {
            _isImporting.value = true
            _importProgress.value = 0
            try {
                val doc = DocumentFile.fromTreeUri(application, uri)
                if (doc != null) {
                    val structure = ToonImportUtils.detectStructure(doc)
                    if (isLocalMode) {
                        if (structure == "SINGLE") {
                            ToonLocalSingleImport.execute(
                                context = application,
                                repository = toonRepository,
                                uri = uri,
                                workspace = workspace,
                                isNsfw = isNsfw,
                                boxPurpose = null,
                                isCancelled = { false },
                                onProgress = { progress -> _importProgress.value = progress }
                            )
                        } else {
                            ToonLocalMegaImport.execute(
                                context = application,
                                repository = toonRepository,
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
                            ToonExternalSingleImport.execute(
                                context = application,
                                repository = toonRepository,
                                uri = uri,
                                workspace = workspace,
                                isNsfw = isNsfw,
                                boxPurpose = null,
                                isCancelled = { false },
                                onProgress = { progress -> _importProgress.value = progress }
                            )
                        } else {
                            ToonExternalMegaImport.execute(
                                context = application,
                                repository = toonRepository,
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
