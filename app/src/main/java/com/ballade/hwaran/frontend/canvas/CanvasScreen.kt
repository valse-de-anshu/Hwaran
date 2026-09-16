package com.ballade.hwaran.frontend.canvas

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.R
import com.ballade.hwaran.ui.background.*
import com.ballade.hwaran.ui.theme.AVAILABLE_APP_THEMES
import com.ballade.hwaran.ui.theme.LocalAppGradient
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class CanvasPanel {
    APP_THEME,
    ATMOSPHERE,
    SPEED,
    MUSIC
}

private data class AnimationItem(
    val id: Int,
    val name: String,
    val description: String,
    val thumbRes: Int
)

private val ANIMATIONS = listOf(
    AnimationItem(0,  "Stars",          "Cosmic drifting starlight",               R.drawable.bg_thumb_stars),
    AnimationItem(1,  "Jellyfish",      "Deep ocean bioluminescence",              R.drawable.bg_thumb_jellyfish),
    AnimationItem(4,  "Kaleidoscope",   "Hypnotic geometric blooms",               R.drawable.bg_thumb_kaleidoscopio),
    AnimationItem(5,  "Flower",         "Harmonic floral petals",                  R.drawable.bg_thumb_flower),
    AnimationItem(6,  "Liquid Fluid",   "Interactive touch particle fluid",         R.drawable.bg_thumb_liquid),
    AnimationItem(8,  "Constellation",  "Drifting stars linked by stardust lines", R.drawable.bg_thumb_constellation),
    AnimationItem(9,  "Neon Ripple",    "Expanding sapphire rings from the void",  R.drawable.bg_thumb_ripple),
    AnimationItem(10, "Crystal Snow",   "Geometric ice crystals floating down",    R.drawable.bg_thumb_crystalsnow),
)

@Composable
fun CanvasScreen(
    settingsViewModel: SettingsViewModel,
    musicViewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val animationType by settingsViewModel.animationType.collectAsState()
    val animationSpeed by settingsViewModel.animationSpeed.collectAsState()
    val appTheme by settingsViewModel.appTheme.collectAsState()
    val appGradient = LocalAppGradient.current

    val currentTrack by musicViewModel.currentChapter.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()

    var uiVisible by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<CanvasPanel?>(null) }

    // Auto-scroll loop for scrolling animations (Kaleidoscope, Flower)
    var autoScrollOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationType, animationSpeed) {
        if (animationType in listOf(4, 5)) {
            while (isActive) {
                autoScrollOffset += 0.8f * animationSpeed
                delay(16)
            }
        }
    }

    // Pointer state for Liquid interactive fluid
    val pointerState = remember { LiquidPointerState() }

    // Shared interaction state for Constellation / Ripple / Crystal Snow
    val canvasInteractionState = remember { CanvasInteractionState() }

    // Ambient background gradient corresponding to the active theme or animation
    val baseGradient = remember(animationType, appGradient) {
        if (appGradient != null) {
            appGradient
        } else {
            when (animationType) {
                0  -> Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A)))
                1  -> Brush.verticalGradient(listOf(Color(0xFF0C071E), Color(0xFF1D1442)))
                4  -> Brush.verticalGradient(listOf(Color(0xFF2E1065), Color(0xFF020617)))
                5  -> Brush.verticalGradient(listOf(Color(0xFF0B0B1A), Color(0xFF160B24)))
                6  -> Brush.verticalGradient(listOf(Color(0xFF050510), Color(0xFF0A1020)))
                8  -> Brush.verticalGradient(listOf(Color(0xFF04060F), Color(0xFF07090F))) // Constellation
                9  -> Brush.verticalGradient(listOf(Color(0xFF030509), Color(0xFF05080E))) // Neon Ripple
                10 -> Brush.verticalGradient(listOf(Color(0xFF04060C), Color(0xFF070A12))) // Crystal Snow
                else -> Brush.verticalGradient(listOf(Color(0xFF0A0A0E), Color(0xFF000000)))
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- 1. FULLSCREEN CANVAS LAYER ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseGradient)
                .pointerInput(animationType) {
                    when {
                        animationType == 6 -> {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        pointerState.x = change.position.x
                                        pointerState.y = change.position.y
                                        if (change.changedToDown()) {
                                            pointerState.triggerExplosion()
                                            pointerState.isPressed = true
                                        } else if (change.position != change.previousPosition) {
                                            pointerState.isPressed = false
                                        }
                                        if (!event.changes.any { it.pressed }) {
                                            pointerState.isPressed = false
                                        }
                                    }
                                }
                            }
                        }
                        animationType in 8..10 -> {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        if (change.changedToDown()) {
                                            canvasInteractionState.onTapDown(change.position.x, change.position.y)
                                        } else if (change.pressed) {
                                            canvasInteractionState.onMove(change.position.x, change.position.y)
                                        }
                                        if (!event.changes.any { it.pressed }) {
                                            canvasInteractionState.onRelease()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val starsAlpha        by animateFloatAsState(targetValue = if (animationType == 0)  1f else 0f, animationSpec = tween(800), label = "stars")
            val jellyfishAlpha    by animateFloatAsState(targetValue = if (animationType == 1)  1f else 0f, animationSpec = tween(800), label = "jellyfish")
            val kaleidoscopeAlpha by animateFloatAsState(targetValue = if (animationType == 4)  1f else 0f, animationSpec = tween(800), label = "kaleidoscope")
            val flowerAlpha       by animateFloatAsState(targetValue = if (animationType == 5)  1f else 0f, animationSpec = tween(800), label = "flower")
            val liquidAlpha       by animateFloatAsState(targetValue = if (animationType == 6)  1f else 0f, animationSpec = tween(800), label = "liquid")
            val constellAlpha     by animateFloatAsState(targetValue = if (animationType == 8)  1f else 0f, animationSpec = tween(800), label = "constellation")
            val rippleAlpha       by animateFloatAsState(targetValue = if (animationType == 9)  1f else 0f, animationSpec = tween(800), label = "ripple")
            val crystalSnowAlpha  by animateFloatAsState(targetValue = if (animationType == 10) 1f else 0f, animationSpec = tween(800), label = "crystalSnow")

            if (starsAlpha > 0.01f) {
                DrunkStarsBackground(
                    animationSpeed = animationSpeed,
                    isEnabled = animationType == 0,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = starsAlpha }
                )
            }
            if (jellyfishAlpha > 0.01f) {
                JellyfishBackground(
                    animationSpeed = animationSpeed,
                    isEnabled = animationType == 1,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = jellyfishAlpha }
                )
            }
            if (kaleidoscopeAlpha > 0.01f) {
                KaleidoscopeBackground(
                    animationSpeed = animationSpeed,
                    isEnabled = animationType == 4,
                    scrollOffset = autoScrollOffset,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = kaleidoscopeAlpha }
                )
            }
            if (flowerAlpha > 0.01f) {
                FlowerBackground(
                    animationSpeed = animationSpeed,
                    isEnabled = animationType == 5,
                    scrollOffset = autoScrollOffset,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = flowerAlpha }
                )
            }
            if (liquidAlpha > 0.01f) {
                LiquidBackground(
                    isEnabled = animationType == 6,
                    pointerState = pointerState,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = liquidAlpha }
                )
            }
            if (constellAlpha > 0.01f) {
                ConstellationBackground(
                    animationSpeed = animationSpeed,
                    isEnabled = animationType == 8,
                    interactionState = canvasInteractionState,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = constellAlpha }
                )
            }
            if (rippleAlpha > 0.01f) {
                NeonRippleBackground(
                    animationSpeed = animationSpeed,
                    isEnabled = animationType == 9,
                    interactionState = canvasInteractionState,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = rippleAlpha }
                )
            }
            if (crystalSnowAlpha > 0.01f) {
                CrystalSnowBackground(
                    animationSpeed = animationSpeed,
                    isEnabled = animationType == 10,
                    interactionState = canvasInteractionState,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = crystalSnowAlpha }
                )
            }
        }


        // --- 2. FULLSCREEN DISMISS LAYER (Tap anywhere to hide UI) ---
        if (uiVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        uiVisible = false
                        activePanel = null
                    }
            )
        }

        // --- 3. RIGHT-SIDE TRIGGER ZONE (When UI hidden, tap right strip to show controls) ---
        if (!uiVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(100.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        uiVisible = true
                    }
            )
        }

        // --- 4. RIGHT-SIDE VERTICAL PILL & FLYOUT PANELS ---
        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn(tween(350)) + slideInHorizontally(tween(350)) { it / 2 },
            exit = fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it / 2 },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Prevent clicks inside controls from bubbling up to dismiss overlay
                }
            ) {
                // Flyout Panel (appears immediately to the left of the pill)
                AnimatedVisibility(
                    visible = activePanel != null,
                    enter = fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 3 },
                    exit = fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 3 }
                ) {
                    when (activePanel) {
                        CanvasPanel.APP_THEME -> {
                            AppThemePickerFlyout(
                                selectedThemeId = appTheme,
                                onSelectTheme = { id ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    settingsViewModel.setAppTheme(id)
                                },
                                onClose = { activePanel = null }
                            )
                        }
                        CanvasPanel.ATMOSPHERE -> {
                            AtmospherePickerFlyout(
                                selectedId = animationType,
                                onSelect = { id ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    settingsViewModel.setAnimationType(id)
                                    settingsViewModel.setAnimationVisibility(true)
                                },
                                onClose = { activePanel = null }
                            )
                        }
                        CanvasPanel.SPEED -> {
                            SpeedPickerFlyout(
                                speed = animationSpeed,
                                onSpeedChange = {
                                    settingsViewModel.setAnimationSpeed(it)
                                },
                                onClose = { activePanel = null }
                            )
                        }
                        CanvasPanel.MUSIC -> {
                            MusicPlayerFlyout(
                                musicViewModel = musicViewModel,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onClose = { activePanel = null }
                            )
                        }
                        null -> {}
                    }
                }

                // The Sleek Vertical Pill (App Theme Integrated)
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.5.dp,
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        )
                    ),
                    shadowElevation = 14.dp
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        Color(0xF2131220),
                                        Color(0xF20F0E1A)
                                    )
                                )
                            )
                            .padding(vertical = 12.dp, horizontal = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Back / Exit
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigateBack()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Exit Canvas",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Theme Accent Divider
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(1.5.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        )

                        // 2. App Theme Picker (Auto-funneled from AVAILABLE_APP_THEMES)
                        val isAppThemeActive = activePanel == CanvasPanel.APP_THEME
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activePanel = if (isAppThemeActive) null else CanvasPanel.APP_THEME
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isAppThemeActive) Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else Modifier
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = "App Theme",
                                tint = if (isAppThemeActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 3. Atmosphere / Animation Picker
                        val isAtmosphereActive = activePanel == CanvasPanel.ATMOSPHERE
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activePanel = if (isAtmosphereActive) null else CanvasPanel.ATMOSPHERE
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isAtmosphereActive) Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else Modifier
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Atmosphere Motion",
                                tint = if (isAtmosphereActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 3. Animation Speed Slider
                        val isSpeedActive = activePanel == CanvasPanel.SPEED
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activePanel = if (isSpeedActive) null else CanvasPanel.SPEED
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSpeedActive) Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else Modifier
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = "Animation Speed",
                                tint = if (isSpeedActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 4. Music Playback (If Track is present)
                        if (currentTrack != null) {
                            val isMusicActive = activePanel == CanvasPanel.MUSIC
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activePanel = if (isMusicActive) null else CanvasPanel.MUSIC
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (isMusicActive) Modifier
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = "Music Controls",
                                    tint = if (isMusicActive || isPlaying) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// FLYOUT: APP THEME SELECTION (Funneled automatically from AVAILABLE_APP_THEMES)
// -----------------------------------------------------------------------------
@Composable
private fun AppThemePickerFlyout(
    selectedThemeId: Int,
    onSelectTheme: (Int) -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .width(280.dp)
            .heightIn(max = 420.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE11111B),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "App Theme",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Active color palette",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AVAILABLE_APP_THEMES) { theme ->
                    val isSelected = theme.id == selectedThemeId
                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectTheme(theme.id)
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(theme.previewBrush)
                                    .border(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                                        CircleShape
                                    )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = theme.name,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// FLYOUT: ATMOSPHERE / ANIMATION SELECTION
// -----------------------------------------------------------------------------
@Composable
private fun AtmospherePickerFlyout(
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .heightIn(max = 420.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE11111B),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Atmosphere",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select ambient mood",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ANIMATIONS) { anim ->
                    val isSelected = anim.id == selectedId
                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(anim.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = anim.thumbRes),
                                contentDescription = anim.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = anim.name,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = anim.description,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SLEEK MINIMALIST CUSTOM SLIDER
// -----------------------------------------------------------------------------
@Composable
private fun AestheticSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0.1f..3.0f,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    if (width > 0) {
                        val fraction = (offset.x / width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueChange(newValue)
                    }
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat()
                        if (width > 0) {
                            val fraction = (change.position.x / width).coerceIn(0f, 1f)
                            val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                            onValueChange(newValue)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

        // Sleek Inactive Track (3dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Active Track (3dp with theme primary gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.primary
                        )
                    )
                )
        )

        // Aesthetic Minimalist Circular Thumb
        val thumbSize = if (isDragging) 16.dp else 13.dp
        val maxOffset = (maxWidth - thumbSize)
        val currentOffset = maxOffset * fraction

        Box(
            modifier = Modifier
                .offset(x = currentOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

// -----------------------------------------------------------------------------
// FLYOUT: ANIMATION SPEED SLIDER
// -----------------------------------------------------------------------------
@Composable
private fun SpeedPickerFlyout(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE11111B),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Motion Speed",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pace of animation",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = String.format("%.1fx", speed),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Minimalist Sleek Slider (Replaced bulky M3 Slider)
            AestheticSlider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = 0.1f..3.0f,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Preset Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { preset ->
                    val isPreset = kotlin.math.abs(speed - preset) < 0.05f
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPreset) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                        border = if (isPreset) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSpeedChange(preset)
                            }
                    ) {
                        Text(
                            text = "${preset}x",
                            color = if (isPreset) Color.Black else Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// FLYOUT: MINI MUSIC PLAYER
// -----------------------------------------------------------------------------
@Composable
private fun MusicPlayerFlyout(
    musicViewModel: MusicViewModel,
    currentTrack: com.ballade.hwaran.core.database.entity.ChapterEntity?,
    isPlaying: Boolean,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val currentPlaylist by musicViewModel.currentPlaylist.collectAsState()
    val repeatMode by musicViewModel.repeatMode.collectAsState()

    val currentIndex = remember(currentPlaylist, currentTrack) { 
        currentPlaylist.indexOfFirst { it.id == currentTrack?.id } 
    }
    val hasPrevious = remember(currentIndex, currentPlaylist, repeatMode) {
        repeatMode != 0 || (currentIndex > 0)
    }
    val hasNext = remember(currentIndex, currentPlaylist, repeatMode) {
        repeatMode != 0 || (currentIndex >= 0 && currentIndex < currentPlaylist.size - 1)
    }

    Surface(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE11111B),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Now Playing",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (currentTrack != null) {
                Text(
                    text = currentTrack.title ?: "Unknown Track",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack.artist ?: "Unknown Artist",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            musicViewModel.previous()
                        },
                        enabled = hasPrevious,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (hasPrevious) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            musicViewModel.togglePlayPause()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            musicViewModel.next()
                        },
                        enabled = hasNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = if (hasNext) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "No track currently playing",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
