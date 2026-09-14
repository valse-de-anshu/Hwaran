package com.ballade.hwaran.frontend.player.novel

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class NovelSettingTab {
    TYPOGRAPHY,
    THEME,
    LAYOUT,
    DISPLAY
}

@Composable
fun NovelReaderSettingsPill(
    activeTab: NovelSettingTab?,
    onTabSelected: (NovelSettingTab?) -> Unit,
    // Typography
    currentFont: NovelFontFamily,
    onFontChange: (NovelFontFamily) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeightMultiplier: Float,
    onLineHeightChange: (Float) -> Unit,
    paragraphSpacingDp: Int,
    onParagraphSpacingChange: (Int) -> Unit,
    horizontalMarginDp: Int,
    onHorizontalMarginChange: (Int) -> Unit,
    // Theme
    currentTheme: NovelTheme,
    onThemeChange: (NovelTheme) -> Unit,
    // Layout Mode
    readMode: NovelReadMode,
    onReadModeChange: (NovelReadMode) -> Unit,
    // Display Screen
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    brightnessOverride: Float?,
    onBrightnessOverrideChange: (Float?) -> Unit,
    glowColor: Color = Color(0xFFE6E8EC),
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // ── Floating Setting Popup Card (Live Preview) ──
        AnimatedVisibility(
            visible = activeTab != null,
            enter = fadeIn(tween(160)) + slideInHorizontally(tween(180)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutHorizontally(tween(160)) { it / 2 },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 76.dp)
        ) {
            if (activeTab != null) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF14131E).copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 330.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume click to prevent dismiss */ }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // ── Popup Header ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val headerTitle = when (activeTab) {
                                NovelSettingTab.TYPOGRAPHY -> "Typography & Sizing"
                                NovelSettingTab.THEME -> "Reading Theme"
                                NovelSettingTab.LAYOUT -> "Reading Layout"
                                NovelSettingTab.DISPLAY -> "Display Screen"
                            }

                            Text(
                                text = headerTitle,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { onTabSelected(null) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // ── Tab Content ──
                        when (activeTab) {
                            // 1. TYPOGRAPHY
                            NovelSettingTab.TYPOGRAPHY -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Font Family Chips
                                    Text(
                                        text = "Font Family",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        NovelFontFamily.entries.forEach { font ->
                                            CompactPresetPill(
                                                label = font.label,
                                                isSelected = currentFont == font,
                                                glowColor = glowColor,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onFontChange(font)
                                                }
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                                    // Font Size
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Size (${fontSizeSp.roundToInt()}sp)",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            MiniStepButton(text = "–") {
                                                onFontSizeChange((fontSizeSp - 1f).coerceIn(12f, 34f))
                                            }
                                            MiniStepButton(text = "+") {
                                                onFontSizeChange((fontSizeSp + 1f).coerceIn(12f, 34f))
                                            }
                                        }
                                    }
                                    Slider(
                                        value = fontSizeSp,
                                        onValueChange = onFontSizeChange,
                                        valueRange = 12f..34f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = glowColor,
                                            activeTrackColor = glowColor,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                                        )
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                                    // Line Height
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Line Height (${String.format("%.2f", lineHeightMultiplier)}x)",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Slider(
                                        value = lineHeightMultiplier,
                                        onValueChange = onLineHeightChange,
                                        valueRange = 1.2f..2.4f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = glowColor,
                                            activeTrackColor = glowColor,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                                        )
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                                    // Margins
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Margin (${horizontalMarginDp}dp)",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Slider(
                                        value = horizontalMarginDp.toFloat(),
                                        onValueChange = { onHorizontalMarginChange(it.roundToInt()) },
                                        valueRange = 12f..36f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = glowColor,
                                            activeTrackColor = glowColor,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                                        )
                                    )
                                }
                            }

                            // 2. THEME
                            NovelSettingTab.THEME -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Reading Canvas Presets",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    NovelTheme.entries.forEach { theme ->
                                        val isSelected = currentTheme == theme
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onThemeChange(theme)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(theme.bg)
                                                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                                    )
                                                    Text(
                                                        text = theme.label,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = theme.bg,
                                                    border = BorderStroke(1.dp, theme.border),
                                                    modifier = Modifier.padding(2.dp)
                                                ) {
                                                    Text(
                                                        text = "Aa",
                                                        color = theme.text,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. LAYOUT
                            NovelSettingTab.LAYOUT -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "Page Display Mode",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    CompactLayoutModeChip(
                                        title = "Continuous Scroll",
                                        subtitle = "Vertical continuous reading flow",
                                        icon = Icons.Rounded.SwapVert,
                                        isSelected = readMode == NovelReadMode.VERTICAL_SCROLL,
                                        glowColor = glowColor,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onReadModeChange(NovelReadMode.VERTICAL_SCROLL)
                                        }
                                    )

                                    CompactLayoutModeChip(
                                        title = "Paginated Book",
                                        subtitle = "Horizontal page turns with tap margins",
                                        icon = Icons.Rounded.AutoStories,
                                        isSelected = readMode == NovelReadMode.PAGINATED,
                                        glowColor = glowColor,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onReadModeChange(NovelReadMode.PAGINATED)
                                        }
                                    )
                                }
                            }

                            // 4. DISPLAY
                            NovelSettingTab.DISPLAY -> {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    // Keep Screen On
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Keep Screen On",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Prevent screen from sleeping",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 10.sp
                                            )
                                        }

                                        Switch(
                                            checked = keepScreenOn,
                                            onCheckedChange = onKeepScreenOnChange,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color(0xFFE6E8EC),
                                                checkedTrackColor = Color(0xFF222631),
                                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                                            )
                                        )
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                                    // Brightness Override
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Screen Brightness",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (brightnessOverride == null) Color.White.copy(alpha = 0.08f) else Color(0xFF222631),
                                                border = BorderStroke(1.dp, if (brightnessOverride == null) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.20f)),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        if (brightnessOverride == null) {
                                                            onBrightnessOverrideChange(0.6f)
                                                        } else {
                                                            onBrightnessOverrideChange(null)
                                                        }
                                                    }
                                            ) {
                                                Text(
                                                    text = if (brightnessOverride == null) "System Auto" else "${(brightnessOverride * 100).roundToInt()}%",
                                                    color = if (brightnessOverride == null) Color.White.copy(alpha = 0.6f) else Color(0xFFE6E8EC),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        if (brightnessOverride != null) {
                                            Slider(
                                                value = brightnessOverride,
                                                onValueChange = onBrightnessOverrideChange,
                                                valueRange = 0.05f..1.0f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = glowColor,
                                                    activeTrackColor = glowColor,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Vertical Pill (Fixed on the far right) ──
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF14131E).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 5.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Typography & Font Sizing
                VerticalPillIcon(
                    icon = Icons.Rounded.FormatSize,
                    contentDescription = "Typography",
                    isSelected = activeTab == NovelSettingTab.TYPOGRAPHY,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == NovelSettingTab.TYPOGRAPHY) null else NovelSettingTab.TYPOGRAPHY)
                    }
                )

                // 2. Reading Theme
                VerticalPillIcon(
                    icon = Icons.Rounded.FormatColorFill,
                    contentDescription = "Theme",
                    isSelected = activeTab == NovelSettingTab.THEME,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == NovelSettingTab.THEME) null else NovelSettingTab.THEME)
                    }
                )

                // 3. Reading Layout
                VerticalPillIcon(
                    icon = Icons.Rounded.AutoStories,
                    contentDescription = "Layout",
                    isSelected = activeTab == NovelSettingTab.LAYOUT,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == NovelSettingTab.LAYOUT) null else NovelSettingTab.LAYOUT)
                    }
                )

                // 4. Display Screen
                VerticalPillIcon(
                    icon = Icons.Rounded.BrightnessMedium,
                    contentDescription = "Display Screen",
                    isSelected = activeTab == NovelSettingTab.DISPLAY,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == NovelSettingTab.DISPLAY) null else NovelSettingTab.DISPLAY)
                    }
                )
            }
        }
    }
}

@Composable
private fun VerticalPillIcon(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    glowColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) Color(0xFF222631) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)) else null,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CompactPresetPill(
    label: String,
    isSelected: Boolean,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.60f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }
}

@Composable
private fun MiniStepButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier
            .size(width = 28.dp, height = 24.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactLayoutModeChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    glowColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    color = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
