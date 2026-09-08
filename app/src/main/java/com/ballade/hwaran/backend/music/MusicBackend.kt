package com.ballade.hwaran.backend.music

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.exoplayer.ExoPlayer
import com.ballade.hwaran.audio.HwaranPlayerHolder
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.HistoryTracker
import com.ballade.hwaran.data.importer.music.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicBackend(private val application: Application) {
    private val database = AppDatabase.getDatabase(application)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val musicRepository = MusicImportRepository(database.libraryDao())

    val exoPlayer: ExoPlayer
        get() = HwaranPlayerHolder.getOrCreate(application)

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(0)
    val importProgress: StateFlow<Int> = _importProgress.asStateFlow()

    fun getPlaylistsFlow(): Flow<List<MangaEntity>> {
        return database.mediaDao().getAllManga().map { list ->
            list.filter { it.contentType == 3 }
        }
    }

    suspend fun getPlaylistById(id: Long): MangaEntity? {
        return withContext(Dispatchers.IO) {
            database.mediaDao().getMangaById(id)
        }
    }

    fun getTracksFlow(playlistId: Long): Flow<List<ChapterEntity>> {
        return database.trackDao().getChaptersForManga(playlistId).map { list ->
            list.sortedBy { it.position }
        }
    }

    fun updateTrackMetadata(trackId: Long, title: String, artist: String?, lyrics: String?, genre: String?) {
        scope.launch {
            val track = database.trackDao().getChapterById(trackId) ?: return@launch
            database.trackDao().insertChapter(
                track.copy(
                    title = title,
                    artist = artist ?: track.artist,
                    lyrics = lyrics ?: track.lyrics,
                    genre = genre ?: track.genre
                )
            )
            HistoryTracker.logEvent("EDIT", title, "Track Metadata Updated")
        }
    }

    fun deletePlaylist(playlistId: Long) {
        scope.launch {
            val playlist = database.mediaDao().getMangaById(playlistId) ?: return@launch
            database.trackDao().deleteChaptersByMangaId(playlistId)
            database.mediaDao().deleteManga(playlist)
            HistoryTracker.logEvent("DELETE", playlist.title, "Playlist Deleted")
        }
    }

    fun deleteTrack(trackId: Long) {
        scope.launch {
            val track = database.trackDao().getChapterById(trackId) ?: return@launch
            database.trackDao().deleteChapter(track)
            HistoryTracker.logEvent("DELETE", track.title, "Track Removed")
        }
    }

    fun importMusic(uri: Uri, workspace: String?, onComplete: () -> Unit = {}) {
        scope.launch {
            _isImporting.value = true
            _importProgress.value = 0
            try {
                val doc = DocumentFile.fromTreeUri(application, uri)
                if (doc != null) {
                    val structure = MusicImportUtils.detectStructure(doc)
                    if (structure == "SINGLE") {
                        MusicExternalSingleImport.execute(
                            context = application,
                            repository = musicRepository,
                            folderDoc = doc,
                            workspace = workspace,
                            isCancelled = { false },
                            onProgress = { progress -> _importProgress.value = progress }
                        )
                    } else {
                        MusicExternalMegaImport.execute(
                            context = application,
                            repository = musicRepository,
                            parentDoc = doc,
                            workspace = workspace,
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
