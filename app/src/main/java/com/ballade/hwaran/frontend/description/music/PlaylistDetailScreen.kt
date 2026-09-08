package com.ballade.hwaran.frontend.description.music

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

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

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
                contentPadding = PaddingValues(top = 120.dp, bottom = 120.dp)
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

                        // Rotating Add Music button with Haptic Feedback
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
                                modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = rotationAngle }
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = chapters,
                    key = { _, chapter -> chapter.id }
                ) { index, chapter ->
                    val isCurrent = currentChapter?.id == chapter.id
                    SongItem(
                        chapter = chapter,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onAddToPlaylist = { 
                            songToAddToPlaylist = chapter
                            showPlaylistDialog = true
                        },
                        onEditTag = { onNavigateToEditSong(chapter.id) },
                        onDelete = {
                            songToDelete = chapter
                            showDeleteDialog = true
                        }
                    ) {
                        musicViewModel.playPlaylist(manga!!, chapters, index)
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
                        .padding(top = 16.dp, bottom = 12.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 12.dp, top = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack, 
                            contentDescription = "Back", 
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
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
    }
}

@Composable
fun SongItem(
    chapter: ChapterEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onAddToPlaylist: () -> Unit,
    onEditTag: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        
        var showMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "Menu", tint = Color.White.copy(alpha = 0.4f))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = Color(0xFF161616),
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(vertical = 4.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Add in Playlist", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) },
                    onClick = { 
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Refine Tag", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) },
                    onClick = { 
                        showMenu = false
                        onEditTag()
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White.copy(alpha = 0.05f))
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFE57373).copy(alpha = 0.7f), fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFE57373).copy(alpha = 0.5f), modifier = Modifier.size(20.dp)) },
                    onClick = { 
                        showMenu = false
                        onDelete()
                    }
                )
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

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
