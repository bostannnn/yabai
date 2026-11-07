package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import com.example.unifiedreader.feature.settings.syncProgressFillAfter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The library list and page cache a server keeps for the comics it lists by library (Kavita, Stump). */
class RemoteSourceLibraryLists(
    val libraryCacheStore: RemoteLibraryCacheStore,
    val pageCacheController: ComicPageSourceCacheController,
)

/**
 * A server's connection in settings: the sign-in form, signing in, syncing the library and following the saved
 * sign-in. Each server's subclass says how to prepare and run its requests; the state changes are the same for all.
 */
abstract class RemoteSourceConnectionController<Request : SourceSyncRequest, Session : StoredSourceSession>(
    private val source: SourceType,
    private val sessionFlow: StateFlow<Session?>,
    private val remoteCoverController: RemoteCoverController,
    private val sourceSyncTimes: SourceSyncTimeStore,
    /** Null for a server that does not list its libraries (Grimmory). */
    private val libraryLists: RemoteSourceLibraryLists?,
) {
    /** The server's name in messages, "Kavita". */
    protected val name: String = RemoteSourceConnectionUiState(source).displayName

    internal abstract fun prepareConnect(connection: RemoteSourceConnectionUiState): SourceSyncPreparation<Request, Session>

    internal abstract fun prepareRefresh(connection: RemoteSourceConnectionUiState): SourceSyncPreparation<Request, Session>

    internal abstract fun refreshedConnectionState(connection: RemoteSourceConnectionUiState): RemoteSourceConnectionUiState

    internal abstract suspend fun executeConnect(
        preparation: SourceSyncPreparation.Ready<Request, Session>,
    ): SourceConnectExecutionResult

    /** Runs the sync [action] names ([ServerConnectionAction.Sync] or [ServerConnectionAction.FastSync]). */
    internal abstract suspend fun executeSync(
        preparation: SourceSyncPreparation.Ready<Request, Session>,
        action: ServerConnectionAction,
        clearCoverState: suspend () -> Unit,
        onProgress: (SourceSyncProgress) -> Unit,
    ): SourceSyncExecutionResult

    /** Where the bar starts before the server answers. */
    internal abstract val syncStartProgress: SourceSyncProgress

    /** The bar after a reported step; by default it never moves back. */
    protected open fun progressAfter(current: Float?, reported: Float?): Float? =
        syncProgressFillAfter(current = current, reported = reported)

    protected open fun syncingMessage(action: ServerConnectionAction): String = "Syncing $name library."

    protected open fun missingSessionMessage(action: ServerConnectionAction): String =
        "Connect to $name before syncing the library."

    /** Clears the series this server's library had open, when a sync opens the library afresh. */
    protected open fun AppUiState.withoutOpenSeries(): AppUiState = this

    fun initialize(updateState: AppUiStateUpdater) {
        updateConnection(updateState) { connection -> connection.withStoredSession(sessionFlow.value) }
    }

    fun onDisplayNameChanged(displayName: String, updateState: AppUiStateUpdater) {
        updateConnectionInput(updateState) { connection -> connection.copy(displayName = displayName) }
    }

    fun onBaseUrlChanged(baseUrl: String, updateState: AppUiStateUpdater) {
        updateConnectionInput(updateState) { connection ->
            connection.copy(baseUrl = baseUrl, availableLibraries = emptyList())
        }
    }

    fun onBackupBaseUrlChanged(backupBaseUrl: String, updateState: AppUiStateUpdater) {
        updateConnectionInput(updateState) { connection ->
            connection.copy(backupBaseUrl = backupBaseUrl, availableLibraries = emptyList())
        }
    }

    fun onUsernameChanged(username: String, updateState: AppUiStateUpdater) {
        updateConnectionInput(updateState) { connection ->
            connection.copy(username = username, availableLibraries = emptyList())
        }
    }

    fun onPasswordChanged(password: String, updateState: AppUiStateUpdater) {
        updateConnection(updateState) { connection ->
            connection.copy(password = password, activeAction = null, message = null, isError = false)
        }
    }

    fun connect(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        // One sign-in or sync at a time: a second would interleave its progress and save over the first.
        val connection = stateProvider().connection()
        if (connection.isConnecting) return
        when (val preparation = prepareConnect(connection)) {
            is SourceSyncPreparation.Invalid -> updateConnection(updateState) {
                preparation.connection.copy(activeAction = null)
            }
            SourceSyncPreparation.MissingSession -> Unit
            is SourceSyncPreparation.Ready -> {
                updateConnection(updateState) {
                    preparation.activeConnection.copy(
                        isConnecting = true,
                        activeAction = ServerConnectionAction.Connect,
                        syncProgressFraction = null,
                        syncProgressLabel = null,
                        message = "Connecting to $name.",
                        isError = false,
                    )
                }
                scope.launch { finishConnect(preparation, updateState) }
            }
        }
    }

    private suspend fun finishConnect(
        preparation: SourceSyncPreparation.Ready<Request, Session>,
        updateState: AppUiStateUpdater,
    ) {
        when (val result = executeConnect(preparation)) {
            is SourceConnectExecutionResult.Success -> {
                rememberLibraries(preparation.request, result.availableLibraries, clearPages = true)
                updateConnection(updateState) {
                    result.connection.copy(activeAction = null, availableLibraries = result.availableLibraries)
                }
            }
            is SourceConnectExecutionResult.Failure -> updateConnection(updateState) {
                result.connection.copy(activeAction = null)
            }
        }
    }

    /**
     * Follows the saved sign-in: a renewed token or a sign-in elsewhere updates the form, and a server that lists its
     * libraries shows the cached list straight away.
     */
    fun observeStoredSession(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
    ) {
        scope.launch {
            sessionFlow.collectLatest { session ->
                if (!stateProvider().connection().isConnecting) {
                    updateConnection(updateState) { connection ->
                        refreshedConnectionState(connection.withStoredSession(session))
                    }
                }
                if (session == null || libraryLists == null) return@collectLatest
                val cachedLibraries = libraryLists.libraryCacheStore.getCachedLibraries(
                    sourceType = source,
                    baseUrl = session.baseUrl,
                    username = session.username,
                )
                if (cachedLibraries.isEmpty()) return@collectLatest
                updateConnection(updateState) { connection ->
                    val isSameAccount = connection.hasStoredSession &&
                        connection.baseUrl == session.baseUrl &&
                        connection.username == session.username
                    if (isSameAccount) connection.copy(availableLibraries = cachedLibraries) else connection
                }
            }
        }
    }

    /**
     * Syncs the library. [stayInPlace] is the library's own refresh, which leaves you where you are; otherwise
     * (Settings' Sync now) the app opens the library when it is done.
     */
    protected fun sync(
        action: ServerConnectionAction,
        stayInPlace: Boolean,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        val connection = stateProvider().connection()
        if (connection.isConnecting) return
        when (val preparation = prepareRefresh(connection)) {
            SourceSyncPreparation.MissingSession -> updateConnection(updateState) { current ->
                current.copy(message = missingSessionMessage(action), activeAction = null, isError = true)
            }
            is SourceSyncPreparation.Invalid -> updateConnection(updateState) {
                preparation.connection.copy(activeAction = null)
            }
            is SourceSyncPreparation.Ready -> {
                updateConnection(updateState) {
                    preparation.activeConnection.copy(
                        isConnecting = true,
                        activeAction = action,
                        syncProgressFraction = syncStartProgress.fraction,
                        syncProgressLabel = syncStartProgress.message,
                        message = syncingMessage(action),
                        isError = false,
                    )
                }
                scope.launch { finishSync(preparation, action, stayInPlace, updateState) }
            }
        }
    }

    private suspend fun finishSync(
        preparation: SourceSyncPreparation.Ready<Request, Session>,
        action: ServerConnectionAction,
        stayInPlace: Boolean,
        updateState: AppUiStateUpdater,
    ) {
        val result = executeSync(
            preparation = preparation,
            action = action,
            clearCoverState = { remoteCoverController.refreshSourceAfterCatalogSync(source) },
            onProgress = { progress ->
                updateConnection(updateState) { connection ->
                    connection.copy(
                        isConnecting = true,
                        activeAction = action,
                        syncProgressFraction = progressAfter(connection.syncProgressFraction, progress.fraction),
                        syncProgressLabel = progress.message,
                        message = syncingMessage(action),
                        isError = false,
                    )
                }
            },
        )
        when (result) {
            is SourceSyncExecutionResult.Success -> {
                sourceSyncTimes.markSynced(source)
                rememberLibraries(preparation.request, result.availableLibraries, clearPages = result.hasCatalogChanges)
                val synced = result.connection.copy(activeAction = null, availableLibraries = result.availableLibraries)
                updateState { state ->
                    if (stayInPlace) {
                        state.withConnection(synced)
                    } else {
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = source,
                            selectedItemId = null,
                            activeReader = null,
                        ).withoutOpenSeries().withConnection(synced)
                    }
                }
            }
            is SourceSyncExecutionResult.Failure -> updateConnection(updateState) {
                result.connection.copy(activeAction = null)
            }
        }
    }

    /** Saves the library list a sign-in or sync returned, and drops cached pages that may be stale. */
    private suspend fun rememberLibraries(
        request: Request,
        libraries: List<com.example.unifiedreader.core.model.Library>,
        clearPages: Boolean,
    ) {
        val lists = libraryLists ?: return
        lists.libraryCacheStore.replaceCachedLibraries(
            sourceType = source,
            baseUrl = request.baseUrl,
            username = request.username,
            libraries = libraries,
        )
        if (clearPages) lists.pageCacheController.clearSourcePageCache(source)
    }

    private fun AppUiState.connection(): RemoteSourceConnectionUiState = when (source) {
        SourceType.KAVITA -> kavitaConnection
        SourceType.STUMP -> stumpConnection
        SourceType.GRIMMORY -> grimmoryConnection
        SourceType.LOCAL -> error("The device's own files have no server connection.")
    }

    private fun AppUiState.withConnection(connection: RemoteSourceConnectionUiState): AppUiState = when (source) {
        SourceType.KAVITA -> copy(kavitaConnection = connection)
        SourceType.STUMP -> copy(stumpConnection = connection)
        SourceType.GRIMMORY -> copy(grimmoryConnection = connection)
        SourceType.LOCAL -> this
    }

    private fun updateConnection(
        updateState: AppUiStateUpdater,
        transform: (RemoteSourceConnectionUiState) -> RemoteSourceConnectionUiState,
    ) {
        updateState { state -> state.withConnection(transform(state.connection())) }
    }

    /** A form edit: clears the last message and checks whether the form still matches the saved sign-in. */
    private fun updateConnectionInput(
        updateState: AppUiStateUpdater,
        transform: (RemoteSourceConnectionUiState) -> RemoteSourceConnectionUiState,
    ) {
        updateConnection(updateState) { connection ->
            refreshedConnectionState(transform(connection).copy(activeAction = null, message = null, isError = false))
        }
    }
}
