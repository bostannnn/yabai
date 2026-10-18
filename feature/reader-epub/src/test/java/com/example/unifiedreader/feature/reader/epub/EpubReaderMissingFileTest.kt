package com.example.unifiedreader.feature.reader.epub

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EpubReaderMissingFileTest {
    @Test
    fun `opening a book without its file and closing it leaves the stored progress alone`() {
        val item = ReadableItem(
            id = "grimmory:item:7",
            sourceType = SourceType.GRIMMORY,
            libraryId = "grimmory:library:2",
            remoteId = "7",
            title = "Remote Book",
            format = FormatType.EPUB,
        )
        val storedProgress = ReaderProgressSnapshot(
            itemId = item.id,
            locatorType = LocatorType.CFI,
            cfi = "{\"href\":\"chapter-3.xhtml\",\"type\":\"application/xhtml+xml\",\"locations\":{\"position\":40}}",
            page = 40,
            percent = 0.4,
            syncState = SyncState.CLEAN,
            updatedAtEpochMillis = 100L,
        )
        val store = RecordingReaderSessionStore(progress = mapOf(item.id to storedProgress))
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()

        // A null URI is what the app hands over for a remote book whose source is offline or
        // signed out, or whose cached file was evicted.
        controller.get().setContent {
            EpubReaderScreen(
                item = item,
                sessionStore = store,
                documentUri = null,
                onBack = {},
            )
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, store.saveProgressCalls)
        assertEquals(storedProgress, store.progressByItemIdFlow.value[item.id])
    }
}

private class RecordingReaderSessionStore(
    progress: Map<String, ReaderProgressSnapshot>,
) : ReaderSessionStore {
    var saveProgressCalls = 0
        private set

    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    private val progressState = MutableStateFlow(progress)
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressState
    private val sessionState = MutableStateFlow<Map<String, ReaderSessionSnapshot>>(emptyMap())
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionState

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        saveProgressCalls += 1
        progressState.value = progressState.value + (
            item.id to ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = locator.locatorType,
                page = locator.page,
                cfi = locator.cfi,
                percent = locator.percent,
                isFinished = isFinished,
                isExplicitUnreadReset = isExplicitUnreadReset,
                syncState = syncState,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        )
    }

    override suspend fun replaceProgressIfCurrent(
        expected: ReaderProgressSnapshot,
        replacement: ReaderProgressSnapshot?,
    ): Boolean = false

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionState.value[item.id]

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionState.value[itemId] ?: ReaderSessionSnapshot(itemId = itemId)
        sessionState.value = sessionState.value + (
            itemId to current.copy(
                pageCount = pageCount ?: current.pageCount,
                preferences = current.preferences + preferences,
            )
        )
    }

    override fun clearProgress(itemId: String) {
        progressState.value = progressState.value - itemId
    }

    override fun clearItem(itemId: String) = clearProgress(itemId)

    override fun restoreItem(itemId: String) = Unit
}
