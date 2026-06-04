package com.ballade.hwaran.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import android.graphics.Matrix as NativeMatrix
import android.graphics.BitmapShader as NativeBitmapShader
import android.graphics.Shader as NativeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun KaleidoscopeBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1.0f,
    isEnabled: Boolean = true,
    scrollOffset: Float = 0f
) {
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }

    val cosmosBitmap = remember { mutableStateOf<ImageBitmap?>(null) }
    val floraBitmap = remember { mutableStateOf<ImageBitmap?>(null) }
    val cosmosShader = remember { mutableStateOf<NativeBitmapShader?>(null) }
    val floraShader = remember { mutableStateOf<NativeBitmapShader?>(null) }

    val textureSize = 800

    LaunchedEffect(Unit) {
        val cosmos = generateCrystallineTexture(textureSize, "cosmos")
        val flora = generateCrystallineTexture(textureSize, "flora")
        cosmosBitmap.value = cosmos.asImageBitmap()
        floraBitmap.value = flora.asImageBitmap()
        cosmosShader.value = NativeBitmapShader(cosmos, NativeShader.TileMode.REPEAT, NativeShader.TileMode.REPEAT)
        floraShader.value = NativeBitmapShader(flora, NativeShader.TileMode.REPEAT, NativeShader.TileMode.REPEAT)
    }

    var time by remember { mutableFloatStateOf(0f) }
    var currentScroll by remember { mutableFloatStateOf(0f) }
    val targetScrollOffset by rememberUpdatedState(scrollOffset)

    LaunchedEffect(isEnabled, animationSpeed) {
        if (!isEnabled) return@LaunchedEffect
        while (isActive) {
            time += 0.012f * animationSpeed
            val scrollDiff = targetScrollOffset - currentScroll
            currentScroll += scrollDiff * 0.08f
            delay(16)
        }
    }

    // Reusable objects for drawing
    val wedgePathCosmos = remember { Path() }
    val wedgePathFlora = remember { Path() }
    val drawPaint = remember { Paint() }
    val seamPaint = remember { 
        Paint().apply {
            color = Color.Black.copy(alpha = 0.9f)
            style = PaintingStyle.Stroke
            strokeWidth = 2.5f
        }
    }
    val nativeMatrix = remember { NativeMatrix() }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!isEnabled) return@Canvas
        
        width = size.width
        height = size.height
        if (width == 0f || height == 0f) return@Canvas

        val centerX = width / 2f
        val centerY = height / 2f

        val cosmos = cosmosBitmap.value
        val flora = floraBitmap.value
        val cShader = cosmosShader.value
        val fShader = floraShader.value

        if (cosmos == null || flora == null || cShader == null || fShader == null) return@Canvas

        // Deep Space Base
        drawRect(Color(0xFF020005))

        // Update wedge paths if size changed
        val cosmosRadius = max(width, height) * 1.2f
        val floraRadius = max(width, height) * 0.7f
        
        if (wedgePathCosmos.isEmpty) {
            val sliceAngle = (Math.PI.toFloat() * 2f) / 16
            wedgePathCosmos.moveTo(0f, 0f)
            wedgePathCosmos.lineTo(cosmosRadius, 0f)
            wedgePathCosmos.lineTo(cosmosRadius * cos(sliceAngle), cosmosRadius * sin(sliceAngle))
            wedgePathCosmos.close()
        }
        if (wedgePathFlora.isEmpty) {
            val sliceAngle = (Math.PI.toFloat() * 2f) / 24
            wedgePathFlora.moveTo(0f, 0f)
            wedgePathFlora.lineTo(floraRadius, 0f)
            wedgePathFlora.lineTo(floraRadius * cos(sliceAngle), floraRadius * sin(sliceAngle))
            wedgePathFlora.close()
        }

        // Parallax Layers
        val layers = listOf(
            Layer(
                shader = cShader,
                segments = 16,
                radius = cosmosRadius,
                rotationDir = 1f,
                scrollSpeedX = 0.1f,
                scrollSpeedY = 0.08f,
                timeSpeedX = 30f,
                timeSpeedY = 20f,
                opacity = 1.0f,
                path = wedgePathCosmos
            ),
            Layer(
                shader = fShader,
                segments = 24,
                radius = floraRadius,
                rotationDir = -1f,
                scrollSpeedX = -0.15f,
                scrollSpeedY = 0.12f,
                timeSpeedX = -40f,
                timeSpeedY = 25f,
                opacity = 0.85f,
                path = wedgePathFlora
            )
        )

        layers.forEach { layer ->
            val sliceAngleDeg = 360f / layer.segments

            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(centerX, centerY)

                // Entire layer rotation
                val totalRotation = currentScroll * 0.15f * layer.rotationDir + time * 25f * layer.rotationDir
                canvas.rotate(totalRotation)

                // Prepare pattern transform
                val offsetX = currentScroll * layer.scrollSpeedX + (time * layer.timeSpeedX)
                val offsetY = currentScroll * layer.scrollSpeedY + (time * layer.timeSpeedY)
                
                nativeMatrix.reset()
                nativeMatrix.postTranslate(offsetX, offsetY)
                nativeMatrix.postRotate(time * 30f * layer.rotationDir)
                layer.shader.setLocalMatrix(nativeMatrix)

                drawPaint.alpha = layer.opacity
                val nativePaint = drawPaint.asFrameworkPaint()
                nativePaint.shader = layer.shader

                // Draw segments
                for (i in 0 until layer.segments) {
                    canvas.save()

                    val isOdd = i % 2 != 0
                    val rotationIndex = if (isOdd) i + 1 else i
                    
                    canvas.rotate(rotationIndex * sliceAngleDeg)
                    if (isOdd) {
                        canvas.scale(1f, -1f)
                    }

                    canvas.clipPath(layer.path)
                    canvas.drawPath(layer.path, drawPaint)
                    canvas.drawPath(layer.path, seamPaint)

                    canvas.restore()
                }
                canvas.restore()
            }
        }

        // Atmospheric glowing core and darkened outer tube
        val gradientRadius = max(width, height) * 0.65f
        drawRect(
            brush = Brush.radialGradient(
                0.0f to Color.White.copy(alpha = 0.15f),
                0.4f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.95f),
                center = Offset(centerX, centerY),
                radius = gradientRadius
            )
        )
    }
}

private data class Layer(
    val shader: NativeBitmapShader,
    val segments: Int,
    val radius: Float,
    val rotationDir: Float,
    val scrollSpeedX: Float,
    val scrollSpeedY: Float,
    val timeSpeedX: Float,
    val timeSpeedY: Float,
    val opacity: Float,
    val path: Path
)

private fun generateCrystallineTexture(size: Int, type: String): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val random = Random(if (type == "flora") 123 else 456)

    fun drawFacetedShard(x: Float, y: Float, angle: Float, length: Float, width: Float, colorLight: Int, colorDark: Int) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        
        val paint = NativePaint()
        paint.isAntiAlias = true
        paint.strokeWidth = 3.5f
        paint.style = NativePaint.Style.FILL
        
        val strokePaint = NativePaint()
        strokePaint.isAntiAlias = true
        strokePaint.strokeWidth = 3.5f
        strokePaint.style = NativePaint.Style.STROKE
        strokePaint.color = android.graphics.Color.BLACK

        // Left Facet
        val leftPath = NativePath()
        leftPath.moveTo(0f, 0f)
        leftPath.lineTo(-width / 2f, length / 2f)
        leftPath.lineTo(0f, length)
        leftPath.close()
        paint.color = colorLight
        canvas.drawPath(leftPath, paint)
        canvas.drawPath(leftPath, strokePaint)

        // Right Facet
        val rightPath = NativePath()
        rightPath.moveTo(0f, 0f)
        rightPath.lineTo(width / 2f, length / 2f)
        rightPath.lineTo(0f, length)
        rightPath.close()
        paint.color = colorDark
        canvas.drawPath(rightPath, paint)
        canvas.drawPath(rightPath, strokePaint)

        canvas.restore()
    }

    if (type == "flora") {
        canvas.drawColor(android.graphics.Color.parseColor("#010805"))
        // Emerald Vines/Leaves
        repeat(100) {
            drawFacetedShard(
                random.nextFloat() * size, random.nextFloat() * size, 
                random.nextFloat() * Math.PI.toFloat() * 2f, 
                120f + random.nextFloat() * 80f, 50f + random.nextFloat() * 30f, 
                android.graphics.Color.argb((0.9f * 255).toInt(), 16, 185, 129), 
                android.graphics.Color.argb((0.9f * 255).toInt(), 4, 120, 87)
            )
        }
        // Cyan/Teal Ferns
        repeat(80) {
            drawFacetedShard(
                random.nextFloat() * size, random.nextFloat() * size, 
                random.nextFloat() * Math.PI.toFloat() * 2f, 
                90f + random.nextFloat() * 60f, 40f + random.nextFloat() * 20f, 
                android.graphics.Color.argb((0.9f * 255).toInt(), 45, 212, 191), 
                android.graphics.Color.argb((0.9f * 255).toInt(), 15, 118, 110)
            )
        }
        // Magenta Lotus Petals
        repeat(70) {
            drawFacetedShard(
                random.nextFloat() * size, random.nextFloat() * size, 
                random.nextFloat() * Math.PI.toFloat() * 2f, 
                70f + random.nextFloat() * 50f, 30f + random.nextFloat() * 15f, 
                android.graphics.Color.argb((0.9f * 255).toInt(), 217, 70, 239), 
                android.graphics.Color.argb((0.9f * 255).toInt(), 162, 28, 175)
            )
        }
    }

    if (type == "cosmos") {
        canvas.drawColor(android.graphics.Color.parseColor("#010008"))
        // Deep Blue Shattered Space
        repeat(120) {
            drawFacetedShard(
                random.nextFloat() * size, random.nextFloat() * size, 
                random.nextFloat() * Math.PI.toFloat() * 2f, 
                180f + random.nextFloat() * 100f, 80f + random.nextFloat() * 40f, 
                android.graphics.Color.argb((0.8f * 255).toInt(), 30, 58, 138), 
                android.graphics.Color.argb((0.9f * 255).toInt(), 17, 24, 39)
            )
        }
        // Cyan Nebula Shards
        repeat(80) {
            drawFacetedShard(
                random.nextFloat() * size, random.nextFloat() * size, 
                random.nextFloat() * Math.PI.toFloat() * 2f, 
                100f + random.nextFloat() * 80f, 40f + random.nextFloat() * 30f, 
                android.graphics.Color.argb((0.8f * 255).toInt(), 14, 165, 233), 
                android.graphics.Color.argb((0.9f * 255).toInt(), 3, 105, 161)
            )
        }
        // Gold Starbursts
        repeat(90) {
            drawFacetedShard(
                random.nextFloat() * size, random.nextFloat() * size, 
                random.nextFloat() * Math.PI.toFloat() * 2f, 
                50f + random.nextFloat() * 30f, 15f + random.nextFloat() * 10f, 
                android.graphics.Color.argb((0.9f * 255).toInt(), 253, 230, 138), 
                android.graphics.Color.argb((0.9f * 255).toInt(), 217, 119, 6)
            )
        }
        // White Blinding Stars
        repeat(60) {
            drawFacetedShard(
                random.nextFloat() * size, random.nextFloat() * size, 
                random.nextFloat() * Math.PI.toFloat() * 2f, 
                35f + random.nextFloat() * 20f, 10f + random.nextFloat() * 5f, 
                android.graphics.Color.argb((0.9f * 255).toInt(), 255, 255, 255), 
                android.graphics.Color.argb((0.9f * 255).toInt(), 203, 213, 225)
            )
        }
    }

    // Final Pass: Shatter lines
    val shatterPaint = NativePaint()
    shatterPaint.isAntiAlias = true
    shatterPaint.strokeWidth = 1.5f
    shatterPaint.color = android.graphics.Color.argb((0.8f * 255).toInt(), 0, 0, 0)
    repeat(250) {
        canvas.drawLine(
            random.nextFloat() * size, random.nextFloat() * size,
            random.nextFloat() * size, random.nextFloat() * size,
            shatterPaint
        )
    }

    return bitmap
}
