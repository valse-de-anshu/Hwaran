package com.ballade.hwaran.frontend.player.book

import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ballade.hwaran.core.database.entity.MangaEntity
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.Job
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.core.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// PdfManager
// ─────────────────────────────────────────────────────────────────────────────

class PdfManager(val renderer: PdfRenderer, val fd: ParcelFileDescriptor) {
    var isClosed = false
    fun close() {
        synchronized(this) {
            if (!isClosed) {
                isClosed = true
                try { renderer.close() } catch (e: Exception) {}
                try { fd.close() } catch (e: Exception) {}
            }
        }
    }
}

suspend fun resolvePdfUri(context: android.content.Context, parentUriString: String): Uri? = withContext(Dispatchers.IO) {
    if (parentUriString.isEmpty()) return@withContext null

    if (parentUriString.startsWith("content://")) {
        val uri = Uri.parse(parentUriString)
        try {
            val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
            if (doc != null && doc.exists() && !doc.isDirectory) {
                return@withContext uri
            }

            val treeDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            if (treeDoc != null && treeDoc.isDirectory) {
                val pdfDoc = treeDoc.listFiles().find { f -> f.name?.endsWith(".pdf", true) == true }
                if (pdfDoc != null) return@withContext pdfDoc.uri
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext uri
    } else {
        val file = File(parentUriString)
        if (file.exists()) {
            if (file.isDirectory) {
                val pdfFile = file.listFiles()?.find { f -> f.name.endsWith(".pdf", true) }
                if (pdfFile != null) return@withContext Uri.fromFile(pdfFile)
            } else {
                return@withContext Uri.fromFile(file)
            }
        }
    }
    return@withContext null
}

// ─────────────────────────────────────────────────────────────────────────────
// Gesture helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun Offset.distance(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}

private fun centroid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

// ─────────────────────────────────────────────────────────────────────────────
// Eye Care and Bottom Panel Models
// ─────────────────────────────────────────────────────────────────────────────

enum class EyeCareMode(val label: String) {
    OFF("Original"),
    SEPIA("Warm Sepia"),
    MINT("Paper Green"),
    NIGHT("OLED Dark")
}

enum class PdfBottomPanel {
    HIGHLIGHTER,
    EYE_CARE,
    PAGE_NAV
}

data class HighlightColorOption(
    val name: String,
    val colorInt: Int,
    val displayColor: Color
)

val HighlighterColors = listOf(
    HighlightColorOption("Neon Yellow", 0xFFFFEB3B.toInt(), Color(0xFFFFEB3B)),
    HighlightColorOption("Mint Green", 0xFFA7F3D0.toInt(), Color(0xFFA7F3D0)),
    HighlightColorOption("Sakura Pink", 0xFFF472B6.toInt(), Color(0xFFF472B6)),
    HighlightColorOption("Sky Cyan", 0xFF60A5FA.toInt(), Color(0xFF60A5FA)),
    HighlightColorOption("Lavender", 0xFFC084FC.toInt(), Color(0xFFC084FC)),
    HighlightColorOption("Warm Amber", 0xFFFFB74D.toInt(), Color(0xFFFFB74D))
)

// ─────────────────────────────────────────────────────────────────────────────
// BookPlayerScreen (formerly PdfReaderScreen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PdfReaderScreen(
    mangaId: Long,
    externalUri: String? = null,
    pdfViewModel: com.ballade.hwaran.ui.viewmodels.PdfViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit
) {
    BookPlayerScreen(mangaId, externalUri, pdfViewModel, onNavigateBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPlayerScreen(
    mangaId: Long,
    externalUri: String? = null,
    pdfViewModel: com.ballade.hwaran.ui.viewmodels.PdfViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val database = remember { AppDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfManager by remember { mutableStateOf<PdfManager?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var isMarkerMode by remember { mutableStateOf(false) }
    var activeColor by remember { mutableIntStateOf(0xFFFFEB3B.toInt()) }
    val markers by pdfViewModel.getMarkers(mangaId).collectAsState(initial = emptyList())
    var eyeCareMode by remember { mutableStateOf(EyeCareMode.OFF) }
    var activeBottomPanel by remember { mutableStateOf<PdfBottomPanel?>(null) }
    var viewWidthPx by remember { mutableIntStateOf(1080) }
    var viewHeightPx by remember { mutableIntStateOf(1920) }

    var manga by remember { mutableStateOf<MangaEntity?>(null) }
    var startPage by remember { mutableIntStateOf(1) }
    val visitedPages = remember { mutableStateListOf<Int>() }

    val currentPage by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                0
            } else {
                val firstItem = visibleItems.first()
                val firstItemScrolledFraction = if (firstItem.size > 0) {
                    (-firstItem.offset.toFloat() / firstItem.size.toFloat()).coerceIn(0f, 1f)
                } else 0f

                if (firstItemScrolledFraction >= 0.8f && visibleItems.size > 1) {
                    visibleItems[1].index
                } else {
                    firstItem.index
                }
            }
        }
    }

    LaunchedEffect(currentPage, pageCount) {
        if (pageCount > 0) {
            val pageNum = currentPage + 1
            if (pageNum in 1..pageCount) {
                if (visitedPages.isEmpty() || visitedPages.last() != pageNum) {
                    visitedPages.add(pageNum)
                }
            }
        }
    }

    LaunchedEffect(pageCount) {
        val currentManga = manga
        if (pageCount > 0 && currentManga != null) {
            val startPageIdx = startPage - 1
            if (startPageIdx > 0 && startPageIdx < pageCount) {
                listState.scrollToItem(startPageIdx)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, listState, manga, pageCount) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                val pageToSave = if (listState.firstVisibleItemIndex >= 0) listState.firstVisibleItemIndex + 1 else 1
                pdfViewModel.saveLastPage(mangaId, pageToSave)
                val currentManga = manga
                if (currentManga != null && pageCount > 0) {
                    val pagesLogged = if (visitedPages.isNotEmpty()) visitedPages.joinToString(",") else "${startPage}-${pageToSave}"
                    com.ballade.hwaran.core.util.HistoryTracker.logEvent(
                        "READ_BOOK",
                        currentManga.title,
                        "mangaId:${currentManga.id}|pages:$pagesLogged|totalPages:$pageCount|fallback:Book: ${currentManga.title}"
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val pageToSave = if (listState.firstVisibleItemIndex >= 0) listState.firstVisibleItemIndex + 1 else 1
            pdfViewModel.saveLastPage(mangaId, pageToSave)
            val currentManga = manga
            if (currentManga != null && pageCount > 0) {
                val pagesLogged = if (visitedPages.isNotEmpty()) visitedPages.joinToString(",") else "${startPage}-${pageToSave}"
                com.ballade.hwaran.core.util.HistoryTracker.logEvent(
                    "READ_BOOK",
                    currentManga.title,
                    "mangaId:${currentManga.id}|pages:$pagesLogged|totalPages:$pageCount|fallback:Book: ${currentManga.title}"
                )
            }
        }
    }

    val handleBack = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onNavigateBack()
    }

    BackHandler(onBack = handleBack)

    var showOverlay by remember { mutableStateOf(false) }

    // ── Zoom / Pan state ─────────────────────────────────────────────────────
    //
    // ─ "Live" vars (plain MutableState): updated every frame during pinch/pan.
    //   These CAN be written from inside AwaitPointerEventScope because they are
    //   not suspend calls. graphicsLayer reads from them while a gesture is active.
    //
    // ─ Animatable: used ONLY for smooth coroutine-driven transitions (double-tap
    //   zoom in/out, pinch snap-back). Driven via coroutineScope.launch { animateTo }.
    //
    var liveScale   by remember { mutableFloatStateOf(1f) }
    var liveOffsetX by remember { mutableFloatStateOf(0f) }
    var liveOffsetY by remember { mutableFloatStateOf(0f) }

    val scaleAnim   = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    // Whether a smooth transition animation is running (drives which source graphicsLayer reads)
    var isAnimating by remember { mutableStateOf(false) }

    // Whether a two-finger pinch is currently active (blocks LazyColumn scroll)
    var isPinching by remember { mutableStateOf(false) }

    // Calm, non-bouncy tween — no spring overshoot at all
    val zoomTweenSpec  = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    val resetTweenSpec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)

    // Helper: clamp pan so content edge doesn't go past screen edge
    fun clampOffset(scale: Float, rawX: Float, rawY: Float, viewWidth: Int, viewHeight: Int): Pair<Float, Float> {
        return if (scale <= 1f) {
            0f to 0f
        } else {
            val maxX = (viewWidth  * (scale - 1f)) / 2f
            val maxY = (viewHeight * (scale - 1f)) / 2f
            rawX.coerceIn(-maxX, maxX) to rawY.coerceIn(-maxY, maxY)
        }
    }

    // Smooth zoom-out to 1× — call only from a coroutine
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

    // Smooth zoom-in to targetScale centred on (tapX, tapY) — call only from a coroutine
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

    LaunchedEffect(mangaId, externalUri) {
        if (externalUri != null) {
            pdfUri = Uri.parse(externalUri)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val m = database.libraryDao().getMangaById(mangaId)
                if (m != null) {
                    manga = m
                    startPage = (m.lastReadPage ?: 1).coerceAtLeast(1)
                    val resolved = resolvePdfUri(context, m.parentUri)
                    if (resolved != null) {
                        pdfUri = resolved
                    } else {
                        error = "Could not find PDF file."
                        isLoading = false
                    }
                } else {
                    error = "Manga not found."
                    isLoading = false
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
                isLoading = false
            }
        }
    }

    LaunchedEffect(pdfUri) {
        if (pdfUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val pfd = if (pdfUri!!.scheme == "content") {
                        context.contentResolver.openFileDescriptor(pdfUri!!, "r")
                    } else {
                        ParcelFileDescriptor.open(File(pdfUri!!.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
                    }

                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        pdfManager = PdfManager(renderer, pfd)
                        pageCount = renderer.pageCount
                        pdfViewModel.incrementOpenCount(mangaId)
                        isLoading = false
                    } else {
                        error = "Could not open file."
                        isLoading = false
                    }
                } catch (e: Throwable) {
                    error = "Error: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfManager?.close()
        }
    }

    val appGradient = com.ballade.hwaran.ui.theme.LocalAppGradient.current
    val bgModifier = if (appGradient != null) Modifier.background(appGradient) else Modifier.background(MaterialTheme.colorScheme.background)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
            .onSizeChanged {
                viewWidthPx = it.width
                viewHeightPx = it.height
            }
    ) {
        Crossfade(
            targetState = (pdfManager == null || isLoading) && error == null,
            animationSpec = tween(220),
            label = "pdf_loading_crossfade"
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
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = manga?.title?.takeIf { it.isNotBlank() } ?: "Opening Document",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "Preparing pages & annotations...",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        LinearProgressIndicator(
                            modifier = Modifier
                                .width(130.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = error ?: "Unknown error",
                        color = Color(0xFFE57373),
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (pdfManager != null && pageCount > 0) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // ── Outer interaction box: handles ALL gestures via one unified coroutine ──
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isMarkerMode, pageCount) {
                                if (isMarkerMode) return@pointerInput

                                val viewW = size.width
                                val viewH = size.height
                                // Double-tap: 300 ms gap, 80 px radius — MUST be outside awaitEachGesture
                                val doubleTapTimeoutMs = 300L
                                val tapSlop = 18f
                                val longPressTimeout = 350L

                                // Persists across gestures so double-tap works between two awaitEachGesture blocks
                                var lastTapUpTime = 0L
                                var lastTapPosition = Offset.Zero
                                var doubleTapPending = false
                                // Job that fires the single-tap action after the double-tap window expires
                                var pendingSingleTapJob: Job? = null

                                awaitEachGesture {
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                    val downTime = System.currentTimeMillis()
                                    val downPos  = firstDown.position

                                    var pinchActive = false
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
                                                if (!pinchActive) {
                                                    pinchActive = true
                                                    isPinching  = true
                                                    // Cancel any pending single-tap action
                                                    pendingSingleTapJob?.cancel()
                                                    doubleTapPending = false

                                                    prevDist     = pointers[0].position.distance(pointers[1].position)
                                                    prevCentroid = centroid(pointers[0].position, pointers[1].position)
                                                } else {
                                                    val currDist     = pointers[0].position.distance(pointers[1].position)
                                                    val currCentroid = centroid(pointers[0].position, pointers[1].position)

                                                    if (prevDist > 10f) {
                                                        val zoomDelta = currDist / prevDist
                                                        val newScale  = (liveScale * zoomDelta).coerceIn(0.5f, 6f)

                                                        val panX = currCentroid.x - prevCentroid.x
                                                        val panY = currCentroid.y - prevCentroid.y

                                                        val (cx, cy) = clampOffset(
                                                            newScale,
                                                            liveOffsetX + panX,
                                                            liveOffsetY + panY,
                                                            viewW, viewH
                                                        )
                                                        liveScale   = newScale
                                                        liveOffsetX = cx
                                                        liveOffsetY = cy
                                                    }
                                                    prevDist     = currDist
                                                    prevCentroid = currCentroid
                                                }
                                                // Consume so LazyColumn does not get pinch moves
                                                pointers.forEach { it.consume() }
                                            }

                                            // ── ONE-FINGER DRAG / SCROLL / TAP ───────────────────────
                                            pointers.size == 1 -> {
                                                val ptr   = pointers[0]
                                                val delta = ptr.position - currentPos

                                                if (ptr.positionChanged()) {
                                                    movedDistance += abs(delta.x) + abs(delta.y)
                                                    currentPos = ptr.position

                                                    // Pan while zoomed — consume so LazyColumn doesn't interfere
                                                    if (liveScale > 1.01f && !pinchActive) {
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

                                        // If LazyColumn consumed the scroll, it's definitely not a tap
                                        if (event.changes.any { it.isConsumed }) {
                                            movedDistance = Float.MAX_VALUE
                                        }

                                    } while (event.changes.any { it.pressed })

                                    // ── Gesture ended ────────────────────────────────────────────
                                    if (pinchActive) {
                                        isPinching = false
                                        // If pinched almost all the way back to 1×, smooth snap cleanly
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
                                            // Cancel the pending single-tap action (overlay toggle)
                                            pendingSingleTapJob?.cancel()
                                            doubleTapPending = false
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                            if (liveScale > 1.05f || liveScale < 0.95f) {
                                                // Zoom OUT — calm, smooth
                                                coroutineScope.launch { smoothReset() }
                                            } else {
                                                // Zoom IN to 3× centred on tap position
                                                coroutineScope.launch { smoothZoomIn(downPos.x, downPos.y, viewW, viewH) }
                                            }
                                        } else {
                                            // ── FIRST TAP (potential first of a double-tap) ───────
                                            // Record position/time but DON'T act yet.
                                            // Schedule the actual single-tap action after the double-tap
                                            // window; cancel it if a second tap arrives first.
                                            lastTapUpTime  = upTime
                                            lastTapPosition = downPos
                                            doubleTapPending = true

                                            pendingSingleTapJob?.cancel()
                                            pendingSingleTapJob = coroutineScope.launch {
                                                kotlinx.coroutines.delay(doubleTapTimeoutMs)
                                                // Only runs if not cancelled by a second tap
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
                                    // Disable scroll only while pinching OR while zoomed in
                                    userScrollEnabled = !isPinching && liveScale <= 1.01f,
                                    verticalArrangement = if (pageCount <= 1) Arrangement.Center else Arrangement.Top,
                                    modifier = Modifier.requiredSize(
                                        width = baseWidth,
                                        height = baseHeight / boundedScale
                                    )
                                ) {
                                    items(pageCount) { index ->
                                        PdfPage(
                                            pdfManager = pdfManager!!,
                                            pageIndex = index,
                                            mangaId = mangaId,
                                            isMarkerMode = isMarkerMode,
                                            activeColor = activeColor,
                                            markers = markers,
                                            eyeCareMode = eyeCareMode,
                                            onAddMarker = { pdfViewModel.addMarker(it) },
                                            onDeleteMarker = { pdfViewModel.deleteMarker(it) },
                                            onScrollToPage = { targetPage ->
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(targetPage)
                                                }
                                            }
                                        )
                                        if (index < pageCount - 1) {
                                            Spacer(
                                                modifier = Modifier
                                                    .height(12.dp)
                                                    .fillMaxWidth()
                                                    .background(if (eyeCareMode == EyeCareMode.NIGHT) Color(0xFF0D0D0D) else Color(0xFF1A1A1A))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── OVERLAY ───────────────────────────────────────────────────────────
                    AnimatedVisibility(
                        visible = showOverlay || isMarkerMode,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(140))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // ── Top Bar (Protected with statusBarsPadding & displayCutoutPadding) ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                                    .statusBarsPadding()
                                    .displayCutoutPadding()
                                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Frosted Back Button
                                Surface(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .clickable { handleBack() },
                                    shape = CircleShape,
                                    color = Color(0xFF14131E).copy(alpha = 0.88f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
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

                                // Document Title Capsule
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF14131E).copy(alpha = 0.88f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = manga?.title ?: "Document",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 180.dp)
                                        )
                                    }
                                }

                                // Quick Page Counter badge
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF14131E).copy(alpha = 0.88f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = "${currentPage + 1} / $pageCount",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // ── Vertical Fast-Scroll UI (Right Side) — kept as requested! ──
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .align(Alignment.CenterEnd)
                                    .padding(top = 100.dp, bottom = 90.dp)
                                    .wrapContentWidth()
                            ) {
                                val density = LocalDensity.current
                                val maxHeightPx = with(density) { maxHeight.toPx() }
                                val thumbHeightPx = with(density) { 52.dp.toPx() }

                                var dragOffset by remember { mutableFloatStateOf(0f) }
                                var isDragging by remember { mutableStateOf(false) }

                                val scrollFraction by remember {
                                    derivedStateOf {
                                        val layoutInfo = listState.layoutInfo
                                        val visibleItems = layoutInfo.visibleItemsInfo
                                        if (visibleItems.isEmpty() || pageCount <= 1) 0f
                                        else {
                                            val firstItem = visibleItems.first()
                                            val itemFraction = if (firstItem.size > 0) (-firstItem.offset.toFloat() / firstItem.size.toFloat()).coerceIn(0f, 1f) else 0f
                                            (firstItem.index.toFloat() + itemFraction) / (pageCount - 1).toFloat()
                                        }
                                    }
                                }

                                val thumbY = if (isDragging) dragOffset else (scrollFraction.coerceIn(0f, 1f) * (maxHeightPx - thumbHeightPx))

                                Row(
                                    modifier = Modifier
                                        .offset { IntOffset(0, thumbY.toInt()) }
                                        .align(Alignment.TopEnd)
                                        .pointerInput(pageCount) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    isDragging = true
                                                    dragOffset = (scrollFraction * (maxHeightPx - thumbHeightPx))
                                                },
                                                onDragEnd = { isDragging = false },
                                                onDragCancel = { isDragging = false }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragOffset = (dragOffset + dragAmount.y).coerceIn(0f, maxHeightPx - thumbHeightPx)
                                                val newFraction = dragOffset / (maxHeightPx - thumbHeightPx)

                                                val totalPosition = newFraction * (pageCount - 1)
                                                val pageIndex = totalPosition.toInt().coerceIn(0, pageCount - 1)
                                                val pageOffsetFraction = totalPosition - pageIndex

                                                val layoutInfo = listState.layoutInfo
                                                val pageHeight = layoutInfo.visibleItemsInfo.find { it.index == pageIndex }?.size
                                                    ?: layoutInfo.visibleItemsInfo.firstOrNull()?.size
                                                    ?: 1000

                                                val offset = (pageOffsetFraction * pageHeight).toInt()
                                                coroutineScope.launch {
                                                    listState.scrollToItem(pageIndex, offset)
                                                }
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Page Counter Pill
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White,
                                        shadowElevation = 4.dp
                                    ) {
                                        Text(
                                            text = "${currentPage + 1}/$pageCount",
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Dot Handle
                                    Surface(
                                        shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50),
                                        color = Color.White,
                                        modifier = Modifier.width(32.dp).height(52.dp),
                                        shadowElevation = 6.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            repeat(3) {
                                                Row {
                                                    repeat(2) {
                                                        Box(modifier = Modifier.padding(2.dp).size(4.dp).background(Color.LightGray, CircleShape))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Bottom Navigation & Feature Pill (Safely above Android 3-button / gesture bar) ──
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Active Floating Card (Highlighter Palette, Eye Care, or Page Navigator)
                                    AnimatedVisibility(
                                        visible = activeBottomPanel != null,
                                        enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { 20 }),
                                        exit = fadeOut(tween(140)) + slideOutVertically(targetOffsetY = { 20 })
                                    ) {
                                        when (activeBottomPanel) {
                                            PdfBottomPanel.HIGHLIGHTER -> {
                                                // Highlighter Palette Floating Card
                                                Surface(
                                                    shape = RoundedCornerShape(24.dp),
                                                    color = Color(0xFF14131E).copy(alpha = 0.96f),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                                    shadowElevation = 16.dp,
                                                    modifier = Modifier.padding(horizontal = 20.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(0.9f),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(10.dp)
                                                                        .background(Color(activeColor), CircleShape)
                                                                )
                                                                Text(
                                                                    text = "Highlighter Active",
                                                                    color = Color.White,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }

                                                            Text(
                                                                text = "Tap highlights to delete",
                                                                color = Color.White.copy(alpha = 0.5f),
                                                                fontSize = 11.sp
                                                            )
                                                        }

                                                        // Palette Swatches
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            HighlighterColors.forEach { option ->
                                                                val isSelected = activeColor == option.colorInt
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(34.dp)
                                                                        .clip(CircleShape)
                                                                        .background(option.displayColor)
                                                                        .border(
                                                                            width = if (isSelected) 3.dp else 1.dp,
                                                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                                                            shape = CircleShape
                                                                        )
                                                                        .clickable {
                                                                            activeColor = option.colorInt
                                                                            isMarkerMode = true
                                                                        },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    if (isSelected) {
                                                                        Icon(
                                                                            imageVector = Icons.Rounded.Check,
                                                                            contentDescription = null,
                                                                            tint = if (option.colorInt == 0xFFFFEB3B.toInt() || option.colorInt == 0xFFA7F3D0.toInt()) Color.Black else Color.White,
                                                                            modifier = Modifier.size(18.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            PdfBottomPanel.EYE_CARE -> {
                                                // Eye Protection Floating Card
                                                Surface(
                                                    shape = RoundedCornerShape(24.dp),
                                                    color = Color(0xFF14131E).copy(alpha = 0.96f),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                                    shadowElevation = 16.dp,
                                                    modifier = Modifier.padding(horizontal = 20.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text(
                                                            text = "Reading Comfort & Eye Protection",
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )

                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            EyeCareMode.values().forEach { mode ->
                                                                val isSelected = eyeCareMode == mode
                                                                Surface(
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                                                                    border = BorderStroke(
                                                                        1.dp,
                                                                        if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)
                                                                    ),
                                                                    modifier = Modifier.clickable { eyeCareMode = mode }
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                    ) {
                                                                        val previewColor = when (mode) {
                                                                            EyeCareMode.OFF -> Color.White
                                                                            EyeCareMode.SEPIA -> Color(0xFFFAF0D7)
                                                                            EyeCareMode.MINT -> Color(0xFFE8F5E9)
                                                                            EyeCareMode.NIGHT -> Color(0xFF1E1E2E)
                                                                        }
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(12.dp)
                                                                                .clip(CircleShape)
                                                                                .background(previewColor)
                                                                                .border(0.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                                                        )
                                                                        Text(
                                                                            text = mode.label,
                                                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                                                            fontSize = 11.sp,
                                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            PdfBottomPanel.PAGE_NAV -> {
                                                // Quick Page Navigator / Scrub Floating Card
                                                Surface(
                                                    shape = RoundedCornerShape(24.dp),
                                                    color = Color(0xFF14131E).copy(alpha = 0.96f),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                                    shadowElevation = 16.dp,
                                                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(0.92f)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            TextButton(
                                                                onClick = { coroutineScope.launch { listState.scrollToItem(0) } },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                            ) {
                                                                Text("Page 1", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                                            }

                                                            Text(
                                                                text = "Page ${currentPage + 1} of $pageCount",
                                                                color = Color.White,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )

                                                            TextButton(
                                                                onClick = { coroutineScope.launch { listState.scrollToItem(pageCount - 1) } },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                            ) {
                                                                Text("Page $pageCount", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                                            }
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            IconButton(
                                                                onClick = {
                                                                    if (currentPage > 0) {
                                                                        coroutineScope.launch { listState.scrollToItem(currentPage - 1) }
                                                                    }
                                                                },
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                                                    contentDescription = "Previous Page",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }

                                                            Slider(
                                                                value = currentPage.toFloat().coerceIn(0f, (pageCount - 1).coerceAtLeast(1).toFloat()),
                                                                onValueChange = { newVal ->
                                                                    coroutineScope.launch {
                                                                        listState.scrollToItem(newVal.toInt().coerceIn(0, pageCount - 1))
                                                                    }
                                                                },
                                                                valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                                                                modifier = Modifier.weight(1f),
                                                                colors = SliderDefaults.colors(
                                                                    thumbColor = Color.White,
                                                                    activeTrackColor = Color.White,
                                                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                                                )
                                                            )

                                                            IconButton(
                                                                onClick = {
                                                                    if (currentPage < pageCount - 1) {
                                                                        coroutineScope.launch { listState.scrollToItem(currentPage + 1) }
                                                                    }
                                                                },
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                                    contentDescription = "Next Page",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            null -> {}
                                        }
                                    }

                                    // Main Aesthetic Dock Pill
                                    Surface(
                                        shape = RoundedCornerShape(32.dp),
                                        color = Color(0xFF14131E).copy(alpha = 0.94f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                        shadowElevation = 14.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 1. Highlighter Button
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isMarkerMode) Color(activeColor).copy(alpha = 0.25f) else Color.Transparent,
                                                border = if (isMarkerMode) BorderStroke(1.dp, Color(activeColor)) else null,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        if (isMarkerMode) {
                                                            if (activeBottomPanel == PdfBottomPanel.HIGHLIGHTER) {
                                                                isMarkerMode = false
                                                                activeBottomPanel = null
                                                            } else {
                                                                activeBottomPanel = PdfBottomPanel.HIGHLIGHTER
                                                            }
                                                        } else {
                                                            isMarkerMode = true
                                                            activeBottomPanel = PdfBottomPanel.HIGHLIGHTER
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Brush,
                                                        contentDescription = "Highlighter",
                                                        tint = if (isMarkerMode) Color(activeColor) else Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                            // 2. Eye Care Tint Button
                                            Surface(
                                                shape = CircleShape,
                                                color = if (eyeCareMode != EyeCareMode.OFF) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                                border = if (eyeCareMode != EyeCareMode.OFF) BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)) else null,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        activeBottomPanel = if (activeBottomPanel == PdfBottomPanel.EYE_CARE) null else PdfBottomPanel.EYE_CARE
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Visibility,
                                                        contentDescription = "Eye Protection",
                                                        tint = when (eyeCareMode) {
                                                            EyeCareMode.OFF -> Color.White
                                                            EyeCareMode.SEPIA -> Color(0xFFFAF0D7)
                                                            EyeCareMode.MINT -> Color(0xFFA7F3D0)
                                                            EyeCareMode.NIGHT -> Color(0xFFB0BEC5)
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                            // 3. Screen Rotation Button
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        activity?.let { act ->
                                                            val isLand = act.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                            act.requestedOrientation = if (isLand) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.ScreenRotation,
                                                        contentDescription = "Rotate",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                            // 4. Zoom & Fit Button
                                            Surface(
                                                shape = CircleShape,
                                                color = if (liveScale > 1.05f) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                                border = if (liveScale > 1.05f) BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)) else null,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        coroutineScope.launch {
                                                            if (liveScale > 1.05f || liveScale < 0.95f) {
                                                                smoothReset()
                                                            } else {
                                                                smoothZoomIn(viewWidthPx / 2f, viewHeightPx / 2f, viewWidthPx, viewHeightPx)
                                                            }
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (liveScale > 1.05f) Icons.Rounded.RestartAlt else Icons.Rounded.ZoomIn,
                                                        contentDescription = "Zoom",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                            // 5. Page Navigator Button
                                            Surface(
                                                shape = CircleShape,
                                                color = if (activeBottomPanel == PdfBottomPanel.PAGE_NAV) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                                border = if (activeBottomPanel == PdfBottomPanel.PAGE_NAV) BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)) else null,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        activeBottomPanel = if (activeBottomPanel == PdfBottomPanel.PAGE_NAV) null else PdfBottomPanel.PAGE_NAV
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.AutoStories,
                                                        contentDescription = "Page Navigator",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
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
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PdfPage — render-only composable (gestures lifted to parent except marker mode)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PdfPage(
    pdfManager: PdfManager,
    pageIndex: Int,
    mangaId: Long,
    isMarkerMode: Boolean,
    activeColor: Int,
    markers: List<com.ballade.hwaran.core.database.entity.PdfMarkerEntity>,
    eyeCareMode: EyeCareMode = EyeCareMode.OFF,
    onAddMarker: (com.ballade.hwaran.core.database.entity.PdfMarkerEntity) -> Unit,
    onDeleteMarker: (com.ballade.hwaran.core.database.entity.PdfMarkerEntity) -> Unit,
    onScrollToPage: (Int) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember { mutableFloatStateOf(0.707f) }

    val context = LocalContext.current
    var pdfLinks by remember { mutableStateOf<List<PdfLinkData>>(emptyList()) }
    val pageMarkers = remember(markers) { markers.filter { it.page == pageIndex + 1 } }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var showDeleteDialog by remember { mutableStateOf<com.ballade.hwaran.core.database.entity.PdfMarkerEntity?>(null) }

    val pageBgColor = when (eyeCareMode) {
        EyeCareMode.OFF -> Color.White
        EyeCareMode.SEPIA -> Color(0xFFFAF0D7)
        EyeCareMode.MINT -> Color(0xFFE8F5E9)
        EyeCareMode.NIGHT -> Color(0xFF121212)
    }

    val nightMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                -1f,  0f,  0f, 0f, 255f,
                 0f, -1f,  0f, 0f, 255f,
                 0f,  0f, -1f, 0f, 255f,
                 0f,  0f,  0f, 1f,   0f
            )
        )
    }

    LaunchedEffect(pageIndex, pdfManager) {
        withContext(Dispatchers.IO) {
            try {
                synchronized(pdfManager) {
                    if (!pdfManager.isClosed) {
                        pdfManager.renderer.openPage(pageIndex).use { page ->
                            aspectRatio = page.width.toFloat() / page.height.toFloat()
                            var width = (page.width * 1.5).toInt()
                            var height = (page.height * 1.5).toInt()
                            val maxDim = 2048
                            if (width > maxDim || height > maxDim) {
                                val scale = maxDim.toFloat() / maxOf(width, height)
                                width = (width * scale).toInt()
                                height = (height * scale).toInt()
                            }
                            if (width > 0 && height > 0) {
                                val bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bm.eraseColor(android.graphics.Color.WHITE)
                                page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap = bm
                            }
                        }
                    }
                }
                val links = PdfLinkExtractor.getLinksForPage(context, mangaId.toString(), pdfManager.fd, pageIndex)
                pdfLinks = links
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(pageBgColor)
            // Gesture handling: only active in marker mode.
            // Normal tap/double-tap/pinch are handled by the parent Box.
            .pointerInput(isMarkerMode, pageMarkers, pdfLinks) {
                if (isMarkerMode) {
                    // Marker draw mode: drag to highlight regions
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStart = offset
                            dragCurrent = offset
                        },
                        onDragEnd = {
                            val start = dragStart
                            val end = dragCurrent
                            if (start != null && end != null) {
                                val dx = abs(start.x - end.x)
                                val dy = abs(start.y - end.y)
                                if (dx > 20f && dy > 20f) {
                                    onAddMarker(
                                        com.ballade.hwaran.core.database.entity.PdfMarkerEntity(
                                            mangaId = mangaId,
                                            page = pageIndex + 1,
                                            x1 = minOf(start.x, end.x) / size.width,
                                            y1 = minOf(start.y, end.y) / size.height,
                                            x2 = maxOf(start.x, end.x) / size.width,
                                            y2 = maxOf(start.y, end.y) / size.height,
                                            color = activeColor
                                        )
                                    )
                                }
                            }
                            dragStart = null
                            dragCurrent = null
                        },
                        onDragCancel = {
                            dragStart = null
                            dragCurrent = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragCurrent = dragCurrent?.plus(dragAmount)
                        }
                    )
                } else {
                    // Normal mode: handle link taps and marker taps via awaitEachGesture.
                    // Double-tap and single-tap-to-overlay are handled by the parent.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture

                        val moved = down.position.distance(up.position)
                        if (moved > 30f) return@awaitEachGesture // was a scroll/drag

                        val offset = up.position

                        // Check for PDF link tap
                        val link = pdfLinks.find {
                            val left = minOf(it.bounds.left, it.bounds.right)
                            val right = maxOf(it.bounds.left, it.bounds.right)
                            val top = minOf(it.bounds.top, it.bounds.bottom)
                            val bottom = maxOf(it.bounds.top, it.bounds.bottom)
                            val nx = offset.x / size.width
                            val ny = offset.y / size.height
                            nx >= left && nx <= right && ny >= top && ny <= bottom
                        }
                        if (link != null) {
                            up.consume()
                            if (link.uri != null) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link.uri)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            } else if (link.destPageIdx != null) {
                                onScrollToPage(link.destPageIdx)
                            }
                            return@awaitEachGesture
                        }

                        // Check for marker tap (to delete)
                        val marker = pageMarkers.find {
                            val rect = RectF(it.x1 * size.width, it.y1 * size.height, it.x2 * size.width, it.y2 * size.height)
                            rect.contains(offset.x, offset.y)
                        }
                        if (marker != null) {
                            up.consume()
                            showDeleteDialog = marker
                        }
                        // else: don't consume — parent gesture dispatcher handles overlay toggle
                    }
                }
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page $pageIndex",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (eyeCareMode == EyeCareMode.NIGHT) ColorFilter.colorMatrix(nightMatrix) else null
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Eye care tint overlay (Sepia / Mint)
                if (eyeCareMode == EyeCareMode.SEPIA) {
                    drawRect(
                        color = Color(0xFFFAF0D7),
                        blendMode = BlendMode.Multiply
                    )
                } else if (eyeCareMode == EyeCareMode.MINT) {
                    drawRect(
                        color = Color(0xFFE8F5E9),
                        blendMode = BlendMode.Multiply
                    )
                }

                // Draw highlight markers
                for (marker in pageMarkers) {
                    drawRect(
                        color = Color(marker.color).copy(alpha = 0.45f),
                        topLeft = Offset(marker.x1 * size.width, marker.y1 * size.height),
                        size = androidx.compose.ui.geometry.Size(
                            (marker.x2 - marker.x1) * size.width,
                            (marker.y2 - marker.y1) * size.height
                        ),
                        blendMode = BlendMode.Multiply
                    )
                }

                // Draw PDF links (subtle blue tint)
                for (link in pdfLinks) {
                    val left = minOf(link.bounds.left, link.bounds.right)
                    val right = maxOf(link.bounds.left, link.bounds.right)
                    val top = minOf(link.bounds.top, link.bounds.bottom)
                    val bottom = maxOf(link.bounds.top, link.bounds.bottom)

                    drawRect(
                        color = Color.Blue.copy(alpha = 0.15f),
                        topLeft = Offset(left * size.width, top * size.height),
                        size = androidx.compose.ui.geometry.Size(
                            (right - left) * size.width,
                            (bottom - top) * size.height
                        )
                    )
                }

                // Draw active drag selection (marker mode)
                val start = dragStart
                val end = dragCurrent
                if (start != null && end != null && isMarkerMode) {
                    drawRect(
                        color = Color(activeColor).copy(alpha = 0.45f),
                        topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)),
                        size = androidx.compose.ui.geometry.Size(
                            abs(start.x - end.x),
                            abs(start.y - end.y)
                        ),
                        blendMode = BlendMode.Multiply
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.LightGray, strokeWidth = 2.dp)
            }
        }

        if (showDeleteDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                containerColor = Color(0xFF1E1C2E),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.85f),
                title = { Text("Remove Highlight", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to remove this highlight marker?") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteMarker(showDeleteDialog!!)
                        showDeleteDialog = null
                    }) {
                        Text("Delete", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }
    }
}

@Composable
fun AnimatedHighlighterIcon(
    isMarkerMode: Boolean,
    activeColor: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )

    val progress by animateFloatAsState(
        targetValue = if (isMarkerMode) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Canvas(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        val scaleFactor = size.width / 64f
        withTransform({ scale(scaleFactor, scaleFactor, Offset.Zero) }) {
            val maskPath = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 2f, top = 2f, right = 62f, bottom = 62f,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                    )
                )
            }
            clipPath(maskPath) {
                drawRoundRect(
                    color = Color(0xFF222222),
                    topLeft = Offset(2f, 2f),
                    size = Size(60f, 60f),
                    cornerRadius = CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = Color(0xFF161616),
                topLeft = Offset(2f, 2f),
                size = Size(60f, 60f),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 3f)
            )
            
            val startX = 14f
            val maxEndX = 50f
            val distance = maxEndX - startX
            val currentEndX = startX + (distance * progress)
            
            if (currentEndX > startX) {
                drawLine(
                    color = Color(activeColor),
                    start = Offset(startX, 44f),
                    end = Offset(currentEndX, 44f),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
            }
            
            val penX = startX + (distance * progress)
            val penY = 44f
            
            withTransform({
                translate(penX, penY)
                rotate(35f, pivot = Offset.Zero)
            }) {
                val tipPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(-3f, 0f)
                    lineTo(-6f, -10f)
                    lineTo(6f, -10f)
                    lineTo(3f, 0f)
                    close()
                }
                
                // Draw fills
                drawPath(path = tipPath, color = Color(activeColor))
                
                drawRoundRect(
                    color = Color(0xFFEEEEEE),
                    topLeft = Offset(-6f, -30f),
                    size = Size(12f, 20f),
                    cornerRadius = CornerRadius(1.5f, 1.5f)
                )
                
                // Active color band on the pen body
                drawRect(
                    color = Color(activeColor),
                    topLeft = Offset(-6f, -22f),
                    size = Size(12f, 4f)
                )
                
                drawRoundRect(
                    color = Color(0xFF111111), // Dark cap
                    topLeft = Offset(-6f, -38f),
                    size = Size(12f, 8f),
                    cornerRadius = CornerRadius(2f, 2f)
                )

                // Draw strokes (outlines) to harmonize
                val outlineColor = Color(0xFF333333)
                val outlineStyle = Stroke(width = 2f, join = StrokeJoin.Round)
                
                drawPath(path = tipPath, color = outlineColor, style = outlineStyle)
                
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(-6f, -30f),
                    size = Size(12f, 20f),
                    cornerRadius = CornerRadius(1.5f, 1.5f),
                    style = outlineStyle
                )
                
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(-6f, -38f),
                    size = Size(12f, 8f),
                    cornerRadius = CornerRadius(2f, 2f),
                    style = outlineStyle
                )
            }
        }
    }
}
}

