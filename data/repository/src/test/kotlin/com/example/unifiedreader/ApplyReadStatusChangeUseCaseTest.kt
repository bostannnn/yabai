package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.progress.ProgressSyncLocator
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyReadStatusChangeUseCaseTest {
    @Test
    fun `mark as read updates local progress and syncs clean state`() = runTest {
        val store = TestProgressSyncStore()
        val sync = RecordingSourceProgressSync(
            sourceType = SourceType.GRIMMORY,
            onPush = { items, sessionStore ->
                val item = items.single()
                val snapshot = checkNotNull(sessionStore.getProgress(item))
                sessionStore.saveProgress(
                    item = item,
                    locator = ProgressSyncLocator(
                        locatorType = snapshot.locatorType,
                        page = snapshot.page,
                        cfi = snapshot.cfi,
                        percent = snapshot.percent,
                    ),
                    isFinished = snapshot.isFinished,
                    isExplicitUnreadReset = snapshot.isExplicitUnreadReset,
                    syncState = SyncState.CLEAN,
                    updatedAtEpochMillis = snapshot.updatedAtEpochMillis,
                )
            },
        )
        val useCase = ApplyReadStatusChangeUseCase(
            progressSyncStore = store,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                SourceProgressSyncRegistry(listOf(sync)),
            ),
        )

        val result = useCase(
            item = grimmoryBook(),
            markAsRead = true,
        )

        val snapshot = store.progressByItemIdFlow.value.getValue(grimmoryBook().id)
        assertTrue(result.applied)
        assertTrue(result.syncFailures.isEmpty())
        assertTrue(snapshot.isFinished)
        assertEquals(1.0, snapshot.percent ?: -1.0, 0.0001)
        assertEquals(SyncState.CLEAN, snapshot.syncState)
        assertEquals(listOf(listOf(grimmoryBook().id)), sync.invocations)
    }

    @Test
    fun `mark as unread clears local progress after successful sync`() = runTest {
        val store = TestProgressSyncStore()
        store.saveProgress(
            item = grimmoryBook(),
            locator = ProgressSyncLocator(
                locatorType = com.example.unifiedreader.core.model.LocatorType.PERCENT,
                percent = 1.0,
            ),
            isFinished = true,
        )
        val useCase = ApplyReadStatusChangeUseCase(
            progressSyncStore = store,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                SourceProgressSyncRegistry(
                    listOf(
                        RecordingSourceProgressSync(
                            sourceType = SourceType.GRIMMORY,
                            onPush = { items, sessionStore ->
                                sessionStore.clearProgress(items.single().id)
                            },
                        ),
                    ),
                ),
            ),
        )

        val result = useCase(
            item = grimmoryBook(),
            markAsRead = false,
        )

        assertTrue(result.applied)
        assertTrue(result.syncFailures.isEmpty())
        assertNull(store.progressByItemIdFlow.value[grimmoryBook().id])
    }

    @Test
    fun `failed sync leaves unread reset dirty for retry`() = runTest {
        val store = TestProgressSyncStore()
        val item = grimmoryBook()
        store.saveProgress(
            item = item,
            locator = ProgressSyncLocator(
                locatorType = com.example.unifiedreader.core.model.LocatorType.PERCENT,
                percent = 1.0,
            ),
            isFinished = true,
            updatedAtEpochMillis = 77L,
        )
        val useCase = ApplyReadStatusChangeUseCase(
            progressSyncStore = store,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                SourceProgressSyncRegistry(
                    listOf(
                        RecordingSourceProgressSync(
                            sourceType = SourceType.GRIMMORY,
                            failure = IllegalStateException("boom"),
                        ),
                    ),
                ),
            ),
        )

        val result = useCase(
            item = item,
            markAsRead = false,
        )

        val snapshot = store.progressByItemIdFlow.value.getValue(item.id)
        assertTrue(result.applied)
        assertEquals(1, result.syncFailures.size)
        assertFalse(snapshot.isFinished)
        assertTrue(snapshot.isExplicitUnreadReset)
        assertEquals(0.0, snapshot.percent ?: -1.0, 0.0001)
        assertEquals(SyncState.DIRTY, snapshot.syncState)
    }

    @Test
    fun `startup retry flushes persisted dirty and failed progress`() = runTest {
        val store = TestProgressSyncStore()
        val dirtyItem = grimmoryBook(id = "grimmory:item:dirty")
        val failedItem = grimmoryBook(id = "grimmory:item:failed")
        val cleanItem = grimmoryBook(id = "grimmory:item:clean")
        listOf(
            dirtyItem to SyncState.DIRTY,
            failedItem to SyncState.FAILED,
            cleanItem to SyncState.CLEAN,
        ).forEach { (item, syncState) ->
            store.saveProgress(
                item = item,
                locator = ProgressSyncLocator(
                    locatorType = com.example.unifiedreader.core.model.LocatorType.PERCENT,
                    percent = 0.5,
                ),
                syncState = syncState,
            )
        }
        val sync = RecordingSourceProgressSync(sourceType = SourceType.GRIMMORY)
        val useCase = FlushPendingProgressOnStartupUseCase(
            progressSyncStore = store,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                SourceProgressSyncRegistry(listOf(sync)),
            ),
        )

        val failures = useCase(listOf(dirtyItem, failedItem, cleanItem))

        assertTrue(failures.isEmpty())
        assertEquals(
            listOf(listOf(dirtyItem.id, failedItem.id)),
            sync.invocations,
        )
    }

    @Test
    fun `startup retry is a no-op when no pending catalog item can be resolved`() = runTest {
        val store = TestProgressSyncStore()
        val pendingItem = grimmoryBook(id = "grimmory:item:missing")
        store.saveProgress(
            item = pendingItem,
            locator = ProgressSyncLocator(
                locatorType = com.example.unifiedreader.core.model.LocatorType.PERCENT,
                percent = 0.5,
            ),
            syncState = SyncState.DIRTY,
        )
        val sync = RecordingSourceProgressSync(sourceType = SourceType.GRIMMORY)
        val useCase = FlushPendingProgressOnStartupUseCase(
            progressSyncStore = store,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                SourceProgressSyncRegistry(listOf(sync)),
            ),
        )

        val failures = useCase(emptyList())

        assertTrue(failures.isEmpty())
        assertTrue(sync.invocations.isEmpty())
    }
}

private fun grimmoryBook(
    id: String = "grimmory:item:7",
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:4",
    remoteId = "7",
    title = "Dune",
    format = FormatType.EPUB,
)

private class RecordingSourceProgressSync(
    override val sourceType: SourceType,
    private val failure: Throwable? = null,
    private val onPush: suspend (List<ReadableItem>, ProgressSyncStore) -> Unit = { _, _ -> },
) : SourceProgressSync {
    val invocations = mutableListOf<List<String>>()

    override suspend fun syncItem(item: ReadableItem) = Unit

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        invocations += items.map(ReadableItem::id)
        failure?.let {
            return listOf(
                SourceProgressSyncFailure(
                    sourceType = sourceType,
                    throwable = it,
                ),
            )
        }
        onPush(items, TestProgressSyncStore.activeStore ?: error("No active test store"))
        return emptyList()
    }
}

private class TestProgressSyncStore : ProgressSyncStore {
    init {
        activeStore = this
    }

    override val isHydratedFlow = MutableStateFlow(true).asStateFlow()
    private val progressState = MutableStateFlow<Map<String, ProgressSyncSnapshot>>(emptyMap())
    override val progressByItemIdFlow = progressState.asStateFlow()

    override fun currentProgressByItemId(): Map<String, ProgressSyncSnapshot> = progressState.value

    override fun getProgress(item: ReadableItem): ProgressSyncSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ProgressSyncLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: com.example.unifiedreader.core.model.SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        progressState.value = progressState.value + (
            item.id to ProgressSyncSnapshot(
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
        expected: ProgressSyncSnapshot,
        replacement: ProgressSyncSnapshot?,
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

    override fun clearProgress(itemId: String) {
        progressState.value = progressState.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
    }

    companion object {
        var activeStore: TestProgressSyncStore? = null
    }
}
