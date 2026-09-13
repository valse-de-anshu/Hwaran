package com.ballade.hwaran.frontend.history.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.frontend.history.models.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ToonHistoryCard(
    item: ToonHistoryGroup,
    sfwText: String,
    nsfwText: String,
    onNavigateToReader: (Long) -> Unit
) {
    val iconColor = Color(0xFF7C9D97) // Soft Sage / Teal accent
    val sLabel = if (item.toonManga.isNsfw) nsfwText else sfwText
    val workspaceLabel = item.toonManga.workspace?.takeIf { it.isNotBlank() } ?: "I Love It"

    val formattedDateFooter = remember(item.latestTimestamp) {
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(item.latestTimestamp))
        val dateFull = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(item.latestTimestamp))
        val readTimeEstimate = "${(item.chapters.size * 5).coerceAtLeast(3)} mins"
        "$day • $readTimeEstimate • $dateFull"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = iconColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "TOON",
                    color = iconColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF222631))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = workspaceLabel,
                        color = Color(0xFFE6E8EC),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Toon Title
            Text(
                text = truncateMiddle(item.toonManga.title, 32),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "shelf: toon ᶻ 𝗓 𐰁 label: $sLabel",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            // Chapters List
            item.chapters.forEachIndexed { idx, chItem ->
                val isLast = idx == item.chapters.size - 1
                val chapter = chItem.chapter
                val pageText = if (chItem.totalPages > 0L) {
                    "Page ${chItem.lastPage} / ${chItem.totalPages}"
                } else {
                    "Page ${chItem.lastPage}"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNavigateToReader(chapter.id) }
                        .padding(vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isLast) "└── " else "├── ",
                            color = Color.White.copy(alpha = 0.20f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = truncateMiddle(chapter.title, 30),
                            color = Color(0xFFE6E8EC),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val prefix = if (isLast) "    " else "│   "
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        Text(text = prefix, color = Color.White.copy(alpha = 0.20f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        Text(
                            text = "$pageText • Last Read: ${formatTimeOnly(chItem.lastTimestamp)}",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formattedDateFooter,
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}
