package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.buildContextualLibraryFilterFacets
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
        )
        val activeDownloadingItemIds = buildActiveDownloadingItemIds(
            downloadingItemIds = input.downloadingRemoteItemIds,
            sourceIndex = sourceIndex,
        )
        val downloadActionSourceItems = (sourceItems + sourceIndex.rawSourceItems(input.selectedSource))
            .distinctBy(ReadableItem::id)
        val remoteDownloadActionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = downloadActionSourceItems,
            downloadedItemIds = input.downloadedRemoteItemIds,
            activeDownloadingItemIds = activeDownloadingItemIds,
            actionItemsProvider = sourceIndex::resolveDownloadActionItems,
        )
        val lastReadAtEpochMillisByItemId = lastReadAtEpochMillisByItemId(
            input = input,
            sourceIndex = sourceIndex,
            progressByItemId = displayProgressByItemId,
        )
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
            selectedLibraryFilterFacets = buildContextualLibraryFilterFacets(
                items = sourceItems,
                filters = input.selectedLibraryFilters,
                downloadedRemoteItemIds = input.locallyAvailableRemoteItemIds,
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

    fun computeQuick(
        input: LibraryDerivedStateInput,
    ): LibraryDerivedState {
        val sourceIndex = sourceIndexFor(input)
        val sourceItems = sourceIndex.sourceItems(
            selectedSource = input.selectedSource,
            selectedKavitaSeriesKey = input.selectedKavitaSeriesKey,
            selectedStumpSeriesKey = input.selectedStumpSeriesKey,
        )
        return LibraryDerivedState(
            sourceItems = sourceItems,
            selectedLibraryFilterFacets = buildContextualLibraryFilterFacets(
                sourceItems,
                downloadedRemoteItemIds = emptySet(),
                filters = input.selectedLibraryFilters,
            ),
            selectedSourceLibraryTitlesById = sourceIndex.selectedSourceLibraryTitlesById(input.selectedSource),
            displayProgressByItemId = input.progressByItemId,
            remoteDownloadActionAvailability = RemoteDownloadActionAvailability(),
            visibleItems = buildVisibleLibraryItems(
                sourceItems = sourceItems,
                filterState = input.selectedLibraryFilters,
                searchQuery = input.selectedLibrarySearchQuery,
                sortMode = input.selectedLibrarySortMode,
                downloadedRemoteItemIds = emptySet(),
                lastReadAtEpochMillisByItemId = lastReadAtEpochMillisByItemId(
                    input = input,
                    sourceIndex = sourceIndex,
                    progressByItemId = input.progressByItemId,
                ),
                forceSeriesIssueOrder = input.isSeriesBrowseActive,
            ),
            libraryBrowseContextKey = buildLibraryBrowseContextKey(input),
        )
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
    ): Map<String, ItemProgressSummary> = input.progressByItemId

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
    append(";publishers=")
    append(publishers.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(",") { value -> value.keyComponent() })
    append(";genres=")
    append(genres.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(",") { value -> value.keyComponent() })
    append(";tags=")
    append(tags.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(",") { value -> value.keyComponent() })
    append(";languages=")
    append(languages.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(",") { value -> value.keyComponent() })
}

private fun String.keyComponent(): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(toByteArray(Charsets.UTF_8))

private fun lastReadAtEpochMillisByItemId(
    input: LibraryDerivedStateInput,
    sourceIndex: LibrarySourceIndex,
    progressByItemId: Map<String, ItemProgressSummary>,
): Map<String, Long> {
    if (input.selectedLibrarySortMode != LibrarySortMode.LAST_READ || progressByItemId.isEmpty()) {
        return emptyMap()
    }
    val itemLastReadAt = progressByItemId.mapValues { (_, progress) -> progress.lastReadAtEpochMillis }
    if (input.isSeriesBrowseActive) {
        return itemLastReadAt
    }
    val rootSeriesLastReadAt = when (input.selectedSource) {
        SourceType.KAVITA -> buildRootSeriesLastReadAtEpochMillisByItemId(
            issuesBySeriesKey = sourceIndex.issueGroupsFor(SourceType.KAVITA),
            progressByItemId = progressByItemId,
            seriesContainerId = ::kavitaSeriesContainerId,
        )

        SourceType.STUMP -> buildRootSeriesLastReadAtEpochMillisByItemId(
            issuesBySeriesKey = sourceIndex.issueGroupsFor(SourceType.STUMP),
            progressByItemId = progressByItemId,
            seriesContainerId = ::stumpSeriesContainerId,
        )

        SourceType.GRIMMORY,
        SourceType.LOCAL -> emptyMap()
    }
    return itemLastReadAt + rootSeriesLastReadAt
}

private fun buildRootSeriesLastReadAtEpochMillisByItemId(
    issuesBySeriesKey: Map<String, List<ReadableItem>>,
    progressByItemId: Map<String, ItemProgressSummary>,
    seriesContainerId: (String) -> String,
): Map<String, Long> = issuesBySeriesKey.mapNotNull { (seriesKey, issues) ->
    val lastReadAtEpochMillis = issues.maxOfOrNull { issue ->
        progressByItemId[issue.id]?.lastReadAtEpochMillis ?: Long.MIN_VALUE
    } ?: Long.MIN_VALUE
    lastReadAtEpochMillis
        .takeIf { it > 0L }
        ?.let { seriesContainerId(seriesKey) to it }
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
