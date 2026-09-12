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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
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
// Eye Care, Annotations & Panel Models
// ─────────────────────────────────────────────────────────────────────────────

enum class EyeCareMode(val label: String) {
    OFF("Default"),
    SEPIA("Sepia"),
    MINT("Mint"),
    NIGHT("Dark")
}

enum class PdfBottomPanel {
    HIGHLIGHTER,
    EYE_CARE
}

data class HighlightColorOption(
    val name: String,
    val colorInt: Int,
    val displayColor: Color
)

val HighlighterColors = listOf(
    HighlightColorOption("Lemon Pastel", 0xFFFFF59D.toInt(), Color(0xFFFFF59D)),
    HighlightColorOption("Mint Pastel", 0xFFA7F3D0.toInt(), Color(0xFFA7F3D0)),
    HighlightColorOption("Rose Pastel", 0xFFFBCFE8.toInt(), Color(0xFFFBCFE8)),
    HighlightColorOption("Sky Pastel", 0xFFBAE6FD.toInt(), Color(0xFFBAE6FD)),
    HighlightColorOption("Lavender Pastel", 0xFFDDD6FE.toInt(), Color(0xFFDDD6FE)),
    HighlightColorOption("Peach Pastel", 0xFFFED7AA.toInt(), Color(0xFFFED7AA))
)

data class PdfTextNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    val mangaId: Long,
    val page: Int, // 1-indexed
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

object PdfNotesManager {
    fun loadNotes(context: android.content.Context, mangaId: Long): List<PdfTextNote> {
        val file = File(context.filesDir, "pdf_notes/notes_${mangaId}.json")
        if (!file.exists()) return emptyList()
        return try {
            val jsonStr = file.readText()
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<PdfTextNote>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PdfTextNote(
                        id = obj.getString("id"),
                        mangaId = obj.getLong("mangaId"),
                        page = obj.getInt("page"),
                        text = obj.getString("text"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveNotes(context: android.content.Context, mangaId: Long, notes: List<PdfTextNote>) {
        try {
            val dir = File(context.filesDir, "pdf_notes")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "notes_${mangaId}.json")
            val array = org.json.JSONArray()
            for (note in notes) {
                val obj = org.json.JSONObject()
                obj.put("id", note.id)
                obj.put("mangaId", note.mangaId)
                obj.put("page", note.page)
                obj.put("text", note.text)
                obj.put("createdAt", note.createdAt)
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

sealed interface PdfAnnotationAction {
    data class AddMarker(val marker: com.ballade.hwaran.core.database.entity.PdfMarkerEntity) : PdfAnnotationAction
    data class DeleteMarker(val marker: com.ballade.hwaran.core.database.entity.PdfMarkerEntity) : PdfAnnotationAction
    data class AddNote(val note: PdfTextNote) : PdfAnnotationAction
    data class DeleteNote(val note: PdfTextNote) : PdfAnnotationAction
}

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
    var activeColor by remember { mutableIntStateOf(0xFFFFF59D.toInt()) }
    val markers by pdfViewModel.getMarkers(mangaId).collectAsState(initial = emptyList())
    var eyeCareMode by remember { mutableStateOf(EyeCareMode.OFF) }
    var activeBottomPanel by remember { mutableStateOf<PdfBottomPanel?>(null) }
    var viewWidthPx by remember { mutableIntStateOf(1080) }
    var viewHeightPx by remember { mutableIntStateOf(1920) }

    val undoStack = remember { mutableStateListOf<PdfAnnotationAction>() }
    val redoStack = remember { mutableStateListOf<PdfAnnotationAction>() }
    val textNotes = remember { mutableStateListOf<PdfTextNote>() }
    var showNotesDialog by remember { mutableStateOf(false) }
    var noteDialogPage by remember { mutableIntStateOf(1) }
    var noteInputText by remember { mutableStateOf("") }
    var noteForDetailDialog by remember { mutableStateOf<PdfTextNote?>(null) }

    LaunchedEffect(mangaId) {
        val loaded = withContext(Dispatchers.IO) {
            PdfNotesManager.loadNotes(context, mangaId)
        }
        textNotes.clear()
        textNotes.addAll(loaded)
    }

    fun persistNotes() {
        val currentList = textNotes.toList()
        coroutineScope.launch(Dispatchers.IO) {
            PdfNotesManager.saveNotes(context, mangaId, currentList)
        }
    }

    fun performUndo() {
        if (undoStack.isEmpty()) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val action = undoStack.removeAt(undoStack.lastIndex)
        when (action) {
            is PdfAnnotationAction.AddMarker -> {
                pdfViewModel.deleteMarker(action.marker)
                redoStack.add(action)
            }
            is PdfAnnotationAction.DeleteMarker -> {
                pdfViewModel.addMarker(action.marker) { inserted ->
                    redoStack.add(PdfAnnotationAction.DeleteMarker(inserted))
                }
            }
            is PdfAnnotationAction.AddNote -> {
                textNotes.removeAll { it.id == action.note.id }
                persistNotes()
                redoStack.add(action)
            }
            is PdfAnnotationAction.DeleteNote -> {
                textNotes.add(action.note)
                persistNotes()
                redoStack.add(action)
            }
        }
    }

    fun performRedo() {
        if (redoStack.isEmpty()) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val action = redoStack.removeAt(redoStack.lastIndex)
        when (action) {
            is PdfAnnotationAction.AddMarker -> {
                pdfViewModel.addMarker(action.marker) { inserted ->
                    undoStack.add(PdfAnnotationAction.AddMarker(inserted))
                }
            }
            is PdfAnnotationAction.DeleteMarker -> {
                pdfViewModel.deleteMarker(action.marker)
                undoStack.add(action)
            }
            is PdfAnnotationAction.AddNote -> {
                textNotes.add(action.note)
                persistNotes()
                undoStack.add(action)
            }
            is PdfAnnotationAction.DeleteNote -> {
                textNotes.removeAll { it.id == action.note.id }
                persistNotes()
                undoStack.add(action)
            }
        }
    }

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
                val vStart = layoutInfo.viewportStartOffset
                val vEnd = layoutInfo.viewportEndOffset
                // The page occupying the most vertical space in the viewport
                val mostVisibleItem = visibleItems.maxByOrNull { item ->
                    val top = maxOf(item.offset, vStart)
                    val bottom = minOf(item.offset + item.size, vEnd)
                    (bottom - top).coerceAtLeast(0)
                }
                mostVisibleItem?.index ?: visibleItems.first().index
            }
        }
    }

    fun addNoteToPage(page: Int, text: String) {
        if (text.isBlank()) return
        val targetPage = page.coerceIn(1, maxOf(1, pageCount))
        val note = PdfTextNote(
            mangaId = mangaId,
            page = targetPage,
            text = text.trim()
        )
        textNotes.add(note)
        persistNotes()
        undoStack.add(PdfAnnotationAction.AddNote(note))
        redoStack.clear()
        noteInputText = ""
        showNotesDialog = false
    }

    fun addNoteToCurrentPage(text: String) {
        addNoteToPage(currentPage + 1, text)
    }

    fun deleteNote(note: PdfTextNote) {
        textNotes.removeAll { it.id == note.id }
        persistNotes()
        undoStack.add(PdfAnnotationAction.DeleteNote(note))
        redoStack.clear()
        noteForDetailDialog = null
    }

    fun deleteLatestHighlightOnCurrentPage() {
        val currentMarkers = markers.filter { it.page == currentPage + 1 }
        val currentNotes = textNotes.filter { it.page == currentPage + 1 }
        if (currentMarkers.isNotEmpty()) {
            val latestMarker = currentMarkers.last()
            pdfViewModel.deleteMarker(latestMarker)
            undoStack.add(PdfAnnotationAction.DeleteMarker(latestMarker))
            redoStack.clear()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (currentNotes.isNotEmpty()) {
            val latestNote = currentNotes.last()
            deleteNote(latestNote)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                            notes = textNotes.toList(),
                                            eyeCareMode = eyeCareMode,
                                            onAddMarker = { marker ->
                                                pdfViewModel.addMarker(marker) { inserted ->
                                                    undoStack.add(PdfAnnotationAction.AddMarker(inserted))
                                                    redoStack.clear()
                                                }
                                            },
                                            onDeleteMarker = { marker ->
                                                pdfViewModel.deleteMarker(marker)
                                                undoStack.add(PdfAnnotationAction.DeleteMarker(marker))
                                                redoStack.clear()
                                            },
                                            onSelectNote = { note ->
                                                noteForDetailDialog = note
                                            },
                                            onDeleteNote = { note ->
                                                deleteNote(note)
                                            },
                                            onOpenPageNotes = { pageNum ->
                                                noteDialogPage = pageNum
                                                showNotesDialog = true
                                            },
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
                                        if (pageCount <= 1) 0f
                                        else (currentPage.toFloat() / (pageCount - 1).toFloat()).coerceIn(0f, 1f)
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
                                    // Page Counter Pill - Sleek frosted dark styling
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF14131E).copy(alpha = 0.94f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        shadowElevation = 8.dp
                                    ) {
                                        Text(
                                            text = "${currentPage + 1} / $pageCount",
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Dot Handle - Sleek frosted handle
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                                        color = Color(0xFF14131E).copy(alpha = 0.94f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier.width(28.dp).height(48.dp),
                                        shadowElevation = 8.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            repeat(3) {
                                                Row {
                                                    repeat(2) {
                                                        Box(modifier = Modifier.padding(1.5.dp).size(3.5.dp).background(Color.White.copy(alpha = 0.5f), CircleShape))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Bottom Navigation & Feature Pill (Safely above Android 3-button / gesture bar) ──
                            val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            val systemBarsBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
                            val maxBottomInset = maxOf(navBarsBottom, systemBarsBottom)
                            val dockBottomPadding = if (maxBottomInset > 20.dp) maxBottomInset + 16.dp else 60.dp

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = dockBottomPadding),
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
                                                // Eye Protection Floating Card (Symmetrical 4-mode layout, no text wrapping)
                                                Surface(
                                                    shape = RoundedCornerShape(24.dp),
                                                    color = Color(0xFF14131E).copy(alpha = 0.96f),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                                    shadowElevation = 16.dp,
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.92f)
                                                        .padding(horizontal = 12.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            EyeCareMode.values().forEach { mode ->
                                                                val isSelected = eyeCareMode == mode
                                                                Surface(
                                                                    shape = RoundedCornerShape(14.dp),
                                                                    color = if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                                                                    border = BorderStroke(
                                                                        1.dp,
                                                                        if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)
                                                                    ),
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .clip(RoundedCornerShape(14.dp))
                                                                        .clickable { eyeCareMode = mode }
                                                                ) {
                                                                    Column(
                                                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                                                    ) {
                                                                        val previewColor = when (mode) {
                                                                            EyeCareMode.OFF -> Color.White
                                                                            EyeCareMode.SEPIA -> Color(0xFFFAF0D7)
                                                                            EyeCareMode.MINT -> Color(0xFFE8F5E9)
                                                                            EyeCareMode.NIGHT -> Color(0xFF1E1E2E)
                                                                        }
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(14.dp)
                                                                                .clip(CircleShape)
                                                                                .background(previewColor)
                                                                                .border(0.8.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.35f), CircleShape)
                                                                        )
                                                                        Text(
                                                                            text = mode.label,
                                                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                                                            fontSize = 11.sp,
                                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                            maxLines = 1,
                                                                            softWrap = false
                                                                        )
                                                                    }
                                                                }
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
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // 1. Highlighter Toggle (Pure On/Off - NO popup!)
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isMarkerMode) Color(activeColor).copy(alpha = 0.25f) else Color.Transparent,
                                                border = if (isMarkerMode) BorderStroke(1.dp, Color(activeColor)) else null,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        isMarkerMode = !isMarkerMode
                                                        if (!isMarkerMode && activeBottomPanel == PdfBottomPanel.HIGHLIGHTER) {
                                                            activeBottomPanel = null
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Brush,
                                                        contentDescription = "Highlighter",
                                                        tint = if (isMarkerMode) Color(activeColor) else Color.White,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                            }

                                            // 2. Color Palette / Section Button (Toggles Color Picker card)
                                            Surface(
                                                shape = CircleShape,
                                                color = if (activeBottomPanel == PdfBottomPanel.HIGHLIGHTER) Color(activeColor).copy(alpha = 0.25f) else Color.Transparent,
                                                border = if (activeBottomPanel == PdfBottomPanel.HIGHLIGHTER) BorderStroke(1.dp, Color(activeColor)) else null,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        activeBottomPanel = if (activeBottomPanel == PdfBottomPanel.HIGHLIGHTER) null else PdfBottomPanel.HIGHLIGHTER
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Palette,
                                                        contentDescription = "Color Palette",
                                                        tint = Color(activeColor),
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                            }

                                            // 3. Delete Button (Deletes latest highlight/note on current page)
                                            val canDeleteOnCurrentPage = markers.any { it.page == currentPage + 1 } || textNotes.any { it.page == currentPage + 1 }
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .clickable(enabled = canDeleteOnCurrentPage) {
                                                        deleteLatestHighlightOnCurrentPage()
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Delete Annotation",
                                                        tint = if (canDeleteOnCurrentPage) Color.White else Color.White.copy(alpha = 0.3f),
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                            }

                                            // 4. Notes Button (Opens Page Notes dialog)
                                            val hasPageNotes = textNotes.any { it.page == currentPage + 1 }
                                            Surface(
                                                shape = CircleShape,
                                                color = if (hasPageNotes) Color(0xFFFFD54F).copy(alpha = 0.2f) else Color.Transparent,
                                                border = if (hasPageNotes) BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f)) else null,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        noteInputText = ""
                                                        noteDialogPage = currentPage + 1
                                                        showNotesDialog = true
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                                                        contentDescription = "Notes",
                                                        tint = if (hasPageNotes) Color(0xFFFFD54F) else Color.White,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                            }

                                            // 5. Eye Care Tint Button
                                            Surface(
                                                shape = CircleShape,
                                                color = if (eyeCareMode != EyeCareMode.OFF) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                                border = if (eyeCareMode != EyeCareMode.OFF) BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)) else null,
                                                modifier = Modifier
                                                    .size(38.dp)
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
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                            }

                                            // 6. Undo Button (Ctrl+Z)
                                            val canUndo = undoStack.isNotEmpty()
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .clickable(enabled = canUndo) {
                                                        performUndo()
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                                                        contentDescription = "Undo",
                                                        tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                            }

                                            // 7. Redo Button (Ctrl+Y)
                                            val canRedo = redoStack.isNotEmpty()
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .clickable(enabled = canRedo) {
                                                        performRedo()
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                                                        contentDescription = "Redo",
                                                        tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.3f),
                                                        modifier = Modifier.size(19.dp)
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

    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = {
                showNotesDialog = false
                noteInputText = ""
            },
            containerColor = Color(0xFF14131E),
            shape = RoundedCornerShape(24.dp),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Notes • Page $noteDialogPage",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            showNotesDialog = false
                            noteInputText = ""
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                val currentPageNotes = textNotes.filter { it.page == noteDialogPage }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentPageNotes.isNotEmpty()) {
                        Text(
                            text = "Notes on Page $noteDialogPage:",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (note in currentPageNotes) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = note.text,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        )
                                        IconButton(
                                            onClick = { deleteNote(note) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete Note",
                                                tint = Color(0xFFE57373),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = noteInputText,
                        onValueChange = { noteInputText = it },
                        placeholder = { Text("Write note for Page $noteDialogPage...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 130.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD54F),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = Color(0xFFFFD54F)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { addNoteToPage(noteDialogPage, noteInputText) },
                    enabled = noteInputText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD54F),
                        contentColor = Color(0xFF14131E),
                        disabledContainerColor = Color.White.copy(alpha = 0.1f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Note", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotesDialog = false
                    noteInputText = ""
                }) {
                    Text("Done", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    if (noteForDetailDialog != null) {
        val currentNote = noteForDetailDialog!!
        AlertDialog(
            onDismissRequest = { noteForDetailDialog = null },
            containerColor = Color(0xFF14131E),
            shape = RoundedCornerShape(24.dp),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Note • Page ${currentNote.page}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                SelectionContainer {
                    Text(
                        text = currentNote.text,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { deleteNote(currentNote) }
                ) {
                    Text("Delete Note", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteForDetailDialog = null }) {
                    Text("Close", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
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
    notes: List<PdfTextNote> = emptyList(),
    eyeCareMode: EyeCareMode = EyeCareMode.OFF,
    onAddMarker: (com.ballade.hwaran.core.database.entity.PdfMarkerEntity) -> Unit,
    onDeleteMarker: (com.ballade.hwaran.core.database.entity.PdfMarkerEntity) -> Unit,
    onSelectNote: (PdfTextNote) -> Unit = {},
    onDeleteNote: (PdfTextNote) -> Unit = {},
    onOpenPageNotes: (Int) -> Unit = {},
    onScrollToPage: (Int) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember { mutableFloatStateOf(0.707f) }

    val context = LocalContext.current
    var pdfLinks by remember { mutableStateOf<List<PdfLinkData>>(emptyList()) }
    val pageMarkers = markers.filter { it.page == pageIndex + 1 }
    val pageNotes = notes.filter { it.page == pageIndex + 1 }

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
                                } else if (dx <= 20f && dy <= 20f) {
                                    // Tap detected while in highlighter mode! Check if user tapped an existing highlight
                                    val tapPos = start
                                    val marker = pageMarkers.find {
                                        val rect = RectF(it.x1 * size.width, it.y1 * size.height, it.x2 * size.width, it.y2 * size.height)
                                        rect.contains(tapPos.x, tapPos.y)
                                    }
                                    if (marker != null) {
                                        showDeleteDialog = marker
                                    }
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

                // Draw highlight markers (soft, rounded, watercolor wash)
                for (marker in pageMarkers) {
                    drawRoundRect(
                        color = Color(marker.color).copy(alpha = 0.28f),
                        topLeft = Offset(marker.x1 * size.width, marker.y1 * size.height),
                        size = androidx.compose.ui.geometry.Size(
                            (marker.x2 - marker.x1) * size.width,
                            (marker.y2 - marker.y1) * size.height
                        ),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
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
                    drawRoundRect(
                        color = Color(activeColor).copy(alpha = 0.28f),
                        topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)),
                        size = androidx.compose.ui.geometry.Size(
                            abs(start.x - end.x),
                            abs(start.y - end.y)
                        ),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        blendMode = BlendMode.Multiply
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.LightGray, strokeWidth = 2.dp)
            }
        }

        // Sleek Non-Intrusive Golden Sticky Bookmark Tab (Top-Right Page Edge)
        if (pageNotes.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                color = Color(0xFFFFD54F),
                shadowElevation = 5.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 14.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .clickable {
                        if (pageNotes.size == 1) {
                            onSelectNote(pageNotes.first())
                        } else {
                            onOpenPageNotes(pageIndex + 1)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                        contentDescription = "Page Notes",
                        tint = Color(0xFF14131E),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (pageNotes.size == 1) "Note" else "${pageNotes.size}",
                        color = Color(0xFF14131E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
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

