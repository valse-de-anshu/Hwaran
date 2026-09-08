package com.ballade.hwaran.ui.screens.video

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
            if (uri.toString().startsWith("content://")) {
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc != null && doc.exists() && doc.isDirectory) {
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
            } else {
                val file = File(uri.path ?: "")
                if (file.exists() && file.isDirectory) {
                    val videoFile = file.listFiles()?.find { f -> 
                        val name = f.name.lowercase()
                        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".webm")
                    }
                    if (videoFile != null) result = Uri.fromFile(videoFile)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    val alpha by animateFloatAsState(
        targetValue = if (isReadyToFade) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "PreviewAlpha"
    )
    
    val exoPlayer = remember(resolvedUri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .build().apply {
                setSeekParameters(SeekParameters.CLOSEST_SYNC) 
                setPlaybackSpeed(1.5f) // Snappy "Phub" style speed
                
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
                        if (playbackState == Player.STATE_READY && duration <= 0L) {
                            duration = this@apply.duration
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
    
    LaunchedEffect(duration, exoPlayer) {
        if (duration > 0) {
            if (duration <= 61000L) {
                exoPlayer.setPlaybackSpeed(2.5f)
                exoPlayer.seekTo(0)
                while (true) {
                    delay(500)
                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(0)
                    }
                }
            } else {
                val glimpseDuration = 1000L 
                val middleStart = 30000L
                val middleEnd = (duration - 40000L).coerceAtLeast(31000L)
                val middleDuration = (middleEnd - middleStart).coerceAtLeast(1000L)
                val segments = 6
                
                while (true) {
                    try {
                        // Phase 1: First 30 seconds at 2.5x
                        exoPlayer.setPlaybackSpeed(2.5f)
                        exoPlayer.seekTo(0)
                        while (exoPlayer.currentPosition < 30000L && exoPlayer.playbackState != Player.STATE_ENDED) {
                            delay(200)
                        }

                        // Phase 2: Middle segments follow current logic (1.5x)
                        exoPlayer.setPlaybackSpeed(1.5f)
                        for (i in 0 until segments) {
                            val seekPosition = (middleStart + (i * (middleDuration / segments))).coerceIn(0, duration - 2000L)
                            exoPlayer.seekTo(seekPosition)
                            
                            var waitCount = 0
                            while (exoPlayer.playbackState == Player.STATE_BUFFERING && waitCount < 30) {
                                delay(20)
                                waitCount++
                            }
                            delay(glimpseDuration)
                        }

                        // Phase 3: Last 40 seconds at 2.5x
                        exoPlayer.setPlaybackSpeed(2.5f)
                        val lastPhaseStart = (duration - 40000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(lastPhaseStart)
                        while (exoPlayer.currentPosition < duration - 1000L && exoPlayer.playbackState != Player.STATE_ENDED) {
                            delay(200)
                        }
                    } catch (e: Exception) {
                        break 
                    }
                }
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
