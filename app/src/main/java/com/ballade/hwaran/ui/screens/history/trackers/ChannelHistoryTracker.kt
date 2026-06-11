package com.ballade.hwaran.ui.screens.history.trackers

import com.ballade.hwaran.data.local.HistoryEventEntity
import com.ballade.hwaran.data.local.MangaEntity
import com.ballade.hwaran.ui.screens.history.models.parseDetails

object ChannelHistoryTracker {
    fun getChannelEvents(events: List<HistoryEventEntity>, allMangaMap: Map<Long, MangaEntity>): List<HistoryEventEntity> {
        return events.filter { it.eventType == "WATCH" }.filter { event ->
            val mangaId = parseDetails(event.details).mangaId
            val rootManga = mangaId?.let {
                val m = allMangaMap[it]
                if (m?.parentMangaId != null) allMangaMap[m.parentMangaId] else m
            }
            rootManga != null && rootManga.boxPurpose == "channel"
        }
    }

    fun calculateWatchCount(events: List<HistoryEventEntity>, allMangaMap: Map<Long, MangaEntity>): Int {
        return getChannelEvents(events, allMangaMap).size
    }

    fun getTimestamps(events: List<HistoryEventEntity>, allMangaMap: Map<Long, MangaEntity>): List<Long> {
        return getChannelEvents(events, allMangaMap).map { it.timestamp }
    }
}
