package com.ballade.hwaran.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.MaterialTheme

import androidx.compose.ui.graphics.Brush

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
    toggleWidth: androidx.compose.ui.unit.Dp = 180.dp,
    toggleHeight: androidx.compose.ui.unit.Dp = 44.dp
) {
    val haptic = LocalHapticFeedback.current
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val bgDark = MaterialTheme.colorScheme.background
    val cardSurface = MaterialTheme.colorScheme.surfaceVariant
 
    val pillGradient = remember(glowColorOverride) {
        val colorVal = glowColorOverride?.let { (it.toArgb().toLong() and 0xFFFFFFFF) } ?: 0xFFD481D2L
        val colors = when (colorVal) {
            0xFFD481D2L -> listOf(Color(0xFFD481D2), Color(0xFFBE74BE), Color(0xFF703B94))
            0xFFC3A6FEL -> listOf(Color(0xFFC3A6FE), Color(0xFF383852), Color(0xFF161622))
            0xFFFDE4E6L -> listOf(Color(0xFFFDE4E6), Color(0xFFE56A72), Color(0xFF992A31), Color(0xFF410C11))
            0xFF8EB69BL -> listOf(Color(0xFF8EB69B), Color(0xFF235347), Color(0xFF163832), Color(0xFF051F20))
            0xFFD6D3E5L -> listOf(Color(0xFFD6D3E5), Color(0xFFACA5B9), Color(0xFF8F85BE), Color(0xFF666A90), Color(0xFF433D6B))
            0xFF5C9FD9L -> listOf(Color(0xFF5C9FD9), Color(0xFF255DAC), Color(0xFF15326D), Color(0xFF111523))
            0xFFBDC6CDL -> listOf(Color(0xFFBDC6CD), Color(0xFF6A757E), Color(0xFF404C55), Color(0xFF111A22))
            0xFF7A6284L -> listOf(Color(0xFF7A6284), Color(0xFF52425C), Color(0xFF382B3F), Color(0xFF1F1823), Color(0xFF0C080D))
            else -> listOf(glowColorOverride ?: primaryColor, glowColorOverride ?: primaryColor)
        }
        Brush.linearGradient(colors)
    }
 
    val isDeepDarkTheme = remember(bgDark) {
        val targets = listOf(0xFF0B0707, 0xFF433D6B, 0xFF0C080D, 0xFF703B94)
        val current = bgDark.toArgb() and 0xFFFFFF
        targets.any { (it.toInt() and 0xFFFFFF) == current }
    }
    
    val isDarkTheme = bgDark.luminance() < 0.5f
    val glowColor = (glowColorOverride ?: if (isDeepDarkTheme) Color.Black else if (isDarkTheme) primaryColor else Color.Black)
        .copy(alpha = (glowBrightness * 1.5f).coerceIn(0f, 1f))


    Box(
        modifier = modifier
            .width(toggleWidth)
            .height(toggleHeight)
            .graphicsLayer { clip = false }
            .drawBehind {
                drawIntoCanvas { canvas ->
                    if (showGlow && glowRadius > 0) {
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            this.color = glowColor.toArgb()
                            maskFilter = android.graphics.BlurMaskFilter(
                                glowRadius.dp.toPx(),
                                android.graphics.BlurMaskFilter.Blur.OUTER
                            )
                        }
                        repeat(3) {
                            canvas.nativeCanvas.drawRoundRect(
                                0f, 0f, size.width, size.height,
                                24.dp.toPx(), 24.dp.toPx(),
                                paint
                            )
                        }
                    }
                }
            }
            .background(cardSurface.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val offsetProgress by animateFloatAsState(
            targetValue = if (isOption2) 1f else 0f,
            animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = 300f
            ),
            label = "springOffset"
        )
        
        val distToCenter = 0.5f - Math.abs(offsetProgress - 0.5f)
        val extraWidth = distToCenter * 40f 
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val actualPillWidth = maxWidth / 2
            Box(
                modifier = Modifier
                    .width(actualPillWidth + extraWidth.dp)
                    .fillMaxHeight()
                    .offset(x = actualPillWidth * offsetProgress - (extraWidth.dp / 2))
                    .clip(RoundedCornerShape(20.dp))
                    .background(pillGradient)
            )
        }
        
        Row(modifier = Modifier.fillMaxSize()) {
            val color1 by animateColorAsState(if (!isOption2) Color.Black else textMuted, label = "c1")
            val color2 by animateColorAsState(if (isOption2) Color.Black else textMuted, label = "c2")

            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(false) 
                },
                contentAlignment = Alignment.Center
            ) {
                Text(text = option1, color = color1, fontSize = 14.sp, fontWeight = if (!isOption2) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(true) 
                },
                contentAlignment = Alignment.Center
            ) {
                Text(text = option2, color = color2, fontSize = 14.sp, fontWeight = if (isOption2) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
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
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val bgDark = MaterialTheme.colorScheme.background
    val cardSurface = MaterialTheme.colorScheme.surfaceVariant

    val pillGradient = remember(glowColorOverride) {
        val colorVal = glowColorOverride?.let { (it.toArgb().toLong() and 0xFFFFFFFF) } ?: 0xFFD481D2L
        val colors = when (colorVal) {
            0xFFD481D2L -> listOf(Color(0xFFD481D2), Color(0xFFBE74BE), Color(0xFF703B94))
            0xFFC3A6FEL -> listOf(Color(0xFFC3A6FE), Color(0xFF383852), Color(0xFF161622))
            0xFFFDE4E6L -> listOf(Color(0xFFFDE4E6), Color(0xFFE56A72), Color(0xFF992A31), Color(0xFF410C11))
            0xFF8EB69BL -> listOf(Color(0xFF8EB69B), Color(0xFF235347), Color(0xFF163832), Color(0xFF051F20))
            0xFFD6D3E5L -> listOf(Color(0xFFD6D3E5), Color(0xFFACA5B9), Color(0xFF8F85BE), Color(0xFF666A90), Color(0xFF433D6B))
            0xFF5C9FD9L -> listOf(Color(0xFF5C9FD9), Color(0xFF255DAC), Color(0xFF15326D), Color(0xFF111523))
            0xFFBDC6CDL -> listOf(Color(0xFFBDC6CD), Color(0xFF6A757E), Color(0xFF404C55), Color(0xFF111A22))
            0xFF7A6284L -> listOf(Color(0xFF7A6284), Color(0xFF52425C), Color(0xFF382B3F), Color(0xFF1F1823), Color(0xFF0C080D))
            else -> listOf(glowColorOverride ?: primaryColor, glowColorOverride ?: primaryColor)
        }
        Brush.linearGradient(colors)
    }

    val isDeepDarkTheme = remember(bgDark) {
        val targets = listOf(0xFF0B0707, 0xFF433D6B, 0xFF0C080D, 0xFF703B94)
        val current = bgDark.toArgb() and 0xFFFFFF
        targets.any { (it.toInt() and 0xFFFFFF) == current }
    }
    
    val isDarkTheme = bgDark.luminance() < 0.5f
    val glowColor = (glowColorOverride ?: if (isDeepDarkTheme) Color.Black else if (isDarkTheme) primaryColor else Color.Black)
        .copy(alpha = (glowBrightness * 1.5f).coerceIn(0f, 1f))

    val toggleHeight = 44.dp

    Box(
        modifier = modifier
            .widthIn(max = if (options.size > 3) 320.dp else 260.dp)
            .fillMaxWidth()
            .height(toggleHeight)
            .graphicsLayer { clip = false }
            .drawBehind {
                drawIntoCanvas { canvas ->
                    if (showGlow && glowRadius > 0) {
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            this.color = glowColor.toArgb()
                            maskFilter = android.graphics.BlurMaskFilter(
                                glowRadius.dp.toPx(),
                                android.graphics.BlurMaskFilter.Blur.OUTER
                            )
                        }
                        repeat(3) {
                            canvas.nativeCanvas.drawRoundRect(
                                0f, 0f, size.width, size.height,
                                24.dp.toPx(), 24.dp.toPx(),
                                paint
                            )
                        }
                    }
                }
            }
            .background(cardSurface.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
        val offsetProgress by animateFloatAsState(
            targetValue = safeIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = 300f
            ),
            label = "springOffset"
        )
        
        val distToCenter = 0.5f - Math.abs((offsetProgress % 1f) - 0.5f)
        val extraWidth = distToCenter * 40f
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val actualPillWidth = maxWidth / options.size.coerceAtLeast(1)
            Box(
                modifier = Modifier
                    .width(actualPillWidth + extraWidth.dp)
                    .fillMaxHeight()
                    .offset(x = actualPillWidth * offsetProgress - (extraWidth.dp / 2))
                    .clip(RoundedCornerShape(20.dp))
                    .background(pillGradient)
            )
            
            Row(modifier = Modifier.fillMaxSize()) {
                options.forEachIndexed { index, option ->
                    val color by animateColorAsState(if (selectedIndex == index) Color.Black else textMuted, label = "color$index")

                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
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
                            fontWeight = if (selectedIndex == index) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
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
