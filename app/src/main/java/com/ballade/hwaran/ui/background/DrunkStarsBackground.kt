package com.ballade.hwaran.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private val PrimaryPurple = Color(0xFFC3A6FE)
private val SecondaryPink = Color(0xFFE5A6FE) // Or whatever secondary is

class Star(
    var angle: Float,
    var orbitSpeed: Float,
    var orbitR: Float,
    var wobAmp: Float,
    var wobPhaseX: Float,
    var wobPhaseY: Float,
    var wobSpeedX: Float,
    var wobSpeedY: Float,
    var r: Float,
    var baseA: Float,
    var alpha: Float,
    var dalpha: Float,
    var twPhase: Float,
    var twSpeed: Float,
    var useP: Boolean,
    var useS: Boolean,
    var x: Float = 0f,
    var y: Float = 0f
)

class Shooter(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var len: Float,
    var alpha: Float,
    var maxA: Float,
    var life: Float,
    var maxLife: Float,
    var delay: Float,
    var active: Boolean,
    var lw: Float
)

@Composable
fun DrunkStarsBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1.0f,
    isEnabled: Boolean = true
) {
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }

    // Use regular lists for performance; we use trigger.value to force redraw
    val stars = remember { mutableListOf<Star>() }
    val shooters = remember { mutableListOf<Shooter>() }

    fun spawnShooter(w: Float, h: Float, delay: Int): Shooter {
        val speed = 4.0f + Random.nextFloat() * 3.5f
        val lean = (Random.nextFloat() - 0.5f) * 1.2f
        return Shooter(
            x = 20f + Random.nextFloat() * max(1f, w - 40f),
            y = h + 8f,
            vx = lean,
            vy = -speed,
            len = 60f + Random.nextFloat() * 65f,
            alpha = 0f,
            maxA = 0.55f + Random.nextFloat() * 0.35f,
            life = 0f,
            maxLife = 55f + Random.nextInt(30).toFloat(),
            delay = delay.toFloat(),
            active = delay <= 0,
            lw = 0.8f + Random.nextFloat() * 0.8f
        )
    }

    LaunchedEffect(width, height) {
        if (width == 0f || height == 0f) return@LaunchedEffect
        
        val newStars = mutableListOf<Star>()
        val maxDim = max(width, height)

        for (i in 0 until 100) {
            val orbitRadius = 30f + Random.nextFloat() * (maxDim * 0.55f)
            val orbitSpeed = (0.002f + Random.nextFloat() * 0.004f) * if (Random.nextBoolean()) 1f else -1f
            newStars.add(
                Star(
                    angle = Random.nextFloat() * Math.PI.toFloat() * 2f,
                    orbitSpeed = orbitSpeed,
                    orbitR = orbitRadius,
                    wobAmp = 2f + Random.nextFloat() * 8f,
                    wobPhaseX = Random.nextFloat() * Math.PI.toFloat() * 2f,
                    wobPhaseY = Random.nextFloat() * Math.PI.toFloat() * 2f,
                    wobSpeedX = 0.008f + Random.nextFloat() * 0.016f,
                    wobSpeedY = 0.006f + Random.nextFloat() * 0.014f,
                    r = 0.8f + Random.nextFloat() * 2.5f,
                    baseA = 0.30f + Random.nextFloat() * 0.45f,
                    alpha = 0.06f + Random.nextFloat() * 0.45f,
                    dalpha = (0.005f + Random.nextFloat() * 0.005f) * if (Random.nextBoolean()) 1f else -1f,
                    twPhase = Random.nextFloat() * Math.PI.toFloat() * 2f,
                    twSpeed = 0.030f + Random.nextFloat() * 0.040f,
                    useP = Random.nextFloat() > 0.78f,
                    useS = Random.nextFloat() > 0.86f
                )
            )
        }
        stars.clear()
        stars.addAll(newStars)

        val newShooters = mutableListOf<Shooter>()
        newShooters.add(spawnShooter(width, height, 0))
        newShooters.add(spawnShooter(width, height, 180))
        newShooters.add(spawnShooter(width, height, 450))
        newShooters.add(spawnShooter(width, height, 465))
        shooters.clear()
        shooters.addAll(newShooters)
    }

    val trigger = remember { mutableStateOf(0) }

    LaunchedEffect(animationSpeed, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        val delayTime = 16L
        while (isActive) {
            val cx = width / 2f
            val cy = height / 2f
            
            val baseSpeed = 0.2f + animationSpeed * 0.8f

            for (st in stars) {
                st.angle += st.orbitSpeed * animationSpeed
                st.wobPhaseX += st.wobSpeedX * baseSpeed
                st.wobPhaseY += st.wobSpeedY * baseSpeed
                st.twPhase += st.twSpeed * baseSpeed

                st.alpha += st.dalpha * 1.5f 
                if (st.alpha > 0.65f || st.alpha < 0.04f) {
                    st.dalpha *= -1f
                }

                val bx = cx + cos(st.angle) * st.orbitR
                val by = cy + sin(st.angle) * st.orbitR

                st.x = bx + sin(st.wobPhaseX) * st.wobAmp + cos(st.wobPhaseY * 0.7f) * st.wobAmp * 1.5f
                st.y = by + cos(st.wobPhaseY) * st.wobAmp + sin(st.wobPhaseX * 0.8f) * st.wobAmp * 1.5f
            }

            for (i in shooters.indices) {
                val sh = shooters[i]
                if (!sh.active) {
                    sh.delay -= baseSpeed
                    if (sh.delay <= 0f) sh.active = true
                    continue
                }
                
                val shooterSpeed = maxOf(0.3f, animationSpeed)
                sh.x += sh.vx * shooterSpeed
                sh.y += sh.vy * shooterSpeed
                sh.life += shooterSpeed
                
                val prog = sh.life / sh.maxLife
                sh.alpha = if (prog < 0.15f) {
                    sh.maxA * (prog / 0.15f)
                } else {
                    sh.maxA * (1.0f - (prog - 0.15f) / 0.85f)
                }

                if (sh.life >= sh.maxLife || sh.y < -sh.len - 10f) {
                    val nextDelay = when(i) {
                        0 -> 600 + Random.nextInt(200)
                        1 -> 600 + Random.nextInt(200)
                        2 -> 900 + Random.nextInt(300)
                        3 -> 910 + Random.nextInt(300)
                        else -> 500
                    }
                    shooters[i] = spawnShooter(width, height, nextDelay)
                }
            }

            trigger.value++
            delay(delayTime)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!isEnabled && stars.isEmpty()) return@Canvas
        
        val _unused = trigger.value 
        width = size.width
        height = size.height

        for (st in stars) {
            val sinV = sin(st.twPhase)
            val pulseMultiplier = 0.88f + 0.12f * sinV
            val haloR = (st.r * pulseMultiplier) * 3.5f 
            val currentAlpha = st.alpha.coerceIn(0f, 1f)

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = currentAlpha),
                        0.45f to Color.White.copy(alpha = currentAlpha * 0.35f),
                        1.0f to Color.Transparent
                    ),
                    center = Offset(st.x, st.y),
                    radius = haloR.coerceAtLeast(0.1f)
                ),
                radius = haloR,
                center = Offset(st.x, st.y),
                blendMode = BlendMode.Plus
            )

            drawCircle(
                color = Color.White.copy(alpha = currentAlpha * 0.8f),
                radius = st.r * pulseMultiplier,
                center = Offset(st.x, st.y),
                blendMode = BlendMode.Plus
            )
        }

        for (sh in shooters) {
            if (!sh.active || sh.alpha <= 0f) continue
            val mag = max(0.1f, kotlin.math.sqrt(sh.vx * sh.vx + sh.vy * sh.vy))
            val tx = sh.x - (sh.vx / mag) * sh.len
            val ty = sh.y - (sh.vy / mag) * sh.len

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = sh.alpha),
                        Color.White.copy(alpha = sh.alpha * 0.3f),
                        Color.Transparent
                    ),
                    start = Offset(sh.x, sh.y),
                    end = Offset(tx, ty)
                ),
                start = Offset(sh.x, sh.y),
                end = Offset(tx, ty),
                strokeWidth = sh.lw * 2.5f 
            )

            drawCircle(
                color = Color.White.copy(alpha = sh.alpha),
                radius = 3.8f,
                center = Offset(sh.x, sh.y),
                blendMode = BlendMode.Plus
            )
        }
    }
}
