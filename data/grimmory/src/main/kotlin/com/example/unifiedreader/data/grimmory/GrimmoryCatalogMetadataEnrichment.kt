package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.facetMetadata
import kotlin.coroutines.cancellation.CancellationException

suspend fun enrichGrimmoryJournalCatalogMetadata(
    api: GrimmoryApi,
    accessToken: String,
    entries: List<GrimmoryBookCatalogEntry>,
): List<GrimmoryBookCatalogEntry> {
    if (entries.isEmpty()) return entries
    val enrichedByItemId = mutableMapOf<String, GrimmoryBookCatalogEntry>()
    val enrichmentCandidates = entries
        .asSequence()
        .filter(GrimmoryBookCatalogEntry::needsJournalMetadataEnrichment)
        .take(GRIMMORY_JOURNAL_METADATA_ENRICHMENT_LIMIT)
        .toList()
    for (entry in enrichmentCandidates) {
        val bookId = entry.item.remoteId ?: continue
        val detailEntry = runCatching {
            api.getBookEntry(
                accessToken = accessToken,
                bookId = bookId,
            )
        }.getOrElse { error ->
            when (error) {
                is CancellationException,
                is GrimmoryUnauthorizedException -> throw error
            }
            null
        } ?: continue
        if (detailEntry.item.facetMetadata().genres.isEmpty()) continue
        enrichedByItemId[entry.item.id] = entry.copy(item = detailEntry.item)
    }
    if (enrichedByItemId.isEmpty()) return entries
    return entries.map { entry -> enrichedByItemId[entry.item.id] ?: entry }
}

private fun GrimmoryBookCatalogEntry.needsJournalMetadataEnrichment(): Boolean =
    item.facetMetadata().genres.isEmpty() && hasJournalRelevantState()

private fun GrimmoryBookCatalogEntry.hasJournalRelevantState(): Boolean =
    progress != null ||
        personalRating != null ||
        !dateFinished.isNullOrBlank() ||
        !lastReadTime.isNullOrBlank() ||
        readStatus.isFinishedReadStatusForEnrichment()

private fun String?.isFinishedReadStatusForEnrichment(): Boolean {
    val normalized = this?.trim()?.lowercase() ?: return false
    return normalized == "read" ||
        normalized == "finished" ||
        normalized == "completed" ||
        normalized == "complete"
}

private const val GRIMMORY_JOURNAL_METADATA_ENRICHMENT_LIMIT = 256
