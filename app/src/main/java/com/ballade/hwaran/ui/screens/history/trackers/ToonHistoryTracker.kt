package com.ballade.hwaran.ui.screens.history.trackers

import com.ballade.hwaran.data.local.HistoryEventEntity
import com.ballade.hwaran.data.local.MangaEntity
import com.ballade.hwaran.ui.screens.history.models.parseDetails

object ToonHistoryTracker {
    fun calculateUniqueToonsCount(events: List<HistoryEventEntity>): Int {
        return events.filter { it.eventType == "READ_TOON" }
            .mapNotNull { parseDetails(it.details).mangaId }
            .distinct()
            .size
    }

    fun calculateTotalToonChaptersRead(events: List<HistoryEventEntity>): Int {
        return events.count { it.eventType == "READ_TOON" }
    }

    fun getLatestToonReadTimestamp(events: List<HistoryEventEntity>): Long? {
        return events.filter { it.eventType == "READ_TOON" }
            .maxOfOrNull { it.timestamp }
    }
}
