package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpMediaCatalogEntry
import com.example.unifiedreader.data.stump.StumpSeriesEntry
import com.example.unifiedreader.data.stump.StumpSession
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
import org.junit.Test

class StumpProgressSyncSupportTest {
    @Test
    fun `newer local progress is not overwritten after Stump upload completes`() = runTest {
        val item = ReadableItem(
            id = "stump:item:42",
            sourceType = SourceType.STUMP,
            libraryId = "stump:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.CBZ,
            pageCount = 28,
        )
        val store = TestStumpReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingStumpApi(
            beforeProgressReturns = {
                store.saveProgress(
                    item = item,
                    locator = ReaderLocator(
                        locatorType = LocatorType.PAGE,
                        page = 8,
                    ),
                    syncState = SyncState.DIRTY,
                    updatedAtEpochMillis = 456L,
                )
            },
        )

        pushDirtyStumpProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            items = listOf(item),
            readerSessionStore = store,
        )

        val snapshot = checkNotNull(store.progressByItemIdFlow.value[item.id])
        assertEquals(8, snapshot.page)
        assertEquals(SyncState.DIRTY, snapshot.syncState)
        assertEquals(456L, snapshot.updatedAtEpochMillis)
    }

    @Test
    fun `failed Stump upload marks progress failed for retry`() = runTest {
        val item = ReadableItem(
            id = "stump:item:42",
            sourceType = SourceType.STUMP,
            libraryId = "stump:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.CBZ,
            pageCount = 28,
        )
        val store = TestStumpReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val api = RecordingStumpApi(
            updateError = IllegalStateException("boom"),
        )

        pushDirtyStumpProgress(
            api = api,
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            items = listOf(item),
            readerSessionStore = store,
        )

        val snapshot = checkNotNull(store.progressByItemIdFlow.value[item.id])
        assertEquals(SyncState.FAILED, snapshot.syncState)
        assertFalse(snapshot.isFinished)
    }

    @Test
    fun `failed Stump completion records diagnostic context`() = runTest {
        val item = ReadableItem(
            id = "stump:item:42",
            sourceType = SourceType.STUMP,
            libraryId = "stump:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.CBZ,
            pageCount = 28,
        )
        val store = TestStumpReaderSessionStore()
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
        val diagnostics = RecordingStumpProgressSyncDiagnostics()

        pushDirtyStumpProgress(
            api = RecordingStumpApi(
                updateError = IllegalStateException("NOT NULL constraint failed: reading_sessions.started_at"),
            ),
            initialAccessToken = "access-token",
            initialRefreshToken = null,
            items = listOf(item),
            readerSessionStore = store,
            diagnostics = diagnostics,
        )

        val attempt = diagnostics.attempts.single()
        val failure = diagnostics.failures.single()
        assertEquals(StumpProgressSyncOperation.MarkMediaComplete, attempt.operation)
        assertEquals("42", attempt.mediaId)
        assertEquals(27, attempt.pageIndex)
        assertEquals(28, attempt.remotePageNumber)
        assertEquals(28, attempt.pageCount)
        assertEquals(true, attempt.isFinished)
        assertEquals(attempt, failure.event)
        assertEquals("NOT NULL constraint failed: reading_sessions.started_at", failure.throwable.message)
    }

    @Test
    fun `cancelled Stump upload rethrows without marking progress failed`() = runTest {
        val item = ReadableItem(
            id = "stump:item:42",
            sourceType = SourceType.STUMP,
            libraryId = "stump:library:5",
            remoteId = "42",
            title = "Chapter 42",
            format = FormatType.CBZ,
            pageCount = 28,
        )
        val store = TestStumpReaderSessionStore()
        store.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        val diagnostics = RecordingStumpProgressSyncDiagnostics()

        try {
            pushDirtyStumpProgress(
                api = RecordingStumpApi(
                    updateError = CancellationException("cancelled"),
                ),
                initialAccessToken = "access-token",
                initialRefreshToken = null,
                items = listOf(item),
                readerSessionStore = store,
                diagnostics = diagnostics,
            )
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }

        val snapshot = checkNotNull(store.progressByItemIdFlow.value[item.id])
        assertEquals(SyncState.DIRTY, snapshot.syncState)
        assertEquals(0, diagnostics.failures.size)
    }
}

private class RecordingStumpProgressSyncDiagnostics : StumpProgressSyncDiagnostics {
    val attempts = mutableListOf<StumpProgressSyncDiagnosticEvent>()
    val successes = mutableListOf<StumpProgressSyncDiagnosticEvent>()
    val failures = mutableListOf<Failure>()

    override fun onAttempt(
        event: StumpProgressSyncDiagnosticEvent,
    ) {
        attempts += event
    }

    override fun onSuccess(
        event: StumpProgressSyncDiagnosticEvent,
    ) {
        successes += event
    }

    override fun onFailure(
        event: StumpProgressSyncDiagnosticEvent,
        throwable: Throwable,
    ) {
        failures += Failure(
            event = event,
            throwable = throwable,
        )
    }

    data class Failure(
        val event: StumpProgressSyncDiagnosticEvent,
        val throwable: Throwable,
    )
}

private class RecordingStumpApi(
    private val beforeProgressReturns: (suspend () -> Unit)? = null,
    private val updateError: Throwable? = null,
) : StumpApi {
    override suspend fun login(
        username: String,
        password: String,
    ): StumpSession = error("Not needed in test.")

    override suspend fun refreshSession(
        refreshToken: String,
    ): StumpSession = error("Not needed in test.")

    override suspend fun getLibraries(
        accessToken: String,
    ) = emptyList<com.example.unifiedreader.core.model.Library>()

    override suspend fun getSeries(
        accessToken: String,
        libraryId: String,
    ): List<StumpSeriesEntry> = emptyList()

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: StumpSeriesEntry,
    ): List<StumpMediaCatalogEntry> = emptyList()

    override suspend fun getProgress(
        accessToken: String,
        mediaId: String,
        itemId: String,
        pageCount: Int?,
    ) = error("Not needed in test.")

    override suspend fun updatePagedProgress(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    ) {
        updateError?.let { throw it }
        beforeProgressReturns?.invoke()
    }

    override suspend fun markMediaComplete(
        accessToken: String,
        mediaId: String,
        isComplete: Boolean,
        pageIndex: Int?,
    ) {
        updateError?.let { throw it }
        beforeProgressReturns?.invoke()
    }

    override suspend fun downloadPageImage(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadThumbnail(
        accessToken: String,
        mediaId: String,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadSeriesThumbnail(
        accessToken: String,
        seriesId: String,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadUrl(
        accessToken: String,
        url: String,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadMediaFile(
        accessToken: String,
        mediaId: String,
        destination: File,
    ) = error("Not needed in test.")
}

private class TestStumpReaderSessionStore : ReaderSessionStore {
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

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = null

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
