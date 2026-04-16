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
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-1-full"))
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
        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-1-full"))
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

    @Test
    fun reader_retries_required_metadata_until_spread_is_ready() {
        val item = smokeTestItem()
        val progressStore = SmokeProgressStore(
            initialPages = mapOf(item.id to 2),
        )
        val preferencesStore = SmokePreferencesStore(
            spreadMode = ComicSpreadMode.Double,
        )
        val pageSource = RetryingMetadataComicPageSource(
            pageCount = 4,
            delayedPageIndex = 3,
            failuresBeforeSuccess = 2,
        )

        composeRule.setReaderContent(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
        )

        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-1")).assertExists()
        composeRule.waitUntil(timeoutMillis = 6_000) {
            pageSource.requiredPageAttempts == 3
        }
        Thread.sleep(1_500)
        assertEquals(3, pageSource.requiredPageAttempts)
    }

    @Test
    fun reader_falls_back_after_permanent_required_metadata_failures() {
        val item = smokeTestItem()
        val progressStore = SmokeProgressStore(
            initialPages = mapOf(item.id to 2),
        )
        val preferencesStore = SmokePreferencesStore(
            spreadMode = ComicSpreadMode.Double,
        )
        val pageSource = RetryingMetadataComicPageSource(
            pageCount = 4,
            delayedPageIndex = 3,
            failuresBeforeSuccess = Int.MAX_VALUE,
        )

        composeRule.setReaderContent(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
        )

        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-1")).assertExists()
        composeRule.waitUntil(timeoutMillis = 6_000) {
            pageSource.requiredPageAttempts == 3
        }
        Thread.sleep(1_500)
        assertEquals(3, pageSource.requiredPageAttempts)
    }

    @Test
    fun reader_does_not_reclassify_after_temporary_fallback_threshold() {
        val item = smokeTestItem()
        val progressStore = SmokeProgressStore(
            initialPages = mapOf(item.id to 2),
        )
        val preferencesStore = SmokePreferencesStore(
            spreadMode = ComicSpreadMode.Double,
        )
        val pageSource = RetryingMetadataComicPageSource(
            pageCount = 4,
            delayedPageIndex = 3,
            failuresBeforeSuccess = 3,
        )

        composeRule.setReaderContent(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
        )

        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-1")).assertExists()
        composeRule.waitUntil(timeoutMillis = 7_000) {
            pageSource.requiredPageAttempts == 3
        }
        Thread.sleep(1_500)
        assertEquals(3, pageSource.requiredPageAttempts)
    }

    @Test
    fun reader_does_not_persist_resume_anchor_before_preferences_hydrate() {
        val item = smokeTestItem()
        val progressStore = SmokeProgressStore(
            restoredPages = mapOf(item.id to 2),
        )
        val preferencesStore = DelayedPreferencesStore(
            item = item,
            snapshot = ComicReaderPreferencesSnapshot(
                readerMode = com.example.unifiedreader.feature.reader.preferences.ComicReaderMode.Paged,
                readingDirection = ComicReadingDirection.LTR,
                pageFitMode = ComicPageFitMode.FitPage,
                spreadMode = ComicSpreadMode.Double,
                splitWidePages = false,
                resumeAnchorPage = 1,
                resumeSegment = null,
            ),
        )
        val pageSource = SmokeComicPageSource(pageCount = 4)

        composeRule.setReaderContent(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
        )

        composeRule.waitForText("3 / 4")
        assertEquals(0, progressStore.saveCalls)
        assertTrue(preferencesStore.savedResumeAnchors.isEmpty())

        preferencesStore.hydrate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            preferencesStore.savedResumeAnchors.isNotEmpty() && progressStore.saveCalls > 0
        }
        assertEquals(1, preferencesStore.savedResumeAnchors.first())
        assertFalse(preferencesStore.savedResumeAnchors.contains(2))
    }

    @Test
    fun reader_start_from_beginning_ignores_saved_progress_for_auto_next_open() {
        val item = smokeTestItem()
        val progressStore = SmokeProgressStore(
            initialPages = mapOf(item.id to 2),
            restoredPages = mapOf(item.id to 2),
        )
        val preferencesStore = SmokePreferencesStore()
        val pageSource = SmokeComicPageSource(pageCount = 4)

        composeRule.setReaderContent(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
            openMode = ReaderOpenMode.StartFromBeginning,
        )

        composeRule.waitForText("1 / 4")
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.setReaderContent(
    item: ReadableItem,
    pageSource: ComicPageSource,
    progressStore: ComicReaderProgressStore,
    preferencesStore: ComicReaderPreferencesStore,
    openMode: ReaderOpenMode = ReaderOpenMode.Default,
    onBeforeClose: () -> Unit = {},
) {
    setContent {
        MaterialTheme {
            ReaderSmokeHarness(
                item = item,
                pageSource = pageSource,
                progressStore = progressStore,
                preferencesStore = preferencesStore,
                openMode = openMode,
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
    openMode: ReaderOpenMode,
    onBeforeClose: () -> Unit,
) {
    val isReaderOpen = remember { mutableStateOf(true) }
    if (isReaderOpen.value) {
        ComicsReaderScreen(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
            openMode = openMode,
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
    initialPages: Map<String, Int> = emptyMap(),
    restoredPages: Map<String, Int> = emptyMap(),
) : ComicReaderProgressStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPagesFlow = MutableStateFlow(restoredPages)
    private val initialPages = initialPages.toMap()
    val persistedPages = mutableMapOf<String, Int>()
    var saveCalls = 0
        private set
    private var persistenceEnabled = false

    override fun loadPage(item: ReadableItem): Int? = persistedPages[item.id] ?: initialPages[item.id]

    fun enablePersistence() {
        persistenceEnabled = true
    }

    override fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
        isFinished: Boolean,
    ) {
        saveCalls += 1
        if (!persistenceEnabled) return
        persistedPages[item.id] = pageIndex.coerceIn(0, pageCount - 1)
    }
}

private open class SmokePreferencesStore(
    private val spreadMode: ComicSpreadMode = ComicSpreadMode.Single,
) : ComicReaderPreferencesStore {
    protected val preferencesState = MutableStateFlow<Map<String, ComicReaderPreferencesSnapshot>>(emptyMap())
    protected val hydrationState = MutableStateFlow(true)
    val savedResumeAnchors = mutableListOf<Int>()

    override val isHydratedFlow: StateFlow<Boolean> = hydrationState
    override val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>> =
        preferencesState

    override fun loadReadingDirection(item: ReadableItem): ComicReadingDirection = ComicReadingDirection.LTR

    override fun loadPageFitMode(item: ReadableItem): ComicPageFitMode = ComicPageFitMode.FitPage

    override fun loadSpreadMode(item: ReadableItem): ComicSpreadMode = spreadMode

    override fun saveResumeAnchorPage(item: ReadableItem, pageIndex: Int?) {
        pageIndex?.let(savedResumeAnchors::add)
    }
}

private class DelayedPreferencesStore(
    private val item: ReadableItem,
    private val snapshot: ComicReaderPreferencesSnapshot,
) : SmokePreferencesStore(spreadMode = snapshot.spreadMode) {
    init {
        setHydrated(false)
    }

    fun hydrate() {
        preferencesState.value = mapOf(item.id to snapshot)
        setHydrated(true)
    }

    override fun loadResumeAnchorPage(item: ReadableItem): Int? = null

    override fun loadResumeSegment(item: ReadableItem): ComicPageSegment? = null

    private fun setHydrated(value: Boolean) {
        hydrationState.value = value
    }
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
            pageIndex = index,
            contentDescription = "${item.title} page ${index + 1}",
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = ComicPageLoadResult.Success(
        image = imagesById.getValue(page.id),
    )
}

private class RetryingMetadataComicPageSource(
    private val pageCount: Int,
    private val delayedPageIndex: Int,
    private val failuresBeforeSuccess: Int,
) : ComicPageSource {
    private val imagesById = List(pageCount) { index ->
        "page-$index" to Bitmap.createBitmap(
            if (index == delayedPageIndex) 96 else 32,
            48,
            Bitmap.Config.ARGB_8888,
        ).apply {
            eraseColor(android.graphics.Color.rgb((80 * index) % 255, 40, 120))
        }.asImageBitmap()
    }.toMap()
    var requiredPageAttempts = 0
        private set

    override fun getPages(item: ReadableItem): List<ComicPageSpec> = List(pageCount) { index ->
        ComicPageSpec(
            id = "page-$index",
            pageIndex = index,
            contentDescription = "${item.title} page ${index + 1}",
        )
    }

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? = null

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        if (page.pageIndex == delayedPageIndex && priority == ComicPageLoadPriority.Prefetch) {
            requiredPageAttempts += 1
            if (requiredPageAttempts <= failuresBeforeSuccess) {
                return ComicPageLoadResult.Failure("Metadata warmup failed")
            }
        }
        return ComicPageLoadResult.Success(imagesById.getValue(page.id))
    }
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
