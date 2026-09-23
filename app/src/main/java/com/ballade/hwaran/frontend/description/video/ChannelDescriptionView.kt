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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant

    var isAboutExpanded by remember { mutableStateOf(false) }
    var isTagsExpanded by remember { mutableStateOf(false) }
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
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    spotColor = Color.Black.copy(alpha = 0.5f)
                                )
                                .clip(CircleShape)
                                .background(Color(0xFF1E1A29))
                                .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.20f)), CircleShape)
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF242033), Color(0xFF161421))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountBox,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
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
                                        color = if (isSel) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
                                        border = BorderStroke(1.dp, if (isSel) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)),
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    focusedIndicatorColor = Color.White.copy(alpha = 0.35f),
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
                                    focusedIndicatorColor = Color.White.copy(alpha = 0.35f),
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

                        // Channel URL (shown only if present in metadata)
                        val channelUrl = entryMetadata.url
                        if (channelUrl.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(channelUrl))
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = "Open channel URL",
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = channelUrl,
                                    color = Color(0xFF64B5F6),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFFE6E8EC),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (firstVideo != null) {
                                        if (manga.lastReadTitle != null) "Resume" else "Watch"
                                    } else "Add Videos",
                                    color = Color(0xFFE6E8EC),
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
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    tint = Color(0xFFE6E8EC),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Videos (${chapters.size})",
                                    color = Color(0xFFE6E8EC),
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
                                tint = Color(0xFFE6E8EC),
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
                                color = if (isTagSearchVisible) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
                                border = BorderStroke(1.dp, if (isTagSearchVisible) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)),
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
                                        tint = if (isTagSearchVisible) Color(0xFFE6E8EC) else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isTagSearchVisible) "Close Search" else "Add Topics",
                                        color = if (isTagSearchVisible) Color(0xFFE6E8EC) else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Assigned Tag Pills
                    if (assignedTags.isNotEmpty()) {
                        val maxInitialTags = 6
                        val displayTags = if (isEditMode || isTagsExpanded || assignedTags.size <= maxInitialTags) {
                            assignedTags
                        } else {
                            assignedTags.take(maxInitialTags)
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            displayTags.forEach { tag ->
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

                            // Expand / Collapse Chevron Pill when more than 6 tags
                            if (!isEditMode && assignedTags.size > maxInitialTags) {
                                Surface(
                                    onClick = { isTagsExpanded = !isTagsExpanded },
                                    shape = RoundedCornerShape(8.dp),
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
                                            contentDescription = if (isTagsExpanded) "Show fewer topics" else "Show all topics",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(16.dp)
                                        )
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
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                OutlinedTextField(
                                    value = tagQuery,
                                    onValueChange = onSetTagQuery,
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Search tags (Tech, Gaming, Vlogs...)", color = TextMuted, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
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
                                        focusedBorderColor = Color.White.copy(alpha = 0.35f),
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
                                            color = if (isAssigned) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
                                            border = BorderStroke(1.dp, if (isAssigned) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = item.tag,
                                                    color = if (isAssigned) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.6f),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isAssigned) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                                if (isAssigned) {
                                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFFE6E8EC), modifier = Modifier.size(12.dp))
                                                } else {
                                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFFE6E8EC), modifier = Modifier.size(12.dp))
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
                                    focusedBorderColor = Color.White.copy(alpha = 0.35f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            val descriptionText = entryMetadata.description.ifBlank {
                                manga.description.ifBlank { "No channel description added yet." }
                            }
                            val isLong = descriptionText.length > 160

                            val urlRegex = Regex("""(https?://\S+|www\.\S+)""")
                            val annotated = buildAnnotatedString {
                                var lastEnd = 0
                                for (match in urlRegex.findAll(descriptionText)) {
                                    if (match.range.first > lastEnd) {
                                        append(descriptionText.substring(lastEnd, match.range.first))
                                    }
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
                                maxLines = if (!isAboutExpanded && isLong) 4 else Int.MAX_VALUE,
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
                                        ) { isAboutExpanded = !isAboutExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isAboutExpanded) "Show less" else "Read more",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = if (isAboutExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Online Statistics Card (shown only if JSON data is present) ──
            val hasChannelStats = entryMetadata.views.isNotBlank() || entryMetadata.likes.isNotBlank() || entryMetadata.comments.isNotBlank()
            if (hasChannelStats) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Online Statistics",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (entryMetadata.views.isNotBlank()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("VIEWS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                        Text(entryMetadata.views, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                if (entryMetadata.likes.isNotBlank()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("LIKES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                        Text(entryMetadata.likes, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                if (entryMetadata.comments.isNotBlank()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("COMMENTS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                        Text(entryMetadata.comments, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
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
                                value = if (isEditMode) draftAuthor else entryMetadata.author.ifBlank { "—" },
                                isEditMode = isEditMode,
                                onValueChange = onUpdateDraftAuthor
                            )
                            MetadataItemView(
                                modifier = Modifier.weight(1f),
                                label = "Format",
                                value = if (isEditMode) draftMaterialTag else entryMetadata.type.ifBlank { "Channel" },
                                isEditMode = isEditMode,
                                onValueChange = onSetMaterialTag
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        // Row 2: Year & Language
                        Row(modifier = Modifier.fillMaxWidth()) {
                            MetadataItemView(
                                modifier = Modifier.weight(1f),
                                label = "Started / Year",
                                value = if (isEditMode) draftYear else entryMetadata.year.ifBlank { "—" },
                                isEditMode = isEditMode,
                                onValueChange = onUpdateDraftYear
                            )
                            MetadataItemView(
                                modifier = Modifier.weight(1f),
                                label = "Primary Language",
                                value = if (isEditMode) draftLanguage else entryMetadata.language.ifBlank { "—" },
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
                            text = "Delete Channel",
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
