package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.library.LibraryBrowsePreferences
import com.example.unifiedreader.feature.navigation.SourceNavigationPreferences
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupLibraryStateTest {
    @Test
    fun `cached kavita catalog appears before startup hydration completes`() {
        val item = sampleItem(id = "book-1", format = FormatType.CBZ)
            .copy(pageCount = 10)
        val viewModel = createViewModel(
            sampleItems = listOf(item),
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            remoteCatalogReadiness = FakeRemoteCatalogReadiness(initialHydrated = false),
            readerSessionStore = FakeReaderSessionStore(
                initialProgress = mapOf(
                    item.id to ReaderProgressSnapshot(
                        itemId = item.id,
                        locatorType = LocatorType.PAGE,
                        page = 3,
                        updatedAtEpochMillis = 100L,
                    ),
                ),
            ),
        )
        val state = viewModel.uiState

        assertFalse(state.toString(), state.isSelectedSourceHydrated)
        assertEquals(1, state.visibleItems.size)
        assertEquals(item.id, state.libraryShelvesState.continueReading.single().item.id)
        assertTrue(state.toString(), state.libraryShelvesState.hasContent)
        assertFalse(state.toString(), state.libraryShelvesState.isLoading)
    }

    @Test
    fun `stale cached local availability shows local filter results immediately on startup`() {
        val item = sampleItem(
            id = "book-1",
            title = "Cached Local Book",
            format = FormatType.EPUB,
        )
        val localStateCache = InMemoryRemoteItemLocalStateCache().apply {
            store(
                items = listOf(item),
                state = RemoteItemLocalState(
                    downloadedItemIds = setOf(item.id),
                    locallyAvailableItemIds = setOf(item.id),
                ),
            )
        }
        val blockedScan = BlockingStoredDocumentScanDataSource(
            sourceType = SourceType.KAVITA,
            freshStoredItemIds = emptySet(),
        )

        val viewModel = createViewModel(
            sampleItems = listOf(item),
            remoteDownloadDataSources = listOf(blockedScan),
            remoteItemLocalStateCache = localStateCache,
            sourceNavigationPreferencesStore = FakeSourceNavigationPreferencesStore(
                SourceNavigationPreferences(sourceOrder = listOf(SourceType.KAVITA)),
            ),
            libraryBrowsePreferencesStore = FakeLibraryBrowsePreferencesStore(
                LibraryBrowsePreferences(
                    filtersBySource = mapOf(
                        SourceType.KAVITA to LibraryFilterState(
                            fileLocations = setOf(LibraryFileLocation.LOCAL),
                        ),
                    ),
                ),
            ),
        )
        val state = viewModel.uiState

        assertTrue(state.isRemoteItemLocalStateKnown)
        assertFalse(state.isSelectedLibraryLocalAvailabilityPending)
        assertEquals(listOf(item.id), state.visibleItems.map(ReadableItem::id))

        blockedScan.release()
    }

    @Test
    fun `partial cached local availability keeps uncached selected library pending on startup`() {
        val cachedItem = sampleItem(
            id = "cached-book",
            title = "Cached Local Book",
            format = FormatType.EPUB,
        ).copy(
            libraryId = SourceType.KAVITA.remoteLibraryId("cached-library"),
        )
        val uncachedItem = sampleItem(
            id = "uncached-book",
            title = "Uncached Book",
            format = FormatType.EPUB,
        ).copy(
            libraryId = SourceType.KAVITA.remoteLibraryId("uncached-library"),
        )
        val localStateCache = InMemoryRemoteItemLocalStateCache().apply {
            store(
                items = listOf(cachedItem),
                state = RemoteItemLocalState(
                    downloadedItemIds = setOf(cachedItem.id),
                    locallyAvailableItemIds = setOf(cachedItem.id),
                ),
            )
        }
        val blockedScan = BlockingStoredDocumentScanDataSource(
            sourceType = SourceType.KAVITA,
            freshStoredItemIds = setOf(cachedItem.id, uncachedItem.id),
        )

        val viewModel = createViewModel(
            sampleItems = listOf(cachedItem, uncachedItem),
            remoteDownloadDataSources = listOf(blockedScan),
            remoteItemLocalStateCache = localStateCache,
            sourceNavigationPreferencesStore = FakeSourceNavigationPreferencesStore(
                SourceNavigationPreferences(sourceOrder = listOf(SourceType.KAVITA)),
            ),
            libraryBrowsePreferencesStore = FakeLibraryBrowsePreferencesStore(
                LibraryBrowsePreferences(
                    filtersBySource = mapOf(
                        SourceType.KAVITA to LibraryFilterState(
                            libraries = setOf(uncachedItem.libraryId),
                            fileLocations = setOf(LibraryFileLocation.LOCAL),
                        ),
                    ),
                ),
            ),
        )
        val state = viewModel.uiState

        assertFalse(state.isRemoteItemLocalStateKnown)
        assertTrue(state.isSelectedLibraryLocalAvailabilityPending)
        assertTrue(state.visibleItems.isEmpty())

        blockedScan.release()
    }

    // In production viewModelScope runs on the single-threaded Dispatchers.Main.immediate, which the
    // AppViewModel coordinators rely on and which runs each launched refresh eagerly to completion.
    // In this unit test viewModelScope otherwise falls back to the multi-threaded Dispatchers.Default,
    // so the async availability refresh both races the assertions and can lose its non-synchronized
    // state writes. Pin it to a single-threaded, eager dispatcher so the failing refresh settles
    // deterministically before the assertions run, mirroring production. The ViewModel is cleared
    // before resetMain so its Main-bound collectors can't outlive the test.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `failed startup availability refresh keeps stale local filter results`() {
        val item = sampleItem(
            id = "book-1",
            title = "Cached Local Book",
            format = FormatType.EPUB,
        )
        val localStateCache = InMemoryRemoteItemLocalStateCache().apply {
            store(
                items = listOf(item),
                state = RemoteItemLocalState(
                    downloadedItemIds = setOf(item.id),
                    locallyAvailableItemIds = setOf(item.id),
                ),
            )
        }

        Dispatchers.setMain(Dispatchers.Unconfined)
        var viewModel: AppViewModel? = null
        try {
            viewModel = createViewModel(
                sampleItems = listOf(item),
                remoteDownloadDataSources = listOf(
                    FailingStoredDocumentScanDataSource(sourceType = SourceType.KAVITA),
                ),
                remoteItemLocalStateCache = localStateCache,
                sourceNavigationPreferencesStore = FakeSourceNavigationPreferencesStore(
                    SourceNavigationPreferences(sourceOrder = listOf(SourceType.KAVITA)),
                ),
                libraryBrowsePreferencesStore = FakeLibraryBrowsePreferencesStore(
                    LibraryBrowsePreferences(
                        filtersBySource = mapOf(
                            SourceType.KAVITA to LibraryFilterState(
                                fileLocations = setOf(LibraryFileLocation.LOCAL),
                            ),
                        ),
                    ),
                ),
            )
            val state = viewModel.uiState

            assertTrue(state.toString(), state.isRemoteItemLocalStateKnown)
            assertFalse(state.toString(), state.isRemoteItemLocalStateRefreshing)
            assertFalse(state.toString(), state.isSelectedLibraryLocalAvailabilityPending)
            assertEquals(state.toString(), listOf(item.id), state.visibleItems.map(ReadableItem::id))
        } finally {
            viewModel?.let(::clearViewModel)
            Dispatchers.resetMain()
        }
    }
}

private class BlockingStoredDocumentScanDataSource(
    override val sourceType: SourceType,
    private val freshStoredItemIds: Set<String>,
) : RemoteItemDownloadDataSource {
    private val gate = CompletableDeferred<Unit>()

    fun release() {
        gate.complete(Unit)
    }

    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> {
        gate.await()
        return freshStoredItemIds
    }

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = false
}

private class FailingStoredDocumentScanDataSource(
    override val sourceType: SourceType,
) : RemoteItemDownloadDataSource {
    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> {
        error("Unable to scan local availability.")
    }

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = false
}
