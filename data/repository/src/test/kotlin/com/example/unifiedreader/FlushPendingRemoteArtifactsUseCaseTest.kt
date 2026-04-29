package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.FlushPendingRemoteArtifactsUseCase
import com.example.unifiedreader.data.repository.RemoteJournalPusher
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.data.repository.SourceRemoteJournalPusher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FlushPendingRemoteArtifactsUseCaseTest {
    @Test
    fun `pushes journal artifacts after data-specific progress failure`() = runTest {
        val item = remoteItem(SourceType.GRIMMORY)
        val journalPusher = RecordingRemoteJournalPusher()
        val useCase = flushUseCase(
            progressSync = ArtifactRecordingSourceProgressSync(
                sourceType = SourceType.GRIMMORY,
                failures = listOf(
                    SourceProgressSyncFailure(
                        sourceType = SourceType.GRIMMORY,
                        throwable = IllegalStateException("Progress rejected"),
                    ),
                ),
            ),
            journalPusher = journalPusher,
            sourceJournalPushers = listOf(sourceJournalPusher(SourceType.GRIMMORY)),
        )

        val failures = useCase(
            com.example.unifiedreader.data.repository.RemoteArtifactFlushRequest(items = listOf(item)),
        )

        assertEquals("Progress rejected", failures.single().throwable.message)
        assertEquals(
            listOf(
                Triple(
                    SourceType.GRIMMORY,
                    listOf(item.id),
                    setOf(RemoteSyncArtifact.JOURNAL_ENTRY, RemoteSyncArtifact.READING_SESSION),
                ),
            ),
            journalPusher.invocations,
        )
    }

    @Test
    fun `skips journal artifacts for source after source-level progress failure`() = runTest {
        val item = remoteItem(SourceType.GRIMMORY)
        val journalPusher = RecordingRemoteJournalPusher()
        val useCase = flushUseCase(
            progressSync = ArtifactRecordingSourceProgressSync(
                sourceType = SourceType.GRIMMORY,
                failures = listOf(
                    SourceProgressSyncFailure(
                        sourceType = SourceType.GRIMMORY,
                        throwable = IllegalStateException("Source unavailable"),
                        blocksDependentArtifacts = true,
                    ),
                ),
            ),
            journalPusher = journalPusher,
            sourceJournalPushers = listOf(sourceJournalPusher(SourceType.GRIMMORY)),
        )

        val failures = useCase(
            com.example.unifiedreader.data.repository.RemoteArtifactFlushRequest(items = listOf(item)),
        )

        assertEquals("Source unavailable", failures.single().throwable.message)
        assertTrue(journalPusher.invocations.isEmpty())
    }

    @Test
    fun `forwards requested journal artifact set to pusher`() = runTest {
        val item = remoteItem(SourceType.GRIMMORY)
        val journalPusher = RecordingRemoteJournalPusher()
        val useCase = flushUseCase(
            progressSync = ArtifactRecordingSourceProgressSync(sourceType = SourceType.GRIMMORY),
            journalPusher = journalPusher,
            sourceJournalPushers = listOf(sourceJournalPusher(SourceType.GRIMMORY)),
        )

        useCase(
            com.example.unifiedreader.data.repository.RemoteArtifactFlushRequest(
                items = listOf(item),
                artifacts = setOf(RemoteSyncArtifact.JOURNAL_ENTRY),
            ),
        )

        assertEquals(
            listOf(Triple(SourceType.GRIMMORY, listOf(item.id), setOf(RemoteSyncArtifact.JOURNAL_ENTRY))),
            journalPusher.invocations,
        )
    }

    @Test
    fun `rethrows journal cancellation`() = runTest {
        val item = remoteItem(SourceType.GRIMMORY)
        val useCase = flushUseCase(
            progressSync = ArtifactRecordingSourceProgressSync(sourceType = SourceType.GRIMMORY),
            journalPusher = RecordingRemoteJournalPusher(error = CancellationException("cancelled")),
            sourceJournalPushers = listOf(sourceJournalPusher(SourceType.GRIMMORY)),
        )

        try {
            useCase(
                com.example.unifiedreader.data.repository.RemoteArtifactFlushRequest(items = listOf(item)),
            )
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }
    }
}

private fun flushUseCase(
    progressSync: SourceProgressSync,
    journalPusher: RemoteJournalPusher,
    sourceJournalPushers: List<SourceRemoteJournalPusher>,
): FlushPendingRemoteArtifactsUseCase = FlushPendingRemoteArtifactsUseCase(
    flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
        sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(progressSync)),
    ),
    remoteJournalPusher = journalPusher,
    sourceJournalPushers = sourceJournalPushers,
)

private fun sourceJournalPusher(
    sourceType: SourceType,
): SourceRemoteJournalPusher = object : SourceRemoteJournalPusher {
    override val sourceType: SourceType = sourceType
    override val supportedArtifacts: Set<RemoteSyncArtifact> = setOf(
        RemoteSyncArtifact.JOURNAL_ENTRY,
        RemoteSyncArtifact.READING_SESSION,
    )

    override suspend fun pushPendingJournalEntries(
        items: List<ReadableItem>,
        artifacts: Set<RemoteSyncArtifact>,
    ): List<Throwable> = emptyList()
}

private class RecordingRemoteJournalPusher(
    private val error: Throwable? = null,
) : RemoteJournalPusher {
    val invocations = mutableListOf<Triple<SourceType, List<String>, Set<RemoteSyncArtifact>>>()

    override suspend fun pushPendingJournalEntries(
        sourceType: SourceType,
        items: List<ReadableItem>,
        artifacts: Set<RemoteSyncArtifact>,
    ): List<Throwable> {
        error?.let { throw it }
        invocations += Triple(sourceType, items.map(ReadableItem::id), artifacts)
        return emptyList()
    }
}

private class ArtifactRecordingSourceProgressSync(
    override val sourceType: SourceType,
    private val failures: List<SourceProgressSyncFailure> = emptyList(),
) : SourceProgressSync {
    override suspend fun syncItem(item: ReadableItem) = Unit

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> = failures
}

private fun remoteItem(
    sourceType: SourceType,
): ReadableItem = ReadableItem(
    id = "${sourceType.name.lowercase()}:item:1",
    sourceType = sourceType,
    libraryId = "${sourceType.name.lowercase()}:library:1",
    remoteId = "1",
    title = "Item 1",
    format = FormatType.EPUB,
)
