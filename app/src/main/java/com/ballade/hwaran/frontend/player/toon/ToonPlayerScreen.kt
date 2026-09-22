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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.viewmodels.ReaderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    chapterId: Long,
    externalUri: String? = null,
    readerViewModel: ReaderViewModel = viewModel(),
    musicViewModel: MusicViewModel? = null,
    onNavigateBack: () -> Unit,
    onNavigateToChapter: (Long) -> Unit
) {
    ToonPlayerScreen(chapterId, externalUri, readerViewModel, musicViewModel, onNavigateBack, onNavigateToChapter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToonPlayerScreen(
    chapterId: Long,
    externalUri: String? = null,
    readerViewModel: ReaderViewModel = viewModel(),
    musicViewModel: MusicViewModel? = null,
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
    var activeSettingTab by remember { mutableStateOf<ReaderSettingTab?>(null) }
    var showChapterList by remember { mutableStateOf(false) }

    // Screen is 100% clean by default; controls appear only when requested
    var showControls by remember { mutableStateOf(false) }

    val images by readerViewModel.images.collectAsState()
    val isLoading by readerViewModel.isLoading.collectAsState()
    val chapter by readerViewModel.chapter.collectAsState()
    val mangaTitle by readerViewModel.mangaTitle.collectAsState()
    val startPage by readerViewModel.startPage.collectAsState()
    val allChapters by readerViewModel.allChapters.collectAsState()
    val nextChapterId by readerViewModel.nextChapterId.collectAsState()
    val prevChapterId by readerViewModel.prevChapterId.collectAsState()

    val PrimaryPurple = MaterialTheme.colorScheme.primary
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val BgSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val OverlayBg = MaterialTheme.colorScheme.background.copy(alpha = 0.90f)
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
        if (activeSettingTab != null) {
            activeSettingTab = null
        } else if (showChapterList) {
            showChapterList = false
        } else if (showControls) {
            showControls = false
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

    // Zoom state for Mode 0 (Webtoon continuous strip)
    val mode0Scale = remember { Animatable(1f) }
    val mode0PanX = remember { Animatable(0f) }
    val mode0PanY = remember { Animatable(0f) }

    // Zoom state for Mode 1 (Page-by-page)
    val mode1Scale = remember { Animatable(1f) }
    val mode1PanX = remember { Animatable(0f) }
    val mode1PanY = remember { Animatable(0f) }

    LaunchedEffect(pagerState.currentPage) {
        mode1Scale.snapTo(1f)
        mode1PanX.snapTo(0f)
        mode1PanY.snapTo(0f)
    }

    // Smooth Preload next images only when scroll is idle to avoid frame drops during rapid scrolling
    LaunchedEffect(listState.isScrollInProgress, activePageIndex) {
        if (!listState.isScrollInProgress && images.isNotEmpty()) {
            delay(150)
            val current = activePageIndex
            val end = (current + 5).coerceAtMost(images.size - 1)
            val imageLoader = context.imageLoader
            for (i in current..end) {
                val filePath = images[i]
                val data = if (filePath.startsWith("content://")) Uri.parse(filePath) else java.io.File(filePath)
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .crossfade(false)
                    .build()
                imageLoader.enqueue(request)
            }
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
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .readerZoomGestures(
                                coroutineScope = coroutineScope,
                                scale = mode0Scale,
                                panX = mode0PanX,
                                panY = mode0PanY,
                                onSingleTap = { offset, width ->
                                    if (activeSettingTab != null) {
                                        activeSettingTab = null
                                    } else if (showControls) {
                                        showControls = false
                                    } else if (mode0Scale.value > 1.05f) {
                                        showControls = !showControls
                                    } else {
                                        // Trigger UI on right side (right ~22% zone, matching one-handed thumb tap)
                                        val rightThreshold = width * 0.78f
                                        if (offset.x >= rightThreshold) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showControls = true
                                        }
                                    }
                                }
                            )
                    ) {
                        val effectiveWidth = if (maxWidth > 600.dp) 680.dp else maxWidth
                        val containerWidth = effectiveWidth
                        val targetItemWidth = containerWidth * cropZoom.coerceIn(0.4f, 3.0f)

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = mode0Scale.value
                                    scaleY = mode0Scale.value
                                    translationX = mode0PanX.value
                                    translationY = mode0PanY.value
                                },
                            userScrollEnabled = mode0Scale.value <= 1.05f,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(images, key = { it }) { filePath ->
                                val data = remember(filePath) {
                                    if (filePath.startsWith("content://")) Uri.parse(filePath) else java.io.File(filePath)
                                }

                                // Margin crop container: clips white gutters when targetItemWidth > containerWidth
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clipToBounds(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(data)
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .requiredWidth(targetItemWidth)
                                            .defaultMinSize(minHeight = 200.dp)
                                    )
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
                            .readerZoomGestures(
                                coroutineScope = coroutineScope,
                                scale = mode1Scale,
                                panX = mode1PanX,
                                panY = mode1PanY,
                                onSingleTap = { offset, width ->
                                    if (activeSettingTab != null) {
                                        activeSettingTab = null
                                    } else if (mode1Scale.value > 1.05f) {
                                        showControls = !showControls
                                    } else {
                                        val leftThreshold = width * 0.28f
                                        val rightThreshold = width * 0.72f

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
                                                showControls = !showControls
                                            }
                                        }
                                    }
                                }
                            )
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = isRtl,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = mode1Scale.value
                                    scaleY = mode1Scale.value
                                    translationX = mode1PanX.value
                                    translationY = mode1PanY.value
                                },
                            userScrollEnabled = mode1Scale.value <= 1.05f
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
                                    val maxPageWidth = if (maxWidth > 600.dp) 800.dp else maxWidth
                                    val itemWidth = maxPageWidth * cropZoom.coerceIn(0.4f, 3.0f)
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(data)
                                            .crossfade(false)
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
                // OVERLAY CONTROLS (Hidden during chapter picker to eliminate interference)
                // ═════════════════════════════════════════════════════════════════════════
                AnimatedVisibility(
                    visible = showControls && !showChapterList,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── Top Bar Container (Safe from Camera Notch & Cutout) ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                    )
                                )
                                .statusBarsPadding()
                                .displayCutoutPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Button (Safely below cutout)
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(OverlayBg, CircleShape)
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Top Right: Live Page Counter Pill
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = OverlayBg,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Text(
                                        text = "p. ${activePageIndex + 1}/${images.size}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // ── REVIVED CLASSIC BOTTOM NAVIGATION PILL & TOP BUTTON (Clean White, No Purple Blooming) ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 36.dp)
                        ) {
                            // Center Classic Navigation Pill
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(OverlayBg, RoundedCornerShape(50))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        prevChapterId?.let { onNavigateToChapter(it) }
                                    },
                                    enabled = prevChapterId != null,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Prev Chapter",
                                        tint = if (prevChapterId != null) Color.White else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(24.dp))
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable {
                                            activeSettingTab = null
                                            showChapterList = true
                                        }
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = chapter?.title ?: "Select Chapter",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        nextChapterId?.let { onNavigateToChapter(it) }
                                    },
                                    enabled = nextChapterId != null,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Next Chapter",
                                        tint = if (nextChapterId != null) Color.White else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Classic Separate Circular "^" Scroll to Top Button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp)
                                    .size(44.dp)
                                    .background(OverlayBg, CircleShape)
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        coroutineScope.launch {
                                            if (readerMode == 0) {
                                                // Jump directly to Page 1 with fast snap loop
                                                var attempts = 0
                                                while (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                                                    listState.scrollToItem(0)
                                                    delay(30)
                                                    attempts++
                                                    if (attempts > 15) break
                                                }
                                            } else {
                                                pagerState.scrollToPage(0)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Scroll to Top",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // ═════════════════════════════════════════════════════════════════
                        // VERTICAL SETTINGS PILL & LIVE TRANSPARENT POPUP
                        // ═════════════════════════════════════════════════════════════════
                        ToonReaderSettingsPill(
                            activeTab = activeSettingTab,
                            onTabSelected = { activeSettingTab = it },
                            readerMode = readerMode,
                            onReaderModeChange = { newMode ->
                                coroutineScope.launch { globalSettings.setReaderMode(newMode) }
                            },
                            readerDirection = readerDirection,
                            onReaderDirectionChange = { newDir ->
                                coroutineScope.launch { globalSettings.setReaderDirection(newDir) }
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
                            glowColor = PrimaryPurple,
                            musicViewModel = musicViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

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
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                contentColor = Color.White,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(40.dp, 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
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
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    val itemHeight = 56.dp
                    val visibleItems = 4
                    val pickerHeight = itemHeight * visibleItems

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pickerHeight)
                            .padding(horizontal = 16.dp)
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
                                items(allChapters, key = { it.id }) { chItem ->
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
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
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
                                .offset(x = (-70).dp)
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

private fun Modifier.readerZoomGestures(
    coroutineScope: CoroutineScope,
    scale: Animatable<Float, AnimationVector1D>,
    panX: Animatable<Float, AnimationVector1D>,
    panY: Animatable<Float, AnimationVector1D>,
    onSingleTap: (Offset, Float) -> Unit
): Modifier = this
    .clipToBounds()
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var isMultiTouch = false
            var isPanningZoomed = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val activePointers = event.changes.filter { it.pressed }

                if (activePointers.isEmpty()) {
                    break
                }

                if (activePointers.size >= 2) {
                    // Multi-touch pinch zoom!
                    // Immediately consume all touches on Initial pass so child LazyColumn/Pager NEVER scrolls
                    event.changes.forEach { it.consume() }
                    isMultiTouch = true

                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = true)

                    val currentScale = scale.value
                    val newScale = (currentScale * zoomChange).coerceIn(0.75f, 6.0f)

                    val maxPanX = (size.width * (newScale - 1f).coerceAtLeast(0f)) / 2f
                    val maxPanY = (size.height * (newScale - 1f).coerceAtLeast(0f)) / 2f

                    // Focal zoom adjustment: keep point under centroid pinned
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val focalPanAdjustment = if (centroid != Offset.Unspecified) {
                        (centroid - center) * (1f - zoomChange)
                    } else Offset.Zero

                    val newPanX = (panX.value + panChange.x + focalPanAdjustment.x)
                        .coerceIn(-maxPanX * 1.5f, maxPanX * 1.5f)
                    val newPanY = (panY.value + panChange.y + focalPanAdjustment.y)
                        .coerceIn(-maxPanY * 1.5f, maxPanY * 1.5f)

                    coroutineScope.launch {
                        scale.snapTo(newScale)
                        panX.snapTo(newPanX)
                        panY.snapTo(newPanY)
                    }
                } else if (activePointers.size == 1 && scale.value > 1.05f) {
                    // Single finger panning when zoomed in
                    val change = activePointers.first()
                    val dragAmount = change.position - change.previousPosition
                    if (dragAmount.getDistance() > 0.5f) {
                        change.consume()
                        isPanningZoomed = true

                        val currentScale = scale.value
                        val maxPanX = (size.width * (currentScale - 1f).coerceAtLeast(0f)) / 2f
                        val maxPanY = (size.height * (currentScale - 1f).coerceAtLeast(0f)) / 2f

                        val newPanX = (panX.value + dragAmount.x).coerceIn(-maxPanX * 1.25f, maxPanX * 1.25f)
                        val newPanY = (panY.value + dragAmount.y).coerceIn(-maxPanY * 1.25f, maxPanY * 1.25f)

                        coroutineScope.launch {
                            panX.snapTo(newPanX)
                            panY.snapTo(newPanY)
                        }
                    }
                }
            }

            // Fingers lifted: animate back to valid bounds smoothly
            if (isMultiTouch || isPanningZoomed) {
                val targetScale = scale.value.coerceIn(1f, 4f)
                val maxPanX = (size.width * (targetScale - 1f).coerceAtLeast(0f)) / 2f
                val maxPanY = (size.height * (targetScale - 1f).coerceAtLeast(0f)) / 2f
                val targetPanX = if (targetScale <= 1.02f) 0f else panX.value.coerceIn(-maxPanX, maxPanX)
                val targetPanY = if (targetScale <= 1.02f) 0f else panY.value.coerceIn(-maxPanY, maxPanY)

                coroutineScope.launch {
                    launch {
                        scale.animateTo(
                            targetScale,
                            spring(dampingRatio = 0.82f, stiffness = 400f)
                        )
                    }
                    launch {
                        panX.animateTo(
                            targetPanX,
                            spring(dampingRatio = 0.82f, stiffness = 400f)
                        )
                    }
                    launch {
                        panY.animateTo(
                            targetPanY,
                            spring(dampingRatio = 0.82f, stiffness = 400f)
                        )
                    }
                }
            }
        }
    }
    .pointerInput(onSingleTap) {
        detectTapGestures(
            onDoubleTap = { tapOffset ->
                coroutineScope.launch {
                    if (scale.value > 1.05f) {
                        // Smoothly animate back to 1.0x
                        launch {
                            scale.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                        }
                        launch {
                            panX.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                        launch {
                            panY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    } else {
                        // Smoothly animate in to 2.5x centered on tap
                        val targetScale = 2.5f
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val maxPanX = (size.width * (targetScale - 1f)) / 2f
                        val maxPanY = (size.height * (targetScale - 1f)) / 2f

                        val targetPanX = ((centerX - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxPanX, maxPanX)
                        val targetPanY = ((centerY - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxPanY, maxPanY)

                        launch {
                            scale.animateTo(targetScale, tween(300, easing = FastOutSlowInEasing))
                        }
                        launch {
                            panX.animateTo(targetPanX, tween(300, easing = FastOutSlowInEasing))
                        }
                        launch {
                            panY.animateTo(targetPanY, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                }
            },
            onTap = { offset ->
                onSingleTap(offset, size.width.toFloat())
            }
        )
    }
