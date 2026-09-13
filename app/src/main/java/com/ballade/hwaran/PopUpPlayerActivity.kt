package com.ballade.hwaran

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ballade.hwaran.ui.components.WavyMusicSlider
import com.ballade.hwaran.ui.theme.HwaranTheme
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import java.util.Locale

class PopUpPlayerActivity : ComponentActivity() {

    private val uriState = mutableStateOf<Uri?>(null)
    private var openMainAppClicked = false
    private val musicViewModel: MusicViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[MusicViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MusicViewModel.skipRestore = true
        MusicViewModel.isPopUpActive = true
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        uriState.value = uri

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()

            val appTheme by settingsViewModel.appTheme.collectAsState()
            val batterySavingMode by settingsViewModel.batterySavingMode.collectAsState()
            val usePillAsHighlight by settingsViewModel.usePillAsHighlight.collectAsState()
            val glowColorLong by settingsViewModel.glowColor.collectAsState()

            val activeUri by uriState

            LaunchedEffect(activeUri) {
                if (activeUri != null) {
                    musicViewModel.playExternalAudio(activeUri!!, this@PopUpPlayerActivity)
                }
            }

            HwaranTheme(
                appTheme = appTheme,
                batterySaving = batterySavingMode,
                usePillAsHighlight = usePillAsHighlight,
                pillHighlightColor = glowColorLong
            ) {
                val currentChapter by musicViewModel.currentChapter.collectAsState()
                val currentManga by musicViewModel.currentManga.collectAsState()
                val isPlaying by musicViewModel.isPlaying.collectAsState()
                val playbackProgress by musicViewModel.playbackProgress.collectAsState()
                val currentPosition by musicViewModel.currentPosition.collectAsState()
                val totalDuration by musicViewModel.totalDuration.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 380.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = true
                            ) {}, // Consume clicks inside the card
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF212121)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header Row: App icon, Title, Close button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.cover),
                                    contentDescription = "Hwaran",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Hwaran",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { finish() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Cover Art and Info Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Artwork
                                val cover = currentChapter?.thumbnailUri
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!cover.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = cover,
                                            contentDescription = "Cover Art",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(R.drawable.cover),
                                            contentDescription = "Placeholder Cover",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Track details: Title, Artist, Album
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = currentChapter?.title?.takeIf { it.isNotBlank() } ?: "Audio Track",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = currentChapter?.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val album = currentManga?.title?.takeIf { it.isNotBlank() && it != "External Audio" }
                                    if (album != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = album,
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Time Label (e.g. 0:00/3:42 right-aligned above slider)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "${formatTime(currentPosition)}/${formatTime(totalDuration)}",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Controls Row: Play/Pause button on left, Snake/Wavy Slider on right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { musicViewModel.togglePlayPause() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                var isDragging by remember { mutableStateOf(false) }
                                var sliderPosition by remember { mutableStateOf(0f) }
                                LaunchedEffect(playbackProgress) {
                                    if (!isDragging) sliderPosition = playbackProgress
                                }

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
                                    thumbColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                    trackHeight = 3.dp,
                                    thumbRadius = 7.dp,
                                    waveAmplitudeWhenPlaying = 2.5.dp,
                                    waveLength = 50.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Source URI / Path text
                            Text(
                                text = activeUri?.toString() ?: currentChapter?.folderUri ?: "",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Open in main app button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        openMainAppClicked = true
                                        val mainIntent = Intent(this@PopUpPlayerActivity, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            putExtra("navigate_to_music", true)
                                            data = activeUri
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startActivity(mainIntent)
                                        finish()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Open in main app",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Open in main app",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUri = intent.data
        if (newUri != null) {
            uriState.value = newUri
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !openMainAppClicked) {
            musicViewModel.stopPlayback()
        }
        MusicViewModel.isPopUpActive = false
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }
}
