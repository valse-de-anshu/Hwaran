package com.ballade.hwaran.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NavigationPill(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFFD481D2)
) {
    val haptic = LocalHapticFeedback.current
    val pillWidth = 220.dp
    val pillHeight = 56.dp
    
    val pillGradient = remember(glowColor) {
        val colors = listOf(glowColor.copy(alpha = 0.8f), glowColor, glowColor.copy(alpha = 0.6f))
        Brush.linearGradient(colors)
    }

    Box(
        modifier = modifier
            .width(pillWidth)
            .height(pillHeight)
            .graphicsLayer { clip = false }
            .drawBehind {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        this.color = glowColor.copy(alpha = 0.3f).toArgb()
                        maskFilter = android.graphics.BlurMaskFilter(
                            20.dp.toPx(),
                            android.graphics.BlurMaskFilter.Blur.OUTER
                        )
                    }
                    repeat(2) {
                        canvas.nativeCanvas.drawRoundRect(
                            0f, 0f, size.width, size.height,
                            28.dp.toPx(), 28.dp.toPx(),
                            paint
                        )
                    }
                }
            }
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val offsetProgress by animateFloatAsState(
            targetValue = activeTab.toFloat(),
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
            label = "tabOffset"
        )
        
        val tabWidth = (pillWidth - 8.dp) / 2
        
        // Active indicator
        Box(
            modifier = Modifier
                .width(tabWidth)
                .fillMaxHeight()
                .offset(x = tabWidth * offsetProgress)
                .clip(RoundedCornerShape(24.dp))
                .background(pillGradient)
        )
        
        Row(modifier = Modifier.fillMaxSize()) {
            TabItem(
                label = "Library",
                icon = Icons.Rounded.MusicNote,
                isSelected = activeTab == 0,
                modifier = Modifier.weight(1f)
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTabSelected(0)
            }
            TabItem(
                label = "Music",
                icon = Icons.Rounded.Waves,
                isSelected = activeTab == 1,
                modifier = Modifier.weight(1f)
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTabSelected(1)
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
        label = "color"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
            )
        }
    }
}
