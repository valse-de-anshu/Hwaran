package com.ballade.hwaran.backend.video.channel

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

class VideoChannelBackend(private val application: Application) {
    private val database = AppDatabase.getDatabase(application)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val videoRepository = VideoImportRepository(database.libraryDao())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun getChannelsFlow(workspace: String?, isNsfw: Boolean): Flow<List<MangaEntity>> {
        return database.mediaDao().getAllManga().map { list ->
            list.filter {
                it.contentType == 2 &&
                it.boxPurpose == "channel" &&
                it.isNsfw == isNsfw &&
                it.workspace == workspace
            }
        }
    }

    suspend fun getChannelById(id: Long): MangaEntity? {
        return withContext(Dispatchers.IO) {
            database.mediaDao().getMangaById(id)
        }
    }

    fun getChannelVideosFlow(channelId: Long): Flow<List<ChapterEntity>> {
        return database.trackDao().getChaptersForManga(channelId).map { list ->
            list.sortedBy { it.position }
        }
    }

    fun addVideoToChannel(channelId: Long, title: String, uriString: String, duration: Long = 0L) {
        scope.launch {
            val existingVideos = database.trackDao().getChaptersForMangaList(channelId)
            val newPosition = existingVideos.size
            database.trackDao().insertChapter(
                ChapterEntity(
                    mangaId = channelId,
                    title = title,
                    folderUri = uriString,
                    duration = duration,
                    position = newPosition
                )
            )
            HistoryTracker.logEvent("IMPORT", title, "Added to channel ID:$channelId")
        }
    }

    fun deleteVideo(videoId: Long) {
        scope.launch {
            val video = database.trackDao().getChapterById(videoId) ?: return@launch
            database.trackDao().deleteChapter(video)
            HistoryTracker.logEvent("DELETE", video.title, "Video Deleted from Channel")
        }
    }

    fun deleteChannel(channelId: Long) {
        scope.launch {
            val channel = database.mediaDao().getMangaById(channelId) ?: return@launch
            database.trackDao().deleteChaptersByMangaId(channelId)
            database.mediaDao().deleteManga(channel)
            HistoryTracker.logEvent("DELETE", channel.title, "Channel Deleted")
        }
    }
}
