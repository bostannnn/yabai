package com.example.unifiedreader.feature.reader.comics

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicsReaderPageCountRetentionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reader_preserves_current_page_when_observed_page_count_changes() {
        val item = testReaderItem(pageCount = 6)
        val pageSource = MutableFlowComicPageSource(initialPageCount = 6)

        composeRule.setContent {
            MaterialTheme {
                ComicsReaderScreen(
                    item = item,
                    pageSource = pageSource,
                    progressStore = TestProgressStore(),
                    preferencesStore = SinglePagePreferencesStore(),
                    onBack = {},
                )
            }
        }

        composeRule.waitForText("1 / 6")
        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-0-full"))
            .performTouchInput { swipeLeft() }
        composeRule.waitForText("2 / 6")
        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-1-full"))
            .performTouchInput { swipeLeft() }
        composeRule.waitForText("3 / 6")

        composeRule.runOnUiThread {
            pageSource.setPageCount(4)
        }

        composeRule.waitForText("3 / 4")
        composeRule.onAllNodesWithText("1 / 4").assertCountEquals(0)
    }
}

private class TestProgressStore : ComicReaderProgressStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPagesFlow: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())

    override fun loadPage(item: ReadableItem): Int? = null

    override fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
        isFinished: Boolean,
    ) = Unit
}

private class SinglePagePreferencesStore : ComicReaderPreferencesStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>> =
        MutableStateFlow(emptyMap())

    override fun loadReaderMode(item: ReadableItem): ComicReaderMode = ComicReaderMode.Paged

    override fun loadSpreadMode(item: ReadableItem): ComicSpreadMode = ComicSpreadMode.Single
}

private class MutableFlowComicPageSource(
    initialPageCount: Int,
) : ComicPageSource {
    private val pagesState = MutableStateFlow(readerPageSpecs(pageCount = initialPageCount))
    private val imagesById = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()

    override fun getPages(item: ReadableItem): List<ComicPageSpec> = pagesState.value

    override fun observePages(item: ReadableItem) = pagesState

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = ComicPageLoadResult.Success(
        imagesById.getOrPut(page.id) {
            Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb((page.pageIndex * 40) % 255, 40, 120))
            }.asImageBitmap()
        },
    )

    fun setPageCount(pageCount: Int) {
        pagesState.value = readerPageSpecs(pageCount = pageCount)
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

private fun readerPageSpecs(
    pageCount: Int,
): List<ComicPageSpec> = List(pageCount.coerceAtLeast(0)) { index ->
    ComicPageSpec(
        id = "page-$index",
        pageIndex = index,
        contentDescription = "Reader page ${index + 1}",
    )
}

private fun testReaderItem(
    pageCount: Int,
): ReadableItem = ReadableItem(
    id = "reader-page-count-test",
    sourceType = SourceType.KAVITA,
    libraryId = "k-comics",
    remoteId = "reader-page-count-test",
    title = "Reader Page Count Test",
    format = FormatType.CBZ,
    pageCount = pageCount,
)
