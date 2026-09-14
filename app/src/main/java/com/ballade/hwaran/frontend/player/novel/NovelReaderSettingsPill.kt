package com.ballade.hwaran.frontend.player.novel

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt

enum class NovelSettingTab {
    TYPOGRAPHY,
    THEME,
    LAYOUT,
    DISPLAY
}

data class CustomFontEntry(
    val name: String,
    val file: File,
    val fontFamily: FontFamily
)

// ── Minimal thin-track slider with zero lag ────────────────────────────────────
@Composable
fun MinimalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White,
    thumbColor: Color = Color.White,
    trackHeight: Dp = 2.5.dp,
    thumbRadius: Dp = 7.dp
) {
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackHeightPx = with(density) { trackHeight.toPx() }

    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height((thumbRadius * 2) + 12.dp)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val trackWidth = size.width - thumbRadiusPx * 2
                    if (trackWidth > 0) {
                        val fraction = ((down.position.x - thumbRadiusPx) / trackWidth).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                    isDragging = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            if (trackWidth > 0) {
                                val fraction = ((change.position.x - thumbRadiusPx) / trackWidth).coerceIn(0f, 1f)
                                val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                                onValueChange(newValue)
                            }
                            change.consume()
                        } else {
                            break
                        }
                    }
                    isDragging = false
                }
            }
            .drawBehind {
                val trackWidth = size.width - thumbRadiusPx * 2
                val centerY = size.height / 2f
                val fraction = if (valueRange.endInclusive > valueRange.start) {
                    ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                } else 0f
                val activeX = thumbRadiusPx + fraction * trackWidth

                // Inactive track (thin, sleek, rounded)
                drawLine(
                    color = trackColor.copy(alpha = 0.18f),
                    start = Offset(thumbRadiusPx, centerY),
                    end = Offset(size.width - thumbRadiusPx, centerY),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round
                )

                // Active track
                if (activeX > thumbRadiusPx) {
                    drawLine(
                        color = trackColor.copy(alpha = 0.9f),
                        start = Offset(thumbRadiusPx, centerY),
                        end = Offset(activeX, centerY),
                        strokeWidth = trackHeightPx,
                        cap = StrokeCap.Round
                    )
                }

                // Thumb circle with touch feedback
                if (isDragging) {
                    drawCircle(
                        color = thumbColor.copy(alpha = 0.22f),
                        radius = thumbRadiusPx * 1.8f,
                        center = Offset(activeX, centerY)
                    )
                }
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadiusPx,
                    center = Offset(activeX, centerY)
                )
            }
    )
}

@Composable
fun NovelReaderSettingsPill(
    activeTab: NovelSettingTab?,
    onTabSelected: (NovelSettingTab?) -> Unit,
    // Typography
    currentFont: NovelFontFamily,
    onFontChange: (NovelFontFamily) -> Unit,
    customFonts: List<CustomFontEntry> = emptyList(),
    selectedCustomFontName: String? = null,
    onSelectCustomFont: (String) -> Unit = {},
    onAddCustomFont: () -> Unit = {},
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeightMultiplier: Float,
    onLineHeightChange: (Float) -> Unit,
    paragraphSpacingDp: Int,
    onParagraphSpacingChange: (Int) -> Unit,
    horizontalMarginDp: Int,
    onHorizontalMarginChange: (Int) -> Unit,
    textAlign: TextAlign = TextAlign.Start,
    onTextAlignChange: (TextAlign) -> Unit = {},
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
    // Navigation & Quick Actions
    onShowToc: () -> Unit,
    onToggleBookmark: () -> Unit,
    isBookmarked: Boolean,
    glowColor: Color = Color(0xFFE6E8EC),
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // ── Floating Setting Popup Card ──
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
                    color = Color(0xFF14131E).copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 340.dp)
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
                                NovelSettingTab.TYPOGRAPHY -> "Typography"
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Font Family",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        // Custom Font Import Button
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = glowColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, glowColor.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onAddCustomFont()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = "Add Font",
                                                    tint = glowColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "Custom",
                                                    color = glowColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    // Preset Font chips
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        val chunked = NovelFontFamily.entries.chunked(3)
                                        chunked.forEach { row ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                row.forEach { font ->
                                                    val isSelected = selectedCustomFontName == null && currentFont == font
                                                    CompactPresetPill(
                                                        label = font.label,
                                                        isSelected = isSelected,
                                                        glowColor = glowColor,
                                                        modifier = Modifier.weight(1f),
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            onFontChange(font)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Custom Imported Fonts (if any exist)
                                    if (customFonts.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Imported Fonts",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            customFonts.chunked(2).forEach { row ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    row.forEach { cFont ->
                                                        val isSel = selectedCustomFontName == cFont.name
                                                        CompactPresetPill(
                                                            label = cFont.name,
                                                            isSelected = isSel,
                                                            glowColor = glowColor,
                                                            modifier = Modifier.weight(1f),
                                                            onClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                onSelectCustomFont(cFont.name)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                                    // Text Alignment
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Alignment",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            CompactPresetPill(
                                                label = "Left",
                                                isSelected = textAlign == TextAlign.Start,
                                                glowColor = glowColor,
                                                onClick = { onTextAlignChange(TextAlign.Start) }
                                            )
                                            CompactPresetPill(
                                                label = "Justify",
                                                isSelected = textAlign == TextAlign.Justify,
                                                glowColor = glowColor,
                                                onClick = { onTextAlignChange(TextAlign.Justify) }
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                                    // Font Size with +/- step
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Size",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "${fontSizeSp.roundToInt()}sp",
                                                color = glowColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            MiniStepButton(text = "–") {
                                                onFontSizeChange((fontSizeSp - 1f).coerceIn(12f, 34f))
                                            }
                                            MiniStepButton(text = "+") {
                                                onFontSizeChange((fontSizeSp + 1f).coerceIn(12f, 34f))
                                            }
                                        }
                                    }
                                    MinimalSlider(
                                        value = fontSizeSp,
                                        onValueChange = onFontSizeChange,
                                        valueRange = 12f..34f,
                                        trackColor = glowColor,
                                        thumbColor = glowColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                                    // Line Height
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Line Height",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            listOf(1.4f to "Compact", 1.65f to "Norm", 1.9f to "Relax", 2.2f to "Loose").forEach { (mult, label) ->
                                                val isSel = (lineHeightMultiplier - mult) in -0.05f..0.05f
                                                CompactPresetPill(
                                                    label = label,
                                                    isSelected = isSel,
                                                    glowColor = glowColor,
                                                    onClick = { onLineHeightChange(mult) }
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                                    // Margins
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Margin",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            listOf(14 to "Thin", 20 to "Norm", 28 to "Wide", 36 to "Max").forEach { (m, lbl) ->
                                                val isSel = horizontalMarginDp == m
                                                CompactPresetPill(
                                                    label = lbl,
                                                    isSelected = isSel,
                                                    glowColor = glowColor,
                                                    onClick = { onHorizontalMarginChange(m) }
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                                    // Paragraph Spacing
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Para Spacing",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "${paragraphSpacingDp}dp",
                                                color = glowColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            MiniStepButton(text = "–") {
                                                onParagraphSpacingChange((paragraphSpacingDp - 2).coerceIn(6, 40))
                                            }
                                            MiniStepButton(text = "+") {
                                                onParagraphSpacingChange((paragraphSpacingDp + 2).coerceIn(6, 40))
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. THEME
                            NovelSettingTab.THEME -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Canvas Theme",
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 10.sp,
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
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .background(theme.bg)
                                                            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
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
                                                    border = BorderStroke(1.dp, theme.border)
                                                ) {
                                                    Text(
                                                        text = "Aa",
                                                        color = theme.text,
                                                        fontSize = 10.sp,
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
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Page Display Mode",
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    CompactLayoutModeChip(
                                        title = "Continuous Scroll",
                                        subtitle = "Vertical reading flow",
                                        icon = Icons.Rounded.SwapVert,
                                        isSelected = readMode == NovelReadMode.VERTICAL_SCROLL,
                                        glowColor = glowColor,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onReadModeChange(NovelReadMode.VERTICAL_SCROLL)
                                        }
                                    )

                                    CompactLayoutModeChip(
                                        title = "Paginated",
                                        subtitle = "Horizontal page turns",
                                        icon = Icons.AutoMirrored.Rounded.MenuBook,
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
                                                text = "Prevent screen sleep",
                                                color = Color.White.copy(alpha = 0.45f),
                                                fontSize = 10.sp
                                            )
                                        }

                                        Switch(
                                            checked = keepScreenOn,
                                            onCheckedChange = onKeepScreenOnChange,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color(0xFFE6E8EC),
                                                checkedTrackColor = Color(0xFF2A2A3C),
                                                uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                                uncheckedTrackColor = Color.White.copy(alpha = 0.10f)
                                            )
                                        )
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                                    // Brightness
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Brightness",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (brightnessOverride == null) Color.White.copy(alpha = 0.08f) else Color(0xFF222631),
                                                border = BorderStroke(1.dp, if (brightnessOverride == null) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.20f)),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onBrightnessOverrideChange(if (brightnessOverride == null) 0.6f else null)
                                                    }
                                            ) {
                                                Text(
                                                    text = if (brightnessOverride == null) "Auto" else "${(brightnessOverride * 100).roundToInt()}%",
                                                    color = if (brightnessOverride == null) Color.White.copy(alpha = 0.55f) else Color(0xFFE6E8EC),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        if (brightnessOverride != null) {
                                            MinimalSlider(
                                                value = brightnessOverride,
                                                onValueChange = onBrightnessOverrideChange,
                                                valueRange = 0.05f..1.0f,
                                                trackColor = glowColor,
                                                thumbColor = glowColor,
                                                modifier = Modifier.fillMaxWidth()
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = "Layout",
                    isSelected = activeTab == NovelSettingTab.LAYOUT,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == NovelSettingTab.LAYOUT) null else NovelSettingTab.LAYOUT)
                    }
                )

                // 4. Display
                VerticalPillIcon(
                    icon = Icons.Rounded.BrightnessMedium,
                    contentDescription = "Display",
                    isSelected = activeTab == NovelSettingTab.DISPLAY,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == NovelSettingTab.DISPLAY) null else NovelSettingTab.DISPLAY)
                    }
                )

                // Divider
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.10f))
                )

                // 5. Table of Contents
                VerticalPillIcon(
                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    contentDescription = "Table of Contents",
                    isSelected = false,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShowToc()
                    }
                )

                // 6. Bookmark
                VerticalPillIcon(
                    icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = "Bookmark",
                    isSelected = isBookmarked,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleBookmark()
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
                tint = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.65f),
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
            if (isSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f)
        )
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
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
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
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
                tint = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    color = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.80f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
