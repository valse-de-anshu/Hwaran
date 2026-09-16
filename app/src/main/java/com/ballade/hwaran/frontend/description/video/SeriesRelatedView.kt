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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import com.ballade.hwaran.frontend.home.LibraryView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import java.io.File
import com.ballade.hwaran.ui.components.DeleteConfirmationDialog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onDeleteVideos: (List<Long>, Boolean) -> Unit,
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

    var customCategories by remember(manga.id) { mutableStateOf(listOf<String>()) }
    var showAddCustomCategoryDialog by remember { mutableStateOf(false) }
    var newCustomCategoryName by remember { mutableStateOf("") }
    var pillToRename by remember { mutableStateOf<String?>(null) }
    var renameCustomCategoryInput by remember { mutableStateOf("") }
    var pillToDelete by remember { mutableStateOf<String?>(null) }

    val builtInTabs = remember {
        listOf("Videos", "Seasons", "Movies", "OVAs", "ONAs", "Specials", "Blu-ray", "Sequels", "Prequels")
    }

    val dynamicCustomTabs = remember(childBoxes, customCategories) {
        val childPurposes = childBoxes.mapNotNull { it.boxPurpose?.trim() }.filter { purpose ->
            val clean = purpose.lowercase()
            clean.isNotBlank() && clean != "series" && clean != "season" && clean != "seasons" && clean != "video" && clean != "videos" &&
            builtInTabs.none { tab -> tab.equals(clean, ignoreCase = true) } &&
            SeriesRelationType.entries.none { rel -> rel.id.equals(clean, ignoreCase = true) }
        }.map { it.replaceFirstChar { char -> char.uppercase() } }
        (childPurposes + customCategories).distinct()
    }

    val filterTabs = remember(dynamicCustomTabs) {
        builtInTabs + dynamicCustomTabs
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
            "OVAs" -> childBoxes.filter {
                SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.OVA
            }
            "ONAs" -> childBoxes.filter {
                SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.ONA
            }
            "Specials" -> childBoxes.filter {
                SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.SPECIAL
            }
            "Blu-ray" -> childBoxes.filter {
                SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.BLURAY
            }
            "Sequels" -> childBoxes.filter {
                SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.SEQUEL
            }
            "Prequels" -> childBoxes.filter {
                SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.PREQUEL
            }
            else -> childBoxes.filter {
                it.boxPurpose?.equals(selectedFilterCategory, ignoreCase = true) == true
            }
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
                        val isCustomTab = dynamicCustomTabs.contains(tab)
                        var showPillMenu by remember { mutableStateOf(false) }

                        val tabCount = when (tab) {
                            "Videos" -> videos.size
                            "Seasons" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.SEASON || (it.parentMangaId == null && (it.boxPurpose == null || it.boxPurpose == "series")) }
                            "Movies" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.MOVIE || it.boxPurpose?.contains("movie", ignoreCase = true) == true }
                            "OVAs" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.OVA }
                            "ONAs" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.ONA }
                            "Specials" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.SPECIAL }
                            "Blu-ray" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.BLURAY }
                            "Sequels" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.SEQUEL }
                            "Prequels" -> childBoxes.count { SeriesRelationType.fromPurpose(it.boxPurpose) == SeriesRelationType.PREQUEL }
                            else -> childBoxes.count { it.boxPurpose?.equals(tab, ignoreCase = true) == true }
                        }

                        val tabLabel = if (tabCount > 0) "$tab ($tabCount)" else tab

                        Box {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (isDeleteMode && tab != "Videos") {
                                                isDeleteMode = false
                                                selectedVideoIds.clear()
                                            }
                                            selectedFilterCategory = tab
                                        },
                                        onLongClick = if (isCustomTab) {
                                            {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showPillMenu = true
                                            }
                                        } else null
                                    ),
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

                            if (isCustomTab) {
                                HwaranDropdownMenu(
                                    expanded = showPillMenu,
                                    onDismissRequest = { showPillMenu = false }
                                ) {
                                    HwaranDropdownMenuItem(
                                        text = "Rename Category",
                                        onClick = {
                                            showPillMenu = false
                                            pillToRename = tab
                                            renameCustomCategoryInput = tab
                                        },
                                        leadingIcon = Icons.Rounded.Edit
                                    )
                                    HwaranDropdownMenuItem(
                                        text = "Delete Category",
                                        onClick = {
                                            showPillMenu = false
                                            pillToDelete = tab
                                        },
                                        leadingIcon = Icons.Rounded.Delete,
                                        isDanger = true
                                    )
                                }
                            }
                        }
                    }

                    // ── '+' Icon Pill to Add Custom Category ──
                    item {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    newCustomCategoryName = ""
                                    showAddCustomCategoryDialog = true
                                },
                            color = Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add Custom Category",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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
                                         } else {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = true)
                                                val downPos = down.position
                                                val tapSlopPx = 18.dp.toPx()
                                                var isHeld = false
                                                var isDragScroll = false

                                                val previewJob = coroutineScope.launch {
                                                    delay(350L)
                                                    if (!isDragScroll) {
                                                        isHeld = true
                                                        showPreview = true
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }

                                                try {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val pointer = event.changes.find { it.id == down.id }
                                                        if (pointer == null || !pointer.pressed) {
                                                            break
                                                        }
                                                        if (!isHeld) {
                                                            val distance = (pointer.position - downPos).getDistance()
                                                            if (distance > tapSlopPx) {
                                                                isDragScroll = true
                                                                previewJob.cancel()
                                                            }
                                                        }
                                                    }
                                                } catch (_: Exception) {
                                                } finally {
                                                    previewJob.cancel()
                                                    if (isHeld) {
                                                        showPreview = false
                                                    } else if (!isDragScroll) {
                                                        onNavigateToVideo(video.id)
                                                    }
                                                }
                                            }
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
        DeleteConfirmationDialog(
            title = "Delete ${selectedVideoIds.size} Video${if (selectedVideoIds.size == 1) "" else "s"}",
            message = "Choose how you would like to remove the selected video(s):",
            onDismiss = { showDeleteConfirmDialog = false },
            onRemoveFromApp = {
                showDeleteConfirmDialog = false
                onDeleteVideos(selectedVideoIds.toList(), false)
                selectedVideoIds.clear()
                isDeleteMode = false
            },
            onDeleteFromDisk = {
                showDeleteConfirmDialog = false
                onDeleteVideos(selectedVideoIds.toList(), true)
                selectedVideoIds.clear()
                isDeleteMode = false
            }
        )
    }

    // ── Single Video Delete Confirmation Dialog ──
    videoPendingDelete?.let { video ->
        DeleteConfirmationDialog(
            title = "Delete Video",
            itemName = video.title,
            message = "Choose how you would like to remove this video:",
            onDismiss = { videoPendingDelete = null },
            onRemoveFromApp = {
                onDeleteVideos(listOf(video.id), false)
                videoPendingDelete = null
            },
            onDeleteFromDisk = {
                onDeleteVideos(listOf(video.id), true)
                videoPendingDelete = null
            }
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

    // ── Library Media Link Picker (Reusing Existing LibraryView) ──
    if (showLinkSheet) {
        Dialog(
            onDismissRequest = { showLinkSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val view = LocalView.current
            DisposableEffect(view) {
                val window = (view.parent as? DialogWindowProvider)?.window
                if (window != null) {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        window.attributes.layoutInDisplayCutoutMode =
                            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                onDispose {}
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F0F13)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .displayCutoutPadding()
                ) {
                    // ── Safe Header Bar (Below Camera Cutout & Status Bar) ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { showLinkSheet = false },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Select Media to Link",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val targetRelation = when (selectedFilterCategory) {
                                "Movies" -> "Movie"
                                "OVAs" -> "OVA"
                                "ONAs" -> "ONA"
                                "Specials" -> "Special"
                                "Blu-ray" -> "Blu-ray"
                                "Sequels" -> "Sequel"
                                "Prequels" -> "Prequel"
                                "Videos" -> "Season"
                                "Seasons" -> "Season"
                                else -> selectedFilterCategory
                            }
                            Text(
                                text = "Linking to '${manga.title}' as $targetRelation",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val currentRelationId = remember(selectedFilterCategory) {
                        when (selectedFilterCategory) {
                            "Movies" -> SeriesRelationType.MOVIE.id
                            "OVAs" -> SeriesRelationType.OVA.id
                            "ONAs" -> SeriesRelationType.ONA.id
                            "Specials" -> SeriesRelationType.SPECIAL.id
                            "Blu-ray" -> SeriesRelationType.BLURAY.id
                            "Sequels" -> SeriesRelationType.SEQUEL.id
                            "Prequels" -> SeriesRelationType.PREQUEL.id
                            "Videos" -> SeriesRelationType.SEASON.id
                            "Seasons" -> SeriesRelationType.SEASON.id
                            else -> selectedFilterCategory.lowercase().trim()
                        }
                    }

                    LibraryView(
                        allManga = availableMediaForLinking,
                        onNavigateToDescription = { selectedId ->
                            onLinkExistingMedia(selectedId, currentRelationId, null)
                            showLinkSheet = false
                        },
                        initialTag = "Series",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
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
        DeleteConfirmationDialog(
            title = "Delete Related Entry",
            itemName = item.boxLabel ?: item.title,
            message = "Choose how you would like to remove this franchise entry:",
            onDismiss = { itemToDelete = null },
            onRemoveFromApp = {
                onDeleteRelated(item.id)
                itemToDelete = null
            },
            onDeleteFromDisk = {
                onDeleteRelated(item.id)
                itemToDelete = null
            }
        )
    }

    // ── Add Custom Category Dialog ──
    if (showAddCustomCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomCategoryDialog = false },
            title = { Text("Add Custom Category", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a custom category name for this franchise (e.g. BTS, Drama CD, Trailer):", color = TextMuted, fontSize = 13.sp)
                    OutlinedTextField(
                        value = newCustomCategoryName,
                        onValueChange = { newCustomCategoryName = it },
                        placeholder = { Text("Category name...", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newCustomCategoryName.trim()
                        if (trimmed.isNotBlank()) {
                            val formatted = trimmed.replaceFirstChar { it.uppercase() }
                            if (!customCategories.contains(formatted)) {
                                customCategories = customCategories + formatted
                            }
                            selectedFilterCategory = formatted
                        }
                        showAddCustomCategoryDialog = false
                    }
                ) {
                    Text("Add", color = Color(0xFFE6E8EC), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomCategoryDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg
        )
    }

    // ── Rename Custom Category Dialog ──
    pillToRename?.let { oldName ->
        AlertDialog(
            onDismissRequest = { pillToRename = null },
            title = { Text("Rename '$oldName'", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter new category name:", color = TextMuted, fontSize = 13.sp)
                    OutlinedTextField(
                        value = renameCustomCategoryInput,
                        onValueChange = { renameCustomCategoryInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameCustomCategoryInput.trim()
                        if (trimmed.isNotBlank() && !trimmed.equals(oldName, ignoreCase = true)) {
                            val newFormatted = trimmed.replaceFirstChar { it.uppercase() }
                            customCategories = customCategories.map { if (it.equals(oldName, ignoreCase = true)) newFormatted else it }
                            childBoxes.filter { it.boxPurpose?.equals(oldName, ignoreCase = true) == true }.forEach { item ->
                                onLinkExistingMedia(item.id, newFormatted.lowercase(), item.boxLabel)
                            }
                            if (selectedFilterCategory.equals(oldName, ignoreCase = true)) {
                                selectedFilterCategory = newFormatted
                            }
                        }
                        pillToRename = null
                    }
                ) {
                    Text("Rename", color = Color(0xFFE6E8EC), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pillToRename = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg
        )
    }

    // ── Delete Custom Category Dialog ──
    pillToDelete?.let { targetPill ->
        AlertDialog(
            onDismissRequest = { pillToDelete = null },
            title = { Text("Delete '$targetPill' Category?", color = DangerRed) },
            text = { Text("Are you sure you want to delete the '$targetPill' category pill? Linked media in this category will be preserved and reassigned as Seasons.", color = TextMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        customCategories = customCategories.filter { !it.equals(targetPill, ignoreCase = true) }
                        childBoxes.filter { it.boxPurpose?.equals(targetPill, ignoreCase = true) == true }.forEach { item ->
                            onLinkExistingMedia(item.id, "season", item.boxLabel)
                        }
                        if (selectedFilterCategory.equals(targetPill, ignoreCase = true)) {
                            selectedFilterCategory = "Videos"
                        }
                        pillToDelete = null
                    }
                ) {
                    Text("Delete", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pillToDelete = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg
        )
    }
}



