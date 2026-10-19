package com.example.unifiedreader.feature.reader.comics

import android.os.Build
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTarget
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.core.curl.isPageCurlShaderSupported
import com.example.unifiedreader.feature.reader.core.curl.resolvePageCurlTarget
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode

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
    pageTurnStyle: PageTurnStyle,
    readerMode: ComicReaderMode,
    sdkInt: Int = Build.VERSION.SDK_INT,
    isCurrentPageZoomed: Boolean,
    hasCurrentComposite: Boolean,
    hasAdjacentComposite: Boolean,
    containerSize: IntSize? = null,
): ComicPageCurlAvailability {
    val reason = when {
        pageTurnStyle != PageTurnStyle.Curl -> ComicPageCurlUnavailableReason.PageTurnStyleNotCurl
        readerMode != ComicReaderMode.Paged -> ComicPageCurlUnavailableReason.ReaderModeNotPaged
        !isPageCurlShaderSupported(sdkInt = sdkInt) -> ComicPageCurlUnavailableReason.UnsupportedApi
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
    pageTurnStyle: PageTurnStyle,
    readerMode: ComicReaderMode = ComicReaderMode.Paged,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = pageTurnStyle == PageTurnStyle.Curl &&
    readerMode == ComicReaderMode.Paged &&
    isPageCurlShaderSupported(sdkInt = sdkInt)

internal fun resolveComicPageCurlTargetSpreadIndex(
    currentSpreadIndex: Int,
    spreadCount: Int,
    target: PageCurlTarget,
): Int? {
    if (spreadCount <= 0 || currentSpreadIndex !in 0 until spreadCount) return null
    val targetIndex = when (target) {
        PageCurlTarget.Previous -> currentSpreadIndex - 1
        PageCurlTarget.Next -> currentSpreadIndex + 1
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
    val target = resolvePageCurlTarget(
        horizontalDrag = horizontalDrag,
        rightToLeft = readingDirection == ComicReadingDirection.RTL,
        touchSlop = touchSlop,
    ) ?: return null
    return resolveComicPageCurlTargetSpreadIndex(
        currentSpreadIndex = currentSpreadIndex,
        spreadCount = spreadCount,
        target = target,
    )
}
