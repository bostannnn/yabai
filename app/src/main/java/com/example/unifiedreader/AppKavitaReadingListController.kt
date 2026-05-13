package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.kavita.KavitaApi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppKavitaReadingListController @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)
    private var observedSessionState: KavitaReadingListObservedSessionState =
        KavitaReadingListObservedSessionState.Unobserved

    fun observeStoredSession(
        scope: CoroutineScope,
        updateState: AppUiStateUpdater,
    ) {
        scope.launch {
            kavitaCredentialStore.sessionFlow.collectLatest { session ->
                if (session == null) {
                    observedSessionState = KavitaReadingListObservedSessionState.NoSession
                    updateState { state ->
                        state.copy(kavitaReadingLists = AppKavitaReadingListState())
                    }
                    return@collectLatest
                }
                val sessionIdentity = KavitaReadingListSessionIdentity.from(session)
                val previousSessionIdentity = (observedSessionState as? KavitaReadingListObservedSessionState.Session)
                    ?.identity
                val shouldInvalidateCachedItems = previousSessionIdentity
                    ?.let { previousIdentity -> previousIdentity != sessionIdentity }
                    ?: false
                observedSessionState = KavitaReadingListObservedSessionState.Session(sessionIdentity)
                if (shouldInvalidateCachedItems) {
                    updateState { state ->
                        state.copy(kavitaReadingLists = AppKavitaReadingListState())
                    }
                }
                loadSummaries(
                    session = session,
                    updateState = updateState,
                    invalidateCachedItems = shouldInvalidateCachedItems,
                )
            }
        }
    }

    fun onReadingListSelected(
        readingListId: String?,
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        updateState { state ->
            val selectedItemsCached = readingListId != null &&
                state.kavitaReadingLists.itemsByReadingListId.containsKey(readingListId)
            state.copy(
                selectedKavitaSeriesKey = null,
                kavitaReadingLists = state.kavitaReadingLists.copy(
                    selectedReadingListId = readingListId,
                    loadingSelectedReadingListId = if (readingListId != null && !selectedItemsCached) {
                        readingListId
                    } else {
                        null
                    },
                    isLoadingSelectedItems = readingListId != null && !selectedItemsCached,
                    errorMessage = null,
                ),
                libraryScrollToTopRequest = state.libraryScrollToTopRequest + 1L,
            )
        }
        if (readingListId == null) return
        if (currentState.kavitaReadingLists.itemsByReadingListId.containsKey(readingListId)) return
        val session = kavitaCredentialStore.sessionFlow.value ?: return
        scope.launch {
            loadItems(
                session = session,
                readingListId = readingListId,
                updateState = updateState,
            )
        }
    }

    suspend fun refreshAfterKavitaSync(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
    ) {
        val session = kavitaCredentialStore.sessionFlow.value ?: run {
            updateState { state ->
                state.copy(kavitaReadingLists = AppKavitaReadingListState())
            }
            return
        }
        val refreshed = loadSummaries(
            session = session,
            updateState = updateState,
            invalidateCachedItems = true,
        )
        if (!refreshed) return
        val selectedReadingListId = stateProvider().kavitaReadingLists.selectedReadingListId ?: return
        loadItems(
            session = session,
            readingListId = selectedReadingListId,
            updateState = updateState,
        )
    }

    private suspend fun loadSummaries(
        session: KavitaStoredSession,
        updateState: AppUiStateUpdater,
        invalidateCachedItems: Boolean = false,
    ): Boolean {
        val requestSessionIdentity = KavitaReadingListSessionIdentity.from(session)
        updateState { state ->
            state.copy(
                kavitaReadingLists = state.kavitaReadingLists.copy(
                    isLoadingSummaries = true,
                    errorMessage = null,
                ),
            )
        }
        var refreshed = false
        runSuspendCatchingPreservingCancellation {
            runWithReadingListAddressFailover(session) { api, activeSession ->
                sessionRequestRunner.run(
                    api = api,
                    session = activeSession,
                ) { accessToken, apiKey ->
                    api.getReadingLists(
                        accessToken = accessToken,
                        apiKey = apiKey,
                    )
                }
            }
        }.onSuccess { summaries ->
            if (!isObservedSessionCurrent(requestSessionIdentity)) {
                return@onSuccess
            }
            refreshed = true
            remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
            updateState { state ->
                if (!isObservedSessionCurrent(requestSessionIdentity)) {
                    return@updateState state
                }
                val summaryIds = summaries.mapTo(hashSetOf()) { summary -> summary.id }
                val loadingSelectedReadingListId = if (invalidateCachedItems) {
                    null
                } else {
                    state.kavitaReadingLists.loadingSelectedReadingListId
                        ?.takeIf(summaryIds::contains)
                }
                state.copy(
                    kavitaReadingLists = state.kavitaReadingLists.copy(
                        summaries = summaries,
                        itemsByReadingListId = if (invalidateCachedItems) {
                            emptyMap()
                        } else {
                            state.kavitaReadingLists.itemsByReadingListId
                                .filterKeys(summaryIds::contains)
                        },
                        selectedReadingListId = state.kavitaReadingLists.selectedReadingListId
                            ?.takeIf(summaryIds::contains),
                        loadingSelectedReadingListId = loadingSelectedReadingListId,
                        isLoadingSummaries = false,
                        isLoadingSelectedItems = state.kavitaReadingLists.isLoadingSelectedItems &&
                            loadingSelectedReadingListId != null,
                        errorMessage = null,
                    ),
                )
            }
        }.onFailure { throwable ->
            if (!isObservedSessionCurrent(requestSessionIdentity)) {
                return@onFailure
            }
            remoteSourceConnectivityPolicy.markFromFailure(SourceType.KAVITA, throwable)
            updateState { state ->
                if (!isObservedSessionCurrent(requestSessionIdentity)) {
                    return@updateState state
                }
                state.copy(
                    kavitaReadingLists = state.kavitaReadingLists.copy(
                        isLoadingSummaries = false,
                        errorMessage = "Unable to load Kavita reading lists: ${throwable.userFacingMessage()}",
                    ),
                )
            }
        }
        return refreshed
    }

    private suspend fun loadItems(
        session: KavitaStoredSession,
        readingListId: String,
        updateState: AppUiStateUpdater,
    ) {
        val requestSessionIdentity = KavitaReadingListSessionIdentity.from(session)
        val remoteId = readingListId.substringAfterLast(':', missingDelimiterValue = readingListId)
        var shouldLoad = false
        updateState { state ->
            if (state.kavitaReadingLists.selectedReadingListId != readingListId) {
                return@updateState state
            }
            shouldLoad = true
            state.copy(
                kavitaReadingLists = state.kavitaReadingLists.copy(
                    loadingSelectedReadingListId = readingListId,
                    isLoadingSelectedItems = true,
                    errorMessage = null,
                ),
            )
        }
        if (!shouldLoad) return
        runSuspendCatchingPreservingCancellation {
            runWithReadingListAddressFailover(session) { api, activeSession ->
                sessionRequestRunner.run(
                    api = api,
                    session = activeSession,
                ) { accessToken, _ ->
                    api.getReadingListItems(
                        accessToken = accessToken,
                        readingListRemoteId = remoteId,
                    )
                }
            }
        }.onSuccess { items ->
            if (!isObservedSessionCurrent(requestSessionIdentity)) {
                return@onSuccess
            }
            remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
            updateState { state ->
                if (!isObservedSessionCurrent(requestSessionIdentity)) {
                    return@updateState state
                }
                val isCurrentLoad = state.kavitaReadingLists.loadingSelectedReadingListId == readingListId
                val isCurrentSelection = state.kavitaReadingLists.selectedReadingListId == readingListId
                state.copy(
                    kavitaReadingLists = state.kavitaReadingLists.copy(
                        itemsByReadingListId = state.kavitaReadingLists.itemsByReadingListId +
                            (readingListId to items),
                        loadingSelectedReadingListId = if (isCurrentLoad) {
                            null
                        } else {
                            state.kavitaReadingLists.loadingSelectedReadingListId
                        },
                        isLoadingSelectedItems = if (isCurrentLoad) {
                            false
                        } else {
                            state.kavitaReadingLists.isLoadingSelectedItems
                        },
                        errorMessage = if (isCurrentSelection) {
                            null
                        } else {
                            state.kavitaReadingLists.errorMessage
                        },
                    ),
                )
            }
        }.onFailure { throwable ->
            if (!isObservedSessionCurrent(requestSessionIdentity)) {
                return@onFailure
            }
            remoteSourceConnectivityPolicy.markFromFailure(SourceType.KAVITA, throwable)
            updateState { state ->
                if (!isObservedSessionCurrent(requestSessionIdentity)) {
                    return@updateState state
                }
                if (state.kavitaReadingLists.loadingSelectedReadingListId != readingListId) {
                    return@updateState state
                }
                state.copy(
                    kavitaReadingLists = state.kavitaReadingLists.copy(
                        loadingSelectedReadingListId = null,
                        isLoadingSelectedItems = false,
                        errorMessage = "Unable to load Kavita reading list: ${throwable.userFacingMessage()}",
                    ),
                )
            }
        }
    }

    private fun isObservedSessionCurrent(
        requestSessionIdentity: KavitaReadingListSessionIdentity,
    ): Boolean = when (val state = observedSessionState) {
        KavitaReadingListObservedSessionState.Unobserved -> true
        KavitaReadingListObservedSessionState.NoSession -> false
        is KavitaReadingListObservedSessionState.Session -> state.identity == requestSessionIdentity
    }

    private suspend fun <T> runWithReadingListAddressFailover(
        session: KavitaStoredSession,
        block: suspend (api: KavitaApi, session: KavitaStoredSession) -> T,
    ): T = runWithServerAddressFailover(
        baseUrl = session.baseUrl,
        backupBaseUrl = session.backupBaseUrl,
        activeBaseUrl = session.activeBaseUrl,
    ) { selectedBaseUrl ->
        val activeSession = session.copy(activeBaseUrl = selectedBaseUrl)
        block(kavitaApiFactory.create(selectedBaseUrl), activeSession)
    }.value
}

private sealed interface KavitaReadingListObservedSessionState {
    data object Unobserved : KavitaReadingListObservedSessionState
    data object NoSession : KavitaReadingListObservedSessionState

    data class Session(
        val identity: KavitaReadingListSessionIdentity,
    ) : KavitaReadingListObservedSessionState
}

private data class KavitaReadingListSessionIdentity(
    val baseUrl: String,
    val username: String,
) {
    companion object {
        fun from(session: KavitaStoredSession): KavitaReadingListSessionIdentity = KavitaReadingListSessionIdentity(
            baseUrl = session.baseUrl,
            username = session.username,
        )
    }
}
