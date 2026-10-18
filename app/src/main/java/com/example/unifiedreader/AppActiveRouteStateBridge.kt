package com.example.unifiedreader

import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode

internal fun AppUiState.toActiveRouteSnapshot(): AppActiveRouteSnapshot {
    val readerItem = selectedItem.takeIf { activeReader != null }
    return AppActiveRouteSnapshot(
        topLevel = topLevel,
        selectedSource = readerItem?.sourceType ?: selectedSource,
        kavitaLibrarySection = kavitaLibrarySection,
        selectedKavitaSeriesKey = selectedKavitaSeriesKey,
        selectedStumpSeriesKey = selectedStumpSeriesKey,
        selectedKavitaReadingListId = kavitaReadingLists.selectedReadingListId,
        selectedKavitaCollectionId = kavitaReadingLists.selectedCollectionId,
        readerItemId = readerItem?.id,
        readerFormat = readerItem?.format ?: activeReader,
        // Always kept, not only for items outside the catalog: after process death the catalog
        // loads on IO, after the restored route is applied.
        readerFallbackItem = readerItem,
    )
}

internal fun AppUiState.withActiveRouteSnapshot(
    snapshot: AppActiveRouteSnapshot,
): AppUiState {
    val restoredReaderItem = snapshot.readerItemId?.let(::itemById)
        ?: snapshot.readerFallbackItem?.takeIf { fallbackItem ->
            fallbackItem.id == snapshot.readerItemId
        }
    val shouldRestoreReader = restoredReaderItem != null &&
        snapshot.readerFormat != null &&
        restoredReaderItem.format == snapshot.readerFormat
    val selectedSource = if (shouldRestoreReader) {
        restoredReaderItem.sourceType
    } else {
        snapshot.selectedSource.takeIf { source -> source in sourceOrder }
            ?: sourceOrder.firstOrNull()
            ?: SourceType.KAVITA
    }
    val selectedKavitaSeriesKey = retainedSeriesKey(
        seriesKey = snapshot.selectedKavitaSeriesKey,
        seriesSource = SourceType.KAVITA,
        selectedSource = selectedSource,
        items = items,
        hydratedSources = hydratedSources,
        belongsToSeries = ReadableItem::belongsToKavitaSeries,
    )
    val selectedStumpSeriesKey = retainedSeriesKey(
        seriesKey = snapshot.selectedStumpSeriesKey,
        seriesSource = SourceType.STUMP,
        selectedSource = selectedSource,
        items = items,
        hydratedSources = hydratedSources,
        belongsToSeries = ReadableItem::belongsToStumpSeries,
    )
    val selectedReadingListId = snapshot.selectedKavitaReadingListId
        ?.takeIf {
            selectedSource == SourceType.KAVITA &&
                selectedKavitaSeriesKey == null &&
                snapshot.selectedKavitaCollectionId == null
        }
    val selectedCollectionId = snapshot.selectedKavitaCollectionId
        ?.takeIf {
            selectedSource == SourceType.KAVITA &&
                selectedKavitaSeriesKey == null
        }
    val kavitaLibrarySection = if (selectedSource == SourceType.KAVITA) {
        when {
            selectedReadingListId != null -> KavitaLibrarySection.ReadingLists
            selectedCollectionId != null -> KavitaLibrarySection.Collections
            else -> snapshot.kavitaLibrarySection
        }
    } else {
        snapshot.kavitaLibrarySection
    }
    return copy(
        topLevel = if (shouldRestoreReader) {
            TopLevelDestination.Library
        } else {
            snapshot.topLevel
        },
        selectedSource = selectedSource,
        kavitaLibrarySection = kavitaLibrarySection,
        selectedItemId = restoredReaderItem?.id.takeIf { shouldRestoreReader },
        activeReader = restoredReaderItem?.format.takeIf { shouldRestoreReader },
        selectedReaderOpenMode = ReaderOpenMode.Default,
        selectedReaderItem = restoredReaderItem
            ?.takeIf { shouldRestoreReader }
            ?.takeIf { item -> items.none { catalogItem -> catalogItem.id == item.id } },
        selectedKavitaSeriesKey = selectedKavitaSeriesKey,
        selectedStumpSeriesKey = selectedStumpSeriesKey,
        kavitaReadingLists = kavitaReadingLists.copy(
            selectedReadingListId = selectedReadingListId,
            loadingSelectedReadingListId = selectedReadingListId,
            selectedCollectionId = selectedCollectionId,
            loadingSelectedCollectionId = selectedCollectionId,
            isLoadingSelectedItems = selectedReadingListId != null || selectedCollectionId != null,
            errorMessage = null,
        ),
        readerCompletionState = null,
    )
}

/**
 * Keeps a series sub-screen while its source's catalog is still loading (a cold start lists only
 * part of it, or nothing), and drops it once the loaded catalog has no item in that series.
 */
internal fun retainedSeriesKey(
    seriesKey: String?,
    seriesSource: SourceType,
    selectedSource: SourceType,
    items: List<ReadableItem>,
    hydratedSources: Set<SourceType>,
    belongsToSeries: (ReadableItem, String) -> Boolean,
): String? = seriesKey?.takeIf { key ->
    selectedSource == seriesSource &&
        (seriesSource !in hydratedSources || items.any { item -> belongsToSeries(item, key) })
}

private fun AppUiState.itemById(
    itemId: String,
): ReadableItem? = items.firstOrNull { item -> item.id == itemId }
