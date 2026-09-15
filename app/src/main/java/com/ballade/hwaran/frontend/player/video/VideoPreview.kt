package com.ballade.hwaran.frontend.player.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.min

@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(
    uri: Uri, 
    initialDuration: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var duration by remember(initialDuration) { mutableLongStateOf(initialDuration) }
    var isReadyToFade by remember { mutableStateOf(false) }
    
    // Robust URI resolution
    val resolvedUri = remember(uri) {
        var result = uri
        try {
            val uriStr = uri.toString()
            if (uriStr.startsWith("/")) {
                val f = File(uriStr)
                if (f.exists()) {
                    if (f.isDirectory) {
                        val videoFile = f.listFiles()?.find { file ->
                            val name = file.name.lowercase()
                            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".webm")
                        }
                        if (videoFile != null) result = Uri.fromFile(videoFile)
                    } else {
                        result = Uri.fromFile(f)
                    }
                }
            } else if (uriStr.startsWith("file://")) {
                val f = File(uri.path ?: "")
                if (f.exists() && f.isDirectory) {
                    val videoFile = f.listFiles()?.find { file ->
                        val name = file.name.lowercase()
                        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".webm")
                    }
                    if (videoFile != null) result = Uri.fromFile(videoFile)
                }
            } else if (uriStr.startsWith("content://")) {
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc != null && doc.exists() && !doc.isDirectory) {
                    result = uri
                } else if (doc != null && doc.exists() && doc.isDirectory) {
                    val videoFile = doc.listFiles().find { f ->
                        val name = f.name?.lowercase() ?: ""
                        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".webm")
                    }
                    if (videoFile != null) result = videoFile.uri
                } else {
                    val treeDoc = DocumentFile.fromTreeUri(context, uri)
                    if (treeDoc != null && treeDoc.isDirectory) {
                        val videoFile = treeDoc.listFiles().find { f ->
                            val name = f.name?.lowercase() ?: ""
                            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".webm")
                        }
                        if (videoFile != null) result = videoFile.uri
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    val alpha by animateFloatAsState(
        targetValue = if (isReadyToFade) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "PreviewAlpha"
    )
    
    val exoPlayer = remember(resolvedUri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .build().apply {
                setSeekParameters(SeekParameters.CLOSEST_SYNC) 
                setPlaybackSpeed(2.0f) // 2x preview
                
                val mediaItem = androidx.media3.common.MediaItem.fromUri(resolvedUri)
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 0f 
                playWhenReady = true
                
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        isReadyToFade = true
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            isReadyToFade = true
                            val dur = this@apply.duration
                            if (dur > 0L) {
                                duration = dur
                            }
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        error.printStackTrace()
                    }
                })
                
                prepare()
            }
    }
    
    // CRITICAL: Proper lifecycle management to prevent silent crashes
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }
    
    LaunchedEffect(exoPlayer) {
        if (duration <= 0L) {
            // Start playing from beginning at 2.0x while waiting for duration to load
            exoPlayer.setPlaybackSpeed(2.0f)
            exoPlayer.seekTo(0)
            var waitLoops = 0
            while (duration <= 0L && waitLoops < 20) {
                delay(100)
                waitLoops++
                val curDur = exoPlayer.duration
                if (curDur > 0L) {
                    duration = curDur
                    break
                }
            }
        }

        while (true) {
            try {
                if (duration <= 60_000L) {
                    // Video <= 1 minute: Play whole video in 2x mode
                    exoPlayer.setPlaybackSpeed(2.0f)
                    exoPlayer.seekTo(0)
                    while (exoPlayer.playbackState != Player.STATE_ENDED) {
                        delay(250)
                        if (duration > 0L && exoPlayer.currentPosition >= (duration - 300L)) break
                    }
                    exoPlayer.seekTo(0)
                } else {
                    // Video > 1 minute:
                    // 1. Play starting 40% in 2x mode
                    // 2. Keep middle 20% as rapid preview snippets across the timeline
                    // 3. Play last 40% in 2x mode
                    val first40Limit = (duration * 0.40).toLong()
                    val last40Start = (duration * 0.60).toLong()

                    // Phase 1: Starting 40% in 2x mode
                    exoPlayer.setPlaybackSpeed(2.0f)
                    exoPlayer.seekTo(0)
                    while (exoPlayer.currentPosition < first40Limit && exoPlayer.playbackState != Player.STATE_ENDED) {
                        delay(200)
                    }

                    // Phase 2: Middle 20% rapid preview snippets (e.g. 45%, 50%, 55%)
                    val middleRatios = floatArrayOf(0.45f, 0.50f, 0.55f)
                    exoPlayer.setPlaybackSpeed(2.5f)
                    for (ratio in middleRatios) {
                        val seekTarget = (duration * ratio).toLong().coerceIn(first40Limit, last40Start)
                        exoPlayer.seekTo(seekTarget)
                        var waitCount = 0
                        while (exoPlayer.playbackState == Player.STATE_BUFFERING && waitCount < 20) {
                            delay(20)
                            waitCount++
                        }
                        delay(1200L)
                    }

                    // Phase 3: Last 40% in 2x mode
                    exoPlayer.setPlaybackSpeed(2.0f)
                    exoPlayer.seekTo(last40Start)
                    while (exoPlayer.playbackState != Player.STATE_ENDED) {
                        delay(200)
                        if (exoPlayer.currentPosition >= (duration - 400L)) break
                    }

                    // Loop back to beginning
                    exoPlayer.seekTo(0)
                }
            } catch (e: Exception) {
                break
            }
        }
    }
    
    Box(modifier = modifier.background(Color.Black).alpha(alpha)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
