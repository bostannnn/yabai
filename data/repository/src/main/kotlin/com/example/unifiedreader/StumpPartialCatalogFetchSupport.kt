package com.example.unifiedreader

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpGraphQlErrorDetail
import com.example.unifiedreader.data.stump.StumpMediaCatalogEntry
import com.example.unifiedreader.data.stump.StumpSeriesEntry
import com.example.unifiedreader.data.stump.StumpUnauthorizedException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class StumpPartialCatalogFetchResult(
    val fetch: RemoteCatalogFetch<StumpMediaCatalogEntry>,
    val warning: StumpPartialCatalogWarning? = null,
)

data class StumpPartialCatalogWarning(
    val failedSeriesCount: Int,
    val incompleteSeriesCount: Int,
    val preservedItemCount: Int,
    val firstFailureMessage: String? = null,
    val firstGraphQlMessage: String? = null,
) {
    val affectedSeriesCount: Int
        get() = failedSeriesCount + incompleteSeriesCount
}

suspend fun fetchStumpCatalogWithPartialSeriesRecovery(
    api: StumpApi,
    accessToken: String,
    libraries: List<Library>,
    remoteCatalogWriter: RemoteCatalogWriter,
    onProgress: (message: String, fraction: Float) -> Unit,
): StumpPartialCatalogFetchResult {
    onProgress("Loading Stump series...", 0.15f)
    val series = libraries.flatMap { library ->
        api.getSeries(
            accessToken = accessToken,
            libraryId = library.remoteId ?: return@flatMap emptyList(),
        )
    }
    val detailFetch = fetchStumpSeriesEntriesWithPartialRecovery(
        api = api,
        accessToken = accessToken,
        series = series,
        onProgress = onProgress,
    )
    val fetchedEntries = detailFetch.entries
    val preservedEntries = preserveExistingStumpEntries(
        remoteCatalogWriter = remoteCatalogWriter,
        affectedSeriesIds = detailFetch.affectedSeriesIds,
        fetchedEntries = fetchedEntries,
    )
    return StumpPartialCatalogFetchResult(
        fetch = RemoteCatalogFetch(
            libraries = libraries,
            entries = fetchedEntries + preservedEntries,
        ),
        warning = detailFetch.toWarning(preservedItemCount = preservedEntries.size),
    )
}

private suspend fun fetchStumpSeriesEntriesWithPartialRecovery(
    api: StumpApi,
    accessToken: String,
    series: List<StumpSeriesEntry>,
    onProgress: (message: String, fraction: Float) -> Unit,
    detailRequestConcurrency: Int = DEFAULT_STUMP_SERIES_DETAIL_REQUEST_CONCURRENCY,
    progressSteps: Int = DEFAULT_STUMP_PROGRESS_REPORT_STEPS,
): StumpSeriesDetailFetch = coroutineScope {
    if (series.isEmpty()) {
        onProgress("No Stump series to import.", 0.85f)
        return@coroutineScope StumpSeriesDetailFetch(entries = emptyList())
    }
    val detailRequestLimiter = Semaphore(detailRequestConcurrency)
    val completedSeriesCount = AtomicInteger(0)
    val reportedSeriesCount = AtomicInteger(0)
    onProgress("Loading Stump series 0/${series.size}...", 0.2f)
    val results = series.map { seriesEntry ->
        async {
            detailRequestLimiter.withPermit {
                fetchStumpSeriesDetail(
                    api = api,
                    accessToken = accessToken,
                    series = seriesEntry,
                ).also {
                    val completed = completedSeriesCount.incrementAndGet()
                    if (shouldReportSeriesProgress(completed, series.size, reportedSeriesCount, progressSteps)) {
                        val detailFraction = 0.2f + (0.65f * completed.toFloat() / series.size.toFloat())
                        onProgress(
                            "Loading Stump series $completed/${series.size}...",
                            detailFraction.coerceIn(0f, 0.85f),
                        )
                    }
                }
            }
        }
    }.awaitAll()
    StumpSeriesDetailFetch(
        entries = results.flatMap { result -> result.entries },
        failedSeries = results.mapNotNull { result -> result.failedSeries },
        incompleteSeries = results.mapNotNull { result -> result.incompleteSeries },
        graphQlErrors = results.flatMap { result -> result.graphQlErrors },
    )
}

private suspend fun fetchStumpSeriesDetail(
    api: StumpApi,
    accessToken: String,
    series: StumpSeriesEntry,
): StumpSeriesDetailFetchResult {
    return try {
        val result = api.getSeriesDetailResult(
            accessToken = accessToken,
            series = series,
        )
        StumpSeriesDetailFetchResult(
            entries = if (result.isIncomplete) {
                result.entries.withoutExplicitUnreadResets()
            } else {
                result.entries
            },
            incompleteSeries = series.takeIf { result.isIncomplete },
            graphQlErrors = result.errors,
        )
    } catch (exception: StumpUnauthorizedException) {
        throw exception
    } catch (exception: CancellationException) {
        throw exception
    } catch (throwable: Throwable) {
        StumpSeriesDetailFetchResult(
            entries = emptyList(),
            failedSeries = FailedStumpSeriesDetail(
                series = series,
                throwable = throwable,
            ),
        )
    }
}

private fun List<StumpMediaCatalogEntry>.withoutExplicitUnreadResets(): List<StumpMediaCatalogEntry> =
    map { entry ->
        if (entry.isExplicitUnreadReset) {
            entry.copy(isExplicitUnreadReset = false)
        } else {
            entry
        }
    }

private suspend fun preserveExistingStumpEntries(
    remoteCatalogWriter: RemoteCatalogWriter,
    affectedSeriesIds: Set<String>,
    fetchedEntries: List<StumpMediaCatalogEntry>,
): List<StumpMediaCatalogEntry> {
    if (affectedSeriesIds.isEmpty()) return emptyList()
    val fetchedItemIds = fetchedEntries.mapTo(mutableSetOf()) { entry -> entry.item.id }
    return remoteCatalogWriter.getItemsForSourceType(SourceType.STUMP)
        .filter { item ->
            item.remoteFileType in affectedSeriesIds && item.id !in fetchedItemIds
        }
        .map(::preservedStumpCatalogEntry)
}

private fun preservedStumpCatalogEntry(
    item: ReadableItem,
): StumpMediaCatalogEntry = StumpMediaCatalogEntry(
    item = item,
    progress = null,
    isExplicitUnreadReset = false,
)

private fun StumpSeriesDetailFetch.toWarning(
    preservedItemCount: Int,
): StumpPartialCatalogWarning? {
    if (failedSeries.isEmpty() && incompleteSeries.isEmpty()) return null
    return StumpPartialCatalogWarning(
        failedSeriesCount = failedSeries.size,
        incompleteSeriesCount = incompleteSeries.size,
        preservedItemCount = preservedItemCount,
        firstFailureMessage = failedSeries.firstOrNull()?.throwable?.message,
        firstGraphQlMessage = graphQlErrors.firstOrNull()?.displayMessage,
    )
}

private data class StumpSeriesDetailFetch(
    val entries: List<StumpMediaCatalogEntry>,
    val failedSeries: List<FailedStumpSeriesDetail> = emptyList(),
    val incompleteSeries: List<StumpSeriesEntry> = emptyList(),
    val graphQlErrors: List<StumpGraphQlErrorDetail> = emptyList(),
) {
    val affectedSeriesIds: Set<String>
        get() = buildSet {
            failedSeries.mapTo(this) { failure -> failure.series.id }
            incompleteSeries.mapTo(this, StumpSeriesEntry::id)
        }
}

private data class StumpSeriesDetailFetchResult(
    val entries: List<StumpMediaCatalogEntry>,
    val failedSeries: FailedStumpSeriesDetail? = null,
    val incompleteSeries: StumpSeriesEntry? = null,
    val graphQlErrors: List<StumpGraphQlErrorDetail> = emptyList(),
)

private data class FailedStumpSeriesDetail(
    val series: StumpSeriesEntry,
    val throwable: Throwable,
)

private const val DEFAULT_STUMP_SERIES_DETAIL_REQUEST_CONCURRENCY = 6
private const val DEFAULT_STUMP_PROGRESS_REPORT_STEPS = 24
