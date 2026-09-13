package com.ballade.hwaran.frontend.history.models

import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.HistoryEventEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.frontend.description.video.SeriesRelationType
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

// Legacy item structure kept for compatibility with existing components
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

data class SeriesNode(
    val manga: MangaEntity,
    val unwatchedVideos: List<UnwatchedItem>,
    val children: List<SeriesNode>
)

fun buildSeriesTree(
    mangaList: List<MangaEntity>,
    unwatchedMap: Map<Long, List<UnwatchedItem>>
): List<SeriesNode> {
    val childrenMap = mangaList.groupBy { it.parentMangaId }

    fun buildNode(manga: MangaEntity): SeriesNode? {
        val directUnwatched = unwatchedMap[manga.id] ?: emptyList()
        val childNodes = childrenMap[manga.id]?.mapNotNull { buildNode(it) } ?: emptyList()

        if (directUnwatched.isNotEmpty() || childNodes.isNotEmpty()) {
            return SeriesNode(manga, directUnwatched, childNodes)
        }
        return null
    }

    val roots = mangaList.filter { it.parentMangaId == null }
    return roots.mapNotNull { buildNode(it) }
}

// ─────────────────────────────────────────────────────────────
// Rich Typed Media History Models
// ─────────────────────────────────────────────────────────────

data class HistoryEpisodeItem(
    val chapter: ChapterEntity,
    val lastTimestamp: Long,
    val occurrences: List<HistoryEventEntity>
)

data class SeriesBranchGroup(
    val branchManga: MangaEntity,
    val relationType: SeriesRelationType,
    val episodes: List<HistoryEpisodeItem>,
    val latestTimestamp: Long
)

data class SeriesHistoryGroup(
    val rootManga: MangaEntity,
    val directEpisodes: List<HistoryEpisodeItem>,
    val branches: List<SeriesBranchGroup>,
    val latestTimestamp: Long,
    val totalTimeSpentMs: Long
)

data class ChannelHistoryGroup(
    val channelManga: MangaEntity,
    val videos: List<HistoryEpisodeItem>,
    val latestTimestamp: Long,
    val totalTimeSpentMs: Long
)

data class HistoryToonChapterItem(
    val chapter: ChapterEntity,
    val lastPage: Long,
    val totalPages: Long,
    val lastTimestamp: Long
)

data class ToonHistoryGroup(
    val toonManga: MangaEntity,
    val chapters: List<HistoryToonChapterItem>,
    val latestTimestamp: Long
)

data class BookHistoryGroup(
    val bookManga: MangaEntity,
    val lastPage: Int,
    val totalPages: Int,
    val latestTimestamp: Long
)

data class MusicHistoryGroup(
    val playlistManga: MangaEntity,
    val tracks: List<HistoryEpisodeItem>,
    val latestTimestamp: Long
)

sealed class HistoryMediaItem(val id: Long, val timestamp: Long) {
    data class SeriesItem(val group: SeriesHistoryGroup) : HistoryMediaItem(group.rootManga.id, group.latestTimestamp)
    data class ChannelItem(val group: ChannelHistoryGroup) : HistoryMediaItem(group.channelManga.id, group.latestTimestamp)
    data class ToonItem(val group: ToonHistoryGroup) : HistoryMediaItem(group.toonManga.id, group.latestTimestamp)
    data class BookItem(val group: BookHistoryGroup) : HistoryMediaItem(group.bookManga.id, group.latestTimestamp)
    data class MusicItem(val group: MusicHistoryGroup) : HistoryMediaItem(group.playlistManga.id, group.latestTimestamp)
}

/**
 * Group raw database events into strongly typed media items.
 * Specifically groups series and any related content (seasons, movies, OVAs)
 * under the main root franchise series with tree branch structures.
 */
fun groupMediaHistory(
    events: List<HistoryEventEntity>,
    allMangaMap: Map<Long, MangaEntity>,
    allChaptersMap: Map<Long, ChapterEntity>
): List<HistoryMediaItem> {
    if (events.isEmpty()) return emptyList()

    val mediaEvents = events.filter {
        it.eventType in listOf("WATCH", "READ_TOON", "READ_BOOK", "LISTEN")
    }

    val result = mutableListOf<HistoryMediaItem>()

    // 1. Process Video Watches (Series vs Channel, with parent/child branch linking)
    val videoEvents = mediaEvents.filter { it.eventType == "WATCH" }
    if (videoEvents.isNotEmpty()) {
        // Group by mangaId from event details
        val eventsByMangaId = videoEvents.groupBy { parseDetails(it.details).mangaId }

        // Find root for series
        val seriesRootToMangaEvents = mutableMapOf<MangaEntity, MutableMap<MangaEntity, List<HistoryEventEntity>>>()
        val channelEvents = mutableMapOf<MangaEntity, List<HistoryEventEntity>>()

        eventsByMangaId.forEach { (mId, mEvents) ->
            if (mId == null) return@forEach
            val manga = allMangaMap[mId] ?: return@forEach

            if (manga.boxPurpose == "channel") {
                channelEvents[manga] = mEvents
            } else {
                // Series or franchise item
                val rootManga = if (manga.parentMangaId != null) {
                    allMangaMap[manga.parentMangaId] ?: manga
                } else {
                    manga
                }

                val subMap = seriesRootToMangaEvents.getOrPut(rootManga) { mutableMapOf() }
                subMap[manga] = mEvents
            }
        }

        // Build Series items with branches
        seriesRootToMangaEvents.forEach { (rootManga, childMap) ->
            var rootDirectEpisodes = emptyList<HistoryEpisodeItem>()
            val branchGroups = mutableListOf<SeriesBranchGroup>()
            var latestSeriesTimestamp = 0L
            var totalSeriesMs = 0L

            childMap.forEach { (mangaItem, mEvents) ->
                val epMap = mEvents.groupBy { parseDetails(it.details).chapterId }
                val episodeItems = epMap.mapNotNull { (chId, chOccs) ->
                    val chapter = chId?.let { allChaptersMap[it] } ?: return@mapNotNull null
                    val lastOcc = chOccs.maxByOrNull { it.timestamp } ?: chOccs.first()
                    totalSeriesMs += chapter.duration
                    HistoryEpisodeItem(
                        chapter = chapter,
                        lastTimestamp = lastOcc.timestamp,
                        occurrences = chOccs.sortedByDescending { it.timestamp }
                    )
                }.sortedByDescending { it.lastTimestamp }

                val itemLatestTs = episodeItems.maxOfOrNull { it.lastTimestamp } ?: 0L
                if (itemLatestTs > latestSeriesTimestamp) {
                    latestSeriesTimestamp = itemLatestTs
                }

                if (mangaItem.id == rootManga.id) {
                    rootDirectEpisodes = episodeItems
                } else {
                    val relationType = SeriesRelationType.fromPurpose(mangaItem.boxPurpose)
                    branchGroups.add(
                        SeriesBranchGroup(
                            branchManga = mangaItem,
                            relationType = relationType,
                            episodes = episodeItems,
                            latestTimestamp = itemLatestTs
                        )
                    )
                }
            }

            if (rootDirectEpisodes.isNotEmpty() || branchGroups.isNotEmpty()) {
                result.add(
                    HistoryMediaItem.SeriesItem(
                        SeriesHistoryGroup(
                            rootManga = rootManga,
                            directEpisodes = rootDirectEpisodes,
                            branches = branchGroups.sortedByDescending { it.latestTimestamp },
                            latestTimestamp = latestSeriesTimestamp,
                            totalTimeSpentMs = totalSeriesMs
                        )
                    )
                )
            }
        }

        // Build Channel items
        channelEvents.forEach { (channelManga, cEvents) ->
            val epMap = cEvents.groupBy { parseDetails(it.details).chapterId }
            var totalChannelMs = 0L
            val videoItems = epMap.mapNotNull { (chId, chOccs) ->
                val chapter = chId?.let { allChaptersMap[it] } ?: return@mapNotNull null
                val lastOcc = chOccs.maxByOrNull { it.timestamp } ?: chOccs.first()
                totalChannelMs += chapter.duration
                HistoryEpisodeItem(
                    chapter = chapter,
                    lastTimestamp = lastOcc.timestamp,
                    occurrences = chOccs.sortedByDescending { it.timestamp }
                )
            }.sortedByDescending { it.lastTimestamp }

            val latestTs = videoItems.maxOfOrNull { it.lastTimestamp } ?: cEvents.first().timestamp
            result.add(
                HistoryMediaItem.ChannelItem(
                    ChannelHistoryGroup(
                        channelManga = channelManga,
                        videos = videoItems,
                        latestTimestamp = latestTs,
                        totalTimeSpentMs = totalChannelMs
                    )
                )
            )
        }
    }

    // 2. Process Toons
    val toonEvents = mediaEvents.filter { it.eventType == "READ_TOON" }
    val toonsByManga = toonEvents.groupBy { parseDetails(it.details).mangaId }
    toonsByManga.forEach { (mId, mEvents) ->
        if (mId == null) return@forEach
        val toonManga = allMangaMap[mId] ?: return@forEach

        val chMap = mEvents.groupBy { parseDetails(it.details).chapterId }
        val chItems = chMap.mapNotNull { (chId, chOccs) ->
            val chapter = chId?.let { allChaptersMap[it] } ?: return@mapNotNull null
            val lastOcc = chOccs.maxByOrNull { it.timestamp } ?: chOccs.first()
            val meta = parseDetails(lastOcc.details)
            val lastPage = meta.pages?.split(",")?.lastOrNull()?.split("-")?.lastOrNull()?.toLongOrNull() ?: chapter.position.toLong()
            val totalPage = meta.totalPages?.toLong() ?: chapter.duration
            HistoryToonChapterItem(
                chapter = chapter,
                lastPage = lastPage,
                totalPages = totalPage,
                lastTimestamp = lastOcc.timestamp
            )
        }.sortedByDescending { it.lastTimestamp }

        val latestTs = chItems.maxOfOrNull { it.lastTimestamp } ?: mEvents.first().timestamp
        result.add(
            HistoryMediaItem.ToonItem(
                ToonHistoryGroup(
                    toonManga = toonManga,
                    chapters = chItems,
                    latestTimestamp = latestTs
                )
            )
        )
    }

    // 3. Process Books
    val bookEvents = mediaEvents.filter { it.eventType == "READ_BOOK" }
    val booksByManga = bookEvents.groupBy { parseDetails(it.details).mangaId }
    booksByManga.forEach { (mId, mEvents) ->
        if (mId == null) return@forEach
        val bookManga = allMangaMap[mId] ?: return@forEach

        val lastOcc = mEvents.maxByOrNull { it.timestamp } ?: mEvents.first()
        val meta = parseDetails(lastOcc.details)
        val lastPage = meta.pages?.split(",")?.lastOrNull()?.split("-")?.lastOrNull()?.toIntOrNull()
            ?: bookManga.lastReadPage ?: 1
        val totalPages = meta.totalPages ?: 0

        result.add(
            HistoryMediaItem.BookItem(
                BookHistoryGroup(
                    bookManga = bookManga,
                    lastPage = lastPage,
                    totalPages = totalPages,
                    latestTimestamp = lastOcc.timestamp
                )
            )
        )
    }

    // 4. Process Music
    val musicEvents = mediaEvents.filter { it.eventType == "LISTEN" }
    val musicByPlaylist = musicEvents.groupBy { parseDetails(it.details).mangaId }
    musicByPlaylist.forEach { (mId, mEvents) ->
        if (mId == null) return@forEach
        val playlistManga = allMangaMap[mId] ?: return@forEach

        val trackMap = mEvents.groupBy { parseDetails(it.details).chapterId }
        val trackItems = trackMap.mapNotNull { (chId, chOccs) ->
            val chapter = chId?.let { allChaptersMap[it] } ?: return@mapNotNull null
            val lastOcc = chOccs.maxByOrNull { it.timestamp } ?: chOccs.first()
            HistoryEpisodeItem(
                chapter = chapter,
                lastTimestamp = lastOcc.timestamp,
                occurrences = chOccs.sortedByDescending { it.timestamp }
            )
        }.sortedByDescending { it.lastTimestamp }

        val latestTs = trackItems.maxOfOrNull { it.lastTimestamp } ?: mEvents.first().timestamp
        result.add(
            HistoryMediaItem.MusicItem(
                MusicHistoryGroup(
                    playlistManga = playlistManga,
                    tracks = trackItems,
                    latestTimestamp = latestTs
                )
            )
        )
    }

    // Return in reverse chronological order
    return result.sortedByDescending { it.timestamp }
}

// ─────────────────────────────────────────────────────────────
// Formatters & Utilities
// ─────────────────────────────────────────────────────────────

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
