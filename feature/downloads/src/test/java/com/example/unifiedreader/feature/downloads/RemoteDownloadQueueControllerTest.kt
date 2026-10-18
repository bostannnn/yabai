package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteDownloadQueueControllerTest {
    @Test
    fun `enqueue stores queued entry and schedules work`() = runTest {
        val store = InMemoryDownloadQueueStore()
        val scheduler = RecordingDownloadWorkScheduler()
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = scheduler,
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )
        val item = testReadableItem("book-1")

        val result = controller.enqueue(item = item, items = listOf(item))

        assertFalse(result.isError)
        assertEquals("Queued book-1 for download.", result.message)
        assertEquals(1, store.entries.size)
        assertEquals(DownloadEntryStatus.Queued, store.entries.single().status)
        assertEquals(listOf(store.entries.single().id), scheduler.scheduledEntryIds)
    }

    @Test
    fun `enqueueBatch stores selected files in one queued entry`() = runTest {
        val store = InMemoryDownloadQueueStore()
        val scheduler = RecordingDownloadWorkScheduler()
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = scheduler,
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )
        val first = testReadableItem("book-1")
        val second = testReadableItem("book-2")

        val result = controller.enqueueBatch(
            selectedItems = listOf(first, second),
            allItems = listOf(first, second),
        )

        val entry = store.entries.single()
        assertFalse(result.isError)
        assertEquals("Queued 2 files for download.", result.message)
        assertEquals("2 selected items", entry.selectedItemTitle)
        assertEquals(listOf("book-1", "book-2"), entry.actionItems.map(QueuedDownloadItem::id))
        assertEquals(setOf("book-1", "book-2"), entry.trackedItemIds)
        assertEquals(listOf(entry.id), scheduler.scheduledEntryIds)
    }

    @Test
    fun `enqueue rejects overlapping active entries`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Running,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        val result = controller.enqueue(
            item = testReadableItem("book-1"),
            items = listOf(testReadableItem("book-1")),
        )

        assertTrue(result.isError)
        assertEquals(1, store.entries.size)
    }

    @Test
    fun `enqueue rejects overlapping canceling entries`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Canceling,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        val result = controller.enqueue(
            item = testReadableItem("book-1"),
            items = listOf(testReadableItem("book-1")),
        )

        assertTrue(result.isError)
        assertEquals(1, store.entries.size)
    }

    @Test
    fun `enqueue uses atomic overlap guard in store`() = runTest {
        val store = InMemoryDownloadQueueStore()
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        val first = controller.enqueue(
            item = testReadableItem("book-1"),
            items = listOf(testReadableItem("book-1")),
        )
        val second = controller.enqueue(
            item = testReadableItem("book-1"),
            items = listOf(testReadableItem("book-1")),
        )

        assertFalse(first.isError)
        assertTrue(second.isError)
        assertEquals(1, store.entries.size)
    }

    @Test
    fun `cancel marks running entry as canceling until worker stops`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Running,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val scheduler = RecordingDownloadWorkScheduler()
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = scheduler,
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        controller.cancel("entry-1")

        assertEquals(DownloadEntryStatus.Canceling, store.entries.single().status)
        assertEquals(listOf("entry-1"), scheduler.canceledEntryIds)
    }

    @Test
    fun `remove deletes failed entry`() = runTest {
        val dataSource = TestRemoteItemDownloadDataSource(
            storedItemIds = mutableSetOf("book-1"),
        )
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = setOf("book-1"),
                    status = DownloadEntryStatus.Failed,
                    errorMessage = "Disk full",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(dataSource),
        )

        val removed = controller.remove("entry-1")

        assertTrue(removed)
        assertTrue(store.entries.isEmpty())
        assertNull(dataSource.storedDocumentUri(testReadableItem("book-1")))
    }

    @Test
    fun `remove keeps failed entry when stored file cannot be deleted`() = runTest {
        val dataSource = TestRemoteItemDownloadDataSource(
            storedItemIds = mutableSetOf("book-1", "book-2"),
            undeletableItemIds = setOf("book-2"),
        )
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1"), testQueuedItem("book-2")),
                    trackedItemIds = setOf("book-1", "book-2"),
                    completedItemIds = setOf("book-1", "book-2"),
                    status = DownloadEntryStatus.Failed,
                    errorMessage = "Disk full",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(dataSource),
        )

        val removed = controller.remove("entry-1")
        val remainingEntry = checkNotNull(store.entries.singleOrNull())

        assertFalse(removed)
        assertEquals(listOf("entry-1"), store.entries.map(DownloadEntry::id))
        assertEquals(setOf("book-2"), remainingEntry.completedItemIds)
        assertNull(dataSource.storedDocumentUri(testReadableItem("book-1")))
        assertEquals("file://book-2", dataSource.storedDocumentUri(testReadableItem("book-2")).toString())
    }

    @Test
    fun `retry after partial cleanup re-downloads removed files`() = runTest {
        val dataSource = TestRemoteItemDownloadDataSource(
            storedItemIds = mutableSetOf("book-1", "book-2"),
            undeletableItemIds = setOf("book-2"),
        )
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1"), testQueuedItem("book-2")),
                    trackedItemIds = setOf("book-1", "book-2"),
                    completedItemIds = setOf("book-1", "book-2"),
                    status = DownloadEntryStatus.Failed,
                    errorMessage = "Disk full",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(dataSource),
        )
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(dataSource),
            completionSideEffects = emptyList(),
        )

        assertFalse(controller.remove("entry-1"))

        controller.retry("entry-1")
        val outcome = runner.runEntry("entry-1")
        val entry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Success, outcome)
        assertEquals(DownloadEntryStatus.Completed, entry.status)
        assertEquals(setOf("book-1", "book-2"), entry.completedItemIds)
        assertEquals(listOf("book-1"), dataSource.downloadedItemIds)
    }

    @Test
    fun `retry does not requeue overlapping entry while another active entry exists`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "failed-entry",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Failed,
                    errorMessage = "boom",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
                DownloadEntry(
                    id = "running-entry",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Running,
                    createdAtEpochMillis = 2L,
                    updatedAtEpochMillis = 2L,
                ),
            ),
        )
        val scheduler = RecordingDownloadWorkScheduler()
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = scheduler,
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        controller.retry("failed-entry")

        val failedEntry = checkNotNull(store.entries.firstOrNull { it.id == "failed-entry" })
        assertEquals(DownloadEntryStatus.Failed, failedEntry.status)
        assertTrue(scheduler.scheduledEntryIds.isEmpty())
    }

    @Test
    fun `retry does not requeue overlapping entry while another canceling entry exists`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "failed-entry",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Failed,
                    errorMessage = "boom",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
                DownloadEntry(
                    id = "canceling-entry",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Canceling,
                    createdAtEpochMillis = 2L,
                    updatedAtEpochMillis = 2L,
                ),
            ),
        )
        val scheduler = RecordingDownloadWorkScheduler()
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = scheduler,
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        controller.retry("failed-entry")

        val failedEntry = checkNotNull(store.entries.firstOrNull { it.id == "failed-entry" })
        assertEquals(DownloadEntryStatus.Failed, failedEntry.status)
        assertTrue(scheduler.scheduledEntryIds.isEmpty())
    }

    @Test
    fun `remove ignores active entry`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Running,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        val removed = controller.remove("entry-1")

        assertFalse(removed)
        assertEquals(listOf("entry-1"), store.entries.map(DownloadEntry::id))
    }

    @Test
    fun `resolve open item returns completed stored item`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "series-1",
                    selectedItemTitle = "Series",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(
                        testQueuedItem("chapter-1"),
                        testQueuedItem("chapter-2"),
                    ),
                    trackedItemIds = setOf("series-1", "chapter-1", "chapter-2"),
                    completedItemIds = setOf("chapter-1", "chapter-2"),
                    status = DownloadEntryStatus.Completed,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val dataSource = TestRemoteItemDownloadDataSource(
            storedItemIds = mutableSetOf("chapter-1", "chapter-2"),
        )
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(dataSource),
        )

        val item = controller.resolveOpenItem("entry-1")

        assertEquals("chapter-1", item?.id)
    }

    @Test
    fun `ensure scheduled finalizes canceling entries and only schedules queued work`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "canceling-entry",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Canceling,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
                DownloadEntry(
                    id = "queued-entry",
                    selectedItemId = "book-2",
                    selectedItemTitle = "book-2",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-2")),
                    trackedItemIds = setOf("book-2"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Queued,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val scheduler = RecordingDownloadWorkScheduler()
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = scheduler,
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        val scheduledCount = controller.ensureScheduled()

        assertEquals(1, scheduledCount)
        assertEquals(listOf("queued-entry"), scheduler.scheduledEntryIds)
        assertEquals(
            DownloadEntryStatus.Canceled,
            store.entries.first { entry -> entry.id == "canceling-entry" }.status,
        )
    }

    @Test
    fun `ensure scheduled pauses queued and running entries while source cannot be scheduled`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "queued-entry",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Queued,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
                DownloadEntry(
                    id = "running-entry",
                    selectedItemId = "book-2",
                    selectedItemTitle = "book-2",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-2")),
                    trackedItemIds = setOf("book-2"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Running,
                    currentItemId = "book-2",
                    currentItemTitle = "book-2",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val scheduler = RecordingDownloadWorkScheduler()
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = scheduler,
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        val scheduledCount = controller.ensureScheduled(
            canScheduleSource = { false },
        )

        assertEquals(0, scheduledCount)
        assertTrue(scheduler.scheduledEntryIds.isEmpty())
        assertEquals(
            DownloadEntryStatus.PausedConnectivity,
            store.entries.first { entry -> entry.id == "queued-entry" }.status,
        )
        val runningEntry = store.entries.first { entry -> entry.id == "running-entry" }
        assertEquals(DownloadEntryStatus.PausedConnectivity, runningEntry.status)
        assertEquals("book-2", runningEntry.currentItemId)
    }
}

@RunWith(RobolectricTestRunner::class)
class RemoteDownloadWorkerRunnerTest {
    @Test
    fun `run entry marks download completed and triggers side effects`() = runTest {
        val item = testReadableItem("book-1")
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = item.id,
                    selectedItemTitle = item.title,
                    sourceType = item.sourceType,
                    actionItems = listOf(testQueuedItem(item.id)),
                    trackedItemIds = setOf(item.id),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Queued,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val dataSource = TestRemoteItemDownloadDataSource()
        val sideEffect = RecordingDownloadCompletionSideEffect()
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(dataSource),
            completionSideEffects = listOf(sideEffect),
        )

        val outcome = runner.runEntry("entry-1")
        val entry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Success, outcome)
        assertEquals(DownloadEntryStatus.Completed, entry.status)
        assertEquals(setOf(item.id), entry.completedItemIds)
        assertEquals(listOf(item.id), dataSource.downloadedItemIds)
        assertEquals(listOf(item.id), sideEffect.completedItemIds)
    }

    @Test
    fun `run entry pauses running download when source cannot run`() = runTest {
        val item = testReadableItem("book-1")
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = item.id,
                    selectedItemTitle = item.title,
                    sourceType = item.sourceType,
                    actionItems = listOf(testQueuedItem(item.id)),
                    trackedItemIds = setOf(item.id),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Running,
                    currentItemId = item.id,
                    currentItemTitle = item.title,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val dataSource = TestRemoteItemDownloadDataSource()
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(dataSource),
            completionSideEffects = emptyList(),
            canRunSource = { false },
        )

        val outcome = runner.runEntry("entry-1")
        val entry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Success, outcome)
        assertEquals(DownloadEntryStatus.PausedConnectivity, entry.status)
        assertEquals(item.id, entry.currentItemId)
        assertTrue(dataSource.downloadedItemIds.isEmpty())
    }

    @Test
    fun `run entry stays completed when side effect fails after local download`() = runTest {
        val item = testReadableItem("book-1")
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = item.id,
                    selectedItemTitle = item.title,
                    sourceType = item.sourceType,
                    actionItems = listOf(testQueuedItem(item.id)),
                    trackedItemIds = setOf(item.id),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Queued,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val dataSource = TestRemoteItemDownloadDataSource()
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(dataSource),
            completionSideEffects = listOf(FailingDownloadCompletionSideEffect()),
        )

        val outcome = runner.runEntry("entry-1")
        val entry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Success, outcome)
        assertEquals(DownloadEntryStatus.Completed, entry.status)
        assertEquals(setOf(item.id), entry.completedItemIds)
        assertEquals(listOf(item.id), dataSource.downloadedItemIds)
    }

    @Test
    fun `run entry queues the download again when stopped during a side effect`() = runTest {
        val item = testReadableItem("book-1")
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = item.id,
                    selectedItemTitle = item.title,
                    sourceType = item.sourceType,
                    actionItems = listOf(testQueuedItem(item.id)),
                    trackedItemIds = setOf(item.id),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Queued,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
            completionSideEffects = listOf(CancelingDownloadCompletionSideEffect()),
        )

        val outcome = runner.runEntry("entry-1")
        val entry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Stopped, outcome)
        assertEquals(DownloadEntryStatus.Queued, entry.status)
        assertEquals(setOf(item.id), entry.completedItemIds)
    }

    @Test
    fun `run entry queues a download stopped by the system and the rerun resumes it`() = runTest {
        var stopDuringItemId: String? = "book-2"
        val dataSource = TestRemoteItemDownloadDataSource(
            onDownload = { item ->
                if (item.id == stopDuringItemId) {
                    stopDuringItemId = null
                    // WorkManager stopping the work (network lost, time limit, Doze) cancels it.
                    throw kotlinx.coroutines.CancellationException("work stopped")
                }
            },
        )
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(threeBookEntry(status = DownloadEntryStatus.Queued)),
        )
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(dataSource),
            completionSideEffects = emptyList(),
        )

        val stopped = runner.runEntry("entry-1")
        val stoppedEntry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Stopped, stopped)
        assertEquals(DownloadEntryStatus.Queued, stoppedEntry.status)
        assertEquals(setOf("book-1"), stoppedEntry.completedItemIds)

        val rerun = runner.runEntry("entry-1")
        val finishedEntry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Success, rerun)
        assertEquals(DownloadEntryStatus.Completed, finishedEntry.status)
        assertEquals(setOf("book-1", "book-2", "book-3"), finishedEntry.completedItemIds)
        assertEquals(listOf("book-1", "book-2", "book-2", "book-3"), dataSource.downloadedItemIds)
    }

    @Test
    fun `run entry records a user cancel that stops the work as canceled`() = runTest {
        lateinit var controller: RemoteDownloadQueueController
        val scheduler = RecordingDownloadWorkScheduler()
        val dataSource = TestRemoteItemDownloadDataSource(
            onDownload = { item ->
                if (item.id == "book-2") {
                    // The user's cancel marks the entry Canceling, then WorkManager cancels the work.
                    controller.cancel("entry-1")
                    throw kotlinx.coroutines.CancellationException("work canceled")
                }
            },
        )
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(threeBookEntry(status = DownloadEntryStatus.Queued)),
        )
        controller = RemoteDownloadQueueController(
            store = store,
            scheduler = scheduler,
            remoteDownloadDataSources = listOf(dataSource),
        )
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(dataSource),
            completionSideEffects = emptyList(),
        )

        val outcome = runner.runEntry("entry-1")
        val entry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Canceled, outcome)
        assertEquals(DownloadEntryStatus.Canceled, entry.status)
        assertEquals(setOf("book-1"), entry.completedItemIds)
        assertEquals(RemoteDownloadWorkerOutcome.Success, runner.runEntry("entry-1"))
        assertEquals(DownloadEntryStatus.Canceled, store.entries.single().status)
    }

    @Test
    fun `run entry honors cancel persisted after load and before running`() = runTest {
        val item = testReadableItem("book-1")
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = item.id,
                    selectedItemTitle = item.title,
                    sourceType = item.sourceType,
                    actionItems = listOf(testQueuedItem(item.id)),
                    trackedItemIds = setOf(item.id),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Queued,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
            onGetEntry = { entryId, callCount, storeRef ->
                if (entryId == "entry-1" && callCount == 1) {
                    storeRef.updateStatus(
                        entryId = entryId,
                        status = DownloadEntryStatus.Canceled,
                        completedAtEpochMillis = 2L,
                    )
                }
            },
        )
        val dataSource = TestRemoteItemDownloadDataSource()
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(dataSource),
            completionSideEffects = emptyList(),
        )

        val outcome = runner.runEntry("entry-1")
        val entry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Canceled, outcome)
        assertEquals(DownloadEntryStatus.Canceled, entry.status)
        assertTrue(dataSource.downloadedItemIds.isEmpty())
    }

    @Test
    fun `run entry does not overwrite canceled status with completed`() = runTest {
        val item = testReadableItem("book-1")
        lateinit var store: InMemoryDownloadQueueStore
        store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = item.id,
                    selectedItemTitle = item.title,
                    sourceType = item.sourceType,
                    actionItems = listOf(testQueuedItem(item.id)),
                    trackedItemIds = setOf(item.id),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Queued,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val dataSource = TestRemoteItemDownloadDataSource(
            onDownload = { downloadedItem ->
                if (downloadedItem.id == item.id) {
                    store.updateStatus(
                        entryId = "entry-1",
                        status = DownloadEntryStatus.Canceled,
                        completedAtEpochMillis = 2L,
                    )
                }
            },
        )
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(dataSource),
            completionSideEffects = emptyList(),
        )

        val outcome = runner.runEntry("entry-1")
        val entry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Canceled, outcome)
        assertEquals(DownloadEntryStatus.Canceled, entry.status)
        assertEquals(setOf(item.id), entry.completedItemIds)
    }

    @Test
    fun `run entry finalizes canceling entry as canceled`() = runTest {
        val item = testReadableItem("book-1")
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = item.id,
                    selectedItemTitle = item.title,
                    sourceType = item.sourceType,
                    actionItems = listOf(testQueuedItem(item.id)),
                    trackedItemIds = setOf(item.id),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Canceling,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
            completionSideEffects = emptyList(),
        )

        val outcome = runner.runEntry("entry-1")
        val entry = checkNotNull(store.entries.singleOrNull())

        assertEquals(RemoteDownloadWorkerOutcome.Canceled, outcome)
        assertEquals(DownloadEntryStatus.Canceled, entry.status)
    }

    @Test
    fun `clear completed keeps canceling entries until cancellation is finalized`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "canceling-entry",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Canceling,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
                DownloadEntry(
                    id = "completed-entry",
                    selectedItemId = "book-2",
                    selectedItemTitle = "book-2",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-2")),
                    trackedItemIds = setOf("book-2"),
                    completedItemIds = setOf("book-2"),
                    status = DownloadEntryStatus.Completed,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                    completedAtEpochMillis = 2L,
                ),
            ),
        )

        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        val removedCount = controller.clearCompleted()

        assertEquals(1, removedCount)
        assertEquals(listOf("canceling-entry"), store.entries.map(DownloadEntry::id))
        assertEquals(DownloadEntryStatus.Canceling, store.entries.single().status)
    }

    @Test
    fun `clear completed keeps the files of a partially completed canceled entry`() = runTest {
        val dataSource = TestRemoteItemDownloadDataSource(
            storedItemIds = mutableSetOf("book-1"),
        )
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("book-1"), testQueuedItem("book-2")),
                    trackedItemIds = setOf("book-1", "book-2"),
                    completedItemIds = setOf("book-1"),
                    status = DownloadEntryStatus.Canceled,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L,
                    completedAtEpochMillis = 2L,
                ),
            ),
        )
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(dataSource),
        )

        val removedCount = controller.clearCompleted()

        assertEquals(1, removedCount)
        assertTrue(store.entries.isEmpty())
        assertTrue(dataSource.storedDocumentUri(testReadableItem("book-1")) != null)
    }

    @Test
    fun `resolve open item returns null when completed files are missing locally`() = runTest {
        val store = InMemoryDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "entry-1",
                    selectedItemId = "series-1",
                    selectedItemTitle = "Series",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedItem("chapter-1")),
                    trackedItemIds = setOf("series-1", "chapter-1"),
                    completedItemIds = setOf("chapter-1"),
                    status = DownloadEntryStatus.Completed,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = RecordingDownloadWorkScheduler(),
            remoteDownloadDataSources = listOf(TestRemoteItemDownloadDataSource()),
        )

        val item = controller.resolveOpenItem("entry-1")

        assertNull(item)
    }
}

private class InMemoryDownloadQueueStore(
    initialEntries: List<DownloadEntry> = emptyList(),
    private val onGetEntry: (suspend (entryId: String, callCount: Int, store: InMemoryDownloadQueueStore) -> Unit)? = null,
) : DownloadQueueStore {
    private val state = MutableStateFlow(initialEntries)
    private val enqueueMutex = Mutex()
    private var getEntryCallCount: Int = 0

    val entries: List<DownloadEntry>
        get() = state.value

    override fun observeEntries(): Flow<DownloadEntriesSnapshot> = MutableStateFlow(
        DownloadEntriesSnapshot(state.value),
    )

    override suspend fun getEntries(): List<DownloadEntry> = state.value

    override suspend fun getEntry(entryId: String): DownloadEntry? {
        val entry = state.value.firstOrNull { it.id == entryId }
        getEntryCallCount += 1
        onGetEntry?.invoke(entryId, getEntryCallCount, this)
        return entry
    }

    override suspend fun putEntry(entry: DownloadEntry) {
        state.value = state.value.filterNot { it.id == entry.id } + entry
    }

    override suspend fun putEntryIfNoActiveTrackedItems(
        entry: DownloadEntry,
    ): Boolean = enqueueMutex.withLock {
        val hasOverlap = state.value.any { existingEntry ->
            (existingEntry.status == DownloadEntryStatus.Queued ||
                existingEntry.status == DownloadEntryStatus.PausedConnectivity ||
                existingEntry.status == DownloadEntryStatus.Running ||
                existingEntry.status == DownloadEntryStatus.Canceling) &&
                existingEntry.trackedItemIds.any(entry.trackedItemIds::contains)
        }
        if (hasOverlap) {
            false
        } else {
            state.value = state.value.filterNot { existing -> existing.id == entry.id } + entry
            true
        }
    }

    override suspend fun retryEntryIfNoOtherActiveTrackedItems(entryId: String): Boolean = enqueueMutex.withLock {
        val entry = state.value.firstOrNull { it.id == entryId } ?: return@withLock false
        val hasOverlap = state.value.any { existingEntry ->
            existingEntry.id != entryId &&
                (existingEntry.status == DownloadEntryStatus.Queued ||
                    existingEntry.status == DownloadEntryStatus.PausedConnectivity ||
                    existingEntry.status == DownloadEntryStatus.Running ||
                    existingEntry.status == DownloadEntryStatus.Canceling) &&
                existingEntry.trackedItemIds.any(entry.trackedItemIds::contains)
        }
        if (hasOverlap) {
            false
        } else {
            state.value = state.value.map { existing ->
                if (existing.id != entryId) {
                    existing
                } else {
                    existing.copy(
                        status = DownloadEntryStatus.Queued,
                        errorMessage = null,
                        currentItemId = null,
                        currentItemTitle = null,
                        completedAtEpochMillis = null,
                    )
                }
            }
            true
        }
    }

    override suspend fun updateStatus(
        entryId: String,
        status: DownloadEntryStatus,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        completedAtEpochMillis: Long?,
    ) {
        state.value = state.value.map { entry ->
            if (entry.id != entryId) return@map entry
            entry.copy(
                status = status,
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
                errorMessage = errorMessage,
                completedAtEpochMillis = completedAtEpochMillis,
            )
        }
    }

    override suspend fun updateStatusIfCurrentStatusIn(
        entryId: String,
        expectedStatuses: Set<DownloadEntryStatus>,
        status: DownloadEntryStatus,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        completedAtEpochMillis: Long?,
    ): Boolean {
        var updated = false
        state.value = state.value.map { entry ->
            if (entry.id != entryId || entry.status !in expectedStatuses) return@map entry
            updated = true
            entry.copy(
                status = status,
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
                errorMessage = errorMessage,
                completedAtEpochMillis = completedAtEpochMillis,
            )
        }
        return updated
    }

    override suspend fun updateProgress(
        entryId: String,
        completedItemIds: Set<String>,
        currentItemId: String?,
        currentItemTitle: String?,
    ) {
        state.value = state.value.map { entry ->
            if (entry.id != entryId) return@map entry
            entry.copy(
                completedItemIds = completedItemIds,
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
            )
        }
    }

    override suspend fun deleteEntry(entryId: String): Int {
        val previousSize = state.value.size
        state.value = state.value.filterNot { entry -> entry.id == entryId }
        return previousSize - state.value.size
    }
}

private class RecordingDownloadWorkScheduler : DownloadWorkScheduler {
    val scheduledEntryIds = mutableListOf<String>()
    val canceledEntryIds = mutableListOf<String>()

    override fun scheduleEntry(entryId: String) {
        scheduledEntryIds += entryId
    }

    override fun cancelEntry(entryId: String) {
        canceledEntryIds += entryId
    }
}

private class TestRemoteItemDownloadDataSource : RemoteItemDownloadDataSource {
    override val sourceType: SourceType = SourceType.KAVITA
    val downloadedItemIds = mutableListOf<String>()
    private val storedItemIds: MutableSet<String>
    private val undeletableItemIds: Set<String>
    private val onDownload: suspend (ReadableItem) -> Unit

    constructor(
        storedItemIds: MutableSet<String> = mutableSetOf(),
        undeletableItemIds: Set<String> = emptySet(),
        onDownload: suspend (ReadableItem) -> Unit = {},
    ) {
        this.storedItemIds = storedItemIds
        this.undeletableItemIds = undeletableItemIds
        this.onDownload = onDownload
    }

    override fun storedDocumentUri(item: ReadableItem) = item.id.takeIf(storedItemIds::contains)?.let {
        android.net.Uri.parse("file://$it")
    }

    override suspend fun downloadToLocalStorage(item: ReadableItem) = android.net.Uri.parse("file://${item.id}").also {
        downloadedItemIds += item.id
        storedItemIds += item.id
        onDownload(item)
    }

    override suspend fun storedDocumentItemIds(items: List<ReadableItem>): Set<String> = emptySet()

    override suspend fun deleteStoredDocument(item: ReadableItem): Boolean {
        if (item.id in undeletableItemIds) return false
        return storedItemIds.remove(item.id)
    }
}

private class RecordingDownloadCompletionSideEffect : DownloadCompletionSideEffect {
    val completedItemIds = mutableListOf<String>()

    override fun supports(item: ReadableItem): Boolean = true

    override suspend fun onDownloadCompleted(item: ReadableItem) {
        completedItemIds += item.id
    }
}

private class FailingDownloadCompletionSideEffect : DownloadCompletionSideEffect {
    override fun supports(item: ReadableItem): Boolean = true

    override suspend fun onDownloadCompleted(item: ReadableItem) {
        error("sync failed")
    }
}

private class CancelingDownloadCompletionSideEffect : DownloadCompletionSideEffect {
    override fun supports(item: ReadableItem): Boolean = true

    override suspend fun onDownloadCompleted(item: ReadableItem) {
        throw kotlinx.coroutines.CancellationException("cancelled during side effect")
    }
}

private fun threeBookEntry(
    status: DownloadEntryStatus,
): DownloadEntry = DownloadEntry(
    id = "entry-1",
    selectedItemId = "book-1",
    selectedItemTitle = "3 selected items",
    sourceType = SourceType.KAVITA,
    actionItems = listOf(testQueuedItem("book-1"), testQueuedItem("book-2"), testQueuedItem("book-3")),
    trackedItemIds = setOf("book-1", "book-2", "book-3"),
    completedItemIds = emptySet(),
    status = status,
    createdAtEpochMillis = 1L,
    updatedAtEpochMillis = 1L,
)

private fun testReadableItem(
    id: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = "library",
    remoteId = id,
    title = id,
    format = FormatType.CBZ,
)

private fun testQueuedItem(
    id: String,
): QueuedDownloadItem = QueuedDownloadItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = "library",
    remoteId = id,
    remoteFileId = null,
    remoteFileType = null,
    title = id,
    formatName = FormatType.CBZ.name,
    pageCount = null,
)
