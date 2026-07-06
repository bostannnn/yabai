package com.example.unifiedreader.feature.reader.comics

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComicPageCurlCompositeCacheTest {
    @Test
    fun `prewarm keeps only settled spread and adjacent composites`() = runBlocking {
        val pages = sampleCurlCachePages(count = 5)
        val spreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = false,
            pageMetadataById = emptyMap(),
        )
        val cache = ComicPageCurlCompositeCache()
        val pageSource = TestCurlCachePageSource()
        val canvasSize = IntSize(120, 180)

        cache.prewarm(
            item = curlCacheItem(),
            spreads = spreads,
            settledSpreadIndex = 2,
            pageSource = pageSource,
            canvasSize = canvasSize,
            pageFitMode = ComicPageFitMode.FitPage,
            stitchedSpreadOverlapPx = 0,
        )

        assertEquals(3, cache.cachedCompositeCount)
        assertNull(cache.compositeFor(spreads[0], canvasSize, ComicPageFitMode.FitPage))
        assertNotNull(cache.compositeFor(spreads[1], canvasSize, ComicPageFitMode.FitPage))
        assertNotNull(cache.compositeFor(spreads[2], canvasSize, ComicPageFitMode.FitPage))
        assertNotNull(cache.compositeFor(spreads[3], canvasSize, ComicPageFitMode.FitPage))

        cache.prewarm(
            item = curlCacheItem(),
            spreads = spreads,
            settledSpreadIndex = 4,
            pageSource = pageSource,
            canvasSize = canvasSize,
            pageFitMode = ComicPageFitMode.FitPage,
            stitchedSpreadOverlapPx = 0,
        )

        assertEquals(2, cache.cachedCompositeCount)
        assertNull(cache.compositeFor(spreads[1], canvasSize, ComicPageFitMode.FitPage))
        assertNotNull(cache.compositeFor(spreads[3], canvasSize, ComicPageFitMode.FitPage))
        assertNotNull(cache.compositeFor(spreads[4], canvasSize, ComicPageFitMode.FitPage))
    }

    @Test
    fun `failed adjacent composite stays unavailable for pager fallback`() = runBlocking {
        val pages = sampleCurlCachePages(count = 3)
        val spreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = false,
            pageMetadataById = emptyMap(),
        )
        val cache = ComicPageCurlCompositeCache()
        val pageSource = TestCurlCachePageSource(failingPageIds = setOf(pages[1].id))
        val canvasSize = IntSize(120, 180)

        cache.prewarm(
            item = curlCacheItem(),
            spreads = spreads,
            settledSpreadIndex = 0,
            pageSource = pageSource,
            canvasSize = canvasSize,
            pageFitMode = ComicPageFitMode.FitPage,
            stitchedSpreadOverlapPx = 0,
        )

        assertNotNull(cache.compositeFor(spreads[0], canvasSize, ComicPageFitMode.FitPage))
        assertNull(cache.compositeFor(spreads[1], canvasSize, ComicPageFitMode.FitPage))
    }

    @Test
    fun `cancelled prewarm does not populate stale composites`() = runBlocking {
        val pages = sampleCurlCachePages(count = 3)
        val spreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = false,
            pageMetadataById = emptyMap(),
        )
        val cache = ComicPageCurlCompositeCache()
        val pageSource = SuspendingCurlCachePageSource()
        val canvasSize = IntSize(120, 180)

        val prewarmJob = launch {
            cache.prewarm(
                item = curlCacheItem(),
                spreads = spreads,
                settledSpreadIndex = 1,
                pageSource = pageSource,
                canvasSize = canvasSize,
                pageFitMode = ComicPageFitMode.FitPage,
                stitchedSpreadOverlapPx = 0,
            )
        }
        pageSource.firstLoadStarted.await()

        prewarmJob.cancelAndJoin()

        assertEquals(0, cache.cachedCompositeCount)
    }
}

private class TestCurlCachePageSource(
    private val failingPageIds: Set<String> = emptySet(),
) : ComicPageSource {
    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> = emptyList()

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        if (page.id in failingPageIds) {
            return ComicPageLoadResult.Failure("missing test page")
        }
        return ComicPageLoadResult.Success(
            Bitmap.createBitmap(24, 36, Bitmap.Config.ARGB_8888).asImageBitmap(),
        )
    }
}

private class SuspendingCurlCachePageSource : ComicPageSource {
    val firstLoadStarted = CompletableDeferred<Unit>()

    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> = emptyList()

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        firstLoadStarted.complete(Unit)
        awaitCancellation()
    }
}

private fun sampleCurlCachePages(
    count: Int,
): List<ComicPageSpec> = List(count) { index ->
    ComicPageSpec(
        id = "page-$index",
        pageIndex = index,
        contentDescription = "Page ${index + 1}",
    )
}

private fun curlCacheItem(): ReadableItem = ReadableItem(
    id = "local:comic:curl-cache",
    libraryId = "local:library",
    sourceType = SourceType.LOCAL,
    title = "Curl Cache",
    format = FormatType.CBZ,
)
