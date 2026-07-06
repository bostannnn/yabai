package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicPageCurlSheetGeometryTest {
    @Test
    fun `single page curl bounds exclude letterbox`() {
        val page = sampleCurlGeometryPage(0)
        val layout = ComicSpreadRasterizer.layout(
            spread = sampleCurlGeometrySpread(ComicSpreadPage(spec = page)),
            imageSizesByPageId = mapOf(page.id to IntSize(100, 200)),
            canvasSize = IntSize(300, 300),
            pageFitMode = ComicPageFitMode.FitPage,
        )

        val sheet = resolveComicPageCurlSheetBounds(
            layout = layout,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        ) ?: error("Expected single page sheet bounds")

        assertEquals(ComicPageCurlSheetSide.Right, sheet.side)
        assertEquals(ComicRasterRect(left = 75, top = 0, width = 150, height = 300), sheet.bounds)
    }

    @Test
    fun `wide solo page curl bounds exclude vertical letterbox`() {
        val page = sampleCurlGeometryPage(0)
        val layout = ComicSpreadRasterizer.layout(
            spread = sampleCurlGeometrySpread(
                ComicSpreadPage(
                    spec = page,
                    metadata = ComicPageMetadata(width = 2400, height = 1200),
                ),
            ),
            imageSizesByPageId = mapOf(page.id to IntSize(2400, 1200)),
            canvasSize = IntSize(400, 400),
            pageFitMode = ComicPageFitMode.FitPage,
        )

        val sheet = resolveComicPageCurlSheetBounds(
            layout = layout,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        ) ?: error("Expected wide page sheet bounds")

        assertEquals(ComicRasterRect(left = 0, top = 100, width = 400, height = 200), sheet.bounds)
    }

    @Test
    fun `split page curl bounds use visible segment bounds`() {
        val page = sampleCurlGeometryPage(0)
        val layout = ComicSpreadRasterizer.layout(
            spread = sampleCurlGeometrySpread(
                ComicSpreadPage(
                    spec = page,
                    segment = ComicPageSegment.RightHalf,
                    metadata = ComicPageMetadata(width = 101, height = 200),
                ),
            ),
            imageSizesByPageId = mapOf(page.id to IntSize(101, 200)),
            canvasSize = IntSize(300, 300),
            pageFitMode = ComicPageFitMode.FitPage,
        )

        val sheet = resolveComicPageCurlSheetBounds(
            layout = layout,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        ) ?: error("Expected split page sheet bounds")

        assertEquals(ComicRasterRect(left = 113, top = 0, width = 75, height = 300), sheet.bounds)
    }

    @Test
    fun `spread curl uses one page half instead of full spread`() {
        val pages = sampleCurlGeometryPages(count = 3)
        val spread = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = true,
            pageMetadataById = emptyMap(),
        )[1]
        val layout = ComicSpreadRasterizer.layout(
            spread = spread,
            imageSizesByPageId = mapOf(
                pages[1].id to IntSize(100, 200),
                pages[2].id to IntSize(100, 200),
            ),
            canvasSize = IntSize(500, 300),
            pageFitMode = ComicPageFitMode.FitPage,
        )

        val sheet = resolveComicPageCurlSheetBounds(
            layout = layout,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        ) ?: error("Expected spread sheet bounds")

        assertEquals(ComicPageCurlSheetSide.Right, sheet.side)
        assertEquals(ComicRasterRect(left = 250, top = 0, width = 150, height = 300), sheet.bounds)
    }

    @Test
    fun `ltr and rtl outgoing page selection mirrors at the gutter`() {
        assertEquals(
            ComicPageCurlSheetSide.Right,
            resolveComicPageCurlOutgoingSheetSide(
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
        )
        assertEquals(
            ComicPageCurlSheetSide.Left,
            resolveComicPageCurlOutgoingSheetSide(
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Previous,
            ),
        )
        assertEquals(
            ComicPageCurlSheetSide.Left,
            resolveComicPageCurlOutgoingSheetSide(
                readingDirection = ComicReadingDirection.RTL,
                target = ComicPageCurlTarget.Next,
            ),
        )
        assertEquals(
            ComicPageCurlSheetSide.Right,
            resolveComicPageCurlOutgoingSheetSide(
                readingDirection = ComicReadingDirection.RTL,
                target = ComicPageCurlTarget.Previous,
            ),
        )
    }

    @Test
    fun `first and last spread fallback uses adjacent targets and visible page bounds`() {
        val page = sampleCurlGeometryPage(0)
        val layout = ComicSpreadRasterizer.layout(
            spread = sampleCurlGeometrySpread(ComicSpreadPage(spec = page)),
            imageSizesByPageId = mapOf(page.id to IntSize(100, 200)),
            canvasSize = IntSize(300, 300),
            pageFitMode = ComicPageFitMode.FitPage,
        )

        assertNull(
            resolveComicPageCurlTargetSpreadIndex(
                currentSpreadIndex = 0,
                spreadCount = 3,
                target = ComicPageCurlTarget.Previous,
            ),
        )
        assertEquals(
            1,
            resolveComicPageCurlTargetSpreadIndex(
                currentSpreadIndex = 0,
                spreadCount = 3,
                target = ComicPageCurlTarget.Next,
            ),
        )
        assertEquals(
            1,
            resolveComicPageCurlTargetSpreadIndex(
                currentSpreadIndex = 2,
                spreadCount = 3,
                target = ComicPageCurlTarget.Previous,
            ),
        )
        assertNull(
            resolveComicPageCurlTargetSpreadIndex(
                currentSpreadIndex = 2,
                spreadCount = 3,
                target = ComicPageCurlTarget.Next,
            ),
        )

        val firstForwardSheet = resolveComicPageCurlSheetBounds(
            layout = layout,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        ) ?: error("Expected first spread fallback bounds")
        val lastBackwardSheet = resolveComicPageCurlSheetBounds(
            layout = layout,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Previous,
        ) ?: error("Expected last spread fallback bounds")

        assertEquals(ComicPageCurlSheetSide.Right, firstForwardSheet.side)
        assertEquals(ComicPageCurlSheetSide.Left, lastBackwardSheet.side)
        assertEquals(ComicRasterRect(left = 75, top = 0, width = 150, height = 300), firstForwardSheet.bounds)
        assertEquals(ComicRasterRect(left = 75, top = 0, width = 150, height = 300), lastBackwardSheet.bounds)
    }
}

private fun sampleCurlGeometryPages(
    count: Int,
): List<ComicPageSpec> = List(count) { index -> sampleCurlGeometryPage(index) }

private fun sampleCurlGeometryPage(
    index: Int,
): ComicPageSpec = ComicPageSpec(
    id = "page-$index",
    pageIndex = index,
    contentDescription = "Page ${index + 1}",
)

private fun sampleCurlGeometrySpread(
    vararg pages: ComicSpreadPage,
): ComicPageSpread = ComicPageSpread(
    id = "spread-${pages.joinToString("-") { it.spec.id }}",
    pages = pages.toList(),
    pageIndexes = pages.map { it.pageIndex },
    resumePageIndex = pages.first().pageIndex,
    progressLabel = pages.joinToString { (it.pageIndex + 1).toString() },
)
