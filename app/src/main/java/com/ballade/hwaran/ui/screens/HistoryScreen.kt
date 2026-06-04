package com.ballade.hwaran.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.data.local.AppDatabase
import com.ballade.hwaran.data.local.HistoryEventEntity
import com.ballade.hwaran.data.local.MangaEntity
import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.ui.components.PremiumGlassPanel
import com.ballade.hwaran.ui.theme.LocalAppGradient
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

fun truncateMiddle(text: String, maxLength: Int = 28): String {
    if (text.length <= maxLength) return text
    
    val words = text.split(" ")
    if (words.size < 2) {
        val firstPart = text.take(maxLength / 2 - 2)
        val lastPart = text.takeLast(maxLength / 2 - 3)
        return "$firstPart.....$lastPart"
    }
    
    val lastWord = words.last()
    var firstPart = ""
    for (i in 0 until words.size - 1) {
        val next = if (firstPart.isEmpty()) words[i] else "$firstPart ${words[i]}"
        if (next.length + 5 + lastWord.length + 1 <= maxLength) {
            firstPart = next
        } else {
            break
        }
    }
    
    if (firstPart.isEmpty()) {
        val first = text.take(10)
        val last = text.takeLast(8)
        return "$first ..... $last"
    }
    
    return "$firstPart ..... $lastWord"
}

// Helper structures for structured event parser
data class EventMetadata(
    val mangaId: Long? = null,
    val chapterId: Long? = null,
    val pages: String? = null,
    val totalPages: Int? = null,
    val fallback: String = ""
)

fun parseDetails(details: String): EventMetadata {
    val parts = details.split("|").map { it.trim() }
    var mangaId: Long? = null
    var chapterId: Long? = null
    var pages: String? = null
    var totalPages: Int? = null
    var fallback = ""
    for (part in parts) {
        if (part.startsWith("mangaId:")) {
            mangaId = part.substringAfter("mangaId:").toLongOrNull()
        } else if (part.startsWith("chapterId:")) {
            chapterId = part.substringAfter("chapterId:").toLongOrNull()
        } else if (part.startsWith("pages:")) {
            pages = part.substringAfter("pages:")
        } else if (part.startsWith("totalPages:")) {
            totalPages = part.substringAfter("totalPages:").toIntOrNull()
        } else if (part.startsWith("fallback:")) {
            fallback = part.substringAfter("fallback:")
        } else {
            if (fallback.isEmpty()) {
                fallback = part
            } else {
                fallback += " | $part"
            }
        }
    }
    return EventMetadata(mangaId, chapterId, pages, totalPages, fallback)
}

data class HistoryTimelineItem(
    val id: Long,
    val eventType: String,
    val mangaId: Long? = null,
    val title: String,
    val details: String,
    val timestamp: Long,
    val isGroupedLifecycle: Boolean = false,
    val isGroupedMedia: Boolean = false,
    val occurrences: List<HistoryEventEntity> = emptyList()
)

data class UnwatchedItem(
    val mangaId: Long,
    val chapterId: Long?,
    val title: String,
    val parentTitle: String,
    val contentType: Int
)

data class UnplayedPlaylist(
    val mangaId: Long,
    val title: String,
    val unplayedSongs: List<ChapterEntity>
)

data class SeriesNode(
    val manga: MangaEntity,
    val unwatchedVideos: List<UnwatchedItem>,
    val children: List<SeriesNode>
)

fun buildSeriesTree(
    mangaList: List<MangaEntity>,
    unwatchedMap: Map<Long, List<UnwatchedItem>>
): List<SeriesNode> {
    val childrenMap = mangaList.groupBy { it.parentMangaId }
    
    fun buildNode(manga: MangaEntity): SeriesNode? {
        val directUnwatched = unwatchedMap[manga.id] ?: emptyList()
        val childNodes = childrenMap[manga.id]?.mapNotNull { buildNode(it) } ?: emptyList()
        
        if (directUnwatched.isNotEmpty() || childNodes.isNotEmpty()) {
            return SeriesNode(manga, directUnwatched, childNodes)
        }
        return null
    }
    
    val roots = mangaList.filter { it.parentMangaId == null }
    return roots.mapNotNull { buildNode(it) }
}

fun Modifier.fadedScrollEdges(): Modifier = this
    .graphicsLayer { alpha = 0.99f }
    .drawWithContent {
        drawContent()
        val fadeHeight = 30.dp.toPx()
        if (size.height > 0f) {
            val stop1 = (fadeHeight / size.height).coerceIn(0f, 1f)
            val stop2 = ((size.height - fadeHeight) / size.height).coerceIn(0f, 1f)
            val brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    stop1 to Color.Black,
                    stop2 to Color.Black,
                    1f to Color.Transparent
                )
            )
            drawRect(
                brush = brush,
                blendMode = BlendMode.DstIn
            )
        }
    }

data class CollapsedBranchItem(
    val label: String,
    val timestamp: Long,
    val count: Int,
    val occ: HistoryEventEntity
)

fun collapseConsecutiveBranches(
    events: List<HistoryEventEntity>, 
    contentType: Int?
): List<CollapsedBranchItem> {
    if (events.isEmpty()) return emptyList()
    
    val map = linkedMapOf<String, CollapsedBranchItem>()
    
    for (occ in events) {
        val meta = parseDetails(occ.details)
        if (contentType == 1) {
            val pagesAttr = meta.pages
            if (pagesAttr != null) {
                val tokens = pagesAttr.split(",")
                for (token in tokens) {
                    val trimmed = token.trim()
                    if (trimmed.isEmpty()) continue
                    
                    val pageLabels = if (trimmed.contains("-")) {
                        val parts = trimmed.split("-")
                        val start = parts.getOrNull(0)?.toIntOrNull()
                        val end = parts.getOrNull(1)?.toIntOrNull()
                        if (start != null && end != null && start <= end) {
                            (start..end).map { "Page $it" }
                        } else {
                            listOf("Page $trimmed")
                        }
                    } else {
                        listOf("Page $trimmed")
                    }
                    
                    for (label in pageLabels) {
                        val existing = map[label]
                        if (existing != null) {
                            map[label] = existing.copy(
                                count = existing.count + 1,
                                timestamp = occ.timestamp,
                                occ = occ
                            )
                        } else {
                            map[label] = CollapsedBranchItem(
                                label = label,
                                timestamp = occ.timestamp,
                                count = 1,
                                occ = occ
                            )
                        }
                    }
                }
            } else {
                val label = "Page read"
                val existing = map[label]
                if (existing != null) {
                    map[label] = existing.copy(
                        count = existing.count + 1,
                        timestamp = occ.timestamp,
                        occ = occ
                    )
                } else {
                    map[label] = CollapsedBranchItem(
                        label = label,
                        timestamp = occ.timestamp,
                        count = 1,
                        occ = occ
                    )
                }
            }
        } else {
            val label = occ.itemName
            val existing = map[label]
            if (existing != null) {
                map[label] = existing.copy(
                    count = existing.count + 1,
                    timestamp = occ.timestamp,
                    occ = occ
                )
            } else {
                map[label] = CollapsedBranchItem(
                    label = label,
                    timestamp = occ.timestamp,
                    count = 1,
                    occ = occ
                )
            }
        }
    }
    return map.values.toList()
}

fun groupHistoryEvents(events: List<HistoryEventEntity>, allMangaMap: Map<Long, MangaEntity>): List<HistoryTimelineItem> {
    val filtered = events.filter { it.eventType != "APP_OPEN" && it.eventType != "APP_CLOSE" }
    if (filtered.isEmpty()) return emptyList()
    
    // Group all lifecycle events globally
    val imports = filtered.filter { it.eventType == "IMPORT" }
    val deletes = filtered.filter { it.eventType == "DELETE" }
    val books = filtered.filter { it.eventType == "READ_BOOK" }
    val videos = filtered.filter { it.eventType == "WATCH" }
    val toons = filtered.filter { it.eventType == "READ_TOON" }
    val music = filtered.filter { it.eventType == "LISTEN" }
    
    val groupedList = mutableListOf<HistoryTimelineItem>()
    
    if (imports.isNotEmpty()) {
        groupedList.add(
            HistoryTimelineItem(
                id = -300L,
                eventType = "IMPORT",
                title = "Imported Folder",
                details = "",
                timestamp = imports.first().timestamp,
                isGroupedLifecycle = true,
                occurrences = imports
            )
        )
    }
    if (deletes.isNotEmpty()) {
        groupedList.add(
            HistoryTimelineItem(
                id = -400L,
                eventType = "DELETE",
                title = "Deleted Item",
                details = "",
                timestamp = deletes.first().timestamp,
                isGroupedLifecycle = true,
                occurrences = deletes
            )
        )
    }
    if (music.isNotEmpty()) {
        groupedList.add(
            HistoryTimelineItem(
                id = -800L,
                eventType = "LISTEN",
                title = "Music",
                details = "",
                timestamp = music.first().timestamp,
                isGroupedMedia = true,
                occurrences = music
            )
        )
    }
    
    // Group Media by Workspace
    fun getWorkspace(detailsStr: String): String {
        val mangaId = parseDetails(detailsStr).mangaId
        return mangaId?.let { allMangaMap[it]?.workspace }?.takeIf { it.isNotEmpty() } ?: "Unlabeled"
    }

    if (books.isNotEmpty()) {
        val grouped = books.groupBy { getWorkspace(it.details) }
        grouped.forEach { (workspace, occs) ->
            groupedList.add(
                HistoryTimelineItem(
                    id = -500L - workspace.hashCode(),
                    eventType = "READ_BOOK",
                    title = "Books",
                    details = workspace, // Using details to store workspace label temporarily
                    timestamp = occs.first().timestamp,
                    isGroupedMedia = true,
                    occurrences = occs
                )
            )
        }
    }
    if (videos.isNotEmpty()) {
        val grouped = videos.groupBy { getWorkspace(it.details) }
        grouped.forEach { (workspace, occs) ->
            groupedList.add(
                HistoryTimelineItem(
                    id = -600L - workspace.hashCode(),
                    eventType = "WATCH",
                    title = "Videos",
                    details = workspace, // Using details to store workspace label
                    timestamp = occs.first().timestamp,
                    isGroupedMedia = true,
                    occurrences = occs
                )
            )
        }
    }
    if (toons.isNotEmpty()) {
        val grouped = toons.groupBy { getWorkspace(it.details) }
        grouped.forEach { (workspace, occs) ->
            groupedList.add(
                HistoryTimelineItem(
                    id = -700L - workspace.hashCode(),
                    eventType = "READ_TOON",
                    title = "Toons",
                    details = workspace, // Using details to store workspace label
                    timestamp = occs.first().timestamp,
                    isGroupedMedia = true,
                    occurrences = occs
                )
            )
        }
    }
    val otherEvents = filtered.filter { it.eventType !in listOf("IMPORT", "DELETE", "READ_BOOK", "WATCH", "READ_TOON", "LISTEN") }
    
    var i = 0
    while (i < otherEvents.size) {
        val current = otherEvents[i]
        groupedList.add(
            HistoryTimelineItem(
                id = current.id,
                eventType = current.eventType,
                title = current.itemName,
                details = current.details,
                timestamp = current.timestamp,
                occurrences = listOf(current)
            )
        )
        i++
    }
    fun getCategoryPriority(eventType: String): Int {
        return when (eventType) {
            "WATCH" -> 1
            "LISTEN" -> 2
            "READ_TOON" -> 3
            "READ_BOOK" -> 4
            "IMPORT" -> 5
            "DELETE" -> 6
            else -> 7
        }
    }
    return groupedList.sortedWith(
        compareBy<HistoryTimelineItem> { getCategoryPriority(it.eventType) }
            .thenByDescending { it.timestamp }
    )
}

@Composable
fun SeriesNodeView(
    node: SeriesNode,
    indentation: Int = 0,
    onVideoClick: (Long) -> Unit
) {
    var isExpanded by remember(node.manga.id) { mutableStateOf(false) }
    var visibleCount by remember(node.manga.id) { mutableIntStateOf(10) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 6.dp, horizontal = (indentation * 12).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = truncateMiddle(node.manga.title),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
        
        if (isExpanded) {
            // Render subfolders (child nodes) first
            node.children.forEach { childNode ->
                SeriesNodeView(
                    node = childNode,
                    indentation = indentation + 1,
                    onVideoClick = onVideoClick
                )
            }
            
            // Render unwatched videos of this folder
            val displayItems = node.unwatchedVideos.take(visibleCount)
            displayItems.forEachIndexed { idx, video ->
                val isLast = idx == displayItems.size - 1 && visibleCount >= node.unwatchedVideos.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { video.chapterId?.let { onVideoClick(it) } }
                        .padding(vertical = 4.dp, horizontal = ((indentation + 1) * 12 + 8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLast) "└── " else "├── ",
                        color = Color.White.copy(alpha = 0.15f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = truncateMiddle(video.title),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (visibleCount < node.unwatchedVideos.size) {
                val remaining = node.unwatchedVideos.size - visibleCount
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { visibleCount += 10 }.padding(vertical = 4.dp, horizontal = ((indentation + 1) * 12 + 8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "└── ",
                        color = Color.White.copy(alpha = 0.15f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("⌄ $remaining", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    settingsViewModel: SettingsViewModel,
    musicViewModel: com.ballade.hwaran.ui.viewmodels.MusicViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (Long) -> Unit,
    onNavigateToPdfReader: (Long) -> Unit,
    onNavigateToVideoPlayer: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToNowPlaying: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val eventsFlow = remember(database) { database.libraryDao().getAllHistoryEventsFlow() }
    val rawEvents by eventsFlow.collectAsState(initial = emptyList())
    val events = remember(rawEvents) { rawEvents.filter { it.eventType != "THEME_CHANGE" } }

    val activeTheme by settingsViewModel.appTheme.collectAsState()
    val sfwText by settingsViewModel.sfwText.collectAsState()
    val nsfwText by settingsViewModel.nsfwText.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Insights, 1 = Timeline

    // State for dynamic stats
    var neverWatched by remember { mutableStateOf<List<UnwatchedItem>>(emptyList()) }
    var neverListenedPlaylists by remember { mutableStateOf<List<UnplayedPlaylist>>(emptyList()) }
    var allMangaMap by remember { mutableStateOf<Map<Long, MangaEntity>>(emptyMap()) }
    var allChaptersMap by remember { mutableStateOf<Map<Long, ChapterEntity>>(emptyMap()) }
    
    var isLoadingStats by remember { mutableStateOf(true) }

    // Dialog trigger states
    var showLifecyclePopup by remember { mutableStateOf<HistoryTimelineItem?>(null) }
    var showNeverWatchedPopup by remember { mutableStateOf(false) }
    var showNeverListenedPopup by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(events) {
        isLoadingStats = true
        withContext(Dispatchers.IO) {
            val allManga = database.libraryDao().getAllMangaList()
            allMangaMap = allManga.associateBy { it.id }

            val chs = mutableListOf<ChapterEntity>()
            allManga.forEach { manga ->
                chs.addAll(database.libraryDao().getChaptersForMangaList(manga.id))
            }
            allChaptersMap = chs.associateBy { it.id }
            
            // Build unwatched / unplayed sets
            val watchedIds = mutableSetOf<Long>()
            val watchedTitles = mutableSetOf<String>()
            
            val listenedIds = mutableSetOf<Long>()
            val listenedTitles = mutableSetOf<String>()
            
            val readToonIds = mutableSetOf<Long>()
            val readToonTitles = mutableSetOf<String>()
            
            val readBookMangaIds = mutableSetOf<Long>()
            val readBookTitles = mutableSetOf<String>()

            for (event in events) {
                val meta = parseDetails(event.details)
                when (event.eventType) {
                    "WATCH" -> {
                        meta.chapterId?.let { watchedIds.add(it) }
                        watchedTitles.add(event.itemName)
                    }
                    "LISTEN" -> {
                        meta.chapterId?.let { listenedIds.add(it) }
                        listenedTitles.add(event.itemName)
                    }
                    "READ_TOON" -> {
                        meta.chapterId?.let { readToonIds.add(it) }
                        readToonTitles.add(event.itemName)
                    }
                    "READ_BOOK" -> {
                        meta.mangaId?.let { readBookMangaIds.add(it) }
                        readBookTitles.add(event.itemName)
                    }
                }
            }

            val unwatchedVideosList = mutableListOf<UnwatchedItem>()
            val unreadToonsList = mutableListOf<UnwatchedItem>()
            val unreadBooksList = mutableListOf<UnwatchedItem>()

            allManga.forEach { manga ->
                val mangaChapters = chs.filter { it.mangaId == manga.id }
                when (manga.contentType) {
                    0 -> { // Toon
                        mangaChapters.forEach { ch ->
                            val isRead = ch.openCount > 0
                            if (!isRead) {
                                unreadToonsList.add(UnwatchedItem(manga.id, ch.id, ch.title, manga.title, 0))
                            }
                        }
                    }
                    1 -> { // Book
                        val isRead = manga.id in readBookMangaIds || manga.title in readBookTitles
                        if (!isRead) {
                            val cleanTitle = manga.title.removeSuffix(".pdf")
                            unreadBooksList.add(UnwatchedItem(manga.id, null, cleanTitle, manga.title, 1))
                        }
                    }
                    2 -> { // Video
                        mangaChapters.forEach { ch ->
                            val isWatched = ch.openCount > 0
                            if (!isWatched) {
                                unwatchedVideosList.add(UnwatchedItem(manga.id, ch.id, ch.title, manga.title, 2))
                            }
                        }
                    }
                }
            }

            // Apply existing Hwaran natural sorting logic
            val numericRegex = Regex("(\\d+(\\.\\d+)?)")
            val padRegex = Regex("\\d+")
            
            val sortedToons = unreadToonsList.sortedWith(compareBy<UnwatchedItem> { 
                numericRegex.find(it.title)?.value?.toFloat() ?: Float.MAX_VALUE 
            }.thenBy {
                it.title.replace(padRegex) { it.value.padStart(10, '0') }
            })
            
            val sortedVideos = unwatchedVideosList.sortedWith(compareBy<UnwatchedItem> { 
                numericRegex.find(it.title)?.value?.toFloat() ?: Float.MAX_VALUE 
            }.thenBy {
                it.title.replace(padRegex) { it.value.padStart(10, '0') }
            })

            val unplayedPlaylistsList = mutableListOf<UnplayedPlaylist>()
            allManga.filter { it.contentType == 3 }.forEach { playlist ->
                val playlistChapters = chs.filter { it.mangaId == playlist.id }
                val unplayed = playlistChapters.filter { ch ->
                    ch.openCount == 0
                }.sortedWith(compareBy<ChapterEntity> { 
                    numericRegex.find(it.title)?.value?.toFloat() ?: Float.MAX_VALUE 
                }.thenBy {
                    it.title.replace(padRegex) { it.value.padStart(10, '0') }
                })
                
                if (unplayed.isNotEmpty()) {
                    unplayedPlaylistsList.add(UnplayedPlaylist(playlist.id, playlist.title, unplayed))
                }
            }

            withContext(Dispatchers.Main) {
                neverWatched = sortedToons + sortedVideos + unreadBooksList
                neverListenedPlaylists = unplayedPlaylistsList
                isLoadingStats = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Top Back Button (Floating above everything)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(top = 40.dp, bottom = 32.dp)
                    .zIndex(10f)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(start = 8.dp, top = 20.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack, 
                        contentDescription = "Back", 
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 80.dp else 140.dp))

                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Library History",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                // Tab Toggles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TabButton(
                        text = "Insights",
                        isSelected = selectedTab == 0,
                        icon = Icons.Rounded.Analytics,
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedTab = 0
                    }
                    TabButton(
                        text = "Timeline",
                        isSelected = selectedTab == 1,
                        icon = Icons.Rounded.History,
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedTab = 1
                    }
                }

                // Main Content Area
                if (selectedTab == 0) {
                    InsightsContent(
                        events = events,
                        activeTheme = activeTheme,
                        neverWatchedCount = neverWatched.size,
                        neverListenedCount = neverListenedPlaylists.sumOf { it.unplayedSongs.size },
                        isLoading = isLoadingStats,
                        onNeverWatchedClick = { showNeverWatchedPopup = true },
                        onNeverListenedClick = { showNeverListenedPopup = true }
                    )
                } else {
                    TimelineContent(
                        events = events,
                        allMangaMap = allMangaMap,
                        allChaptersMap = allChaptersMap,
                        sfwText = sfwText,
                        nsfwText = nsfwText,
                        onNavigateToReader = onNavigateToReader,
                        onNavigateToPdfReader = onNavigateToPdfReader,
                        onNavigateToVideoPlayer = onNavigateToVideoPlayer,
                        onNavigateToPlaylist = onNavigateToPlaylist,
                        onLifecycleGroupClick = { showLifecyclePopup = it }
                    )
                }
            }

            // Compact timestamp popup for collapsed APP/IMPORT/DELETE events
            if (showLifecyclePopup != null) {
                val group = showLifecyclePopup!!
                AlertDialog(
                    onDismissRequest = { showLifecyclePopup = null },
                    title = {
                        Text(
                            text = when (group.eventType) {
                                "APP_OPEN" -> "App Opened History"
                                "APP_CLOSE" -> "App Closed History"
                                "IMPORT" -> "Import History"
                                "DELETE" -> "Delete History"
                                else -> "Event History"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().fadedScrollEdges(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(group.occurrences) { occ ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (occ.itemName != "App Opened" && occ.itemName != "App Closed") occ.itemName else formatDateOnly(occ.timestamp),
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (occ.details.isNotEmpty() && !occ.details.startsWith("mangaId:")) {
                                                Text(
                                                    text = occ.details,
                                                    color = Color.Gray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Text(
                                            text = formatTimeOnly(occ.timestamp),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLifecyclePopup = null }) {
                            Text("Close", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    containerColor = Color(0xFF0F0F13),
                    shape = RoundedCornerShape(24.dp)
                )
            }

            // Compact tabbed popup for Never Watched content
            if (showNeverWatchedPopup) {
                var unwatchedTab by remember { mutableIntStateOf(0) } // 0 = Toon, 1 = Video, 2 = Book
                
                // Grouping Toons by mangaId
                val toonsByManga = remember(neverWatched, allMangaMap) {
                    neverWatched.filter { it.contentType == 0 }
                        .groupBy { it.mangaId }
                        .mapNotNull { (mangaId, items) ->
                            val manga = allMangaMap[mangaId] ?: return@mapNotNull null
                            manga to items
                        }
                }
                
                // Book list
                val unreadBooks = remember(neverWatched) { neverWatched.filter { it.contentType == 1 } }
                
                // For videos: Series vs Channel toggle
                var videoSubTab by remember { mutableIntStateOf(0) } // 0 = Series, 1 = Channel
                
                val unwatchedVideosByManga = remember(neverWatched) {
                    neverWatched.filter { it.contentType == 2 }
                        .groupBy { it.mangaId }
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
                    onDismissRequest = { showNeverWatchedPopup = false },
                    title = {
                        Text("Never Watched Content", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Sub-tabs Selection
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Toon", "Video", "Book").forEachIndexed { index, name ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (unwatchedTab == index) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f))
                                            .clickable { unwatchedTab = index }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(name, color = if (unwatchedTab == index) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                                when (unwatchedTab) {
                                    0 -> { // Toon: grouped by folder cover
                                        if (toonsByManga.isEmpty()) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("All toons have been read!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize().fadedScrollEdges(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(toonsByManga, key = { it.first.id }) { (manga, items) ->
                                                    var isExpanded by remember(manga.id) { mutableStateOf(false) }
                                                    var visibleCount by remember(manga.id) { mutableIntStateOf(10) }
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable { isExpanded = !isExpanded },
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                                    Text(truncateMiddle(manga.title), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                                Icon(
                                                                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                                    contentDescription = null,
                                                                    tint = Color.Gray
                                                                )
                                                            }
                                                            if (isExpanded) {
                                                                Spacer(modifier = Modifier.height(8.dp))
                                                                val displayItems = items.take(visibleCount)
                                                                displayItems.forEachIndexed { idx, item ->
                                                                    val isLast = idx == displayItems.size - 1 && visibleCount >= items.size
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clickable {
                                                                                showNeverWatchedPopup = false
                                                                                item.chapterId?.let { onNavigateToReader(it) }
                                                                            }
                                                                            .padding(vertical = 6.dp, horizontal = 8.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(
                                                                            text = if (isLast) "└── " else "├── ",
                                                                            color = Color.White.copy(alpha = 0.15f),
                                                                            fontFamily = FontFamily.Monospace,
                                                                            fontSize = 13.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                        Text(truncateMiddle(item.title), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                                                    }
                                                                }
                                                                if (visibleCount < items.size) {
                                                                    val remaining = items.size - visibleCount
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth().clickable { visibleCount += 10 }.padding(vertical = 6.dp, horizontal = 8.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(
                                                                            text = "└── ",
                                                                            color = Color.White.copy(alpha = 0.15f),
                                                                            fontFamily = FontFamily.Monospace,
                                                                            fontSize = 13.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                        Text("⌄ $remaining", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    1 -> { // Video: Series vs Channel toggle + expandable subfolders
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf("Series", "Channel").forEachIndexed { index, name ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (videoSubTab == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.02f))
                                                            .border(1.dp, if (videoSubTab == index) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                                            .clickable { videoSubTab = index }
                                                            .padding(vertical = 6.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(name, color = if (videoSubTab == index) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            
                                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                if (videoSubTab == 1) { // Channel
                                                    if (unwatchedChannels.isEmpty()) {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Text("All channel videos have been watched!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                                                        }
                                                    } else {
                                                        LazyColumn(
                                                            modifier = Modifier.fillMaxSize().fadedScrollEdges(),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            items(unwatchedChannels, key = { it.first.id }) { (channel, items) ->
                                                                var isExpanded by remember(channel.id) { mutableStateOf(false) }
                                                                var visibleCount by remember(channel.id) { mutableIntStateOf(10) }
                                                                Card(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                                                                ) {
                                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                                        Row(
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .clickable { isExpanded = !isExpanded },
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Row(
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                            ) {
                                                                                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                                                Text(truncateMiddle(channel.title), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                                            }
                                                                            Icon(
                                                                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                                                contentDescription = null,
                                                                                tint = Color.Gray
                                                                            )
                                                                        }
                                                                        if (isExpanded) {
                                                                            Spacer(modifier = Modifier.height(8.dp))
                                                                            val displayItems = items.take(visibleCount)
                                                                            displayItems.forEachIndexed { idx, item ->
                                                                                val isLast = idx == displayItems.size - 1 && visibleCount >= items.size
                                                                                Row(
                                                                                    modifier = Modifier
                                                                                        .fillMaxWidth()
                                                                                        .clickable {
                                                                                            showNeverWatchedPopup = false
                                                                                            item.chapterId?.let { onNavigateToVideoPlayer(it) }
                                                                                        }
                                                                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                                                                    verticalAlignment = Alignment.CenterVertically
                                                                                ) {
                                                                                    Text(
                                                                                        text = if (isLast) "└── " else "├── ",
                                                                                        color = Color.White.copy(alpha = 0.15f),
                                                                                        fontFamily = FontFamily.Monospace,
                                                                                        fontSize = 13.sp,
                                                                                        fontWeight = FontWeight.Bold
                                                                                    )
                                                                                    Text(truncateMiddle(item.title), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                                                                }
                                                                            }
                                                                            if (visibleCount < items.size) {
                                                                                val remaining = items.size - visibleCount
                                                                                Row(
                                                                                    modifier = Modifier.fillMaxWidth().clickable { visibleCount += 10 }.padding(vertical = 6.dp, horizontal = 8.dp),
                                                                                    verticalAlignment = Alignment.CenterVertically
                                                                                ) {
                                                                                    Text(
                                                                                        text = "└── ",
                                                                                        color = Color.White.copy(alpha = 0.15f),
                                                                                        fontFamily = FontFamily.Monospace,
                                                                                        fontSize = 13.sp,
                                                                                        fontWeight = FontWeight.Bold
                                                                                    )
                                                                                    Text("⌄ $remaining", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else { // Series: parent-child seasons logic
                                                    if (unwatchedSeriesTree.isEmpty()) {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Text("All series videos have been watched!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                                                        }
                                                    } else {
                                                        LazyColumn(
                                                            modifier = Modifier.fillMaxSize().fadedScrollEdges(),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            items(unwatchedSeriesTree) { node ->
                                                                Card(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                                                                ) {
                                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                                        SeriesNodeView(
                                                                            node = node,
                                                                            indentation = 0,
                                                                            onVideoClick = { videoId ->
                                                                                showNeverWatchedPopup = false
                                                                                onNavigateToVideoPlayer(videoId)
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
                                    else -> { // Book
                                        if (unreadBooks.isEmpty()) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("All books have been read!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize().fadedScrollEdges(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(unreadBooks) { item ->
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                showNeverWatchedPopup = false
                                                                onNavigateToPdfReader(item.mangaId)
                                                            },
                                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Book,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Text(
                                                                text = truncateMiddle(item.title),
                                                                color = Color.White,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1
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
                    },
                    confirmButton = {
                        TextButton(onClick = { showNeverWatchedPopup = false }) {
                            Text("Close", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    containerColor = Color(0xFF0F0F13),
                    shape = RoundedCornerShape(24.dp)
                )
            }

            // Compact expander popup for Never Listened playlist songs
            if (showNeverListenedPopup) {
                var expandedPlaylistId by remember { mutableStateOf<Long?>(null) }
                AlertDialog(
                    onDismissRequest = { showNeverListenedPopup = false },
                    title = {
                        Text("Never Listened Music", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    text = {
                        Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                            if (neverListenedPlaylists.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("All playlist tracks have been played!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().fadedScrollEdges(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(neverListenedPlaylists) { playlist ->
                                        val isExpanded = expandedPlaylistId == playlist.mangaId
                                        var visibleCount by remember(isExpanded) { mutableIntStateOf(10) }
                                        
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            expandedPlaylistId = if (isExpanded) null else playlist.mangaId
                                                        },
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(truncateMiddle(playlist.title), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                        Text("${playlist.unplayedSongs.size} unplayed tracks", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                                    }
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                        contentDescription = null,
                                                        tint = Color.Gray
                                                    )
                                                }
                                                if (isExpanded) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    val displayItems = playlist.unplayedSongs.take(visibleCount)
                                                    displayItems.forEachIndexed { idx, song ->
                                                        val isLast = idx == displayItems.size - 1 && visibleCount >= playlist.unplayedSongs.size
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    showNeverListenedPopup = false
                                                                    coroutineScope.launch(Dispatchers.IO) {
                                                                        val chs = database.libraryDao().getChaptersForMangaList(playlist.mangaId)
                                                                        val playlistManga = database.libraryDao().getMangaById(playlist.mangaId)
                                                                        if (playlistManga != null) {
                                                                            val clickIndex = chs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                                                            withContext(Dispatchers.Main) {
                                                                                musicViewModel.playPlaylist(playlistManga, chs, clickIndex)
                                                                                onNavigateToNowPlaying()
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                            Text(truncateMiddle(song.title), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                                        }
                                                    }
                                                    if (visibleCount < playlist.unplayedSongs.size) {
                                                        val remaining = playlist.unplayedSongs.size - visibleCount
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable { visibleCount += 10 }
                                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "└── ",
                                                                color = Color.White.copy(alpha = 0.15f),
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text("⌄ $remaining+", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showNeverListenedPopup = false }) {
                            Text("Close", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    containerColor = Color(0xFF0F0F13),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
fun InsightsContent(
    events: List<HistoryEventEntity>,
    activeTheme: Int,
    neverWatchedCount: Int,
    neverListenedCount: Int,
    isLoading: Boolean,
    onNeverWatchedClick: () -> Unit,
    onNeverListenedClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fadedScrollEdges()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Usage session calculation
        val totalUsageTime = remember(events) { calculateTotalTimeSpent(events) }
        val importsCount = remember(events) { events.count { it.eventType == "IMPORT" } }
        val activeThemeName = getThemeName(activeTheme)
        
        val passwordEvent = remember(events) {
            events.firstOrNull { it.eventType == "PASSWORD_SET" }
        }
        val passwordSetDate = passwordEvent?.let { formatShortDate(it.timestamp) } ?: "Not Set"

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = 2
        ) {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val cardWidth = (configuration.screenWidthDp.dp - 56.dp) / 2
            
            StatCard(title = "App Time Spent", value = formatDurationTime(totalUsageTime), icon = Icons.Rounded.Timelapse, modifier = Modifier.width(cardWidth))
            StatCard(title = "Passcode Status", value = passwordSetDate, icon = Icons.Rounded.Lock, modifier = Modifier.width(cardWidth))
            StatCard(title = "Files Imported", value = importsCount.toString(), icon = Icons.Rounded.Storage, modifier = Modifier.width(cardWidth))
            StatCard(title = "Active Theme", value = activeThemeName, icon = Icons.Rounded.Palette, modifier = Modifier.width(cardWidth))
        }

        // Stacked Horizontal Activity breakdown chart
        PremiumGlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Activity Overview", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                val watchCount = remember(events) { events.count { it.eventType == "WATCH" } }
                val listenCount = remember(events) { events.count { it.eventType == "LISTEN" } }
                val toonCount = remember(events) { events.count { it.eventType == "READ_TOON" } }
                val bookCount = remember(events) { events.count { it.eventType == "READ_BOOK" } }
                val importCount = remember(events) { events.count { it.eventType == "IMPORT" } }
                val deleteCount = remember(events) { events.count { it.eventType == "DELETE" } }
                
                val total = (watchCount + listenCount + toonCount + bookCount + importCount + deleteCount).toFloat()
                
                if (total > 0f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        if (watchCount > 0) Box(modifier = Modifier.weight(watchCount.toFloat()).fillMaxHeight().background(Color(0xFF1C1DAB)))
                        if (listenCount > 0) Box(modifier = Modifier.weight(listenCount.toFloat()).fillMaxHeight().background(Color(0xFF3785D8)))
                        if (toonCount > 0) Box(modifier = Modifier.weight(toonCount.toFloat()).fillMaxHeight().background(Color(0xFFADC6E5)))
                        if (bookCount > 0) Box(modifier = Modifier.weight(bookCount.toFloat()).fillMaxHeight().background(Color(0xFFBF8CE1)))
                        if (importCount > 0) Box(modifier = Modifier.weight(importCount.toFloat()).fillMaxHeight().background(Color(0xFFCBD8E8)))
                        if (deleteCount > 0) Box(modifier = Modifier.weight(deleteCount.toFloat()).fillMaxHeight().background(Color(0xFFE893C5)))
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f)))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Color Key Legend
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendItem(color = Color(0xFF1C1DAB), label = "Video Watches", count = watchCount)
                    LegendItem(color = Color(0xFF3785D8), label = "Music Plays", count = listenCount)
                    LegendItem(color = Color(0xFFADC6E5), label = "Toon Reads", count = toonCount)
                    LegendItem(color = Color(0xFFBF8CE1), label = "Book Reads", count = bookCount)
                    LegendItem(color = Color(0xFFCBD8E8), label = "Imports", count = importCount)
                    LegendItem(color = Color(0xFFE893C5), label = "Deletes", count = deleteCount)
                }
            }
        }

        // Most Listened Song Stats
        val listenCounts = remember(events) {
            events.filter { it.eventType == "LISTEN" }
                .groupBy { it.itemName }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(3)
        }

        PremiumGlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Most Played Tracks", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                if (listenCounts.isEmpty()) {
                    Text("No audio playback history yet.", color = Color.Gray, fontSize = 13.sp)
                } else {
                    val maxVal = listenCounts.firstOrNull()?.second?.toFloat() ?: 1f
                    listenCounts.forEach { (title, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$count plays",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { count.toFloat() / maxVal },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.05f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // Never Watched card - triggers compact popup
        PremiumGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .clickable(onClick = onNeverWatchedClick)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Never Watched Content", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isLoading) {
                        Text("Loading...", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        Text(
                            text = if (neverWatchedCount == 0) "All videos, toons, and books have been opened!" else "$neverWatchedCount unseen items remaining",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray)
            }
        }

        // Never Listened card - triggers compact popup
        PremiumGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .clickable(onClick = onNeverListenedClick)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Never Played Music", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isLoading) {
                        Text("Loading...", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        Text(
                            text = if (neverListenedCount == 0) "All songs in all playlists have been played!" else "$neverListenedCount unplayed songs remaining",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun LegendItem(color: Color, label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("$count items", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TimelineContent(
    events: List<HistoryEventEntity>,
    allMangaMap: Map<Long, MangaEntity>,
    allChaptersMap: Map<Long, ChapterEntity>,
    sfwText: String,
    nsfwText: String,
    onNavigateToReader: (Long) -> Unit,
    onNavigateToPdfReader: (Long) -> Unit,
    onNavigateToVideoPlayer: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onLifecycleGroupClick: (HistoryTimelineItem) -> Unit
) {
    if (events.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.HistoryToggleOff,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No history logged yet.",
                    color = Color.Gray,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        val groupedEvents = remember(events, allMangaMap) { groupHistoryEvents(events, allMangaMap) }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize().fadedScrollEdges(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            items(groupedEvents, key = { it.id }) { item ->
                TimelineGroupedCard(
                    item = item,
                    allMangaMap = allMangaMap,
                    allChaptersMap = allChaptersMap,
                    sfwText = sfwText,
                    nsfwText = nsfwText,
                    onNavigateToReader = onNavigateToReader,
                    onNavigateToPdfReader = onNavigateToPdfReader,
                    onNavigateToVideoPlayer = onNavigateToVideoPlayer,
                    onNavigateToPlaylist = onNavigateToPlaylist,
                    onLifecycleClick = { onLifecycleGroupClick(item) }
                )
            }
        }
    }
}

@Composable
fun TimelineGroupedCard(
    item: HistoryTimelineItem,
    allMangaMap: Map<Long, MangaEntity>,
    allChaptersMap: Map<Long, ChapterEntity>,
    sfwText: String,
    nsfwText: String,
    onNavigateToReader: (Long) -> Unit,
    onNavigateToPdfReader: (Long) -> Unit,
    onNavigateToVideoPlayer: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onLifecycleClick: () -> Unit
) {
    var isCardExpanded by remember(item.id) { mutableStateOf(false) }
    // 1. Grouped Lifecycle Card (App session, imports, deletes)
    if (item.isGroupedLifecycle) {
        val occurrencesSize = item.occurrences.size
        val icon = when (item.eventType) {
            "APP_OPEN" -> Icons.Rounded.PowerSettingsNew
            "APP_CLOSE" -> Icons.Rounded.Power
            "IMPORT" -> Icons.Rounded.Storage
            "DELETE" -> Icons.Rounded.DeleteOutline
            else -> Icons.Rounded.Info
        }
        val iconColor = when (item.eventType) {
            "APP_OPEN", "APP_CLOSE" -> Color(0xFF7B466A)
            "IMPORT" -> Color(0xFFBA6E8F)
            "DELETE" -> Color(0xFF6B111A)
            else -> Color.White
        }
        val titleText = when (item.eventType) {
            "APP_OPEN" -> "App Opened ${occurrencesSize}x"
            "APP_CLOSE" -> "App Closed ${occurrencesSize}x"
            "IMPORT" -> "Imported Folder ${occurrencesSize}x"
            "DELETE" -> "Deleted Item ${occurrencesSize}x"
            else -> "${item.title} ${occurrencesSize}x"
        }
        val subtitleText = when (item.eventType) {
            "IMPORT" -> {
                val latest = item.occurrences.firstOrNull()
                if (latest != null) {
                    val details = latest.details
                    val metadata = if (details.contains(" | Source")) details.substringBefore(" | Source").trim() else ""
                    val path = if (details.contains("Source: ")) details.substringAfter("Source: ") else ""
                    
                    if (metadata.isNotEmpty()) {
                        "Latest: ${latest.itemName} ($metadata)"
                    } else if (path.isNotEmpty()) {
                        "Latest: ${latest.itemName} from $path"
                    } else {
                        "Latest: ${latest.itemName}"
                    }
                } else "Tap to see exact times"
            }
            "DELETE" -> {
                val latest = item.occurrences.firstOrNull()
                if (latest != null) {
                    "Latest: ${latest.itemName} (${latest.details})"
                } else "Tap to see exact times"
            }
            else -> "Tap to see exact times"
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(onClick = onLifecycleClick),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f))
                        .border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(titleText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(subtitleText, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray)
            }
        }
        return
    }

    // 2. Folder tree media grouping card (Same mangaId, consecutive media operations)
    if (item.isGroupedMedia && (item.mangaId != null || item.eventType == "READ_BOOK" || item.eventType == "WATCH" || item.eventType == "READ_TOON" || item.eventType == "LISTEN")) {
        val manga = if (item.mangaId != null) allMangaMap[item.mangaId] else null
        val screenLabel = if (manga?.isNsfw == true) nsfwText else sfwText
        val workspaceLabel = if (item.isGroupedMedia) item.details.ifEmpty { "Unlabeled" } else "Unlabeled"
        
        val icon = when (item.eventType) {
            "WATCH" -> Icons.Rounded.Movie // Proper movie icon
            "LISTEN" -> Icons.Rounded.MusicNote
            "READ_TOON" -> Icons.AutoMirrored.Rounded.MenuBook
            "READ_BOOK" -> Icons.Rounded.Book
            else -> Icons.Rounded.Info
        }

        val iconColor = when (item.eventType) {
            "WATCH" -> Color(0xFF9F6496)
            "LISTEN" -> Color(0xFFD391B0)
            "READ_TOON" -> Color(0xFF5D3C64)
            "READ_BOOK" -> Color(0xFFBA6E8F)
            else -> MaterialTheme.colorScheme.primary
        }

        val headerText = when (item.eventType) {
            "WATCH" -> "Watched Videos"
            "LISTEN" -> "Played Tracks"
            "READ_TOON" -> "Read Toons"
            "READ_BOOK" -> "Books"
            else -> "Media Group"
        }

        // Subtitle Mode Label
        val subtitleText = if (manga != null) {
            when (manga.contentType) {
                3 -> "" // Music
                2 -> { // Video: keep only shelf
                    val layoutModeLabel = if (manga.boxPurpose == "channel") "channel" else "series"
                    "shelf : $layoutModeLabel"
                }
                else -> "" // Toon, Book
            }
        } else ""

        val folderTitle = if (item.eventType == "READ_BOOK" && item.mangaId == null) "Books" else if (item.eventType == "READ_TOON" && item.mangaId == null) "Toons" else if (item.eventType == "WATCH" && item.mangaId == null) "Videos" else if (item.eventType == "LISTEN" && item.mangaId == null) "Music" else (manga?.title ?: item.title)

        // Reverse to chronological order (earliest first) for correct tree branching representation
        val chronologicallyOrdered = remember(item.occurrences) { item.occurrences.reversed() }

        // Time spent calculation
        val timeSpentText = remember(chronologicallyOrdered, manga, item.eventType) {
            var totalMs = 0L
            if (manga?.contentType == 2 || manga?.contentType == 3 || item.eventType == "LISTEN" || item.eventType == "WATCH") {
                chronologicallyOrdered.forEach { occ ->
                    val m = parseDetails(occ.details)
                    m.chapterId?.let { chId ->
                        totalMs += allChaptersMap[chId]?.duration ?: 0L
                    }
                }
            }
            if (totalMs > 0L) {
                val totalMins = totalMs / 1000 / 60
                if (totalMins > 0) "$totalMins mins" else "1 min"
            } else {
                val span = chronologicallyOrdered.last().timestamp - chronologicallyOrdered.first().timestamp
                if (span > 60000L) {
                    "${span / 60000L} mins"
                } else {
                    "${(chronologicallyOrdered.size * 3).coerceAtLeast(3)} mins"
                }
            }
        }

        val formattedDateFooter = remember(item.timestamp) {
            val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(item.timestamp))
            val dateFull = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(item.timestamp))
            "$day • $timeSpentText • $dateFull"
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Category
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
                    Text(
                        text = headerText.uppercase(),
                        color = iconColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Folder Title
                Text(
                    text = truncateMiddle(folderTitle),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle
                if (subtitleText.isNotEmpty()) {
                    Text(
                        text = subtitleText,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tree Branching Structure List
                if (item.eventType == "WATCH" && item.mangaId == null) {
                    val videosById = remember(chronologicallyOrdered) {
                        chronologicallyOrdered.groupBy { parseDetails(it.details).mangaId }
                            .toList()
                            .sortedByDescending { it.second.last().timestamp }
                    }

                    videosById.forEachIndexed { vIdx, (mId, occs) ->
                        if (mId == null) return@forEachIndexed
                        val videoManga = allMangaMap[mId] ?: return@forEachIndexed
                        val isLastSeries = vIdx == videosById.size - 1
                        
                        val episodesByChapter = remember(occs) {
                            occs.groupBy { parseDetails(it.details).chapterId }
                                .toList()
                                .sortedByDescending { it.second.last().timestamp }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            // 📺 [Title of the Cover]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isLastSeries) "└── " else "├── ",
                                    color = Color.White.copy(alpha = 0.15f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = truncateMiddle(videoManga.title),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            // shelf: [series/channel] ᶻ 𝗓 𐰁 label: [SFW/NSFW]
                            val sLabel = if (videoManga.isNsfw) nsfwText else sfwText
                            val layoutModeLabel = if (videoManga.boxPurpose == "channel") "channel" else "series"
                            val seriesLinePrefix = if (isLastSeries) "    " else "│   "
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = seriesLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text(text = "shelf: $layoutModeLabel ᶻ 𝗓 𐰁 label: $sLabel", color = Color.Gray, fontSize = 11.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Episodes
                            episodesByChapter.forEachIndexed { epIdx, (cId, epOccs) ->
                                if (cId == null) return@forEachIndexed
                                val chapter = allChaptersMap[cId] ?: return@forEachIndexed
                                val lastEpOcc = epOccs.last()
                                val isLastEp = epIdx == episodesByChapter.size - 1
                                
                                val openCount = chapter.openCount
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToVideoPlayer(cId) }
                                        .padding(start = 32.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = seriesLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                        Text(
                                            text = if (isLastEp) "└── " else "├── ",
                                            color = Color.White.copy(alpha = 0.15f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = truncateMiddle(chapter.title),
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    val epLinePrefix = seriesLinePrefix + if (isLastEp) "    " else "│   "
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = epLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                        Text("Last Watched: ${formatTimeOnly(lastEpOcc.timestamp)}", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = epLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                        Text("timeline : ${formatDurationTime(chapter.position.toLong())}/${formatDurationTime(chapter.duration)}", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = epLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                        Text("Opened: $openCount", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                } else if (item.eventType == "READ_BOOK") {
                    val booksById = remember(chronologicallyOrdered) {
                        chronologicallyOrdered.groupBy { parseDetails(it.details).mangaId }
                            .toList()
                            .sortedByDescending { it.second.last().timestamp }
                    }
                    
                    booksById.forEachIndexed { bIdx, (mId, occs) ->
                        if (mId == null) return@forEachIndexed
                        val bookManga = allMangaMap[mId] ?: return@forEachIndexed
                        val lastOcc = occs.last()
                        val lastMeta = parseDetails(lastOcc.details)
                        
                        // Extract last read page from metadata if available, else from manga
                        val lastPage = lastMeta.pages?.split(",")?.lastOrNull()?.split("-")?.lastOrNull()?.toIntOrNull() 
                            ?: bookManga.lastReadPage ?: 1
                            
                        val openCount = bookManga.openCount

                        val isLastBook = bIdx == booksById.size - 1

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToPdfReader(mId) }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isLastBook) "└── " else "├── ",
                                    color = Color.White.copy(alpha = 0.15f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = truncateMiddle(bookManga.title.removeSuffix(".pdf")),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(modifier = Modifier.padding(start = 32.dp)) {
                                val currentP = lastPage
                                val totalP = lastMeta.totalPages ?: 0
                                val pageText = if (totalP > 0) "Page: $currentP / $totalP" else "Page: $currentP"
                                val linePrefix = if (isLastBook) "    " else "│   "

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                    Text(pageText, color = Color.Gray, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                    Text("Opened: $openCount", color = Color.Gray, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                    Text("Last read: ${formatTimeOnly(lastOcc.timestamp)}", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else if (item.eventType == "READ_TOON" && item.mangaId == null) {
                    val toonsById = remember(chronologicallyOrdered) {
                        chronologicallyOrdered.groupBy { parseDetails(it.details).mangaId }
                            .toList()
                            .sortedByDescending { it.second.last().timestamp }
                    }

                    toonsById.forEachIndexed { tIdx, (mId, occs) ->
                        if (mId == null) return@forEachIndexed
                        val toonManga = allMangaMap[mId] ?: return@forEachIndexed
                        val isLastToon = tIdx == toonsById.size - 1
                        var isToonExpanded by remember(mId) { mutableStateOf(false) }

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isToonExpanded = !isToonExpanded }
                            ) {
                                Text(
                                    text = if (isLastToon) "└── " else "├── ",
                                    color = Color.White.copy(alpha = 0.15f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ImportContacts,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = truncateMiddle(toonManga.title),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = if (isToonExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            val chaptersById = occs.groupBy { parseDetails(it.details).chapterId }.toList().sortedByDescending { it.second.last().timestamp }
                            
                            if (!isToonExpanded) {
                                val lastChOcc = chaptersById.firstOrNull()
                                if (lastChOcc != null) {
                                    val lastChapter = allChaptersMap[lastChOcc.first]
                                    if (lastChapter != null) {
                                        val lastMeta = parseDetails(lastChOcc.second.last().details)
                                        val openCount = toonManga.openCount
                                        
                                        val lastPage = lastMeta.pages?.split(",")?.lastOrNull()?.split("-")?.lastOrNull()?.toLongOrNull() ?: lastChapter.position
                                        val totalPage = lastMeta.totalPages?.toLong() ?: lastChapter.duration
                                        val pageText = if (totalPage > 0L) "Page: $lastPage / $totalPage" else "Page: $lastPage"
                                        val linePrefix = if (isLastToon) "    " else "│   "

                                        Column(modifier = Modifier.padding(start = 32.dp).clickable { onNavigateToReader(lastChapter.id) }) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                                Text("Last Read: ${truncateMiddle(lastChapter.title)}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                                Text(pageText, color = Color.Gray, fontSize = 11.sp)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = linePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                                Text("Opened: $openCount", color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                chaptersById.forEachIndexed { cIdx, (cId, cOccs) ->
                                    if (cId == null) return@forEachIndexed
                                    val chapter = allChaptersMap[cId] ?: return@forEachIndexed
                                    val isLastChapter = cIdx == chaptersById.size - 1
                                    val lastOcc = cOccs.last()
                                    val lastMeta = parseDetails(lastOcc.details)
                                    
                                    val openCount = chapter.openCount
                                    
                                    val lastPage = lastMeta.pages?.split(",")?.lastOrNull()?.split("-")?.lastOrNull()?.toLongOrNull() ?: chapter.position
                                    val totalPage = lastMeta.totalPages?.toLong() ?: chapter.duration
                                    val pageText = if (totalPage > 0L) "Page: $lastPage / $totalPage" else "Page: $lastPage"
    
                                    val toonLinePrefix = if (isLastToon) "    " else "│   "
                                    
                                    Column(modifier = Modifier.padding(start = 32.dp).clickable { onNavigateToReader(cId) }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isLastChapter) "└── " else "├── ",
                                                color = Color.White.copy(alpha = 0.15f),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = truncateMiddle(chapter.title),
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        val chLinePrefix = toonLinePrefix + if (isLastChapter) "    " else "│   "
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = chLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                            Text(pageText, color = Color.Gray, fontSize = 11.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = chLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                            Text("Opened: $openCount", color = Color.Gray, fontSize = 11.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = chLinePrefix, color = Color.White.copy(alpha = 0.15f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                            Text("Last read: ${formatTimeOnly(lastOcc.timestamp)}", color = Color.Gray, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (item.eventType == "LISTEN" && item.mangaId == null) {
                    val tracksByAlbum = remember(chronologicallyOrdered) {
                        chronologicallyOrdered.groupBy { parseDetails(it.details).mangaId }
                            .toList()
                            .sortedByDescending { it.second.last().timestamp }
                    }

                    tracksByAlbum.forEachIndexed { aIdx, (mId, occs) ->
                        if (mId == null) return@forEachIndexed
                        val albumManga = allMangaMap[mId] ?: return@forEachIndexed
                        val isLastAlbum = aIdx == tracksByAlbum.size - 1

                        val tracksByChapter = remember(occs) {
                            occs.groupBy { parseDetails(it.details).chapterId }
                                .toList()
                                .sortedByDescending { it.second.last().timestamp }
                        }

                        var visibleCount by remember(mId) { mutableIntStateOf(10) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            // ├── Album Title
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isLastAlbum) "└── " else "├── ",
                                    color = Color.White.copy(alpha = 0.15f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = truncateMiddle(albumManga.title),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            val albumLinePrefix = if (isLastAlbum) "    " else "│   "
                            val displayList = tracksByChapter.take(visibleCount)
                            
                            displayList.forEachIndexed { tIdx, (cId, _) ->
                                if (cId == null) return@forEachIndexed
                                val chapter = allChaptersMap[cId] ?: return@forEachIndexed
                                val isLastTrack = tIdx == displayList.size - 1 && visibleCount >= tracksByChapter.size
                                
                                val trackLinePrefix = albumLinePrefix + if (isLastTrack) "└── " else "├── "
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { onNavigateToPlaylist(albumManga.id) }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = trackLinePrefix,
                                        color = Color.White.copy(alpha = 0.15f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = truncateMiddle(chapter.title),
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            if (tracksByChapter.size > visibleCount) {
                                val remaining = tracksByChapter.size - visibleCount
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { visibleCount += 10 }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = albumLinePrefix + "│   ",
                                        color = Color.Transparent,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ExpandMore,
                                        contentDescription = "Expand",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp).offset(x = (-4).dp)
                                    )
                                    Text(
                                        text = "$remaining+",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val collapsedBranches = remember(chronologicallyOrdered, manga) {
                        collapseConsecutiveBranches(chronologicallyOrdered, manga?.contentType)
                    }

                    val isCollapseAllowed = item.eventType == "WATCH"
                    val limit = 10
                    val showExpandControl = isCollapseAllowed && collapsedBranches.size > limit
                    val displayList = if (showExpandControl && !isCardExpanded) {
                        collapsedBranches.take(limit)
                    } else {
                        collapsedBranches
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        displayList.forEachIndexed { index, branchItem ->
                            val isLast = index == displayList.size - 1
                            val meta = parseDetails(branchItem.occ.details)
                            val displayLabel = if (branchItem.count > 1) {
                                "${truncateMiddle(branchItem.label)} ${branchItem.count}x"
                            } else {
                                truncateMiddle(branchItem.label)
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (manga != null) {
                                            when (manga.contentType) {
                                                0 -> meta.chapterId?.let { onNavigateToReader(it) }
                                                1 -> onNavigateToPdfReader(manga.id)
                                                2 -> meta.chapterId?.let { onNavigateToVideoPlayer(it) }
                                                3 -> onNavigateToPlaylist(manga.id)
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isLast) "└── " else "├── ",
                                    color = Color.White.copy(alpha = 0.15f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = displayLabel,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatTimeOnly(branchItem.timestamp),
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (showExpandControl && !isCardExpanded) {
                            val hiddenCount = collapsedBranches.size - limit
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isCardExpanded = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ExpandMore,
                                    contentDescription = "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$hiddenCount+",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (item.eventType != "LISTEN") {
                    // Footer Section Info
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formattedDateFooter,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
        return
    }

    // 3. Fallback Singular Card (If grouping criteria is not met)
    val singularOcc = item.occurrences.firstOrNull() ?: return
    val icon = when (item.eventType) {
        "APP_OPEN" -> Icons.Rounded.PowerSettingsNew
        "APP_CLOSE" -> Icons.Rounded.Power
        "IMPORT" -> Icons.Rounded.Storage
        "DELETE" -> Icons.Rounded.DeleteOutline
        "WATCH" -> Icons.Rounded.Movie
        "LISTEN" -> Icons.Rounded.MusicNote
        "READ_TOON" -> Icons.AutoMirrored.Rounded.MenuBook
        "READ_BOOK" -> Icons.Rounded.Book
        "THEME_CHANGE" -> Icons.Rounded.Palette
        "PASSWORD_SET" -> Icons.Rounded.Lock
        else -> Icons.Rounded.Info
    }

    val iconColor = when (item.eventType) {
        "APP_OPEN", "APP_CLOSE" -> Color(0xFF7B466A)
        "IMPORT" -> Color(0xFFBA6E8F)
        "DELETE" -> Color(0xFF6B111A)
        "WATCH" -> Color(0xFF9F6496)
        "LISTEN" -> Color(0xFFD391B0)
        "READ_TOON" -> Color(0xFF5D3C64)
        "READ_BOOK" -> Color(0xFFBA6E8F)
        "THEME_CHANGE" -> Color(0xFFBA6E8F)
        "PASSWORD_SET" -> Color(0xFFD391B0)
        else -> Color.White
    }

    val title = when (item.eventType) {
        "APP_OPEN" -> "App Opened"
        "APP_CLOSE" -> "App Closed"
        "IMPORT" -> "Imported Folder"
        "DELETE" -> "Deleted Item"
        "WATCH" -> "Watched Video"
        "LISTEN" -> "Played Track"
        "THEME_CHANGE" -> "Theme Switched"
        "PASSWORD_SET" -> "Passcode Updated"
        else -> item.title
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f))
                    .border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatTimeOnly(item.timestamp),
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.title,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.details.isNotEmpty() && !item.details.startsWith("mangaId:")) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.details,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDateOnly(item.timestamp),
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    PremiumGlassPanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun getThemeName(themeId: Int): String {
    return when (themeId) {
        0 -> "Orchid"
        1 -> "Pure Dark"
        2 -> "Poiesis"
        3 -> "Forest"
        4 -> "Amethyst"
        5 -> "Blueberry"
        6 -> "Snowfall"
        7 -> "Grape"
        else -> "Pure Dark"
    }
}

private fun formatShortDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDateOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTimeOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun calculateTotalTimeSpent(events: List<HistoryEventEntity>): Long {
    var totalTime = 0L
    val sortedEvents = events.sortedBy { it.timestamp }
    var currentOpenTime: Long? = null
    
    for (event in sortedEvents) {
        if (event.eventType == "APP_OPEN") {
            currentOpenTime = event.timestamp
        } else if (event.eventType == "APP_CLOSE") {
            if (currentOpenTime != null) {
                val duration = event.timestamp - currentOpenTime
                if (duration > 0 && duration < 24 * 60 * 60 * 1000) {
                    totalTime += duration
                }
                currentOpenTime = null
            }
        }
    }
    
    if (currentOpenTime != null) {
        val currentDuration = System.currentTimeMillis() - currentOpenTime
        if (currentDuration > 0 && currentDuration < 24 * 60 * 60 * 1000) {
            totalTime += currentDuration
        }
    }
    
    return totalTime
}

private fun formatDurationTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

@Composable
fun TabButton(
    text: String,
    isSelected: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f)
    val contentColor = if (isSelected) Color.White else Color.Gray

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
