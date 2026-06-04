package com.ballade.hwaran.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val PrimaryPurple = Color(0xFFC3A6FE)
private val DeepPurple = Color(0xFF6B4FA9)
private val LightCyan = Color(0xFFFFFFFF)

class Bubble(
    var x: Float,
    var y: Float,
    var r: Float,
    var speed: Float,
    var alpha: Float,
    var baseAlpha: Float,
    var phase: Float,
    var phaseSpeed: Float,
    var twinkleSpeed: Float,
    var twinklePhase: Float
)

class Jellyfish(
    var x: Float,
    var y: Float,
    var r: Float,
    var speed: Float,
    var phase: Float,
    var phaseSpeed: Float,
    var driftX: Float,
    val tentacles: List<Tentacle>
)

class Tentacle(
    val length: Float,
    val thickness: Float,
    val angleOffset: Float,
    val xOffsetMult: Float,
    var currentAngle: Float = 0f
)

@Composable
fun JellyfishBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1.0f,
    isEnabled: Boolean = true
) {
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }

    val bubbles = remember { mutableListOf<Bubble>() }
    val jellyfishList = remember { mutableListOf<Jellyfish>() }

    fun spawnBubble(w: Float, h: Float, offscreen: Boolean = false): Bubble {
        val baseAlpha = 0.15f + Random.nextFloat() * 0.45f
        return Bubble(
            x = Random.nextFloat() * w,
            y = if (offscreen) h + 50f else Random.nextFloat() * h,
            r = 0.8f + Random.nextFloat() * 2.5f,
            speed = 0.28f + Random.nextFloat() * 0.55f, 
            alpha = baseAlpha,
            baseAlpha = baseAlpha,
            phase = Random.nextFloat() * Math.PI.toFloat() * 2f,
            phaseSpeed = 0.045f,
            twinkleSpeed = 0.03f + Random.nextFloat() * 0.07f,
            twinklePhase = Random.nextFloat() * Math.PI.toFloat() * 2f
        )
    }

    fun spawnJellyfish(w: Float, h: Float, offscreen: Boolean = false): Jellyfish {
        val r = 5f + Random.nextFloat() * 7f
        val numTentacles = 6 + Random.nextInt(5)
        val tentacles = List(numTentacles) { index ->
            val ratio = (index.toFloat() / (numTentacles - 1).coerceAtLeast(1)) - 0.5f
            Tentacle(
                length = r * (2.2f + 0.9f * sin(Random.nextFloat() * Math.PI.toFloat())),
                thickness = 0.4f + Random.nextFloat() * 0.2f,
                angleOffset = (Random.nextFloat() - 0.5f) * 0.5f,
                xOffsetMult = ratio
            )
        }
        return Jellyfish(
            x = Random.nextFloat() * w,
            y = if (offscreen) h + 150f else Random.nextFloat() * h,
            r = r,
            speed = 0.18f + Random.nextFloat() * 0.22f,
            phase = Random.nextFloat() * Math.PI.toFloat() * 2f,
            phaseSpeed = 0.055f,
            driftX = (Random.nextFloat() - 0.5f) * 0.35f,
            tentacles = tentacles
        )
    }

    LaunchedEffect(width, height) {
        if (width == 0f || height == 0f) return@LaunchedEffect
        
        val newBubbles = mutableListOf<Bubble>()
        repeat(60) { newBubbles.add(spawnBubble(width, height)) }
        bubbles.clear()
        bubbles.addAll(newBubbles)

        val newJellys = mutableListOf<Jellyfish>()
        repeat(7) { newJellys.add(spawnJellyfish(width, height)) }
        jellyfishList.clear()
        jellyfishList.addAll(newJellys)
    }

    val trigger = remember { mutableStateOf(0) }

    LaunchedEffect(animationSpeed, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        val delayTime = 16L
        while (isActive) {
            val baseSpeed = animationSpeed * 0.8f

            for (i in bubbles.indices) {
                val b = bubbles[i]
                b.y -= b.speed * baseSpeed
                b.phase += b.phaseSpeed * baseSpeed
                b.twinklePhase += b.twinkleSpeed * baseSpeed
                b.x += sin(b.phase) * 0.5f * baseSpeed
                
                val opacityOsc = (sin(b.twinklePhase) + 1f) / 2f
                b.alpha = b.baseAlpha * (0.2f + 0.8f * opacityOsc)

                if (b.y < -50f) {
                    bubbles[i] = spawnBubble(width, height, true)
                }
            }

            for (i in jellyfishList.indices) {
                val j = jellyfishList[i]
                j.phase += j.phaseSpeed * baseSpeed
                j.y -= j.speed * (0.65f + 0.35f * sin(j.phase)) * baseSpeed
                j.x += j.driftX * sin(j.phase * 0.6f) * baseSpeed

                for (t in j.tentacles) {
                    t.currentAngle = sin(j.phase * 0.8f + t.angleOffset) * 0.2f
                }

                if (j.y < -150f) {
                    jellyfishList[i] = spawnJellyfish(width, height, true)
                }
            }

            trigger.value++
            delay(delayTime)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!isEnabled && bubbles.isEmpty()) return@Canvas
        
        val _unused = trigger.value
        width = size.width
        height = size.height

        for (b in bubbles) {
            val pulse = 0.88f + 0.12f * sin(b.phase)
            val pulsatingR = b.r * pulse
            val currentAlpha = b.alpha.coerceIn(0f, 1f)
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = currentAlpha),
                        Color.White.copy(alpha = currentAlpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(b.x, b.y),
                    radius = pulsatingR * 4.5f
                ),
                radius = pulsatingR * 4.5f,
                center = Offset(b.x, b.y),
                blendMode = BlendMode.Plus
            )

            drawCircle(
                color = Color.White.copy(alpha = currentAlpha * 0.8f),
                radius = pulsatingR,
                center = Offset(b.x, b.y),
                blendMode = BlendMode.Plus
            )
        }

        for (j in jellyfishList) {
            val pulsate = 0.90f + 0.10f * sin(j.phase)
            val curR = j.r * pulsate

            val haloRadius = curR * 4.5f
            if (haloRadius > 0.1f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(j.x, j.y),
                        radius = haloRadius
                    ),
                    radius = haloRadius,
                    center = Offset(j.x, j.y)
                )
            }

            val bellPath = Path().apply {
                val top = j.y - curR
                val bottom = j.y
                val left = j.x - curR
                val right = j.x + curR
                
                moveTo(left, bottom)
                arcTo(androidx.compose.ui.geometry.Rect(left, top, right, bottom + curR), 180f, 180f, false)
                quadraticTo(j.x, bottom + curR * 0.22f, left, bottom)
                close()
            }

            drawPath(
                path = bellPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(j.x, j.y - curR * 0.35f),
                    radius = curR.coerceAtLeast(0.1f)
                )
            )

            drawPath(
                path = bellPath,
                color = Color.White.copy(alpha = 0.8f * (0.6f + 0.4f * sin(j.phase))),
                style = Stroke(width = 1.0f)
            )
            
            val specR = curR * 0.28f
            if (specR > 0.1f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f), Color.Transparent),
                        center = Offset(j.x - curR * 0.28f, j.y - curR * 0.32f),
                        radius = specR
                    ),
                    radius = specR,
                    center = Offset(j.x - curR * 0.28f, j.y - curR * 0.32f)
                )
            }

            for ((tIndex, t) in j.tentacles.withIndex()) {
                val startX = j.x + t.xOffsetMult * curR * 1.5f
                val startY = j.y + curR * 0.22f
                
                val tLen = t.length + curR * 0.4f * sin(j.phase * 1.2f + tIndex * 0.7f)
                val tWave = curR * 0.35f * sin(j.phase * 1.8f + tIndex * 1.05f)
                val tAlpha = (0.2f + 0.25f * sin(j.phase * 1.2f + tIndex.toFloat())).coerceIn(0f, 0.5f)

                val path = Path().apply {
                    moveTo(startX, startY)
                    val cp1x = startX + tWave * 0.6f
                    val cp1y = startY + tLen * 0.35f
                    val cp2x = startX - tWave * 0.6f
                    val cp2y = startY + tLen * 0.65f
                    val endX = startX + tWave * 0.3f
                    val endY = startY + tLen
                    cubicTo(cp1x, cp1y, cp2x, cp2y, endX, endY)
                }
                
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = tAlpha.coerceIn(0f, 1f)), Color.Transparent),
                        startY = startY,
                        endY = startY + tLen
                    ),
                    style = Stroke(
                        width = t.thickness * 1.5f + 0.3f * Math.abs(sin(j.phase * 0.5f + tIndex)).toFloat(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
