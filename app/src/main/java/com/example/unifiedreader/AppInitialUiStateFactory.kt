package com.example.unifiedreader

import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class AppInitialUiStateFactory @Inject constructor(
    private val navigationController: AppNavigationController,
    private val activeRouteStore: AppActiveRouteStore,
    private val appStateHydrator: AppStateHydrator,
    private val catalogSnapshotsUseCase: ObserveAppCatalogSnapshotsUseCase,
    private val libraryStateRecomputeCoordinator: AppLibraryStateRecomputeCoordinator,
) {
    fun create(): AppUiState = runBlocking {
        val navigationState = navigationController.initialState()
        val snapshot = catalogSnapshotsUseCase.currentSnapshot()
        val baseState = AppUiState(
            sourceOrder = navigationState.sourceOrder,
            selectedSource = navigationState.startupSource,
            libraries = snapshot.libraries,
            items = snapshot.items,
            progressByItemId = snapshot.progressByItemId,
            downloadedRemoteItemIds = snapshot.downloadedRemoteItemIds,
            locallyAvailableRemoteItemIds = snapshot.locallyAvailableRemoteItemIds,
            remoteItemLocalStateItemIds = snapshot.items.remoteItemLocalStateScopeItemIds(),
            remoteItemLocalAvailabilityScopeIds = snapshot.items.remoteItemLocalAvailabilityScopeIds(),
            remoteItemLocalStateKnownSourceLibraryScopeIds = snapshot.remoteItemLocalStateKnownSourceLibraryScopeIds,
            isRemoteItemLocalStateKnown = snapshot.remoteItemLocalStateKnownSourceLibraryScopeIds.containsAll(
                snapshot.items.remoteItemLocalStateSourceLibraryScopeIds(),
            ),
            isRemoteItemLocalStateRefreshing = !snapshot.remoteItemLocalStateKnownSourceLibraryScopeIds.containsAll(
                snapshot.items.remoteItemLocalStateSourceLibraryScopeIds(),
            ),
            hydratedSources = snapshot.hydratedSources,
            themeMode = snapshot.themeMode,
        )
        val restoredRouteState = activeRouteStore.restore()
            ?.let(baseState::withActiveRouteSnapshot)
            ?: baseState
        libraryStateRecomputeCoordinator.initialize(restoredRouteState)
    }

    suspend fun hydrateAfterInitialState() {
        appStateHydrator.hydrate()
    }
}
