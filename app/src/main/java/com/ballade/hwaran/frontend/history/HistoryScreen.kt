package com.ballade.hwaran.frontend.history

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.HistoryEventEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.frontend.history.cards.*
import com.ballade.hwaran.frontend.history.models.*
import com.ballade.hwaran.frontend.history.neverwatched.*
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(
    settingsViewModel: SettingsViewModel,
    musicViewModel: MusicViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (Long) -> Unit,
    onNavigateToPdfReader: (Long) -> Unit,
    onNavigateToVideoPlayer: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToNowPlaying: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val eventsFlow = remember(database) { database.historyDao().getAllHistoryEventsFlow() }
    val rawEvents by eventsFlow.collectAsState(initial = emptyList())

    val sfwText by settingsViewModel.sfwText.collectAsState()
    val nsfwText by settingsViewModel.nsfwText.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // 0 = Timeline, 1 = Insights
    var activeViewMode by remember { mutableIntStateOf(0) }
    // Selected media filter tab for Timeline
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filterTabs = remember { listOf("All", "Series", "Channels", "Toons", "Books", "Music") }

    // Dialog trigger states
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showNeverWatchedPopup by remember { mutableStateOf(false) }
    var showNeverListenedPopup by remember { mutableStateOf(false) }

    // Database lookup maps
    var allMangaMap by remember { mutableStateOf<Map<Long, MangaEntity>>(emptyMap()) }
    var allChaptersMap by remember { mutableStateOf<Map<Long, ChapterEntity>>(emptyMap()) }
    var neverWatched by remember { mutableStateOf<List<UnwatchedItem>>(emptyList()) }
    var neverListenedPlaylists by remember { mutableStateOf<List<UnplayedPlaylist>>(emptyList()) }
    var isLoadingData by remember { mutableStateOf(true) }

    LaunchedEffect(rawEvents) {
        isLoadingData = true
        withContext(Dispatchers.IO) {
            val allManga = database.libraryDao().getAllMangaList()
            allMangaMap = allManga.associateBy { it.id }

            val chs = mutableListOf<ChapterEntity>()
            allManga.forEach { manga ->
                chs.addAll(database.trackDao().getChaptersForMangaList(manga.id))
            }
            allChaptersMap = chs.associateBy { it.id }

            // Build unwatched / unplayed sets
            val unwatchedVideosList = mutableListOf<UnwatchedItem>()
            val unreadToonsList = mutableListOf<UnwatchedItem>()
            val unreadBooksList = mutableListOf<UnwatchedItem>()

            val readBookTitles = mutableSetOf<String>()
            for (event in rawEvents) {
                if (event.eventType == "READ_BOOK") {
                    readBookTitles.add(event.itemName)
                }
            }

            allManga.forEach { manga ->
                val mangaChapters = chs.filter { it.mangaId == manga.id }
                when (manga.contentType) {
                    0 -> { // Toon
                        mangaChapters.forEach { ch ->
                            if (ch.openCount == 0) {
                                unreadToonsList.add(UnwatchedItem(manga.id, ch.id, ch.title, manga.title, 0))
                            }
                        }
                    }
                    1 -> { // Book
                        val isRead = manga.openCount > 0 || manga.title in readBookTitles
                        if (!isRead) {
                            unreadBooksList.add(UnwatchedItem(manga.id, null, manga.title.removeSuffix(".pdf"), manga.title, 1))
                        }
                    }
                    2 -> { // Video
                        mangaChapters.forEach { ch ->
                            if (ch.openCount == 0) {
                                unwatchedVideosList.add(UnwatchedItem(manga.id, ch.id, ch.title, manga.title, 2))
                            }
                        }
                    }
                }
            }

            val unplayedPlaylistsList = mutableListOf<UnplayedPlaylist>()
            allManga.filter { it.contentType == 3 }.forEach { playlist ->
                val playlistChapters = chs.filter { it.mangaId == playlist.id }
                val unplayed = playlistChapters.filter { it.openCount == 0 }
                if (unplayed.isNotEmpty()) {
                    unplayedPlaylistsList.add(UnplayedPlaylist(playlist.id, playlist.title, unplayed))
                }
            }

            withContext(Dispatchers.Main) {
                neverWatched = unreadToonsList + unwatchedVideosList + unreadBooksList
                neverListenedPlaylists = unplayedPlaylistsList
                isLoadingData = false
            }
        }
    }

    // Process typed media items
    val mediaItems = remember(rawEvents, allMangaMap, allChaptersMap) {
        groupMediaHistory(rawEvents, allMangaMap, allChaptersMap)
    }

    // Filtered media items based on active pill
    val displayedItems = remember(mediaItems, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> mediaItems.filterIsInstance<HistoryMediaItem.SeriesItem>()
            2 -> mediaItems.filterIsInstance<HistoryMediaItem.ChannelItem>()
            3 -> mediaItems.filterIsInstance<HistoryMediaItem.ToonItem>()
            4 -> mediaItems.filterIsInstance<HistoryMediaItem.BookItem>()
            5 -> mediaItems.filterIsInstance<HistoryMediaItem.MusicItem>()
            else -> mediaItems
        }
    }

    // Colors adhering to Library top pills specification:
    val selectedPillBg = Color(0xFF222631)
    val unselectedPillBg = Color.White.copy(alpha = 0.04f)
    val selectedPillBorder = Color.White.copy(alpha = 0.18f)
    val unselectedPillBorder = Color.White.copy(alpha = 0.08f)
    val selectedTextColor = Color(0xFFE6E8EC)
    val unselectedTextColor = Color.White.copy(alpha = 0.60f)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top Bar (Camera-safe zone: 48dp top padding) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 18.dp, end = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(selectedPillBg)
                            .border(1.dp, selectedPillBorder, CircleShape)
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = selectedTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Library History",
                        color = selectedTextColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Action Controls: View Mode Toggle + Clear History
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Insights / Timeline Toggle Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (activeViewMode == 1) selectedPillBg else unselectedPillBg)
                            .border(1.dp, if (activeViewMode == 1) selectedPillBorder else unselectedPillBorder, CircleShape)
                            .clickable {
                                activeViewMode = if (activeViewMode == 0) 1 else 0
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeViewMode == 1) Icons.Rounded.History else Icons.Rounded.Analytics,
                            contentDescription = "Toggle Insights",
                            tint = if (activeViewMode == 1) selectedTextColor else unselectedTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Clear History Button
                    if (rawEvents.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(unselectedPillBg)
                                .border(1.dp, unselectedPillBorder, CircleShape)
                                .clickable { showClearConfirmDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear History",
                                tint = unselectedTextColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // ── Timeline View ──
            if (activeViewMode == 0) {
                // Category Filter Pills
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterTabs.size) { index ->
                        val isSelected = selectedFilterIndex == index
                        val tab = filterTabs[index]
                        val count = when (index) {
                            1 -> mediaItems.count { it is HistoryMediaItem.SeriesItem }
                            2 -> mediaItems.count { it is HistoryMediaItem.ChannelItem }
                            3 -> mediaItems.count { it is HistoryMediaItem.ToonItem }
                            4 -> mediaItems.count { it is HistoryMediaItem.BookItem }
                            5 -> mediaItems.count { it is HistoryMediaItem.MusicItem }
                            else -> mediaItems.size
                        }
                        val label = if (count > 0 && index > 0) "$tab ($count)" else tab

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) selectedPillBg else unselectedPillBg)
                                .border(1.dp, if (isSelected) selectedPillBorder else unselectedPillBorder, RoundedCornerShape(12.dp))
                                .clickable { selectedFilterIndex = index }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) selectedTextColor else unselectedTextColor,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Main Timeline Cards
                if (displayedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(selectedPillBg)
                                    .border(1.dp, selectedPillBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HistoryToggleOff,
                                    contentDescription = null,
                                    tint = unselectedTextColor,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = "No media history yet",
                                color = selectedTextColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Episodes, chapters, books, and tracks you consume will appear here.",
                                color = unselectedTextColor,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(displayedItems, key = { "${it::class.simpleName}_${it.id}_${it.timestamp}" }) { item ->
                            when (item) {
                                is HistoryMediaItem.SeriesItem -> {
                                    SeriesHistoryCard(
                                        item = item.group,
                                        sfwText = sfwText,
                                        nsfwText = nsfwText,
                                        onNavigateToVideoPlayer = onNavigateToVideoPlayer
                                    )
                                }
                                is HistoryMediaItem.ChannelItem -> {
                                    ChannelHistoryCard(
                                        item = item.group,
                                        sfwText = sfwText,
                                        nsfwText = nsfwText,
                                        onNavigateToVideoPlayer = onNavigateToVideoPlayer
                                    )
                                }
                                is HistoryMediaItem.ToonItem -> {
                                    ToonHistoryCard(
                                        item = item.group,
                                        sfwText = sfwText,
                                        nsfwText = nsfwText,
                                        onNavigateToReader = onNavigateToReader
                                    )
                                }
                                is HistoryMediaItem.BookItem -> {
                                    BookHistoryCard(
                                        item = item.group,
                                        onNavigateToPdfReader = onNavigateToPdfReader
                                    )
                                }
                                is HistoryMediaItem.MusicItem -> {
                                    MusicHistoryCard(
                                        item = item.group,
                                        onNavigateToPlaylist = onNavigateToPlaylist
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ── Insights / Analytics View ──
                HistoryInsightsView(
                    mediaItems = mediaItems,
                    rawEvents = rawEvents,
                    neverWatchedCount = neverWatched.size,
                    neverListenedCount = neverListenedPlaylists.sumOf { it.unplayedSongs.size },
                    isLoading = isLoadingData,
                    onNeverWatchedClick = { showNeverWatchedPopup = true },
                    onNeverListenedClick = { showNeverListenedPopup = true }
                )
            }
        }

        // ── Clear History Confirmation Dialog ──
        if (showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showClearConfirmDialog = false },
                title = {
                    Text("Clear All History?", color = selectedTextColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                },
                text = {
                    Text(
                        "This will permanently delete all logged watch, read, and listen progress. Your media files will remain completely untouched.",
                        color = unselectedTextColor,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearConfirmDialog = false
                            coroutineScope.launch(Dispatchers.IO) {
                                database.historyDao().clearAllHistoryEvents()
                            }
                        }
                    ) {
                        Text("Clear All", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirmDialog = false }) {
                        Text("Cancel", color = unselectedTextColor)
                    }
                },
                containerColor = Color(0xFF14171F),
                shape = RoundedCornerShape(20.dp)
            )
        }

        // ── Never Watched Popup Dialog ──
        if (showNeverWatchedPopup) {
            NeverWatchedDialog(
                neverWatched = neverWatched,
                allMangaMap = allMangaMap,
                onNavigateToReader = onNavigateToReader,
                onNavigateToPdfReader = onNavigateToPdfReader,
                onNavigateToVideoPlayer = onNavigateToVideoPlayer,
                onDismiss = { showNeverWatchedPopup = false }
            )
        }

        // ── Never Listened Popup Dialog ──
        if (showNeverListenedPopup) {
            NeverListenedDialog(
                neverListenedPlaylists = neverListenedPlaylists,
                database = database,
                musicViewModel = musicViewModel,
                onNavigateToNowPlaying = onNavigateToNowPlaying,
                onDismiss = { showNeverListenedPopup = false }
            )
        }
    }
}

@Composable
private fun HistoryInsightsView(
    mediaItems: List<HistoryMediaItem>,
    rawEvents: List<HistoryEventEntity>,
    neverWatchedCount: Int,
    neverListenedCount: Int,
    isLoading: Boolean,
    onNeverWatchedClick: () -> Unit,
    onNeverListenedClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    val seriesCount = remember(mediaItems) { mediaItems.count { it is HistoryMediaItem.SeriesItem } }
    val channelCount = remember(mediaItems) { mediaItems.count { it is HistoryMediaItem.ChannelItem } }
    val toonCount = remember(mediaItems) { mediaItems.count { it is HistoryMediaItem.ToonItem } }
    val bookCount = remember(mediaItems) { mediaItems.count { it is HistoryMediaItem.BookItem } }
    val musicCount = remember(mediaItems) { mediaItems.count { it is HistoryMediaItem.MusicItem } }

    val totalWatchMs = remember(mediaItems) {
        mediaItems.filterIsInstance<HistoryMediaItem.SeriesItem>().sumOf { it.group.totalTimeSpentMs } +
                mediaItems.filterIsInstance<HistoryMediaItem.ChannelItem>().sumOf { it.group.totalTimeSpentMs }
    }
    val watchTimeStr = formatDurationTime(totalWatchMs)

    // Most played music tracks
    val topTracks = remember(rawEvents) {
        rawEvents.filter { it.eventType == "LISTEN" }
            .groupBy { it.itemName }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(3)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Stat Cards Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InsightStatCard(
                title = "Total Watch Time",
                value = watchTimeStr,
                icon = Icons.Rounded.Timelapse,
                modifier = Modifier.weight(1f)
            )
            InsightStatCard(
                title = "Media Consumed",
                value = mediaItems.size.toString(),
                icon = Icons.Rounded.PlayCircle,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InsightStatCard(
                title = "Series Watched",
                value = seriesCount.toString(),
                icon = Icons.Rounded.Movie,
                modifier = Modifier.weight(1f)
            )
            InsightStatCard(
                title = "Books & Toons",
                value = (bookCount + toonCount).toString(),
                icon = Icons.Rounded.Book,
                modifier = Modifier.weight(1f)
            )
        }

        // Most Played Music Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFFD391B0), modifier = Modifier.size(18.dp))
                    Text(
                        text = "Most Played Tracks",
                        color = Color(0xFFE6E8EC),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (topTracks.isEmpty()) {
                    Text("No music playback logged yet.", color = Color.White.copy(alpha = 0.45f), fontSize = 12.5.sp)
                } else {
                    val maxCount = topTracks.firstOrNull()?.second?.toFloat() ?: 1f
                    topTracks.forEach { (title, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$count plays",
                                color = Color(0xFFD391B0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { count.toFloat() / maxCount },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = Color(0xFFD391B0),
                            trackColor = Color.White.copy(alpha = 0.06f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // Never Watched Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onNeverWatchedClick),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Never Watched Content", color = Color(0xFFE6E8EC), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isLoading) {
                        Text("Loading...", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                    } else {
                        Text(
                            text = if (neverWatchedCount == 0) "All media has been opened!" else "$neverWatchedCount unseen items available",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.5.sp
                        )
                    }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
            }
        }

        // Never Listened Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onNeverListenedClick),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Never Played Music", color = Color(0xFFE6E8EC), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isLoading) {
                        Text("Loading...", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                    } else {
                        Text(
                            text = if (neverListenedCount == 0) "All playlist songs have been played!" else "$neverListenedCount unplayed songs remaining",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.5.sp
                        )
                    }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun InsightStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFE6E8EC),
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun NeverWatchedDialog(
    neverWatched: List<UnwatchedItem>,
    allMangaMap: Map<Long, MangaEntity>,
    onNavigateToReader: (Long) -> Unit,
    onNavigateToPdfReader: (Long) -> Unit,
    onNavigateToVideoPlayer: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var unwatchedTab by remember { mutableIntStateOf(0) } // 0 = Toon, 1 = Video, 2 = Book

    val toonsByManga = remember(neverWatched, allMangaMap) {
        neverWatched.filter { it.contentType == 0 }
            .groupBy { it.mangaId }
            .mapNotNull { (mangaId, items) ->
                val manga = allMangaMap[mangaId] ?: return@mapNotNull null
                manga to items
            }
    }

    val unreadBooks = remember(neverWatched) { neverWatched.filter { it.contentType == 1 } }

    var videoSubTab by remember { mutableIntStateOf(0) }
    val unwatchedVideosByManga = remember(neverWatched) {
        neverWatched.filter { it.contentType == 2 }.groupBy { it.mangaId }
    }
    val videoMangaList = remember(allMangaMap) {
        allMangaMap.values.filter { it.contentType == 2 }
    }
    val unwatchedChannels = remember(videoMangaList, unwatchedVideosByManga) {
        videoMangaList.filter { it.boxPurpose == "channel" }
            .mapNotNull { channel ->
                val items = unwatchedVideosByManga[channel.id]
                if (!items.isNullOrEmpty()) channel to items else null
            }
    }
    val unwatchedSeriesTree = remember(videoMangaList, unwatchedVideosByManga) {
        val seriesManga = videoMangaList.filter { it.boxPurpose != "channel" }
        buildSeriesTree(seriesManga, unwatchedVideosByManga)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Never Watched Content", color = Color(0xFFE6E8EC), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Sub-tabs Selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Toon", "Video", "Book").forEachIndexed { index, name ->
                        val isSel = unwatchedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Color(0xFF222631) else Color.White.copy(alpha = 0.04f))
                                .border(1.dp, if (isSel) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .clickable { unwatchedTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, color = if (isSel) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.60f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    when (unwatchedTab) {
                        0 -> ToonNeverWatched(toonsByManga = toonsByManga, onNavigateToReader = onNavigateToReader, onClose = onDismiss)
                        1 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Series", "Channel").forEachIndexed { index, name ->
                                        val isSel = videoSubTab == index
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) Color(0xFF222631) else Color.White.copy(alpha = 0.04f))
                                                .border(1.dp, if (isSel) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                .clickable { videoSubTab = index }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(name, color = if (isSel) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.60f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    if (videoSubTab == 1) {
                                        ChannelNeverWatched(unwatchedChannels = unwatchedChannels, onNavigateToVideoPlayer = onNavigateToVideoPlayer, onClose = onDismiss)
                                    } else {
                                        SeriesNeverWatched(unwatchedSeriesTree = unwatchedSeriesTree, onNavigateToVideoPlayer = onNavigateToVideoPlayer, onClose = onDismiss)
                                    }
                                }
                            }
                        }
                        else -> BookNeverWatched(unreadBooks = unreadBooks, onNavigateToPdfReader = onNavigateToPdfReader, allMangaMap = allMangaMap, onClose = onDismiss)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFFE6E8EC), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF14171F),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun NeverListenedDialog(
    neverListenedPlaylists: List<UnplayedPlaylist>,
    database: AppDatabase,
    musicViewModel: MusicViewModel,
    onNavigateToNowPlaying: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Never Listened Music", color = Color(0xFFE6E8EC), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                PlaylistNeverListened(
                    neverListenedPlaylists = neverListenedPlaylists,
                    database = database,
                    musicViewModel = musicViewModel,
                    onNavigateToNowPlaying = onNavigateToNowPlaying,
                    onClose = onDismiss
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFFE6E8EC), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF14171F),
        shape = RoundedCornerShape(20.dp)
    )
}
