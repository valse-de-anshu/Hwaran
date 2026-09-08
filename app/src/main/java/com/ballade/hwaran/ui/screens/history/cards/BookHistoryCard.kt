package com.ballade.hwaran.ui.screens.history.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
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
import com.ballade.hwaran.ui.screens.history.models.HistoryTimelineItem
import com.ballade.hwaran.ui.screens.history.models.parseDetails
import com.ballade.hwaran.ui.screens.history.models.truncateMiddle
import com.ballade.hwaran.ui.screens.history.models.formatTimeOnly
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BookHistoryCard(
    item: HistoryTimelineItem,
    allMangaMap: Map<Long, MangaEntity>,
    onNavigateToPdfReader: (Long) -> Unit
) {
    val manga = if (item.mangaId != null) allMangaMap[item.mangaId] else null
    val iconColor = Color(0xFFBA6E8F)
    val folderTitle = if (item.mangaId == null) "Books" else (manga?.title ?: item.title)

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
                Icon(Icons.Rounded.Book, null, tint = iconColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "BOOKS",
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
                Text(text = "book", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
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

            val booksById = remember(chronologicallyOrdered) {
                chronologicallyOrdered.groupBy { parseDetails(it.details).mangaId }
                    .toList()
                    .sortedByDescending { it.second.last().timestamp }
            }
            
            booksById.forEachIndexed { bIdx, (mId, occs) ->
                if (mId == null) return@forEachIndexed
                val bookManga = allMangaMap[mId] ?: return@forEachIndexed
                val lastOcc = occs.last()
                val lastMeta = parseDetails(lastOcc.details)
                
                val lastPage = lastMeta.pages?.split(",")?.lastOrNull()?.split("-")?.lastOrNull()?.toIntOrNull() 
                    ?: bookManga.lastReadPage ?: 1
                    
                val openCount = bookManga.openCount
                val isLastBook = bIdx == booksById.size - 1

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPdfReader(mId) }
                        .padding(vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isLastBook) "└── " else "├── ",
                            color = Color.White.copy(alpha = 0.15f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Rounded.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp).padding(end = 4.dp)
                        )
                        Text(
                            text = truncateMiddle(bookManga.title.removeSuffix(".pdf")),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(modifier = Modifier.padding(start = 32.dp)) {
                        val currentP = lastPage
                        val totalP = lastMeta.totalPages ?: 0
                        val pageText = if (totalP > 0) "Page: $currentP / $totalP" else "Page: $currentP"
                        val linePrefix = if (isLastBook) "    " else "│   "

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text(pageText, color = Color.Gray, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text("Opened: $openCount", color = Color.Gray, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text("Last read: ${formatTimeOnly(lastOcc.timestamp)}", color = Color.Gray, fontSize = 11.sp)
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
