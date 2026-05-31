package com.example.unifiedreader

import com.example.unifiedreader.core.model.CollectionSeriesRef
import com.example.unifiedreader.core.model.CollectionSummary
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadingListItemRef
import com.example.unifiedreader.core.model.ReadingListSummary
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteCollectionId
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteReadingListId
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaChapterInfoDto
import com.example.unifiedreader.data.kavita.KavitaProgressDto
import com.example.unifiedreader.data.kavita.KavitaProgressUpdate
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppKavitaReadingListControllerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stale queued item load does not run after another reading list is selected from cache`() = runTest {
        val firstListId = SourceType.KAVITA.remoteReadingListId("1")
        val secondListId = SourceType.KAVITA.remoteReadingListId("2")
        val fakeApi = FakeKavitaReadingListApi(
            itemsByRemoteId = mapOf(
                "1" to listOf(readingListItem(readingListRemoteId = "1", itemRemoteId = "11")),
            ),
        )
        val controller = controller(fakeApi)
        var state = AppUiState()
        val updateState: AppUiStateUpdater = { reducer -> state = reducer(state) }

        controller.onReadingListSelected(
            readingListId = firstListId,
            currentState = state,
            updateState = updateState,
            scope = this,
        )
        state = state.copy(
            kavitaReadingLists = state.kavitaReadingLists.copy(
                itemsByReadingListId = mapOf(
                    secondListId to listOf(readingListItem(readingListRemoteId = "2", itemRemoteId = "21")),
                ),
            ),
        )
        controller.onReadingListSelected(
            readingListId = secondListId,
            currentState = state,
            updateState = updateState,
            scope = this,
        )
        advanceUntilIdle()

        assertEquals(secondListId, state.kavitaReadingLists.selectedReadingListId)
        assertEquals(KavitaLibrarySection.ReadingLists, state.kavitaLibrarySection)
        assertFalse(state.kavitaReadingLists.isLoadingSelectedItems)
        assertEquals(emptyList<String>(), fakeApi.itemRequests)
    }

    @Test
    fun `sync refresh reloads summaries invalidates cached details and refetches selected reading list`() = runTest {
        val readingListId = SourceType.KAVITA.remoteReadingListId("42")
        val fakeApi = FakeKavitaReadingListApi(
            summaries = listOf(readingListSummary(remoteId = "42", title = "Updated", itemCount = 2)),
            itemsByRemoteId = mapOf(
                "42" to listOf(
                    readingListItem(readingListRemoteId = "42", itemRemoteId = "100", order = 0),
                    readingListItem(readingListRemoteId = "42", itemRemoteId = "101", order = 1),
                ),
            ),
        )
        val controller = controller(fakeApi)
        var state = AppUiState(
            kavitaReadingLists = AppKavitaReadingListState(
                summaries = listOf(readingListSummary(remoteId = "42", title = "Old", itemCount = 1)),
                itemsByReadingListId = mapOf(
                    readingListId to listOf(readingListItem(readingListRemoteId = "42", itemRemoteId = "99")),
                ),
                selectedReadingListId = readingListId,
            ),
        )
        val updateState: AppUiStateUpdater = { reducer -> state = reducer(state) }

        controller.refreshAfterKavitaSync(
            stateProvider = { state },
            updateState = updateState,
        )

        assertEquals("Updated", state.kavitaReadingLists.summaries.single().title)
        assertEquals(
            listOf(SourceType.KAVITA.remoteItemId("100"), SourceType.KAVITA.remoteItemId("101")),
            state.kavitaReadingLists.itemsByReadingListId.getValue(readingListId).map(ReadingListItemRef::itemId),
        )
        assertEquals(listOf("42"), fakeApi.itemRequests)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `collection selection loads collection series and clears reading list selection`() = runTest {
        val readingListId = SourceType.KAVITA.remoteReadingListId("42")
        val collectionId = SourceType.KAVITA.remoteCollectionId("9")
        val fakeApi = FakeKavitaReadingListApi(
            collectionSeriesByRemoteId = mapOf(
                "9" to listOf(collectionSeries(collectionRemoteId = "9", seriesKey = "kavita:library:1::100")),
            ),
        )
        val controller = controller(fakeApi)
        var state = AppUiState(
            kavitaReadingLists = AppKavitaReadingListState(
                summaries = listOf(readingListSummary(remoteId = "42", title = "Old List", itemCount = 1)),
                selectedReadingListId = readingListId,
            ),
        )
        val updateState: AppUiStateUpdater = { reducer -> state = reducer(state) }

        controller.onReadingListSelected(
            readingListId = collectionId,
            currentState = state,
            updateState = updateState,
            scope = this,
        )
        advanceUntilIdle()

        assertEquals(null, state.kavitaReadingLists.selectedReadingListId)
        assertEquals(collectionId, state.kavitaReadingLists.selectedCollectionId)
        assertEquals(KavitaLibrarySection.Collections, state.kavitaLibrarySection)
        assertFalse(state.kavitaReadingLists.isLoadingSelectedItems)
        assertEquals(
            listOf("kavita:library:1::100"),
            state.kavitaReadingLists.seriesByCollectionId.getValue(collectionId).map(CollectionSeriesRef::seriesKey),
        )
        assertEquals(listOf("9"), fakeApi.collectionRequests)
    }

    @Test
    fun `sync refresh reloads summaries invalidates cached details and refetches selected collection`() = runTest {
        val collectionId = SourceType.KAVITA.remoteCollectionId("9")
        val fakeApi = FakeKavitaReadingListApi(
            collections = listOf(collectionSummary(remoteId = "9", title = "Updated Collection", itemCount = 1)),
            collectionSeriesByRemoteId = mapOf(
                "9" to listOf(collectionSeries(collectionRemoteId = "9", seriesKey = "kavita:library:1::new")),
            ),
        )
        val controller = controller(fakeApi)
        var state = AppUiState(
            kavitaReadingLists = AppKavitaReadingListState(
                collections = listOf(collectionSummary(remoteId = "9", title = "Old Collection", itemCount = 1)),
                seriesByCollectionId = mapOf(
                    collectionId to listOf(
                        collectionSeries(collectionRemoteId = "9", seriesKey = "kavita:library:1::old"),
                    ),
                ),
                selectedCollectionId = collectionId,
            ),
        )
        val updateState: AppUiStateUpdater = { reducer -> state = reducer(state) }

        controller.refreshAfterKavitaSync(
            stateProvider = { state },
            updateState = updateState,
        )

        assertEquals("Updated Collection", state.kavitaReadingLists.collections.single().title)
        assertEquals(
            listOf("kavita:library:1::new"),
            state.kavitaReadingLists.seriesByCollectionId.getValue(collectionId).map(CollectionSeriesRef::seriesKey),
        )
        assertEquals(listOf("9"), fakeApi.collectionRequests)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stored session identity change clears cached reading list details`() = runTest {
        val readingListId = SourceType.KAVITA.remoteReadingListId("42")
        val fakeApi = FakeKavitaReadingListApi(
            summaries = listOf(readingListSummary(remoteId = "42", title = "New Server List", itemCount = 1)),
        )
        val credentialStore = FakeKavitaCredentialStore(
            storedSession(
                baseUrl = "https://old-kavita.example",
                username = "old-reader",
            ),
        )
        val controller = controller(
            fakeApi = fakeApi,
            credentialStore = credentialStore,
        )
        var state = AppUiState(
            kavitaReadingLists = AppKavitaReadingListState(
                summaries = listOf(readingListSummary(remoteId = "42", title = "Old Server List", itemCount = 1)),
                itemsByReadingListId = mapOf(
                    readingListId to listOf(readingListItem(readingListRemoteId = "42", itemRemoteId = "99")),
                ),
                selectedReadingListId = readingListId,
            ),
        )
        val updateState: AppUiStateUpdater = { reducer -> state = reducer(state) }
        val observerJob = Job()
        val observerScope = CoroutineScope(coroutineContext + observerJob)
        try {
            controller.observeStoredSession(
                scope = observerScope,
                updateState = updateState,
            )
            advanceUntilIdle()

            credentialStore.save(
                storedSession(
                    baseUrl = "https://new-kavita.example",
                    username = "new-reader",
                ),
            )
            advanceUntilIdle()

            assertEquals("New Server List", state.kavitaReadingLists.summaries.single().title)
            assertEquals(emptyMap<String, List<ReadingListItemRef>>(), state.kavitaReadingLists.itemsByReadingListId)
            assertEquals(null, state.kavitaReadingLists.selectedReadingListId)
        } finally {
            observerJob.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stale item response from previous session identity is ignored`() = runTest {
        val readingListId = SourceType.KAVITA.remoteReadingListId("42")
        val oldRequestStarted = CompletableDeferred<Unit>()
        val releaseOldRequest = CompletableDeferred<Unit>()
        val fakeApisByBaseUrl = mutableMapOf(
            "https://old-kavita.example" to FakeKavitaReadingListApi(
                itemsByRemoteId = mapOf(
                    "42" to listOf(readingListItem(readingListRemoteId = "42", itemRemoteId = "old")),
                ),
                onItemsRequested = {
                    oldRequestStarted.complete(Unit)
                    releaseOldRequest.await()
                },
            ),
            "https://new-kavita.example" to FakeKavitaReadingListApi(
                itemsByRemoteId = mapOf(
                    "42" to listOf(readingListItem(readingListRemoteId = "42", itemRemoteId = "new")),
                ),
            ),
        )
        val credentialStore = FakeKavitaCredentialStore(
            storedSession(
                baseUrl = "https://old-kavita.example",
                username = "old-reader",
            ),
        )
        val controller = AppKavitaReadingListController(
            kavitaApiFactory = KavitaApiFactory { baseUrl -> fakeApisByBaseUrl.getValue(baseUrl) },
            kavitaCredentialStore = credentialStore,
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
        )
        var state = AppUiState(
            kavitaReadingLists = AppKavitaReadingListState(
                summaries = listOf(readingListSummary(remoteId = "42", title = "List", itemCount = 1)),
                selectedReadingListId = readingListId,
            ),
        )
        val updateState: AppUiStateUpdater = { reducer -> state = reducer(state) }
        val observerJob = Job()
        val observerScope = CoroutineScope(coroutineContext + observerJob)
        try {
            controller.observeStoredSession(
                scope = observerScope,
                updateState = updateState,
            )
            advanceUntilIdle()

            val oldSelection = backgroundScope.async {
                controller.onReadingListSelected(
                    readingListId = readingListId,
                    currentState = state,
                    updateState = updateState,
                    scope = this,
                )
            }
            oldRequestStarted.await()

            credentialStore.save(
                storedSession(
                    baseUrl = "https://new-kavita.example",
                    username = "new-reader",
                ),
            )
            advanceUntilIdle()
            controller.onReadingListSelected(
                readingListId = readingListId,
                currentState = state,
                updateState = updateState,
                scope = this,
            )
            advanceUntilIdle()

            releaseOldRequest.complete(Unit)
            oldSelection.await()
            advanceUntilIdle()

            assertEquals(
                listOf(SourceType.KAVITA.remoteItemId("new")),
                state.kavitaReadingLists.itemsByReadingListId.getValue(readingListId).map(ReadingListItemRef::itemId),
            )
        } finally {
            observerJob.cancel()
            releaseOldRequest.complete(Unit)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stale summary response from previous session identity is ignored`() = runTest {
        val oldRefreshStarted = CompletableDeferred<Unit>()
        val releaseOldRefresh = CompletableDeferred<Unit>()
        var oldSummaryRequests = 0
        val fakeApisByBaseUrl = mutableMapOf(
            "https://old-kavita.example" to FakeKavitaReadingListApi(
                summaries = listOf(readingListSummary(remoteId = "1", title = "Old Server List", itemCount = 1)),
                onSummariesRequested = {
                    oldSummaryRequests += 1
                    if (oldSummaryRequests == 2) {
                        oldRefreshStarted.complete(Unit)
                        releaseOldRefresh.await()
                    }
                },
            ),
            "https://new-kavita.example" to FakeKavitaReadingListApi(
                summaries = listOf(readingListSummary(remoteId = "2", title = "New Server List", itemCount = 1)),
            ),
        )
        val credentialStore = FakeKavitaCredentialStore(
            storedSession(
                baseUrl = "https://old-kavita.example",
                username = "old-reader",
            ),
        )
        val controller = AppKavitaReadingListController(
            kavitaApiFactory = KavitaApiFactory { baseUrl -> fakeApisByBaseUrl.getValue(baseUrl) },
            kavitaCredentialStore = credentialStore,
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
        )
        var state = AppUiState()
        val updateState: AppUiStateUpdater = { reducer -> state = reducer(state) }
        val observerJob = Job()
        val observerScope = CoroutineScope(coroutineContext + observerJob)
        try {
            controller.observeStoredSession(
                scope = observerScope,
                updateState = updateState,
            )
            advanceUntilIdle()

            val oldRefresh = backgroundScope.async {
                controller.refreshAfterKavitaSync(
                    stateProvider = { state },
                    updateState = updateState,
                )
            }
            oldRefreshStarted.await()

            credentialStore.save(
                storedSession(
                    baseUrl = "https://new-kavita.example",
                    username = "new-reader",
                ),
            )
            advanceUntilIdle()

            releaseOldRefresh.complete(Unit)
            oldRefresh.await()
            advanceUntilIdle()

            assertEquals(listOf("New Server List"), state.kavitaReadingLists.summaries.map(ReadingListSummary::title))
        } finally {
            observerJob.cancel()
            releaseOldRefresh.complete(Unit)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stale summary response after stored session is cleared is ignored`() = runTest {
        val oldRefreshStarted = CompletableDeferred<Unit>()
        val releaseOldRefresh = CompletableDeferred<Unit>()
        var oldSummaryRequests = 0
        val fakeApi = FakeKavitaReadingListApi(
            summaries = listOf(readingListSummary(remoteId = "1", title = "Disconnected List", itemCount = 1)),
            onSummariesRequested = {
                oldSummaryRequests += 1
                if (oldSummaryRequests == 2) {
                    oldRefreshStarted.complete(Unit)
                    releaseOldRefresh.await()
                }
            },
        )
        val credentialStore = FakeKavitaCredentialStore(storedSession())
        val controller = controller(
            fakeApi = fakeApi,
            credentialStore = credentialStore,
        )
        var state = AppUiState()
        val updateState: AppUiStateUpdater = { reducer -> state = reducer(state) }
        val observerJob = Job()
        val observerScope = CoroutineScope(coroutineContext + observerJob)
        try {
            controller.observeStoredSession(
                scope = observerScope,
                updateState = updateState,
            )
            advanceUntilIdle()

            val oldRefresh = backgroundScope.async {
                controller.refreshAfterKavitaSync(
                    stateProvider = { state },
                    updateState = updateState,
                )
            }
            oldRefreshStarted.await()

            credentialStore.clear()
            advanceUntilIdle()

            releaseOldRefresh.complete(Unit)
            oldRefresh.await()
            advanceUntilIdle()

            assertEquals(AppKavitaReadingListState(), state.kavitaReadingLists)
        } finally {
            observerJob.cancel()
            releaseOldRefresh.complete(Unit)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stale item response after stored session is cleared is ignored`() = runTest {
        val readingListId = SourceType.KAVITA.remoteReadingListId("42")
        val oldRequestStarted = CompletableDeferred<Unit>()
        val releaseOldRequest = CompletableDeferred<Unit>()
        val fakeApi = FakeKavitaReadingListApi(
            summaries = listOf(readingListSummary(remoteId = "42", title = "Disconnected List", itemCount = 1)),
            itemsByRemoteId = mapOf(
                "42" to listOf(readingListItem(readingListRemoteId = "42", itemRemoteId = "old")),
            ),
            onItemsRequested = {
                oldRequestStarted.complete(Unit)
                releaseOldRequest.await()
            },
        )
        val credentialStore = FakeKavitaCredentialStore(storedSession())
        val controller = controller(
            fakeApi = fakeApi,
            credentialStore = credentialStore,
        )
        var state = AppUiState()
        val updateState: AppUiStateUpdater = { reducer -> state = reducer(state) }
        val observerJob = Job()
        val observerScope = CoroutineScope(coroutineContext + observerJob)
        try {
            controller.observeStoredSession(
                scope = observerScope,
                updateState = updateState,
            )
            advanceUntilIdle()

            val oldSelection = backgroundScope.async {
                controller.onReadingListSelected(
                    readingListId = readingListId,
                    currentState = state,
                    updateState = updateState,
                    scope = this,
                )
            }
            oldRequestStarted.await()

            credentialStore.clear()
            advanceUntilIdle()

            releaseOldRequest.complete(Unit)
            oldSelection.await()
            advanceUntilIdle()

            assertEquals(AppKavitaReadingListState(), state.kavitaReadingLists)
        } finally {
            observerJob.cancel()
            releaseOldRequest.complete(Unit)
        }
    }

    private fun controller(
        fakeApi: FakeKavitaReadingListApi,
        credentialStore: KavitaCredentialStore = FakeKavitaCredentialStore(storedSession()),
    ): AppKavitaReadingListController = AppKavitaReadingListController(
        kavitaApiFactory = KavitaApiFactory { fakeApi },
        kavitaCredentialStore = credentialStore,
        remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
    )
}

private fun storedSession(
    baseUrl: String = "https://kavita.example",
    username: String = "reader",
): KavitaStoredSession = KavitaStoredSession(
    displayName = "Kavita",
    baseUrl = baseUrl,
    username = username,
    accessToken = "access-token",
    refreshToken = "refresh-token",
    apiKey = "api-key",
)

private class FakeKavitaReadingListApi(
    var summaries: List<ReadingListSummary> = emptyList(),
    var itemsByRemoteId: Map<String, List<ReadingListItemRef>> = emptyMap(),
    var collections: List<CollectionSummary> = emptyList(),
    var collectionSeriesByRemoteId: Map<String, List<CollectionSeriesRef>> = emptyMap(),
    val onSummariesRequested: suspend () -> Unit = {},
    val onItemsRequested: suspend () -> Unit = {},
    val onCollectionsRequested: suspend () -> Unit = {},
    val onCollectionSeriesRequested: suspend () -> Unit = {},
) : KavitaApi {
    val itemRequests = mutableListOf<String>()
    val collectionRequests = mutableListOf<String>()

    override suspend fun login(
        username: String,
        password: String,
    ): KavitaSession = error("Not needed in test.")

    override suspend fun refreshSession(
        refreshToken: String,
        accessToken: String?,
        apiKey: String?,
    ): KavitaSession = error("Not needed in test.")

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = emptyList()

    override suspend fun getSeries(
        accessToken: String,
        apiKey: String?,
    ): List<KavitaSeriesEntry> = emptyList()

    override suspend fun getReadingLists(
        accessToken: String,
        apiKey: String?,
    ): List<ReadingListSummary> {
        onSummariesRequested()
        return summaries
    }

    override suspend fun getReadingListItems(
        accessToken: String,
        readingListRemoteId: String,
    ): List<ReadingListItemRef> {
        itemRequests += readingListRemoteId
        onItemsRequested()
        return itemsByRemoteId[readingListRemoteId].orEmpty()
    }

    override suspend fun getCollections(
        accessToken: String,
        apiKey: String?,
    ): List<CollectionSummary> {
        onCollectionsRequested()
        return collections
    }

    override suspend fun getCollectionSeries(
        accessToken: String,
        collectionRemoteId: String,
    ): List<CollectionSeriesRef> {
        collectionRequests += collectionRemoteId
        onCollectionSeriesRequested()
        return collectionSeriesByRemoteId[collectionRemoteId].orEmpty()
    }

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: KavitaSeriesEntry,
    ): List<KavitaChapterCatalogEntry> = emptyList()

    override suspend fun getProgress(
        accessToken: String,
        chapterId: String,
    ): KavitaProgressDto = error("Not needed in test.")

    override suspend fun getChapterInfo(
        accessToken: String,
        chapterId: String,
        includeDimensions: Boolean,
    ): KavitaChapterInfoDto = error("Not needed in test.")

    override suspend fun updateProgress(
        accessToken: String,
        progress: KavitaProgressUpdate,
    ) = Unit

    override suspend fun downloadPageImage(
        accessToken: String,
        chapterId: String,
        pageIndex: Int,
        apiKey: String?,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadPageThumbnail(
        accessToken: String,
        chapterId: String,
        pageNumber: Int,
        apiKey: String?,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadCover(
        accessToken: String,
        coverUrl: String,
        apiKey: String?,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadChapter(
        accessToken: String,
        chapterId: String,
        apiKey: String?,
        destination: File,
    ) = Unit
}

private fun readingListSummary(
    remoteId: String,
    title: String,
    itemCount: Int,
): ReadingListSummary = ReadingListSummary(
    id = SourceType.KAVITA.remoteReadingListId(remoteId),
    sourceType = SourceType.KAVITA,
    remoteId = remoteId,
    title = title,
    itemCount = itemCount,
)

private fun collectionSummary(
    remoteId: String,
    title: String,
    itemCount: Int,
): CollectionSummary = CollectionSummary(
    id = SourceType.KAVITA.remoteCollectionId(remoteId),
    sourceType = SourceType.KAVITA,
    remoteId = remoteId,
    title = title,
    itemCount = itemCount,
)

private fun readingListItem(
    readingListRemoteId: String,
    itemRemoteId: String,
    order: Int = 0,
): ReadingListItemRef = ReadingListItemRef(
    readingListId = SourceType.KAVITA.remoteReadingListId(readingListRemoteId),
    sourceType = SourceType.KAVITA,
    itemId = SourceType.KAVITA.remoteItemId(itemRemoteId),
    order = order,
)

private fun collectionSeries(
    collectionRemoteId: String,
    seriesKey: String,
    order: Int = 0,
): CollectionSeriesRef = CollectionSeriesRef(
    collectionId = SourceType.KAVITA.remoteCollectionId(collectionRemoteId),
    sourceType = SourceType.KAVITA,
    seriesKey = seriesKey,
    order = order,
)
