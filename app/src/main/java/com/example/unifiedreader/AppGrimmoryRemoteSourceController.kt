package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppGrimmoryRemoteSourceController @Inject constructor(
    private val grimmoryCoordinator: AppGrimmoryCoordinator,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val remoteCoverController: RemoteCoverController,
) {
    fun initialize(
        updateState: AppUiStateUpdater,
    ) {
        updateState { state ->
            state.copy(
                grimmoryConnection = state.grimmoryConnection.withStoredSession(grimmoryCredentialStore.sessionFlow.value),
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

    fun onBackupBaseUrlChanged(
        backupBaseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        updateConnectionInput(updateState) { connection ->
            connection.copy(backupBaseUrl = backupBaseUrl)
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
                startSync(preparation, updateState, scope)
            }
        }
    }

    fun observeStoredSession(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
    ) {
        scope.launch {
            grimmoryCredentialStore.sessionFlow.collect { session ->
                if (!stateProvider().grimmoryConnection.isConnecting) {
                    updateConnection(updateState) { connection ->
                        grimmoryCoordinator.refreshedConnectionState(
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
        when (val preparation = grimmoryCoordinator.prepareRefresh(stateProvider().grimmoryConnection)) {
            GrimmorySyncPreparation.MissingSession -> {
                updateState { state ->
                    state.copy(
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
                    )
                }
                startSync(preparation, updateState, scope)
            }
        }
    }

    private fun startSync(
        preparation: GrimmorySyncPreparation.Ready,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            when (val result = grimmoryCoordinator.executeSync(preparation, ::resetCoverState)) {
                is GrimmorySyncExecutionResult.Success -> {
                    updateState { state ->
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = SourceType.GRIMMORY,
                            selectedItemId = null,
                            activeReader = null,
                            grimmoryConnection = result.connection,
                        )
                    }
                }
                is GrimmorySyncExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
                            grimmoryConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private suspend fun resetCoverState() {
        remoteCoverController.resetSource(SourceType.GRIMMORY)
    }

    private fun updateConnection(
        updateState: AppUiStateUpdater,
        transform: (GrimmoryConnectionUiState) -> GrimmoryConnectionUiState,
    ) {
        updateState { state ->
            state.copy(
                grimmoryConnection = transform(state.grimmoryConnection),
            )
        }
    }

    private fun updateConnectionInput(
        updateState: AppUiStateUpdater,
        transform: (GrimmoryConnectionUiState) -> GrimmoryConnectionUiState,
    ) {
        updateConnection(updateState) { connection ->
            grimmoryCoordinator.refreshedConnectionState(
                transform(connection).copy(
                    message = null,
                    isError = false,
                ),
            )
        }
    }
}
