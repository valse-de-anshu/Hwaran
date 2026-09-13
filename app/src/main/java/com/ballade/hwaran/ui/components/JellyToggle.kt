package com.ballade.hwaran.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JellyToggle(
    option1: String,
    option2: String,
    isOption2: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    glowBrightness: Float = 0f,
    glowRadius: Float = 0f,
    showGlow: Boolean = false,
    glowColorOverride: Color? = null,
    toggleWidth: Dp = 180.dp,
    toggleHeight: Dp = 42.dp
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .width(toggleWidth)
            .height(toggleHeight)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF13151D))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val offsetProgress by animateFloatAsState(
            targetValue = if (isOption2) 1f else 0f,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = 380f
            ),
            label = "springOffset"
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val actualPillWidth = maxWidth / 2
            Box(
                modifier = Modifier
                    .width(actualPillWidth)
                    .fillMaxHeight()
                    .offset(x = actualPillWidth * offsetProgress)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color(0xFF222631))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(19.dp))
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            val color1 by animateColorAsState(
                if (!isOption2) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                label = "c1"
            )
            val color2 by animateColorAsState(
                if (isOption2) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                label = "c2"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle(false)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option1,
                    color = color1,
                    fontSize = 13.sp,
                    fontWeight = if (!isOption2) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle(true)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option2,
                    color = color2,
                    fontSize = 13.sp,
                    fontWeight = if (isOption2) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun JellyToggle3(
    options: List<String>,
    selectedIndex: Int,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glowBrightness: Float = 0f,
    glowRadius: Float = 0f,
    showGlow: Boolean = false,
    glowColorOverride: Color? = null
) {
    val haptic = LocalHapticFeedback.current
    val toggleHeight = 42.dp

    Box(
        modifier = modifier
            .widthIn(max = if (options.size > 3) 320.dp else 260.dp)
            .fillMaxWidth()
            .height(toggleHeight)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF13151D))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
        val offsetProgress by animateFloatAsState(
            targetValue = safeIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = 380f
            ),
            label = "springOffset"
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val actualPillWidth = maxWidth / options.size.coerceAtLeast(1)
            Box(
                modifier = Modifier
                    .width(actualPillWidth)
                    .fillMaxHeight()
                    .offset(x = actualPillWidth * offsetProgress)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color(0xFF222631))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(19.dp))
            )

            Row(modifier = Modifier.fillMaxSize()) {
                options.forEachIndexed { index, option ->
                    val color by animateColorAsState(
                        if (selectedIndex == index) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.45f),
                        label = "color$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggle(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            color = color,
                            fontSize = 13.sp,
                            fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
