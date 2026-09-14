package com.ballade.hwaran.frontend.home

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.service.VaultMigrationService
import com.ballade.hwaran.core.util.LocalVaultMigrator
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.ballade.hwaran.ui.dialogs.HolographicCloverPanel
import com.ballade.hwaran.ui.dialogs.PremiumGlassPanel
import com.ballade.hwaran.ui.dialogs.PremiumSlider
import com.ballade.hwaran.ui.dialogs.SidebarIcon
import com.ballade.hwaran.frontend.home.importer.ImportStudioSheet
import com.ballade.hwaran.frontend.home.music.MusicScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.*
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
    onNavigateToPlaylistDetail: (Long) -> Unit = onNavigateToDescription,
    onNavigateToEditDescription: (Long) -> Unit = onNavigateToDescription,
    onNavigateToMedia: (Long, Int) -> Unit = { _, _ -> }
) {
    var activeDockTab by rememberSaveable { mutableIntStateOf(if (settingsViewModel.activeTab.value == 1) 4 else 0) }
    var previousDockTab by rememberSaveable { mutableIntStateOf(0) }
    var libraryInitialTag by rememberSaveable { mutableStateOf("All") }
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

    val coroutineScope = rememberCoroutineScope()
    var quickActionsManga by remember { mutableStateOf<MangaEntity?>(null) }
    var isMigratingToVault by remember { mutableStateOf(false) }
    var migrationProgress by remember { mutableIntStateOf(0) }
    var migrationStatus by remember { mutableStateOf("") }

    var pendingVaultMangaId by remember { mutableStateOf<Long?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val targetId = pendingVaultMangaId
        if (targetId != null) {
            VaultMigrationService.start(context, targetId)
            Toast.makeText(context, "Shifting to Local Vault in background...", Toast.LENGTH_SHORT).show()
            pendingVaultMangaId = null
        }
    }

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
            settingsViewModel.setActiveTab(0)
        }
    }
    
    var showGenreDialog by remember { mutableStateOf(false) }
    var lastImportedMangaId by remember { mutableStateOf<Long?>(null) }
    var showImportStudio by remember { mutableStateOf(false) }
    var importConfigMediaMode by remember { mutableStateOf<Int?>(null) }
    var importConfigStorageMode by remember { mutableStateOf<Int?>(null) }
    var importConfigBoxPurpose by remember { mutableStateOf<String?>(null) }
    var importConfigWorkspace by remember { mutableStateOf<String?>(null) }
    var importConfigIsNsfw by remember { mutableStateOf<Boolean?>(null) }

    val availableWorkspaces by database.mediaDao().getAllDistinctWorkspaces().collectAsState(initial = emptyList())
    var chosenStorageModeForImport by remember { mutableStateOf<Int?>(null) }
    var chosenVideoLayoutModeForImport by remember { mutableStateOf<Int?>(null) }
    var mediaModeForImport by remember { mutableStateOf<Int?>(null) }
    val isCancelArmed by libraryViewModel.isCancelArmed.collectAsState()

    if (showGenreDialog && lastImportedMangaId != null) {
        GenreSelectionDialog(
            onGenreSelected = { genre ->
                if (genre != "Skip") {
                    libraryViewModel.updateMangaGenre(lastImportedMangaId!!, genre)
                }
                showGenreDialog = false
                onNavigateToPlaylistDetail(lastImportedMangaId!!)
            },
            onDismiss = { 
                showGenreDialog = false 
                onNavigateToPlaylistDetail(lastImportedMangaId!!)
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
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val targetMode = importConfigMediaMode ?: currentMediaModeState
            val targetStorage = importConfigStorageMode ?: chosenStorageModeForImport
            val targetPurpose = importConfigBoxPurpose ?: if (targetMode == 2) {
                if ((chosenVideoLayoutModeForImport ?: currentVideoLayoutState) == 1) "channel" else "series"
            } else null
            val targetWorkspace = importConfigWorkspace ?: (if (currentScreenState.isNullOrBlank()) null else currentScreenState)
            val targetNsfw = importConfigIsNsfw ?: isNsfwFilter

            if (targetPurpose != null && targetMode == 2) {
                settingsViewModel.setMediaMode(2)
                settingsViewModel.setVideoLayoutMode(if (targetPurpose == "channel") 1 else 0)
            }
            
            libraryViewModel.importFolder(
                uri = it,
                isFile = false,
                boxPurposeOverride = targetPurpose,
                workspace = targetWorkspace,
                isNsfwOverride = targetNsfw,
                storageModeOverride = targetStorage,
                mediaModeOverride = targetMode
            ) { mangaId ->
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
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val targetMode = importConfigMediaMode ?: currentMediaModeState
            val targetStorage = importConfigStorageMode ?: chosenStorageModeForImport
            val targetPurpose = importConfigBoxPurpose ?: if (targetMode == 2) {
                if ((chosenVideoLayoutModeForImport ?: currentVideoLayoutState) == 1) "channel" else "series"
            } else null
            val targetWorkspace = importConfigWorkspace ?: (if (currentScreenState.isNullOrBlank()) null else currentScreenState)
            val targetNsfw = importConfigIsNsfw ?: isNsfwFilter

            if (targetPurpose != null && targetMode == 2) {
                settingsViewModel.setMediaMode(2)
                settingsViewModel.setVideoLayoutMode(if (targetPurpose == "channel") 1 else 0)
            }
            
            libraryViewModel.importFolder(
                uri = it,
                isFile = true,
                boxPurposeOverride = targetPurpose,
                workspace = targetWorkspace,
                isNsfwOverride = targetNsfw,
                storageModeOverride = targetStorage,
                mediaModeOverride = targetMode
            ) { mangaId ->
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
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { 
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val targetMode = importConfigMediaMode ?: currentMediaModeState
            val targetStorage = importConfigStorageMode ?: chosenStorageModeForImport
            val targetPurpose = importConfigBoxPurpose ?: if (targetMode == 2) {
                if ((chosenVideoLayoutModeForImport ?: currentVideoLayoutState) == 1) "channel" else "series"
            } else null
            val targetWorkspace = importConfigWorkspace ?: (if (currentScreenState.isNullOrBlank()) null else currentScreenState)
            val targetNsfw = importConfigIsNsfw ?: isNsfwFilter

            if (targetPurpose != null && targetMode == 2) {
                settingsViewModel.setMediaMode(2)
                settingsViewModel.setVideoLayoutMode(if (targetPurpose == "channel") 1 else 0)
            }
            
            libraryViewModel.megaImportFolder(
                parentUri = it,
                boxPurposeOverride = targetPurpose,
                workspace = targetWorkspace,
                isNsfwOverride = targetNsfw,
                storageModeOverride = targetStorage,
                mediaModeOverride = targetMode
            )
        }
    }

    val musicFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val targetWorkspace = importConfigWorkspace ?: (if (currentScreenState.isNullOrBlank()) null else currentScreenState)
            libraryViewModel.importMusicFolder(it, targetWorkspace)
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content Area with Smooth Fluid Transitions
            AnimatedContent(
                targetState = activeDockTab,
                transitionSpec = {
                    if (targetState == 4) {
                        // Forward transition: Home/Library -> Music
                        (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) { it / 4 } + fadeIn(tween(280)))
                            .togetherWith(slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(180)))
                    } else if (initialState == 4) {
                        // Backward transition: Music -> Home/Library
                        (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) { -it / 6 } + fadeIn(tween(280)))
                            .togetherWith(slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(180)))
                    } else if (targetState > initialState) {
                        // Forward tab switch (e.g. Home -> Library -> Search)
                        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { (it * 0.12f).toInt() } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { (-it * 0.08f).toInt() } + fadeOut(tween(180)))
                    } else {
                        // Backward tab switch (e.g. Search -> Library -> Home)
                        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { (-it * 0.12f).toInt() } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { (it * 0.08f).toInt() } + fadeOut(tween(180)))
                    }
                },
                label = "home_content_transition",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    0 -> {
                        HomeDashboard(
                            allManga = allManga,
                            historyEvents = historyEvents,
                            onNavigateToDescription = onNavigateToDescription,
                            onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
                            onNavigateToMedia = onNavigateToMedia,
                            onPlaySong = { manga, chapters, index ->
                                musicViewModel.playPlaylist(manga, chapters, index)
                            },
                            onNavigateToSettings = onNavigateToSettings,
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
                            onItemLongClick = { manga -> quickActionsManga = manga },
                            glowColor = Color(glowColor)
                        )
                    }
                    1 -> {
                        LibraryView(
                            allManga = allManga,
                            onNavigateToDescription = { id ->
                                val m = allManga.find { it.id == id }
                                if (m?.contentType == 3) onNavigateToPlaylistDetail(id) else onNavigateToDescription(id)
                            },
                            initialTag = libraryInitialTag,
                            isLibraryLocked = isLibraryLocked,
                            libraryPassword = libraryPassword,
                            glowColor = Color(glowColor),
                            onOpenMusic = { openMusicWindow() },
                            onItemLongClick = { manga -> quickActionsManga = manga }
                        )
                    }
                    2 -> {
                        HomeSearchView(
                            allManga = allManga,
                            onNavigateToDescription = { id ->
                                val m = allManga.find { it.id == id }
                                if (m?.contentType == 3) onNavigateToPlaylistDetail(id) else onNavigateToDescription(id)
                            },
                            onPlaySong = { manga, chapters, index ->
                                musicViewModel.playPlaylist(manga, chapters, index)
                            },
                            onBack = { activeDockTab = 0 },
                            glowColor = Color(glowColor)
                        )
                    }
                    4 -> {
                        val pillGradient = remember(glowColor) {
                            val activeColor = Color(glowColor)
                            Brush.linearGradient(
                                listOf(
                                    activeColor.copy(alpha = 0.12f),
                                    Color(0xFF111318)
                                )
                            )
                        }
                        MusicScreen(
                            libraryViewModel = libraryViewModel,
                            settingsViewModel = settingsViewModel,
                            musicViewModel = musicViewModel,
                            onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
                            onImportMusic = {
                                musicFolderPickerLauncher.launch(null)
                            },
                            pillGradient = pillGradient,
                            onBack = { closeMusicWindow() }
                        )
                    }
                }
            }

            // Floating Navigation Dock - Cleanly hidden during Search (2) and Music (4)
            androidx.compose.animation.AnimatedVisibility(
                visible = activeDockTab != 2 && activeDockTab != 4,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(240)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)),
                exit = fadeOut(tween(200)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy))
            ) {
                Box(modifier = Modifier.graphicsLayer { alpha = homeUiTransparency }) {
                    HomeNavDock(
                        selectedTab = activeDockTab,
                        onTabSelected = { tab ->
                            if (tab == 3) {
                                onNavigateToSettings()
                            } else {
                                if (tab == 1 && activeDockTab != 1) {
                                    libraryInitialTag = "All"
                                }
                                activeDockTab = tab
                                settingsViewModel.setActiveTab(0)
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
                                showImportStudio = true
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

    if (showImportStudio) {
        val initialMode = if (activeDockTab == 4) 3 else mediaMode
        ImportStudioSheet(
            initialMediaMode = initialMode,
            initialStorageMode = storageMode,
            initialVideoLayoutMode = videoLayoutMode,
            initialIsNsfw = isNsfwFilter,
            currentWorkspace = currentActiveScreen,
            availableWorkspaces = availableWorkspaces,
            glowColor = Color(glowColor),
            onImportSingleFile = { mode, storage, purpose, workspace, nsfw, mimeTypes ->
                showImportStudio = false
                importConfigMediaMode = mode
                importConfigStorageMode = storage
                importConfigBoxPurpose = purpose
                importConfigWorkspace = workspace
                importConfigIsNsfw = nsfw
                filePickerLauncher.launch(mimeTypes)
            },
            onImportSingleFolder = { mode, storage, purpose, workspace, nsfw ->
                showImportStudio = false
                importConfigMediaMode = mode
                importConfigStorageMode = storage
                importConfigBoxPurpose = purpose
                importConfigWorkspace = workspace
                importConfigIsNsfw = nsfw
                if (mode == 3) {
                    musicFolderPickerLauncher.launch(null)
                } else {
                    folderPickerLauncher.launch(null)
                }
            },
            onImportBatchFolder = { mode, storage, purpose, workspace, nsfw ->
                showImportStudio = false
                importConfigMediaMode = mode
                importConfigStorageMode = storage
                importConfigBoxPurpose = purpose
                importConfigWorkspace = workspace
                importConfigIsNsfw = nsfw
                mediaModeForImport = mode
                chosenVideoLayoutModeForImport = if (purpose == "channel") 1 else 0
                if (mode == 3) {
                    musicFolderPickerLauncher.launch(null)
                } else {
                    megaFolderPickerLauncher.launch(null)
                }
            },
            onDismiss = {
                showImportStudio = false
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

    quickActionsManga?.let { manga ->
        MediaQuickActionsSheet(
            manga = manga,
            isMigrating = isMigratingToVault,
            migrationProgress = migrationProgress,
            migrationStatus = migrationStatus,
            onShiftToLocal = {
                val startMigration = {
                    VaultMigrationService.start(context, manga.id)
                    Toast.makeText(
                        context,
                        "Shifting \"${manga.title}\" to Local Vault in background...",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingVaultMangaId = manga.id
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startMigration()
                }
            },
            onEditMetadata = {
                quickActionsManga = null
                onNavigateToEditDescription(manga.id)
            },
            onDelete = {
                coroutineScope.launch {
                    LocalVaultMigrator.deleteMedia(
                        context = context,
                        database = database,
                        manga = manga
                    )
                    quickActionsManga = null
                }
            },
            onDismiss = {
                quickActionsManga = null
            }
        )
    }
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
