package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppKavitaRemoteSourceController @Inject constructor(
    private val kavitaCoordinator: AppKavitaCoordinator,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val comicPageSourceCacheController: ComicPageSourceCacheController,
    private val remoteLibraryCacheStore: RemoteLibraryCacheStore,
) {
    fun initialize(
        updateState: AppUiStateUpdater,
    ) {
        updateState { state ->
            state.copy(
                kavitaConnection = state.kavitaConnection.withStoredSession(kavitaCredentialStore.sessionFlow.value),
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
        if (state.kavitaConnection.hasStoredSession && state.kavitaConnection.password.isBlank()) {
            refresh(stateProvider, updateState, scope)
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
            kavitaCredentialStore.sessionFlow.collectLatest { session ->
                if (!stateProvider().kavitaConnection.isConnecting) {
                    updateConnection(updateState) { connection ->
                        kavitaCoordinator.refreshedConnectionState(
                            connection.withStoredSession(session),
                        )
                    }
                }
                if (session == null) return@collectLatest
                val cachedLibraries = remoteLibraryCacheStore.getCachedLibraries(
                    sourceType = SourceType.KAVITA,
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
                if (stateProvider().kavitaConnection.isConnecting) return@collectLatest
                val libraries = runCatching {
                    kavitaCoordinator.loadAvailableLibraries(session)
                }.getOrNull() ?: return@collectLatest
                remoteLibraryCacheStore.replaceCachedLibraries(
                    sourceType = SourceType.KAVITA,
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
        when (val preparation = kavitaCoordinator.prepareRefresh(stateProvider().kavitaConnection)) {
            KavitaSyncPreparation.MissingSession -> {
                updateState { state ->
                    state.copy(
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
                    )
                }
                startSync(preparation, updateState, scope)
            }
        }
    }

    private fun startSync(
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
                            )
                        }
                    },
                )
            ) {
                is KavitaSyncExecutionResult.Success -> {
                    remoteLibraryCacheStore.replaceCachedLibraries(
                        sourceType = SourceType.KAVITA,
                        baseUrl = preparation.request.baseUrl,
                        username = preparation.request.username,
                        libraries = result.availableLibraries,
                    )
                    clearPageCache()
                    updateState { state ->
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = SourceType.KAVITA,
                            selectedItemId = null,
                            activeReader = null,
                            selectedKavitaSeriesKey = null,
                            kavitaConnection = result.connection.copy(
                                availableLibraries = result.availableLibraries,
                            ),
                        )
                    }
                }
                is KavitaSyncExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
                            kavitaConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun startConnect(
        preparation: KavitaSyncPreparation.Ready,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            when (val result = kavitaCoordinator.executeConnect(preparation)) {
                is KavitaConnectExecutionResult.Success -> {
                    remoteLibraryCacheStore.replaceCachedLibraries(
                        sourceType = SourceType.KAVITA,
                        baseUrl = preparation.request.baseUrl,
                        username = preparation.request.username,
                        libraries = result.availableLibraries,
                    )
                    clearPageCache()
                    updateState { state ->
                        state.copy(
                            kavitaConnection = result.connection.copy(
                                availableLibraries = result.availableLibraries,
                            ),
                        )
                    }
                }
                is KavitaConnectExecutionResult.Failure -> {
                    updateState { state ->
                        state.copy(
                            kavitaConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun clearPageCache() {
        comicPageSourceCacheController.clearSourcePageCache(SourceType.KAVITA)
    }

    private fun updateConnection(
        updateState: AppUiStateUpdater,
        transform: (KavitaConnectionUiState) -> KavitaConnectionUiState,
    ) {
        updateState { state ->
            state.copy(
                kavitaConnection = transform(state.kavitaConnection),
            )
        }
    }

    private fun updateConnectionInput(
        updateState: AppUiStateUpdater,
        transform: (KavitaConnectionUiState) -> KavitaConnectionUiState,
    ) {
        updateConnection(updateState) { connection ->
            kavitaCoordinator.refreshedConnectionState(
                transform(connection).copy(
                    message = null,
                    isError = false,
                ),
            )
        }
    }
}
