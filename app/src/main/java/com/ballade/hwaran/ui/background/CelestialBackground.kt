package com.ballade.hwaran.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// Colors from React code
private val NightColors = listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF09090B))
private val DawnColors = listOf(Color(0xFF312E81), Color(0xFFBE123C), Color(0xFFF59E0B))
private val DayColors = listOf(Color(0xFF38BDF8), Color(0xFF7DD3FC), Color(0xFFBAE6FD))
private val DuskColors = listOf(Color(0xFF4C1D95), Color(0xFF9D174D), Color(0xFFEA580C))

private class CelestialStar(
    val id: Int,
    val cx: Float, // %
    val cy: Float, // %
    val r: Float,
    val delay: Float,
    val duration: Float
)

private class CelestialCloud(
    val id: Int,
    val type: Int, // 0 for CloudA, 1 for CloudB
    var x: Float, // %
    var y: Float, // %
    val depth: Float,
    val baseRotation: Float,
    val floatSpeed: Float,
    val rotationSpeed: Float,
    val rotXSpeed: Float,
    val rotYSpeed: Float,
    val phaseOffset: Float,
    val zIndex: Int,
    val sizeWidth: Float,
    val sizeHeight: Float
)

@Composable
fun CelestialBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1.0f,
    isEnabled: Boolean = true,
    scrollOffset: Float = 0f 
) {
    val stars = remember { 
        List(150) { i ->
            CelestialStar(
                id = i,
                cx = Random.nextFloat() * 100f,
                cy = Random.nextFloat() * 100f,
                r = Random.nextFloat() * 1.2f + 0.4f,
                delay = Random.nextFloat() * 5f,
                duration = Random.nextFloat() * 3f + 2f
            )
        }
    }

    val clouds = remember {
        List(5) { i ->
            val depthCategory = Random.nextFloat()
            val depth: Float
            val sizeWidth: Float
            val sizeHeight: Float
            val zIndex: Int
            
            if (depthCategory < 0.3f) {
                depth = 0.2f + Random.nextFloat() * 0.2f
                sizeWidth = 128f
                sizeHeight = 80f
                zIndex = 20
            } else if (depthCategory < 0.7f) {
                depth = 0.5f + Random.nextFloat() * 0.3f
                sizeWidth = 224f
                sizeHeight = 128f
                zIndex = 30
            } else {
                depth = 0.9f + Random.nextFloat() * 0.4f
                sizeWidth = 320f
                sizeHeight = 192f
                zIndex = 50
            }

            CelestialCloud(
                id = i,
                type = i % 2,
                x = (Random.nextFloat() * 100f) - 10f,
                y = (Random.nextFloat() * 100f) - 10f,
                depth = depth,
                baseRotation = (Random.nextFloat() - 0.5f) * 15f,
                floatSpeed = 0.0015f + Random.nextFloat() * 0.0015f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 0.002f,
                rotXSpeed = 0.003f + Random.nextFloat() * 0.004f,
                rotYSpeed = 0.003f + Random.nextFloat() * 0.004f,
                phaseOffset = Random.nextFloat() * PI.toFloat() * 2f,
                zIndex = zIndex,
                sizeWidth = sizeWidth,
                sizeHeight = sizeHeight
            )
        }
    }

    var time by remember { mutableStateOf(0f) }

    LaunchedEffect(animationSpeed, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        while (isActive) {
            time += 0.015f * animationSpeed
            delay(16)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width == 0f || size.height == 0f) return@Canvas

        // Cycle Math
        val viewportHeight = size.height
        val cycleHeight = viewportHeight * 3f
        val p = ((scrollOffset % cycleHeight) + cycleHeight) % cycleHeight / cycleHeight

        // opDay, opDawn, opDusk, opNight
        val opDay = celestialGaussian(p, 0.5f, 0.15f)
        val opDawn = celestialGaussian(p, 0.25f, 0.1f)
        val opDusk = celestialGaussian(p, 0.75f, 0.1f)
        val opNight = celestialGaussian(p, 0f, 0.15f) + celestialGaussian(p, 1f, 0.15f)

        // Draw Background Gradients
        drawRect(color = Color.Black) // Base

        drawCelestialGradient(NightColors, opNight)
        drawCelestialGradient(DawnColors, opDawn)
        drawCelestialGradient(DayColors, opDay)
        drawCelestialGradient(DuskColors, opDusk)

        // Draw Stars
        val starsAlpha = (opNight + opDawn * 0.3f + opDusk * 0.3f).coerceIn(0f, 1f)
        if (starsAlpha > 0f) {
            for (star in stars) {
                val twinkle = (sin((time + star.delay) * (PI.toFloat() * 2f / star.duration)) + 1f) / 2f
                val alpha = (0.1f + 0.9f * twinkle) * starsAlpha
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = star.r,
                    center = Offset(star.cx / 100f * size.width, star.cy / 100f * size.height)
                )
            }
        }

        // Draw Sun & Moon
        drawCelestialBodies(p, time)

        // Draw Clouds
        drawCelestialClouds(clouds, time, scrollOffset, opDay, opDawn, opDusk, opNight)
    }
}

private fun DrawScope.drawCelestialGradient(colors: List<Color>, opacity: Float) {
    if (opacity <= 0.01f) return
    drawRect(
        brush = Brush.verticalGradient(colors),
        alpha = opacity.coerceIn(0f, 1f)
    )
}

private fun celestialGaussian(x: Float, mean: Float, std: Float): Float {
    return exp(-((x - mean).pow(2)) / (2 * std.pow(2)))
}

private fun DrawScope.drawCelestialBodies(p: Float, time: Float) {
    val vpWidth = size.width
    val vpHeight = size.height

    // Sun rises at 0.15, peaks at 0.5, sets at 0.85
    val sunAngle = ((p - 0.15f) / 0.7f) * PI.toFloat()
    if (p > 0.1f && p < 0.9f) {
        val sunX = (-0.2f + (sunAngle / PI.toFloat()) * 1.2f) * vpWidth
        val sunY = (0.55f - sin(sunAngle) * 0.45f) * vpHeight
        val sunOpacity = (sin(sunAngle) * 2f).coerceIn(0f, 1f)
        val scale = 0.8f + sin(time * 0.02f) * 0.05f

        withTransform({
            translate(sunX, sunY)
            scale(scale, scale, Offset.Zero)
        }) {
            drawCelestialSun(sunOpacity)
        }
    }

    // Moon rises at 0.65, peaks at 0 (or 1), sets at 0.35
    val moonP = (p + 0.5f) % 1.0f
    val moonAngle = ((moonP - 0.15f) / 0.7f) * PI.toFloat()
    if (moonP > 0.1f && moonP < 0.9f) {
        val moonX = (-0.2f + (moonAngle / PI.toFloat()) * 1.2f) * vpWidth
        val moonY = (0.55f - sin(moonAngle) * 0.45f) * vpHeight
        val moonOpacity = (sin(moonAngle) * 2f).coerceIn(0f, 1f)
        val scale = 0.8f + sin(time * 0.01f) * 0.02f

        withTransform({
            translate(moonX, moonY)
            scale(scale, scale, Offset.Zero)
        }) {
            drawCelestialMoon(moonOpacity)
        }
    }
}

private fun DrawScope.drawCelestialSun(opacity: Float) {
    val r = 200f 
    drawCircle(
        brush = Brush.radialGradient(
            0.15f to Color.White,
            0.25f to Color(0xFFFDE68A).copy(alpha = 0.9f),
            0.55f to Color(0xFFF59E0B).copy(alpha = 0.3f),
            1.00f to Color(0xFFF59E0B).copy(alpha = 0f),
            center = Offset.Zero,
            radius = r
        ),
        radius = r,
        center = Offset.Zero,
        alpha = opacity
    )
    drawCircle(
        color = Color.White,
        radius = 42f,
        center = Offset.Zero,
        alpha = opacity
    )
}

private fun DrawScope.drawCelestialMoon(opacity: Float) {
    val r = 180f
    drawCircle(
        brush = Brush.radialGradient(
            0.15f to Color(0xFFF8FAFC),
            0.30f to Color(0xFFE2E8F0).copy(alpha = 0.6f),
            0.65f to Color(0xFF94A3B8).copy(alpha = 0.15f),
            1.00f to Color(0xFF94A3B8).copy(alpha = 0f),
            center = Offset.Zero,
            radius = r
        ),
        radius = r,
        center = Offset.Zero,
        alpha = opacity
    )
    drawCircle(
        color = Color(0xFFF1F5F9),
        radius = 45f,
        center = Offset.Zero,
        alpha = opacity
    )
    // Craters
    drawCircle(Color(0xFFCBD5E1).copy(alpha = 0.6f * opacity), 12f, Offset(-20f, -18f))
    drawCircle(Color(0xFFCBD5E1).copy(alpha = 0.5f * opacity), 8f, Offset(20f, -10f))
    drawCircle(Color(0xFFCBD5E1).copy(alpha = 0.7f * opacity), 15f, Offset(10f, 20f))
}

private fun DrawScope.drawCelestialClouds(
    clouds: List<CelestialCloud>,
    time: Float,
    scrollOffset: Float,
    opDay: Float,
    opDawn: Float,
    opDusk: Float,
    opNight: Float
) {
    val viewportWidth = size.width
    val viewportHeight = size.height

    val sortedClouds = clouds.sortedBy { it.zIndex }

    for (cloud in sortedClouds) {
        val baseXPx = (cloud.x / 100f) * viewportWidth
        val windDriftX = time * cloud.floatSpeed * 1500f
        val totalX = baseXPx + windDriftX
        
        val wrapRangeX = viewportWidth + 800f
        val wrapOffsetX = 400f
        val wrappedX = ((((totalX + wrapOffsetX) % wrapRangeX) + wrapRangeX) % wrapRangeX) - wrapOffsetX
        
        val driftX = wrappedX - baseXPx

        // Constant upward drift + minimal turbulence
        val windDriftY = -time * cloud.floatSpeed * 1200f
        val driftY = windDriftY + sin(time * cloud.floatSpeed * 5f + cloud.phaseOffset) * 5f
        
        val parallaxY = -scrollOffset * cloud.depth * 0.8f
        val baseYPx = (cloud.y / 100f) * viewportHeight
        val wrapRangeY = viewportHeight * 2f // Increased range for better vertical flow
        val wrapOffsetY = viewportHeight * 0.5f
        val totalY = baseYPx + parallaxY + driftY
        val wrappedY = ((((totalY + wrapOffsetY) % wrapRangeY) + wrapRangeY) % wrapRangeY) - wrapOffsetY

        val rotZ = cloud.baseRotation + (time * cloud.rotationSpeed * 50f) + (scrollOffset * 0.01f * cloud.depth)
        val scale = 1f + (sin(time * 0.02f + cloud.phaseOffset) * 0.05f)

        // Lighting - Calculate color based on time of day
        // opDay -> White, opDawn -> Warm, opDusk -> Orange/Dark, opNight -> Deep Blue/Dark
        val r = (opDay * 1.0f + opDawn * 0.95f + opDusk * 0.8f + opNight * 0.05f).coerceIn(0f, 1f)
        val g = (opDay * 1.0f + opDawn * 0.8f + opDusk * 0.4f + opNight * 0.07f).coerceIn(0f, 1f)
        val b = (opDay * 1.0f + opDawn * 0.6f + opDusk * 0.2f + opNight * 0.15f).coerceIn(0f, 1f)
        val cloudColor = Color(r, g, b, 1f)
        
        withTransform({
            translate(baseXPx + driftX, wrappedY)
            scale(scale, scale, Offset(cloud.sizeWidth / 2f, cloud.sizeHeight / 2f))
            rotate(rotZ, Offset(cloud.sizeWidth / 2f, cloud.sizeHeight / 2f))
        }) {
            if (cloud.type == 0) drawCelestialCloudA(cloud.sizeWidth, cloud.sizeHeight, cloudColor)
            else drawCelestialCloudB(cloud.sizeWidth, cloud.sizeHeight, cloudColor)
        }
    }
}

private fun DrawScope.drawCelestialCloudA(width: Float, height: Float, color: Color) {
    val path = Path().apply {
        val sx = width / 200f
        val sy = height / 120f
        moveTo(50f * sx, 80f * sy)
        quadraticTo(20f * sx, 80f * sy, 20f * sx, 50f * sy)
        quadraticTo(20f * sx, 20f * sy, 60f * sx, 25f * sy)
        quadraticTo(80f * sx, 5f * sy, 120f * sx, 15f * sy)
        quadraticTo(160f * sx, 5f * sy, 180f * sx, 30f * sy)
        quadraticTo(195f * sx, 50f * sy, 180f * sx, 80f * sy)
        quadraticTo(160f * sx, 100f * sy, 120f * sx, 95f * sy)
        quadraticTo(80f * sx, 100f * sy, 50f * sx, 80f * sy)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawCelestialCloudB(width: Float, height: Float, color: Color) {
    val path = Path().apply {
        val sx = width / 240f
        val sy = height / 140f
        moveTo(60f * sx, 90f * sy)
        quadraticTo(30f * sx, 90f * sy, 30f * sx, 60f * sy)
        quadraticTo(30f * sx, 30f * sy, 70f * sx, 35f * sy)
        quadraticTo(90f * sx, 10f * sy, 140f * sx, 20f * sy)
        quadraticTo(180f * sx, 10f * sy, 200f * sx, 40f * sy)
        quadraticTo(220f * sx, 60f * sy, 200f * sx, 90f * sy)
        quadraticTo(170f * sx, 110f * sy, 130f * sx, 105f * sy)
        quadraticTo(90f * sx, 110f * sy, 60f * sx, 90f * sy)
        close()
    }
    drawPath(path, color)
}

