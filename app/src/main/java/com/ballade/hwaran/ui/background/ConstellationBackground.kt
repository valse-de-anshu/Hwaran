package com.ballade.hwaran.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ballade.hwaran.frontend.canvas.CanvasInteractionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// ── Palette ──────────────────────────────────────────────────────────────────
// Single calm colour: near-white silver. Line connections: barely perceptible.
private val StarWhite  = Color(0xFFF0F4FF)   // pure silver-white
private val StarGlow   = Color(0xFFB0C4EE)   // faint cold-blue halo
private val LineColor  = Color(0xFFAABBDD)   // whisper-thin connection lines
// ─────────────────────────────────────────────────────────────────────────────

private class CStar(
    val id: Int,
    var bx: Float,                // base position x (drifts slowly)
    var by: Float,                // base position y
    // Smooth individual sine-wander
    val wFreqX: Float,
    val wFreqY: Float,
    var wPhaseX: Float,
    var wPhaseY: Float,
    val wAmp: Float,              // wander amplitude in px
    // Visual
    val radius: Float,
    val baseAlpha: Float,
    var twPhase: Float,
    val twSpeed: Float,
    // Touch-repulsion velocity
    var vx: Float = 0f,
    var vy: Float = 0f
) {
    // Current position = base + wander
    val x get() = bx + sin(wPhaseX) * wAmp
    val y get() = by + cos(wPhaseY) * wAmp
}

private const val LINK_FRAC = 0.20f      // max link distance as fraction of max screen dim

@Composable
fun ConstellationBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1.0f,
    isEnabled: Boolean = true,
    interactionState: CanvasInteractionState? = null
) {
    var width  by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }
    val stars  = remember { mutableListOf<CStar>() }
    val tick   = remember { mutableStateOf(0) }

    // Seed stars when dimensions are known
    LaunchedEffect(width, height) {
        if (width == 0f || height == 0f) return@LaunchedEffect
        stars.clear()
        repeat(52) { i ->
            val amp = 10f + Random.nextFloat() * 22f
            stars += CStar(
                id      = i,
                bx      = Random.nextFloat() * width,
                by      = Random.nextFloat() * height,
                wFreqX  = 0.00055f + Random.nextFloat() * 0.0014f,
                wFreqY  = 0.00045f + Random.nextFloat() * 0.0012f,
                wPhaseX = Random.nextFloat() * 2f * PI.toFloat(),
                wPhaseY = Random.nextFloat() * 2f * PI.toFloat(),
                wAmp    = amp,
                radius  = 1.0f + Random.nextFloat() * 1.7f,
                baseAlpha = 0.38f + Random.nextFloat() * 0.52f,
                twPhase = Random.nextFloat() * 2f * PI.toFloat(),
                twSpeed = 0.010f + Random.nextFloat() * 0.020f
            )
        }
    }

    LaunchedEffect(animationSpeed, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        val repelRadius = 160f
        val repelForce  = 0.42f
        val damping     = 0.91f

        while (isActive) {
            val spd = 0.20f + animationSpeed * 0.80f

            for (s in stars) {
                // Advance individual sine-wander phases
                s.wPhaseX += s.wFreqX * spd * 60f
                s.wPhaseY += s.wFreqY * spd * 60f

                // Twinkle
                s.twPhase += s.twSpeed * spd

                // Touch repulsion — gentle push away from finger
                val ia = interactionState
                if (ia != null && ia.isTouching) {
                    val tx = ia.touchX
                    val ty = ia.touchY
                    val dx = s.x - tx
                    val dy = s.y - ty
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < repelRadius && dist > 0.5f) {
                        val strength = repelForce * (1f - dist / repelRadius)
                        s.vx += (dx / dist) * strength
                        s.vy += (dy / dist) * strength
                    }
                }

                // Apply velocity to base position, damp
                s.bx += s.vx * spd
                s.by += s.vy * spd
                s.vx *= damping
                s.vy *= damping

                // Soft wrap
                if (s.bx < -80f)        s.bx += width  + 160f
                if (s.bx > width  + 80f) s.bx -= width  + 160f
                if (s.by < -80f)        s.by += height + 160f
                if (s.by > height + 80f) s.by -= height + 160f
            }

            tick.value++
            delay(16L)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!isEnabled && stars.isEmpty()) return@Canvas
        @Suppress("UNUSED_VARIABLE") val _t = tick.value
        width  = size.width
        height = size.height

        val linkPx = LINK_FRAC * maxOf(width, height)

        // Connection lines — quadratic alpha falloff for very subtle look
        for (i in stars.indices) {
            for (j in i + 1 until stars.size) {
                val a = stars[i]; val b = stars[j]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < linkPx) {
                    val t = 1f - dist / linkPx
                    drawLine(
                        color = LineColor.copy(alpha = t * t * 0.18f),
                        start = Offset(a.x, a.y),
                        end   = Offset(b.x, b.y),
                        strokeWidth = 0.55f
                    )
                }
            }
        }

        // Stars
        for (s in stars) {
            val twinkle = 0.78f + 0.22f * sin(s.twPhase)
            val alpha   = (s.baseAlpha * twinkle).coerceIn(0f, 1f)

            // Soft glow halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StarWhite.copy(alpha = alpha * 0.50f),
                        StarGlow.copy(alpha  = alpha * 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(s.x, s.y),
                    radius = (s.radius * 5.5f).coerceAtLeast(0.1f)
                ),
                radius = s.radius * 5.5f,
                center = Offset(s.x, s.y),
                blendMode = BlendMode.Plus
            )

            // Solid core
            drawCircle(
                color  = Color.White.copy(alpha = alpha * 0.92f),
                radius = s.radius * twinkle,
                center = Offset(s.x, s.y),
                blendMode = BlendMode.Plus
            )
        }
    }
}
