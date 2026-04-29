package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
import com.example.unifiedreader.data.grimmory.GrimmoryProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GrimmoryProgressSyncServiceTest {
    @Test
    fun `push pending progress only uploads items with remote Grimmory ids`() = runBlocking {
        val activeItem = grimmoryItem(remoteId = "active-book")
        val orphanedItem = grimmoryItem(remoteId = null, id = "orphan-book")
        val readerSessionStore = ProgressSyncTestReaderSessionStore().apply {
            saveProgress(
                item = activeItem,
                locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.55),
                syncState = SyncState.DIRTY,
                updatedAtEpochMillis = 10L,
            )
            saveProgress(
                item = orphanedItem,
                locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.25),
                syncState = SyncState.DIRTY,
                updatedAtEpochMillis = 20L,
            )
        }
        val api = RecordingGrimmoryApi()
        val service = GrimmoryProgressSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = ProgressSyncTestGrimmoryCredentialStore(
                GrimmoryStoredSession(
                    displayName = "Home Grimmory",
                    baseUrl = "https://grimmory.example",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
            readerSessionStore = readerSessionStore,
        )

        service.pushPendingProgress(listOf(activeItem, orphanedItem))

        assertEquals(listOf("active-book"), api.progressUpdates.map(GrimmoryProgressUpdate::bookId))
        assertEquals(SyncState.CLEAN, readerSessionStore.getProgress(activeItem)?.syncState)
        assertEquals(SyncState.DIRTY, readerSessionStore.getProgress(orphanedItem)?.syncState)
    }

    @Test
    fun `sync item ignores grimmory item without remote id`() = runBlocking {
        val orphanedItem = grimmoryItem(remoteId = null, id = "orphan-book")
        val api = RecordingGrimmoryApi(
            bookEntriesById = mapOf(
                "orphan-book" to GrimmoryBookCatalogEntry(
                    item = grimmoryItem(remoteId = "orphan-book"),
                    progress = ReadingProgress(
                        itemId = SourceType.GRIMMORY.remoteItemId("orphan-book"),
                        locatorType = LocatorType.PERCENT,
                        percent = 0.9,
                        updatedAtEpochMillis = 50L,
                    ),
                ),
            ),
        )
        val readerSessionStore = ProgressSyncTestReaderSessionStore()
        val service = GrimmoryProgressSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = ProgressSyncTestGrimmoryCredentialStore(
                GrimmoryStoredSession(
                    displayName = "Home Grimmory",
                    baseUrl = "https://grimmory.example",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
            readerSessionStore = readerSessionStore,
        )

        service.syncItem(orphanedItem)

        assertEquals(emptyList<String>(), api.requestedBookIds)
        assertNull(readerSessionStore.getProgress(orphanedItem))
    }

    @Test
    fun `sync item does not let remote unread reset clear dirty local progress`() = runBlocking {
        val item = grimmoryItem(remoteId = "book-1")
        val readerSessionStore = ProgressSyncTestReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.5),
                syncState = SyncState.DIRTY,
                updatedAtEpochMillis = 10L,
            )
        }
        val api = RecordingGrimmoryApi(
            bookEntriesById = mapOf(
                "book-1" to GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    isExplicitUnreadReset = true,
                ),
            ),
        )
        val service = GrimmoryProgressSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = ProgressSyncTestGrimmoryCredentialStore(
                GrimmoryStoredSession(
                    displayName = "Home Grimmory",
                    baseUrl = "https://grimmory.example",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
            readerSessionStore = readerSessionStore,
        )

        service.syncItem(item)

        val snapshot = checkNotNull(readerSessionStore.getProgress(item))
        assertEquals(SyncState.DIRTY, snapshot.syncState)
        assertEquals(0.5, snapshot.percent)
    }

    @Test
    fun `failed Grimmory upload does not strand progress in syncing state`() = runBlocking {
        val item = grimmoryItem(remoteId = "book-1")
        val readerSessionStore = ProgressSyncTestReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.5),
                syncState = SyncState.DIRTY,
                updatedAtEpochMillis = 10L,
            )
        }
        val api = RecordingGrimmoryApi(
            updateError = IllegalStateException("boom"),
        )
        val service = GrimmoryProgressSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = ProgressSyncTestGrimmoryCredentialStore(
                GrimmoryStoredSession(
                    displayName = "Home Grimmory",
                    baseUrl = "https://grimmory.example",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
            readerSessionStore = readerSessionStore,
        )

        service.pushPendingProgress(listOf(item))

        assertEquals(SyncState.FAILED, readerSessionStore.getProgress(item)?.syncState)
        assertNotEquals(SyncState.SYNCING, readerSessionStore.getProgress(item)?.syncState)
    }

    @Test
    fun `newer local progress is not marked failed after Grimmory upload error`() = runBlocking {
        val item = grimmoryItem(remoteId = "book-1")
        val readerSessionStore = ProgressSyncTestReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.5),
                syncState = SyncState.DIRTY,
                updatedAtEpochMillis = 10L,
            )
        }
        val api = RecordingGrimmoryApi(
            updateError = IllegalStateException("boom"),
            beforeUpdateThrows = {
                readerSessionStore.saveProgress(
                    item = item,
                    locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
                    syncState = SyncState.DIRTY,
                    updatedAtEpochMillis = 20L,
                )
            },
        )
        val service = GrimmoryProgressSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = ProgressSyncTestGrimmoryCredentialStore(
                GrimmoryStoredSession(
                    displayName = "Home Grimmory",
                    baseUrl = "https://grimmory.example",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
            readerSessionStore = readerSessionStore,
        )

        service.pushPendingProgress(listOf(item))

        val snapshot = checkNotNull(readerSessionStore.getProgress(item))
        assertEquals(0.75, snapshot.percent)
        assertEquals(SyncState.DIRTY, snapshot.syncState)
        assertEquals(20L, snapshot.updatedAtEpochMillis)
    }

    @Test
    fun `finished Grimmory progress uploads explicit finished state`() = runBlocking {
        val item = grimmoryItem(remoteId = "book-1")
        val readerSessionStore = ProgressSyncTestReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 1.0),
                isFinished = true,
                syncState = SyncState.DIRTY,
                updatedAtEpochMillis = 123L,
            )
        }
        val api = RecordingGrimmoryApi()
        val service = GrimmoryProgressSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = ProgressSyncTestGrimmoryCredentialStore(
                GrimmoryStoredSession(
                    displayName = "Home Grimmory",
                    baseUrl = "https://grimmory.example",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
            readerSessionStore = readerSessionStore,
        )

        service.pushPendingProgress(listOf(item))

        val update = api.progressUpdates.single()
        assertEquals(true, update.isFinished)
        assertEquals(123L, update.updatedAtEpochMillis)
        assertEquals(1.0, update.percent, 0.0001)
        assertEquals(SyncState.CLEAN, readerSessionStore.getProgress(item)?.syncState)
    }

    @Test
    fun `unread reset Grimmory progress uses reset endpoint`() = runBlocking {
        val item = grimmoryItem(remoteId = "book-1")
        val readerSessionStore = ProgressSyncTestReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.0),
                isExplicitUnreadReset = true,
                syncState = SyncState.DIRTY,
                updatedAtEpochMillis = 456L,
            )
        }
        val api = RecordingGrimmoryApi()
        val service = GrimmoryProgressSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = ProgressSyncTestGrimmoryCredentialStore(
                GrimmoryStoredSession(
                    displayName = "Home Grimmory",
                    baseUrl = "https://grimmory.example",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
            readerSessionStore = readerSessionStore,
        )

        service.pushPendingProgress(listOf(item))

        assertEquals(emptyList<GrimmoryProgressUpdate>(), api.progressUpdates)
        assertEquals(listOf("book-1"), api.resetProgressBookIds)
        assertNull(readerSessionStore.getProgress(item))
    }

    @Test
    fun `cancelled Grimmory upload rethrows without marking progress failed`() = runBlocking {
        val item = grimmoryItem(remoteId = "book-1")
        val readerSessionStore = ProgressSyncTestReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.5),
                syncState = SyncState.DIRTY,
                updatedAtEpochMillis = 10L,
            )
        }
        val service = GrimmoryProgressSyncService(
            grimmoryApiFactory = GrimmoryApiFactory {
                RecordingGrimmoryApi(updateError = CancellationException("cancelled"))
            },
            grimmoryCredentialStore = ProgressSyncTestGrimmoryCredentialStore(
                GrimmoryStoredSession(
                    displayName = "Home Grimmory",
                    baseUrl = "https://grimmory.example",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
            readerSessionStore = readerSessionStore,
        )

        try {
            service.pushPendingProgress(listOf(item))
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }

        val snapshot = checkNotNull(readerSessionStore.getProgress(item))
        assertEquals(SyncState.DIRTY, snapshot.syncState)
    }
}

private fun grimmoryItem(
    remoteId: String?,
    id: String = remoteId?.let(SourceType.GRIMMORY::remoteItemId) ?: "missing-remote-id",
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.GRIMMORY,
    libraryId = SourceType.GRIMMORY.remoteLibraryId("books"),
    remoteId = remoteId,
    remoteFileId = remoteId?.let { "$it-file" },
    remoteFileType = FormatType.EPUB.name,
    title = remoteId ?: id,
    format = FormatType.EPUB,
)

private class ProgressSyncTestGrimmoryCredentialStore(
    initialSession: GrimmoryStoredSession? = null,
) : GrimmoryCredentialStore {
    private val sessionState = MutableStateFlow(initialSession)

    override val sessionFlow: StateFlow<GrimmoryStoredSession?> = sessionState

    override suspend fun save(
        session: GrimmoryStoredSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}

private class ProgressSyncTestReaderSessionStore : ReaderSessionStore {
    private val progressState = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    private val sessionState = MutableStateFlow<Map<String, ReaderSessionSnapshot>>(emptyMap())

    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressState
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionState

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        progressState.value = progressState.value + (
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

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionState.value[item.id]

    override suspend fun replaceProgressIfCurrent(
        expected: ReaderProgressSnapshot,
        replacement: ReaderProgressSnapshot?,
    ): Boolean {
        val current = progressState.value[expected.itemId] ?: return false
        if (current != expected) return false
        progressState.value = if (replacement == null) {
            progressState.value - expected.itemId
        } else {
            progressState.value + (expected.itemId to replacement)
        }
        return true
    }

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val existing = sessionState.value[itemId] ?: ReaderSessionSnapshot(itemId = itemId)
        sessionState.value = sessionState.value + (
            itemId to existing.copy(
                pageCount = pageCount ?: existing.pageCount,
                preferences = existing.preferences + preferences,
            )
        )
    }

    override fun clearProgress(itemId: String) {
        progressState.value = progressState.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
        sessionState.value = sessionState.value - itemId
    }

    override fun restoreItem(itemId: String) = Unit
}

private class RecordingGrimmoryApi(
    private val bookEntriesById: Map<String, GrimmoryBookCatalogEntry> = emptyMap(),
    private val updateError: Throwable? = null,
    private val beforeUpdateThrows: (suspend () -> Unit)? = null,
) : GrimmoryApi {
    val progressUpdates = mutableListOf<GrimmoryProgressUpdate>()
    val resetProgressBookIds = mutableListOf<String>()
    val requestedBookIds = mutableListOf<String>()

    override suspend fun login(
        username: String,
        password: String,
    ): GrimmorySession = error("Unexpected login call in test.")

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = emptyList()

    override suspend fun getBooks(
        accessToken: String,
        libraryId: String,
    ): List<ReadableItem> = emptyList()

    override suspend fun getBookEntry(
        accessToken: String,
        bookId: String,
    ): GrimmoryBookCatalogEntry? {
        requestedBookIds += bookId
        return bookEntriesById[bookId]
    }

    override fun bookContentUrl(
        bookId: String,
        bookType: String?,
    ): String = ""

    override fun bookCoverUrl(
        bookId: String,
    ): String = ""

    override suspend fun updateProgress(
        accessToken: String,
        progress: GrimmoryProgressUpdate,
    ) {
        beforeUpdateThrows?.invoke()
        updateError?.let { throw it }
        progressUpdates += progress
    }

    override suspend fun resetBookProgress(
        accessToken: String,
        bookId: String,
    ) {
        beforeUpdateThrows?.invoke()
        updateError?.let { throw it }
        resetProgressBookIds += bookId
    }
}
