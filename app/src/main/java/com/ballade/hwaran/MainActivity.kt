package com.ballade.hwaran

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ballade.hwaran.ui.navigation.AppNavGraph
import com.ballade.hwaran.ui.navigation.Screen
import com.ballade.hwaran.ui.components.MiniPlayer
import com.ballade.hwaran.ui.theme.HwaranTheme
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.core.datastore.GlobalSettings
import com.ballade.hwaran.core.datastore.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.os.Build

class MainActivity : ComponentActivity() {
    private var intentState = mutableStateOf<android.content.Intent?>(null)

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        intentState.value = intent
        
        super.onCreate(savedInstanceState)

        // Initialize database and tracking singleton
        val database = com.ballade.hwaran.core.database.AppDatabase.getDatabase(applicationContext)
        com.ballade.hwaran.core.util.HistoryTracker.init(applicationContext, database)

        if (savedInstanceState == null) {
            com.ballade.hwaran.core.util.HistoryTracker.logEvent("APP_OPEN", "App Opened", "")
        }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val isReady by settingsViewModel.isReady.collectAsState()
            
            val musicViewModel: MusicViewModel = viewModel()
            val appTheme by settingsViewModel.appTheme.collectAsState()
            val batterySavingMode by settingsViewModel.batterySavingMode.collectAsState()
            val usePillAsHighlight by settingsViewModel.usePillAsHighlight.collectAsState()
            val glowColorLong by settingsViewModel.glowColor.collectAsState()
            val currentIntent by intentState

            HwaranTheme(
                appTheme = appTheme, 
                batterySaving = batterySavingMode,
                usePillAsHighlight = usePillAsHighlight,
                pillHighlightColor = glowColorLong
            ) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val activeTab by settingsViewModel.activeTab.collectAsState()
                val hasSeenIntro by settingsViewModel.hasSeenIntro.collectAsState()



                // Request notification permission for Media3 background controls
                val permissionState = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted -> }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionState.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val baseAppGradient = com.ballade.hwaran.ui.theme.LocalAppGradient.current
                    Box(modifier = Modifier.fillMaxSize()) {
                        val shouldNavigateToMusic = remember(currentIntent) {
                            currentIntent?.getBooleanExtra("navigate_to_music", false) == true
                        }

                        // Content Layer - Composed when ready or immediately when opening external music
                        if (isReady || shouldNavigateToMusic) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Layer 1: Theme Gradient / Solid Color Background
                                androidx.compose.animation.AnimatedContent(
                                    targetState = baseAppGradient,
                                    transitionSpec = {
                                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)).togetherWith(
                                            androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                                        )
                                    },
                                    label = "bg_gradient_animation"
                                ) { gradient ->
                                    val bgSolid = MaterialTheme.colorScheme.background
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(if (gradient != null) Modifier.background(gradient) else Modifier.background(bgSolid))
                                    )
                                }

                                val startDestination = remember { 
                                    if (hasSeenIntro || shouldNavigateToMusic) Screen.Home.route else Screen.Intro.route 
                                }
                                AppNavGraph(
                                    navController = navController,
                                    startDestination = startDestination,
                                    settingsViewModel = settingsViewModel,
                                    musicViewModel = musicViewModel
                                )
                                
                                // Handle external intents and direct music navigation
                                LaunchedEffect(currentIntent) {
                                    currentIntent?.let { intent ->
                                        if (intent.getBooleanExtra("navigate_to_music", false)) {
                                            musicViewModel.syncExternalAudio(intent.data, this@MainActivity)
                                            settingsViewModel.setActiveTab(1)
                                            navController.navigate(Screen.NowPlaying.route) {
                                                popUpTo(Screen.Home.route) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                            intentState.value = null
                                        } else if (intent.action == android.content.Intent.ACTION_VIEW) {
                                            intent.data?.let { uri ->
                                                val mimeType = intent.type ?: contentResolver.getType(uri) ?: ""
                                                val mimeTypeLower = mimeType.lowercase()
                                                val uriString = uri.toString().lowercase()
                                                val isNovel = mimeTypeLower == "text/plain" ||
                                                        mimeTypeLower == "text/markdown" ||
                                                        com.ballade.hwaran.backend.novel.NovelParser.isNovelFile(uriString) ||
                                                        com.ballade.hwaran.backend.novel.NovelParser.isNovelFile(uri.lastPathSegment)
                                                val isBook = mimeTypeLower == "application/pdf" ||
                                                        mimeTypeLower.contains("epub") ||
                                                        mimeTypeLower.contains("mobipocket") ||
                                                        mimeTypeLower.contains("fictionbook") ||
                                                        mimeTypeLower == "text/html" ||
                                                        mimeTypeLower == "application/xhtml+xml" ||
                                                        com.ballade.hwaran.backend.novel.NovelParser.isBookFile(uriString) ||
                                                        com.ballade.hwaran.backend.novel.NovelParser.isBookFile(uri.lastPathSegment)
                                                when {
                                                    isNovel -> {
                                                        navController.navigate(Screen.ExternalNovel.createRoute(uri.toString()))
                                                    }
                                                    isBook -> {
                                                        navController.navigate(Screen.ExternalPdf.createRoute(uri.toString()))
                                                    }
                                                    mimeTypeLower.startsWith("video/") -> {
                                                        navController.navigate(Screen.ExternalVideo.createRoute(uri.toString()))
                                                    }
                                                    mimeTypeLower.startsWith("audio/") -> {
                                                        musicViewModel.playExternalAudio(uri, this@MainActivity)
                                                        settingsViewModel.setActiveTab(1)
                                                        navController.navigate(Screen.NowPlaying.route) {
                                                            popUpTo(Screen.Home.route) { inclusive = false }
                                                            launchSingleTop = true
                                                        }
                                                    }
                                                }
                                                intentState.value = null // Clear intent after handling
                                            }
                                        }
                                    }
                                }

                                // Mini Player Widget
                                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                                val currentChapter by musicViewModel.currentChapter.collectAsState()
                                val currentLifecycleState by (navBackStackEntry?.lifecycle?.currentStateFlow ?: remember { kotlinx.coroutines.flow.MutableStateFlow(androidx.lifecycle.Lifecycle.State.RESUMED) }).collectAsState(androidx.lifecycle.Lifecycle.State.RESUMED)
                                val isMiniPlayerVisible = remember(currentRoute, currentChapter, activeTab, currentLifecycleState) {
                                    currentChapter != null &&
                                    currentLifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED &&
                                    currentRoute != Screen.NowPlaying.route && 
                                    currentRoute != Screen.Settings.route &&
                                    currentRoute != Screen.Canvas.route &&
                                    currentRoute != Screen.Intro.route &&
                                    currentRoute != Screen.LockSelection.route &&
                                    currentRoute?.startsWith("lock_selection") == false &&
                                    currentRoute?.startsWith("description") == false &&
                                    currentRoute?.startsWith("reader") == false &&
                                    currentRoute?.startsWith("pdf_reader") == false &&
                                    currentRoute?.startsWith("novel_reader") == false &&
                                    currentRoute?.startsWith("video_player") == false &&
                                    currentRoute?.startsWith("external_video") == false &&
                                    currentRoute?.startsWith("external_pdf") == false &&
                                    currentRoute?.startsWith("external_novel") == false &&
                                    currentRoute?.startsWith("external_image") == false &&
                                    currentRoute?.startsWith("edit_song") == false &&
                                    currentRoute?.startsWith("edit_playlist") == false
                                }

                                val isVerticalCompact = true
                                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                val systemBarsBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
                                val maxBottomInset = maxOf(navBarBottom, systemBarsBottom)
                                val targetPadding = if (isLandscape) 16.dp else 0.dp

                                val miniPlayerBottomPadding by animateDpAsState(
                                    targetValue = targetPadding,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                    ),
                                    label = "mini_player_padding"
                                )

                                val alignment = if (isVerticalCompact) Alignment.CenterEnd else Alignment.BottomCenter

                                Box(
                                    modifier = Modifier
                                        .align(alignment)
                                        .padding(bottom = miniPlayerBottomPadding.coerceAtLeast(0.dp))
                                ) {
                                    MiniPlayer(
                                        musicViewModel = musicViewModel,
                                        isVisible = isMiniPlayerVisible,
                                        isVerticalCompact = isVerticalCompact,
                                        onClick = { navController.navigate(Screen.NowPlaying.route) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
    }

    override fun onStop() {
        super.onStop()
        com.ballade.hwaran.core.util.HistoryTracker.logEvent("APP_CLOSE", "App Closed", "")
    }
}
