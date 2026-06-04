package com.ballade.hwaran.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import java.io.InputStream

@Composable
fun MiniPlayer(
    musicViewModel: MusicViewModel,
    isVisible: Boolean,
    onClick: () -> Unit
) {
    val currentChapter by musicViewModel.currentChapter.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val currentManga by musicViewModel.currentManga.collectAsState()
    val currentPlaylist by musicViewModel.currentPlaylist.collectAsState()
    val shuffleMode by musicViewModel.shuffleMode.collectAsState()
    val repeatMode by musicViewModel.repeatMode.collectAsState()
    
    val context = LocalContext.current

    // Boundary Logic for Dimming Controls
    val currentIndex = remember(currentPlaylist, currentChapter) { 
        currentPlaylist.indexOfFirst { it.id == currentChapter?.id } 
    }
    val hasNext = currentIndex < currentPlaylist.size - 1 && currentPlaylist.size > 1
    val hasPrevious = currentIndex > 0 && currentPlaylist.size > 1
    
    // Independent Robust Color Logic for Widget
    var widgetDominant by remember { mutableStateOf(Color.Black) }
    var widgetVibrant by remember { mutableStateOf(Color.DarkGray) }
    
    LaunchedEffect(currentChapter?.id) {
        val path = currentChapter?.thumbnailUri ?: currentChapter?.folderUri ?: currentManga?.coverPath
        if (path != null) {
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = if (path.startsWith("content://")) {
                        val input: InputStream? = context.contentResolver.openInputStream(Uri.parse(path))
                        BitmapFactory.decodeStream(input)
                    } else {
                        BitmapFactory.decodeFile(path)
                    }
                    
                    if (bitmap != null) {
                        val p = Palette.from(bitmap).generate()
                        widgetDominant = Color(p.getDominantColor(0xFF000000.toInt()))
                        widgetVibrant = Color(p.getVibrantColor(0xFF222222.toInt()))
                    } else {
                        widgetDominant = Color.Black
                        widgetVibrant = Color.DarkGray
                    }
                } catch (e: Exception) {
                    widgetDominant = Color.Black
                    widgetVibrant = Color.DarkGray
                }
            }
        } else {
            widgetDominant = Color.Black
            widgetVibrant = Color.DarkGray
        }
    }

    // Smooth transitions
    val dominantColor by animateColorAsState(
        targetValue = widgetDominant,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "dominantColorTransition"
    )
    val vibrantColor by animateColorAsState(
        targetValue = widgetVibrant,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "vibrantColorTransition"
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AnimatedVisibility(
        visible = isVisible && currentChapter != null,
        enter = fadeIn(tween(600)) + slideInVertically(tween(600, easing = EaseOutBack)) { it },
        exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { it }
    ) {
        Box(
            modifier = Modifier
                .then(if (isLandscape) Modifier.width(360.dp) else Modifier.fillMaxWidth())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(62.dp)
                .graphicsLayer {
                    shadowElevation = 24f
                    shape = CircleShape
                    clip = true
                }
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    spotColor = dominantColor.copy(alpha = 0.5f)
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            dominantColor.copy(alpha = 0.95f),
                            vibrantColor.copy(alpha = 0.85f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .border(2.5.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                .clickable { onClick() }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Premium Album Art Container
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    val cover = currentChapter?.thumbnailUri ?: currentChapter?.folderUri ?: currentManga?.coverPath
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(cover)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Track Info with better typography
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentChapter?.title ?: "Unknown",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.2.sp
                    )
                    Text(
                        text = currentChapter?.artist ?: "Unknown Artist",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    IconButton(
                        onClick = { musicViewModel.previous() },
                        enabled = hasPrevious,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (hasPrevious) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Surface(
                        onClick = { musicViewModel.togglePlayPause() },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(8.dp, CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = dominantColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { musicViewModel.next() },
                        enabled = hasNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = if (hasNext) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
