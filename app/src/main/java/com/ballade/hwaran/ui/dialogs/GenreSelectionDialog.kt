package com.ballade.hwaran.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val musicGenres = listOf(
    "Classical", "Pop", "Rock", "Jazz", "Folk", 
    "Ambient", "Phonk", "Dreamcore", "Emotional", "Depression", "Lyrics"
)

@Composable
fun GenreSelectionDialog(
    onGenreSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var selectedGenre by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.4f else 0.9f)
            .clip(RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
        title = { 
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Conductor Animation Based on Genre", 
                    color = Color.White, 
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Choose the animation style for JellyBall conductor",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isLandscape) 3 else 2),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = if (isLandscape) 180.dp else 400.dp)
            ) {
                items(musicGenres) { genre ->
                    val isSelected = selectedGenre == genre
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedGenre = genre }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = genre,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenreSelected(selectedGenre ?: "Skip") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedGenre != null) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
                    contentColor = if (selectedGenre != null) MaterialTheme.colorScheme.onPrimary else Color.White
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (selectedGenre != null) "Let's Go!" else "Skip", 
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = Color(0xFF0D0D0D),
        shape = RoundedCornerShape(32.dp)
    )
}
