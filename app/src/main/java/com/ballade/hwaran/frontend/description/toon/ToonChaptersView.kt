package com.ballade.hwaran.frontend.description.toon

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.CoverArtResolver
import com.ballade.hwaran.ui.components.DeleteConfirmationDialog

enum class ChapterSortOption(val displayName: String, val subtitle: String) {
    FIRST_TO_LAST("First to Last", "Ch. 1 → Latest"),
    LAST_TO_FIRST("Last to First", "Latest → Ch. 1"),
    TITLE_AZ("Title (A to Z)", "Alphabetical order"),
    TITLE_ZA("Title (Z to A)", "Reverse alphabetical"),
    MOST_READ("Most Read", "Highest views / opens"),
    SHUFFLE("Shuffle", "Random order")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToonChaptersView(
    manga: MangaEntity,
    chapters: List<ChapterEntity>,
    onNavigateBack: () -> Unit,
    onNavigateToChapter: (Long) -> Unit,
    onPickChaptersFolder: () -> Unit,
    onDeleteChapters: (List<Long>, Boolean) -> Unit
) {
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedChapterIds = remember { mutableStateListOf<Long>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(ChapterSortOption.FIRST_TO_LAST) }
    var showSortSheet by remember { mutableStateOf(false) }

    BackHandler {
        if (isDeleteMode) {
            isDeleteMode = false
            selectedChapterIds.clear()
        } else if (isSearchExpanded) {
            isSearchExpanded = false
            searchQuery = ""
        } else {
            onNavigateBack()
        }
    }

    val context = LocalContext.current
    val CardBg = MaterialTheme.colorScheme.surface
    val PrimaryPurple = MaterialTheme.colorScheme.primary
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val DangerRed = Color(0xFFE57373)

    // Sort chapters based on selected option
    val filteredAndSortedChapters = remember(chapters, searchQuery, selectedSort) {
        val filtered = if (searchQuery.isBlank()) {
            chapters
        } else {
            val q = searchQuery.trim().lowercase()
            chapters.filter { it.title.lowercase().contains(q) }
        }

        when (selectedSort) {
            ChapterSortOption.FIRST_TO_LAST -> filtered.sortedWith(compareBy {
                Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloatOrNull() ?: Float.MAX_VALUE
            })
            ChapterSortOption.LAST_TO_FIRST -> filtered.sortedWith(compareByDescending {
                Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloatOrNull() ?: Float.MIN_VALUE
            })
            ChapterSortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            ChapterSortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
            ChapterSortOption.MOST_READ -> filtered.sortedByDescending { it.openCount }
            ChapterSortOption.SHUFFLE -> filtered.shuffled()
        }
    }

    val scrollState = rememberLazyListState()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 36.dp, start = 20.dp, end = 20.dp)
        ) {
            // ── Clean Luxury Header Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isDeleteMode) {
                                isDeleteMode = false
                                selectedChapterIds.clear()
                            } else {
                                onNavigateBack()
                            }
                        },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
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

                // Action Row (Search, Sort, Delete, Add)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search Button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) searchQuery = ""
                            },
                        shape = CircleShape,
                        color = if (isSearchExpanded) PrimaryPurple.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isSearchExpanded) PrimaryPurple else Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = if (isSearchExpanded) PrimaryPurple else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Sort Button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { showSortSheet = true },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Sort Options",
                                tint = PrimaryPurple,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Delete Button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (isDeleteMode) {
                                    if (selectedChapterIds.isNotEmpty()) {
                                        showDeleteConfirmDialog = true
                                    } else {
                                        isDeleteMode = false
                                    }
                                } else {
                                    isDeleteMode = true
                                    selectedChapterIds.clear()
                                }
                            },
                        shape = CircleShape,
                        color = if (isDeleteMode) DangerRed.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isDeleteMode) DangerRed else Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDeleteMode && selectedChapterIds.isNotEmpty()) Icons.Rounded.DeleteSweep else Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete",
                                tint = if (isDeleteMode) DangerRed else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (!isDeleteMode) {
                        // Add Chapters Button
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { onPickChaptersFolder() },
                            shape = CircleShape,
                            color = PrimaryPurple.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.5f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add",
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        // Select / Deselect All Button
                        TextButton(
                            onClick = {
                                if (selectedChapterIds.size == filteredAndSortedChapters.size) {
                                    selectedChapterIds.clear()
                                } else {
                                    selectedChapterIds.clear()
                                    selectedChapterIds.addAll(filteredAndSortedChapters.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedChapterIds.size == filteredAndSortedChapters.size) "Clear" else "All",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Title & Status Row (Clean & Spacious) ──
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    text = if (isDeleteMode) "Select chapters to delete" else manga.title,
                    color = if (isDeleteMode) DangerRed else Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isDeleteMode) "${selectedChapterIds.size} of ${filteredAndSortedChapters.size} selected" else "${filteredAndSortedChapters.size} Chapters",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    Text("•", color = TextMuted, fontSize = 12.sp)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryPurple.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = selectedSort.displayName,
                            color = PrimaryPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // ── Expandable Search Bar (Appears only when search icon is active) ──
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    placeholder = { Text("Filter chapters...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            // ── Chapter Items List ──
            if (filteredAndSortedChapters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No chapters matching \"$searchQuery\"" else "No chapters found",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredAndSortedChapters, key = { it.id }) { chapter ->
                        val isLastRead = manga.lastReadTitle == chapter.title
                        val isSelected = selectedChapterIds.contains(chapter.id)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (isDeleteMode) {
                                        if (isSelected) selectedChapterIds.remove(chapter.id)
                                        else selectedChapterIds.add(chapter.id)
                                    } else {
                                        onNavigateToChapter(chapter.id)
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) DangerRed.copy(alpha = 0.12f) else CardBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DangerRed.copy(alpha = 0.6f)
                                else if (isLastRead) PrimaryPurple.copy(alpha = 0.5f)
                                else Color.White.copy(alpha = 0.07f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (isDeleteMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedChapterIds.add(chapter.id)
                                            else selectedChapterIds.remove(chapter.id)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = DangerRed,
                                            uncheckedColor = Color.White.copy(alpha = 0.4f)
                                        )
                                    )
                                }

                                // Thumbnail / Cover preview
                                Box(
                                    modifier = Modifier
                                        .size(48.dp, 64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.05f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val thumbModel = remember(chapter.thumbnailUri, manga.coverPath) {
                                        chapter.thumbnailUri?.let { CoverArtResolver.resolveCoverModel(it) }
                                            ?: CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri)
                                    }
                                    if (thumbModel != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(thumbModel)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Title and Info
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isLastRead) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = PrimaryPurple.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "LAST READ",
                                                    color = PrimaryPurple,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (chapter.openCount > 0) {
                                            Text(
                                                text = "Read ${chapter.openCount}x",
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // Play / Read Action Button
                                if (!isDeleteMode) {
                                    Surface(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .clickable { onNavigateToChapter(chapter.id) },
                                        shape = CircleShape,
                                        color = PrimaryPurple.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.4f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Read",
                                                tint = PrimaryPurple,
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

        // ── Delete Confirmation Dialog ──
        if (showDeleteConfirmDialog) {
            DeleteConfirmationDialog(
                title = "Delete ${selectedChapterIds.size} Chapter${if (selectedChapterIds.size == 1) "" else "s"}",
                message = "Choose how you would like to remove the selected chapter(s):",
                onDismiss = { showDeleteConfirmDialog = false },
                onRemoveFromApp = {
                    showDeleteConfirmDialog = false
                    onDeleteChapters(selectedChapterIds.toList(), false)
                    selectedChapterIds.clear()
                    isDeleteMode = false
                },
                onDeleteFromDisk = {
                    showDeleteConfirmDialog = false
                    onDeleteChapters(selectedChapterIds.toList(), true)
                    selectedChapterIds.clear()
                    isDeleteMode = false
                }
            )
        }

        // ── Luxury Sort Bottom Sheet ──
        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                containerColor = CardBg,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 36.dp)
                ) {
                    Text(
                        text = "Sort Order",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    ChapterSortOption.values().forEach { option ->
                        val isSelected = selectedSort == option
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    selectedSort = option
                                    showSortSheet = false
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) PrimaryPurple.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryPurple.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = option.displayName,
                                        color = if (isSelected) PrimaryPurple else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = option.subtitle,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
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
