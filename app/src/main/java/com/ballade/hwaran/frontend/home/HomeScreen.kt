package com.ballade.hwaran.frontend.home

import androidx.activity.compose.BackHandler
import com.ballade.hwaran.core.database.AppDatabase
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
    onNavigateToDescription: (Long) -> Unit,
    onNavigateToHistory: () -> Unit = {}
) {
    var activeDockTab by remember { mutableIntStateOf(0) }
    var previousDockTab by remember { mutableIntStateOf(0) }
    var libraryInitialTag by remember { mutableStateOf("All") }
    val isLibraryLocked by settingsViewModel.isLibraryLocked.collectAsState()
    val libraryPassword by settingsViewModel.libraryPassword.collectAsState()
    val activeTab by settingsViewModel.activeTab.collectAsState()
    val isImportingGlobal by libraryViewModel.isImportingGlobal.collectAsState()
    val isImporting by libraryViewModel.isImporting.collectAsState()
    val importProgress by libraryViewModel.importProgress.collectAsState()
    val isMegaImporting by libraryViewModel.isMegaImporting.collectAsState()
    val megaImportProgress by libraryViewModel.megaImportProgress.collectAsState()

    fun openMusicWindow() {
        if (activeDockTab != 4) {
            previousDockTab = activeDockTab
            activeDockTab = 4
            settingsViewModel.setActiveTab(1)
        }
    }

    fun closeMusicWindow() {
        activeDockTab = previousDockTab
        settingsViewModel.setActiveTab(0)
    }

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
    
    val database = remember(context) { AppDatabase.getDatabase(context) }
    val historyEvents by database.historyDao().getAllHistoryEventsFlow().collectAsState(initial = emptyList())

    LaunchedEffect(activeTab) {
        if (activeTab == 1 && activeDockTab != 4) {
            openMusicWindow()
        } else if (activeTab == 0 && activeDockTab == 4) {
            closeMusicWindow()
        }
    }

    BackHandler(enabled = activeDockTab != 0) {
        if (activeDockTab == 4) {
            closeMusicWindow()
        } else {
            if (activeDockTab == 1) {
                libraryInitialTag = "All"
            }
            activeDockTab = 0
        }
    }
    
    var showGenreDialog by remember { mutableStateOf(false) }
    var lastImportedMangaId by remember { mutableStateOf<Long?>(null) }
    
    var showImportTypeDialog by remember { mutableStateOf(false) }
    var chosenStorageModeForImport by remember { mutableStateOf<Int?>(null) }
    var chosenVideoLayoutModeForImport by remember { mutableStateOf<Int?>(null) }
    // Track which media mode was active when the batch import was kicked off.
    // This prevents the mega-import LaunchedEffect from redirecting to video mode
    // when the user does a PDF/Toon batch import (Bug: mode redirect on non-video batch imports).
    var mediaModeForImport by remember { mutableStateOf<Int?>(null) }
    val isCancelArmed by libraryViewModel.isCancelArmed.collectAsState()

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

    val musicFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            libraryViewModel.importMusicFolder(it, null)
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content Area
            Box(modifier = Modifier.fillMaxSize()) {
                when (activeDockTab) {
                    0 -> {
                        HomeDashboard(
                            allManga = allManga,
                            historyEvents = historyEvents,
                            onNavigateToDescription = onNavigateToDescription,
                            onNavigateToMedia = { id, _ -> onNavigateToDescription(id) },
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigateToHistory = onNavigateToHistory,
                            onNavigateToSearch = { activeDockTab = 2 },
                            onMediaShortcutClick = { targetTag ->
                                if (targetTag == "Music") {
                                    openMusicWindow()
                                } else {
                                    libraryInitialTag = targetTag
                                    activeDockTab = 1
                                }
                            },
                            onOpenMusic = { openMusicWindow() },
                            glowColor = Color(glowColor)
                        )
                    }
                    1 -> {
                        LibraryView(
                            allManga = allManga,
                            onNavigateToDescription = onNavigateToDescription,
                            initialTag = libraryInitialTag,
                            isLibraryLocked = isLibraryLocked,
                            libraryPassword = libraryPassword,
                            glowColor = Color(glowColor),
                            onOpenMusic = { openMusicWindow() }
                        )
                    }
                    2 -> {
                        HomeSearchView(
                            allManga = allManga,
                            onNavigateToDescription = onNavigateToDescription,
                            onPlaySong = { manga, chapters, index ->
                                musicViewModel.playPlaylist(manga, chapters, index)
                            },
                            onBack = { activeDockTab = 0 },
                            glowColor = Color(glowColor)
                        )
                    }
                    4 -> {
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
                        MusicScreen(
                            libraryViewModel = libraryViewModel,
                            settingsViewModel = settingsViewModel,
                            musicViewModel = musicViewModel,
                            onNavigateToPlaylistDetail = onNavigateToDescription,
                            onImportMusic = {
                                musicFolderPickerLauncher.launch(null)
                            },
                            pillGradient = pillGradient,
                            onBack = { closeMusicWindow() }
                        )
                    }
                }
            }

            // Floating Navigation Dock
            androidx.compose.animation.AnimatedVisibility(
                visible = activeDockTab != 2,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(modifier = Modifier.graphicsLayer { alpha = homeUiTransparency }) {
                    HomeNavDock(
                        selectedTab = if (activeDockTab == 4) -1 else activeDockTab,
                        onTabSelected = { tab ->
                            if (tab == 3) {
                                onNavigateToSettings()
                            } else {
                                if (activeDockTab == 4) {
                                    settingsViewModel.setActiveTab(0)
                                }
                                if (tab == 1 && activeDockTab != 1) {
                                    libraryInitialTag = "All"
                                }
                                activeDockTab = tab
                            }
                        },
                        onCenterActionClick = {
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
                                if (activeDockTab == 4) {
                                    musicFolderPickerLauncher.launch(null)
                                } else if (mediaMode == 0 || mediaMode == 1 || mediaMode == 2) {
                                    showImportTypeDialog = true
                                } else {
                                    folderPickerLauncher.launch(null)
                                }
                            }
                        },
                        onCenterActionLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isImportingGlobal) {
                                libraryViewModel.cancelImport()
                                android.widget.Toast.makeText(context, "Canceling import", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        isImporting = isImportingGlobal,
                        importProgress = if (isMegaImporting) megaImportProgress else (importProgress / 100f),
                        glowColor = Color(glowColor),
                        fabStyle = fabStyle
                    )
                }
            }
        }
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
fun ImportTypeDialog(
    initialStorageMode: Int,
    initialVideoLayoutMode: Int,
    mediaMode: Int,
    glowColor: Color,
    onSingleImport: (Int, Int) -> Unit,
    onMegaImport: (Int, Int) -> Unit,
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
