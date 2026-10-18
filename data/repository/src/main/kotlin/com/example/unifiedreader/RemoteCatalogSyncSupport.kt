package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RemoteCatalogFetch<Entry>(
    val libraries: List<Library>,
    val entries: List<Entry>,
)

data class RemoteCatalogSyncOutcome<Session>(
    val libraryCount: Int,
    val itemCount: Int,
    val supportedFormatCounts: Map<FormatType, Int>,
    val session: Session?,
    val progressPushFailures: List<Throwable>,
)

data class RemoteCatalogProgressPushResult<Session>(
    val session: Session?,
    val failures: List<Throwable>,
)

suspend fun <Entry, Session> finalizeRemoteCatalogSync(
    sourceType: SourceType,
    fetch: RemoteCatalogFetch<Entry>,
    remoteCatalogWriter: RemoteCatalogWriter,
    progressSyncStore: ProgressSyncStore?,
    nowEpochMillis: Long,
    onProgress: (message: String, fraction: Float) -> Unit,
    savingMessage: String,
    applyingProgressMessage: String,
    itemOf: (Entry) -> ReadableItem,
    progressOf: (Entry) -> ReadingProgress?,
    missingRemoteClearsLocal: (Entry) -> Boolean = { false },
    remoteClearOverridesLocal: (Entry) -> Boolean = { false },
    localRefinesRemote: (local: ProgressSyncSnapshot, remote: ReadingProgress?) -> Boolean = { _, _ -> false },
    syncGroups: List<RemoteCatalogSyncGroupEntity> = emptyList(),
    pushDirtyProgress: suspend (items: List<ReadableItem>) -> RemoteCatalogProgressPushResult<Session>,
): RemoteCatalogSyncOutcome<Session> {
    val libraries = fetch.libraries
    val entries = fetch.entries
    val items = entries.map(itemOf)
    clearStaleRemoteProgress(
        sourceType = sourceType,
        incomingItemIds = items.mapTo(mutableSetOf(), ReadableItem::id),
        remoteCatalogWriter = remoteCatalogWriter,
        progressSyncStore = progressSyncStore,
    )
    onProgress(savingMessage, 0.92f)
    remoteCatalogWriter.replaceSourceCatalog(
        sourceType = sourceType,
        libraries = libraries.map { library -> library.toEntity() },
        items = items.map { item ->
            item.toEntity(updatedAtEpochMillis = nowEpochMillis)
        },
        syncGroups = syncGroups,
    )
    onProgress(applyingProgressMessage, 0.97f)
    applyRemoteCatalogProgress(
        entries = entries,
        progressSyncStore = progressSyncStore,
        nowEpochMillis = nowEpochMillis,
        itemOf = itemOf,
        progressOf = progressOf,
        missingRemoteClearsLocal = missingRemoteClearsLocal,
        remoteClearOverridesLocal = remoteClearOverridesLocal,
        localRefinesRemote = localRefinesRemote,
    )
    val progressPushResult = pushDirtyProgress(items)
    return RemoteCatalogSyncOutcome(
        libraryCount = libraries.size,
        itemCount = items.size,
        supportedFormatCounts = items.groupingBy(ReadableItem::format).eachCount(),
        session = progressPushResult.session,
        progressPushFailures = progressPushResult.failures,
    )
}

suspend fun <Series, Entry> fetchSeriesEntriesWithProgress(
    sourceLabel: String,
    series: List<Series>,
    onProgress: (message: String, fraction: Float) -> Unit,
    detailRequestConcurrency: Int = DEFAULT_REMOTE_SERIES_DETAIL_REQUEST_CONCURRENCY,
    progressSteps: Int = DEFAULT_REMOTE_PROGRESS_REPORT_STEPS,
    fetchEntries: suspend (series: Series) -> List<Entry>,
): List<Entry> = coroutineScope {
    if (series.isEmpty()) {
        onProgress("No $sourceLabel series to import.", 0.85f)
        return@coroutineScope emptyList()
    }
    val detailRequestLimiter = Semaphore(detailRequestConcurrency)
    val completedSeriesCount = AtomicInteger(0)
    val reportedSeriesCount = AtomicInteger(0)
    onProgress("Loading $sourceLabel series 0/${series.size}...", 0.2f)
    series.map { seriesEntry ->
        async {
            detailRequestLimiter.withPermit {
                fetchEntries(seriesEntry).also {
                    val completed = completedSeriesCount.incrementAndGet()
                    if (shouldReportSeriesProgress(completed, series.size, reportedSeriesCount, progressSteps)) {
                        val detailFraction = 0.2f + (0.65f * completed.toFloat() / series.size.toFloat())
                        onProgress(
                            "Loading $sourceLabel series $completed/${series.size}...",
                            detailFraction.coerceIn(0f, 0.85f),
                        )
                    }
                }
            }
        }
    }.awaitAll().flatten()
}

fun shouldReportSeriesProgress(
    completed: Int,
    total: Int,
    reportedSeriesCount: AtomicInteger,
    progressSteps: Int = DEFAULT_REMOTE_PROGRESS_REPORT_STEPS,
): Boolean {
    if (completed <= 0 || total <= 0) return false
    if (completed >= total) {
        reportedSeriesCount.set(completed)
        return true
    }
    val minReportStep = (total / progressSteps).coerceAtLeast(1)
    val lastReported = reportedSeriesCount.get()
    val shouldReport = completed == 1 || completed - lastReported >= minReportStep
    if (shouldReport) {
        reportedSeriesCount.set(completed)
    }
    return shouldReport
}

fun <Entry> applyRemoteCatalogProgress(
    entries: List<Entry>,
    progressSyncStore: ProgressSyncStore?,
    nowEpochMillis: Long,
    itemOf: (Entry) -> ReadableItem,
    progressOf: (Entry) -> ReadingProgress?,
    missingRemoteClearsLocal: (Entry) -> Boolean,
    remoteClearOverridesLocal: (Entry) -> Boolean,
    localRefinesRemote: (local: ProgressSyncSnapshot, remote: ReadingProgress?) -> Boolean = { _, _ -> false },
) {
    val store = progressSyncStore ?: return
    entries.forEach { entry ->
        val item = itemOf(entry)
        applyRemoteProgressSyncDecision(
            store = store,
            item = item,
            pageCount = item.pageCount,
            decision = decideRemoteProgressSync(
                local = store.getProgress(item),
                remote = progressOf(entry),
                nowEpochMillis = nowEpochMillis,
                missingRemoteClearsLocal = missingRemoteClearsLocal(entry),
                remoteClearOverridesLocal = remoteClearOverridesLocal(entry),
                localRefinesRemote = localRefinesRemote,
            ),
        )
    }
}

fun clearRemoteCatalogProgressItems(
    itemIds: Set<String>,
    progressSyncStore: ProgressSyncStore?,
) {
    val store = progressSyncStore ?: return
    itemIds.forEach(store::clearItem)
}

private suspend fun clearStaleRemoteProgress(
    sourceType: SourceType,
    incomingItemIds: Set<String>,
    remoteCatalogWriter: RemoteCatalogWriter,
    progressSyncStore: ProgressSyncStore?,
) {
    val store = progressSyncStore ?: return
    remoteCatalogWriter.getItemsForSourceType(sourceType)
        .asSequence()
        .map(ReadableItem::id)
        .filterNot(incomingItemIds::contains)
        .distinct()
        .forEach(store::clearItem)
}

private const val DEFAULT_REMOTE_SERIES_DETAIL_REQUEST_CONCURRENCY = 6
private const val DEFAULT_REMOTE_PROGRESS_REPORT_STEPS = 24
