package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpMediaCatalogEntry
import com.example.unifiedreader.data.stump.StumpSeriesEntry
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

fun interface StumpApiFactory {
    fun create(
        baseUrl: String,
    ): StumpApi
}

interface StumpCatalogSyncer {
    suspend fun connect(
        request: StumpCatalogSyncRequest,
    ): StumpCatalogConnectionResult

    suspend fun sync(
        request: StumpCatalogSyncRequest,
        onProgress: (StumpSyncProgress) -> Unit = {},
    ): StumpCatalogSyncResult
}

class StumpCatalogSyncService(
    private val stumpApiFactory: StumpApiFactory,
    private val remoteCatalogWriter: RemoteCatalogWriter,
    private val readerSessionStore: ReaderSessionStore? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : StumpCatalogSyncer {
    override suspend fun connect(
        request: StumpCatalogSyncRequest,
    ): StumpCatalogConnectionResult {
        val api = stumpApiFactory.create(request.baseUrl)
        val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
            api.login(
                username = request.username,
                password = password,
            )
        }
        val accessToken = session?.accessToken ?: request.accessToken
            ?: error("A Stump connection requires a password or saved access token.")
        val refreshResult = api.runWithTokenRefresh(
            initialAccessToken = accessToken,
            initialRefreshToken = session?.refreshToken ?: request.refreshToken,
        ) { token ->
            api.getLibraries(token)
        }
        return StumpCatalogConnectionResult(
            libraryCount = refreshResult.value.size,
            session = refreshResult.refreshedSession ?: session,
        )
    }

    override suspend fun sync(
        request: StumpCatalogSyncRequest,
        onProgress: (StumpSyncProgress) -> Unit,
    ): StumpCatalogSyncResult {
        val api = stumpApiFactory.create(request.baseUrl)
        val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
            api.login(
                username = request.username,
                password = password,
            )
        }
        val accessToken = session?.accessToken ?: request.accessToken
            ?: error("A Stump sync requires a password or saved access token.")
        var activeAccessToken = accessToken
        var activeRefreshToken = session?.refreshToken ?: request.refreshToken
        var latestSession: StumpSession? = session
        val fetchResult = api.runWithTokenRefresh(
            initialAccessToken = activeAccessToken,
            initialRefreshToken = activeRefreshToken,
        ) { token ->
            onProgress(StumpSyncProgress(message = "Loading Stump libraries...", fraction = 0.05f))
            val libraries = api.getLibraries(token)
            onProgress(StumpSyncProgress(message = "Loading Stump series...", fraction = 0.15f))
            val series = libraries.flatMap { library ->
                api.getSeries(
                    accessToken = token,
                    libraryId = library.remoteId ?: return@flatMap emptyList(),
                )
            }
            val entries = fetchSeriesEntries(
                api = api,
                accessToken = token,
                series = series,
                onProgress = onProgress,
            )
            StumpSyncFetch(
                libraries = libraries,
                entries = entries,
            )
        }
        activeAccessToken = fetchResult.accessToken
        activeRefreshToken = fetchResult.refreshToken
        latestSession = fetchResult.refreshedSession ?: latestSession
        val libraries = fetchResult.value.libraries
        val entries = fetchResult.value.entries
        val items = entries.map(StumpMediaCatalogEntry::item)
        val updatedAtEpochMillis = nowEpochMillis()
        clearStaleStumpProgress(
            incomingItemIds = items.mapTo(mutableSetOf(), ReadableItem::id),
        )
        onProgress(StumpSyncProgress(message = "Saving Stump catalog...", fraction = 0.92f))
        remoteCatalogWriter.replaceSourceCatalog(
            sourceType = SourceType.STUMP,
            libraries = libraries.map { library -> library.toEntity() },
            items = items.map { item ->
                item.toEntity(updatedAtEpochMillis = updatedAtEpochMillis)
            },
        )
        onProgress(StumpSyncProgress(message = "Applying Stump progress...", fraction = 0.97f))
        pullProgress(entries)
        val progressSync = pushDirtyProgress(
            api = api,
            initialAccessToken = activeAccessToken,
            initialRefreshToken = activeRefreshToken,
            items = items,
        )
        latestSession = progressSync.refreshedSession ?: latestSession
        return StumpCatalogSyncResult(
            libraryCount = libraries.size,
            itemCount = items.size,
            supportedFormatCounts = items.groupingBy(ReadableItem::format).eachCount(),
            session = latestSession,
        )
    }

    private fun pullProgress(
        entries: List<StumpMediaCatalogEntry>,
    ) {
        val store = readerSessionStore ?: return
        entries.forEach { entry ->
            applyRemoteProgressSyncDecision(
                store = store,
                item = entry.item,
                pageCount = entry.item.pageCount,
                decision = decideRemoteProgressSync(
                    local = store.getProgress(entry.item),
                    remote = entry.progress,
                    nowEpochMillis = nowEpochMillis(),
                    missingRemoteClearsLocal = false,
                    remoteClearOverridesLocal = false,
                ),
            )
        }
    }

    private suspend fun clearStaleStumpProgress(
        incomingItemIds: Set<String>,
    ) {
        val store = readerSessionStore ?: return
        remoteCatalogWriter.getItemsForSourceType(SourceType.STUMP)
            .asSequence()
            .map(ReadableItem::id)
            .filterNot(incomingItemIds::contains)
            .distinct()
            .forEach(store::clearItem)
    }

    private suspend fun pushDirtyProgress(
        api: StumpApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        items: List<ReadableItem>,
    ): StumpTokenRefreshResult<Unit> {
        val store = readerSessionStore ?: return StumpTokenRefreshResult(
            value = Unit,
            accessToken = initialAccessToken,
            refreshToken = initialRefreshToken,
            refreshedSession = null,
        )
        return pushDirtyStumpProgress(
            api = api,
            initialAccessToken = initialAccessToken,
            initialRefreshToken = initialRefreshToken,
            items = items,
            readerSessionStore = store,
        )
    }

    private suspend fun fetchSeriesEntries(
        api: StumpApi,
        accessToken: String,
        series: List<StumpSeriesEntry>,
        onProgress: (StumpSyncProgress) -> Unit,
    ): List<StumpMediaCatalogEntry> = coroutineScope {
        if (series.isEmpty()) {
            onProgress(StumpSyncProgress(message = "No Stump series to import.", fraction = 0.85f))
            return@coroutineScope emptyList()
        }
        val detailRequestLimiter = Semaphore(STUMP_SERIES_DETAIL_REQUEST_CONCURRENCY)
        val completedSeriesCount = AtomicInteger(0)
        val reportedSeriesCount = AtomicInteger(0)
        onProgress(
            StumpSyncProgress(
                message = "Loading Stump series 0/${series.size}...",
                fraction = 0.2f,
            ),
        )
        series.map { seriesEntry ->
            async {
                detailRequestLimiter.withPermit {
                    api.getSeriesDetail(
                        accessToken = accessToken,
                        series = seriesEntry,
                    ).also {
                        val completed = completedSeriesCount.incrementAndGet()
                        if (shouldReportStumpSeriesProgress(completed, series.size, reportedSeriesCount)) {
                            val detailFraction = 0.2f + (0.65f * completed.toFloat() / series.size.toFloat())
                            onProgress(
                                StumpSyncProgress(
                                    message = "Loading Stump series $completed/${series.size}...",
                                    fraction = detailFraction.coerceIn(0f, 0.85f),
                                ),
                            )
                        }
                    }
                }
            }
        }.awaitAll().flatten()
    }
}

private fun shouldReportStumpSeriesProgress(
    completed: Int,
    total: Int,
    reportedSeriesCount: AtomicInteger,
): Boolean {
    if (completed <= 0 || total <= 0) return false
    if (completed >= total) {
        reportedSeriesCount.set(completed)
        return true
    }
    val minReportStep = (total / STUMP_PROGRESS_REPORT_STEPS).coerceAtLeast(1)
    val lastReported = reportedSeriesCount.get()
    val shouldReport = completed == 1 || completed - lastReported >= minReportStep
    if (shouldReport) {
        reportedSeriesCount.set(completed)
    }
    return shouldReport
}

private data class StumpSyncFetch(
    val libraries: List<com.example.unifiedreader.core.model.Library>,
    val entries: List<StumpMediaCatalogEntry>,
)

data class StumpCatalogSyncRequest(
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val password: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

data class StumpCatalogConnectionResult(
    val libraryCount: Int,
    val session: StumpSession? = null,
)

data class StumpSyncProgress(
    val message: String,
    val fraction: Float,
)

data class StumpCatalogSyncResult(
    val libraryCount: Int,
    val itemCount: Int,
    val supportedFormatCounts: Map<FormatType, Int>,
    val session: StumpSession? = null,
)

private const val STUMP_SERIES_DETAIL_REQUEST_CONCURRENCY = 6
private const val STUMP_PROGRESS_REPORT_STEPS = 24
