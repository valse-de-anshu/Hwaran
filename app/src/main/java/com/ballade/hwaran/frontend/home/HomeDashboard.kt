package com.ballade.hwaran.frontend.home

import android.content.Context
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
    onNavigateToHistory: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onMediaShortcutClick: (tag: String) -> Unit,
    onOpenMusic: () -> Unit = {},
    glowColor: Color = Color(0xFF9C27B0),
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
    val bookCount = remember(allManga) {
        allManga.count { !it.isNsfw && (it.contentType == 1 || it.boxPurpose == "book") }
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

    // Continue watching / in progress covers
    val inProgressItems = remember(allManga, historyEvents) {
        val opened = allManga.filter { it.openCount > 0 || it.lastReadTitle != null }
            .sortedByDescending { it.lastModified }
        if (opened.isNotEmpty()) opened.take(8)
        else allManga.take(4)
    }

    // Real progress map computed asynchronously from database
    var itemProgressMap by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    LaunchedEffect(inProgressItems) {
        withContext(Dispatchers.IO) {
            val map = mutableMapOf<Long, Float>()
            for (manga in inProgressItems) {
                val chapters = database.trackDao().getChaptersForMangaList(manga.id)
                if (chapters.isNotEmpty()) {
                    val target = chapters.firstOrNull { it.title == manga.lastReadTitle }
                        ?: chapters.firstOrNull { it.position > 0 }
                        ?: chapters.first()
                    if (manga.contentType == 2) {
                        if (target.duration > 0) {
                            map[manga.id] = (target.position.toFloat() / target.duration.toFloat()).coerceIn(0.02f, 1f)
                        } else if (target.position > 0) {
                            map[manga.id] = 0.2f
                        } else {
                            map[manga.id] = 0f
                        }
                    } else {
                        val idx = chapters.indexOfFirst { it.title == manga.lastReadTitle }.takeIf { it >= 0 } ?: 0
                        map[manga.id] = ((idx + 1).toFloat() / chapters.size.toFloat()).coerceIn(0.05f, 1f)
                    }
                } else {
                    if (manga.lastReadPage != null && manga.lastReadPage > 0) {
                        map[manga.id] = 0.3f
                    } else {
                        map[manga.id] = 0f
                    }
                }
            }
            itemProgressMap = map
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
        // 1. Clean Top Header (3-dot overflow only; redundant top search removed)
        DashboardTopHeader(
            onSettingsClick = onNavigateToSettings,
            onHistoryClick = onNavigateToHistory
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

        // 3. Media Shortcuts (Manhua, Manga, Series, Book, Channel, Music, Favorite)
        MediaShortcutsSection(
            manhuaCount = manhuaCount,
            mangaCount = mangaCount,
            seriesCount = seriesCount,
            bookCount = bookCount,
            channelCount = channelCount,
            musicCount = musicCount,
            favoriteCount = favoriteCount,
            onShortcutClick = onMediaShortcutClick,
            onMusicClick = onOpenMusic
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 4. Continue Watching / Reading
        if (inProgressItems.isNotEmpty()) {
            ContinueWatchingSection(
                items = inProgressItems,
                onItemClick = { manga -> onNavigateToDescription(manga.id) },
                onViewAllClick = onNavigateToHistory,
                itemProgressMap = itemProgressMap,
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
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Three-Dot Overflow Menu Button
        Box {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable { showMenu = !showMenu },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(Color(0xFF1B1924))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("History & Insights", color = Color.White) },
                    leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, tint = Color.White) },
                    onClick = {
                        showMenu = false
                        onHistoryClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Settings", color = Color.White) },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = Color.White) },
                    onClick = {
                        showMenu = false
                        onSettingsClick()
                    }
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
    var scrollForward by remember { mutableStateOf(true) }

    // Natural auto-scroll left and right
    LaunchedEffect(pagerState, banners.size) {
        if (banners.size <= 1) return@LaunchedEffect
        while (true) {
            delay(4000)
            if (!pagerState.isScrollInProgress) {
                val current = pagerState.currentPage
                val target = if (scrollForward) {
                    if (current < banners.size - 1) {
                        current + 1
                    } else {
                        scrollForward = false
                        current - 1
                    }
                } else {
                    if (current > 0) {
                        current - 1
                    } else {
                        scrollForward = true
                        current + 1
                    }
                }
                pagerState.animateScrollToPage(
                    page = target,
                    animationSpec = tween(
                        durationMillis = 850,
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
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val context = LocalContext.current
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(740f / 235f)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(18.dp),
                        spotColor = glowColor.copy(alpha = 0.25f)
                    )
                    .clickable { onBannerClick(page) },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF10151C),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(banners[page])
                        .crossfade(true)
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
                    animationSpec = spring(stiffness = 300f),
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
    manhuaCount: Int,
    mangaCount: Int,
    seriesCount: Int,
    bookCount: Int,
    channelCount: Int,
    musicCount: Int,
    favoriteCount: Int,
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
        // Manhua
        ShortcutCard(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = "Manhua",
            count = manhuaCount,
            accentColor = Color(0xFFBA68C8),
            onClick = { onShortcutClick("Manhua") }
        )

        // Manga
        ShortcutCard(
            icon = Icons.Rounded.AutoStories,
            title = "Manga",
            count = mangaCount,
            accentColor = Color(0xFF81C784),
            onClick = { onShortcutClick("Manga") }
        )

        // Music
        ShortcutCard(
            icon = Icons.Rounded.MusicNote,
            title = "Music",
            count = musicCount,
            accentColor = Color(0xFFEC407A),
            onClick = onMusicClick
        )

        // Series
        ShortcutCard(
            icon = Icons.Rounded.PlayCircle,
            title = "Series",
            count = seriesCount,
            accentColor = Color(0xFF64B5F6),
            onClick = { onShortcutClick("Series") }
        )

        // Book
        ShortcutCard(
            icon = Icons.Rounded.Book,
            title = "Book",
            count = bookCount,
            accentColor = Color(0xFFFFB74D),
            onClick = { onShortcutClick("Book") }
        )

        // Channel
        ShortcutCard(
            icon = Icons.Rounded.Subscriptions,
            title = "Channel",
            count = channelCount,
            accentColor = Color(0xFFFF7043),
            onClick = { onShortcutClick("Channel") }
        )

        // Favorite
        if (favoriteCount > 0) {
            ShortcutCard(
                icon = Icons.Rounded.Star,
                title = "Favorite",
                count = favoriteCount,
                accentColor = Color(0xFFFFD54F),
                onClick = { onShortcutClick("Favorite") }
            )
        }
    }
}

@Composable
private fun ShortcutCard(
    icon: ImageVector,
    title: String,
    count: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xDD181622),
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
                    .background(accentColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$count",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    items: List<MangaEntity>,
    onItemClick: (MangaEntity) -> Unit,
    onViewAllClick: () -> Unit,
    itemProgressMap: Map<Long, Float>,
    glowColor: Color
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Continue Watching",
            onClick = onViewAllClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.id }) { manga ->
                val progress = itemProgressMap[manga.id] ?: 0f

                Surface(
                    modifier = Modifier
                        .width(220.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onItemClick(manga) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF15141E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Cover background
                        val coverModel = remember(manga.coverPath, manga.parentUri) {
                            CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(coverModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = manga.title,
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
                                .size(38.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Bottom Text & Accurate Progress
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = manga.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = manga.lastReadTitle ?: when (manga.contentType) {
                                    0 -> "Toon"
                                    1 -> "Book"
                                    2 -> "Video"
                                    3 -> "Music"
                                    else -> "Media"
                                },
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Accurate Progress bar
                            if (progress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress.coerceIn(0.04f, 1f))
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
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onItemClick(manga) }
                ) {
                    // Portrait Cover
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF161520),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        val coverModel = remember(manga.coverPath, manga.parentUri) {
                            CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(coverModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = manga.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = manga.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val typeLabel = when (manga.contentType) {
                        0 -> if (manga.genre?.contains("manhua", ignoreCase = true) == true) "Manhua" else "Manga"
                        1 -> "Book • PDF"
                        2 -> if (manga.boxPurpose == "channel") "Video • Channel" else "Video • Series"
                        3 -> "Music"
                        else -> "Media"
                    }

                    Text(
                        text = typeLabel,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "View all $title",
                tint = if (onClick != null) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
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
    val filters = remember { listOf("All", "Manhua", "Manga", "Series", "Book", "Channel") }
    val context = LocalContext.current

    val filtered = remember(allRecentlyAdded, selectedFilter) {
        when (selectedFilter) {
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
            "Series" -> allRecentlyAdded.filter { (it.contentType == 2 || it.boxPurpose == "series") && it.boxPurpose != "channel" }
            "Book" -> allRecentlyAdded.filter { it.contentType == 1 || it.boxPurpose == "book" }
            "Channel" -> allRecentlyAdded.filter { (it.contentType == 2 || it.boxPurpose == "channel") && it.boxPurpose == "channel" }
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
                        color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onDismiss()
                                    onItemClick(manga)
                                }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.68f),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF161520),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                val coverModel = remember(manga.coverPath, manga.parentUri) {
                                    CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
                                }
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(coverModel)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = manga.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = manga.title,
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
