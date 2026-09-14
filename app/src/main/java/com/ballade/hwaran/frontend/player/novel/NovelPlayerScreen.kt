package com.ballade.hwaran.frontend.player.novel

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    var bookmarkedChapters by remember { mutableStateOf(setOf<Int>()) }

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

    // Shared LazyListState for continuous vertical scrolling
    val verticalListState = rememberLazyListState()

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
                                it.folderUri.lowercase().let { u ->
                                    u.endsWith(".txt") || u.endsWith(".md") || u.endsWith(".markdown") || u.endsWith(".epub")
                                }
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
    fun saveProgress(chapterIdx: Int) {
        if (mangaId > 0L && novelBook != null && chapterIdx in novelBook!!.chapters.indices) {
            val chapterTitle = novelBook!!.chapters[chapterIdx].title
            coroutineScope.launch(Dispatchers.IO) {
                val current = database.mediaDao().getMangaById(mangaId) ?: return@launch
                database.mediaDao().insertManga(
                    current.copy(lastReadPage = chapterIdx, lastReadTitle = chapterTitle)
                )
                HistoryTracker.logEvent(
                    "READ_NOVEL",
                    chapterTitle,
                    "mangaId:$mangaId|pages:${chapterIdx + 1}|totalPages:${novelBook?.chapters?.size ?: 0}"
                )
            }
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

    // Scroll Progress Percentage Calculation (for bottom-right 53% indicator)
    val scrollProgressPercent by remember(readMode, currentChapterIndex, chapters.size) {
        derivedStateOf {
            if (readMode == NovelReadMode.VERTICAL_SCROLL) {
                val layoutInfo = verticalListState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems <= 1) 0
                else {
                    val index = verticalListState.firstVisibleItemIndex
                    val offset = verticalListState.firstVisibleItemScrollOffset
                    val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1000
                    val fraction = (index.toFloat() + (offset.toFloat() / itemSize.coerceAtLeast(1))) / (totalItems.toFloat())
                    (fraction * 100).toInt().coerceIn(0, 100)
                }
            } else {
                if (chapters.isEmpty()) 0
                else (((currentChapterIndex + 1).toFloat() / chapters.size.toFloat()) * 100).toInt().coerceIn(0, 100)
            }
        }
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

            // ── Vertical Scroll Mode ────────────────────────────────────────────────
            if (readMode == NovelReadMode.VERTICAL_SCROLL) {
                LaunchedEffect(currentChapterIndex) {
                    verticalListState.scrollToItem(0)
                    saveProgress(currentChapterIndex)
                }

                LazyColumn(
                    state = verticalListState,
                    contentPadding = PaddingValues(
                        top = if (isControlsVisible) 80.dp else 44.dp,
                        bottom = 120.dp,
                        start = horizontalMarginDp.dp,
                        end = horizontalMarginDp.dp
                    ),
                    modifier = Modifier.fillMaxSize()
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
                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    item(key = "ch_content_$currentChapterIndex") {
                        val paragraphs = remember(activeChapter?.content) {
                            val raw = activeChapter?.content ?: ""
                            if (raw.contains("\n\n") || raw.contains("\r\n\r\n")) {
                                raw.split(Regex("""(?:\r?\n\s*){2,}"""))
                            } else {
                                raw.split(Regex("""\r?\n"""))
                            }
                        }

                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp.dp)) {
                                paragraphs.forEach { paragraph ->
                                    if (paragraph.isNotBlank()) {
                                        Text(
                                            text = paragraph.trim(),
                                            color = currentTheme.text,
                                            fontSize = fontSizeSp.sp,
                                            fontFamily = resolvedFont,
                                            lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                                            textAlign = textAlign,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
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
                                    Spacer(Modifier.width(6.dp))
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
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
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
                                        Text(
                                            text = p.trim(),
                                            color = currentTheme.text,
                                            fontSize = fontSizeSp.sp,
                                            fontFamily = resolvedFont,
                                            lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                                            textAlign = textAlign,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(40.dp))
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

                // Reading Progress Pill (bottom-right 53% indicator from screenshot 14.32.09)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141418).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isControlsVisible = true
                        }
                ) {
                    Text(
                        text = "$scrollProgressPercent%",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
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

                    // 2. TOP HUD — Minimalist: Back button + Title/Chapter only
                    Row(
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
                            .padding(start = 12.dp, end = 70.dp, top = 12.dp, bottom = 28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(44.dp)
                                .background(currentTheme.surface.copy(alpha = 0.90f), CircleShape)
                                .border(BorderStroke(1.dp, currentTheme.border.copy(alpha = 0.5f)), CircleShape)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = currentTheme.text,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = novelBook?.title ?: mangaEntity?.title ?: "Novel Reader",
                                color = currentTheme.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeChapter?.title != null) {
                                Text(
                                    text = activeChapter.title,
                                    color = currentTheme.secondaryText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 3. Right Side Settings Pill (Without bookmark)
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
                        onShowToc = { showTocSheet = true; activeSettingTab = null },
                        glowColor = currentTheme.accent,
                        modifier = Modifier.fillMaxSize()
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

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search chapter…", color = currentTheme.secondaryText) },
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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
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
                                showTocSheet = false
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
}
