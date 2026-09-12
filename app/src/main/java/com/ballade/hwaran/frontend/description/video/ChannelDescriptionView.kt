package com.ballade.hwaran.frontend.description.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun ChannelDescriptionView(
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
    draftMaterialTag: String,
    draftIsFavorite: Boolean,
    draftDesc: String,
    draftCover: String,
    scrollState: LazyListState,
    onToggleEditMode: () -> Unit,
    onSaveManga: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToMedia: (Long, Int) -> Unit,
    onOpenVideos: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onSetTagQuery: (String) -> Unit,
    onToggleTagSearchVisible: () -> Unit,
    onPickCover: () -> Unit,
    onPickVideos: () -> Unit,
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
    onUpdateDraftDesc: (String) -> Unit,
    onDeleteManga: () -> Unit
) {
    val context = LocalContext.current
    val CardBg = MaterialTheme.colorScheme.surface
    val PrimaryPurple = MaterialTheme.colorScheme.primary
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant

    var isAboutExpanded by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val resolvedCoverModel = remember(manga.coverPath, draftCover, isEditMode) {
        val path = if (isEditMode && draftCover.isNotBlank()) draftCover else manga.coverPath
        CoverArtResolver.resolveCoverModel(path, manga.parentUri, chapters, context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. Scrollable Channel Content ──
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Spacer ensuring clean clearance below camera punch hole/notch
            item {
                Spacer(modifier = Modifier.statusBarsPadding().displayCutoutPadding().height(64.dp))
            }

            // ── Hero Channel Profile Card ──
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Channel Circular Avatar with glowing border
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1A29))
                                .border(BorderStroke(2.dp, PrimaryPurple.copy(alpha = 0.6f)), CircleShape)
                                .shadow(8.dp, CircleShape)
                                .clickable(enabled = isEditMode) { onPickCover() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (resolvedCoverModel != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(resolvedCoverModel)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Channel Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AccountBox,
                                    contentDescription = null,
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(48.dp)
                                )
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
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CameraAlt,
                                            contentDescription = "Change Avatar",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = "Edit",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Tag 1: Material Type Pill (Channel) & Favorite Badge
                        val effectiveType = if (isEditMode) draftMaterialTag else {
                            entryMetadata.type.ifBlank { "Channel" }
                        }

                        if (isEditMode) {
                            val availableOptions = listOf("Book", "Manhua", "Manga", "Series", "Channel")
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
                                        color = if (isSel) PrimaryPurple.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                        border = BorderStroke(1.dp, if (isSel) PrimaryPurple else Color.White.copy(alpha = 0.12f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = typeOption,
                                            color = if (isSel) PrimaryPurple else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PrimaryPurple.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.4f))
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
                                                .background(PrimaryPurple)
                                        )
                                        Text(
                                            text = effectiveType.uppercase(),
                                            color = PrimaryPurple,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.07f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                ) {
                                    Text(
                                        text = "${chapters.size} Videos",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
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

                        // Channel Title
                        if (isEditMode) {
                            TextField(
                                value = draftTitle,
                                onValueChange = onUpdateDraftTitle,
                                placeholder = { Text("Channel Name", color = TextMuted, fontSize = 18.sp) },
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
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp
                            )
                        }

                        // Channel Handle / Subtitle
                        val handle = if (isEditMode) draftAltTitle else {
                            if (entryMetadata.altTitle.isNotBlank()) entryMetadata.altTitle
                            else "@${manga.title.lowercase().replace("\\s+".toRegex(), "")}"
                        }

                        if (isEditMode) {
                            TextField(
                                value = draftAltTitle,
                                onValueChange = onUpdateDraftAltTitle,
                                placeholder = { Text("@channel_handle", color = TextMuted, fontSize = 13.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = PrimaryPurple,
                                    focusedTextColor = TextMuted,
                                    unfocusedTextColor = TextMuted
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = handle,
                                color = TextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Action Row (Watch, Videos Window, Bookmark) - Hidden during Edit Mode ──
            if (!isEditMode) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Primary "Watch" Button -> Plays First/Latest Video immediately
                        val firstVideo = chapters.firstOrNull { it.title == manga.lastReadTitle }
                            ?: chapters.firstOrNull()

                        Button(
                            onClick = {
                                if (firstVideo != null) {
                                    onNavigateToMedia(firstVideo.id, 2)
                                } else {
                                    onPickVideos()
                                }
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (firstVideo != null) {
                                        if (manga.lastReadTitle != null) "Resume" else "Watch"
                                    } else "Add Videos",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 2. "Videos" Button -> Opens Dedicated Channel Videos List
                        FilledTonalButton(
                            onClick = onOpenVideos,
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
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Videos (${chapters.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // 3. Bookmark / Favorite Button
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

            // ── Tags Section (Tag 2 - Topics & Categories with ✕ cut buttons) ──
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
                            Icon(
                                imageVector = Icons.Rounded.LocalOffer,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "CHANNEL TOPICS & TAGS",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }

                        if (isEditMode) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isTagSearchVisible) PrimaryPurple.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, if (isTagSearchVisible) PrimaryPurple else Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleTagSearchVisible() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTagSearchVisible) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = if (isTagSearchVisible) PrimaryPurple else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isTagSearchVisible) "Close Search" else "Add Topics",
                                        color = if (isTagSearchVisible) PrimaryPurple else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Assigned Tag Pills
                    if (assignedTags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            assignedTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CardBg,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
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
                                        if (isEditMode) {
                                            IconButton(
                                                onClick = { onRemoveTag(tag) },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Remove topic",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No topics assigned.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Expandable Master Tag Searcher
                    AnimatedVisibility(
                        visible = isEditMode && isTagSearchVisible,
                        enter = expandVertically() + fadeIn(),
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
                                    placeholder = { Text("Search tags (Tech, Gaming, Vlogs...)", color = TextMuted, fontSize = 12.sp) },
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

            // ── About Channel Section (Synopsis Card) ──
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "About Channel",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (isEditMode) {
                            OutlinedTextField(
                                value = draftDesc,
                                onValueChange = onUpdateDraftDesc,
                                placeholder = { Text("Enter channel description / bio...", color = TextMuted, fontSize = 13.sp) },
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
                            val descriptionText = manga.description.ifBlank { "No channel description added yet." }
                            val isLong = descriptionText.length > 160

                            Text(
                                text = descriptionText,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = if (!isAboutExpanded && isLong) 4 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isLong) {
                                Row(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { isAboutExpanded = !isAboutExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isAboutExpanded) "Show less" else "Read more",
                                        color = PrimaryPurple,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = if (isAboutExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
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

            // ── Additional Channel Info Card ──
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Channel Details",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Row 1: Creator & Platform / Source
                        Row(modifier = Modifier.fillMaxWidth()) {
                            MetadataItemView(
                                modifier = Modifier.weight(1f),
                                label = "Creator / Host",
                                value = if (isEditMode) draftAuthor else entryMetadata.author.ifBlank { "Creator" },
                                isEditMode = isEditMode,
                                onValueChange = onUpdateDraftAuthor
                            )
                            MetadataItemView(
                                modifier = Modifier.weight(1f),
                                label = "Platform / Source",
                                value = if (isEditMode) draftSerialization else entryMetadata.serialization.ifBlank { "Local" },
                                isEditMode = isEditMode,
                                onValueChange = onUpdateDraftSerialization
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        // Row 2: Year & Language
                        Row(modifier = Modifier.fillMaxWidth()) {
                            MetadataItemView(
                                modifier = Modifier.weight(1f),
                                label = "Started / Year",
                                value = if (isEditMode) draftYear else entryMetadata.year.ifBlank { "2024" },
                                isEditMode = isEditMode,
                                onValueChange = onUpdateDraftYear
                            )
                            MetadataItemView(
                                modifier = Modifier.weight(1f),
                                label = "Primary Language",
                                value = if (isEditMode) draftLanguage else entryMetadata.language.ifBlank { "English" },
                                isEditMode = isEditMode,
                                onValueChange = onUpdateDraftLanguage
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        // Row 3: Total Videos
                        Row(modifier = Modifier.fillMaxWidth()) {
                            MetadataItemView(
                                modifier = Modifier.weight(1f),
                                label = "Total Videos",
                                value = "${chapters.size} items",
                                isEditMode = false,
                                onValueChange = {}
                            )
                        }
                    }
                }
            }

            // Bottom spacer for comfortable scrolling
            item {
                Spacer(modifier = Modifier.height(140.dp))
            }
        }

        // ── 2. Top Floating Navigation Bar ──
        if (!isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                // Back Button
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { onNavigateBack() }
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

                // More Menu Button (3 Dots)
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { showMoreMenu = true }
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
                            text = { Text("Edit Channel") },
                            onClick = {
                                showMoreMenu = false
                                onToggleEditMode()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change Avatar / Cover") },
                            onClick = {
                                showMoreMenu = false
                                onPickCover()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Image, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add / Import Videos") },
                            onClick = {
                                showMoreMenu = false
                                onPickVideos()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.VideoFile, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open Videos (${chapters.size})") },
                            onClick = {
                                showMoreMenu = false
                                onOpenVideos()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.VideoLibrary, contentDescription = null)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Channel", color = Color(0xFFE57373)) },
                            onClick = {
                                showMoreMenu = false
                                onDeleteManga()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFE57373))
                            }
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
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                PrimaryPurple.copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.15f),
                                PrimaryPurple.copy(alpha = 0.5f)
                            )
                        )
                    ),
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Pill
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

                        // Save Changes Pill
                        Surface(
                            shape = CircleShape,
                            color = PrimaryPurple,
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
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Save Changes",
                                    color = Color.White,
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
