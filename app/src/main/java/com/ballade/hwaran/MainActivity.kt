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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import com.ballade.hwaran.ui.background.DrunkStarsBackground
import com.ballade.hwaran.ui.background.JellyfishBackground
import com.ballade.hwaran.ui.background.KaleidoscopeBackground
import com.ballade.hwaran.ui.background.FlowerBackground
import com.ballade.hwaran.ui.theme.HwaranTheme
import com.ballade.hwaran.ui.dialogs.OnboardingOverlay
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
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

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

                val animationVisibility by settingsViewModel.animationVisibility.collectAsState()
                val animationType by settingsViewModel.animationType.collectAsState()
                val animationSpeed by settingsViewModel.animationSpeed.collectAsState()

                // Effective animation visibility: OFF when battery saving is on
                val effectiveAnimationVisible = animationVisibility && !batterySavingMode

                // Hide system bars globally for immersive premium experience
                LaunchedEffect(Unit) {
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                }

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
                    color = Color.Black // Keep surface black to avoid flickers during navigation
                ) {
                    val baseAppGradient = com.ballade.hwaran.ui.theme.LocalAppGradient.current
                    val appGradient = remember(baseAppGradient, animationType) {
                        if (animationType == 1) {
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFF0C071E), Color(0xFF1D1442))
                            )
                        } else {
                            baseAppGradient
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Content Layer - Only composed when ready, but overlay is always here
                        if (isReady) {
                            val pointerState = remember { com.ballade.hwaran.ui.background.LiquidPointerState() }

                            Box(modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull()
                                            if (change != null) {
                                                pointerState.x = change.position.x
                                                pointerState.y = change.position.y
                                                
                                                // mimic mouse:
                                                // 1. Initial touch = "Mouse Down" (Explode)
                                                // 2. Sliding = "Mouse Hover" (Compact Blob)
                                                // 3. Releasing = Clear state
                                                if (change.changedToDown()) {
                                                    pointerState.triggerExplosion()
                                                    pointerState.isPressed = true
                                                } else if (change.position != change.previousPosition) {
                                                    // While moving, we treat it as "Hovering"
                                                    pointerState.isPressed = false
                                                }

                                                if (!event.changes.any { it.pressed }) {
                                                    pointerState.isPressed = false
                                                }
                                            }
                                        }
                                    }
                                }
                            ) {
                                // Layer 1: Theme Gradient Background
                                Box(modifier = Modifier.fillMaxSize()) {
                                    androidx.compose.animation.AnimatedContent(
                                        targetState = appGradient,
                                        transitionSpec = {
                                            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(500)).togetherWith(
                                                androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(500))
                                            )
                                        },
                                        label = "bg_gradient_animation"
                                    ) { gradient ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .then(if (gradient != null) Modifier.background(gradient) else Modifier)
                                        )
                                    }
                                    // Background Animations
                                    val starsAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (animationType == 0) 1f else 0f,
                                        animationSpec = androidx.compose.animation.core.tween(1500),
                                        label = "stars_alpha"
                                    )
                                    val jellyfishAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (animationType == 1) 1f else 0f,
                                        animationSpec = androidx.compose.animation.core.tween(1500),
                                        label = "jellyfish_alpha"
                                    )
                                    val celestialAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (animationType == 2) 1f else 0f,
                                        animationSpec = androidx.compose.animation.core.tween(1500),
                                        label = "celestial_alpha"
                                    )
                                    val pokerAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (animationType == 3) 1f else 0f,
                                        animationSpec = androidx.compose.animation.core.tween(1500),
                                        label = "poker_alpha"
                                    )
                                    val kaleidoscopeAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (animationType == 4) 1f else 0f,
                                        animationSpec = androidx.compose.animation.core.tween(1500),
                                        label = "kaleidoscope_alpha"
                                    )
                                    val flowerAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (animationType == 5) 1f else 0f,
                                        animationSpec = androidx.compose.animation.core.tween(1500),
                                        label = "flower_alpha"
                                    )
                                    val liquidAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (animationType == 6) 1f else 0f,
                                        animationSpec = androidx.compose.animation.core.tween(1500),
                                        label = "liquid_alpha"
                                    )

                                    val bgScrollOffset by settingsViewModel.backgroundScrollOffset.collectAsState()
                                    var autoScrollOffset by remember { mutableStateOf(0f) }
                                    LaunchedEffect(animationType, animationSpeed, effectiveAnimationVisible) {
                                        if ((animationType == 2 || animationType == 3 || animationType == 4 || animationType == 5) && effectiveAnimationVisible) {
                                            while (isActive) {
                                                autoScrollOffset += 0.8f * animationSpeed
                                                kotlinx.coroutines.delay(16)
                                            }
                                        }
                                    }

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = effectiveAnimationVisible,
                                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(800)),
                                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(800))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            com.ballade.hwaran.ui.background.DrunkStarsBackground(
                                                animationSpeed = animationSpeed,
                                                isEnabled = animationType == 0 && animationVisibility,
                                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = starsAlpha }
                                            )
                                            com.ballade.hwaran.ui.background.JellyfishBackground(
                                                animationSpeed = animationSpeed,
                                                isEnabled = animationType == 1 && effectiveAnimationVisible,
                                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = jellyfishAlpha }
                                            )
                                            com.ballade.hwaran.ui.background.CelestialBackground(
                                                animationSpeed = animationSpeed,
                                                isEnabled = animationType == 2 && effectiveAnimationVisible,
                                                scrollOffset = bgScrollOffset + autoScrollOffset,
                                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = celestialAlpha }
                                            )
                                            com.ballade.hwaran.ui.background.PokerBackground(
                                                animationSpeed = animationSpeed,
                                                isEnabled = animationType == 3 && effectiveAnimationVisible,
                                                scrollOffset = bgScrollOffset + autoScrollOffset,
                                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pokerAlpha }
                                            )
                                            com.ballade.hwaran.ui.background.KaleidoscopeBackground(
                                                animationSpeed = animationSpeed,
                                                isEnabled = animationType == 4 && effectiveAnimationVisible,
                                                scrollOffset = bgScrollOffset + autoScrollOffset,
                                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = kaleidoscopeAlpha }
                                            )
                                            com.ballade.hwaran.ui.background.FlowerBackground(
                                                animationSpeed = animationSpeed,
                                                isEnabled = animationType == 5 && effectiveAnimationVisible,
                                                scrollOffset = bgScrollOffset + autoScrollOffset,
                                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = flowerAlpha }
                                            )
                                            com.ballade.hwaran.ui.background.LiquidBackground(
                                                isEnabled = animationType == 6 && effectiveAnimationVisible,
                                                pointerState = pointerState,
                                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = liquidAlpha }
                                            )
                                        }
                                    }

                                    val startDestination = remember { if (hasSeenIntro) Screen.Home.route else Screen.Intro.route }
                                    AppNavGraph(
                                        navController = navController,
                                        startDestination = startDestination,
                                        settingsViewModel = settingsViewModel,
                                        musicViewModel = musicViewModel
                                    )
                                    
                                    // Handle external intents
                                    LaunchedEffect(currentIntent) {
                                        currentIntent?.let { intent ->
                                            if (intent.action == android.content.Intent.ACTION_VIEW) {
                                                intent.data?.let { uri ->
                                                    val mimeType = intent.type ?: contentResolver.getType(uri) ?: ""
                                                    val mimeTypeLower = mimeType.lowercase()
                                                    when {
                                                        mimeTypeLower.startsWith("video/") -> {
                                                            navController.navigate(Screen.ExternalVideo.createRoute(uri.toString()))
                                                        }
                                                        mimeTypeLower == "application/pdf" -> {
                                                            navController.navigate(Screen.ExternalPdf.createRoute(uri.toString()))
                                                        }
                                                        mimeTypeLower.startsWith("audio/") -> {
                                                            musicViewModel.playExternalAudio(uri, this@MainActivity)
                                                            settingsViewModel.setActiveTab(1)
                                                            navController.navigate(Screen.Home.route) {
                                                                popUpTo(Screen.Home.route) { inclusive = true }
                                                            }
                                                        }
                                                    }
                                                    intentState.value = null // Clear intent after handling
                                                }
                                            }
                                        }
                                    }
                                }

                                // Mini Player Widget
                                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                                val currentChapter by musicViewModel.currentChapter.collectAsState()
                                val isMusicSection = remember(currentRoute, activeTab, currentChapter) {
                                    activeTab == 1 &&
                                    currentRoute != Screen.NowPlaying.route && 
                                    currentRoute != Screen.Settings.route &&
                                    currentRoute?.startsWith("edit_song") == false &&
                                    currentRoute?.startsWith("edit_playlist") == false &&
                                    currentChapter != null
                                }

                                val miniPlayerBottomPadding by animateDpAsState(
                                    targetValue = if (isLandscape) 8.dp else (if (currentRoute == Screen.Home.route) 140.dp else 64.dp),
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                    ),
                                    label = "mini_player_padding"
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = miniPlayerBottomPadding) 
                                ) {
                                    MiniPlayer(
                                        musicViewModel = musicViewModel,
                                        isVisible = isMusicSection,
                                        onClick = { navController.navigate(Screen.NowPlaying.route) }
                                    )
                                }

                                OnboardingOverlay(
                                    settingsViewModel = settingsViewModel,
                                    currentRoute = currentRoute,
                                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                    onNavigateBack = { navController.popBackStack() },
                                    onImportNow = { settingsViewModel.setActiveTab(1) }
                                )
                            }
                        }

                        // Initial Black Overlay for seamless transition from Splash
                        val launchAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
                        LaunchedEffect(isReady) {
                            if (isReady) {
                                // Small buffer so the first Compose frame is settled
                                // (avoids white flash) without sitting on a black screen
                                kotlinx.coroutines.delay(150)
                                launchAlpha.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 900,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                )
                            }
                        }

                        if (launchAlpha.value > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = launchAlpha.value }
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White.copy(alpha = 0.7f),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(40.dp)
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
