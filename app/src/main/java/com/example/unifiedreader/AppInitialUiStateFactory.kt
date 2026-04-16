package com.example.unifiedreader

import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class AppInitialUiStateFactory @Inject constructor(
    private val navigationController: AppNavigationController,
    private val appStateHydrator: AppStateHydrator,
    private val catalogSnapshotsUseCase: ObserveAppCatalogSnapshotsUseCase,
    private val libraryStateRecomputeCoordinator: AppLibraryStateRecomputeCoordinator,
) {
    fun create(): AppUiState = runBlocking {
        appStateHydrator.hydrate()
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
            hydratedSources = snapshot.hydratedSources,
            themeMode = snapshot.themeMode,
        )
        libraryStateRecomputeCoordinator.initialize(baseState)
    }
}
