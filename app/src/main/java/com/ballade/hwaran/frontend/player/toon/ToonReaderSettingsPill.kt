package com.ballade.hwaran.frontend.player.toon

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.frontend.player.novel.MinimalSlider
import com.ballade.hwaran.ui.components.ReaderMusicPlayerCard
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import kotlin.math.roundToInt

enum class ReaderSettingTab {
    DISPLAY,
    ZOOM_CROP,
    LAYOUT,
    BACKGROUND,
    MUSIC
}

@Composable
fun ToonReaderSettingsPill(
    activeTab: ReaderSettingTab?,
    onTabSelected: (ReaderSettingTab?) -> Unit,
    readerMode: Int,
    onReaderModeChange: (Int) -> Unit,
    readerDirection: Int,
    onReaderDirectionChange: (Int) -> Unit,
    cropZoom: Float,
    onCropZoomChange: (Float) -> Unit,
    readerBgColor: Int,
    onReaderBgColorChange: (Int) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    brightnessOverride: Float?,
    onBrightnessOverrideChange: (Float?) -> Unit,
    glowColor: Color = Color(0xFFE6E8EC),
    musicViewModel: MusicViewModel? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // ── Floating Setting Popup Card (Transparent Background - Live Preview) ──
        AnimatedVisibility(
            visible = activeTab != null,
            enter = fadeIn(tween(160)) + slideInHorizontally(tween(180)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutHorizontally(tween(160)) { it / 2 },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 76.dp)
        ) {
            if (activeTab == ReaderSettingTab.MUSIC && musicViewModel != null) {
                ReaderMusicPlayerCard(
                    musicViewModel = musicViewModel,
                    onClose = { onTabSelected(null) }
                )
            } else if (activeTab != null && activeTab != ReaderSettingTab.MUSIC) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF14131E).copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 320.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume click to prevent dismiss */ }
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // ── Popup Header ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val headerTitle = when (activeTab) {
                                ReaderSettingTab.DISPLAY -> "Display Screen"
                                ReaderSettingTab.ZOOM_CROP -> "Crop Margin & Zoom"
                                ReaderSettingTab.LAYOUT -> "Reading Layout"
                                ReaderSettingTab.BACKGROUND -> "Canvas Background"
                                ReaderSettingTab.MUSIC -> "Music Player"
                                else -> ""
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

                        // ── Popup Content Per Tab ──
                        when (activeTab) {
                            // 1. DISPLAY SCREEN
                            ReaderSettingTab.DISPLAY -> {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    // Keep Screen On Switch
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

                                    // Brightness Override Slider
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
                                                border = BorderStroke(1.dp, if (brightnessOverride == null) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.20f)),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        if (brightnessOverride == null) {
                                                            onBrightnessOverrideChange(0.6f)
                                                        } else {
                                                            onBrightnessOverrideChange(null)
                                                        }
                                                    }
                                            ) {
                                                Text(
                                                    text = if (brightnessOverride == null) "System Auto" else "${(brightnessOverride * 100).roundToInt()}%",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        if (brightnessOverride != null) {
                                            MinimalSlider(
                                                value = brightnessOverride,
                                                onValueChange = onBrightnessOverrideChange,
                                                valueRange = 0.05f..1.0f,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                trackColor = glowColor,
                                                thumbColor = Color.White,
                                                trackHeight = 2.5.dp,
                                                thumbRadius = 7.dp
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. CROP SIZE MARGIN & ZOOM
                            ReaderSettingTab.ZOOM_CROP -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Zoom & Width Scale",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF222631),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                        ) {
                                            Text(
                                                text = "${(cropZoom * 100).roundToInt()}%",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    MinimalSlider(
                                        value = cropZoom,
                                        onValueChange = onCropZoomChange,
                                        valueRange = 0.50f..2.50f,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                        trackColor = glowColor,
                                        thumbColor = Color.White,
                                        trackHeight = 2.5.dp,
                                        thumbRadius = 7.dp
                                    )

                                    // Quick presets
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CompactPresetPill(
                                            label = "75%",
                                            isSelected = cropZoom in 0.72f..0.78f,
                                            glowColor = glowColor,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onCropZoomChange(0.75f) }
                                        )
                                        CompactPresetPill(
                                            label = "Fit",
                                            isSelected = cropZoom in 0.98f..1.02f,
                                            glowColor = glowColor,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onCropZoomChange(1.0f) }
                                        )
                                        CompactPresetPill(
                                            label = "125%",
                                            isSelected = cropZoom in 1.22f..1.28f,
                                            glowColor = glowColor,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onCropZoomChange(1.25f) }
                                        )
                                        CompactPresetPill(
                                            label = "150%",
                                            isSelected = cropZoom in 1.47f..1.53f,
                                            glowColor = glowColor,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onCropZoomChange(1.50f) }
                                        )
                                        CompactPresetPill(
                                            label = "200%",
                                            isSelected = cropZoom in 1.95f..2.05f,
                                            glowColor = glowColor,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onCropZoomChange(2.0f) }
                                        )
                                        // Reset Button
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White.copy(alpha = 0.08f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onCropZoomChange(1.0f) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.RestartAlt,
                                                contentDescription = "Reset",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. READING LAYOUT
                            ReaderSettingTab.LAYOUT -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CompactLayoutModeChip(
                                            title = "Long Strip",
                                            icon = Icons.Rounded.ViewStream,
                                            isSelected = readerMode == 0,
                                            glowColor = glowColor,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onReaderModeChange(0) }
                                        )

                                        CompactLayoutModeChip(
                                            title = "Page by Page",
                                            icon = Icons.Rounded.AutoStories,
                                            isSelected = readerMode == 1,
                                            glowColor = glowColor,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onReaderModeChange(1) }
                                        )
                                    }

                                    if (readerMode == 1) {
                                        Text(
                                            text = "Reading Direction",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CompactPresetPill(
                                                label = "Left to Right (LTR)",
                                                isSelected = readerDirection == 0,
                                                glowColor = glowColor,
                                                modifier = Modifier.weight(1f),
                                                onClick = { onReaderDirectionChange(0) }
                                            )
                                            CompactPresetPill(
                                                label = "Right to Left (RTL)",
                                                isSelected = readerDirection == 1,
                                                glowColor = glowColor,
                                                modifier = Modifier.weight(1f),
                                                onClick = { onReaderDirectionChange(1) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. CANVAS BACKGROUND
                            ReaderSettingTab.BACKGROUND -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CompactColorToneChip(
                                        title = "Theme",
                                        previewColor = Color(0xFF2C243B),
                                        isSelected = readerBgColor == 0,
                                        glowColor = glowColor,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onReaderBgColorChange(0) }
                                    )

                                    CompactColorToneChip(
                                        title = "Black",
                                        previewColor = Color(0xFF000000),
                                        isSelected = readerBgColor == 1,
                                        glowColor = glowColor,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onReaderBgColorChange(1) }
                                    )

                                    CompactColorToneChip(
                                        title = "Slate",
                                        previewColor = Color(0xFF14131C),
                                        isSelected = readerBgColor == 2,
                                        glowColor = glowColor,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onReaderBgColorChange(2) }
                                    )

                                    CompactColorToneChip(
                                        title = "White",
                                        previewColor = Color(0xFFFFFFFF),
                                        isSelected = readerBgColor == 3,
                                        glowColor = glowColor,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onReaderBgColorChange(3) }
                                    )
                                }
                            }
                            ReaderSettingTab.MUSIC, null -> {}
                        }
                    }
                }
            }
        }

        // ── Vertical Settings Pill (Matching Screenshot Design) ──
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFF1E1B26).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .padding(end = 16.dp)
                .width(52.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Display Screen
                VerticalPillIcon(
                    icon = Icons.Rounded.BrightnessMedium,
                    contentDescription = "Display Screen",
                    isSelected = activeTab == ReaderSettingTab.DISPLAY,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == ReaderSettingTab.DISPLAY) null else ReaderSettingTab.DISPLAY)
                    }
                )

                // 2. Crop Size Margin & Zoom
                VerticalPillIcon(
                    icon = Icons.Rounded.ZoomIn,
                    contentDescription = "Crop Size Margin & Zoom",
                    isSelected = activeTab == ReaderSettingTab.ZOOM_CROP,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == ReaderSettingTab.ZOOM_CROP) null else ReaderSettingTab.ZOOM_CROP)
                    }
                )

                // 3. Reading Layout
                VerticalPillIcon(
                    icon = Icons.Rounded.AutoStories,
                    contentDescription = "Reading Layout",
                    isSelected = activeTab == ReaderSettingTab.LAYOUT,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == ReaderSettingTab.LAYOUT) null else ReaderSettingTab.LAYOUT)
                    }
                )

                // 4. Canvas Background
                VerticalPillIcon(
                    icon = Icons.Rounded.FormatColorFill,
                    contentDescription = "Canvas Background",
                    isSelected = activeTab == ReaderSettingTab.BACKGROUND,
                    glowColor = glowColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(if (activeTab == ReaderSettingTab.BACKGROUND) null else ReaderSettingTab.BACKGROUND)
                    }
                )

                // 5. Music Player (shown only when music from Hwaran is active in background)
                val currentTrack = musicViewModel?.currentChapter?.collectAsState()?.value
                if (currentTrack != null) {
                    VerticalPillIcon(
                        icon = Icons.Rounded.MusicNote,
                        contentDescription = "Music Player",
                        isSelected = activeTab == ReaderSettingTab.MUSIC,
                        glowColor = glowColor,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTabSelected(if (activeTab == ReaderSettingTab.MUSIC) null else ReaderSettingTab.MUSIC)
                        }
                    )
                }
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
private fun CompactLayoutModeChip(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                color = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CompactColorToneChip(
    title: String,
    previewColor: Color,
    isSelected: Boolean,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF222631) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )

            Text(
                text = title,
                color = if (isSelected) Color(0xFFE6E8EC) else Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
