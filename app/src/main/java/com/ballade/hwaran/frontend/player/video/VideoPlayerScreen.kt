@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ballade.hwaran.frontend.player.video

import androidx.lifecycle.compose.LocalLifecycleOwner
import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.ballade.hwaran.core.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun formatVideoTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    chapterId: Long,
    externalUri: String? = null,
    videoViewModel: com.ballade.hwaran.ui.viewmodels.VideoViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChapter: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val database = remember { AppDatabase.getDatabase(context) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var chapterTitle by remember { mutableStateOf("Episode") }
    var isLoadingMetadata by remember { mutableStateOf(true) }

    var allChapters by remember { mutableStateOf<List<com.ballade.hwaran.core.database.entity.ChapterEntity>>(emptyList()) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var dragStartPos by remember { mutableLongStateOf(0L) }
    var isGestureDragging by remember { mutableStateOf(false) }
    var isHorizontalDrag by remember { mutableStateOf<Boolean?>(null) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }
    
    var showUi by remember { mutableStateOf(true) }
    var isSpeedUpActive by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val coroutineScope = rememberCoroutineScope()

    val handleBack = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onNavigateBack()
    }

    BackHandler(onBack = handleBack)

    // Auto-hide UI logic: Hides HUD after 1 second of inactivity if playing
    LaunchedEffect(showUi, showUnlockButton, lastInteractionTime, isPlaying) {
        if (showUi && isPlaying) {
            delay(2000)
            showUi = false
        }
        if (showUnlockButton) {
            delay(2000)
            showUnlockButton = false
        }
    }

    LaunchedEffect(chapterId, externalUri) {
        if (externalUri != null) {
            val parsedUri = Uri.parse(externalUri)
            // Try to extract real embedded title from media file metadata first
            val embeddedTitle = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, parsedUri)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    retriever.release()
                    title?.takeIf { it.isNotBlank() }
                } catch (e: Exception) { null }
            }
            videoUri = parsedUri
            chapterTitle = embeddedTitle
                ?: parsedUri.lastPathSegment?.let {
                    // Strip common extensions for cleaner display
                    it.substringBeforeLast(".").replace('_', ' ').replace('-', ' ')
                } ?: "Video"
            com.ballade.hwaran.core.util.HistoryTracker.logEvent("WATCH", chapterTitle, "External Video: $externalUri")
            isLoadingMetadata = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val chapter = database.trackDao().getChapterById(chapterId)
            chapter?.let {
                chapterTitle = it.title
                val manga = database.libraryDao().getMangaById(it.mangaId)
                com.ballade.hwaran.core.util.HistoryTracker.logEvent(
                    "WATCH",
                    it.title,
                    "mangaId:${manga?.id ?: -1L}|chapterId:${it.id}|fallback:Series/Channel: ${manga?.title ?: "Unknown"}"
                )
                
                allChapters = database.trackDao().getChaptersForManga(it.mangaId).first().sortedWith(compareBy<com.ballade.hwaran.core.database.entity.ChapterEntity> { 
                    Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloat() ?: Float.MAX_VALUE 
                }.thenBy {
                    it.title.replace(Regex("\\d+")) { matchResult ->
                        matchResult.value.padStart(10, '0')
                    }
                })
                if (it.folderUri.startsWith("content://")) {
                    val uri = Uri.parse(it.folderUri)
                    // Robust SAF resolution
                    val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    if (doc != null && doc.exists() && !doc.isDirectory) {
                        videoUri = uri
                    } else {
                        val treeDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                        if (treeDoc != null && treeDoc.isDirectory) {
                            val videoFile = treeDoc.listFiles().find { f ->
                                f.name?.endsWith(".mp4", true) == true || 
                                f.name?.endsWith(".mkv", true) == true || 
                                f.name?.endsWith(".avi", true) == true
                            }
                            videoUri = videoFile?.uri
                        } else {
                            videoUri = uri
                        }
                    }
                } else {
                    val file = File(it.folderUri)
                    if (file.exists() && file.isFile) {
                        videoUri = Uri.fromFile(file)
                    } else if (file.exists() && file.isDirectory) {
                        val videoFile = file.listFiles()?.find { f -> 
                            f.name.endsWith(".mp4", true) || f.name.endsWith(".mkv", true) || f.name.endsWith(".avi", true)
                        }
                        if (videoFile != null) {
                            videoUri = Uri.fromFile(videoFile)
                        }
                    }
                }
            }
        }
        isLoadingMetadata = false
    }

    // Hoist isPortrait here so ALL overlays (including 2x Speed outside AnimatedVisibility) can use it
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (videoUri != null) {
            val currentIndex = remember(allChapters, chapterId) { allChapters.indexOfFirst { it.id == chapterId } }
            val hasPrev = currentIndex > 0
            val hasNext = currentIndex >= 0 && currentIndex < allChapters.size - 1

            val onNextEpisode = androidx.compose.runtime.rememberUpdatedState {
                if (hasNext) {
                    val currentOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    onNavigateToChapter(allChapters[currentIndex + 1].id)
                    // Re-apply orientation after navigation
                    activity?.requestedOrientation = currentOrientation
                }
            }

            val exoPlayer = remember(videoUri) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUri!!))
                    setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    prepare()
                    playWhenReady = true
                }
            }

            var initialSeekDone by remember(videoUri) { mutableStateOf(false) }

            LaunchedEffect(exoPlayer) {
                if (!initialSeekDone && chapterId != -1L) {
                    val chapter = withContext(Dispatchers.IO) { database.trackDao().getChapterById(chapterId) }
                    if (chapter != null) {
                        val seekPos = chapter.position * 1L
                        if (seekPos > 0) {
                            exoPlayer.seekTo(seekPos)
                        }
                    }
                    initialSeekDone = true
                    
                    videoViewModel.incrementOpenCount(chapterId)
                    
                    // Update tracker AFTER initial seek check, so we don't accidentally seek to an old timestamp for a new video
                    if (chapter != null) {
                        val currentManga = withContext(Dispatchers.IO) { database.libraryDao().getMangaById(chapter.mangaId) }
                        if (currentManga != null) {
                            withContext(Dispatchers.IO) {
                                database.libraryDao().insertManga(currentManga.copy(lastReadTitle = chapter.title))
                            }
                        }
                    }
                }
                while(true) {
                    currentPosition = exoPlayer.currentPosition
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                    delay(500)
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(exoPlayer, lifecycleOwner) {
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            onNextEpisode.value()
                        }
                    }
                }
                exoPlayer.addListener(listener)
                
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                        val finalPos = exoPlayer.currentPosition
                        val finalDur = exoPlayer.duration.coerceAtLeast(0L)
                        videoViewModel.saveLastPosition(chapterId, finalPos, finalDur)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    val finalPos = exoPlayer.currentPosition
                    val finalDur = exoPlayer.duration.coerceAtLeast(0L)
                    videoViewModel.saveLastPosition(chapterId, finalPos, finalDur)
                    exoPlayer.removeListener(listener)
                    exoPlayer.release()
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(isLocked) {
                        detectTapGestures(
                            onPress = {
                                val wasPlaying = isPlaying
                                val job = coroutineScope.launch {
                                    kotlinx.coroutines.delay(400)
                                    isSpeedUpActive = true
                                    exoPlayer.setPlaybackSpeed(2f)
                                    if (!wasPlaying) exoPlayer.play()
                                    
                                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        vibrator?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK))
                                    } else {
                                        vibrator?.vibrate(50)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                                if (isSpeedUpActive) {
                                    isSpeedUpActive = false
                                    exoPlayer.setPlaybackSpeed(1f)
                                    if (!wasPlaying) exoPlayer.pause()
                                }
                            },
                            onTap = {
                                if (isLocked) {
                                    showUnlockButton = !showUnlockButton
                                    lastInteractionTime = System.currentTimeMillis()
                                } else {
                                    showUi = !showUi
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            },
                            onDoubleTap = {
                                if (!isLocked) {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    lastInteractionTime = System.currentTimeMillis()
                                    showUi = true
                                }
                            }
                        )
                    }
                    .pointerInput(duration) {
                        if (duration > 0) {
                            var accumulatedDrag = 0f
                            detectDragGestures(
                                onDragStart = { _ ->
                                    isGestureDragging = true
                                    dragStartPos = exoPlayer.currentPosition
                                    accumulatedDrag = 0f
                                    isHorizontalDrag = null
                                    dragPosition = dragStartPos.toFloat()
                                    seekDeltaMs = 0L
                                    // Keep HUD hidden during full-screen gesture
                                    if (!isLocked) showUi = false 
                                },
                                onDragEnd = {
                                    isGestureDragging = false
                                    dragPosition = null
                                    seekDeltaMs = 0L
                                    dragStartPos = 0L
                                    if (isHorizontalDrag == true && !isLocked) {
                                        showUi = true // Show UI briefly after horizontal gesture, but not if locked
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                    isHorizontalDrag = null
                                },
                                onDragCancel = {
                                    isGestureDragging = false
                                    dragPosition = null
                                    seekDeltaMs = 0L
                                    dragStartPos = 0L
                                    isHorizontalDrag = null
                                },
                                onDrag = { change, dragAmount ->
                                    if (isHorizontalDrag == null) {
                                        isHorizontalDrag = kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)
                                    }
                                    if (isHorizontalDrag == true) {
                                        change.consume()
                                        accumulatedDrag += dragAmount.x
                                        // Sensitivity: 100 pixels = ~10 seconds (100ms per pixel)
                                        val sensitivity = 100f 
                                        val deltaMs = (accumulatedDrag * sensitivity).toLong()
                                        val newPos = (dragStartPos + deltaMs).coerceIn(0, duration)
                                        
                                        val now = System.currentTimeMillis()
                                        if (now - lastSeekTime > 30) {
                                            exoPlayer.seekTo(newPos)
                                            lastSeekTime = now
                                        }
                                        dragPosition = newPos.toFloat()
                                        seekDeltaMs = newPos - dragStartPos
                                    }
                                }
                            )
                        }
                    },
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            )

            // UI Overlay
            AnimatedVisibility(
                visible = showUi && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    
                    // isPortrait is hoisted to composable scope above — accessible here too

                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=0.8f), Color.Black.copy(alpha=0.3f), Color.Transparent)))
                            .padding(start = 16.dp, end = 24.dp, top = if (isPortrait) 72.dp else 32.dp, bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = handleBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        if (isPortrait) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = chapterTitle, 
                                color = Color.White, 
                                fontSize = 15.sp, 
                                fontWeight = FontWeight.SemiBold,
                                maxLines = Int.MAX_VALUE,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Center Playback Controls
                    val hasPrev = currentIndex > 0
                    val hasNext = currentIndex >= 0 && currentIndex < allChapters.size - 1

                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (allChapters.size > 1) {
                            IconButton(
                                onClick = { 
                                    if (hasPrev) {
                                        val currentOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        onNavigateToChapter(allChapters[currentIndex - 1].id)
                                        activity?.requestedOrientation = currentOrientation
                                    }
                                },
                                enabled = hasPrev
                            ) {
                                Icon(
                                    Icons.Filled.SkipPrevious, 
                                    contentDescription = "Prev", 
                                    tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.3f), 
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha=0.4f))
                                .clickable {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        if (allChapters.size > 1) {
                            IconButton(
                                onClick = { 
                                    if (hasNext) {
                                        val currentOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        onNavigateToChapter(allChapters[currentIndex + 1].id)
                                        activity?.requestedOrientation = currentOrientation
                                    }
                                },
                                enabled = hasNext
                            ) {
                                Icon(
                                    Icons.Filled.SkipNext, 
                                    contentDescription = "Next", 
                                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f), 
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    
                    // Bottom Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=0.8f))))
                            .padding(
                                start = if (isLandscape) 120.dp else 24.dp,
                                end = if (isLandscape) 120.dp else 24.dp,
                                bottom = if (isLandscape) 32.dp else 80.dp,
                                top = 32.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rotation and Lock Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { 
                                    lastInteractionTime = System.currentTimeMillis()
                                    isLocked = true
                                    showUi = false
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Lock, 
                                    contentDescription = "Lock", 
                                    tint = Color.White, 
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(
                                onClick = { 
                                    lastInteractionTime = System.currentTimeMillis()
                                    activity?.let { act ->
                                        val current = act.requestedOrientation
                                        if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        } else {
                                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (isLandscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, 
                                    contentDescription = "Toggle Fullscreen", 
                                    tint = Color.White, 
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        // Seekbar row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                formatVideoTime(if (dragPosition != null) dragPosition!!.toLong() else currentPosition), 
                                color = Color.White, 
                                fontSize = 12.sp,
                                modifier = Modifier.width(45.dp)
                            )
                            
                            val sliderInteractionSource = remember { MutableInteractionSource() }
                            val isSliderDragged by sliderInteractionSource.collectIsDraggedAsState()

                            // Watchdog: Clear seek states if no interaction is active
                            LaunchedEffect(isSliderDragged, isGestureDragging) {
                                if (!isSliderDragged && !isGestureDragging) {
                                    dragPosition = null
                                    seekDeltaMs = 0L
                                    dragStartPos = 0L
                                }
                            }

                            val aestheticPurpleWhite = androidx.compose.ui.graphics.lerp(Color.White, Color(0xFF7A6284), 0.5f)
                            
                            com.ballade.hwaran.ui.components.WavyMusicSlider(
                                value = dragPosition ?: if (duration > 0) currentPosition.toFloat() else 0f,
                                interactionSource = sliderInteractionSource,
                                onValueChange = { 
                                    if (dragPosition == null) {
                                        dragStartPos = currentPosition
                                    }
                                    dragPosition = it
                                    seekDeltaMs = it.toLong() - dragStartPos
                                    lastInteractionTime = System.currentTimeMillis()
                                    val now = System.currentTimeMillis()
                                    if (now - lastSeekTime > 30) {
                                        exoPlayer.seekTo(it.toLong())
                                        lastSeekTime = now
                                    }
                                },
                                onValueChangeFinished = { 
                                    if (dragPosition != null) {
                                        currentPosition = dragPosition!!.toLong()
                                    }
                                    dragPosition = null 
                                    seekDeltaMs = 0L
                                    dragStartPos = 0L
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                                isPlaying = isPlaying,
                                activeTrackColor = aestheticPurpleWhite,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                thumbColor = Color(0xFF7A6284),
                                trackHeight = 4.dp,
                                thumbRadius = 6.dp
                            )
                            
                            Text(
                                formatVideoTime(duration), 
                                color = Color.White, 
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // Unlock Button Overlay
            AnimatedVisibility(
                visible = isLocked && showUnlockButton,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            isLocked = false
                            showUnlockButton = false
                            showUi = true
                            lastInteractionTime = System.currentTimeMillis()
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.LockOpen,
                            contentDescription = "Unlock",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Unlock", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            // ── Seek Feedback Overlay (Netflix / YouTube style) ──
            AnimatedVisibility(
                visible = dragPosition != null && isHorizontalDrag == true && isGestureDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val isForward = seekDeltaMs >= 0
                val absSeconds = kotlin.math.abs(seekDeltaMs / 1000)
                val sign = if (isForward) "+" else "-"
                val chevron = if (isForward) "\u00BB" else "\u00AB"

                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                        )
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = chevron,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$sign${absSeconds}s",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── 2x Speed Feedback Overlay ──
            AnimatedVisibility(
                visible = isSpeedUpActive,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isPortrait) 160.dp else 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                        )
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "2x Speed  »",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Polished loading screen shown while URI is resolving or metadata is being read
            VideoLoadingScreen(
                title = if (isLoadingMetadata) "Loading..." else chapterTitle,
                onBack = onNavigateBack
            )
        }
    }
}

@Composable
private fun VideoLoadingScreen(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0A0F), Color(0xFF12121A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Back button at top
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 32.dp, start = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Animated pulsing ring
            val infiniteTransition = rememberInfiniteTransition(label = "loading_pulse")
            val pulse by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )
            val ringAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ring_alpha"
            )

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color.White.copy(alpha = ringAlpha * 0.15f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Text(
                    text = "Preparing video...",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
