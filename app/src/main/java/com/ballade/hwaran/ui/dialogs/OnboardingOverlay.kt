package com.ballade.hwaran.ui.dialogs

import com.ballade.hwaran.ui.components.JellyBall
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import kotlin.math.roundToInt

fun Modifier.spotlightTarget(key: String, settingsViewModel: SettingsViewModel): Modifier = composed {
    DisposableEffect(key) {
        onDispose {
            settingsViewModel.updateSpotlightTarget(key, null)
        }
    }
    this.onGloballyPositioned { coords ->
        if (coords.isAttached) {
            val pos = coords.positionInWindow()
            val size = coords.size
            settingsViewModel.updateSpotlightTarget(
                key,
                Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
            )
        } else {
            settingsViewModel.updateSpotlightTarget(key, null)
        }
    }
}

@Composable
fun PremiumBokehAnimation() {
    val particles = remember {
        List(15) {
            ConfettiParticle(
                x = (0..1000).random() / 1000f,
                y = (0..1400).random() / 1000f,
                color = listOf(
                    Color(0xFFE56A72),
                    Color(0xFF8EB69B),
                    Color(0xFFD6D3E5),
                    Color(0xFFC3A6FE),
                    Color(0xFF5C9FD9)
                ).random(),
                size = (20..50).random().dp
            )
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition("bokeh")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bokeh_progress"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bokeh_pulse"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEachIndexed { index, particle ->
            // Speed must be an exact multiple of 1.4f so that when progress goes 0->1, it wraps perfectly.
            val speedMultiplier = 1.4f * (1 + index % 3)
            // .mod(1.4f) ensures strict positive wrapping, -0.2f shifts the viewport to allow particles to fully exit the top
            val drawY = (particle.y - progress * speedMultiplier).mod(1.4f) - 0.2f
            
            // Math.sin needs to complete exact cycles. 2 * PI * xLoops guarantees the same value at progress=0 and progress=1
            val xLoops = 2 + index % 2
            val cx = particle.x * size.width + kotlin.math.sin(progress * xLoops * 2 * Math.PI.toFloat() + index) * 50f
            val cy = drawY * size.height
            val radius = particle.size.toPx() * (if (index % 2 == 0) pulse else 1.2f - pulse * 0.2f)
            
            drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(particle.color.copy(alpha = 0.3f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        radius = radius * 2
                    ),
                    radius = radius * 2,
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )
        }
    }
}

data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val color: Color,
    val size: androidx.compose.ui.unit.Dp
)

@Composable
fun WelcomeModal(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        PremiumGlassPanel(
            modifier = Modifier
                .width(340.dp)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                JellyBall(
                    modifier = Modifier.size(100.dp),
                    isHappy = false,
                    enableJump = false
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Welcome to Hwaran 🎈",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Want a quick guided tour? I'll show you everything in under a minute.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Yes, guide me", fontWeight = FontWeight.Bold, color = Color.White)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("No thanks", color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun FinishModal(
    onFinish: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        PremiumBokehAnimation()
        
        PremiumGlassPanel(
            modifier = Modifier
                .width(340.dp)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                JellyBall(
                    modifier = Modifier.size(100.dp),
                    isHappy = true,
                    enableJump = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "You're ready 🎵📚🎬",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Import your favorites and enjoy everything offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Start using app", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun TooltipContainer(
    currentStep: String,
    targetRect: Rect?,
    settingsViewModel: SettingsViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    onImportNow: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    
    val (title, text, hasBack, hasNext, primaryText) = when (currentStep) {
        "step_jelly" -> TooltipInfo(
            title = "Reporting for duty! 😈",
            text = "Bring me your music, books, manga, manhua and videos... and I’ll quietly turn the chaos into something beautiful.\n\nPromise I won’t peek through your folders... probably. hehe~ ✨",
            hasBack = false,
            hasNext = true,
            primaryText = "Let's Go"
        )
        "step_media" -> TooltipInfo(
            title = "Media Modes",
            text = "“Pick what you’re bringing me… and I’ll treat it the right way.”\n\n• Toon — Manga, manhua, comics… smooth scrolling, made for binge-reading.\n• Book — PDFs and digital books, comfy and distraction-free.\n• Video — Anime, movies… press play and relax.",
            hasBack = false,
            hasNext = true,
            primaryText = "Next"
        )
        "step_storage" -> TooltipInfo(
            title = "Storage Mode",
            text = "“One tiny question… where should I keep your treasures?”\n\n• Local — I tuck everything safely inside the app. Clean and private.\n• External — Files stay where they already are. Faster and flexible.",
            hasBack = true,
            hasNext = true,
            primaryText = "Next"
        )
        "step_home" -> TooltipInfo(
            title = "Dashboard 🏠",
            text = "Yep… this is home.\n\nImport anything you like—\nand I’ll line it all up here nice and pretty for you",
            hasBack = false,
            hasNext = true,
            primaryText = "Next"
        )
        "step_wrapper" -> TooltipInfo(
            title = "Music Section 🎵",
            text = "Uh… this one’s my favorite.\n\nYour songs, albums, and playlists all live here.\n\nTap the panel for quick controls—\nthen hit play and pretend I’m not vibing with you",
            hasBack = false,
            hasNext = true,
            primaryText = "Got it"
        )
        "step_popup_add" -> TooltipInfo(
            title = "Add Music ➕",
            text = "Add music or create a playlist here.",
            hasBack = false,
            hasNext = true,
            primaryText = "Next"
        )
        "step_popup_edit" -> TooltipInfo(
            title = "Edit Playlist ✏️",
            text = "Edit titles, playlists, or update details.",
            hasBack = false,
            hasNext = true,
            primaryText = "Next"
        )
        "step_popup_delete" -> TooltipInfo(
            title = "Delete Playlist 🗑️",
            text = "Remove items anytime.",
            hasBack = false,
            hasNext = true,
            primaryText = "Next"
        )
        else -> TooltipInfo("", "", false, false, "")
    }

    val forceCenter = currentStep == "step_wrapper"

    val y = if (targetRect != null && !forceCenter) {
        val spaceAbove = targetRect.top
        val spaceBelow = screenHeightPx - targetRect.bottom
        val tooltipHeight = with(density) { 240.dp.toPx() }
        val offsetPx = with(density) { if (currentStep == "step_jelly") 120.dp.toPx() else 24.dp.toPx() }
        
        val rawY = if (spaceBelow > tooltipHeight || spaceBelow > spaceAbove) {
            targetRect.bottom + offsetPx
        } else {
            targetRect.top - tooltipHeight - offsetPx
        }
        
        rawY.coerceIn(
            with(density) { 32.dp.toPx() },
            screenHeightPx - tooltipHeight - with(density) { 32.dp.toPx() }
        )
    } else {
        screenHeightPx / 2 - with(density) { 120.dp.toPx() }
    }

    val x = if (targetRect != null && !forceCenter) {
        val targetCenterX = (targetRect.left + targetRect.right) / 2
        (targetCenterX - with(density) { 160.dp.toPx() }).coerceIn(
            with(density) { 16.dp.toPx() },
            screenWidthPx - with(density) { 336.dp.toPx() }
        )
    } else {
        screenWidthPx / 2 - with(density) { 160.dp.toPx() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(320.dp)
        ) {
            val contentBlock = @Composable {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        JellyBall(
                            modifier = Modifier.size(42.dp),
                            isHappy = false,
                            enableJump = false
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 22.sp,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { settingsViewModel.endTour() }
                        ) {
                            Text("Skip", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasBack) {
                                Button(
                                    onClick = {
                                        val prevStep = when (currentStep) {
                                            "step_storage" -> "step_media"
                                            else -> null
                                        }
                                        if (prevStep != null) {
                                            settingsViewModel.setOnboardingStep(prevStep)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Back", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            
                            if (hasNext) {
                                Button(
                                    onClick = {
                                        when (currentStep) {
                                            "step_jelly" -> {
                                                settingsViewModel.setOnboardingStep("step_transition")
                                                onNavigateToSettings()
                                            }
                                            "step_media" -> {
                                                settingsViewModel.setOnboardingStep("step_storage")
                                            }
                                            "step_storage" -> {
                                                onNavigateBack()
                                                settingsViewModel.setOnboardingStep("step_home_transition")
                                            }
                                            "step_home" -> {
                                                onImportNow()
                                                settingsViewModel.setOnboardingStep("step_wrapper")
                                            }
                                            "step_wrapper" -> {
                                                settingsViewModel.setOnboardingStep("step_popup_add")
                                            }
                                            "step_popup_add" -> {
                                                settingsViewModel.setOnboardingStep("step_popup_edit")
                                            }
                                            "step_popup_edit" -> {
                                                settingsViewModel.setOnboardingStep("step_popup_delete")
                                            }
                                            "step_popup_delete" -> {
                                                settingsViewModel.setOnboardingStep("step_finish")
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(primaryText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            
            val isGlass = currentStep == "step_home" || currentStep == "step_wrapper"
            
            if (isGlass) {
                PremiumGlassPanel(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    contentBlock()
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .shadow(
                            elevation = 32.dp, 
                            shape = RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp), 
                            ambientColor = MaterialTheme.colorScheme.primary, 
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        .clip(RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp))
                        .background(Color(0xFF16161A).copy(alpha = 0.95f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp))
                ) {
                    contentBlock()
                }
            }
        }
    }
}

@Composable
fun OnboardingOverlay(
    settingsViewModel: SettingsViewModel,
    currentRoute: String?,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    onImportNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onboardingStep by settingsViewModel.onboardingStep.collectAsState()
    val spotlightTargets = settingsViewModel.spotlightTargets
    
    val introSeen by settingsViewModel.introSeen.collectAsState()
    LaunchedEffect(onboardingStep, introSeen, currentRoute) {
        if (onboardingStep == "step_home_transition") {
            delay(500)
            settingsViewModel.setOnboardingStep("step_home")
        }
        if (!introSeen && onboardingStep == null && currentRoute != "intro" && currentRoute != null) {
            delay(4000)
            if (!introSeen && onboardingStep == null) {
                settingsViewModel.startTour()
            }
        }
    }
    
    if (onboardingStep == null || currentRoute == "intro" || onboardingStep == "step_transition" || onboardingStep == "step_home_transition") return
    
    val currentStep = onboardingStep!!
    
    val targetKey = when (currentStep) {
        "step_jelly" -> "tour_jelly_ball"
        "step_media" -> "tour_media_mode"
        "step_storage" -> "tour_storage_mode"
        "step_popup_add" -> "tour_add_btn"
        "step_popup_edit" -> "tour_edit_btn"
        "step_popup_delete" -> "tour_delete_btn"
        else -> null
    }
    
    val targetRect = targetKey?.let { spotlightTargets[it] }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .zIndex(100f)
    ) {
        if (currentStep != "step_welcome" && currentStep != "step_finish") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                val pulseScale by rememberInfiniteTransition("pulse").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_cutout"
                )
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val isGlass = currentStep == "step_home" || currentStep == "step_wrapper"
                    val bgAlpha = if (isGlass) 0.1f else 0.8f
                    drawRect(color = Color.Black.copy(alpha = bgAlpha))
                    
                    if (targetRect != null) {
                        val scale = if (currentStep == "step_wrapper") pulseScale else 1f
                        val width = targetRect.width * scale
                        val height = targetRect.height * scale
                        val dx = (width - targetRect.width) / 2
                        val dy = (height - targetRect.height) / 2
                        
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(targetRect.left - dx - 8.dp.toPx(), targetRect.top - dy - 8.dp.toPx()),
                            size = Size(width + 16.dp.toPx(), height + 16.dp.toPx()),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            blendMode = BlendMode.Clear
                        )
                    }
                }
            }
        }
        
        when (currentStep) {
            "step_welcome" -> {
                WelcomeModal(
                    onAccept = {
                        settingsViewModel.setIntroSeen(true)
                        settingsViewModel.setIntroAccepted(true)
                        settingsViewModel.setOnboardingStep("step_jelly")
                    },
                    onDecline = {
                        settingsViewModel.setIntroSeen(true)
                        settingsViewModel.setIntroAccepted(false)
                        settingsViewModel.endTour()
                    }
                )
            }
            "step_finish" -> {
                FinishModal(
                    onFinish = {
                        settingsViewModel.setActiveTab(0)
                        settingsViewModel.endTour()
                    }
                )
            }
            else -> {
                TooltipContainer(
                    currentStep = currentStep,
                    targetRect = targetRect,
                    settingsViewModel = settingsViewModel,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateBack = onNavigateBack,
                    onImportNow = onImportNow
                )
            }
        }
    }
}

data class TooltipInfo(
    val title: String,
    val text: String,
    val hasBack: Boolean,
    val hasNext: Boolean,
    val primaryText: String
)
