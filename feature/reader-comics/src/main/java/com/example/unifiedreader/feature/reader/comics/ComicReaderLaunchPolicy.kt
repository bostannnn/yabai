package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.feature.reader.core.ReaderOpenMode

internal data class ComicReaderInitialPosition(
    val page: Int,
    val resumeAnchorPage: Int?,
    val resumeSegment: ComicPageSegment?,
    val resumeScrollOffsetRatio: Float?,
)

internal fun resolveComicReaderInitialPosition(
    openMode: ReaderOpenMode,
    savedPage: Int?,
    savedResumeAnchorPage: Int?,
    savedResumeSegment: ComicPageSegment?,
    savedResumeScrollOffsetRatio: Float?,
    lastPageIndex: Int,
): ComicReaderInitialPosition = if (shouldRestoreComicReaderPosition(openMode)) {
    ComicReaderInitialPosition(
        page = savedPage?.coerceIn(0, lastPageIndex) ?: 0,
        resumeAnchorPage = savedResumeAnchorPage?.coerceIn(0, lastPageIndex),
        resumeSegment = savedResumeSegment,
        resumeScrollOffsetRatio = savedResumeScrollOffsetRatio,
    )
} else {
    ComicReaderInitialPosition(
        page = 0,
        resumeAnchorPage = 0,
        resumeSegment = null,
        resumeScrollOffsetRatio = null,
    )
}

internal fun shouldRestoreComicReaderPosition(
    openMode: ReaderOpenMode,
): Boolean = openMode == ReaderOpenMode.Default
