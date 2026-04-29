package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.graphics.ImageBitmap
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ComicSpreadPreparationTest {
    @Test
    fun `wide pages produce stable final spread plan before ready`() = runBlocking {
        val pages = samplePreparationPages(count = 6)
        val source = MetadataOnlyPageSource(
            metadataByPageId = mapOf(
                pages[2].id to ComicPageMetadata(width = 2400, height = 1200),
            ),
        )

        val spreadPlanState = prepareComicSpreadPlanState(
            item = samplePreparationItem(),
            pages = pages,
            pageSource = source,
            retryDelayMillis = 0L,
        )
        val spreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = true,
            pageMetadataById = spreadPlanState.metadataByPageId,
        )

        assertEquals(pages.map(ComicPageSpec::id).toSet(), source.requestedPageIds.toSet())
        assertEquals(listOf(0), spreads[0].pageIndexes)
        assertEquals(listOf(1), spreads[1].pageIndexes)
        assertEquals(listOf(2), spreads[2].pageIndexes)
        assertEquals(listOf(3, 4), spreads[3].pageIndexes)
        assertEquals(listOf(5), spreads[4].pageIndexes)
    }

    @Test
    fun `prepared metadata yields same spread list for reader and preview strip`() = runBlocking {
        val pages = samplePreparationPages(count = 5)
        val source = MetadataOnlyPageSource(
            metadataByPageId = mapOf(
                pages[3].id to ComicPageMetadata(width = 2400, height = 1200),
            ),
        )

        val spreadPlanState = prepareComicSpreadPlanState(
            item = samplePreparationItem(),
            pages = pages,
            pageSource = source,
            retryDelayMillis = 0L,
        )
        val readerSpreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.RTL,
            useDoublePageSpreads = true,
            pageMetadataById = spreadPlanState.metadataByPageId,
        )
        val previewSpreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.RTL,
            useDoublePageSpreads = true,
            pageMetadataById = spreadPlanState.metadataByPageId,
        )

        assertEquals(readerSpreads.map { it.pageIndexes }, previewSpreads.map { it.pageIndexes })
        assertEquals(readerSpreads.map { it.id }, previewSpreads.map { it.id })
    }

    @Test
    fun `metadata failure path settles deterministically before reader display`() = runBlocking {
        val pages = samplePreparationPages(count = 3)
        val source = MetadataOnlyPageSource(
            metadataByPageId = mapOf(
                pages[0].id to ComicPageMetadata(width = 1000, height = 1500),
                pages[2].id to ComicPageMetadata(width = 1000, height = 1500),
            ),
        )
        val progress = mutableListOf<ComicSpreadPreparationProgress>()

        val spreadPlanState = prepareComicSpreadPlanState(
            item = samplePreparationItem(),
            pages = pages,
            pageSource = source,
            retryDelayMillis = 0L,
            onProgress = progress::add,
        )

        assertEquals(setOf(pages[0].id, pages[2].id), spreadPlanState.metadataByPageId.keys)
        assertEquals(3, source.requestCountByPageId.getValue(pages[1].id))
        assertEquals(3, progress.last().loadedPageCount)
        assertEquals(3, progress.last().totalPageCount)
    }

    @Test
    fun `thrown metadata failures degrade instead of cancelling preparation`() = runBlocking {
        val pages = samplePreparationPages(count = 2)
        val source = MetadataOnlyPageSource(
            metadataByPageId = mapOf(
                pages[0].id to ComicPageMetadata(width = 1000, height = 1500),
            ),
            throwingPageIds = setOf(pages[1].id),
        )
        val progress = mutableListOf<ComicSpreadPreparationProgress>()

        val spreadPlanState = prepareComicSpreadPlanState(
            item = samplePreparationItem(),
            pages = pages,
            pageSource = source,
            retryDelayMillis = 0L,
            onProgress = progress::add,
        )

        assertEquals(setOf(pages[0].id), spreadPlanState.metadataByPageId.keys)
        assertEquals(3, source.requestCountByPageId.getValue(pages[1].id))
        assertEquals(2, progress.last().loadedPageCount)
        assertEquals(2, progress.last().totalPageCount)
    }

    @Test
    fun `preview state identity changes when spread page composition changes`() {
        val pages = samplePreparationPages(count = 3)
        val pairedSpread = ComicPageSpread(
            id = "spread-1",
            pages = listOf(
                ComicSpreadPage(spec = pages[1]),
                ComicSpreadPage(spec = pages[2]),
            ),
            pageIndexes = listOf(1, 2),
            resumePageIndex = 1,
            progressLabel = "2-3 / 3",
        )
        val soloSpreadWithReusedId = ComicPageSpread(
            id = "spread-1",
            pages = listOf(ComicSpreadPage(spec = pages[1])),
            pageIndexes = listOf(1),
            resumePageIndex = 1,
            progressLabel = "2 / 3",
        )

        assertNotEquals(
            comicSpreadPreviewStateKey(pairedSpread),
            comicSpreadPreviewStateKey(soloSpreadWithReusedId),
        )
    }
}

private class MetadataOnlyPageSource(
    private val metadataByPageId: Map<String, ComicPageMetadata>,
    private val throwingPageIds: Set<String> = emptySet(),
) : ComicPageSource {
    val requestedPageIds = mutableListOf<String>()
    val requestCountByPageId = mutableMapOf<String, Int>()

    override fun getPages(item: ReadableItem): List<ComicPageSpec> = emptyList()

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? {
        requestedPageIds += page.id
        requestCountByPageId[page.id] = requestCountByPageId.getOrDefault(page.id, 0) + 1
        if (page.id in throwingPageIds) {
            error("metadata unavailable for ${page.id}")
        }
        return metadataByPageId[page.id]
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = ComicPageLoadResult.Success(ImageBitmap(1, 1))
}

private fun samplePreparationItem(): ReadableItem = ReadableItem(
    id = "comic-1",
    libraryId = "library-1",
    sourceType = SourceType.LOCAL,
    title = "Comic",
    format = FormatType.CBZ,
)

private fun samplePreparationPages(
    count: Int,
): List<ComicPageSpec> = List(count) { index ->
    ComicPageSpec(
        id = "page-$index",
        pageIndex = index,
        contentDescription = "Page ${index + 1}",
    )
}
