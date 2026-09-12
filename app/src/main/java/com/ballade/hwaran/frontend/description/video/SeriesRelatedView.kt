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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.CoverArtResolver
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeriesRelatedView(
    manga: MangaEntity,
    videos: List<ChapterEntity>,
    childBoxes: List<MangaEntity>,
    availableMediaForLinking: List<MangaEntity>,
    initialTab: String = "Videos",
    onNavigateBack: () -> Unit,
    onNavigateToVideo: (Long) -> Unit,
    onPickVideos: () -> Unit,
    onPickVideosFolder: () -> Unit,
    onDeleteVideos: (List<Long>) -> Unit,
    onChangeVideoThumbnail: (Long) -> Unit,
    onNavigateToRelated: (Long) -> Unit,
    onCreateRelatedBox: (String, String) -> Unit = { _, _ -> },
    onLinkExistingMedia: (Long, String, String?) -> Unit,
    onUnlinkRelated: (Long) -> Unit,
    onDeleteRelated: (Long) -> Unit,
    onRefreshAvailableMedia: () -> Unit
) {
    val context = LocalContext.current
    val CardBg = MaterialTheme.colorScheme.surface
    val PrimaryPurple = MaterialTheme.colorScheme.primary
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val DangerRed = Color(0xFFE57373)

    var selectedFilterCategory by remember(initialTab) { mutableStateOf(initialTab) }
    var selectedSort by remember { mutableStateOf(ChannelVideoSortOption.FIRST_TO_LAST) }
    var showSortSheet by remember { mutableStateOf(false) }
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

    val filterTabs = remember {
        listOf("Videos", "Seasons", "Movies", "OVAs & ONAs", "Specials & BD", "Prequels & Sequels", "Other")
    }

    val filteredBoxes = remember(childBoxes, selectedFilterCategory) {
        when (selectedFilterCategory) {
            "Seasons" -> childBoxes.filter {
                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                rel == SeriesRelationType.SEASON
            }
            "Movies" -> childBoxes.filter {
                val rel = SeriesRelationType.fromPurpose(it.boxPurpose)
                rel == SeriesRelationType.MOVIE
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

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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

                    Column {
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
                            fontSize = 11.sp
                        )
                    }
                }

                // Action Row based on active tab
                if (selectedFilterCategory == "Videos") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Filter / Sort Button
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
                                    tint = PrimaryPurple,
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
                                color = PrimaryPurple.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.5f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add Videos",
                                        tint = PrimaryPurple,
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
                        color = PrimaryPurple,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Link Media", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Category Filter Pills (Videos default, followed by franchise relation tags) ──
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        color = if (isSel) PrimaryPurple.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, if (isSel) PrimaryPurple else Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = tabLabel,
                            color = if (isSel) PrimaryPurple else Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
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
                                        .background(PrimaryPurple.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VideoLibrary,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
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
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 300.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(sortedVideos, key = { it.id }) { video ->
                            val isSelected = selectedVideoIds.contains(video.id)

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
                                    .clickable {
                                        if (isDeleteMode) {
                                            if (isSelected) selectedVideoIds.remove(video.id)
                                            else selectedVideoIds.add(video.id)
                                        } else {
                                            onNavigateToVideo(video.id)
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = CardBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) PrimaryPurple else Color.White.copy(alpha = 0.08f)
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

                                        // Duration Badge
                                        if (durationStr != null && !isDeleteMode) {
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
                                                        checkedColor = PrimaryPurple,
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
                                                color = if (isSelected) PrimaryPurple else Color.White,
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
                                        .background(PrimaryPurple.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountTree,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
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
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
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
                                            color = relationType.color.copy(alpha = 0.18f),
                                            border = BorderStroke(1.dp, relationType.color.copy(alpha = 0.45f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = relationType.icon,
                                                    contentDescription = null,
                                                    tint = relationType.color,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = relationType.displayName.uppercase(),
                                                    color = relationType.color,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
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

                                        DropdownMenu(
                                            expanded = showItemMenu,
                                            onDismissRequest = { showItemMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Open Details") },
                                                onClick = {
                                                    showItemMenu = false
                                                    onNavigateToRelated(boxItem.id)
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Unlink from Series") },
                                                onClick = {
                                                    showItemMenu = false
                                                    itemToUnlink = boxItem
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.LinkOff, contentDescription = null)
                                                }
                                            )
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Delete Entry", color = DangerRed) },
                                                onClick = {
                                                    showItemMenu = false
                                                    itemToDelete = boxItem
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

    // ── Luxury Sort Modal Bottom Sheet (Matching Manhua & Channel Screens) ──
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
                        color = if (isSelected) PrimaryPurple.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) PrimaryPurple.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f)
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
                                    color = if (isSelected) PrimaryPurple else Color.White,
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
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(20.dp)
                                )
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
                            Icon(Icons.Rounded.VideoFile, contentDescription = null, tint = PrimaryPurple)
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
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = PrimaryPurple)
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

    // ── Luxury Modal Bottom Sheet: Link Media from Library ──
    if (showLinkSheet) {
        var selectedExistingMedia by remember { mutableStateOf<MangaEntity?>(null) }
        var selectedRelationType by remember { mutableStateOf(SeriesRelationType.SEASON) }
        var customLabel by remember { mutableStateOf("") }
        var searchQuery by remember { mutableStateOf("") }
        var typeFilter by remember { mutableStateOf("All") }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showLinkSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF14121A),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Link from Library",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose media to link to \"${manga.title}\"",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = { showLinkSheet = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search library anime, movies, series...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Media Type Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Series", "Movies", "Books", "Manga").forEach { filter ->
                        val isSel = typeFilter == filter
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { typeFilter = filter },
                            color = if (isSel) PrimaryPurple.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, if (isSel) PrimaryPurple else Color.White.copy(alpha = 0.10f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = filter,
                                color = if (isSel) PrimaryPurple else TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Filtered Candidates from Library
                val candidates = remember(availableMediaForLinking, searchQuery, typeFilter) {
                    availableMediaForLinking.filter { candidate ->
                        val matchesSearch = searchQuery.isBlank() || candidate.title.contains(searchQuery.trim(), ignoreCase = true)
                        val matchesType = when (typeFilter) {
                            "Series" -> candidate.contentType == 2 && candidate.boxPurpose != "channel"
                            "Movies" -> candidate.contentType == 2 && candidate.boxPurpose?.contains("movie", ignoreCase = true) == true
                            "Books" -> candidate.contentType == 1
                            "Manga" -> candidate.contentType == 0
                            else -> true
                        }
                        matchesSearch && matchesType
                    }
                }

                // Candidates Selection Area
                if (candidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No library items match \"$searchQuery\"" else "No available media in library to link",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Text(
                        text = "SELECT MEDIA TO LINK",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidates, key = { it.id }) { candidate ->
                            val isSelected = selectedExistingMedia?.id == candidate.id
                            val candidateCover = remember(candidate.coverPath, candidate.parentUri) {
                                CoverArtResolver.resolveCoverModel(candidate.coverPath, candidate.parentUri, null, context)
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedExistingMedia = candidate
                                        if (customLabel.isBlank()) {
                                            customLabel = candidate.title
                                        }
                                    },
                                color = if (isSelected) PrimaryPurple.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) PrimaryPurple else Color.White.copy(alpha = 0.08f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Cover art
                                    Box(
                                        modifier = Modifier
                                            .size(width = 38.dp, height = 52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (candidateCover != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(candidateCover)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = candidate.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.Rounded.Movie, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.title,
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = when (candidate.contentType) {
                                                1 -> "Book / Novel"
                                                2 -> if (candidate.boxPurpose == "channel") "Channel" else "Series"
                                                else -> "Manga / Manhua"
                                            },
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (isSelected) {
                                        Surface(
                                            shape = CircleShape,
                                            color = PrimaryPurple,
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Relation Type Classification Section (Shown when media is selected)
                if (selectedExistingMedia != null) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ASSIGN RELATION TAG",
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        // Rich Visual Relation Tags
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            SeriesRelationType.allOptions.forEach { type ->
                                val isSel = selectedRelationType == type
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedRelationType = type },
                                    color = if (isSel) type.color.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(
                                        if (isSel) 1.5.dp else 1.dp,
                                        if (isSel) type.color else Color.White.copy(alpha = 0.10f)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = type.icon,
                                            contentDescription = null,
                                            tint = if (isSel) type.color else TextMuted,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = type.displayName,
                                            color = if (isSel) type.color else Color.White.copy(alpha = 0.85f),
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Optional Custom Label
                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { customLabel = it },
                        label = { Text("Display Label in Series (Optional)", fontSize = 12.sp) },
                        placeholder = { Text(selectedExistingMedia?.title ?: "e.g. Season 2, Movie 1", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryPurple,
                            focusedLabelColor = PrimaryPurple,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                // Link Action Button
                Button(
                    onClick = {
                        selectedExistingMedia?.let { media ->
                            onLinkExistingMedia(
                                media.id,
                                selectedRelationType.id,
                                customLabel.trim().ifBlank { null }
                            )
                            showLinkSheet = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    enabled = selectedExistingMedia != null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (selectedExistingMedia != null) "Link \"${selectedExistingMedia!!.title}\"" else "Select Media from Library",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                    Text("Unlink", color = PrimaryPurple, fontWeight = FontWeight.Bold)
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
