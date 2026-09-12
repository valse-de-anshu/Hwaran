package com.ballade.hwaran.frontend.player.toon

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.ballade.hwaran.core.datastore.GlobalSettings
import com.ballade.hwaran.ui.viewmodels.ReaderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    chapterId: Long,
    externalUri: String? = null,
    readerViewModel: ReaderViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChapter: (Long) -> Unit
) {
    ToonPlayerScreen(chapterId, externalUri, readerViewModel, onNavigateBack, onNavigateToChapter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToonPlayerScreen(
    chapterId: Long,
    externalUri: String? = null,
    readerViewModel: ReaderViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChapter: (Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val globalSettings = remember { GlobalSettings(context) }
    val readerMode by globalSettings.readerModeFlow.collectAsState(initial = 0)
    val cropZoom by globalSettings.readerCropZoomFlow.collectAsState(initial = 1.0f)
    val readerDirection by globalSettings.readerDirectionFlow.collectAsState(initial = 0)
    val readerBgColor by globalSettings.readerBgColorFlow.collectAsState(initial = 0)
    val keepScreenOn by globalSettings.readerKeepScreenOnFlow.collectAsState(initial = true)

    var brightnessOverride by remember { mutableStateOf<Float?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showFloatingControls by remember { mutableStateOf(true) }

    val images by readerViewModel.images.collectAsState()
    val isLoading by readerViewModel.isLoading.collectAsState()
    val chapter by readerViewModel.chapter.collectAsState()
    val mangaTitle by readerViewModel.mangaTitle.collectAsState()
    val startPage by readerViewModel.startPage.collectAsState()
    val allChapters by readerViewModel.allChapters.collectAsState()
    val nextChapterId by readerViewModel.nextChapterId.collectAsState()
    val prevChapterId by readerViewModel.prevChapterId.collectAsState()

    val PrimaryPurple = MaterialTheme.colorScheme.primary
    val appGradient = com.ballade.hwaran.ui.theme.LocalAppGradient.current

    // Background selection
    val canvasBackground = when (readerBgColor) {
        1 -> Color.Black
        2 -> Color(0xFF12111A)
        3 -> Color.White
        else -> null
    }

    val bgModifier = when {
        canvasBackground != null -> Modifier.background(canvasBackground)
        appGradient != null -> Modifier.background(appGradient)
        else -> Modifier.background(MaterialTheme.colorScheme.background)
    }

    // Load Chapter Data
    LaunchedEffect(chapterId, externalUri) {
        if (externalUri != null) {
            readerViewModel.loadExternalImage(externalUri)
        } else {
            readerViewModel.loadChapter(chapterId)
        }
    }

    // Keep Screen On Effect
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Screen Brightness Effect
    DisposableEffect(brightnessOverride) {
        val window = activity?.window
        val lp = window?.attributes
        val originalBrightness = lp?.screenBrightness ?: -1f
        if (brightnessOverride != null && lp != null) {
            lp.screenBrightness = brightnessOverride!!
            window.attributes = lp
        }
        onDispose {
            if (lp != null) {
                lp.screenBrightness = originalBrightness
                window.attributes = lp
            }
        }
    }

    // Back handler
    BackHandler {
        if (showSettingsSheet) {
            showSettingsSheet = false
        } else if (showChapterList) {
            showChapterList = false
        } else {
            onNavigateBack()
        }
    }

    // State for Long Strip mode
    val listState = rememberLazyListState()
    val currentStripPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    // State for Page-by-Page mode
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { images.size }
    )

    // Sync startPage when loaded
    var hasRestoredStartPage by remember(chapterId) { mutableStateOf(false) }
    LaunchedEffect(images, startPage) {
        if (!hasRestoredStartPage && images.isNotEmpty() && startPage > 0 && startPage < images.size) {
            hasRestoredStartPage = true
            listState.scrollToItem(startPage)
            pagerState.scrollToPage(startPage)
        }
    }

    // Track active page index across modes
    val activePageIndex = if (readerMode == 0) currentStripPage else pagerState.currentPage

    // Preload next images
    LaunchedEffect(activePageIndex, images) {
        if (images.isEmpty()) return@LaunchedEffect
        val startPreload = (activePageIndex - 2).coerceAtLeast(0)
        val endPreload = (activePageIndex + 6).coerceAtMost(images.size - 1)
        val imageLoader = context.imageLoader
        for (i in startPreload..endPreload) {
            val filePath = images[i]
            val data = if (filePath.startsWith("content://")) Uri.parse(filePath) else java.io.File(filePath)
            val request = ImageRequest.Builder(context)
                .data(data)
                .build()
            imageLoader.enqueue(request)
        }
    }

    // Save Reading Progress on Lifecycle
    DisposableEffect(lifecycleOwner, activePageIndex) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                readerViewModel.saveLastPage(activePageIndex + 1)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            readerViewModel.saveLastPage(activePageIndex + 1)
        }
    }

    // Jump helper
    val jumpToPage: (Int) -> Unit = { targetPage ->
        val safePage = targetPage.coerceIn(0, (images.size - 1).coerceAtLeast(0))
        coroutineScope.launch {
            if (readerMode == 0) {
                listState.scrollToItem(safePage)
            } else {
                pagerState.scrollToPage(safePage)
            }
            readerViewModel.saveLastPage(safePage + 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
    ) {
        Crossfade(
            targetState = isLoading,
            animationSpec = tween(220),
            label = "reader_loading_crossfade"
        ) { loading ->
            if (loading) {
                // ── Sleek, Lag-Free Minimalist Loading Canvas ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(bgModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = mangaTitle?.takeIf { it.isNotBlank() } ?: "Opening Reader",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        chapter?.let { ch ->
                            Text(
                                text = ch.title,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        LinearProgressIndicator(
                            modifier = Modifier
                                .width(130.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = PrimaryPurple,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                }
            } else if (images.isEmpty()) {
                // ── Empty Chapter State ──
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No pages found in chapter",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp
                        )
                        OutlinedButton(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            } else {
                // ── Active Reader Content ──
                if (readerMode == 0) {
                    // ═════════════════════════════════════════════════════════════════════════
                    // MODE 0: SINGLE LONG STRIP (Continuous Webtoon Scroll)
                    // ═════════════════════════════════════════════════════════════════════════
                    Box(modifier = Modifier.fillMaxSize()) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val containerWidth = maxWidth
                            val targetItemWidth = containerWidth * cropZoom.coerceAtLeast(1.0f)

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(images, key = { index, path -> "$path-$index" }) { index, filePath ->
                                    val data = remember(filePath) {
                                        if (filePath.startsWith("content://")) Uri.parse(filePath) else java.io.File(filePath)
                                    }

                                    // Zoom/margin crop container: crops white borders when targetItemWidth > containerWidth
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clipToBounds(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(data)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.requiredWidth(targetItemWidth)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ═════════════════════════════════════════════════════════════════════════
                    // MODE 1: PAGE BY PAGE (Single Comic Page with Left/Right Taps)
                    // ═════════════════════════════════════════════════════════════════════════
                    val isRtl = (readerDirection == 1)

                    fun goToNext() {
                        if (pagerState.currentPage < images.size - 1) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else if (nextChapterId != null) {
                            onNavigateToChapter(nextChapterId!!)
                        }
                    }

                    fun goToPrev() {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        } else if (prevChapterId != null) {
                            onNavigateToChapter(prevChapterId!!)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isRtl, pagerState.currentPage, images.size) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val w = size.width
                                        val leftThreshold = w * 0.28f
                                        val rightThreshold = w * 0.72f

                                        when {
                                            offset.x < leftThreshold -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (isRtl) goToNext() else goToPrev()
                                            }
                                            offset.x > rightThreshold -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (isRtl) goToPrev() else goToNext()
                                            }
                                            else -> {
                                                // Center Tap toggles controls
                                                showFloatingControls = !showFloatingControls
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = isRtl,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true
                        ) { pageIndex ->
                            val filePath = images.getOrNull(pageIndex)
                            if (filePath != null) {
                                val data = remember(filePath) {
                                    if (filePath.startsWith("content://")) Uri.parse(filePath) else java.io.File(filePath)
                                }

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clipToBounds(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val itemWidth = maxWidth * cropZoom.coerceAtLeast(1.0f)
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(data)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Page ${pageIndex + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .requiredWidth(itemWidth)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                }

                // ═════════════════════════════════════════════════════════════════════════
                // SLEEK FLOATING CORNER CONTROLS (Non-intrusive, no accidental full taps)
                // ═════════════════════════════════════════════════════════════════════════
                AnimatedVisibility(
                    visible = showFloatingControls,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── Top Bar Container ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ── Top-Left: Sleek Back Button ──
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF14131C).copy(alpha = 0.82f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                modifier = Modifier
                                    .size(42.dp)
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .clickable { onNavigateBack() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // ── Top-Right: Sleek Settings Pill (Page counter + Settings Icon) ──
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFF14131C).copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                modifier = Modifier
                                    .height(42.dp)
                                    .shadow(8.dp, RoundedCornerShape(24.dp))
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showSettingsSheet = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${activePageIndex + 1}/${images.size}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(Color.White.copy(alpha = 0.35f), CircleShape)
                                    )

                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = "Reader Settings",
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // ── Bottom Floating Chapter Dock ──
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
                                .shadow(12.dp, RoundedCornerShape(28.dp))
                                .background(Color(0xFF14131C).copy(alpha = 0.88f), RoundedCornerShape(28.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(28.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Prev Chapter
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    prevChapterId?.let { onNavigateToChapter(it) }
                                },
                                enabled = prevChapterId != null,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Chapter",
                                    tint = if (prevChapterId != null) PrimaryPurple else Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Chapter Title Selector Chip
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showChapterList = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = chapter?.title ?: "Select Chapter",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Next Chapter
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    nextChapterId?.let { onNavigateToChapter(it) }
                                },
                                enabled = nextChapterId != null,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Chapter",
                                    tint = if (nextChapterId != null) PrimaryPurple else Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Scroll to Top (Strip mode only)
                            if (readerMode == 0) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            coroutineScope.launch { listState.animateScrollToItem(0) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Scroll to Top",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════════════
        // SLEEK READER SETTINGS BOTTOM SHEET
        // ═════════════════════════════════════════════════════════════════════════
        ToonReaderSettingsSheet(
            show = showSettingsSheet,
            onDismiss = { showSettingsSheet = false },
            readerMode = readerMode,
            onReaderModeChange = { newMode ->
                coroutineScope.launch { globalSettings.setReaderMode(newMode) }
            },
            readerDirection = readerDirection,
            onReaderDirectionChange = { newDirection ->
                coroutineScope.launch { globalSettings.setReaderDirection(newDirection) }
            },
            cropZoom = cropZoom,
            onCropZoomChange = { newCrop ->
                coroutineScope.launch { globalSettings.setReaderCropZoom(newCrop) }
            },
            readerBgColor = readerBgColor,
            onReaderBgColorChange = { newBg ->
                coroutineScope.launch { globalSettings.setReaderBgColor(newBg) }
            },
            keepScreenOn = keepScreenOn,
            onKeepScreenOnChange = { newKeep ->
                coroutineScope.launch { globalSettings.setReaderKeepScreenOn(newKeep) }
            },
            brightnessOverride = brightnessOverride,
            onBrightnessOverrideChange = { brightnessOverride = it },
            currentPage = activePageIndex,
            totalPages = images.size,
            onJumpToPage = jumpToPage,
            mangaTitle = mangaTitle,
            chapterTitle = chapter?.title,
            glowColor = PrimaryPurple
        )

        // ═════════════════════════════════════════════════════════════════════════
        // CHAPTER PICKER MODAL BOTTOM SHEET
        // ═════════════════════════════════════════════════════════════════════════
        if (showChapterList) {
            val chapterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            val currentChapterIndex = allChapters.indexOfFirst { it.id == chapterId }.coerceAtLeast(0)
            val chapterListState = rememberLazyListState(initialFirstVisibleItemIndex = currentChapterIndex)

            ModalBottomSheet(
                onDismissRequest = { showChapterList = false },
                sheetState = chapterSheetState,
                containerColor = Color(0xFF13121B).copy(alpha = 0.95f),
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(40.dp, 4.dp)
                            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 28.dp, start = 20.dp, end = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chapters",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${allChapters.size} Total",
                            color = PrimaryPurple,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val itemHeight = 56.dp
                    val visibleItems = 4
                    val pickerHeight = itemHeight * visibleItems

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pickerHeight)
                            .padding(horizontal = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.3f to Color.Black,
                                            0.7f to Color.Black,
                                            1f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        ) {
                            LazyColumn(
                                state = chapterListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 84.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(allChapters, key = { _, item -> item.id }) { _, chItem ->
                                    val isCurrent = chItem.id == chapterId

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(itemHeight)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                showChapterList = false
                                                if (!isCurrent) onNavigateToChapter(chItem.id)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = chItem.title,
                                            color = if (isCurrent) PrimaryPurple else Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Center Selection Accent Bar
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = (-80).dp)
                                .width(24.dp)
                                .height(3.dp)
                                .background(PrimaryPurple, RoundedCornerShape(50))
                        )
                    }
                }
            }
        }
    }
}
