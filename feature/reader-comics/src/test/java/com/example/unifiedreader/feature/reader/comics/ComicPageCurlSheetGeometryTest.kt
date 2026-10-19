package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `spread turn hinges at the gutter and lands on the facing page`() {
        val pages = sampleCurlGeometryPages(count = 5)
        val spreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = true,
            pageMetadataById = emptyMap(),
        )
        val current = sampleCurlGeometryLayout(*spreads[1].pages.toTypedArray())
        val next = sampleCurlGeometryLayout(*spreads[2].pages.toTypedArray())

        val forward = resolveComicPageCurlTurn(
            currentLayout = current,
            targetLayout = next,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        ) ?: error("Expected a spread turn")
        val backward = resolveComicPageCurlTurn(
            currentLayout = current,
            targetLayout = next,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Previous,
        ) ?: error("Expected a spread turn")

        assertEquals(ComicRasterRect(left = 250, top = 0, width = 150, height = 300), forward.paperBounds)
        assertEquals(250f, forward.sheet.hingeX, 0.0001f)
        assertEquals(1f, forward.sheet.outward, 0.0001f)
        assertEquals(ComicRasterRect(left = 100, top = 0, width = 300, height = 300), forward.bookBounds)
        assertTrue(forward.backIsFacingPage)
        assertEquals(ComicPageCurlFollow.Edge, forward.follow)
        assertEquals(250f, backward.sheet.hingeX, 0.0001f)
        assertEquals(-1f, backward.sheet.outward, 0.0001f)
        assertEquals(ComicPageCurlFollow.Edge, backward.follow)
    }

    @Test
    fun `single page turns hinge on the reading start edge and go back with the previous page`() {
        val current = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(1)),
            canvasSize = IntSize(300, 300),
        )
        val adjacent = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(0)),
            canvasSize = IntSize(300, 300),
        )

        val forward = resolveComicPageCurlTurn(
            currentLayout = current,
            targetLayout = adjacent,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        ) ?: error("Expected a single page turn")
        val backward = resolveComicPageCurlTurn(
            currentLayout = current,
            targetLayout = adjacent,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Previous,
        ) ?: error("Expected a single page turn")
        val rtlForward = resolveComicPageCurlTurn(
            currentLayout = current,
            targetLayout = adjacent,
            readingDirection = ComicReadingDirection.RTL,
            target = ComicPageCurlTarget.Next,
        ) ?: error("Expected a single page turn")

        assertEquals(ComicRasterRect(left = 75, top = 0, width = 150, height = 300), forward.paperBounds)
        assertEquals(forward.paperBounds, forward.bookBounds)
        assertEquals(75f, forward.sheet.hingeX, 0.0001f)
        assertEquals(ComicPageCurlFollow.Edge, forward.follow)
        assertTrue(!forward.backIsFacingPage)
        assertEquals(ComicPageCurlFollow.Fold, backward.follow)
        assertTrue(backward.sheetIsAdjacentPage)
        assertEquals(75f, backward.sheet.hingeX, 0.0001f)
        assertEquals(225f, rtlForward.sheet.hingeX, 0.0001f)
        assertEquals(-1f, rtlForward.sheet.outward, 0.0001f)
    }

    @Test
    fun `turning leaf may swing above and below a letterboxed page but not past its sides`() {
        val current = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(1)),
            canvasSize = IntSize(300, 700),
        )
        val adjacent = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(2)),
            canvasSize = IntSize(300, 700),
        )

        val turn = resolveComicPageCurlTurn(
            currentLayout = current,
            targetLayout = adjacent,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        ) ?: error("Expected a single page turn")

        assertEquals(ComicRasterRect(left = 0, top = 50, width = 300, height = 600), turn.paperBounds)
        assertEquals(ComicRasterRect(left = 0, top = 0, width = 300, height = 700), turn.bookBounds)
    }

    @Test
    fun `incompatible layouts resolve no curl turn`() {
        val single = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(0)),
            canvasSize = IntSize(300, 300),
        )
        val spread = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(1)),
            ComicSpreadPage(spec = sampleCurlGeometryPage(2)),
            canvasSize = IntSize(300, 300),
        )

        assertNull(
            resolveComicPageCurlTurn(
                currentLayout = single,
                targetLayout = spread,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
        )
    }

    @Test
    fun `transition policy allows compatible single and spread layouts`() {
        val firstSingle = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(0)),
            canvasSize = IntSize(300, 300),
        )
        val nextSingle = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(1)),
            canvasSize = IntSize(300, 300),
        )
        val firstSpread = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(2)),
            ComicSpreadPage(spec = sampleCurlGeometryPage(3)),
        )
        val nextSpread = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(4)),
            ComicSpreadPage(spec = sampleCurlGeometryPage(5)),
        )

        assertEquals(
            ComicPageCurlTransitionPolicy.UseCurl,
            resolveComicPageCurlTransitionPolicy(
                currentLayout = firstSingle,
                targetLayout = nextSingle,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
        )
        assertEquals(
            ComicPageCurlTransitionPolicy.UseCurl,
            resolveComicPageCurlTransitionPolicy(
                currentLayout = firstSpread,
                targetLayout = nextSpread,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
        )
    }

    @Test
    fun `transition policy yields when single and spread layouts differ`() {
        val single = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(0)),
            canvasSize = IntSize(300, 300),
        )
        val spread = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = sampleCurlGeometryPage(1)),
            ComicSpreadPage(spec = sampleCurlGeometryPage(2)),
        )

        assertEquals(
            ComicPageCurlTransitionPolicy.YieldToPager,
            resolveComicPageCurlTransitionPolicy(
                currentLayout = single,
                targetLayout = spread,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
        )
        assertEquals(
            ComicPageCurlTransitionPolicy.YieldToPager,
            resolveComicPageCurlTransitionPolicy(
                currentLayout = spread,
                targetLayout = single,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
        )
    }

    @Test
    fun `transition policy yields when same layout bounds differ too much`() {
        val portraitPage = sampleCurlGeometryPage(0)
        val widePage = sampleCurlGeometryPage(1)
        val portraitLayout = sampleCurlGeometryLayout(
            ComicSpreadPage(spec = portraitPage),
            canvasSize = IntSize(300, 300),
            imageSizesByPageId = mapOf(portraitPage.id to IntSize(100, 200)),
        )
        val wideLayout = sampleCurlGeometryLayout(
            ComicSpreadPage(
                spec = widePage,
                metadata = ComicPageMetadata(width = 2400, height = 1200),
            ),
            canvasSize = IntSize(300, 300),
            imageSizesByPageId = mapOf(widePage.id to IntSize(2400, 1200)),
        )

        assertEquals(
            ComicPageCurlTransitionPolicy.YieldToPager,
            resolveComicPageCurlTransitionPolicy(
                currentLayout = portraitLayout,
                targetLayout = wideLayout,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
        )
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

private fun sampleCurlGeometryLayout(
    vararg pages: ComicSpreadPage,
    canvasSize: IntSize = IntSize(500, 300),
    imageSizesByPageId: Map<String, IntSize> = pages.associate { page ->
        page.spec.id to IntSize(100, 200)
    },
): ComicSpreadRasterLayout = ComicSpreadRasterizer.layout(
    spread = sampleCurlGeometrySpread(*pages),
    imageSizesByPageId = imageSizesByPageId,
    canvasSize = canvasSize,
    pageFitMode = ComicPageFitMode.FitPage,
)
