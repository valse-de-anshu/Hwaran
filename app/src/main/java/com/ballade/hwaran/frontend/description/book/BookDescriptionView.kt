package com.ballade.hwaran.frontend.description.book

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
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
import com.ballade.hwaran.core.metadata.EntryMetadata
import com.ballade.hwaran.core.metadata.MasterTagItem
import com.ballade.hwaran.core.util.CoverArtResolver

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDescriptionView(
    manga: MangaEntity,
    chapters: List<ChapterEntity>,
    entryMetadata: EntryMetadata,
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
    onOpenChapters: () -> Unit,
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
    val isSingleFileBook = chapters.size <= 1

    var isSynopsisExpanded by remember { mutableStateOf(false) }

    val CardBg = MaterialTheme.colorScheme.surface
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val PrimaryPurple = MaterialTheme.colorScheme.primary

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gestureBottom = WindowInsets.systemGestures.asPaddingValues().calculateBottomPadding()
    val bottomFloatingClearance = maxOf(navBarBottom + 20.dp, maxOf(gestureBottom + 16.dp, 36.dp))

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
            // Generous Top Spacer ensuring full clearance below camera punch hole/notch
            item {
                Spacer(modifier = Modifier.statusBarsPadding().displayCutoutPadding().height(92.dp))
            }

            // ── Header Card (Cover Art + Title + Metadata) ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cover Art
                    Box(
                        modifier = Modifier
                            .width(115.dp)
                            .aspectRatio(0.68f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBg)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .clickable(enabled = isEditMode, onClick = onPickCover)
                    ) {
                        val activeCover = if (isEditMode) (draftCover ?: manga.coverPath) else manga.coverPath
                        val coverModel = remember(activeCover, manga.parentUri) {
                            CoverArtResolver.resolveCoverModel(activeCover, manga.parentUri, null, context)
                        }

                        if (coverModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(coverModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = manga.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Book,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        if (isEditMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CameraAlt,
                                        contentDescription = "Change Cover",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Change",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Title & Quick Details
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Material / Format Pill
                        val primaryPills = listOf("Book", "Manhua", "Manga", "Series", "Channel")
                        val currentActiveTag = if (isEditMode) draftMaterialTag else "Book"

                        if (isEditMode) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(primaryPills) { pill ->
                                    val isSelected = pill.equals(currentActiveTag, ignoreCase = true)
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onSetMaterialTag(pill) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) PrimaryPurple.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, if (isSelected) PrimaryPurple else Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Text(
                                            text = pill,
                                            color = if (isSelected) Color.White else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimaryPurple.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Book / Novel",
                                    color = PrimaryPurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Title
                        if (isEditMode) {
                            OutlinedTextField(
                                value = draftTitle,
                                onValueChange = onUpdateDraftTitle,
                                placeholder = { Text("Book / Novel Title", color = TextMuted, fontSize = 16.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryPurple,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        } else {
                            Text(
                                text = manga.title,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Author / Publisher Subtitle
                        val author = if (isEditMode) draftAuthor else entryMetadata.author
                        if (author.isNotBlank()) {
                            Text(
                                text = "By $author",
                                color = TextMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Single file vs Chapters badge
                        Text(
                            text = if (isSingleFileBook) "Single Document / Volume" else "${chapters.size} Chapters available",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // ── Tags Section (Website Style with ✕ cut buttons) ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Tags",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = "${assignedTags.size}",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (!isTagSearchVisible && !isEditMode) {
                            TextButton(
                                onClick = onToggleTagSearchVisible,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Add Tag", color = PrimaryPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Flow of Assigned Tags
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        assignedTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    IconButton(
                                        onClick = { onRemoveTag(tag) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove $tag",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (assignedTags.isEmpty()) {
                            Text(
                                text = "No tags assigned. Tap '+ Add Tag' to search master tags.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Tag Searcher Panel
                    AnimatedVisibility(
                        visible = isTagSearchVisible || isEditMode,
                        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                OutlinedTextField(
                                    value = tagQuery,
                                    onValueChange = onSetTagQuery,
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Search tags (Fiction, Mystery, Sci-Fi...)", color = TextMuted, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Search, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        if (tagQuery.isNotEmpty()) {
                                            IconButton(onClick = { onSetTagQuery("") }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryPurple,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    tagSuggestions.forEach { item ->
                                        val isAssigned = assignedTags.any { it.equals(item.tag, ignoreCase = true) }
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    if (isAssigned) onRemoveTag(item.tag)
                                                    else onAddTag(item.tag)
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isAssigned) PrimaryPurple.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                                            border = BorderStroke(1.dp, if (isAssigned) PrimaryPurple else Color.White.copy(alpha = 0.12f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = item.tag,
                                                    color = if (isAssigned) TextMuted else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isAssigned) FontWeight.Normal else FontWeight.SemiBold
                                                )
                                                if (isAssigned) {
                                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                                } else {
                                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }

                                    if (tagQuery.isNotBlank() && !tagSuggestions.any { it.tag.equals(tagQuery.trim(), ignoreCase = true) }) {
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onAddTag(tagQuery.trim())
                                                    onSetTagQuery("")
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.6f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                                                Text(
                                                    text = "Add \"${tagQuery.trim()}\"",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
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

            // ── Synopsis Section (Description Card) ──
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Synopsis",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (isEditMode) {
                            OutlinedTextField(
                                value = draftDesc,
                                onValueChange = onUpdateDraftDesc,
                                placeholder = { Text("Enter book synopsis / description...", color = TextMuted, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryPurple,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            val descriptionText = manga.description.ifBlank { "No description added yet." }
                            val isLong = descriptionText.length > 160

                            Text(
                                text = descriptionText,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = if (!isSynopsisExpanded && isLong) 4 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isLong) {
                                Row(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { isSynopsisExpanded = !isSynopsisExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isSynopsisExpanded) "Show less" else "Read more",
                                        color = PrimaryPurple,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = if (isSynopsisExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Action Row (Read, Chapters, Bookmark) - Hidden during Edit Mode ──
            if (!isEditMode) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onNavigateToMedia(manga.id, 1) },
                            modifier = Modifier
                                .weight(if (isSingleFileBook) 1f else 1.2f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (manga.lastReadPage != null && manga.lastReadPage!! > 1) "Resume (p. ${manga.lastReadPage})"
                                           else if (manga.lastReadTitle != null) "Resume" else "Read",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!isSingleFileBook) {
                            FilledTonalButton(
                                onClick = onOpenChapters,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CardBg,
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Chapters (${chapters.size})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        val isFavorite = draftIsFavorite || manga.isFavorite

                        OutlinedButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFavorite) PrimaryPurple.copy(alpha = 0.18f) else Color.Transparent
                            ),
                            border = BorderStroke(1.dp, if (isFavorite) PrimaryPurple else Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) PrimaryPurple else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // ── Additional Information Grid ──
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Additional Information",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                BookMetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Author",
                                    value = if (isEditMode) draftAuthor else entryMetadata.author.ifBlank { "Unknown" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftAuthor
                                )
                                BookMetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Publisher",
                                    value = if (isEditMode) draftPublisher else entryMetadata.publisher.ifBlank { "Unknown" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftPublisher
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                BookMetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Published",
                                    value = if (isEditMode) draftYear else entryMetadata.year.ifBlank { "Unknown" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftYear
                                )
                                BookMetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Language",
                                    value = if (isEditMode) draftLanguage else entryMetadata.language.ifBlank { "English" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftLanguage
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                BookMetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Status",
                                    value = if (isEditMode) draftStatus else entryMetadata.status.ifBlank { "Completed" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftStatus
                                )
                                BookMetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Pages",
                                    value = if (isEditMode) draftPages else entryMetadata.pages.ifBlank { "—" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftPages
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Top Bar (Safely below camera punch hole/cutout) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 28.dp, start = 18.dp, end = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable { onNavigateBack() },
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

            var showMoreMenu by remember { mutableStateOf(false) }

            if (!isEditMode) {
                Box {
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { showMoreMenu = true },
                        shape = CircleShape,
                        color = Color(0xFF14131E).copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More Options",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Information") },
                            onClick = {
                                showMoreMenu = false
                                onToggleEditMode()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change Cover") },
                            onClick = {
                                showMoreMenu = false
                                onPickCover()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Book", color = Color(0xFFE57373)) },
                            onClick = {
                                showMoreMenu = false
                                onDeleteManga()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFE57373)
                                )
                            }
                        )
                    }
                }
            }
        }

        // ── Floating Bottom Save & Cancel Capsule for Edit Mode ──
        if (isEditMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomFloatingClearance)
                    .padding(horizontal = 24.dp)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp), spotColor = PrimaryPurple.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(32.dp),
                color = CardBg,
                border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onToggleEditMode,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Cancel", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            onSaveManga()
                            onToggleEditMode()
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Changes", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookMetadataItemView(
    label: String,
    value: String,
    isEditMode: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val PrimaryPurple = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (isEditMode) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            )
        } else {
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
