package com.ballade.hwaran.frontend.home

import com.ballade.hwaran.frontend.settings.MediaConfigHelpDialog
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.ballade.hwaran.ui.dialogs.HolographicCloverPanel
import com.ballade.hwaran.ui.dialogs.PremiumGlassPanel
import com.ballade.hwaran.ui.dialogs.PremiumSlider
import com.ballade.hwaran.ui.dialogs.SidebarIcon
import com.ballade.hwaran.frontend.home.music.MusicScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.rounded.Image
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ballade.hwaran.ui.components.JellyToggle
import com.ballade.hwaran.ui.components.JellyToggle3
import com.ballade.hwaran.ui.components.MediaModeIndicator
import com.ballade.hwaran.ui.components.JellyBall
import com.ballade.hwaran.ui.dialogs.spotlightTarget
import com.ballade.hwaran.ui.components.WobblySnakeRing
import com.ballade.hwaran.ui.components.LiquidNavigation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animate
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import kotlin.math.abs
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.ui.dialogs.GenreSelectionDialog
import com.ballade.hwaran.ui.viewmodels.LibraryViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.theme.LocalBatterySaving
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode

@Composable
fun HomeScreen(
    libraryViewModel: LibraryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToDescription: (Long) -> Unit
) {
    val activeTab by settingsViewModel.activeTab.collectAsState()
    val isImportingGlobal by libraryViewModel.isImportingGlobal.collectAsState()
    val isImporting by libraryViewModel.isImporting.collectAsState()
    val importProgress by libraryViewModel.importProgress.collectAsState()
    val isMegaImporting by libraryViewModel.isMegaImporting.collectAsState()
    val megaImportProgress by libraryViewModel.megaImportProgress.collectAsState()

    val storageMode by settingsViewModel.storageMode.collectAsState()
    val mediaMode by settingsViewModel.mediaMode.collectAsState()
    val videoLayoutMode by settingsViewModel.videoLayoutMode.collectAsState()
    val fabStyle by settingsViewModel.fabStyle.collectAsState()
    val glowColor by settingsViewModel.glowColor.collectAsState()
    val homeUiTransparency by settingsViewModel.homeUiTransparency.collectAsState()
    val allManga by libraryViewModel.allMangaState.collectAsState()
    // Collect isNsfwFilter here so we can pass it explicitly to importFolder.
    // This avoids a race condition where the pager-page→filter sync coroutine
    // hasn't fired yet when the picker result callback runs.
    val isNsfwFilter by libraryViewModel.isNsfwFilter.collectAsState()

    val activeScreenToon by settingsViewModel.activeScreenToon.collectAsState()
    val activeScreenBook by settingsViewModel.activeScreenBook.collectAsState()
    val activeScreenVideo by settingsViewModel.activeScreenVideo.collectAsState()
    val currentActiveScreen = remember(mediaMode, activeScreenToon, activeScreenBook, activeScreenVideo) {
        when (mediaMode) {
            0 -> activeScreenToon
            1 -> activeScreenBook
            2 -> activeScreenVideo
            else -> null
        }
    }

    val BgDark = MaterialTheme.colorScheme.background
    val PrimaryPurple = MaterialTheme.colorScheme.primary
    
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    var showGenreDialog by remember { mutableStateOf(false) }
    var lastImportedMangaId by remember { mutableStateOf<Long?>(null) }
    
    var showNameDefaultDialog by remember { mutableStateOf(false) }
    var showNewScreenDialog by remember { mutableStateOf(false) }
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }
    var showImportTypeDialog by remember { mutableStateOf(false) }
    var chosenStorageModeForImport by remember { mutableStateOf<Int?>(null) }
    var chosenVideoLayoutModeForImport by remember { mutableStateOf<Int?>(null) }
    // Track which media mode was active when the batch import was kicked off.
    // This prevents the mega-import LaunchedEffect from redirecting to video mode
    // when the user does a PDF/Toon batch import (Bug: mode redirect on non-video batch imports).
    var mediaModeForImport by remember { mutableStateOf<Int?>(null) }
    val isCancelArmed by libraryViewModel.isCancelArmed.collectAsState()

    var isCloverExpanded by remember { mutableStateOf(false) }
    var isMediaMenuExpanded by remember { mutableStateOf(false) }

    if (showGenreDialog && lastImportedMangaId != null) {
        GenreSelectionDialog(
            onGenreSelected = { genre ->
                if (genre != "Skip") {
                    libraryViewModel.updateMangaGenre(lastImportedMangaId!!, genre)
                }
                showGenreDialog = false
                onNavigateToDescription(lastImportedMangaId!!)
            },
            onDismiss = { 
                showGenreDialog = false 
                onNavigateToDescription(lastImportedMangaId!!)
            }
        )
    }

    val currentScreenState by rememberUpdatedState(currentActiveScreen)
    val currentMediaModeState by rememberUpdatedState(mediaMode)
    val currentVideoLayoutState by rememberUpdatedState(videoLayoutMode)

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { 
            // Fix: Pass explicit boxPurpose to prevent "Channel" mode imports from shifting to "Series"
            val purpose = if (currentMediaModeState == 2) {
                if ((chosenVideoLayoutModeForImport ?: currentVideoLayoutState) == 1) "channel" else "series"
            } else null
            
            if (purpose != null) {
                settingsViewModel.setMediaMode(2)
                settingsViewModel.setVideoLayoutMode(if (purpose == "channel") 1 else 0)
            }
            
            // Pass the current active workspace so imported items belong to it.
            // A null/blank activeScreen means "default unnamed workspace" — pass null so the
            // item gets workspace=null, which is exactly what updateDefaultWorkspace targets later.
            val targetWorkspace = if (currentScreenState.isNullOrBlank()) null else currentScreenState
            libraryViewModel.importFolder(it, false, purpose, targetWorkspace, isNsfwOverride = isNsfwFilter, storageModeOverride = chosenStorageModeForImport) { mangaId ->
                if (activeTab == 1) {
                    lastImportedMangaId = mangaId
                    showGenreDialog = true
                } else {
                    onNavigateToDescription(mangaId)
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { 
            val purpose = if (currentMediaModeState == 2) {
                if ((chosenVideoLayoutModeForImport ?: currentVideoLayoutState) == 1) "channel" else "series"
            } else null
            
            if (purpose != null) {
                settingsViewModel.setMediaMode(2)
                settingsViewModel.setVideoLayoutMode(if (purpose == "channel") 1 else 0)
            }
            
            val targetWorkspace = if (currentScreenState.isNullOrBlank()) null else currentScreenState
            libraryViewModel.importFolder(it, true, purpose, targetWorkspace, isNsfwOverride = isNsfwFilter, storageModeOverride = chosenStorageModeForImport) { mangaId ->
                if (activeTab == 1) {
                    lastImportedMangaId = mangaId
                    showGenreDialog = true
                } else {
                    onNavigateToDescription(mangaId)
                }
            }
        }
    }

    val megaFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(
        )
    ) { uri: Uri? ->
        uri?.let { 
            val purpose = if (currentMediaModeState == 2) {
                if ((chosenVideoLayoutModeForImport ?: currentVideoLayoutState) == 1) "channel" else "series"
            } else null
            
            if (purpose != null) {
                settingsViewModel.setMediaMode(2)
                settingsViewModel.setVideoLayoutMode(if (purpose == "channel") 1 else 0)
            }
            
            val targetWorkspace = if (currentScreenState.isNullOrBlank()) null else currentScreenState
            libraryViewModel.megaImportFolder(it, purpose, targetWorkspace, isNsfwOverride = isNsfwFilter, storageModeOverride = chosenStorageModeForImport)
        }
    }

    val batterySaving = LocalBatterySaving.current
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotationAngle by if (batterySaving) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 50000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotationAngle"
        ).let { remember { it } }
    }

    val pillGradient = remember(glowColor) {
        val colors = when (glowColor) {
            0xFFD481D2L -> listOf(Color(0xFFD481D2), Color(0xFFBE74BE), Color(0xFF703B94))
            0xFFC3A6FEL -> listOf(Color(0xFFC3A6FE), Color(0xFF383852), Color(0xFF161622))
            0xFFFDE4E6L -> listOf(Color(0xFFFDE4E6), Color(0xFFE56A72), Color(0xFF992A31), Color(0xFF410C11))
            0xFF8EB69BL -> listOf(Color(0xFF8EB69B), Color(0xFF235347), Color(0xFF163832), Color(0xFF051F20))
            0xFFD6D3E5L -> listOf(Color(0xFFD6D3E5), Color(0xFFACA5B9), Color(0xFF8F85BE), Color(0xFF666A90), Color(0xFF433D6B))
            0xFF5C9FD9L -> listOf(Color(0xFF5C9FD9), Color(0xFF255DAC), Color(0xFF15326D), Color(0xFF111523))
            0xFFBDC6CDL -> listOf(Color(0xFFBDC6CD), Color(0xFF6A757E), Color(0xFF404C55), Color(0xFF111A22))
            0xFF7A6284L -> listOf(Color(0xFF7A6284), Color(0xFF52425C), Color(0xFF382B3F), Color(0xFF1F1823), Color(0xFF0C080D))
            else -> listOf(Color(glowColor), Color(glowColor))
        }
        Brush.linearGradient(colors)
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (activeTab == 0) {
                    LibraryContent(
                        libraryViewModel = libraryViewModel,
                        settingsViewModel = settingsViewModel,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToDescription = onNavigateToDescription,
                        rotationAngle = rotationAngle,
                        pillGradient = pillGradient,
                        isLandscape = isLandscape,
                        isCloverExpanded = isCloverExpanded,
                        onCloverExpandedChange = { isCloverExpanded = it },
                        isMediaMenuExpanded = isMediaMenuExpanded,
                        onMediaMenuExpandedChange = { isMediaMenuExpanded = it },
                        showNameDefaultDialog = showNameDefaultDialog,
                        onShowNameDefaultDialogChange = { showNameDefaultDialog = it },
                        showNewScreenDialog = showNewScreenDialog,
                        onShowNewScreenDialogChange = { showNewScreenDialog = it },
                        showDeleteSelectionDialog = showDeleteSelectionDialog,
                        onShowDeleteSelectionDialogChange = { showDeleteSelectionDialog = it }
                    )
                } else {
                    MusicScreen(
                        libraryViewModel = libraryViewModel,
                        settingsViewModel = settingsViewModel,
                        musicViewModel = musicViewModel,
                        onNavigateToPlaylistDetail = onNavigateToDescription,
                        onImportMusic = {
                            settingsViewModel.setStorageMode(1)
                            folderPickerLauncher.launch(null)
                        },
                        pillGradient = pillGradient
                    )
                }
            }

            // Floating Navigation Bar (ADAPTIVE FOR LANDSCAPE)
            androidx.compose.animation.AnimatedVisibility(
                visible = !isCloverExpanded && !isMediaMenuExpanded,
                modifier = Modifier
                    .align(if (isLandscape && activeTab == 1) Alignment.CenterStart else Alignment.BottomCenter)
                    .padding(
                        start = if (isLandscape && activeTab == 1) 24.dp else 0.dp, 
                        bottom = if (isLandscape && activeTab == 1) 0.dp else (if (isLandscape) 24.dp else 32.dp)
                    ),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier.graphicsLayer { alpha = homeUiTransparency }
                ) {
                    if (isLandscape && activeTab == 1) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LiquidNavigation(
                                activeTab = activeTab,
                                onTabSelected = { settingsViewModel.setActiveTab(it) },
                                glowColor = Color(glowColor),
                                isVertical = true
                            )
                        }
                    } else {
                        LiquidNavigation(
                            activeTab = activeTab,
                            onTabSelected = { settingsViewModel.setActiveTab(it) },
                            glowColor = Color(glowColor)
                        )
                    }
                }
            }

            // Floating FAB (Manual positioning to avoid repositioning)
            if (activeTab == 0) {
                val onboardingStep by settingsViewModel.onboardingStep.collectAsState()
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (isLandscape) 40.dp else 100.dp, end = 24.dp)
                        .graphicsLayer { alpha = homeUiTransparency }
                        .then(
                            if (onboardingStep == "step_jelly") {
                                Modifier.spotlightTarget("tour_jelly_ball", settingsViewModel)
                            } else Modifier
                        ),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    if (fabStyle == 0) {
                        FloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isImportingGlobal) {
                                    if (!isCancelArmed) {
                                        libraryViewModel.armCancel()
                                        android.widget.Toast.makeText(context, "Press again to cancel", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        libraryViewModel.cancelImport()
                                        android.widget.Toast.makeText(context, "Canceling… finishing current import", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    if (mediaMode == 0 || mediaMode == 1 || mediaMode == 2) {
                                        showImportTypeDialog = true
                                    } else {
                                        folderPickerLauncher.launch(null)
                                    }
                                }
                            },
                            shape = CircleShape,
                            containerColor = PrimaryPurple,
                            contentColor = BgDark,
                            modifier = Modifier.size(64.dp)
                        ) {
                            if (isImportingGlobal) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    val progressVal = if (isMegaImporting) megaImportProgress else (importProgress / 100f)
                                    CircularProgressIndicator(
                                        progress = { progressVal.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        color = BgDark,
                                        trackColor = Color.Transparent,
                                        strokeWidth = 3.dp
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "Import",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .graphicsLayer { rotationZ = rotationAngle }
                                )
                            }
                        }
                    } else if (fabStyle == 1) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .pointerInput(isImportingGlobal, isCancelArmed, mediaMode) {
                                    detectTapGestures(
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onTap = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isImportingGlobal) {
                                                if (!isCancelArmed) {
                                                    libraryViewModel.armCancel()
                                                    android.widget.Toast.makeText(context, "Press again to cancel", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    libraryViewModel.cancelImport()
                                                    android.widget.Toast.makeText(context, "Canceling… finishing current import", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                if (mediaMode == 0 || mediaMode == 1 || mediaMode == 2) {
                                                    showImportTypeDialog = true
                                                } else {
                                                    folderPickerLauncher.launch(null)
                                                }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val scale by animateFloatAsState(targetValue = if (isImportingGlobal) 0.5f else 0.7f, label = "jellyScale")
                            
                            JellyBall(
                                modifier = Modifier
                                    .size(120.dp)
                                    .scale(scale),
                                isHappy = isImportingGlobal,
                                lookUp = false
                            )

                            if (isImportingGlobal) {
                                val density = LocalDensity.current
                                
                                var fakeProgress by remember { mutableFloatStateOf(0f) }
                                LaunchedEffect(isImportingGlobal, importProgress) {
                                    if (isImportingGlobal && importProgress == 0 && !isMegaImporting) {
                                        animate(
                                            initialValue = fakeProgress,
                                            targetValue = 0.15f,
                                            animationSpec = tween(durationMillis = 4000, easing = LinearOutSlowInEasing)
                                        ) { value, _ ->
                                            if (importProgress == 0 && !isMegaImporting) {
                                                fakeProgress = value
                                            }
                                        }
                                    }
                                }

                                val displayedProgress = if (isMegaImporting) {
                                    megaImportProgress.coerceIn(0f, 1f)
                                } else if (importProgress > 0) {
                                    (importProgress / 100f).coerceIn(0f, 1f)
                                } else {
                                    fakeProgress
                                }

                                val animatedProgress by animateFloatAsState(
                                    targetValue = displayedProgress,
                                    animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
                                    label = "importProgress"
                                )
                                
                                Box(contentAlignment = Alignment.Center) {
                                    WobblySnakeRing(
                                        modifier = Modifier.size(76.dp), // Decreased size back
                                        colors = listOf(MaterialTheme.colorScheme.primary, Color.White),
                                        progress = animatedProgress,
                                        strokeWidth = 3f,
                                        wobbleAmplitude = 5f,
                                        wobbleSpeed = 6000,
                                        baseRadiusOverride = with(density) { 34.dp.toPx() }, // Decreased radius back
                                        showOuterRing = false
                                    )
                                    //
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    var showMediaConfigHelp by remember { mutableStateOf(false) }

    if (showMediaConfigHelp) {
        val glowBrightness by settingsViewModel.glowBrightness.collectAsState()
        val glowRadius by settingsViewModel.glowRadius.collectAsState()
        val glowColorLong by settingsViewModel.glowColor.collectAsState()
        MediaConfigHelpDialog(
            glowBrightness = glowBrightness,
            glowRadius = glowRadius,
            glowColorLong = glowColorLong,
            onDismiss = { showMediaConfigHelp = false }
        )
    }

    if (showImportTypeDialog) {
        ImportTypeDialog(
            initialStorageMode = storageMode,
            initialVideoLayoutMode = videoLayoutMode,
            mediaMode = mediaMode,
            glowColor = Color(glowColor),
            onSingleImport = { mode, videoMode ->
                showImportTypeDialog = false
                chosenStorageModeForImport = mode
                chosenVideoLayoutModeForImport = videoMode
                mediaModeForImport = mediaMode
                if (mediaMode == 1) {
                    filePickerLauncher.launch(arrayOf("application/pdf"))
                } else {
                    folderPickerLauncher.launch(null)
                }
            },
            onMegaImport = { mode, videoMode ->
                showImportTypeDialog = false
                chosenStorageModeForImport = mode
                chosenVideoLayoutModeForImport = videoMode
                mediaModeForImport = mediaMode
                megaFolderPickerLauncher.launch(null)
            },
            onShowDocs = { showMediaConfigHelp = true },
            onDismiss = {
                showImportTypeDialog = false
            }
        )
    }

    val megaImportSummary by libraryViewModel.megaImportSummary.collectAsState()
    LaunchedEffect(megaImportSummary) {
        val summary = megaImportSummary
        // Only redirect to video mode if the batch import was actually a VIDEO-mode import.
        // Previously this fired for PDF/Toon batch imports too because chosenVideoLayoutModeForImport
        // was never reset, causing a bogus switch to series/video mode.
        if (summary != null && !summary.isCancelled && summary.imported > 0) {
            if (mediaModeForImport == 2 && chosenVideoLayoutModeForImport != null) {
                settingsViewModel.setMediaMode(2)
                settingsViewModel.setVideoLayoutMode(chosenVideoLayoutModeForImport!!)
            }
            // Reset tracked import context so stale values don't leak into the next import.
            mediaModeForImport = null
            chosenVideoLayoutModeForImport = null
        }
    }
    megaImportSummary?.let { summary ->
        MegaImportSummaryDialog(
            summary = summary,
            onDismiss = {
                libraryViewModel.clearMegaImportSummary()
            }
        )
    }
}


@Composable
fun LibraryContent(
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToDescription: (Long) -> Unit,
    rotationAngle: Float,
    pillGradient: Brush,
    isLandscape: Boolean = false,
    isCloverExpanded: Boolean,
    onCloverExpandedChange: (Boolean) -> Unit,
    isMediaMenuExpanded: Boolean,
    onMediaMenuExpandedChange: (Boolean) -> Unit,
    showNameDefaultDialog: Boolean,
    onShowNameDefaultDialogChange: (Boolean) -> Unit,
    showNewScreenDialog: Boolean,
    onShowNewScreenDialogChange: (Boolean) -> Unit,
    showDeleteSelectionDialog: Boolean,
    onShowDeleteSelectionDialogChange: (Boolean) -> Unit
) {
    val allManga by libraryViewModel.allMangaState.collectAsState()
    val isNsfwFilter by libraryViewModel.isNsfwFilter.collectAsState()
    val sfwText by settingsViewModel.sfwText.collectAsState()
    val nsfwText by settingsViewModel.nsfwText.collectAsState()
    val homeUiTransparency by settingsViewModel.homeUiTransparency.collectAsState()
    val mediaMode by settingsViewModel.mediaMode.collectAsState()
    val storageMode by settingsViewModel.storageMode.collectAsState()
    val videoLayoutMode by settingsViewModel.videoLayoutMode.collectAsState()
    val isLibraryLocked by settingsViewModel.isLibraryLocked.collectAsState()
    val glowBrightness by settingsViewModel.glowBrightness.collectAsState()
    val glowRadius by settingsViewModel.glowRadius.collectAsState()
    val glowColor by settingsViewModel.glowColor.collectAsState()
    val coverTransparency by settingsViewModel.coverTransparency.collectAsState()
    val libraryPassword by settingsViewModel.libraryPassword.collectAsState()

    var mangaToUnlock by remember { mutableStateOf<MangaEntity?>(null) }
    var unlockPasswordInput by remember { mutableStateOf("") }
    var showIncorrectPassword by remember { mutableStateOf(false) }
    var showGalleryNavigator by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeScreenToon by settingsViewModel.activeScreenToon.collectAsState()
    val activeScreenBook by settingsViewModel.activeScreenBook.collectAsState()
    val activeScreenVideo by settingsViewModel.activeScreenVideo.collectAsState()

    val workspacesToon by settingsViewModel.workspacesToon.collectAsState()
    val workspacesBook by settingsViewModel.workspacesBook.collectAsState()
    val workspacesVideo by settingsViewModel.workspacesVideo.collectAsState()
    
    val currentActiveScreen = remember(mediaMode, activeScreenToon, activeScreenBook, activeScreenVideo) {
        when (mediaMode) {
            0 -> activeScreenToon
            1 -> activeScreenBook
            2 -> activeScreenVideo
            else -> null
        }
    }

    val PrimaryPurple = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1 && !isNsfwFilter) {
            libraryViewModel.toggleFilter(true)
        } else if (pagerState.currentPage == 0 && isNsfwFilter) {
            libraryViewModel.toggleFilter(false)
        }
    }
    
    LaunchedEffect(isNsfwFilter) {
        val targetPage = if (isNsfwFilter) 1 else 0
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    var isWrapperExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true, // We want horizontal swipes
            beyondViewportPageCount = 1
        ) { page ->
            // Workspace isolation: match by `workspace` field.
            // null activeScreen = unnamed default workspace → show items with workspace == null.
            // Named workspace → show only items whose workspace matches exactly.
            val pageManga = if (page == 1) {
                allManga.filter { 
                    it.isNsfw && 
                    it.contentType == mediaMode && 
                    it.workspace == currentActiveScreen &&
                    (mediaMode != 2 || it.boxPurpose == (if (videoLayoutMode == 1) "channel" else "series") || (videoLayoutMode == 0 && it.boxPurpose == null)) 
                }
            } else {
                allManga.filter { 
                    !it.isNsfw && 
                    it.contentType == mediaMode && 
                    it.workspace == currentActiveScreen &&
                    (mediaMode != 2 || it.boxPurpose == (if (videoLayoutMode == 1) "channel" else "series") || (videoLayoutMode == 0 && it.boxPurpose == null)) 
                }
            }

            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(if (isLandscape) 110.dp else 175.dp),
                contentPadding = PaddingValues(
                    top = if (isLandscape) 120.dp else 220.dp, 
                    start = if (isLandscape) 16.dp else 16.dp, 
                    end = 16.dp, 
                    bottom = if (isLandscape) 180.dp else 180.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val height = size.height
                        if (height > 0f) {
                            val topFadePx = (if (isLandscape) 120.dp else 220.dp).toPx()
                            val bottomFadePx = (if (isLandscape) 180.dp else 180.dp).toPx()
                            val transitionPx = 30.dp.toPx()
                            
                            val topOpaqueFraction = (topFadePx / height).coerceIn(0f, 1f)
                            val topTransparentFraction = ((topFadePx - transitionPx) / height).coerceIn(0f, 1f)
                            val bottomOpaqueFraction = ((height - bottomFadePx) / height).coerceIn(0f, 1f)
                            val bottomTransparentFraction = ((height - bottomFadePx + transitionPx) / height).coerceIn(0f, 1f)
                            
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    topTransparentFraction to Color.Transparent,
                                    topOpaqueFraction to Color.Black,
                                    bottomOpaqueFraction to Color.Black,
                                    bottomTransparentFraction to Color.Transparent,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) {
                if (pageManga.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3000.dp)
                        )
                    }
                } else {
                    items(pageManga, key = { it.id }) { manga ->
                        MangaCard(
                            manga = manga,
                            coverTransparency = coverTransparency,
                            isLibraryLocked = isLibraryLocked,
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isLibraryLocked && manga.isLocked) {
                                    mangaToUnlock = manga
                                } else {
                                    onNavigateToDescription(manga.id) 
                                }
                            }
                        )
                    }
                }
            }
        }

        // 1. FULL SCREEN BLUR OVERLAY
        androidx.compose.animation.AnimatedVisibility(
            visible = isCloverExpanded || isMediaMenuExpanded || isWrapperExpanded,
            enter = androidx.compose.animation.fadeIn(tween(400)),
            exit = androidx.compose.animation.fadeOut(tween(400)),
            modifier = Modifier.zIndex(100f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Background Blur Layer (Detached from panel to ensure clarity)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .blur(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { 
                                onCloverExpandedChange(false) 
                                onMediaMenuExpandedChange(false)
                                isWrapperExpanded = false
                            }
                        )
                )

                if (isCloverExpanded) {
                    HolographicCloverPanel(
                        isExpanded = isCloverExpanded,
                        onDismiss = { onCloverExpandedChange(false) },
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}) 
                    ) {
                        PremiumSlider(
                            label = "Home UI Transparency",
                            value = homeUiTransparency,
                            onValueChange = { settingsViewModel.setHomeUiTransparency(it) }
                        )
                        PremiumSlider(
                            label = "Cover Transparency",
                            value = coverTransparency,
                            onValueChange = { settingsViewModel.setCoverTransparency(it) }
                        )
                    }
                }

                if (isMediaMenuExpanded) {
                    HolographicCloverPanel(
                        isExpanded = isMediaMenuExpanded,
                        onDismiss = { onMediaMenuExpandedChange(false) },
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}) 
                    ) {
                        Text(
                            text = "Media Mode",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        JellyToggle3(
                            options = listOf("Toon", "Book", "Video"),
                            selectedIndex = mediaMode,
                            onToggle = { settingsViewModel.setMediaMode(it) },
                            glowBrightness = glowBrightness, 
                            glowRadius = glowRadius, 
                            showGlow = false, 
                            glowColorOverride = Color(glowColor)
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Storage Mode",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        JellyToggle(
                            option1 = "Local", option2 = "External",
                            isOption2 = storageMode == 1,
                            onToggle = { settingsViewModel.setStorageMode(if (it) 1 else 0) },
                            glowBrightness = glowBrightness, 
                            glowRadius = glowRadius, 
                            showGlow = false, 
                            glowColorOverride = Color(glowColor)
                        )
                        
                        if (mediaMode == 2) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Shelf",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            JellyToggle(
                                option1 = "Series", option2 = "Channel",
                                isOption2 = videoLayoutMode == 1,
                                onToggle = { settingsViewModel.setVideoLayoutMode(if (it) 1 else 0) },
                                glowBrightness = glowBrightness, 
                                glowRadius = glowRadius, 
                                showGlow = false, 
                                glowColorOverride = Color(glowColor)
                            )
                        }
                    }
                }
            }
        }

        // 2. DETACHED VERTICAL WRAPPER (Music Section Style)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isCloverExpanded && !isMediaMenuExpanded,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isLandscape) 20.dp else 40.dp, end = 24.dp)
                .zIndex(101f),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier.graphicsLayer { alpha = homeUiTransparency },
                contentAlignment = Alignment.TopEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(60.dp)
                ) {
                    // THE STANDALONE TRIGGER BUTTON
                    PremiumGlassPanel(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isWrapperExpanded = !isWrapperExpanded
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isWrapperExpanded) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Text(
                                    text = "𑣲⋆", 
                                    color = Color.White, 
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // CURRENT MEDIA MODE INDICATOR
                    // Holds accumulated vertical drag distance so we only switch mode
                    // once the finger has moved a meaningful amount (threshold = 40dp).
                    var mediaDragAccum by remember { mutableFloatStateOf(0f) }
                    val mediaDragThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }

                    Box(
                        modifier = Modifier
                            .size(64.dp) // Larger hit box for reliable swiping/tapping
                            .pointerInput(mediaMode, videoLayoutMode) {
                                detectTapGestures(
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMediaMenuExpandedChange(true)
                                    },
                                    onTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val currentComposite = when {
                                            mediaMode == 0 -> 0
                                            mediaMode == 1 -> 1
                                            mediaMode == 2 && videoLayoutMode == 0 -> 2
                                            mediaMode == 2 && videoLayoutMode == 1 -> 3
                                            else -> 0
                                        }
                                        val next = (currentComposite + 1) % 4
                                        when (next) {
                                            0 -> settingsViewModel.setMediaMode(0)
                                            1 -> settingsViewModel.setMediaMode(1)
                                            2 -> {
                                                settingsViewModel.setMediaMode(2)
                                                settingsViewModel.setVideoLayoutMode(0)
                                            }
                                            3 -> {
                                                settingsViewModel.setMediaMode(2)
                                                settingsViewModel.setVideoLayoutMode(1)
                                            }
                                        }
                                    }
                                )
                            }
                            // Vertical drag cycles through modes without opening popup
                            .pointerInput(mediaMode, videoLayoutMode) {
                                detectVerticalDragGestures(
                                    onDragStart = { mediaDragAccum = 0f },
                                    onDragEnd   = { mediaDragAccum = 0f },
                                    onDragCancel = { mediaDragAccum = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        mediaDragAccum += dragAmount
                                        when {
                                            // Slide UP (negative dy) → next mode (higher index)
                                            mediaDragAccum < -mediaDragThresholdPx -> {
                                                val currentComposite = when {
                                                    mediaMode == 0 -> 0
                                                    mediaMode == 1 -> 1
                                                    mediaMode == 2 && videoLayoutMode == 0 -> 2
                                                    mediaMode == 2 && videoLayoutMode == 1 -> 3
                                                    else -> 0
                                                }
                                                val next = (currentComposite + 1).coerceAtMost(3)
                                                if (next != currentComposite) {
                                                    when (next) {
                                                        0 -> settingsViewModel.setMediaMode(0)
                                                        1 -> settingsViewModel.setMediaMode(1)
                                                        2 -> {
                                                            settingsViewModel.setMediaMode(2)
                                                            settingsViewModel.setVideoLayoutMode(0)
                                                        }
                                                        3 -> {
                                                            settingsViewModel.setMediaMode(2)
                                                            settingsViewModel.setVideoLayoutMode(1)
                                                        }
                                                    }
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                mediaDragAccum = 0f
                                            }
                                            // Slide DOWN (positive dy) → previous mode (lower index)
                                            mediaDragAccum > mediaDragThresholdPx -> {
                                                val currentComposite = when {
                                                    mediaMode == 0 -> 0
                                                    mediaMode == 1 -> 1
                                                    mediaMode == 2 && videoLayoutMode == 0 -> 2
                                                    mediaMode == 2 && videoLayoutMode == 1 -> 3
                                                    else -> 0
                                                }
                                                val prev = (currentComposite - 1).coerceAtLeast(0)
                                                if (prev != currentComposite) {
                                                    when (prev) {
                                                        0 -> settingsViewModel.setMediaMode(0)
                                                        1 -> settingsViewModel.setMediaMode(1)
                                                        2 -> {
                                                            settingsViewModel.setMediaMode(2)
                                                            settingsViewModel.setVideoLayoutMode(0)
                                                        }
                                                        3 -> {
                                                            settingsViewModel.setMediaMode(2)
                                                            settingsViewModel.setVideoLayoutMode(1)
                                                        }
                                                    }
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                mediaDragAccum = 0f
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        MediaModeIndicator(
                            mediaMode = mediaMode,
                            videoLayoutMode = videoLayoutMode
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // THE SEPARATE REVEALED PILL
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isWrapperExpanded,
                        enter = androidx.compose.animation.expandVertically(
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                            expandFrom = Alignment.Top
                        ) + androidx.compose.animation.fadeIn(tween(300)),
                        exit = androidx.compose.animation.shrinkVertically(
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                            shrinkTowards = Alignment.Top
                        ) + androidx.compose.animation.fadeOut(tween(200))
                    ) {
                        PremiumGlassPanel(modifier = Modifier.wrapContentSize()) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp).width(52.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SidebarIcon(
                                    symbol = "❆",
                                    isSelected = false,
                                    alwaysBright = true,
                                    showHighlight = false, 
                                    onClick = {
                                        isWrapperExpanded = false
                                        onNavigateToSettings()
                                    }
                                )


                                
                                // Clover Trigger Icon
                                val cloverInteractionSource = remember { MutableInteractionSource() }
                                val isCloverPressed by cloverInteractionSource.collectIsPressedAsState()
                                val cloverScale by animateFloatAsState(
                                    targetValue = if (isCloverPressed) 0.85f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    label = "clover_scale"
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .graphicsLayer {
                                            scaleX = cloverScale
                                            scaleY = cloverScale
                                        }
                                        .clip(CircleShape)
                                        .background(if (isCloverExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(if (isCloverExpanded) 1.dp else 0.dp, if (isCloverExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
                                        .clickable(interactionSource = cloverInteractionSource, indication = null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onCloverExpandedChange(true)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "☘︎", 
                                        color = Color.White, 
                                        fontSize = 22.sp
                                    )
                                }

                                // Add New Screen Icon
                                SidebarIcon(
                                    symbol = "⿻",
                                    isSelected = false,
                                    alwaysBright = true,
                                    showHighlight = false,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (currentActiveScreen.isNullOrBlank()) {
                                            onShowNameDefaultDialogChange(true)
                                        } else {
                                            onShowNewScreenDialogChange(true)
                                        }
                                        isWrapperExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. FLOATING FILTER SECTION
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isLandscape) 20.dp else 90.dp, bottom = 24.dp)
                .height(44.dp)
                .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                .graphicsLayer { 
                    clip = false 
                    alpha = homeUiTransparency
                }
                .requiredHeight(250.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                JellyToggle(
                    option1 = sfwText,
                    option2 = nsfwText,
                    isOption2 = isNsfwFilter,
                    onToggle = { libraryViewModel.toggleFilter(it) },
                    glowBrightness = glowBrightness,
                    glowRadius = glowRadius,
                    showGlow = true,
                    glowColorOverride = Color(glowColor)
                )
            }
        }

        // 4. WORKSPACE GALLERY SIDE WIDGET (Redesigned with Dialer System)
        val screenNames = remember(mediaMode, workspacesToon, workspacesBook, workspacesVideo) {
            when (mediaMode) {
                0 -> workspacesToon.toList()
                1 -> workspacesBook.toList()
                2 -> workspacesVideo.toList()
                else -> emptyList()
            }
        }.sorted()
        
        // Ensure the current workspace is always in the list even if it has no items yet
        val activeScreensList = remember(screenNames, currentActiveScreen) {
            val list = screenNames.toMutableList()
            if (!currentActiveScreen.isNullOrBlank() && !list.contains(currentActiveScreen)) {
                list.add(currentActiveScreen)
            }
            list.filter { it.isNotBlank() }.sorted()
        }
        val currentWorkspace = currentActiveScreen ?: ""
        
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { alpha = homeUiTransparency },
            contentAlignment = Alignment.CenterEnd
        ) {
            // Minimized Dash Trigger
            Text(
                text = "-", 
                color = Color.White.copy(alpha = 0.5f), 
                fontSize = 32.sp, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showGalleryNavigator = true 
                    }
                    .padding(start = 16.dp, top = 16.dp, end = 2.dp, bottom = 16.dp)
                    .graphicsLayer { alpha = if (showGalleryNavigator) 0f else 1f }
            )
            
            if (showGalleryNavigator) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.CenterEnd,
                    onDismissRequest = { showGalleryNavigator = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true, dismissOnClickOutside = true)
                ) {
                    // Expanded Premium Navigator Card (Ultra Compact)
                    Surface(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .width(100.dp)
                            .height(140.dp)
                            .animateContentSize(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF121212).copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        tonalElevation = 16.dp
                    ) {
                        val currentIndex = activeScreensList.indexOf(currentWorkspace).coerceAtLeast(0)
                        val dialerState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
                        val interactionSource = remember { MutableInteractionSource() }
                        
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (activeScreensList.isEmpty()) {
                                // Guide for unnamed/empty workspaces
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(interactionSource = interactionSource, indication = null) { showGalleryNavigator = false }
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("No Screen", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Tap ⿻ to start", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp, textAlign = TextAlign.Center)
                                }
                            } else {
                                // THE DIALER SYSTEM
                                val itemHeight = 36.dp
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        0f to Color.Transparent,
                                                        0.4f to Color.Black,
                                                        0.6f to Color.Black,
                                                        1f to Color.Transparent
                                                    ),
                                                    blendMode = BlendMode.DstIn
                                                )
                                            }
                                    ) {
                                        androidx.compose.foundation.lazy.LazyColumn(
                                            state = dialerState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(vertical = 52.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            items(activeScreensList.size) { index ->
                                                val name = activeScreensList[index]
                                                val isSelected = name == currentWorkspace
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(itemHeight)
                                                        .clickable(
                                                            interactionSource = interactionSource,
                                                            indication = null
                                                        ) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            settingsViewModel.setActiveScreen(mediaMode, name)
                                                            showGalleryNavigator = false
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        Text(
                                                            text = name,
                                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                                            fontSize = if (isSelected) 13.sp else 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f),
                                                            textAlign = TextAlign.End
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        // The Dash Marker
                                                        Box(
                                                            modifier = Modifier
                                                                .width(6.dp)
                                                                .height(1.5.dp)
                                                            .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        var nameInput by remember { mutableStateOf("") }
        
        if (showNameDefaultDialog) {
            AlertDialog(
                onDismissRequest = { onShowNameDefaultDialogChange(false); nameInput = "" },
                title = { Text("Name Default Screen", color = Color.White) },
                text = {
                    Column {
                        Text("Please name your current default screen before creating a new one.", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Screen Name", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                confirmButton = {
                    TextButton(onClick = {
                        if (nameInput.isNotBlank()) {
                            val cleanName = nameInput.trim()
                            coroutineScope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val db = com.ballade.hwaran.core.database.AppDatabase.getDatabase(context)
                                    db.libraryDao().updateDefaultWorkspace(mediaMode, cleanName)
                                }
                                settingsViewModel.addWorkspace(mediaMode, cleanName)
                                settingsViewModel.setActiveScreen(mediaMode, cleanName)
                                onShowNameDefaultDialogChange(false)
                                nameInput = ""
                            }
                        }
                    }) { Text("Confirm", color = PrimaryPurple) }
                },
                dismissButton = {
                    TextButton(onClick = { onShowNameDefaultDialogChange(false); nameInput = "" }) { Text("Cancel", color = Color.Gray) }
                }
            )
        }

        if (showNewScreenDialog) {
            val modes = listOf("Toon", "Book", "Video")
            var selectedModeIndex by remember { mutableStateOf(mediaMode) }
            val modeListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = selectedModeIndex)
            
            AlertDialog(
                onDismissRequest = { onShowNewScreenDialogChange(false); nameInput = "" },
                title = { Text("Manage Workspace", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Configure your isolated workspace.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Workspace Name", color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PrimaryPurple,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // DIALER FOR MODE SELECTION (Manual Selection)
                        val itemHeight = 44.dp
                        val pickerHeight = itemHeight * 3
                        val modeInteractionSource = remember { MutableInteractionSource() }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pickerHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                0.3f to Color.Black,
                                                0.7f to Color.Black,
                                                1f to Color.Transparent
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                            ) {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    state = modeListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = itemHeight),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(modes.size) { index ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(itemHeight)
                                                .clickable(
                                                    interactionSource = modeInteractionSource,
                                                    indication = null
                                                ) { 
                                                    selectedModeIndex = index
                                                    coroutineScope.launch { modeListState.animateScrollToItem(index) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = modes[index],
                                                color = if (index == selectedModeIndex) PrimaryPurple else Color.White.copy(alpha = 0.4f),
                                                fontSize = if (index == selectedModeIndex) 20.sp else 16.sp,
                                                fontWeight = if (index == selectedModeIndex) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Indicator line
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = (-60).dp)
                                    .width(20.dp)
                                    .height(2.dp)
                                    .background(PrimaryPurple, RoundedCornerShape(50))
                            )
                        }

                        // DELETE SECTION
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onShowDeleteSelectionDialogChange(true)
                                }
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFE57373).copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Workspace", fontSize = 12.sp, color = Color(0xFFE57373))
                            }
                        }
                    }
                },
                containerColor = Color(0xFF161616),
                shape = RoundedCornerShape(28.dp),
                confirmButton = {
                    TextButton(onClick = {
                        if (nameInput.isNotBlank()) {
                            val cleanName = nameInput.trim()
                            // Register workspace in DataStore. Do NOT switch to it — the user
                            // stays on their current workspace. They can switch via the "-" dial.
                            settingsViewModel.addWorkspace(selectedModeIndex, cleanName)
                            onShowNewScreenDialogChange(false)
                            nameInput = ""
                        }
                    }) { Text("Create", color = PrimaryPurple, fontWeight = FontWeight.ExtraBold) }
                },
                dismissButton = {
                    TextButton(onClick = { onShowNewScreenDialogChange(false); nameInput = "" }) { Text("Cancel", color = Color.Gray) }
                }
            )
        }
        if (showDeleteSelectionDialog) {
            // Use DataStore-backed workspace list so empty workspaces (created via ⿻
            // but not yet imported into) are also visible here.
            val screenNames = remember(mediaMode, workspacesToon, workspacesBook, workspacesVideo) {
                when (mediaMode) {
                    0 -> workspacesToon
                    1 -> workspacesBook
                    2 -> workspacesVideo
                    else -> emptyList()
                }
            }
            var selectedToDelete by remember { mutableStateOf<String?>(null) }
            val deleteInteractionSource = remember { MutableInteractionSource() }

            AlertDialog(
                onDismissRequest = { onShowDeleteSelectionDialogChange(false); selectedToDelete = null },
                title = { Text("Delete Workspace", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        Text("Select a workspace to permanently delete.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(screenNames) { name ->
                                val isSelected = name == selectedToDelete
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) PrimaryPurple.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                                        .border(1.dp, if (isSelected) PrimaryPurple.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
                                        .clickable(
                                            interactionSource = deleteInteractionSource,
                                            indication = null
                                        ) { selectedToDelete = name }
                                        .padding(vertical = 12.dp, horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = name,
                                        color = if (isSelected) PrimaryPurple else Color.White.copy(alpha = 0.8f),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        
                        if (screenNames.isEmpty()) {
                            Text("No workspaces found.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp).align(Alignment.CenterHorizontally))
                        }
                    }
                },
                containerColor = Color(0xFF161616),
                shape = RoundedCornerShape(24.dp),
                confirmButton = {
                    TextButton(
                        enabled = selectedToDelete != null,
                        onClick = {
                            selectedToDelete?.let { 
                                libraryViewModel.deleteWorkspace(mediaMode, it)
                                if (currentActiveScreen == it) {
                                    settingsViewModel.setActiveScreen(mediaMode, null)
                                }
                            }
                            onShowDeleteSelectionDialogChange(false)
                            selectedToDelete = null
                        }
                    ) { Text("Delete", color = if (selectedToDelete != null) Color(0xFFE57373) else Color.Gray, fontWeight = FontWeight.ExtraBold) }
                },
                dismissButton = {
                    TextButton(onClick = { onShowDeleteSelectionDialogChange(false); selectedToDelete = null }) { Text("Cancel", color = Color.Gray) }
                }
            )
        }
    }

    if (mangaToUnlock != null) {
        AlertDialog(
            onDismissRequest = { 
                mangaToUnlock = null 
                unlockPasswordInput = ""
                showIncorrectPassword = false
            },
            title = { Text("Unlock Item", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = unlockPasswordInput,
                        onValueChange = { unlockPasswordInput = it },
                        label = { Text("Password", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
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
                        onNavigateToDescription(mangaToUnlock!!.id)
                        mangaToUnlock = null
                        unlockPasswordInput = ""
                        showIncorrectPassword = false
                    } else {
                        showIncorrectPassword = true
                    }
                }) {
                    Text("Unlock", color = PrimaryPurple)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    mangaToUnlock = null
                    unlockPasswordInput = ""
                    showIncorrectPassword = false
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}


@Composable
fun MangaCard(manga: MangaEntity, coverTransparency: Float, isLibraryLocked: Boolean, onClick: () -> Unit) {
    val isItemLocked = isLibraryLocked && manga.isLocked
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = coverTransparency }) {
                if (isItemLocked) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ꗃꄗ",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (manga.coverPath.isNotEmpty() && manga.coverPath != "android.resource://android/drawable/ic_menu_gallery") {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(manga.coverPath)
                            .crossfade(true)
                            .build(),
                        contentDescription = manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = if (manga.contentType == 3) Icons.Rounded.MusicNote else Icons.Rounded.Image,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.0f), Color.Transparent)))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 1.0f))))
            )

            if (!isItemLocked) {
                Text(
                    text = manga.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 4.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun ImportTypeDialog(
    initialStorageMode: Int,
    initialVideoLayoutMode: Int,
    mediaMode: Int,
    glowColor: Color,
    onSingleImport: (Int, Int) -> Unit,
    onMegaImport: (Int, Int) -> Unit,
    onShowDocs: () -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var selectedStorageMode by remember { mutableStateOf(initialStorageMode) }
    var selectedVideoLayoutMode by remember { mutableStateOf(initialVideoLayoutMode) }

    val dialogTitle = when (mediaMode) {
        0 -> "Add Comics"
        1 -> "Add Books"
        2 -> "Add Videos"
        3 -> "Add Music"
        else -> "Add Content"
    }
    
    val singleTitle = when {
        mediaMode == 1 -> "Add a Single Book"
        mediaMode == 2 && selectedVideoLayoutMode == 1 -> "Add a Single Channel"
        mediaMode == 2 -> "Add a Single Series"
        mediaMode == 3 -> "Add a Single Album"
        else -> "Add a Single Comic"
    }
    
    val singleDesc = when {
        mediaMode == 1 -> "Pick one or more PDF files directly (e.g. 'Harry Potter.pdf')."
        mediaMode == 2 && selectedVideoLayoutMode == 1 -> "Pick a folder with videos from one creator (e.g. 'MrBeast' folder)."
        mediaMode == 2 -> "Pick a folder containing an anime or show (e.g. 'Attack on Titan' folder)."
        mediaMode == 3 -> "Pick a folder containing an album or playlist (e.g. 'Cyberpunk OST' folder)."
        else -> "Pick a folder containing comic chapters (e.g. 'Solo Leveling' folder)."
    }
    
    val megaTitle = "Batch Import (Add Many)"
    val megaDesc = when {
        mediaMode == 1 -> "Pick a big folder full of PDFs, and we'll add them all (e.g. 'My Book Library')."
        mediaMode == 2 && selectedVideoLayoutMode == 1 -> "Pick a big folder full of different creators, to add them all at once (e.g. 'YouTube Archive')."
        mediaMode == 2 -> "Pick a big folder full of different shows, to add them all at once (e.g. 'My Anime Collection')."
        mediaMode == 3 -> "Pick a big folder full of different albums, to add them all at once (e.g. 'My Music Library')."
        else -> "Pick a big folder full of different comics, to add them all at once (e.g. 'My Manga Downloads')."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.45f else 0.9f)
            .clip(RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
        title = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        dialogTitle,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Select how you want to add content to your library",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                IconButton(
                    onClick = onShowDocs,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-12).dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = "Documentation",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        "Importing to",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    MediaModeIndicator(
                        mediaMode = mediaMode,
                        videoLayoutMode = selectedVideoLayoutMode
                    )
                }
                // Storage Mode Toggle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        "Storage Mode",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    JellyToggle(
                        option1 = "Local",
                        option2 = "External",
                        isOption2 = selectedStorageMode == 1,
                        onToggle = { isExternal ->
                            selectedStorageMode = if (isExternal) 1 else 0
                        },
                        glowColorOverride = glowColor
                    )
                }

                // Shelf Toggle (only for Video mode)
                if (mediaMode == 2) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            "Shelf",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        JellyToggle(
                            option1 = "Series",
                            option2 = "Channel",
                            isOption2 = selectedVideoLayoutMode == 1,
                            onToggle = { isChannel ->
                                selectedVideoLayoutMode = if (isChannel) 1 else 0
                            },
                            glowColorOverride = glowColor
                        )
                    }
                }

                // Option 1: Single Import
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable {
                            onSingleImport(selectedStorageMode, selectedVideoLayoutMode)
                        }
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = singleTitle,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = singleDesc,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Option 2: Mega Import
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable {
                            onMegaImport(selectedStorageMode, selectedVideoLayoutMode)
                        }
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = megaTitle,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = megaDesc,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF0D0D0D),
        shape = RoundedCornerShape(32.dp)
    )
}

@Composable
fun MegaImportSummaryDialog(
    summary: LibraryViewModel.MegaImportSummary,
    onDismiss: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.5f else 0.9f)
            .clip(RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (summary.isCancelled) "Import Cancelled" else "Mega Import Complete",
                    color = if (summary.isCancelled) Color(0xFFE56A72) else Color(0xFF8EB69B),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (summary.isCancelled) "The process was stopped by the user." else "Batch scanning process finished.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Summary Metrics Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Candidates", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${summary.total}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                    
                    // Imported
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Imported", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${summary.imported}", color = Color(0xFF8EB69B), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }

                    // Skipped
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Skipped", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${summary.skipped}", color = if (summary.skipped > 0) Color(0xFFE56A72) else Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }

                if (summary.skippedFolders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Skipped Folders Details:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (isLandscape) 120.dp else 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(summary.skippedFolders) { detail ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    color = Color(0xFFE56A72),
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(
                                    text = detail,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Got it", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF0D0D0D),
        shape = RoundedCornerShape(32.dp)
    )
}
