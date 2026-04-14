package com.example.unifiedreader.feature.reader.comics

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicPagesStateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun observed_pages_rebuild_when_item_metadata_changes() {
        var item by mutableStateOf(testComicItem(pageCount = 3))
        val pageSource = FlowBackedComicPageSource { readableItem ->
            readableItem.pageCount ?: 0
        }

        composeRule.setContent {
            val pages = rememberObservedComicPages(
                item = item,
                pageSource = pageSource,
            )
            Text("pages=${pages.size}")
        }

        composeRule.waitForText("pages=3")
        composeRule.runOnUiThread {
            item = item.copy(pageCount = 5)
        }
        composeRule.waitForText("pages=5")
    }

    @Test
    fun observed_pages_rebuild_when_source_pages_change_for_same_item() {
        val item = testComicItem(pageCount = 5)
        var recompositionNonce by mutableStateOf(0)
        val pageSource = ObservableComicPageSource(initialPageCount = 5)

        composeRule.setContent {
            val trigger = recompositionNonce
            val pages = rememberObservedComicPages(
                item = item,
                pageSource = pageSource,
            )
            Text("pages=${pages.size}:trigger=$trigger")
        }

        composeRule.waitForText("pages=5:trigger=0")
        composeRule.runOnUiThread {
            pageSource.setPageCount(2)
            recompositionNonce = 2
        }
        composeRule.waitForText("pages=2:trigger=2")
        assertEquals(1, pageSource.getPagesCallCount)
    }
}

private class FlowBackedComicPageSource(
    private val pageCountFor: (ReadableItem) -> Int,
) : ComicPageSource {
    override fun getPages(item: ReadableItem): List<ComicPageSpec> = pageSpecs(
        item = item,
        pageCount = pageCountFor(item),
    )

    override fun observePages(item: ReadableItem) = flowOf(getPages(item))

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = error("Not used in this test")
}

private class ObservableComicPageSource(
    initialPageCount: Int,
) : ComicPageSource {
    private val pagesState = MutableStateFlow(pageSpecs(testComicItem(pageCount = initialPageCount), initialPageCount))

    var getPagesCallCount: Int = 0
        private set

    override fun getPages(item: ReadableItem): List<ComicPageSpec> {
        getPagesCallCount += 1
        return pagesState.value
    }

    override fun observePages(item: ReadableItem) = pagesState

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = error("Not used in this test")

    fun setPageCount(pageCount: Int) {
        pagesState.value = pageSpecs(testComicItem(pageCount = pageCount), pageCount)
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForText(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun pageSpecs(
    item: ReadableItem,
    pageCount: Int,
): List<ComicPageSpec> = List(pageCount.coerceAtLeast(0)) { index ->
    ComicPageSpec(
        id = "page-$index",
        pageIndex = index,
        contentDescription = "${item.title} page ${index + 1}",
    )
}

private fun testComicItem(
    pageCount: Int,
): ReadableItem = ReadableItem(
    id = "comic-pages-test",
    sourceType = SourceType.KAVITA,
    libraryId = "k-comics",
    remoteId = "comic-pages-test",
    title = "Comic Pages Test",
    format = FormatType.CBZ,
    pageCount = pageCount,
)
