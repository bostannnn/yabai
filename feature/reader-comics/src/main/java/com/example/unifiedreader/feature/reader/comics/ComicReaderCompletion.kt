package com.example.unifiedreader.feature.reader.comics

internal data class ComicSettledReadingPosition(
    val pageIndex: Int,
    val resumeAnchorPage: Int,
    val segment: ComicPageSegment?,
)

internal fun ComicPageSpread.settledReadingPosition(
    currentPage: Int,
    pageCount: Int,
): ComicSettledReadingPosition {
    val lastPageIndex = (pageCount - 1).coerceAtLeast(0)
    val visibleLastPageIndex = pageIndexes
        .filter { pageIndex -> pageIndex <= lastPageIndex }
        .maxOrNull()
    val progressPageIndex = when {
        visibleLastPageIndex == lastPageIndex -> lastPageIndex
        currentPage in pageIndexes -> currentPage
        else -> resumePageIndex
    }.coerceIn(0, lastPageIndex)
    return ComicSettledReadingPosition(
        pageIndex = progressPageIndex,
        resumeAnchorPage = resumePageIndex.coerceIn(0, lastPageIndex),
        segment = resumeSegment,
    )
}

internal fun completedComicPageIndex(
    pageCount: Int,
): Int = (pageCount - 1).coerceAtLeast(0)
