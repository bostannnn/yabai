package com.example.unifiedreader.feature.reader.comics

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComicSpreadRasterizerTest {
    @Test
    fun `layout uses segment crop window for fit page sizing`() {
        val page = ComicSpreadPage(
            spec = sampleRasterPage(0),
            segment = ComicPageSegment.RightHalf,
            metadata = ComicPageMetadata(width = 101, height = 200),
        )

        val layout = ComicSpreadRasterizer.layout(
            spread = sampleRasterSpread(page),
            imageSizesByPageId = mapOf(page.spec.id to IntSize(101, 200)),
            canvasSize = IntSize(300, 300),
            pageFitMode = ComicPageFitMode.FitPage,
        )

        val placement = layout.pages.single()
        assertEquals(ComicPageSourceWindow(offsetX = 51, width = 50, height = 200), placement.sourceWindow)
        assertEquals(
            ComicRasterRect(left = 113, top = 0, width = 75, height = 300),
            placement.destinationBounds,
        )
    }

    @Test
    fun `layout keeps fit width overflow centered inside page bounds`() {
        val page = ComicSpreadPage(
            spec = sampleRasterPage(0),
            metadata = ComicPageMetadata(width = 100, height = 200),
        )

        val layout = ComicSpreadRasterizer.layout(
            spread = sampleRasterSpread(page),
            imageSizesByPageId = mapOf(page.spec.id to IntSize(100, 200)),
            canvasSize = IntSize(300, 300),
            pageFitMode = ComicPageFitMode.FitWidth,
        )

        val placement = layout.pages.single()
        assertEquals(ComicRasterRect(left = 0, top = 0, width = 300, height = 300), placement.pageBounds)
        assertEquals(
            ComicRasterRect(left = 0, top = -150, width = 300, height = 600),
            placement.destinationBounds,
        )
    }

    @Test
    fun `layout uses fit height sizing inside page bounds`() {
        val page = ComicSpreadPage(
            spec = sampleRasterPage(0),
            metadata = ComicPageMetadata(width = 100, height = 200),
        )

        val layout = ComicSpreadRasterizer.layout(
            spread = sampleRasterSpread(page),
            imageSizesByPageId = mapOf(page.spec.id to IntSize(100, 200)),
            canvasSize = IntSize(300, 300),
            pageFitMode = ComicPageFitMode.FitHeight,
        )

        val placement = layout.pages.single()
        assertEquals(ComicRasterRect(left = 0, top = 0, width = 300, height = 300), placement.pageBounds)
        assertEquals(
            ComicRasterRect(left = 75, top = 0, width = 150, height = 300),
            placement.destinationBounds,
        )
    }

    @Test
    fun `fit page double spread preserves rtl page order and centered spread bounds`() {
        val pages = sampleRasterPages(count = 4)
        val spread = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.RTL,
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

        assertEquals(listOf(2, 1), layout.pages.map { it.page.pageIndex })
        assertEquals(ComicRasterRect(left = 100, top = 0, width = 150, height = 300), layout.pages[0].pageBounds)
        assertEquals(ComicRasterRect(left = 250, top = 0, width = 150, height = 300), layout.pages[1].pageBounds)
        assertEquals(
            ComicRasterRect(left = 100, top = 0, width = 150, height = 300),
            layout.pages[0].destinationBounds,
        )
        assertEquals(
            ComicRasterRect(left = 250, top = 0, width = 150, height = 300),
            layout.pages[1].destinationBounds,
        )
    }

    @Test
    fun `layout applies stitched spread overlap to the second page`() {
        val pages = sampleRasterPages(count = 3)
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
            canvasSize = IntSize(400, 200),
            pageFitMode = ComicPageFitMode.FitPage,
            stitchedSpreadOverlapPx = 1,
        )

        assertEquals(ComicRasterRect(left = 100, top = 0, width = 100, height = 200), layout.pages[0].pageBounds)
        assertEquals(ComicRasterRect(left = 199, top = 0, width = 100, height = 200), layout.pages[1].pageBounds)
        assertEquals(
            ComicRasterRect(left = 199, top = 0, width = 100, height = 200),
            layout.pages[1].clipBounds,
        )
    }

    @Test
    fun `rasterize returns an image bitmap at the requested canvas size`() {
        val page = ComicSpreadPage(
            spec = sampleRasterPage(0),
            segment = ComicPageSegment.RightHalf,
        )
        val source = solidBitmap(width = 40, height = 10, color = AndroidColor.GREEN)

        val rendered = ComicSpreadRasterizer.rasterize(
            spread = sampleRasterSpread(page),
            pageImagesById = mapOf(page.spec.id to source.asImageBitmap()),
            canvasSize = IntSize(20, 10),
            pageFitMode = ComicPageFitMode.FitPage,
        )

        assertEquals(20, rendered.width)
        assertEquals(10, rendered.height)
    }

    @Test
    fun `rasterize keeps reader background around centered fit spread`() {
        val pages = sampleRasterPages(count = 3)
        val spread = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = true,
            pageMetadataById = emptyMap(),
        )[1]
        val pageImage = solidBitmap(width = 100, height = 200, color = AndroidColor.BLUE).asImageBitmap()

        val rendered = ComicSpreadRasterizer.rasterize(
            spread = spread,
            pageImagesById = mapOf(
                pages[1].id to pageImage,
                pages[2].id to pageImage,
            ),
            canvasSize = IntSize(500, 300),
            pageFitMode = ComicPageFitMode.FitPage,
        ).asAndroidBitmap()

        assertEquals(500, rendered.width)
        assertEquals(300, rendered.height)
        assertEquals(ComicReaderBackgroundColor.toArgb(), rendered.getPixel(0, 0))
    }
}

private fun sampleRasterPages(
    count: Int,
): List<ComicPageSpec> = List(count) { index -> sampleRasterPage(index) }

private fun sampleRasterPage(
    index: Int,
): ComicPageSpec = ComicPageSpec(
    id = "page-$index",
    pageIndex = index,
    contentDescription = "Page ${index + 1}",
)

private fun sampleRasterSpread(
    vararg pages: ComicSpreadPage,
): ComicPageSpread = ComicPageSpread(
    id = "spread-${pages.joinToString("-") { it.spec.id }}",
    pages = pages.toList(),
    pageIndexes = pages.map { it.pageIndex },
    resumePageIndex = pages.first().pageIndex,
    progressLabel = pages.joinToString { (it.pageIndex + 1).toString() },
)

private fun solidBitmap(
    width: Int,
    height: Int,
    color: Int,
): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
    eraseColor(color)
}
