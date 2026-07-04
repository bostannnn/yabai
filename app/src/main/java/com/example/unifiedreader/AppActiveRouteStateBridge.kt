package com.example.unifiedreader

import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode

internal fun AppUiState.toActiveRouteSnapshot(): AppActiveRouteSnapshot {
    val readerItem = selectedItem.takeIf { activeReader != null }
    val isCatalogReaderItem = readerItem?.let { item ->
        items.any { catalogItem -> catalogItem.id == item.id }
    } ?: false
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
        readerFallbackItem = readerItem.takeUnless { isCatalogReaderItem },
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
    val selectedKavitaSeriesKey = snapshot.selectedKavitaSeriesKey
        ?.takeIf { seriesKey ->
            selectedSource == SourceType.KAVITA &&
                items.any { item -> item.belongsToKavitaSeries(seriesKey) }
        }
    val selectedStumpSeriesKey = snapshot.selectedStumpSeriesKey
        ?.takeIf { seriesKey ->
            selectedSource == SourceType.STUMP &&
                items.any { item -> item.belongsToStumpSeries(seriesKey) }
        }
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

private fun AppUiState.itemById(
    itemId: String,
): ReadableItem? = items.firstOrNull { item -> item.id == itemId }
