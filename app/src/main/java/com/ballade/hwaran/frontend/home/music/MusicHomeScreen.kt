package com.ballade.hwaran.frontend.home.music

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.ui.components.JellyBall
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.ui.viewmodels.LibraryViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import com.ballade.hwaran.ui.components.WobblySnakeRing
import com.ballade.hwaran.ui.components.DeleteConfirmationDialog
import android.graphics.BitmapFactory
import androidx.compose.ui.draw.blur
import androidx.palette.graphics.Palette
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.theme.LocalBatterySaving
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.core.animate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

@Composable
fun MusicHomeScreen(
    libraryViewModel: LibraryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onImportMusic: () -> Unit,
    pillGradient: Brush,
    onBack: (() -> Unit)? = null
) {
    MusicScreen(libraryViewModel, settingsViewModel, musicViewModel, onNavigateToPlaylistDetail, onImportMusic, pillGradient, onBack)
}

@Composable
fun MusicScreen(
    libraryViewModel: LibraryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onImportMusic: () -> Unit,
    pillGradient: Brush,
    onBack: (() -> Unit)? = null
) {
    val allManga by libraryViewModel.allMangaState.collectAsState()
    val isLoading by libraryViewModel.isLoading.collectAsState()
    
    val musicPlaylists = remember(allManga) {
        allManga.filter { it.contentType == 3 }
    }
    
    val homeUiTransparency by settingsViewModel.homeUiTransparency.collectAsState()
    val glowColor by settingsViewModel.glowColor.collectAsState()
    val isLibraryLocked by settingsViewModel.isLibraryLocked.collectAsState()
    val libraryPassword by settingsViewModel.libraryPassword.collectAsState()
    
    val currentManga by musicViewModel.currentManga.collectAsState()
    val currentChapter by musicViewModel.currentChapter.collectAsState()
    
    val isImporting by libraryViewModel.isImporting.collectAsState()
    val importProgress by libraryViewModel.importProgress.collectAsState()
    
    var isEditMode by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var selectedPlaylistIdsForDelete by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<MangaEntity?>(null) }
    var playlistToUnlock by remember { mutableStateOf<MangaEntity?>(null) }
    var unlockPasswordInput by remember { mutableStateOf("") }
    var showIncorrectPassword by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val musicFolderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // No workspaces in Music mode yet, so pass null
            libraryViewModel.importMusicFolder(it, null)
        }
    }
    
    val haptic = LocalHapticFeedback.current

    // Trigger color extraction for all playlists in batch for immediate availability
    LaunchedEffect(musicPlaylists) {
        musicViewModel.extractPlaylistColors(musicPlaylists)
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // Fast background blur from active playing song
        if (currentChapter != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentChapter?.thumbnailUri?.takeIf { it.isNotBlank() } ?: currentManga?.coverPath?.takeIf { it.isNotBlank() })
                        .size(64, 64)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 8.dp)
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = homeUiTransparency }
        ) {
            if (isLoading) {
                // Show nothing while loading
            } else if (musicPlaylists.isEmpty()) {
                val messages = remember {
                    listOf(
                        "It's so quiet... I'm falling asleep.",
                        "No music? My jelly is solidifying...",
                        "Tap me to import some tunes!",
                        "I can't dance without a beat...",
                        "Yawn... I need some rhythm.",
                        "A world without music is just... empty.",
                        "Play something, anything!",
                        "*sigh* Even white noise would be nice.",
                        "I'm a music player mascot with no music...",
                        "If I had ears, they would be sad right now.",
                        "I'm going to take a nap until you play a song.",
                        "Are you going to leave me in silence?",
                        "Waiting for the bass to drop... still waiting.",
                        "Please feed me some audio files...",
                        "I'm levitating out of sheer boredom."
                    )
                }

                var messageIndex by remember { mutableIntStateOf(0) }
                
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(4000)
                        messageIndex = (messageIndex + 1) % messages.size
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(), 
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        AnimatedContent(
                            targetState = messages[messageIndex],
                            transitionSpec = {
                                (fadeIn(tween(600)) + scaleIn(initialScale = 0.8f, transformOrigin = TransformOrigin(0.5f, 1f))) togetherWith
                                (fadeOut(tween(400)) + scaleOut(targetScale = 0.8f, transformOrigin = TransformOrigin(0.5f, 1f)))
                            },
                            label = "chat_anim"
                        ) { msg ->
                            ChatBubble(
                                message = msg,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        musicFolderPickerLauncher.launch(null)
                                    }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(18.dp))
                        
                        // Signature interactive JellyBall Mascot
                        JellyBall(
                            modifier = Modifier
                                .size(160.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    musicFolderPickerLauncher.launch(null)
                                },
                            enableJump = true,
                            isHappy = false,
                            lookUp = true
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Add Songs Action Button
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                musicFolderPickerLauncher.launch(null)
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = "Add Songs",
                                    tint = Color(0xFFEC407A),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Add Songs & Playlists",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                val favoritesPlaylist = musicPlaylists.find { it.title.equals("Favorites", ignoreCase = true) }
                val otherPlaylists = musicPlaylists.filter { it.id != favoritesPlaylist?.id }
                val chunks = otherPlaylists.chunked(3)
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = if (isLandscape) 70.dp else 130.dp, 
                        start = if (isLandscape) 80.dp else 0.dp, 
                        bottom = if (isLandscape) 40.dp else 140.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 40.dp else 80.dp)
                ) {
                    if (favoritesPlaylist != null) {
                        item(key = "favorites_isolated") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    PlaylistCircleItemBackground(
                                        playlist = favoritesPlaylist,
                                        musicViewModel = musicViewModel,
                                        size = 235.dp,
                                        glowColor = Color(glowColor),
                                        isSelectedForDelete = selectedPlaylistIdsForDelete.contains(favoritesPlaylist.id),
                                        seed = favoritesPlaylist.id.toInt()
                                    )
                                    PlaylistCircleItemForeground(
                                        playlist = favoritesPlaylist,
                                        size = 235.dp,
                                        isEditMode = isEditMode,
                                        isSelectedForDelete = selectedPlaylistIdsForDelete.contains(favoritesPlaylist.id),
                                        isMenuExpanded = isMenuExpanded,
                                        onPlaylistClick = {
                                            if (isDeleteMode) {
                                                selectedPlaylistIdsForDelete = if (selectedPlaylistIdsForDelete.contains(favoritesPlaylist.id)) {
                                                    selectedPlaylistIdsForDelete - favoritesPlaylist.id
                                                } else {
                                                    selectedPlaylistIdsForDelete + favoritesPlaylist.id
                                                }
                                            } else if (isLibraryLocked && favoritesPlaylist.isLocked) {
                                                playlistToUnlock = favoritesPlaylist
                                            } else {
                                                onNavigateToPlaylistDetail(favoritesPlaylist.id)
                                            }
                                        },
                                        onEditClick = { editingPlaylist = favoritesPlaylist },
                                        pencilAlignment = Alignment.BottomStart
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(chunks) { index, chunk ->
                        PlaylistMessyGroup(
                            playlists = chunk,
                            groupIndex = index,
                            musicViewModel = musicViewModel,
                            glowColor = Color(glowColor),
                            isEditMode = isEditMode,
                            isDeleteMode = isDeleteMode,
                            selectedDeletePlaylistIds = selectedPlaylistIdsForDelete,
                            isMenuExpanded = isMenuExpanded,
                            isLandscape = isLandscape,
                            onPlaylistClick = { playlist ->
                                if (playlist.id == -999L) {
                                    isMenuExpanded = !isMenuExpanded
                                } else {
                                    if (isDeleteMode) {
                                        selectedPlaylistIdsForDelete = if (selectedPlaylistIdsForDelete.contains(playlist.id)) {
                                            selectedPlaylistIdsForDelete - playlist.id
                                        } else {
                                            selectedPlaylistIdsForDelete + playlist.id
                                        }
                                    } else if (isLibraryLocked && playlist.isLocked) {
                                        playlistToUnlock = playlist
                                    } else {
                                        onNavigateToPlaylistDetail(playlist.id)
                                    }
                                }
                            },
                            onEditClick = { editingPlaylist = it },
                            onDeleteClick = { /* Handled via selection */ },
                            modifier = Modifier
                        )
                    }
                }
            }

            // Bottom Delete Button - Elevated to avoid MiniPlayer overlap
            AnimatedVisibility(
                visible = isDeleteMode && selectedPlaylistIdsForDelete.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 280.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(56.dp).padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        if (selectedPlaylistIdsForDelete.size == 1) "Delete 1 Playlist" 
                        else "Delete ${selectedPlaylistIdsForDelete.size} Playlists", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Overlay to block misclicks when menu is expanded
            if (isMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isMenuExpanded = false }
                )
            }

            // Back Button at TopStart
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isLandscape) 24.dp else 64.dp, start = 24.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Action Menu & Trigger - Repositioned to TopEnd with Horizontal Animation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isLandscape) 24.dp else 64.dp, end = 24.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(
                        visible = isMenuExpanded || isEditMode || isDeleteMode,
                        enter = expandHorizontally(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                            expandFrom = Alignment.End
                        ) + fadeIn(animationSpec = tween(400)),
                        exit = shrinkHorizontally(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                            shrinkTowards = Alignment.End
                        ) + fadeOut(animationSpec = tween(300))
                    ) {
                        Row(
                            modifier = Modifier
                                .height(52.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.12f),
                                            Color.White.copy(alpha = 0.06f)
                                        )
                                    ), 
                                    RoundedCornerShape(26.dp)
                                )
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    musicFolderPickerLauncher.launch(null)
                                    isMenuExpanded = false
                                },
                                enabled = !isEditMode && !isDeleteMode,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Add, 
                                    contentDescription = "Import", 
                                    tint = if (isEditMode || isDeleteMode) Color.White.copy(alpha = 0.2f) else Color.White
                                )
                            }

                            IconButton(
                                onClick = { 
                                    isEditMode = !isEditMode
                                    isDeleteMode = false
                                    isMenuExpanded = false
                                    selectedPlaylistIdsForDelete = emptySet()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = "Toggle Edit",
                                    tint = if (isEditMode) Color(glowColor) else Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    isDeleteMode = !isDeleteMode
                                    isEditMode = false
                                    isMenuExpanded = false
                                    selectedPlaylistIdsForDelete = emptySet()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteSweep,
                                    contentDescription = "Toggle Delete",
                                    tint = if (isDeleteMode) Color(0xFFE57373) else Color.White
                                )
                            }
                        }
                    }

                    Box(contentAlignment = Alignment.Center) {
                        // Halo Progress Indicator - Organic and Confident
                        if (isImporting) {
                            val density = LocalDensity.current
                            
                            var fakeProgress by remember { mutableFloatStateOf(0f) }
                            LaunchedEffect(isImporting, importProgress) {
                                if (isImporting && importProgress == 0) {
                                    animate(
                                        initialValue = fakeProgress,
                                        targetValue = 0.15f,
                                        animationSpec = tween(durationMillis = 4000, easing = LinearOutSlowInEasing)
                                    ) { value, _ ->
                                        if (importProgress == 0) {
                                            fakeProgress = value
                                        }
                                    }
                                }
                            }

                            val displayedProgress = if (importProgress > 0) (importProgress / 100f).coerceIn(0f, 1f) else fakeProgress

                            val animatedProgress by animateFloatAsState(
                                targetValue = displayedProgress,
                                animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
                                label = "importProgress"
                            )
                            
                            Box(contentAlignment = Alignment.Center) {
                                WobblySnakeRing(
                                    modifier = Modifier.size(68.dp),
                                    colors = listOf(Color(glowColor), Color.White),
                                    progress = animatedProgress,
                                    strokeWidth = 3f,
                                    wobbleAmplitude = 5f, // Slightly more wobbly for "life"
                                    wobbleSpeed = 6000,   // Active rotation
                                    baseRadiusOverride = with(density) { 30.dp.toPx() },
                                    showOuterRing = false
                                )
                            }
                        }

                        IconButton(
                            onClick = { 
                                if (isEditMode || isDeleteMode) {
                                    isEditMode = false
                                    isDeleteMode = false
                                    isMenuExpanded = false
                                    selectedPlaylistIdsForDelete = emptySet()
                                } else {
                                    isMenuExpanded = !isMenuExpanded 
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (isEditMode || isDeleteMode) Color(0xFFE57373).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f), 
                                    CircleShape
                                )
                                .then(
                                    if (isImporting) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (isMenuExpanded) isMenuExpanded = false
                                            else isMenuExpanded = true
                                        }
                                    } else Modifier
                                )
                        ) {
                            Icon(
                                if (isEditMode || isDeleteMode) Icons.Rounded.Close
                                else if (isMenuExpanded) Icons.AutoMirrored.Rounded.KeyboardArrowRight
                                else if (isImporting) Icons.Rounded.Downloading
                                else Icons.Rounded.Tune,
                                contentDescription = "Actions",
                                tint = if (isEditMode || isDeleteMode) Color(0xFFE57373) else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog && selectedPlaylistIdsForDelete.isNotEmpty()) {
        val count = selectedPlaylistIdsForDelete.size
        DeleteConfirmationDialog(
            title = "Delete ${if (count == 1) "Playlist" else "$count Playlists"}",
            message = "Remove from App removes the playlist entry only — your music files remain on disk. Delete from Disk also removes the playlist and attempts to delete associated audio files.",
            onDismiss = { showDeleteConfirmDialog = false },
            onRemoveFromApp = {
                selectedPlaylistIdsForDelete.forEach { id ->
                    musicViewModel.stopIfPlaylistDeleted(id)
                    libraryViewModel.deletePlaylist(id)
                }
                showDeleteConfirmDialog = false
                selectedPlaylistIdsForDelete = emptySet()
                isDeleteMode = false
            },
            onDeleteFromDisk = {
                selectedPlaylistIdsForDelete.forEach { id ->
                    musicViewModel.stopIfPlaylistDeleted(id)
                    libraryViewModel.deletePlaylist(id)
                }
                showDeleteConfirmDialog = false
                selectedPlaylistIdsForDelete = emptySet()
                isDeleteMode = false
            }
        )
    }

    if (playlistToUnlock != null) {
        AlertDialog(
            onDismissRequest = {
                playlistToUnlock = null
                unlockPasswordInput = ""
                showIncorrectPassword = false
            },
            title = { Text("Unlock Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
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
                            focusedBorderColor = Color(glowColor),
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
                        val id = playlistToUnlock!!.id
                        playlistToUnlock = null
                        unlockPasswordInput = ""
                        showIncorrectPassword = false
                        onNavigateToPlaylistDetail(id)
                    } else {
                        showIncorrectPassword = true
                    }
                }) {
                    Text("Unlock", color = Color(glowColor), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    playlistToUnlock = null
                    unlockPasswordInput = ""
                    showIncorrectPassword = false
                }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            },
            containerColor = Color(0xFF1B1924)
        )
    }

    if (editingPlaylist != null) {
        EditPlaylistDialog(
            playlist = editingPlaylist!!,
            onDismiss = { editingPlaylist = null },
            onSave = { id, title, artist, cover ->
                libraryViewModel.updateMangaMetadata(id, title, artist, cover)
                editingPlaylist = null
            }
        )
    }
}

data class MessyConfig(
    val size: androidx.compose.ui.unit.Dp,
    val seedOffset: Int,
    val pencilAlignment: Alignment,
    val offsetX: androidx.compose.ui.unit.Dp,
    val offsetY: androidx.compose.ui.unit.Dp
)

@Composable
fun PlaylistMessyGroup(
    playlists: List<MangaEntity>,
    groupIndex: Int,
    musicViewModel: MusicViewModel,
    glowColor: Color,
    isEditMode: Boolean,
    isDeleteMode: Boolean,
    selectedDeletePlaylistIds: Set<Long>,
    isMenuExpanded: Boolean,
    isLandscape: Boolean = false,
    onPlaylistClick: (MangaEntity) -> Unit,
    onEditClick: (MangaEntity) -> Unit,
    onDeleteClick: (MangaEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutType = groupIndex % 4
    
    val configs = when (layoutType) {
        0 -> listOf(
            MessyConfig(235.dp, 0, Alignment.BottomStart, (-35).dp, (-15).dp),
            MessyConfig(145.dp, 100, Alignment.TopEnd, 105.dp, (-95).dp),
            MessyConfig(165.dp, 200, Alignment.BottomEnd, 95.dp, 85.dp)
        )
        1 -> listOf(
            MessyConfig(225.dp, 0, Alignment.BottomEnd, 45.dp, (-10).dp),
            MessyConfig(150.dp, 100, Alignment.TopStart, (-100).dp, (-85).dp),
            MessyConfig(140.dp, 200, Alignment.BottomStart, (-85).dp, 90.dp)
        )
        2 -> listOf(
            MessyConfig(230.dp, 0, Alignment.TopCenter, 0.dp, (-65).dp),
            MessyConfig(155.dp, 100, Alignment.BottomStart, (-90).dp, 75.dp),
            MessyConfig(160.dp, 200, Alignment.BottomEnd, 90.dp, 90.dp)
        )
        else -> listOf(
            MessyConfig(175.dp, 0, Alignment.TopStart, (-80).dp, (-95).dp),
            MessyConfig(230.dp, 100, Alignment.CenterEnd, 10.dp, 15.dp),
            MessyConfig(150.dp, 200, Alignment.BottomEnd, 100.dp, 115.dp)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp),
        contentAlignment = Alignment.Center
    ) {
        // Layer 2: Backgrounds (Wobbly rings, glow, and texts)
        playlists.forEachIndexed { index, playlist ->
            if (index < configs.size) {
                val config = configs[index]
                PlaylistCircleItemBackground(
                    playlist = playlist,
                    musicViewModel = musicViewModel,
                    size = config.size,
                    glowColor = glowColor,
                    isSelectedForDelete = selectedDeletePlaylistIds.contains(playlist.id),
                    seed = playlist.id.toInt() + config.seedOffset,
                    modifier = Modifier.offset(x = config.offsetX, y = config.offsetY)
                )
            }
        }
        
        // Layer 1: Foregrounds (Covers and edit buttons)
        playlists.forEachIndexed { index, playlist ->
            if (index < configs.size) {
                val config = configs[index]
                PlaylistCircleItemForeground(
                    playlist = playlist,
                    size = config.size,
                    isEditMode = isEditMode,
                    isSelectedForDelete = selectedDeletePlaylistIds.contains(playlist.id),
                    isMenuExpanded = isMenuExpanded,
                    onPlaylistClick = { onPlaylistClick(playlist) },
                    onEditClick = { onEditClick(playlist) },
                    pencilAlignment = config.pencilAlignment,
                    modifier = Modifier.offset(x = config.offsetX, y = config.offsetY)
                )
            }
        }
    }
}

@Composable
fun PlaylistCircleItemBackground(
    playlist: MangaEntity,
    musicViewModel: MusicViewModel,
    size: androidx.compose.ui.unit.Dp,
    glowColor: Color,
    isSelectedForDelete: Boolean,
    seed: Int = 0,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val playlistPalettes by musicViewModel.playlistPalettes.collectAsState()
    
    val targetColors = remember(playlist.coverPath, playlistPalettes, isSelectedForDelete) {
        if (isSelectedForDelete) {
            listOf(Color(0xFFE57373), Color(0xFFE57373))
        } else if (playlist.coverPath.isEmpty() || playlist.coverPath == "android.resource://android/drawable/ic_menu_gallery") {
            listOf(glowColor)
        } else {
            val entry = playlistPalettes[playlist.id]
            if (entry != null && entry.first == playlist.coverPath) {
                val palette = entry.second
                listOf(Color(palette.dominant), Color(palette.vibrant), Color(palette.muted), Color(palette.dominant))
            } else {
                listOf(Color.Transparent)
            }
        }
    }

    val batterySaving = LocalBatterySaving.current
    val glowAlphaState = if (batterySaving) {
        remember { mutableFloatStateOf(0.7f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500 + (seed % 1000), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        ).let { remember { it } }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(size + 60.dp)
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            // Lightweight glow
            Box(
                modifier = Modifier
                    .size(size * 1.4f)
                    .drawBehind {
                        val glowAlpha = glowAlphaState.value
                        if (targetColors.isNotEmpty() && targetColors.first() != Color.Transparent) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        targetColors[0].copy(alpha = 0.10f * glowAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                        }
                    }
            )

            WobblySnakeRing(
                modifier = Modifier.fillMaxSize(),
                colors = targetColors,
                wobbleSpeed = 8000 + (seed % 4000),
                wobbleCount = 4 + (seed % 2),
                wobbleAmplitude = 8f + (seed % 3),
                strokeWidth = 4.5f,
                phaseOffset = (seed % 15).toFloat()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = playlist.title,
            color = Color.White,
            fontSize = if (size > 200.dp) 22.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun PlaylistCircleItemForeground(
    playlist: MangaEntity,
    size: androidx.compose.ui.unit.Dp,
    isEditMode: Boolean,
    isSelectedForDelete: Boolean,
    isMenuExpanded: Boolean,
    onPlaylistClick: () -> Unit,
    onEditClick: () -> Unit,
    pencilAlignment: Alignment = Alignment.TopEnd,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(size + 60.dp)
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.76f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable(
                        enabled = !isEditMode && !isMenuExpanded,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPlaylistClick() }
            ) {
                val bubbleContext = LocalContext.current
                val resolvedCover = remember(playlist.coverPath, playlist.parentUri) {
                    com.ballade.hwaran.core.util.CoverArtResolver.resolveCoverModel(
                        coverPath = playlist.coverPath,
                        parentUri = playlist.parentUri,
                        chapters = null,
                        context = bubbleContext
                    )
                }
                if (resolvedCover != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(bubbleContext)
                            .data(resolvedCover)
                            .crossfade(true)
                            .build(),
                        contentDescription = playlist.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote, 
                            contentDescription = null, 
                            tint = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.size(size * 0.3f)
                        )
                    }
                }
                
                if (isSelectedForDelete) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE57373).copy(alpha = 0.3f)))
                }
            }
            
            if (isEditMode) {
                IconButton(
                    onClick = { onEditClick() },
                    modifier = Modifier
                        .align(pencilAlignment)
                        .size(38.dp)
                        .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoFixHigh, 
                        contentDescription = "Action", 
                        tint = Color.White, 
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Invisible text to keep the height consistent with the background component
        Text(
            text = playlist.title,
            color = Color.Transparent,
            fontSize = if (size > 200.dp) 22.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun EditPlaylistDialog(
    playlist: MangaEntity,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(playlist.title) }
    var coverPath by remember { mutableStateOf(playlist.coverPath) }
    
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            val cached = com.ballade.hwaran.core.util.CoverCacheManager.cacheCoverFromUri(context, it, "playlist", title.ifBlank { "playlist" })
            coverPath = cached ?: it.toString()
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = if (isLandscape) 600.dp else 400.dp)
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
        containerColor = Color(0xFF0F0F0F),
        title = null,
        text = {
            val content = @Composable {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 16.dp else 24.dp)
                ) {
                    Text(
                        text = "Refine Playlist",
                        color = Color.White,
                        fontSize = if (isLandscape) 20.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )

                    val innerContent = @Composable {
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 120.dp else 160.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (coverPath.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(coverPath)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                                        )
                                    ),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .padding(bottom = 12.dp)
                                        .size(if (isLandscape) 36.dp else 42.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.PhotoCamera,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(if (isLandscape) 18.dp else 20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.then(if (isLandscape) Modifier.width(300.dp) else Modifier.fillMaxWidth()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Playlist Name",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                placeholder = { Text("Enter title...", color = Color.White.copy(alpha = 0.2f)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedContainerColor = Color.White.copy(alpha = 0.02f),
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            innerContent()
                        }
                    } else {
                        innerContent()
                    }
                }
            }
            content()
        },
        confirmButton = {
            Button(
                onClick = { onSave(playlist.id, title, playlist.description, coverPath) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 48.dp else 56.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text("Update Playlist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isLandscape) 0.dp else 8.dp)
            ) {
                Text("Discard Changes", color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
fun ChatBubble(message: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .background(Color(0xFF1A1A1A).copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
        Canvas(modifier = Modifier.size(16.dp, 10.dp).offset(y = (-1).dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, Color(0xFF1A1A1A).copy(alpha = 0.9f))
        }
    }
}
