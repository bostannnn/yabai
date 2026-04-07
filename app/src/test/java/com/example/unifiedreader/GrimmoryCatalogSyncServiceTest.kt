package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ServerEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryEpubProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmoryProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.grimmory.GrimmoryUnauthorizedException
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GrimmoryCatalogSyncServiceTest {
    @Test
    fun `sync fetches grimmory catalog and writes remote room entities`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter()
        val api = FakeGrimmoryApi(
            libraries = listOf(
                Library(
                    id = "g-books",
                    serverId = "grimmory-1",
                    sourceType = SourceType.GRIMMORY,
                    title = "Books",
                ),
            ),
            booksByLibraryId = mapOf(
                "g-books" to listOf(
                    ReadableItem(
                        id = "book-1",
                        serverId = "grimmory-1",
                        sourceType = SourceType.GRIMMORY,
                        libraryId = "g-books",
                        title = "Book 1",
                        format = FormatType.EPUB,
                    ),
                ),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _, _ -> api },
            remoteCatalogWriter = writer,
            nowEpochMillis = { 1775046600000L },
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                serverId = "grimmory-1",
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                password = "password",
            ),
        )

        assertEquals("user" to "password", api.lastLogin)
        assertEquals(1, result.libraryCount)
        assertEquals(1, result.itemCount)
        assertEquals(mapOf(FormatType.EPUB to 1), result.supportedFormatCounts)
        assertEquals("access-token", result.session?.accessToken)
        assertEquals("refresh-token", result.session?.refreshToken)
        assertEquals("https://grimmory.example", writer.server?.baseUrl)
        assertEquals(listOf("g-books"), writer.libraries.map { it.id })
        assertEquals(listOf("book-1"), writer.items.map { it.id })
        assertEquals(1775046600000L, writer.items.single().updatedAtEpochMillis)
    }

    @Test
    fun `sync can reuse saved access token without logging in`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter()
        val api = FakeGrimmoryApi(
            libraries = listOf(
                Library(
                    id = "g-books",
                    serverId = "grimmory-1",
                    sourceType = SourceType.GRIMMORY,
                    title = "Books",
                ),
            ),
            booksByLibraryId = mapOf("g-books" to emptyList()),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _, _ -> api },
            remoteCatalogWriter = writer,
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                serverId = "grimmory-1",
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )

        assertEquals(null, api.lastLogin)
        assertEquals(listOf("saved-access-token"), api.libraryAccessTokens)
        assertEquals(listOf("saved-access-token"), api.bookAccessTokens)
        assertEquals(null, result.session)
    }

    @Test
    fun `sync refreshes saved token when catalog fetch is unauthorized`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter()
        val api = FakeGrimmoryApi(
            libraries = listOf(
                Library(
                    id = "g-books",
                    serverId = "grimmory-1",
                    sourceType = SourceType.GRIMMORY,
                    title = "Books",
                ),
            ),
            booksByLibraryId = mapOf("g-books" to emptyList()),
        ).apply {
            unauthorizedLibraryTokens += "expired-access-token"
            refreshedSession = GrimmorySession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
            )
        }
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _, _ -> api },
            remoteCatalogWriter = writer,
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                serverId = "grimmory-1",
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "expired-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )

        assertEquals(listOf("expired-access-token", "fresh-access-token"), api.libraryAccessTokens)
        assertEquals(listOf("fresh-access-token"), api.bookAccessTokens)
        assertEquals(listOf("saved-refresh-token"), api.refreshTokens)
        assertEquals("fresh-access-token", result.session?.accessToken)
        assertEquals("fresh-refresh-token", result.session?.refreshToken)
    }

    @Test
    fun `sync refreshes saved token when progress upload is unauthorized`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter()
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = ReadableItem(
            id = "42",
            serverId = "grimmory-1",
            sourceType = SourceType.GRIMMORY,
            libraryId = "g-books",
            remoteFileId = "77",
            remoteFileType = "PDF",
            title = "Remote PDF",
            format = FormatType.PDF,
            pageCount = 120,
        )
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 9,
                percent = 0.4,
            ),
            syncState = com.example.unifiedreader.core.model.SyncState.DIRTY,
            pageCount = item.pageCount,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(
                Library(
                    id = "g-books",
                    serverId = "grimmory-1",
                    sourceType = SourceType.GRIMMORY,
                    title = "Books",
                ),
            ),
            booksByLibraryId = mapOf("g-books" to listOf(item)),
        ).apply {
            unauthorizedProgressTokens += "expired-access-token"
            refreshedSession = GrimmorySession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
            )
        }
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _, _ -> api },
            remoteCatalogWriter = writer,
            readerSessionStore = readerSessionStore,
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                serverId = "grimmory-1",
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "expired-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )

        assertEquals(listOf("expired-access-token"), api.libraryAccessTokens)
        assertEquals(listOf("expired-access-token"), api.bookAccessTokens)
        assertEquals(listOf("expired-access-token", "fresh-access-token"), api.progressAccessTokens)
        assertEquals(listOf("saved-refresh-token"), api.refreshTokens)
        val syncedProgress = readerSessionStore.getProgress(item)
        assertEquals(LocatorType.PAGE, syncedProgress?.locatorType)
        assertEquals(9, syncedProgress?.page)
        assertEquals(0.4, syncedProgress?.percent)
        assertEquals(com.example.unifiedreader.core.model.SyncState.CLEAN, syncedProgress?.syncState)
        assertEquals("42", api.progressUpdates.single().bookId)
        assertEquals("77", api.progressUpdates.single().bookFileId)
        assertEquals("fresh-access-token", result.session?.accessToken)
        assertEquals("fresh-refresh-token", result.session?.refreshToken)
    }

    @Test
    fun `sync clears progress for previously active grimmory server`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter().apply {
            sourceItems = mapOf(
                SourceType.GRIMMORY to listOf(
                    ReadableItem(
                        id = "legacy-book",
                        serverId = "grimmory-old",
                        sourceType = SourceType.GRIMMORY,
                        libraryId = "legacy-library",
                        title = "Legacy Book",
                        format = FormatType.EPUB,
                    ),
                ),
            )
        }
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val legacyItem = writer.sourceItems.getValue(SourceType.GRIMMORY).single()
        readerSessionStore.saveProgress(
            item = legacyItem,
            locator = ReaderLocator(
                locatorType = LocatorType.PERCENT,
                percent = 0.75,
            ),
            syncState = com.example.unifiedreader.core.model.SyncState.CLEAN,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(
                Library(
                    id = "g-books",
                    serverId = "grimmory-new",
                    sourceType = SourceType.GRIMMORY,
                    title = "Books",
                ),
            ),
            booksByLibraryId = mapOf("g-books" to emptyList()),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _, _ -> api },
            remoteCatalogWriter = writer,
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                serverId = "grimmory-new",
                displayName = "New Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        assertEquals(null, readerSessionStore.getProgress(legacyItem))
    }
}

private class RecordingRemoteCatalogWriter : RemoteCatalogWriter {
    var server: ServerEntity? = null
    var libraries: List<LibraryEntity> = emptyList()
    var items: List<ReadableItemEntity> = emptyList()
    var sourceItems: Map<SourceType, List<ReadableItem>> = emptyMap()

    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = sourceItems[sourceType].orEmpty()

    override suspend fun replaceServerCatalog(
        server: ServerEntity,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    ) {
        this.server = server
        this.libraries = libraries
        this.items = items
    }
}

private class FakeGrimmoryApi(
    private val libraries: List<Library>,
    private val booksByLibraryId: Map<String, List<ReadableItem>>,
) : GrimmoryApi {
    var lastLogin: Pair<String, String>? = null
    var refreshedSession = GrimmorySession(
        accessToken = "refreshed-access-token",
        refreshToken = "refreshed-refresh-token",
    )
    val libraryAccessTokens = mutableListOf<String>()
    val bookAccessTokens = mutableListOf<String>()
    val progressAccessTokens = mutableListOf<String>()
    val refreshTokens = mutableListOf<String>()
    val progressUpdates = mutableListOf<GrimmoryProgressUpdate>()
    val unauthorizedLibraryTokens = mutableSetOf<String>()
    val unauthorizedProgressTokens = mutableSetOf<String>()

    override suspend fun login(
        username: String,
        password: String,
    ): GrimmorySession {
        lastLogin = username to password
        return GrimmorySession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
        )
    }

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> {
        libraryAccessTokens += accessToken
        if (accessToken in unauthorizedLibraryTokens) {
            throw GrimmoryUnauthorizedException()
        }
        return libraries
    }

    override suspend fun refreshSession(
        refreshToken: String,
    ): GrimmorySession {
        refreshTokens += refreshToken
        return refreshedSession
    }

    override suspend fun getBooks(
        accessToken: String,
        libraryId: String,
    ): List<ReadableItem> {
        bookAccessTokens += accessToken
        return booksByLibraryId.getValue(libraryId)
    }

    override fun bookContentUrl(
        bookId: String,
        bookType: String?,
    ): String = error("Not used")

    override fun bookCoverUrl(
        bookId: String,
    ): String = error("Not used")

    override suspend fun updateEpubProgress(
        accessToken: String,
        progress: GrimmoryEpubProgressUpdate,
    ) = error("Not used")

    override suspend fun updateProgress(
        accessToken: String,
        progress: GrimmoryProgressUpdate,
    ) {
        progressAccessTokens += accessToken
        if (accessToken in unauthorizedProgressTokens) {
            throw GrimmoryUnauthorizedException()
        }
        progressUpdates += progress
    }
}

private class GrimmoryTestReaderSessionStore : ReaderSessionStore {
    private val progressByItemId = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    private val sessionByItemId = MutableStateFlow<Map<String, ReaderSessionSnapshot>>(emptyMap())

    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressByItemId
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionByItemId

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressByItemId.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        syncState: com.example.unifiedreader.core.model.SyncState,
        pageCount: Int?,
    ) {
        progressByItemId.value = progressByItemId.value + (
            item.id to ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = locator.locatorType,
                page = locator.page,
                cfi = locator.cfi,
                percent = locator.percent,
                isFinished = isFinished,
                syncState = syncState,
            )
        )
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionByItemId.value[item.id]

    override fun updateSession(
        item: ReadableItem,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionByItemId.value[item.id] ?: ReaderSessionSnapshot(itemId = item.id)
        sessionByItemId.value = sessionByItemId.value + (
            item.id to current.copy(
                pageCount = pageCount ?: current.pageCount,
                preferences = current.preferences + preferences,
            )
        )
    }

    override fun clearItem(itemId: String) {
        progressByItemId.value = progressByItemId.value - itemId
        sessionByItemId.value = sessionByItemId.value - itemId
    }
}
