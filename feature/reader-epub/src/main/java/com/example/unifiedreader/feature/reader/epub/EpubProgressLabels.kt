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

/**
 * The dock's progress at [position] of [pageCount]. Readium's progression stops just under 1 on
 * the last page, so the last page reads 100% and fills the slider; no earlier page rounds up to it.
 * Without known pages the progression is shown as it is.
 */
internal fun epubDockProgress(
    progress: Double,
    position: Int,
    pageCount: Int,
): Double {
    val clamped = progress.coerceIn(0.0, 1.0)
    if (position <= 0 || pageCount <= 0) return clamped
    return if (position >= pageCount) 1.0 else clamped.coerceAtMost(BeforeLastPageMaxProgress)
}

/** The most a page before the last can show: rounds to 99%. */
private const val BeforeLastPageMaxProgress = 0.994

internal fun buildEpubPageProgressLabel(
    position: Int,
    pageCount: Int,
): String = when {
    position > 0 && pageCount > 0 -> "$position/$pageCount"
    pageCount > 0 -> "?/$pageCount"
    position > 0 -> "$position"
    else -> "?/?"
}

/** The dock's position: "p. 37 / 412", or the bare count before the page is known. */
internal fun buildEpubDockPositionLabel(
    position: Int,
    pageCount: Int,
): String = when {
    position > 0 && pageCount > 0 -> "p. $position / $pageCount"
    pageCount > 0 -> "$pageCount pages"
    position > 0 -> "p. $position"
    else -> ""
}
