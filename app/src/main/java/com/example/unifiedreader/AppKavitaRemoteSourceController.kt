package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class AppKavitaRemoteSourceController @Inject constructor(
    private val kavitaCoordinator: AppKavitaCoordinator,
    kavitaCredentialStore: KavitaCredentialStore,
    comicPageSourceCacheController: ComicPageSourceCacheController,
    remoteLibraryCacheStore: RemoteLibraryCacheStore,
    remoteCoverController: RemoteCoverController,
    sourceSyncTimes: SourceSyncTimeStore,
) : RemoteSourceConnectionController<KavitaCatalogSyncRequest, KavitaStoredSession>(
    source = SourceType.KAVITA,
    sessionFlow = kavitaCredentialStore.sessionFlow,
    remoteCoverController = remoteCoverController,
    sourceSyncTimes = sourceSyncTimes,
    libraryLists = RemoteSourceLibraryLists(remoteLibraryCacheStore, comicPageSourceCacheController),
) {
    /** Settings' Sync now: syncs everything, then opens the library. */
    fun refresh(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        sync(ServerConnectionAction.Sync, stayInPlace = false, stateProvider, updateState, scope)
    }

    /** Syncs only what changed; [preserveLibraryContext] leaves you where you are. */
    fun fastRefresh(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        preserveLibraryContext: Boolean = false,
    ) {
        sync(ServerConnectionAction.FastSync, stayInPlace = preserveLibraryContext, stateProvider, updateState, scope)
    }

    override fun prepareConnect(connection: RemoteSourceConnectionUiState) = kavitaCoordinator.prepareConnect(connection)

    override fun prepareRefresh(connection: RemoteSourceConnectionUiState) = kavitaCoordinator.prepareRefresh(connection)

    override fun refreshedConnectionState(connection: RemoteSourceConnectionUiState) =
        kavitaCoordinator.refreshedConnectionState(connection)

    override suspend fun executeConnect(preparation: KavitaSyncReady) = kavitaCoordinator.executeConnect(preparation)

    override suspend fun executeSync(
        preparation: KavitaSyncReady,
        action: ServerConnectionAction,
        clearCoverState: suspend () -> Unit,
        onProgress: (SourceSyncProgress) -> Unit,
    ): SourceSyncExecutionResult {
        val report = { progress: KavitaSyncProgress -> onProgress(SourceSyncProgress(progress.message, progress.fraction)) }
        return if (action == ServerConnectionAction.FastSync) {
            kavitaCoordinator.executeFastSync(preparation, clearCoverState = clearCoverState, onProgress = report)
        } else {
            kavitaCoordinator.executeSync(preparation, clearCoverState = clearCoverState, onProgress = report)
        }
    }

    override val syncStartProgress = SourceSyncProgress(KavitaSyncSteps.connecting.message, KavitaSyncSteps.connecting.fraction)

    override fun syncingMessage(action: ServerConnectionAction): String =
        if (action == ServerConnectionAction.FastSync) "Fast syncing Kavita library." else super.syncingMessage(action)

    override fun missingSessionMessage(action: ServerConnectionAction): String =
        if (action == ServerConnectionAction.FastSync) {
            "Connect to Kavita before fast syncing the library."
        } else {
            super.missingSessionMessage(action)
        }

    override fun AppUiState.withoutOpenSeries(): AppUiState = copy(selectedKavitaSeriesKey = null)
}
