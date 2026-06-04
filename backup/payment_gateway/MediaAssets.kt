package com.ballade.hwaran.ui.screens.archive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MangaEyeBg = Color(0xFFE5E5E5)
private val DarkPaper = Color(0xFFF2EFE9)
private val DarkText = Color(0xFF2A2A2A)
private val VinylBlack = Color(0xFF111111)

@Composable
fun MangaEyePanel() {
    Box(
        modifier = Modifier
            .width(192.dp)
            .height(80.dp)
            .graphicsLayer {
                rotationZ = -5f
                alpha = 0.15f
            }
            .background(MangaEyeBg)
            .border(2.dp, Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val path = Path().apply {
                moveTo(width * 0.1f, height * 0.5f)
                quadraticTo(width * 0.3f, height * 0.1f, width * 0.7f, height * 0.3f)
                quadraticTo(width * 0.85f, height * 0.4f, width * 0.9f, height * 0.6f)
                quadraticTo(width * 0.6f, height * 0.9f, width * 0.3f, height * 0.7f)
                quadraticTo(width * 0.15f, height * 0.6f, width * 0.1f, height * 0.5f)
                close()
            }
            drawPath(path = path, color = Color.Black, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = Color.Black, radius = 8.dp.toPx(), center = center.copy(x = width * 0.55f, y = height * 0.44f))
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = center.copy(x = width * 0.52f, y = height * 0.38f))
            drawLine(Color.Black, start = center.copy(x = width * 0.15f, y = height * 0.36f), end = center.copy(x = width * 0.3f, y = height * 0.2f), strokeWidth = 1.5.dp.toPx())
        }
    }
}

@Composable
fun CinematicVideoFrame() {
    Box(
        modifier = Modifier
            .width(224.dp)
            .height(128.dp)
            .graphicsLayer { alpha = 0.25f }
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0x4D008B8B), Color(0x33FF1493)))))
        
        // Video Scrubber Bar
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.3f))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.3f).background(Color.White))
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White).align(Alignment.CenterStart).offset(x = 64.dp))
        }

        // Play Button
        Box(modifier = Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun AudioWaveform() {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(64.dp)
            .graphicsLayer { alpha = 0.2f; rotationZ = 10f }
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF0F0F0F))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(32.dp))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = 15
            val gap = 4.dp.toPx()
            val totalWidth = size.width
            val barWidth = (totalWidth - (gap * (barCount - 1))) / barCount
            val heights = listOf(0.2f, 0.4f, 0.7f, 0.9f, 0.5f, 0.3f, 0.8f, 1.0f, 0.6f, 0.4f, 0.8f, 0.5f, 0.3f, 0.5f, 0.2f)
            
            for (i in 0 until barCount) {
                val x = i * (barWidth + gap)
                val barHeight = size.height * heights[i]
                val y = (size.height - barHeight) / 2
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
        }
    }
}

@Composable
fun BookPage() {
    Column(
        modifier = Modifier
            .width(128.dp)
            .height(176.dp)
            .graphicsLayer {
                rotationZ = -8f
                alpha = 0.15f
            }
            .background(DarkPaper)
            .drawBehind { drawLine(Color(0xFFD1CCC0), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = 16.dp.toPx()) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.width(40.dp).height(4.dp).background(DarkText.copy(alpha = 0.3f)))
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(DarkText.copy(alpha = 0.2f)))
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(DarkText.copy(alpha = 0.2f)))
        Box(modifier = Modifier.fillMaxWidth(0.8f).height(4.dp).background(DarkText.copy(alpha = 0.2f)))
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(DarkText.copy(alpha = 0.2f)))
        Box(modifier = Modifier.fillMaxWidth(0.9f).height(4.dp).background(DarkText.copy(alpha = 0.2f)))
    }
}

@Composable
fun VinylRecord() {
    Box(
        modifier = Modifier
            .size(144.dp)
            .graphicsLayer {
                rotationZ = 25f
                alpha = 0.2f
            }
            .background(VinylBlack, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(Color.Transparent, radius = radius - 8.dp.toPx(), style = Stroke(1.dp.toPx()), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.1f)))
            drawCircle(Color.Transparent, radius = radius - 16.dp.toPx(), style = Stroke(1.dp.toPx()), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.05f)))
            drawCircle(Color.Transparent, radius = radius - 24.dp.toPx(), style = Stroke(1.dp.toPx()), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.15f)))
            drawCircle(Color.Transparent, radius = radius - 32.dp.toPx(), style = Stroke(1.dp.toPx()), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.05f)))
        }
        Box(modifier = Modifier.size(48.dp).background(Brush.linearGradient(listOf(Color(0xFF8B0000), Color(0xFF220000))), CircleShape).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(8.dp).background(VinylBlack, CircleShape))
        }
    }
}

@Composable
fun TypographyElement() {
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(100.dp)
            .graphicsLayer {
                rotationZ = -12f
                alpha = 0.1f
            }
            .background(Color.Transparent)
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text("Aa", fontSize = 64.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = Color.White)
    }
}