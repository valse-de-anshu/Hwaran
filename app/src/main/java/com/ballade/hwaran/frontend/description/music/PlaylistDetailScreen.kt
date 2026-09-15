package com.ballade.hwaran.frontend.description.music

import android.content.Intent
import androidx.activity.compose.BackHandler
import java.io.File
import androidx.compose.material.icons.rounded.MusicNote

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenu
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.ui.viewmodels.DescriptionViewModel
import com.ballade.hwaran.ui.viewmodels.LibraryViewModel
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import com.ballade.hwaran.ui.components.AnimatedEqualizer
import com.ballade.hwaran.ui.dialogs.PlaylistSelectionDialog

enum class SelectionMode {
    NONE,
    DELETE,
    SHARE
}

@Composable
fun MusicDescriptionScreen(
    mangaId: Long,
    descriptionViewModel: DescriptionViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToEditPlaylist: (Long) -> Unit,
    onNavigateToEditSong: (Long) -> Unit
) {
    PlaylistDetailScreen(
        mangaId,
        descriptionViewModel,
        musicViewModel,
        settingsViewModel,
        libraryViewModel,
        onNavigateBack,
        onNavigateToNowPlaying,
        onNavigateToEditPlaylist,
        onNavigateToEditSong
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    mangaId: Long,
    descriptionViewModel: DescriptionViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToEditPlaylist: (Long) -> Unit,
    onNavigateToEditSong: (Long) -> Unit
) {
    LaunchedEffect(mangaId) {
        descriptionViewModel.loadManga(mangaId)
    }

    val manga by descriptionViewModel.manga.collectAsState()
    val chapters by descriptionViewModel.chapters.collectAsState()
    val currentChapter by musicViewModel.currentChapter.collectAsState()
    val currentManga by musicViewModel.currentManga.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    // colorPalette intentionally NOT collected here — reading it caused full-screen
    // recompositions on every palette-analysis update during music playback.
    val allManga by libraryViewModel.allMangaState.collectAsState()
    // Memoized so the filter doesn't run on every recomposition
    val playlists = remember(allManga) { allManga.filter { it.contentType == 3 } }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<ChapterEntity?>(null) }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<ChapterEntity?>(null) }

    var selectionMode by remember { mutableStateOf(SelectionMode.NONE) }
    val selectedChapterIds = remember { mutableStateListOf<Long>() }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode != SelectionMode.NONE) {
        selectionMode = SelectionMode.NONE
        selectedChapterIds.clear()
    }

    val isMiniPlayerActive = currentChapter != null
    val bottomActionPadding by animateDpAsState(
        targetValue = if (isMiniPlayerActive) 176.dp else 28.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bottom_action_padding"
    )

    val listBottomPadding = when {
        selectionMode != SelectionMode.NONE && isMiniPlayerActive -> 270.dp
        selectionMode != SelectionMode.NONE -> 150.dp
        isMiniPlayerActive -> 190.dp
        else -> 100.dp
    }


    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            descriptionViewModel.importMultipleMusic(uris)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Blurred Album Art Background
        // allowHardware(false) is required — the blur modifier cannot operate on
        // hardware-accelerated bitmaps. Radius reduced to 60dp (still fully blurred
        // but cheaper for the GPU). crossfade disabled so the image snaps in without
        // an extra animation layer on every track change.
        val bgImageData = remember(currentChapter?.id, currentManga?.id) {
            currentChapter?.thumbnailUri ?: currentChapter?.folderUri ?: currentManga?.coverPath
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(bgImageData)
                    .crossfade(false)
                    .allowHardware(false)
                    .memoryCacheKey("playlist_bg_${currentManga?.id}_${currentChapter?.id}")
                    .diskCacheKey("playlist_bg_${currentManga?.id}_${currentChapter?.id}")
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 60.dp)
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 148.dp, bottom = listBottomPadding)
            ) {
                item(key = "header") {
                    // Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                manga?.let {
                                    if (it.coverPath.isNotEmpty() && it.coverPath != "android.resource://android/drawable/ic_menu_gallery") {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(it.coverPath)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        androidx.compose.material3.Icon(
                                            Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.1f),
                                            modifier = Modifier.size(80.dp)
                                        )
                                    }
                                } ?: run {
                                    androidx.compose.material3.Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = manga?.title ?: "Loading...",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "${chapters.size} songs",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Rotating Add Music button with Haptic Feedback (hidden in selection mode)
                        if (selectionMode == SelectionMode.NONE) {
                            IconButton(
                                onClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    musicPickerLauncher.launch(arrayOf("audio/*", "application/ogg")) 
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(top = 300.dp)
                                    .size(56.dp)
                                    .background(Color.White, CircleShape)
                            ) {
                                Icon(
                                    Icons.Rounded.Add, 
                                    contentDescription = "Add Music", 
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = chapters,
                    key = { _, chapter -> chapter.id }
                ) { index, chapter ->
                    val isCurrent = currentChapter?.id == chapter.id
                    val isSelected = selectedChapterIds.contains(chapter.id)
                    SongItem(
                        chapter = chapter,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onAddToPlaylist = { 
                            songToAddToPlaylist = chapter
                            showPlaylistDialog = true
                        },
                        onEditTag = { onNavigateToEditSong(chapter.id) },
                        onDelete = {
                            songToDelete = chapter
                            showDeleteDialog = true
                        },
                        onToggleSelect = {
                            if (isSelected) {
                                selectedChapterIds.remove(chapter.id)
                            } else {
                                selectedChapterIds.add(chapter.id)
                            }
                        }
                    ) {
                        if (selectionMode != SelectionMode.NONE) {
                            if (isSelected) {
                                selectedChapterIds.remove(chapter.id)
                            } else {
                                selectedChapterIds.add(chapter.id)
                            }
                        } else {
                            musicViewModel.playPlaylist(manga!!, chapters, index)
                        }
                    }
                }
            }

            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(top = 26.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    if (selectionMode == SelectionMode.NONE) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack, 
                                contentDescription = "Back", 
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // 3-dots overflow button (Delete / Share)
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            HwaranDropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                HwaranDropdownMenuItem(
                                    text = "Share Songs",
                                    leadingIcon = Icons.Rounded.Share,
                                    onClick = {
                                        showOverflowMenu = false
                                        selectionMode = SelectionMode.SHARE
                                        selectedChapterIds.clear()
                                    }
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                                HwaranDropdownMenuItem(
                                    text = "Delete Songs",
                                    leadingIcon = Icons.Rounded.DeleteSweep,
                                    isDanger = true,
                                    onClick = {
                                        showOverflowMenu = false
                                        selectionMode = SelectionMode.DELETE
                                        selectedChapterIds.clear()
                                    }
                                )
                            }
                        }
                    } else {
                        // Selection Mode Top Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        selectionMode = SelectionMode.NONE
                                        selectedChapterIds.clear()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Cancel",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${selectedChapterIds.size} Selected",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (selectionMode == SelectionMode.DELETE) "Delete mode" else "Share mode",
                                        color = if (selectionMode == SelectionMode.DELETE) Color(0xFFE57373) else MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            val allSelected = chapters.isNotEmpty() && selectedChapterIds.size == chapters.size
                            TextButton(
                                onClick = {
                                    if (allSelected) {
                                        selectedChapterIds.clear()
                                    } else {
                                        selectedChapterIds.clear()
                                        selectedChapterIds.addAll(chapters.map { it.id })
                                    }
                                }
                            ) {
                                Text(
                                    text = if (allSelected) "Deselect All" else "Select All",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom Action Bar for Selection Mode (elevated above MiniPlayer)
        AnimatedVisibility(
            visible = selectionMode != SelectionMode.NONE,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(15f)
                .navigationBarsPadding()
                .padding(bottom = bottomActionPadding)
        ) {
            val count = selectedChapterIds.size
            if (selectionMode == SelectionMode.DELETE) {
                Button(
                    onClick = {
                        if (count > 0) {
                            showBatchDeleteDialog = true
                        }
                    },
                    enabled = count > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE57373),
                        disabledContainerColor = Color(0xFFE57373).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (count > 0) "Delete ($count)" else "Select Songs to Delete",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (selectionMode == SelectionMode.SHARE) {
                Button(
                    onClick = {
                        if (count > 0) {
                            val selectedTracks = chapters.filter { it.id in selectedChapterIds }
                            shareTracks(context, selectedTracks)
                            selectionMode = SelectionMode.NONE
                            selectedChapterIds.clear()
                        }
                    },
                    enabled = count > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (count > 0) "Share ($count)" else "Select Songs to Share",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Shared Premium Dialogs
        if (showPlaylistDialog && songToAddToPlaylist != null) {
            PlaylistSelectionDialog(
                playlists = playlists,
                onPlaylistSelected = { playlistId ->
                    libraryViewModel.addSongToPlaylist(playlistId, songToAddToPlaylist!!)
                    showPlaylistDialog = false
                    songToAddToPlaylist = null
                },
                onCreatePlaylist = { name ->
                    libraryViewModel.createPlaylist(name)
                },
                onDismiss = { 
                    showPlaylistDialog = false 
                    songToAddToPlaylist = null
                }
            )
        }

        if (showDeleteDialog && songToDelete != null) {
            DeleteConfirmationDialog(
                onDeleteFromPlaylist = {
                    libraryViewModel.deleteChapterOnlyFromDb(songToDelete!!.id)
                    showDeleteDialog = false
                    songToDelete = null
                },
                onDeleteFromDevice = {
                    libraryViewModel.deleteSelectedChapters(listOf(songToDelete!!.id))
                    showDeleteDialog = false
                    songToDelete = null
                },
                onDismiss = { showDeleteDialog = false }
            )
        }

        if (showBatchDeleteDialog && selectedChapterIds.isNotEmpty()) {
            val count = selectedChapterIds.size
            AlertDialog(
                onDismissRequest = { showBatchDeleteDialog = false },
                title = { 
                    Text(
                        text = "Delete $count ${if (count == 1) "Song" else "Songs"}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                text = {
                    Text(
                        text = "How do you want to remove the selected ${if (count == 1) "song" else "songs"}?",
                        color = Color.White.copy(alpha = 0.7f)
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val idsToDelete = selectedChapterIds.toList()
                                libraryViewModel.deleteChaptersOnlyFromDb(idsToDelete)
                                showBatchDeleteDialog = false
                                selectionMode = SelectionMode.NONE
                                selectedChapterIds.clear()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                        ) {
                            Text("Remove from Playlist Only", color = Color.White)
                        }
                        Button(
                            onClick = {
                                val idsToDelete = selectedChapterIds.toList()
                                libraryViewModel.deleteSelectedChapters(idsToDelete)
                                showBatchDeleteDialog = false
                                selectionMode = SelectionMode.NONE
                                selectedChapterIds.clear()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                        ) {
                            Text("Delete from Device", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { showBatchDeleteDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                },
                containerColor = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@Composable
fun SongItem(
    chapter: ChapterEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    selectionMode: SelectionMode = SelectionMode.NONE,
    isSelected: Boolean = false,
    onAddToPlaylist: () -> Unit,
    onEditTag: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelect: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (selectionMode != SelectionMode.NONE) {
                    onToggleSelect()
                } else {
                    onClick()
                }
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode != SelectionMode.NONE) {
            IconButton(
                onClick = onToggleSelect,
                modifier = Modifier.size(36.dp).padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) {
                        if (selectionMode == SelectionMode.DELETE) Color(0xFFE57373) else MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            // Cache keys ensure Coil serves from memory on every scroll pass
            // without re-decoding the album art.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(chapter.thumbnailUri ?: chapter.folderUri)
                    .crossfade(false)
                    .memoryCacheKey("song_thumb_${chapter.id}")
                    .diskCacheKey("song_thumb_${chapter.id}")
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            if (isCurrent && isPlaying) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    AnimatedEqualizer(modifier = Modifier.size(20.dp), color = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = chapter.artist ?: "Unknown Artist",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = formatDuration(chapter.duration),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Light
        )
        
        if (selectionMode == SelectionMode.NONE) {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "Menu", tint = Color.White.copy(alpha = 0.4f))
                }
                HwaranDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    HwaranDropdownMenuItem(
                        text = "Add in Playlist",
                        leadingIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        onClick = { 
                            showMenu = false
                            onAddToPlaylist()
                        }
                    )
                    HwaranDropdownMenuItem(
                        text = "Refine Tag",
                        leadingIcon = Icons.Rounded.AutoFixHigh,
                        onClick = { 
                            showMenu = false
                            onEditTag()
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    HwaranDropdownMenuItem(
                        text = "Delete",
                        leadingIcon = Icons.Rounded.DeleteSweep,
                        isDanger = true,
                        onClick = { 
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    onDeleteFromPlaylist: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Song", color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Text("How do you want to remove this piece?", color = Color.White.copy(alpha = 0.7f)) },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDeleteFromPlaylist,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                ) {
                    Text("Remove from Playlist Only", color = Color.White)
                }
                Button(
                    onClick = onDeleteFromDevice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) {
                    Text("Delete from Device", color = Color.White, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        },
        containerColor = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(28.dp)
    )
}

private fun shareTracks(context: android.content.Context, tracks: List<ChapterEntity>) {
    if (tracks.isEmpty()) return
    try {
        val uris = ArrayList<Uri>()
        for (track in tracks) {
            val uri = if (track.folderUri.startsWith("content://")) {
                Uri.parse(track.folderUri)
            } else {
                val file = File(track.folderUri)
                if (file.exists()) {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                } else null
            }
            if (uri != null) {
                uris.add(uri)
            }
        }

        if (uris.isEmpty()) {
            android.widget.Toast.makeText(context, "No audio files available to share", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share Audio"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error sharing audio: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

