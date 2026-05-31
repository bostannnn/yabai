package com.example.unifiedreader

import com.example.unifiedreader.core.model.CollectionSummary
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.ReadingListSummary
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
        val selectedScope = readingListId?.toKavitaScopeSelection()
        updateState { state ->
            val selectedItemsCached = when (selectedScope) {
                is KavitaScopeSelection.Collection ->
                    state.kavitaReadingLists.seriesByCollectionId.containsKey(selectedScope.id)

                is KavitaScopeSelection.ReadingList ->
                    state.kavitaReadingLists.itemsByReadingListId.containsKey(selectedScope.id)

                null -> false
            }
            state.copy(
                selectedKavitaSeriesKey = null,
                kavitaLibrarySection = selectedScope?.sectionForSelection() ?: state.kavitaLibrarySection,
                kavitaReadingLists = state.kavitaReadingLists.copy(
                    selectedReadingListId = (selectedScope as? KavitaScopeSelection.ReadingList)?.id,
                    loadingSelectedReadingListId = (selectedScope as? KavitaScopeSelection.ReadingList)
                        ?.id
                        ?.takeUnless { selectedItemsCached },
                    selectedCollectionId = (selectedScope as? KavitaScopeSelection.Collection)?.id,
                    loadingSelectedCollectionId = (selectedScope as? KavitaScopeSelection.Collection)
                        ?.id
                        ?.takeUnless { selectedItemsCached },
                    isLoadingSelectedItems = selectedScope != null && !selectedItemsCached,
                    errorMessage = null,
                ),
                libraryScrollToTopRequest = state.libraryScrollToTopRequest + 1L,
            )
        }
        if (selectedScope == null) return
        val selectedItemsCached = when (selectedScope) {
            is KavitaScopeSelection.Collection ->
                currentState.kavitaReadingLists.seriesByCollectionId.containsKey(selectedScope.id)

            is KavitaScopeSelection.ReadingList ->
                currentState.kavitaReadingLists.itemsByReadingListId.containsKey(selectedScope.id)
        }
        if (selectedItemsCached) return
        val session = kavitaCredentialStore.sessionFlow.value ?: return
        scope.launch {
            when (selectedScope) {
                is KavitaScopeSelection.Collection -> loadCollectionSeries(
                    session = session,
                    collectionId = selectedScope.id,
                    updateState = updateState,
                )

                is KavitaScopeSelection.ReadingList -> loadItems(
                    session = session,
                    readingListId = selectedScope.id,
                    updateState = updateState,
                )
            }
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
        val state = stateProvider().kavitaReadingLists
        state.selectedCollectionId?.let { collectionId ->
            loadCollectionSeries(
                session = session,
                collectionId = collectionId,
                updateState = updateState,
            )
            return
        }
        state.selectedReadingListId?.let { selectedReadingListId ->
            loadItems(
                session = session,
                readingListId = selectedReadingListId,
                updateState = updateState,
            )
        }
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
                    KavitaScopeSummaries(
                        readingLists = api.getReadingLists(
                            accessToken = accessToken,
                            apiKey = apiKey,
                        ),
                        collections = api.getCollections(
                            accessToken = accessToken,
                            apiKey = apiKey,
                        ),
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
                val readingListIds = summaries.readingLists.mapTo(hashSetOf()) { summary -> summary.id }
                val collectionIds = summaries.collections.mapTo(hashSetOf()) { collection -> collection.id }
                val loadingSelectedReadingListId = if (invalidateCachedItems) {
                    null
                } else {
                    state.kavitaReadingLists.loadingSelectedReadingListId
                        ?.takeIf(readingListIds::contains)
                }
                val loadingSelectedCollectionId = if (invalidateCachedItems) {
                    null
                } else {
                    state.kavitaReadingLists.loadingSelectedCollectionId
                        ?.takeIf(collectionIds::contains)
                }
                state.copy(
                    kavitaReadingLists = state.kavitaReadingLists.copy(
                        summaries = summaries.readingLists,
                        itemsByReadingListId = if (invalidateCachedItems) {
                            emptyMap()
                        } else {
                            state.kavitaReadingLists.itemsByReadingListId
                                .filterKeys(readingListIds::contains)
                        },
                        collections = summaries.collections,
                        seriesByCollectionId = if (invalidateCachedItems) {
                            emptyMap()
                        } else {
                            state.kavitaReadingLists.seriesByCollectionId
                                .filterKeys(collectionIds::contains)
                        },
                        selectedReadingListId = state.kavitaReadingLists.selectedReadingListId
                            ?.takeIf(readingListIds::contains),
                        selectedCollectionId = state.kavitaReadingLists.selectedCollectionId
                            ?.takeIf(collectionIds::contains),
                        loadingSelectedReadingListId = loadingSelectedReadingListId,
                        loadingSelectedCollectionId = loadingSelectedCollectionId,
                        isLoadingSummaries = false,
                        isLoadingSelectedItems = state.kavitaReadingLists.isLoadingSelectedItems &&
                            (loadingSelectedReadingListId != null || loadingSelectedCollectionId != null),
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
                        errorMessage = "Unable to load Kavita lists: ${throwable.userFacingMessage()}",
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

    private suspend fun loadCollectionSeries(
        session: KavitaStoredSession,
        collectionId: String,
        updateState: AppUiStateUpdater,
    ) {
        val requestSessionIdentity = KavitaReadingListSessionIdentity.from(session)
        val remoteId = collectionId.substringAfterLast(':', missingDelimiterValue = collectionId)
        var shouldLoad = false
        updateState { state ->
            if (state.kavitaReadingLists.selectedCollectionId != collectionId) {
                return@updateState state
            }
            shouldLoad = true
            state.copy(
                kavitaReadingLists = state.kavitaReadingLists.copy(
                    loadingSelectedCollectionId = collectionId,
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
                    api.getCollectionSeries(
                        accessToken = accessToken,
                        collectionRemoteId = remoteId,
                    )
                }
            }
        }.onSuccess { seriesRefs ->
            if (!isObservedSessionCurrent(requestSessionIdentity)) {
                return@onSuccess
            }
            remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
            updateState { state ->
                if (!isObservedSessionCurrent(requestSessionIdentity)) {
                    return@updateState state
                }
                val isCurrentLoad = state.kavitaReadingLists.loadingSelectedCollectionId == collectionId
                val isCurrentSelection = state.kavitaReadingLists.selectedCollectionId == collectionId
                state.copy(
                    kavitaReadingLists = state.kavitaReadingLists.copy(
                        seriesByCollectionId = state.kavitaReadingLists.seriesByCollectionId +
                            (collectionId to seriesRefs),
                        loadingSelectedCollectionId = if (isCurrentLoad) {
                            null
                        } else {
                            state.kavitaReadingLists.loadingSelectedCollectionId
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
                if (state.kavitaReadingLists.loadingSelectedCollectionId != collectionId) {
                    return@updateState state
                }
                state.copy(
                    kavitaReadingLists = state.kavitaReadingLists.copy(
                        loadingSelectedCollectionId = null,
                        isLoadingSelectedItems = false,
                        errorMessage = "Unable to load Kavita collection: ${throwable.userFacingMessage()}",
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

private data class KavitaScopeSummaries(
    val readingLists: List<ReadingListSummary>,
    val collections: List<CollectionSummary>,
)

private sealed interface KavitaScopeSelection {
    val id: String

    data class Collection(
        override val id: String,
    ) : KavitaScopeSelection

    data class ReadingList(
        override val id: String,
    ) : KavitaScopeSelection
}

private fun String.toKavitaScopeSelection(): KavitaScopeSelection = when {
    startsWith("kavita:collection:") -> KavitaScopeSelection.Collection(this)
    else -> KavitaScopeSelection.ReadingList(this)
}

private fun KavitaScopeSelection.sectionForSelection(): KavitaLibrarySection = when (this) {
    is KavitaScopeSelection.Collection -> KavitaLibrarySection.Collections
    is KavitaScopeSelection.ReadingList -> KavitaLibrarySection.ReadingLists
}
