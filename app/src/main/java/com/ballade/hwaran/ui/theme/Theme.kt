package com.ballade.hwaran.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalAppGradient = staticCompositionLocalOf<Brush?> { null }
val LocalBatterySaving = staticCompositionLocalOf<Boolean> { false }



private val PureDarkColorScheme = darkColorScheme(
    primary = PureDarkPrimary,
    secondary = PureDarkPrimary, // Reverted from PureDarkSecondary to match primary for UI elements
    background = PureDarkBackground,
    surface = PureDarkSurface,
    surfaceVariant = PureDarkSurfaceVariant,
    onPrimary = PureDarkBackground,
    onSecondary = PureDarkBackground,
    onBackground = PureDarkText,
    onSurface = PureDarkText,
    onSurfaceVariant = PureDarkTextMuted,
    outline = PureDarkBorder
)



private val BlueberryColorScheme = darkColorScheme(
    primary = BlueberryPrimary,
    secondary = BlueberryPrimary,
    background = BlueberryBackground,
    surface = BlueberrySurface,
    surfaceVariant = BlueberrySurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    outline = BlueberrySurfaceVariant
)

private val SnowfallColorScheme = darkColorScheme(
    primary = SnowfallPrimary,
    secondary = SnowfallPrimary,
    background = SnowfallBackground,
    surface = SnowfallSurface,
    surfaceVariant = SnowfallSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    outline = SnowfallSurfaceVariant
)

private val GrapeColorScheme = darkColorScheme(
    primary = GrapePrimary,
    secondary = GrapePrimary,
    background = GrapeBackground,
    surface = GrapeSurface,
    surfaceVariant = GrapeSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    outline = GrapeSurfaceVariant
)

@Composable
fun animateColorScheme(targetColorScheme: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    val primary by animateColorAsState(targetValue = targetColorScheme.primary, animationSpec = tween(500), label = "primary")
    val secondary by animateColorAsState(targetValue = targetColorScheme.secondary, animationSpec = tween(500), label = "secondary")
    val background by animateColorAsState(targetValue = targetColorScheme.background, animationSpec = tween(500), label = "background")
    val surface by animateColorAsState(targetValue = targetColorScheme.surface, animationSpec = tween(500), label = "surface")
    val surfaceVariant by animateColorAsState(targetValue = targetColorScheme.surfaceVariant, animationSpec = tween(500), label = "surfaceVariant")
    val onPrimary by animateColorAsState(targetValue = targetColorScheme.onPrimary, animationSpec = tween(500), label = "onPrimary")
    val onSecondary by animateColorAsState(targetValue = targetColorScheme.onSecondary, animationSpec = tween(500), label = "onSecondary")
    val onBackground by animateColorAsState(targetValue = targetColorScheme.onBackground, animationSpec = tween(500), label = "onBackground")
    val onSurface by animateColorAsState(targetValue = targetColorScheme.onSurface, animationSpec = tween(500), label = "onSurface")
    val onSurfaceVariant by animateColorAsState(targetValue = targetColorScheme.onSurfaceVariant, animationSpec = tween(500), label = "onSurfaceVariant")
    val outline by animateColorAsState(targetValue = targetColorScheme.outline, animationSpec = tween(500), label = "outline")

    return targetColorScheme.copy(
        primary = primary,
        secondary = secondary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        onPrimary = onPrimary,
        onSecondary = onSecondary,
        onBackground = onBackground,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline
    )
}

data class AppThemeSpec(
    val id: Int,
    val name: String,
    val colorScheme: androidx.compose.material3.ColorScheme,
    val gradient: Brush?,
    val previewBrush: Brush
)

val AVAILABLE_APP_THEMES: List<AppThemeSpec> = listOf(
    AppThemeSpec(
        id = 1,
        name = "Dark",
        colorScheme = PureDarkColorScheme,
        gradient = null,
        previewBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF14131F))
    ),
    AppThemeSpec(
        id = 5,
        name = "Blueberry",
        colorScheme = BlueberryColorScheme,
        gradient = Brush.verticalGradient(listOf(Color(0xFF15326D), Color(0xFF0C1D40), Color(0xFF071126), Color(0xFF030812))),
        previewBrush = Brush.verticalGradient(listOf(Color(0xFF15326D), Color(0xFF0C1D40), Color(0xFF071126), Color(0xFF030812)))
    ),
    AppThemeSpec(
        id = 6,
        name = "Snowfall",
        colorScheme = SnowfallColorScheme,
        gradient = Brush.verticalGradient(listOf(Color(0xFF404C55), Color(0xFF27323B), Color(0xFF161C22), Color(0xFF0B0E11))),
        previewBrush = Brush.verticalGradient(listOf(Color(0xFF404C55), Color(0xFF27323B), Color(0xFF161C22), Color(0xFF0B0E11)))
    ),
    AppThemeSpec(
        id = 7,
        name = "Grape",
        colorScheme = GrapeColorScheme,
        gradient = Brush.verticalGradient(listOf(Color(0xFF7A6284), Color(0xFF52425C), Color(0xFF382B3F), Color(0xFF1F1823), Color(0xFF0C080D))),
        previewBrush = Brush.verticalGradient(listOf(Color(0xFF7A6284), Color(0xFF52425C), Color(0xFF382B3F), Color(0xFF1F1823), Color(0xFF0C080D)))
    )
)

@Composable
fun HwaranTheme(
    appTheme: Int = 0,
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    batterySaving: Boolean = false,
    usePillAsHighlight: Boolean = false,
    pillHighlightColor: Long = 0xFF7A6284L,
    content: @Composable () -> Unit
) {
    val themeSpec = AVAILABLE_APP_THEMES.find { it.id == appTheme } ?: AVAILABLE_APP_THEMES.first()
    val baseColorScheme = themeSpec.colorScheme

    val colorScheme = if (usePillAsHighlight) {
        baseColorScheme.copy(
            primary = Color(pillHighlightColor),
            secondary = Color(pillHighlightColor)
        )
    } else {
        baseColorScheme
    }

    val animatedColorScheme = animateColorScheme(colorScheme)
    val appGradient = themeSpec.gradient

    CompositionLocalProvider(
        LocalAppGradient provides appGradient,
        LocalBatterySaving provides batterySaving
    ) {
        MaterialTheme(
            colorScheme = animatedColorScheme,
            typography = Typography,
            content = content
        )
    }
}