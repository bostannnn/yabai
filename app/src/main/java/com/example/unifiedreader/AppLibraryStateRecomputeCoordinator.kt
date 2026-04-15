package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
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

    fun initialize(
        baseState: AppUiState,
    ): AppUiState {
        val derivedState = libraryDerivedStateComputer.computeQuick(baseState.toLibraryDerivedStateInput())
        appliedDerivedSignature = baseState.libraryDerivedStateSignature()
        appliedDerivedState = derivedState
        appliedShelvesSignature = null
        appliedShelvesState = null
        return baseState
            .withLibraryDerivedState(derivedState)
            .withLibraryShelvesState(LibraryShelvesState())
            .also { initializedState ->
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
        val derivedSignature = nextBaseState.libraryDerivedStateSignature()
        val shelvesSignature = nextBaseState.libraryShelvesStateSignature()
        val shouldComputeDerived = derivedSignature != appliedDerivedSignature
        val shouldComputeShelves = nextBaseState.shouldReserveLibraryShelvesSlot &&
            shelvesSignature != appliedShelvesSignature
        val projectedState = if (shouldComputeDerived) {
            nextBaseState.withLibraryDerivedState(
                libraryDerivedStateComputer.computeQuick(nextBaseState.toLibraryDerivedStateInput()),
            )
        } else {
            nextBaseState
        }
        val nextState = mergeAppliedLibraryState(projectedState)
        latestState = nextState
        publishState(nextState)
        if (!shouldComputeDerived && !shouldComputeShelves) {
            pendingRecomputeJob?.cancel()
            pendingRecomputeJob = null
            pendingDerivedSignature = null
            pendingShelvesSignature = null
            return
        }
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
            val mergedState = mergeAppliedLibraryState(latest)
            latestState = mergedState
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
    val downloadedRemoteItemIds: IdentityBox<Set<*>>,
    val locallyAvailableRemoteItemIds: IdentityBox<Set<*>>,
    val downloadingRemoteItemIds: IdentityBox<Set<*>>,
    val selectedKavitaSeriesKey: String?,
    val selectedStumpSeriesKey: String?,
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
    downloadedRemoteItemIds = IdentityBox(downloadedRemoteItemIds),
    locallyAvailableRemoteItemIds = IdentityBox(locallyAvailableRemoteItemIds),
    downloadingRemoteItemIds = IdentityBox(downloadingRemoteItemIds),
    selectedKavitaSeriesKey = selectedKavitaSeriesKey,
    selectedStumpSeriesKey = selectedStumpSeriesKey,
)

private fun AppUiState.libraryShelvesStateSignature(): AppLibraryShelvesStateSignature = AppLibraryShelvesStateSignature(
    selectedSource = selectedSource,
    items = IdentityBox(items),
    progressByItemId = IdentityBox(progressByItemId),
)

private class IdentityBox<T : Any?>(
    private val value: T,
) {
    override fun equals(
        other: Any?,
    ): Boolean = other is IdentityBox<*> && value === other.value

    override fun hashCode(): Int = System.identityHashCode(value)
}
