package com.ballade.hwaran.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ballade.hwaran.frontend.canvas.CanvasInteractionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// ── Palette — pure ice: white core, pale arctic blue tint ────────────────────
private val IceWhite  = Color(0xFFEFF6FF)   // near-pure white
private val IceBlue   = Color(0xFFBDD8F5)   // very pale arctic blue
private val IceShadow = Color(0xFF8AB4D8)   // dim blue for depth/glow
// ─────────────────────────────────────────────────────────────────────────────

private class SnowCrystal(
    var x: Float,
    var y: Float,
    val size: Float,           // arm length px
    var rot: Float,            // rotation radians
    val rotSpeed: Float,
    val fallSpeed: Float,      // larger crystals fall slower (depth)
    val driftAmp: Float,
    var driftPhase: Float,
    val driftSpeed: Float,
    val alpha: Float,          // brighter = closer
    val layer: Int             // 0=far/small/dim, 2=close/large/bright
)

private fun DrawScope.drawCrystal(c: SnowCrystal) {
    val path = Path()
    val armCount = 6
    val step = (2f * PI / armCount).toFloat()

    for (i in 0 until armCount) {
        val angle = c.rot + step * i
        val tipX  = c.x + cos(angle) * c.size
        val tipY  = c.y + sin(angle) * c.size

        // Main arm
        path.moveTo(c.x, c.y)
        path.lineTo(tipX, tipY)

        // Two branch stubs at 55% along each arm
        val frac    = 0.55f
        val bLen    = c.size * 0.32f
        val bx      = c.x + cos(angle) * c.size * frac
        val by      = c.y + sin(angle) * c.size * frac
        val b1a     = angle + PI.toFloat() / 3f
        val b2a     = angle - PI.toFloat() / 3f
        path.moveTo(bx, by)
        path.lineTo(bx + cos(b1a) * bLen, by + sin(b1a) * bLen)
        path.moveTo(bx, by)
        path.lineTo(bx + cos(b2a) * bLen, by + sin(b2a) * bLen)
    }

    val strokeW = (c.size * 0.11f).coerceIn(0.6f, 1.6f)
    val alpha   = c.alpha.coerceIn(0f, 1f)
    val color   = if (c.layer == 2) IceWhite else IceBlue

    // Glow halo for closer crystals
    if (c.layer >= 1) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    IceShadow.copy(alpha = alpha * 0.30f),
                    Color.Transparent
                ),
                center = Offset(c.x, c.y),
                radius = c.size * 1.8f
            ),
            radius = c.size * 1.8f,
            center = Offset(c.x, c.y),
            blendMode = BlendMode.Plus
        )
    }

    drawPath(
        path  = path,
        color = color.copy(alpha = alpha),
        style = Stroke(width = strokeW),
        blendMode = BlendMode.Plus
    )
}

@Composable
fun CrystalSnowBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1.0f,
    isEnabled: Boolean = true,
    interactionState: CanvasInteractionState? = null
) {
    var width   by remember { mutableStateOf(0f) }
    var height  by remember { mutableStateOf(0f) }
    val crystals = remember { mutableListOf<SnowCrystal>() }
    val tick     = remember { mutableStateOf(0) }

    fun spawnCrystal(offscreen: Boolean = false): SnowCrystal {
        // Layer 0 = far (small/dim/fast), 2 = close (large/bright/slow)
        val layer     = Random.nextInt(3)
        val sizeMult  = when (layer) { 0 -> 0.4f; 1 -> 0.75f; else -> 1.0f }
        val size      = (7f + Random.nextFloat() * 16f) * sizeMult
        val alphaMult = when (layer) { 0 -> 0.25f; 1 -> 0.55f; else -> 0.80f }
        val fallMult  = when (layer) { 2 -> 0.5f;  1 -> 0.75f; else -> 1.1f  }
        return SnowCrystal(
            x          = Random.nextFloat() * width,
            y          = if (offscreen) -(20f + Random.nextFloat() * 80f) else Random.nextFloat() * height,
            size       = size,
            rot        = Random.nextFloat() * 2f * PI.toFloat(),
            rotSpeed   = (0.003f + Random.nextFloat() * 0.007f) * if (Random.nextBoolean()) 1f else -1f,
            fallSpeed  = (0.20f  + Random.nextFloat() * 0.45f) * fallMult,
            driftAmp   = 0.5f + Random.nextFloat() * 1.2f,
            driftPhase = Random.nextFloat() * 2f * PI.toFloat(),
            driftSpeed = 0.016f + Random.nextFloat() * 0.028f,
            alpha      = (0.20f + Random.nextFloat() * 0.40f) * alphaMult / 0.8f,
            layer      = layer
        )
    }

    LaunchedEffect(width, height) {
        if (width == 0f || height == 0f) return@LaunchedEffect
        crystals.clear()
        // Spawn back-to-front (far first, close last) for correct blend ordering
        repeat(32) { crystals += spawnCrystal(offscreen = false) }
        crystals.sortBy { it.layer }
    }

    LaunchedEffect(animationSpeed, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        val draftRadius = 180f
        val draftForce  = 0.30f

        while (isActive) {
            val spd = 0.25f + animationSpeed * 0.75f

            val ia = interactionState

            for (i in crystals.indices) {
                val c = crystals[i]

                // Normal fall + drift
                c.y          += c.fallSpeed * spd
                c.rot        += c.rotSpeed  * spd
                c.driftPhase += c.driftSpeed * spd
                c.x          += sin(c.driftPhase) * c.driftAmp * 0.5f * spd

                // Touch draft — finger push gently lifts nearby crystals upward
                if (ia != null && ia.isTouching) {
                    val dx   = c.x - ia.touchX
                    val dy   = c.y - ia.touchY
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < draftRadius && dist > 1f) {
                        val f = draftForce * (1f - dist / draftRadius) * spd
                        // Push up (negative y) and slightly outward
                        c.y -= f * 3.5f
                        c.x += (dx / dist) * f * 0.8f
                    }
                }

                // Respawn when below screen; wrap x gently
                if (c.y > height + 60f) {
                    crystals[i] = spawnCrystal(offscreen = true)
                }
                if (c.x < -60f)        c.x += width + 120f
                if (c.x > width + 60f) c.x -= width + 120f
            }

            tick.value++
            delay(16L)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!isEnabled && crystals.isEmpty()) return@Canvas
        @Suppress("UNUSED_VARIABLE") val _t = tick.value
        width  = size.width
        height = size.height

        // Draw in layer order (already sorted far→close on init)
        for (c in crystals) {
            drawCrystal(c)
        }
    }
}
