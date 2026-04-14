package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ComicPagePrefetchWindow(
    val forwardCount: Int = 4,
    val backwardCount: Int = 4,
) {
    init {
        require(forwardCount >= 0) { "forwardCount must be >= 0" }
        require(backwardCount >= 0) { "backwardCount must be >= 0" }
    }
}

fun ComicPagePrefetchWindow.candidateIndexes(
    settledPageIndex: Int,
    pageCount: Int,
): List<Int> {
    if (pageCount <= 1) return emptyList()
    val safePageIndex = settledPageIndex.coerceIn(0, pageCount - 1)
    return buildList {
        for (offset in 1..forwardCount) {
            add(safePageIndex + offset)
        }
        for (offset in 1..backwardCount) {
            add(safePageIndex - offset)
        }
    }.filter { index -> index in 0 until pageCount }
}

internal fun continuousReaderPrefetchWindow(): ComicPagePrefetchWindow = ComicPagePrefetchWindow(
    forwardCount = 10,
    backwardCount = 3,
)

internal fun continuousReaderContentWidth(
    maxWidth: Dp,
    maxHeight: Dp,
): Dp {
    val availableWidth = maxOf(0.dp, maxWidth - 32.dp)
    val preferredWidth = (maxHeight * 0.68f).coerceIn(420.dp, 760.dp)
    return minOf(availableWidth, preferredWidth)
}

internal fun continuousReaderPlaceholderHeight(
    contentWidth: Dp,
    metadata: ComicPageMetadata?,
): Dp? = metadata
    ?.takeIf { it.width > 0 && it.height > 0 }
    ?.let { contentWidth / it.aspectRatio }

internal fun continuousReaderFallbackPlaceholderHeight(
    contentWidth: Dp,
    viewportHeight: Dp,
): Dp = maxOf(
    contentWidth * 1.4f,
    viewportHeight * 0.85f,
    320.dp,
)
