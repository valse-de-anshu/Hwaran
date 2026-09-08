package com.ballade.hwaran.ui.screens

import com.ballade.hwaran.R
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.ballade.hwaran.core.database.AppDatabase
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import com.ballade.hwaran.ui.components.JellyToggle
import com.ballade.hwaran.ui.components.JellyToggle3
import com.ballade.hwaran.ui.components.MediaModeIndicator
import com.ballade.hwaran.ui.dialogs.PremiumGlassPanel
import com.ballade.hwaran.ui.dialogs.PremiumSlider
import com.ballade.hwaran.ui.dialogs.SidebarIcon
import com.ballade.hwaran.ui.components.JellyBall
import com.ballade.hwaran.ui.dialogs.spotlightTarget
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ballade.hwaran.ui.theme.*

enum class PremiumSection {
    CONFIG, APPEARANCE, ADD_BUTTON, ENVIRONMENT, SECURITY, ABOUT
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToLockSelection: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    var activeSection by remember { mutableStateOf<PremiumSection?>(null) }

    val onboardingStep by settingsViewModel.onboardingStep.collectAsState()
    LaunchedEffect(onboardingStep) {
        if (onboardingStep == "step_transition") {
            delay(500)
            settingsViewModel.setOnboardingStep("step_media")
        }
        when (onboardingStep) {
            "step_settings", "step_media", "step_storage", "step_music" -> {
                activeSection = PremiumSection.CONFIG
            }
        }
    }
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
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
                
                // --- THE PREMIUM NAVIGATION RAIL ---
                AnimatedVisibility(
                    visible = isRailVisible,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
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
                            SidebarIcon(symbol = "´ཀ`", isSelected = activeSection == PremiumSection.CONFIG) { activeSection = if(activeSection == PremiumSection.CONFIG) null else PremiumSection.CONFIG }
                            SidebarIcon(symbol = "ִֶָ࣪☾", isSelected = activeSection == PremiumSection.APPEARANCE) { activeSection = if(activeSection == PremiumSection.APPEARANCE) null else PremiumSection.APPEARANCE }
                            SidebarIcon(symbol = "𓏲ּ𝄢", isSelected = activeSection == PremiumSection.ENVIRONMENT) { activeSection = if(activeSection == PremiumSection.ENVIRONMENT) null else PremiumSection.ENVIRONMENT }
                            SidebarIcon(symbol = "ᯓ⚽︎", isSelected = activeSection == PremiumSection.ADD_BUTTON) { activeSection = if(activeSection == PremiumSection.ADD_BUTTON) null else PremiumSection.ADD_BUTTON }
                            SidebarIcon(symbol = "ꗃꄗ", isSelected = activeSection == PremiumSection.SECURITY) { activeSection = if(activeSection == PremiumSection.SECURITY) null else PremiumSection.SECURITY }
                            SidebarIcon(symbol = "ᝰ.", isSelected = activeSection == PremiumSection.ABOUT) { activeSection = if(activeSection == PremiumSection.ABOUT) null else PremiumSection.ABOUT }
                        }
                    }
                }

                // --- THE EXPANDING CONTENT PANEL ---
                val isCompactSection = activeSection == PremiumSection.ADD_BUTTON || activeSection == PremiumSection.SECURITY
                AnimatedVisibility(
                    visible = activeSection != null,
                    enter = fadeIn(tween(250)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
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
                                modifier = colModifier.then(
                                    if (onboardingStep == "step_settings") {
                                        Modifier.spotlightTarget("tour_settings_container", settingsViewModel)
                                    } else Modifier
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (section) {
                                    PremiumSection.CONFIG -> ConfigContent(settingsViewModel)
                                    PremiumSection.APPEARANCE -> AppearanceContent(settingsViewModel)
                                    PremiumSection.ADD_BUTTON -> AddButtonContent(settingsViewModel)
                                    PremiumSection.ENVIRONMENT -> EnvironmentContent(settingsViewModel)
                                    PremiumSection.SECURITY -> SecurityContent(settingsViewModel, onNavigateToLockSelection, onNavigateToHistory)
                                    PremiumSection.ABOUT -> AboutContent(scrollState, settingsViewModel, onNavigateBack)
                                    null -> {} // Handled by visibility
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
// SECTION CONTENTS (Densely Packed & Premium)
// ---------------------------------------------------------

// ---------------------------------------------------------

@Composable
fun ConfigContent(vm: SettingsViewModel) {
    val mediaMode by vm.mediaMode.collectAsState()
    val storageMode by vm.storageMode.collectAsState()
    val videoLayoutMode by vm.videoLayoutMode.collectAsState()
    val glowBrightness by vm.glowBrightness.collectAsState()
    val glowRadius by vm.glowRadius.collectAsState()
    val glowColorLong by vm.glowColor.collectAsState()
    val sfwTextFlow by vm.sfwText.collectAsState()
    val nsfwTextFlow by vm.nsfwText.collectAsState()
    val musicMode by vm.musicMode.collectAsState()
    val onboardingStep by vm.onboardingStep.collectAsState()
    val batterySavingMode by vm.batterySavingMode.collectAsState()

    var showConfigHelp by remember { mutableStateOf(false) }

    SectionHeader("Configuration", "Core app behavior", onHelpClick = { showConfigHelp = true })
    
    if (showConfigHelp) {
        MediaConfigHelpDialog(
            glowBrightness = glowBrightness,
            glowRadius = glowRadius,
            glowColorLong = glowColorLong,
            onDismiss = { showConfigHelp = false }
        )
    }
    
    PremiumToggleRow(
        "Media Mode",
        modifier = if (onboardingStep == "step_media") Modifier.spotlightTarget("tour_media_mode", vm) else Modifier
    ) {
        JellyToggle3(
            options = listOf("Toon", "Book", "Video"),
            selectedIndex = mediaMode,
            onToggle = { vm.setMediaMode(it) },
            glowBrightness = glowBrightness, glowRadius = glowRadius, showGlow = false, glowColorOverride = Color(glowColorLong)
        )
    }

    if (mediaMode == 2) {
        PremiumToggleRow("Shelf") {
            JellyToggle(
                option1 = "Series", option2 = "Channel",
                isOption2 = videoLayoutMode == 1,
                onToggle = { vm.setVideoLayoutMode(if (it) 1 else 0) },
                glowBrightness = glowBrightness, glowRadius = glowRadius, showGlow = false, glowColorOverride = Color(glowColorLong)
            )
        }
    }

    PremiumToggleRow(
        "Storage Mode",
        modifier = if (onboardingStep == "step_storage") Modifier.spotlightTarget("tour_storage_mode", vm) else Modifier
    ) {
        JellyToggle(
            option1 = "Local", option2 = "External",
            isOption2 = storageMode == 1,
            onToggle = { vm.setStorageMode(if (it) 1 else 0) },
            glowBrightness = glowBrightness, glowRadius = glowRadius, showGlow = false, glowColorOverride = Color(glowColorLong)
        )
    }
    
    if (onboardingStep == null) {
        Spacer(modifier = Modifier.height(16.dp))
        
        var showLabelsHelp by remember { mutableStateOf(false) }
        SectionHeader("Content Labels", "Custom category names", onHelpClick = { showLabelsHelp = true })
        
        if (showLabelsHelp) {
            HelpDialog(
                title = "Content Labeling",
                onDismiss = { showLabelsHelp = false }
            ) {
                HelpContentItem(
                    title = "Organize Your Library",
                    description = "Use the dual-category system on the Home screen to divide your library into two separate sections.\n\n" +
                        "Ideal for:\n\n" +
                        "* Manga and Manhua\n" +
                        "* SFW and NSFW content\n" +
                        "* Personal and shared collections\n" +
                        "* Any two custom content groups"
                )
                HelpContentItem(
                    title = "Customizable Categories",
                    description = "Rename each label to match your preferred organization style.\n\nAny changes made here will update instantly on the Home screen and description screen."
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Tip", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("Organizing content into clear categories keeps your library cleaner and allows faster switching between collections with a single swipe.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PremiumTextField("X Label", sfwTextFlow, { vm.setSfwText(it) }, Modifier.weight(1f))
            PremiumTextField("Y Label", nsfwTextFlow, { vm.setNsfwText(it) }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Battery Saving Mode ────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (batterySavingMode)
                Color(0xFF1A2A1A)
            else
                Color.White.copy(alpha = 0.04f),
            border = BorderStroke(
                1.dp,
                if (batterySavingMode) Color(0xFF4CAF50).copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.08f)
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
}

@Composable
fun AppearanceContent(vm: SettingsViewModel) {
    val appTheme by vm.appTheme.collectAsState()
    val glowColorLong by vm.glowColor.collectAsState()

    var showAppearanceHelp by remember { mutableStateOf(false) }
    SectionHeader("Appearance", "Colors and themes", onHelpClick = { showAppearanceHelp = true })
    
    if (showAppearanceHelp) {
        HelpDialog(
            title = "Appearance Settings",
            onDismiss = { showAppearanceHelp = false }
        ) {
            HelpContentItem(
                title = "App Theme",
                description = "Choose the overall color scheme of the application. Each theme has its own unique palette and background style.",
            )
            HelpContentItem(
                title = "Pill Accent Colour",
                description = "Select the color of the animated highlighters and interactive elements. This color will be used for the 'glow' effects.",
            )
        }
    }
    
    Text("App Theme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    val themes = listOf(1 to "Dark", 5 to "Blueberry", 6 to "Snowfall", 7 to "Grape")

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
                            if (isSelected) 2.dp else 1.dp, 
                            if (isSelected) Color.White else Color.White.copy(alpha=0.2f), 
                            CircleShape
                        )
                        .clickable { vm.setAppTheme(themeId) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    themeName, 
                    color = if (isSelected) Color.White else Color.Gray, 
                    fontSize = 10.sp, 
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text("Pill Accent Colour", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

    val glowGradients = listOf(
        "Dark" to listOf(0xFFC3A6FE, 0xFF383852, 0xFF161622), // Tokyo / Dark
        "Blueberry" to listOf(0xFF5C9FD9, 0xFF255DAC, 0xFF15326D, 0xFF111523), // Blueberry
        "Snowfall" to listOf(0xFFBDC6CD, 0xFF6A757E, 0xFF404C55, 0xFF111A22), // Snowfall
        "Grape" to listOf(0xFF7A6284, 0xFF52425C, 0xFF382B3F, 0xFF1F1823, 0xFF0C080D)  // Grape
    )

    val sfwText by vm.sfwText.collectAsState()
    val nsfwText by vm.nsfwText.collectAsState()
    val glowBrightness by vm.glowBrightness.collectAsState()
    val glowRadius by vm.glowRadius.collectAsState()

    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 4
    ) {
        glowGradients.forEach { (name, colors) ->
            val repColor = colors[0]
            val isSelected = glowColorLong == repColor

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(colors.map { Color(it) }))
                        .border(
                            if (isSelected) 2.dp else 1.dp, 
                            if (isSelected) Color.White else Color.White.copy(alpha=0.2f), 
                            CircleShape
                        )
                        .clickable { vm.setGlowColor(repColor) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    name, 
                    color = if (isSelected) Color.White else Color.Gray, 
                    fontSize = 10.sp, 
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(top = 4.dp)
        ) {
            Text("Preview", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            JellyToggle(
                option1 = sfwText,
                option2 = nsfwText,
                isOption2 = false,
                onToggle = {},
                showGlow = true,
                glowBrightness = glowBrightness,
                glowRadius = glowRadius,
                glowColorOverride = Color(glowColorLong),
                toggleWidth = 140.dp,
                toggleHeight = 32.dp
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    val usePillAsHighlight by vm.usePillAsHighlight.collectAsState()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text("Use Pill Accent as Highlighter", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Overrides the app's default primary color with your selected pill color.", color = Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = usePillAsHighlight, 
            onCheckedChange = { vm.setUsePillAsHighlight(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Pill Accent Physics — moved here because it directly controls glow of the preview pill above
    var showPillHelp by remember { mutableStateOf(false) }
    SectionHeader("Pill Accent Physics", "Fine-tune the glow", onHelpClick = { showPillHelp = true })
    if (showPillHelp) {
        HelpDialog(title = "Pill Accent Physics", onDismiss = { showPillHelp = false }) {
            HelpContentItem(title = "Glow Brightness", description = "Controls how bright/intense the glow effect is around the pill toggle.")
            HelpContentItem(title = "Glow Radius", description = "Controls how far the glow spreads outward from the pill toggle.")
        }
    }
    PremiumSlider("Glow Brightness", glowBrightness, { vm.setGlowBrightness(it) })
    PremiumSlider("Glow Radius", glowRadius / 100f, { vm.setGlowRadius(it * 100f) }, formatValue = { "${(it * 100).toInt()}px" })

}

@Composable
fun AddButtonContent(vm: SettingsViewModel) {
    val fabStyle by vm.fabStyle.collectAsState()
    
    SectionHeader("Button Style", "Choose your action trigger")
    
    val styles = listOf(0 to "Icon", 1 to "JellyBall")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        styles.forEach { (id, name) ->
            val isSelected = fabStyle == id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                    .clickable { vm.setFabStyle(id) }
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (id == 0) {
                        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    } else {
                        JellyBall(modifier = Modifier.size(38.dp), enableJump = false, isTrapped = true)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(name, color = if (isSelected) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EnvironmentContent(vm: SettingsViewModel) {
    val animationVisibility by vm.animationVisibility.collectAsState()
    val animationType by vm.animationType.collectAsState()
    val animationSpeed by vm.animationSpeed.collectAsState()
    val glowColorLong by vm.glowColor.collectAsState()

    SectionHeader("Environment", "Background atmosphere")
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Show Background Animation", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Switch(
            checked = animationVisibility, 
            onCheckedChange = { vm.setAnimationVisibility(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }

    if (animationVisibility) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Background Type", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 3
        ) {
            EnvironmentThumbnailCard(
                name = "Stars", 
                isSelected = animationType == 0, 
                onClick = { vm.setAnimationType(0) },
                gradient = Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A)))
            )
            EnvironmentThumbnailCard(
                name = "Jellyfish", 
                isSelected = animationType == 1, 
                onClick = { vm.setAnimationType(1) },
                gradient = Brush.verticalGradient(listOf(Color(0xFF6B4FA9), Color(0xFFC3A6FE)))
            )
            EnvironmentThumbnailCard(
                name = "Day", 
                isSelected = animationType == 2, 
                onClick = { vm.setAnimationType(2) },
                gradient = Brush.verticalGradient(listOf(Color(0xFF312E81), Color(0xFFF59E0B)))
            )
            EnvironmentThumbnailCard(
                name = "Poker", 
                isSelected = animationType == 3, 
                onClick = { vm.setAnimationType(3) },
                gradient = Brush.verticalGradient(listOf(Color(0xFF8B0018), Color(0xFF151518)))
            )
            EnvironmentThumbnailCard(
                name = "Kaleidoscopio", 
                isSelected = animationType == 4, 
                onClick = { vm.setAnimationType(4) },
                gradient = Brush.verticalGradient(listOf(Color(0xFF2E1065), Color(0xFF020617)))
            )
            EnvironmentThumbnailCard(
                name = "Flower", 
                isSelected = animationType == 5, 
                onClick = { vm.setAnimationType(5) },
                gradient = Brush.verticalGradient(listOf(Color(0xFF0B0B1A), Color(0xFF160B24)))
            )
            EnvironmentThumbnailCard(
                name = "Liquid", 
                isSelected = animationType == 6, 
                onClick = { vm.setAnimationType(6) },
                gradient = Brush.verticalGradient(listOf(Color(0xFF0EA5E9), Color(0xFFD946EF)))
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        PremiumSlider("Animation Speed", animationSpeed, { vm.setAnimationSpeed(it) }, valueRange = 0.1f..3.0f, formatValue = { String.format("%.1fx", it) })
    }
}

@Composable
private fun ThumbnailContent(name: String) {
    val imageRes = when (name) {
        "Stars" -> R.drawable.bg_thumb_stars
        "Jellyfish" -> R.drawable.bg_thumb_jellyfish
        "Day" -> R.drawable.bg_thumb_day
        "Poker" -> R.drawable.bg_thumb_poker
        "Kaleidoscopio" -> R.drawable.bg_thumb_kaleidoscopio
        "Flower" -> R.drawable.bg_thumb_flower
        "Liquid" -> R.drawable.bg_thumb_liquid
        else -> R.drawable.bg_thumb_stars
    }

    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = imageRes),
        contentDescription = "$name Thumbnail",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun EnvironmentThumbnailCard(name: String, isSelected: Boolean, onClick: () -> Unit, gradient: Brush) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isSelected) 1.05f else 1f, label = "scale")
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val cardWidth = (screenWidth - 80.dp) / 3 // Dynamic width for 3 cards per line with padding

    Column(
        modifier = Modifier.width(cardWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Fixed ratio to fit perfectly without gaps
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(gradient)
                .border(
                    width = 2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick() 
                },
            contentAlignment = Alignment.Center
        ) {
            ThumbnailContent(name)
            
            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
fun SecurityContent(
    vm: SettingsViewModel,
    onNavigateToLockSelection: () -> Unit,
    onNavigateToHistory: () -> Unit
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
    var showWipeDetailHelp by remember { mutableStateOf(false) }

    if (showWipeConfirmation) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmation = false },
            title = { Text("Wipe History", color = Color.White) },
            text = { 
                Text(
                    text = "do u really want to completly wipe out the histry data of urs ??", 
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

    if (showWipeDetailHelp) {
        HelpDialog(
            title = "History Tracking Details",
            onDismiss = { showWipeDetailHelp = false }
        ) {
            HelpContentItem(
                title = "What is tracked?",
                description = "To help you navigate and explore your library better, Hwaran tracks key activity logs locally on your device:\n\n" +
                    "• App session launch and exit events (timestamp, date, year).\n" +
                    "• Folder imports, file pathways, and batch imports.\n" +
                    "• Content deletions (playlists, workspaces, or songs).\n" +
                    "• Accent theme choices and switch frequency.\n" +
                    "• Password configuration and change times.\n" +
                    "• Playback metrics (most watched videos, most played songs, and unplayed items)."
            )
            HelpContentItem(
                title = "Privacy First",
                description = "All history data is stored 100% offline inside the secure database on your device. Nothing is sent to external servers."
            )
        }
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

    var showLockHelp by remember { mutableStateOf(false) }
    SectionHeader("Security", "Vault protection", onHelpClick = { showLockHelp = true })

    if (showLockHelp) {
        HelpDialog(
            title = "Library Security",
            onDismiss = { showLockHelp = false }
        ) {
            HelpContentItem(
                title = "Protect Your Privacy",
                description = "Secure your most private library items with a dedicated password. Once locked, content is obscured from the home screen."
            )
            HelpContentItem(
                title = "How to Lock Items",
                description = "1. Click the Locker icon to open the selection menu.\n2. Tap items to select them (a blue checkmark will appear).\n3. Click the Check mark at the top right to save.\nIf it's your first time, you will be prompted to create a password."
            )
            HelpContentItem(
                title = "Toggling the Lock",
                description = "Use the Locked/Unlocked switch to globally enforce or remove the security overlay on your selected items."
            )
            HelpContentItem(
                title = "Resetting Passwords",
                description = "Use the Refresh icon button to reset your password. You will need to enter your current password to set a new one."
            )
        }
    }

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
    SectionHeader("History Tracking", "Activity & playback stats", onHelpClick = { showWipeDetailHelp = true })

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

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // View History Button
        Button(
            onClick = onNavigateToHistory,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = "View History",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View History",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Delete Button
        Button(
            onClick = { showWipeConfirmation = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.1f),
                contentColor = Color(0xFFE57373)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
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
                    text = "Wipe History",
                    color = Color(0xFFE57373),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AboutContent(scrollState: ScrollState, vm: SettingsViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    // Use a key that increments each time the section is opened so the animation
    // always restarts from scratch — fixes the bug where it didn't play on first open.
    var hasBeenTriggered by remember { mutableStateOf(false) }

    // Always trigger shortly after the section opens
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
                        // Fire the animation JS call inside onPageFinished so it
                        // always executes after the page is ready — fixes the
                        // "animation doesn't start on first open" bug.
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
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_telegram),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Join our Telegram Community", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SectionHeader("Help & Guided Tour", "Learn how to use Hwaran")
        
        Text(
            text = "Need help finding your way around? You can start the interactive guided tour at any time to learn how to import and manage your media library.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            lineHeight = 20.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                onNavigateBack()
                vm.startTour()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("✦", fontSize = 24.sp, color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Guided Tour", fontWeight = FontWeight.Bold, color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        var showMediaConfigHelp by remember { mutableStateOf(false) }
        
        Button(
            onClick = { showMediaConfigHelp = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Article,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Documentation", fontWeight = FontWeight.Bold, color = Color.White)
        }

        if (showMediaConfigHelp) {
            val glowBrightness by vm.glowBrightness.collectAsState()
            val glowRadius by vm.glowRadius.collectAsState()
            val glowColorLong by vm.glowColor.collectAsState()
            
            MediaConfigHelpDialog(
                glowBrightness = glowBrightness,
                glowRadius = glowRadius,
                glowColorLong = glowColorLong,
                onDismiss = { showMediaConfigHelp = false }
            )
        }
    }
}

// ---------------------------------------------------------
// UTILS
// ---------------------------------------------------------

@Composable
fun MediaConfigHelpDialog(
    glowBrightness: Float,
    glowRadius: Float,
    glowColorLong: Long,
    onDismiss: () -> Unit
) {
    var activeHelpTab by remember { mutableStateOf(0) } // 0: General, 1: File Format
    
    HelpDialog(
        title = "Media Configuration",
        onDismiss = onDismiss
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            JellyToggle(
                option1 = "General",
                option2 = "File Format",
                isOption2 = activeHelpTab == 1,
                onToggle = { activeHelpTab = if (it) 1 else 0 },
                glowBrightness = glowBrightness,
                glowRadius = glowRadius,
                showGlow = false,
                glowColorOverride = Color(glowColorLong),
                toggleWidth = 240.dp,
                toggleHeight = 44.dp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (activeHelpTab == 0) {
            // General Tab
            var expandedHelp by remember { mutableStateOf(-1) }

            @Composable
            fun ExpandableHelpBlock(index: Int, title: String, summary: String, details: String) {
                val isExpanded = expandedHelp == index
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isExpanded) 0.5f else 0.2f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { expandedHelp = if (isExpanded) -1 else index }
                ) {
                    Column(modifier = Modifier.padding(12.dp).animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                if (!isExpanded) {
                                    Text(summary, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState())) {
                ExpandableHelpBlock(
                    0, "Media Modes", "Toon, Book, and Video modes",
                    "• Toon — Ideal for Manhua, Manga, Comics, and image folders with smooth vertical scrolling.\n\n" +
                    "• Book — Built for reading PDF files and digital books with classic paging. Great for text-heavy novels.\n\n" +
                    "• Video — Optimized for MP4 and MKV video playback, featuring gesture controls for brightness, volume, and seeking.\n\n" +
                    "• Music — A dedicated audio player for local music, offering full background playback and notification controls."
                )

                ExpandableHelpBlock(
                    1, "Shelf Organization", "How your video library is organized",
                    "When using Video Mode, you can customize your library's structure:\n\n" +
                    "• Series — Best for Anime, TV shows, and episodic content. Content is automatically grouped into seasons and episodes based on your folder structure. The app intelligently maps subfolders to seasons, and files within to episodes, providing a clean, Netflix-style browsing experience.\n\n" +
                    "• Channel — Best for YouTube videos, creator archives, or tutorials. Each folder appears as its own channel, and all videos inside that folder are treated as individual uploads from that creator."
                )
                
                ExpandableHelpBlock(
                    2, "Storage Mode", "Local Vault vs External Folders",
                    "• Local (Recommended) — Files are safely copied into the app's secure private vault. This completely hides your files from your phone's default Gallery, keeping things clean and private. Original files are deleted after import.\n\n" +
                    "• External — Files remain in their original folders on your device. The app simply reads from those directories. This allows for faster importing without taking up extra duplicate storage space, but the files will still appear in your Gallery app."
                )
                
                ExpandableHelpBlock(
                    3, "Battery Saving Mode", "Optimize background visuals",
                    "This option is designed for older hardware, low power devices, or those who simply value battery life over visual performance. Activating Battery Saving Mode instantly pauses all dynamic background processes, glowing effects, and ambient visuals to drastically extend your battery life and reduce thermal load."
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Toon Mode", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            MediaModeIndicator(mediaMode = 0)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Series Mode", style = MaterialTheme.typography.labelMedium, color = Color(0xFF66BB6A))
                            Spacer(modifier = Modifier.height(4.dp))
                            MediaModeIndicator(mediaMode = 2, videoLayoutMode = 0)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Book Mode", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            MediaModeIndicator(mediaMode = 1)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Channel Mode", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFA726))
                            Spacer(modifier = Modifier.height(4.dp))
                            MediaModeIndicator(mediaMode = 2, videoLayoutMode = 1)
                        }
                    }
                }
            }
        } else {
            // File Format Tab
            Text("Folder Structures & File Types", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            var expandedFormat by remember { mutableStateOf(-1) }
            
            @Composable
            fun GlassCodeBlock(index: Int, title: String, types: String, code: String) {
                val isExpanded = expandedFormat == index
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isExpanded) 0.5f else 0.2f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { expandedFormat = if (isExpanded) -1 else index }
                ) {
                    Column(modifier = Modifier.padding(12.dp).animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            if (!isExpanded) {
                                Text(types, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = code,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState())) {
                GlassCodeBlock(
                    0, "Toon (Manga / Manhua)", "jpg, jpeg, png, webp, gif",
                    "• Structure A — Batch Import\n" +
                    "Manhua/\n" +
                    "├── Reverend Insanity/\n" +
                    "│   ├── ch1/\n" +
                    "│   ├── ch2/\n" +
                    "│   └── cover.jpg\n" +
                    "├── the beginning after the end/\n" +
                    "│   ├── ch1/\n" +
                    "│   ├── ch2/\n" +
                    "│   └── cover.jpg\n\n" +
                    "• Structure B — Single Import\n" +
                    "Reverend Insanity/\n" +
                    "├── ch1/\n" +
                    "├── ch2/\n" +
                    "├── ch3/\n" +
                    "└── cover.jpg\n\n" +
                    "Folder title becomes the library title."
                )
                GlassCodeBlock(
                    1, "Books", "pdf",
                    "• Structure A — Batch Import\n" +
                    "Books/\n" +
                    "├── Business/\n" +
                    "│   ├── Atomic Habits.pdf\n" +
                    "│   └── Deep Work.pdf\n" +
                    "├── Psychology/\n" +
                    "│   ├── Mindset.pdf\n" +
                    "│   └── Thinking Fast.pdf\n\n" +
                    "• Structure B — Direct PDF Folder\n" +
                    "Business/\n" +
                    "├── Atomic Habits.pdf\n" +
                    "├── Deep Work.pdf\n\n" +
                    "Folder names are ignored in Structure B.\n" +
                    "PDF filename becomes the title."
                )
                GlassCodeBlock(
                    2, "Video", "mp4, mkv, webm, mov",
                    "• Structure A — Batch Import\n" +
                    "Anime/\n" +
                    "├── Solo Leveling/\n" +
                    "├── Jujutsu Kaisen/\n" +
                    "├── Bleach/\n\n" +
                    "• Structure B — Single Import\n" +
                    "Solo Leveling/\n" +
                    "├── Episode 1.mp4\n" +
                    "├── Episode 2.mkv\n" +
                    "├── preview.png\n" +
                    "└── cover.jpg"
                )
                GlassCodeBlock(
                    3, "Music", "mp3, flac, wav, ogg, m4a, opus, aac",
                    "• Structure A — Batch Import\n" +
                    "Music/\n" +
                    "├── Ambient/\n" +
                    "│   ├── Rain.flac\n" +
                    "│   └── Sleep.mp3\n" +
                    "├── Cyberpunk OST/\n" +
                    "│   ├── Major Crimes.mp3\n" +
                    "│   └── Outsider No More.flac\n\n" +
                    "• Structure B — Single Import\n" +
                    "Cyberpunk OST/\n" +
                    "├── track1.flac\n" +
                    "├── track2.mp3\n" +
                    "└── cover.jpg\n\n" +
                    "Folder title becomes the album title.\n" +
                    "Songs appear inside that album."
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ---------------------------------------------------------
// UTILS
// ---------------------------------------------------------

@Composable
fun SectionHeader(title: String, subtitle: String, onHelpClick: (() -> Unit)? = null) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    color = MaterialTheme.colorScheme.primary, 
                    fontSize = 16.sp, 
                    fontWeight = FontWeight.Black, 
                    letterSpacing = 1.sp
                )
                Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            if (onHelpClick != null) {
                IconButton(onClick = onHelpClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun HelpDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF1A1A1A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.5f))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 16.dp, bottom = 16.dp), // keep text below shadow
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        content()
                    }

                    // Top fade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1A1A1A), Color.Transparent)
                                )
                            )
                    )

                    // Bottom fade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF1A1A1A))
                                )
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HelpContentItem(
    title: String,
    description: String,
    example: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            lineHeight = 20.sp
        )
        if (example != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
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
        label = { Text(label, color = Color.White.copy(alpha=0.5f)) },
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
        )
    )
}

fun getGradientForTheme(themeId: Int): Brush {
    return when (themeId) {
        5 -> Brush.verticalGradient(listOf(Color(0xFF15326D), Color(0xFF0C1D40), Color(0xFF071126), Color(0xFF030812)))
        6 -> Brush.verticalGradient(listOf(Color(0xFF404C55), Color(0xFF27323B), Color(0xFF161C22), Color(0xFF0B0E11)))
        7 -> Brush.verticalGradient(listOf(Color(0xFF7A6284), Color(0xFF52425C), Color(0xFF382B3F), Color(0xFF1F1823), Color(0xFF0C080D)))
        1 -> androidx.compose.ui.graphics.SolidColor(Color(0xFF000000))
        else -> androidx.compose.ui.graphics.SolidColor(Color(0xFF000000))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPreviewDialog(
    videoNameOrPath: String,
    isCustom: Boolean,
    onDismiss: () -> Unit,
    onSetAsSplash: (String) -> Unit
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    
    DisposableEffect(view) {
        val window = (view.parent as? android.view.Window) ?: (context as? android.app.Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {}
    }

    val isWebView = videoNameOrPath == "3d_intro.html"

    val exoPlayer = remember {
        if (!isWebView) {
            ExoPlayer.Builder(context).build().apply {
                val mediaUri = if (isCustom) {
                    Uri.fromFile(File(context.filesDir, "custom_splash/$videoNameOrPath"))
                } else {
                    Uri.parse("android.resource://${context.packageName}/raw/$videoNameOrPath")
                }
                setMediaItem(MediaItem.fromUri(mediaUri))
                volume = 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
            }
        } else null
    }

    LaunchedEffect(exoPlayer) { exoPlayer?.volume = 0f }
    DisposableEffect(Unit) { onDispose { exoPlayer?.release() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (isWebView) {
                AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadUrl("file:///android_asset/3d_intro.html")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding().background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
            Button(
                onClick = { onSetAsSplash(videoNameOrPath) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).navigationBarsPadding().fillMaxWidth(0.7f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Set as Splash", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}



