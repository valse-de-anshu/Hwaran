package com.ballade.hwaran.ui.screens.history.models

import com.ballade.hwaran.data.local.HistoryEventEntity
import com.ballade.hwaran.data.local.MangaEntity
import com.ballade.hwaran.data.local.ChapterEntity
import java.text.SimpleDateFormat
import java.util.*

data class EventMetadata(
    val mangaId: Long? = null,
    val chapterId: Long? = null,
    val pages: String? = null,
    val totalPages: Int? = null,
    val fallback: String = ""
)

fun parseDetails(details: String): EventMetadata {
    val parts = details.split("|").map { it.trim() }
    var mangaId: Long? = null
    var chapterId: Long? = null
    var pages: String? = null
    var totalPages: Int? = null
    var fallback = ""
    for (part in parts) {
        if (part.startsWith("mangaId:")) {
            mangaId = part.substringAfter("mangaId:").toLongOrNull()
        } else if (part.startsWith("chapterId:")) {
            chapterId = part.substringAfter("chapterId:").toLongOrNull()
        } else if (part.startsWith("pages:")) {
            pages = part.substringAfter("pages:")
        } else if (part.startsWith("totalPages:")) {
            totalPages = part.substringAfter("totalPages:").toIntOrNull()
        } else if (part.startsWith("fallback:")) {
            fallback = part.substringAfter("fallback:")
        } else {
            if (fallback.isEmpty()) {
                fallback = part
            } else {
                fallback += " | $part"
            }
        }
    }
    return EventMetadata(mangaId, chapterId, pages, totalPages, fallback)
}

data class HistoryTimelineItem(
    val id: Long,
    val eventType: String,
    val mangaId: Long? = null,
    val title: String,
    val details: String,
    val timestamp: Long,
    val isGroupedLifecycle: Boolean = false,
    val isGroupedMedia: Boolean = false,
    val occurrences: List<HistoryEventEntity> = emptyList()
)

data class UnwatchedItem(
    val mangaId: Long,
    val chapterId: Long?,
    val title: String,
    val parentTitle: String,
    val contentType: Int
)

data class UnplayedPlaylist(
    val mangaId: Long,
    val title: String,
    val unplayedSongs: List<ChapterEntity>
)


data class CollapsedBranchItem(
    val label: String,
    val timestamp: Long,
    val count: Int,
    val occ: HistoryEventEntity
)

fun collapseConsecutiveBranches(
    events: List<HistoryEventEntity>, 
    contentType: Int?
): List<CollapsedBranchItem> {
    if (events.isEmpty()) return emptyList()
    
    val map = linkedMapOf<String, CollapsedBranchItem>()
    
    for (occ in events) {
        val meta = parseDetails(occ.details)
        if (contentType == 1) {
            val pagesAttr = meta.pages
            if (pagesAttr != null) {
                val tokens = pagesAttr.split(",")
                for (token in tokens) {
                    val trimmed = token.trim()
                    if (trimmed.isEmpty()) continue
                    
                    val pageLabels = if (trimmed.contains("-")) {
                        val parts = trimmed.split("-")
                        val start = parts.getOrNull(0)?.toIntOrNull()
                        val end = parts.getOrNull(1)?.toIntOrNull()
                        if (start != null && end != null && start <= end) {
                            (start..end).map { "Page $it" }
                        } else {
                            listOf("Page $trimmed")
                        }
                    } else {
                        listOf("Page $trimmed")
                    }
                    
                    for (label in pageLabels) {
                        val existing = map[label]
                        if (existing != null) {
                            map[label] = existing.copy(
                                count = existing.count + 1,
                                timestamp = occ.timestamp,
                                occ = occ
                            )
                        } else {
                            map[label] = CollapsedBranchItem(
                                label = label,
                                timestamp = occ.timestamp,
                                count = 1,
                                occ = occ
                            )
                        }
                    }
                }
            } else {
                val label = "Page read"
                val existing = map[label]
                if (existing != null) {
                    map[label] = existing.copy(
                        count = existing.count + 1,
                        timestamp = occ.timestamp,
                        occ = occ
                    )
                } else {
                    map[label] = CollapsedBranchItem(
                        label = label,
                        timestamp = occ.timestamp,
                        count = 1,
                        occ = occ
                    )
                }
            }
        } else {
            val label = occ.itemName
            val existing = map[label]
            if (existing != null) {
                map[label] = existing.copy(
                    count = existing.count + 1,
                    timestamp = occ.timestamp,
                    occ = occ
                )
            } else {
                map[label] = CollapsedBranchItem(
                    label = label,
                    timestamp = occ.timestamp,
                    count = 1,
                    occ = occ
                )
            }
        }
    }
    return map.values.toList()
}

fun groupHistoryEvents(events: List<HistoryEventEntity>, allMangaMap: Map<Long, MangaEntity>): List<HistoryTimelineItem> {
    val filtered = events.filter { it.eventType != "APP_OPEN" && it.eventType != "APP_CLOSE" }
    if (filtered.isEmpty()) return emptyList()
    
    val imports = filtered.filter { it.eventType == "IMPORT" }
    val deletes = filtered.filter { it.eventType == "DELETE" }
    val books = filtered.filter { it.eventType == "READ_BOOK" }
    val videos = filtered.filter { it.eventType == "WATCH" }
    val toons = filtered.filter { it.eventType == "READ_TOON" }
    val music = filtered.filter { it.eventType == "LISTEN" }
    
    val groupedList = mutableListOf<HistoryTimelineItem>()
    
    if (imports.isNotEmpty()) {
        groupedList.add(
            HistoryTimelineItem(
                id = -300L,
                eventType = "IMPORT",
                title = "Imported Folder",
                details = "",
                timestamp = imports.first().timestamp,
                isGroupedLifecycle = true,
                occurrences = imports
            )
        )
    }
    if (deletes.isNotEmpty()) {
        groupedList.add(
            HistoryTimelineItem(
                id = -400L,
                eventType = "DELETE",
                title = "Deleted Item",
                details = "",
                timestamp = deletes.first().timestamp,
                isGroupedLifecycle = true,
                occurrences = deletes
            )
        )
    }
    if (music.isNotEmpty()) {
        groupedList.add(
            HistoryTimelineItem(
                id = -800L,
                eventType = "LISTEN",
                title = "Music",
                details = "",
                timestamp = music.first().timestamp,
                isGroupedMedia = true,
                occurrences = music
            )
        )
    }
    
    fun getWorkspace(detailsStr: String): String {
        val mangaId = parseDetails(detailsStr).mangaId
        val rootManga = mangaId?.let {
            val m = allMangaMap[it]
            if (m?.parentMangaId != null) allMangaMap[m.parentMangaId] else m
        }
        return rootManga?.workspace?.takeIf { it.isNotEmpty() } ?: "I Love It"
    }
    
    fun getRootMangaId(detailsStr: String): Long? {
        val mangaId = parseDetails(detailsStr).mangaId ?: return null
        val m = allMangaMap[mangaId] ?: return mangaId
        return if (m.parentMangaId != null) m.parentMangaId else m.id
    }

    if (books.isNotEmpty()) {
        val grouped = books.groupBy { getWorkspace(it.details) }
        grouped.forEach { (workspace, occs) ->
            groupedList.add(
                HistoryTimelineItem(
                    id = -500L - workspace.hashCode(),
                    eventType = "READ_BOOK",
                    title = "Books",
                    details = workspace,
                    timestamp = occs.first().timestamp,
                    isGroupedMedia = true,
                    occurrences = occs
                )
            )
        }
    }
    if (videos.isNotEmpty()) {
        val groupedByWorkspace = videos.groupBy { getWorkspace(it.details) }
        groupedByWorkspace.forEach { (workspace, workspaceOccs) ->
            val groupedByRoot = workspaceOccs.groupBy { getRootMangaId(it.details) }
            groupedByRoot.forEach { (rootId, occs) ->
                val rootManga = rootId?.let { allMangaMap[it] }
                if (rootManga == null || rootManga.boxPurpose != "series") {
                    groupedList.add(
                        HistoryTimelineItem(
                            id = -600L - workspace.hashCode() - (rootId ?: 0L),
                            eventType = "WATCH",
                            mangaId = rootId,
                            title = rootManga?.title ?: "Videos",
                            details = workspace,
                            timestamp = occs.first().timestamp,
                            isGroupedMedia = true,
                            occurrences = occs
                        )
                    )
                }
            }
        }
    }
    if (toons.isNotEmpty()) {
        val grouped = toons.groupBy { getWorkspace(it.details) }
        grouped.forEach { (workspace, occs) ->
            groupedList.add(
                HistoryTimelineItem(
                    id = -700L - workspace.hashCode(),
                    eventType = "READ_TOON",
                    title = "Toons",
                    details = workspace,
                    timestamp = occs.first().timestamp,
                    isGroupedMedia = true,
                    occurrences = occs
                )
            )
        }
    }
    
    val otherEvents = filtered.filter { it.eventType !in listOf("IMPORT", "DELETE", "READ_BOOK", "WATCH", "READ_TOON", "LISTEN") }
    
    var i = 0
    while (i < otherEvents.size) {
        val current = otherEvents[i]
        groupedList.add(
            HistoryTimelineItem(
                id = current.id,
                eventType = current.eventType,
                title = current.itemName,
                details = current.details,
                timestamp = current.timestamp,
                occurrences = listOf(current)
            )
        )
        i++
    }
    
    fun getCategoryPriority(eventType: String): Int {
        return when (eventType) {
            "WATCH" -> 1
            "LISTEN" -> 2
            "READ_TOON" -> 3
            "READ_BOOK" -> 4
            "IMPORT" -> 5
            "DELETE" -> 6
            else -> 7
        }
    }
    
    return groupedList.sortedWith(
        compareBy<HistoryTimelineItem> { getCategoryPriority(it.eventType) }
            .thenByDescending { it.timestamp }
    )
}

fun formatShortDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDateOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatTimeOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDurationTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

fun truncateMiddle(text: String, maxLength: Int = 28): String {
    if (text.length <= maxLength) return text
    
    val words = text.split(" ")
    if (words.size < 2) {
        val firstPart = text.take(maxLength / 2 - 2)
        val lastPart = text.takeLast(maxLength / 2 - 3)
        return "$firstPart.....$lastPart"
    }
    
    val lastWord = words.last()
    var firstPart = ""
    for (i in 0 until words.size - 1) {
        val next = if (firstPart.isEmpty()) words[i] else "$firstPart ${words[i]}"
        if (next.length + 5 + lastWord.length + 1 <= maxLength) {
            firstPart = next
        } else {
            break
        }
    }
    
    if (firstPart.isEmpty()) {
        val first = text.take(10)
        val last = text.takeLast(8)
        return "$first ..... $last"
    }
    
    return "$firstPart ..... $lastWord"
}
