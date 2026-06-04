package com.ballade.hwaran.ui.screens

import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Stable
class LiquidPointerState {
    var x by mutableFloatStateOf(500f)
    var y by mutableFloatStateOf(1000f)
    var isPressed by mutableStateOf(false)
    var explosionTrigger by mutableIntStateOf(0)

    fun triggerExplosion() {
        explosionTrigger++
    }
}

private class LiquidInternalState(val nodeCount: Int) {
    val nodes = Array(nodeCount) {
        LiquidNodeInternal(
            mass = 0.8f + Random.nextFloat() * 0.5f,
            friction = 0.85f + Random.nextFloat() * 0.05f,
            elasticity = 0.02f + Random.nextFloat() * 0.03f,
            size = 35f + Random.nextFloat() * 45f,
            orbitOffset = Random.nextFloat() * Math.PI.toFloat() * 2f
        )
    }
    var pointerX = 0f
    var pointerY = 0f
    var time = 0f
}

private class LiquidNodeInternal(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val mass: Float,
    val friction: Float,
    val elasticity: Float,
    val size: Float,
    val orbitOffset: Float
)

private data class LiquidStarInternal(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseOpacity: Float,
    val isTwinkling: Boolean,
    val twinkleDuration: Float,
    val twinkleDelay: Float
)

@Composable
fun LiquidBackground(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    pointerState: LiquidPointerState = remember { LiquidPointerState() }
) {
    if (!isEnabled) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0B1A)))
        return
    }

    // Restored to original React specs: 8 nodes, 150 stars
    val nodeCount = 8
    val starCount = 150
    
    val colors = remember {
        listOf(
            Color(0xFFF9A8D4), // Pastel Pink
            Color(0xFFC084FC), // Pastel Purple
            Color(0xFF67E8F9), // Aqua/Cyan
            Color(0xFFD946EF), // Neon Magenta
            Color(0xFF8B5CF6), // Deep Purple
            Color(0xFF2DD4BF)  // Teal
        )
    }

    val internalState = remember { LiquidInternalState(nodeCount) }
    val stars = remember {
        Array(starCount) {
            LiquidStarInternal(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 2f + 0.5f,
                baseOpacity = 0.1f + Random.nextFloat() * 0.5f,
                isTwinkling = Random.nextFloat() > 0.8f,
                twinkleDuration = 1.5f + Random.nextFloat() * 3f,
                twinkleDelay = Random.nextFloat() * 5f
            )
        }
    }

    var drawTrigger by remember { mutableIntStateOf(0) }

    // Re-enabled Explosive shockwave logic (Only on tap)
    LaunchedEffect(pointerState.explosionTrigger) {
        if (pointerState.explosionTrigger > 0) {
            internalState.nodes.forEach { node ->
                val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
                val force = 50f + Random.nextFloat() * 40f
                node.vx += cos(angle) * force
                node.vy += sin(angle) * force
            }
        }
    }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (isActive) {
            withInfiniteAnimationFrameMillis { frameTime ->
                if (lastTime == 0L) {
                    lastTime = frameTime
                    return@withInfiniteAnimationFrameMillis
                }
                lastTime = frameTime

                internalState.time += 0.05f
                val time = internalState.time

                // Smooth pointer tracking (Spring physics on the cursor itself)
                internalState.pointerX += (pointerState.x - internalState.pointerX) * 0.15f
                internalState.pointerY += (pointerState.y - internalState.pointerY) * 0.15f
                
                val px = internalState.pointerX
                val py = internalState.pointerY
                val isPressed = pointerState.isPressed

                internalState.nodes.forEach { node ->
                    // Exact physics from React code
                    val orbitRadius = if (isPressed) 80f else 15f + sin(time + node.orbitOffset) * 15f
                    val tx = px + cos(time * 0.5f + node.orbitOffset) * orbitRadius
                    val ty = py + sin(time * 0.5f + node.orbitOffset) * orbitRadius

                    val ax = (tx - node.x) * node.elasticity
                    val ay = (ty - node.y) * node.elasticity

                    node.vx += ax / node.mass
                    node.vy += ay / node.mass
                    node.vx *= node.friction
                    node.vy *= node.friction

                    node.x += node.vx
                    node.y += node.vy
                }
                
                drawTrigger++
            }
        }
    }

    // Restored exact Gooey Filter parameters (15f blur) for the authentic look
    val liquidEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = android.graphics.RenderEffect.createBlurEffect(15f, 15f, android.graphics.Shader.TileMode.CLAMP)
            val matrix = android.graphics.ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 30f, -2550f
                )
            )
            val filter = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(matrix))
            android.graphics.RenderEffect.createChainEffect(filter, blur).asComposeRenderEffect()
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0B1A), Color(0xFF160B24), Color(0xFF0A1118))))
            .drawBehind {
                drawTrigger
                val cw = size.width
                val ch = size.height
                val time = internalState.time

                // Background Nebula Glow
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD946EF).copy(alpha = 0.15f), Color.Transparent),
                        center = center,
                        radius = maxOf(cw, ch) * 0.7f
                    ),
                    blendMode = BlendMode.Screen
                )

                stars.forEach { star ->
                    var opacity = star.baseOpacity
                    var scale = 1f
                    if (star.isTwinkling) {
                        val phase = ((time - star.twinkleDelay) / star.twinkleDuration) % 1f
                        if (phase > 0) {
                            opacity = if (phase < 0.5f) 0.1f + 0.9f * (phase * 2f) else 1f - 0.9f * ((phase - 0.5f) * 2f)
                            scale = if (phase < 0.5f) 0.8f + 0.6f * (phase * 2f) else 1.4f - 0.6f * ((phase - 0.5f) * 2f)
                        }
                    }
                    drawCircle(
                        color = Color.White,
                        radius = star.size * scale,
                        center = Offset(star.x * cw, star.y * ch),
                        alpha = opacity
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.renderEffect = liquidEffect
                    this.clip = false
                }
                .drawBehind {
                    drawTrigger
                    val time = internalState.time
                    val isPressed = pointerState.isPressed
                    
                    // Central Tracking Core
                    val coreScale = if (isPressed) 1.4f else 1f + sin(time * 2f) * 0.05f
                    drawCircle(
                        color = Color(0xFF0EA5E9),
                        radius = 30f * coreScale,
                        center = Offset(internalState.pointerX, internalState.pointerY)
                    )

                    // Satellite Nodes
                    internalState.nodes.forEachIndexed { i, node ->
                        val nodeScale = if (isPressed) 0.6f else 1f
                        drawCircle(
                            color = colors[i % colors.size],
                            radius = (node.size / 2f) * nodeScale,
                            center = Offset(node.x, node.y)
                        )
                    }
                }
        )
    }
}
