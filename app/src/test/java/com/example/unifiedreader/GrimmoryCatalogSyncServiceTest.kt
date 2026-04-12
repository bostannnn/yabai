package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
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
import org.junit.Assert.assertTrue
import org.junit.Test

class GrimmoryCatalogSyncServiceTest {
    @Test
    fun `sync fetches grimmory catalog and writes remote room entities`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter()
        val library = grimmoryLibrary("g-books")
        val item = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books")
        val api = FakeGrimmoryApi(
            libraries = listOf(library),
            allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = writer,
            nowEpochMillis = { 1775046600000L },
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
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
        assertEquals(SourceType.GRIMMORY, writer.replacedSourceType)
        assertEquals(listOf(SourceType.GRIMMORY.remoteLibraryId("g-books")), writer.libraries.map(LibraryEntity::id))
        assertEquals(listOf(SourceType.GRIMMORY.remoteItemId("book-1")), writer.items.map(ReadableItemEntity::id))
        assertEquals(1775046600000L, writer.items.single().updatedAtEpochMillis)
    }

    @Test
    fun `sync can reuse saved access token without logging in`() = runBlocking {
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = emptyList(),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
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
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = emptyList(),
        ).apply {
            unauthorizedLibraryTokens += "expired-access-token"
            refreshedSession = GrimmorySession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
            )
        }
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
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
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(
            remoteId = "42",
            libraryRemoteId = "g-books",
            format = FormatType.PDF,
            remoteFileId = "77",
            remoteFileType = "PDF",
            pageCount = 120,
            title = "Remote PDF",
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
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = ReadingProgress(
                        itemId = item.id,
                        locatorType = LocatorType.PAGE,
                        page = 9,
                        percent = 0.4,
                        updatedAtEpochMillis = 1L,
                    ),
                ),
            ),
        ).apply {
            unauthorizedProgressTokens += "expired-access-token"
            refreshedSession = GrimmorySession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
            )
        }
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
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
    fun `sync pulls remote progress from grimmory books endpoint`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(
            remoteId = "42",
            libraryRemoteId = "g-books",
            remoteFileId = "77",
            remoteFileType = "EPUB",
            pageCount = 321,
            title = "Remote EPUB",
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = ReadingProgress(
                        itemId = item.id,
                        locatorType = LocatorType.PERCENT,
                        percent = 0.42,
                        updatedAtEpochMillis = 1_775_046_600_000L,
                    ),
                ),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        val syncedProgress = readerSessionStore.getProgress(item)
        assertEquals(listOf("saved-access-token"), api.bookAccessTokens)
        assertEquals(LocatorType.PERCENT, syncedProgress?.locatorType)
        assertEquals(0.42, syncedProgress?.percent)
        assertEquals(com.example.unifiedreader.core.model.SyncState.CLEAN, syncedProgress?.syncState)
    }

    @Test
    fun `sync clears progress for grimmory item no longer in catalog`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter().apply {
            sourceItems = mapOf(
                SourceType.GRIMMORY to listOf(
                    grimmoryItem(remoteId = "legacy-book", libraryRemoteId = "legacy-library", title = "Legacy Book"),
                ),
            )
        }
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val legacyItem = writer.sourceItems.getValue(SourceType.GRIMMORY).single()
        readerSessionStore.saveProgress(
            item = legacyItem,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.CLEAN,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = emptyList(),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = writer,
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "New Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        assertEquals(null, readerSessionStore.getProgress(legacyItem))
    }

    @Test
    fun `sync keeps clean progress when grimmory omits book progress`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", title = "Remote EPUB")
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.CLEAN,
        )
        readerSessionStore.updateSession(
            item = item,
            pageCount = 321,
            preferences = mapOf("theme" to "dark"),
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        val progress = readerSessionStore.getProgress(item)
        assertEquals(0.75, progress?.percent)
        assertEquals(com.example.unifiedreader.core.model.SyncState.CLEAN, progress?.syncState)
        val session = readerSessionStore.getSession(item)
        assertEquals(321, session?.pageCount)
        assertEquals("dark", session?.preferences?.get("theme"))
        assertEquals(emptyList<GrimmoryProgressUpdate>(), api.progressUpdates)
    }

    @Test
    fun `sync clears clean progress when grimmory marks book unread`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", title = "Remote EPUB")
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.CLEAN,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    isExplicitUnreadReset = true,
                ),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        assertEquals(null, readerSessionStore.getProgress(item))
        assertEquals(emptyList<GrimmoryProgressUpdate>(), api.progressUpdates)
    }

    @Test
    fun `sync keeps dirty progress when grimmory omits book progress`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", title = "Remote EPUB")
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.DIRTY,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        val progress = readerSessionStore.getProgress(item)
        assertEquals(0.75, progress?.percent)
        assertEquals(com.example.unifiedreader.core.model.SyncState.CLEAN, progress?.syncState)
        assertEquals(1, api.progressUpdates.size)
    }

    @Test
    fun `sync clears dirty progress when grimmory marks book unread`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", title = "Remote EPUB")
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.DIRTY,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    isExplicitUnreadReset = true,
                ),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        assertEquals(null, readerSessionStore.getProgress(item))
        assertEquals(emptyList<GrimmoryProgressUpdate>(), api.progressUpdates)
    }
}

private fun grimmoryLibrary(
    remoteId: String,
    title: String = "Books",
): Library = Library(
    id = SourceType.GRIMMORY.remoteLibraryId(remoteId),
    sourceType = SourceType.GRIMMORY,
    title = title,
    remoteId = remoteId,
)

private fun grimmoryItem(
    remoteId: String,
    libraryRemoteId: String,
    format: FormatType = FormatType.EPUB,
    remoteFileId: String? = null,
    remoteFileType: String? = format.name,
    pageCount: Int? = null,
    title: String = "Book",
): ReadableItem = ReadableItem(
    id = SourceType.GRIMMORY.remoteItemId(remoteId),
    sourceType = SourceType.GRIMMORY,
    libraryId = SourceType.GRIMMORY.remoteLibraryId(libraryRemoteId),
    remoteId = remoteId,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format,
    pageCount = pageCount,
)

private class RecordingRemoteCatalogWriter : RemoteCatalogWriter {
    var replacedSourceType: SourceType? = null
    var libraries: List<LibraryEntity> = emptyList()
    var items: List<ReadableItemEntity> = emptyList()
    var sourceItems: Map<SourceType, List<ReadableItem>> = emptyMap()

    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = sourceItems[sourceType].orEmpty()

    override suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    ) {
        replacedSourceType = sourceType
        this.libraries = libraries
        this.items = items
    }
}

private class FakeGrimmoryApi(
    private val libraries: List<Library>,
    private val allBookEntries: List<GrimmoryBookCatalogEntry>,
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
        return allBookEntries
            .map(GrimmoryBookCatalogEntry::item)
            .filter { item -> item.libraryId == SourceType.GRIMMORY.remoteLibraryId(libraryId) }
    }

    override suspend fun getAllBookEntries(
        accessToken: String,
    ): List<GrimmoryBookCatalogEntry> {
        bookAccessTokens += accessToken
        return allBookEntries
    }

    override suspend fun getBookEntry(
        accessToken: String,
        bookId: String,
    ): GrimmoryBookCatalogEntry? {
        bookAccessTokens += accessToken
        return allBookEntries.firstOrNull { entry -> entry.item.remoteId == bookId }
    }

    override fun bookContentUrl(
        bookId: String,
        bookType: String?,
    ): String = error("Not used")

    override fun bookCoverUrl(
        bookId: String,
    ): String = error("Not used")

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
        isExplicitUnreadReset: Boolean,
        syncState: com.example.unifiedreader.core.model.SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        progressByItemId.value = progressByItemId.value + (
            item.id to ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = locator.locatorType,
                page = locator.page,
                cfi = locator.cfi,
                percent = locator.percent,
                isFinished = isFinished,
                isExplicitUnreadReset = isExplicitUnreadReset,
                syncState = syncState,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        )
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionByItemId.value[item.id]

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionByItemId.value[itemId] ?: ReaderSessionSnapshot(itemId = itemId)
        sessionByItemId.value = sessionByItemId.value + (
            itemId to current.copy(
                pageCount = pageCount ?: current.pageCount,
                preferences = current.preferences + preferences,
            )
        )
    }

    override fun clearProgress(itemId: String) {
        progressByItemId.value = progressByItemId.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
        sessionByItemId.value = sessionByItemId.value - itemId
    }
}
