package com.ballade.hwaran.frontend.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.CoverArtResolver

@Composable
fun LibraryView(
    allManga: List<MangaEntity>,
    onNavigateToDescription: (Long) -> Unit,
    initialTag: String = "All",
    isLibraryLocked: Boolean = false,
    libraryPassword: String = "",
    glowColor: Color = Color(0xFFE2E8F0),
    onOpenMusic: () -> Unit = {},
    onItemLongClick: (MangaEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tags = remember {
        listOf("All", "Fav", "Manhua", "Manga", "Light Novel", "Book", "Series", "Channel", "Music")
    }

    val normalizedInitialTag = remember(initialTag) {
        when (initialTag) {
            "Favorite" -> "Fav"
            "Novel" -> "Light Novel"
            else -> initialTag
        }
    }
    var selectedTag by remember(normalizedInitialTag) { mutableStateOf(if (normalizedInitialTag == "Music") "All" else normalizedInitialTag) }
    val tagListState = rememberLazyListState()

    LaunchedEffect(normalizedInitialTag) {
        if (normalizedInitialTag == "Music") {
            onOpenMusic()
        } else if (normalizedInitialTag.isNotBlank()) {
            selectedTag = normalizedInitialTag
        }
    }

    LaunchedEffect(selectedTag) {
        val index = tags.indexOf(selectedTag)
        if (index >= 0) {
            val targetIndex = if (index <= 1) 0 else index - 1
            tagListState.animateScrollToItem(targetIndex)
        }
    }

    // Password unlock state for locked media
    var mangaToUnlock by remember { mutableStateOf<MangaEntity?>(null) }
    var unlockPasswordInput by remember { mutableStateOf("") }
    var showIncorrectPassword by remember { mutableStateOf(false) }

    // Filter items based on selected tag
    val filteredManga = remember(allManga, selectedTag) {
        val nonNsfw = allManga.filter { !it.isNsfw && it.contentType != 3 }
        when (selectedTag) {
            "All" -> nonNsfw
            "Fav", "Favorite" -> nonNsfw.filter { it.isFavorite || it.genre?.contains("favorite", ignoreCase = true) == true }
            "Manhua" -> nonNsfw.filter {
                (it.contentType == 0 || it.boxPurpose == "manhua") && (
                    it.boxPurpose == "manhua" ||
                    it.genre?.contains("manhua", ignoreCase = true) == true ||
                    it.genre?.contains("manhwa", ignoreCase = true) == true ||
                    it.genre?.contains("webtoon", ignoreCase = true) == true ||
                    it.title.contains("manhua", ignoreCase = true) ||
                    it.title.contains("manhwa", ignoreCase = true)
                )
            }
            "Manga" -> nonNsfw.filter {
                it.contentType == 0 && it.boxPurpose != "manhua" && it.boxPurpose != "book" && (
                    it.genre == null || (
                        !it.genre.contains("manhua", ignoreCase = true) &&
                        !it.genre.contains("manhwa", ignoreCase = true) &&
                        !it.genre.contains("webtoon", ignoreCase = true)
                    )
                )
            }
            "Light Novel", "Novel" -> nonNsfw.filter { it.contentType == 4 || it.boxPurpose == "novel" }
            "Book" -> nonNsfw.filter { (it.contentType == 1 || it.boxPurpose == "book") && it.contentType != 4 && it.boxPurpose != "novel" }
            "Series" -> nonNsfw.filter { (it.contentType == 2 || it.boxPurpose == "series") && it.boxPurpose != "channel" }
            "Channel" -> nonNsfw.filter { (it.contentType == 2 || it.boxPurpose == "channel") && it.boxPurpose == "channel" }
            else -> nonNsfw
        }
    }

    // Navigation and gesture insets
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gestureBottom = WindowInsets.systemGestures.asPaddingValues().calculateBottomPadding()
    val bottomDockClearance = maxOf(navBarBottom + 16.dp, maxOf(gestureBottom + 12.dp, 32.dp)) + 68.dp + 24.dp

    val density = LocalDensity.current
    val fadeStartPx = with(density) { 86.dp.toPx() }
    val fadeEndPx = with(density) { 128.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding()
    ) {
        // 1. Clean 3-Column Material Grid (Offscreen alpha mask gives smooth fading under pills without opaque color blocks)
        if (filteredManga.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 132.dp, bottom = bottomDockClearance),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No $selectedTag in your library",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 124.dp,
                    bottom = bottomDockClearance
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredManga, key = { it.id }) { manga ->
                    LibraryMaterialCard(
                        manga = manga,
                        isLocked = isLibraryLocked && manga.isLocked,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isLibraryLocked && manga.isLocked) {
                                mangaToUnlock = manga
                            } else {
                                onNavigateToDescription(manga.id)
                            }
                        },
                        onLongClick = {
                            onItemLongClick(manga)
                        }
                    )
                }
            }
        }

        // 2. Floating Top Tags Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, bottom = 8.dp)
        ) {
            LazyRow(
                state = tagListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tags, key = { it }) { tag ->
                    val isSelected = selectedTag == tag
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
                        animationSpec = tween(200),
                        label = "tagBgColor"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.60f),
                        animationSpec = tween(200),
                        label = "tagTextColor"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                        animationSpec = tween(200),
                        label = "tagBorderColor"
                    )

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (tag == "Music") {
                                    onOpenMusic()
                                } else {
                                    selectedTag = tag
                                }
                            },
                        shape = RoundedCornerShape(20.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Text(
                            text = tag,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        if (mangaToUnlock != null) {
            AlertDialog(
                onDismissRequest = {
                    mangaToUnlock = null
                    unlockPasswordInput = ""
                    showIncorrectPassword = false
                },
                title = { Text("Unlock Item", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = unlockPasswordInput,
                            onValueChange = { unlockPasswordInput = it },
                            label = { Text("Password", color = Color.White.copy(alpha = 0.5f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = glowColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        if (showIncorrectPassword) {
                            Text("Incorrect password", color = Color(0xFFE57373), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (unlockPasswordInput == libraryPassword) {
                            val id = mangaToUnlock!!.id
                            mangaToUnlock = null
                            unlockPasswordInput = ""
                            showIncorrectPassword = false
                            onNavigateToDescription(id)
                        } else {
                            showIncorrectPassword = true
                        }
                    }) {
                        Text("Unlock", color = glowColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        mangaToUnlock = null
                        unlockPasswordInput = ""
                        showIncorrectPassword = false
                    }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                    }
                },
                containerColor = Color(0xFF1B1924),
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryMaterialCard(
    manga: MangaEntity,
    isLocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = Color.Black.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF14131C),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Locked",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                val coverModel = remember(manga.coverPath, manga.parentUri) {
                    CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF14131C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (manga.contentType == 3) Icons.Rounded.MusicNote else Icons.Rounded.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title inside the cover art with smooth bottom gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = manga.title,
                        color = Color(0xFFF0F2F5),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
