package com.ballade.hwaran.ui.screens.history.trackers

import com.ballade.hwaran.data.local.HistoryEventEntity
import com.ballade.hwaran.ui.screens.history.models.parseDetails

object BookHistoryTracker {
    fun calculateUniqueBooksRead(events: List<HistoryEventEntity>): Int {
        return events.filter { it.eventType == "READ_BOOK" }
            .mapNotNull { parseDetails(it.details).mangaId }
            .distinct()
            .size
    }

    fun calculateTotalBookOpenings(events: List<HistoryEventEntity>): Int {
        return events.count { it.eventType == "READ_BOOK" }
    }

    fun getLatestBookReadTimestamp(events: List<HistoryEventEntity>): Long? {
        return events.filter { it.eventType == "READ_BOOK" }
            .maxOfOrNull { it.timestamp }
    }
}
