package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.core.model.progress.ProgressSyncLocator
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCatalogSyncSupportTest {
    @Test
    fun `finalize remote catalog sync clears stale items applies remote progress and pushes dirty progress`() = runTest {
        val incomingItem = remoteItem(id = "issue-1")
        val staleItem = remoteItem(id = "issue-stale")
        val remoteCatalogWriter = SupportTestRemoteCatalogWriter(existingItems = listOf(staleItem))
        val progressSyncStore = RecordingProgressSyncStore().apply {
            saveProgress(
                item = staleItem,
                locator = ProgressSyncLocator(locatorType = LocatorType.PAGE, page = 2),
                syncState = SyncState.CLEAN,
                pageCount = staleItem.pageCount,
                updatedAtEpochMillis = 50L,
            )
            saveProgress(
                item = incomingItem,
                locator = ProgressSyncLocator(locatorType = LocatorType.PERCENT, percent = 0.2),
                syncState = SyncState.CLEAN,
                pageCount = incomingItem.pageCount,
                updatedAtEpochMillis = 100L,
            )
        }
        val progressMessages = mutableListOf<String>()
        var pushedItems: List<ReadableItem> = emptyList()

        val outcome = finalizeRemoteCatalogSync(
            sourceType = SourceType.KAVITA,
            fetch = RemoteCatalogFetch(
                libraries = listOf(remoteLibrary("lib-1")),
                entries = listOf(
                    TestRemoteEntry(
                        item = incomingItem,
                        progress = ReadingProgress(
                            itemId = incomingItem.id,
                            locatorType = LocatorType.PERCENT,
                            percent = 0.8,
                            updatedAtEpochMillis = 200L,
                        ),
                    ),
                ),
            ),
            remoteCatalogWriter = remoteCatalogWriter,
            progressSyncStore = progressSyncStore,
            nowEpochMillis = 999L,
            onProgress = { message, _ -> progressMessages += message },
            savingMessage = "Saving remote catalog...",
            applyingProgressMessage = "Applying remote progress...",
            itemOf = TestRemoteEntry::item,
            progressOf = TestRemoteEntry::progress,
        ) { items ->
            pushedItems = items
            RemoteCatalogProgressPushResult(
                session = "refreshed-session",
                failures = emptyList(),
            )
        }

        assertEquals(SourceType.KAVITA, remoteCatalogWriter.replacedSourceType)
        assertEquals(listOf("Saving remote catalog...", "Applying remote progress..."), progressMessages)
        assertEquals(listOf(incomingItem.id), pushedItems.map(ReadableItem::id))
        assertNull(progressSyncStore.getProgress(staleItem))
        assertEquals(0.8, progressSyncStore.getProgress(incomingItem)?.percent)
        assertEquals(SyncState.CLEAN, progressSyncStore.getProgress(incomingItem)?.syncState)
        assertEquals(200L, progressSyncStore.getProgress(incomingItem)?.updatedAtEpochMillis)
        assertEquals(incomingItem.pageCount, progressSyncStore.savedPageCountByItemId[incomingItem.id])
        assertEquals(1, outcome.libraryCount)
        assertEquals(1, outcome.itemCount)
        assertEquals(mapOf(FormatType.CBZ to 1), outcome.supportedFormatCounts)
        assertEquals("refreshed-session", outcome.session)
        assertTrue(outcome.progressPushFailures.isEmpty())
    }

    @Test
    fun `finalize remote catalog sync propagates missing remote clear rules`() = runTest {
        val cleanItem = remoteItem(id = "issue-clean")
        val dirtyItem = remoteItem(id = "issue-dirty")
        val progressSyncStore = RecordingProgressSyncStore().apply {
            saveProgress(
                item = cleanItem,
                locator = ProgressSyncLocator(locatorType = LocatorType.PAGE, page = 4),
                syncState = SyncState.CLEAN,
                pageCount = cleanItem.pageCount,
                updatedAtEpochMillis = 100L,
            )
            saveProgress(
                item = dirtyItem,
                locator = ProgressSyncLocator(locatorType = LocatorType.PAGE, page = 7),
                syncState = SyncState.DIRTY,
                pageCount = dirtyItem.pageCount,
                updatedAtEpochMillis = 150L,
            )
        }

        finalizeRemoteCatalogSync(
            sourceType = SourceType.KAVITA,
            fetch = RemoteCatalogFetch(
                libraries = listOf(remoteLibrary("lib-1")),
                entries = listOf(
                    TestRemoteEntry(
                        item = cleanItem,
                        progress = null,
                        missingRemoteClearsLocal = true,
                    ),
                    TestRemoteEntry(
                        item = dirtyItem,
                        progress = null,
                        missingRemoteClearsLocal = true,
                        remoteClearOverridesLocal = false,
                    ),
                ),
            ),
            remoteCatalogWriter = SupportTestRemoteCatalogWriter(),
            progressSyncStore = progressSyncStore,
            nowEpochMillis = 999L,
            onProgress = { _, _ -> },
            savingMessage = "Saving remote catalog...",
            applyingProgressMessage = "Applying remote progress...",
            itemOf = TestRemoteEntry::item,
            progressOf = TestRemoteEntry::progress,
            missingRemoteClearsLocal = TestRemoteEntry::missingRemoteClearsLocal,
            remoteClearOverridesLocal = TestRemoteEntry::remoteClearOverridesLocal,
        ) { _ ->
            RemoteCatalogProgressPushResult(
                session = null,
                failures = emptyList(),
            )
        }

        assertNull(progressSyncStore.getProgress(cleanItem))
        assertEquals(SyncState.DIRTY, progressSyncStore.getProgress(dirtyItem)?.syncState)
    }

    @Test
    fun `fetch series entries with progress flattens results and reports progress`() = runTest {
        val progressMessages = mutableListOf<String>()

        val entries = fetchSeriesEntriesWithProgress(
            sourceLabel = "Kavita",
            series = listOf("one", "two", "three"),
            onProgress = { message, _ -> progressMessages += message },
            detailRequestConcurrency = 1,
            progressSteps = 2,
        ) { seriesId ->
            listOf("$seriesId-a", "$seriesId-b")
        }

        assertEquals(
            listOf("one-a", "one-b", "two-a", "two-b", "three-a", "three-b"),
            entries,
        )
        assertEquals("Loading Kavita series 0/3...", progressMessages.first())
        assertTrue(progressMessages.any { message -> message.startsWith("Loading Kavita series 3/3") })
    }

    @Test
    fun `fetch series entries with progress reports empty source`() = runTest {
        val progressMessages = mutableListOf<String>()

        val entries = fetchSeriesEntriesWithProgress<String, String>(
            sourceLabel = "Stump",
            series = emptyList(),
            onProgress = { message, _ -> progressMessages += message },
        ) { error("Unexpected") }

        assertTrue(entries.isEmpty())
        assertEquals(listOf("No Stump series to import."), progressMessages)
    }

    @Test
    fun `should report series progress emits first threshold and final updates`() {
        val reportedSeriesCount = AtomicInteger(0)

        assertTrue(shouldReportSeriesProgress(1, 10, reportedSeriesCount, progressSteps = 5))
        assertEquals(1, reportedSeriesCount.get())
        assertFalse(shouldReportSeriesProgress(2, 10, reportedSeriesCount, progressSteps = 5))
        assertTrue(shouldReportSeriesProgress(3, 10, reportedSeriesCount, progressSteps = 5))
        assertTrue(shouldReportSeriesProgress(10, 10, reportedSeriesCount, progressSteps = 5))
        assertEquals(10, reportedSeriesCount.get())
    }
}

private data class TestRemoteEntry(
    val item: ReadableItem,
    val progress: ReadingProgress?,
    val missingRemoteClearsLocal: Boolean = false,
    val remoteClearOverridesLocal: Boolean = false,
)

private fun remoteLibrary(
    remoteId: String,
): Library = Library(
    id = SourceType.KAVITA.remoteLibraryId(remoteId),
    sourceType = SourceType.KAVITA,
    title = "Library $remoteId",
    remoteId = remoteId,
)

private fun remoteItem(
    id: String,
    format: FormatType = FormatType.CBZ,
): ReadableItem = ReadableItem(
    id = SourceType.KAVITA.remoteItemId(id),
    sourceType = SourceType.KAVITA,
    libraryId = SourceType.KAVITA.remoteLibraryId("lib-1"),
    remoteId = id,
    remoteFileType = null,
    title = "Issue $id",
    format = format,
    pageCount = 24,
    coverUrl = null,
    description = null,
    addedAtEpochMillis = null,
    comicMetadata = null,
)

private class SupportTestRemoteCatalogWriter(
    private val existingItems: List<ReadableItem> = emptyList(),
) : RemoteCatalogWriter {
    var replacedSourceType: SourceType? = null
    var replacedLibraries: List<LibraryEntity> = emptyList()
    var replacedItems: List<ReadableItemEntity> = emptyList()

    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = existingItems

    override suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    ) {
        replacedSourceType = sourceType
        replacedLibraries = libraries
        replacedItems = items
    }
}

private class RecordingProgressSyncStore : ProgressSyncStore {
    private val progressByItemId = MutableStateFlow<Map<String, ProgressSyncSnapshot>>(emptyMap())
    val savedPageCountByItemId = mutableMapOf<String, Int?>()

    override val isHydratedFlow = MutableStateFlow(true).asStateFlow()
    override val progressByItemIdFlow = progressByItemId.asStateFlow()

    override fun currentProgressByItemId(): Map<String, ProgressSyncSnapshot> = progressByItemId.value

    override fun getProgress(item: ReadableItem): ProgressSyncSnapshot? = progressByItemId.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ProgressSyncLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        progressByItemId.value = progressByItemId.value + (
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
        savedPageCountByItemId[item.id] = pageCount
    }

    override suspend fun replaceProgressIfCurrent(
        expected: ProgressSyncSnapshot,
        replacement: ProgressSyncSnapshot?,
    ): Boolean {
        val current = progressByItemId.value[expected.itemId] ?: return false
        if (current != expected) return false
        progressByItemId.value = if (replacement == null) {
            progressByItemId.value - expected.itemId
        } else {
            progressByItemId.value + (expected.itemId to replacement)
        }
        return true
    }

    override fun clearProgress(itemId: String) {
        progressByItemId.value = progressByItemId.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
        savedPageCountByItemId.remove(itemId)
    }
}
