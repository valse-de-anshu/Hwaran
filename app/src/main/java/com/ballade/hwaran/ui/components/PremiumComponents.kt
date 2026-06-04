package com.ballade.hwaran.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun PremiumGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF0F0F13).copy(alpha = 0.55f)) // Deep charcoal glass
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(48.dp)
                .background(Color.Black.copy(alpha = 0.2f))
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    formatValue: (Float) -> String = { "${(it * 100).toInt()}%" }
) {
    // Local draft tracks finger exactly; commits to VM only when released
    var draft by remember(value) { mutableStateOf(value) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val thumbScale by animateFloatAsState(
        targetValue = if (isPressed) 1.3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "thumb_scale"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatValue(draft),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = draft,
            onValueChange = { draft = it },           // instant, no VM call
            onValueChangeFinished = { onValueChange(draft) }, // commit on release
            valueRange = valueRange,
            interactionSource = interactionSource,
            modifier = Modifier.height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                inactiveTrackColor = Color.White.copy(alpha = 0.05f)
            ),
            thumb = {
                val primaryColor = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = thumbScale
                            scaleY = thumbScale
                            shadowElevation = if (isPressed) 16f else 4f
                            ambientShadowColor = primaryColor
                            spotShadowColor = primaryColor
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier.height(3.dp).clip(RoundedCornerShape(1.5.dp))
                )
            }
        )
    }
}

@Composable
fun HolographicCloverPanel(
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn(tween(300)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(300)
        ) + slideInHorizontally(
            initialOffsetX = { it / 2 },
            animationSpec = tween(300)
        ),
        exit = fadeOut(tween(200)) + scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(200)
        ) + slideOutHorizontally(
            targetOffsetX = { it / 2 },
            animationSpec = tween(200)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .width(280.dp)
                .heightIn(max = if (isLandscape) 320.dp else 600.dp)
        ) {
            PremiumGlassPanel {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun SidebarIcon(
    icon: ImageVector? = null,
    isSelected: Boolean,
    symbol: String? = null,
    alwaysBright: Boolean = false,
    showHighlight: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else if (isSelected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "icon_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isSelected || alwaysBright) 1f else 0.4f,
        animationSpec = tween(200),
        label = "icon_alpha"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .then(
                if (showHighlight && isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                } else Modifier
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected && showHighlight) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(24.dp)
            )
        } else if (symbol != null) {
            Text(
                text = symbol,
                color = if (isSelected && showHighlight) MaterialTheme.colorScheme.primary else Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}