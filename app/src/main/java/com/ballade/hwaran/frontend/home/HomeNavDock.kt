package com.ballade.hwaran.frontend.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    glowColor: Color = Color(0xFF9C27B0)
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
        // Floating Frosted Pill Dock
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(34.dp),
                    spotColor = Color.Black.copy(alpha = 0.8f),
                    ambientColor = Color.Black.copy(alpha = 0.5f)
                ),
            shape = RoundedCornerShape(34.dp),
            color = Color(0xEE111016),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
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

                // Center: JellyBall Mascot in middle with icons (full, prominent size)
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

                    JellyBall(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(jellyScale),
                        isHappy = isImporting,
                        enableJump = false,
                        lookUp = false
                    )
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

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    glowColor: Color,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
        animationSpec = tween(250),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) glowColor else Color.White.copy(alpha = 0.45f),
        animationSpec = tween(250),
        label = "textColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "dockScale"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                // Subtle glow behind active icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.35f),
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
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
