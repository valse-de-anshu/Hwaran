package com.ballade.hwaran.frontend.history.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.frontend.history.models.HistoryTimelineItem
import com.ballade.hwaran.frontend.history.models.parseDetails
import com.ballade.hwaran.frontend.history.models.truncateMiddle

@Composable
fun MusicHistoryCard(
    item: HistoryTimelineItem,
    allMangaMap: Map<Long, MangaEntity>,
    allChaptersMap: Map<Long, ChapterEntity>,
    onNavigateToPlaylist: (Long) -> Unit
) {
    val iconColor = Color(0xFFD391B0)
    val folderTitle = "Music"

    val chronologicallyOrdered = remember(item.occurrences) { item.occurrences.reversed() }

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
                Icon(Icons.Rounded.MusicNote, null, tint = iconColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "PLAYED TRACKS",
                    color = iconColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = folderTitle,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            val tracksByAlbum = remember(chronologicallyOrdered) {
                chronologicallyOrdered.groupBy { parseDetails(it.details).mangaId }
                    .toList()
                    .sortedByDescending { it.second.last().timestamp }
            }

            tracksByAlbum.forEachIndexed { aIdx, (mId, occs) ->
                if (mId == null) return@forEachIndexed
                val albumManga = allMangaMap[mId] ?: return@forEachIndexed
                val isLastAlbum = aIdx == tracksByAlbum.size - 1

                val tracksByChapter = remember(occs) {
                    occs.groupBy { parseDetails(it.details).chapterId }
                        .toList()
                        .sortedByDescending { it.second.last().timestamp }
                }

                var visibleCount by remember(mId) { mutableIntStateOf(10) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isLastAlbum) "└── " else "├── ",
                            color = Color.White.copy(alpha = 0.15f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = truncateMiddle(albumManga.title),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    val albumLinePrefix = if (isLastAlbum) "    " else "│   "
                    val displayList = tracksByChapter.take(visibleCount)
                    
                    displayList.forEachIndexed { tIdx, (cId, _) ->
                        if (cId == null) return@forEachIndexed
                        val chapter = allChaptersMap[cId] ?: return@forEachIndexed
                        val isLastTrack = tIdx == displayList.size - 1 && visibleCount >= tracksByChapter.size
                        
                        val trackLinePrefix = albumLinePrefix + if (isLastTrack) "└── " else "├── "
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onNavigateToPlaylist(albumManga.id) }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = trackLinePrefix,
                                color = Color.White.copy(alpha = 0.15f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = truncateMiddle(chapter.title),
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    if (tracksByChapter.size > visibleCount) {
                        val remaining = tracksByChapter.size - visibleCount
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { visibleCount += 10 }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = albumLinePrefix + "│   ",
                                color = Color.Transparent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = "Expand",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp).offset(x = (-4).dp)
                            )
                            Text(
                                text = "$remaining+",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
