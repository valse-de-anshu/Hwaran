package com.ballade.hwaran.frontend.description.video

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenu
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenuItem
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ballade.hwaran.frontend.player.video.VideoPreview
import com.ballade.hwaran.core.metadata.EntryMetadata
import com.ballade.hwaran.core.metadata.VideoItemMetadata
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import java.io.File

// Internal sort options — still kept for the filter pills that do work locally
enum class ChannelVideoSortOption(val displayName: String, val subtitle: String) {
    FIRST_TO_LAST("First to Last", "Order added • First → Latest"),
    LAST_TO_FIRST("Last to First", "Latest → First"),
    TITLE_AZ("Title (A to Z)", "Alphabetical order"),
    TITLE_ZA("Title (Z to A)", "Reverse alphabetical"),
    LONGEST("Longest Video", "Highest duration first"),
    SHORTEST("Shortest Video", "Shortest duration first"),
    SHUFFLE("Shuffle", "Random order")
}

// Filter pill options shown at the top (in this exact order)
enum class VideoFilterPill(val label: String, val requiresJsonData: Boolean) {
    MOST_VIEWS("Most Views", true),
    MOST_LIKED("Most Liked", true),
    MOST_RATED("Most Rated", true),
    SHORTEST("Shortest", false),
    LONGEST("Longest", false),
    ASCENDING("Ascending", false),
    DESCENDING("Descending", false),
}

private const val PAGE_SIZE = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelVideosView(
    channel: MangaEntity,
    videos: List<ChapterEntity>,
    entryMetadata: EntryMetadata = EntryMetadata(),
    onNavigateBack: () -> Unit,
    onNavigateToVideo: (Long) -> Unit,
    onPickVideos: () -> Unit,
    onPickVideosFolder: () -> Unit,
    onDeleteVideos: (List<Long>) -> Unit,
    onChangeVideoThumbnail: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val CardBg = MaterialTheme.colorScheme.surface
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val DangerRed = Color(0xFFE57373)

    val snackbarHostState = remember { SnackbarHostState() }
    var lastSnackbarTime by remember { mutableLongStateOf(0L) }

    var selectedPill by remember { mutableStateOf<VideoFilterPill?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedVideoIds = remember { mutableStateListOf<Long>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var videoPendingDelete by remember { mutableStateOf<ChapterEntity?>(null) }

    // Pagination
    var currentPage by remember { mutableIntStateOf(0) }

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

    // Title normalizer for fuzzy-matching video files with JSON metadata entries
    fun normalizeTitle(raw: String): String {
        return raw.lowercase()
            .replace(Regex("^\\s*\\d+\\s*[.\\-_)]\\s*"), "") // strip leading "1. ", "01 - "
            .replace(Regex("[^\\p{L}0-9]"), "") // retain only letters & digits across all alphabets
    }

    val videoStatsMap = remember(entryMetadata.videoItems) {
        entryMetadata.videoItems.associateBy { normalizeTitle(it.title) }
    }

    val hasViewsData = remember(entryMetadata.videoItems) {
        entryMetadata.videoItems.any { it.viewCount > 0L }
    }
    val hasLikesData = remember(entryMetadata.videoItems) {
        entryMetadata.videoItems.any { it.likeCount > 0L }
    }
    val hasRatedData = remember(entryMetadata.videoItems) {
        entryMetadata.videoItems.any { it.topRatedRank >= 0 }
    }

    // Sorted video list driven by selected pill
    val sortedVideos = remember(videos, selectedPill, videoStatsMap) {
        when (selectedPill) {
            VideoFilterPill.SHORTEST -> videos.sortedBy { it.duration }
            VideoFilterPill.LONGEST -> videos.sortedByDescending { it.duration }
            VideoFilterPill.ASCENDING -> videos.sortedBy { it.title.lowercase() }
            VideoFilterPill.DESCENDING -> videos.sortedByDescending { it.title.lowercase() }
            VideoFilterPill.MOST_VIEWS -> {
                if (hasViewsData) {
                    videos.sortedWith(
                        compareByDescending<ChapterEntity> { videoStatsMap[normalizeTitle(it.title)]?.viewCount ?: 0L }
                            .thenBy { it.position }
                    )
                } else videos
            }
            VideoFilterPill.MOST_LIKED -> {
                if (hasLikesData) {
                    videos.sortedWith(
                        compareByDescending<ChapterEntity> { videoStatsMap[normalizeTitle(it.title)]?.likeCount ?: 0L }
                            .thenBy { it.position }
                    )
                } else videos
            }
            VideoFilterPill.MOST_RATED -> {
                if (hasRatedData) {
                    videos.sortedWith(
                        compareBy<ChapterEntity> {
                            val rank = videoStatsMap[normalizeTitle(it.title)]?.topRatedRank
                            if (rank != null && rank >= 0) rank else Int.MAX_VALUE
                        }.thenBy { it.position }
                    )
                } else videos
            }
            null -> videos.sortedWith(compareBy {
                Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloatOrNull() ?: Float.MAX_VALUE
            })
        }
    }

    val gridState = rememberLazyGridState()

    // Reset page and scroll to top when filter changes
    LaunchedEffect(selectedPill) {
        currentPage = 0
        gridState.scrollToItem(0)
    }
    // Reset page and scroll to top when video list changes
    LaunchedEffect(sortedVideos.size) {
        currentPage = 0
        gridState.scrollToItem(0)
    }
    // Scroll to top when switching pages
    LaunchedEffect(currentPage) {
        gridState.scrollToItem(0)
    }

    val totalPages = maxOf(1, (sortedVideos.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val pagedVideos = remember(sortedVideos, currentPage) {
        sortedVideos.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE)
    }

    val displayTitle = remember(channel.title, entryMetadata.title) {
        if (channel.title.equals("Videos", ignoreCase = true) && entryMetadata.title.isNotBlank()) {
            entryMetadata.title
        } else if (entryMetadata.title.isNotBlank()) {
            entryMetadata.title
        } else {
            channel.title
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF2A2733),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 24.dp)
        ) {
            // ── Header Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button + Title
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isDeleteMode) "${selectedVideoIds.size} selected" else "${videos.size} Videos",
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action buttons: Clear Filter, Filter, Delete, Add
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clear Filter Button (shown if a filter is active)
                    if (selectedPill != null && !isDeleteMode) {
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { selectedPill = null },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.FilterListOff,
                                    contentDescription = "Clear Filter",
                                    tint = Color(0xFFE6E8EC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Filter Toggle Button
                    if (!isDeleteMode) {
                        val isFilterActive = selectedPill != null || showFilters
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { showFilters = !showFilters },
                            shape = CircleShape,
                            color = if (isFilterActive) Color(0xFF222631) else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(
                                1.dp,
                                if (isFilterActive) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.FilterList,
                                    contentDescription = "Filter",
                                    tint = if (isFilterActive) Color(0xFFE6E8EC) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Delete button
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

            // ── Filter Pills Row with Smooth Fading Edges ──
            AnimatedVisibility(
                visible = sortedVideos.isNotEmpty() && (showFilters || selectedPill != null)
            ) {
                val pillListState = rememberLazyListState()
                val canScrollBackward by remember {
                    derivedStateOf { pillListState.firstVisibleItemIndex > 0 || pillListState.firstVisibleItemScrollOffset > 0 }
                }
                val canScrollForward by remember {
                    derivedStateOf { pillListState.canScrollForward }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fadeWidth = 24.dp.toPx()
                            if (fadeWidth > 0f && size.width > fadeWidth * 2) {
                                val leftFadeFraction = if (canScrollBackward) (fadeWidth / size.width) else 0f
                                val rightFadeFraction = if (canScrollForward) ((size.width - fadeWidth) / size.width) else 1f
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to (if (canScrollBackward) Color.Transparent else Color.Black),
                                        leftFadeFraction to Color.Black,
                                        rightFadeFraction to Color.Black,
                                        1f to (if (canScrollForward) Color.Transparent else Color.Black)
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                ) {
                    LazyRow(
                        state = pillListState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        items(VideoFilterPill.values().size) { idx ->
                            val pill = VideoFilterPill.values()[idx]
                            val isSelected = selectedPill == pill

                            val isDataAvailable = when (pill) {
                                VideoFilterPill.MOST_VIEWS -> hasViewsData
                                VideoFilterPill.MOST_LIKED -> hasLikesData
                                VideoFilterPill.MOST_RATED -> hasRatedData
                                else -> true
                            }

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        if (pill.requiresJsonData && !isDataAvailable) {
                                            val now = System.currentTimeMillis()
                                            if (now - lastSnackbarTime > 1500L) {
                                                lastSnackbarTime = now
                                                coroutineScope.launch {
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    snackbarHostState.showSnackbar("No data present")
                                                }
                                            }
                                        } else {
                                            selectedPill = if (isSelected) null else pill
                                        }
                                    },
                                color = if (isSelected) Color(0xFF222631) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected -> Color.White.copy(alpha = 0.22f)
                                        pill.requiresJsonData && !isDataAvailable -> Color.White.copy(alpha = 0.06f)
                                        else -> Color.White.copy(alpha = 0.10f)
                                    }
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = pill.label,
                                    color = when {
                                        isSelected -> Color(0xFFE6E8EC)
                                        pill.requiresJsonData && !isDataAvailable -> Color.White.copy(alpha = 0.35f)
                                        else -> Color.White.copy(alpha = 0.75f)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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
                Box(modifier = Modifier.weight(1f)) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 300.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
                    ) {

                        items(pagedVideos, key = { it.id }) { video ->
                        val isSelected = selectedVideoIds.contains(video.id)
                        var showPreview by remember { mutableStateOf(false) }

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
                                    if (isDeleteMode) {
                                        detectTapGestures(
                                            onTap = {
                                                if (isSelected) selectedVideoIds.remove(video.id)
                                                else selectedVideoIds.add(video.id)
                                            }
                                        )
                                        return@pointerInput
                                    }
                                    detectTapGestures(
                                        onPress = {
                                            var isHeld = false
                                            val previewJob = coroutineScope.launch {
                                                delay(180L)
                                                isHeld = true
                                                showPreview = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            val released = tryAwaitRelease()
                                            previewJob.cancel()
                                            if (isHeld) {
                                                showPreview = false
                                            } else if (released) {
                                                onNavigateToVideo(video.id)
                                            }
                                        }
                                    )
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
                                            .crossfade(false)
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
                                        val stat = videoStatsMap[normalizeTitle(video.title)]
                                        val viewsStr = if (stat != null && stat.viewCount > 0L) {
                                            when {
                                                stat.viewCount >= 1_000_000 -> String.format("%.1fM views", stat.viewCount / 1_000_000.0)
                                                stat.viewCount >= 1_000 -> String.format("%.1fK views", stat.viewCount / 1_000.0)
                                                else -> "${stat.viewCount} views"
                                            }
                                        } else null

                                        val subtitleText = listOfNotNull(
                                            durationStr?.let { "Duration • $it" },
                                            viewsStr
                                        ).joinToString(" • ")

                                        if (subtitleText.isNotBlank()) {
                                            Text(
                                                text = subtitleText,
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

                                        HwaranDropdownMenu(
                                            expanded = showItemMenu,
                                            onDismissRequest = { showItemMenu = false }
                                        ) {
                                            HwaranDropdownMenuItem(
                                                text = "Change Thumbnail",
                                                onClick = {
                                                    showItemMenu = false
                                                    onChangeVideoThumbnail(video.id)
                                                },
                                                leadingIcon = Icons.Rounded.Image
                                            )
                                            HwaranDropdownMenuItem(
                                                text = "Delete Video",
                                                onClick = {
                                                    showItemMenu = false
                                                    videoPendingDelete = video
                                                },
                                                leadingIcon = Icons.Rounded.Delete,
                                                isDanger = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Pagination as footer inside the grid ──
                    if (totalPages > 1) {
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "pagination"
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                                    .navigationBarsPadding(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = currentPage > 0) { currentPage-- },
                                    shape = CircleShape,
                                    color = if (currentPage > 0) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = if (currentPage > 0) 0.15f else 0.05f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                                            contentDescription = "Previous",
                                            tint = if (currentPage > 0) Color.White else Color.White.copy(alpha = 0.25f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = "${currentPage + 1} / $totalPages",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Surface(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = currentPage < totalPages - 1) { currentPage++ },
                                    shape = CircleShape,
                                    color = if (currentPage < totalPages - 1) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = if (currentPage < totalPages - 1) 0.15f else 0.05f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                                            contentDescription = "Next",
                                            tint = if (currentPage < totalPages - 1) Color.White else Color.White.copy(alpha = 0.25f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "nav_spacer"
                        ) {
                            Spacer(modifier = Modifier.navigationBarsPadding())
                        }
                    }
                } // end LazyVerticalGrid

                    // Top fading edge overlay when scrolled down
                    val showTopFade by remember {
                        derivedStateOf {
                            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
                        }
                    }
                    if (showTopFade) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF0D0F14),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                } // end Box
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
