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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NavigationPill(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFFD4D8E0)
) {
    val haptic = LocalHapticFeedback.current
    val pillWidth = 220.dp
    val pillHeight = 56.dp

    Box(
        modifier = modifier
            .width(pillWidth)
            .height(pillHeight)
            .background(Color(0xFF111318), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val offsetProgress by animateFloatAsState(
            targetValue = activeTab.toFloat(),
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
            label = "tabOffset"
        )
        
        val tabWidth = (pillWidth - 8.dp) / 2
        
        // Active indicator: Elevated obsidian dark glass with soft moonlight border
        Box(
            modifier = Modifier
                .width(tabWidth)
                .fillMaxHeight()
                .offset(x = tabWidth * offsetProgress)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF222631))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
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
