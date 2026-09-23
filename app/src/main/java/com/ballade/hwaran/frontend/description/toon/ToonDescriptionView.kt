package com.ballade.hwaran.frontend.description.toon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenu
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.ClickableText
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
fun ToonDescriptionView(
    manga: MangaEntity,
    chapters: List<ChapterEntity>,
    entryMetadata: EntryMetadata,
    assignedTags: List<String>,
    isEditMode: Boolean,
    tagQuery: String,
    tagSuggestions: List<MasterTagItem>,
    isTagSearchVisible: Boolean,
    draftTitle: String,
    draftAltTitle: String,
    draftAuthor: String,
    draftArtist: String,
    draftPublisher: String,
    draftSerialization: String,
    draftYear: String,
    draftStatus: String,
    draftRating: String,
    draftLanguage: String,
    draftPages: String,
    draftMaterialTag: String,
    draftIsFavorite: Boolean,
    draftDesc: String,
    draftCover: String,
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
    onUpdateDraftAltTitle: (String) -> Unit,
    onUpdateDraftAuthor: (String) -> Unit,
    onUpdateDraftArtist: (String) -> Unit,
    onUpdateDraftPublisher: (String) -> Unit,
    onUpdateDraftSerialization: (String) -> Unit,
    onUpdateDraftYear: (String) -> Unit,
    onUpdateDraftStatus: (String) -> Unit,
    onUpdateDraftRating: (String) -> Unit,
    onUpdateDraftLanguage: (String) -> Unit,
    onUpdateDraftPages: (String) -> Unit,
    onUpdateDraftDesc: (String) -> Unit,
    onDeleteManga: () -> Unit
) {
    val context = LocalContext.current
    val CardBg = MaterialTheme.colorScheme.surface
    val PrimaryPurple = MaterialTheme.colorScheme.primary
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant

    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var isTagsExpanded by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val resolvedCoverModel = remember(manga.coverPath, draftCover, isEditMode) {
        val path = if (isEditMode && draftCover.isNotBlank()) draftCover else manga.coverPath
        CoverArtResolver.resolveCoverModel(path, manga.parentUri, chapters, context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. Scrollable Description & Metadata Content ──
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Generous Top Spacer ensuring full clearance below camera punch hole/notch
            item {
                Spacer(modifier = Modifier.statusBarsPadding().displayCutoutPadding().height(120.dp))
            }

            // ── Hero Media Card (Flexible for long titles) ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 2:3 Cover Art Card with luxury glass border
                    Box(
                        modifier = Modifier
                            .width(136.dp)
                            .height(196.dp)
                            .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.6f))
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardBg)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(18.dp))
                            .clickable(enabled = isEditMode) { onPickCover() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (resolvedCoverModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(resolvedCoverModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Cover Art",
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
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        if (isEditMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Image,
                                        contentDescription = "Change Cover",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
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

                    // Metadata Column (Flexible wrapping for big titles)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tag 1: Material Type Pill (Book / Manhua / Manga / Series / Channel) & Favorite Badge
                        val effectiveType = if (isEditMode) draftMaterialTag else {
                            entryMetadata.type.ifBlank {
                                when {
                                    manga.contentType == 4 || manga.boxPurpose == "novel" -> "Novel"
                                    manga.contentType == 1 || manga.boxPurpose == "book" -> "Book"
                                    (manga.contentType == 2 && manga.boxPurpose == "channel") -> "Channel"
                                    manga.contentType == 2 -> "Series"
                                    manga.contentType == 0 && (manga.boxPurpose == "manhua" || manga.genre?.contains("manhua", ignoreCase = true) == true || manga.genre?.contains("manhwa", ignoreCase = true) == true) -> "Manhua"
                                    else -> "Manga"
                                }
                            }
                        }

                        if (isEditMode) {
                            val availableOptions = listOf("Book", "Novel", "Manhua", "Manga", "Series", "Channel")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableOptions.forEach { typeOption ->
                                    val isSel = effectiveType.equals(typeOption, ignoreCase = true)
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onSetMaterialTag(typeOption) },
                                        color = if (isSel) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
                                        border = BorderStroke(1.dp, if (isSel) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = typeOption,
                                            color = if (isSel) Color(0xFFE6E8EC) else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF222631),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE6E8EC))
                                        )
                                        Text(
                                            text = effectiveType.uppercase(),
                                            color = Color(0xFFE6E8EC),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                }

                                if (draftIsFavorite || manga.isFavorite) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFFB800).copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, Color(0xFFFFB800).copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Star,
                                                contentDescription = null,
                                                tint = Color(0xFFFFB800),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "FAVORITE",
                                                color = Color(0xFFFFB800),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Title (Unrestricted lines for big names)
                        if (isEditMode) {
                            TextField(
                                value = draftTitle,
                                onValueChange = onUpdateDraftTitle,
                                placeholder = { Text(if (manga.contentType == 1) "Book / Novel Title" else "Manga Title", color = TextMuted, fontSize = 16.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = PrimaryPurple,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = manga.title,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp
                            )
                        }

                        // Alternative / Native Title
                        val alt = if (isEditMode) draftAltTitle else entryMetadata.altTitle
                        if (isEditMode) {
                            TextField(
                                value = draftAltTitle,
                                onValueChange = onUpdateDraftAltTitle,
                                placeholder = { Text("Alt / Native Title", color = TextMuted, fontSize = 12.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = PrimaryPurple,
                                    focusedTextColor = TextMuted,
                                    unfocusedTextColor = TextMuted
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (alt.isNotBlank()) {
                            Text(
                                text = alt,
                                color = TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            )
                        }

                        // Status & Rating Line
                        val status = if (isEditMode) draftStatus else entryMetadata.status.ifBlank { "Ongoing" }
                        val rating = if (isEditMode) draftRating else entryMetadata.rating.ifBlank { "8.7 (152K)" }

                        if (isEditMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextField(
                                    value = draftStatus,
                                    onValueChange = onUpdateDraftStatus,
                                    placeholder = { Text("Status", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                TextField(
                                    value = draftRating,
                                    onValueChange = onUpdateDraftRating,
                                    placeholder = { Text("Rating", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB800),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = rating,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("•", color = TextMuted, fontSize = 12.sp)
                                Text(
                                    text = status,
                                    color = if (status.contains("ongoing", ignoreCase = true)) Color(0xFF4CAF50) else PrimaryPurple,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Chapters Count Badge
                        Text(
                            text = "${chapters.size} Chapters available",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // ── Tags Section (Tag 2 - Website Style with ✕ cut buttons) ──
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

                        // Summon Tag Searcher button
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleTagSearchVisible() },
                            color = if (isTagSearchVisible) PrimaryPurple.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, if (isTagSearchVisible) PrimaryPurple else Color.White.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTagSearchVisible) Icons.Rounded.Close else Icons.Rounded.Add,
                                    contentDescription = "Search Tags",
                                    tint = if (isTagSearchVisible) PrimaryPurple else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isTagSearchVisible) "Close Search" else "Add Tag",
                                    color = if (isTagSearchVisible) PrimaryPurple else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Assigned Tags FlowRow
                    val maxInitialTags = 6
                    val displayTags = if (isEditMode || isTagsExpanded || assignedTags.size <= maxInitialTags) {
                        assignedTags
                    } else {
                        assignedTags.take(maxInitialTags)
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        displayTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CardBg.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        start = 10.dp,
                                        end = if (isEditMode) 4.dp else 10.dp,
                                        top = 5.dp,
                                        bottom = 5.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    // ✕ Button ONLY in edit mode
                                    if (isEditMode) {
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
                        }

                        // Expand / Collapse Chevron Pill when more than 6 tags
                        if (!isEditMode && assignedTags.size > maxInitialTags) {
                            Surface(
                                onClick = { isTagsExpanded = !isTagsExpanded },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (isTagsExpanded) "Less" else "+${assignedTags.size - maxInitialTags}",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = if (isTagsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = if (isTagsExpanded) "Show fewer tags" else "Show all tags",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(16.dp)
                                    )
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

                    // Tag Searcher Panel (Summoned via + Add Tag)
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
                                    placeholder = { Text("Search 4,000+ tags (Action, Isekai, Romance...)", color = TextMuted, fontSize = 12.sp) },
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

                                Text(
                                    text = if (tagQuery.isBlank()) "Popular Master Tags" else "Matching Master Tags",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

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
                                                .clickable(enabled = !isAssigned) {
                                                    onAddTag(item.tag)
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isAssigned) Color.White.copy(alpha = 0.05f) else PrimaryPurple.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, if (isAssigned) Color.White.copy(alpha = 0.08f) else PrimaryPurple.copy(alpha = 0.5f))
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

                                    // Add custom typed tag
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
                                placeholder = { Text("Enter manga synopsis / description...", color = TextMuted, fontSize = 13.sp) },
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
                            val descriptionText = entryMetadata.description.ifBlank {
                                manga.description.ifBlank { "No description added yet." }
                            }
                            val isLong = descriptionText.length > 160

                            // Build AnnotatedString with clickable URL spans
                            val urlRegex = Regex("""(https?://\S+|www\.\S+)""")
                            val annotated = buildAnnotatedString {
                                var lastEnd = 0
                                for (match in urlRegex.findAll(descriptionText)) {
                                    // Append text before the URL
                                    if (match.range.first > lastEnd) {
                                        append(descriptionText.substring(lastEnd, match.range.first))
                                    }
                                    // Append the URL with styling and annotation
                                    val url = match.value
                                    val fullUrl = if (url.startsWith("www.")) "https://$url" else url
                                    pushStringAnnotation(tag = "URL", annotation = fullUrl)
                                    withStyle(style = SpanStyle(
                                        color = Color(0xFF64B5F6),
                                        textDecoration = TextDecoration.Underline
                                    )) {
                                        append(url)
                                    }
                                    pop()
                                    lastEnd = match.range.last + 1
                                }
                                if (lastEnd < descriptionText.length) {
                                    append(descriptionText.substring(lastEnd))
                                }
                            }

                            ClickableText(
                                text = annotated,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                ),
                                maxLines = if (!isSynopsisExpanded && isLong) 4 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis,
                                onClick = { offset ->
                                    annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                        .firstOrNull()?.let { annotation ->
                                            try {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(annotation.item)
                                                )
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                }
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

            // ── Action Row (Read, Chapters Window, Bookmark) - Hidden during Edit Mode ──
            if (!isEditMode) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Primary "Read" Button -> Opens chapter 1 / resume chapter
                        Button(
                            onClick = {
                                val targetChapter = chapters.firstOrNull { it.title == manga.lastReadTitle }
                                    ?: chapters.firstOrNull()
                                if (targetChapter != null) {
                                    onNavigateToMedia(targetChapter.id, manga.contentType)
                                }
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF222631),
                                contentColor = Color(0xFFE6E8EC)
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFFE6E8EC),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (manga.lastReadTitle != null) "Resume" else "Read",
                                    color = Color(0xFFE6E8EC),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // "Chapters" Button
                        FilledTonalButton(
                            onClick = onOpenChapters,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.04f),
                                contentColor = Color(0xFFE6E8EC)
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFFE6E8EC),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Chapters (${chapters.size})",
                                    color = Color(0xFFE6E8EC),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Bookmark / In Library Button -> Connected with Favorite
                        val isFavorite = draftIsFavorite || manga.isFavorite

                        OutlinedButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFavorite) Color(0xFF222631) else Color.Transparent
                            ),
                            border = BorderStroke(1.dp, if (isFavorite) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // ── Source URL Card (shown if URL is present in metadata) ──
            val toonUrl = entryMetadata.url
            if (toonUrl.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(toonUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = "Open Source URL",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = toonUrl,
                                color = Color(0xFF64B5F6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = Color(0xFF64B5F6).copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ── Online Statistics Row (Views / Likes / Comments) ──
            val statsViews = entryMetadata.views
            val statsLikes = entryMetadata.likes
            val statsComments = entryMetadata.comments
            if (statsViews.isNotBlank() || statsLikes.isNotBlank() || statsComments.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (statsViews.isNotBlank()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = "Views",
                                        tint = Color(0xFF90CAF9),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = statsViews,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Views",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            if (statsLikes.isNotBlank()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.ThumbUp,
                                        contentDescription = "Likes",
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = statsLikes,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Likes",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            if (statsComments.isNotBlank()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubbleOutline,
                                        contentDescription = "Comments",
                                        tint = Color(0xFF81C784),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = statsComments,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Comments",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
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

                        // 2-Column Key-Value Grid
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                MetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Author",
                                    value = if (isEditMode) draftAuthor else entryMetadata.author.ifBlank { "Unknown" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftAuthor
                                )
                                MetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Artist",
                                    value = if (isEditMode) draftArtist else entryMetadata.artist.ifBlank { "Unknown" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftArtist
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                MetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Year",
                                    value = if (isEditMode) draftYear else entryMetadata.year.ifBlank { "Unknown" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftYear
                                )
                                MetadataItemView(
                                    modifier = Modifier.weight(1f),
                                    label = "Status",
                                    value = if (isEditMode) draftStatus else entryMetadata.status.ifBlank { "Ongoing" },
                                    isEditMode = isEditMode,
                                    onValueChange = onUpdateDraftStatus
                                )
                            }
                        }
                    }
                }
            }

            // Bottom clearance
            item {
                Spacer(modifier = Modifier.height(if (isEditMode) 200.dp else 48.dp))
            }
        }

        // ── 2. Top Bar (Positioned well below camera cutout/notch, hidden during edit mode) ──
        if (!isEditMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(top = 36.dp, start = 18.dp, end = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minimal Frosted Back Button
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { onNavigateBack() },
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

                // 3-Dot More Menu Button (Re-activated, Pencil removed)
                Box {
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { showMoreMenu = true },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
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

                    HwaranDropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        HwaranDropdownMenuItem(
                            text = "Edit Description",
                            onClick = {
                                showMoreMenu = false
                                onToggleEditMode()
                            },
                            leadingIcon = Icons.Rounded.Edit
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                        HwaranDropdownMenuItem(
                            text = "Delete Entry",
                            onClick = {
                                showMoreMenu = false
                                onDeleteManga()
                            },
                            leadingIcon = Icons.Rounded.Delete,
                            isDanger = true
                        )
                    }
                }
            }
        }

        // ── 3. Luxury Floating Save Capsule (Visible when in Edit Mode) ──
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        val tappableBottom = WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding()
        val gestureBottom = WindowInsets.systemGestures.asPaddingValues().calculateBottomPadding()
        val systemBottomInsets = maxOf(navBarBottom, maxOf(safeBottom, maxOf(tappableBottom, gestureBottom)))
        val floatingCapsuleBottomPadding = maxOf(systemBottomInsets + 40.dp, 96.dp)

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = floatingCapsuleBottomPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isEditMode,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF16131F).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tactile Frosted Cancel Pill
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onToggleEditMode() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cancel",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Cancel",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Obsidian Dark Glass Save Changes Pill
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF222631),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onSaveManga() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color(0xFFE6E8EC),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Save Changes",
                                color = Color(0xFFE6E8EC),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MetadataItemView(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isEditMode: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(
            text = label.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (isEditMode) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        } else {
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
