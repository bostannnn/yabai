package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

fun interface KavitaApiFactory {
    fun create(
        baseUrl: String,
    ): KavitaApi
}

interface KavitaCatalogSyncer {
    suspend fun connect(
        request: KavitaCatalogSyncRequest,
    ): KavitaCatalogConnectionResult

    suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit = {},
    ): KavitaCatalogSyncResult
}

class KavitaCatalogSyncService(
    private val kavitaApiFactory: KavitaApiFactory,
    private val remoteCatalogWriter: RemoteCatalogWriter,
    private val readerSessionStore: ReaderSessionStore? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : KavitaCatalogSyncer {
    override suspend fun connect(
        request: KavitaCatalogSyncRequest,
    ): KavitaCatalogConnectionResult {
        val api = kavitaApiFactory.create(request.baseUrl)
        val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
            api.login(
                username = request.username,
                password = password,
            )
        }
        val accessToken = session?.accessToken ?: request.accessToken
            ?: error("A Kavita connection requires a password or saved access token.")
        val refreshResult = api.runWithTokenRefresh(
            initialAccessToken = accessToken,
            initialRefreshToken = session?.refreshToken ?: request.refreshToken,
            initialApiKey = session?.apiKey ?: request.apiKey,
        ) { token, _ ->
            api.getLibraries(token)
        }
        return KavitaCatalogConnectionResult(
            libraryCount = refreshResult.value.size,
            session = refreshResult.refreshedSession ?: session,
        )
    }

    override suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): KavitaCatalogSyncResult {
        val api = kavitaApiFactory.create(request.baseUrl)
        val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
            api.login(
                username = request.username,
                password = password,
            )
        }
        val accessToken = session?.accessToken ?: request.accessToken
            ?: error("A Kavita sync requires a password or saved access token.")
        var activeAccessToken = accessToken
        var activeRefreshToken = session?.refreshToken ?: request.refreshToken
        var activeApiKey = session?.apiKey ?: request.apiKey
        var latestSession: KavitaSession? = session
        val fetchResult = api.runWithTokenRefresh(
            initialAccessToken = activeAccessToken,
            initialRefreshToken = activeRefreshToken,
            initialApiKey = activeApiKey,
        ) { token, apiKey ->
            onProgress(KavitaSyncProgress(message = "Loading Kavita libraries...", fraction = 0.05f))
            val libraries = api.getLibraries(token)
            onProgress(KavitaSyncProgress(message = "Loading Kavita series...", fraction = 0.15f))
            val series = api.getSeries(
                accessToken = token,
                apiKey = apiKey,
            )
            val entries = fetchSeriesEntries(
                api = api,
                accessToken = token,
                series = series,
                onProgress = onProgress,
            )
            KavitaSyncFetch(
                libraries = libraries,
                entries = entries,
            )
        }
        activeAccessToken = fetchResult.accessToken
        activeRefreshToken = fetchResult.refreshToken
        activeApiKey = fetchResult.refreshedSession?.apiKey ?: activeApiKey
        latestSession = fetchResult.refreshedSession ?: latestSession
        val libraries = fetchResult.value.libraries
        val entries = fetchResult.value.entries
        val items = entries.map(KavitaChapterCatalogEntry::item)
        val updatedAtEpochMillis = nowEpochMillis()
        clearStaleKavitaProgress(
            incomingItemIds = items.mapTo(mutableSetOf(), ReadableItem::id),
        )
        onProgress(KavitaSyncProgress(message = "Saving Kavita catalog...", fraction = 0.92f))
        remoteCatalogWriter.replaceSourceCatalog(
            sourceType = SourceType.KAVITA,
            libraries = libraries.map { library -> library.toEntity() },
            items = items.map { item ->
                item.toEntity(updatedAtEpochMillis = updatedAtEpochMillis)
            },
        )
        onProgress(KavitaSyncProgress(message = "Applying Kavita progress...", fraction = 0.97f))
        pullProgress(entries)
        val progressSync = pushDirtyProgress(
            api = api,
            initialAccessToken = activeAccessToken,
            initialRefreshToken = activeRefreshToken,
            initialApiKey = activeApiKey,
            items = items,
        )
        latestSession = progressSync.refreshedSession ?: latestSession
        return KavitaCatalogSyncResult(
            libraryCount = libraries.size,
            itemCount = items.size,
            supportedFormatCounts = items.groupingBy(ReadableItem::format).eachCount(),
            session = latestSession,
        )
    }

    private fun pullProgress(
        entries: List<KavitaChapterCatalogEntry>,
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
                    missingRemoteClearsLocal = entry.isExplicitUnreadReset,
                    remoteClearOverridesLocal = false,
                ),
            )
        }
    }

    private suspend fun clearStaleKavitaProgress(
        incomingItemIds: Set<String>,
    ) {
        val store = readerSessionStore ?: return
        remoteCatalogWriter.getItemsForSourceType(SourceType.KAVITA)
            .asSequence()
            .map(ReadableItem::id)
            .filterNot(incomingItemIds::contains)
            .distinct()
            .forEach(store::clearItem)
    }

    private suspend fun pushDirtyProgress(
        api: KavitaApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        initialApiKey: String?,
        items: List<ReadableItem>,
    ): KavitaTokenRefreshResult<Unit> {
        val store = readerSessionStore ?: return KavitaTokenRefreshResult(
            value = Unit,
            accessToken = initialAccessToken,
            refreshToken = initialRefreshToken,
            refreshedSession = null,
        )
        return pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = initialAccessToken,
            initialRefreshToken = initialRefreshToken,
            initialApiKey = initialApiKey,
            items = items,
            readerSessionStore = store,
        )
    }

    private suspend fun fetchSeriesEntries(
        api: KavitaApi,
        accessToken: String,
        series: List<KavitaSeriesEntry>,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): List<KavitaChapterCatalogEntry> = coroutineScope {
        if (series.isEmpty()) {
            onProgress(KavitaSyncProgress(message = "No Kavita series to import.", fraction = 0.85f))
            return@coroutineScope emptyList()
        }
        val detailRequestLimiter = Semaphore(KAVITA_SERIES_DETAIL_REQUEST_CONCURRENCY)
        val completedSeriesCount = AtomicInteger(0)
        val reportedSeriesCount = AtomicInteger(0)
        onProgress(
            KavitaSyncProgress(
                message = "Loading Kavita series 0/${series.size}...",
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
                        if (shouldReportSeriesProgress(completed, series.size, reportedSeriesCount)) {
                            val detailFraction = 0.2f + (0.65f * completed.toFloat() / series.size.toFloat())
                            onProgress(
                                KavitaSyncProgress(
                                    message = "Loading Kavita series $completed/${series.size}...",
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

private fun shouldReportSeriesProgress(
    completed: Int,
    total: Int,
    reportedSeriesCount: AtomicInteger,
): Boolean {
    if (completed <= 0 || total <= 0) return false
    if (completed >= total) {
        reportedSeriesCount.set(completed)
        return true
    }
    val minReportStep = (total / KAVITA_PROGRESS_REPORT_STEPS).coerceAtLeast(1)
    val lastReported = reportedSeriesCount.get()
    val shouldReport = completed == 1 || completed - lastReported >= minReportStep
    if (shouldReport) {
        reportedSeriesCount.set(completed)
    }
    return shouldReport
}

private data class KavitaSyncFetch(
    val libraries: List<com.example.unifiedreader.core.model.Library>,
    val entries: List<KavitaChapterCatalogEntry>,
)

data class KavitaCatalogSyncRequest(
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val password: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val apiKey: String? = null,
)

data class KavitaCatalogConnectionResult(
    val libraryCount: Int,
    val session: KavitaSession? = null,
)

data class KavitaSyncProgress(
    val message: String,
    val fraction: Float,
)

data class KavitaCatalogSyncResult(
    val libraryCount: Int,
    val itemCount: Int,
    val supportedFormatCounts: Map<FormatType, Int>,
    val session: KavitaSession? = null,
)

private const val KAVITA_SERIES_DETAIL_REQUEST_CONCURRENCY = 6
private const val KAVITA_PROGRESS_REPORT_STEPS = 24
