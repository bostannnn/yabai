package com.example.unifiedreader.feature.reader.epub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubReaderEdgeTapSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<FragmentActivity>()

    @Test
    fun topCenterTapOnLiveEpubSurfaceShowsOverlay() {
        val sessionStore = FakeReaderSessionStore()
        val item = sampleEpubItem()

        composeRule.setContent {
            EpubReaderScreen(
                item = item,
                sessionStore = sessionStore,
                onBack = {},
            )
        }

        waitForReaderReady(sessionStore, item.id)
        hideOverlay()

        tapViewportAtTopEdge()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(EpubReaderOverlayTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(EpubReaderOverlayTag).assertExists()
    }

    @Test
    fun bottomCenterTapOnLiveEpubSurfaceShowsOverlay() {
        val sessionStore = FakeReaderSessionStore()
        val item = sampleEpubItem(id = "epub-edge-bottom")

        composeRule.setContent {
            EpubReaderScreen(
                item = item,
                sessionStore = sessionStore,
                onBack = {},
            )
        }

        waitForReaderReady(sessionStore, item.id)
        hideOverlay()

        tapViewportAtBottomEdge()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(EpubReaderOverlayTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(EpubReaderOverlayTag).assertExists()
    }

    private fun waitForReaderReady(
        sessionStore: FakeReaderSessionStore,
        itemId: String,
    ) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            sessionStore.progressByItemIdFlow.value[itemId]?.cfi != null
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(EpubReaderSurfaceTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(EpubReaderViewportTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(EpubReaderOverlayTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun hideOverlay() {
        composeRule.onNodeWithTag(EpubReaderOverlayTag).assertExists()
        tapViewportAtCenter()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(EpubReaderOverlayTag).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(EpubReaderOverlayTag).assertDoesNotExist()
    }

    private fun tapViewportAtTopEdge() {
        composeRule.onNodeWithTag(EpubReaderViewportTag).performTouchInput {
            click(
                Offset(
                    x = center.x,
                    y = 1f,
                ),
            )
        }
        composeRule.waitForIdle()
    }

    private fun tapViewportAtBottomEdge() {
        composeRule.onNodeWithTag(EpubReaderViewportTag).performTouchInput {
            click(
                Offset(
                    x = center.x,
                    y = center.y * 2f - 1f,
                ),
            )
        }
        composeRule.waitForIdle()
    }

    private fun tapViewportAtCenter() {
        composeRule.onNodeWithTag(EpubReaderViewportTag).performTouchInput {
            click(
                Offset(
                    x = center.x,
                    y = center.y,
                ),
            )
        }
        composeRule.waitForIdle()
    }
}

private fun sampleEpubItem(
    id: String = "epub-edge-top",
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = "epub",
    remoteId = id,
    isLocalImport = false,
    title = "EPUB Edge Test",
    format = FormatType.EPUB,
    description = "Instrumentation smoke test for EPUB edge taps.",
    coverUrl = null,
)

private class FakeReaderSessionStore : ReaderSessionStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    private val progressState = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressState
    private val sessionState = MutableStateFlow<Map<String, ReaderSessionSnapshot>>(emptyMap())
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionState

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        progressState.value = progressState.value + (
            item.id to ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = locator.locatorType,
                page = locator.page,
                cfi = locator.cfi,
                percent = locator.percent,
                isFinished = isFinished,
                syncState = syncState,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        )
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionState.value[item.id]

    override fun updateSession(
        item: ReadableItem,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionState.value[item.id] ?: ReaderSessionSnapshot(itemId = item.id)
        sessionState.value = sessionState.value + (
            item.id to current.copy(
                pageCount = pageCount ?: current.pageCount,
                preferences = current.preferences + preferences,
            )
        )
    }

    override fun clearProgress(itemId: String) {
        progressState.value = progressState.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
        sessionState.value = sessionState.value - itemId
    }
}
