package com.ballade.hwaran.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedEqualizer(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    barCount: Int = 3
) {
    val isBatterySaving = com.ballade.hwaran.ui.theme.LocalBatterySaving.current
    val staticHeights = remember { listOf(0.4f, 0.85f, 0.55f, 0.75f, 0.45f) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { i ->
            val heightScale = if (isBatterySaving) {
                staticHeights[i % staticHeights.size]
            } else {
                val infiniteTransition = rememberInfiniteTransition(label = "equalizer_$i")
                val animatedHeight by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 400 + (i * 150),
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "barHeight"
                )
                animatedHeight
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightScale)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(color)
            )
        }
    }
}
