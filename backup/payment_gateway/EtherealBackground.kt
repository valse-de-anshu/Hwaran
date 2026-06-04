package com.ballade.hwaran.ui.screens.archive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

data class MediaFragment(
    val id: String,
    val content: @Composable () -> Unit,
    val speed: Float,
    val xDp: Float, 
    val yOffsetDp: Float, 
    val scale: Float
)

@Composable
fun EtherealBackground(scrollState: ScrollState, activeHover: String?) {
    val maxScrollPx = 4000f 
    val scrollValue = scrollState.value.toFloat()
    val scrollProgress = (scrollValue / maxScrollPx).coerceIn(0f, 1f)
    
    val bgOpacity = 1f - (scrollProgress * 0.8f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030303))
    ) {
        val targetGlowColor = when (activeHover) {
            "lifetime" -> Color(230, 200, 150, 38)
            "ads" -> Color(200, 200, 200, 20)
            "themes" -> Color(100, 150, 255, 30)
            else -> Color(30, 30, 30, 102)
        }

        val animatedGlowColor by animateColorAsState(
            targetValue = targetGlowColor,
            animationSpec = tween(durationMillis = 1500),
            label = "GlowColor"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(600.dp) 
                .offset(y = (-100).dp)
                .blur(120.dp)
                .graphicsLayer { alpha = 0.2f }
                .drawBehind {
                    drawCircle(color = animatedGlowColor, radius = size.minDimension / 2)
                }
        )

        val fragments = listOf(
            MediaFragment("m1", { MangaEyePanel() }, 1.1f, -39f, 50f, 0.9f),
            MediaFragment("a1", { CinematicVideoFrame() }, 0.8f, 175f, 200f, 1f),
            MediaFragment("b1", { BookPage() }, 1.3f, 19f, 400f, 0.85f),
            MediaFragment("v1", { AudioWaveform() }, 0.9f, 234f, 550f, 1.1f),
            MediaFragment("mh1", { TypographyElement() }, 1.5f, -19f, 700f, 0.95f),
            MediaFragment("m2", { VinylRecord() }, 0.6f, 214f, 900f, 1.2f),
            MediaFragment("a2", { CinematicVideoFrame() }, 1.2f, 39f, 1100f, 0.8f),
            MediaFragment("b2", { BookPage() }, 1.0f, 273f, 1300f, 0.9f)
        )

        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = bgOpacity }) {
            fragments.forEach { fragment ->
                val currentTranslateY = -(scrollValue * 0.25f * fragment.speed)

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = fragment.xDp.dp.roundToPx(),
                                y = fragment.yOffsetDp.dp.roundToPx()
                            )
                        }
                        .graphicsLayer {
                            translationY = currentTranslateY
                            scaleX = fragment.scale
                            scaleY = fragment.scale
                        }
                ) {
                    fragment.content()
                }
            }
        }
    }
}