package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.core.model.LocatorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupLibraryStateTest {
    @Test
    fun `cached kavita catalog appears in initial ui state`() {
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

        assertTrue(state.toString(), state.isSelectedSourceHydrated)
        assertEquals(1, state.visibleItems.size)
        assertEquals(item.id, state.libraryShelvesState.continueReading.single().item.id)
        assertTrue(state.toString(), state.libraryShelvesState.hasContent)
        assertFalse(state.toString(), state.libraryShelvesState.isLoading)
    }
}
