package com.ballade.hwaran.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SecondaryToggle(
    activeTab: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFD481D2)
) {
    Row(
        modifier = modifier
            .width(40.dp)
            .height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotIndicator(isActive = activeTab == 0, activeColor = activeColor)
        DotIndicator(isActive = activeTab == 1, activeColor = activeColor)
    }
}

@Composable
private fun DotIndicator(
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val size by animateFloatAsState(
        targetValue = if (isActive) 6f else 4f,
        animationSpec = tween(300),
        label = "size"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.3f,
        animationSpec = tween(300),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(size.dp)
            .alpha(alpha)
            .background(if (isActive) activeColor else Color.White, CircleShape)
    )
}
