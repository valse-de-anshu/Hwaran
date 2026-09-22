@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ballade.hwaran.frontend.player.video

import androidx.lifecycle.compose.LocalLifecycleOwner
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ballade.hwaran.audio.HwaranPlayerHolder
import com.ballade.hwaran.core.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

fun formatVideoTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Reusable translucent dark fade dialog popup container with subtle outline & rounded corners.
 */
@Composable
private fun SleekFadeDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismissRequest() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .padding(horizontal = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = false
                    ) {},
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF012141F),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier.padding(top = 18.dp, bottom = 18.dp, start = 20.dp, end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            content()
                        }

                        // Top gradient fade overlay mask for graceful scrolling
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xF012141F), Color.Transparent)
                                    )
                                )
                        )

                        // Bottom gradient fade overlay mask for graceful scrolling
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xF012141F))
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Automatically scans local directories for matching sidecar subtitle files (.srt, .vtt, .ass).
 * Uses exact filename for label instead of hardcoded language tags.
 */
@UnstableApi
private fun findSidecarSubtitles(context: Context, videoUri: Uri, chapterTitle: String? = null): List<MediaItem.SubtitleConfiguration> {
    val result = mutableListOf<MediaItem.SubtitleConfiguration>()
    val addedUris = mutableSetOf<String>()

    fun addSubtitleConfig(uri: Uri, name: String, isDefault: Boolean = false) {
        val uriStr = uri.toString()
        if (addedUris.contains(uriStr)) return
        addedUris.add(uriStr)

        val ext = name.substringAfterLast(".", "").lowercase()
        val mimeType = when (ext) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val subConfig = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLabel(name)
            .setId(name)
            .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
            .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .build()
        result.add(subConfig)
    }

    fun extractEpNum(str: String): String? {
        val match = Regex("""(?i)(?:ep|episode|e|ch|chapter|v)?[\s._-]*0*(\d+)""").find(str)
        if (match != null && match.groupValues[1].isNotBlank()) return match.groupValues[1]
        val standaloneNum = Regex("""\b0*(\d+)\b""").find(str)
        return standaloneNum?.groupValues[1]
    }

    fun cleanName(str: String): String {
        return str.replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
            .replace(Regex("(?i)\\.(srt|vtt|ass|ssa|sub)$"), "")
            .replace(Regex("[_.-]+"), " ")
            .trim()
            .lowercase()
    }

    fun isSubtitleMatch(videoName: String, subFileName: String): Boolean {
        val vidClean = cleanName(videoName)
        val subClean = cleanName(subFileName)
        val subBaseName = subFileName.substringBeforeLast(".").lowercase()
        val vidBaseName = videoName.substringBeforeLast(".").lowercase()

        if (subBaseName == vidBaseName) return true
        if (subClean.isNotBlank() && subClean == vidClean) return true

        val langSuffixes = listOf("eng", "en", "english", "ja", "jpn", "japanese", "sub", "subs", "default", "sdh")
        for (suffix in langSuffixes) {
            if (subClean == "$vidClean $suffix" || subClean == "$vidClean-$suffix") return true
            if (subBaseName.startsWith(vidBaseName) && subBaseName.substring(vidBaseName.length).contains(suffix)) return true
        }

        if (subClean.isNotBlank() && vidClean.isNotBlank()) {
            if (subClean.startsWith(vidClean) || vidClean.startsWith(subClean)) return true
        }

        val vidEp = extractEpNum(videoName) ?: chapterTitle?.let { extractEpNum(it) }
        val subEp = extractEpNum(subFileName)
        if (vidEp != null && subEp != null && vidEp == subEp) return true

        return false
    }

    try {
        var resolvedFilePath: String? = null
        val scheme = videoUri.scheme

        if (scheme == "file") {
            resolvedFilePath = videoUri.path
        } else if (scheme.isNullOrBlank()) {
            resolvedFilePath = videoUri.toString()
        } else if (scheme == "content") {
            try {
                context.contentResolver.query(videoUri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                        if (idx != -1) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrBlank() && File(path).exists()) {
                                resolvedFilePath = path
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            if (resolvedFilePath == null) {
                val path = videoUri.path
                if (path != null) {
                    val storageIdx = path.indexOf("/storage/")
                    if (storageIdx != -1) {
                        val p = path.substring(storageIdx)
                        if (File(p).exists()) resolvedFilePath = p
                    }
                    if (resolvedFilePath == null) {
                        val sdcardIdx = path.indexOf("/sdcard/")
                        if (sdcardIdx != -1) {
                            val p = path.substring(sdcardIdx)
                            if (File(p).exists()) resolvedFilePath = p
                        }
                    }
                }
            }
        }

        if (resolvedFilePath != null) {
            val videoFile = File(resolvedFilePath)
            val parentDir = videoFile.parentFile
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                val videoName = videoFile.name
                val possibleExtensions = setOf("srt", "vtt", "ass", "ssa", "sub")
                val subFolders = listOf(".", "subs", "subtitles", "sub", "Subs", "Subtitles", "Sub")

                for (folderName in subFolders) {
                    val targetDir = if (folderName == ".") parentDir else File(parentDir, folderName)
                    if (targetDir.exists() && targetDir.isDirectory) {
                        val files = targetDir.listFiles() ?: continue
                        val subFiles = files.filter { it.isFile && possibleExtensions.contains(it.extension.lowercase()) }

                        for (file in subFiles) {
                            if (isSubtitleMatch(videoName, file.name) || (folderName != "." && subFiles.size == 1)) {
                                val isFirstMatch = result.isEmpty()
                                addSubtitleConfig(Uri.fromFile(file), file.name, isDefault = isFirstMatch)
                            }
                        }
                    }
                }
            }
        }

        if (result.isEmpty() && scheme == "content") {
            try {
                val docId = try { android.provider.DocumentsContract.getDocumentId(videoUri) } catch (e: Exception) { null }
                if (docId != null && docId.contains("/")) {
                    val parentDocId = docId.substringBeforeLast("/")
                    val authority = videoUri.authority
                    if (authority != null) {
                        val parentChildUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                            videoUri, parentDocId
                        )
                        context.contentResolver.query(
                            parentChildUri,
                            arrayOf(
                                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                            ),
                            null, null, null
                        )?.use { cursor ->
                            val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val mimeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)

                            while (cursor.moveToNext()) {
                                val childId = if (idIdx != -1) cursor.getString(idIdx) else null
                                val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                                val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) else null

                                if (childId != null && name != null) {
                                    val isDir = mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                                    if (isDir && listOf("subs", "subtitles", "sub").contains(name.lowercase())) {
                                        try {
                                            val subfolderUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(videoUri, childId)
                                            context.contentResolver.query(
                                                subfolderUri,
                                                arrayOf(
                                                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                                                ),
                                                null, null, null
                                            )?.use { subCursor ->
                                                val sIdIdx = subCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                                                val sNameIdx = subCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                                val sCount = subCursor.count
                                                while (subCursor.moveToNext()) {
                                                    val sChildId = if (sIdIdx != -1) subCursor.getString(sIdIdx) else null
                                                    val sName = if (sNameIdx != -1) subCursor.getString(sNameIdx) else null
                                                    if (sChildId != null && sName != null) {
                                                        val sExt = sName.substringAfterLast(".", "").lowercase()
                                                        if (setOf("srt", "vtt", "ass", "ssa", "sub").contains(sExt)) {
                                                            val videoName = videoUri.lastPathSegment?.substringAfterLast("/") ?: chapterTitle ?: "video"
                                                            if (isSubtitleMatch(videoName, sName) || sCount == 1) {
                                                                val subDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(videoUri, sChildId)
                                                                val isFirstMatch = result.isEmpty()
                                                                addSubtitleConfig(subDocUri, sName, isDefault = isFirstMatch)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    } else {
                                        val ext = name.substringAfterLast(".", "").lowercase()
                                        if (setOf("srt", "vtt", "ass", "ssa", "sub").contains(ext)) {
                                            val videoName = videoUri.lastPathSegment?.substringAfterLast("/") ?: chapterTitle ?: "video"
                                            if (isSubtitleMatch(videoName, name)) {
                                                val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(videoUri, childId)
                                                val isFirstMatch = result.isEmpty()
                                                addSubtitleConfig(childUri, name, isDefault = isFirstMatch)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
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
    var currentChapterId by remember(chapterId) { mutableLongStateOf(chapterId) }
    val currentChapterIdState = rememberUpdatedState(currentChapterId)
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var chapterTitle by remember { mutableStateOf("Episode") }
    var seriesTitle by remember { mutableStateOf<String?>(null) }
    var isLoadingMetadata by remember { mutableStateOf(true) }

    var allChapters by remember { mutableStateOf<List<com.ballade.hwaran.core.database.entity.ChapterEntity>>(emptyList()) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var dragStartPos by remember { mutableLongStateOf(0L) }
    var isGestureDragging by remember { mutableStateOf(false) }
    var isHorizontalDrag by remember { mutableStateOf<Boolean?>(null) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }

    // Audio & Smooth Volume Services
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15 }
    var volumeFloat by remember(audioManager) {
        val initVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        mutableFloatStateOf(initVol.toFloat() / maxVolume.toFloat())
    }

    var verticalGestureType by remember { mutableStateOf<String?>(null) } // "BRIGHTNESS" or "VOLUME"
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var showVerticalGestureOverlay by remember { mutableStateOf(false) }

    // Double Tap Seek Ripple State
    var doubleTapRippleType by remember { mutableStateOf<String?>(null) } // "BACKWARD" or "FORWARD"

    // Aspect Ratio Modes (0: FIT, 1: CROP/ZOOM, 2: FILL/STRETCH)
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit to Screen",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop to Fill",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch (16:9)"
        )
    }
    var currentResizeModeIndex by remember { mutableIntStateOf(0) }
    var aspectRatioOverlayText by remember { mutableStateOf<String?>(null) }

    // Playback Speed State
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }

    // Custom Subtitles Added by User
    var customSubtitles by remember { mutableStateOf<List<MediaItem.SubtitleConfiguration>>(emptyList()) }

    // Floating Dialog States (All minimal floating popups, NO bottom sheets)
    var showMainSettingsDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showEpisodeDialog by remember { mutableStateOf(false) }
    var showSubStyleDialog by remember { mutableStateOf(false) }

    // Subtitle Customization State
    var subtitleTextSizeSp by remember { mutableFloatStateOf(18f) }
    var subtitleTextColor by remember { mutableStateOf(Color.White) }
    var subtitleBgColor by remember { mutableStateOf(Color(0x99000000)) }
    var subtitleHasOutline by remember { mutableStateOf(true) }

    var currentTracks by remember { mutableStateOf<Tracks?>(null) }

    // External Subtitle Picker Launcher
    val openSubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
            val name = doc?.name ?: "external_subtitle.srt"
            val ext = name.substringAfterLast(".", "").lowercase()
            val mimeType = when (ext) {
                "vtt" -> MimeTypes.TEXT_VTT
                "ass" -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val newSubConfig = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mimeType)
                .setLabel(name)
                .setId(name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()

            customSubtitles = customSubtitles + newSubConfig
            Toast.makeText(context, "Added subtitle: $name", Toast.LENGTH_SHORT).show()
        }
    }

    var showUi by remember { mutableStateOf(true) }
    var isSpeedUpActive by remember { mutableStateOf(false) }
    var lastSpeedUpEndTime by remember { mutableLongStateOf(0L) }
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val coroutineScope = rememberCoroutineScope()

    val hasPipSupport = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun enterPip() {
        if (hasPipSupport) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity?.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val handleBack = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onNavigateBack()
    }

    BackHandler(onBack = handleBack)

    // Auto-hide UI logic: Hides HUD after 2.5 seconds of inactivity if playing
    LaunchedEffect(showUi, showUnlockButton, lastInteractionTime, isPlaying) {
        if (showUi && isPlaying) {
            delay(2500)
            showUi = false
        }
        if (showUnlockButton) {
            delay(2000)
            showUnlockButton = false
        }
    }

    // Auto-clear gesture overlays
    LaunchedEffect(aspectRatioOverlayText) {
        if (aspectRatioOverlayText != null) {
            delay(1500)
            aspectRatioOverlayText = null
        }
    }
    LaunchedEffect(doubleTapRippleType) {
        if (doubleTapRippleType != null) {
            delay(800)
            doubleTapRippleType = null
        }
    }

    val exoPlayer = remember {
        HwaranPlayerHolder.pauseIfPlaying()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }
    }

    val currentIndex = remember(allChapters, currentChapterId) { allChapters.indexOfFirst { it.id == currentChapterId } }
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex >= 0 && currentIndex < allChapters.size - 1

    val switchToChapter: (Long) -> Unit = { newChapterId ->
        val finalPos = exoPlayer.currentPosition
        val finalDur = exoPlayer.duration.coerceAtLeast(0L)
        val chId = currentChapterIdState.value
        if (chId != -1L) {
            videoViewModel.saveLastPosition(chId, finalPos, finalDur)
        }
        exoPlayer.pause()
        currentPosition = 0L
        duration = 0L
        dragPosition = null
        seekDeltaMs = 0L
        dragStartPos = 0L
        lastInteractionTime = System.currentTimeMillis()
        currentChapterId = newChapterId
        onNavigateToChapter(newChapterId)
    }

    val onNextEpisode = rememberUpdatedState {
        if (hasNext) {
            val nextChapter = allChapters.getOrNull(currentIndex + 1)
            if (nextChapter != null) {
                switchToChapter(nextChapter.id)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_ENDED) {
                    onNextEpisode.value()
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }
        }
        exoPlayer.addListener(listener)

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                val finalPos = exoPlayer.currentPosition
                val finalDur = exoPlayer.duration.coerceAtLeast(0L)
                val chId = currentChapterIdState.value
                if (chId != -1L) {
                    videoViewModel.saveLastPosition(chId, finalPos, finalDur)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val finalPos = exoPlayer.currentPosition
            val finalDur = exoPlayer.duration.coerceAtLeast(0L)
            val chId = currentChapterIdState.value
            if (chId != -1L) {
                videoViewModel.saveLastPosition(chId, finalPos, finalDur)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(currentChapterId, externalUri) {
        if (externalUri != null) {
            val parsedUri = Uri.parse(externalUri)
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
                    it.substringBeforeLast(".").replace('_', ' ').replace('-', ' ')
                } ?: "Video"
            com.ballade.hwaran.core.util.HistoryTracker.logEvent("WATCH", chapterTitle, "External Video: $externalUri")
            isLoadingMetadata = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val chapter = database.trackDao().getChapterById(currentChapterId)
            chapter?.let {
                val resolvedTitle = it.title
                val manga = database.libraryDao().getMangaById(it.mangaId)
                val resolvedSeries = if (manga != null && manga.title.isNotBlank() && manga.title != "Standalone Videos") {
                    manga.title
                } else null

                com.ballade.hwaran.core.util.HistoryTracker.logEvent(
                    "WATCH",
                    it.title,
                    "mangaId:${manga?.id ?: -1L}|chapterId:${it.id}|fallback:Series/Channel: ${manga?.title ?: "Unknown"}"
                )

                val chaptersList = database.trackDao().getChaptersForManga(it.mangaId).first().sortedWith(compareBy<com.ballade.hwaran.core.database.entity.ChapterEntity> { 
                    Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloat() ?: Float.MAX_VALUE 
                }.thenBy {
                    it.title.replace(Regex("\\d+")) { matchResult ->
                        matchResult.value.padStart(10, '0')
                    }
                })

                var resolvedUri: Uri? = null
                if (it.folderUri.startsWith("content://")) {
                    val uri = Uri.parse(it.folderUri)
                    val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    if (doc != null && doc.exists() && !doc.isDirectory) {
                        resolvedUri = uri
                    } else {
                        val treeDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                        if (treeDoc != null && treeDoc.isDirectory) {
                            val videoFile = treeDoc.listFiles().find { f ->
                                f.name?.endsWith(".mp4", true) == true || 
                                f.name?.endsWith(".mkv", true) == true || 
                                f.name?.endsWith(".avi", true) == true
                            }
                            resolvedUri = videoFile?.uri
                        } else {
                            resolvedUri = uri
                        }
                    }
                } else {
                    val file = File(it.folderUri)
                    if (file.exists() && file.isFile) {
                        resolvedUri = Uri.fromFile(file)
                    } else if (file.exists() && file.isDirectory) {
                        val videoFile = file.listFiles()?.find { f -> 
                            f.name.endsWith(".mp4", true) || f.name.endsWith(".mkv", true) || f.name.endsWith(".avi", true)
                        }
                        if (videoFile != null) {
                            resolvedUri = Uri.fromFile(videoFile)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    chapterTitle = resolvedTitle
                    seriesTitle = resolvedSeries
                    allChapters = chaptersList
                    if (resolvedUri != null) {
                        videoUri = resolvedUri
                    }
                    isLoadingMetadata = false
                }
            }
        }
    }

    LaunchedEffect(videoUri, currentChapterId, customSubtitles) {
        val uri = videoUri ?: return@LaunchedEffect
        
        // Scan for local sidecar subtitles (.srt, .vtt, .ass) + user added subtitles
        val sidecarSubtitles = withContext(Dispatchers.IO) { findSidecarSubtitles(context, uri, chapterTitle) }
        val allSubs = sidecarSubtitles + customSubtitles
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .apply {
                if (allSubs.isNotEmpty()) {
                    setSubtitleConfigurations(allSubs)
                }
            }
            .build()

        val currentPos = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.isPlaying
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // Ensure text/subtitle tracks are enabled by default in track selection parameters
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()

        if (currentPos > 0) {
            exoPlayer.seekTo(currentPos)
        } else if (currentChapterId != -1L) {
            val chapter = withContext(Dispatchers.IO) { database.trackDao().getChapterById(currentChapterId) }
            if (chapter != null) {
                val seekPos = chapter.position * 1L
                if (seekPos > 0) {
                    exoPlayer.seekTo(seekPos)
                } else {
                    exoPlayer.seekTo(0L)
                }
                videoViewModel.incrementOpenCount(currentChapterId)

                val currentManga = withContext(Dispatchers.IO) { database.libraryDao().getMangaById(chapter.mangaId) }
                if (currentManga != null) {
                    withContext(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        database.libraryDao().insertManga(currentManga.copy(lastReadTitle = chapter.title, lastModified = now))
                        if (currentManga.parentMangaId != null) {
                            database.libraryDao().getMangaById(currentManga.parentMangaId)?.let { parent ->
                                database.libraryDao().insertManga(parent.copy(lastReadTitle = chapter.title, lastModified = now))
                            }
                        }
                    }
                }
            }
        }
        exoPlayer.setPlaybackSpeed(currentSpeed)
        if (wasPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.playWhenReady = true
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (videoUri != null) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isLocked) {
                        detectTapGestures(
                            onPress = {
                                val wasPlaying = isPlaying
                                val job = coroutineScope.launch {
                                    delay(400)
                                    isSpeedUpActive = true
                                    exoPlayer.setPlaybackSpeed(2f)
                                    if (!wasPlaying) exoPlayer.play()
                                    
                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        vibrator?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator?.vibrate(50)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                                if (isSpeedUpActive) {
                                    isSpeedUpActive = false
                                    lastSpeedUpEndTime = System.currentTimeMillis()
                                    exoPlayer.setPlaybackSpeed(currentSpeed)
                                    if (!wasPlaying) exoPlayer.pause()
                                }
                            },
                            onTap = {
                                if (System.currentTimeMillis() - lastSpeedUpEndTime < 600L) {
                                    return@detectTapGestures
                                }
                                if (isLocked) {
                                    showUnlockButton = !showUnlockButton
                                    lastInteractionTime = System.currentTimeMillis()
                                } else {
                                    showUi = !showUi
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            },
                            onDoubleTap = { tapOffset ->
                                if (!isLocked) {
                                    val viewWidth = size.width
                                    val touchX = tapOffset.x
                                    if (touchX < viewWidth * 0.35f) {
                                        // Double-tap left side: Seek -10s without revealing UI
                                        val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(newPos)
                                        doubleTapRippleType = "BACKWARD"
                                    } else if (touchX > viewWidth * 0.65f) {
                                        // Double-tap right side: Seek +10s without revealing UI
                                        val newPos = (exoPlayer.currentPosition + 10000L).coerceIn(0L, duration)
                                        exoPlayer.seekTo(newPos)
                                        doubleTapRippleType = "FORWARD"
                                    } else {
                                        // Double-tap center: Play/Pause
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(duration, isLocked) {
                        if (duration > 0 && !isLocked) {
                            var accumulatedDragX = 0f
                            var accumulatedDragY = 0f
                            detectDragGestures(
                                onDragStart = { _ ->
                                    isGestureDragging = true
                                    dragStartPos = exoPlayer.currentPosition
                                    accumulatedDragX = 0f
                                    accumulatedDragY = 0f
                                    isHorizontalDrag = null
                                    dragPosition = dragStartPos.toFloat()
                                    seekDeltaMs = 0L
                                },
                                onDragEnd = {
                                    isGestureDragging = false
                                    dragPosition = null
                                    seekDeltaMs = 0L
                                    dragStartPos = 0L
                                    showVerticalGestureOverlay = false
                                    isHorizontalDrag = null
                                },
                                onDragCancel = {
                                    isGestureDragging = false
                                    dragPosition = null
                                    seekDeltaMs = 0L
                                    dragStartPos = 0L
                                    showVerticalGestureOverlay = false
                                    isHorizontalDrag = null
                                },
                                onDrag = { change, dragAmount ->
                                    if (isHorizontalDrag == null) {
                                        accumulatedDragX += dragAmount.x
                                        accumulatedDragY += dragAmount.y
                                        if (kotlin.math.abs(accumulatedDragX) > 15f || kotlin.math.abs(accumulatedDragY) > 15f) {
                                            isHorizontalDrag = kotlin.math.abs(accumulatedDragX) > kotlin.math.abs(accumulatedDragY)
                                        }
                                    }
                                    if (isHorizontalDrag == true) {
                                        change.consume()
                                        accumulatedDragX += dragAmount.x
                                        val sensitivity = 100f 
                                        val deltaMs = (accumulatedDragX * sensitivity).toLong()
                                        val newPos = (dragStartPos + deltaMs).coerceIn(0, duration)
                                        
                                        val now = System.currentTimeMillis()
                                        if (now - lastSeekTime > 30) {
                                            exoPlayer.seekTo(newPos)
                                            lastSeekTime = now
                                        }
                                        dragPosition = newPos.toFloat()
                                        seekDeltaMs = newPos - dragStartPos
                                    } else if (isHorizontalDrag == false) {
                                        change.consume()
                                        val touchX = change.position.x
                                        val viewWidth = size.width
                                        val isLeftHalf = touchX < (viewWidth * 0.5f)
                                        
                                        if (isLeftHalf) {
                                            // Brightness Gesture
                                            val lp = activity?.window?.attributes
                                            var b = lp?.screenBrightness ?: 0.5f
                                            if (b < 0f) b = 0.5f
                                            b = (b - dragAmount.y / 600f).coerceIn(0.01f, 1.0f)
                                            lp?.screenBrightness = b
                                            activity?.window?.attributes = lp
                                            verticalGestureType = "BRIGHTNESS"
                                            gestureValue = b
                                            showVerticalGestureOverlay = true
                                        } else {
                                            // Ultra-smooth Volume Gesture
                                            volumeFloat = (volumeFloat - dragAmount.y / 600f).coerceIn(0f, 1f)
                                            val targetVol = (volumeFloat * maxVolume).roundToInt()
                                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                            verticalGestureType = "VOLUME"
                                            gestureValue = volumeFloat
                                            showVerticalGestureOverlay = true
                                        }
                                    }
                                }
                            )
                        }
                    },
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = resizeModes[currentResizeModeIndex].first
                        val edgeType = if (subtitleHasOutline) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                        val subStyle = androidx.media3.ui.CaptionStyleCompat(
                            subtitleTextColor.toArgb(),
                            subtitleBgColor.toArgb(),
                            android.graphics.Color.TRANSPARENT,
                            edgeType,
                            android.graphics.Color.BLACK,
                            null
                        )
                        subtitleView?.apply {
                            setApplyEmbeddedStyles(false)
                            setApplyEmbeddedFontSizes(false)
                            setStyle(subStyle)
                            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
                            visibility = android.view.View.VISIBLE
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                    val targetResizeMode = resizeModes[currentResizeModeIndex].first
                    if (view.resizeMode != targetResizeMode) {
                        view.resizeMode = targetResizeMode
                        view.requestLayout()
                    }
                    val edgeType = if (subtitleHasOutline) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                    val subStyle = androidx.media3.ui.CaptionStyleCompat(
                        subtitleTextColor.toArgb(),
                        subtitleBgColor.toArgb(),
                        android.graphics.Color.TRANSPARENT,
                        edgeType,
                        android.graphics.Color.BLACK,
                        null
                    )
                    view.subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setStyle(subStyle)
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
                        visibility = android.view.View.VISIBLE
                    }
                }
            )

            // Buffering Indicator when controls are hidden
            AnimatedVisibility(
                visible = !showUi && isBuffering,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            }

            // UI Overlay
            AnimatedVisibility(
                visible = showUi && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    
                    // Clean Top Header Bar (Comfortable padding for thumb reaching settings icon)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=0.85f), Color.Black.copy(alpha=0.3f), Color.Transparent)))
                            .padding(start = 12.dp, end = 8.dp, top = if (isPortrait) 48.dp else 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = handleBack, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            val displayTitle = if (!seriesTitle.isNullOrBlank() && seriesTitle != chapterTitle) {
                                "$seriesTitle • $chapterTitle"
                            } else {
                                chapterTitle
                            }
                            Text(
                                text = displayTitle, 
                                color = Color.White, 
                                fontSize = 15.sp, 
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        // Circular Touch Area Settings Icon on Top-Right Corner (60dp touch target, sleek 20dp icon)
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .clickable {
                                    lastInteractionTime = System.currentTimeMillis()
                                    showMainSettingsDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Settings, 
                                contentDescription = "Settings", 
                                tint = Color.White, 
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Center Playback Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (allChapters.size > 1) {
                            IconButton(
                                onClick = { 
                                    if (hasPrev) {
                                        val prevChapter = allChapters.getOrNull(currentIndex - 1)
                                        if (prevChapter != null) {
                                            switchToChapter(prevChapter.id)
                                        }
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
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        if (allChapters.size > 1) {
                            IconButton(
                                onClick = { 
                                    if (hasNext) {
                                        val nextChapter = allChapters.getOrNull(currentIndex + 1)
                                        if (nextChapter != null) {
                                            switchToChapter(nextChapter.id)
                                        }
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

                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    
                    // Bottom Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=0.85f))))
                            .padding(
                                start = if (isLandscape) 120.dp else 24.dp,
                                end = if (isLandscape) 120.dp else 24.dp,
                                bottom = if (isLandscape) 32.dp else 80.dp,
                                top = 32.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rotation and Lock Button Row (Lock Icon shifted to LEFT side)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
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

                            LaunchedEffect(isSliderDragged, isGestureDragging) {
                                if (!isSliderDragged && !isGestureDragging) {
                                    dragPosition = null
                                    seekDeltaMs = 0L
                                    dragStartPos = 0L
                                }
                            }

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
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                thumbColor = Color.White,
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

            // ── Horizontal Drag Seek Overlay ──
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
                            shape = RoundedCornerShape(50)
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

            // ── Vertical Drag Gesture Overlay (Smooth Volume & Brightness) ──
            AnimatedVisibility(
                visible = showVerticalGestureOverlay && isGestureDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val icon = if (verticalGestureType == "BRIGHTNESS") {
                    Icons.Filled.Brightness6
                } else {
                    if (gestureValue > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute
                }
                val label = if (verticalGestureType == "BRIGHTNESS") "Brightness" else "Volume"
                val percent = (gestureValue * 100).toInt()

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Column {
                            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text("$percent%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Double Tap Ripple Feedback Overlay ──
            AnimatedVisibility(
                visible = doubleTapRippleType == "BACKWARD",
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 48.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "« 10s",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = doubleTapRippleType == "FORWARD",
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 48.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "10s »",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Aspect Ratio Mode Notification Pill ──
            AnimatedVisibility(
                visible = aspectRatioOverlayText != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = aspectRatioOverlayText ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // ── 2x Speed Hold Feedback Overlay ──
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
                            shape = RoundedCornerShape(50)
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

            // ── 1. MAIN SETTINGS DIALOG POPUP (SLEEK FADE TRANSLUCENT POPUP) ──
            if (showMainSettingsDialog) {
                SleekFadeDialog(
                    onDismissRequest = { showMainSettingsDialog = false },
                    title = "Player Settings"
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            // Option 1: Subtitles & Audio Tracks
                            Surface(
                                onClick = {
                                    showMainSettingsDialog = false
                                    showTrackDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Filled.Subtitles, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Subtitles & Audio", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Tracks, external .srt", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        item {
                            // Option: Subtitle Style & Appearance
                            Surface(
                                onClick = {
                                    showMainSettingsDialog = false
                                    showSubStyleDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Subtitle Style", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Font size, colors, background", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        item {
                            // Option 2: Playback Speed
                            Surface(
                                onClick = {
                                    showMainSettingsDialog = false
                                    showSpeedDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Filled.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Playback Speed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(String.format("%.2fx", currentSpeed), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        item {
                            // Option 3: Aspect Ratio
                            Surface(
                                onClick = {
                                    currentResizeModeIndex = (currentResizeModeIndex + 1) % resizeModes.size
                                    aspectRatioOverlayText = resizeModes[currentResizeModeIndex].second
                                    showMainSettingsDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Aspect Ratio Mode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(resizeModes[currentResizeModeIndex].second, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (allChapters.size > 1) {
                            item {
                                // Option 4: Episodes
                                Surface(
                                    onClick = {
                                        showMainSettingsDialog = false
                                        showEpisodeDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.06f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Episodes", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                            Text(chapterTitle, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        if (hasPipSupport) {
                            item {
                                // Option 5: Picture in Picture
                                Surface(
                                    onClick = {
                                        showMainSettingsDialog = false
                                        enterPip()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.06f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Picture-in-Picture", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Minimize video window", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                        }
                                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // ── 2. Dedicated Playback Speed Dialog (Thin Sleek Slider 0.25x - 4.0x) ──
            if (showSpeedDialog) {
                SleekFadeDialog(
                    onDismissRequest = { showSpeedDialog = false },
                    title = "Playback Speed (${String.format("%.2fx", currentSpeed)})"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Thin Minimal Custom Slider
                        Slider(
                            value = currentSpeed,
                            onValueChange = { speed ->
                                currentSpeed = (speed * 20).roundToInt() / 20f
                                exoPlayer.setPlaybackSpeed(currentSpeed)
                            },
                            valueRange = 0.25f..4.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color.White, CircleShape)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(3.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Speed preset pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f).forEach { preset ->
                                val isSelected = kotlin.math.abs(currentSpeed - preset) < 0.05f
                                Surface(
                                    onClick = {
                                        currentSpeed = preset
                                        exoPlayer.setPlaybackSpeed(preset)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (preset == 1.0f) "1x" else "${preset}x",
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Dedicated Subtitle & Audio Track Selection Dialog Popup ──
            if (showTrackDialog) {
                SleekFadeDialog(
                    onDismissRequest = { showTrackDialog = false },
                    title = "Subtitles & Audio Tracks"
                ) {
                    val tracks = currentTracks
                    val audioGroups = remember(tracks) {
                        mutableListOf<Pair<Int, Tracks.Group>>().apply {
                            tracks?.groups?.forEachIndexed { idx, group ->
                                if (group.type == C.TRACK_TYPE_AUDIO) add(idx to group)
                            }
                        }
                    }
                    val textGroups = remember(tracks) {
                        mutableListOf<Pair<Int, Tracks.Group>>().apply {
                            tracks?.groups?.forEachIndexed { idx, group ->
                                if (group.type == C.TRACK_TYPE_TEXT) add(idx to group)
                            }
                        }
                    }
                    val isSubDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        // Add External Subtitle Button at top
                        item {
                            Surface(
                                onClick = {
                                    showTrackDialog = false
                                    openSubLauncher.launch(arrayOf("*/*"))
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text("Add External Subtitle (.srt, .vtt, .ass)", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Subtitle Style & Appearance Button
                        item {
                            Surface(
                                onClick = {
                                    showTrackDialog = false
                                    showSubStyleDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text("Subtitle Appearance & Style", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Subtitles Section Header
                        item {
                            Text("SUBTITLES", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }

                        // Subtitle Off Option
                        item {
                            Surface(
                                onClick = {
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        .build()
                                    showTrackDialog = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSubDisabled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Off / None", color = Color.White, fontSize = 13.sp, fontWeight = if (isSubDisabled) FontWeight.Bold else FontWeight.Normal)
                                    if (isSubDisabled) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Subtitle Tracks
                        itemsIndexed(textGroups) { index, pair ->
                            val group = pair.second
                            val format = group.mediaTrackGroup.getFormat(0)
                            val isSelected = group.isSelected && !isSubDisabled
                            val trackName = when {
                                !format.label.isNullOrBlank() -> format.label!!
                                !format.language.isNullOrBlank() -> format.language!!.uppercase()
                                !format.id.isNullOrBlank() -> format.id!!
                                else -> "Subtitle Track #${index + 1}"
                            }

                            Surface(
                                onClick = {
                                    try {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                            .build()
                                    } catch (e: Exception) { e.printStackTrace() }
                                    showTrackDialog = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = trackName,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Audio Section Header
                        if (audioGroups.isNotEmpty()) {
                            item {
                                Text("AUDIO TRACKS", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                            }

                            itemsIndexed(audioGroups) { index, pair ->
                                val group = pair.second
                                val format = group.mediaTrackGroup.getFormat(0)
                                val isSelected = group.isSelected
                                val trackName = when {
                                    !format.label.isNullOrBlank() -> format.label!!
                                    !format.language.isNullOrBlank() -> format.language!!.uppercase()
                                    else -> "Audio Track #${index + 1}"
                                }

                                Surface(
                                    onClick = {
                                        try {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                                .build()
                                        } catch (e: Exception) { e.printStackTrace() }
                                        showTrackDialog = false
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = trackName,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 4. Dedicated Episode Quick Selection Dialog Popup ──
            if (showEpisodeDialog) {
                SleekFadeDialog(
                    onDismissRequest = { showEpisodeDialog = false },
                    title = seriesTitle ?: "Episodes"
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        itemsIndexed(allChapters) { idx, ch ->
                            val isCurrent = ch.id == currentChapterId
                            Surface(
                                onClick = {
                                    showEpisodeDialog = false
                                    switchToChapter(ch.id)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(20.dp)
                                        )
                                        Text(
                                            text = ch.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 5. Dedicated Subtitle Styling & Appearance Dialog Popup ──
            if (showSubStyleDialog) {
                SleekFadeDialog(
                    onDismissRequest = { showSubStyleDialog = false },
                    title = "Subtitle Appearance"
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .heightIn(max = 340.dp)
                            .fillMaxWidth()
                    ) {
                        // Live Preview Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = subtitleBgColor
                                    ) {
                                        Text(
                                            text = "Sample Subtitle Text 123",
                                            color = subtitleTextColor,
                                            fontSize = (subtitleTextSizeSp * 0.85f).sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = if (subtitleHasOutline) {
                                                androidx.compose.ui.text.TextStyle(
                                                    shadow = androidx.compose.ui.graphics.Shadow(
                                                        color = Color.Black,
                                                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                                        blurRadius = 3f
                                                    )
                                                )
                                            } else androidx.compose.ui.text.TextStyle.Default
                                        )
                                    }
                                }
                            }
                        }

                        // Text Size Section
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("TEXT SIZE", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val sizes = listOf(14f to "Small", 18f to "Normal", 22f to "Large", 26f to "Huge")
                                    sizes.forEach { (sz, label) ->
                                        val isSel = subtitleTextSizeSp == sz
                                        Surface(
                                            onClick = { subtitleTextSizeSp = sz },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                            border = if (isSel) BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)) else null,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                Text(label, color = Color.White, fontSize = 11.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Text Color Section
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("TEXT COLOR", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val colors = listOf(
                                        Color.White to "White",
                                        Color(0xFFFFEB3B) to "Yellow",
                                        Color(0xFF00E5FF) to "Cyan",
                                        Color(0xFF69F0AE) to "Green"
                                    )
                                    colors.forEach { (col, label) ->
                                        val isSel = subtitleTextColor == col
                                        Surface(
                                            onClick = { subtitleTextColor = col },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                            border = if (isSel) BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)) else null,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(modifier = Modifier.size(10.dp).background(col, CircleShape))
                                                Text(label, color = Color.White, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Background Style Section
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("BACKGROUND", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val bgStyles = listOf(
                                        Color.Transparent to "None",
                                        Color(0x99000000) to "Dark",
                                        Color(0xFF000000) to "Black"
                                    )
                                    bgStyles.forEach { (bg, label) ->
                                        val isSel = subtitleBgColor == bg
                                        Surface(
                                            onClick = { subtitleBgColor = bg },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                            border = if (isSel) BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)) else null,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                Text(label, color = Color.White, fontSize = 11.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Text Outline Toggle Section
                        item {
                            Surface(
                                onClick = { subtitleHasOutline = !subtitleHasOutline },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Text Shadow / Outline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Switch(
                                        checked = subtitleHasOutline,
                                        onCheckedChange = { subtitleHasOutline = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color.White.copy(alpha = 0.4f),
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

        } else {
            // Loading screen shown while URI is resolving or metadata is being read
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
