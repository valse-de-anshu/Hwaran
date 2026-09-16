package com.ballade.hwaran.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.ui.viewmodels.MusicViewModel

@Composable
fun ReaderMusicPlayerCard(
    musicViewModel: MusicViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by musicViewModel.currentChapter.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val currentManga by musicViewModel.currentManga.collectAsState()
    val currentPlaylist by musicViewModel.currentPlaylist.collectAsState()
    val repeatMode by musicViewModel.repeatMode.collectAsState()
    val shuffleMode by musicViewModel.shuffleMode.collectAsState()

    val currentIndex = remember(currentPlaylist, currentTrack) { 
        currentPlaylist.indexOfFirst { it.id == currentTrack?.id } 
    }
    val hasPrevious = remember(currentIndex, currentPlaylist.size, shuffleMode, repeatMode) {
        if (currentPlaylist.size <= 1) false
        else if (shuffleMode || repeatMode != 0) true
        else currentIndex > 0
    }
    val hasNext = remember(currentIndex, currentPlaylist.size, shuffleMode, repeatMode) {
        if (currentPlaylist.size <= 1) false
        else if (shuffleMode || repeatMode != 0) true
        else currentIndex >= 0 && currentIndex < currentPlaylist.size - 1
    }
    val haptic = LocalHapticFeedback.current

    val coverPath = currentTrack?.thumbnailUri?.takeIf { it.isNotBlank() }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF14131E).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        shadowElevation = 16.dp,
        modifier = modifier
            .widthIn(min = 280.dp, max = 320.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume click */ }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Now Playing",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Kill / Stop music button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            musicViewModel.stopPlayback()
                            onClose()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "Stop Music",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Close flyout panel button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClose()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Track info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Album Art
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                ) {
                    if (coverPath != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(coverPath)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.align(Alignment.Center).size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: "No track playing",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTrack?.artist ?: "Unknown Artist",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        musicViewModel.previous()
                    },
                    enabled = hasPrevious,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = if (hasPrevious) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        musicViewModel.togglePlayPause()
                    },
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(6.dp, CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF14131E),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        musicViewModel.next()
                    },
                    enabled = hasNext,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = if (hasNext) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
