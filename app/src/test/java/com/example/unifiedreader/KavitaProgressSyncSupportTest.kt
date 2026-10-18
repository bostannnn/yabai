package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaChapterInfoDto
import com.example.unifiedreader.data.kavita.KavitaProgressDto
import com.example.unifiedreader.data.kavita.KavitaProgressUpdate
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaProgressSyncSupportTest {
    @Test
    fun `explicit unread reset uploads page zero and clears local progress`() = runTest {
        val item = ReadableItem(
            id = "kavita:item:42",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.EPUB,
            pageCount = 28,
        )
        val store = TestKavitaReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PERCENT,
                percent = 0.0,
            ),
            isFinished = false,
            isExplicitUnreadReset = true,
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingKavitaApi(
            chapterInfo = KavitaChapterInfoDto(
                libraryId = 5,
                seriesId = 12,
                volumeId = 7,
                pages = 28,
            ),
        )

        pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            initialApiKey = null,
            items = listOf(item),
            readerSessionStore = store,
        )

        assertEquals(1, api.progressUpdates.size)
        assertEquals(0, api.progressUpdates.single().pageNum)
        assertNull(store.progressByItemIdFlow.value[item.id])
    }

    @Test
    fun `unbuildable Kavita progress update stays dirty for retry`() = runTest {
        val item = ReadableItem(
            id = "kavita:item:42",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.EPUB,
            pageCount = 28,
        )
        val store = TestKavitaReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PERCENT,
                percent = 1.0,
            ),
            isFinished = true,
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingKavitaApi(
            chapterInfo = KavitaChapterInfoDto(
                libraryId = 5,
                volumeId = 7,
                pages = 28,
            ),
        )

        pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            initialApiKey = null,
            items = listOf(item),
            readerSessionStore = store,
        )

        val snapshot = checkNotNull(store.progressByItemIdFlow.value[item.id])
        assertTrue(api.progressUpdates.isEmpty())
        assertEquals(SyncState.DIRTY, snapshot.syncState)
        assertTrue(snapshot.isFinished)
    }

    @Test
    fun `newer local progress is not overwritten after Kavita upload completes`() = runTest {
        val item = ReadableItem(
            id = "kavita:item:42",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.EPUB,
            pageCount = 28,
        )
        val store = TestKavitaReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingKavitaApi(
            chapterInfo = KavitaChapterInfoDto(
                libraryId = 5,
                seriesId = 12,
                volumeId = 7,
                pages = 28,
            ),
            beforeUpdateProgressReturns = {
                store.saveProgress(
                    item = item,
                    locator = ReaderLocator(
                        locatorType = LocatorType.PAGE,
                        page = 7,
                    ),
                    syncState = SyncState.DIRTY,
                    updatedAtEpochMillis = 456L,
                )
            },
        )

        pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            initialApiKey = null,
            items = listOf(item),
            readerSessionStore = store,
        )

        val snapshot = checkNotNull(store.progressByItemIdFlow.value[item.id])
        assertEquals(7, snapshot.page)
        assertEquals(SyncState.DIRTY, snapshot.syncState)
        assertEquals(456L, snapshot.updatedAtEpochMillis)
    }

    @Test
    fun `failed Kavita upload marks progress failed for retry`() = runTest {
        val item = ReadableItem(
            id = "kavita:item:42",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.EPUB,
            pageCount = 28,
        )
        val store = TestKavitaReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingKavitaApi(
            chapterInfo = KavitaChapterInfoDto(
                libraryId = 5,
                seriesId = 12,
                volumeId = 7,
                pages = 28,
            ),
            updateError = IllegalStateException("boom"),
        )

        pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            initialApiKey = null,
            items = listOf(item),
            readerSessionStore = store,
        )

        val snapshot = checkNotNull(store.progressByItemIdFlow.value[item.id])
        assertEquals(SyncState.FAILED, snapshot.syncState)
        assertFalse(snapshot.isFinished)
    }

    @Test
    fun `missing Kavita catalog item marks dirty progress clean without upload`() = runTest {
        val item = ReadableItem(
            id = "kavita:item:42",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.EPUB,
            pageCount = 28,
        )
        val store = TestKavitaReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingKavitaApi(
            chapterInfo = KavitaChapterInfoDto(
                libraryId = 5,
                seriesId = 12,
                volumeId = 7,
                pages = 28,
            ),
        )

        val result = pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            initialApiKey = null,
            items = emptyList(),
            readerSessionStore = store,
        )

        val snapshot = checkNotNull(store.progressByItemIdFlow.value[item.id])
        assertTrue(result.value.isEmpty())
        assertTrue(api.progressUpdates.isEmpty())
        assertEquals(0, api.chapterInfoRequests)
        assertEquals(SyncState.CLEAN, snapshot.syncState)
    }

    @Test
    fun `other sources' pending progress is left for their own sync`() = runTest {
        val stumpItem = ReadableItem(
            id = "stump:item:abc",
            sourceType = SourceType.STUMP,
            libraryId = "stump:library:1",
            remoteId = "abc",
            title = "Stump Issue",
            format = FormatType.CBZ,
            pageCount = 20,
        )
        val grimmoryItem = ReadableItem(
            id = "grimmory:item:7",
            sourceType = SourceType.GRIMMORY,
            libraryId = "grimmory:library:2",
            remoteId = "7",
            title = "Grimmory Book",
            format = FormatType.EPUB,
            pageCount = 0,
        )
        val store = TestKavitaReaderSessionStore()
        store.saveProgress(
            item = stumpItem,
            locator = ReaderLocator(locatorType = LocatorType.PAGE, page = 5),
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 100L,
        )
        store.saveProgress(
            item = grimmoryItem,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.0),
            isExplicitUnreadReset = true,
            syncState = SyncState.FAILED,
            updatedAtEpochMillis = 200L,
        )
        val api = RecordingKavitaApi(
            chapterInfo = KavitaChapterInfoDto(
                libraryId = 5,
                seriesId = 12,
                volumeId = 7,
                pages = 28,
            ),
        )

        val result = pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            initialApiKey = null,
            items = emptyList(),
            readerSessionStore = store,
        )

        assertTrue(result.value.isEmpty())
        assertTrue(api.progressUpdates.isEmpty())
        assertEquals(SyncState.DIRTY, store.progressByItemIdFlow.value[stumpItem.id]?.syncState)
        val grimmoryReset = checkNotNull(store.progressByItemIdFlow.value[grimmoryItem.id])
        assertTrue(grimmoryReset.isExplicitUnreadReset)
        assertEquals(SyncState.FAILED, grimmoryReset.syncState)
    }

    @Test
    fun `Kavita upload uses catalog ids without fetching chapter info`() = runTest {
        val item = ReadableItem(
            id = "kavita:item:42",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:5",
            remoteId = "42",
            remoteFileId = "7",
            remoteFileType = "12",
            title = "Chapter 42",
            format = FormatType.CBZ,
            pageCount = 28,
        )
        val store = TestKavitaReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingKavitaApi(
            chapterInfo = KavitaChapterInfoDto(
                libraryId = 99,
                seriesId = 99,
                volumeId = 99,
                pages = 99,
            ),
        )

        pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            initialApiKey = null,
            items = listOf(item),
            readerSessionStore = store,
        )

        val update = api.progressUpdates.single()
        assertEquals(0, api.chapterInfoRequests)
        assertEquals(5, update.libraryId)
        assertEquals(12, update.seriesId)
        assertEquals(7, update.volumeId)
        assertEquals(42, update.chapterId)
        assertEquals(3, update.pageNum)
    }

    @Test
    fun `finished Kavita upload fetches chapter info when catalog page count is unknown`() = runTest {
        val item = ReadableItem(
            id = "kavita:item:42",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:5",
            remoteId = "42",
            remoteFileId = "7",
            remoteFileType = "12",
            title = "Chapter 42",
            format = FormatType.CBZ,
            pageCount = null,
        )
        val store = TestKavitaReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            isFinished = true,
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingKavitaApi(
            chapterInfo = KavitaChapterInfoDto(
                libraryId = 5,
                seriesId = 12,
                volumeId = 7,
                pages = 28,
            ),
        )

        pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            initialApiKey = null,
            items = listOf(item),
            readerSessionStore = store,
        )

        val update = api.progressUpdates.single()
        assertEquals(1, api.chapterInfoRequests)
        assertEquals(28, update.pageNum)
    }

    @Test
    fun `cancelled Kavita upload rethrows without marking progress failed`() = runTest {
        val item = ReadableItem(
            id = "kavita:item:42",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.EPUB,
            pageCount = 28,
        )
        val store = TestKavitaReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingKavitaApi(
            chapterInfo = KavitaChapterInfoDto(
                libraryId = 5,
                seriesId = 12,
                volumeId = 7,
                pages = 28,
            ),
            updateError = CancellationException("cancelled"),
        )

        try {
            pushDirtyKavitaProgress(
                api = api,
                initialAccessToken = "access-token",
                initialRefreshToken = null,
                initialApiKey = null,
                items = listOf(item),
                readerSessionStore = store,
            )
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }

        val snapshot = checkNotNull(store.progressByItemIdFlow.value[item.id])
        assertEquals(SyncState.DIRTY, snapshot.syncState)
        assertFalse(snapshot.isFinished)
    }
}

private class RecordingKavitaApi(
    private val chapterInfo: KavitaChapterInfoDto,
    private val beforeUpdateProgressReturns: (suspend () -> Unit)? = null,
    private val updateError: Throwable? = null,
) : KavitaApi {
    val progressUpdates = mutableListOf<KavitaProgressUpdate>()
    var chapterInfoRequests = 0

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
    ) = emptyList<com.example.unifiedreader.core.model.Library>()

    override suspend fun getSeries(
        accessToken: String,
        apiKey: String?,
    ): List<KavitaSeriesEntry> = emptyList()

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
    ): KavitaChapterInfoDto {
        chapterInfoRequests += 1
        return chapterInfo
    }

    override suspend fun updateProgress(
        accessToken: String,
        progress: KavitaProgressUpdate,
    ) {
        updateError?.let { throw it }
        progressUpdates += progress
        beforeUpdateProgressReturns?.invoke()
    }

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
    ) = error("Not needed in test.")
}

private class TestKavitaReaderSessionStore : ReaderSessionStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    private val progressState = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressState.asStateFlow()
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> =
        MutableStateFlow(emptyMap<String, ReaderSessionSnapshot>()).asStateFlow()

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
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = null

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
    ) = Unit

    override fun clearProgress(itemId: String) {
        progressState.value = progressState.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
    }

    override fun restoreItem(itemId: String) = Unit
}
