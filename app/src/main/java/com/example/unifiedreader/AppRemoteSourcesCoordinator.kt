package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppRemoteSourcesCoordinator @Inject constructor(
    private val kavitaCoordinator: AppKavitaCoordinator,
    private val stumpCoordinator: AppStumpCoordinator,
    private val grimmoryCoordinator: AppGrimmoryCoordinator,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val stumpCredentialStore: StumpCredentialStore,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val stumpCoverCacheController: StumpCoverCacheController,
    private val grimmoryCoverCacheController: GrimmoryCoverCacheController,
    private val comicPageSourceCacheController: ComicPageSourceCacheController,
) {
    fun initialize(
        updateState: AppUiStateUpdater,
    ) {
        updateState { state ->
            state.copy(
                kavitaConnection = state.kavitaConnection.withStoredSession(kavitaCredentialStore.sessionFlow.value),
                stumpConnection = state.stumpConnection.withStoredSession(stumpCredentialStore.sessionFlow.value),
                grimmoryConnection = state.grimmoryConnection.withStoredSession(grimmoryCredentialStore.sessionFlow.value),
            )
        }
    }

    fun onKavitaDisplayNameChanged(
        displayName: String,
        updateState: AppUiStateUpdater,
    ) {
        updateKavitaConnectionInput(updateState) { connection ->
            connection.copy(displayName = displayName)
        }
    }

    fun onKavitaBaseUrlChanged(
        baseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        updateKavitaConnectionInput(updateState) { connection ->
            connection.copy(baseUrl = baseUrl)
        }
    }

    fun onKavitaUsernameChanged(
        username: String,
        updateState: AppUiStateUpdater,
    ) {
        updateKavitaConnectionInput(updateState) { connection ->
            connection.copy(username = username)
        }
    }

    fun onKavitaPasswordChanged(
        password: String,
        updateState: AppUiStateUpdater,
    ) {
        updateKavitaConnection(updateState) { connection ->
            connection.copy(
                password = password,
                message = null,
                isError = false,
            )
        }
    }

    fun onStumpDisplayNameChanged(
        displayName: String,
        updateState: AppUiStateUpdater,
    ) {
        updateStumpConnectionInput(updateState) { connection ->
            connection.copy(displayName = displayName)
        }
    }

    fun onStumpBaseUrlChanged(
        baseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        updateStumpConnectionInput(updateState) { connection ->
            connection.copy(baseUrl = baseUrl)
        }
    }

    fun onStumpUsernameChanged(
        username: String,
        updateState: AppUiStateUpdater,
    ) {
        updateStumpConnectionInput(updateState) { connection ->
            connection.copy(username = username)
        }
    }

    fun onStumpPasswordChanged(
        password: String,
        updateState: AppUiStateUpdater,
    ) {
        updateStumpConnection(updateState) { connection ->
            connection.copy(
                password = password,
                message = null,
                isError = false,
            )
        }
    }

    fun onGrimmoryDisplayNameChanged(
        displayName: String,
        updateState: AppUiStateUpdater,
    ) {
        updateGrimmoryConnectionInput(updateState) { connection ->
            connection.copy(displayName = displayName)
        }
    }

    fun onGrimmoryBaseUrlChanged(
        baseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        updateGrimmoryConnectionInput(updateState) { connection ->
            connection.copy(baseUrl = baseUrl)
        }
    }

    fun onGrimmoryUsernameChanged(
        username: String,
        updateState: AppUiStateUpdater,
    ) {
        updateGrimmoryConnectionInput(updateState) { connection ->
            connection.copy(username = username)
        }
    }

    fun onGrimmoryPasswordChanged(
        password: String,
        updateState: AppUiStateUpdater,
    ) {
        updateGrimmoryConnection(updateState) { connection ->
            connection.copy(
                password = password,
                message = null,
                isError = false,
            )
        }
    }

    fun connectKavitaServer(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        val state = stateProvider()
        if (state.kavitaConnection.hasStoredSession && state.kavitaConnection.password.isBlank()) {
            refreshKavitaLibrary(stateProvider, updateState, scope)
            return
        }
        when (val preparation = kavitaCoordinator.prepareConnect(state.kavitaConnection)) {
            is KavitaSyncPreparation.Invalid -> {
                updateState { current -> current.copy(kavitaConnection = preparation.connection) }
            }
            KavitaSyncPreparation.MissingSession -> Unit
            is KavitaSyncPreparation.Ready -> {
                updateState { current ->
                    current.copy(
                        kavitaConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            syncProgressFraction = null,
                            syncProgressLabel = null,
                            message = "Connecting to Kavita.",
                            isError = false,
                        ),
                    )
                }
                startKavitaConnect(preparation, updateState, scope)
            }
        }
    }

    fun connectStumpServer(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        val state = stateProvider()
        if (state.stumpConnection.hasStoredSession && state.stumpConnection.password.isBlank()) {
            refreshStumpLibrary(stateProvider, updateState, scope)
            return
        }
        when (val preparation = stumpCoordinator.prepareConnect(state.stumpConnection)) {
            is StumpSyncPreparation.Invalid -> {
                updateState { current -> current.copy(stumpConnection = preparation.connection) }
            }
            StumpSyncPreparation.MissingSession -> Unit
            is StumpSyncPreparation.Ready -> {
                updateState { current ->
                    current.copy(
                        stumpConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            syncProgressFraction = null,
                            syncProgressLabel = null,
                            message = "Connecting to Stump.",
                            isError = false,
                        ),
                    )
                }
                startStumpConnect(preparation, updateState, scope)
            }
        }
    }

    fun connectGrimmoryServer(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        when (val preparation = grimmoryCoordinator.prepareConnect(stateProvider().grimmoryConnection)) {
            is GrimmorySyncPreparation.Invalid -> {
                updateState { state -> state.copy(grimmoryConnection = preparation.connection) }
            }
            GrimmorySyncPreparation.MissingSession -> Unit
            is GrimmorySyncPreparation.Ready -> {
                updateState { state ->
                    state.copy(
                        grimmoryConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            message = "Connecting to Grimmory.",
                            isError = false,
                        ),
                    )
                }
                startGrimmorySync(preparation, updateState, scope)
            }
        }
    }

    fun refreshSelectedRemoteLibrary(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        when (stateProvider().selectedSource) {
            SourceType.KAVITA -> refreshKavitaLibrary(stateProvider, updateState, scope)
            SourceType.STUMP -> refreshStumpLibrary(stateProvider, updateState, scope)
            SourceType.GRIMMORY -> refreshGrimmoryLibrary(stateProvider, updateState, scope)
            SourceType.LOCAL -> Unit
        }
    }

    fun observeStoredSessions(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
    ) {
        scope.launch {
            kavitaCredentialStore.sessionFlow.collect { session ->
                if (!stateProvider().kavitaConnection.isConnecting) {
                    updateKavitaConnection(updateState) { connection ->
                        kavitaCoordinator.refreshedConnectionState(
                            connection.withStoredSession(session),
                        )
                    }
                }
            }
        }
        scope.launch {
            stumpCredentialStore.sessionFlow.collect { session ->
                if (!stateProvider().stumpConnection.isConnecting) {
                    updateStumpConnection(updateState) { connection ->
                        stumpCoordinator.refreshedConnectionState(
                            connection.withStoredSession(session),
                        )
                    }
                }
            }
        }
        scope.launch {
            grimmoryCredentialStore.sessionFlow.collect { session ->
                if (!stateProvider().grimmoryConnection.isConnecting) {
                    updateGrimmoryConnection(updateState) { connection ->
                        grimmoryCoordinator.refreshedConnectionState(
                            connection.withStoredSession(session),
                        )
                    }
                }
            }
        }
    }

    fun refreshKavitaLibrary(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        when (val preparation = kavitaCoordinator.prepareRefresh(stateProvider().kavitaConnection)) {
            KavitaSyncPreparation.MissingSession -> {
                updateState { state ->
                    state.copy(
                        libraryMessage = "Connect to Kavita before syncing the library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        kavitaConnection = state.kavitaConnection.copy(
                            message = "Connect to Kavita before syncing the library.",
                            isError = true,
                        ),
                    )
                }
            }
            is KavitaSyncPreparation.Invalid -> {
                updateState { state ->
                    state.copy(
                        libraryMessage = preparation.connection.message,
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        kavitaConnection = preparation.connection,
                    )
                }
            }
            is KavitaSyncPreparation.Ready -> {
                updateState { state ->
                    state.copy(
                        kavitaConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            syncProgressFraction = 0f,
                            syncProgressLabel = "Preparing Kavita sync...",
                            message = "Syncing Kavita library.",
                            isError = false,
                        ),
                        libraryMessage = "Syncing Kavita library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Info,
                    )
                }
                startKavitaSync(preparation, updateState, scope)
            }
        }
    }

    fun refreshStumpLibrary(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        when (val preparation = stumpCoordinator.prepareRefresh(stateProvider().stumpConnection)) {
            StumpSyncPreparation.MissingSession -> {
                updateState { state ->
                    state.copy(
                        libraryMessage = "Connect to Stump before syncing the library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        stumpConnection = state.stumpConnection.copy(
                            message = "Connect to Stump before syncing the library.",
                            isError = true,
                        ),
                    )
                }
            }
            is StumpSyncPreparation.Invalid -> {
                updateState { state ->
                    state.copy(
                        libraryMessage = preparation.connection.message,
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        stumpConnection = preparation.connection,
                    )
                }
            }
            is StumpSyncPreparation.Ready -> {
                updateState { state ->
                    state.copy(
                        stumpConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            syncProgressFraction = 0f,
                            syncProgressLabel = "Preparing Stump sync...",
                            message = "Syncing Stump library.",
                            isError = false,
                        ),
                        libraryMessage = "Syncing Stump library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Info,
                    )
                }
                startStumpSync(preparation, updateState, scope)
            }
        }
    }

    fun refreshGrimmoryLibrary(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        when (val preparation = grimmoryCoordinator.prepareRefresh(stateProvider().grimmoryConnection)) {
            GrimmorySyncPreparation.MissingSession -> {
                updateState { state ->
                    state.copy(
                        libraryMessage = "Connect to Grimmory before syncing the library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        grimmoryConnection = state.grimmoryConnection.copy(
                            message = "Connect to Grimmory before syncing the library.",
                            isError = true,
                        ),
                    )
                }
            }
            is GrimmorySyncPreparation.Invalid -> Unit
            is GrimmorySyncPreparation.Ready -> {
                updateState { state ->
                    state.copy(
                        grimmoryConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            message = "Syncing Grimmory library.",
                            isError = false,
                        ),
                        libraryMessage = "Syncing Grimmory library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Info,
                    )
                }
                startGrimmorySync(preparation, updateState, scope)
            }
        }
    }

    private fun startKavitaSync(
        preparation: KavitaSyncPreparation.Ready,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            when (
                val result = kavitaCoordinator.executeSync(
                    preparation = preparation,
                    onProgress = { progress ->
                        updateState { state ->
                            state.copy(
                                kavitaConnection = state.kavitaConnection.copy(
                                    isConnecting = true,
                                    syncProgressFraction = progress.fraction.coerceIn(0f, 1f),
                                    syncProgressLabel = progress.message,
                                    message = "Syncing Kavita library.",
                                    isError = false,
                                ),
                                libraryMessage = progress.message,
                                libraryMessageSeverity = LibraryMessageSeverity.Info,
                            )
                        }
                    },
                )
            ) {
                is KavitaSyncExecutionResult.Success -> {
                    clearKavitaPageCache()
                    updateState { state ->
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = SourceType.KAVITA,
                            selectedItemId = null,
                            activeReader = null,
                            selectedKavitaSeriesKey = null,
                            libraryMessage = "Synced ${result.itemCount} Kavita items from ${result.libraryCount} libraries.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            kavitaConnection = result.connection,
                        )
                    }
                }
                is KavitaSyncExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            kavitaConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun startKavitaConnect(
        preparation: KavitaSyncPreparation.Ready,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            when (val result = kavitaCoordinator.executeConnect(preparation)) {
                is KavitaConnectExecutionResult.Success -> {
                    clearKavitaPageCache()
                    updateState { state ->
                        state.copy(
                            libraryMessage = "Connected to Kavita. ${result.libraryCount} libraries available to sync.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            kavitaConnection = result.connection,
                        )
                    }
                }
                is KavitaConnectExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            kavitaConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun startStumpSync(
        preparation: StumpSyncPreparation.Ready,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            when (
                val result = stumpCoordinator.executeSync(
                    preparation = preparation,
                    clearCoverState = ::clearStumpCoverState,
                    onProgress = { progress ->
                        updateState { state ->
                            state.copy(
                                stumpConnection = state.stumpConnection.copy(
                                    isConnecting = true,
                                    syncProgressFraction = progress.fraction.coerceIn(0f, 1f),
                                    syncProgressLabel = progress.message,
                                    message = "Syncing Stump library.",
                                    isError = false,
                                ),
                                libraryMessage = progress.message,
                                libraryMessageSeverity = LibraryMessageSeverity.Info,
                            )
                        }
                    },
                )
            ) {
                is StumpSyncExecutionResult.Success -> {
                    clearStumpPageCache()
                    updateState { state ->
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = SourceType.STUMP,
                            selectedItemId = null,
                            activeReader = null,
                            selectedKavitaSeriesKey = null,
                            selectedStumpSeriesKey = null,
                            libraryMessage = "Synced ${result.itemCount} Stump items from ${result.libraryCount} libraries.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            stumpConnection = result.connection,
                        )
                    }
                }
                is StumpSyncExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            stumpConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun startStumpConnect(
        preparation: StumpSyncPreparation.Ready,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            when (val result = stumpCoordinator.executeConnect(preparation)) {
                is StumpConnectExecutionResult.Success -> {
                    clearStumpPageCache()
                    updateState { state ->
                        state.copy(
                            libraryMessage = "Connected to Stump. ${result.libraryCount} libraries available to sync.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            stumpConnection = result.connection,
                        )
                    }
                }
                is StumpConnectExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            stumpConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun startGrimmorySync(
        preparation: GrimmorySyncPreparation.Ready,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            when (val result = grimmoryCoordinator.executeSync(preparation, ::clearGrimmoryCoverState)) {
                is GrimmorySyncExecutionResult.Success -> {
                    updateState { state ->
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = SourceType.GRIMMORY,
                            selectedItemId = null,
                            activeReader = null,
                            libraryMessage = "Synced ${result.itemCount} Grimmory books from ${result.libraryCount} libraries.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            grimmoryConnection = result.connection,
                        )
                    }
                }
                is GrimmorySyncExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            grimmoryConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun updateKavitaConnection(
        updateState: AppUiStateUpdater,
        transform: (KavitaConnectionUiState) -> KavitaConnectionUiState,
    ) {
        updateState { state ->
            state.copy(
                kavitaConnection = transform(state.kavitaConnection),
            )
        }
    }

    private fun updateKavitaConnectionInput(
        updateState: AppUiStateUpdater,
        transform: (KavitaConnectionUiState) -> KavitaConnectionUiState,
    ) {
        updateKavitaConnection(updateState) { connection ->
            kavitaCoordinator.refreshedConnectionState(
                transform(connection).copy(
                    message = null,
                    isError = false,
                ),
            )
        }
    }

    private fun updateGrimmoryConnection(
        updateState: AppUiStateUpdater,
        transform: (GrimmoryConnectionUiState) -> GrimmoryConnectionUiState,
    ) {
        updateState { state ->
            state.copy(
                grimmoryConnection = transform(state.grimmoryConnection),
            )
        }
    }

    private fun updateGrimmoryConnectionInput(
        updateState: AppUiStateUpdater,
        transform: (GrimmoryConnectionUiState) -> GrimmoryConnectionUiState,
    ) {
        updateGrimmoryConnection(updateState) { connection ->
            grimmoryCoordinator.refreshedConnectionState(
                transform(connection).copy(
                    message = null,
                    isError = false,
                ),
            )
        }
    }

    private fun updateStumpConnection(
        updateState: AppUiStateUpdater,
        transform: (StumpConnectionUiState) -> StumpConnectionUiState,
    ) {
        updateState { state ->
            state.copy(
                stumpConnection = transform(state.stumpConnection),
            )
        }
    }

    private fun updateStumpConnectionInput(
        updateState: AppUiStateUpdater,
        transform: (StumpConnectionUiState) -> StumpConnectionUiState,
    ) {
        updateStumpConnection(updateState) { connection ->
            stumpCoordinator.refreshedConnectionState(
                transform(connection).copy(
                    message = null,
                    isError = false,
                ),
            )
        }
    }

    private fun clearStumpCoverState() {
        stumpCoverCacheController.clear()
    }

    private fun clearGrimmoryCoverState() {
        grimmoryCoverCacheController.clear()
    }

    private fun clearKavitaPageCache() {
        comicPageSourceCacheController.clearSourcePageCache(SourceType.KAVITA)
    }

    private fun clearStumpPageCache() {
        comicPageSourceCacheController.clearSourcePageCache(SourceType.STUMP)
    }
}
