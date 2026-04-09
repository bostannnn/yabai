package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppStumpRemoteSourceController @Inject constructor(
    private val stumpCoordinator: AppStumpCoordinator,
    private val stumpCredentialStore: StumpCredentialStore,
    private val stumpCoverCacheController: StumpCoverCacheController,
    private val comicPageSourceCacheController: ComicPageSourceCacheController,
) {
    fun initialize(
        updateState: AppUiStateUpdater,
    ) {
        updateState { state ->
            state.copy(
                stumpConnection = state.stumpConnection.withStoredSession(stumpCredentialStore.sessionFlow.value),
            )
        }
    }

    fun onDisplayNameChanged(
        displayName: String,
        updateState: AppUiStateUpdater,
    ) {
        updateConnectionInput(updateState) { connection ->
            connection.copy(displayName = displayName)
        }
    }

    fun onBaseUrlChanged(
        baseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        updateConnectionInput(updateState) { connection ->
            connection.copy(baseUrl = baseUrl)
        }
    }

    fun onUsernameChanged(
        username: String,
        updateState: AppUiStateUpdater,
    ) {
        updateConnectionInput(updateState) { connection ->
            connection.copy(username = username)
        }
    }

    fun onPasswordChanged(
        password: String,
        updateState: AppUiStateUpdater,
    ) {
        updateConnection(updateState) { connection ->
            connection.copy(
                password = password,
                message = null,
                isError = false,
            )
        }
    }

    fun connect(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        val state = stateProvider()
        if (state.stumpConnection.hasStoredSession && state.stumpConnection.password.isBlank()) {
            refresh(stateProvider, updateState, scope)
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
                startConnect(preparation, updateState, scope)
            }
        }
    }

    fun observeStoredSession(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
    ) {
        scope.launch {
            stumpCredentialStore.sessionFlow.collect { session ->
                if (!stateProvider().stumpConnection.isConnecting) {
                    updateConnection(updateState) { connection ->
                        stumpCoordinator.refreshedConnectionState(
                            connection.withStoredSession(session),
                        )
                    }
                }
            }
        }
    }

    fun refresh(
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
                startSync(preparation, updateState, scope)
            }
        }
    }

    private fun startSync(
        preparation: StumpSyncPreparation.Ready,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            when (
                val result = stumpCoordinator.executeSync(
                    preparation = preparation,
                    clearCoverState = ::clearCoverState,
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
                    clearPageCache()
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

    private fun startConnect(
        preparation: StumpSyncPreparation.Ready,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            when (val result = stumpCoordinator.executeConnect(preparation)) {
                is StumpConnectExecutionResult.Success -> {
                    clearPageCache()
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

    private fun clearCoverState() {
        stumpCoverCacheController.clear()
    }

    private fun clearPageCache() {
        comicPageSourceCacheController.clearSourcePageCache(SourceType.STUMP)
    }

    private fun updateConnection(
        updateState: AppUiStateUpdater,
        transform: (StumpConnectionUiState) -> StumpConnectionUiState,
    ) {
        updateState { state ->
            state.copy(
                stumpConnection = transform(state.stumpConnection),
            )
        }
    }

    private fun updateConnectionInput(
        updateState: AppUiStateUpdater,
        transform: (StumpConnectionUiState) -> StumpConnectionUiState,
    ) {
        updateConnection(updateState) { connection ->
            stumpCoordinator.refreshedConnectionState(
                transform(connection).copy(
                    message = null,
                    isError = false,
                ),
            )
        }
    }
}
