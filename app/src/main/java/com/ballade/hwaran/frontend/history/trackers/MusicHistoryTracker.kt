package com.ballade.hwaran.frontend.history.trackers

import com.ballade.hwaran.core.database.entity.HistoryEventEntity

object MusicHistoryTracker {
    fun calculateTotalPlaybacks(events: List<HistoryEventEntity>): Int {
        return events.count { it.eventType == "LISTEN" }
    }

    fun getLatestPlayTimestamp(events: List<HistoryEventEntity>): Long? {
        return events.filter { it.eventType == "LISTEN" }
            .maxOfOrNull { it.timestamp }
    }
}
