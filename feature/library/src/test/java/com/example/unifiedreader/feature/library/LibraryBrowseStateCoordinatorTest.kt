package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryBrowseStateCoordinatorTest {
    @Test
    fun `restore state loads persisted filters and sort modes`() {
        val preferencesStore = FakeLibraryBrowsePreferencesStore(
            initialPreferences = LibraryBrowsePreferences(
                filtersBySource = mapOf(
                    SourceType.LOCAL to LibraryFilterState(formats = setOf(FormatType.EPUB)),
                ),
                sortsBySource = mapOf(
                    SourceType.LOCAL to LibrarySortMode.TITLE,
                ),
            ),
        )
        val coordinator = LibraryBrowseStateCoordinator(preferencesStore)

        val restoredState = coordinator.restoreState(
            currentSearchQueriesBySource = mapOf(SourceType.LOCAL to "earthsea"),
        )

        assertEquals(setOf(FormatType.EPUB), restoredState.filtersBySource[SourceType.LOCAL]?.formats)
        assertEquals(LibrarySortMode.TITLE, restoredState.sortsBySource[SourceType.LOCAL])
        assertEquals("earthsea", restoredState.searchQueriesBySource[SourceType.LOCAL])
    }

    @Test
    fun `update filters removes empty state for selected source`() {
        val coordinator = LibraryBrowseStateCoordinator(FakeLibraryBrowsePreferencesStore())
        val initialState = LibraryBrowseState(
            filtersBySource = mapOf(
                SourceType.KAVITA to LibraryFilterState(formats = setOf(FormatType.CBZ)),
            ),
        )

        val updatedState = coordinator.clearFilters(
            currentState = initialState,
            selectedSource = SourceType.KAVITA,
        )

        assertFalse(SourceType.KAVITA in updatedState.filtersBySource)
    }

    @Test
    fun `update sort mode does not store default sort explicitly`() {
        val coordinator = LibraryBrowseStateCoordinator(FakeLibraryBrowsePreferencesStore())
        val initialState = LibraryBrowseState(
            sortsBySource = mapOf(
                SourceType.LOCAL to LibrarySortMode.TITLE,
            ),
        )

        val updatedState = coordinator.updateSortMode(
            currentState = initialState,
            selectedSource = SourceType.LOCAL,
            sortMode = defaultLibrarySortMode,
        )

        assertFalse(SourceType.LOCAL in updatedState.sortsBySource)
    }

    @Test
    fun `persist delegates filters and sort mode to preferences store`() = runBlocking {
        val preferencesStore = FakeLibraryBrowsePreferencesStore()
        val coordinator = LibraryBrowseStateCoordinator(preferencesStore)
        val filters = LibraryFilterState(formats = setOf(FormatType.EPUB))

        coordinator.persistFilters(
            sourceType = SourceType.LOCAL,
            filters = filters,
        )
        coordinator.persistSortMode(
            sourceType = SourceType.LOCAL,
            sortMode = LibrarySortMode.TITLE,
        )

        assertEquals(filters, preferencesStore.preferencesFlow.value.filtersBySource[SourceType.LOCAL])
        assertEquals(LibrarySortMode.TITLE, preferencesStore.preferencesFlow.value.sortsBySource[SourceType.LOCAL])
    }
}

private class FakeLibraryBrowsePreferencesStore(
    initialPreferences: LibraryBrowsePreferences = LibraryBrowsePreferences(),
) : LibraryBrowsePreferencesStore {
    private val state = MutableStateFlow(initialPreferences)

    override val preferencesFlow: StateFlow<LibraryBrowsePreferences> = state

    override suspend fun setFilters(
        sourceType: SourceType,
        filters: LibraryFilterState,
    ) {
        state.value = state.value.copy(
            filtersBySource = state.value.filtersBySource.updatedFilters(
                sourceType = sourceType,
                filters = filters,
            ),
        )
    }

    override suspend fun setSortMode(
        sourceType: SourceType,
        sortMode: LibrarySortMode,
    ) {
        state.value = state.value.copy(
            sortsBySource = state.value.sortsBySource.updatedSortMode(
                sourceType = sourceType,
                sortMode = sortMode,
            ),
        )
    }
}
