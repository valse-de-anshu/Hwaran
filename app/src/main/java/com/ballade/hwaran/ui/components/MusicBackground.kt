package com.ballade.hwaran.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity

@Composable
fun MusicBackground(
    currentChapter: ChapterEntity?,
    currentManga: MangaEntity?,
    modifier: Modifier = Modifier,
    blurRadius: androidx.compose.ui.unit.Dp = 100.dp,
    overlayAlpha: Float = 0.5f
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(
                    currentChapter?.thumbnailUri?.takeIf { it.isNotBlank() }
                        ?: currentManga?.coverPath?.takeIf { it.isNotBlank() }
                        ?: currentChapter?.folderUri?.takeIf { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) || it.endsWith(".webp", true) }
                )
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = blurRadius)
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = overlayAlpha)))
    }
}
