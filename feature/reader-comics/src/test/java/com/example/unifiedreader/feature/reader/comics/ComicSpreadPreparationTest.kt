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
