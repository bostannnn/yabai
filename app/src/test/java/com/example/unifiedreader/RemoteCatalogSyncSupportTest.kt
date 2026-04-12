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
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        val readerSessionStore = RecordingReaderSessionStore().apply {
            saveProgress(
                item = staleItem,
                locator = ReaderLocator(locatorType = LocatorType.PAGE, page = 2),
                syncState = SyncState.CLEAN,
                pageCount = staleItem.pageCount,
                updatedAtEpochMillis = 50L,
            )
            saveProgress(
                item = incomingItem,
                locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.2),
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
            readerSessionStore = readerSessionStore,
            nowEpochMillis = 999L,
            onProgress = { message, _ -> progressMessages += message },
            savingMessage = "Saving remote catalog...",
            applyingProgressMessage = "Applying remote progress...",
            itemOf = TestRemoteEntry::item,
            progressOf = TestRemoteEntry::progress,
        ) { items ->
            pushedItems = items
            "refreshed-session"
        }

        assertEquals(SourceType.KAVITA, remoteCatalogWriter.replacedSourceType)
        assertEquals(listOf("Saving remote catalog...", "Applying remote progress..."), progressMessages)
        assertEquals(listOf(incomingItem.id), pushedItems.map(ReadableItem::id))
        assertNull(readerSessionStore.getProgress(staleItem))
        assertEquals(0.8, readerSessionStore.getProgress(incomingItem)?.percent)
        assertEquals(SyncState.CLEAN, readerSessionStore.getProgress(incomingItem)?.syncState)
        assertEquals(200L, readerSessionStore.getProgress(incomingItem)?.updatedAtEpochMillis)
        assertEquals(incomingItem.pageCount, readerSessionStore.getSession(incomingItem)?.pageCount)
        assertEquals(1, outcome.libraryCount)
        assertEquals(1, outcome.itemCount)
        assertEquals(mapOf(FormatType.CBZ to 1), outcome.supportedFormatCounts)
        assertEquals("refreshed-session", outcome.session)
    }

    @Test
    fun `finalize remote catalog sync propagates missing remote clear rules`() = runTest {
        val cleanItem = remoteItem(id = "issue-clean")
        val dirtyItem = remoteItem(id = "issue-dirty")
        val readerSessionStore = RecordingReaderSessionStore().apply {
            saveProgress(
                item = cleanItem,
                locator = ReaderLocator(locatorType = LocatorType.PAGE, page = 4),
                syncState = SyncState.CLEAN,
                pageCount = cleanItem.pageCount,
                updatedAtEpochMillis = 100L,
            )
            saveProgress(
                item = dirtyItem,
                locator = ReaderLocator(locatorType = LocatorType.PAGE, page = 7),
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
            readerSessionStore = readerSessionStore,
            nowEpochMillis = 999L,
            onProgress = { _, _ -> },
            savingMessage = "Saving remote catalog...",
            applyingProgressMessage = "Applying remote progress...",
            itemOf = TestRemoteEntry::item,
            progressOf = TestRemoteEntry::progress,
            missingRemoteClearsLocal = TestRemoteEntry::missingRemoteClearsLocal,
            remoteClearOverridesLocal = TestRemoteEntry::remoteClearOverridesLocal,
        ) { _ -> null }

        assertNull(readerSessionStore.getProgress(cleanItem))
        assertEquals(SyncState.DIRTY, readerSessionStore.getProgress(dirtyItem)?.syncState)
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

private class RecordingReaderSessionStore : ReaderSessionStore {
    private val progressByItemId = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    private val sessionByItemId = MutableStateFlow<Map<String, ReaderSessionSnapshot>>(emptyMap())

    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressByItemId
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionByItemId

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressByItemId.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        progressByItemId.value = progressByItemId.value + (
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
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionByItemId.value[item.id]

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionByItemId.value[itemId] ?: ReaderSessionSnapshot(itemId = itemId)
        sessionByItemId.value = sessionByItemId.value + (
            itemId to current.copy(
                pageCount = pageCount ?: current.pageCount,
                preferences = current.preferences + preferences,
            )
        )
    }

    override fun clearProgress(itemId: String) {
        progressByItemId.value = progressByItemId.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
        sessionByItemId.value = sessionByItemId.value - itemId
    }
}
