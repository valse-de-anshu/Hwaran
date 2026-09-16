package com.ballade.hwaran.backend.history

import android.app.Application
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.HistoryEventEntity
import com.ballade.hwaran.core.util.HistoryTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HistoryBackend(private val application: Application) {
    private val database = AppDatabase.getDatabase(application)
    private val scope = CoroutineScope(Dispatchers.IO)

    val allHistoryFlow: Flow<List<HistoryEventEntity>> = database.historyDao().getAllHistoryEventsFlow()

    val videoHistoryFlow: Flow<List<HistoryEventEntity>> = allHistoryFlow.map { events ->
        events.filter { it.eventType == "WATCH" || it.eventType == "PLAY_VIDEO" }
    }

    val toonHistoryFlow: Flow<List<HistoryEventEntity>> = allHistoryFlow.map { events ->
        events.filter { it.eventType == "READ_TOON" || it.eventType == "READ_MANGA" }
    }

    val bookHistoryFlow: Flow<List<HistoryEventEntity>> = allHistoryFlow.map { events ->
        events.filter { it.eventType == "READ_BOOK" || it.eventType == "READ_NOVEL" }
    }

    val musicHistoryFlow: Flow<List<HistoryEventEntity>> = allHistoryFlow.map { events ->
        events.filter { it.eventType == "LISTEN" }
    }

    fun logVideoWatch(title: String, mangaId: Long, chapterId: Long) {
        HistoryTracker.logEvent("WATCH", title, "mangaId:$mangaId|chapterId:$chapterId")
    }

    fun logToonRead(title: String, mangaId: Long, chapterId: Long, page: Long, totalPages: Long) {
        HistoryTracker.logEvent("READ_TOON", title, "mangaId:$mangaId|chapterId:$chapterId|pages:$page|totalPages:$totalPages")
    }

    fun logBookRead(title: String, mangaId: Long, page: Int, totalPages: Int) {
        HistoryTracker.logEvent("READ_BOOK", title, "mangaId:$mangaId|pages:$page|totalPages:$totalPages")
    }

    fun logMusicPlay(title: String, mangaId: Long, chapterId: Long) {
        HistoryTracker.logEvent("LISTEN", title, "mangaId:$mangaId|chapterId:$chapterId")
    }

    fun clearAllHistory() {
        scope.launch {
            database.historyDao().clearAllHistoryEvents()
        }
    }
}
