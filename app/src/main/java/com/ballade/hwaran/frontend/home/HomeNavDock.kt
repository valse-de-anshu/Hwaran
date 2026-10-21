package com.ballade.hwaran.frontend.home

import com.ballade.hwaran.ui.components.CustomJellyBall
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.ui.components.DevilJellyBall
import com.ballade.hwaran.ui.components.JellyBall
import com.ballade.hwaran.ui.components.WobblySnakeRing

@Composable
fun HomeNavDock(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCenterActionClick: () -> Unit,
    onCenterActionLongClick: () -> Unit = {},
    isImporting: Boolean = false,
    importProgress: Float = 0f,
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFFE2E8F0),
    fabStyle: Int = 1
) {
    val haptic = LocalHapticFeedback.current

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gestureBottom = WindowInsets.systemGestures.asPaddingValues().calculateBottomPadding()
    val bottomClearance = maxOf(navBarBottom + 16.dp, maxOf(gestureBottom + 12.dp, 32.dp))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = bottomClearance),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Frosted Titanium Pill Dock
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 22.dp,
                    shape = RoundedCornerShape(34.dp),
                    spotColor = Color.Black,
                    ambientColor = Color.Black.copy(alpha = 0.6f)
                ),
            shape = RoundedCornerShape(34.dp),
            color = Color(0xFF111118),
            border = BorderStroke(
                1.2.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.25f),
                        glowColor.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF191824),
                                Color(0xFF12111A),
                                Color(0xFF0D0C14)
                            )
                        )
                    )
            ) {
                // Subtle top specular highlight line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.22f),
                                    glowColor.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Left 1: Home
                    DockItem(
                        icon = Icons.Rounded.Home,
                        label = "Home",
                        isSelected = selectedTab == 0,
                        glowColor = glowColor,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(0)
                        }
                    )

                    // Left 2: Library
                    DockItem(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        label = "Library",
                        isSelected = selectedTab == 1,
                        glowColor = glowColor,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(1)
                        }
                    )

                    // Center: JellyBall Mascot in middle with glowing pedestal
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .pointerInput(isImporting) {
                                detectTapGestures(
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCenterActionLongClick()
                                    },
                                    onTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCenterActionClick()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient radial glow pedestal
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            glowColor.copy(alpha = if (isImporting) 0.32f else 0.16f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                    val jellyScale by animateFloatAsState(
                        targetValue = if (isImporting) 0.58f else 0.74f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "jellyScale"
                    )

                    if (isImporting) {
                        WobblySnakeRing(
                            modifier = Modifier.size(70.dp),
                            progress = importProgress.coerceIn(0f, 1f)
                        )
                    }

                    key(fabStyle) {
                        val mascotModifier = Modifier
                            .size(120.dp)
                            .graphicsLayer {
                                scaleX = jellyScale
                                scaleY = jellyScale
                            }

                        when (fabStyle) {
                            2 -> {
                                DevilJellyBall(
                                    modifier = mascotModifier,
                                    isHappy = isImporting
                                )
                            }
                            3 -> {
                                CustomJellyBall(
                                    assetName = "autumn_harvest_jellyball.html",
                                    modifier = mascotModifier,
                                    isHappy = isImporting
                                )
                            }
                            4 -> {
                                CustomJellyBall(
                                    assetName = "chinese_new_year_dragon_lantern_jellyball.html",
                                    modifier = mascotModifier,
                                    isHappy = isImporting
                                )
                            }
                            5 -> {
                                CustomJellyBall(
                                    assetName = "sakura_jellyball.html",
                                    modifier = mascotModifier,
                                    isHappy = isImporting
                                )
                            }
                            6 -> {
                                CustomJellyBall(
                                    assetName = "spring_awakening_jellyball.html",
                                    modifier = mascotModifier.offset(y = 6.dp),
                                    isHappy = isImporting
                                )
                            }
                            7 -> {
                                CustomJellyBall(
                                    assetName = "winter_frost_jellyball.html",
                                    modifier = mascotModifier,
                                    isHappy = isImporting
                                )
                            }
                            else -> {
                                JellyBall(
                                    modifier = mascotModifier,
                                    isHappy = isImporting,
                                    enableJump = false,
                                    lookUp = false
                                )
                            }
                        }
                    }
                }

                // Right 1: Search
                DockItem(
                    icon = Icons.Rounded.Search,
                    label = "Search",
                    isSelected = selectedTab == 2,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(2)
                    }
                )

                // Right 2: Settings
                DockItem(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    isSelected = selectedTab == 3,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(3)
                    }
                )
            }
        }
    }
}
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    glowColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFFFFF) else Color(0xFF8E8D9A),
        animationSpec = tween(220),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFFFFF) else Color(0xFF8E8D9A),
        animationSpec = tween(220),
        label = "textColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dockScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) {
                    Brush.verticalGradient(
                        listOf(
                            glowColor.copy(alpha = 0.20f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                } else {
                    SolidColor(Color.Transparent)
                }
            )
            .border(
                if (isSelected) {
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                glowColor.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        )
                    )
                } else BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isSelected) {
                    // Soft diffused glow behind active icon
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        glowColor.copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = if (isSelected) 0.2.sp else 0.sp
            )
        }
    }
}
