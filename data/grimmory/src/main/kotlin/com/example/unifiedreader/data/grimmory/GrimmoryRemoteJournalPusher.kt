package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.journal.JournalRepository

interface GrimmoryJournalSessionClient {
    suspend fun pushDirtyJournalEntries(
        items: List<ReadableItem>,
        journalRepository: JournalRepository,
    )
}

class GrimmoryRemoteJournalPusher(
    private val sessionClient: GrimmoryJournalSessionClient,
    private val journalRepository: JournalRepository,
) {
    suspend fun pushPendingJournalEntries(
        items: List<ReadableItem>,
    ) {
        val grimmoryItems = items.filter { item -> item.sourceType == SourceType.GRIMMORY }
        if (grimmoryItems.isEmpty()) return
        sessionClient.pushDirtyJournalEntries(
            items = grimmoryItems,
            journalRepository = journalRepository,
        )
    }
}
