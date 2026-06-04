package com.ballade.hwaran.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WobblySnakeRing(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Color.White),
    wobbleSpeed: Int = 8000,
    wobbleCount: Int = 4,
    wobbleAmplitude: Float = 8f,
    strokeWidth: Float = 4.5f,
    phaseOffset: Float = 0f,
    progress: Float = 1f,
    baseRadiusOverride: Float? = null,
    showOuterRing: Boolean = true,
    trackAlpha: Float = 0.15f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_rings")
    
    val rotationState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(wobbleSpeed * 6, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val phase1State = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(wobbleSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2State = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((wobbleSpeed * 2.5f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val glowPulseState = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Canvas(modifier = modifier) {
        val rotation = rotationState.value
        val phase1 = phase1State.value + phaseOffset
        val phase2 = phase2State.value - phaseOffset
        val glowPulse = glowPulseState.value
        
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = baseRadiusOverride ?: (size.minDimension / 2 - 12.dp.toPx())

        // logic from background: position-based color blending
        // Use SweepGradient to distribute colors around the ring for a diffused, multi-color look
        val brush = if (colors.size >= 2) {
            Brush.sweepGradient(
                colors = colors,
                center = center
            )
        } else {
            SolidColor(colors.firstOrNull() ?: Color.White)
        }
        
        rotate(rotation) {
            // 0. GHOST TRACK (Always visible)
            drawWobblyFluidPath(
                center = center,
                radius = baseRadius,
                phase = phase1,
                wobbleCount = wobbleCount,
                wobbleAmplitude = wobbleAmplitude,
                brush = brush,
                alpha = trackAlpha * glowPulse,
                strokeWidth = strokeWidth,
                progress = 1f
            )

            // 1. ACTIVE SNAKE (Real progress)
            if (progress > 0.01f) {
                drawWobblyFluidPath(
                    center = center,
                    radius = baseRadius,
                    phase = phase1,
                    wobbleCount = wobbleCount,
                    wobbleAmplitude = wobbleAmplitude,
                    brush = brush,
                    alpha = 0.9f * glowPulse,
                    strokeWidth = strokeWidth * 1.2f, // Slightly thicker for "active"
                    progress = progress
                )
            }

            // Outer Ring
            if (showOuterRing && progress >= 0.95f) {
                drawWobblyFluidPath(
                    center = center,
                    radius = baseRadius + 12.dp.toPx(),
                    phase = phase2,
                    wobbleCount = (wobbleCount - 1).coerceAtLeast(3),
                    wobbleAmplitude = wobbleAmplitude * 1.3f,
                    brush = brush,
                    alpha = 0.45f * glowPulse,
                    strokeWidth = strokeWidth * 0.7f,
                    progress = progress
                )
            }
        }
    }
}

private fun DrawScope.drawWobblyFluidPath(
    center: Offset,
    radius: Float,
    phase: Float,
    wobbleCount: Int,
    wobbleAmplitude: Float,
    brush: Brush,
    alpha: Float,
    strokeWidth: Float,
    progress: Float = 1f
) {
    val path = Path()
    val points = 60 
    val endPoint = (points * progress).toInt()
    
    val count = wobbleCount.toFloat()
    
    for (i in 0..endPoint) {
        val angle = (i.toFloat() / points) * 2f * PI.toFloat()
        
        val primaryWobble = sin((angle * count + phase).toDouble()).toFloat()
        val secondaryWobble = sin((angle * count * 2f - phase * 2f).toDouble()).toFloat() * 0.35f
        val tertiaryWobble = cos((angle * 3f + phase).toDouble()).toFloat() * 0.15f
        
        val totalWobble = (primaryWobble + secondaryWobble + tertiaryWobble) * wobbleAmplitude
        
        val r = radius + totalWobble
        val x = center.x + r * cos(angle.toDouble()).toFloat()
        val y = center.y + r * sin(angle.toDouble()).toFloat()
        
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    
    if (progress >= 1f) path.close()
    
    drawPath(
        path = path,
        brush = brush,
        alpha = alpha,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}
