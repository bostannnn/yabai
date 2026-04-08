package com.example.unifiedreader.feature.reader.comics

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicsReaderRestoreSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reader_restores_latest_page_after_close_and_reopen() {
        val item = smokeTestItem()
        val progressStore = SmokeProgressStore(
            initialPages = mapOf(item.id to 1),
        )
        val preferencesStore = SmokePreferencesStore()
        val pageSource = SmokeComicPageSource(pageCount = 3)

        composeRule.setReaderContent(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
            onBeforeClose = progressStore::enablePersistence,
        )

        composeRule.waitForText("2 / 3")
        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-1"))
            .performTouchInput { swipeLeft() }
        composeRule.waitForText("3 / 3")
        assertEquals(null, progressStore.persistedPages[item.id])
        composeRule.onNodeWithContentDescription("Back to library").performClick()
        composeRule.waitForText("Reader closed")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            progressStore.persistedPages[item.id] == 2
        }
        assertEquals(2, progressStore.persistedPages[item.id])
        composeRule.onNodeWithTag("reopen-reader").performClick()
        composeRule.waitForText("3 / 3")
    }

    @Test
    fun reader_persists_latest_page_when_activity_stops() {
        val item = smokeTestItem()
        val progressStore = SmokeProgressStore(
            initialPages = mapOf(item.id to 1),
        )
        val preferencesStore = SmokePreferencesStore()
        val pageSource = SmokeComicPageSource(pageCount = 3)

        composeRule.setReaderContent(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
        )

        composeRule.waitForText("2 / 3")
        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-1"))
            .performTouchInput { swipeLeft() }
        composeRule.waitForText("3 / 3")
        assertEquals(null, progressStore.persistedPages[item.id])

        progressStore.enablePersistence()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            progressStore.persistedPages[item.id] == 2
        }
        assertEquals(2, progressStore.persistedPages[item.id])

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForText("3 / 3")
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.setReaderContent(
    item: ReadableItem,
    pageSource: ComicPageSource,
    progressStore: ComicReaderProgressStore,
    preferencesStore: ComicReaderPreferencesStore,
    onBeforeClose: () -> Unit = {},
) {
    setContent {
        MaterialTheme {
            ReaderSmokeHarness(
                item = item,
                pageSource = pageSource,
                progressStore = progressStore,
                preferencesStore = preferencesStore,
                onBeforeClose = onBeforeClose,
            )
        }
    }
    waitForIdle()
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForText(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

@Composable
private fun ReaderSmokeHarness(
    item: ReadableItem,
    pageSource: ComicPageSource,
    progressStore: ComicReaderProgressStore,
    preferencesStore: ComicReaderPreferencesStore,
    onBeforeClose: () -> Unit,
) {
    val isReaderOpen = remember { mutableStateOf(true) }
    if (isReaderOpen.value) {
        ComicsReaderScreen(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
            onBack = {
                onBeforeClose()
                isReaderOpen.value = false
            },
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Reader closed",
                modifier = Modifier
                    .testTag("reopen-reader")
                    .clickable { isReaderOpen.value = true },
            )
        }
    }
}

private class SmokeProgressStore(
    initialPages: Map<String, Int>,
) : ComicReaderProgressStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPagesFlow = MutableStateFlow(emptyMap<String, Int>())
    private val initialPages = initialPages.toMap()
    val persistedPages = mutableMapOf<String, Int>()
    private var persistenceEnabled = false

    override fun loadPage(item: ReadableItem): Int? = persistedPages[item.id] ?: initialPages[item.id]

    fun enablePersistence() {
        persistenceEnabled = true
    }

    override fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
    ) {
        if (!persistenceEnabled) return
        persistedPages[item.id] = pageIndex.coerceIn(0, pageCount - 1)
    }
}

private class SmokePreferencesStore : ComicReaderPreferencesStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>> =
        MutableStateFlow(
            emptyMap(),
        )

    override fun loadReadingDirection(item: ReadableItem): ComicReadingDirection = ComicReadingDirection.LTR

    override fun loadPageFitMode(item: ReadableItem): ComicPageFitMode = ComicPageFitMode.FitPage

    override fun loadSpreadMode(item: ReadableItem): ComicSpreadMode = ComicSpreadMode.Single
}

private class SmokeComicPageSource(
    private val pageCount: Int,
) : ComicPageSource {
    private val imagesById = List(pageCount) { index ->
        "page-$index" to Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(
                when (index) {
                    0 -> android.graphics.Color.RED
                    1 -> android.graphics.Color.GREEN
                    else -> android.graphics.Color.BLUE
                },
            )
        }.asImageBitmap()
    }.toMap()

    override fun getPages(item: ReadableItem): List<ComicPageSpec> = List(pageCount) { index ->
        ComicPageSpec(
            id = "page-$index",
            contentDescription = "${item.title} page ${index + 1}",
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageLoadResult = ComicPageLoadResult.Success(
        image = imagesById.getValue(page.id),
    )
}

private fun smokeTestItem(): ReadableItem = ReadableItem(
    id = "smoke-comic",
    sourceType = SourceType.KAVITA,
    libraryId = "k-comics",
    remoteId = "smoke-comic",
    title = "Smoke Test Comic",
    format = FormatType.CBZ,
    description = "Reader restore smoke test",
    coverUrl = null,
)
