package com.ballade.hwaran.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sleek, hardware-accelerated draw-phase vertical scrollbar for [LazyListState].
 * Reading scroll state within drawWithContent executes exclusively during Compose's RenderNode draw pass,
 * completely avoiding recompositions and relayouts during fast flings and smooth scrolling.
 *
 * Thumb height is consistently bounded between [minThumbHeight] and [maxThumbHeight] to prevent
 * massive or microscopic scrollbar thumbs across short and long lists.
 */
fun Modifier.simpleVerticalScrollbar(
    state: LazyListState,
    thumbWidth: Dp = 3.5.dp,
    thumbColor: Color = Color.White.copy(alpha = 0.42f),
    minThumbHeight: Dp = 44.dp,
    maxThumbHeight: Dp = 68.dp,
    paddingEnd: Dp = 3.dp,
    paddingTop: Dp = 90.dp,
    paddingBottom: Dp = 48.dp
): Modifier = this.drawWithContent {
    drawContent()

    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size

    if (totalItems > 0 && totalItems > visibleItems) {
        val topPx = paddingTop.toPx()
        val bottomPx = paddingBottom.toPx()
        val availableHeight = size.height - topPx - bottomPx
        if (availableHeight <= 0f) return@drawWithContent

        val minHeightPx = minThumbHeight.toPx()
        val maxHeightPx = maxThumbHeight.toPx().coerceAtMost(availableHeight * 0.4f)
        val thumbWidthPx = thumbWidth.toPx()
        val paddingEndPx = paddingEnd.toPx()

        val ratio = visibleItems.toFloat() / totalItems.toFloat()
        // Smooth interpolation that keeps thumb proportioned like a modern mobile OS scrollbar
        val thumbHeight = (minHeightPx + (maxHeightPx - minHeightPx) * ratio.coerceIn(0f, 1f))
            .coerceIn(minHeightPx, maxHeightPx)

        if (availableHeight <= thumbHeight) return@drawWithContent

        val firstVisibleIndex = state.firstVisibleItemIndex
        val firstVisibleOffset = state.firstVisibleItemScrollOffset
        val firstItemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1

        val exactItemIndex = firstVisibleIndex + (firstVisibleOffset.toFloat() / firstItemSize.toFloat().coerceAtLeast(1f))
        val maxScrollableIndex = (totalItems - visibleItems).coerceAtLeast(1)
        val progress = (exactItemIndex / maxScrollableIndex.toFloat()).coerceIn(0f, 1f)

        val thumbY = topPx + (progress * (availableHeight - thumbHeight))
        val thumbX = size.width - thumbWidthPx - paddingEndPx

        // Subtle dark backdrop stroke for contrast over light covers
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset(thumbX - 0.5f, thumbY - 0.5f),
            size = Size(thumbWidthPx + 1f, thumbHeight + 1f),
            cornerRadius = CornerRadius((thumbWidthPx + 1f) / 2f, (thumbWidthPx + 1f) / 2f)
        )

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbX, thumbY),
            size = Size(thumbWidthPx, thumbHeight),
            cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
        )
    }
}

/**
 * Sleek, hardware-accelerated draw-phase vertical scrollbar for [LazyGridState].
 */
fun Modifier.simpleVerticalScrollbar(
    state: LazyGridState,
    thumbWidth: Dp = 3.5.dp,
    thumbColor: Color = Color.White.copy(alpha = 0.42f),
    minThumbHeight: Dp = 44.dp,
    maxThumbHeight: Dp = 68.dp,
    paddingEnd: Dp = 3.dp,
    paddingTop: Dp = 90.dp,
    paddingBottom: Dp = 48.dp
): Modifier = this.drawWithContent {
    drawContent()

    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size

    if (totalItems > 0 && totalItems > visibleItems) {
        val topPx = paddingTop.toPx()
        val bottomPx = paddingBottom.toPx()
        val availableHeight = size.height - topPx - bottomPx
        if (availableHeight <= 0f) return@drawWithContent

        val minHeightPx = minThumbHeight.toPx()
        val maxHeightPx = maxThumbHeight.toPx().coerceAtMost(availableHeight * 0.4f)
        val thumbWidthPx = thumbWidth.toPx()
        val paddingEndPx = paddingEnd.toPx()

        val ratio = visibleItems.toFloat() / totalItems.toFloat()
        val thumbHeight = (minHeightPx + (maxHeightPx - minHeightPx) * ratio.coerceIn(0f, 1f))
            .coerceIn(minHeightPx, maxHeightPx)

        if (availableHeight <= thumbHeight) return@drawWithContent

        val firstVisibleIndex = state.firstVisibleItemIndex
        val firstVisibleOffset = state.firstVisibleItemScrollOffset
        val firstItemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 1

        val exactItemIndex = firstVisibleIndex + (firstVisibleOffset.toFloat() / firstItemHeight.toFloat().coerceAtLeast(1f))
        val maxScrollableIndex = (totalItems - visibleItems).coerceAtLeast(1)
        val progress = (exactItemIndex / maxScrollableIndex.toFloat()).coerceIn(0f, 1f)

        val thumbY = topPx + (progress * (availableHeight - thumbHeight))
        val thumbX = size.width - thumbWidthPx - paddingEndPx

        // Subtle dark backdrop stroke for contrast over light covers
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset(thumbX - 0.5f, thumbY - 0.5f),
            size = Size(thumbWidthPx + 1f, thumbHeight + 1f),
            cornerRadius = CornerRadius((thumbWidthPx + 1f) / 2f, (thumbWidthPx + 1f) / 2f)
        )

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbX, thumbY),
            size = Size(thumbWidthPx, thumbHeight),
            cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
        )
    }
}
