package com.ballade.hwaran.frontend.history.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ImportContacts
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
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.frontend.history.models.HistoryTimelineItem
import com.ballade.hwaran.frontend.history.models.parseDetails
import com.ballade.hwaran.frontend.history.models.truncateMiddle
import com.ballade.hwaran.frontend.history.models.formatTimeOnly
import com.ballade.hwaran.frontend.history.models.formatDateOnly
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ToonHistoryCard(
    item: HistoryTimelineItem,
    allMangaMap: Map<Long, MangaEntity>,
    allChaptersMap: Map<Long, ChapterEntity>,
    sfwText: String,
    nsfwText: String,
    onNavigateToReader: (Long) -> Unit
) {
    val manga = if (item.mangaId != null) allMangaMap[item.mangaId] else null
    val iconColor = Color(0xFF5D3C64)
    val folderTitle = if (item.mangaId == null) "Toons" else (manga?.title ?: item.title)

    val chronologicallyOrdered = remember(item.occurrences) { item.occurrences.reversed() }

    val timeSpentText = remember(chronologicallyOrdered) {
        val span = chronologicallyOrdered.last().timestamp - chronologicallyOrdered.first().timestamp
        if (span > 60000L) {
            "${span / 60000L} mins"
        } else {
            "${(chronologicallyOrdered.size * 3).coerceAtLeast(3)} mins"
        }
    }

    val formattedDateFooter = remember(item.timestamp) {
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(item.timestamp))
        val dateFull = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(item.timestamp))
        "$day • $timeSpentText • $dateFull"
    }

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
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = iconColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "READ TOONS",
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

            // Workspace badge row
            val workspaceLabel = item.details.takeIf { it.isNotBlank() } ?: "I Love It"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
            ) {
                Text(text = "toon", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(iconColor.copy(alpha = 0.15f))
                        .border(0.5.dp, iconColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = workspaceLabel, color = iconColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            val toonsById = remember(chronologicallyOrdered) {
                chronologicallyOrdered.groupBy { parseDetails(it.details).mangaId }
                    .toList()
                    .sortedByDescending { it.second.last().timestamp }
            }

            toonsById.forEachIndexed { tIdx, (mId, occs) ->
                if (mId == null) return@forEachIndexed
                val toonManga = allMangaMap[mId] ?: return@forEachIndexed
                val isLastToon = tIdx == toonsById.size - 1
                var isToonExpanded by remember(mId) { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isToonExpanded = !isToonExpanded }
                    ) {
                        Text(
                            text = if (isLastToon) "└── " else "├── ",
                            color = Color.White.copy(alpha = 0.15f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Rounded.ImportContacts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp).padding(end = 4.dp)
                        )
                        Text(
                            text = truncateMiddle(toonManga.title),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = if (isToonExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    val chaptersById = occs.groupBy { parseDetails(it.details).chapterId }.toList().sortedByDescending { it.second.last().timestamp }
                    
                    if (!isToonExpanded) {
                        val lastChOcc = chaptersById.firstOrNull()
                        if (lastChOcc != null) {
                            val lastChapter = allChaptersMap[lastChOcc.first]
                            if (lastChapter != null) {
                                val lastMeta = parseDetails(lastChOcc.second.last().details)
                                val openCount = toonManga.openCount
                                
                                val lastPage = lastMeta.pages?.split(",")?.lastOrNull()?.split("-")?.lastOrNull()?.toLongOrNull() ?: lastChapter.position
                                val totalPage = lastMeta.totalPages?.toLong() ?: lastChapter.duration
                                val pageText = if (totalPage > 0L) "Page: $lastPage / $totalPage" else "Page: $lastPage"
                                val linePrefix = if (isLastToon) "    " else "│   "

                                Column(modifier = Modifier.padding(start = 32.dp).clickable { onNavigateToReader(lastChapter.id) }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                        Text("Last Read: ${truncateMiddle(lastChapter.title)}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                        Text(pageText, color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                        Text("Opened: $openCount", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        chaptersById.forEachIndexed { cIdx, (cId, cOccs) ->
                            if (cId == null) return@forEachIndexed
                            val chapter = allChaptersMap[cId] ?: return@forEachIndexed
                            val isLastChapter = cIdx == chaptersById.size - 1
                            val lastOcc = cOccs.last()
                            val lastMeta = parseDetails(lastOcc.details)
                            
                            val openCount = chapter.openCount
                            
                            val lastPage = lastMeta.pages?.split(",")?.lastOrNull()?.split("-")?.lastOrNull()?.toLongOrNull() ?: chapter.position
                            val totalPage = lastMeta.totalPages?.toLong() ?: chapter.duration
                            val pageText = if (totalPage > 0L) "Page: $lastPage / $totalPage" else "Page: $lastPage"

                            val toonLinePrefix = if (isLastToon) "    " else "│   "
                            
                            Column(modifier = Modifier.padding(start = 32.dp).clickable { onNavigateToReader(cId) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isLastChapter) "└── " else "├── ",
                                        color = Color.White.copy(alpha = 0.15f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = truncateMiddle(chapter.title),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                val chLinePrefix = toonLinePrefix + if (isLastChapter) "    " else "│   "
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = chLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                    Text(pageText, color = Color.Gray, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = chLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                    Text("Opened: $openCount", color = Color.Gray, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = chLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                    Text("Last read: ${formatTimeOnly(lastOcc.timestamp)}", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
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
