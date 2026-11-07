package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class AppStumpRemoteSourceController @Inject constructor(
    private val stumpCoordinator: AppStumpCoordinator,
    stumpCredentialStore: StumpCredentialStore,
    remoteCoverController: RemoteCoverController,
    comicPageSourceCacheController: ComicPageSourceCacheController,
    remoteLibraryCacheStore: RemoteLibraryCacheStore,
    sourceSyncTimes: SourceSyncTimeStore,
) : RemoteSourceConnectionController<StumpCatalogSyncRequest, StumpStoredSession>(
    source = SourceType.STUMP,
    sessionFlow = stumpCredentialStore.sessionFlow,
    remoteCoverController = remoteCoverController,
    sourceSyncTimes = sourceSyncTimes,
    libraryLists = RemoteSourceLibraryLists(remoteLibraryCacheStore, comicPageSourceCacheController),
) {
    /**
     * Syncs the library. [inPlace] is the library's own refresh, which leaves you where you are;
     * otherwise (Settings' Sync now) the app opens the library when it is done.
     */
    fun refresh(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        inPlace: Boolean = false,
    ) {
        sync(ServerConnectionAction.Sync, stayInPlace = inPlace, stateProvider, updateState, scope)
    }

    override fun prepareConnect(connection: RemoteSourceConnectionUiState) = stumpCoordinator.prepareConnect(connection)

    override fun prepareRefresh(connection: RemoteSourceConnectionUiState) = stumpCoordinator.prepareRefresh(connection)

    override fun refreshedConnectionState(connection: RemoteSourceConnectionUiState) =
        stumpCoordinator.refreshedConnectionState(connection)

    override suspend fun executeConnect(preparation: StumpSyncReady) = stumpCoordinator.executeConnect(preparation)

    override suspend fun executeSync(
        preparation: StumpSyncReady,
        action: ServerConnectionAction,
        clearCoverState: suspend () -> Unit,
        onProgress: (SourceSyncProgress) -> Unit,
    ): SourceSyncExecutionResult = stumpCoordinator.executeSync(
        preparation = preparation,
        clearCoverState = clearCoverState,
        onProgress = { progress -> onProgress(SourceSyncProgress(progress.message, progress.fraction)) },
    )

    override val syncStartProgress = SourceSyncProgress(message = "Preparing Stump sync...", fraction = 0f)

    /** Stump reports a fraction for every step, so the bar follows it. */
    override fun progressAfter(current: Float?, reported: Float?): Float? = reported?.coerceIn(0f, 1f) ?: current

    override fun AppUiState.withoutOpenSeries(): AppUiState =
        copy(selectedKavitaSeriesKey = null, selectedStumpSeriesKey = null)
}
