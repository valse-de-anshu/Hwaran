package com.ballade.hwaran.frontend.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import com.ballade.hwaran.ui.theme.AVAILABLE_APP_THEMES
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ballade.hwaran.R
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.ui.components.DevilJellyBall
import com.ballade.hwaran.ui.components.JellyBall
import com.ballade.hwaran.ui.components.JellyToggle
import com.ballade.hwaran.ui.components.JellyToggle3
import com.ballade.hwaran.ui.dialogs.PremiumGlassPanel
import com.ballade.hwaran.ui.dialogs.PremiumSlider
import com.ballade.hwaran.ui.dialogs.SidebarIcon
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PremiumSection {
    CONFIG, APPEARANCE, ENVIRONMENT, ADD_BUTTON, SECURITY, ABOUT
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToLockSelection: () -> Unit = {},
    onNavigateToCanvas: () -> Unit = {}
) {
    var activeSection by remember { mutableStateOf<PremiumSection?>(PremiumSection.CONFIG) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Smooth entrance animation for the rail
    var isRailVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isRailVisible = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {

            // Top Back Button (Floating above everything)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(top = 40.dp, bottom = 32.dp)
                    .zIndex(10f)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(start = 8.dp, top = 20.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            // Main Layout: Rail + Animated Content Panel
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
                    .padding(top = if (isLandscape) 64.dp else 160.dp, bottom = if (isLandscape) 16.dp else 32.dp),
                verticalAlignment = Alignment.Top
            ) {

                // --- THE NAVIGATION RAIL ---
                AnimatedVisibility(
                    visible = isRailVisible,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(350)),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                ) {
                    PremiumGlassPanel(modifier = Modifier.wrapContentSize()) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = if (isLandscape) 320.dp else 800.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = if (isLandscape) 12.dp else 24.dp, horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SidebarIcon(symbol = "´ཀ`", isSelected = activeSection == PremiumSection.CONFIG) {
                                activeSection = if (activeSection == PremiumSection.CONFIG) null else PremiumSection.CONFIG
                            }
                            SidebarIcon(symbol = "ִֶָ࣪☾", isSelected = activeSection == PremiumSection.APPEARANCE) {
                                activeSection = if (activeSection == PremiumSection.APPEARANCE) null else PremiumSection.APPEARANCE
                            }
                            SidebarIcon(symbol = "𓏲ּ𝄢", isSelected = activeSection == PremiumSection.ENVIRONMENT) {
                                activeSection = if (activeSection == PremiumSection.ENVIRONMENT) null else PremiumSection.ENVIRONMENT
                            }
                            SidebarIcon(symbol = "ᯓ⚽︎", isSelected = activeSection == PremiumSection.ADD_BUTTON) {
                                activeSection = if (activeSection == PremiumSection.ADD_BUTTON) null else PremiumSection.ADD_BUTTON
                            }
                            SidebarIcon(symbol = "ꗃꄗ", isSelected = activeSection == PremiumSection.SECURITY) {
                                activeSection = if (activeSection == PremiumSection.SECURITY) null else PremiumSection.SECURITY
                            }
                            SidebarIcon(symbol = "ᝰ.", isSelected = activeSection == PremiumSection.ABOUT) {
                                activeSection = if (activeSection == PremiumSection.ABOUT) null else PremiumSection.ABOUT
                            }
                        }
                    }
                }

                // --- THE EXPANDING CONTENT PANEL ---
                val isCompactSection = activeSection == PremiumSection.ADD_BUTTON || activeSection == PremiumSection.SECURITY
                AnimatedVisibility(
                    visible = activeSection != null,
                    enter = fadeIn(tween(250)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    ),
                    exit = fadeOut(tween(180)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(180)
                    ),
                    modifier = if (isCompactSection)
                        Modifier.weight(1f).padding(end = 16.dp).wrapContentHeight(Alignment.Top)
                    else
                        Modifier.weight(1f).padding(end = 16.dp).fillMaxHeight()
                ) {
                    PremiumGlassPanel(
                        modifier = if (isCompactSection)
                            Modifier.fillMaxWidth().wrapContentHeight()
                        else
                            Modifier.fillMaxSize()
                    ) {
                        AnimatedContent(
                            targetState = activeSection,
                            transitionSpec = {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                            },
                            modifier = if (isCompactSection)
                                Modifier.fillMaxWidth().wrapContentHeight()
                            else
                                Modifier.fillMaxSize(),
                            label = "content"
                        ) { section ->
                            val scrollState = rememberScrollState()
                            val colModifier = if (section == PremiumSection.ADD_BUTTON || section == PremiumSection.SECURITY)
                                Modifier.fillMaxWidth().wrapContentHeight().padding(24.dp).verticalScroll(scrollState)
                            else
                                Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)
                            Column(
                                modifier = colModifier,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (section) {
                                    PremiumSection.CONFIG -> ConfigContent(settingsViewModel)
                                    PremiumSection.APPEARANCE -> AppearanceContent(settingsViewModel)
                                    PremiumSection.ENVIRONMENT -> EnvironmentContent(settingsViewModel, onNavigateToCanvas)
                                    PremiumSection.ADD_BUTTON -> AddButtonContent(settingsViewModel)
                                    PremiumSection.SECURITY -> SecurityContent(settingsViewModel, onNavigateToLockSelection)
                                    PremiumSection.ABOUT -> AboutContent(scrollState, settingsViewModel, onNavigateBack)
                                    null -> {}
                                }
                                if (section != PremiumSection.ADD_BUTTON && section != PremiumSection.SECURITY) {
                                    Spacer(modifier = Modifier.height(40.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// CONFIG SECTION
// ---------------------------------------------------------

@Composable
fun ConfigContent(vm: SettingsViewModel) {
    val mediaMode by vm.mediaMode.collectAsState()
    val storageMode by vm.storageMode.collectAsState()
    val videoLayoutMode by vm.videoLayoutMode.collectAsState()
    val musicMode by vm.musicMode.collectAsState()
    val glowBrightness by vm.glowBrightness.collectAsState()
    val glowRadius by vm.glowRadius.collectAsState()
    val glowColorLong by vm.glowColor.collectAsState()
    val batterySavingMode by vm.batterySavingMode.collectAsState()

    SectionHeader("Configuration", "Core app behavior")

    PremiumToggleRow("Media Mode") {
        JellyToggle3(
            options = listOf("Toon", "Book", "Video", "Music"),
            selectedIndex = mediaMode,
            onToggle = { vm.setMediaMode(it) },
            glowBrightness = glowBrightness,
            glowRadius = glowRadius,
            showGlow = false,
            glowColorOverride = Color(glowColorLong)
        )
    }

    if (mediaMode == 2) {
        PremiumToggleRow("Shelf Layout") {
            JellyToggle(
                option1 = "Series",
                option2 = "Channel",
                isOption2 = videoLayoutMode == 1,
                onToggle = { vm.setVideoLayoutMode(if (it) 1 else 0) },
                glowBrightness = glowBrightness,
                glowRadius = glowRadius,
                showGlow = false,
                glowColorOverride = Color(glowColorLong)
            )
        }
    }

    if (mediaMode == 3) {
        PremiumToggleRow("Music Display") {
            JellyToggle(
                option1 = "Album Art",
                option2 = "Conductor",
                isOption2 = musicMode == 1,
                onToggle = { vm.setMusicMode(if (it) 1 else 0) },
                glowBrightness = glowBrightness,
                glowRadius = glowRadius,
                showGlow = false,
                glowColorOverride = Color(glowColorLong)
            )
        }
    }

    PremiumToggleRow("Storage Mode") {
        JellyToggle(
            option1 = "Local",
            option2 = "External",
            isOption2 = storageMode == 1,
            onToggle = { vm.setStorageMode(if (it) 1 else 0) },
            glowBrightness = glowBrightness,
            glowRadius = glowRadius,
            showGlow = false,
            glowColorOverride = Color(glowColorLong)
        )
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // ── All Files Access Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (hasStoragePermission) Color(0xFF142416) else Color(0xFF261D12),
        border = BorderStroke(
            1.dp,
            if (hasStoragePermission) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color(0xFFFF9800).copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        context.startActivity(intent)
                    }
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasStoragePermission) Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else Color(0xFFFF9800).copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasStoragePermission) Icons.Rounded.CheckCircle else Icons.Rounded.FolderShared,
                    contentDescription = null,
                    tint = if (hasStoragePermission) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "All Files Access",
                    color = if (hasStoragePermission) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (hasStoragePermission)
                        "Active • Full access to external storage & local vault migration"
                    else
                        "Tap to grant • Required for reading external media & shifting to vault",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Manage",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    var isPruning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val database = remember { com.ballade.hwaran.core.database.AppDatabase.getDatabase(context) }

    Spacer(modifier = Modifier.height(10.dp))

    // ── Clean Missing Media Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isPruning) {
                isPruning = true
                scope.launch {
                    val count = com.ballade.hwaran.core.util.LocalVaultMigrator.pruneMissingMedia(context, database)
                    isPruning = false
                    android.widget.Toast.makeText(
                        context,
                        if (count > 0) "Cleaned $count deleted media items from library" else "Library is clean. No missing items found.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                if (isPruning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Clean Missing Media",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isPruning) "Scanning library for deleted files..."
                    else "Scan and remove library entries whose files were deleted from storage",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Run",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ── Battery Saving Mode ────────────────────────────────────────
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (batterySavingMode) Color(0xFF1A2A1A) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (batterySavingMode) Color(0xFF4CAF50).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (batterySavingMode) Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else Color.White.copy(alpha = 0.06f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BatteryChargingFull,
                    contentDescription = null,
                    tint = if (batterySavingMode) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Battery Saving",
                    color = if (batterySavingMode) Color(0xFF4CAF50) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Pauses ambient backgrounds to maximize battery",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = batterySavingMode,
                onCheckedChange = { vm.setBatterySavingMode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.8f),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}

// ---------------------------------------------------------
// APPEARANCE SECTION
// ---------------------------------------------------------

@Composable
fun AppearanceContent(vm: SettingsViewModel) {
    val appTheme by vm.appTheme.collectAsState()

    SectionHeader("Appearance", "Luxury dark finishes")

    Text("App Theme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    val themes = AVAILABLE_APP_THEMES.map { it.id to it.name }

    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 4
    ) {
        themes.forEach { (themeId, themeName) ->
            val isSelected = appTheme == themeId
            val gradientBrush = getGradientForTheme(themeId)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(gradientBrush)
                        .border(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                            CircleShape
                        )
                        .clickable { vm.setAppTheme(themeId) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    themeName,
                    color = if (isSelected) Color(0xFFE6E8EC) else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------
// ADD BUTTON SECTION
// ---------------------------------------------------------

@Composable
fun AddButtonContent(vm: SettingsViewModel) {
    val fabStyle by vm.fabStyle.collectAsState()

    SectionHeader("Button Style", "Choose your action trigger")

    val styles = listOf(1 to "JellyBall", 2 to "Devil Ball")
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        styles.forEach { (id, name) ->
            val isSelected = (fabStyle == id) || (id == 1 && fabStyle == 0)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f))
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { vm.setFabStyle(id) }
                    .padding(vertical = 16.dp, horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (id == 1) {
                        JellyBall(modifier = Modifier.size(56.dp), enableJump = false, isTrapped = true)
                    } else {
                        DevilJellyBall(modifier = Modifier.size(68.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    name,
                    color = if (isSelected) Color.White else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------
// ENVIRONMENT SECTION
// ---------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnvironmentContent(vm: SettingsViewModel, onNavigateToCanvas: () -> Unit = {}) {
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary

    SectionHeader("Environment", "Atmosphere & relaxation")

    // Dedicated Aesthetic Blank Canvas Card
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF14131F),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    primary.copy(alpha = 0.65f),
                    primary.copy(alpha = 0.35f),
                    primary.copy(alpha = 0.55f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNavigateToCanvas()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            primary.copy(alpha = 0.09f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header badge + Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.15f))
                            .border(1.dp, primary.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "ZEN ATMOSPHERE",
                            color = primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Blank Canvas",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "An immersive, distraction-free sanctuary to experience ambient visual motions and relax with your music.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Feature tags with FlowRow and no-wrap text to avoid any awkward wrapping
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Pure Visuals", "Audio Zen", "Minimal").forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = "• $tag",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Enter Canvas Action Button
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToCanvas()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF222631),
                        contentColor = Color(0xFFE6E8EC)
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Enter Canvas",
                            color = Color(0xFFE6E8EC),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFFE6E8EC),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// SECURITY SECTION
// ---------------------------------------------------------

@Composable
fun SecurityContent(
    vm: SettingsViewModel,
    onNavigateToLockSelection: () -> Unit
) {
    val isLibraryLocked by vm.isLibraryLocked.collectAsState()
    val libraryPassword by vm.libraryPassword.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var isSettingNewPassword by remember { mutableStateOf(false) }
    var isResettingPassword by remember { mutableStateOf(false) }
    var showIncorrectPassword by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var showWipeConfirmation by remember { mutableStateOf(false) }

    if (showWipeConfirmation) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmation = false },
            title = { Text("Wipe History", color = Color.White) },
            text = {
                Text(
                    text = "Do you really want to completely wipe all your history data?",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        database.historyDao().clearAllHistoryEvents()
                    }
                    showWipeConfirmation = false
                }) {
                    Text("Yes", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmation = false }) {
                    Text("No", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                passwordInput = ""
                isSettingNewPassword = false
                isResettingPassword = false
                showIncorrectPassword = false
            },
            title = {
                Text(
                    if (isSettingNewPassword) "Set New Password"
                    else if (isResettingPassword) "Enter Current Password"
                    else "Enter Password",
                    color = Color.White
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    if (showIncorrectPassword) {
                        Text(
                            "Incorrect password",
                            color = Color(0xFFE57373),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isSettingNewPassword) {
                        vm.setLibraryPassword(passwordInput)
                        showPasswordDialog = false
                        passwordInput = ""
                        isSettingNewPassword = false
                    } else if (isResettingPassword) {
                        if (passwordInput == libraryPassword) {
                            passwordInput = ""
                            isResettingPassword = false
                            isSettingNewPassword = true
                            showIncorrectPassword = false
                        } else {
                            showIncorrectPassword = true
                        }
                    } else {
                        if (passwordInput == libraryPassword) {
                            vm.setIsLibraryLocked(!isLibraryLocked)
                            showPasswordDialog = false
                            passwordInput = ""
                            showIncorrectPassword = false
                        } else {
                            showIncorrectPassword = true
                        }
                    }
                }) {
                    Text(
                        if (isSettingNewPassword) "Set" else "Confirm",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    passwordInput = ""
                    isSettingNewPassword = false
                    isResettingPassword = false
                    showIncorrectPassword = false
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    SectionHeader("Security", "Vault protection")

    Text(
        text = "Secure your private library items with a password. Locked items will display a placeholder cover and require a password to access.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle Lock/Unlock
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isLibraryLocked) "Locked" else "Unlocked",
                color = Color.White,
                modifier = Modifier.padding(end = 8.dp)
            )
            Switch(
                checked = isLibraryLocked,
                onCheckedChange = {
                    if (libraryPassword.isEmpty()) {
                        isSettingNewPassword = true
                        showPasswordDialog = true
                    } else {
                        showPasswordDialog = true
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            )
        }

        // Select Items Button
        IconButton(
            onClick = onNavigateToLockSelection,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Text(
                text = "ꗃꄗ",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Reset Password Button
        IconButton(
            onClick = {
                if (libraryPassword.isNotEmpty()) {
                    isResettingPassword = true
                    showPasswordDialog = true
                } else {
                    isSettingNewPassword = true
                    showPasswordDialog = true
                }
            },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "Reset Password",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    SectionHeader("History Tracking", "Activity & playback stats")

    Text(
        text = "Track app launches, folder imports, item deletions, theme switches, password updates, and playback statistics. Toggle tracking, view insights, or wipe logs below.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 8.dp)
    )

    val stopTracking by vm.stopTracking.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (stopTracking) "Stopped" else "Tracking",
            color = Color.White,
            modifier = Modifier.padding(end = 8.dp),
            fontSize = 14.sp
        )
        Switch(
            checked = stopTracking,
            onCheckedChange = { vm.setStopTracking(it) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Wipe History Button
    Button(
        onClick = { showWipeConfirmation = true },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.04f),
            contentColor = Color(0xFFE57373)
        ),
        border = BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.25f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Wipe Data",
                tint = Color(0xFFE57373),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Wipe History Data",
                color = Color(0xFFE57373),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------
// ABOUT SECTION
// ---------------------------------------------------------

@Composable
fun AboutContent(scrollState: ScrollState, vm: SettingsViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    var hasBeenTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        hasBeenTriggered = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (hasBeenTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "author_section_alpha"
    )

    SectionHeader("About", "Do you believe in owning things?")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(0) // Transparent
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        webChromeClient = android.webkit.WebChromeClient()
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(
                                view: android.webkit.WebView?,
                                url: String?
                            ) {
                                view?.evaluateJavascript(
                                    "if(window.startAuthorAnimation) window.startAuthorAnimation();",
                                    null
                                )
                            }
                        }
                        loadUrl("file:///android_asset/author.html")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Developed with ꨄ by Anshu",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "I built this app as an offline reader/watcher for manhua, manga, books and videos. I'd love to know how I can improve this app for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+p0nu4Zsk4ZQ3M2Rl"))
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF222631),
                contentColor = Color(0xFFE6E8EC)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_telegram),
                contentDescription = null,
                tint = Color(0xFFE6E8EC),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Join our Telegram Community", fontWeight = FontWeight.Bold, color = Color(0xFFE6E8EC))
        }
    }
}

// ---------------------------------------------------------
// UTILS & REUSABLE COMPONENTS
// ---------------------------------------------------------

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PremiumToggleRow(label: String, modifier: Modifier = Modifier, toggle: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            toggle()
        }
    }
}

@Composable
fun PremiumTextField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.5f)) },
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
        )
    )
}

fun getGradientForTheme(themeId: Int): Brush {
    return AVAILABLE_APP_THEMES.find { it.id == themeId }?.previewBrush
        ?: androidx.compose.ui.graphics.SolidColor(Color(0xFF000000))
}
