package com.ballade.hwaran.frontend.home.importer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class ImportMediaOption(
    val id: String,
    val modeId: Int, // 0: Toon/Comic, 1: Book, 4: Novel, 2: Video, 3: Music
    val boxPurpose: String?,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val supportedFormats: List<String>
)

private val MEDIA_OPTIONS = listOf(
    ImportMediaOption(
        id = "manga",
        modeId = 0,
        boxPurpose = "manga",
        title = "Manga",
        subtitle = "Japanese & RTL",
        icon = Icons.AutoMirrored.Rounded.MenuBook,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif")
    ),
    ImportMediaOption(
        id = "manhua",
        modeId = 0,
        boxPurpose = "manhua",
        title = "Manhua",
        subtitle = "Webtoons & Manhwa",
        icon = Icons.AutoMirrored.Rounded.ChromeReaderMode,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif")
    ),
    ImportMediaOption(
        id = "book",
        modeId = 1,
        boxPurpose = "book",
        title = "Books",
        subtitle = "PDF Documents",
        icon = Icons.Rounded.Book,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".pdf")
    ),
    ImportMediaOption(
        id = "novel",
        modeId = 4,
        boxPurpose = "novel",
        title = "Novels",
        subtitle = "Web & Light Novels",
        icon = Icons.Rounded.ImportContacts,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".epub", ".txt", ".md")
    ),
    ImportMediaOption(
        id = "series",
        modeId = 2,
        boxPurpose = "series",
        title = "Series Video",
        subtitle = "Shows & Anime",
        icon = Icons.Rounded.Tv,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".flv")
    ),
    ImportMediaOption(
        id = "channel",
        modeId = 2,
        boxPurpose = "channel",
        title = "Channel Video",
        subtitle = "Creators & Clips",
        icon = Icons.Rounded.Subscriptions,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".flv")
    ),
    ImportMediaOption(
        id = "music",
        modeId = 3,
        boxPurpose = "music",
        title = "Music",
        subtitle = "Albums & Tracks",
        icon = Icons.Rounded.MusicNote,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".mp3", ".flac", ".wav", ".ogg", ".m4a", ".opus", ".aac")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStudioSheet(
    initialMediaMode: Int,
    initialStorageMode: Int = 1,
    initialVideoLayoutMode: Int = 0,
    initialIsNsfw: Boolean = false,
    currentWorkspace: String? = null,
    availableWorkspaces: List<String> = emptyList(),
    glowColor: Color = Color.White,
    onImportSingleFile: (mediaMode: Int, storageMode: Int, boxPurpose: String?, workspace: String?, isNsfw: Boolean, mimeTypes: Array<String>) -> Unit = { _, _, _, _, _, _ -> },
    onImportSingleFolder: (mediaMode: Int, storageMode: Int, boxPurpose: String?, workspace: String?, isNsfw: Boolean) -> Unit,
    onImportBatchFolder: (mediaMode: Int, storageMode: Int, boxPurpose: String?, workspace: String?, isNsfw: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Selection State
    var selectedOptionId by remember(initialMediaMode, initialVideoLayoutMode) {
        mutableStateOf(
            when (initialMediaMode) {
                0 -> "manga"
                1 -> "book"
                4 -> "novel"
                2 -> if (initialVideoLayoutMode == 1) "channel" else "series"
                3 -> "music"
                else -> "manga"
            }
        )
    }

    val activeOption = remember(selectedOptionId) {
        MEDIA_OPTIONS.firstOrNull { it.id == selectedOptionId } ?: MEDIA_OPTIONS[0]
    }
    val currentAccent = activeOption.accentColor

    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.60f else 1f)
                    .fillMaxHeight(if (isLandscape) 0.95f else 0.88f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* prevent backdrop click through */ }
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
                    .drawBehind {
                        // Ambient dynamic top glow
                        val glowBrush = Brush.radialGradient(
                            colors = listOf(
                                currentAccent.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                            radius = size.width * 0.75f
                        )
                        drawRect(glowBrush)
                    },
                color = Color(0xFF101015),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    // Drag handle & Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, start = 20.dp, end = 20.dp, bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(42.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Import Studio",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = currentAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, currentAccent.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = activeOption.title,
                                            color = currentAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Choose media format and configure target destination",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.07f))
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Content Scroll Area
                    val carouselListState = rememberLazyListState()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ══════════════════════════════════════════════════════
                        // STAGE 01: SELECT MATERIAL
                        // ══════════════════════════════════════════════════════
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                            ) {
                                Text(
                                    text = "01",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "SELECT MATERIAL TYPE",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Carousel with edge-fade blur for overflowing items
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    val canScrollLeft = carouselListState.canScrollBackward
                                    val canScrollRight = carouselListState.canScrollForward

                                    if (canScrollLeft || canScrollRight) {
                                        val fadeWidthPx = 32.dp.toPx()
                                        val leftStop = if (canScrollLeft) (fadeWidthPx / size.width).coerceIn(0f, 0.35f) else 0f
                                        val rightStop = if (canScrollRight) (1f - (fadeWidthPx / size.width)).coerceIn(0.65f, 1f) else 1f

                                        val fadeBrush = Brush.horizontalGradient(
                                            0f to (if (canScrollLeft) Color.Transparent else Color.Black),
                                            leftStop to Color.Black,
                                            rightStop to Color.Black,
                                            1f to (if (canScrollRight) Color.Transparent else Color.Black)
                                        )
                                        drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                                    }
                                }
                        ) {
                            LazyRow(
                                state = carouselListState,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(MEDIA_OPTIONS, key = { it.id }) { option ->
                                    val isSelected = option.id == selectedOptionId
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedOptionId = option.id
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f),
                                        border = BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.07f)
                                        ),
                                        modifier = Modifier.width(130.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSelected) Color.White.copy(alpha = 0.14f)
                                                        else Color.White.copy(alpha = 0.05f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = option.icon,
                                                    contentDescription = option.title,
                                                    tint = if (isSelected) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.45f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Text(
                                                text = option.title,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = option.subtitle,
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Formats Badge Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 2.dp)
                        ) {
                            Text(
                                text = "Supported formats:",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                            activeOption.supportedFormats.forEach { fmt ->
                                Surface(
                                    color = Color.White.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                ) {
                                    Text(
                                        text = fmt,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // ══════════════════════════════════════════════════════
                        // STAGE 02: KNOWLEDGE BASE (READ-ONLY SPECIFICATION)
                        // ══════════════════════════════════════════════════════
                        MaterialViabilityGuide(
                            selectedOptionId = selectedOptionId,
                            accentColor = currentAccent
                        )

                        // ══════════════════════════════════════════════════════
                        // STAGE 03: LAUNCH IMPORT (INTERACTIVE ACTIONS)
                        // ══════════════════════════════════════════════════════
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = "03",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "LAUNCH IMPORT",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                            ) {
                                Text(
                                    text = "ACTION REQUIRED",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val computedPurpose = activeOption.boxPurpose

                        // Action 1: Single Title Folder
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = Color(0xFFE2E8F0),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Folder Import",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                color = Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                            ) {
                                                Text(
                                                    text = "Single Title",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = when (activeOption.id) {
                                                "manga" -> "Select a single manga title folder (contains chapter subfolders)"
                                                "manhua" -> "Select a single webtoon/manhua folder with strip chapters"
                                                "book" -> "Select a folder containing your PDF book(s)"
                                                "novel" -> "Select a folder containing an EPUB or numbered text files"
                                                "series" -> "Select a show or anime folder containing season subfolders"
                                                "channel" -> "Select a creator channel directory containing video files"
                                                "music" -> "Select an album or music folder containing tracks"
                                                else -> "Select a single title folder"
                                            },
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onImportSingleFolder(
                                            activeOption.modeId,
                                            1,
                                            computedPurpose,
                                            currentWorkspace,
                                            initialIsNsfw
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1E222D),
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SELECT TITLE FOLDER",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }

                        // Action 2: Batch Master Scanner
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.AutoAwesomeMotion,
                                            contentDescription = null,
                                            tint = Color(0xFFE2E8F0),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Batch Import",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                color = Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                            ) {
                                                Text(
                                                    text = "Multi-Title Scanner",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = when (activeOption.id) {
                                                "manga" -> "Select a master folder containing multiple manga titles"
                                                "manhua" -> "Select a master directory containing multiple webtoon series"
                                                "book" -> "Select a root library folder containing dozens or hundreds of books"
                                                "novel" -> "Select a master novel folder containing multiple book titles"
                                                "series" -> "Select a root directory containing multiple TV shows or anime series"
                                                "channel" -> "Select a master directory containing multiple creator channels"
                                                "music" -> "Select a master music collection containing multiple artist/album folders"
                                                else -> "Select a master collection directory"
                                            },
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                Surface(
                                    color = Color.Black.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Info,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Recursively indexes each subfolder into an independent library entry in parallel.",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onImportBatchFolder(
                                            activeOption.modeId,
                                            1,
                                            computedPurpose,
                                            currentWorkspace,
                                            initialIsNsfw
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1E222D),
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesomeMotion,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SELECT MASTER / BATCH FOLDER",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialViabilityGuide(
    selectedOptionId: String,
    accentColor: Color
) {
    var guideTab by remember { mutableIntStateOf(0) } // 0: Folder (Single), 1: Batch (Multi)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header - Explicitly Read-Only Blueprint
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.AccountTree,
                            contentDescription = null,
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "DIRECTORY BLUEPRINT",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Text(
                            text = "Verify your local file structure before selecting a folder below",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = "READ-ONLY",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Structure Mode Tab Selector (Single Folder vs Batch Library)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { guideTab = 0 },
                    shape = RoundedCornerShape(8.dp),
                    color = if (guideTab == 0) Color(0xFF1E222D) else Color.Transparent,
                    border = if (guideTab == 0) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "1. Single Title Blueprint",
                        color = if (guideTab == 0) Color.White else Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        fontWeight = if (guideTab == 0) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 7.dp)
                    )
                }

                Surface(
                    onClick = { guideTab = 1 },
                    shape = RoundedCornerShape(8.dp),
                    color = if (guideTab == 1) Color(0xFF1E222D) else Color.Transparent,
                    border = if (guideTab == 1) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "2. Batch Scanner Blueprint",
                        color = if (guideTab == 1) Color.White else Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        fontWeight = if (guideTab == 1) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 7.dp)
                    )
                }
            }

            // Directory Tree Viewport
            val treeText = when (selectedOptionId) {
                "manga" -> if (guideTab == 0) {
                    """
📁 One Piece/                   ← Select manga folder
├── 🖼️ cover.jpg                (optional manga cover)
├── 📁 Chapter 01/
│   ├── 001.webp
│   └── 002.webp
└── 📁 Chapter 02/
    └── 001.webp
Tip: Supported images: JPG, PNG, WEBP, BMP, GIF. Chapters are subfolders of page images.
                    """.trimIndent()
                } else {
                    """
📁 Manga Collection/            ← Select master folder
├── 📁 One Piece/
│   ├── 📁 Chapter 01/ ...
│   └── 📁 Chapter 02/ ...
└── 📁 Berserk/
    ├── 📁 Chapter 01/ ...
    └── 📁 Chapter 02/ ...
Tip: Recursively scans and imports all manga title folders.
                    """.trimIndent()
                }

                "manhua" -> if (guideTab == 0) {
                    """
📁 Solo Leveling/               ← Select webtoon folder
├── 🖼️ cover.jpg                (optional series cover)
├── 📁 Chapter 01/
│   ├── 001.webp                (vertical strip slice)
│   └── 002.webp
└── 📁 Chapter 02/
    └── 001.webp
Tip: Optimized for long-strip reading. Chapters contain vertical slice images.
                    """.trimIndent()
                } else {
                    """
📁 Webtoons & Manhua/           ← Select master folder
├── 📁 Solo Leveling/
│   ├── 📁 Chapter 01/ ...
│   └── 📁 Chapter 02/ ...
└── 📁 Tower of God/
    ├── 📁 Chapter 01/ ...
    └── 📁 Chapter 02/ ...
Tip: Recursively scans and imports all webtoon & manhua folders.
                    """.trimIndent()
                }

                "book" -> if (guideTab == 0) {
                    """
📁 Calculus Book/               ← Select book folder
├── 🖼️ cover.jpg                (optional custom cover)
└── 📄 calculus.pdf             (PDF document)
Tip: If no cover image exists, the first page is automatically rendered as the cover.
                    """.trimIndent()
                } else {
                    """
📁 PDF Library/                 ← Select master folder
├── 📁 Dune/
│   └── 📄 dune.pdf
├── 📁 Foundation/
│   └── 📄 foundation.pdf
└── 📁 Science/
    └── 📄 physics.pdf
Tip: Recursively indexes all PDF books found across your folder hierarchy.
                    """.trimIndent()
                }

                "novel" -> if (guideTab == 0) {
                    """
📁 Shadow Slave/                ← Select novel folder
├── 🖼️ cover.webp               (optional cover)
├── 📄 novel.epub               (EPUB file)
├── 📄 chapter_001.txt          (or numbered .txt / .md files)
└── 📄 chapter_002.txt
Tip: EPUB files and text files (.epub, .txt, .md) are parsed into readable chapters.
                    """.trimIndent()
                } else {
                    """
📁 Web Novels Library/          ← Select master folder
├── 📁 Shadow Slave/
│   ├── 📄 ch001.txt
│   └── 📄 ch002.txt
├── 📁 Lord of the Mysteries/
│   └── 📄 novel.epub
└── 📁 Omniscient Reader/
    └── 📄 novel.epub
Tip: Fast multi-novel indexing across all subfolders.
                    """.trimIndent()
                }

                "series" -> if (guideTab == 0) {
                    """
📁 Attack on Titan/             ← Select series or anime folder
├── 🖼️ cover.jpg                (optional poster cover)
├── 📁 Season 1/                (season subfolder)
│   ├── 🎬 S01E01.mp4
│   └── 🎬 S01E02.mp4
└── 📁 Season 2/
    └── 🎬 S02E01.mp4
Tip: Groups episodes by season subfolders for organized series viewing.
                    """.trimIndent()
                } else {
                    """
📁 Video Series Library/        ← Select master directory
├── 📁 Attack on Titan/
│   ├── 📁 Season 1/ ...
│   └── 📁 Season 2/ ...
└── 📁 Jujutsu Kaisen/
    └── 📁 Season 1/ ...
Tip: Creates a series card for each show/season folder in your directory.
                    """.trimIndent()
                }

                "channel" -> if (guideTab == 0) {
                    """
📁 Veritasium/                  ← Select creator channel folder
├── 🖼️ banner.jpg               (optional channel avatar/banner)
├── 🎬 The Infinite Pattern.mp4
├── 🎬 Why Gravity is Weird.mp4
└── 🎬 Quantum Spin Explained.mp4
Tip: All videos placed directly in this folder appear in a creator feed layout.
                    """.trimIndent()
                } else {
                    """
📁 Creator Channels/            ← Select master directory
├── 📁 Veritasium/
│   ├── 🎬 Video1.mp4
│   └── 🎬 Video2.mp4
└── 📁 Kurzgesagt/
    ├── 🎬 Video1.mp4
    └── 🎬 Video2.mp4
Tip: Each subfolder is imported as a distinct creator channel feed.
                    """.trimIndent()
                }

                "music" -> if (guideTab == 0) {
                    """
📁 Random Access Memories/      ← Select album or artist folder
├── 🖼️ cover.jpg                (optional album art)
├── 🎵 01 - Give Life Back.flac
├── 🎵 02 - Giorgio.flac
└── 🎵 03 - Giorgio Moroder.flac
Tip: Track numbers, artist, and album tags are extracted automatically.
                    """.trimIndent()
                } else {
                    """
📁 Music Collection/            ← Select master music folder
├── 📁 Daft Punk/
│   ├── 📁 Discovery/ ...
│   └── 📁 RAM/ ...
└── 📁 Pink Floyd/
    └── 📁 The Wall/ ...
Tip: Scans all artist and album folders into your library.
                    """.trimIndent()
                }

                else -> ""
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF090A0E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = treeText,
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Key Rules / Checklist (Monochrome)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val rules = when (selectedOptionId) {
                    "manga" -> listOf(
                        "Supported Formats: .jpg, .jpeg, .png, .webp, .bmp, .gif image files.",
                        "Folder Layout: Select a manga title folder with chapter subfolders (e.g. 'Chapter 01').",
                        "Library Shelving: Automatically sorted into the Manga shelf in your library and dashboard."
                    )
                    "manhua" -> listOf(
                        "Supported Formats: .jpg, .jpeg, .png, .webp, .bmp, .gif image files.",
                        "Webtoon Strips: Select a manhua/webtoon folder with vertical strip images inside chapters.",
                        "Library Shelving: Automatically assigned to the Manhua / Webtoon category with strip reader presets."
                    )
                    "book" -> listOf(
                        "Supported Format: .pdf documents only.",
                        "Folder Import: Select the folder containing your PDF book(s).",
                        "Auto-Render: First page is automatically extracted as the high-res cover.",
                        "Reader Engine: Supports page-by-page, vertical scroll, bookmarks, and text search."
                    )
                    "novel" -> listOf(
                        "Supported Formats: .epub, .txt, .md, .markdown.",
                        "Folder Import: Select a folder containing an EPUB or numbered text chapter files.",
                        "Chapter Ordering: For text novels, prefix file names with numbers (e.g. '001_intro.txt')."
                    )
                    "series" -> listOf(
                        "Supported Formats: .mp4, .mkv, .webm, .mov, .avi, .flv.",
                        "Series Structure: Subfolders named 'Season 1', 'Season 2' group episodes chronologically.",
                        "Metadata: Thumbnails, durations, and aspect ratios generated automatically via Media3."
                    )
                    "channel" -> listOf(
                        "Supported Formats: .mp4, .mkv, .webm, .mov, .avi, .flv.",
                        "Channel Layout: Place video files directly in the creator folder for a YouTube-like feed.",
                        "Video Feed: Ideal for tutorials, creator clips, vlog playlists, and short videos."
                    )
                    "music" -> listOf(
                        "Supported Formats: .mp3, .flac, .wav, .ogg, .m4a, .opus, .aac.",
                        "Metadata: ID3 and Vorbis tags (artist, album, track, embedded cover) read automatically.",
                        "Folder Structure: Each folder containing audio files is recognized as an album."
                    )
                    else -> emptyList()
                }

                rules.forEach { rule ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier
                                .size(12.dp)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = rule,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Read-Only Assurance Notice
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Read-only blueprint • All media remains in its original external directory via SAF.",
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
