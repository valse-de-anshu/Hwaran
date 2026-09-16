package com.ballade.hwaran.frontend.player.music

import androidx.compose.material.icons.rounded.MusicNote
import com.ballade.hwaran.ui.components.DeleteConfirmationDialog

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenu
import com.ballade.hwaran.ui.dialogs.HwaranDropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.painterResource
import com.ballade.hwaran.ui.components.WavyMusicSlider
import com.ballade.hwaran.ui.components.MusicBackground
import com.ballade.hwaran.ui.dialogs.PlaylistSelectionDialog
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.viewmodels.MusicViewModel.ShuffleMode
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import com.ballade.hwaran.ui.viewmodels.LibraryViewModel
import com.ballade.hwaran.ui.theme.LocalBatterySaving
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import com.ballade.hwaran.ui.dialogs.GenreSelectionDialog
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch



@Composable
fun NowPlayingScreen(
    musicViewModel: MusicViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEditSong: (Long) -> Unit
) {
    MusicPlayerScreen(musicViewModel, settingsViewModel, libraryViewModel, onNavigateBack, onNavigateToEditSong)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    musicViewModel: MusicViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEditSong: (Long) -> Unit
) {
    val currentManga by musicViewModel.currentManga.collectAsState()
    val currentChapter by musicViewModel.currentChapter.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val playbackProgress by musicViewModel.playbackProgress.collectAsState()
    val currentPosition by musicViewModel.currentPosition.collectAsState()
    val totalDuration by musicViewModel.totalDuration.collectAsState()
    val colorPalette by musicViewModel.colorPalette.collectAsState()
    val musicMode by settingsViewModel.musicMode.collectAsState()
    val currentPlaylist by musicViewModel.currentPlaylist.collectAsState()
    val allManga by libraryViewModel.allMangaState.collectAsState()
    val playlists = allManga.filter { it.contentType == 3 }
    
    val shuffleMode by musicViewModel.shuffleMode.collectAsState()
    val currentShuffleMode by musicViewModel.currentShuffleMode.collectAsState()
    val repeatMode by musicViewModel.repeatMode.collectAsState()

    // Boundary Logic for Dimming Controls
    val currentIndex = remember(currentPlaylist, currentChapter) { 
        currentPlaylist.indexOfFirst { it.id == currentChapter?.id } 
    }
    val hasNext = remember(currentIndex, currentPlaylist.size, shuffleMode, repeatMode) {
        if (currentPlaylist.size <= 1) false
        else if (shuffleMode || repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) true
        else currentIndex < currentPlaylist.size - 1
    }
    val hasPrevious = remember(currentIndex, currentPlaylist.size, shuffleMode, repeatMode) {
        if (currentPlaylist.size <= 1) false
        else if (shuffleMode || repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) true
        else currentIndex > 0
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showRepeatMenu by rememberSaveable { mutableStateOf(false) }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showQueueDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showGenreDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsDialog by rememberSaveable { mutableStateOf(false) }
    var isLyricsMode by rememberSaveable { mutableStateOf(false) }

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val rawBytes = inputStream.readBytes()
                    val content = try {
                        String(rawBytes, java.nio.charset.StandardCharsets.UTF_8).removePrefix("\uFEFF")
                    } catch (e: Exception) {
                        String(rawBytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                    }
                    if (content.isNotBlank()) {
                        musicViewModel.updateCurrentChapterLyrics(content)
                        scope.launch {
                            snackbarHostState.showSnackbar("Lyrics loaded")
                        }
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Failed to read lyrics: ${e.message}")
                }
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val controlIconColor = Color.White

    androidx.activity.compose.BackHandler {
        when {
            showMenu -> showMenu = false
            showRepeatMenu -> showRepeatMenu = false
            showPlaylistDialog -> showPlaylistDialog = false
            showQueueDialog -> showQueueDialog = false
            showDeleteDialog -> showDeleteDialog = false
            showGenreDialog -> showGenreDialog = false
            showLyricsDialog -> showLyricsDialog = false
            isLyricsMode -> isLyricsMode = false
            else -> onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Blur
        MusicBackground(
            currentChapter = currentChapter,
            currentManga = currentManga
        )
if (isLandscape) {
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).statusBarsPadding()
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Album Art / Conductor
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .aspectRatio(1f)
                        .then(
                            if (!isLyricsMode) {
                                Modifier.shadow(
                                    elevation = 12.dp,
                                    shape = RoundedCornerShape(32.dp),
                                    spotColor = Color(colorPalette.vibrant).copy(alpha = 0.35f),
                                    ambientColor = Color.Transparent
                                )
                            } else Modifier
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isLyricsMode = !isLyricsMode }
                    ) {
                        AnimatedContent(
                            targetState = isLyricsMode,
                            transitionSpec = { fadeIn(animationSpec = tween(240)) togetherWith fadeOut(animationSpec = tween(240)) },
                            label = "lyrics_transition"
                        ) { targetLyricsMode ->
                            if (targetLyricsMode) {
                                SyncedLyricsView(
                                    lyrics = currentChapter?.lyrics,
                                    currentPositionMs = currentPosition,
                                    onOpenLyricsDialog = { showLyricsDialog = true }
                                )
                            } else {
                                val cover = currentChapter?.thumbnailUri?.takeIf { it.isNotBlank() }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(32.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!cover.isNullOrEmpty() && cover != "android.resource://android/drawable/ic_menu_gallery") {
                                        AsyncImage(model = ImageRequest.Builder(context).data(cover).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (musicMode == 1) Modifier.blur(10.dp).graphicsLayer { alpha = 0.6f } else Modifier))
                                    } else {
                                        androidx.compose.material3.Icon(
                                            Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.1f),
                                            modifier = Modifier.size(100.dp)
                                        )
                                    }
                                    if (musicMode == 1) {
                                        val genre = currentChapter?.genre ?: currentManga?.genre ?: "Skip"
                                        if (!LocalBatterySaving.current) {
                                            ConductorAnimation(isPlaying = isPlaying, genre = genre)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Conductor Style chip (only shown when conductor is enabled and not in lyrics mode)
                    if (musicMode == 1 && !isLyricsMode) {
                        Surface(
                            onClick = { showGenreDialog = true },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "Conductor Style",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val activeGenre = currentChapter?.genre?.takeIf { it.isNotBlank() && it != "Skip" }
                                    ?: currentManga?.genre?.takeIf { it.isNotBlank() && it != "Skip" }
                                    ?: "Default"
                                Text(
                                    text = activeGenre,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Right Side: Info and Controls
                Column(
                    modifier = Modifier.weight(1.3f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.weight(0.1f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            MarqueeTextWithFade(
                                text = currentChapter?.title ?: "No Song Playing",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(text = currentChapter?.artist ?: "Unknown Artist", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var sliderPosition by remember { mutableStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }
                    LaunchedEffect(playbackProgress) { if (!isDragging) sliderPosition = playbackProgress }

                    WavyMusicSlider(
                        value = sliderPosition,
                        onValueChange = { isDragging = true; sliderPosition = it },
                        onValueChangeFinished = { isDragging = false; musicViewModel.seekTo(sliderPosition) },
                        isPlaying = isPlaying,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        thumbColor = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = formatTime((sliderPosition * totalDuration).toLong()), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text(text = "-${formatTime(((1f - sliderPosition) * totalDuration).toLong())}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                        ShuffleControlMenu(
                            currentShuffleMode = currentShuffleMode,
                            onSelectMode = { musicViewModel.setShuffleMode(it) }
                        )
                        
                        IconButton(onClick = { musicViewModel.previous() }, enabled = hasPrevious) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.25f), modifier = Modifier.size(40.dp))
                        }
                        Surface(onClick = { musicViewModel.togglePlayPause() }, shape = CircleShape, color = Color.White, modifier = Modifier.size(60.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(36.dp))
                            }
                        }
                        IconButton(onClick = { musicViewModel.next() }, enabled = hasNext) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.25f), modifier = Modifier.size(40.dp))
                        }

                        Box {
                            IconButton(onClick = { showRepeatMenu = true }) {
                                Icon(
                                    imageVector = when(repeatMode) {
                                        androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                        androidx.media3.common.Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                                        else -> Icons.AutoMirrored.Rounded.ArrowRightAlt
                                    }, 
                                    contentDescription = null, 
                                    tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.5f)
                                )
                            }
                            HwaranDropdownMenu(
                                expanded = showRepeatMenu,
                                onDismissRequest = { showRepeatMenu = false }
                            ) {
                                HwaranDropdownMenuItem(
                                    text = "Play next song",
                                    leadingIcon = Icons.AutoMirrored.Rounded.ArrowRightAlt,
                                    onClick = { 
                                        showRepeatMenu = false
                                        musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF)
                                    }
                                )
                                HwaranDropdownMenuItem(
                                    text = "Repeat the same queue",
                                    leadingIcon = Icons.Rounded.Repeat,
                                    onClick = { 
                                        showRepeatMenu = false
                                        musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL)
                                    }
                                )
                                HwaranDropdownMenuItem(
                                    text = "Repeat the same song",
                                    leadingIcon = Icons.Rounded.RepeatOne,
                                    onClick = { 
                                        showRepeatMenu = false
                                        musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                            context.startActivity(intent)
                        }) {
                            Icon(
                                painter = painterResource(id = com.ballade.hwaran.R.drawable.ic_speaker),
                                contentDescription = "Cast",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { 
                            currentChapter?.let { chapter ->
                                try {
                                    val uri = Uri.parse(chapter.folderUri)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "audio/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Music File"))
                                } catch (e: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("Sharing failed") }
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.IosShare, contentDescription = "Share", tint = Color.White.copy(alpha = 0.7f))
                        }
                        IconButton(onClick = { showQueueDialog = true }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue", tint = Color.White.copy(alpha = 0.7f))
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Menu", tint = Color.White.copy(alpha = 0.7f))
                            }
                            
                            HwaranDropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                HwaranDropdownMenuItem(
                                    text = "Refine Tag",
                                    leadingIcon = Icons.Rounded.AutoFixHigh,
                                    onClick = { 
                                        showMenu = false
                                        currentChapter?.let { onNavigateToEditSong(it.id) }
                                    }
                                )
                                HwaranDropdownMenuItem(
                                    text = "Add in Playlist",
                                    leadingIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    onClick = { 
                                        showMenu = false
                                        showPlaylistDialog = true
                                    }
                                )
                                HwaranDropdownMenuItem(
                                    text = if (musicMode == 1) "Show Album Art" else "Show Conductor",
                                    leadingIcon = if (musicMode == 1) Icons.Rounded.Image else Icons.Rounded.GraphicEq,
                                    onClick = { 
                                        showMenu = false
                                        settingsViewModel.setMusicMode(if (musicMode == 1) 0 else 1)
                                    }
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                                HwaranDropdownMenuItem(
                                    text = "Delete",
                                    leadingIcon = Icons.Rounded.DeleteSweep,
                                    isDanger = true,
                                    onClick = { 
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Top Bar - Repositioned for Camera Safety
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 42.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Menu", tint = Color.White)
                    }
                    HwaranDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        HwaranDropdownMenuItem(
                            text = "Refine Tag",
                            leadingIcon = Icons.Rounded.AutoFixHigh,
                            onClick = { 
                                showMenu = false
                                currentChapter?.let { onNavigateToEditSong(it.id) }
                            }
                        )
                        HwaranDropdownMenuItem(
                            text = "Add in Playlist",
                            leadingIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            onClick = { 
                                showMenu = false
                                showPlaylistDialog = true
                            }
                        )
                        HwaranDropdownMenuItem(
                            text = if (musicMode == 1) "Show Album Art" else "Show Conductor",
                            leadingIcon = if (musicMode == 1) Icons.Rounded.Image else Icons.Rounded.GraphicEq,
                            onClick = { 
                                showMenu = false
                                settingsViewModel.setMusicMode(if (musicMode == 1) 0 else 1)
                            }
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                        HwaranDropdownMenuItem(
                            text = "Delete",
                            leadingIcon = Icons.Rounded.DeleteSweep,
                            isDanger = true,
                            onClick = { 
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Main Display Area (Album Cover or Lyrics)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .then(
                        if (!isLyricsMode) {
                            Modifier.shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(32.dp),
                                spotColor = Color(colorPalette.vibrant).copy(alpha = 0.35f),
                                ambientColor = Color.Transparent
                            )
                        } else Modifier
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isLyricsMode = !isLyricsMode }
                ) {
                    AnimatedContent(
                        targetState = isLyricsMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(240)) togetherWith fadeOut(animationSpec = tween(240))
                        },
                        label = "lyrics_transition"
                    ) { targetLyricsMode ->
                        if (targetLyricsMode) {
                            SyncedLyricsView(
                                lyrics = currentChapter?.lyrics,
                                currentPositionMs = currentPosition,
                                onOpenLyricsDialog = { showLyricsDialog = true }
                            )
                        } else {
                            val cover = currentChapter?.thumbnailUri?.takeIf { it.isNotBlank() }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(32.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!cover.isNullOrEmpty() && cover != "android.resource://android/drawable/ic_menu_gallery") {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(cover)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().then(
                                            if (musicMode == 1) Modifier.blur(10.dp).graphicsLayer { alpha = 0.6f } else Modifier
                                        )
                                    )
                                } else {
                                    androidx.compose.material3.Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier.size(150.dp)
                                    )
                                }
                                if (musicMode == 1) {
                                    val genre = currentChapter?.genre ?: currentManga?.genre ?: "Skip"
                                    if (!LocalBatterySaving.current) {
                                        ConductorAnimation(isPlaying = isPlaying, genre = genre)
                                    }
                                }
                            }
                        }
                    }
                }

                // Conductor Style chip (only shown when conductor is enabled and not in lyrics mode)
                if (musicMode == 1 && !isLyricsMode) {
                    Surface(
                        onClick = { showGenreDialog = true },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                            .height(38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Conductor Style",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val activeGenre = currentChapter?.genre?.takeIf { it.isNotBlank() && it != "Skip" }
                                ?: currentManga?.genre?.takeIf { it.isNotBlank() && it != "Skip" }
                                ?: "Default"
                            Text(
                                text = activeGenre,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Song Titles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    MarqueeTextWithFade(
                        text = currentChapter?.title ?: "No Song Playing",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = currentChapter?.artist ?: "Unknown Artist",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Slider
            var sliderPosition by remember { mutableStateOf(0f) }
            var isDragging by remember { mutableStateOf(false) }
            LaunchedEffect(playbackProgress) { if (!isDragging) sliderPosition = playbackProgress }

            Column(modifier = Modifier.fillMaxWidth()) {
                WavyMusicSlider(
                    value = sliderPosition,
                    onValueChange = { 
                        isDragging = true
                        sliderPosition = it 
                    },
                    onValueChangeFinished = { 
                        isDragging = false
                        musicViewModel.seekTo(sliderPosition)
                    },
                    isPlaying = isPlaying,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    thumbColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatTime((sliderPosition * totalDuration).toLong()), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    Text(text = "-${formatTime(((1f - sliderPosition) * totalDuration).toLong())}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                ShuffleControlMenu(
                    currentShuffleMode = currentShuffleMode,
                    onSelectMode = { musicViewModel.setShuffleMode(it) }
                )
                IconButton(
                    onClick = { musicViewModel.previous() },
                    enabled = hasPrevious
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious, 
                        contentDescription = null, 
                        tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.25f), 
                        modifier = Modifier.size(48.dp)
                    )
                }
                Surface(
                    onClick = { musicViewModel.togglePlayPause() },
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(40.dp))
                    }
                }
                IconButton(
                    onClick = { musicViewModel.next() },
                    enabled = hasNext
                ) {
                    Icon(
                        Icons.Rounded.SkipNext, 
                        contentDescription = null, 
                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.25f), 
                        modifier = Modifier.size(48.dp)
                    )
                }
                Box {
                    IconButton(onClick = { showRepeatMenu = true }) {
                        Icon(
                            imageVector = when(repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                androidx.media3.common.Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                                else -> Icons.AutoMirrored.Rounded.ArrowRightAlt
                            },
                            contentDescription = null, 
                            tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    HwaranDropdownMenu(
                        expanded = showRepeatMenu,
                        onDismissRequest = { showRepeatMenu = false }
                    ) {
                        HwaranDropdownMenuItem(
                            text = "Play next song",
                            leadingIcon = Icons.AutoMirrored.Rounded.ArrowRightAlt,
                            onClick = { 
                                showRepeatMenu = false
                                musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF)
                            }
                        )
                        HwaranDropdownMenuItem(
                            text = "Repeat the same queue",
                            leadingIcon = Icons.Rounded.Repeat,
                            onClick = { 
                                showRepeatMenu = false
                                musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL)
                            }
                        )
                        HwaranDropdownMenuItem(
                            text = "Repeat the same song",
                            leadingIcon = Icons.Rounded.RepeatOne,
                            onClick = { 
                                showRepeatMenu = false
                                musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Bottom row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), 
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { 
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Icon(
                        painter = painterResource(id = com.ballade.hwaran.R.drawable.ic_speaker),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { 
                    currentChapter?.let { chapter ->
                        try {
                            val uri = Uri.parse(chapter.folderUri)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Music File"))
                        } catch (e: Exception) {
                            scope.launch { snackbarHostState.showSnackbar("Sharing failed") }
                        }
                    }
                }) {
                    Icon(Icons.Rounded.IosShare, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = { showQueueDialog = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                }

                // Heart Button Logic
                val favoritedUris by libraryViewModel.favoritedUris.collectAsState()
                val isFavorite = currentChapter?.let { chapter ->
                    favoritedUris.contains(chapter.folderUri) ||
                    (chapter.title.isNotBlank() && favoritedUris.contains(chapter.title))
                } == true
                
                val heartScale by animateFloatAsState(
                    targetValue = if (isFavorite) 1.25f else 1f, 
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
                val heartColor by animateColorAsState(
                    targetValue = if (isFavorite) Color(0xFF4A0404) else Color.White.copy(alpha = 0.5f), 
                    animationSpec = tween(300)
                )
                val heartIcon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
                
                IconButton(
                    onClick = {
                        if (currentChapter != null) {
                            libraryViewModel.toggleFavorite(currentChapter!!)
                        }
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Main heart
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = heartScale
                                    scaleY = heartScale
                                }
                        ) {
                            Icon(
                                imageVector = heartIcon,
                                contentDescription = "Favorite",
                                tint = heartColor,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                }
            }
        }
    }

    // Feature Dialogs
        if (showGenreDialog) {
            GenreSelectionDialog(
                onGenreSelected = { newGenre ->
                    showGenreDialog = false
                    musicViewModel.updateCurrentChapterGenre(newGenre)
                    settingsViewModel.setMusicMode(1)
                },
                onDismiss = { showGenreDialog = false }
            )
        }

        if (showLyricsDialog) {
            LyricsManagementDialog(
                currentLyrics = currentChapter?.lyrics,
                onSaveLyrics = { newLyrics ->
                    musicViewModel.updateCurrentChapterLyrics(newLyrics)
                    scope.launch {
                        snackbarHostState.showSnackbar(if (newLyrics.isNullOrBlank()) "Lyrics reset" else "Lyrics updated")
                    }
                },
                onSelectLrcFile = {
                    showLyricsDialog = false
                    lrcPickerLauncher.launch(arrayOf("*/*"))
                },
                onDismiss = { showLyricsDialog = false }
            )
        }

        if (showPlaylistDialog && currentChapter != null) {
            PlaylistSelectionDialog(
                playlists = playlists,
                onPlaylistSelected = { playlistId ->
                    libraryViewModel.addSongToPlaylist(playlistId, currentChapter!!)
                    showPlaylistDialog = false
                    scope.launch { snackbarHostState.showSnackbar("Added to playlist") }
                },
                onCreatePlaylist = { name ->
                    libraryViewModel.createPlaylist(name)
                },
                onDismiss = { showPlaylistDialog = false }
            )
        }

        if (showDeleteDialog && currentChapter != null) {
            DeleteConfirmationDialog(
                title = "Delete Track",
                itemName = currentChapter!!.title,
                message = "Choose how you would like to remove this track:",
                onDismiss = { showDeleteDialog = false },
                onRemoveFromApp = {
                    libraryViewModel.deleteChapterOnlyFromDb(currentChapter!!.id)
                    showDeleteDialog = false
                    onNavigateBack()
                },
                onDeleteFromDisk = {
                    libraryViewModel.deleteSelectedChapters(listOf(currentChapter!!.id), deleteFromDisk = true)
                    showDeleteDialog = false
                    onNavigateBack()
                }
            )
        }

        if (showQueueDialog) {
            QueueDialog(
                queue = currentPlaylist,
                currentChapter = currentChapter,
                currentManga = currentManga,
                musicViewModel = musicViewModel,
                onDismiss = { showQueueDialog = false }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState, 
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
        ) { snackbarData ->
            val accentColor = remember(colorPalette) {
                if (colorPalette.vibrant != 0xFF000000) Color(colorPalette.vibrant) else Color(0xFF8B5CF6)
            }
            val msg = snackbarData.visuals.message
            val isSuccess = !msg.contains("failed", ignoreCase = true) && !msg.contains("error", ignoreCase = true)
            
            val vectorIcon = when {
                msg.contains("playlist", ignoreCase = true) -> Icons.AutoMirrored.Rounded.PlaylistAddCheck
                !isSuccess -> Icons.Rounded.ErrorOutline
                else -> null
            }
            val snackIconColor = if (isSuccess) accentColor else Color(0xFFEF5350)

            Card(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(24.dp),
                        clip = false,
                        spotColor = snackIconColor.copy(alpha = 0.4f)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F0F13).copy(alpha = 0.9f)
                ),
                border = BorderStroke(1.dp, snackIconColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(snackIconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (vectorIcon != null) {
                            Icon(
                                imageVector = vectorIcon,
                                contentDescription = null,
                                tint = snackIconColor,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = com.ballade.hwaran.R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = snackIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }

    LaunchedEffect(currentChapter?.id) {
        musicViewModel.refreshCurrentChapter()
    }
}

@Composable
fun QueueDialog(
    queue: List<com.ballade.hwaran.core.database.entity.ChapterEntity>,
    currentChapter: com.ballade.hwaran.core.database.entity.ChapterEntity?,
    currentManga: com.ballade.hwaran.core.database.entity.MangaEntity?,
    musicViewModel: com.ballade.hwaran.ui.viewmodels.MusicViewModel,
    onDismiss: () -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentIndex = remember(queue, currentChapter) { queue.indexOfFirst { it.id == currentChapter?.id } }
    
    LaunchedEffect(currentIndex) {
        if (currentIndex != -1) {
            listState.scrollToItem(currentIndex)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0F0F13),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${queue.size} songs in queue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Fading scroll list
                val fadeColor = Color.Transparent
                val solidColor = Color.Black
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .heightIn(max = 350.dp)
                        .graphicsLayer { alpha = 0.99f }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to fadeColor,
                                    0.08f to solidColor,
                                    0.92f to solidColor,
                                    1f to fadeColor
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    items(queue.size) { index ->
                        val item = queue[index]
                        val isPlaying = item.id == currentChapter?.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isPlaying) {
                                        Color(0xFF222631)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .then(
                                    if (isPlaying) Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .clickable {
                                    currentManga?.let { manga ->
                                        musicViewModel.playPlaylist(manga, queue, index)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = if (isPlaying) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = if (isPlaying) Color(0xFFE6E8EC) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.artist != null && item.artist.trim().isNotEmpty() && item.artist != "Unknown Artist") {
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = item.artist,
                                        color = if (isPlaying) Color.White.copy(alpha = 0.7f) else Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isPlaying) {
                                Text(
                                    text = "Playing",
                                    color = Color(0xFFE6E8EC),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
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
fun ConductorAnimation(isPlaying: Boolean, genre: String = "Skip") {
    val context = LocalContext.current
    var isLoaded by remember { mutableStateOf(false) }

    val webView = remember {
        android.webkit.WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            webChromeClient = android.webkit.WebChromeClient()
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null) 
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoaded = true
                }
            }
        }
    }

    LaunchedEffect(genre) {
        val g = genre.lowercase()
        val fileName = when (g) {
            "pop", "rock", "phonk", "jazz", "folk", "ambient", "dreamcore", "emotional", "depression", "lyrics" -> "conductors/jelly_conductor_$g.html"
            else -> "jelly_conductor.html"
        }
        isLoaded = false
        webView.loadUrl("file:///android_asset/$fileName")
    }

    LaunchedEffect(isPlaying, isLoaded) {
        if (isLoaded) {
            webView.evaluateJavascript("if(window.setPlaybackState) window.setPlaybackState(${isPlaying});", null)
        }
    }

    LaunchedEffect(genre, isLoaded) {
        if (isLoaded) {
            webView.evaluateJavascript("if(window.setGenre) window.setGenre('${genre}');", null)
        }
    }

    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format("%02d:%02d", mins, secs)
}

fun Modifier.horizontalFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadeWidth = 16.dp.toPx()
        val fraction = if (size.width > 0) (fadeWidth / size.width).coerceIn(0f, 0.5f) else 0f
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                fraction to Color.Black,
                (1f - fraction) to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
fun MarqueeTextWithFade(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var isOverflowing by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth

        LaunchedEffect(text, maxWidthPx) {
            val textLayoutResult = textMeasurer.measure(
                text = AnnotatedString(text),
                style = TextStyle(
                    fontSize = fontSize,
                    fontWeight = fontWeight
                ),
                maxLines = 1
            )
            isOverflowing = textLayoutResult.size.width > maxWidthPx
        }

        if (isOverflowing) {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalFade()
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
        } else {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

object LyricsParser {
    // Matches standard LRC timestamps: [mm:ss.xx], [mm:ss:xx], [mm:ss], [hh:mm:ss.xx], [m:ss.x], etc.
    private val TIMESTAMP_REGEX = Regex("""\[(?:(\d{1,2}):)?(\d{1,3}):(\d{2})(?:[.:](\d{1,4}))?\]""")
    private val OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

    fun parse(lyrics: String?): List<LyricLine> {
        if (lyrics.isNullOrBlank()) return emptyList()
        val cleanLyrics = lyrics.removePrefix("\uFEFF")
        val lines = cleanLyrics.lines()

        var globalOffsetMs = 0L
        for (rawLine in lines) {
            val offsetMatch = OFFSET_REGEX.find(rawLine)
            if (offsetMatch != null) {
                globalOffsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
            }
        }

        val result = mutableListOf<LyricLine>()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            // Skip metadata header lines like [ar:Artist], [ti:Title], [al:Album]
            if (line.startsWith("[") && line.contains(":") && !TIMESTAMP_REGEX.containsMatchIn(line)) {
                continue
            }
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val text = line.replace(TIMESTAMP_REGEX, "").trim()
                for (match in matches) {
                    val hours = match.groupValues[1].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
                    val minutes = match.groupValues[2].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[3].toLongOrNull() ?: 0L
                    val millisPart = match.groupValues.getOrNull(4).orEmpty()
                    val millis = when {
                        millisPart.isEmpty() -> 0L
                        millisPart.length == 1 -> millisPart.toLong() * 100L
                        millisPart.length == 2 -> millisPart.toLong() * 10L
                        else -> millisPart.take(3).padEnd(3, '0').toLong()
                    }
                    val totalMs = (hours * 3600_000L + minutes * 60_000L + seconds * 1000L + millis + globalOffsetMs).coerceAtLeast(0L)
                    result.add(LyricLine(timestampMs = totalMs, text = text))
                }
            }
        }
        return result.sortedBy { it.timestampMs }
    }
}

fun Modifier.fadingEdges(
    topFade: androidx.compose.ui.unit.Dp = 56.dp,
    bottomFade: androidx.compose.ui.unit.Dp = 56.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val topFadePx = topFade.toPx().coerceAtLeast(0f)
        val bottomFadePx = bottomFade.toPx().coerceAtLeast(0f)
        val topStop = (topFadePx / h).coerceIn(0f, 0.45f)
        val bottomStop = ((h - bottomFadePx) / h).coerceIn(0.55f, 1f)

        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                topStop to Color.Black,
                bottomStop to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
fun ShuffleControlMenu(
    currentShuffleMode: ShuffleMode,
    onSelectMode: (ShuffleMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val themeColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = when (currentShuffleMode) {
                    ShuffleMode.SMART -> Icons.Rounded.AutoAwesome
                    ShuffleMode.ADVANCE -> Icons.Rounded.Tune
                    ShuffleMode.NORMAL -> Icons.Rounded.Shuffle
                    ShuffleMode.OFF -> Icons.AutoMirrored.Rounded.ArrowRightAlt
                },
                contentDescription = "Shuffle Mode",
                tint = if (currentShuffleMode != ShuffleMode.OFF) Color.White else Color.White.copy(alpha = 0.5f)
            )
        }
        HwaranDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            HwaranDropdownMenuItem(
                text = "Smart shuffle mode",
                leadingIcon = Icons.Rounded.AutoAwesome,
                isActive = currentShuffleMode == ShuffleMode.SMART,
                activeColor = themeColor,
                onClick = {
                    expanded = false
                    onSelectMode(ShuffleMode.SMART)
                }
            )
            HwaranDropdownMenuItem(
                text = "Advance shuffle mode",
                leadingIcon = Icons.Rounded.Tune,
                isActive = currentShuffleMode == ShuffleMode.ADVANCE,
                activeColor = themeColor,
                onClick = {
                    expanded = false
                    onSelectMode(ShuffleMode.ADVANCE)
                }
            )
            HwaranDropdownMenuItem(
                text = "Normal shuffle mode",
                leadingIcon = Icons.Rounded.Shuffle,
                isActive = currentShuffleMode == ShuffleMode.NORMAL,
                activeColor = themeColor,
                onClick = {
                    expanded = false
                    onSelectMode(ShuffleMode.NORMAL)
                }
            )
            HwaranDropdownMenuItem(
                text = "Shuffle mode off",
                leadingIcon = Icons.AutoMirrored.Rounded.ArrowRightAlt,
                isActive = currentShuffleMode == ShuffleMode.OFF,
                activeColor = themeColor,
                onClick = {
                    expanded = false
                    onSelectMode(ShuffleMode.OFF)
                }
            )
        }
    }
}

@Composable
fun SyncedLyricsView(
    lyrics: String?,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit = {},
    onOpenLyricsDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val parsedLines = remember(lyrics) {
        LyricsParser.parse(lyrics)
    }

    if (lyrics.isNullOrBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No lyrics found.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onOpenLyricsDialog,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Lyrics")
                }
            }
        }
    } else if (parsedLines.isNotEmpty()) {
        val activeIndex = remember(currentPositionMs, parsedLines) {
            val idx = parsedLines.indexOfLast { it.timestampMs <= currentPositionMs }
            if (idx < 0) 0 else idx
        }
        val isStarted = remember(currentPositionMs, parsedLines) {
            parsedLines.any { it.timestampMs <= currentPositionMs }
        }

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .fadingEdges(topFade = 56.dp, bottomFade = 56.dp)
        ) {
            val density = LocalDensity.current
            val halfViewportDp = with(density) { (constraints.maxHeight / 2).toDp() }

            LaunchedEffect(activeIndex, listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                if (activeIndex in parsedLines.indices && listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                    val info = listState.layoutInfo
                    val targetCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    val item = info.visibleItemsInfo.find { it.index == activeIndex }
                    if (item != null) {
                        val itemCenter = item.offset + item.size / 2
                        val delta = (itemCenter - targetCenter).toFloat()
                        if (abs(delta) > 1f) {
                            listState.animateScrollBy(delta, tween(durationMillis = 350, easing = FastOutSlowInEasing))
                        }
                    } else {
                        listState.scrollToItem(activeIndex)
                        val postInfo = listState.layoutInfo
                        val postTarget = (postInfo.viewportStartOffset + postInfo.viewportEndOffset) / 2
                        val postItem = postInfo.visibleItemsInfo.find { it.index == activeIndex }
                        if (postItem != null) {
                            val itemCenter = postItem.offset + postItem.size / 2
                            val delta = (itemCenter - postTarget).toFloat()
                            listState.scrollBy(delta)
                        }
                    }
                }
            }

            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = halfViewportDp, bottom = halfViewportDp),
                userScrollEnabled = false
            ) {
                items(
                    count = parsedLines.size,
                    key = { index -> "${index}_${parsedLines[index].timestampMs}" }
                ) { index ->
                    val line = parsedLines[index]
                    val isCurrent = isStarted && (index == activeIndex)
                    val distance = abs(index - activeIndex)

                    val (alpha, fontSize, fontWeight) = when {
                        isCurrent -> Triple(1.0f, 24.sp, FontWeight.ExtraBold)
                        distance == 1 -> Triple(0.55f, 17.sp, FontWeight.SemiBold)
                        distance == 2 -> Triple(0.28f, 15.sp, FontWeight.Normal)
                        distance == 3 -> Triple(0.10f, 14.sp, FontWeight.Normal)
                        else -> Triple(0.0f, 13.sp, FontWeight.Normal)
                    }

                    Text(
                        text = line.text.ifBlank { "• • •" },
                        color = Color.White.copy(alpha = alpha),
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = if (isCurrent) {
                                androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.95f),
                                    blurRadius = 14f,
                                    offset = androidx.compose.ui.geometry.Offset(0f, 2f)
                                )
                            } else {
                                androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    blurRadius = 6f
                                )
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 10.dp)
                    )
                }
            }
        }
    } else {
        // Plain text lyrics without timestamps
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lyrics,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.verticalScroll(rememberScrollState(), enabled = false)
            )
        }
    }
}

enum class LyricsType {
    EMBEDDED,
    LRC
}

@Composable
fun LyricsManagementDialog(
    currentLyrics: String?,
    onSaveLyrics: (String?) -> Unit,
    onSelectLrcFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val themeColor = MaterialTheme.colorScheme.primary
    var selectedType by remember {
        mutableStateOf(
            if (currentLyrics != null && currentLyrics.contains(Regex("""\[\d{1,2}:\d{2}"""))) {
                LyricsType.LRC
            } else {
                LyricsType.EMBEDDED
            }
        )
    }
    var showEditor by remember { mutableStateOf(false) }
    var editorInitialText by remember { mutableStateOf("") }
    var editorTitle by remember { mutableStateOf("Edit Lyrics") }

    if (showEditor) {
        LyricsEditorDialog(
            title = editorTitle,
            initialText = editorInitialText,
            onSave = { newLyrics ->
                onSaveLyrics(newLyrics)
                showEditor = false
                onDismiss()
            },
            onDismiss = { showEditor = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Lyrics Options",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(24.dp),
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Option 1: Embedded lyrics
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedType = LyricsType.EMBEDDED }
                        .padding(vertical = 6.dp)
                ) {
                    RadioButton(
                        selected = selectedType == LyricsType.EMBEDDED,
                        onClick = { selectedType = LyricsType.EMBEDDED },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = themeColor,
                            unselectedColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Embedded lyrics",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (selectedType == LyricsType.EMBEDDED) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 4.dp, bottom = 12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                editorTitle = "Edit Lyrics"
                                editorInitialText = currentLyrics ?: ""
                                showEditor = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: .LRC file
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedType = LyricsType.LRC }
                        .padding(vertical = 6.dp)
                ) {
                    RadioButton(
                        selected = selectedType == LyricsType.LRC,
                        onClick = { selectedType = LyricsType.LRC },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = themeColor,
                            unselectedColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ".LRC file",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (selectedType == LyricsType.LRC) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 4.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSelectLrcFile,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select a '.lrc' file", color = Color.White, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                editorTitle = "Create .LRC Lyrics"
                                editorInitialText = currentLyrics ?: "[00:00.00] \n"
                                showEditor = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Buttons: RESET (left) and DONE (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onSaveLyrics(null)
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = "RESET",
                            color = themeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(
                            text = "DONE",
                            color = themeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
fun LyricsEditorDialog(
    title: String,
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themeColor = MaterialTheme.colorScheme.primary
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                placeholder = {
                    Text("Enter or paste lyrics here...", color = Color.White.copy(alpha = 0.4f))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = themeColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = themeColor
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Save", color = themeColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}
