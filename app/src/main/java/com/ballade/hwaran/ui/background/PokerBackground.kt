package com.ballade.hwaran.ui.background

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// Luxury Colors from JSX
private val GoldGrad = listOf(Color(0xFFE8C988), Color(0xFFC5A059), Color(0xFF8A6A32))
private val CrimsonGrad = listOf(Color(0xFF8B0018), Color(0xFF3A0010))
private val VoidGrad = listOf(Color(0xFF151518), Color(0xFF050505))

private class PokerArtifact(
    val id: Int,
    val type: Int, // 0: Card, 1: Chip, 2: Mask, 3: Dice
    val x: Float, // %
    val y: Float, // %
    val depth: Float,
    val baseRotation: Float,
    val floatSpeed: Float,
    val rotationSpeed: Float,
    val rotXSpeed: Float,
    val rotYSpeed: Float,
    val floatPattern: Int,
    val floatAmpX: Float,
    val floatAmpY: Float,
    val phaseOffset: Float,
    val zIndex: Int,
    val size: Float,
    val blur: Float
)

@Composable
fun PokerBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1.0f,
    isEnabled: Boolean = true,
    scrollOffset: Float = 0f
) {
    // Reduced count to 12 as requested
    val artifacts = remember {
        List(12) { i ->
            val depthCategory = Random.nextFloat()
            val depth: Float
            val size: Float
            val zIndex: Int
            val blur: Float
            
            if (depthCategory < 0.3f) {
                depth = 0.3f + Random.nextFloat() * 0.2f
                size = 40f
                zIndex = 10
                blur = 4f
            } else if (depthCategory < 0.7f) {
                depth = 0.6f + Random.nextFloat() * 0.3f
                size = 65f
                zIndex = 20
                blur = 1f
            } else {
                depth = 1.0f + Random.nextFloat() * 0.5f
                size = 100f
                zIndex = 30
                blur = 0f
            }

            PokerArtifact(
                id = i,
                type = i % 4,
                x = (Random.nextFloat() * 85f) + 5f,
                y = (Random.nextFloat() * 120f) - 10f,
                depth = depth,
                baseRotation = Random.nextFloat() * 360f,
                floatSpeed = 0.02f + Random.nextFloat() * 0.02f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 0.015f,
                rotXSpeed = 0.01f + Random.nextFloat() * 0.02f,
                rotYSpeed = 0.01f + Random.nextFloat() * 0.02f,
                floatPattern = Random.nextInt(3),
                floatAmpX = 15f + Random.nextFloat() * 30f,
                floatAmpY = 20f + Random.nextFloat() * 40f,
                phaseOffset = Random.nextFloat() * PI.toFloat() * 2f,
                zIndex = zIndex,
                size = size,
                blur = blur
            )
        }
    }

    var time by remember { mutableStateOf(0f) }
    var currentScroll by remember { mutableStateOf(0f) }
    val currentScrollOffset by rememberUpdatedState(scrollOffset)
    val scrollDiff = scrollOffset - currentScroll

    LaunchedEffect(animationSpeed, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        while (isActive) {
            time += 0.015f * animationSpeed
            currentScroll += (currentScrollOffset - currentScroll) * 0.05f
            delay(16)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val vWidth = constraints.maxWidth.toFloat()
        val vHeight = constraints.maxHeight.toFloat()

        // Deep Background Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0f to Color(0xFF1F0A0C),
                        0.6f to Color(0xFF050505),
                        1f to Color.Black
                    )
                )
        )

        // Fog Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = sin(time * 0.05f) * 15f
                    translationY = -currentScroll * 0.01f
                    alpha = 0.15f
                }
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent)
                    )
                )
        )

        // Artifacts Layer
        artifacts.forEach { artifact ->
            ArtifactItem(
                artifact = artifact,
                time = time,
                currentScroll = currentScroll,
                scrollDiff = scrollDiff,
                viewportWidth = vWidth,
                viewportHeight = vHeight
            )
        }

        // Heavy Vignette Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.7f to Color.Transparent,
                        1f to Color.Black
                    )
                )
        )
    }
}

@Composable
private fun ArtifactItem(
    artifact: PokerArtifact,
    time: Float,
    currentScroll: Float,
    scrollDiff: Float,
    viewportWidth: Float,
    viewportHeight: Float
) {
    val density = LocalDensity.current
    
    // Independent floating physics
    var driftX = 0f
    var driftY = 0f

    when (artifact.floatPattern) {
        0 -> {
            driftX = sin(time * artifact.floatSpeed + artifact.phaseOffset) * artifact.floatAmpX
            driftY = cos(time * artifact.floatSpeed + artifact.phaseOffset) * artifact.floatAmpY
        }
        1 -> {
            driftX = sin(time * artifact.floatSpeed + artifact.phaseOffset) * artifact.floatAmpX
            driftY = sin(2 * (time * artifact.floatSpeed) + artifact.phaseOffset) * (artifact.floatAmpY / 2f)
        }
        else -> {
            driftX = cos(time * artifact.floatSpeed * 0.8f + artifact.phaseOffset) * artifact.floatAmpX
            driftY = sin(time * artifact.floatSpeed + artifact.phaseOffset) * artifact.floatAmpY
        }
    }

    // Scroll Reaction & Parallax
    val parallaxY = -currentScroll * artifact.depth * 1.2f
    
    // 3D Rotations
    val boundedDrag = (scrollDiff * 0.1f * artifact.depth).coerceIn(-10f, 10f)
    val rotX = sin(time * artifact.rotXSpeed + artifact.phaseOffset) * 15f - boundedDrag
    val rotY = cos(time * artifact.rotYSpeed + artifact.phaseOffset) * 15f
    val scrollTwist = currentScroll * 0.04f * artifact.depth
    val rotZ = artifact.baseRotation + (time * artifact.rotationSpeed * 200f) + scrollTwist
    val scale = 1f + (sin(time * 0.03f + artifact.phaseOffset) * 0.06f)

    Box(
        modifier = Modifier
            .size((artifact.size * 1.5f).dp) // Increased box size to give room for blur
            .graphicsLayer {
                if (viewportWidth > 0 && viewportHeight > 0) {
                    // X Positioning
                    translationX = (artifact.x / 100f * viewportWidth) + (driftX * density.density) - (size.width / 2f)
                    
                    // Vertical Wrapping Logic
                    val wrapRange = viewportHeight * 1.5f
                    val wrapOffset = viewportHeight * 0.25f
                    val baseYPx = (artifact.y / 100f) * viewportHeight
                    val totalY = baseYPx + parallaxY + (driftY * density.density)
                    translationY = ((((totalY + wrapOffset) % wrapRange) + wrapRange) % wrapRange) - wrapOffset
                }
                
                // 3D Transforms
                rotationX = rotX
                rotationY = rotY
                rotationZ = rotZ
                scaleX = scale
                scaleY = scale
                cameraDistance = 12f * density.density
            }
            .zIndex(artifact.zIndex.toFloat())
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(artifact.blur.dp)
        ) {
            // Use 80% of canvas to leave room for blur and avoid "square" clipping
            val drawArea = size.width * 0.8f
            val offset = (size.width - drawArea) / 2f
            
            withTransform({
                translate(offset, offset)
            }) {
                when (artifact.type) {
                    0 -> drawPokerCard(drawArea)
                    1 -> drawPokerChip(drawArea)
                    2 -> drawPokerMask(drawArea)
                    3 -> drawPokerDice(drawArea)
                }
            }
        }
    }
}

private fun DrawScope.drawPokerCard(canvasWidth: Float) {
    // Reference viewBox: 0 0 100 160
    val s = canvasWidth / 160f // Base on height to fit card in square area
    val cardW = 100f * s
    val cardH = 160f * s
    val centerX = canvasWidth / 2f
    val centerY = canvasWidth / 2f
    val topLeftX = centerX - (cardW / 2f)
    val topLeftY = centerY - (cardH / 2f)
    
    val rect = Rect(topLeftX, topLeftY, topLeftX + cardW, topLeftY + cardH)
    
    // Card Base
    drawRoundRect(
        brush = Brush.radialGradient(
            0.6f to Color(0xFF151518),
            1.0f to Color(0xFF050505),
            center = rect.center, 
            radius = 50f * s
        ),
        size = Size(90f * s, 150f * s),
        topLeft = Offset(topLeftX + 5f * s, topLeftY + 5f * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * s)
    )
    drawRoundRect(
        brush = Brush.linearGradient(GoldGrad),
        size = Size(90f * s, 150f * s),
        topLeft = Offset(topLeftX + 5f * s, topLeftY + 5f * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * s),
        style = Stroke(width = 2f * s)
    )

    // Inner Border
    drawRoundRect(
        brush = Brush.linearGradient(GoldGrad),
        topLeft = Offset(topLeftX + 12f * s, topLeftY + 12f * s),
        size = Size(76f * s, 136f * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s),
        style = Stroke(width = 1f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 4f * s))),
        alpha = 0.7f
    )

    // Mystical Centerpiece
    withTransform({
        translate(centerX, centerY)
    }) {
        // Hexagon
        val hexPath = Path().apply {
            moveTo(0f, -35f * s)
            lineTo(30.3f * s, -17.5f * s)
            lineTo(30.3f * s, 17.5f * s)
            lineTo(0f, 35f * s)
            lineTo(-30.3f * s, 17.5f * s)
            lineTo(-30.3f * s, -17.5f * s)
            close()
        }
        drawPath(hexPath, brush = Brush.linearGradient(GoldGrad), style = Stroke(width = 1.5f * s))

        // Occult Crimson Star
        val starPath = Path().apply {
            moveTo(0f, -45f * s)
            lineTo(15f * s, -15f * s)
            lineTo(45f * s, 0f)
            lineTo(15f * s, 15f * s)
            lineTo(0f, 45f * s)
            lineTo(-15f * s, 15f * s)
            lineTo(-45f * s, 0f)
            lineTo(-15f * s, -15f * s)
            close()
        }
        drawPath(starPath, Color(0xFF8B0018), alpha = 0.6f, style = Stroke(width = 1f * s))

        // Inner Eye
        val eyePath = Path().apply {
            moveTo(-20f * s, 0f)
            quadraticTo(0f, -20f * s, 20f * s, 0f)
            quadraticTo(0f, 20f * s, -20f * s, 0f)
            close()
        }
        drawPath(eyePath, brush = Brush.verticalGradient(CrimsonGrad))
        drawPath(eyePath, brush = Brush.linearGradient(GoldGrad), style = Stroke(width = 1.5f * s))

        drawCircle(Color(0xFF151518), 6f * s, Offset.Zero)
        drawCircle(brush = Brush.linearGradient(GoldGrad), radius = 6f * s, center = Offset.Zero, style = Stroke(width = 1f * s))
        drawCircle(Color(0xFFE8C988), 2f * s, Offset.Zero)
    }

    // Corner Ornaments
    drawPath(Path().apply { moveTo(topLeftX + 12f * s, topLeftY + 12f * s); lineTo(topLeftX + 22f * s, topLeftY + 12f * s); lineTo(topLeftX + 12f * s, topLeftY + 22f * s); close() }, brush = Brush.linearGradient(GoldGrad))
    drawPath(Path().apply { moveTo(topLeftX + 88f * s, topLeftY + 12f * s); lineTo(topLeftX + 78f * s, topLeftY + 12f * s); lineTo(topLeftX + 88f * s, topLeftY + 22f * s); close() }, brush = Brush.linearGradient(GoldGrad))
    drawPath(Path().apply { moveTo(topLeftX + 12f * s, topLeftY + 148f * s); lineTo(topLeftX + 22f * s, topLeftY + 148f * s); lineTo(topLeftX + 12f * s, topLeftY + 138f * s); close() }, brush = Brush.linearGradient(GoldGrad))
    drawPath(Path().apply { moveTo(topLeftX + 88f * s, topLeftY + 148f * s); lineTo(topLeftX + 78f * s, topLeftY + 148f * s); lineTo(topLeftX + 88f * s, topLeftY + 138f * s); close() }, brush = Brush.linearGradient(GoldGrad))
}

private fun DrawScope.drawPokerChip(canvasWidth: Float) {
    // Reference viewBox: 0 0 100 100
    val s = canvasWidth / 100f
    val center = Offset(canvasWidth / 2f, canvasWidth / 2f)

    // Outer Rim
    drawCircle(Color(0xFF0A0A0C), 48f * s, center)
    drawCircle(brush = Brush.linearGradient(GoldGrad), radius = 48f * s, center = center, style = Stroke(width = 4f * s))

    // Edge Details
    drawCircle(
        brush = Brush.linearGradient(GoldGrad),
        radius = 45f * s,
        center = center,
        style = Stroke(width = 6f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * s, 12f * s)))
    )

    drawCircle(Color(0xFF8B0018), 38f * s, center, style = Stroke(width = 2f * s))

    // Inner Core
    drawCircle(brush = Brush.radialGradient(0.6f to Color(0xFF151518), 1.0f to Color(0xFF050505), center = center, radius = 32f * s), radius = 32f * s, center = center)
    drawCircle(brush = Brush.linearGradient(GoldGrad), radius = 32f * s, center = center, style = Stroke(width = 1.5f * s))

    // Center Star
    val starPath = Path().apply {
        moveTo(center.x, center.y - 32f * s)
        lineTo(center.x + 4f * s, center.y - 4f * s)
        lineTo(center.x + 32f * s, center.y)
        lineTo(center.x + 4f * s, center.y + 4f * s)
        lineTo(center.x, center.y + 32f * s)
        lineTo(center.x - 4f * s, center.y + 4f * s)
        lineTo(center.x - 32f * s, center.y)
        lineTo(center.x - 4f * s, center.y - 4f * s)
        close()
    }
    drawPath(starPath, brush = Brush.verticalGradient(CrimsonGrad))
    drawPath(starPath, brush = Brush.linearGradient(GoldGrad), style = Stroke(width = 1f * s))

    drawCircle(Color(0xFF0A0A0C), 10f * s, center)
    drawCircle(brush = Brush.linearGradient(GoldGrad), radius = 10f * s, center = center, style = Stroke(width = 2f * s))
    drawCircle(Color(0xFFE8C988), 4f * s, center)
}

private fun DrawScope.drawPokerMask(canvasWidth: Float) {
    // Reference viewBox: 0 0 100 120
    val s = canvasWidth / 120f
    val centerX = canvasWidth / 2f
    val maskW = 100f * s
    val maskH = 120f * s
    val topLeftX = centerX - (maskW / 2f)

    // Base Mask Split
    val leftHalf = Path().apply {
        moveTo(topLeftX + 50f * s, 10f * s)
        cubicTo(topLeftX + 20f * s, 10f * s, topLeftX + 5f * s, 35f * s, topLeftX + 10f * s, 65f * s)
        cubicTo(topLeftX + 15f * s, 95f * s, topLeftX + 35f * s, 110f * s, topLeftX + 50f * s, 110f * s)
    }
    val rightHalf = Path().apply {
        moveTo(topLeftX + 50f * s, 10f * s)
        cubicTo(topLeftX + 80f * s, 10f * s, topLeftX + 95f * s, 35f * s, topLeftX + 90f * s, 65f * s)
        cubicTo(topLeftX + 85f * s, 95f * s, topLeftX + 65f * s, 110f * s, topLeftX + 50f * s, 110f * s)
    }

    drawPath(leftHalf, Color(0xFFE5E5E5))
    drawPath(leftHalf, Color(0xFF151518), style = Stroke(width = 2f * s))

    drawPath(rightHalf, brush = Brush.radialGradient(0.6f to Color(0xFF151518), 1.0f to Color(0xFF050505), center = Offset(topLeftX + 50f * s, 60f * s), radius = 50f * s))
    drawPath(rightHalf, Color(0xFF050505), style = Stroke(width = 2f * s))
    
    // Middle Split Dash
    drawLine(Color(0xFF151518), Offset(topLeftX + 50f * s, 10f * s), Offset(topLeftX + 50f * s, 110f * s), strokeWidth = 1.5f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * s, 3f * s)), alpha = 0.8f)

    // Eyes
    val leftEye = Path().apply {
        moveTo(topLeftX + 20f * s, 45f * s)
        cubicTo(topLeftX + 30f * s, 40f * s, topLeftX + 40f * s, 40f * s, topLeftX + 45f * s, 48f * s)
        cubicTo(topLeftX + 38f * s, 52f * s, topLeftX + 28f * s, 52f * s, topLeftX + 20f * s, 45f * s)
        close()
    }
    val rightEye = Path().apply {
        moveTo(topLeftX + 80f * s, 45f * s)
        cubicTo(topLeftX + 70f * s, 40f * s, topLeftX + 60f * s, 40f * s, topLeftX + 55f * s, 48f * s)
        cubicTo(topLeftX + 62f * s, 52f * s, topLeftX + 72f * s, 52f * s, topLeftX + 80f * s, 45f * s)
        close()
    }

    drawPath(leftEye, Color(0xFF050505))
    drawPath(rightEye, brush = Brush.verticalGradient(CrimsonGrad))

    // Blood Tear
    val tear = Path().apply {
        moveTo(topLeftX + 32f * s, 55f * s)
        cubicTo(topLeftX + 36f * s, 65f * s, topLeftX + 34f * s, 75f * s, topLeftX + 30f * s, 75f * s)
        cubicTo(topLeftX + 26f * s, 75f * s, topLeftX + 24f * s, 65f * s, topLeftX + 32f * s, 55f * s)
        close()
    }
    drawPath(tear, brush = Brush.verticalGradient(CrimsonGrad))
    
    // Cheekbones
    drawPath(Path().apply { moveTo(topLeftX + 15f * s, 75f * s); cubicTo(topLeftX + 25f * s, 80f * s, topLeftX + 35f * s, 85f * s, topLeftX + 50f * s, 90f * s) }, Color(0xFF151518), alpha = 0.8f, style = Stroke(width = 2.5f * s, cap = StrokeCap.Round))
    drawPath(Path().apply { moveTo(topLeftX + 85f * s, 75f * s); cubicTo(topLeftX + 75f * s, 80f * s, topLeftX + 65f * s, 85f * s, topLeftX + 50f * s, 90f * s) }, Color(0xFF050505), alpha = 0.8f, style = Stroke(width = 2.5f * s, cap = StrokeCap.Round))
}

private fun DrawScope.drawPokerDice(canvasWidth: Float) {
    // Reference viewBox: 0 0 100 110
    val s = canvasWidth / 110f
    val centerX = canvasWidth / 2f
    val diceW = 100f * s
    val topLeftX = centerX - (diceW / 2f)
    
    // Top Face
    val topFace = Path().apply {
        moveTo(topLeftX + 50f * s, 15f * s)
        lineTo(topLeftX + 85f * s, 35f * s)
        lineTo(topLeftX + 50f * s, 55f * s)
        lineTo(topLeftX + 15f * s, 35f * s)
        close()
    }
    drawPath(topFace, brush = Brush.radialGradient(0.6f to Color(0xFF151518), 1.0f to Color(0xFF050505), center = Offset(topLeftX + 50f * s, 35f * s), radius = 35f * s))
    drawPath(topFace, brush = Brush.linearGradient(GoldGrad), style = Stroke(width = 2f * s, join = StrokeJoin.Round))

    // Left Face
    val leftFace = Path().apply {
        moveTo(topLeftX + 15f * s, 35f * s)
        lineTo(topLeftX + 50f * s, 55f * s)
        lineTo(topLeftX + 50f * s, 95f * s)
        lineTo(topLeftX + 15f * s, 75f * s)
        close()
    }
    drawPath(leftFace, Color(0xFF050505))
    drawPath(leftFace, brush = Brush.linearGradient(GoldGrad), style = Stroke(width = 2f * s, join = StrokeJoin.Round))

    // Right Face
    val rightFace = Path().apply {
        moveTo(topLeftX + 50f * s, 55f * s)
        lineTo(topLeftX + 85f * s, 35f * s)
        lineTo(topLeftX + 85f * s, 75f * s)
        lineTo(topLeftX + 50f * s, 95f * s)
        close()
    }
    drawPath(rightFace, Color(0xFF0A0A0C))
    drawPath(rightFace, brush = Brush.linearGradient(GoldGrad), style = Stroke(width = 2f * s, join = StrokeJoin.Round))

    // Top Face Pip (1)
    drawCircle(brush = Brush.verticalGradient(CrimsonGrad), radius = 5f * s, center = Offset(topLeftX + 50f * s, 35f * s))
    
    // Occult lines on top
    drawLine(brush = Brush.linearGradient(GoldGrad), start = Offset(topLeftX + 30f * s, 25f * s), end = Offset(topLeftX + 70f * s, 45f * s), strokeWidth = 0.5f * s, alpha = 0.4f)
    drawLine(brush = Brush.linearGradient(GoldGrad), start = Offset(topLeftX + 70f * s, 25f * s), end = Offset(topLeftX + 30f * s, 45f * s), strokeWidth = 0.5f * s, alpha = 0.4f)

    // Left Face Pips (2)
    drawCircle(brush = Brush.linearGradient(GoldGrad), radius = 3.5f * s, center = Offset(topLeftX + 32f * s, 55f * s))
    drawCircle(brush = Brush.linearGradient(GoldGrad), radius = 3.5f * s, center = Offset(topLeftX + 32f * s, 75f * s))

    // Right Face Pips (3)
    drawCircle(brush = Brush.linearGradient(GoldGrad), radius = 3.5f * s, center = Offset(topLeftX + 68f * s, 50f * s))
    drawCircle(brush = Brush.linearGradient(GoldGrad), radius = 3.5f * s, center = Offset(topLeftX + 68f * s, 65f * s))
    drawCircle(brush = Brush.linearGradient(GoldGrad), radius = 3.5f * s, center = Offset(topLeftX + 68f * s, 80f * s))
}

