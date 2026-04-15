package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLibraryProgressRefreshCoordinatorTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `stale refresh result is ignored when library items change`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstItem = sampleItem(id = "first", format = FormatType.EPUB)
        val secondItem = sampleItem(id = "second", format = FormatType.EPUB)
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = firstItem,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = com.example.unifiedreader.core.model.LocatorType.PAGE,
                    page = 2,
                ),
                pageCount = 10,
            )
        }
        val coordinator = AppLibraryProgressRefreshCoordinator(
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = emptyList<RemoteItemDownloadDataSource>(),
            ),
            readerSessionStore = readerSessionStore,
            appDispatchers = AppDispatchers(
                io = dispatcher,
                default = dispatcher,
            ),
        )
        var state = AppUiState(items = listOf(firstItem))
        var refreshDownloadedItems: List<com.example.unifiedreader.core.model.ReadableItem>? = null
        var updateCalls = 0

        coordinator.refresh(
            scope = backgroundScope,
            stateProvider = { state },
            updateState = { transform ->
                state = transform(state)
                updateCalls += 1
            },
            refreshDownloadedRemoteItemIds = { items ->
                refreshDownloadedItems = items
            },
        )
        state = state.copy(items = listOf(secondItem))

        advanceUntilIdle()

        assertEquals(0, updateCalls)
        assertEquals(emptyMap<String, com.example.unifiedreader.feature.library.ItemProgressSummary>(), state.progressByItemId)
        assertNull(refreshDownloadedItems)
    }
}
