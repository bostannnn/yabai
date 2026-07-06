package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.navigation.SourceNavigationState
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
        val activeRouteSnapshot = activeRouteStore.restore()
        val snapshot = catalogSnapshotsUseCase.currentSnapshot(
            startupSource = activeRouteSnapshot.startupSeedSource(navigationState),
        )
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
        val restoredRouteState = activeRouteSnapshot
            ?.let(baseState::withActiveRouteSnapshot)
            ?: baseState
        libraryStateRecomputeCoordinator.initialize(restoredRouteState)
    }

    suspend fun hydrateAfterInitialState() {
        appStateHydrator.hydrate()
    }
}

private fun AppActiveRouteSnapshot?.startupSeedSource(
    navigationState: SourceNavigationState,
): SourceType {
    val sourceOrder = navigationState.sourceOrder
    val restorableFallbackReaderSource = this?.let { snapshot ->
        snapshot.readerFallbackItem
            ?.takeIf { fallbackItem ->
                fallbackItem.id == snapshot.readerItemId &&
                    fallbackItem.format == snapshot.readerFormat
            }
            ?.sourceType
    }
    val restoredRouteSource = this?.selectedSource
        ?.takeIf { sourceType -> sourceType in sourceOrder }
    return restorableFallbackReaderSource
        ?.takeIf { sourceType -> sourceType in sourceOrder }
        ?: restoredRouteSource
        ?: navigationState.startupSource
}
