package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.stumpSeriesContainerId
import java.util.Base64
import javax.inject.Inject

class LibraryDerivedStateComputer @Inject constructor() {
    private var cachedSourceIndexKey: SourceIndexCacheKey? = null
    private var cachedSourceIndex: LibrarySourceIndex? = null
    private val derivedStateCache =
        object : LinkedHashMap<LibraryDerivedStateCacheKey, LibraryDerivedState>(DERIVED_STATE_CACHE_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<LibraryDerivedStateCacheKey, LibraryDerivedState>,
            ): Boolean = size > DERIVED_STATE_CACHE_LIMIT
        }

    fun compute(
        input: LibraryDerivedStateInput,
    ): LibraryDerivedState {
        val sourceIndex = sourceIndexFor(input)
        val cacheKey = LibraryDerivedStateCacheKey(
            sourceIndexKey = cachedSourceIndexKey ?: SourceIndexCacheKey(input.libraries, input.items),
            selectedSource = input.selectedSource,
            selectedKavitaSeriesKey = input.selectedKavitaSeriesKey,
            selectedStumpSeriesKey = input.selectedStumpSeriesKey,
            selectedLibraryFilters = input.selectedLibraryFilters,
            selectedLibrarySearchQuery = input.selectedLibrarySearchQuery,
            selectedLibrarySortMode = input.selectedLibrarySortMode,
            progressByItemId = input.progressByItemId,
            downloadedRemoteItemIds = input.downloadedRemoteItemIds,
            locallyAvailableRemoteItemIds = input.locallyAvailableRemoteItemIds,
            downloadingRemoteItemIds = input.downloadingRemoteItemIds,
        )
        derivedStateCache[cacheKey]?.let { return it }

        val sourceItems = sourceIndex.sourceItems(
            selectedSource = input.selectedSource,
            selectedKavitaSeriesKey = input.selectedKavitaSeriesKey,
            selectedStumpSeriesKey = input.selectedStumpSeriesKey,
        )
        val displayProgressByItemId = buildDisplayProgressByItemId(
            input = input,
            sourceIndex = sourceIndex,
        )
        val activeDownloadingItemIds = buildActiveDownloadingItemIds(
            downloadingItemIds = input.downloadingRemoteItemIds,
            sourceIndex = sourceIndex,
        )
        val remoteDownloadActionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = sourceItems,
            downloadedItemIds = input.downloadedRemoteItemIds,
            activeDownloadingItemIds = activeDownloadingItemIds,
            actionItemsProvider = sourceIndex::resolveDownloadActionItems,
        )
        val lastReadAtEpochMillisByItemId = displayProgressByItemId.mapValues { (_, progress) ->
            progress.lastReadAtEpochMillis
        }
        val visibleItems = buildVisibleLibraryItems(
            sourceItems = sourceItems,
            filterState = input.selectedLibraryFilters,
            searchQuery = input.selectedLibrarySearchQuery,
            sortMode = input.selectedLibrarySortMode,
            downloadedRemoteItemIds = input.locallyAvailableRemoteItemIds,
            lastReadAtEpochMillisByItemId = lastReadAtEpochMillisByItemId,
            forceSeriesIssueOrder = input.isSeriesBrowseActive,
        )
        return LibraryDerivedState(
            sourceItems = sourceItems,
            selectedLibraryFilterFacets = com.example.unifiedreader.core.model.buildLibraryFilterFacets(
                sourceItems,
                input.locallyAvailableRemoteItemIds,
            ),
            selectedSourceLibraryTitlesById = sourceIndex.selectedSourceLibraryTitlesById(input.selectedSource),
            displayProgressByItemId = displayProgressByItemId,
            remoteDownloadActionAvailability = remoteDownloadActionAvailability,
            visibleItems = visibleItems,
            libraryBrowseContextKey = buildLibraryBrowseContextKey(input),
        ).also { derivedState ->
            derivedStateCache[cacheKey] = derivedState
        }
    }

    private fun sourceIndexFor(
        input: LibraryDerivedStateInput,
    ): LibrarySourceIndex {
        val key = SourceIndexCacheKey(
            libraries = input.libraries,
            items = input.items,
        )
        val cachedKey = cachedSourceIndexKey
        val cachedIndex = cachedSourceIndex
        if (cachedKey == key && cachedIndex != null) {
            return cachedIndex
        }
        return LibrarySourceIndex.build(
            libraries = input.libraries,
            items = input.items,
        ).also { index ->
            cachedSourceIndexKey = key
            cachedSourceIndex = index
        }
    }

    private fun buildDisplayProgressByItemId(
        input: LibraryDerivedStateInput,
        sourceIndex: LibrarySourceIndex,
    ): Map<String, ItemProgressSummary> = when {
        input.selectedSource == SourceType.KAVITA && input.selectedKavitaSeriesKey == null ->
            input.progressByItemId + buildComicSeriesProgressByItemId(
                issuesBySeriesKey = sourceIndex.issueGroupsFor(SourceType.KAVITA),
                progressByItemId = input.progressByItemId,
                seriesContainerId = ::kavitaSeriesContainerId,
            )

        input.selectedSource == SourceType.STUMP && input.selectedStumpSeriesKey == null ->
            input.progressByItemId + buildComicSeriesProgressByItemId(
                issuesBySeriesKey = sourceIndex.issueGroupsFor(SourceType.STUMP),
                progressByItemId = input.progressByItemId,
                seriesContainerId = ::stumpSeriesContainerId,
            )

        else -> input.progressByItemId
    }

    private fun buildActiveDownloadingItemIds(
        downloadingItemIds: Set<String>,
        sourceIndex: LibrarySourceIndex,
    ): Set<String> = buildSet {
        addAll(downloadingItemIds)
        downloadingItemIds.forEach { downloadingItemId ->
            val downloadingItem = sourceIndex.itemById(downloadingItemId) ?: return@forEach
            addAll(sourceIndex.trackedRemoteDownloadItemIds(downloadingItem))
        }
    }
}

private val LibraryDerivedStateInput.isSeriesBrowseActive: Boolean
    get() = when (selectedSource) {
        SourceType.KAVITA -> selectedKavitaSeriesKey != null
        SourceType.STUMP -> selectedStumpSeriesKey != null
        SourceType.GRIMMORY,
        SourceType.LOCAL -> false
    }

private fun buildLibraryBrowseContextKey(
    input: LibraryDerivedStateInput,
): String = when (input.selectedSource) {
    SourceType.KAVITA -> input.selectedKavitaSeriesKey?.let { seriesKey ->
        "kavita:series:${seriesKey.keyComponent()}:${libraryBrowseStateKeySuffix(input)}"
    } ?: "kavita:root:${libraryBrowseStateKeySuffix(input)}"

    SourceType.STUMP -> input.selectedStumpSeriesKey?.let { seriesKey ->
        "stump:series:${seriesKey.keyComponent()}:${libraryBrowseStateKeySuffix(input)}"
    } ?: "stump:root:${libraryBrowseStateKeySuffix(input)}"

    SourceType.GRIMMORY -> "grimmory:root:${libraryBrowseStateKeySuffix(input)}"
    SourceType.LOCAL -> "local:root:${libraryBrowseStateKeySuffix(input)}"
}

private fun libraryBrowseStateKeySuffix(
    input: LibraryDerivedStateInput,
): String = buildString {
    append("query=")
    append(input.selectedLibrarySearchQuery.keyComponent())
    append("|sort=")
    append(input.selectedLibrarySortMode.name)
    append("|filters=")
    append(input.selectedLibraryFilters.keyFragment())
}

private fun LibraryFilterState.keyFragment(): String = buildString {
    append("formats=")
    append(formats.map(FormatType::name).sorted().joinToString(","))
    append(";libraries=")
    append(libraries.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(",") { value -> value.keyComponent() })
    append(";locations=")
    append(fileLocations.map { location -> location.name }.sorted().joinToString(","))
    append(";authors=")
    append(authors.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(",") { value -> value.keyComponent() })
    append(";series=")
    append(series.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(",") { value -> value.keyComponent() })
}

private fun String.keyComponent(): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(toByteArray(Charsets.UTF_8))

private fun buildComicSeriesProgressByItemId(
    issuesBySeriesKey: Map<String, List<ReadableItem>>,
    progressByItemId: Map<String, ItemProgressSummary>,
    seriesContainerId: (String) -> String,
): Map<String, ItemProgressSummary> = issuesBySeriesKey.mapNotNull { (seriesKey, issues) ->
    val issueProgress = issues.mapNotNull { issue ->
        progressByItemId[issue.id]?.let { progress -> issue to progress }
    }
    if (issueProgress.isEmpty()) return@mapNotNull null
    val latestInProgress = issueProgress
        .filter { (_, progress) -> progress.fraction < 0.999f }
        .maxByOrNull { (_, progress) -> progress.lastReadAtEpochMillis }
    val latestDisplayProgress = latestInProgress
        ?: issueProgress.maxByOrNull { (_, progress) -> progress.lastReadAtEpochMillis }
        ?: return@mapNotNull null
    val latestActivityProgress = issueProgress.maxByOrNull { (_, progress) ->
        progress.lastReadAtEpochMillis
    } ?: return@mapNotNull null
    val progressByIssueId = issueProgress.associate { (issue, progress) ->
        issue.id to progress
    }
    val averageFraction = issues
        .map { issue -> progressByIssueId[issue.id]?.fraction?.coerceIn(0f, 1f) ?: 0f }
        .average()
        .toFloat()
    val completedCount = issues.count { issue ->
        (progressByIssueId[issue.id]?.fraction ?: 0f) >= 0.999f
    }
    val isFinished = completedCount == issues.size
    val latestIssueLabel = latestDisplayProgress.first.comicMetadata?.issueTitle?.takeIf(String::isNotBlank)
        ?: latestDisplayProgress.first.title
    seriesContainerId(seriesKey) to ItemProgressSummary(
        label = when {
            isFinished -> "Finished all ${issues.size} issues"
            latestInProgress != null && latestIssueLabel.isNotBlank() -> "Continue $latestIssueLabel"
            else -> "Read $completedCount of ${issues.size} issues"
        },
        fraction = averageFraction,
        lastReadAtEpochMillis = latestActivityProgress.second.lastReadAtEpochMillis,
        isFinished = isFinished,
    )
}.toMap()

private data class SourceIndexCacheKey(
    val libraries: IdentityBox<List<*>>,
    val items: IdentityBox<List<*>>,
) {
    constructor(
        libraries: List<*>,
        items: List<*>,
    ) : this(
        libraries = IdentityBox(libraries),
        items = IdentityBox(items),
    )
}

private data class LibraryDerivedStateCacheKey(
    val sourceIndexKey: SourceIndexCacheKey,
    val selectedSource: SourceType,
    val selectedKavitaSeriesKey: String?,
    val selectedStumpSeriesKey: String?,
    val selectedLibraryFilters: LibraryFilterState,
    val selectedLibrarySearchQuery: String,
    val selectedLibrarySortMode: com.example.unifiedreader.core.model.LibrarySortMode,
    val progressByItemId: IdentityBox<Map<*, *>>,
    val downloadedRemoteItemIds: IdentityBox<Set<*>>,
    val locallyAvailableRemoteItemIds: IdentityBox<Set<*>>,
    val downloadingRemoteItemIds: IdentityBox<Set<*>>,
) {
    constructor(
        sourceIndexKey: SourceIndexCacheKey,
        selectedSource: SourceType,
        selectedKavitaSeriesKey: String?,
        selectedStumpSeriesKey: String?,
        selectedLibraryFilters: LibraryFilterState,
        selectedLibrarySearchQuery: String,
        selectedLibrarySortMode: com.example.unifiedreader.core.model.LibrarySortMode,
        progressByItemId: Map<*, *>,
        downloadedRemoteItemIds: Set<*>,
        locallyAvailableRemoteItemIds: Set<*>,
        downloadingRemoteItemIds: Set<*>,
    ) : this(
        sourceIndexKey = sourceIndexKey,
        selectedSource = selectedSource,
        selectedKavitaSeriesKey = selectedKavitaSeriesKey,
        selectedStumpSeriesKey = selectedStumpSeriesKey,
        selectedLibraryFilters = selectedLibraryFilters,
        selectedLibrarySearchQuery = selectedLibrarySearchQuery,
        selectedLibrarySortMode = selectedLibrarySortMode,
        progressByItemId = IdentityBox(progressByItemId),
        downloadedRemoteItemIds = IdentityBox(downloadedRemoteItemIds),
        locallyAvailableRemoteItemIds = IdentityBox(locallyAvailableRemoteItemIds),
        downloadingRemoteItemIds = IdentityBox(downloadingRemoteItemIds),
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

private const val DERIVED_STATE_CACHE_LIMIT = 24
