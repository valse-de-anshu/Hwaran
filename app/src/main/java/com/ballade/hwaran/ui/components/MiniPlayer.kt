package com.ballade.hwaran.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import java.io.File
import java.io.InputStream

@Composable
fun MiniPlayer(
    musicViewModel: MusicViewModel,
    isVisible: Boolean,
    isVerticalCompact: Boolean = false,
    onClick: () -> Unit
) {
    val currentChapter by musicViewModel.currentChapter.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val currentManga by musicViewModel.currentManga.collectAsState()
    val currentPlaylist by musicViewModel.currentPlaylist.collectAsState()
    val playbackProgress by musicViewModel.playbackProgress.collectAsState()
    val shuffleMode by musicViewModel.shuffleMode.collectAsState()
    val repeatMode by musicViewModel.repeatMode.collectAsState()
    
    val context = LocalContext.current

    // Boundary Logic for Dimming Controls
    val currentIndex = remember(currentPlaylist, currentChapter) { 
        currentPlaylist.indexOfFirst { it.id == currentChapter?.id } 
    }
    val hasNext = currentPlaylist.isNotEmpty()
    val hasPrevious = currentPlaylist.isNotEmpty()
    
    val colorPalette by musicViewModel.colorPalette.collectAsState()

    var fallbackDominant by remember { mutableStateOf<Color?>(null) }
    var fallbackVibrant by remember { mutableStateOf<Color?>(null) }

    val coverPath = currentChapter?.thumbnailUri?.takeIf { it.isNotBlank() }
        ?: currentManga?.coverPath?.takeIf { it.isNotBlank() }
        ?: currentChapter?.folderUri?.takeIf { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) || it.endsWith(".webp", true) }

    LaunchedEffect(currentChapter?.id, coverPath) {
        if (coverPath != null) {
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = when {
                        coverPath.startsWith("content://") || coverPath.startsWith("android.resource://") -> {
                            val input: InputStream? = context.contentResolver.openInputStream(Uri.parse(coverPath))
                            BitmapFactory.decodeStream(input)
                        }
                        coverPath.startsWith("file://") -> {
                            val file = File(Uri.parse(coverPath).path ?: "")
                            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                        }
                        else -> {
                            BitmapFactory.decodeFile(coverPath)
                        }
                    }

                    if (bitmap != null) {
                        val p = Palette.from(bitmap).generate()
                        fallbackDominant = Color(p.getDominantColor(0xFF1E1E1E.toInt()))
                        fallbackVibrant = Color(p.getVibrantColor(0xFF2A2A2A.toInt()))
                    } else {
                        fallbackDominant = null
                        fallbackVibrant = null
                    }
                } catch (e: Exception) {
                    fallbackDominant = null
                    fallbackVibrant = null
                }
            }
        } else {
            fallbackDominant = null
            fallbackVibrant = null
        }
    }

    val targetDominant = remember(colorPalette.dominant, fallbackDominant) {
        if (colorPalette.dominant != 0L && colorPalette.dominant != 0xFF000000.toInt().toLong()) {
            Color(colorPalette.dominant)
        } else {
            fallbackDominant ?: Color(0xFF1C1C1E)
        }
    }

    val targetVibrant = remember(colorPalette.vibrant, fallbackVibrant, targetDominant) {
        if (colorPalette.vibrant != 0L && colorPalette.vibrant != 0xFF000000.toInt().toLong()) {
            Color(colorPalette.vibrant)
        } else {
            fallbackVibrant ?: targetDominant
        }
    }

    // Smooth transitions
    val dominantColor by animateColorAsState(
        targetValue = targetDominant,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "dominantColorTransition"
    )
    val vibrantColor by animateColorAsState(
        targetValue = targetVibrant,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "vibrantColorTransition"
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isVerticalCompact) {
        var isCollapsed by rememberSaveable { mutableStateOf(false) }
        val haptic = LocalHapticFeedback.current
        var accumulatedDrag by remember { mutableFloatStateOf(0f) }
        var collapsedDrag by remember { mutableFloatStateOf(0f) }

        // Ultra-compact vertical mini player for Home screen (right side)
        AnimatedVisibility(
            visible = isVisible && currentChapter != null,
            enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(160, easing = LinearOutSlowInEasing)) + slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { it }
        ) {
            AnimatedContent(
                targetState = isCollapsed,
                transitionSpec = {
                    if (targetState) {
                        // Collapsing into edge tab (slide out to right, tab slides in from right)
                        (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)) { it } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(tween(200)))
                    } else {
                        // Expanding back (tab slides out to right, pill slides in from right)
                        (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)) { it } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(tween(200)))
                    }
                },
                label = "mini_player_collapse_transition"
            ) { collapsed ->
                if (collapsed) {
                    // Sleek docked edge handle with "<" icon
                    Surface(
                        modifier = Modifier
                            .width(32.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                                spotColor = dominantColor.copy(alpha = 0.7f),
                                ambientColor = Color.Black.copy(alpha = 0.5f)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isCollapsed = false
                            }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    if (delta < 0) { // Drag left to reveal
                                        collapsedDrag += delta
                                        if (collapsedDrag < -20f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isCollapsed = false
                                            collapsedDrag = 0f
                                        }
                                    }
                                },
                                onDragStopped = { collapsedDrag = 0f }
                            ),
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                        color = Color(0xDD121118),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            dominantColor.copy(alpha = 0.95f),
                                            vibrantColor.copy(alpha = 0.75f),
                                            Color(0xDD121118)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "Expand Mini Player",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (isPlaying) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
                                    val dotAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.35f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(800, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "dot_alpha"
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = dotAlpha))
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .width(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(26.dp),
                                spotColor = dominantColor.copy(alpha = 0.65f),
                                ambientColor = Color.Black.copy(alpha = 0.6f)
                            )
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    if (delta > 0) { // Drag right to collapse
                                        accumulatedDrag += delta
                                        if (accumulatedDrag > 25f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isCollapsed = true
                                            accumulatedDrag = 0f
                                        }
                                    }
                                },
                                onDragStopped = { accumulatedDrag = 0f }
                            )
                            .clickable { onClick() },
                        shape = RoundedCornerShape(26.dp),
                        color = Color(0xDD121118),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            dominantColor.copy(alpha = 0.90f),
                                            vibrantColor.copy(alpha = 0.72f),
                                            Color(0xEE111016)
                                        )
                                    )
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Spinning vinyl album art with progress ring
                                val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
                                val rotation by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(12000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "vinyl_angle"
                                )
                                val currentRotation = if (isPlaying) rotation else 0f

                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .clickable { onClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Circular progress track
                                    CircularProgressIndicator(
                                        progress = { playbackProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxSize(),
                                        color = Color.White.copy(alpha = 0.9f),
                                        trackColor = Color.White.copy(alpha = 0.15f),
                                        strokeWidth = 2.5.dp
                                    )

                                    // Inner spinning cover
                                    Box(
                                        modifier = Modifier
                                            .size(33.dp)
                                            .clip(CircleShape)
                                            .graphicsLayer { rotationZ = currentRotation }
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!coverPath.isNullOrBlank()) {
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
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // Previous button
                                IconButton(
                                    onClick = { musicViewModel.previous() },
                                    enabled = hasPrevious,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = if (hasPrevious) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Play / Pause button
                                Surface(
                                    onClick = { musicViewModel.togglePlayPause() },
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(6.dp, CircleShape, spotColor = Color.White.copy(alpha = 0.4f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = dominantColor.copy(alpha = 0.95f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Next button
                                IconButton(
                                    onClick = { musicViewModel.next() },
                                    enabled = hasNext,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = "Next",
                                        tint = if (hasNext) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Close / Kill button
                                IconButton(
                                    onClick = { musicViewModel.stopPlayback() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Stop Music",
                                        tint = Color.White.copy(alpha = 0.60f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Full horizontal MiniPlayer for description/playlist detail/history screens
        AnimatedVisibility(
            visible = isVisible && currentChapter != null,
            enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 },
            exit = fadeOut(tween(160, easing = LinearOutSlowInEasing)) + slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 2 }
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
                        .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!coverPath.isNullOrBlank()) {
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
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
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

                    IconButton(
                        onClick = { musicViewModel.stopPlayback() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Stop Music",
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
}
