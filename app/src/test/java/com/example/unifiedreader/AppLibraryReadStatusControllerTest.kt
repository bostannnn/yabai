package com.example.unifiedreader

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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLibraryReadStatusControllerTest {
    @Test
    fun `mark as read updates local progress and syncs clean state`() = runTest {
        val store = TestReaderSessionStore()
        val sync = RecordingSourceProgressSync(
            sourceType = SourceType.GRIMMORY,
            onPush = { items, sessionStore ->
                val item = items.single()
                val snapshot = checkNotNull(sessionStore.getProgress(item))
                sessionStore.saveProgress(
                    item = item,
                    locator = snapshot.toReaderLocator(),
                    isFinished = snapshot.isFinished,
                    isExplicitUnreadReset = snapshot.isExplicitUnreadReset,
                    syncState = SyncState.CLEAN,
                    updatedAtEpochMillis = snapshot.updatedAtEpochMillis,
                )
            },
        )
        val controller = AppLibraryReadStatusController(
            readerSessionStore = store,
            sourceProgressSyncs = listOf(sync),
        )
        var refreshCount = 0

        controller.setReadStatus(
            item = grimmoryBook(),
            markAsRead = true,
            onProgressChanged = { refreshCount++ },
        )

        val snapshot = store.progressByItemIdFlow.value.getValue(grimmoryBook().id)
        assertTrue(snapshot.isFinished)
        assertEquals(1.0, snapshot.percent ?: -1.0, 0.0001)
        assertEquals(SyncState.CLEAN, snapshot.syncState)
        assertEquals(listOf(listOf(grimmoryBook().id)), sync.invocations)
        assertEquals(2, refreshCount)
    }

    @Test
    fun `mark as unread clears local progress after successful sync`() = runTest {
        val store = TestReaderSessionStore()
        store.saveProgress(
            item = grimmoryBook(),
            locator = ReaderLocator(
                locatorType = com.example.unifiedreader.core.model.LocatorType.PERCENT,
                percent = 1.0,
            ),
            isFinished = true,
        )
        val sync = RecordingSourceProgressSync(
            sourceType = SourceType.GRIMMORY,
            onPush = { items, sessionStore ->
                sessionStore.clearProgress(items.single().id)
            },
        )
        val controller = AppLibraryReadStatusController(
            readerSessionStore = store,
            sourceProgressSyncs = listOf(sync),
        )

        controller.setReadStatus(
            item = grimmoryBook(),
            markAsRead = false,
            onProgressChanged = {},
        )

        assertNull(store.progressByItemIdFlow.value[grimmoryBook().id])
        assertEquals(listOf(listOf(grimmoryBook().id)), sync.invocations)
    }

    @Test
    fun `failed sync leaves unread reset dirty for retry`() = runTest {
        val store = TestReaderSessionStore()
        val item = grimmoryBook()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = com.example.unifiedreader.core.model.LocatorType.PERCENT,
                percent = 1.0,
            ),
            isFinished = true,
            updatedAtEpochMillis = 77L,
        )
        val controller = AppLibraryReadStatusController(
            readerSessionStore = store,
            sourceProgressSyncs = listOf(
                RecordingSourceProgressSync(
                    sourceType = SourceType.GRIMMORY,
                    failure = IllegalStateException("boom"),
                ),
            ),
        )
        var refreshCount = 0

        controller.setReadStatus(
            item = item,
            markAsRead = false,
            onProgressChanged = { refreshCount++ },
        )

        val snapshot = store.progressByItemIdFlow.value.getValue(item.id)
        assertFalse(snapshot.isFinished)
        assertTrue(snapshot.isExplicitUnreadReset)
        assertEquals(0.0, snapshot.percent ?: -1.0, 0.0001)
        assertEquals(SyncState.DIRTY, snapshot.syncState)
        assertEquals(2, refreshCount)
    }
}

private fun grimmoryBook(): ReadableItem = ReadableItem(
    id = "grimmory:item:7",
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:4",
    remoteId = "7",
    title = "Dune",
    format = FormatType.EPUB,
)

private class RecordingSourceProgressSync(
    override val sourceType: SourceType,
    private val failure: Throwable? = null,
    private val onPush: suspend (List<ReadableItem>, ReaderSessionStore) -> Unit = { _, _ -> },
) : SourceProgressSync {
    val invocations = mutableListOf<List<String>>()

    override suspend fun syncItem(item: ReadableItem) = Unit

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ) {
        invocations += items.map(ReadableItem::id)
        failure?.let { throw it }
        onPush(items, TestReaderSessionStore.activeStore ?: error("No active test store"))
    }
}

private class TestReaderSessionStore : ReaderSessionStore {
    init {
        activeStore = this
    }

    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    private val progressState = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressState.asStateFlow()
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> =
        MutableStateFlow(emptyMap<String, ReaderSessionSnapshot>()).asStateFlow()

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: com.example.unifiedreader.core.model.SyncState,
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
                isExplicitUnreadReset = isExplicitUnreadReset,
                syncState = syncState,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        )
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = null

    override suspend fun replaceProgressIfCurrent(
        expected: ReaderProgressSnapshot,
        replacement: ReaderProgressSnapshot?,
    ): Boolean {
        val current = progressState.value[expected.itemId] ?: return false
        if (current != expected) return false
        progressState.value = if (replacement == null) {
            progressState.value - expected.itemId
        } else {
            progressState.value + (expected.itemId to replacement)
        }
        return true
    }

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) = Unit

    override fun clearProgress(itemId: String) {
        progressState.value = progressState.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
    }

    override fun restoreItem(itemId: String) = Unit

    companion object {
        var activeStore: TestReaderSessionStore? = null
    }
}
