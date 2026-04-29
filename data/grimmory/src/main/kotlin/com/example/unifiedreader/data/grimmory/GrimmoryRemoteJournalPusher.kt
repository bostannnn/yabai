package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.journal.JournalRepository

interface GrimmoryJournalSessionClient {
    suspend fun pushDirtyJournalEntries(
        items: List<ReadableItem>,
        journalRepository: JournalRepository,
        pushJournalEntries: Boolean,
        pushReadingSessions: Boolean,
    ): List<Throwable>
}

class GrimmoryRemoteJournalPusher(
    private val sessionClient: GrimmoryJournalSessionClient,
    private val journalRepository: JournalRepository,
) {
    suspend fun pushPendingJournalEntries(
        items: List<ReadableItem>,
        pushJournalEntries: Boolean = true,
        pushReadingSessions: Boolean = true,
    ): List<Throwable> {
        val grimmoryItems = items.filter { item -> item.sourceType == SourceType.GRIMMORY }
        if (grimmoryItems.isEmpty() || (!pushJournalEntries && !pushReadingSessions)) return emptyList()
        return sessionClient.pushDirtyJournalEntries(
            items = grimmoryItems,
            journalRepository = journalRepository,
            pushJournalEntries = pushJournalEntries,
            pushReadingSessions = pushReadingSessions,
        )
    }
}
