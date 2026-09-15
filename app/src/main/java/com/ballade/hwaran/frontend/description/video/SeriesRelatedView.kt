package com.ballade.hwaran.frontend.description.video

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenu
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ballade.hwaran.core.metadata.EntryMetadata
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.CoverArtResolver
import com.ballade.hwaran.frontend.player.video.VideoPreview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeriesRelatedView(
    manga: MangaEntity,
    videos: List<ChapterEntity>,
    childBoxes: List<MangaEntity>,
    availableMediaForLinking: List<MangaEntity>,
    initialTab: String = "Videos",
    entryMetadata: EntryMetadata = EntryMetadata(),
    onNavigateBack: () -> Unit,
    onNavigateToVideo: (Long) -> Unit,
    onPickVideos: () -> Unit,
    onPickVideosFolder: () -> Unit,
    onDeleteVideos: (List<Long>) -> Unit,
    onChangeVideoThumbnail: (Long) -> Unit,
    onNavigateToRelated: (Long) -> Unit,
    onCreateRelatedBox: (String, String) -> Unit = { _, _ -> },
    onLinkExistingMedia: (Long, String, String?) -> Unit,
    onLinkMultipleExistingMedia: (List<Long>, String) -> Unit = { _, _ -> },
    onUnlinkRelated: (Long) -> Unit,
    onDeleteRelated: (Long) -> Unit,
    onRefreshAvailableMedia: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val CardBg = MaterialTheme.colorScheme.surface
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val DangerRed = Color(0xFFE57373)

    var selectedFilterCategory by remember(initialTab) { mutableStateOf(initialTab) }
    // Filter pill for video sorting (Videos tab only)
    var selectedPill by remember { mutableStateOf<VideoFilterPill?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    // Pagination state (Videos tab only)
    var currentPage by remember { mutableIntStateOf(0) }
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedVideoIds = remember { mutableStateListOf<Long>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var videoPendingDelete by remember { mutableStateOf<ChapterEntity?>(null) }

    var showLinkSheet by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<MangaEntity?>(null) }
    var itemToUnlink by remember { mutableStateOf<MangaEntity?>(null) }

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

    fun normalizeTitle(raw: String): String {
        return raw.lowercase()
            .replace(Regex("^\\s*\\d+\\s*[.\\-_)]\\s*"), "")
            .replace(Regex("[^\\p{L}0-9]"), "")
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

    val filterTabs = remember {
        listOf("Videos", "Seasons", "Movies", "OVAs & ONAs", "Specials & BD", "Prequels & Sequels", "Other")
    }

    val filteredBoxes = remember(childBoxes, selectedFilterCategory) {
        when (selectedFilterCategory) {
            "Seasons" -> childBoxes.filter {
                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                rel == SeriesRelationType.SEASON || (it.parentMangaId == null && (it.boxPurpose == null || it.boxPurpose == "series"))
            }
            "Movies" -> childBoxes.filter {
                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                rel == SeriesRelationType.MOVIE || it.boxPurpose?.contains("movie", ignoreCase = true) == true
            }
            "OVAs & ONAs" -> childBoxes.filter {
                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                rel == SeriesRelationType.OVA || rel == SeriesRelationType.ONA
            }
            "Specials & BD" -> childBoxes.filter {
                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                rel == SeriesRelationType.SPECIAL || rel == SeriesRelationType.BLURAY
            }
            "Prequels & Sequels" -> childBoxes.filter {
                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                rel == SeriesRelationType.PREQUEL || rel == SeriesRelationType.SEQUEL
            }
            "Other" -> childBoxes.filter {
                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                rel == SeriesRelationType.SPINOFF || rel == SeriesRelationType.SUMMARY || rel == SeriesRelationType.ALT_VERSION
            }
            else -> childBoxes
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var lastSnackbarTime by remember { mutableLongStateOf(0L) }
    val videoGridState = rememberLazyGridState()

    // Reset page and scroll to top when filter or data changes
    LaunchedEffect(selectedPill) {
        currentPage = 0
        videoGridState.scrollToItem(0)
    }
    LaunchedEffect(sortedVideos.size) {
        currentPage = 0
        videoGridState.scrollToItem(0)
    }
    LaunchedEffect(currentPage) {
        videoGridState.scrollToItem(0)
    }

    val videoPageSize = 10
    val videoTotalPages = maxOf(1, (sortedVideos.size + videoPageSize - 1) / videoPageSize)
    val pagedVideos = remember(sortedVideos, currentPage) {
        sortedVideos.drop(currentPage * videoPageSize).take(videoPageSize)
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
                .padding(top = 16.dp, start = 18.dp, end = 18.dp)
        ) {
            // ── Top Navigation / Action Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
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
                            text = manga.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedFilterCategory == "Videos") {
                                if (isDeleteMode) "${selectedVideoIds.size} selected" else "${videos.size} Videos"
                            } else {
                                "${childBoxes.size} Franchise Links"
                            },
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action Row based on active tab
                if (selectedFilterCategory == "Videos") {
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
                } else {
                    // Franchise Linking Action Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onRefreshAvailableMedia()
                                showLinkSheet = true
                            },
                        color = Color(0xFF222631),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFFE6E8EC), modifier = Modifier.size(16.dp))
                            Text("Link Media", color = Color(0xFFE6E8EC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Category Filter Pills (Videos default, followed by franchise relation tags) with Smooth Fading Edges ──
            val categoryListState = rememberLazyListState()
            val canScrollCategoryBackward by remember {
                derivedStateOf { categoryListState.firstVisibleItemIndex > 0 || categoryListState.firstVisibleItemScrollOffset > 0 }
            }
            val canScrollCategoryForward by remember {
                derivedStateOf { categoryListState.canScrollForward }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fadeWidth = 24.dp.toPx()
                        if (fadeWidth > 0f && size.width > fadeWidth * 2) {
                            val leftFadeFraction = if (canScrollCategoryBackward) (fadeWidth / size.width) else 0f
                            val rightFadeFraction = if (canScrollCategoryForward) ((size.width - fadeWidth) / size.width) else 1f
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to (if (canScrollCategoryBackward) Color.Transparent else Color.Black),
                                    leftFadeFraction to Color.Black,
                                    rightFadeFraction to Color.Black,
                                    1f to (if (canScrollCategoryForward) Color.Transparent else Color.Black)
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) {
                LazyRow(
                    state = categoryListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    items(filterTabs) { tab ->
                        val isSel = selectedFilterCategory == tab
                        val tabCount = when (tab) {
                            "Videos" -> videos.size
                            "Seasons" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.SEASON }
                            "Movies" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.MOVIE }
                            "OVAs & ONAs" -> childBoxes.count {
                                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                                rel == SeriesRelationType.OVA || rel == SeriesRelationType.ONA
                            }
                            "Specials & BD" -> childBoxes.count {
                                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                                rel == SeriesRelationType.SPECIAL || rel == SeriesRelationType.BLURAY
                            }
                            "Prequels & Sequels" -> childBoxes.count {
                                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                                rel == SeriesRelationType.PREQUEL || rel == SeriesRelationType.SEQUEL
                            }
                            "Other" -> childBoxes.count {
                                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                                rel == SeriesRelationType.SPINOFF || rel == SeriesRelationType.SUMMARY || rel == SeriesRelationType.ALT_VERSION
                            }
                            else -> 0
                        }

                        val tabLabel = if (tabCount > 0) "$tab ($tabCount)" else tab

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isDeleteMode && tab != "Videos") {
                                        isDeleteMode = false
                                        selectedVideoIds.clear()
                                    }
                                    selectedFilterCategory = tab
                                },
                            color = if (isSel) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, if (isSel) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (isSel) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // ── Video Sort Filter Pills (Only shown in Videos tab) with Smooth Fading Edges ──
            AnimatedVisibility(
                visible = selectedFilterCategory == "Videos" && sortedVideos.isNotEmpty() && (showFilters || selectedPill != null)
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
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
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

            // ── Main Content Area: Videos Grid or Franchise Cards ──
            if (selectedFilterCategory == "Videos") {
                if (sortedVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VideoLibrary,
                                        contentDescription = null,
                                        tint = Color(0xFFE6E8EC),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = "No Videos in Series Yet",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Add video files or select an episodes folder to start watching directly from this series.",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showAddDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF222631),
                                        contentColor = Color(0xFFE6E8EC)
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("Add Videos", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        LazyVerticalGrid(
                            state = videoGridState,
                            columns = GridCells.Adaptive(minSize = 300.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
                        ) {
                            items(pagedVideos, key = { it.id }) { video ->
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
                        if (videoTotalPages > 1) {
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
                                        text = "${currentPage + 1} / $videoTotalPages",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Surface(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = currentPage < videoTotalPages - 1) { currentPage++ },
                                        shape = CircleShape,
                                        color = if (currentPage < videoTotalPages - 1) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = if (currentPage < videoTotalPages - 1) 0.15f else 0.05f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                                                contentDescription = "Next",
                                                tint = if (currentPage < videoTotalPages - 1) Color.White else Color.White.copy(alpha = 0.25f),
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

                    // Top fading edge overlay as user scrolls up
                    val showTopFade by remember {
                        derivedStateOf {
                            videoGridState.firstVisibleItemIndex > 0 || videoGridState.firstVisibleItemScrollOffset > 0
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
            } // end else (sortedVideos not empty)


            } else {
                // ── Franchise Media List (Seasons, Movies, OVAs, etc.) ──
                if (filteredBoxes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountTree,
                                        contentDescription = null,
                                        tint = Color(0xFFE6E8EC),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = if (childBoxes.isEmpty()) "No Related Media Linked" else "No items in '$selectedFilterCategory'",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Link prequels, sequels, movies, OVAs, or Blu-ray editions from your library to group the entire franchise together.",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        onRefreshAvailableMedia()
                                        showLinkSheet = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF222631),
                                        contentColor = Color(0xFFE6E8EC)
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("Link from Library", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(filteredBoxes, key = { it.id }) { boxItem ->
                            val relationType = remember(boxItem.boxPurpose) {
                                SeriesRelationType.fromPurpose(boxItem.boxPurpose)
                            }

                            val boxCoverModel = remember(boxItem.coverPath, boxItem.parentUri) {
                                CoverArtResolver.resolveCoverModel(boxItem.coverPath, boxItem.parentUri, null, context)
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onNavigateToRelated(boxItem.id) },
                                shape = RoundedCornerShape(18.dp),
                                color = CardBg,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Cover Preview Thumbnail
                                    Box(
                                        modifier = Modifier
                                            .width(68.dp)
                                            .height(96.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.4f))
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (boxCoverModel != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(boxCoverModel)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = boxItem.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Movie,
                                                contentDescription = null,
                                                tint = TextMuted,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    // Info Column
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Relation Tag Pill
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF222631),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = relationType.icon,
                                                    contentDescription = null,
                                                    tint = Color(0xFFE6E8EC),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = relationType.displayName.uppercase(),
                                                    color = Color(0xFFE6E8EC),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.6.sp
                                                )
                                            }
                                        }

                                        // Title
                                        Text(
                                            text = boxItem.boxLabel ?: boxItem.title,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (!boxItem.boxLabel.isNullOrBlank() && !boxItem.boxLabel.equals(boxItem.title, ignoreCase = true)) {
                                            Text(
                                                text = boxItem.title,
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Action Menu (Unlink / Delete)
                                    var showItemMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showItemMenu = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = "Options",
                                                tint = TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        HwaranDropdownMenu(
                                            expanded = showItemMenu,
                                            onDismissRequest = { showItemMenu = false }
                                        ) {
                                            HwaranDropdownMenuItem(
                                                text = "Open Details",
                                                onClick = {
                                                    showItemMenu = false
                                                    onNavigateToRelated(boxItem.id)
                                                },
                                                leadingIcon = Icons.AutoMirrored.Rounded.OpenInNew
                                            )
                                            HwaranDropdownMenuItem(
                                                text = "Unlink from Series",
                                                onClick = {
                                                    showItemMenu = false
                                                    itemToUnlink = boxItem
                                                },
                                                leadingIcon = Icons.Rounded.LinkOff
                                            )
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                                            HwaranDropdownMenuItem(
                                                text = "Delete Entry",
                                                onClick = {
                                                    showItemMenu = false
                                                    itemToDelete = boxItem
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
                }
            }
        }
    }


    // ── Bulk Delete Confirmation Dialog (Videos) ──
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

    // ── Add Videos Dialog (Files or Folder) ──
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Videos to Series", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select how you would like to import videos into this series:", color = TextMuted, fontSize = 13.sp)
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

    // ── Full-Screen Library Media Link Picker ──
    if (showLinkSheet) {
        FullLibraryLinkPickerModal(
            targetManga = manga,
            availableMedia = availableMediaForLinking,
            onDismiss = { showLinkSheet = false },
            onLinkSelected = { selectedIds, relationType ->
                onLinkMultipleExistingMedia(selectedIds, relationType)
            }
        )
    }

    // ── Unlink Confirmation Dialog ──
    itemToUnlink?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToUnlink = null },
            title = { Text("Unlink '${item.boxLabel ?: item.title}'?", color = Color.White) },
            text = { Text("This media will remain in your library as an independent item, but will no longer be linked to '${manga.title}'.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    onUnlinkRelated(item.id)
                    itemToUnlink = null
                }) {
                    Text("Unlink", color = Color(0xFFE6E8EC), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToUnlink = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg
        )
    }

    // ── Delete Confirmation Dialog (Franchise Item) ──
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete '${item.boxLabel ?: item.title}'?", color = DangerRed) },
            text = { Text("Are you sure you want to permanently delete this related entry and its media from your storage?", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRelated(item.id)
                    itemToDelete = null
                }) {
                    Text("Delete", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullLibraryLinkPickerModal(
    targetManga: MangaEntity,
    availableMedia: List<MangaEntity>,
    onDismiss: () -> Unit,
    onLinkSelected: (List<Long>, String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedRelation by remember { mutableStateOf(SeriesRelationType.SEASON) }
    val selectedIds = remember { mutableStateListOf<Long>() }

    val categoryPills = remember {
        listOf("All", "Videos", "Series", "Channels", "Movies", "OVAs & ONAs", "Specials")
    }

    val filteredMedia = remember(availableMedia, searchQuery, selectedCategory) {
        availableMedia.filter { item ->
            val matchesQuery = searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true)
            val rel = SeriesRelationType.fromPurpose(item.boxPurpose)
            val matchesCategory = when (selectedCategory) {
                "Videos" -> item.contentType == 2
                "Series" -> item.contentType == 2 && (item.boxPurpose == null || item.boxPurpose == "series")
                "Channels" -> item.contentType == 2 && item.boxPurpose == "channel"
                "Movies" -> rel == SeriesRelationType.MOVIE || item.boxPurpose?.contains("movie", ignoreCase = true) == true
                "OVAs & ONAs" -> rel == SeriesRelationType.OVA || rel == SeriesRelationType.ONA
                "Specials" -> rel == SeriesRelationType.SPECIAL || rel == SeriesRelationType.BLURAY
                else -> true
            }
            matchesQuery && matchesCategory
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F0F13)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // ── Top Header ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = "Link Media to Series",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Target: ${targetManga.title}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (selectedIds.isNotEmpty()) {
                        Button(
                            onClick = {
                                onLinkSelected(selectedIds.toList(), selectedRelation.id)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Link (${selectedIds.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // ── Search Bar ──
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search media to link...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // ── Category Pills (Media Type Filter) ──
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categoryPills) { cat ->
                        val isSelected = cat == selectedCategory
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = Color.White.copy(alpha = 0.07f),
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.15f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                // ── Relation Selector ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Assign As:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(SeriesRelationType.entries) { rel: SeriesRelationType ->
                            val isSelected = rel == selectedRelation
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedRelation = rel }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = rel.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = rel.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Grid of Available Media ──
                if (filteredMedia.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching media found" else "No available media to link",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredMedia, key = { it.id }) { item ->
                            val isSelected = selectedIds.contains(item.id)
                            val coverModel = remember(item.id) {
                                CoverArtResolver.resolveCoverModel(item.coverPath, item.parentUri, null, context)
                            }

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isSelected) {
                                            selectedIds.remove(item.id)
                                        } else {
                                            selectedIds.add(item.id)
                                        }
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (coverModel != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(coverModel)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.White.copy(alpha = 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Movie,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }

                                    // Gradient overlay at bottom for title legibility
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.5f)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                                )
                                            )
                                    )

                                    // Title at bottom
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                    )

                                    // Selection badge (Top Right)
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(24.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
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
}

