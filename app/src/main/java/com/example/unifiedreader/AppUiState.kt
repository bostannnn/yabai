package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.applyLibraryFilters
import com.example.unifiedreader.core.model.applyLibrarySearch
import com.example.unifiedreader.core.model.applyLibrarySort
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.core.model.buildKavitaSeriesContainer
import com.example.unifiedreader.core.model.buildStumpSeriesContainer
import com.example.unifiedreader.core.model.buildLibraryFilterFacets
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.stumpSeriesContainerId
import com.example.unifiedreader.core.model.stumpSeriesGroupKey
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import com.example.unifiedreader.feature.settings.ThemeMode
import java.util.Base64

internal data class AppCatalogSnapshot(
    val libraries: List<Library>,
    val catalogItems: List<ReadableItem>,
    val items: List<ReadableItem>,
    val downloadedRemoteItemIds: Set<String>,
    val progressByItemId: Map<String, ItemProgressSummary>,
    val hydratedSources: Set<SourceType>,
    val themeMode: ThemeMode,
)

enum class LibraryMessageSeverity {
    Info,
    Error,
}

enum class TopLevelDestination {
    Library,
    Settings,
}

data class AppUiState(
    val topLevel: TopLevelDestination = TopLevelDestination.Library,
    val selectedSource: SourceType = SourceType.KAVITA,
    val selectedItemId: String? = null,
    val activeReader: FormatType? = null,
    val libraries: List<Library> = emptyList(),
    val items: List<ReadableItem> = emptyList(),
    val libraryFiltersBySource: Map<SourceType, LibraryFilterState> = emptyMap(),
    val librarySearchQueriesBySource: Map<SourceType, String> = emptyMap(),
    val librarySortsBySource: Map<SourceType, LibrarySortMode> = emptyMap(),
    val progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val downloadedRemoteItemIds: Set<String> = emptySet(),
    val downloadingRemoteItemIds: Set<String> = emptySet(),
    val libraryMessage: String? = null,
    val libraryMessageSeverity: LibraryMessageSeverity = LibraryMessageSeverity.Info,
    val hydratedSources: Set<SourceType> = setOf(SourceType.KAVITA),
    val themeMode: ThemeMode = ThemeMode.System,
    val kavitaConnection: KavitaConnectionUiState = KavitaConnectionUiState(),
    val stumpConnection: StumpConnectionUiState = StumpConnectionUiState(),
    val grimmoryConnection: GrimmoryConnectionUiState = GrimmoryConnectionUiState(),
    val selectedKavitaSeriesKey: String? = null,
    val selectedStumpSeriesKey: String? = null,
) {
    val sourceItems: List<ReadableItem>
        get() {
            val selectedItems = items.filter { it.sourceType == selectedSource }
            when (selectedSource) {
                SourceType.KAVITA -> {
                    val seriesKey = selectedKavitaSeriesKey
                    if (seriesKey != null) {
                        return selectedItems.filter { item -> item.belongsToKavitaSeries(seriesKey) }
                    }
                    val comicIssues = selectedItems.filter { item -> item.kavitaSeriesGroupKey() != null }
                    val directItems = selectedItems.filterNot { item -> item.kavitaSeriesGroupKey() != null }
                    val seriesItems = comicIssues
                        .groupBy { issue -> issue.kavitaSeriesGroupKey()!! }
                        .map { (key, issues) -> buildKavitaSeriesContainer(key, issues) }
                    return directItems + seriesItems
                }
                SourceType.STUMP -> {
                    val seriesKey = selectedStumpSeriesKey
                    if (seriesKey != null) {
                        return selectedItems.filter { item -> item.belongsToStumpSeries(seriesKey) }
                    }
                    val comicIssues = selectedItems.filter { item -> item.stumpSeriesGroupKey() != null }
                    val directItems = selectedItems.filterNot { item -> item.stumpSeriesGroupKey() != null }
                    val seriesItems = comicIssues
                        .groupBy { issue -> issue.stumpSeriesGroupKey()!! }
                        .map { (key, issues) -> buildStumpSeriesContainer(key, issues) }
                    return directItems + seriesItems
                }
                SourceType.GRIMMORY,
                SourceType.LOCAL -> return selectedItems
            }
        }

    val selectedLibraryFilters: LibraryFilterState
        get() = libraryFiltersBySource[selectedSource] ?: LibraryFilterState()

    val selectedLibrarySearchQuery: String
        get() = librarySearchQueriesBySource[selectedSource].orEmpty()

    val visibleLibraryErrorMessage: String?
        get() = libraryMessage.takeIf { libraryMessageSeverity == LibraryMessageSeverity.Error }

    val selectedLibraryFilterFacets: LibraryFilterFacets
        get() = buildLibraryFilterFacets(sourceItems, downloadedRemoteItemIds)

    val selectedSourceLibraryTitlesById: Map<String, String>
        get() = libraries
            .filter { library -> library.sourceType == selectedSource }
            .associate { library -> library.id to library.title }

    val displayProgressByItemId: Map<String, ItemProgressSummary>
        get() = when {
            selectedSource == SourceType.KAVITA && selectedKavitaSeriesKey == null ->
                progressByItemId + buildKavitaSeriesProgressByItemId(items, progressByItemId)
            selectedSource == SourceType.STUMP && selectedStumpSeriesKey == null ->
                progressByItemId + buildStumpSeriesProgressByItemId(items, progressByItemId)
            else -> progressByItemId
        }

    val selectedLibrarySortMode: LibrarySortMode
        get() = librarySortsBySource[selectedSource] ?: LibrarySortMode.DATE_ADDED

    val isSelectedSourceHydrated: Boolean
        get() = selectedSource in hydratedSources

    val visibleItems: List<ReadableItem>
        get() = applyLibrarySort(
            items = applyLibrarySearch(
                items = applyLibraryFilters(sourceItems, selectedLibraryFilters, downloadedRemoteItemIds),
                query = selectedLibrarySearchQuery,
            ),
            sortMode = selectedLibrarySortMode,
            lastReadAtEpochMillisByItemId = displayProgressByItemId.mapValues { (_, progress) ->
                progress.lastReadAtEpochMillis
            },
        )

    val selectedItem: ReadableItem?
        get() = items.firstOrNull { it.id == selectedItemId }

    val selectedKavitaSeriesTitle: String?
        get() = selectedKavitaSeriesKey?.let { seriesKey ->
            items.firstOrNull { item -> item.belongsToKavitaSeries(seriesKey) }?.let { item ->
                item.comicMetadata?.series?.takeIf(String::isNotBlank) ?: item.title
            }
        }

    val selectedStumpSeriesTitle: String?
        get() = selectedStumpSeriesKey?.let { seriesKey ->
            items.firstOrNull { item -> item.belongsToStumpSeries(seriesKey) }?.let { item ->
                item.comicMetadata?.series?.takeIf(String::isNotBlank) ?: item.title
            }
        }

    val selectedSeriesTitle: String?
        get() = when (selectedSource) {
            SourceType.KAVITA -> selectedKavitaSeriesTitle
            SourceType.STUMP -> selectedStumpSeriesTitle
            SourceType.GRIMMORY,
            SourceType.LOCAL -> null
        }

    val libraryBrowseContextKey: String
        get() = when (selectedSource) {
            SourceType.KAVITA -> selectedKavitaSeriesKey?.let { seriesKey ->
                "kavita:series:${seriesKey.keyComponent()}:${libraryBrowseStateKeySuffix()}"
            } ?: "kavita:root:${libraryBrowseStateKeySuffix()}"
            SourceType.STUMP -> selectedStumpSeriesKey?.let { seriesKey ->
                "stump:series:${seriesKey.keyComponent()}:${libraryBrowseStateKeySuffix()}"
            } ?: "stump:root:${libraryBrowseStateKeySuffix()}"
            SourceType.GRIMMORY -> "grimmory:root:${libraryBrowseStateKeySuffix()}"
            SourceType.LOCAL -> "local:root:${libraryBrowseStateKeySuffix()}"
        }
}

private fun AppUiState.libraryBrowseStateKeySuffix(): String = buildString {
    append("query=")
    append(selectedLibrarySearchQuery.keyComponent())
    append("|sort=")
    append(selectedLibrarySortMode.name)
    append("|filters=")
    append(selectedLibraryFilters.keyFragment())
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

private fun buildKavitaSeriesProgressByItemId(
    items: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): Map<String, ItemProgressSummary> = items
    .filter { item ->
        item.sourceType == SourceType.KAVITA &&
            item.kavitaSeriesGroupKey() != null &&
            !item.isKavitaSeriesContainer()
    }
    .groupBy { item -> item.kavitaSeriesGroupKey()!! }
    .mapNotNull { (seriesKey, issues) ->
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
        val latestIssueLabel = latestDisplayProgress.first.comicMetadata?.issueTitle?.takeIf(String::isNotBlank)
            ?: latestDisplayProgress.first.title
        kavitaSeriesContainerId(seriesKey) to ItemProgressSummary(
            label = when {
                completedCount == issues.size -> "Finished all ${issues.size} issues"
                latestInProgress != null && latestIssueLabel.isNotBlank() -> "Continue $latestIssueLabel"
                else -> "Read $completedCount of ${issues.size} issues"
            },
            fraction = averageFraction,
            lastReadAtEpochMillis = latestActivityProgress.second.lastReadAtEpochMillis,
        )
    }
    .toMap()

private fun buildStumpSeriesProgressByItemId(
    items: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): Map<String, ItemProgressSummary> = items
    .filter { item ->
        item.sourceType == SourceType.STUMP &&
            item.stumpSeriesGroupKey() != null &&
            !item.isStumpSeriesContainer()
    }
    .groupBy { item -> item.stumpSeriesGroupKey()!! }
    .mapNotNull { (seriesKey, issues) ->
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
        val latestIssueLabel = latestDisplayProgress.first.comicMetadata?.issueTitle?.takeIf(String::isNotBlank)
            ?: latestDisplayProgress.first.title
        stumpSeriesContainerId(seriesKey) to ItemProgressSummary(
            label = when {
                completedCount == issues.size -> "Finished all ${issues.size} issues"
                latestInProgress != null && latestIssueLabel.isNotBlank() -> "Continue $latestIssueLabel"
                else -> "Read $completedCount of ${issues.size} issues"
            },
            fraction = averageFraction,
            lastReadAtEpochMillis = latestActivityProgress.second.lastReadAtEpochMillis,
        )
    }
    .toMap()
