package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.journal.JournalEventRecorder
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JournalRecordingAdaptersTest {
    @Test
    fun `comic save at restored page does not record journal progress`() = runTest {
        val delegate = RecordingAdaptersFakeComicReaderProgressStore(initialPage = 5)
        val recorder = FakeJournalEventRecorder()
        val store = JournalRecordingComicReaderProgressStore(
            delegate = delegate,
            journalEventRecorder = recorder,
            scope = this,
        )

        store.savePage(item = testComic, pageIndex = 5, pageCount = 10, isFinished = false)
        advanceUntilIdle()

        assertTrue(recorder.inputs.isEmpty())
        assertEquals(5, delegate.loadPage(testComic))
    }

    @Test
    fun `comic forward page save records journal progress`() = runTest {
        val delegate = RecordingAdaptersFakeComicReaderProgressStore(initialPage = 5)
        val recorder = FakeJournalEventRecorder()
        val store = JournalRecordingComicReaderProgressStore(
            delegate = delegate,
            journalEventRecorder = recorder,
            scope = this,
        )

        store.savePage(item = testComic, pageIndex = 8, pageCount = 10, isFinished = false)
        advanceUntilIdle()

        assertEquals(1, recorder.inputs.size)
        assertEquals(9, recorder.inputs.single().page)
        assertEquals(0.9, checkNotNull(recorder.inputs.single().percent), 0.0)
    }

    @Test
    fun `comic first automatic page save does not record journal progress`() = runTest {
        val delegate = RecordingAdaptersFakeComicReaderProgressStore(initialPage = null)
        val recorder = FakeJournalEventRecorder()
        val store = JournalRecordingComicReaderProgressStore(
            delegate = delegate,
            journalEventRecorder = recorder,
            scope = this,
        )

        store.savePage(item = testComic, pageIndex = 0, pageCount = 10, isFinished = false)
        advanceUntilIdle()

        assertTrue(recorder.inputs.isEmpty())
        assertEquals(0, delegate.loadPage(testComic))
    }

    @Test
    fun `page locator session saves record one based journal page`() = runTest {
        val delegate = RecordingAdaptersFakeReaderSessionStore()
        val recorder = FakeJournalEventRecorder()
        val store = JournalRecordingReaderSessionStore(
            delegate = delegate,
            journalEventRecorder = recorder,
            scope = this,
        )

        store.saveProgress(
            item = testPdf,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 0,
                percent = 0.1,
            ),
            pageCount = 10,
        )
        advanceUntilIdle()

        assertEquals(1, recorder.inputs.size)
        assertEquals(1, recorder.inputs.single().page)
        assertEquals(0.1, checkNotNull(recorder.inputs.single().percent), 0.0)
    }
}

private class FakeJournalEventRecorder : JournalEventRecorder {
    val inputs = mutableListOf<JournalProgressInput>()

    override suspend fun recordReaderProgress(input: JournalProgressInput) {
        inputs += input
    }
}

private class RecordingAdaptersFakeComicReaderProgressStore(
    initialPage: Int?,
) : ComicReaderProgressStore {
    private val savedPages = MutableStateFlow(
        initialPage?.let { mapOf(testComic.id to it) }.orEmpty(),
    )

    override val isHydratedFlow = MutableStateFlow(true)
    override val savedPagesFlow = savedPages

    override fun loadPage(item: ReadableItem): Int? = savedPages.value[item.id]

    override fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
        isFinished: Boolean,
    ) {
        val safePageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        savedPages.value = savedPages.value + (item.id to safePageIndex)
    }
}

private class RecordingAdaptersFakeReaderSessionStore : ReaderSessionStore {
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
    }

    override suspend fun replaceProgressIfCurrent(
        expected: ReaderProgressSnapshot,
        replacement: ReaderProgressSnapshot?,
    ): Boolean = false

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionState.value[item.id]

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
        progressState.value = progressState.value - itemId
        sessionState.value = sessionState.value - itemId
    }

    override fun restoreItem(itemId: String) = Unit
}

private val testComic = ReadableItem(
    id = "local:comic",
    sourceType = SourceType.LOCAL,
    libraryId = "local:library:comics",
    title = "Comic",
    format = FormatType.CBZ,
    kind = ReadableItemKind.COMIC,
    pageCount = 10,
)

private val testPdf = ReadableItem(
    id = "local:pdf",
    sourceType = SourceType.LOCAL,
    libraryId = "local:library:books",
    title = "PDF",
    format = FormatType.PDF,
    kind = ReadableItemKind.BOOK,
    pageCount = 10,
)
