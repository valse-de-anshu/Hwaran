package com.ballade.hwaran.frontend.description.novel

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.backend.novel.NovelBook
import com.ballade.hwaran.backend.novel.NovelParser
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.metadata.EntryMetadata
import com.ballade.hwaran.core.metadata.MasterTagItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelDescriptionView(
    manga: MangaEntity,
    chapters: List<ChapterEntity>,
    entryMetadata: EntryMetadata?,
    assignedTags: List<String>,
    isEditMode: Boolean,
    tagQuery: String,
    tagSuggestions: List<MasterTagItem>,
    isTagSearchVisible: Boolean,
    draftTitle: String,
    draftAuthor: String,
    draftPublisher: String,
    draftYear: String,
    draftStatus: String,
    draftLanguage: String,
    draftPages: String,
    draftMaterialTag: String,
    draftIsFavorite: Boolean,
    draftDesc: String,
    draftCover: String?,
    scrollState: LazyListState,
    onToggleEditMode: () -> Unit,
    onSaveManga: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToMedia: (Long, Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onSetTagQuery: (String) -> Unit,
    onToggleTagSearchVisible: () -> Unit,
    onPickCover: () -> Unit,
    onSetMaterialTag: (String) -> Unit,
    onUpdateDraftTitle: (String) -> Unit,
    onUpdateDraftAuthor: (String) -> Unit,
    onUpdateDraftPublisher: (String) -> Unit,
    onUpdateDraftYear: (String) -> Unit,
    onUpdateDraftStatus: (String) -> Unit,
    onUpdateDraftLanguage: (String) -> Unit,
    onUpdateDraftPages: (String) -> Unit,
    onUpdateDraftDesc: (String) -> Unit,
    onDeleteManga: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var parsedNovel by remember { mutableStateOf<NovelBook?>(null) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var chapterSearchQuery by remember { mutableStateOf("") }
    var isAscendingSort by remember { mutableStateOf(true) }

    val CardBg = MaterialTheme.colorScheme.surface
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gestureBottom = WindowInsets.systemGestures.asPaddingValues().calculateBottomPadding()
    val bottomFloatingClearance = maxOf(navBarBottom + 20.dp, maxOf(gestureBottom + 16.dp, 36.dp))

    // Parse novel chapters in background
    LaunchedEffect(manga.id, manga.parentUri) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(manga.parentUri)
                val book = if (manga.parentUri.startsWith("file://") || manga.parentUri.startsWith("/")) {
                    val file = if (manga.parentUri.startsWith("file://")) File(Uri.parse(manga.parentUri).path ?: "") else File(manga.parentUri)
                    if (file.exists()) NovelParser.parseNovelFromFile(file) else NovelParser.parseNovel(context, uri, manga.title)
                } else {
                    NovelParser.parseNovel(context, uri, manga.title)
                }
                parsedNovel = book
            } catch (_: Exception) {}
        }
    }

    val displayChapters = remember(parsedNovel, chapterSearchQuery, isAscendingSort) {
        val list = parsedNovel?.chapters ?: emptyList()
        val filtered = if (chapterSearchQuery.isBlank()) list else list.filter { it.title.contains(chapterSearchQuery, ignoreCase = true) }
        if (isAscendingSort) filtered else filtered.reversed()
    }

    val totalChaptersCount = parsedNovel?.chapters?.size ?: chapters.size
    val totalWords = parsedNovel?.totalWordCount ?: 0
    val lastReadChapterIndex = manga.lastReadPage ?: 0

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 0.dp,
                bottom = if (isEditMode) bottomFloatingClearance + 80.dp else bottomFloatingClearance + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.statusBarsPadding().displayCutoutPadding().height(92.dp))
            }

            // ── 1. Header Card (Cover Art + Title + Author + Stats) ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cover Art
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .aspectRatio(0.68f)
                            .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.6f))
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBg)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(16.dp))
                            .clickable(enabled = isEditMode) { onPickCover() }
                    ) {
                        val coverModel = if (isEditMode) (draftCover ?: manga.coverPath) else manga.coverPath
                        if (!coverModel.isNullOrEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(coverModel).crossfade(true).build(),
                                contentDescription = "Novel Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF1E1D2A), Color(0xFF121118))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        if (isEditMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = "Change Cover", tint = Color.White)
                            }
                        }
                    }

                    // Metadata Details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                        ) {
                            Text(
                                text = "LIGHT NOVEL",
                                color = Color(0xFFE6E8EC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            text = if (isEditMode) draftTitle else manga.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        val authorName = if (isEditMode) draftAuthor else (entryMetadata?.author ?: parsedNovel?.author ?: "Unknown Author")
                        Text(
                            text = "By $authorName",
                            fontSize = 14.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Stats pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (totalChaptersCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.FormatListBulleted, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                    Text("$totalChaptersCount Chs", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                            if (totalWords > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.TextFields, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                    Text("${totalWords / 1000}k words", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. Primary Action: "Continue Reading" / "Start Reading" ──
            item {
                val hasStarted = manga.lastReadPage != null && manga.lastReadPage!! > 0
                val continueText = if (hasStarted) "Continue Chapter ${lastReadChapterIndex + 1}" else "Start Reading"

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToMedia(manga.id, 4)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF222631),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasStarted) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = Color(0xFFE6E8EC),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = continueText,
                            color = Color(0xFFE6E8EC),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── 3. Reading Progress Card ──
            if (totalChaptersCount > 0) {
                item {
                    val progressRatio = if (totalChaptersCount > 0) ((lastReadChapterIndex + 1).toFloat() / totalChaptersCount.toFloat()).coerceIn(0f, 1f) else 0f
                    val progressPercent = (progressRatio * 100).toInt()

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (manga.lastReadTitle != null) "Last: ${manga.lastReadTitle}" else "Reading Progress",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$progressPercent%",
                                    color = Color(0xFFE6E8EC),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { progressRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = Color(0xFFE6E8EC),
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }

            // ── 4. Synopsis / Description Card ──
            val synopsis = if (isEditMode) draftDesc else (entryMetadata?.description ?: parsedNovel?.description ?: manga.description)
            if (synopsis.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Synopsis", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = if (isSynopsisExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = synopsis,
                                color = TextMuted,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── 5. Chapter List Header with Search & Sort ──
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chapters ($totalChaptersCount)",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isAscendingSort = !isAscendingSort
                            }
                        ) {
                            Icon(
                                imageVector = if (isAscendingSort) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = "Sort order",
                                tint = Color(0xFFE6E8EC),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (totalChaptersCount > 5) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = chapterSearchQuery,
                            onValueChange = { chapterSearchQuery = it },
                            placeholder = { Text("Search chapters...", color = TextMuted, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = 0.35f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }

            // ── 6. Chapter Items ──
            itemsIndexed(displayChapters, key = { _, ch -> "ch_${ch.index}" }) { _, chapter ->
                val isCurrent = chapter.index == lastReadChapterIndex

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(context)
                            val currentManga = db.mediaDao().getMangaById(manga.id) ?: manga
                            db.mediaDao().insertManga(currentManga.copy(lastReadPage = chapter.index, lastReadTitle = chapter.title))
                            withContext(Dispatchers.Main) {
                                onNavigateToMedia(manga.id, 4)
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCurrent) Color(0xFF222631) else CardBg,
                    border = BorderStroke(1.dp, if (isCurrent) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${chapter.index + 1}",
                            color = if (isCurrent) Color(0xFFE6E8EC) else TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chapter.title,
                                color = if (isCurrent) Color(0xFFE6E8EC) else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chapter.wordCount > 0) {
                                Text(
                                    text = "${chapter.wordCount} words",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = if (isCurrent) Color(0xFFE6E8EC) else TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── Top Navigation Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = if (manga.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (manga.isFavorite) Color(0xFFFFD54F) else Color.White
                    )
                }

                IconButton(
                    onClick = onToggleEditMode,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                        contentDescription = "Edit",
                        tint = if (isEditMode) Color(0xFFE6E8EC) else Color.White
                    )
                }
            }
        }
    }
}
