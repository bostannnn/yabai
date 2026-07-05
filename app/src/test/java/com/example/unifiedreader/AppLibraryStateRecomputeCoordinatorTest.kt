package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.ReadingListItemRef
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.core.model.remoteReadingListId
import com.example.unifiedreader.feature.library.LibraryDerivedStateComputer
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesStateComputer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun `initialize restores computed shelves when shelf content exists`() = runTest {
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
        assertTrue(initializedState.libraryShelvesState.hasContent)
        assertFalse(initializedState.libraryShelvesState.isLoading)
    }

    @Test
    fun `library updates publish coherent root presentation before full enrichment`() = runTest {
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

        assertTrue(publishedStates.isEmpty())

        advanceUntilIdle()

        assertEquals(1, publishedStates.size)
        assertEquals(1, publishedStates.single().visibleItems.size)
        assertTrue(publishedStates.single().libraryShelvesState.hasContent)
        assertFalse(publishedStates.single().libraryShelvesState.isLoading)
    }

    @Test
    fun `library updates publish refreshed shelves with refreshed grid`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialState = coordinator.initialize(
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
        val publishedStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(
                    progressByItemId = mapOf(
                        "book-1" to ItemProgressSummary(
                            label = "Continue",
                            fraction = 0.65f,
                            lastReadAtEpochMillis = 50L,
                        ),
                    ),
                )
            },
            scope = this,
            publishState = publishedStates::add,
        )

        assertTrue(publishedStates.isEmpty())

        advanceUntilIdle()

        assertEquals(1, publishedStates.size)
        assertEquals(1, publishedStates.single().visibleItems.size)
        assertTrue(publishedStates.single().libraryShelvesState.hasContent)
        assertFalse(publishedStates.single().libraryShelvesState.isLoading)
        assertEquals(0.65f, publishedStates.single().libraryShelvesState.continueReading.single().progress.fraction)
    }

    @Test
    fun `pending Kavita root recomputes publish only latest coherent presentation`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialState = coordinator.initialize(sampleLibraryState())
        val firstItem = sampleItem(id = "book-1", format = FormatType.EPUB)
        val refreshedItem = sampleItem(id = "book-1", format = FormatType.EPUB)
        val publishedStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(
                    items = listOf(firstItem),
                    progressByItemId = mapOf(
                        firstItem.id to ItemProgressSummary(
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

        assertTrue(publishedStates.isEmpty())

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(
                    items = listOf(refreshedItem),
                    progressByItemId = mapOf(
                        refreshedItem.id to ItemProgressSummary(
                            label = "Continue",
                            fraction = 0.65f,
                            lastReadAtEpochMillis = 50L,
                        ),
                    ),
                )
            },
            scope = this,
            publishState = publishedStates::add,
        )

        assertTrue(publishedStates.isEmpty())

        advanceUntilIdle()

        assertEquals(1, publishedStates.size)
        assertEquals(listOf(refreshedItem.id), publishedStates.single().visibleItems.map { item -> item.id })
        assertTrue(publishedStates.single().libraryShelvesState.hasContent)
        assertFalse(publishedStates.single().libraryShelvesState.isLoading)
        assertEquals(0.65f, publishedStates.single().libraryShelvesState.continueReading.single().progress.fraction)
    }

    @Test
    fun `pending Kavita root recompute publishes with eager dispatcher`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val coordinator = createCoordinator(dispatcher)
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
        assertEquals(1, publishedStates.single().visibleItems.size)
        assertTrue(publishedStates.single().libraryShelvesState.hasContent)
        assertFalse(publishedStates.single().libraryShelvesState.isLoading)
    }

    @Test
    fun `leaving library cancels pending Kavita root recompute`() = runTest {
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

        assertTrue(publishedStates.isEmpty())

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

        advanceUntilIdle()

        assertEquals(1, publishedStates.size)
        assertEquals(TopLevelDestination.Settings, publishedStates.single().topLevel)
        assertFalse(publishedStates.any { state -> state.topLevel == TopLevelDestination.Library })
    }

    @Test
    fun `returning from series publishes refreshed root shelves with grid`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val item = sampleItem(id = "book-1", format = FormatType.EPUB)
        val initialState = coordinator.initialize(
            sampleLibraryState().copy(
                items = listOf(item),
                progressByItemId = mapOf(
                    item.id to ItemProgressSummary(
                        label = "Continue",
                        fraction = 0.35f,
                        lastReadAtEpochMillis = 25L,
                    ),
                ),
            ),
        )
        val seriesStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(selectedKavitaSeriesKey = "series-1")
            },
            scope = this,
            publishState = seriesStates::add,
        )
        advanceUntilIdle()

        val seriesState = seriesStates.last()
        assertFalse(seriesState.shouldReserveLibraryShelvesSlot)
        assertFalse(seriesState.libraryShelvesState.hasContent)
        val backStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = seriesState,
            transform = { state ->
                state.copy(
                    selectedKavitaSeriesKey = null,
                    progressByItemId = mapOf(
                        item.id to ItemProgressSummary(
                            label = "Continue",
                            fraction = 0.65f,
                            lastReadAtEpochMillis = 50L,
                        ),
                    ),
                )
            },
            scope = this,
            publishState = backStates::add,
        )

        assertTrue(backStates.isEmpty())

        advanceUntilIdle()

        assertEquals(1, backStates.size)
        assertTrue(backStates.single().shouldReserveLibraryShelvesSlot)
        assertEquals(1, backStates.single().visibleItems.size)
        assertTrue(backStates.single().libraryShelvesState.hasContent)
        assertFalse(backStates.single().libraryShelvesState.isLoading)
        assertEquals(
            item.id,
            backStates.single().libraryShelvesState.continueReading.single().item.id,
        )
        assertEquals(0.65f, backStates.single().libraryShelvesState.continueReading.single().progress.fraction)
    }

    @Test
    fun `first return from series preserves startup shelves after root identity refresh`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialItem = sampleItem(id = "book-1", format = FormatType.EPUB)
        val refreshedItem = sampleItem(id = "book-1", format = FormatType.EPUB)
        val initialState = coordinator.initialize(
            sampleLibraryState().copy(
                items = listOf(initialItem),
                progressByItemId = mapOf(
                    initialItem.id to ItemProgressSummary(
                        label = "Continue",
                        fraction = 0.35f,
                        lastReadAtEpochMillis = 25L,
                    ),
                ),
            ),
        )
        val rootRefreshStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(
                    items = listOf(refreshedItem),
                    progressByItemId = mapOf(
                        refreshedItem.id to ItemProgressSummary(
                            label = "Continue",
                            fraction = 0.45f,
                            lastReadAtEpochMillis = 35L,
                        ),
                    ),
                )
            },
            scope = this,
            publishState = rootRefreshStates::add,
        )

        assertTrue(rootRefreshStates.isEmpty())
        advanceUntilIdle()

        val rootRefreshingState = rootRefreshStates.single()
        assertEquals(1, rootRefreshingState.visibleItems.size)
        assertTrue(rootRefreshingState.libraryShelvesState.hasContent)
        assertFalse(rootRefreshingState.libraryShelvesState.isLoading)
        val seriesStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = rootRefreshingState,
            transform = { state ->
                state.copy(selectedKavitaSeriesKey = "series-1")
            },
            scope = this,
            publishState = seriesStates::add,
        )

        val seriesState = seriesStates.first()
        assertFalse(seriesState.shouldReserveLibraryShelvesSlot)
        assertFalse(seriesState.libraryShelvesState.hasContent)
        val backStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = seriesState,
            transform = { state ->
                state.copy(
                    selectedKavitaSeriesKey = null,
                    progressByItemId = mapOf(
                        refreshedItem.id to ItemProgressSummary(
                            label = "Continue",
                            fraction = 0.65f,
                            lastReadAtEpochMillis = 50L,
                        ),
                    ),
                )
            },
            scope = this,
            publishState = backStates::add,
        )

        assertTrue(backStates.isEmpty())

        advanceUntilIdle()

        assertEquals(1, backStates.size)
        assertTrue(backStates.single().shouldReserveLibraryShelvesSlot)
        assertEquals(1, backStates.single().visibleItems.size)
        assertTrue(backStates.single().libraryShelvesState.hasContent)
        assertFalse(backStates.single().libraryShelvesState.isLoading)
        assertEquals(
            refreshedItem.id,
            backStates.single().libraryShelvesState.continueReading.single().item.id,
        )
        assertEquals(0.65f, backStates.single().libraryShelvesState.continueReading.single().progress.fraction)
    }

    @Test
    fun `library filters do not publish new visible items with loading shelves`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val shelfItem = sampleItem(id = "book-1", title = "Shelf Book", format = FormatType.EPUB)
        val filteredItem = sampleItem(id = "pdf-1", title = "Filtered PDF", format = FormatType.PDF)
        val initialState = coordinator.initialize(
            sampleLibraryState().copy(
                items = listOf(shelfItem, filteredItem),
                progressByItemId = mapOf(
                    shelfItem.id to ItemProgressSummary(
                        label = "Continue",
                        fraction = 0.35f,
                        lastReadAtEpochMillis = 25L,
                    ),
                ),
            ),
        )
        val publishedStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(
                    libraryFiltersBySource = mapOf(
                        SourceType.KAVITA to LibraryFilterState(formats = setOf(FormatType.PDF)),
                    ),
                )
            },
            scope = this,
            publishState = publishedStates::add,
        )

        assertEquals(1, publishedStates.size)
        assertEquals(listOf(filteredItem.id), publishedStates.first().visibleItems.map { item -> item.id })
        assertTrue(publishedStates.first().libraryShelvesState.hasContent)
        assertFalse(publishedStates.first().libraryShelvesState.isLoading)

        advanceUntilIdle()

        assertEquals(2, publishedStates.size)
        assertTrue(publishedStates.all { state -> state.shouldReserveLibraryShelvesSlot })
        assertTrue(publishedStates.all { state -> state.libraryShelvesState.hasContent })
        assertTrue(
            publishedStates.none { state ->
                state.visibleItems.map { item -> item.id } == listOf(filteredItem.id) &&
                    state.libraryShelvesState.isLoading
            },
        )
        assertFalse(publishedStates.last().libraryShelvesState.isLoading)
        assertEquals(listOf(filteredItem.id), publishedStates.last().visibleItems.map { item -> item.id })
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
                    items = listOf(sampleItem(id = "book-1", title = "Book One", format = FormatType.EPUB)),
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

        assertEquals(1, publishedStates.size)
        assertEquals(1, publishedStates.first().visibleItems.size)

        advanceUntilIdle()

        assertEquals(2, publishedStates.size)
        assertTrue(publishedStates.all { state -> !state.shouldReserveLibraryShelvesSlot })
        assertTrue(publishedStates.all { state -> !state.libraryShelvesState.hasContent })
        assertTrue(publishedStates.none { state -> state.libraryShelvesState.isLoading })
    }

    @Test
    fun `selected Kavita reading list loading does not publish empty scope as ready content`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val firstItem = sampleItem(id = "book-1", title = "First Book", format = FormatType.EPUB)
        val secondItem = sampleItem(id = "book-2", title = "Second Book", format = FormatType.EPUB)
        val readingListId = SourceType.KAVITA.remoteReadingListId("reading-list-1")
        val initialState = coordinator.initialize(
            sampleLibraryState().copy(
                items = listOf(firstItem, secondItem),
            ),
        )
        val loadingStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(
                    kavitaReadingLists = state.kavitaReadingLists.copy(
                        selectedReadingListId = readingListId,
                        loadingSelectedReadingListId = readingListId,
                        isLoadingSelectedItems = true,
                    ),
                )
            },
            scope = this,
            publishState = loadingStates::add,
        )

        advanceUntilIdle()

        assertTrue(loadingStates.isNotEmpty())
        assertTrue(loadingStates.all { state -> !state.shouldReserveLibraryShelvesSlot })
        assertTrue(loadingStates.all { state -> state.kavitaReadingLists.isLoadingSelectedItems })
        assertTrue(loadingStates.all { state -> state.visibleItems.isEmpty() })
        assertTrue(
            loadingStates.none { state ->
                !state.kavitaReadingLists.isLoadingSelectedItems &&
                    state.visibleItems.isEmpty()
            },
        )

        val readyStates = mutableListOf<AppUiState>()
        coordinator.updateState(
            currentState = loadingStates.last(),
            transform = { state ->
                state.copy(
                    kavitaReadingLists = state.kavitaReadingLists.copy(
                        itemsByReadingListId = mapOf(
                            readingListId to listOf(
                                ReadingListItemRef(
                                    readingListId = readingListId,
                                    sourceType = SourceType.KAVITA,
                                    itemId = firstItem.id,
                                    order = 0,
                                ),
                            ),
                        ),
                        loadingSelectedReadingListId = null,
                        isLoadingSelectedItems = false,
                    ),
                )
            },
            scope = this,
            publishState = readyStates::add,
        )

        assertTrue(
            readyStates.none { state ->
                !state.kavitaReadingLists.isLoadingSelectedItems &&
                    state.visibleItems.isEmpty()
            },
        )
        assertEquals(listOf(firstItem.id), readyStates.first().visibleItems.map { item -> item.id })

        advanceUntilIdle()

        assertFalse(readyStates.last().kavitaReadingLists.isLoadingSelectedItems)
        assertEquals(listOf(firstItem.id), readyStates.last().visibleItems.map { item -> item.id })
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
        hydratedSources = setOf(SourceType.KAVITA),
    )
}
