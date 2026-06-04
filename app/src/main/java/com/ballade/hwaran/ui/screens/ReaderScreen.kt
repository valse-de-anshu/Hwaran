package com.ballade.hwaran.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.ballade.hwaran.ui.viewmodels.ReaderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ballade.hwaran.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.net.Uri
import coil.size.Size

// Exact App Colors are now derived from MaterialTheme locally

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    chapterId: Long,
    externalUri: String? = null,
    readerViewModel: ReaderViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChapter: (Long) -> Unit
) {
    // --- ZOOM & PAN STATE ---
    var liveScale by remember { mutableFloatStateOf(1f) }
    var liveOffsetX by remember { mutableFloatStateOf(0f) }
    var liveOffsetY by remember { mutableFloatStateOf(0f) }
    
    val scaleAnim = remember { androidx.compose.animation.core.Animatable(1f) }
    val offsetXAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val offsetYAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    
    var isAnimating by remember { mutableStateOf(false) }
    var isPinching by remember { mutableStateOf(false) }
    
    val zoomTweenSpec = tween<Float>(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val resetTweenSpec = tween<Float>(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    
    fun centroid(p1: Offset, p2: Offset): Offset = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    
    fun clampOffset(scale: Float, rawX: Float, rawY: Float, viewWidth: Int, viewHeight: Int): Pair<Float, Float> {
        return if (scale <= 1f) {
            0f to 0f
        } else {
            val maxX = (viewWidth * (scale - 1f)) / 2f
            val maxY = (viewHeight * (scale - 1f)) / 2f
            rawX.coerceIn(-maxX, maxX) to rawY.coerceIn(-maxY, maxY)
        }
    }
    
    suspend fun smoothReset() = kotlinx.coroutines.coroutineScope {
        isAnimating = true
        scaleAnim.snapTo(liveScale)
        offsetXAnim.snapTo(liveOffsetX)
        offsetYAnim.snapTo(liveOffsetY)
        val j1 = launch { scaleAnim.animateTo(1f, resetTweenSpec) }
        val j2 = launch { offsetXAnim.animateTo(0f, resetTweenSpec) }
        val j3 = launch { offsetYAnim.animateTo(0f, resetTweenSpec) }
        j1.join(); j2.join(); j3.join()
        liveScale = 1f; liveOffsetX = 0f; liveOffsetY = 0f
        isAnimating = false
    }
    
    suspend fun smoothZoomIn(tapX: Float, tapY: Float, viewW: Int, viewH: Int) = kotlinx.coroutines.coroutineScope {
        val targetScale = 3f
        val rawX = -(tapX - viewW / 2f) * (targetScale - 1f)
        val rawY = -(tapY - viewH / 2f) * (targetScale - 1f)
        val (cx, cy) = clampOffset(targetScale, rawX, rawY, viewW, viewH)
        isAnimating = true
        scaleAnim.snapTo(liveScale)
        offsetXAnim.snapTo(liveOffsetX)
        offsetYAnim.snapTo(liveOffsetY)
        val j1 = launch { scaleAnim.animateTo(targetScale, zoomTweenSpec) }
        val j2 = launch { offsetXAnim.animateTo(cx, zoomTweenSpec) }
        val j3 = launch { offsetYAnim.animateTo(cy, zoomTweenSpec) }
        j1.join(); j2.join(); j3.join()
        liveScale = targetScale; liveOffsetX = cx; liveOffsetY = cy
        isAnimating = false
    }
    
    val PrimaryPurple = MaterialTheme.colorScheme.primary
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val BgSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val OverlayBg = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)

    LaunchedEffect(chapterId, externalUri) {
        if (externalUri != null) {
            readerViewModel.loadExternalImage(externalUri)
        } else {
            readerViewModel.loadChapter(chapterId)
        }
        liveScale = 1f; liveOffsetX = 0f; liveOffsetY = 0f
        isAnimating = false
        isPinching = false
    }

    val images by readerViewModel.images.collectAsState()
    val isLoading by readerViewModel.isLoading.collectAsState()
    
    val allChapters by readerViewModel.allChapters.collectAsState()
    val nextChapterId by readerViewModel.nextChapterId.collectAsState()
    val prevChapterId by readerViewModel.prevChapterId.collectAsState()
    
    val chapter by readerViewModel.chapter.collectAsState()
    val mangaTitle by readerViewModel.mangaTitle.collectAsState()
    val startPage by readerViewModel.startPage.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(startPage, images) {
        if (images.isNotEmpty() && startPage > 0 && startPage < images.size) {
            val imageLoader = context.imageLoader
            for (i in 0 until startPage) {
                val filePath = images[i]
                val data = if (filePath.startsWith("content://")) Uri.parse(filePath) else java.io.File(filePath)
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .allowHardware(false)
                    .build()
                imageLoader.enqueue(request)
            }
            var waitAttempts = 0
            while (listState.layoutInfo.totalItemsCount == 0 && waitAttempts < 40) {
                delay(50)
                waitAttempts++
            }
            delay(300)
            listState.scrollToItem(startPage)
            var scrollAttempts = 0
            while (listState.firstVisibleItemIndex != startPage && scrollAttempts < 15) {
                delay(150)
                listState.scrollToItem(startPage)
                scrollAttempts++
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, listState, chapter) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                val pageToSave = if (listState.firstVisibleItemIndex >= 0) listState.firstVisibleItemIndex + 1 else 1
                readerViewModel.saveLastPage(pageToSave)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val pageToSave = if (listState.firstVisibleItemIndex >= 0) listState.firstVisibleItemIndex + 1 else 1
            readerViewModel.saveLastPage(pageToSave)
        }
    }

    var showOverlay by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }

    val currentVisibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    LaunchedEffect(currentVisibleIndex, images) {
        if (images.isEmpty()) return@LaunchedEffect

        val startPreload = (currentVisibleIndex - 3).coerceAtLeast(0)
        val endPreload = (currentVisibleIndex + 5).coerceAtMost(images.size - 1)
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

    val appGradient = com.ballade.hwaran.ui.theme.LocalAppGradient.current
    val bgModifier = if (appGradient != null) Modifier.background(appGradient) else Modifier.background(MaterialTheme.colorScheme.background)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
    ) {
        Crossfade(targetState = isLoading, animationSpec = tween(1200), label = "loading_crossfade") { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize().then(bgModifier),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryPurple)
                }
            } else {
                // ── Outer interaction box: handles ALL gestures via one unified coroutine ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val viewW = size.width
                            val viewH = size.height
                            val doubleTapTimeoutMs = 300L
                            val tapSlop = 18f
                            val longPressTimeout = 350L

                            var lastTapUpTime = 0L
                            var lastTapPosition = Offset.Zero
                            var doubleTapPending = false
                            var pendingSingleTapJob: Job? = null

                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                val downTime = System.currentTimeMillis()
                                val downPos  = firstDown.position

                                var prevDist = 0f
                                var prevCentroid = Offset.Zero
                                
                                var movedDistance = 0f
                                var currentPos = downPos

                                do {
                                    val event = awaitPointerEvent()
                                    val pointers = event.changes.filter { it.pressed }

                                    when {
                                        // ── TWO-FINGER PINCH ─────────────────────────────────────
                                        pointers.size >= 2 -> {
                                            if (!isPinching) {
                                                isPinching  = true
                                                pendingSingleTapJob?.cancel()
                                                doubleTapPending = false
                                                prevDist     = pointers[0].position.distance(pointers[1].position)
                                                prevCentroid = centroid(pointers[0].position, pointers[1].position)
                                            }

                                            event.changes.forEach { it.consume() }

                                            val p0 = pointers[0].position
                                            val p1 = pointers[1].position
                                            val newDist     = p0.distance(p1)
                                            val newCentroid = centroid(p0, p1)

                                            if (prevDist > 0f && newDist > 0f) {
                                                val scaleDelta  = newDist / prevDist
                                                val newScale    = (liveScale * scaleDelta).coerceIn(0.2f, 5f)
                                                val panDelta    = newCentroid - prevCentroid
                                                val scaleChange = newScale / liveScale
                                                val rawX = (liveOffsetX - newCentroid.x) * scaleChange + newCentroid.x + panDelta.x
                                                val rawY = (liveOffsetY - newCentroid.y) * scaleChange + newCentroid.y + panDelta.y
                                                val (cx, cy)    = clampOffset(newScale, rawX, rawY, viewW, viewH)

                                                liveScale   = newScale
                                                liveOffsetX = cx
                                                liveOffsetY = cy
                                            }

                                            prevDist     = newDist
                                            prevCentroid = newCentroid
                                        }

                                        // ── SINGLE FINGER ────────────────────────────────────────
                                        pointers.size == 1 -> {
                                            val ptr = pointers[0]
                                            if (ptr.positionChanged()) {
                                                val delta = ptr.position - currentPos
                                                movedDistance += abs(delta.x) + abs(delta.y)
                                                currentPos = ptr.position

                                                if (liveScale > 1.01f && !isPinching) {
                                                    ptr.consume()
                                                    val rawX = liveOffsetX + delta.x
                                                    val rawY = liveOffsetY + delta.y
                                                    val (cx, cy) = clampOffset(liveScale, rawX, rawY, viewW, viewH)
                                                    
                                                    val consumedY = cy - liveOffsetY
                                                    val leftoverY = delta.y - consumedY
                                                    
                                                    liveOffsetX = cx
                                                    liveOffsetY = cy
                                                    
                                                    if (kotlin.math.abs(leftoverY) > 0f) {
                                                        listState.dispatchRawDelta(-leftoverY / liveScale)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (event.changes.any { it.isConsumed }) {
                                        movedDistance = Float.MAX_VALUE
                                    }

                                } while (event.changes.any { it.pressed })

                                // ── Gesture ended ────────────────────────────────────────────
                                if (isPinching) {
                                    isPinching = false
                                    if (liveScale in 0.95f..1.05f) {
                                        coroutineScope.launch { smoothReset() }
                                    }
                                    return@awaitEachGesture
                                }

                                // ── Tap classification ───────────────────────────────────────
                                val upTime       = System.currentTimeMillis()
                                val pressDuration = upTime - downTime

                                if (movedDistance < tapSlop && pressDuration < longPressTimeout) {
                                    val timeSinceLast = upTime - lastTapUpTime
                                    val distFromLast  = downPos.distance(lastTapPosition)

                                    if (doubleTapPending
                                        && timeSinceLast < doubleTapTimeoutMs
                                        && distFromLast  < 80f
                                    ) {
                                        // ── DOUBLE TAP ───────────────────────────────────────
                                        pendingSingleTapJob?.cancel()
                                        doubleTapPending = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                        if (liveScale > 1.05f || liveScale < 0.95f) {
                                            coroutineScope.launch { smoothReset() }
                                        } else {
                                            coroutineScope.launch { smoothZoomIn(downPos.x, downPos.y, viewW, viewH) }
                                        }
                                    } else {
                                        // ── SINGLE TAP ───────────────────────────────────────
                                        doubleTapPending = true
                                        lastTapUpTime    = upTime
                                        lastTapPosition  = downPos
                                        
                                        pendingSingleTapJob = coroutineScope.launch {
                                            kotlinx.coroutines.delay(doubleTapTimeoutMs)
                                            doubleTapPending = false
                                            showOverlay = !showOverlay
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // ── Zoom/pan transform layer ──────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s  = if (isAnimating) scaleAnim.value   else liveScale
                                val tx = if (isAnimating) offsetXAnim.value  else liveOffsetX
                                val ty = if (isAnimating) offsetYAnim.value  else liveOffsetY
                                scaleX       = s
                                scaleY       = s
                                translationX = tx
                                translationY = ty
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val currentScale = if (isAnimating) scaleAnim.value else liveScale
                        val boundedScale = currentScale.coerceAtMost(1f).coerceAtLeast(0.1f)
                        
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val baseWidth = maxWidth
                            val baseHeight = maxHeight
                            
                            LazyColumn(
                                state = listState,
                                userScrollEnabled = !isPinching && liveScale <= 1.01f,
                                verticalArrangement = if (images.size <= 1) Arrangement.Center else Arrangement.Top,
                                modifier = Modifier.requiredSize(
                                    width = baseWidth,
                                    height = baseHeight / boundedScale
                                )
                            ) {
                                items(images, key = { it }) { filePath ->
                                    val data = remember(filePath) { if (filePath.startsWith("content://")) Uri.parse(filePath) else java.io.File(filePath) }
            
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(data)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- OVERLAY ---
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(start = 8.dp, end = 8.dp, top = 56.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(OverlayBg, RoundedCornerShape(50))
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
                                tint = if (prevChapterId != null) PrimaryPurple else TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(BgSurfaceVariant, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { showChapterList = true }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = chapter?.title ?: "Select Chapter",
                                color = PrimaryPurple,
                                style = MaterialTheme.typography.bodyMedium,
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
                                tint = if (nextChapterId != null) PrimaryPurple else TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                            .size(44.dp)
                            .background(OverlayBg, CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                liveScale = 1f; liveOffsetX = 0f; liveOffsetY = 0f
                                isAnimating = false
                                isPinching = false
                                showOverlay = false
                                coroutineScope.launch {
                                    var attempts = 0
                                    while (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                                        listState.scrollToItem(0)
                                        delay(80)
                                        attempts++
                                        if (attempts > 15) break
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Top", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        if (showChapterList) {
            val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)
            val currentChapterIndex = allChapters.indexOfFirst { it.id == chapterId }.coerceAtLeast(0)
            val chapterListState = rememberLazyListState(initialFirstVisibleItemIndex = currentChapterIndex)

            ModalBottomSheet(
                onDismissRequest = { showChapterList = false },
                sheetState = sheetState,
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, start = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chapters",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
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
                                items(allChapters, key = { it.id }) { chapter ->
                                    val isCurrent = chapter.id == chapterId
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(itemHeight)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                showChapterList = false
                                                if (!isCurrent) onNavigateToChapter(chapter.id)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = chapter.title,
                                            color = if (isCurrent) PrimaryPurple else Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        
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

private fun Offset.distance(other: Offset): Float = (this - other).getDistance()
