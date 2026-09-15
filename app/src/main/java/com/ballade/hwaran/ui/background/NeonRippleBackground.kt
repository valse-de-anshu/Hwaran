package com.ballade.hwaran.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ballade.hwaran.frontend.canvas.CanvasInteractionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.random.Random

// ── Palette — single calm hue: deep sapphire / midnight blue / ice white ──────
private val RingDeep = Color(0xFF1A40A8)   // deep sapphire — ring body
private val RingMid  = Color(0xFF3D70E8)   // lighter sapphire
private val RingEdge = Color(0xFFCCD8FF)   // pale ice-white edge
// ─────────────────────────────────────────────────────────────────────────────

private class NRing(
    val cx: Float,
    val cy: Float,
    var radius: Float,
    val maxRadius: Float,
    val growSpeed: Float,
    val strokeWidth: Float,
    val isTouch: Boolean     // touch-spawned rings are slightly brighter
)

@Composable
fun NeonRippleBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1.0f,
    isEnabled: Boolean = true,
    interactionState: CanvasInteractionState? = null
) {
    var width  by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }
    val rings  = remember { mutableListOf<NRing>() }
    val tick   = remember { mutableStateOf(0) }

    var spawnTimer  by remember { mutableIntStateOf(0) }
    var lastTapVer  by remember { mutableIntStateOf(-1) }

    fun ambientRing(): NRing {
        val maxR = max(width, height) * (0.18f + Random.nextFloat() * 0.32f)
        return NRing(
            cx          = width  * 0.08f + Random.nextFloat() * width  * 0.84f,
            cy          = height * 0.08f + Random.nextFloat() * height * 0.84f,
            radius      = 0f,
            maxRadius   = maxR,
            growSpeed   = 0.60f + Random.nextFloat() * 0.80f,
            strokeWidth = 0.9f  + Random.nextFloat() * 1.1f,
            isTouch     = false
        )
    }

    fun touchRing(tx: Float, ty: Float): NRing {
        val maxR = max(width, height) * (0.15f + Random.nextFloat() * 0.20f)
        return NRing(
            cx          = tx,
            cy          = ty,
            radius      = 0f,
            maxRadius   = maxR,
            growSpeed   = 1.1f + Random.nextFloat() * 0.6f,
            strokeWidth = 1.2f + Random.nextFloat() * 0.8f,
            isTouch     = true
        )
    }

    // Seed a few rings at staggered radii so the screen isn't empty on first open
    LaunchedEffect(width, height) {
        if (width == 0f || height == 0f) return@LaunchedEffect
        rings.clear()
        repeat(4) { i ->
            val r = ambientRing()
            r.radius = r.maxRadius * (i * 0.22f)
            rings += r
        }
    }

    LaunchedEffect(animationSpeed, isEnabled) {
        if (!isEnabled) return@LaunchedEffect

        while (isActive) {
            val spd = 0.25f + animationSpeed * 0.75f

            // Advance rings, cull finished ones
            val iter = rings.iterator()
            while (iter.hasNext()) {
                val r = iter.next()
                r.radius += r.growSpeed * spd
                if (r.radius >= r.maxRadius) iter.remove()
            }

            // Ambient spawn — one ring every ~100 frames at normal speed
            spawnTimer++
            val interval = (100f / spd).toInt().coerceIn(25, 250)
            if (spawnTimer >= interval && width > 0f) {
                spawnTimer = 0
                rings += ambientRing()
            }

            // Touch-down spawn — one crisp ring per tap-down event
            val ia = interactionState
            if (ia != null) {
                val tapV = ia.tapVersion
                if (tapV != lastTapVer && ia.isTouching && width > 0f) {
                    lastTapVer = tapV
                    rings += touchRing(ia.touchX, ia.touchY)
                    // Second smaller echo ring for depth
                    rings += touchRing(ia.touchX, ia.touchY).also { it.maxRadius.coerceAtMost(it.maxRadius * 0.5f) }
                }
            }

            tick.value++
            delay(16L)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!isEnabled && rings.isEmpty()) return@Canvas
        @Suppress("UNUSED_VARIABLE") val _t = tick.value
        width  = size.width
        height = size.height

        for (r in rings) {
            if (r.radius <= 0.5f) continue
            val progress = (r.radius / r.maxRadius).coerceIn(0f, 1f)
            // Alpha: rises briefly then fades — rings appear to emerge then dissolve
            val alpha = when {
                progress < 0.08f -> progress / 0.08f               // quick fade-in
                else             -> (1f - progress) * (1f - progress) // smooth ease-out
            }.coerceIn(0f, 1f) * if (r.isTouch) 0.90f else 0.65f

            if (alpha < 0.01f) continue

            // Outer glow (wide, soft)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.60f to Color.Transparent,
                        0.82f to RingDeep.copy(alpha = alpha * 0.20f),
                        0.93f to RingMid.copy(alpha  = alpha * 0.50f),
                        0.99f to RingEdge.copy(alpha = alpha * 0.80f),
                        1.00f to Color.Transparent
                    ),
                    center = Offset(r.cx, r.cy),
                    radius = r.radius.coerceAtLeast(0.1f)
                ),
                radius = r.radius,
                center = Offset(r.cx, r.cy),
                blendMode = BlendMode.Plus
            )

            // Crisp edge stroke
            drawCircle(
                color  = RingEdge.copy(alpha = alpha * 0.70f),
                radius = r.radius,
                center = Offset(r.cx, r.cy),
                style  = Stroke(width = r.strokeWidth),
                blendMode = BlendMode.Plus
            )
        }
    }
}
