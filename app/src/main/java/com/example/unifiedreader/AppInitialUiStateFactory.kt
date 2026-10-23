package com.example.unifiedreader

import javax.inject.Inject

class AppInitialUiStateFactory @Inject constructor(
    private val navigationController: AppNavigationController,
    private val activeRouteStore: AppActiveRouteStore,
    private val appStateHydrator: AppStateHydrator,
    private val catalogSnapshotsUseCase: ObserveAppCatalogSnapshotsUseCase,
    private val libraryStateRecomputeCoordinator: AppLibraryStateRecomputeCoordinator,
) {
    fun create(): AppUiState {
        val navigationState = navigationController.initialState()
        val activeRouteSnapshot = activeRouteStore.restore()
        val snapshot = catalogSnapshotsUseCase.initialSnapshot()
        val baseState = AppUiState(
            sourceOrder = navigationState.sourceOrder,
            lastConnectedSources = navigationState.lastConnectedSources,
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
        val restoredRouteState = activeRouteSnapshot
            ?.let(baseState::withActiveRouteSnapshot)
            ?: baseState
        return libraryStateRecomputeCoordinator.initialize(restoredRouteState)
    }

    suspend fun hydrateAfterInitialState() {
        appStateHydrator.hydrate()
    }
}
