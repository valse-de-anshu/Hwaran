package com.ballade.hwaran.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

@Composable
fun FlowerBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1.0f,
    isEnabled: Boolean = true,
    scrollOffset: Float = 0f
) {
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }

    var time by remember { mutableFloatStateOf(0f) }
    var currentScroll by remember { mutableFloatStateOf(0f) }
    val targetScrollOffset by rememberUpdatedState(scrollOffset)

    LaunchedEffect(isEnabled, animationSpeed) {
        if (!isEnabled) return@LaunchedEffect
        while (isActive) {
            time += 0.015f * animationSpeed
            // Smoothly interpolate currentScroll towards targetScrollOffset for elastic feel
            currentScroll += (targetScrollOffset - currentScroll) * 0.12f
            delay(16)
        }
    }

    val spores = remember { generateSpores(60) }
    
    // Reusable Paths
    val floraPath = remember { 
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(20f, -50f, 60f, -100f, 10f, -180f)
        }
    }
    val floraPath2 = remember {
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(10f, -40f, 40f, -60f, 50f, -120f)
        }
    }
    val leafPath = remember {
        Path().apply {
            moveTo(23f, -62f)
            quadraticTo(40f, -60f, 35f, -75f)
            quadraticTo(20f, -70f, 23f, -62f)
            close()
        }
    }
    val leafPath2 = remember {
        Path().apply {
            moveTo(38f, -95f)
            quadraticTo(55f, -90f, 50f, -105f)
            quadraticTo(35f, -100f, 38f, -95f)
            close()
        }
    }
    val petalPath = remember {
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(30f, -40f, 60f, -80f, 0f, -150f)
            cubicTo(-20f, -80f, -10f, -40f, 0f, 0f)
            close()
        }
    }
    val petalVein1 = remember {
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(10f, -40f, 15f, -80f, 0f, -130f)
        }
    }
    val petalVein2 = remember {
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(-5f, -30f, -5f, -60f, -10f, -90f)
        }
    }
    val wingPath = remember {
        Path().apply {
            moveTo(0f, -20f)
            cubicTo(30f, -30f, 80f, -10f, 90f, -60f)
            cubicTo(80f, -90f, 40f, -80f, 0f, -20f)
            close()
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!isEnabled) return@Canvas
        width = size.width
        height = size.height
        val centerX = width / 2f
        val centerY = height / 2f

        // 1. Background Gradient
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF0B0B1A), Color(0xFF160B24), Color(0xFF0A1118))
            )
        )

        // 2. Dynamic Nebula Bloom
        val bloomPulse = sin((currentScroll % 1500f) / 1500f * PI.toFloat() * 2f)
        val bloomOpacity = max(0f, bloomPulse) * 0.5f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFD946EF).copy(alpha = bloomOpacity * 0.25f), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = max(width, height) * 0.65f
            )
        )

        // 3. Kaleidoscope Layers
        val scrollRot1 = currentScroll * 0.25f
        val scrollRot2 = currentScroll * -0.40f
        val scrollRot3 = currentScroll * 0.65f

        drawIntoCanvas { canvas ->
            // Outer Ring: Flora
            canvas.save()
            canvas.translate(centerX, centerY)
            val scale1 = 1f + (bloomPulse * 0.05f)
            canvas.rotate(time * 65f + scrollRot1)
            canvas.scale(scale1, scale1)
            drawKaleidoscopeLayer(canvas, 12) {
                drawPath(floraPath, Color(0xFF2DD4BF), alpha = 0.7f, style = Stroke(width = 1.5f))
                drawPath(floraPath2, Color(0xFF0EA5E9), alpha = 0.5f, style = Stroke(width = 0.5f))
                drawPath(leafPath, Color(0xFF2DD4BF), alpha = 0.6f)
                drawPath(leafPath2, Color(0xFF0EA5E9), alpha = 0.4f)
                drawCircle(Color.White, radius = 1.5f, center = Offset(20f, -140f))
                drawCircle(Color.White.copy(alpha = 0.6f), radius = 1f, center = Offset(45f, -80f))
            }
            canvas.restore()

            // Middle Ring: Orchid
            canvas.save()
            canvas.translate(centerX, centerY)
            val scale2 = 0.9f - (bloomPulse * 0.1f)
            canvas.rotate(time * -45f + scrollRot2)
            canvas.scale(scale2, scale2)
            drawKaleidoscopeLayer(canvas, 8) {
                val petalGrad = Brush.linearGradient(
                    0.0f to Color(0xFFD946EF).copy(alpha = 0.8f),
                    0.5f to Color(0xFF8B5CF6).copy(alpha = 0.4f),
                    1.0f to Color(0xFF0EA5E9).copy(alpha = 0f),
                    start = Offset(0f, 0f),
                    end = Offset(0f, -150f)
                )
                drawPath(petalPath, brush = petalGrad)
                drawPath(petalPath, Color(0xFFD946EF), alpha = 0.8f, style = Stroke(width = 1f))
                drawPath(petalVein1, Color(0xFFF0ABFC), alpha = 0.6f, style = Stroke(width = 0.5f))
                drawPath(petalVein2, Color(0xFFF0ABFC), alpha = 0.4f, style = Stroke(width = 0.3f))
            }
            canvas.restore()

            // Inner Ring: Butterflies
            canvas.save()
            canvas.translate(centerX, centerY)
            val scale3 = 0.6f + (bloomPulse * 0.15f)
            canvas.rotate(time * 95f + scrollRot3)
            canvas.scale(scale3, scale3)
            drawKaleidoscopeLayer(canvas, 12) {
                drawPath(wingPath, Color(0xFF38BDF8), alpha = 0.9f, style = Stroke(width = 1.2f))
                drawCircle(Color(0xFFE0F2FE), radius = 4f, center = Offset(50f, -45f))
                drawCircle(Color(0xFFE0F2FE).copy(alpha = 0.6f), radius = 2f, center = Offset(70f, -35f))
                val wingVein = Path().apply { moveTo(20f, -30f); quadraticTo(40f, -45f, 60f, -35f) }
                drawPath(wingVein, Color(0xFFBAE6FD), alpha = 0.5f, style = Stroke(width = 0.5f))
            }
            drawCircle(Color.White, radius = 8f)
            canvas.restore()
        }

        // 4. Floating Particles
        spores.forEach { spore ->
            val driftX = time * spore.driftSpeedX * 1000f + sin(time + spore.phase) * 15f
            val driftY = time * spore.driftSpeedY * 1000f + cos(time + spore.phase) * 15f
            val parallaxY = -currentScroll * spore.depth
            
            val totalX = (spore.x * width) + driftX
            val totalY = (spore.y * height) + parallaxY + driftY
            
            val wrappedX = ((totalX % (width * 1.3f)) + (width * 1.3f)) % (width * 1.3f) - (width * 0.15f)
            val wrappedY = ((totalY % (height * 1.3f)) + (height * 1.3f)) % (height * 1.3f) - (height * 0.15f)
            
            val opacity = 0.3f + sin(time * 2f + spore.phase).pow(2) * 0.7f
            
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(wrappedX, wrappedY)
                canvas.scale(spore.scale, spore.scale)
                
                when (spore.type) {
                    "star" -> {
                        val path = Path().apply {
                            moveTo(0f, -15f)
                            quadraticTo(0f, 0f, 15f, 0f)
                            quadraticTo(0f, 0f, 0f, 15f)
                            quadraticTo(0f, 0f, -15f, 0f)
                            quadraticTo(0f, 0f, 0f, -15f)
                        }
                        drawPath(path, Color.White, alpha = opacity * 0.9f)
                    }
                    "spore" -> {
                        drawCircle(Color(0xFFF0ABFC), radius = 3f, alpha = opacity * 0.8f)
                    }
                    "leaf" -> {
                        val path = Path().apply {
                            moveTo(0f, -10f)
                            cubicTo(10f, -10f, 10f, 10f, 0f, 10f)
                            cubicTo(-10f, 10f, -10f, -10f, 0f, -10f)
                            close()
                        }
                        drawPath(path, Color(0xFF2DD4BF), alpha = opacity * 0.6f)
                    }
                }
                canvas.restore()
            }
        }
    }
}

private fun DrawScope.drawKaleidoscopeLayer(canvas: androidx.compose.ui.graphics.Canvas, slices: Int, content: DrawScope.() -> Unit) {
    for (i in 0 until slices) {
        val rotation = i * (360f / slices)
        val isMirrored = i % 2 != 0
        canvas.save()
        canvas.rotate(rotation)
        if (isMirrored) {
            canvas.scale(-1f, 1f)
        }
        content()
        canvas.restore()
    }
}

private data class Spore(
    val id: Int,
    val type: String,
    val x: Float,
    val y: Float,
    val depth: Float,
    val scale: Float,
    val driftSpeedX: Float,
    val driftSpeedY: Float,
    val phase: Float
)

private fun generateSpores(count: Int): List<Spore> {
    val types = listOf("star", "spore", "leaf", "star", "spore")
    return List(count) { i ->
        Spore(
            id = i,
            type = types[i % types.size],
            x = Random.nextFloat(),
            y = Random.nextFloat(),
            depth = 0.3f + Random.nextFloat() * 1.5f,
            scale = 0.15f + Random.nextFloat() * 0.4f,
            driftSpeedX = (Random.nextFloat() - 0.5f) * 0.003f,
            driftSpeedY = (Random.nextFloat() - 0.5f) * 0.003f,
            phase = Random.nextFloat() * PI.toFloat() * 2f
        )
    }
}
