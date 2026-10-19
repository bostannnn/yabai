package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.toRect
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTapGrabFraction
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTarget

internal enum class ComicPageCurlGestureStartPolicy {
    StartCurl,
    YieldToPager,
}

internal fun resolveComicPageCurlGestureStartPolicy(
    targetSpreadIndex: Int?,
    hasCurrentComposite: Boolean,
    hasUnderneathComposite: Boolean,
    sheetBounds: IntRect?,
    downPosition: Offset,
    transitionPolicy: ComicPageCurlTransitionPolicy = ComicPageCurlTransitionPolicy.UseCurl,
): ComicPageCurlGestureStartPolicy {
    if (targetSpreadIndex == null) return ComicPageCurlGestureStartPolicy.YieldToPager
    if (transitionPolicy != ComicPageCurlTransitionPolicy.UseCurl) {
        return ComicPageCurlGestureStartPolicy.YieldToPager
    }
    if (!hasCurrentComposite || !hasUnderneathComposite || sheetBounds == null || sheetBounds.isEmpty) {
        return ComicPageCurlGestureStartPolicy.YieldToPager
    }
    return if (sheetBounds.toRect().contains(downPosition)) {
        ComicPageCurlGestureStartPolicy.StartCurl
    } else {
        ComicPageCurlGestureStartPolicy.YieldToPager
    }
}

/** Resolves a curl turn for [target], or null when the pager should handle it instead. */
internal fun startComicPageCurlInteraction(
    target: PageCurlTarget,
    currentSpreadIndex: Int,
    spreadCount: Int,
    readingDirection: ComicReadingDirection,
    currentComposite: ComicPageCurlComposite?,
    adjacentComposite: ComicPageCurlComposite?,
    downPosition: Offset?,
): ComicPageCurlInteraction? {
    val targetSpreadIndex = resolveComicPageCurlTargetSpreadIndex(
        currentSpreadIndex = currentSpreadIndex,
        spreadCount = spreadCount,
        target = target,
    )
    val turn = if (currentComposite != null && adjacentComposite != null) {
        resolvePageCurlTurn(
            currentLayout = currentComposite.layout,
            targetLayout = adjacentComposite.layout,
            readingDirection = readingDirection,
            target = target,
        )
    } else {
        null
    }
    val startPolicy = resolveComicPageCurlGestureStartPolicy(
        targetSpreadIndex = targetSpreadIndex,
        hasCurrentComposite = currentComposite != null,
        hasUnderneathComposite = adjacentComposite != null,
        sheetBounds = turn?.paperBounds,
        downPosition = downPosition ?: turn?.paperBounds?.center() ?: Offset.Zero,
        transitionPolicy = if (turn != null) {
            ComicPageCurlTransitionPolicy.UseCurl
        } else {
            ComicPageCurlTransitionPolicy.YieldToPager
        },
    )
    if (startPolicy != ComicPageCurlGestureStartPolicy.StartCurl) return null
    val resolvedTurn = checkNotNull(turn)
    val grabY = downPosition?.y ?: (
        resolvedTurn.sheet.top +
            (resolvedTurn.sheet.bottom - resolvedTurn.sheet.top) * PageCurlTapGrabFraction
        )
    return buildComicPageCurlInteraction(
        turn = resolvedTurn,
        targetSpreadIndex = checkNotNull(targetSpreadIndex),
        grab = resolvedTurn.grabPoint(grabY),
        currentPage = checkNotNull(currentComposite).image,
        adjacentPage = checkNotNull(adjacentComposite).image,
    )
}

private fun IntRect.center(): Offset = Offset(
    x = left + width / 2f,
    y = top + height / 2f,
)
