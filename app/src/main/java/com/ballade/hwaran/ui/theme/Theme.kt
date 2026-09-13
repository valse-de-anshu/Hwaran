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



private val ObsidianColorScheme = darkColorScheme(
    primary = AccentTitanium,
    secondary = AccentTitaniumDark,
    background = ObsidianBackground,
    surface = ObsidianSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onPrimary = ObsidianBackground,
    onSecondary = ObsidianBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder
)

private val PitchBlackColorScheme = darkColorScheme(
    primary = Color.White,
    secondary = AccentTitanium,
    background = PitchBlackBackground,
    surface = PitchBlackSurface,
    surfaceVariant = PitchBlackSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    outline = Color(0x24FFFFFF)
)

private val NordicSlateColorScheme = darkColorScheme(
    primary = AccentTitanium,
    secondary = AccentTitaniumDark,
    background = NordicSlateBackground,
    surface = NordicSlateSurface,
    surfaceVariant = NordicSlateSurfaceVariant,
    onPrimary = NordicSlateBackground,
    onSecondary = NordicSlateBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder
)

private val SmokedCharcoalColorScheme = darkColorScheme(
    primary = Color(0xFFDCD7C9),
    secondary = Color(0xFFA27B5C),
    background = SmokedCharcoalBackground,
    surface = SmokedCharcoalSurface,
    surfaceVariant = SmokedCharcoalSurfaceVariant,
    onPrimary = SmokedCharcoalBackground,
    onSecondary = SmokedCharcoalBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder
)

// Legacy alias
private val PureDarkColorScheme = ObsidianColorScheme
private val BlueberryColorScheme = NordicSlateColorScheme
private val SnowfallColorScheme = SmokedCharcoalColorScheme
private val GrapeColorScheme = ObsidianColorScheme

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
        name = "Obsidian Onyx",
        colorScheme = ObsidianColorScheme,
        gradient = null,
        previewBrush = androidx.compose.ui.graphics.SolidColor(ObsidianBackground)
    ),
    AppThemeSpec(
        id = 5,
        name = "OLED Pitch Black",
        colorScheme = PitchBlackColorScheme,
        gradient = null,
        previewBrush = androidx.compose.ui.graphics.SolidColor(PitchBlackBackground)
    ),
    AppThemeSpec(
        id = 6,
        name = "Nordic Slate",
        colorScheme = NordicSlateColorScheme,
        gradient = null,
        previewBrush = androidx.compose.ui.graphics.SolidColor(NordicSlateBackground)
    ),
    AppThemeSpec(
        id = 7,
        name = "Smoked Charcoal",
        colorScheme = SmokedCharcoalColorScheme,
        gradient = null,
        previewBrush = androidx.compose.ui.graphics.SolidColor(SmokedCharcoalBackground)
    )
)

@Composable
fun HwaranTheme(
    appTheme: Int = 0,
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    batterySaving: Boolean = false,
    usePillAsHighlight: Boolean = false,
    pillHighlightColor: Long = 0xFFE2E8F0L,
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