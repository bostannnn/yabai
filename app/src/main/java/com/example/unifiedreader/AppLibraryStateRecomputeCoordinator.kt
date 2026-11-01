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
import kotlinx.coroutines.launch

@ViewModelScoped
class AppLibraryStateRecomputeCoordinator @Inject constructor(
    private val libraryDerivedStateComputer: LibraryDerivedStateComputer,
    private val libraryShelvesStateComputer: LibraryShelvesStateComputer,
    // Hilt injects the real (DB-sourced) resolver; the no-op default keeps the ~dozen manual test
    // constructions on the in-memory shelves path unchanged.
    private val shelvesInputResolver: LibraryShelvesInputResolver = LibraryShelvesInputResolver { null },
    private val appDispatchers: AppDispatchers,
) {
    private var latestState: AppUiState? = null
    private var appliedDerivedSignature: AppLibraryDerivedStateSignature? = null
    private var appliedDerivedState: LibraryDerivedState? = null
    private var appliedShelvesSignature: AppLibraryShelvesStateSignature? = null
    private var appliedShelvesState: LibraryShelvesState? = null
    private val pendingRecompute = AppLibraryPendingRecompute(
        appDispatchers = appDispatchers,
        computeDerivedState = { state ->
            libraryDerivedStateComputer.compute(state.toLibraryDerivedStateInput())
        },
        computeShelvesState = { state ->
            // Prefer the DB-sourced input (Kavita) so shelves don't wait for the full catalog to
            // hydrate; fall back to the in-memory input for other sources.
            libraryShelvesStateComputer.compute(
                shelvesInputResolver.resolve(state) ?: state.toLibraryShelvesInput(),
            )
        },
    )
    private val retainedPresentations = AppLibraryRetainedPresentations()

    fun initialize(
        baseState: AppUiState,
    ): AppUiState {
        val derivedState = libraryDerivedStateComputer.computeQuick(baseState.toLibraryDerivedStateInput())
        val projectedState = baseState.withLibraryDerivedState(derivedState)
        val shelvesSignature = projectedState.libraryShelvesStateSignature()
        val computedShelvesState = if (projectedState.shouldReserveLibraryShelvesSlot) {
            libraryShelvesStateComputer.compute(projectedState.toLibraryShelvesInput())
        } else {
            LibraryShelvesState()
        }
        val stateWithShelves = projectedState.withLibraryShelvesState(computedShelvesState)
        val initializedState = presentShelvesState(
            state = stateWithShelves,
            isLoading = stateWithShelves.shouldShowLibraryShelvesLoading,
        )
        appliedDerivedSignature = baseState.libraryDerivedStateSignature()
        appliedDerivedState = derivedState
        appliedShelvesSignature = shelvesSignature.takeIf { projectedState.shouldReserveLibraryShelvesSlot }
        appliedShelvesState = computedShelvesState.takeIf { projectedState.shouldReserveLibraryShelvesSlot }
        return initializedState.also { state ->
            retainedPresentations.retain(state)
            latestState = state
        }
    }

    /**
     * Computes the shelves from the DB-sourced input (Kavita) at cold start and publishes them, so the
     * shelves don't sit in the loading state for the ~seconds it takes the full catalog to hydrate into
     * memory. No-op when there is no DB-sourced input, when shelves aren't shown, when the DB has no
     * shelf content, or once the catalog has already hydrated (the signature-driven recompute owns
     * shelves from then on).
     */
    fun seedColdStartShelves(
        scope: CoroutineScope,
        publishState: (AppUiState) -> Unit,
    ) {
        val state = latestState ?: return
        if (!state.shouldReserveLibraryShelvesSlot || state.isSelectedSourceHydrated) return
        scope.launch {
            val input = shelvesInputResolver.resolve(state) ?: return@launch
            val shelvesState = libraryShelvesStateComputer.compute(input)
            if (!shelvesState.hasContent) return@launch
            val latest = latestState ?: return@launch
            // Once the catalog hydrates, the signature-driven recompute owns shelves — don't fight it.
            if (latest.isSelectedSourceHydrated ||
                !latest.shouldReserveLibraryShelvesSlot ||
                latest.selectedSource != state.selectedSource
            ) {
                return@launch
            }
            // Key to the current state's signature so the merge below applies the seeded shelves, and
            // pre-hydration updates (same signature) keep showing them instead of the empty placeholder.
            appliedShelvesSignature = latest.libraryShelvesStateSignature()
            appliedShelvesState = shelvesState
            val mergedState = mergeAppliedLibraryState(latest)
            val nextState = presentShelvesState(
                state = mergedState,
                isLoading = mergedState.shouldShowLibraryShelvesLoading,
            )
            latestState = nextState
            retainedPresentations.retain(nextState)
            publishState(nextState)
        }
    }

    fun updateState(
        currentState: AppUiState,
        transform: (AppUiState) -> AppUiState,
        scope: CoroutineScope,
        publishState: (AppUiState) -> Unit,
    ) {
        val previousState = latestState ?: currentState
        val nextBaseState = transform(previousState)
        latestState = nextBaseState
        // Don't recompute the (hidden) library grid/shelves while a reader is open over the library
        // tab. It stays topLevel == Library, so without this guard every progress/catalog update
        // during reading re-derives the whole catalog behind the reader. The current presentation is
        // preserved and a single recompute catches up when the reader closes.
        val isLibraryPresentationActive = nextBaseState.isLibraryPresentationActive
        val derivedSignature = nextBaseState.libraryDerivedStateSignature()
        val shelvesSignature = nextBaseState.libraryShelvesStateSignature()
        val shouldComputeDerived = isLibraryPresentationActive &&
            derivedSignature != appliedDerivedSignature
        val shouldComputeShelves = isLibraryPresentationActive &&
            nextBaseState.shouldReserveLibraryShelvesSlot &&
            shelvesSignature != appliedShelvesSignature
        if (!shouldComputeDerived && !shouldComputeShelves) {
            pendingRecompute.cancel()
            val stablePresentationState = preserveCurrentLibraryPresentation(
                state = nextBaseState,
                currentState = currentState,
            )
            val stableState = presentShelvesState(
                state = stablePresentationState,
                isLoading = stablePresentationState.shouldShowLibraryShelvesLoading,
            )
            latestState = stableState
            retainedPresentations.retain(stableState)
            publishState(stableState)
            return
        }
        // Opening a view (another server, back from a series, the reader closed, back from another tab)
        // shows the view's last finished presentation at once and recomputes it in the background, so
        // the tap is answered in the same frame instead of after the recompute (Kavita) or behind one
        // on the main thread (the other servers). Data updates within a view still wait for a coherent
        // presentation below.
        if (nextBaseState.opensLibraryViewFrom(previousState)) {
            retainedPresentations.presentationFor(nextBaseState)?.let { retainedPresentation ->
                val retainedState = mergeAppliedLibraryState(nextBaseState.withLibraryPresentation(retainedPresentation))
                val openedState = presentShelvesState(
                    state = retainedState,
                    isLoading = retainedState.shouldShowLibraryShelvesLoading,
                )
                latestState = openedState
                publishState(openedState)
                scheduleLibraryRecompute(
                    baseState = nextBaseState,
                    derivedSignature = derivedSignature,
                    shelvesSignature = shelvesSignature,
                    shouldComputeDerived = shouldComputeDerived,
                    shouldComputeShelves = shouldComputeShelves,
                    scope = scope,
                    publishState = publishState,
                )
                return
            }
        }
        // The other servers compute on the main thread below. When the recompute already running covers
        // these inputs (a view opened above, or the last update's), show this update over the current
        // presentation instead of computing the same thing again.
        if (nextBaseState.selectedSource != SourceType.KAVITA &&
            pendingRecompute.covers(
                derivedKey = derivedSignature.takeIf { shouldComputeDerived },
                shelvesKey = shelvesSignature.takeIf { shouldComputeShelves },
            )
        ) {
            val waitingPresentationState = preserveCurrentLibraryPresentation(
                state = nextBaseState,
                currentState = previousState,
            )
            val waitingState = presentShelvesState(
                state = waitingPresentationState,
                isLoading = waitingPresentationState.shouldShowLibraryShelvesLoading,
            )
            latestState = waitingState
            publishState(waitingState)
            return
        }
        if (shouldComputeShelves && nextBaseState.selectedSource == SourceType.KAVITA) {
            scheduleLibraryRecompute(
                baseState = nextBaseState,
                derivedSignature = derivedSignature,
                shelvesSignature = shelvesSignature,
                shouldComputeDerived = shouldComputeDerived,
                shouldComputeShelves = true,
                scope = scope,
                publishState = publishState,
            )
            return
        }
        if (shouldComputeShelves) {
            appliedShelvesSignature = shelvesSignature
            appliedShelvesState = libraryShelvesStateComputer.compute(nextBaseState.toLibraryShelvesInput())
        }
        val projectedState = if (shouldComputeDerived) {
            nextBaseState.withQuickLibraryDerivedState(
                derivedState = libraryDerivedStateComputer.computeQuick(nextBaseState.toLibraryDerivedStateInput()),
                previousState = previousState,
            )
        } else {
            nextBaseState
        }
        val mergedProjectedState = mergeAppliedLibraryState(projectedState)
        val retainedShelvesState = retainedShelvesStateFor(mergedProjectedState, shelvesSignature)
        val nextState = presentShelvesState(
            state = mergedProjectedState,
            isLoading = mergedProjectedState.shouldShowLibraryShelvesLoading,
            retainedShelvesState = retainedShelvesState,
        )
        latestState = nextState
        retainedPresentations.retain(nextState)
        publishState(nextState)
        if (!shouldComputeDerived) {
            pendingRecompute.cancel()
            return
        }
        scheduleLibraryRecompute(
            baseState = nextBaseState,
            derivedSignature = derivedSignature,
            shelvesSignature = shelvesSignature,
            shouldComputeDerived = true,
            shouldComputeShelves = false,
            scope = scope,
            publishState = publishState,
        )
    }

    private fun scheduleLibraryRecompute(
        baseState: AppUiState,
        derivedSignature: AppLibraryDerivedStateSignature,
        shelvesSignature: AppLibraryShelvesStateSignature,
        shouldComputeDerived: Boolean,
        shouldComputeShelves: Boolean,
        scope: CoroutineScope,
        publishState: (AppUiState) -> Unit,
    ) {
        pendingRecompute.schedule(
            baseState = baseState,
            derivedKey = derivedSignature,
            shelvesKey = shelvesSignature,
            shouldComputeDerived = shouldComputeDerived,
            shouldComputeShelves = shouldComputeShelves,
            scope = scope,
            onComputed = onComputed@{ computedState ->
                val latest = latestState ?: return@onComputed
                if (shouldComputeDerived && latest.libraryDerivedStateSignature() == derivedSignature) {
                    appliedDerivedSignature = derivedSignature
                    appliedDerivedState = computedState.derivedState
                }
                if (shouldComputeShelves && latest.libraryShelvesStateSignature() == shelvesSignature) {
                    appliedShelvesSignature = shelvesSignature
                    appliedShelvesState = computedState.shelvesState
                }
                val latestWithAppliedState = mergeAppliedLibraryState(latest)
                val mergedState = presentShelvesState(
                    state = latestWithAppliedState,
                    isLoading = latestWithAppliedState.shouldShowLibraryShelvesLoading,
                    retainedShelvesState = retainedShelvesStateFor(
                        latestWithAppliedState,
                        latestWithAppliedState.libraryShelvesStateSignature(),
                    ),
                )
                latestState = mergedState
                retainedPresentations.retain(mergedState)
                publishState(mergedState)
            },
        )
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
        return retainedPresentations.loadingRootShelvesFor(state)
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

    private fun preserveCurrentLibraryPresentation(
        state: AppUiState,
        currentState: AppUiState,
    ): AppUiState = state.withLibraryPresentation(currentState.libraryPresentation())
}

private data class AppLibraryDerivedStateSignature(
    val selectedSource: com.example.unifiedreader.core.model.SourceType,
    val libraries: IdentityBox<List<*>>,
    val items: IdentityBox<List<*>>,
    val selectedLibraryFilters: com.example.unifiedreader.core.model.LibraryFilterState,
    val selectedLibrarySearchQuery: String,
    val selectedLibrarySortMode: com.example.unifiedreader.core.model.LibrarySortMode,
    // Compared by content (not instance identity): these small maps/sets are frequently rebuilt with
    // identical content (e.g. a new JournalSnapshot on every reading event rebuilds entryByItemId),
    // and identity comparison would force a full catalog recompute for a change that isn't there.
    val progressByItemId: Map<*, *>,
    val journalEntryByItemId: Map<*, *>,
    val downloadedRemoteItemIds: Set<*>,
    val locallyAvailableRemoteItemIds: Set<*>,
    val downloadingRemoteItemIds: Set<*>,
    val selectedKavitaSeriesKey: String?,
    val selectedStumpSeriesKey: String?,
)

private data class AppLibraryShelvesStateSignature(
    val selectedSource: com.example.unifiedreader.core.model.SourceType,
    val items: IdentityBox<List<*>>,
    val progressByItemId: Map<*, *>,
)

private fun AppUiState.libraryDerivedStateSignature(): AppLibraryDerivedStateSignature = AppLibraryDerivedStateSignature(
    selectedSource = selectedSource,
    libraries = IdentityBox(libraries),
    items = IdentityBox(items),
    selectedLibraryFilters = selectedLibraryFilters,
    selectedLibrarySearchQuery = selectedLibrarySearchQuery,
    selectedLibrarySortMode = selectedLibrarySortMode,
    progressByItemId = progressByItemId,
    journalEntryByItemId = journalSnapshot.entryByItemId,
    downloadedRemoteItemIds = downloadedRemoteItemIds,
    locallyAvailableRemoteItemIds = locallyAvailableRemoteItemIds,
    downloadingRemoteItemIds = downloadingRemoteItemIds,
    selectedKavitaSeriesKey = selectedKavitaSeriesKey,
    selectedStumpSeriesKey = selectedStumpSeriesKey,
)

private fun AppUiState.libraryShelvesStateSignature(): AppLibraryShelvesStateSignature = AppLibraryShelvesStateSignature(
    selectedSource = selectedSource,
    items = IdentityBox(items),
    progressByItemId = progressByItemId,
)

private val AppUiState.shouldShowLibraryShelvesLoading: Boolean
    get() = shouldReserveLibraryShelvesSlot &&
        !isSelectedSourceHydrated &&
        !libraryShelvesState.hasContent

private class IdentityBox<T : Any?>(
    private val value: T,
) {
    override fun equals(
        other: Any?,
    ): Boolean = other is IdentityBox<*> && value === other.value

    override fun hashCode(): Int = System.identityHashCode(value)
}

