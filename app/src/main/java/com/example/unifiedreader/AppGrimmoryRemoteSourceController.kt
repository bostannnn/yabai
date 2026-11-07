package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class AppGrimmoryRemoteSourceController @Inject constructor(
    private val grimmoryCoordinator: AppGrimmoryCoordinator,
    grimmoryCredentialStore: GrimmoryCredentialStore,
    remoteCoverController: RemoteCoverController,
    sourceSyncTimes: SourceSyncTimeStore,
) : RemoteSourceConnectionController<GrimmoryCatalogSyncRequest, GrimmoryStoredSession>(
    source = SourceType.GRIMMORY,
    sessionFlow = grimmoryCredentialStore.sessionFlow,
    remoteCoverController = remoteCoverController,
    sourceSyncTimes = sourceSyncTimes,
    libraryLists = null,
) {
    /**
     * Syncs the library; both kinds save only the books that changed. [inPlace] is the library's own
     * refresh: it loads reading history for the books read since the last sync and leaves you where
     * you are; otherwise (Settings' Sync now) every book's history is loaded and the app opens the library.
     */
    fun refresh(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        inPlace: Boolean = false,
    ) {
        val action = if (inPlace) ServerConnectionAction.FastSync else ServerConnectionAction.Sync
        sync(action, stayInPlace = inPlace, stateProvider, updateState, scope)
    }

    override fun prepareConnect(connection: RemoteSourceConnectionUiState) = grimmoryCoordinator.prepareConnect(connection)

    override fun prepareRefresh(connection: RemoteSourceConnectionUiState) = grimmoryCoordinator.prepareRefresh(connection)

    override fun refreshedConnectionState(connection: RemoteSourceConnectionUiState) =
        grimmoryCoordinator.refreshedConnectionState(connection)

    override suspend fun executeConnect(preparation: GrimmorySyncReady) = grimmoryCoordinator.executeConnect(preparation)

    override suspend fun executeSync(
        preparation: GrimmorySyncReady,
        action: ServerConnectionAction,
        clearCoverState: suspend () -> Unit,
        onProgress: (SourceSyncProgress) -> Unit,
    ): SourceSyncExecutionResult = grimmoryCoordinator.executeSync(
        preparation = preparation,
        clearCoverState = clearCoverState,
        refresh = action == ServerConnectionAction.FastSync,
        onProgress = { progress -> onProgress(SourceSyncProgress(progress.message, progress.fraction)) },
    )

    override val syncStartProgress =
        SourceSyncProgress(GrimmorySyncSteps.connecting.message, GrimmorySyncSteps.connecting.fraction)
}
