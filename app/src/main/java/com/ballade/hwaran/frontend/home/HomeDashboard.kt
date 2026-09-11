package com.ballade.hwaran.frontend.home

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.ballade.hwaran.core.database.entity.HistoryEventEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.delay

@Composable
fun HomeDashboard(
    allManga: List<MangaEntity>,
    historyEvents: List<HistoryEventEntity>,
    onNavigateToDescription: (Long) -> Unit,
    onNavigateToMedia: (Long, Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onMediaShortcutClick: (mediaMode: Int, videoLayoutMode: Int) -> Unit,
    glowColor: Color = Color(0xFF9C27B0),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

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

    // Counts for shortcuts & stats
    val toonCount = remember(allManga) { allManga.count { it.contentType == 0 && !it.isNsfw } }
    val bookCount = remember(allManga) { allManga.count { it.contentType == 1 && !it.isNsfw } }
    val seriesCount = remember(allManga) { allManga.count { it.contentType == 2 && it.boxPurpose == "series" && !it.isNsfw } }
    val channelCount = remember(allManga) { allManga.count { it.contentType == 2 && it.boxPurpose == "channel" && !it.isNsfw } }
    val musicCount = remember(allManga) { allManga.count { it.contentType == 3 } }

    // Continue watching / in progress covers
    val inProgressItems = remember(allManga, historyEvents) {
        val opened = allManga.filter { it.openCount > 0 || it.lastReadTitle != null }
            .sortedByDescending { it.lastModified }
        if (opened.isNotEmpty()) opened.take(8)
        else allManga.take(4)
    }

    // Recently added covers
    val recentlyAdded = remember(allManga) {
        allManga.filter { !it.isNsfw }.sortedByDescending { it.id }.take(12)
    }

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
        // 1. Clean Top Header (Compact Search + 3-dot overflow only; no logo, no tagline)
        DashboardTopHeader(
            onSearchClick = onNavigateToSearch,
            onSettingsClick = onNavigateToSettings,
            onHistoryClick = onNavigateToHistory
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Large Horizontally Swipeable Promo Banner Carousel
        BannerCarouselSection(
            banners = promoBanners,
            glowColor = glowColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Media Shortcuts (Toon, Books, Series Video, Channel Video, Music)
        MediaShortcutsSection(
            toonCount = toonCount,
            bookCount = bookCount,
            seriesCount = seriesCount,
            channelCount = channelCount,
            musicCount = musicCount,
            onShortcutClick = onMediaShortcutClick
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 4. Continue Watching / Reading
        if (inProgressItems.isNotEmpty()) {
            ContinueWatchingSection(
                items = inProgressItems,
                onItemClick = { manga -> onNavigateToDescription(manga.id) },
                glowColor = glowColor
            )

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Recently Added Covers
        if (recentlyAdded.isNotEmpty()) {
            RecentlyAddedSection(
                items = recentlyAdded,
                onItemClick = { manga -> onNavigateToDescription(manga.id) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DashboardTopHeader(
    onSearchClick: () -> Unit,
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
        // Compact Circular Search Button
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onSearchClick),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

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
    glowColor: Color
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
                    ),
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
    toonCount: Int,
    bookCount: Int,
    seriesCount: Int,
    channelCount: Int,
    musicCount: Int,
    onShortcutClick: (mediaMode: Int, videoLayoutMode: Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Toon
        ShortcutCard(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = "Toon",
            count = toonCount,
            accentColor = Color(0xFFBA68C8),
            onClick = { onShortcutClick(0, 0) }
        )

        // Books
        ShortcutCard(
            icon = Icons.Rounded.Book,
            title = "Books",
            count = bookCount,
            accentColor = Color(0xFFFFB74D),
            onClick = { onShortcutClick(1, 0) }
        )

        // Series Video
        ShortcutCard(
            icon = Icons.Rounded.PlayCircle,
            title = "Series Video",
            count = seriesCount,
            accentColor = Color(0xFF8EB69B),
            onClick = { onShortcutClick(2, 0) }
        )

        // Channel Video
        ShortcutCard(
            icon = Icons.Rounded.Subscriptions,
            title = "Channel Video",
            count = channelCount,
            accentColor = Color(0xFFFF7043),
            onClick = { onShortcutClick(2, 1) }
        )

        // Music
        ShortcutCard(
            icon = Icons.Rounded.MusicNote,
            title = "Music",
            count = musicCount,
            accentColor = Color(0xFF7986CB),
            onClick = { onShortcutClick(3, 0) }
        )
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
    glowColor: Color
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Continue Watching")

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.id }) { manga ->
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
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(manga.coverPath)
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

                        // Bottom Text & Progress
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

                            // Subtle progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
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

@Composable
private fun RecentlyAddedSection(
    items: List<MangaEntity>,
    onItemClick: (MangaEntity) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Recently Added")

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
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(manga.coverPath)
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
                        0 -> "Manga"
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
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
