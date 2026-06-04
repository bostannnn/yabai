package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppKavitaRemoteSourceController @Inject constructor(
    private val kavitaCoordinator: AppKavitaCoordinator,
    private val kavitaReadingListController: AppKavitaReadingListController,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val comicPageSourceCacheController: ComicPageSourceCacheController,
    private val remoteLibraryCacheStore: RemoteLibraryCacheStore,
    private val remoteCoverController: RemoteCoverController,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    private var suppressedObservedLibraryRefresh: KavitaStoredSessionKey? = null

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

    fun onBackupBaseUrlChanged(
        backupBaseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        updateConnectionInput(updateState) { connection ->
            connection.copy(
                backupBaseUrl = backupBaseUrl,
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
                activeAction = null,
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
        when (val preparation = kavitaCoordinator.prepareConnect(state.kavitaConnection)) {
            is KavitaSyncPreparation.Invalid -> {
                updateState { current ->
                    current.copy(
                        kavitaConnection = preparation.connection.copy(activeAction = null),
                    )
                }
            }
            KavitaSyncPreparation.MissingSession -> Unit
            is KavitaSyncPreparation.Ready -> {
                updateState { current ->
                    current.copy(
                        kavitaConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            activeAction = ServerConnectionAction.Connect,
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
                if (consumeSuppressedObservedLibraryRefresh(session)) return@collectLatest
                if (cachedLibraries.isEmpty()) return@collectLatest
                if (!remoteSourceConnectivityPolicy.isReachable(SourceType.KAVITA)) return@collectLatest
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
                            activeAction = null,
                            isError = true,
                        ),
                    )
                }
            }
            is KavitaSyncPreparation.Invalid -> {
                updateState { state ->
                    state.copy(
                        kavitaConnection = preparation.connection.copy(activeAction = null),
                    )
                }
            }
            is KavitaSyncPreparation.Ready -> {
                updateState { state ->
                    state.copy(
                        kavitaConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            activeAction = ServerConnectionAction.Sync,
                            syncProgressFraction = 0f,
                            syncProgressLabel = "Preparing Kavita sync...",
                            message = "Syncing Kavita library.",
                            isError = false,
                        ),
                    )
                }
                startSync(preparation, stateProvider, updateState, scope)
            }
        }
    }

    fun fastRefresh(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        preserveLibraryContext: Boolean = false,
    ) {
        when (val preparation = kavitaCoordinator.prepareRefresh(stateProvider().kavitaConnection)) {
            KavitaSyncPreparation.MissingSession -> {
                updateState { state ->
                    state.copy(
                        kavitaConnection = state.kavitaConnection.copy(
                            message = "Connect to Kavita before fast syncing the library.",
                            activeAction = null,
                            isError = true,
                        ),
                    )
                }
            }
            is KavitaSyncPreparation.Invalid -> {
                updateState { state ->
                    state.copy(
                        kavitaConnection = preparation.connection.copy(activeAction = null),
                    )
                }
            }
            is KavitaSyncPreparation.Ready -> {
                updateState { state ->
                    state.copy(
                        kavitaConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            activeAction = ServerConnectionAction.FastSync,
                            syncProgressFraction = 0f,
                            syncProgressLabel = "Preparing Kavita fast sync...",
                            message = "Fast syncing Kavita library.",
                            isError = false,
                        ),
                    )
                }
                startFastSync(
                    preparation = preparation,
                    stateProvider = stateProvider,
                    updateState = updateState,
                    scope = scope,
                    preserveLibraryContext = preserveLibraryContext,
                )
            }
        }
    }

    private fun startSync(
        preparation: KavitaSyncPreparation.Ready,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        scope.launch {
            suppressObservedLibraryRefresh(preparation.request.baseUrl, preparation.request.username)
            when (
                val result = kavitaCoordinator.executeSync(
                    preparation = preparation,
                    clearCoverState = ::resetCoverState,
                    onProgress = { progress ->
                        updateState { state ->
                            state.copy(
                                kavitaConnection = state.kavitaConnection.copy(
                                    isConnecting = true,
                                    activeAction = ServerConnectionAction.Sync,
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
                            kavitaReadingLists = state.kavitaReadingLists.copy(
                                errorMessage = null,
                            ),
                            kavitaConnection = result.connection.copy(
                                activeAction = null,
                                availableLibraries = result.availableLibraries,
                            ),
                        )
                    }
                    kavitaReadingListController.refreshAfterKavitaSync(
                        stateProvider = stateProvider,
                        updateState = updateState,
                    )
                }
                is KavitaSyncExecutionResult.Failure -> {
                    clearSuppressedObservedLibraryRefresh(preparation.request.baseUrl, preparation.request.username)
                    updateState { state ->
                        state.copy(
                            kavitaConnection = result.connection.copy(activeAction = null),
                        )
                    }
                }
            }
        }
    }

    private fun startFastSync(
        preparation: KavitaSyncPreparation.Ready,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        preserveLibraryContext: Boolean,
    ) {
        scope.launch {
            suppressObservedLibraryRefresh(preparation.request.baseUrl, preparation.request.username)
            when (
                val result = kavitaCoordinator.executeFastSync(
                    preparation = preparation,
                    clearCoverState = ::resetCoverState,
                    onProgress = { progress ->
                        updateState { state ->
                            state.copy(
                                kavitaConnection = state.kavitaConnection.copy(
                                    isConnecting = true,
                                    activeAction = ServerConnectionAction.FastSync,
                                    syncProgressFraction = progress.fraction.coerceIn(0f, 1f),
                                    syncProgressLabel = progress.message,
                                    message = "Fast syncing Kavita library.",
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
                    if (result.hasCatalogChanges) {
                        clearPageCache()
                    }
                    updateState { state ->
                        val syncedConnection = result.connection.copy(
                            activeAction = null,
                            availableLibraries = result.availableLibraries,
                        )
                        val syncedReadingLists = state.kavitaReadingLists.copy(
                            errorMessage = null,
                        )
                        if (preserveLibraryContext) {
                            state.copy(
                                kavitaReadingLists = syncedReadingLists,
                                kavitaConnection = syncedConnection,
                            )
                        } else {
                            state.copy(
                                topLevel = TopLevelDestination.Library,
                                selectedSource = SourceType.KAVITA,
                                selectedItemId = null,
                                activeReader = null,
                                selectedKavitaSeriesKey = null,
                                kavitaReadingLists = syncedReadingLists,
                                kavitaConnection = syncedConnection,
                            )
                        }
                    }
                    kavitaReadingListController.refreshAfterKavitaSync(
                        stateProvider = stateProvider,
                        updateState = updateState,
                    )
                }
                is KavitaSyncExecutionResult.Failure -> {
                    clearSuppressedObservedLibraryRefresh(preparation.request.baseUrl, preparation.request.username)
                    updateState { state ->
                        state.copy(
                            kavitaConnection = result.connection.copy(activeAction = null),
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
            suppressObservedLibraryRefresh(preparation.request.baseUrl, preparation.request.username)
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
                                activeAction = null,
                                availableLibraries = result.availableLibraries,
                            ),
                        )
                    }
                }
                is KavitaConnectExecutionResult.Failure -> {
                    clearSuppressedObservedLibraryRefresh(preparation.request.baseUrl, preparation.request.username)
                    updateState { state ->
                        state.copy(
                            kavitaConnection = result.connection.copy(activeAction = null),
                        )
                    }
                }
            }
        }
    }

    private fun clearPageCache() {
        comicPageSourceCacheController.clearSourcePageCache(SourceType.KAVITA)
    }

    private suspend fun resetCoverState() {
        remoteCoverController.refreshSourceAfterCatalogSync(SourceType.KAVITA)
    }

    private fun suppressObservedLibraryRefresh(
        baseUrl: String,
        username: String,
    ) {
        suppressedObservedLibraryRefresh = KavitaStoredSessionKey(
            baseUrl = baseUrl,
            username = username,
        )
    }

    private fun clearSuppressedObservedLibraryRefresh(
        baseUrl: String,
        username: String,
    ) {
        val key = KavitaStoredSessionKey(
            baseUrl = baseUrl,
            username = username,
        )
        if (suppressedObservedLibraryRefresh == key) {
            suppressedObservedLibraryRefresh = null
        }
    }

    private fun consumeSuppressedObservedLibraryRefresh(
        session: KavitaStoredSession,
    ): Boolean {
        val key = KavitaStoredSessionKey(
            baseUrl = session.baseUrl,
            username = session.username,
        )
        return if (suppressedObservedLibraryRefresh == key) {
            suppressedObservedLibraryRefresh = null
            true
        } else {
            false
        }
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
                    activeAction = null,
                    message = null,
                    isError = false,
                ),
            )
        }
    }
}

private data class KavitaStoredSessionKey(
    val baseUrl: String,
    val username: String,
)
