package com.ballade.hwaran.backend.video.series

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.HistoryTracker
import com.ballade.hwaran.data.importer.video.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoSeriesBackend(private val application: Application) {
    private val database = AppDatabase.getDatabase(application)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val videoRepository = VideoImportRepository(database.libraryDao())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(0)
    val importProgress: StateFlow<Int> = _importProgress.asStateFlow()

    fun getSeriesFlow(workspace: String?, isNsfw: Boolean): Flow<List<MangaEntity>> {
        return database.mediaDao().getAllManga().map { list ->
            list.filter {
                it.contentType == 2 &&
                (it.boxPurpose == "series" || it.boxPurpose == null) &&
                it.isNsfw == isNsfw &&
                it.workspace == workspace
            }
        }
    }

    suspend fun getSeriesById(id: Long): MangaEntity? {
        return withContext(Dispatchers.IO) {
            database.mediaDao().getMangaById(id)
        }
    }

    fun getSeasonsFlow(seriesId: Long): Flow<List<MangaEntity>> {
        return database.mediaDao().getChildrenForManga(seriesId)
    }

    fun getEpisodesFlow(seasonOrSeriesId: Long): Flow<List<ChapterEntity>> {
        return database.trackDao().getChaptersForManga(seasonOrSeriesId).map { list ->
            list.sortedWith(compareBy<ChapterEntity> {
                Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloat() ?: Float.MAX_VALUE
            }.thenBy {
                it.title.replace(Regex("\\d+")) { matchResult ->
                    matchResult.value.padStart(10, '0')
                }
            })
        }
    }

    fun updatePlaybackProgress(seriesId: Long, episodeTitle: String, positionMs: Long) {
        scope.launch {
            val series = database.mediaDao().getMangaById(seriesId) ?: return@launch
            val now = System.currentTimeMillis()
            database.mediaDao().insertManga(series.copy(lastReadTitle = episodeTitle, openCount = series.openCount + 1, lastModified = now))
            if (series.parentMangaId != null) {
                database.mediaDao().getMangaById(series.parentMangaId)?.let { parent ->
                    database.mediaDao().insertManga(parent.copy(lastReadTitle = episodeTitle, openCount = parent.openCount + 1, lastModified = now))
                }
            }
            HistoryTracker.logEvent("PLAY_VIDEO", episodeTitle, "mangaId:$seriesId|seriesId:$seriesId|pos:$positionMs")
        }
    }

    fun deleteSeries(seriesId: Long) {
        scope.launch {
            val series = database.mediaDao().getMangaById(seriesId) ?: return@launch
            val children = database.mediaDao().getChildrenForMangaList(seriesId)
            children.forEach { child ->
                database.trackDao().deleteChaptersByMangaId(child.id)
            }
            database.mediaDao().deleteChildrenByParentId(seriesId)
            database.trackDao().deleteChaptersByMangaId(seriesId)
            database.mediaDao().deleteManga(series)
            HistoryTracker.logEvent("DELETE", series.title, "Series Deleted")
        }
    }

    fun importSeries(uri: Uri, isLocalMode: Boolean, isNsfw: Boolean, workspace: String?, onComplete: () -> Unit = {}) {
        scope.launch {
            _isImporting.value = true
            _importProgress.value = 0
            try {
                val doc = DocumentFile.fromTreeUri(application, uri)
                if (doc != null) {
                    if (isLocalMode) {
                        VideoLocalMegaImport.execute(
                            context = application,
                            repository = videoRepository,
                            parentUri = uri,
                            workspace = workspace,
                            isNsfw = isNsfw,
                            boxPurpose = "series",
                            isCancelled = { false }
                        ) { progress ->
                            _importProgress.value = (progress * 100).toInt()
                        }
                    } else {
                        VideoExternalMegaImport.execute(
                            context = application,
                            repository = videoRepository,
                            parentUri = uri,
                            workspace = workspace,
                            isNsfw = isNsfw,
                            boxPurpose = "series",
                            isCancelled = { false }
                        ) { progress ->
                            _importProgress.value = (progress * 100).toInt()
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
