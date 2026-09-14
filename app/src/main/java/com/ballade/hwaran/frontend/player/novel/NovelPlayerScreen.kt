package com.ballade.hwaran.frontend.player.novel

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ballade.hwaran.backend.novel.NovelBook
import com.ballade.hwaran.backend.novel.NovelChapter
import com.ballade.hwaran.backend.novel.NovelParser
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.HistoryEventEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.HistoryTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class NovelTheme(
    val label: String,
    val bg: Color,
    val text: Color,
    val secondaryText: Color,
    val surface: Color,
    val border: Color,
    val accent: Color
) {
    DARK(
        label = "Dark",
        bg = Color(0xFF141418),
        text = Color(0xFFE2E2E6),
        secondaryText = Color(0xFF8E8E98),
        surface = Color(0xFF1E1E26),
        border = Color(0xFF2E2E38),
        accent = Color(0xFFE6E8EC)
    ),
    SEPIA(
        label = "Sepia",
        bg = Color(0xFFF4ECD8),
        text = Color(0xFF3E3127),
        secondaryText = Color(0xFF7A6858),
        surface = Color(0xFFE7DCBF),
        border = Color(0xFFD6C8A6),
        accent = Color(0xFF8D6E63)
    ),
    PARCHMENT(
        label = "Parchment",
        bg = Color(0xFFEAE2CF),
        text = Color(0xFF26231E),
        secondaryText = Color(0xFF6B6458),
        surface = Color(0xFFDDD2BC),
        border = Color(0xFFCAC0A6),
        accent = Color(0xFF6D4C41)
    ),
    OLED_BLACK(
        label = "OLED",
        bg = Color(0xFF000000),
        text = Color(0xFFDCDCDC),
        secondaryText = Color(0xFF787878),
        surface = Color(0xFF121212),
        border = Color(0xFF222222),
        accent = Color(0xFFE6E8EC)
    ),
    LIGHT(
        label = "Paper",
        bg = Color(0xFFFAFAFA),
        text = Color(0xFF1E1E1E),
        secondaryText = Color(0xFF6A6A6A),
        surface = Color(0xFFECECEC),
        border = Color(0xFFDCDCDC),
        accent = Color(0xFF4A5568)
    )
}

enum class NovelReadMode {
    PAGINATED,
    VERTICAL_SCROLL
}

enum class NovelFontFamily(val label: String, val family: FontFamily) {
    SERIF("Serif", FontFamily.Serif),
    SANS("Sans", FontFamily.SansSerif),
    MONO("Mono", FontFamily.Monospace),
    CURSIVE("Cursive", FontFamily.Cursive)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelPlayerScreen(
    mangaId: Long = 0L,
    externalUriString: String? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember(context) { AppDatabase.getDatabase(context) }

    // State
    var novelBook by remember { mutableStateOf<NovelBook?>(null) }
    var mangaEntity by remember { mutableStateOf<MangaEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentChapterIndex by rememberSaveable { mutableIntStateOf(0) }
    var isControlsVisible by rememberSaveable { mutableStateOf(false) }
    var showTocDrawer by remember { mutableStateOf(false) }
    var showTypographySheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var bookmarkedChapters by remember { mutableStateOf(setOf<Int>()) }

    // Preferences
    var currentTheme by rememberSaveable { mutableStateOf(NovelTheme.DARK) }
    var readMode by rememberSaveable { mutableStateOf(NovelReadMode.VERTICAL_SCROLL) }
    var currentFont by rememberSaveable { mutableStateOf(NovelFontFamily.SERIF) }
    var fontSizeSp by rememberSaveable { mutableFloatStateOf(18f) }
    var lineHeightMultiplier by rememberSaveable { mutableFloatStateOf(1.65f) }
    var paragraphSpacingDp by rememberSaveable { mutableIntStateOf(14) }
    var horizontalMarginDp by rememberSaveable { mutableIntStateOf(20) }

    // Immersive Fullscreen Mode Controller
    val activity = context as? Activity
    DisposableEffect(isControlsVisible) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isControlsVisible) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Load Novel Data
    LaunchedEffect(mangaId, externalUriString) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                if (externalUriString != null) {
                    val uri = Uri.parse(externalUriString)
                    val book = NovelParser.parseNovel(context, uri)
                    novelBook = book
                } else if (mangaId > 0L) {
                    var manga = database.mediaDao().getMangaById(mangaId)
                    var targetChapterIndex = 0
                    if (manga == null) {
                        val chapter = database.trackDao().getChapterById(mangaId)
                        if (chapter != null) {
                            manga = database.mediaDao().getMangaById(chapter.mangaId)
                            targetChapterIndex = chapter.position
                        }
                    }
                    mangaEntity = manga
                    if (manga != null) {
                        val dbChapters = database.trackDao().getChaptersForMangaList(manga.id)
                        val book = if (dbChapters.isNotEmpty() && dbChapters.any { it.folderUri.lowercase().let { u -> u.endsWith(".txt") || u.endsWith(".md") || u.endsWith(".markdown") || u.endsWith(".epub") } }) {
                            NovelParser.parseNovelFromChapterEntities(context, manga.title, dbChapters)
                        } else {
                            val uri = Uri.parse(manga.parentUri)
                            val loaded = if (manga.parentUri.startsWith("file://") || manga.parentUri.startsWith("/")) {
                                val file = if (manga.parentUri.startsWith("file://")) File(Uri.parse(manga.parentUri).path ?: "") else File(manga.parentUri)
                                if (file.exists()) NovelParser.parseNovelFromFile(file) else NovelParser.parseNovel(context, uri, manga.title)
                            } else {
                                NovelParser.parseNovel(context, uri, manga.title)
                            }
                            if (loaded.chapters.isEmpty() && dbChapters.isNotEmpty()) {
                                NovelParser.parseNovelFromChapterEntities(context, manga.title, dbChapters)
                            } else loaded
                        }
                        novelBook = book

                        // Restore last read position
                        val savedChapterIndex = if (targetChapterIndex > 0) targetChapterIndex else (manga.lastReadPage ?: 0)
                        if (book.chapters.isNotEmpty()) {
                            currentChapterIndex = savedChapterIndex.coerceIn(0, book.chapters.size - 1)
                        }

                        // Record History Event
                        database.historyDao().insertHistoryEvent(
                            HistoryEventEntity(
                                timestamp = System.currentTimeMillis(),
                                eventType = "READ_NOVEL",
                                itemName = manga.title,
                                details = "Opened Chapter ${currentChapterIndex + 1}"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    // Save progress helper
    fun saveProgress(chapterIdx: Int) {
        if (mangaId > 0L && novelBook != null && chapterIdx in novelBook!!.chapters.indices) {
            val chapterTitle = novelBook!!.chapters[chapterIdx].title
            coroutineScope.launch(Dispatchers.IO) {
                val current = database.mediaDao().getMangaById(mangaId) ?: return@launch
                val updated = current.copy(
                    lastReadPage = chapterIdx,
                    lastReadTitle = chapterTitle
                )
                database.mediaDao().insertManga(updated)
                HistoryTracker.logEvent(
                    "READ_NOVEL",
                    chapterTitle,
                    "mangaId:$mangaId|pages:${chapterIdx + 1}|totalPages:${novelBook?.chapters?.size ?: 0}"
                )
            }
        }
    }

    BackHandler {
        if (showTocDrawer) {
            showTocDrawer = false
        } else if (showTypographySheet) {
            showTypographySheet = false
        } else if (showBookmarksSheet) {
            showBookmarksSheet = false
        } else if (isControlsVisible) {
            isControlsVisible = false
        } else {
            onNavigateBack()
        }
    }

    val chapters = novelBook?.chapters ?: emptyList()
    val activeChapter = chapters.getOrNull(currentChapterIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentTheme.bg)
    ) {
        if (isLoading) {
            // Ambient Loading Screen
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.9f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glowAlpha"
                )

                Surface(
                    shape = CircleShape,
                    color = currentTheme.surface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = "Loading Novel",
                            tint = Color(0xFFE6E8EC),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Opening Novel...",
                    color = currentTheme.text,
                    fontSize = 16.sp,
                    fontFamily = currentFont.family,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (chapters.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.MenuBook,
                    contentDescription = null,
                    tint = currentTheme.secondaryText,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No readable chapters found in this file.",
                    color = currentTheme.text,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = currentTheme.surface)
                ) {
                    Text("Go Back", color = currentTheme.text)
                }
            }
        } else {
            // ─────────────────────────────────────────────────────────────────────────────
            // Reading View (Paginated vs Vertical Scroll)
            // ─────────────────────────────────────────────────────────────────────────────

            if (readMode == NovelReadMode.VERTICAL_SCROLL) {
                // Continuous Vertical Scroll Mode
                val listState = rememberLazyListState()

                // When chapter changes via slider or TOC, scroll to top
                LaunchedEffect(currentChapterIndex) {
                    listState.scrollToItem(0)
                    saveProgress(currentChapterIndex)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    isControlsVisible = !isControlsVisible
                                }
                            )
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = if (isControlsVisible) 88.dp else 44.dp,
                            bottom = if (isControlsVisible) 130.dp else 60.dp,
                            start = horizontalMarginDp.dp,
                            end = horizontalMarginDp.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "chapter_header_${currentChapterIndex}") {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                                Text(
                                    text = activeChapter?.title ?: "",
                                    fontFamily = currentFont.family,
                                    fontSize = (fontSizeSp + 6).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentTheme.text,
                                    lineHeight = ((fontSizeSp + 6) * 1.3f).sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Chapter ${currentChapterIndex + 1} of ${chapters.size}",
                                        color = currentTheme.secondaryText,
                                        fontSize = 13.sp,
                                        fontFamily = currentFont.family
                                    )
                                    Text(
                                        text = "•",
                                        color = currentTheme.secondaryText.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${activeChapter?.wordCount ?: 0} words",
                                        color = currentTheme.secondaryText,
                                        fontSize = 13.sp,
                                        fontFamily = currentFont.family
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = currentTheme.border.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        item(key = "chapter_content_${currentChapterIndex}") {
                            val paragraphs = remember(activeChapter?.content) {
                                (activeChapter?.content ?: "").split(Regex("""\n\s*\n"""))
                            }

                            SelectionContainer {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp.dp)
                                ) {
                                    paragraphs.forEach { paragraph ->
                                        if (paragraph.isNotBlank()) {
                                            Text(
                                                text = paragraph.trim(),
                                                color = currentTheme.text,
                                                fontSize = fontSizeSp.sp,
                                                fontFamily = currentFont.family,
                                                lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Chapter Skip Actions
                        item(key = "chapter_footer_${currentChapterIndex}") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                HorizontalDivider(color = currentTheme.border.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (currentChapterIndex > 0) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                currentChapterIndex--
                                            }
                                        },
                                        enabled = currentChapterIndex > 0,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = currentTheme.surface,
                                            contentColor = currentTheme.text
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Prev Chapter")
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            if (currentChapterIndex < chapters.size - 1) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                currentChapterIndex++
                                            }
                                        },
                                        enabled = currentChapterIndex < chapters.size - 1,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = currentTheme.surface,
                                            contentColor = currentTheme.text
                                        )
                                    ) {
                                        Text("Next Chapter")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Paginated Book Flip Mode
                val pagerState = rememberPagerState(
                    initialPage = currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)),
                    pageCount = { chapters.size }
                )

                LaunchedEffect(pagerState.currentPage) {
                    currentChapterIndex = pagerState.currentPage
                    saveProgress(currentChapterIndex)
                }

                LaunchedEffect(currentChapterIndex) {
                    if (pagerState.currentPage != currentChapterIndex) {
                        pagerState.scrollToPage(currentChapterIndex)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val width = size.width
                                    when {
                                        offset.x < width * 0.25f -> {
                                            // Tap Left: Prev Page/Chapter
                                            if (pagerState.currentPage > 0) {
                                                coroutineScope.launch {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            }
                                        }
                                        offset.x > width * 0.75f -> {
                                            // Tap Right: Next Page/Chapter
                                            if (pagerState.currentPage < chapters.size - 1) {
                                                coroutineScope.launch {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            }
                                        }
                                        else -> {
                                            // Tap Center: Toggle HUD
                                            isControlsVisible = !isControlsVisible
                                        }
                                    }
                                }
                            )
                        }
                ) { page ->
                    val chapter = chapters[page]
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(
                                top = if (isControlsVisible) 88.dp else 40.dp,
                                bottom = if (isControlsVisible) 120.dp else 44.dp,
                                start = horizontalMarginDp.dp,
                                end = horizontalMarginDp.dp
                            )
                    ) {
                        Text(
                            text = chapter.title,
                            fontFamily = currentFont.family,
                            fontSize = (fontSizeSp + 6).sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.text,
                            lineHeight = ((fontSizeSp + 6) * 1.3f).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Chapter ${page + 1} of ${chapters.size} • ${chapter.wordCount} words",
                            color = currentTheme.secondaryText,
                            fontSize = 12.sp,
                            fontFamily = currentFont.family
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = currentTheme.border.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(20.dp))

                        val paragraphs = remember(chapter.content) {
                            chapter.content.split(Regex("""\n\s*\n"""))
                        }

                        SelectionContainer {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp.dp)
                            ) {
                                paragraphs.forEach { p ->
                                    if (p.isNotBlank()) {
                                        Text(
                                            text = p.trim(),
                                            color = currentTheme.text,
                                            fontSize = fontSizeSp.sp,
                                            fontFamily = currentFont.family,
                                            lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────────────────
            // Top HUD Bar
            // ─────────────────────────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, spotColor = Color.Black.copy(alpha = 0.3f)),
                    color = currentTheme.surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, currentTheme.border.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = currentTheme.text)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = novelBook?.title ?: mangaEntity?.title ?: "Novel Reader",
                                color = currentTheme.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeChapter?.title ?: "",
                                color = currentTheme.secondaryText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Bookmark toggle
                        val isBookmarked = bookmarkedChapters.contains(currentChapterIndex)
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                bookmarkedChapters = if (isBookmarked) {
                                    bookmarkedChapters - currentChapterIndex
                                } else {
                                    bookmarkedChapters + currentChapterIndex
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) currentTheme.accent else currentTheme.secondaryText
                            )
                        }

                        // Table of Contents Toggle
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showTocDrawer = true
                            }
                        ) {
                            Icon(Icons.Rounded.FormatListBulleted, contentDescription = "Table of Contents", tint = currentTheme.text)
                        }

                        // Typography & Appearance Settings Toggle
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showTypographySheet = true
                            }
                        ) {
                            Icon(Icons.Rounded.FormatSize, contentDescription = "Appearance", tint = currentTheme.text)
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────────────────
            // Bottom HUD Bar
            // ─────────────────────────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, spotColor = Color.Black.copy(alpha = 0.4f)),
                    color = currentTheme.surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, currentTheme.border.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Chapter progress indicator and chapter seek
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Chapter ${currentChapterIndex + 1} of ${chapters.size}",
                                color = currentTheme.secondaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            val progressPercent = if (chapters.isNotEmpty()) {
                                ((currentChapterIndex + 1).toFloat() / chapters.size.toFloat() * 100f).toInt()
                            } else 0

                            Text(
                                text = "$progressPercent%",
                                color = currentTheme.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Progress Slider
                        if (chapters.size > 1) {
                            Slider(
                                value = currentChapterIndex.toFloat(),
                                onValueChange = { newIdx ->
                                    currentChapterIndex = newIdx.toInt().coerceIn(0, chapters.size - 1)
                                },
                                onValueChangeFinished = {
                                    saveProgress(currentChapterIndex)
                                },
                                valueRange = 0f..(chapters.size - 1).toFloat(),
                                steps = (chapters.size - 2).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = currentTheme.accent,
                                    activeTrackColor = currentTheme.accent,
                                    inactiveTrackColor = currentTheme.border
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Bottom Actions: Prev Chapter, Mode Toggle, Theme Quick Picker, Next Chapter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentChapterIndex > 0) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentChapterIndex--
                                    }
                                },
                                enabled = currentChapterIndex > 0
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Previous",
                                    tint = if (currentChapterIndex > 0) currentTheme.text else currentTheme.secondaryText.copy(alpha = 0.3f)
                                )
                            }

                            // Read Mode Toggle (Paginated vs Scroll)
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    readMode = if (readMode == NovelReadMode.PAGINATED) NovelReadMode.VERTICAL_SCROLL else NovelReadMode.PAGINATED
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = currentTheme.bg,
                                border = BorderStroke(1.dp, currentTheme.border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (readMode == NovelReadMode.PAGINATED) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.SwapVert,
                                        contentDescription = null,
                                        tint = currentTheme.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (readMode == NovelReadMode.PAGINATED) "Paginated" else "Scroll",
                                        color = currentTheme.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Quick Themes Row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NovelTheme.values().forEach { theme ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(theme.bg)
                                            .border(
                                                width = if (currentTheme == theme) 2.dp else 1.dp,
                                                color = if (currentTheme == theme) currentTheme.accent else theme.border,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                currentTheme = theme
                                            }
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (currentChapterIndex < chapters.size - 1) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentChapterIndex++
                                    }
                                },
                                enabled = currentChapterIndex < chapters.size - 1
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Next",
                                    tint = if (currentChapterIndex < chapters.size - 1) currentTheme.text else currentTheme.secondaryText.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────────────────
            // Table of Contents Modal Bottom Sheet
            // ─────────────────────────────────────────────────────────────────────────────
            if (showTocDrawer) {
                var searchQuery by remember { mutableStateOf("") }
                val filteredChapters = remember(chapters, searchQuery) {
                    if (searchQuery.isBlank()) chapters
                    else chapters.filter { it.title.contains(searchQuery, ignoreCase = true) }
                }

                ModalBottomSheet(
                    onDismissRequest = { showTocDrawer = false },
                    containerColor = currentTheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = currentTheme.secondaryText) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Table of Contents",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentTheme.text
                            )
                            Text(
                                text = "${chapters.size} Chapters",
                                fontSize = 13.sp,
                                color = currentTheme.secondaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Search Chapters
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search chapter title...", color = currentTheme.secondaryText) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = currentTheme.secondaryText) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = currentTheme.text,
                                unfocusedTextColor = currentTheme.text,
                                focusedBorderColor = currentTheme.accent,
                                unfocusedBorderColor = currentTheme.border
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(filteredChapters) { _, chapter ->
                                val isSelected = chapter.index == currentChapterIndex
                                val isBookmarked = bookmarkedChapters.contains(chapter.index)

                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentChapterIndex = chapter.index
                                        saveProgress(chapter.index)
                                        showTocDrawer = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) currentTheme.accent.copy(alpha = 0.15f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${chapter.index + 1}",
                                            color = if (isSelected) currentTheme.accent else currentTheme.secondaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(36.dp)
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = chapter.title,
                                                color = if (isSelected) currentTheme.accent else currentTheme.text,
                                                fontSize = 15.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${chapter.wordCount} words",
                                                color = currentTheme.secondaryText,
                                                fontSize = 12.sp
                                            )
                                        }

                                        if (isBookmarked) {
                                            Icon(
                                                imageVector = Icons.Rounded.Bookmark,
                                                contentDescription = "Bookmarked",
                                                tint = currentTheme.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────────────────
            // Typography & Appearance Modal Bottom Sheet
            // ─────────────────────────────────────────────────────────────────────────────
            if (showTypographySheet) {
                ModalBottomSheet(
                    onDismissRequest = { showTypographySheet = false },
                    containerColor = currentTheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = currentTheme.secondaryText) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 36.dp)
                    ) {
                        Text(
                            text = "Reading Appearance",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.text
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Theme Selector
                        Text("Theme", color = currentTheme.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            NovelTheme.values().forEach { theme ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentTheme = theme
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(theme.bg)
                                            .border(
                                                width = if (currentTheme == theme) 2.5.dp else 1.dp,
                                                color = if (currentTheme == theme) currentTheme.accent else theme.border,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Aa", color = theme.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = theme.label,
                                        color = if (currentTheme == theme) currentTheme.accent else currentTheme.secondaryText,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Font Family Selector
                        Text("Font Family", color = currentTheme.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NovelFontFamily.values().forEach { font ->
                                val isSelected = currentFont == font
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentFont = font
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) currentTheme.accent.copy(alpha = 0.2f) else currentTheme.bg,
                                    border = BorderStroke(1.dp, if (isSelected) currentTheme.accent else currentTheme.border),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = font.label,
                                            fontFamily = font.family,
                                            color = if (isSelected) currentTheme.accent else currentTheme.text,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Font Size Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Font Size", color = currentTheme.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${fontSizeSp.toInt()} sp", color = currentTheme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = fontSizeSp,
                            onValueChange = { fontSizeSp = it },
                            valueRange = 14f..32f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = currentTheme.accent,
                                activeTrackColor = currentTheme.accent,
                                inactiveTrackColor = currentTheme.border
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Line Spacing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Line Spacing", color = currentTheme.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1.4f, 1.65f, 1.9f, 2.2f).forEach { multiplier ->
                                    val isSelected = (lineHeightMultiplier - multiplier) in -0.05f..0.05f
                                    Surface(
                                        onClick = { lineHeightMultiplier = multiplier },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) currentTheme.accent.copy(alpha = 0.2f) else currentTheme.bg,
                                        border = BorderStroke(1.dp, if (isSelected) currentTheme.accent else currentTheme.border)
                                    ) {
                                        Text(
                                            text = "${multiplier}x",
                                            color = if (isSelected) currentTheme.accent else currentTheme.text,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Page Margins
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Page Margin", color = currentTheme.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(14, 20, 28, 36).forEach { margin ->
                                    val isSelected = horizontalMarginDp == margin
                                    Surface(
                                        onClick = { horizontalMarginDp = margin },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) currentTheme.accent.copy(alpha = 0.2f) else currentTheme.bg,
                                        border = BorderStroke(1.dp, if (isSelected) currentTheme.accent else currentTheme.border)
                                    ) {
                                        Text(
                                            text = "${margin}dp",
                                            color = if (isSelected) currentTheme.accent else currentTheme.text,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
