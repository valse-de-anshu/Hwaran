package com.ballade.hwaran.frontend.home

import android.content.Context
import android.net.Uri
import com.ballade.hwaran.data.importer.music.MusicImportUtils
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.HistoryEventEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.CoverArtResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    allManga: List<MangaEntity>,
    historyEvents: List<HistoryEventEntity>,
    onNavigateToDescription: (Long) -> Unit,
    onNavigateToMedia: (Long, Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onMediaShortcutClick: (tag: String) -> Unit,
    onOpenMusic: () -> Unit = {},
    onPlaySong: (MangaEntity, List<ChapterEntity>, Int) -> Unit = { _, _, _ -> },
    glowColor: Color = Color(0xFFE2E8F0),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val database = remember(context) { AppDatabase.getDatabase(context) }

    // Promo banner assets
    val promoBanners = remember {
        listOf(
            "file:///android_asset/promo/01_hwaran_home.png",
            "file:///android_asset/promo/02_manga.png",
            "file:///android_asset/promo/03_books.png",
            "file:///android_asset/promo/04_videos.png",
            "file:///android_asset/promo/05_music.png",
            "file:///android_asset/promo/06_one_app_all_worlds.png",
            "file:///android_asset/promo/07_hwaran_make_it_yours.png",
            "file:///android_asset/promo/08_more_than_media.png"
        )
    }

    // Accurate Counts for shortcuts matching LibraryView filters
    val allCount = remember(allManga) { allManga.count { !it.isNsfw && it.contentType != 3 } }
    val manhuaCount = remember(allManga) {
        allManga.count {
            !it.isNsfw && (
                (it.contentType == 0 || it.boxPurpose == "manhua") && (
                    it.boxPurpose == "manhua" ||
                    it.genre?.contains("manhua", ignoreCase = true) == true ||
                    it.genre?.contains("manhwa", ignoreCase = true) == true ||
                    it.genre?.contains("webtoon", ignoreCase = true) == true ||
                    it.title.contains("manhua", ignoreCase = true) ||
                    it.title.contains("manhwa", ignoreCase = true)
                )
            )
        }
    }
    val mangaCount = remember(allManga) {
        allManga.count {
            !it.isNsfw && it.contentType == 0 && it.boxPurpose != "manhua" && it.boxPurpose != "book" && (
                it.genre == null || (
                    !it.genre.contains("manhua", ignoreCase = true) &&
                    !it.genre.contains("manhwa", ignoreCase = true) &&
                    !it.genre.contains("webtoon", ignoreCase = true)
                )
            )
        }
    }
    val seriesCount = remember(allManga) {
        allManga.count { !it.isNsfw && (it.contentType == 2 || it.boxPurpose == "series") && it.boxPurpose != "channel" }
    }
    val novelCount = remember(allManga) {
        allManga.count { !it.isNsfw && (it.contentType == 4 || it.boxPurpose == "novel") }
    }
    val bookCount = remember(allManga) {
        allManga.count { !it.isNsfw && (it.contentType == 1 || it.boxPurpose == "book") && it.contentType != 4 && it.boxPurpose != "novel" }
    }
    val channelCount = remember(allManga) {
        allManga.count { !it.isNsfw && (it.contentType == 2 || it.boxPurpose == "channel") && it.boxPurpose == "channel" }
    }
    val musicCount = remember(allManga) {
        allManga.count { !it.isNsfw && it.contentType == 3 }
    }
    val favoriteCount = remember(allManga) {
        allManga.count { !it.isNsfw && (it.isFavorite || it.genre?.contains("favorite", ignoreCase = true) == true) }
    }

    // Pre-index history events for O(1) membership check
    val openedMangaIdsFromHistory = remember(historyEvents) {
        historyEvents.mapNotNull { event ->
            if (event.eventType in listOf("WATCH", "READ_TOON", "READ_BOOK", "LISTEN", "READ_NOVEL")) {
                Regex("mangaId:(\\d+)").find(event.details)?.groupValues?.getOrNull(1)?.toLongOrNull()
            } else null
        }.toSet()
    }

    // Continue watching / in progress covers
    val inProgressItems = remember(allManga, openedMangaIdsFromHistory) {
        val opened = allManga.filter { manga ->
            manga.openCount > 0 || !manga.lastReadTitle.isNullOrBlank() || ((manga.lastReadPage ?: 0) > 0) || openedMangaIdsFromHistory.contains(manga.id)
        }.sortedByDescending { it.lastModified }
        opened.take(8)
    }

    val initialItems = remember(inProgressItems) {
        inProgressItems.map { manga ->
            ContinueWatchingItem(
                manga = manga,
                displayTitle = manga.title,
                displaySubtitle = manga.lastReadTitle ?: when (manga.contentType) {
                    0 -> "Toon"
                    1 -> "Book"
                    2 -> "Video"
                    3 -> "Music"
                    4 -> "Novel"
                    else -> "Media"
                },
                coverModel = CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context),
                progress = 0f
            )
        }
    }
    var continueWatchingItems by remember(inProgressItems) { mutableStateOf(initialItems) }

    LaunchedEffect(inProgressItems, historyEvents) {
        withContext(Dispatchers.IO) {
            val result = mutableListOf<ContinueWatchingItem>()
            for (manga in inProgressItems) {
                val chapters = database.trackDao().getChaptersForMangaList(manga.id)
                when (manga.contentType) {
                    3 -> {
                        // Music item: resolve active or last played song
                        val target = if (!manga.lastReadTitle.isNullOrBlank()) {
                            chapters.firstOrNull { it.title == manga.lastReadTitle }
                        } else {
                            val lastListenEvent = historyEvents.firstOrNull {
                                it.eventType == "LISTEN" && it.details.contains("mangaId:${manga.id}")
                            }
                            if (lastListenEvent != null) {
                                chapters.firstOrNull { it.title == lastListenEvent.itemName }
                            } else null
                        } ?: chapters.firstOrNull { it.position > 0 } ?: chapters.firstOrNull()

                        val targetIdx = if (target != null) chapters.indexOfFirst { it.id == target.id }.coerceAtLeast(0) else 0

                        val songTitle = target?.title?.takeIf { it.isNotBlank() } ?: manga.title
                        val subtitle = if (!target?.artist.isNullOrBlank()) {
                            "${target.artist} • ${manga.title}"
                        } else {
                            "Music • ${manga.title}"
                        }

                        var resolvedThumb: Any? = target?.thumbnailUri?.takeIf { it.isNotBlank() }
                        if (resolvedThumb == null && target != null && !target.folderUri.isNullOrBlank()) {
                            try {
                                val parsedUri = Uri.parse(target.folderUri)
                                val extracted = MusicImportUtils.extractEmbeddedCover(context, parsedUri, manga.title)
                                if (extracted != null) {
                                    resolvedThumb = extracted
                                    database.trackDao().insertChapter(target.copy(thumbnailUri = extracted))
                                }
                            } catch (_: Exception) {}
                        }

                        val cover = resolvedThumb ?: CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, chapters, context)

                        val prog = if (target != null && target.duration > 0) {
                            (target.position.toFloat() / target.duration.toFloat()).coerceIn(0.02f, 1f)
                        } else 0f

                        result.add(
                            ContinueWatchingItem(
                                manga = manga,
                                targetChapter = target,
                                allChapters = chapters,
                                targetIndex = targetIdx,
                                displayTitle = songTitle,
                                displaySubtitle = subtitle,
                                coverModel = cover,
                                progress = prog
                            )
                        )
                    }
                    2 -> {
                        // Video item
                        val target = chapters.firstOrNull { it.title == manga.lastReadTitle }
                            ?: chapters.firstOrNull { it.position > 0 }
                            ?: chapters.firstOrNull()
                        val targetIdx = if (target != null) chapters.indexOfFirst { it.id == target.id }.coerceAtLeast(0) else 0
                        val prog = if (target != null && target.duration > 0) {
                            (target.position.toFloat() / target.duration.toFloat()).coerceIn(0.02f, 1f)
                        } else if (target != null && target.position > 0) 0.2f else 0f
                        val cover = CoverArtResolver.resolveCoverModel(target?.thumbnailUri ?: manga.coverPath, manga.parentUri, chapters, context)
                        result.add(
                            ContinueWatchingItem(
                                manga = manga,
                                targetChapter = target,
                                allChapters = chapters,
                                targetIndex = targetIdx,
                                displayTitle = manga.title,
                                displaySubtitle = target?.title ?: if (manga.boxPurpose == "channel") "Channel" else "Series",
                                coverModel = cover,
                                progress = prog
                            )
                        )
                    }
                    0 -> {
                        // Manga / Toon
                        val target = chapters.firstOrNull { it.title == manga.lastReadTitle } ?: chapters.firstOrNull()
                        val targetIdx = if (target != null) chapters.indexOfFirst { it.id == target.id }.coerceAtLeast(0) else 0
                        val prog = if (chapters.isNotEmpty()) {
                            val idx = chapters.indexOfFirst { it.title == manga.lastReadTitle }.takeIf { it >= 0 } ?: 0
                            ((idx + 1).toFloat() / chapters.size.toFloat()).coerceIn(0.05f, 1f)
                        } else 0f
                        val cover = CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, chapters, context)
                        result.add(
                            ContinueWatchingItem(
                                manga = manga,
                                targetChapter = target,
                                allChapters = chapters,
                                targetIndex = targetIdx,
                                displayTitle = manga.title,
                                displaySubtitle = manga.lastReadTitle ?: if (manga.boxPurpose == "manhua") "Manhua" else "Manga",
                                coverModel = cover,
                                progress = prog
                            )
                        )
                    }
                    4 -> {
                        // Novel (contentType == 4)
                        val prog = if (manga.lastReadPage != null && manga.lastReadPage > 0) 0.35f else 0f
                        val cover = CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, chapters, context)
                        result.add(
                            ContinueWatchingItem(
                                manga = manga,
                                targetChapter = chapters.firstOrNull(),
                                allChapters = chapters,
                                displayTitle = manga.title,
                                displaySubtitle = manga.lastReadTitle ?: "Novel",
                                coverModel = cover,
                                progress = prog
                            )
                        )
                    }
                    else -> {
                        // Book (contentType == 1)
                        val prog = if (manga.lastReadPage != null && manga.lastReadPage > 0) 0.3f else 0f
                        val cover = CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
                        result.add(
                            ContinueWatchingItem(
                                manga = manga,
                                displayTitle = manga.title,
                                displaySubtitle = manga.lastReadTitle ?: "Book • PDF",
                                coverModel = cover,
                                progress = prog
                            )
                        )
                    }
                }
            }
            continueWatchingItems = result
        }
    }

    // Recently added covers
    val recentlyAdded = remember(allManga) {
        allManga.filter { !it.isNsfw }.sortedByDescending { it.id }
    }

    var showRecentlyAddedSheet by remember { mutableStateOf(false) }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gestureBottom = WindowInsets.systemGestures.asPaddingValues().calculateBottomPadding()
    val bottomDockClearance = maxOf(navBarBottom + 16.dp, maxOf(gestureBottom + 12.dp, 32.dp)) + 68.dp + 20.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(bottom = bottomDockClearance)
    ) {
        // 1. Clean Top Header (Settings shortcut)
        DashboardTopHeader(
            onSettingsClick = onNavigateToSettings
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Large Horizontally Swipeable Promo Banner Carousel
        BannerCarouselSection(
            banners = promoBanners,
            glowColor = glowColor,
            onBannerClick = { page ->
                when (page) {
                    1 -> onMediaShortcutClick("Manga")
                    2 -> onMediaShortcutClick("Book")
                    3 -> onMediaShortcutClick("Series")
                    4 -> onOpenMusic()
                    else -> {}
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Media Shortcuts (All, Fav, Manhua, Manga, Light Novel, Book, Series, Channel, Music)
        MediaShortcutsSection(
            allCount = allCount,
            favoriteCount = favoriteCount,
            manhuaCount = manhuaCount,
            mangaCount = mangaCount,
            novelCount = novelCount,
            bookCount = bookCount,
            seriesCount = seriesCount,
            channelCount = channelCount,
            musicCount = musicCount,
            onShortcutClick = onMediaShortcutClick,
            onMusicClick = onOpenMusic
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 4. Continue Watching / Reading
        if (inProgressItems.isNotEmpty()) {
            ContinueWatchingSection(
                items = continueWatchingItems,
                onItemClick = { manga -> onNavigateToDescription(manga.id) },
                onPlayItem = { item ->
                    if (item.manga.contentType == 3) {
                        onPlaySong(item.manga, item.allChapters, item.targetIndex)
                    } else if (item.manga.contentType == 2) {
                        if (item.targetChapter != null) {
                            onNavigateToMedia(item.targetChapter.id, 2)
                        } else {
                            onNavigateToDescription(item.manga.id)
                        }
                    } else if (item.manga.contentType == 4 || item.manga.boxPurpose == "novel") {
                        onNavigateToMedia(item.manga.id, 4)
                    } else if (item.manga.contentType == 1) {
                        onNavigateToMedia(item.manga.id, 1)
                    } else {
                        if (item.targetChapter != null) {
                            onNavigateToMedia(item.targetChapter.id, 0)
                        } else {
                            onNavigateToDescription(item.manga.id)
                        }
                    }
                },
                glowColor = glowColor
            )

            Spacer(modifier = Modifier.height(28.dp))
        }

        // 5. Recently Added Covers
        if (recentlyAdded.isNotEmpty()) {
            RecentlyAddedSection(
                items = recentlyAdded.take(12),
                onItemClick = { manga -> onNavigateToDescription(manga.id) },
                onViewAllClick = { showRecentlyAddedSheet = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Full Recently Added Window (Modal Bottom Sheet)
    if (showRecentlyAddedSheet) {
        RecentlyAddedSheet(
            allRecentlyAdded = recentlyAdded,
            onDismiss = { showRecentlyAddedSheet = false },
            onItemClick = { manga -> onNavigateToDescription(manga.id) }
        )
    }
}

@Composable
private fun DashboardTopHeader(
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable { onSettingsClick() },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BannerCarouselSection(
    banners: List<String>,
    glowColor: Color,
    onBannerClick: (Int) -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { banners.size })

    // Buttery-smooth cyclic auto-scroll that respects user touch and pauses during drags
    LaunchedEffect(banners.size) {
        if (banners.size <= 1) return@LaunchedEffect
        while (true) {
            delay(4200)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % banners.size
                pagerState.animateScrollToPage(
                    page = next,
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 14.dp,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val context = LocalContext.current
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(740f / 235f)
                    .clickable { onBannerClick(page) },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF10151C),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(banners[page])
                        .crossfade(false)
                        .build(),
                    contentDescription = "Hwaran Feature Banner",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Page indicator dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(banners.size) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 6.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "dotWidth"
                )
                val color by animateColorAsState(
                    targetValue = if (isSelected) glowColor else Color.White.copy(alpha = 0.2f),
                    animationSpec = tween(200),
                    label = "dotColor"
                )
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun MediaShortcutsSection(
    allCount: Int,
    favoriteCount: Int,
    manhuaCount: Int,
    mangaCount: Int,
    novelCount: Int,
    bookCount: Int,
    seriesCount: Int,
    channelCount: Int,
    musicCount: Int,
    onShortcutClick: (tag: String) -> Unit,
    onMusicClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. All
        ShortcutCard(
            icon = Icons.Rounded.GridView,
            title = "All",
            count = allCount,
            onClick = { onShortcutClick("All") }
        )

        // 2. Fav
        ShortcutCard(
            icon = Icons.Rounded.Star,
            title = "Fav",
            count = favoriteCount,
            onClick = { onShortcutClick("Fav") }
        )

        // 3. Manhua
        ShortcutCard(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = "Manhua",
            count = manhuaCount,
            onClick = { onShortcutClick("Manhua") }
        )

        // 4. Manga
        ShortcutCard(
            icon = Icons.Rounded.AutoStories,
            title = "Manga",
            count = mangaCount,
            onClick = { onShortcutClick("Manga") }
        )

        // 5. Light Novel
        ShortcutCard(
            icon = Icons.Rounded.ImportContacts,
            title = "Light Novel",
            count = novelCount,
            onClick = { onShortcutClick("Light Novel") }
        )

        // 6. Book
        ShortcutCard(
            icon = Icons.Rounded.Book,
            title = "Book",
            count = bookCount,
            onClick = { onShortcutClick("Book") }
        )

        // 7. Series
        ShortcutCard(
            icon = Icons.Rounded.PlayCircle,
            title = "Series",
            count = seriesCount,
            onClick = { onShortcutClick("Series") }
        )

        // 8. Channel
        ShortcutCard(
            icon = Icons.Rounded.Subscriptions,
            title = "Channel",
            count = channelCount,
            onClick = { onShortcutClick("Channel") }
        )

        // 9. Music
        ShortcutCard(
            icon = Icons.Rounded.MusicNote,
            title = "Music",
            count = musicCount,
            onClick = onMusicClick
        )
    }
}

@Composable
private fun ShortcutCard(
    icon: ImageVector,
    title: String,
    count: Int,
    accentColor: Color = Color(0xFFE2E8F0),
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xD9111318),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    color = Color(0xFFF0F2F5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$count",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

data class ContinueWatchingItem(
    val manga: MangaEntity,
    val targetChapter: ChapterEntity? = null,
    val allChapters: List<ChapterEntity> = emptyList(),
    val targetIndex: Int = 0,
    val displayTitle: String = manga.title,
    val displaySubtitle: String = manga.lastReadTitle ?: "Media",
    val coverModel: Any? = null,
    val progress: Float = 0f
)

@Composable
private fun ContinueWatchingSection(
    items: List<ContinueWatchingItem>,
    onItemClick: (MangaEntity) -> Unit,
    onPlayItem: (ContinueWatchingItem) -> Unit,
    glowColor: Color
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Continue Watching"
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.manga.id }) { item ->
                Surface(
                    modifier = Modifier
                        .width(220.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onItemClick(item.manga) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF15141E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Cover background (uses resolved track cover for music)
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.coverModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dark gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Black.copy(alpha = 0.92f)
                                        )
                                    )
                                )
                        )

                        // Play / Action Overlay Icon
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(42.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                .clickable { onPlayItem(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Bottom Text & Accurate Progress
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = item.displayTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = item.displaySubtitle,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Accurate Progress bar
                            if (item.progress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(item.progress.coerceIn(0.04f, 1f))
                                            .fillMaxHeight()
                                            .background(glowColor, CircleShape)
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

@Composable
private fun RecentlyAddedSection(
    items: List<MangaEntity>,
    onItemClick: (MangaEntity) -> Unit,
    onViewAllClick: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Recently Added",
            onClick = onViewAllClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { manga ->
                Surface(
                    modifier = Modifier
                        .width(110.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onItemClick(manga) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF161520),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val coverModel = remember(manga.coverPath, manga.parentUri) {
                            CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
                        }
                        if (coverModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(coverModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = manga.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (manga.contentType == 3) Icons.Rounded.MusicNote else Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Title inside the cover art
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.55f),
                                            Color.Black.copy(alpha = 0.88f)
                                        )
                                    )
                                )
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = manga.title,
                                color = Color(0xFFF0F2F5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick)
                    else Modifier
                )
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            if (onClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "View all $title",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentlyAddedSheet(
    allRecentlyAdded: List<MangaEntity>,
    onDismiss: () -> Unit,
    onItemClick: (MangaEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = remember { listOf("All", "Fav", "Manhua", "Manga", "Light Novel", "Book", "Series", "Channel", "Music") }
    val context = LocalContext.current

    val filtered = remember(allRecentlyAdded, selectedFilter) {
        when (selectedFilter) {
            "Fav" -> allRecentlyAdded.filter { it.isFavorite || it.genre?.contains("favorite", ignoreCase = true) == true }
            "Manhua" -> allRecentlyAdded.filter {
                (it.contentType == 0 || it.boxPurpose == "manhua") && (
                    it.boxPurpose == "manhua" ||
                    it.genre?.contains("manhua", ignoreCase = true) == true ||
                    it.genre?.contains("manhwa", ignoreCase = true) == true ||
                    it.genre?.contains("webtoon", ignoreCase = true) == true ||
                    it.title.contains("manhua", ignoreCase = true) ||
                    it.title.contains("manhwa", ignoreCase = true)
                )
            }
            "Manga" -> allRecentlyAdded.filter {
                it.contentType == 0 && it.boxPurpose != "manhua" && it.boxPurpose != "book" && (
                    it.genre == null || (
                        !it.genre.contains("manhua", ignoreCase = true) &&
                        !it.genre.contains("manhwa", ignoreCase = true) &&
                        !it.genre.contains("webtoon", ignoreCase = true)
                    )
                )
            }
            "Light Novel" -> allRecentlyAdded.filter { it.contentType == 4 || it.boxPurpose == "novel" }
            "Book" -> allRecentlyAdded.filter { (it.contentType == 1 || it.boxPurpose == "book") && it.contentType != 4 && it.boxPurpose != "novel" }
            "Series" -> allRecentlyAdded.filter { (it.contentType == 2 || it.boxPurpose == "series") && it.boxPurpose != "channel" }
            "Channel" -> allRecentlyAdded.filter { (it.contentType == 2 || it.boxPurpose == "channel") && it.boxPurpose == "channel" }
            "Music" -> allRecentlyAdded.filter { it.contentType == 3 || it.boxPurpose == "music" }
            else -> allRecentlyAdded
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14121A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Recently Added",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${allRecentlyAdded.size} items in library • newest first",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filters) { filter ->
                    val isSel = selectedFilter == filter
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedFilter = filter },
                        color = if (isSel) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
                        border = BorderStroke(1.dp, if (isSel) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSel) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.60f),
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3-Column Grid of Recent Items
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No items found in $selectedFilter", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    items(filtered, key = { it.id }) { manga ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.68f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onDismiss()
                                    onItemClick(manga)
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF161520),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val coverModel = remember(manga.coverPath, manga.parentUri) {
                                    CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
                                }
                                if (coverModel != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(coverModel)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = manga.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (manga.contentType == 3) Icons.Rounded.MusicNote else Icons.Rounded.Image,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.18f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                // Title inside the cover art
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.55f),
                                                    Color.Black.copy(alpha = 0.88f)
                                                )
                                            )
                                        )
                                        .padding(horizontal = 7.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = manga.title,
                                        color = Color(0xFFF0F2F5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 13.sp
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
