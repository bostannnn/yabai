package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.journal.JournalReadingSessionInput
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteAwareJournalReadingSessionRecorderTest {
    @Test
    fun `marks grimmory reading sessions dirty for server upload`() = runTest {
        val recorder = RecordingReadingSessionRecorder()
        val wrapper = remoteAwareRecorder(recorder = recorder)

        wrapper.recordReadingSession(journalInput(readableItem(SourceType.GRIMMORY)))

        assertEquals(SyncState.DIRTY, recorder.recorded.single().syncState)
    }

    @Test
    fun `keeps kavita reading sessions clean because server reading-time upload is unsupported`() = runTest {
        val item = readableItem(SourceType.KAVITA)
        val recorder = RecordingReadingSessionRecorder()
        val wrapper = remoteAwareRecorder(
            recorder = recorder,
            storedItemIds = setOf(item.id),
        )

        wrapper.recordReadingSession(journalInput(item))

        assertEquals(SyncState.CLEAN, recorder.recorded.single().syncState)
    }

    @Test
    fun `keeps non downloaded kavita reading sessions clean`() = runTest {
        val recorder = RecordingReadingSessionRecorder()
        val wrapper = remoteAwareRecorder(recorder = recorder)

        wrapper.recordReadingSession(journalInput(readableItem(SourceType.KAVITA)))

        assertEquals(SyncState.CLEAN, recorder.recorded.single().syncState)
    }
}

private fun remoteAwareRecorder(
    recorder: RecordingReadingSessionRecorder,
    storedItemIds: Set<String> = emptySet(),
    connectivityPolicy: RemoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
): RemoteAwareJournalReadingSessionRecorder = RemoteAwareJournalReadingSessionRecorder(
    delegate = recorder,
    remoteItemLocalAvailability = RemoteItemLocalAvailability(
        listOf(TestRemoteItemDownloadDataSource(storedItemIds)),
    ),
    remoteSourceConnectivityPolicy = connectivityPolicy,
)

private class RecordingReadingSessionRecorder : JournalReadingSessionRecorder {
    val recorded = mutableListOf<JournalReadingSessionInput>()

    override suspend fun recordReadingSession(input: JournalReadingSessionInput) {
        recorded += input
    }
}

private class TestRemoteItemDownloadDataSource(
    private val storedItemIds: Set<String>,
) : RemoteItemDownloadDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    override fun storedDocumentUri(item: ReadableItem): Uri? =
        if (item.id in storedItemIds) Uri.parse("file:///downloads/${item.id}") else null

    override suspend fun downloadToLocalStorage(item: ReadableItem): Uri? = storedDocumentUri(item)

    override suspend fun storedDocumentItemIds(items: List<ReadableItem>): Set<String> =
        items.mapTo(mutableSetOf(), ReadableItem::id).intersect(storedItemIds)

    override suspend fun deleteStoredDocument(item: ReadableItem): Boolean = false
}

private fun journalInput(
    item: ReadableItem,
): JournalReadingSessionInput = JournalReadingSessionInput(
    item = item,
    startedAtEpochMillis = 1_000L,
    endedAtEpochMillis = 61_000L,
    fromPage = 1,
    toPage = 3,
    pageCount = 20,
    fromPercent = 0.05,
    toPercent = 0.15,
)

private fun readableItem(
    sourceType: SourceType,
): ReadableItem = ReadableItem(
    id = "${sourceType.name.lowercase()}:item:1",
    sourceType = sourceType,
    libraryId = "${sourceType.name.lowercase()}:library:1",
    remoteId = "1",
    title = "Item 1",
    format = FormatType.CBZ,
    kind = ReadableItemKind.COMIC,
    pageCount = 20,
)
