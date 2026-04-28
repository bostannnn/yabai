package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.journal.JournalRepository

interface KavitaJournalSessionClient {
    suspend fun pushDirtyJournalEntries(
        items: List<ReadableItem>,
        journalRepository: JournalRepository,
    )
}

class KavitaRemoteJournalPusher(
    private val sessionClient: KavitaJournalSessionClient,
    private val journalRepository: JournalRepository,
) {
    suspend fun pushPendingJournalEntries(
        items: List<ReadableItem>,
    ) {
        val kavitaItems = items.filter { item -> item.sourceType == SourceType.KAVITA }
        if (kavitaItems.isEmpty()) return
        sessionClient.pushDirtyJournalEntries(
            items = kavitaItems,
            journalRepository = journalRepository,
        )
    }
}
