package com.example.unifiedreader.feature.reader.comics

import android.os.Build
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import kotlin.math.abs

internal enum class ComicPageCurlUnavailableReason {
    PageTurnStyleNotCurl,
    ReaderModeNotPaged,
    UnsupportedApi,
    Zoomed,
    InvalidContainerSize,
    MissingCurrentComposite,
    MissingAdjacentComposite,
}

internal data class ComicPageCurlAvailability(
    val isAvailable: Boolean,
    val reason: ComicPageCurlUnavailableReason?,
)

internal fun resolveComicPageCurlAvailability(
    pageTurnStyle: ComicPageTurnStyle,
    readerMode: ComicReaderMode,
    sdkInt: Int = Build.VERSION.SDK_INT,
    isCurrentPageZoomed: Boolean,
    hasCurrentComposite: Boolean,
    hasAdjacentComposite: Boolean,
    containerSize: IntSize? = null,
): ComicPageCurlAvailability {
    val reason = when {
        pageTurnStyle != ComicPageTurnStyle.Curl -> ComicPageCurlUnavailableReason.PageTurnStyleNotCurl
        readerMode != ComicReaderMode.Paged -> ComicPageCurlUnavailableReason.ReaderModeNotPaged
        !isComicPageCurlShaderSupported(sdkInt = sdkInt) -> ComicPageCurlUnavailableReason.UnsupportedApi
        isCurrentPageZoomed -> ComicPageCurlUnavailableReason.Zoomed
        containerSize != null && (containerSize.width <= 0 || containerSize.height <= 0) ->
            ComicPageCurlUnavailableReason.InvalidContainerSize
        !hasCurrentComposite -> ComicPageCurlUnavailableReason.MissingCurrentComposite
        !hasAdjacentComposite -> ComicPageCurlUnavailableReason.MissingAdjacentComposite
        else -> null
    }
    return ComicPageCurlAvailability(
        isAvailable = reason == null,
        reason = reason,
    )
}

internal fun shouldUseComicPageCurlPagerLayer(
    pageTurnStyle: ComicPageTurnStyle,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = pageTurnStyle == ComicPageTurnStyle.Curl &&
    isComicPageCurlShaderSupported(sdkInt = sdkInt)

internal fun shouldCommitComicPageCurl(
    progress: Float,
    velocity: Float = 0f,
    progressThreshold: Float = ComicPageCurlCommitProgressThreshold,
    velocityThreshold: Float = ComicPageCurlCommitVelocityThreshold,
): Boolean = progress > progressThreshold || abs(velocity) > velocityThreshold

internal fun resolveComicPageCurlTargetSpreadIndex(
    currentSpreadIndex: Int,
    spreadCount: Int,
    target: ComicPageCurlTarget,
): Int? {
    if (spreadCount <= 0 || currentSpreadIndex !in 0 until spreadCount) return null
    val targetIndex = when (target) {
        ComicPageCurlTarget.Previous -> currentSpreadIndex - 1
        ComicPageCurlTarget.Next -> currentSpreadIndex + 1
    }
    return targetIndex.takeIf { it in 0 until spreadCount }
}

internal fun resolveComicPageCurlTargetSpreadIndex(
    horizontalDrag: Float,
    readingDirection: ComicReadingDirection,
    touchSlop: Float,
    currentSpreadIndex: Int,
    spreadCount: Int,
): Int? {
    val target = resolveComicPageCurlTarget(
        horizontalDrag = horizontalDrag,
        readingDirection = readingDirection,
        touchSlop = touchSlop,
    ) ?: return null
    return resolveComicPageCurlTargetSpreadIndex(
        currentSpreadIndex = currentSpreadIndex,
        spreadCount = spreadCount,
        target = target,
    )
}

private const val ComicPageCurlCommitProgressThreshold = 0.25f
private const val ComicPageCurlCommitVelocityThreshold = 1.5f
