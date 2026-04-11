package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppStumpRemoteSourceController @Inject constructor(
    private val stumpCoordinator: AppStumpCoordinator,
    private val stumpCredentialStore: StumpCredentialStore,
    private val stumpCoverCacheController: StumpCoverCacheController,
    private val comicPageSourceCacheController: ComicPageSourceCacheController,
    private val remoteLibraryCacheStore: RemoteLibraryCacheStore,
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
            connection.copy(
                baseUrl = baseUrl,
                availableLibraries = emptyList(),
            )
        }
    }

    fun onUsernameChanged(
        username: String,
        updateState: AppUiStateUpdater,
    ) {
        updateConnectionInput(updateState) { connection ->
            connection.copy(
                username = username,
                availableLibraries = emptyList(),
            )
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
            stumpCredentialStore.sessionFlow.collectLatest { session ->
                if (!stateProvider().stumpConnection.isConnecting) {
                    updateConnection(updateState) { connection ->
                        stumpCoordinator.refreshedConnectionState(
                            connection.withStoredSession(session),
                        )
                    }
                }
                if (session == null) return@collectLatest
                val cachedLibraries = remoteLibraryCacheStore.getCachedLibraries(
                    sourceType = SourceType.STUMP,
                    baseUrl = session.baseUrl,
                    username = session.username,
                )
                if (cachedLibraries.isNotEmpty()) {
                    updateConnection(updateState) { connection ->
                        if (
                            connection.hasStoredSession &&
                            connection.baseUrl == session.baseUrl &&
                            connection.username == session.username
                        ) {
                            connection.copy(availableLibraries = cachedLibraries)
                        } else {
                            connection
                        }
                    }
                }
                if (stateProvider().stumpConnection.isConnecting) return@collectLatest
                val libraries = runCatching {
                    stumpCoordinator.loadAvailableLibraries(session)
                }.getOrNull() ?: return@collectLatest
                remoteLibraryCacheStore.replaceCachedLibraries(
                    sourceType = SourceType.STUMP,
                    baseUrl = session.baseUrl,
                    username = session.username,
                    libraries = libraries,
                )
                updateConnection(updateState) { connection ->
                    if (
                        connection.hasStoredSession &&
                        connection.baseUrl == session.baseUrl &&
                        connection.username == session.username
                    ) {
                        connection.copy(availableLibraries = libraries)
                    } else {
                        connection
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
                            )
                        }
                    },
                )
            ) {
                is StumpSyncExecutionResult.Success -> {
                    remoteLibraryCacheStore.replaceCachedLibraries(
                        sourceType = SourceType.STUMP,
                        baseUrl = preparation.request.baseUrl,
                        username = preparation.request.username,
                        libraries = result.availableLibraries,
                    )
                    clearPageCache()
                    updateState { state ->
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = SourceType.STUMP,
                            selectedItemId = null,
                            activeReader = null,
                            selectedKavitaSeriesKey = null,
                            selectedStumpSeriesKey = null,
                            stumpConnection = result.connection.copy(
                                availableLibraries = result.availableLibraries,
                            ),
                        )
                    }
                }
                is StumpSyncExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
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
                    remoteLibraryCacheStore.replaceCachedLibraries(
                        sourceType = SourceType.STUMP,
                        baseUrl = preparation.request.baseUrl,
                        username = preparation.request.username,
                        libraries = result.availableLibraries,
                    )
                    clearPageCache()
                    updateState { state ->
                        state.copy(
                            stumpConnection = result.connection.copy(
                                availableLibraries = result.availableLibraries,
                            ),
                        )
                    }
                }
                is StumpConnectExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
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
