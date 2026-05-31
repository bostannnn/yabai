package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryDerivedState
import com.example.unifiedreader.feature.library.LibraryDerivedStateComputer
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesState
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesStateComputer
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ViewModelScoped
class AppLibraryStateRecomputeCoordinator @Inject constructor(
    private val libraryDerivedStateComputer: LibraryDerivedStateComputer,
    private val libraryShelvesStateComputer: LibraryShelvesStateComputer,
    private val appDispatchers: AppDispatchers,
) {
    private var latestState: AppUiState? = null
    private var appliedDerivedSignature: AppLibraryDerivedStateSignature? = null
    private var appliedDerivedState: LibraryDerivedState? = null
    private var appliedShelvesSignature: AppLibraryShelvesStateSignature? = null
    private var appliedShelvesState: LibraryShelvesState? = null
    private var pendingDerivedSignature: AppLibraryDerivedStateSignature? = null
    private var pendingShelvesSignature: AppLibraryShelvesStateSignature? = null
    private var pendingRecomputeJob: Job? = null
    private val retainedRootShelvesBySource =
        object : LinkedHashMap<SourceType, LibraryShelvesState>(RETAINED_ROOT_SHELVES_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<SourceType, LibraryShelvesState>,
            ): Boolean = size > RETAINED_ROOT_SHELVES_LIMIT
        }

    fun initialize(
        baseState: AppUiState,
    ): AppUiState {
        val derivedState = libraryDerivedStateComputer.computeQuick(baseState.toLibraryDerivedStateInput())
        val projectedState = baseState.withLibraryDerivedState(derivedState)
        val shelvesSignature = projectedState.libraryShelvesStateSignature()
        val shelvesState = if (projectedState.shouldReserveLibraryShelvesSlot) {
            libraryShelvesStateComputer.compute(projectedState.toLibraryShelvesInput())
        } else {
            LibraryShelvesState()
        }
        appliedDerivedSignature = baseState.libraryDerivedStateSignature()
        appliedDerivedState = derivedState
        appliedShelvesSignature = shelvesSignature.takeIf { projectedState.shouldReserveLibraryShelvesSlot }
        appliedShelvesState = shelvesState.takeIf { projectedState.shouldReserveLibraryShelvesSlot }
        return projectedState
            .withLibraryShelvesState(shelvesState)
            .also { initializedState ->
                retainRootShelves(initializedState)
                latestState = initializedState
            }
    }

    fun updateState(
        currentState: AppUiState,
        transform: (AppUiState) -> AppUiState,
        scope: CoroutineScope,
        publishState: (AppUiState) -> Unit,
    ) {
        val nextBaseState = transform(currentState)
        latestState = nextBaseState
        val isLibraryPresentationActive = nextBaseState.topLevel == TopLevelDestination.Library
        val derivedSignature = nextBaseState.libraryDerivedStateSignature()
        val shelvesSignature = nextBaseState.libraryShelvesStateSignature()
        val shouldComputeDerived = isLibraryPresentationActive &&
            derivedSignature != appliedDerivedSignature
        val shouldComputeShelves = isLibraryPresentationActive &&
            nextBaseState.shouldReserveLibraryShelvesSlot &&
            shelvesSignature != appliedShelvesSignature
        val projectedState = if (shouldComputeDerived) {
            nextBaseState.withLibraryDerivedState(
                libraryDerivedStateComputer.computeQuick(nextBaseState.toLibraryDerivedStateInput()),
            )
        } else {
            nextBaseState
        }
        val retainedShelvesState = retainedShelvesStateFor(projectedState, shelvesSignature)
        val nextState = presentShelvesState(
            state = mergeAppliedLibraryState(projectedState),
            isLoading = nextBaseState.shouldReserveLibraryShelvesSlot &&
                (!nextBaseState.isSelectedSourceHydrated || shouldComputeShelves),
            retainedShelvesState = retainedShelvesState,
        )
        if (!shouldComputeDerived && !shouldComputeShelves) {
            pendingRecomputeJob?.cancel()
            pendingRecomputeJob = null
            pendingDerivedSignature = null
            pendingShelvesSignature = null
            val stableState = preserveCurrentLibraryPresentation(
                state = nextBaseState,
                currentState = currentState,
            )
            latestState = stableState
            retainRootShelves(stableState)
            publishState(stableState)
            return
        }
        latestState = nextState
        retainRootShelves(nextState)
        publishState(nextState)
        if (
            pendingDerivedSignature == derivedSignature.takeIf { shouldComputeDerived } &&
            pendingShelvesSignature == shelvesSignature.takeIf { shouldComputeShelves }
        ) {
            return
        }
        pendingRecomputeJob?.cancel()
        pendingDerivedSignature = derivedSignature.takeIf { shouldComputeDerived }
        pendingShelvesSignature = shelvesSignature.takeIf { shouldComputeShelves }
        var recomputeJob: Job? = null
        recomputeJob = scope.launch {
            val computedState = withContext(appDispatchers.default) {
                AppLibraryComputedState(
                    derivedState = if (shouldComputeDerived) {
                        libraryDerivedStateComputer.compute(nextBaseState.toLibraryDerivedStateInput())
                    } else {
                        null
                    },
                    shelvesState = if (shouldComputeShelves) {
                        libraryShelvesStateComputer.compute(nextBaseState.toLibraryShelvesInput())
                    } else {
                        null
                    },
                )
            }
            if (pendingRecomputeJob === recomputeJob) {
                pendingRecomputeJob = null
                pendingDerivedSignature = null
                pendingShelvesSignature = null
            }
            val latest = latestState ?: return@launch
            if (shouldComputeDerived && latest.libraryDerivedStateSignature() == derivedSignature) {
                appliedDerivedSignature = derivedSignature
                appliedDerivedState = computedState.derivedState
            }
            if (shouldComputeShelves && latest.libraryShelvesStateSignature() == shelvesSignature) {
                appliedShelvesSignature = shelvesSignature
                appliedShelvesState = computedState.shelvesState
            }
            val mergedState = presentShelvesState(
                state = mergeAppliedLibraryState(latest),
                isLoading = latest.shouldReserveLibraryShelvesSlot &&
                    (!latest.isSelectedSourceHydrated || pendingShelvesSignature != null),
                retainedShelvesState = retainedShelvesStateFor(latest, latest.libraryShelvesStateSignature()),
            )
            latestState = mergedState
            retainRootShelves(mergedState)
            publishState(mergedState)
        }
        pendingRecomputeJob = recomputeJob
    }

    private fun mergeAppliedLibraryState(
        state: AppUiState,
    ): AppUiState {
        var mergedState = state
        if (state.libraryDerivedStateSignature() == appliedDerivedSignature) {
            appliedDerivedState?.let { derivedState ->
                mergedState = mergedState.withLibraryDerivedState(derivedState)
            }
        }
        if (state.libraryShelvesStateSignature() == appliedShelvesSignature) {
            appliedShelvesState?.let { shelvesState ->
                mergedState = mergedState.withLibraryShelvesState(shelvesState)
            }
        }
        return mergedState
    }

    private fun presentShelvesState(
        state: AppUiState,
        isLoading: Boolean,
        retainedShelvesState: LibraryShelvesState? = null,
    ): AppUiState {
        val shelvesState = when {
            !state.shouldReserveLibraryShelvesSlot -> LibraryShelvesState()
            isLoading -> state.libraryShelvesState
                .takeIf(LibraryShelvesState::hasContent)
                ?.asLoading()
                ?: retainedShelvesState
                ?: state.libraryShelvesState
                .asLoading()
            else -> state.libraryShelvesState.ready()
        }
        return state.withLibraryShelvesState(shelvesState)
    }

    private fun retainedShelvesStateFor(
        state: AppUiState,
        shelvesSignature: AppLibraryShelvesStateSignature,
    ): LibraryShelvesState? {
        retainedAppliedShelvesStateFor(shelvesSignature)?.let { shelvesState ->
            return shelvesState
        }
        return retainedRootShelvesBySource[state.selectedSource]
            ?.takeIf { shelvesState -> shelvesState.hasOnlyCurrentItems(state) }
            ?.asLoading()
    }

    private fun retainedAppliedShelvesStateFor(
        shelvesSignature: AppLibraryShelvesStateSignature,
    ): LibraryShelvesState? {
        val appliedSignature = appliedShelvesSignature ?: return null
        if (appliedSignature.selectedSource != shelvesSignature.selectedSource) return null
        if (appliedSignature.items != shelvesSignature.items) return null
        return appliedShelvesState
            ?.takeIf(LibraryShelvesState::hasContent)
            ?.asLoading()
    }

    private fun retainRootShelves(
        state: AppUiState,
    ) {
        if (!state.shouldReserveLibraryShelvesSlot) return
        val shelvesState = state.libraryShelvesState
            .takeIf(LibraryShelvesState::hasContent)
            ?.ready()
            ?: return
        retainedRootShelvesBySource[state.selectedSource] = shelvesState
    }

    private fun preserveCurrentLibraryPresentation(
        state: AppUiState,
        currentState: AppUiState,
    ): AppUiState = state.copy(
        sourceItems = currentState.sourceItems,
        selectedLibraryFilterFacets = currentState.selectedLibraryFilterFacets,
        selectedSourceLibraryTitlesById = currentState.selectedSourceLibraryTitlesById,
        displayProgressByItemId = currentState.displayProgressByItemId,
        displayRatingByItemId = currentState.displayRatingByItemId,
        remoteDownloadActionAvailability = currentState.remoteDownloadActionAvailability,
        visibleItems = currentState.visibleItems,
        libraryBrowseContextKey = currentState.libraryBrowseContextKey,
        libraryShelvesState = currentState.libraryShelvesState,
    )
}

private data class AppLibraryComputedState(
    val derivedState: LibraryDerivedState?,
    val shelvesState: LibraryShelvesState?,
)

private data class AppLibraryDerivedStateSignature(
    val selectedSource: com.example.unifiedreader.core.model.SourceType,
    val libraries: IdentityBox<List<*>>,
    val items: IdentityBox<List<*>>,
    val selectedLibraryFilters: com.example.unifiedreader.core.model.LibraryFilterState,
    val selectedLibrarySearchQuery: String,
    val selectedLibrarySortMode: com.example.unifiedreader.core.model.LibrarySortMode,
    val progressByItemId: IdentityBox<Map<*, *>>,
    val journalEntryByItemId: IdentityBox<Map<*, *>>,
    val downloadedRemoteItemIds: IdentityBox<Set<*>>,
    val locallyAvailableRemoteItemIds: IdentityBox<Set<*>>,
    val downloadingRemoteItemIds: IdentityBox<Set<*>>,
    val selectedKavitaSeriesKey: String?,
    val selectedStumpSeriesKey: String?,
    val selectedKavitaReadingListId: String?,
    val selectedKavitaReadingListItemIds: List<String>,
    val selectedKavitaCollectionId: String?,
    val selectedKavitaCollectionSeriesKeys: Set<String>,
)

private data class AppLibraryShelvesStateSignature(
    val selectedSource: com.example.unifiedreader.core.model.SourceType,
    val items: IdentityBox<List<*>>,
    val progressByItemId: IdentityBox<Map<*, *>>,
)

private fun AppUiState.libraryDerivedStateSignature(): AppLibraryDerivedStateSignature = AppLibraryDerivedStateSignature(
    selectedSource = selectedSource,
    libraries = IdentityBox(libraries),
    items = IdentityBox(items),
    selectedLibraryFilters = selectedLibraryFilters,
    selectedLibrarySearchQuery = selectedLibrarySearchQuery,
    selectedLibrarySortMode = selectedLibrarySortMode,
    progressByItemId = IdentityBox(progressByItemId),
    journalEntryByItemId = IdentityBox(journalSnapshot.entryByItemId),
    downloadedRemoteItemIds = IdentityBox(downloadedRemoteItemIds),
    locallyAvailableRemoteItemIds = IdentityBox(locallyAvailableRemoteItemIds),
    downloadingRemoteItemIds = IdentityBox(downloadingRemoteItemIds),
    selectedKavitaSeriesKey = selectedKavitaSeriesKey,
    selectedStumpSeriesKey = selectedStumpSeriesKey,
    selectedKavitaReadingListId = kavitaReadingLists.selectedReadingListId,
    selectedKavitaReadingListItemIds = selectedKavitaReadingListItemIds,
    selectedKavitaCollectionId = kavitaReadingLists.selectedCollectionId,
    selectedKavitaCollectionSeriesKeys = selectedKavitaCollectionSeriesKeys,
)

private fun AppUiState.libraryShelvesStateSignature(): AppLibraryShelvesStateSignature = AppLibraryShelvesStateSignature(
    selectedSource = selectedSource,
    items = IdentityBox(items),
    progressByItemId = IdentityBox(progressByItemId),
)

private fun LibraryShelvesState.hasOnlyCurrentItems(
    state: AppUiState,
): Boolean {
    val currentItemIds = state.items.mapTo(hashSetOf(), com.example.unifiedreader.core.model.ReadableItem::id)
    return allItemIds().all(currentItemIds::contains)
}

private fun LibraryShelvesState.allItemIds(): List<String> = buildList {
    continueReading.forEach { item -> add(item.item.id) }
    nextInSeries.forEach { item ->
        add(item.item.id)
        add(item.currentItem.id)
    }
    recentlyFinished.forEach { item -> add(item.item.id) }
}

private class IdentityBox<T : Any?>(
    private val value: T,
) {
    override fun equals(
        other: Any?,
    ): Boolean = other is IdentityBox<*> && value === other.value

    override fun hashCode(): Int = System.identityHashCode(value)
}

private const val RETAINED_ROOT_SHELVES_LIMIT = 4
