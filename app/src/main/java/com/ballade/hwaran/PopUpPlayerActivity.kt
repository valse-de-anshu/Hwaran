package com.ballade.hwaran

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

class PopUpPlayerActivity : ComponentActivity() {

    private val uriState = mutableStateOf<Uri?>(null)
    private var openMainAppClicked = false
    private val musicViewModel: MusicViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[MusicViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.ballade.hwaran.ui.viewmodels.MusicViewModel.skipRestore = true
        com.ballade.hwaran.ui.viewmodels.MusicViewModel.isPopUpActive = true
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

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
                val isPlaying by musicViewModel.isPlaying.collectAsState()
                val playbackProgress by musicViewModel.playbackProgress.collectAsState()
                val currentPosition by musicViewModel.currentPosition.collectAsState()
                val totalDuration by musicViewModel.totalDuration.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(300.dp)
                            .clickable(enabled = false) {}, // Consume clicks
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF16151D)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header Row: App icon, Title, Close button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = R.mipmap.ic_launcher,
                                    contentDescription = "App Icon",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Hwaran",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { finish() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close",
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Cover Art and Info Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Artwork
                                val cover = currentChapter?.thumbnailUri
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(16.dp))
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
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = "Placeholder",
                                            tint = Color.White.copy(alpha = 0.2f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Track details
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = currentChapter?.title ?: "Unknown Song",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentChapter?.artist ?: "Unknown Artist",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Timeline Row with volume icon on left and WavyMusicSlider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "Volume",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
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
                                    activeTrackColor = Color(0xFF7A6284),
                                    thumbColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                                    trackHeight = 4.dp,
                                    thumbRadius = 6.dp,
                                    waveAmplitudeWhenPlaying = 3.dp,
                                    waveLength = 60.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Time Labels
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(currentPosition),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = formatTime(totalDuration),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Center Play/Pause button
                            IconButton(
                                onClick = { musicViewModel.togglePlayPause() },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Source URI/Path text
                            Text(
                                text = currentChapter?.folderUri ?: "",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Open in main app button
                            Button(
                                onClick = {
                                    openMainAppClicked = true
                                    val mainIntent = Intent(this@PopUpPlayerActivity, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        putExtra("navigate_to_music", true)
                                        data = activeUri
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(mainIntent)
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = "Open",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Open in main app",
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
        com.ballade.hwaran.ui.viewmodels.MusicViewModel.isPopUpActive = false
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
