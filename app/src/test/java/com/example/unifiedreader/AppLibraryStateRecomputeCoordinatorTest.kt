package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.library.LibraryDerivedStateComputer
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesStateComputer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLibraryStateRecomputeCoordinatorTest {
    @Test
    fun `non library state updates reuse computed library state immediately`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialState = coordinator.initialize(sampleLibraryState())
        val publishedStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(topLevel = TopLevelDestination.Settings)
            },
            scope = this,
            publishState = publishedStates::add,
        )

        assertEquals(1, publishedStates.size)
        assertEquals(TopLevelDestination.Settings, publishedStates.single().topLevel)
        assertSame(initialState.visibleItems, publishedStates.single().visibleItems)
        assertSame(initialState.libraryShelvesState, publishedStates.single().libraryShelvesState)
    }

    @Test
    fun `initialize keeps shelves deferred even when shelf content exists`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))

        val initializedState = coordinator.initialize(
            sampleLibraryState().copy(
                items = listOf(sampleItem(id = "book-1", format = FormatType.EPUB)),
                progressByItemId = mapOf(
                    "book-1" to ItemProgressSummary(
                        label = "Continue",
                        fraction = 0.35f,
                        lastReadAtEpochMillis = 25L,
                    ),
                ),
            ),
        )

        assertEquals(1, initializedState.visibleItems.size)
        assertFalse(initializedState.libraryShelvesState.hasContent)
    }

    @Test
    fun `library updates publish quick root projection before shelf enrichment`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialState = coordinator.initialize(sampleLibraryState())
        val publishedStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(
                    items = listOf(sampleItem(id = "book-1", format = FormatType.EPUB)),
                    progressByItemId = mapOf(
                        "book-1" to ItemProgressSummary(
                            label = "Continue",
                            fraction = 0.35f,
                            lastReadAtEpochMillis = 25L,
                        ),
                    ),
                )
            },
            scope = this,
            publishState = publishedStates::add,
        )

        assertEquals(1, publishedStates.size)
        assertEquals(1, publishedStates.first().visibleItems.size)
        assertFalse(publishedStates.first().libraryShelvesState.hasContent)

        advanceUntilIdle()

        assertEquals(2, publishedStates.size)
        assertTrue(publishedStates.last().libraryShelvesState.hasContent)
    }

    @Test
    fun `search browse keeps shelves deferred even after full recompute`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialState = coordinator.initialize(sampleLibraryState())
        val publishedStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(
                    items = listOf(sampleItem(id = "book-1", format = FormatType.EPUB)),
                    progressByItemId = mapOf(
                        "book-1" to ItemProgressSummary(
                            label = "Continue",
                            fraction = 0.35f,
                            lastReadAtEpochMillis = 25L,
                        ),
                    ),
                    librarySearchQueriesBySource = mapOf(SourceType.KAVITA to "book"),
                )
            },
            scope = this,
            publishState = publishedStates::add,
        )

        advanceUntilIdle()

        assertEquals(2, publishedStates.size)
        assertTrue(publishedStates.all { state -> !state.shouldReserveLibraryShelvesSlot })
        assertTrue(publishedStates.all { state -> !state.libraryShelvesState.hasContent })
    }

    private fun createCoordinator(
        testDispatcher: TestDispatcher,
    ): AppLibraryStateRecomputeCoordinator {
        return AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = AppDispatchers(
                io = testDispatcher,
                default = testDispatcher,
            ),
        )
    }

    private fun sampleLibraryState(): AppUiState = AppUiState(
        libraries = listOf(
            Library(
                id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
                contentType = LibraryContentType.COMICS,
            ),
        ),
        items = emptyList(),
        selectedSource = SourceType.KAVITA,
    )
}
