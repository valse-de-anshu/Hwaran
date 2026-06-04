package com.ballade.hwaran.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.compose.animation.togetherWith

@Composable
fun MediaModeIndicator(mediaMode: Int, videoLayoutMode: Int = 0, modifier: Modifier = Modifier) {
    val state = remember(mediaMode, videoLayoutMode) {
        when {
            mediaMode == 0 -> 0
            mediaMode == 1 -> 1
            mediaMode == 2 && videoLayoutMode == 0 -> 2
            mediaMode == 2 && videoLayoutMode == 1 -> 3
            else -> 0
        }
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = state,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(animationSpec = tween(400)) + 
                 androidx.compose.animation.scaleIn(initialScale = 0.8f, animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)))
                .togetherWith(
                 androidx.compose.animation.fadeOut(animationSpec = tween(400)) +
                 androidx.compose.animation.scaleOut(targetScale = 0.8f, animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)))
            },
            label = "media_mode_transition"
        ) { targetState ->
            when (targetState) {
                0 -> ToonIndicator()
                1 -> BookIndicator()
                2 -> VideoSeriesIndicator()
                3 -> VideoCreatorIndicator()
            }
        }
    }
}

@Composable
private fun ToonIndicator() {
    val infiniteTransition = rememberInfiniteTransition("toon")
    val scrollY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scroll"
    )

    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        
        // Scale 24x24 down to 20x20
        val scale = size.width / 24f
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(5f, 2f),
                size = Size(14f, 20f),
                cornerRadius = CornerRadius(3f, 3f),
                style = stroke
            )
            
            val clipPath = Path().apply {
                addRoundRect(RoundRect(5f, 2f, 19f, 22f, CornerRadius(3f, 3f)))
            }
            
            clipPath(clipPath) {
                withTransform({ translate(top = -scrollY) }) {
                    // Block 1
                    drawRoundRect(Color.White, Offset(7f, 4f), Size(10f, 5f), CornerRadius(1f, 1f), style = stroke)
                    drawRoundRect(Color.White, Offset(7f, 11f), Size(10f, 8f), CornerRadius(1f, 1f), style = stroke)
                    drawRoundRect(Color.White, Offset(7f, 21f), Size(10f, 5f), CornerRadius(1f, 1f), style = stroke)
                    
                    // Block 2
                    drawRoundRect(Color.White, Offset(7f, 28f), Size(10f, 5f), CornerRadius(1f, 1f), style = stroke)
                    drawRoundRect(Color.White, Offset(7f, 35f), Size(10f, 8f), CornerRadius(1f, 1f), style = stroke)
                    drawRoundRect(Color.White, Offset(7f, 45f), Size(10f, 5f), CornerRadius(1f, 1f), style = stroke)
                }
            }
        }
    }
}

@Composable
private fun BookIndicator() {
    val infiniteTransition = rememberInfiniteTransition("book")
    
    val coverScaleX by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                -1f at 0
                -1f at 600
                1f at 1500 using FastOutSlowInEasing
                1f at 4500
                -1f at 5400 using FastOutSlowInEasing
                -1f at 6000
            }
        ), label = "cover"
    )
    
    val page1ScaleX by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                1f at 0
                1f at 1500
                1f at 2100
                -1f at 2700 using FastOutSlowInEasing
                -1f at 5400
                -1f at 6000
            }
        ), label = "page1_scale"
    )
    val page1Alpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0
                0f at 1490
                1f at 1500
                1f at 5400
                0f at 5410
                0f at 6000
            }
        ), label = "page1_alpha"
    )

    val page2ScaleX by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                1f at 0
                1f at 1500
                1f at 3000
                -1f at 3600 using FastOutSlowInEasing
                -1f at 5400
                -1f at 6000
            }
        ), label = "page2_scale"
    )
    val page2Alpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0
                0f at 1490
                1f at 1500
                1f at 5400
                0f at 5410
                0f at 6000
            }
        ), label = "page2_alpha"
    )

    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val scale = size.width / 24f
        
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            // Spine
            drawLine(Color.White.copy(alpha = 0.3f), Offset(12f, 4f), Offset(12f, 20f), stroke.width, StrokeCap.Round)
            
            // Left Cover
            val leftCover = Path().apply {
                moveTo(12f, 4f)
                quadraticTo(6f, 4f, 3f, 6f)
                lineTo(3f, 20f)
                quadraticTo(6f, 18f, 12f, 20f)
            }
            drawPath(leftCover, Color.White, style = stroke)
            
            // Right Cover
            withTransform({ scale(scaleX = coverScaleX, scaleY = 1f, pivot = Offset(12f, 12f)) }) {
                val rightCover = Path().apply {
                    moveTo(12f, 4f)
                    quadraticTo(18f, 4f, 21f, 6f)
                    lineTo(21f, 20f)
                    quadraticTo(18f, 18f, 12f, 20f)
                }
                drawPath(rightCover, Color.White, style = stroke)
            }
            
            // Page 1
            if (page1Alpha > 0f) {
                withTransform({ scale(scaleX = page1ScaleX, scaleY = 1f, pivot = Offset(12f, 12f)) }) {
                    val p1 = Path().apply {
                        moveTo(12f, 4f)
                        quadraticTo(18f, 4f, 20f, 5.5f)
                        lineTo(20f, 19.5f)
                        quadraticTo(18f, 18f, 12f, 20f)
                    }
                    drawPath(p1, Color.White.copy(alpha = page1Alpha), style = stroke)
                }
            }
            
            // Page 2
            if (page2Alpha > 0f) {
                withTransform({ scale(scaleX = page2ScaleX, scaleY = 1f, pivot = Offset(12f, 12f)) }) {
                    val p2 = Path().apply {
                        moveTo(12f, 4f)
                        quadraticTo(18f, 4f, 19f, 5f)
                        lineTo(19f, 19f)
                        quadraticTo(18f, 18f, 12f, 20f)
                    }
                    drawPath(p2, Color.White.copy(alpha = page2Alpha), style = stroke)
                }
            }
        }
    }
}

@Composable
private fun VideoSeriesIndicator() {
    val infiniteTransition = rememberInfiniteTransition("video_series")
    
    val scrubberX by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                0f at 0
                0f at 350
                14f at 2975 using FastOutSlowInEasing
                14f at 3500
            }
        ), label = "scrub_x"
    )
    val scrubberAlpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                0f at 0
                1f at 350
                1f at 2975
                0f at 3325
                0f at 3500
            }
        ), label = "scrub_alpha"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                1f at 0
                1f at 2975
                0.85f at 3150
                1f at 3325
                1f at 3500
            }
        ), label = "pulse"
    )

    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val scale = size.width / 24f
        val greenColor = Color(0xFF8EB69B)
        
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            // Screen Border
            drawRoundRect(
                color = greenColor,
                topLeft = Offset(2f, 4f),
                size = Size(20f, 16f),
                cornerRadius = CornerRadius(2f, 2f),
                style = stroke
            )
            
            // Play Triangle
            withTransform({ scale(pulse, pulse, Offset(12f, 10.5f)) }) {
                val triangle = Path().apply {
                    moveTo(10f, 8.5f)
                    lineTo(15f, 11.5f)
                    lineTo(10f, 14.5f)
                    close()
                }
                drawPath(triangle, greenColor, style = stroke)
            }
            
            // Timeline Track
            drawLine(greenColor.copy(alpha = 0.3f), Offset(5f, 17f), Offset(19f, 17f), stroke.width, StrokeCap.Round)
            
            // Playhead
            if (scrubberAlpha > 0f) {
                drawCircle(
                    color = greenColor.copy(alpha = scrubberAlpha),
                    radius = 1.5f,
                    center = Offset(5f + scrubberX, 17f)
                )
            }
        }
    }
}

@Composable
private fun VideoCreatorIndicator() {
    val infiniteTransition = rememberInfiniteTransition("video_creator")
    
    val scrubberX by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                0f at 0
                0f at 350
                14f at 2975 using FastOutSlowInEasing
                14f at 3500
            }
        ), label = "scrub_x"
    )
    val scrubberAlpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                0f at 0
                1f at 350
                1f at 2975
                0f at 3325
                0f at 3500
            }
        ), label = "scrub_alpha"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                1f at 0
                1f at 2975
                0.85f at 3150
                1f at 3325
                1f at 3500
            }
        ), label = "pulse"
    )

    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val scale = size.width / 24f
        val orangeColor = Color(0xFFFF7043)
        
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            // Screen Border
            drawRoundRect(
                color = orangeColor,
                topLeft = Offset(2f, 4f),
                size = Size(20f, 16f),
                cornerRadius = CornerRadius(2f, 2f),
                style = stroke
            )
            
            // Play Triangle
            withTransform({ scale(pulse, pulse, Offset(12f, 10.5f)) }) {
                val triangle = Path().apply {
                    moveTo(10f, 8.5f)
                    lineTo(15f, 11.5f)
                    lineTo(10f, 14.5f)
                    close()
                }
                drawPath(triangle, orangeColor, style = stroke)
            }
            
            // Timeline Track
            drawLine(orangeColor.copy(alpha = 0.3f), Offset(5f, 17f), Offset(19f, 17f), stroke.width, StrokeCap.Round)
            
            // Playhead
            if (scrubberAlpha > 0f) {
                drawCircle(
                    color = orangeColor.copy(alpha = scrubberAlpha),
                    radius = 1.5f,
                    center = Offset(5f + scrubberX, 17f)
                )
            }
        }
    }
}
