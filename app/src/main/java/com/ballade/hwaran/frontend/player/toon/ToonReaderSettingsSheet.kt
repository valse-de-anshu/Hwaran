package com.ballade.hwaran.frontend.player.toon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToonReaderSettingsSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    readerMode: Int, // 0 = Long Strip, 1 = Page by Page
    onReaderModeChange: (Int) -> Unit,
    readerDirection: Int, // 0 = LTR, 1 = RTL
    onReaderDirectionChange: (Int) -> Unit,
    cropZoom: Float, // 1.0f to 1.5f
    onCropZoomChange: (Float) -> Unit,
    readerBgColor: Int, // 0 = Theme, 1 = Black, 2 = Slate, 3 = White
    onReaderBgColorChange: (Int) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    brightnessOverride: Float?,
    onBrightnessOverrideChange: (Float?) -> Unit,
    currentPage: Int,
    totalPages: Int,
    onJumpToPage: (Int) -> Unit,
    mangaTitle: String?,
    chapterTitle: String?,
    glowColor: Color = MaterialTheme.colorScheme.primary
) {
    if (!show) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF13121B).copy(alpha = 0.96f),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(42.dp, 4.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reader Settings",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${mangaTitle ?: "Manga"} • ${chapterTitle ?: "Chapter"}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── Quick Page Scrub Slider ──
            if (totalPages > 1) {
                SettingsSectionCard(title = "Page Jump", icon = Icons.AutoMirrored.Rounded.MenuBook) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current Position",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = glowColor.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, glowColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Page ${currentPage + 1} / $totalPages",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Slider(
                            value = (currentPage + 1).toFloat(),
                            onValueChange = { onJumpToPage(it.roundToInt() - 1) },
                            valueRange = 1f..totalPages.toFloat(),
                            steps = (totalPages - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = glowColor,
                                activeTrackColor = glowColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }

            // ── Reading Layout Mode ──
            SettingsSectionCard(title = "Reading Layout", icon = Icons.Rounded.AutoStories) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LayoutModeChip(
                            title = "Single Long Strip",
                            subtitle = "Continuous vertical webtoon",
                            icon = Icons.Rounded.ViewStream,
                            isSelected = readerMode == 0,
                            glowColor = glowColor,
                            modifier = Modifier.weight(1f),
                            onClick = { onReaderModeChange(0) }
                        )

                        LayoutModeChip(
                            title = "Page by Page",
                            subtitle = "Tap or swipe comic pages",
                            icon = Icons.Rounded.AutoStories,
                            isSelected = readerMode == 1,
                            glowColor = glowColor,
                            modifier = Modifier.weight(1f),
                            onClick = { onReaderModeChange(1) }
                        )
                    }

                    // Reading Direction (only relevant for Page by Page)
                    AnimatedVisibility(visible = readerMode == 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Reading Direction",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PillChoiceChip(
                                    label = "Left to Right (LTR)",
                                    sublabel = "Western / Webtoon",
                                    isSelected = readerDirection == 0,
                                    glowColor = glowColor,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onReaderDirectionChange(0) }
                                )

                                PillChoiceChip(
                                    label = "Right to Left (RTL)",
                                    sublabel = "Japanese Manga",
                                    isSelected = readerDirection == 1,
                                    glowColor = glowColor,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onReaderDirectionChange(1) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Crop Side Margins / Zoom ──
            SettingsSectionCard(title = "Crop Side Margins / Zoom", icon = Icons.Rounded.ZoomIn) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Horizontal Scale (Eliminates white bars)",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = glowColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, glowColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${(cropZoom * 100).roundToInt()}%",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Slider(
                        value = cropZoom,
                        onValueChange = onCropZoomChange,
                        valueRange = 1.0f..1.50f,
                        steps = 9, // increments of 0.05
                        colors = SliderDefaults.colors(
                            thumbColor = glowColor,
                            activeTrackColor = glowColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                        )
                    )

                    // Quick Crop Preset Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CropPresetPill(
                            label = "Fit (100%)",
                            isSelected = cropZoom <= 1.02f,
                            glowColor = glowColor,
                            modifier = Modifier.weight(1f),
                            onClick = { onCropZoomChange(1.0f) }
                        )

                        CropPresetPill(
                            label = "Crop 15%",
                            isSelected = (cropZoom in 1.12f..1.18f),
                            glowColor = glowColor,
                            modifier = Modifier.weight(1f),
                            onClick = { onCropZoomChange(1.15f) }
                        )

                        CropPresetPill(
                            label = "Crop 30%",
                            isSelected = (cropZoom in 1.28f..1.35f),
                            glowColor = glowColor,
                            modifier = Modifier.weight(1f),
                            onClick = { onCropZoomChange(1.30f) }
                        )

                        // Reset
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onCropZoomChange(1.0f) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset Zoom",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ── Background Canvas Tone ──
            SettingsSectionCard(title = "Canvas Background", icon = Icons.Rounded.FormatColorFill) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BackgroundToneChip(
                        title = "Theme",
                        previewColor = Color(0xFF2C243B),
                        isSelected = readerBgColor == 0,
                        glowColor = glowColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onReaderBgColorChange(0) }
                    )

                    BackgroundToneChip(
                        title = "Black",
                        previewColor = Color(0xFF000000),
                        isSelected = readerBgColor == 1,
                        glowColor = glowColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onReaderBgColorChange(1) }
                    )

                    BackgroundToneChip(
                        title = "Slate",
                        previewColor = Color(0xFF14131C),
                        isSelected = readerBgColor == 2,
                        glowColor = glowColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onReaderBgColorChange(2) }
                    )

                    BackgroundToneChip(
                        title = "White",
                        previewColor = Color(0xFFFFFFFF),
                        isSelected = readerBgColor == 3,
                        glowColor = glowColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onReaderBgColorChange(3) }
                    )
                }
            }

            // ── Screen Brightness & Keep Awake ──
            SettingsSectionCard(title = "Display & Screen", icon = Icons.Rounded.BrightnessMedium) {
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Prevent screen from turning off while reading",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }

                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = onKeepScreenOnChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = glowColor,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                            )
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // Screen Brightness Override
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "In-Reader Brightness",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (brightnessOverride == null) Color.White.copy(alpha = 0.08f) else glowColor.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, if (brightnessOverride == null) Color.White.copy(alpha = 0.12f) else glowColor.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (brightnessOverride == null) {
                                            onBrightnessOverrideChange(0.6f)
                                        } else {
                                            onBrightnessOverrideChange(null) // Auto
                                        }
                                    }
                            ) {
                                Text(
                                    text = if (brightnessOverride == null) "System Auto" else "${(brightnessOverride * 100).roundToInt()}%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (brightnessOverride != null) {
                            Slider(
                                value = brightnessOverride,
                                onValueChange = { onBrightnessOverrideChange(it) },
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1A1926),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            content()
        }
    }
}

@Composable
private fun LayoutModeChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) glowColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            1.dp,
            if (isSelected) glowColor else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) glowColor else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = glowColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PillChoiceChip(
    label: String,
    sublabel: String,
    isSelected: Boolean,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) glowColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            1.dp,
            if (isSelected) glowColor else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = sublabel,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun CropPresetPill(
    label: String,
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
        color = if (isSelected) glowColor.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(
            1.dp,
            if (isSelected) glowColor else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun BackgroundToneChip(
    title: String,
    previewColor: Color,
    isSelected: Boolean,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) glowColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            1.dp,
            if (isSelected) glowColor else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )

            Text(
                text = title,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
