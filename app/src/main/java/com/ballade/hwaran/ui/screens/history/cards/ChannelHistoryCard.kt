package com.ballade.hwaran.ui.screens.history.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.data.local.MangaEntity
import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.ui.screens.history.models.HistoryTimelineItem
import com.ballade.hwaran.ui.screens.history.models.parseDetails
import com.ballade.hwaran.ui.screens.history.models.truncateMiddle
import com.ballade.hwaran.ui.screens.history.models.formatTimeOnly
import com.ballade.hwaran.ui.screens.history.models.formatDurationTime
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChannelHistoryCard(
    item: HistoryTimelineItem,
    allMangaMap: Map<Long, MangaEntity>,
    allChaptersMap: Map<Long, ChapterEntity>,
    sfwText: String,
    nsfwText: String,
    onNavigateToVideoPlayer: (Long) -> Unit
) {
    val rootManga = if (item.mangaId != null) allMangaMap[item.mangaId] else null
    val iconColor = Color(0xFF9F6496)
    val folderTitle = if (item.mangaId == null) "Videos" else (rootManga?.title ?: item.title)

    val chronologicallyOrdered = remember(item.occurrences) { item.occurrences.reversed() }

    val timeSpentText = remember(chronologicallyOrdered, rootManga) {
        var totalMs = 0L
        chronologicallyOrdered.forEach { occ ->
            val m = parseDetails(occ.details)
            m.chapterId?.let { chId ->
                totalMs += allChaptersMap[chId]?.duration ?: 0L
            }
        }
        if (totalMs > 0L) {
            val totalMins = totalMs / 1000 / 60
            if (totalMins > 0) "$totalMins mins" else "1 min"
        } else {
            val span = chronologicallyOrdered.last().timestamp - chronologicallyOrdered.first().timestamp
            if (span > 60000L) {
                "${span / 60000L} mins"
            } else {
                "${(chronologicallyOrdered.size * 3).coerceAtLeast(3)} mins"
            }
        }
    }

    val formattedDateFooter = remember(item.timestamp) {
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(item.timestamp))
        val dateFull = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(item.timestamp))
        "$day • $timeSpentText • $dateFull"
    }

    val hasValidOccurrences = remember(item.occurrences, allChaptersMap) {
        item.occurrences.any { occ ->
            val m = parseDetails(occ.details)
            m.chapterId != null && allChaptersMap.containsKey(m.chapterId)
        }
    }
    if (!hasValidOccurrences) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Movie, null, tint = iconColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "WATCHED VIDEOS",
                    color = iconColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = truncateMiddle(folderTitle),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (rootManga != null) {
                val sLabel = if (rootManga.isNsfw) nsfwText else sfwText
                val workspaceLabel = item.details.takeIf { it.isNotBlank() } ?: "I Love It"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "channel · $sLabel", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    // Workspace badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(iconColor.copy(alpha = 0.15f))
                            .border(0.5.dp, iconColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = workspaceLabel,
                            color = iconColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                val episodesByChapter = remember(chronologicallyOrdered) {
                    chronologicallyOrdered.groupBy { parseDetails(it.details).chapterId }
                        .toList()
                        .sortedByDescending { it.second.last().timestamp }
                }
                var visibleCount by remember(rootManga.id) { mutableIntStateOf(10) }
                val visibleEpisodes = episodesByChapter.take(visibleCount)

                visibleEpisodes.forEachIndexed { epIdx, (cId, epOccs) ->
                    if (cId == null) return@forEachIndexed
                    val chapter = allChaptersMap[cId] ?: return@forEachIndexed
                    val lastEpOcc = epOccs.last()
                    val isLastEp = epIdx == visibleEpisodes.size - 1 && visibleCount >= episodesByChapter.size
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onNavigateToVideoPlayer(cId) }
                            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isLastEp) "└── " else "├── ", color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(truncateMiddle(chapter.title), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val epPrefix = if (isLastEp) "    " else "│   "
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = epPrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text("Last Watched: ${formatTimeOnly(lastEpOcc.timestamp)}", color = Color.Gray, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = epPrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text("timeline : ${formatDurationTime(chapter.position.toLong())}/${formatDurationTime(chapter.duration)}", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
                if (visibleCount < episodesByChapter.size) {
                    TextButton(onClick = { visibleCount += 10 }, modifier = Modifier.padding(start = 16.dp)) {
                        Text("Load ${minOf(10, episodesByChapter.size - visibleCount)} more…", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formattedDateFooter,
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}
