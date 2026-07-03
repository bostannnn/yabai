package com.example.unifiedreader.data.repository

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.journal.JournalRepository
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards

interface RemoteJournalPusher {
    suspend fun pushPendingJournalEntries(
        sourceType: SourceType,
        items: List<ReadableItem>,
        artifacts: Set<RemoteSyncArtifact>,
    ): List<Throwable>
}

interface SourceRemoteJournalPusher : SourceRemoteArtifactSupport {
    override val sourceType: SourceType
    override val supportedArtifacts: Set<RemoteSyncArtifact>
        get() = setOf(RemoteSyncArtifact.JOURNAL_ENTRY)

    suspend fun pushPendingJournalEntries(
        items: List<ReadableItem>,
        artifacts: Set<RemoteSyncArtifact>,
    ): List<Throwable>
}

class RemoteJournalPusherRegistry @Inject constructor(
    private val sourcePushers: List<@JvmSuppressWildcards SourceRemoteJournalPusher>,
) : RemoteJournalPusher {
    override suspend fun pushPendingJournalEntries(
        sourceType: SourceType,
        items: List<ReadableItem>,
        artifacts: Set<RemoteSyncArtifact>,
    ): List<Throwable> =
        sourcePushers
            .firstOrNull { pusher -> pusher.sourceType == sourceType }
            ?.pushPendingJournalEntries(items, artifacts)
            .orEmpty()
}

class JournalEntrySaveUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
) {
    suspend operator fun invoke(
        item: JournalItemRef,
        rating: Int?,
        reviewText: String,
    ) {
        journalRepository.saveJournalEntry(
            item = item,
            rating = rating,
            reviewText = reviewText,
        )
    }
}
