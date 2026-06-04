package com.ballade.hwaran.ui.screens

import androidx.compose.material.icons.rounded.MusicNote

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.ballade.hwaran.ui.components.PlaylistSelectionDialog
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import com.ballade.hwaran.ui.viewmodels.LibraryViewModel
import com.ballade.hwaran.ui.theme.LocalBatterySaving
import kotlinx.coroutines.launch


val SequentialPlayIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SequentialPlay",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 8f)
            lineTo(20f, 8f)
            moveTo(16f, 4f)
            lineTo(20f, 8f)
            lineTo(16f, 12f)
            moveTo(4f, 16f)
            lineTo(20f, 16f)
            moveTo(16f, 12f)
            lineTo(20f, 16f)
            lineTo(16f, 20f)
        }
    }.build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
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
    val repeatMode by musicViewModel.repeatMode.collectAsState()

    // Boundary Logic for Dimming Controls
    val currentIndex = remember(currentPlaylist, currentChapter) { 
        currentPlaylist.indexOfFirst { it.id == currentChapter?.id } 
    }
    val hasNext = currentIndex < currentPlaylist.size - 1 && currentPlaylist.size > 1
    val hasPrevious = currentIndex > 0 && currentPlaylist.size > 1

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showRepeatMenu by rememberSaveable { mutableStateOf(false) }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showQueueDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var isLyricsMode by rememberSaveable { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val controlIconColor = Color(0xFF7A6284)

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
                        .clip(RoundedCornerShape(32.dp))
                        .shadow(
                            elevation = 40.dp,
                            shape = RoundedCornerShape(32.dp),
                            spotColor = Color(colorPalette.vibrant).copy(alpha = 0.5f),
                            ambientColor = Color.Transparent
                        )
                        .clickable { isLyricsMode = !isLyricsMode }
                ) {
                    AnimatedContent(
                        targetState = isLyricsMode,
                        transitionSpec = { fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500)) },
                        label = "lyrics_transition"
                    ) { targetLyricsMode ->
                        if (targetLyricsMode) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(text = currentChapter?.lyrics ?: "No lyrics found.", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.verticalScroll(rememberScrollState()))
                            }
                        } else {
                            val cover = currentChapter?.thumbnailUri ?: currentChapter?.folderUri ?: currentManga?.coverPath
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
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

                // Right Side: Info and Controls
                Column(
                    modifier = Modifier.weight(1.3f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.weight(0.1f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = currentChapter?.title ?: "No Song Playing", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.horizontalFade().basicMarquee())
                            Text(text = currentChapter?.artist ?: "Unknown Artist", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var sliderPosition by remember { mutableStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }
                    LaunchedEffect(playbackProgress) { if (!isDragging) sliderPosition = playbackProgress }

                    WavyMusicSlider(value = sliderPosition, onValueChange = { isDragging = true; sliderPosition = it }, onValueChangeFinished = { isDragging = false; musicViewModel.seekTo(sliderPosition) }, isPlaying = isPlaying, activeTrackColor = controlIconColor, inactiveTrackColor = controlIconColor.copy(alpha = 0.2f), thumbColor = controlIconColor, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = formatTime((sliderPosition * totalDuration).toLong()), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text(text = "-${formatTime(((1f - sliderPosition) * totalDuration).toLong())}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape), onClick = { 
                            musicViewModel.toggleShuffle() 
                            scope.launch { 
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(if (!shuffleMode) "Shuffle mode is on" else "Shuffle mode is off") 
                            }
                        }) {
                            Icon(if (shuffleMode) Icons.Rounded.Shuffle else SequentialPlayIcon, contentDescription = null, tint = if (shuffleMode) controlIconColor else controlIconColor.copy(alpha = 0.6f))
                        }
                        
                        IconButton(onClick = { musicViewModel.previous() }, enabled = hasPrevious, modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = if (hasPrevious) controlIconColor else controlIconColor.copy(alpha = 0.2f), modifier = Modifier.size(40.dp))
                        }
                        Surface(onClick = { musicViewModel.togglePlayPause() }, shape = CircleShape, color = controlIconColor, modifier = Modifier.size(60.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }
                        IconButton(onClick = { musicViewModel.next() }, enabled = hasNext, modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = if (hasNext) controlIconColor else controlIconColor.copy(alpha = 0.2f), modifier = Modifier.size(40.dp))
                        }

                        Box {
                            IconButton(onClick = { showRepeatMenu = true }, modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                                Icon(
                                    imageVector = when(repeatMode) {
                                        androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                        androidx.media3.common.Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                                        else -> Icons.Rounded.ArrowRightAlt
                                    }, 
                                    contentDescription = null, 
                                    tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) controlIconColor else controlIconColor.copy(alpha = 0.6f)
                                )
                            }
                            DropdownMenu(
                                expanded = showRepeatMenu,
                                onDismissRequest = { showRepeatMenu = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xFF1A1A1A),
                                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Play next song", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.ArrowRightAlt, contentDescription = null, tint = controlIconColor) },
                                    onClick = { 
                                        showRepeatMenu = false
                                        musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Repeat the same queue", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.Repeat, contentDescription = null, tint = controlIconColor) },
                                    onClick = { 
                                        showRepeatMenu = false
                                        musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Repeat the same song", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.RepeatOne, contentDescription = null, tint = controlIconColor) },
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
                        IconButton(modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape), onClick = { 
                            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                            context.startActivity(intent)
                        }) {
                            Icon(
                                painter = painterResource(id = com.ballade.hwaran.R.drawable.ic_speaker),
                                contentDescription = "Cast",
                                tint = controlIconColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape), onClick = { 
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
                            Icon(Icons.Rounded.IosShare, contentDescription = "Share", tint = controlIconColor.copy(alpha = 0.7f))
                        }
                        IconButton(onClick = { showQueueDialog = true }, modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue", tint = controlIconColor.copy(alpha = 0.7f))
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Menu", tint = Color.White.copy(alpha = 0.7f))
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xFF1A1A1A),
                                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Refine Tag", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
                                    onClick = { 
                                        showMenu = false
                                        currentChapter?.let { onNavigateToEditSong(it.id) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add in Playlist", color = Color.White) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
                                    onClick = { 
                                        showMenu = false
                                        showPlaylistDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (musicMode == 1) "Show Album Art" else "Show Conductor", color = Color.White) },
                                    leadingIcon = { Icon(if (musicMode == 1) Icons.Rounded.Image else Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
                                    onClick = { 
                                        showMenu = false
                                        settingsViewModel.setMusicMode(if (musicMode == 1) 0 else 1)
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White.copy(alpha = 0.05f))
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color(0xFFE57373).copy(alpha = 0.8f)) },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFE57373).copy(alpha = 0.6f)) },
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
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xFF1A1A1A),
                        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refine Tag", color = Color.White) },
                            leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
                            onClick = { 
                                showMenu = false
                                currentChapter?.let { onNavigateToEditSong(it.id) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add in Playlist", color = Color.White) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
                            onClick = { 
                                showMenu = false
                                showPlaylistDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (musicMode == 1) "Show Album Art" else "Show Conductor", color = Color.White) },
                            leadingIcon = { Icon(if (musicMode == 1) Icons.Rounded.Image else Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
                            onClick = { 
                                showMenu = false
                                settingsViewModel.setMusicMode(if (musicMode == 1) 0 else 1)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White.copy(alpha = 0.05f))
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFE57373).copy(alpha = 0.8f)) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFE57373).copy(alpha = 0.6f)) },
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
                    .clip(RoundedCornerShape(32.dp))
                    .shadow(
                        elevation = 40.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = Color(colorPalette.vibrant).copy(alpha = 0.5f),
                        ambientColor = Color.Transparent
                    )
                    .clickable { isLyricsMode = !isLyricsMode }
            ) {
                AnimatedContent(
                    targetState = isLyricsMode,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                    },
                    label = "lyrics_transition"
                ) { targetLyricsMode ->
                    if (targetLyricsMode) {
                        // Lyrics View
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentChapter?.lyrics ?: "No lyrics found.",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    } else {
                        // Album Cover / Animation View
                        val cover = currentChapter?.thumbnailUri ?: currentChapter?.folderUri ?: currentManga?.coverPath
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
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
                Box(modifier = Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)))
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Song Titles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = currentChapter?.title ?: "No Song Playing",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().horizontalFade().basicMarquee()
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
                    activeTrackColor = controlIconColor,
                    inactiveTrackColor = controlIconColor.copy(alpha = 0.2f),
                    thumbColor = controlIconColor,
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
                IconButton(modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape), onClick = { 
                    musicViewModel.toggleShuffle() 
                    scope.launch { 
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(if (!shuffleMode) "Shuffle mode is on" else "Shuffle mode is off") 
                    }
                }) {
                    Icon(if (shuffleMode) Icons.Rounded.Shuffle else SequentialPlayIcon, contentDescription = null, tint = if (shuffleMode) controlIconColor else controlIconColor.copy(alpha = 0.6f))
                }
                IconButton(
                    onClick = { musicViewModel.previous() },
                    enabled = hasPrevious,
                    modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious, 
                        contentDescription = null, 
                        tint = if (hasPrevious) controlIconColor else controlIconColor.copy(alpha = 0.2f), 
                        modifier = Modifier.size(48.dp)
                    )
                }
                Surface(
                    onClick = { musicViewModel.togglePlayPause() },
                    shape = CircleShape,
                    color = controlIconColor,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
                IconButton(
                    onClick = { musicViewModel.next() },
                    enabled = hasNext,
                    modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.SkipNext, 
                        contentDescription = null, 
                        tint = if (hasNext) controlIconColor else controlIconColor.copy(alpha = 0.2f), 
                        modifier = Modifier.size(48.dp)
                    )
                }
                Box {
                    IconButton(onClick = { showRepeatMenu = true }, modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                        Icon(
                            imageVector = when(repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                androidx.media3.common.Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                                else -> Icons.Rounded.ArrowRightAlt
                            },
                            contentDescription = null, 
                            tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) controlIconColor else controlIconColor.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = showRepeatMenu,
                        onDismissRequest = { showRepeatMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xFF1A1A1A),
                        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play next song", color = Color.White) },
                            leadingIcon = { Icon(Icons.Rounded.ArrowRightAlt, contentDescription = null, tint = controlIconColor) },
                            onClick = { 
                                showRepeatMenu = false
                                musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Repeat the same queue", color = Color.White) },
                            leadingIcon = { Icon(Icons.Rounded.Repeat, contentDescription = null, tint = controlIconColor) },
                            onClick = { 
                                showRepeatMenu = false
                                musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Repeat the same song", color = Color.White) },
                            leadingIcon = { Icon(Icons.Rounded.RepeatOne, contentDescription = null, tint = controlIconColor) },
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
                IconButton(modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape), onClick = { 
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Icon(
                        painter = painterResource(id = com.ballade.hwaran.R.drawable.ic_speaker),
                        contentDescription = null,
                        tint = controlIconColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape), onClick = { 
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
                    Icon(Icons.Rounded.IosShare, contentDescription = null, tint = controlIconColor.copy(alpha = 0.5f))
                }
                IconButton(onClick = { showQueueDialog = true }, modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = controlIconColor.copy(alpha = 0.5f))
                }

                // Heart Button Logic
                val favoritedUris by libraryViewModel.favoritedUris.collectAsState()
                val isFavorite = currentChapter?.folderUri?.let { favoritedUris.contains(it) } == true
                
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
                onDeleteFromPlaylist = {
                    libraryViewModel.deleteChapterOnlyFromDb(currentChapter!!.id)
                    showDeleteDialog = false
                    onNavigateBack()
                },
                onDeleteFromDevice = {
                    libraryViewModel.deleteSelectedChapters(listOf(currentChapter!!.id))
                    showDeleteDialog = false
                    onNavigateBack()
                },
                onDismiss = { showDeleteDialog = false }
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
    queue: List<com.ballade.hwaran.data.local.ChapterEntity>,
    currentChapter: com.ballade.hwaran.data.local.ChapterEntity?,
    currentManga: com.ballade.hwaran.data.local.MangaEntity?,
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
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    } else {
                                        Color.Transparent
                                    }
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
                                tint = if (isPlaying) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.artist != null && item.artist.trim().isNotEmpty() && item.artist != "Unknown Artist") {
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = item.artist,
                                        color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isPlaying) {
                                Text(
                                    text = "Playing",
                                    color = MaterialTheme.colorScheme.primary,
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
