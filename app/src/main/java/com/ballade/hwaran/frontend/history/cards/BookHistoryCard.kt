package com.ballade.hwaran.frontend.history.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
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
fun BookHistoryCard(
    item: BookHistoryGroup,
    onNavigateToPdfReader: (Long) -> Unit
) {
    val iconColor = Color(0xFFBA6E8F) // Soft Coral / Rose accent
    val workspaceLabel = item.bookManga.workspace?.takeIf { it.isNotBlank() } ?: "I Love It"

    val formattedDateFooter = remember(item.latestTimestamp) {
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(item.latestTimestamp))
        val dateFull = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(item.latestTimestamp))
        "$day • 15 mins • $dateFull"
    }

    val pageText = if (item.totalPages > 0) {
        "Page ${item.lastPage} / ${item.totalPages}"
    } else {
        "Page ${item.lastPage}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onNavigateToPdfReader(item.bookManga.id) },
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
                Icon(Icons.Rounded.Book, null, tint = iconColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "BOOK",
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

            // Book Title
            Text(
                text = truncateMiddle(item.bookManga.title.removeSuffix(".pdf"), 32),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "shelf: book",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            // Book details
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "└── ",
                    color = Color.White.copy(alpha = 0.20f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = pageText,
                    color = Color(0xFFE6E8EC),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 1.dp)
            ) {
                Text(text = "    ", color = Color.White.copy(alpha = 0.20f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text(
                    text = "Last Read: ${formatTimeOnly(item.latestTimestamp)}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.5.sp
                )
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
