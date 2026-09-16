package com.ballade.hwaran.frontend.player.novel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import coil.compose.AsyncImage
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
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
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
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
    LITERATA("Literata", FontFamily.Serif),
    LORA("Lora", FontFamily.Serif),
    MERRIWEATHER("Merit", FontFamily.Serif),
    SOURCE_SERIF("Source", FontFamily.Serif),
    NUNITO("Nunito", FontFamily.SansSerif),
    SERIF("Serif", FontFamily.Serif),
    SANS("Sans", FontFamily.SansSerif),
    MONO("Mono", FontFamily.Monospace)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelPlayerScreen(
    mangaId: Long = 0L,
    externalUriString: String? = null,
    musicViewModel: MusicViewModel? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember(context) { AppDatabase.getDatabase(context) }

    // ── Asset font families resolved once ──────────────────────────────────────
    val assetFonts: Map<NovelFontFamily, FontFamily> = remember(context) {
        mapOf(
            NovelFontFamily.LORA to try {
                val tf = android.graphics.Typeface.createFromAsset(context.assets, "fonts/Lora_Regular.ttf")
                FontFamily(tf)
            } catch (_: Exception) { FontFamily.Serif },

            NovelFontFamily.MERRIWEATHER to try {
                val tf = android.graphics.Typeface.createFromAsset(context.assets, "fonts/Merriweather_Regular.ttf")
                FontFamily(tf)
            } catch (_: Exception) { FontFamily.Serif },

            NovelFontFamily.SOURCE_SERIF to try {
                val tf = android.graphics.Typeface.createFromAsset(context.assets, "fonts/SourceSerif4_Regular.ttf")
                FontFamily(tf)
            } catch (_: Exception) { FontFamily.Serif },

            NovelFontFamily.LITERATA to try {
                val tf = android.graphics.Typeface.createFromAsset(context.assets, "fonts/Literata_Regular.ttf")
                FontFamily(tf)
            } catch (_: Exception) { FontFamily.Serif },

            NovelFontFamily.NUNITO to try {
                val tf = android.graphics.Typeface.createFromAsset(context.assets, "fonts/Nunito_Regular.ttf")
                FontFamily(tf)
            } catch (_: Exception) { FontFamily.SansSerif }
        )
    }

    fun resolveFont(selected: NovelFontFamily): FontFamily =
        assetFonts[selected] ?: selected.family

    // ── Custom Fonts Management ────────────────────────────────────────────────
    var customFonts by remember { mutableStateOf<List<CustomFontEntry>>(emptyList()) }
    var selectedCustomFontName by rememberSaveable { mutableStateOf<String?>(null) }

    fun refreshCustomFonts() {
        val dir = File(context.filesDir, "custom_fonts")
        if (!dir.exists()) dir.mkdirs()
        val list = dir.listFiles { f -> f.extension.lowercase() in listOf("ttf", "otf") }
            ?.mapNotNull { file ->
                try {
                    val tf = android.graphics.Typeface.createFromFile(file)
                    val cleanName = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
                    CustomFontEntry(cleanName, file, FontFamily(tf))
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()
        customFonts = list
    }

    LaunchedEffect(Unit) {
        refreshCustomFonts()
    }

    // Custom Font File Picker Launcher
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val dir = File(context.filesDir, "custom_fonts")
                    if (!dir.exists()) dir.mkdirs()
                    var displayName = "Font_${System.currentTimeMillis()}"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                    val destFile = File(dir, displayName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        refreshCustomFonts()
                        selectedCustomFontName = destFile.nameWithoutExtension.replace("_", " ").replace("-", " ")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────
    var novelBook by remember { mutableStateOf<NovelBook?>(null) }
    var mangaEntity by remember { mutableStateOf<MangaEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentChapterIndex by rememberSaveable { mutableIntStateOf(0) }
    var isControlsVisible by rememberSaveable { mutableStateOf(false) }
    var activeSettingTab by remember { mutableStateOf<NovelSettingTab?>(null) }
    var showTocSheet by remember { mutableStateOf(false) }
    var bookmarkToastMessage by remember { mutableStateOf<String?>(null) }
    var initialPositionRestored by remember(mangaId) { mutableStateOf(false) }
    var lastScrolledChapter by remember { mutableIntStateOf(-1) }

    // Shared LazyListState for continuous vertical scrolling
    val verticalListState = rememberLazyListState()

    // ── Checkpoints ───────────────────────────────────────────────────────────
    val markers by remember(mangaId) {
        if (mangaId > 0L) database.annotationDao().getMarkersForPdf(mangaId)
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // ── Reading Preferences ────────────────────────────────────────────────────
    var currentTheme by rememberSaveable { mutableStateOf(NovelTheme.DARK) }
    var readMode by rememberSaveable { mutableStateOf(NovelReadMode.VERTICAL_SCROLL) }
    var currentFont by rememberSaveable { mutableStateOf(NovelFontFamily.LITERATA) }
    var fontSizeSp by rememberSaveable { mutableFloatStateOf(18f) }
    var lineHeightMultiplier by rememberSaveable { mutableFloatStateOf(1.65f) }
    var paragraphSpacingDp by rememberSaveable { mutableIntStateOf(14) }
    var horizontalMarginDp by rememberSaveable { mutableIntStateOf(20) }
    var isJustified by rememberSaveable { mutableStateOf(false) }
    val textAlign = if (isJustified) TextAlign.Justify else TextAlign.Start
    var keepScreenOn by rememberSaveable { mutableStateOf(false) }
    var brightnessOverride by remember { mutableStateOf<Float?>(null) }

    val resolvedFont = remember(currentFont, selectedCustomFontName, customFonts, assetFonts) {
        if (selectedCustomFontName != null) {
            customFonts.find { it.name == selectedCustomFontName }?.fontFamily ?: resolveFont(currentFont)
        } else {
            resolveFont(currentFont)
        }
    }

    // ── Immersive Fullscreen (Always in Reading Mode) ───────────────────────────
    val activity = context as? Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ── Brightness ─────────────────────────────────────────────────────────────
    DisposableEffect(brightnessOverride) {
        val w = activity?.window
        val lp = w?.attributes
        if (brightnessOverride != null && lp != null) {
            lp.screenBrightness = brightnessOverride!!
            w.attributes = lp
        }
        onDispose {
            if (lp != null) {
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w?.attributes = lp
            }
        }
    }

    // ── Keep Screen On ─────────────────────────────────────────────────────────
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Load Novel Data ────────────────────────────────────────────────────────
    LaunchedEffect(mangaId, externalUriString) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                if (externalUriString != null) {
                    val uri = Uri.parse(externalUriString)
                    novelBook = NovelParser.parseNovel(context, uri)
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
                        val book = if (dbChapters.isNotEmpty() && dbChapters.any {
                                NovelParser.isNovelFile(it.folderUri)
                            }) {
                            NovelParser.parseNovelFromChapterEntities(context, manga.title, dbChapters)
                        } else {
                            val uri = Uri.parse(manga.parentUri)
                            val loaded = if (manga.parentUri.startsWith("file://") || manga.parentUri.startsWith("/")) {
                                val file = if (manga.parentUri.startsWith("file://"))
                                    File(Uri.parse(manga.parentUri).path ?: "")
                                else File(manga.parentUri)
                                if (file.exists()) NovelParser.parseNovelFromFile(file)
                                else NovelParser.parseNovel(context, uri, manga.title)
                            } else {
                                NovelParser.parseNovel(context, uri, manga.title)
                            }
                            if (loaded.chapters.isEmpty() && dbChapters.isNotEmpty())
                                NovelParser.parseNovelFromChapterEntities(context, manga.title, dbChapters)
                            else loaded
                        }
                        novelBook = book

                        val savedChapterIndex = if (targetChapterIndex > 0) targetChapterIndex else (manga.lastReadPage ?: 0)
                        if (book.chapters.isNotEmpty()) {
                            currentChapterIndex = savedChapterIndex.coerceIn(0, book.chapters.size - 1)
                        }

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

    // ── Save Progress ──────────────────────────────────────────────────────────
    fun saveProgress(chapterIdx: Int, scrollPosition: Int = 0) {
        if (mangaId > 0L && novelBook != null && chapterIdx in novelBook!!.chapters.indices) {
            val chapterTitle = novelBook!!.chapters[chapterIdx].title
            coroutineScope.launch(Dispatchers.IO) {
                val current = database.mediaDao().getMangaById(mangaId) ?: return@launch
                val now = System.currentTimeMillis()
                val updated = current.copy(
                    lastReadPage = chapterIdx,
                    lastReadTitle = chapterTitle,
                    position = scrollPosition,
                    openCount = current.openCount + 1,
                    lastModified = now
                )
                database.mediaDao().insertManga(updated)
                withContext(Dispatchers.Main) {
                    mangaEntity = updated
                }
                if (current.parentMangaId != null) {
                    database.mediaDao().getMangaById(current.parentMangaId)?.let { parent ->
                        database.mediaDao().insertManga(
                            parent.copy(
                                lastReadPage = chapterIdx,
                                lastReadTitle = chapterTitle,
                                position = scrollPosition,
                                openCount = parent.openCount + 1,
                                lastModified = now
                            )
                        )
                    }
                }
                HistoryTracker.logEvent(
                    "READ_NOVEL",
                    chapterTitle,
                    "mangaId:$mangaId|pages:${chapterIdx + 1}|pos:$scrollPosition|totalPages:${novelBook?.chapters?.size ?: 0}"
                )
            }
        }
    }

    // ── Bookmarks / Reading Progress Checkpoint ────────────────────────────────
    val isCurrentBookmarked = remember(mangaEntity?.lastReadPage, currentChapterIndex) {
        mangaEntity?.lastReadPage == currentChapterIndex
    }

    fun toggleBookmark() {
        if (mangaId > 0L && novelBook != null && currentChapterIndex in novelBook!!.chapters.indices) {
            val chapterIdx = currentChapterIndex
            val scrollOffset = if (readMode == NovelReadMode.VERTICAL_SCROLL) {
                verticalListState.firstVisibleItemIndex
            } else {
                0
            }
            saveProgress(chapterIdx, scrollOffset)
            val title = novelBook!!.chapters.getOrNull(chapterIdx)?.title ?: "Chapter ${chapterIdx + 1}"
            bookmarkToastMessage = "Progress Saved • ${title.ifBlank { "Chapter ${chapterIdx + 1}" }}"
        }
    }

    LaunchedEffect(bookmarkToastMessage) {
        if (bookmarkToastMessage != null) {
            kotlinx.coroutines.delay(2200)
            bookmarkToastMessage = null
        }
    }

    // ── Back Handler ───────────────────────────────────────────────────────────
    BackHandler {
        when {
            activeSettingTab != null -> activeSettingTab = null
            showTocSheet -> showTocSheet = false
            isControlsVisible -> isControlsVisible = false
            else -> onNavigateBack()
        }
    }

    val chapters = novelBook?.chapters ?: emptyList()
    val activeChapter = chapters.getOrNull(currentChapterIndex)
    val basePath = remember(mangaEntity, externalUriString) {
        externalUriString ?: mangaEntity?.parentUri
    }

    // ── Root Canvas ─────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentTheme.bg)
    ) {

        // ── Loading ──────────────────────────────────────────────────────────────
        if (isLoading) {
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
                            contentDescription = "Loading",
                            tint = Color(0xFFE6E8EC).copy(alpha = glowAlpha),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Opening Novel…",
                    color = currentTheme.text,
                    fontSize = 16.sp,
                    fontFamily = resolvedFont,
                    fontWeight = FontWeight.Medium
                )
            }

        // ── Empty State ───────────────────────────────────────────────────────────
        } else if (chapters.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = currentTheme.secondaryText,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No readable chapters found.",
                    color = currentTheme.text,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                FilledTonalButton(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = currentTheme.surface)
                ) { Text("Go Back", color = currentTheme.text) }
            }

        // ── Reading View ───────────────────────────────────────────────────────────
        } else {
            val totalChapters = chapters.size.coerceAtLeast(1)
            val paragraphs = remember(activeChapter?.content) {
                val raw = activeChapter?.content ?: ""
                val list = if (raw.contains("\n\n") || raw.contains("\r\n\r\n")) {
                    raw.split(Regex("""(?:\r?\n\s*){2,}"""))
                } else {
                    raw.split(Regex("""\r?\n"""))
                }
                list.filter { it.isNotBlank() }
            }

            val currentOverallPct = remember(currentChapterIndex, verticalListState.firstVisibleItemIndex, paragraphs.size, totalChapters, readMode) {
                if (readMode == NovelReadMode.VERTICAL_SCROLL) {
                    val chProgress = if (paragraphs.isNotEmpty()) {
                        (verticalListState.firstVisibleItemIndex.toFloat() / paragraphs.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                    } else 0f
                    (((currentChapterIndex.toFloat() + chProgress) / totalChapters) * 100).toInt().coerceIn(0, 100)
                } else {
                    (((currentChapterIndex + 1).toFloat() / totalChapters) * 100).toInt().coerceIn(0, 100)
                }
            }

            // ── Vertical Scroll Mode ────────────────────────────────────────────────
            if (readMode == NovelReadMode.VERTICAL_SCROLL) {
                LaunchedEffect(novelBook, mangaEntity) {
                    if (!initialPositionRestored && novelBook != null && mangaEntity != null) {
                        val targetChapter = (mangaEntity?.lastReadPage ?: 0).coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                        val targetPos = (mangaEntity?.position ?: 0).coerceAtLeast(0)
                        currentChapterIndex = targetChapter
                        lastScrolledChapter = targetChapter
                        if (targetPos > 0) {
                            verticalListState.scrollToItem(targetPos)
                        }
                        initialPositionRestored = true
                    }
                }

                LaunchedEffect(currentChapterIndex) {
                    if (initialPositionRestored && lastScrolledChapter != currentChapterIndex) {
                        lastScrolledChapter = currentChapterIndex
                        verticalListState.scrollToItem(0)
                        saveProgress(currentChapterIndex, 0)
                    }
                }

                LaunchedEffect(currentChapterIndex, verticalListState, initialPositionRestored) {
                    if (!initialPositionRestored) return@LaunchedEffect
                    snapshotFlow { verticalListState.firstVisibleItemIndex }
                        .distinctUntilChanged()
                        .collect { firstIdx ->
                            if (firstIdx >= 0 && lastScrolledChapter == currentChapterIndex) {
                                saveProgress(currentChapterIndex, firstIdx)
                            }
                        }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LazyColumn(
                        state = verticalListState,
                        contentPadding = PaddingValues(
                            top = if (isControlsVisible) 80.dp else 44.dp,
                            bottom = 120.dp,
                            start = horizontalMarginDp.dp,
                            end = horizontalMarginDp.dp
                        ),
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 750.dp)
                    ) {
                        item(key = "ch_header_$currentChapterIndex") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            ) {
                                Text(
                                    text = activeChapter?.title ?: "",
                                    fontFamily = resolvedFont,
                                    fontSize = (fontSizeSp + 6).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentTheme.text,
                                    lineHeight = ((fontSizeSp + 6) * 1.3f).sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Chapter ${currentChapterIndex + 1} of ${chapters.size}",
                                        color = currentTheme.secondaryText,
                                        fontSize = 13.sp,
                                        fontFamily = resolvedFont
                                    )
                                    Text("•", color = currentTheme.secondaryText.copy(alpha = 0.5f), fontSize = 12.sp)
                                    Text(
                                        text = "${activeChapter?.wordCount ?: 0} words",
                                        color = currentTheme.secondaryText,
                                        fontSize = 13.sp,
                                        fontFamily = resolvedFont
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = currentTheme.border.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))
                            }
                        }

                        itemsIndexed(
                            items = paragraphs,
                            key = { pIdx, _ -> "p_${currentChapterIndex}_$pIdx" }
                        ) { _, paragraph ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = paragraphSpacingDp.dp)
                            ) {
                                SelectionContainer {
                                    NovelParagraphBlock(
                                        paragraph = paragraph,
                                        theme = currentTheme,
                                        fontFamily = resolvedFont,
                                        fontSizeSp = fontSizeSp,
                                        lineHeightMultiplier = lineHeightMultiplier,
                                        textAlign = textAlign,
                                        basePath = basePath
                                    )
                                }
                            }
                        }

                    // End of chapter footer with prev/next
                    item(key = "ch_footer_$currentChapterIndex") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalDivider(color = currentTheme.border.copy(alpha = 0.5f))
                            Spacer(Modifier.height(24.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val hasPrev = currentChapterIndex > 0 && chapters.isNotEmpty()
                                val hasNext = currentChapterIndex < chapters.size - 1 && chapters.isNotEmpty()

                                FilledTonalButton(
                                    onClick = {
                                        if (hasPrev) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentChapterIndex--
                                        }
                                    },
                                    enabled = hasPrev,
                                    modifier = Modifier.graphicsLayer { alpha = if (hasPrev) 1f else 0.30f },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = currentTheme.surface,
                                        contentColor = currentTheme.text,
                                        disabledContainerColor = currentTheme.surface.copy(alpha = 0.40f),
                                        disabledContentColor = currentTheme.secondaryText.copy(alpha = 0.40f)
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Prev Chapter")
                                }

                                FilledTonalButton(
                                    onClick = {
                                        if (hasNext) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentChapterIndex++
                                        }
                                    },
                                    enabled = hasNext,
                                    modifier = Modifier.graphicsLayer { alpha = if (hasNext) 1f else 0.30f },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = currentTheme.surface,
                                        contentColor = currentTheme.text,
                                        disabledContainerColor = currentTheme.surface.copy(alpha = 0.40f),
                                        disabledContentColor = currentTheme.secondaryText.copy(alpha = 0.40f)
                                    )
                                ) {
                                    Text("Next Chapter")
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Paginated Mode ────────────────────────────────────────────────────
            } else {
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
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val chapter = chapters[page]
                    val scrollState = rememberScrollState()

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 750.dp)
                                .verticalScroll(scrollState)
                                .padding(
                                    top = if (isControlsVisible) 80.dp else 40.dp,
                                    bottom = 120.dp,
                                    start = horizontalMarginDp.dp,
                                    end = horizontalMarginDp.dp
                                )
                        ) {
                        Text(
                            text = chapter.title,
                            fontFamily = resolvedFont,
                            fontSize = (fontSizeSp + 6).sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.text,
                            lineHeight = ((fontSizeSp + 6) * 1.3f).sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Chapter ${page + 1} of ${chapters.size} • ${chapter.wordCount} words",
                            color = currentTheme.secondaryText,
                            fontSize = 12.sp,
                            fontFamily = resolvedFont
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = currentTheme.border.copy(alpha = 0.5f))
                        Spacer(Modifier.height(20.dp))

                        val paragraphs = remember(chapter.content) {
                            val raw = chapter.content
                            if (raw.contains("\n\n") || raw.contains("\r\n\r\n")) {
                                raw.split(Regex("""(?:\r?\n\s*){2,}"""))
                            } else {
                                raw.split(Regex("""\r?\n"""))
                            }
                        }

                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp.dp)) {
                                paragraphs.forEach { p ->
                                    if (p.isNotBlank()) {
                                        NovelParagraphBlock(
                                            paragraph = p,
                                            theme = currentTheme,
                                            fontFamily = resolvedFont,
                                            fontSizeSp = fontSizeSp,
                                            lineHeightMultiplier = lineHeightMultiplier,
                                            textAlign = textAlign,
                                            basePath = basePath
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════════
            // SLEEK TOP-RIGHT PROGRESS & CHECKPOINT STICKY TAB
            // ═════════════════════════════════════════════════════════════════════
            if (!showTocSheet) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    color = Color(0xFF14131E).copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 14.dp)
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isControlsVisible = true
                            activeSettingTab = NovelSettingTab.CHECKPOINTS
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = "Checkpoints",
                            tint = if (markers.isNotEmpty()) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "$currentOverallPct%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════════
            // RIGHT-SIDE FOCUS TRIGGER (Full-height right margin tap zone)
            // ═════════════════════════════════════════════════════════════════════
            if (!isControlsVisible && !showTocSheet) {
                // Entire right margin tap zone (as drawn by user in screenshot 14.32.09)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(72.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isControlsVisible = true
                        }
                )
            }

            // ═════════════════════════════════════════════════════════════════════
            // OVERLAY CONTROLS (Top Bar, Dismiss Background, and Settings Pill)
            // ═════════════════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(160)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Transparent dismiss layer BEHIND top bar and settings pill
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (activeSettingTab != null) {
                                    activeSettingTab = null
                                } else {
                                    isControlsVisible = false
                                }
                            }
                    )

                    // 2. TOP HUD — Minimalist: Back button + Centered Title Capsule
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        currentTheme.bg.copy(alpha = 0.96f),
                                        currentTheme.bg.copy(alpha = 0f)
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .displayCutoutPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)
                    ) {
                        // Circular Frosted Back Button (Left Aligned)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { onNavigateBack() },
                            shape = CircleShape,
                            color = currentTheme.surface.copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, currentTheme.border.copy(alpha = 0.5f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = currentTheme.text,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Document Title Capsule (Middle Top Centered)
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(20.dp),
                            color = currentTheme.surface.copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, currentTheme.border.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = currentTheme.secondaryText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = novelBook?.title ?: mangaEntity?.title ?: "Novel Reader",
                                    color = currentTheme.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp)
                                )
                            }
                        }

                        // Quick Jump to Last Read Capsule (if away from last read chapter or has saved position)
                        val lastReadChapter = mangaEntity?.lastReadPage
                        if (lastReadChapter != null && (lastReadChapter != currentChapterIndex || (mangaEntity?.position ?: 0) > 0)) {
                            val savedPos = (mangaEntity?.position ?: 0).coerceAtLeast(0)
                            val savedPct = (((lastReadChapter.toFloat() + 0.5f) / totalChapters) * 100).toInt().coerceIn(0, 100)
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        val target = lastReadChapter.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                                        lastScrolledChapter = target
                                        currentChapterIndex = target
                                        coroutineScope.launch {
                                            verticalListState.scrollToItem(savedPos)
                                        }
                                        saveProgress(target, savedPos)
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFFFD54F).copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.40f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bookmark,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Last: $savedPct% (Ch. ${lastReadChapter + 1})",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // 3. Right Side Settings Pill
                    NovelReaderSettingsPill(
                        activeTab = activeSettingTab,
                        onTabSelected = { activeSettingTab = it },
                        currentFont = currentFont,
                        onFontChange = {
                            selectedCustomFontName = null
                            currentFont = it
                        },
                        customFonts = customFonts,
                        selectedCustomFontName = selectedCustomFontName,
                        onSelectCustomFont = { selectedCustomFontName = it },
                        onAddCustomFont = {
                            fontPickerLauncher.launch(
                                arrayOf("font/*", "application/octet-stream", "application/x-font-ttf", "application/x-font-opentype")
                            )
                        },
                        fontSizeSp = fontSizeSp,
                        onFontSizeChange = { fontSizeSp = it },
                        lineHeightMultiplier = lineHeightMultiplier,
                        onLineHeightChange = { lineHeightMultiplier = it },
                        paragraphSpacingDp = paragraphSpacingDp,
                        onParagraphSpacingChange = { paragraphSpacingDp = it },
                        horizontalMarginDp = horizontalMarginDp,
                        onHorizontalMarginChange = { horizontalMarginDp = it },
                        textAlign = textAlign,
                        onTextAlignChange = { isJustified = (it == TextAlign.Justify) },
                        currentTheme = currentTheme,
                        onThemeChange = { currentTheme = it },
                        readMode = readMode,
                        onReadModeChange = { readMode = it },
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChange = { keepScreenOn = it },
                        brightnessOverride = brightnessOverride,
                        onBrightnessOverrideChange = { brightnessOverride = it },
                        markers = markers,
                        chapters = chapters,
                        onAddCheckpoint = {
                            if (mangaId > 0L && novelBook != null && currentChapterIndex in novelBook!!.chapters.indices) {
                                val chapterIdx = currentChapterIndex
                                val scrollOffset = if (readMode == NovelReadMode.VERTICAL_SCROLL) {
                                    verticalListState.firstVisibleItemIndex
                                } else {
                                    0
                                }
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.annotationDao().insertMarker(
                                        com.ballade.hwaran.core.database.entity.PdfMarkerEntity(
                                            mangaId = mangaId,
                                            page = chapterIdx,
                                            x1 = scrollOffset.toFloat(),
                                            y1 = 0f,
                                            x2 = 0f,
                                            y2 = 0f,
                                            color = 0,
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                                saveProgress(chapterIdx, scrollOffset)
                                val title = novelBook!!.chapters.getOrNull(chapterIdx)?.title ?: "Chapter ${chapterIdx + 1}"
                                bookmarkToastMessage = "Checkpoint Added • $title"
                            }
                        },
                        onJumpToCheckpoint = { marker ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val targetChapter = marker.page.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                            val targetScroll = marker.x1.toInt().coerceAtLeast(0)
                            lastScrolledChapter = targetChapter
                            currentChapterIndex = targetChapter
                            coroutineScope.launch {
                                verticalListState.scrollToItem(targetScroll)
                            }
                            saveProgress(targetChapter, targetScroll)
                        },
                        onDeleteCheckpoint = { marker ->
                            coroutineScope.launch(Dispatchers.IO) {
                                database.annotationDao().deleteMarkerById(marker.id)
                            }
                        },
                        onShowToc = { showTocSheet = true; activeSettingTab = null },
                        glowColor = currentTheme.accent,
                        musicViewModel = musicViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        // ── Bookmark Saved Notification Banner ──────────────────────────────────
        AnimatedVisibility(
            visible = bookmarkToastMessage != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { -it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1E28).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, currentTheme.accent.copy(alpha = 0.50f)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmark,
                        contentDescription = null,
                        tint = currentTheme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = bookmarkToastMessage ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TABLE OF CONTENTS MODAL BOTTOM SHEET
    // ═════════════════════════════════════════════════════════════════════════
    if (showTocSheet) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredChapters = remember(chapters, searchQuery) {
            if (searchQuery.isBlank()) chapters
            else chapters.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            containerColor = currentTheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = currentTheme.secondaryText) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Table of Contents",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.text
                        )
                        Text(
                            text = "${chapters.size} Chapters",
                            fontSize = 12.sp,
                            color = currentTheme.secondaryText
                        )
                    }

                    // Jump to saved bookmark badge if available
                    if (mangaEntity?.lastReadPage != null && mangaEntity?.lastReadPage != currentChapterIndex) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val target = (mangaEntity?.lastReadPage ?: 0).coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                                currentChapterIndex = target
                                coroutineScope.launch {
                                    verticalListState.scrollToItem((mangaEntity?.position ?: 0).coerceAtLeast(0))
                                }
                                saveProgress(target, mangaEntity?.position ?: 0)
                                showTocSheet = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = currentTheme.accent.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, currentTheme.accent.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = currentTheme.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Saved: Ch. ${(mangaEntity?.lastReadPage ?: 0) + 1}",
                                    color = currentTheme.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search chapters…", color = currentTheme.secondaryText) },
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

                Spacer(Modifier.height(14.dp))

                // Chapters List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filteredChapters) { _, chapter ->
                        val isCurrent = chapter.index == currentChapterIndex
                        val isSavedBookmark = mangaEntity?.lastReadPage == chapter.index

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentChapterIndex = chapter.index
                                val targetScroll = if (isSavedBookmark) (mangaEntity?.position ?: 0).coerceAtLeast(0) else 0
                                coroutineScope.launch {
                                    verticalListState.scrollToItem(targetScroll)
                                }
                                saveProgress(chapter.index, targetScroll)
                                showTocSheet = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) currentTheme.accent.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isCurrent) BorderStroke(1.dp, currentTheme.accent.copy(alpha = 0.35f)) else null,
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
                                    color = if (isCurrent) currentTheme.accent else currentTheme.secondaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        color = if (isCurrent) currentTheme.accent else currentTheme.text,
                                        fontSize = 15.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${chapter.wordCount} words",
                                        color = currentTheme.secondaryText,
                                        fontSize = 12.sp
                                    )
                                }

                                if (isSavedBookmark) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .background(currentTheme.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bookmark,
                                            contentDescription = "Saved Bookmark",
                                            tint = currentTheme.accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Saved",
                                            color = currentTheme.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                } else if (isCurrent) {
                                    Text(
                                        text = "Reading",
                                        color = currentTheme.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .background(currentTheme.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
private fun NovelParagraphBlock(
    paragraph: String,
    theme: NovelTheme,
    fontFamily: FontFamily,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    textAlign: TextAlign,
    basePath: String?,
    modifier: Modifier = Modifier
) {
    val trimmed = paragraph.trim()
    if (trimmed.isBlank()) return

    val context = LocalContext.current

    // 1. Markdown Image: ![alt](url_or_path)
    val imageMatch = Regex("""^!\[(.*?)\]\((.*?)\)$""").find(trimmed)
    if (imageMatch != null) {
        val alt = imageMatch.groupValues[1]
        val rawPath = imageMatch.groupValues[2].trim()

        val resolvedModel: Any = remember(rawPath, basePath) {
            when {
                rawPath.startsWith("http://") || rawPath.startsWith("https://") || rawPath.startsWith("content://") || rawPath.startsWith("file://") -> rawPath
                basePath != null -> {
                    val baseDir = if (basePath.startsWith("file://")) File(basePath.removePrefix("file://")) else File(basePath)
                    val targetDir = if (baseDir.isDirectory) baseDir else (baseDir.parentFile ?: baseDir)
                    val resolvedFile = File(targetDir, rawPath.removePrefix("./"))
                    if (resolvedFile.exists()) resolvedFile else rawPath
                }
                else -> rawPath
            }
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = resolvedModel,
                contentDescription = alt.ifBlank { null },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.surface)
            )
            if (alt.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = alt,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.secondaryText,
                    textAlign = TextAlign.Center,
                    fontSize = (fontSizeSp * 0.85f).sp
                )
            }
        }
        return
    }

    // 2. Markdown Headings (# Header)
    if (trimmed.startsWith("#")) {
        val level = trimmed.takeWhile { it == '#' }.length
        val headerText = trimmed.drop(level).trim()
        val headingSize = when (level) {
            1 -> (fontSizeSp + 8).sp
            2 -> (fontSizeSp + 5).sp
            else -> (fontSizeSp + 3).sp
        }
        Text(
            text = headerText,
            color = theme.text,
            fontSize = headingSize,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily,
            lineHeight = (headingSize.value * 1.3f).sp,
            textAlign = textAlign,
            modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
        return
    }

    // 3. Blockquote (> quote)
    if (trimmed.startsWith(">")) {
        val quoteText = trimmed.removePrefix(">").trim()
        Surface(
            color = theme.surface.copy(alpha = 0.5f),
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            border = BorderStroke(1.dp, theme.border.copy(alpha = 0.4f)),
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(theme.accent, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = quoteText,
                    color = theme.text.copy(alpha = 0.9f),
                    fontSize = fontSizeSp.sp,
                    fontFamily = fontFamily,
                    lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
        return
    }

    // 4. Video / Media source link: e.g. [Watch Video](https://...mp4) or bare https://...mp4, youtube.com
    val isMediaLink = trimmed.contains(".mp4", ignoreCase = true) ||
            trimmed.contains(".webm", ignoreCase = true) ||
            trimmed.contains(".mkv", ignoreCase = true) ||
            trimmed.contains(".mp3", ignoreCase = true) ||
            trimmed.contains("youtube.com/watch", ignoreCase = true) ||
            trimmed.contains("youtu.be/", ignoreCase = true)

    if (isMediaLink) {
        val urlMatch = Regex("""https?://[^\s)]+""").find(trimmed)
        if (urlMatch != null) {
            val mediaUrl = urlMatch.value
            Surface(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mediaUrl))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                color = theme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, theme.border),
                modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = theme.accent.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play Media",
                                tint = theme.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trimmed.substringBefore("http").ifBlank { "Play Media Source" },
                            color = theme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mediaUrl,
                            color = theme.secondaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            return
        }
    }

    // 5. Default text / paragraph (with link handling)
    val hasLinks = trimmed.contains("http://") || trimmed.contains("https://")
    if (hasLinks) {
        val linkRegex = Regex("""https?://[^\s)]+""")
        val urlMatch = linkRegex.find(trimmed)
        if (urlMatch != null) {
            val url = urlMatch.value
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = trimmed,
                    color = theme.text,
                    fontSize = fontSizeSp.sp,
                    fontFamily = fontFamily,
                    lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open Link",
                        tint = theme.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Open Link ($url)",
                        color = theme.accent,
                        fontSize = (fontSizeSp * 0.85f).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            return
        }
    }

    // Default plain text
    Text(
        text = trimmed,
        color = theme.text,
        fontSize = fontSizeSp.sp,
        fontFamily = fontFamily,
        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
        textAlign = textAlign,
        modifier = modifier.fillMaxWidth()
    )
}
