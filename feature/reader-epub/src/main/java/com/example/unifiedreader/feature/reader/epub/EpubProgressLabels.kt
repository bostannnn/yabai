package com.example.unifiedreader.feature.reader.epub

import kotlin.math.roundToInt

internal fun buildEpubProgressLabel(
    progress: Double,
    position: Int,
    pageCount: Int,
): String = when {
    position > 0 && pageCount > 0 -> {
        "${buildEpubPercentLabel(progress)} read · $position/$pageCount"
    }

    progress > 0.0 -> "${buildEpubPercentLabel(progress)} read"
    pageCount > 0 -> "$pageCount locations"
    else -> "Saved location"
}

internal fun buildEpubPercentLabel(
    progress: Double,
): String = "${((progress.coerceIn(0.0, 1.0) * 100.0).roundToInt()).coerceIn(0, 100)}%"

internal fun buildEpubPageProgressLabel(
    position: Int,
    pageCount: Int,
): String = when {
    position > 0 && pageCount > 0 -> "$position/$pageCount"
    pageCount > 0 -> "?/$pageCount"
    position > 0 -> "$position"
    else -> "?/?"
}
