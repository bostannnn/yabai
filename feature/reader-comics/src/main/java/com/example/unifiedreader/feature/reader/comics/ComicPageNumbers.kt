package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.feature.reader.core.ReaderPageNumber

/**
 * The page numbers for a spread: one under each page, centred under the part of the screen the
 * page is drawn on. The placement comes from [ComicSpreadRasterizer.layout], the same layout the
 * page curl draws, from the pages' known sizes, so it is where the page sits before any zoom.
 */
internal fun ComicPageSpread.pageNumbers(
    pageCount: Int,
    containerSize: IntSize,
    pageFitMode: ComicPageFitMode,
): List<ReaderPageNumber> {
    if (pages.isEmpty() || pageCount <= 0) return emptyList()
    // One page, or the two halves of one wide page: a single number in the middle.
    if (pageIndexes.distinct().size < 2 || containerSize.width <= 0 || containerSize.height <= 0) {
        return listOf(ReaderPageNumber(comicPageNumberLabel(resumePageIndex, pageCount)))
    }
    val layout = ComicSpreadRasterizer.layout(
        spread = this,
        imageSizesByPageId = emptyMap(),
        canvasSize = containerSize,
        pageFitMode = pageFitMode,
    )
    return layout.pages.map { placement ->
        val bounds = placement.destinationBounds
        ReaderPageNumber(
            label = comicPageNumberLabel(placement.page.pageIndex, pageCount),
            centerFraction = (bounds.left + bounds.width / 2f) / containerSize.width,
        )
    }
}

internal fun comicPageNumberLabel(pageIndex: Int, pageCount: Int): String = "${pageIndex + 1} / $pageCount"
