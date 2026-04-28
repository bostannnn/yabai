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
    )
}

interface SourceRemoteJournalPusher {
    val sourceType: SourceType

    suspend fun pushPendingJournalEntries(
        items: List<ReadableItem>,
    )
}

class RemoteJournalPusherRegistry @Inject constructor(
    private val sourcePushers: List<@JvmSuppressWildcards SourceRemoteJournalPusher>,
) : RemoteJournalPusher {
    override suspend fun pushPendingJournalEntries(
        sourceType: SourceType,
        items: List<ReadableItem>,
    ) {
        sourcePushers
            .firstOrNull { pusher -> pusher.sourceType == sourceType }
            ?.pushPendingJournalEntries(items)
    }
}

class JournalEntrySaveUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
    private val remoteJournalPusher: RemoteJournalPusher,
) {
    suspend operator fun invoke(
        item: JournalItemRef,
        rating: Int?,
        reviewText: String,
        candidateItems: List<ReadableItem>,
        selectedItem: ReadableItem?,
    ) {
        journalRepository.saveJournalEntry(
            item = item,
            rating = rating,
            reviewText = reviewText,
        )
        remoteJournalPusher.pushPendingJournalEntries(
            sourceType = item.sourceType,
            items = candidateItems.withSelectedJournalItem(selectedItem),
        )
    }
}

private fun List<ReadableItem>.withSelectedJournalItem(
    selectedItem: ReadableItem?,
): List<ReadableItem> {
    selectedItem ?: return this
    if (any { item -> item.id == selectedItem.id }) return this
    return this + selectedItem
}
