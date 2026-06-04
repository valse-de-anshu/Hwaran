package com.ballade.hwaran.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import com.ballade.hwaran.ui.theme.LocalBatterySaving
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * A refined, smaller JellyBall mascot.
 * Removed over-engineered effects to match the clean HTML/CSS source.
 */
@Composable
fun JellyBall(
    modifier: Modifier = Modifier, 
    enableJump: Boolean = true, 
    isTrapped: Boolean = false, 
    isHappy: Boolean = false, 
    isPreviewMode: Boolean = false,
    lookUp: Boolean = false
) {
    // Scaled down sizes
    val stageSize = 120f
    val bodySize = 64f
    
    // Animation State
    var time by remember { mutableFloatStateOf(0f) }
    var jumpTimer by remember { mutableFloatStateOf(0f) }
    var isJumping by remember { mutableStateOf(false) }
    var jumpProgress by remember { mutableFloatStateOf(0f) }

    // Physics State (Scaled)
    var currentEyeX by remember { mutableFloatStateOf(0f) }
    var currentEyeY by remember { mutableFloatStateOf(0f) }
    var targetEyeX by remember { mutableFloatStateOf(0f) }
    var targetEyeY by remember { mutableFloatStateOf(0f) }
    var eyeVelX by remember { mutableFloatStateOf(0f) }
    var eyeVelY by remember { mutableFloatStateOf(0f) }
    var eyeSpring by remember { mutableFloatStateOf(0.05f) }
    val eyeFriction = 0.8f

    var isBlinking by remember { mutableStateOf(false) }
    var isShySquint by remember { mutableStateOf(false) }
    var blushActive by remember { mutableStateOf(false) }
    var shiverOffset by remember { mutableFloatStateOf(0f) }

    val batterySaving = LocalBatterySaving.current

    // Swirl — suspended in battery save mode (zero GPU cost)
    val swirlTransition = rememberInfiniteTransition(label = "swirl")
    val swirlRotation by if (batterySaving) {
        remember { mutableFloatStateOf(0f) } // fixed, no animation
    } else {
        swirlTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing)),
            label = "swirl"
        ).let { state -> remember { state } }
    }

    if (!isPreviewMode && !batterySaving) {
        LaunchedEffect(Unit) {
            var lastFrameTime = 0L
            while (true) {
                withFrameNanos { frameTime ->
                    if (lastFrameTime == 0L) lastFrameTime = frameTime
                    val elapsedMs = (frameTime - lastFrameTime) / 1_000_000f
                    lastFrameTime = frameTime

                    time += 0.04f
                    jumpTimer += elapsedMs

                    val targetJumpTime = if (isHappy) 300f else 6000f
                    if ((enableJump || isHappy) && jumpTimer > targetJumpTime && !isJumping) {
                        isJumping = true
                        jumpProgress = 0f
                    }

                    if (isJumping) {
                        jumpProgress += if (isHappy) 0.024f else 0.012f
                        if (jumpProgress >= 1.0f) {
                            isJumping = false
                            jumpTimer = 0f
                        }
                    }

                    val dx = targetEyeX - currentEyeX
                    val dy = targetEyeY - currentEyeY
                    eyeVelX += dx * eyeSpring
                    eyeVelY += dy * eyeSpring
                    eyeVelX *= eyeFriction
                    eyeVelY *= eyeFriction
                    currentEyeX += eyeVelX
                    currentEyeY += eyeVelY
                }
            }
        }
    }

    var isLaughing by remember { mutableStateOf(false) }
    if (!isPreviewMode && !batterySaving) {
        LaunchedEffect(isHappy, lookUp) {
            if (isHappy) {
                targetEyeY = -5f
                isShySquint = true
                blushActive = true
                while(isHappy) {
                    isLaughing = Math.random() > 0.3 // 70% chance to squint when looking around
                    targetEyeX = (Math.random().toFloat() - 0.5f) * 10f
                    delay((400L..1200L).random())
                    isLaughing = false
                    delay((200L..600L).random())
                }
            } else if (lookUp) {
                targetEyeY = -25f
                targetEyeX = 0f
                isShySquint = false
                blushActive = false
                isLaughing = false
                eyeSpring = 0.1f
            } else {
                isLaughing = false
                isShySquint = false
                blushActive = false
            }
        }

        LaunchedEffect(Unit) {
            // Initial state
            targetEyeX = 0f
            targetEyeY = 0f
            var courageLevel = 0f // 0 to 1
            
            if (isTrapped) {
                targetEyeY = -25f
                targetEyeX = 0f
                isBlinking = true
                isShySquint = true
                
                for (i in 0..20) {
                    shiverOffset = (Math.random().toFloat() - 0.5f) * 8f
                    delay(50)
                }
                shiverOffset = 0f
                
                isBlinking = false
                delay(500)
                targetEyeY = 15f
                blushActive = true
                delay(1000)
            }

            while (true) {
                if (isHappy || lookUp) {
                    delay(100)
                    continue
                }
                val roll = Math.random()
                
                if (courageLevel > 0.8f) {
                    // He gathered courage! Hold eye contact.
                    targetEyeX = 0f
                    targetEyeY = 0f
                    eyeSpring = 0.08f
                    isShySquint = false
                    blushActive = false
                    
                    // Hold for a long time
                    delay((2000 + Math.random() * 3000).toLong())
                    
                    // Courage drops after a long hold
                    courageLevel = 0f
                    
                    // Blinks and looks away out of sudden shyness
                    isBlinking = true
                    delay(120)
                    isBlinking = false
                    
                    targetEyeX = if (Math.random() > 0.5) 15f else -15f
                    targetEyeY = 12f
                    isShySquint = true
                    blushActive = true
                    delay((800 + Math.random() * 1000).toLong())
                } else if (roll < 0.4) {
                    // Thinking / Anxious
                    // Looks down or slightly to the side
                    isBlinking = true
                    delay(100)
                    isBlinking = false
                    
                    targetEyeX = (Math.random().toFloat() - 0.5f) * 20f
                    targetEyeY = 15f
                    eyeSpring = 0.05f
                    isShySquint = false
                    blushActive = false
                    
                    delay((1500 + Math.random() * 2000).toLong())
                    courageLevel += 0.2f
                } else if (roll < 0.7) {
                    // Hesitant glance at you
                    targetEyeX = 0f
                    targetEyeY = 0f
                    eyeSpring = 0.12f
                    isShySquint = false
                    blushActive = false
                    
                    // Very brief glance
                    delay((300 + Math.random() * 500).toLong())
                    
                    // Immediately shy away
                    isBlinking = true
                    delay(100)
                    isBlinking = false
                    
                    targetEyeX = if (Math.random() > 0.5) 14f else -14f
                    targetEyeY = 10f
                    eyeSpring = 0.15f
                    isShySquint = true
                    blushActive = true
                    
                    delay((1000 + Math.random() * 1500).toLong())
                    courageLevel += 0.3f
                } else {
                    // Deep in thought, small eye darting
                    targetEyeX += (Math.random().toFloat() - 0.5f) * 10f
                    targetEyeY += (Math.random().toFloat() - 0.5f) * 10f
                    targetEyeX = targetEyeX.coerceIn(-15f, 15f)
                    targetEyeY = targetEyeY.coerceIn(-5f, 15f)
                    eyeSpring = 0.04f
                    isShySquint = false
                    blushActive = false
                    
                    delay((800 + Math.random() * 1200).toLong())
                    courageLevel += 0.1f
                }
            }
        }
    } else if (!batterySaving) {
        // PREVIEW MODE: Minimal animation
        val breathingTransition = rememberInfiniteTransition(label = "breathing")
        val breathScale by breathingTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "breath"
        )
        LaunchedEffect(Unit) {
            currentEyeY = -5f
            isBlinking = false
            blushActive = true
            isLaughing = true
            while(true) {
                delay(3000)
                isBlinking = true
                delay(150)
                isBlinking = false
            }
        }
    }

    // Animation Transforms
    var sX = if (isPreviewMode) 1f else 1f + sin(time * 2.5f) * 0.015f
    var sY = if (isPreviewMode) 1f else 1f - sin(time * 2.5f) * 0.015f
    var translateY = 0f
    var shadS = if (isPreviewMode) 1f else 1f + sin(time * 2.5f) * 0.05f
    var shadO = if (isPreviewMode) 0.2f else 0.2f + sin(time * 2.5f) * 0.03f

    if (isJumping) {
        if (jumpProgress < 0.25f) {
            val p = jumpProgress / 0.25f
            val squish = sin(p * PI.toFloat())
            sX += squish * 0.12f
            sY -= squish * 0.08f
        } else if (jumpProgress < 0.45f) {
            val p = (jumpProgress - 0.25f) / 0.2f
            val jumpHeight = sin(p * PI.toFloat())
            translateY = -20f * jumpHeight // Scaled jump
            sX -= jumpHeight * 0.08f
            sY += jumpHeight * 0.12f
            shadS -= jumpHeight * 0.3f
            shadO -= jumpHeight * 0.1f
        } else if (jumpProgress < 0.65f) {
            val p = (jumpProgress - 0.45f) / 0.2f
            val impact = sin(p * PI.toFloat())
            sX += impact * 0.18f
            sY -= impact * 0.12f
            shadS += impact * 0.2f
        } else if (jumpProgress < 1.0f) {
            val p = (jumpProgress - 0.65f) / 0.35f
            val settle = sin(p * PI.toFloat()) * exp(-p * 3f)
            sX -= settle * 0.05f
            sY += settle * 0.05f
        }
    }

    val blushAlpha by animateFloatAsState(if (blushActive) 0.7f else 0f, tween(1000), label = "blush")

    Box(
        modifier = modifier.size(stageSize.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floor Shadow (Simplified)
        Canvas(modifier = Modifier.fillMaxWidth().height(30.dp).padding(bottom = 15.dp)) {
            val width = 40f
            val height = 5f
            drawOval(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = shadO),
                    0.7f to Color.Transparent,
                    center = center
                ),
                topLeft = Offset(center.x - (width * shadS) / 2, center.y - (height * shadS) / 2),
                size = Size(width * shadS, height * shadS)
            )
        }

        // Mascot Body
        Box(
            modifier = Modifier
                .size(bodySize.dp)
                .offset(x = shiverOffset.dp, y = translateY.dp + shiverOffset.dp)
                .graphicsLayer {
                    scaleX = sX
                    scaleY = sY
                    transformOrigin = TransformOrigin(0.5f, 1.0f)
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.width / 2

                // a. Body Gradient (More Opaque as requested)
                val mainOrbBrush = Brush.radialGradient(
                    0.0f to Color(140, 140, 140, (255 * 0.45f).toInt()),
                    0.5f to Color(40, 40, 40, (255 * 0.7f).toInt()),
                    1.0f to Color(10, 10, 10, (255 * 0.9f).toInt()),
                    center = Offset(size.width * 0.35f, size.height * 0.35f),
                    radius = size.width * 0.8f
                )
                drawCircle(brush = mainOrbBrush)

                // Simulate inset shadows
                drawCircle(
                    brush = Brush.radialGradient(
                        0.8f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.5f),
                        center = Offset(size.width * 0.5f, size.height * 0.5f)
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        0.7f to Color.White.copy(alpha = 0.2f),
                        1.0f to Color.Transparent,
                        center = Offset(size.width * 0.2f, size.height * 0.2f),
                        radius = size.width * 0.6f
                    )
                )

                // b. Rim Light
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.White.copy(alpha = 0.15f),
                        0.85f to Color.Transparent,
                        center = Offset(r * 0.5f, r * 0.5f),
                        radius = r * 0.9f
                    )
                )

                // c. Internal Smoke
                clipPath(Path().apply { addOval(Rect(Offset.Zero, size)) }) {
                    rotate(swirlRotation) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.0f to Color.White.copy(alpha = 0.12f),
                                0.5f to Color.Transparent,
                                center = Offset(center.x * 0.8f, center.y * 0.8f)
                            ),
                            radius = size.width * 0.7f,
                            center = center
                        )
                    }
                }

                // d. Clean Edge
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    style = Stroke(width = 0.5.dp.toPx())
                )
            }

            // Facial Features
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = currentEyeX
                        translationY = currentEyeY
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Eyes (Scaled)
                    Row(
                        modifier = Modifier.width(if (isLaughing) 24.dp else 19.dp), 
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MascotEye(isBlinking, isShySquint, isLaughing)
                        MascotEye(isBlinking, isShySquint, isLaughing)
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Blush (Scaled & Simplified)
                    Row(
                        modifier = Modifier.width(48.dp).graphicsLayer { alpha = blushAlpha },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MascotBlush()
                        MascotBlush()
                    }
                }
            }
        }
    }
}

@Composable
fun MascotEye(isBlinking: Boolean, isShySquint: Boolean, isLaughing: Boolean = false) {
    val baseHeight = when {
        isLaughing -> 3.dp
        isShySquint -> 7.dp
        else -> 10.dp
    }
    val baseWidth = if (isLaughing) 6.dp else 4.dp
    val scaleY by animateFloatAsState(if (isBlinking) 0f else 1f, tween(100), label = "eye")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(baseWidth, baseHeight)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOval(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.8f),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = 8.dp.toPx()
                ),
                size = Size(size.width * 2.2f, size.height * 1.4f),
                topLeft = Offset(-size.width * 0.6f, -size.height * 0.2f),
                alpha = scaleY
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.scaleY = scaleY }
                .background(Color.White, RoundedCornerShape(10.dp))
        )
    }
}

@Composable
fun MascotBlush() {
    Canvas(modifier = Modifier.size(24.dp, 12.dp).blur(2.dp)) { // More diffuse blush
        drawOval(
            brush = Brush.radialGradient(
                0.0f to Color(255, 40, 100, (0.80f * 255).toInt()),
                0.7f to Color(255, 80, 160, (0.30f * 255).toInt()),
                1.0f to Color.Transparent,
                center = center
            )
        )
    }
}
