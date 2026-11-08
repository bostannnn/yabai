package com.example.unifiedreader.feature.reader.epub

import com.example.unifiedreader.feature.reader.core.ReaderTapSide
import org.readium.r2.navigator.preferences.ReadingProgression

internal enum class EpubTapAction {
    Previous,
    Next,
    ToggleOverlay,
}

/** What a tap on [side] of the page does: the sides turn, mirrored in a right-to-left book; the middle opens the menu. */
internal fun resolveEpubTapAction(
    side: ReaderTapSide,
    readingProgression: ReadingProgression,
): EpubTapAction {
    val rightToLeft = readingProgression == ReadingProgression.RTL
    return when (side) {
        ReaderTapSide.Left -> if (rightToLeft) EpubTapAction.Next else EpubTapAction.Previous
        ReaderTapSide.Right -> if (rightToLeft) EpubTapAction.Previous else EpubTapAction.Next
        ReaderTapSide.Middle -> EpubTapAction.ToggleOverlay
    }
}
