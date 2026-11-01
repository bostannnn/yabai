package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
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
    fun `initialize keeps shelves loading when startup catalog exists without shelf progress`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))

        val initializedState = coordinator.initialize(
            sampleLibraryState().copy(
                items = listOf(sampleItem(id = "book-1", format = FormatType.EPUB)),
                progressByItemId = emptyMap(),
                hydratedSources = emptySet(),
            ),
        )

        assertFalse(initializedState.isSelectedSourceHydrated)
        assertEquals(listOf("book-1"), initializedState.visibleItems.map { item -> item.id })
        assertFalse(initializedState.libraryShelvesState.hasContent)
        assertTrue(initializedState.libraryShelvesState.isLoading)
        assertTrue(initializedState.shouldShowLibraryShelves)
    }

    @Test
    fun `hydrating startup catalog without shelf progress clears shelves loading`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialState = coordinator.initialize(
            sampleLibraryState().copy(
                items = listOf(sampleItem(id = "book-1", format = FormatType.EPUB)),
                progressByItemId = emptyMap(),
                hydratedSources = emptySet(),
            ),
        )
        val publishedStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(hydratedSources = setOf(SourceType.KAVITA))
            },
            scope = this,
            publishState = publishedStates::add,
        )

        assertEquals(1, publishedStates.size)
        assertTrue(publishedStates.single().isSelectedSourceHydrated)
        assertEquals(listOf("book-1"), publishedStates.single().visibleItems.map { item -> item.id })
        assertFalse(publishedStates.single().libraryShelvesState.hasContent)
        assertFalse(publishedStates.single().libraryShelvesState.isLoading)
        assertFalse(publishedStates.single().shouldShowLibraryShelves)
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
    fun `cached startup shelves stay ready while selected source hydration is pending`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val cachedItem = sampleItem(id = "book-1", format = FormatType.EPUB)
        val refreshedCachedItem = sampleItem(id = "book-1", format = FormatType.EPUB)
        val cachedProgress = mapOf(
            cachedItem.id to ItemProgressSummary(
                label = "Continue",
                fraction = 0.35f,
                lastReadAtEpochMillis = 25L,
            ),
        )
        val initialState = coordinator.initialize(
            sampleLibraryState().copy(
                items = listOf(cachedItem),
                progressByItemId = cachedProgress,
                hydratedSources = emptySet(),
            ),
        )
        val publishedStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state ->
                state.copy(
                    items = listOf(refreshedCachedItem),
                    progressByItemId = cachedProgress,
                    hydratedSources = emptySet(),
                )
            },
            scope = this,
            publishState = publishedStates::add,
        )

        assertTrue(publishedStates.isEmpty())

        advanceUntilIdle()

        assertEquals(1, publishedStates.size)
        assertFalse(publishedStates.single().isSelectedSourceHydrated)
        assertEquals(listOf(refreshedCachedItem.id), publishedStates.single().visibleItems.map { item -> item.id })
        assertTrue(publishedStates.single().libraryShelvesState.hasContent)
        assertFalse(publishedStates.single().libraryShelvesState.isLoading)
        assertEquals(
            refreshedCachedItem.id,
            publishedStates.single().libraryShelvesState.continueReading.single().item.id,
        )
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

        // The root's last presentation shows at once, as it was when the series opened.
        assertEquals(1, backStates.size)
        val reopenedState = backStates.single()
        assertEquals(null, reopenedState.selectedKavitaSeriesKey)
        assertTrue(reopenedState.shouldReserveLibraryShelvesSlot)
        assertEquals(1, reopenedState.visibleItems.size)
        assertTrue(reopenedState.libraryShelvesState.hasContent)
        assertFalse(reopenedState.libraryShelvesState.isLoading)
        assertEquals(0.35f, reopenedState.libraryShelvesState.continueReading.single().progress.fraction)

        advanceUntilIdle()

        assertEquals(2, backStates.size)
        val refreshedState = backStates.last()
        assertTrue(refreshedState.shouldReserveLibraryShelvesSlot)
        assertEquals(1, refreshedState.visibleItems.size)
        assertTrue(refreshedState.libraryShelvesState.hasContent)
        assertFalse(refreshedState.libraryShelvesState.isLoading)
        assertEquals(
            item.id,
            refreshedState.libraryShelvesState.continueReading.single().item.id,
        )
        assertEquals(0.65f, refreshedState.libraryShelvesState.continueReading.single().progress.fraction)
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

        // The refreshed root presentation shows at once, then the recompute brings the new progress.
        assertEquals(1, backStates.size)
        assertTrue(backStates.single().libraryShelvesState.hasContent)
        assertFalse(backStates.single().libraryShelvesState.isLoading)
        assertEquals(0.45f, backStates.single().libraryShelvesState.continueReading.single().progress.fraction)

        advanceUntilIdle()

        assertEquals(2, backStates.size)
        val refreshedState = backStates.last()
        assertTrue(refreshedState.shouldReserveLibraryShelvesSlot)
        assertEquals(1, refreshedState.visibleItems.size)
        assertTrue(refreshedState.libraryShelvesState.hasContent)
        assertFalse(refreshedState.libraryShelvesState.isLoading)
        assertEquals(
            refreshedItem.id,
            refreshedState.libraryShelvesState.continueReading.single().item.id,
        )
        assertEquals(0.65f, refreshedState.libraryShelvesState.continueReading.single().progress.fraction)
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
    fun `seedColdStartShelves publishes DB-sourced shelves before the catalog hydrates`() = runTest {
        val item = ReadableItem(
            id = "kavita:item:1",
            sourceType = SourceType.KAVITA,
            libraryId = "lib",
            title = "A Book",
            format = FormatType.EPUB,
        )
        val dbShelvesInput = com.example.unifiedreader.feature.library.shelves.LibraryShelvesInput(
            selectedSource = SourceType.KAVITA,
            items = listOf(item),
            progressByItemId = mapOf(
                item.id to ItemProgressSummary(label = "Continue", fraction = 0.5f, lastReadAtEpochMillis = 100L),
            ),
        )
        val coordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(LibrarySeriesContinuationResolver()),
            shelvesInputResolver = { state -> dbShelvesInput.takeIf { state.selectedSource == SourceType.KAVITA } },
            appDispatchers = AppDispatchers(
                io = StandardTestDispatcher(testScheduler),
                default = StandardTestDispatcher(testScheduler),
            ),
        )
        // Pre-hydration: Kavita library landing, shelves shown, source not yet hydrated.
        coordinator.initialize(sampleLibraryState().copy(hydratedSources = emptySet()))

        val publishedStates = mutableListOf<AppUiState>()
        coordinator.seedColdStartShelves(scope = this, publishState = publishedStates::add)
        advanceUntilIdle()

        // Shelves are populated from the DB-sourced input without the catalog having hydrated.
        assertTrue(publishedStates.isNotEmpty())
        val shelves = publishedStates.last().libraryShelvesState
        assertTrue(shelves.hasContent)
        assertEquals(listOf(item.id), shelves.continueReading.map { entry -> entry.item.id })
    }

    @Test
    fun `switching servers shows each server's last presentation at once`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialState = coordinator.initialize(twoServerLibraryState())
        val grimmoryStates = mutableListOf<AppUiState>()

        // The first visit to Grimmory has nothing to show yet, so it computes as before.
        coordinator.updateState(
            currentState = initialState,
            transform = { state -> state.copy(selectedSource = SourceType.GRIMMORY) },
            scope = this,
            publishState = grimmoryStates::add,
        )
        advanceUntilIdle()
        val grimmoryState = grimmoryStates.last()
        assertEquals(listOf(grimmoryBook.id), grimmoryState.visibleItems.map { item -> item.id })
        val kavitaStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = grimmoryState,
            transform = { state -> state.copy(selectedSource = SourceType.KAVITA) },
            scope = this,
            publishState = kavitaStates::add,
        )

        // Kavita's last presentation is published in the same call, not after the recompute.
        assertEquals(1, kavitaStates.size)
        assertEquals(SourceType.KAVITA, kavitaStates.single().selectedSource)
        assertSame(initialState.visibleItems, kavitaStates.single().visibleItems)
        assertTrue(kavitaStates.single().libraryShelvesState.hasContent)
        assertFalse(kavitaStates.single().libraryShelvesState.isLoading)
        assertEquals(kavitaBook.id, kavitaStates.single().libraryShelvesState.continueReading.single().item.id)

        advanceUntilIdle()

        assertEquals(2, kavitaStates.size)
        assertEquals(listOf(kavitaBook.id), kavitaStates.last().visibleItems.map { item -> item.id })
        assertEquals(kavitaBook.id, kavitaStates.last().libraryShelvesState.continueReading.single().item.id)
        val backToGrimmoryStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = kavitaStates.last(),
            transform = { state -> state.copy(selectedSource = SourceType.GRIMMORY) },
            scope = this,
            publishState = backToGrimmoryStates::add,
        )

        // The same lists as Grimmory's last presentation: nothing was recomputed on the caller's thread.
        assertEquals(1, backToGrimmoryStates.size)
        assertSame(grimmoryState.visibleItems, backToGrimmoryStates.single().visibleItems)
        assertSame(grimmoryState.libraryShelvesState.continueReading, backToGrimmoryStates.single().libraryShelvesState.continueReading)

        // An unrelated update while that recompute runs doesn't compute it again on the caller's thread.
        coordinator.updateState(
            currentState = backToGrimmoryStates.single(),
            transform = { state -> state.copy(libraryScrollToTopRequest = state.libraryScrollToTopRequest + 1L) },
            scope = this,
            publishState = backToGrimmoryStates::add,
        )

        assertEquals(2, backToGrimmoryStates.size)
        assertSame(grimmoryState.visibleItems, backToGrimmoryStates.last().visibleItems)

        advanceUntilIdle()

        assertEquals(3, backToGrimmoryStates.size)
        assertEquals(SourceType.GRIMMORY, backToGrimmoryStates.last().selectedSource)
        assertEquals(listOf(grimmoryBook.id), backToGrimmoryStates.last().visibleItems.map { item -> item.id })
        assertEquals(grimmoryBook.id, backToGrimmoryStates.last().libraryShelvesState.continueReading.single().item.id)
    }

    @Test
    fun `closing the reader shows the library at once`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialState = coordinator.initialize(twoServerLibraryState())
        val readerStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = initialState,
            transform = { state -> state.copy(selectedItemId = kavitaBook.id, activeReader = FormatType.EPUB) },
            scope = this,
            publishState = readerStates::add,
        )
        coordinator.updateState(
            currentState = readerStates.last(),
            transform = { state ->
                state.copy(
                    progressByItemId = state.progressByItemId + (
                        kavitaBook.id to ItemProgressSummary(label = "Continue", fraction = 0.8f, lastReadAtEpochMillis = 90L)
                        ),
                )
            },
            scope = this,
            publishState = readerStates::add,
        )
        advanceUntilIdle()
        val closedStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = readerStates.last(),
            transform = { state -> state.copy(selectedItemId = null, activeReader = null) },
            scope = this,
            publishState = closedStates::add,
        )

        // The reader closes in the same call, over the library as it was when the book opened.
        assertEquals(1, closedStates.size)
        assertEquals(null, closedStates.single().activeReader)
        assertSame(initialState.visibleItems, closedStates.single().visibleItems)
        assertEquals(0.35f, closedStates.single().libraryShelvesState.continueReading.single().progress.fraction)

        advanceUntilIdle()

        assertEquals(2, closedStates.size)
        assertEquals(0.8f, closedStates.last().libraryShelvesState.continueReading.single().progress.fraction)
    }

    @Test
    fun `a last presentation showing a book no longer in the catalog waits for the recompute`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val initialState = coordinator.initialize(twoServerLibraryState())
        val grimmoryStates = mutableListOf<AppUiState>()
        coordinator.updateState(
            currentState = initialState,
            transform = { state -> state.copy(selectedSource = SourceType.GRIMMORY) },
            scope = this,
            publishState = grimmoryStates::add,
        )
        advanceUntilIdle()
        val otherKavitaBook = sampleItem(id = "book-2", title = "Other Book", format = FormatType.EPUB)
        val kavitaStates = mutableListOf<AppUiState>()

        coordinator.updateState(
            currentState = grimmoryStates.last(),
            transform = { state ->
                state.copy(
                    selectedSource = SourceType.KAVITA,
                    items = listOf(otherKavitaBook, grimmoryBook),
                    progressByItemId = mapOf(
                        otherKavitaBook.id to ItemProgressSummary(label = "Continue", fraction = 0.2f, lastReadAtEpochMillis = 70L),
                        grimmoryBook.id to state.progressByItemId.getValue(grimmoryBook.id),
                    ),
                )
            },
            scope = this,
            publishState = kavitaStates::add,
        )

        // Its shelves held book-1, which is gone: nothing stale is shown, the recompute is awaited.
        assertTrue(kavitaStates.isEmpty())

        advanceUntilIdle()

        assertEquals(1, kavitaStates.size)
        assertEquals(listOf(otherKavitaBook.id), kavitaStates.single().visibleItems.map { item -> item.id })
        assertEquals(otherKavitaBook.id, kavitaStates.single().libraryShelvesState.continueReading.single().item.id)
    }

    @Test
    fun `a quick recompute keeps the view's download badges until the full one lands`() = runTest {
        val coordinator = createCoordinator(StandardTestDispatcher(testScheduler))
        val epub = sampleItem(id = "book-1", title = "Book", format = FormatType.EPUB)
        val pdf = sampleItem(id = "pdf-1", title = "Paper", format = FormatType.PDF)
        val initialState = coordinator.initialize(sampleLibraryState().copy(items = listOf(epub, pdf)))
        // The cold-start compute is the quick one: the grid fills its badges itself until the full one.
        assertFalse(initialState.libraryHasAllBadges)
        val publishedStates = mutableListOf<AppUiState>()
        coordinator.updateState(
            currentState = initialState,
            transform = { state -> state.copy(downloadingRemoteItemIds = setOf(pdf.id)) },
            scope = this,
            publishState = publishedStates::add,
        )
        advanceUntilIdle()
        val fullState = publishedStates.last()
        assertTrue(fullState.libraryHasAllBadges)
        assertEquals(setOf(pdf.id), fullState.remoteDownloadActionAvailability.activeDownloadingItemIds)

        coordinator.updateState(
            currentState = fullState,
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

        val quickState = publishedStates.last()
        assertEquals(listOf(pdf.id), quickState.visibleItems.map { item -> item.id })
        assertTrue(quickState.libraryHasAllBadges)
        assertSame(fullState.remoteDownloadActionAvailability, quickState.remoteDownloadActionAvailability)
    }

    private val kavitaBook = sampleItem(id = "book-1", title = "Kavita Book", format = FormatType.EPUB)

    private val grimmoryBook = ReadableItem(
        id = "grimmory:book:1",
        sourceType = SourceType.GRIMMORY,
        libraryId = SourceType.GRIMMORY.remoteLibraryId("g-books"),
        remoteId = "1",
        title = "Grimmory Book",
        format = FormatType.EPUB,
    )

    private fun twoServerLibraryState(): AppUiState = sampleLibraryState().copy(
        libraries = sampleLibraryState().libraries + Library(
            id = SourceType.GRIMMORY.remoteLibraryId("g-books"),
            sourceType = SourceType.GRIMMORY,
            title = "Books",
            remoteId = "g-books",
            contentType = LibraryContentType.BOOKS,
        ),
        items = listOf(kavitaBook, grimmoryBook),
        progressByItemId = mapOf(
            kavitaBook.id to ItemProgressSummary(label = "Continue", fraction = 0.35f, lastReadAtEpochMillis = 25L),
            grimmoryBook.id to ItemProgressSummary(label = "Continue", fraction = 0.5f, lastReadAtEpochMillis = 40L),
        ),
        hydratedSources = setOf(SourceType.KAVITA, SourceType.GRIMMORY),
    )

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
