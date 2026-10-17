package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.stumpSeriesGroupKey
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import javax.inject.Inject

class LibraryShelvesStateComputer @Inject constructor(
    private val seriesContinuationResolver: LibrarySeriesContinuationResolver,
) {
    private val stateCache =
        object : LinkedHashMap<LibraryShelvesStateCacheKey, LibraryShelvesState>(
            SHELVES_STATE_CACHE_LIMIT,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<LibraryShelvesStateCacheKey, LibraryShelvesState>,
            ): Boolean = size > SHELVES_STATE_CACHE_LIMIT
        }

    fun compute(
        input: LibraryShelvesInput,
    ): LibraryShelvesState {
        val computeStart = LibraryShelvesPerfLog.startNanos()
        val cacheKey = LibraryShelvesStateCacheKey(
            selectedSource = input.selectedSource,
            items = input.items,
            progressByItemId = input.progressByItemId,
        )
        stateCache[cacheKey]?.let { cached ->
            LibraryShelvesPerfLog.logSince("shelves.compute HIT [${input.selectedSource}]", computeStart)
            return cached
        }
        val sourceItems = input.items.filter { item ->
            item.sourceType == input.selectedSource
        }
        val continueReading = buildContinueReadingShelf(
            items = sourceItems,
            progressByItemId = input.progressByItemId,
        )
        val nextInSeries = buildNextInSeriesShelf(
            selectedSource = input.selectedSource,
            items = sourceItems,
            progressByItemId = input.progressByItemId,
        )
        val recentlyFinished = buildRecentlyFinishedShelf(
            items = sourceItems,
            progressByItemId = input.progressByItemId,
        )
        return LibraryShelvesState(
            continueReading = continueReading,
            nextInSeries = nextInSeries,
            recentlyFinished = recentlyFinished,
        ).also { shelvesState ->
            stateCache[cacheKey] = shelvesState
            LibraryShelvesPerfLog.logSince(
                "shelves.compute MISS [${input.selectedSource} items=${sourceItems.size}]",
                computeStart,
            )
        }
    }

    private fun buildContinueReadingShelf(
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): List<ContinueReadingShelfItem> = items.mapNotNull { item ->
        val progress = progressByItemId[item.id] ?: return@mapNotNull null
        if (progress.isComplete) return@mapNotNull null
        if (!progress.hasActivity) return@mapNotNull null
        ContinueReadingShelfItem(
            item = item,
            progress = progress,
        )
    }
        .sortedWith(
            compareByDescending<ContinueReadingShelfItem> { entry ->
                entry.progress.lastReadAtEpochMillis
            }.thenByDescending { entry ->
                entry.progress.fraction
            },
        )

    private fun buildNextInSeriesShelf(
        selectedSource: SourceType,
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): List<NextInSeriesShelfItem> {
        // Only comics with activity can seed a "Next in series" entry, so resolve continuations for
        // just those (and their series) instead of grouping/sorting every series in the catalog.
        val activeComicItemIds = items.asSequence()
            .filter { item -> item.kind == ReadableItemKind.COMIC }
            .filter { item -> progressByItemId[item.id]?.hasActivity == true }
            .mapTo(HashSet(), ReadableItem::id)
        if (activeComicItemIds.isEmpty()) return emptyList()
        val nextUnstartedByCurrentItemId = seriesContinuationResolver.nextUnstartedAfterForItems(
            seedItemIds = activeComicItemIds,
            items = items,
            progressByItemId = progressByItemId,
        )
        return items
            .asSequence()
            .filter { item -> item.kind == ReadableItemKind.COMIC }
            .mapNotNull { currentItem ->
                val progress = progressByItemId[currentItem.id] ?: return@mapNotNull null
                if (!progress.hasActivity) return@mapNotNull null
                val nextItem = nextUnstartedByCurrentItemId[currentItem.id] ?: return@mapNotNull null
                NextInSeriesShelfItem(
                    item = nextItem,
                    currentItem = currentItem,
                    lastActivityAtEpochMillis = progress.lastReadAtEpochMillis,
                )
            }
            .groupBy { entry ->
                entry.seriesGroupKey(selectedSource)
            }
            .mapNotNull { (seriesKey, entries) ->
                seriesKey?.let {
                    entries.maxByOrNull(NextInSeriesShelfItem::lastActivityAtEpochMillis)
                }
            }
            .sortedByDescending(NextInSeriesShelfItem::lastActivityAtEpochMillis)
    }

    private fun buildRecentlyFinishedShelf(
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): List<RecentlyFinishedShelfItem> = items.mapNotNull { item ->
        val progress = progressByItemId[item.id] ?: return@mapNotNull null
        if (!progress.isComplete) return@mapNotNull null
        if (progress.lastReadAtEpochMillis <= 0L) return@mapNotNull null
        RecentlyFinishedShelfItem(
            item = item,
            progress = progress,
        )
    }
        .sortedByDescending { entry -> entry.progress.lastReadAtEpochMillis }
}

private val ItemProgressSummary.isComplete: Boolean
    get() = isFinished || fraction >= 0.999f

private val ItemProgressSummary.hasActivity: Boolean
    get() = lastReadAtEpochMillis > 0L || fraction > 0f || isFinished

private fun NextInSeriesShelfItem.seriesGroupKey(
    selectedSource: SourceType,
): String? = when (selectedSource) {
    SourceType.KAVITA -> item.kavitaSeriesGroupKey()
    SourceType.STUMP -> item.stumpSeriesGroupKey()
    SourceType.GRIMMORY,
    SourceType.LOCAL -> item.localSeriesGroupKey()
}

private fun ReadableItem.localSeriesGroupKey(): String? {
    val series = comicMetadata?.series
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return "$libraryId::${series.lowercase()}"
}

private const val SHELVES_STATE_CACHE_LIMIT = 12

private data class LibraryShelvesStateCacheKey(
    val selectedSource: SourceType,
    val items: IdentityBox<List<ReadableItem>>,
    val progressByItemId: IdentityBox<Map<String, ItemProgressSummary>>,
    val itemCount: Int,
    val progressCount: Int,
) {
    constructor(
        selectedSource: SourceType,
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ) : this(
        selectedSource = selectedSource,
        items = IdentityBox(items),
        progressByItemId = IdentityBox(progressByItemId),
        itemCount = items.size,
        progressCount = progressByItemId.size,
    )
}

private class IdentityBox<T : Any?>(
    private val value: T,
) {
    override fun equals(
        other: Any?,
    ): Boolean = other is IdentityBox<*> && value === other.value

    override fun hashCode(): Int = System.identityHashCode(value)
}
