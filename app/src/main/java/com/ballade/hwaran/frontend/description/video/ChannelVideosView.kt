package com.ballade.hwaran.frontend.description.video

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.frontend.player.video.VideoPreview
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import java.io.File

enum class ChannelVideoSortOption(val displayName: String, val subtitle: String) {
    FIRST_TO_LAST("First to Last", "Order added • First → Latest"),
    LAST_TO_FIRST("Last to First", "Latest → First"),
    TITLE_AZ("Title (A to Z)", "Alphabetical order"),
    TITLE_ZA("Title (Z to A)", "Reverse alphabetical"),
    LONGEST("Longest Video", "Highest duration first"),
    SHORTEST("Shortest Video", "Shortest duration first"),
    SHUFFLE("Shuffle", "Random order")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelVideosView(
    channel: MangaEntity,
    videos: List<ChapterEntity>,
    onNavigateBack: () -> Unit,
    onNavigateToVideo: (Long) -> Unit,
    onPickVideos: () -> Unit,
    onPickVideosFolder: () -> Unit,
    onDeleteVideos: (List<Long>) -> Unit,
    onChangeVideoThumbnail: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val CardBg = MaterialTheme.colorScheme.surface
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val DangerRed = Color(0xFFE57373)

    var selectedSort by remember { mutableStateOf(ChannelVideoSortOption.FIRST_TO_LAST) }
    var showSortSheet by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedVideoIds = remember { mutableStateListOf<Long>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var videoPendingDelete by remember { mutableStateOf<ChapterEntity?>(null) }

    BackHandler {
        if (isDeleteMode) {
            isDeleteMode = false
            selectedVideoIds.clear()
        } else {
            onNavigateBack()
        }
    }

    val videoImageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    val sortedVideos = remember(videos, selectedSort) {
        when (selectedSort) {
            ChannelVideoSortOption.FIRST_TO_LAST -> videos.sortedWith(compareBy {
                Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloatOrNull() ?: Float.MAX_VALUE
            })
            ChannelVideoSortOption.LAST_TO_FIRST -> videos.sortedWith(compareByDescending {
                Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloatOrNull() ?: Float.MIN_VALUE
            })
            ChannelVideoSortOption.TITLE_AZ -> videos.sortedBy { it.title.lowercase() }
            ChannelVideoSortOption.TITLE_ZA -> videos.sortedByDescending { it.title.lowercase() }
            ChannelVideoSortOption.LONGEST -> videos.sortedByDescending { it.duration }
            ChannelVideoSortOption.SHORTEST -> videos.sortedBy { it.duration }
            ChannelVideoSortOption.SHUFFLE -> videos.shuffled()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 24.dp, start = 20.dp, end = 20.dp)
        ) {
            // ── Clean Luxury Header Bar (Identical to Manhua Episode Screen) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isDeleteMode) {
                                isDeleteMode = false
                                selectedVideoIds.clear()
                            } else {
                                onNavigateBack()
                            }
                        },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Action Row: Filter/Sort Button, Delete Button, Add Button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Proper Filter / Sort Button (Opens ModalBottomSheet like manhua)
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { showSortSheet = true },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Filter and Sort",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Delete Button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (isDeleteMode) {
                                    if (selectedVideoIds.isNotEmpty()) {
                                        showDeleteConfirmDialog = true
                                    } else {
                                        isDeleteMode = false
                                    }
                                } else {
                                    isDeleteMode = true
                                    selectedVideoIds.clear()
                                }
                            },
                        shape = CircleShape,
                        color = if (isDeleteMode) DangerRed.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isDeleteMode) DangerRed else Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDeleteMode && selectedVideoIds.isNotEmpty()) Icons.Rounded.DeleteSweep else Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete",
                                tint = if (isDeleteMode) DangerRed else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (!isDeleteMode) {
                        // Add Videos Button
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { showAddDialog = true },
                            shape = CircleShape,
                            color = Color(0xFF222631),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add Videos",
                                    tint = Color(0xFFE6E8EC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Video Grid Catalog ──
            if (sortedVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No videos in channel yet",
                            color = TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(sortedVideos, key = { it.id }) { video ->
                        val isSelected = selectedVideoIds.contains(video.id)
                        var showPreview by remember { mutableStateOf(false) }
                        var wasPreviewing by remember { mutableStateOf(false) }

                        val (modelData, cacheKey) = remember(video.thumbnailUri, video.folderUri) {
                            if (video.thumbnailUri != null) {
                                File(video.thumbnailUri) to video.thumbnailUri!!
                            } else {
                                Uri.parse(video.folderUri) to "vthumb_raw_${video.id}"
                            }
                        }

                        val durationStr = remember(video.duration) {
                            if (video.duration > 0) {
                                val totalSeconds = video.duration / 1000
                                val hours = totalSeconds / 3600
                                val minutes = (totalSeconds % 3600) / 60
                                val seconds = totalSeconds % 60
                                if (hours > 0) {
                                    String.format("%d:%02d:%02d", hours, minutes, seconds)
                                } else {
                                    String.format("%02d:%02d", minutes, seconds)
                                }
                            } else null
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .pointerInput(video.id, isDeleteMode) {
                                    if (isDeleteMode) return@pointerInput
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        showPreview = false
                                        wasPreviewing = false
                                        val startPos = down.position
                                        val startTime = System.currentTimeMillis()
                                        var held = true
                                        val touchSlop = viewConfiguration.touchSlop
                                        do {
                                            val event = awaitPointerEvent()
                                            val pointer = event.changes.firstOrNull { it.id == down.id }
                                            if (pointer == null || !pointer.pressed) {
                                                held = false
                                                break
                                            }
                                            if ((pointer.position - startPos).getDistance() > touchSlop) {
                                                held = false
                                                break
                                            }
                                            val elapsed = System.currentTimeMillis() - startTime
                                            if (!showPreview && elapsed >= 180L) {
                                                showPreview = true
                                                wasPreviewing = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        } while (held)
                                        showPreview = false
                                    }
                                }
                                .clickable {
                                    if (isDeleteMode) {
                                        if (isSelected) selectedVideoIds.remove(video.id)
                                        else selectedVideoIds.add(video.id)
                                    } else {
                                        if (!wasPreviewing) {
                                            onNavigateToVideo(video.id)
                                        }
                                        wasPreviewing = false
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = CardBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // 16:9 Video Thumbnail Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(modelData)
                                            .crossfade(true)
                                            .memoryCacheKey(cacheKey)
                                            .diskCacheKey(cacheKey)
                                            .build(),
                                        imageLoader = videoImageLoader,
                                        contentDescription = video.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (showPreview) {
                                        VideoPreview(
                                            uri = Uri.parse(video.folderUri),
                                            initialDuration = video.duration,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Duration Badge
                                    if (durationStr != null && !isDeleteMode && !showPreview) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.80f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = durationStr,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Selection Checkbox in Delete Mode
                                    if (isDeleteMode) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                            ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) selectedVideoIds.add(video.id)
                                                    else selectedVideoIds.remove(video.id)
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFFE6E8EC),
                                                    checkmarkColor = Color.Black,
                                                    uncheckedColor = Color.White.copy(alpha = 0.7f)
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Video Info Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (durationStr != null) {
                                            Text(
                                                text = "Duration • $durationStr",
                                                color = TextMuted,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }

                                    var showItemMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showItemMenu = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = "Options",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showItemMenu,
                                            onDismissRequest = { showItemMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Change Thumbnail") },
                                                onClick = {
                                                    showItemMenu = false
                                                    onChangeVideoThumbnail(video.id)
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Image, contentDescription = null)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete Video", color = DangerRed) },
                                                onClick = {
                                                    showItemMenu = false
                                                    videoPendingDelete = video
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = DangerRed)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Luxury Sort Modal Bottom Sheet (Identical to Manhua Episode Screen) ──
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = CardBg,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Sort Order",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ChannelVideoSortOption.values().forEach { option ->
                    val isSelected = selectedSort == option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                selectedSort = option
                                showSortSheet = false
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF222631) else Color.White.copy(alpha = 0.03f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = option.displayName,
                                    color = if (isSelected) Color(0xFFE6E8EC) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = option.subtitle,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color(0xFFE6E8EC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Bulk Delete Confirmation Dialog ──
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Videos?", color = Color.White) },
            text = { Text("Delete ${selectedVideoIds.size} selected video(s)? This will remove them from the database and storage.", color = TextMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteVideos(selectedVideoIds.toList())
                        selectedVideoIds.clear()
                        isDeleteMode = false
                    }
                ) {
                    Text("Delete", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg
        )
    }

    // ── Single Video Delete Confirmation Dialog ──
    videoPendingDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoPendingDelete = null },
            title = { Text("Delete Video", color = Color.White) },
            text = { Text("Are you sure you want to delete \"${video.title}\"?", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteVideos(listOf(video.id))
                    videoPendingDelete = null
                }) {
                    Text("Delete", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoPendingDelete = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg
        )
    }

    // ── Add Videos Dialog ──
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Videos to Channel", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select how you would like to import videos into this channel:", color = TextMuted, fontSize = 13.sp)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showAddDialog = false
                                onPickVideos()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.VideoFile, contentDescription = null, tint = Color(0xFFE6E8EC))
                            Text("Select Video Files", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showAddDialog = false
                                onPickVideosFolder()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(0xFFE6E8EC))
                            Text("Select Folder", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg
        )
    }
}
