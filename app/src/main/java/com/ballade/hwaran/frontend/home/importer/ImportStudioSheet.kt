package com.ballade.hwaran.frontend.home.importer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class ImportMediaOption(
    val modeId: Int, // 0: Comic, 1: Book, 4: Novel, 2: Video, 3: Music
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val supportedFormats: List<String>,
    val fileMimeTypes: Array<String>,
    val canImportFile: Boolean = true,
    val canImportFolder: Boolean = true
)

private val MEDIA_OPTIONS = listOf(
    ImportMediaOption(
        modeId = 0,
        title = "Comics",
        subtitle = "Manga & Webtoons",
        icon = Icons.AutoMirrored.Rounded.MenuBook,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf("Folder", "CBZ", "ZIP", "Images"),
        fileMimeTypes = arrayOf("application/zip", "application/x-cbz", "application/octet-stream", "image/*"),
        canImportFile = true,
        canImportFolder = true
    ),
    ImportMediaOption(
        modeId = 1,
        title = "Books",
        subtitle = "PDF Documents",
        icon = Icons.Rounded.Book,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".pdf"),
        fileMimeTypes = arrayOf("application/pdf"),
        canImportFile = true,
        canImportFolder = true
    ),
    ImportMediaOption(
        modeId = 4,
        title = "Novels",
        subtitle = "EPUB & Text",
        icon = Icons.Rounded.ImportContacts,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".epub", ".txt", ".md"),
        fileMimeTypes = arrayOf(
            "application/epub+zip",
            "text/plain",
            "text/markdown",
            "application/octet-stream",
            "*/*"
        ),
        canImportFile = true,
        canImportFolder = true
    ),
    ImportMediaOption(
        modeId = 2,
        title = "Videos",
        subtitle = "Shows & Channels",
        icon = Icons.Rounded.PlayCircle,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".mp4", ".mkv", ".webm", ".mov"),
        fileMimeTypes = arrayOf("video/*"),
        canImportFile = true,
        canImportFolder = true
    ),
    ImportMediaOption(
        modeId = 3,
        title = "Music",
        subtitle = "Albums & Tracks",
        icon = Icons.Rounded.MusicNote,
        accentColor = Color(0xFFE2E8F0),
        supportedFormats = listOf(".mp3", ".flac", ".wav", ".m4a"),
        fileMimeTypes = arrayOf("audio/*"),
        canImportFile = true,
        canImportFolder = true
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStudioSheet(
    initialMediaMode: Int,
    initialStorageMode: Int,
    initialVideoLayoutMode: Int,
    initialIsNsfw: Boolean,
    currentWorkspace: String?,
    availableWorkspaces: List<String> = emptyList(),
    glowColor: Color,
    onImportSingleFile: (mediaMode: Int, storageMode: Int, boxPurpose: String?, workspace: String?, isNsfw: Boolean, mimeTypes: Array<String>) -> Unit,
    onImportSingleFolder: (mediaMode: Int, storageMode: Int, boxPurpose: String?, workspace: String?, isNsfw: Boolean) -> Unit,
    onImportBatchFolder: (mediaMode: Int, storageMode: Int, boxPurpose: String?, workspace: String?, isNsfw: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Selection State
    var selectedMediaId by remember(initialMediaMode) {
        mutableIntStateOf(
            if (MEDIA_OPTIONS.any { it.modeId == initialMediaMode }) initialMediaMode else 0
        )
    }
    var selectedStorageMode by remember(initialStorageMode) { mutableIntStateOf(initialStorageMode) }
    var selectedVideoShelf by remember(initialVideoLayoutMode) { mutableIntStateOf(initialVideoLayoutMode) }
    var selectedComicPurpose by remember { mutableStateOf("manga") }
    var selectedWorkspace by remember(currentWorkspace) { mutableStateOf(currentWorkspace) }
    var isNsfwVault by remember(initialIsNsfw) { mutableStateOf(initialIsNsfw) }

    val activeOption = remember(selectedMediaId) {
        MEDIA_OPTIONS.firstOrNull { it.modeId == selectedMediaId } ?: MEDIA_OPTIONS[0]
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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ── 1. Media Type Selector Carousel ──
                        Text(
                            text = "MEDIA CATEGORY",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(MEDIA_OPTIONS, key = { it.modeId }) { option ->
                                val isSelected = option.modeId == selectedMediaId
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedMediaId = option.modeId
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f)
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
                                                    if (isSelected) Color.White.copy(alpha = 0.12f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = option.icon,
                                                contentDescription = option.title,
                                                tint = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = option.title,
                                            color = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.65f),
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

                        // Formats Badge Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
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
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = fmt,
                                        color = currentAccent.copy(alpha = 0.9f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // ── 2. Configuration Deck ──
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.035f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Tune,
                                        contentDescription = null,
                                        tint = currentAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "IMPORT CONFIGURATION",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                }

                                // Storage Mode Pill
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Storage Pipeline",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.Black.copy(alpha = 0.4f))
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val isLocal = selectedStorageMode == 0
                                        Surface(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedStorageMode = 0
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isLocal) Color(0xFF222631) else Color.Transparent,
                                            border = if (isLocal) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Shield,
                                                    contentDescription = null,
                                                    tint = if (isLocal) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Local Vault",
                                                    color = if (isLocal) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isLocal) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }

                                        Surface(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedStorageMode = 1
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (!isLocal) Color(0xFF222631) else Color.Transparent,
                                            border = if (!isLocal) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Rounded.FolderOpen,
                                                    contentDescription = null,
                                                    tint = if (!isLocal) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "External In-Place",
                                                    color = if (!isLocal) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                                                    fontSize = 12.sp,
                                                    fontWeight = if (!isLocal) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = if (selectedStorageMode == 0)
                                            "Copies content into app's secure private sandbox for instant caching and protection."
                                        else
                                            "Reads in-place from your storage without duplicating files to preserve disk space.",
                                        color = Color.White.copy(alpha = 0.38f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }

                                // Per-Media Specific Shelf Configuration
                                when (selectedMediaId) {
                                    2 -> {
                                        // Video Mode: Series vs Channel
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Video Shelf Type",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color.Black.copy(alpha = 0.4f))
                                                    .padding(3.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val isChannel = selectedVideoShelf == 1
                                                Surface(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedVideoShelf = 0
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (!isChannel) Color(0xFF222631) else Color.Transparent,
                                                    border = if (!isChannel) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Series / Anime",
                                                            color = if (!isChannel) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                                                            fontSize = 12.sp,
                                                            fontWeight = if (!isChannel) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedVideoShelf = 1
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChannel) Color(0xFF222631) else Color.Transparent,
                                                    border = if (isChannel) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Creator Channel",
                                                            color = if (isChannel) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isChannel) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    0 -> {
                                        // Comic Mode: Manga vs Manhua
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Comic Shelf Format",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color.Black.copy(alpha = 0.4f))
                                                    .padding(3.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val isManhua = selectedComicPurpose == "manhua"
                                                Surface(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedComicPurpose = "manga"
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (!isManhua) Color(0xFF222631) else Color.Transparent,
                                                    border = if (!isManhua) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Manga (Paged)",
                                                            color = if (!isManhua) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                                                            fontSize = 12.sp,
                                                            fontWeight = if (!isManhua) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedComicPurpose = "manhua"
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isManhua) Color(0xFF222631) else Color.Transparent,
                                                    border = if (isManhua) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Webtoon (Vertical)",
                                                            color = if (isManhua) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isManhua) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    4 -> {
                                        // Novel Mode: Informational pill
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(currentAccent.copy(alpha = 0.08f))
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = currentAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "EPUB spine parsing, cover art extraction, and chapter word-counts automatically applied.",
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    3 -> {
                                        // Music Mode: Informational pill
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(currentAccent.copy(alpha = 0.08f))
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = currentAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Embedded ID3 audio tags, artist metadata, and cover art automatically extracted.",
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // Target Workspace Selector
                                if (availableWorkspaces.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Assign to Workspace",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            item {
                                                val isDefault = selectedWorkspace.isNullOrBlank()
                                                FilterChip(
                                                    selected = isDefault,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedWorkspace = null
                                                    },
                                                    label = { Text("Main Library", fontSize = 11.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = currentAccent.copy(alpha = 0.25f),
                                                        selectedLabelColor = Color.White
                                                    )
                                                )
                                            }
                                            items(availableWorkspaces) { ws ->
                                                val isSelected = selectedWorkspace == ws
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedWorkspace = ws
                                                    },
                                                    label = { Text(ws, fontSize = 11.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = currentAccent.copy(alpha = 0.25f),
                                                        selectedLabelColor = Color.White
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                // Vault / Locked content toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.25f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            if (isNsfwVault) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                            contentDescription = null,
                                            tint = if (isNsfwVault) Color(0xFFFF5252) else Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Private / Locked Vault",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Hides content behind passcode / biometrics",
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = isNsfwVault,
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            isNsfwVault = it
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFFFF5252),
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                        )
                                    )
                                }
                            }
                        }

                        // ── 3. Action Cards (Pick Single vs. Batch) ──
                        Text(
                            text = "IMPORT SOURCE",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        val computedPurpose = when (selectedMediaId) {
                            2 -> if (selectedVideoShelf == 1) "channel" else "series"
                            0 -> selectedComicPurpose
                            4 -> "novel"
                            1 -> "book"
                            else -> null
                        }

                        // Card A: Single Item Import
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.045f),
                            border = BorderStroke(1.dp, currentAccent.copy(alpha = 0.25f)),
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
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(currentAccent.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (selectedMediaId) {
                                                1 -> Icons.Rounded.Description
                                                4 -> Icons.Rounded.Article
                                                2 -> Icons.Rounded.Movie
                                                3 -> Icons.Rounded.Album
                                                else -> Icons.Rounded.Folder
                                            },
                                            contentDescription = null,
                                            tint = currentAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = when (selectedMediaId) {
                                                1 -> "Single Book (PDF)"
                                                4 -> "Single Novel (EPUB / Text)"
                                                2 -> if (selectedVideoShelf == 1) "Creator Channel Folder" else "Show / Season Folder"
                                                3 -> "Single Album Folder"
                                                else -> "Single Comic / Manga"
                                            },
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = when (selectedMediaId) {
                                                1 -> "Pick a single PDF document or folder containing book pages"
                                                4 -> "Pick an .epub, .txt, or markdown file directly or its folder"
                                                2 -> "Pick a video file or folder with episodes for a specific series"
                                                3 -> "Pick an album folder containing audio tracks and cover"
                                                else -> "Pick a folder with chapter subfolders or loose pages"
                                            },
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }

                                // Action Buttons (File or Folder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (activeOption.canImportFile) {
                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onImportSingleFile(
                                                    selectedMediaId,
                                                    selectedStorageMode,
                                                    computedPurpose,
                                                    selectedWorkspace,
                                                    isNsfwVault,
                                                    activeOption.fileMimeTypes
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF222631),
                                                contentColor = Color(0xFFE6E8EC)
                                            ),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Rounded.InsertDriveFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (selectedMediaId == 1 || selectedMediaId == 4) "Pick File" else "Pick File",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    if (activeOption.canImportFolder) {
                                        OutlinedButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onImportSingleFolder(
                                                    selectedMediaId,
                                                    selectedStorageMode,
                                                    computedPurpose,
                                                    selectedWorkspace,
                                                    isNsfwVault
                                                )
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = Color.White.copy(alpha = 0.04f),
                                                contentColor = Color(0xFFE6E8EC)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Folder,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Pick Folder",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card B: Batch Mega Import
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onImportBatchFolder(
                                    selectedMediaId,
                                    selectedStorageMode,
                                    computedPurpose,
                                    selectedWorkspace,
                                    isNsfwVault
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.035f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesomeMotion,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Batch Library Import",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            color = Color(0xFF66BB6A).copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFF66BB6A).copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = "Scanner",
                                                color = Color(0xFF66BB6A),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = when (selectedMediaId) {
                                            1 -> "Pick a master directory containing dozens or hundreds of PDF books"
                                            4 -> "Pick a root collection containing multiple EPUB / light novel titles"
                                            2 -> "Pick a parent directory with multiple shows/anime to index all at once"
                                            3 -> "Pick a music library folder containing multiple artists and albums"
                                            else -> "Pick a collection folder full of different manga / comic titles"
                                        },
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}
