package com.ballade.hwaran.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun LiquidNavigation(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFFE2E8F0),
    isVertical: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    
    // Core animation for tab switching (Viscous Flow)
    val navProgress by animateFloatAsState(
        targetValue = activeTab.toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 100f),
        label = "navProgress"
    )

    // Breathing pulse for the halo
    val infiniteTransition = rememberInfiniteTransition(label = "haloPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Pre-load vector painters to draw them directly on Canvas
    val homePainter = rememberVectorPainter(Icons.Rounded.Home)
    val musicPainter = rememberVectorPainter(Icons.Rounded.LibraryMusic)

    BoxWithConstraints(
        modifier = modifier
            .size(if (isVertical) 100.dp else 240.dp, if (isVertical) 240.dp else 100.dp),
        contentAlignment = Alignment.Center
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        val density = androidx.compose.ui.platform.LocalDensity.current
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isVertical) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                                val pos = event.changes.first().position
                                if (isVertical) {
                                    if (pos.y < height / 2f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTabSelected(0)
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTabSelected(1)
                                    }
                                } else {
                                    if (pos.x < width / 2f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTabSelected(0)
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTabSelected(1)
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val baseRadius = 28.dp.toPx()
            val activeExtra = 10.dp.toPx()
            
            val r1 = lerp(baseRadius + activeExtra, baseRadius, navProgress)
            val r2 = lerp(baseRadius, baseRadius + activeExtra, navProgress)
            val haloRadius = baseRadius + activeExtra

            if (isVertical) {
                val topY = height * 0.28f
                val bottomY = height * 0.72f
                val ballCenterX = centerX + 0.dp.toPx() // No shift for vertical centered

                val dist = bottomY - topY
                val waist = (r1 + r2) * 0.32f
                
                val path = Path().apply {
                    moveTo(ballCenterX - r1, topY)
                    cubicTo(ballCenterX - r1 * 0.85f, topY + dist * 0.35f, ballCenterX - waist, height * 0.5f - dist * 0.1f, ballCenterX - waist, height * 0.5f)
                    cubicTo(ballCenterX - waist, height * 0.5f + dist * 0.1f, ballCenterX - r2 * 0.85f, bottomY - dist * 0.35f, ballCenterX - r2, bottomY)
                    arcTo(androidx.compose.ui.geometry.Rect(ballCenterX - r2, bottomY - r2, ballCenterX + r2, bottomY + r2), 180f, -180f, false)
                    cubicTo(ballCenterX + r2 * 0.85f, bottomY - dist * 0.35f, ballCenterX + waist, height * 0.5f + dist * 0.1f, ballCenterX + waist, height * 0.5f)
                    cubicTo(ballCenterX + waist, height * 0.5f - dist * 0.1f, ballCenterX + r1 * 0.85f, topY + dist * 0.35f, ballCenterX + r1, topY)
                    arcTo(androidx.compose.ui.geometry.Rect(ballCenterX - r1, topY - r1, ballCenterX + r1, topY + r1), 0f, -180f, false)
                    close()
                }

                drawIntoCanvas { canvas ->
                    val activeY = lerp(topY, bottomY, navProgress)
                    
                    val glowPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = glowColor.copy(alpha = 0.10f * pulse).toArgb()
                        maskFilter = android.graphics.BlurMaskFilter(30.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawCircle(ballCenterX, activeY, haloRadius * 1.1f, glowPaint)
                    
                    val coreGlowPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = glowColor.copy(alpha = 0.15f).toArgb()
                        maskFilter = android.graphics.BlurMaskFilter(16.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawCircle(ballCenterX, activeY, haloRadius * 0.6f, coreGlowPaint)

                    val iconSize = 28.dp.toPx()
                    val t1Scale = lerp(1.2f, 0.9f, navProgress)
                    translate(ballCenterX - (iconSize * t1Scale) / 2f, topY - (iconSize * t1Scale) / 2f) {
                        with(homePainter) {
                            draw(size = Size(iconSize * t1Scale, iconSize * t1Scale), alpha = (1f - navProgress).coerceIn(0.4f, 1f), colorFilter = ColorFilter.tint(Color.White))
                        }
                    }

                    val b1Scale = lerp(0.9f, 1.2f, navProgress)
                    translate(ballCenterX - (iconSize * b1Scale) / 2f, bottomY - (iconSize * b1Scale) / 2f) {
                        with(musicPainter) {
                            draw(size = Size(iconSize * b1Scale, iconSize * b1Scale), alpha = navProgress.coerceIn(0.4f, 1f), colorFilter = ColorFilter.tint(Color.White))
                        }
                    }
                }
            } else {
                val leftX = width * 0.28f
                val rightX = width * 0.72f
                val ballCenterY = centerY + 12.dp.toPx()

                val dist = rightX - leftX
                val waist = (r1 + r2) * 0.32f
                
                val path = Path().apply {
                    moveTo(leftX, ballCenterY - r1)
                    cubicTo(leftX + dist * 0.35f, ballCenterY - r1 * 0.85f, width * 0.5f - dist * 0.1f, ballCenterY - waist, width * 0.5f, ballCenterY - waist)
                    cubicTo(width * 0.5f + dist * 0.1f, ballCenterY - waist, rightX - dist * 0.35f, ballCenterY - r2 * 0.85f, rightX, ballCenterY - r2)
                    arcTo(androidx.compose.ui.geometry.Rect(rightX - r2, ballCenterY - r2, rightX + r2, ballCenterY + r2), -90f, 180f, false)
                    cubicTo(rightX - dist * 0.35f, ballCenterY + r2 * 0.85f, width * 0.5f + dist * 0.1f, ballCenterY + waist, width * 0.5f, ballCenterY + waist)
                    cubicTo(width * 0.5f - dist * 0.1f, ballCenterY + waist, leftX + dist * 0.35f, ballCenterY + r1 * 0.85f, leftX, ballCenterY + r1)
                    arcTo(androidx.compose.ui.geometry.Rect(leftX - r1, ballCenterY - r1, leftX + r1, ballCenterY + r1), 90f, 180f, false)
                    close()
                }

                drawIntoCanvas { canvas ->
                    val activeX = lerp(leftX, rightX, navProgress)
                    
                    val glowPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = glowColor.copy(alpha = 0.10f * pulse).toArgb()
                        maskFilter = android.graphics.BlurMaskFilter(30.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawCircle(activeX, ballCenterY, haloRadius * 1.1f, glowPaint)
                    
                    val coreGlowPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = glowColor.copy(alpha = 0.15f).toArgb()
                        maskFilter = android.graphics.BlurMaskFilter(16.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawCircle(activeX, ballCenterY, haloRadius * 0.6f, coreGlowPaint)

                    val iconSize = 28.dp.toPx()
                    val leftIconScale = lerp(1.2f, 0.9f, navProgress)
                    translate(leftX - (iconSize * leftIconScale) / 2f, ballCenterY - (iconSize * leftIconScale) / 2f) {
                        with(homePainter) {
                            draw(size = Size(iconSize * leftIconScale, iconSize * leftIconScale), alpha = (1f - navProgress).coerceIn(0.4f, 1f), colorFilter = ColorFilter.tint(Color.White))
                        }
                    }

                    val rightIconScale = lerp(0.9f, 1.2f, navProgress)
                    translate(rightX - (iconSize * rightIconScale) / 2f, ballCenterY - (iconSize * rightIconScale) / 2f) {
                        with(musicPainter) {
                            draw(size = Size(iconSize * rightIconScale, iconSize * rightIconScale), alpha = navProgress.coerceIn(0.4f, 1f), colorFilter = ColorFilter.tint(Color.White))
                        }
                    }
                }
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}