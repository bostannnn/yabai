package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.journal.JournalEventRecorder
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

private val testComic = ReadableItem(
    id = "local:comic",
    sourceType = SourceType.LOCAL,
    libraryId = "local:library:comics",
    title = "Comic",
    format = FormatType.CBZ,
    kind = ReadableItemKind.COMIC,
    pageCount = 10,
)
