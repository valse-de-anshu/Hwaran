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

    val toonHistoryFlow: Flow<List<HistoryEventEntity>> = allHistoryFlow.map { events ->
        events.filter { it.eventType == "READ_TOON" }
    }

    val bookHistoryFlow: Flow<List<HistoryEventEntity>> = allHistoryFlow.map { events ->
        events.filter { it.eventType == "READ_BOOK" }
    }

    val videoHistoryFlow: Flow<List<HistoryEventEntity>> = allHistoryFlow.map { events ->
        events.filter { it.eventType == "PLAY_VIDEO" }
    }

    val musicHistoryFlow: Flow<List<HistoryEventEntity>> = allHistoryFlow.map { events ->
        events.filter { it.eventType == "PLAY_MUSIC" }
    }

    fun clearAllHistory() {
        scope.launch {
            database.historyDao().clearAllHistoryEvents()
            HistoryTracker.logEvent("DELETE", "Clear History", "All history cleared")
        }
    }

    fun logEvent(eventType: String, itemName: String, details: String) {
        HistoryTracker.logEvent(eventType, itemName, details)
    }
}
