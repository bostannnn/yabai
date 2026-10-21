package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSeriesUpdateEntry
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
import com.example.unifiedreader.data.kavita.pullKavitaJournalCache
import com.example.unifiedreader.data.kavita.pushDirtyKavitaJournalEntries
import com.example.unifiedreader.data.kavita.toReadingProgress
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
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

    suspend fun fastSync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit = {},
    ): KavitaCatalogSyncResult
}

class KavitaCatalogSyncService(
    private val kavitaApiFactory: KavitaApiFactory,
    private val remoteCatalogWriter: RemoteCatalogWriter,
    private val readerSessionStore: ReaderSessionStore? = null,
    private val journalRepository: JournalRepository? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : KavitaCatalogSyncer {
    private val progressSyncStore: ProgressSyncStore? = readerSessionStore?.let(::ReaderSessionProgressStoreAdapter)

    override suspend fun connect(
        request: KavitaCatalogSyncRequest,
    ): KavitaCatalogConnectionResult {
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = kavitaApiFactory.create(selectedBaseUrl)
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
            KavitaCatalogConnectionResult(
                libraryCount = refreshResult.value.size,
                libraries = refreshResult.value,
                session = refreshResult.refreshedSession ?: session,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    override suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): KavitaCatalogSyncResult {
        val syncStartedAt = nowEpochMillis()
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = kavitaApiFactory.create(selectedBaseUrl)
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
                val catalogFetch = fetchKavitaCatalog(
                    api = api,
                    accessToken = token,
                    libraries = libraries,
                    series = series,
                    onProgress = onProgress,
                )
                KavitaFullCatalogSyncFetch(
                    fetch = RemoteCatalogFetch(
                        libraries = libraries,
                        entries = catalogFetch.entries,
                    ),
                    syncGroups = buildKavitaSeriesSyncGroups(
                        series = series,
                        nowEpochMillis = syncStartedAt,
                    ),
                )
            }
            activeAccessToken = fetchResult.accessToken
            activeRefreshToken = fetchResult.refreshToken
            activeApiKey = fetchResult.refreshedSession?.apiKey ?: activeApiKey
            latestSession = fetchResult.refreshedSession ?: latestSession
            val syncOutcome = finalizeRemoteCatalogSync(
                sourceType = SourceType.KAVITA,
                fetch = fetchResult.value.fetch,
                remoteCatalogWriter = remoteCatalogWriter,
                progressSyncStore = progressSyncStore,
                nowEpochMillis = syncStartedAt,
                onProgress = { message, fraction ->
                    onProgress(KavitaSyncProgress(message = message, fraction = fraction))
                },
                savingMessage = "Saving Kavita catalog...",
                applyingProgressMessage = "Applying Kavita progress...",
                itemOf = KavitaChapterCatalogEntry::item,
                progressOf = KavitaChapterCatalogEntry::progress,
                missingRemoteClearsLocal = KavitaChapterCatalogEntry::isExplicitUnreadReset,
                remoteClearOverridesLocal = { false },
                localRefinesRemote = ::kavitaEpubLocalRefinesRemote,
                syncGroups = fetchResult.value.syncGroups,
            ) { items ->
                val progressSync = pushDirtyProgress(
                    api = api,
                    initialAccessToken = activeAccessToken,
                    initialRefreshToken = activeRefreshToken,
                    initialApiKey = activeApiKey,
                    items = items,
                )
                activeAccessToken = progressSync.accessToken
                activeRefreshToken = progressSync.refreshToken
                activeApiKey = progressSync.refreshedSession?.apiKey ?: activeApiKey
                RemoteCatalogProgressPushResult(
                    session = progressSync.refreshedSession,
                    failures = progressSync.value,
                )
            }
            latestSession = syncOutcome.session ?: latestSession
            runCatching {
                val journalPull = pullJournalCache(
                    api = api,
                    initialAccessToken = activeAccessToken,
                    initialRefreshToken = activeRefreshToken,
                    initialApiKey = activeApiKey,
                    libraries = fetchResult.value.fetch.libraries,
                    entries = fetchResult.value.fetch.entries,
                )
                activeAccessToken = journalPull.accessToken
                activeRefreshToken = journalPull.refreshToken
                activeApiKey = journalPull.refreshedSession?.apiKey ?: activeApiKey
                latestSession = journalPull.refreshedSession ?: latestSession
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
            val journalSync = pushDirtyJournalEntries(
                api = api,
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
                initialApiKey = activeApiKey,
                entries = fetchResult.value.fetch.entries.map(KavitaChapterCatalogEntry::item),
            )
            activeAccessToken = journalSync.accessToken
            activeRefreshToken = journalSync.refreshToken
            activeApiKey = journalSync.refreshedSession?.apiKey ?: activeApiKey
            latestSession = journalSync.refreshedSession ?: latestSession
            KavitaCatalogSyncResult(
                libraryCount = syncOutcome.libraryCount,
                itemCount = syncOutcome.itemCount,
                supportedFormatCounts = syncOutcome.supportedFormatCounts,
                libraries = fetchResult.value.fetch.libraries,
                session = latestSession,
                progressPushFailures = syncOutcome.progressPushFailures + journalSync.value,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    override suspend fun fastSync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): KavitaCatalogSyncResult {
        val syncStartedAt = nowEpochMillis()
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = kavitaApiFactory.create(selectedBaseUrl)
            val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
                api.login(
                    username = request.username,
                    password = password,
                )
            }
            val accessToken = session?.accessToken ?: request.accessToken
                ?: error("A Kavita fast sync requires a password or saved access token.")
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
                onProgress(KavitaSyncProgress(message = "Checking Kavita catalog changes...", fraction = 0.15f))
                val series = api.getSeries(
                    accessToken = token,
                    apiKey = apiKey,
                )
                val recentlyUpdatedSeries = fetchRecentlyUpdatedSeries(
                    api = api,
                    accessToken = token,
                )
                val existingItems = remoteCatalogWriter.getItemsForSourceType(SourceType.KAVITA)
                val existingSyncGroups = remoteCatalogWriter.getCatalogSyncGroups(SourceType.KAVITA)
                val changedSeries = selectChangedKavitaSeries(
                    series = series,
                    existingSyncGroups = existingSyncGroups,
                    recentlyUpdatedSeries = recentlyUpdatedSeries,
                )
                val changedEntries = if (changedSeries.isEmpty()) {
                    onProgress(KavitaSyncProgress(message = "No Kavita catalog changes found.", fraction = 0.85f))
                    emptyList()
                } else {
                    fetchSeriesEntries(
                        api = api,
                        accessToken = token,
                        series = changedSeries,
                        onProgress = onProgress,
                    )
                }
                buildKavitaFastCatalogDeltaPlan(
                    libraries = libraries,
                    series = series,
                    existingItems = existingItems,
                    existingSyncGroups = existingSyncGroups,
                    changedSeries = changedSeries,
                    changedEntries = changedEntries,
                    recentlyUpdatedSeries = recentlyUpdatedSeries,
                    nowEpochMillis = syncStartedAt,
                )
            }
            activeAccessToken = fetchResult.accessToken
            activeRefreshToken = fetchResult.refreshToken
            activeApiKey = fetchResult.refreshedSession?.apiKey ?: activeApiKey
            latestSession = fetchResult.refreshedSession ?: latestSession
            val deltaPlan = fetchResult.value
            val progressPull = pullFastSyncProgressEntries(
                api = api,
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
                initialApiKey = activeApiKey,
                deltaPlan = deltaPlan,
                pullUnchangedProgress = request.pullUnchangedProgressDuringFastSync,
                onProgress = onProgress,
            )
            activeAccessToken = progressPull.accessToken
            activeRefreshToken = progressPull.refreshToken
            activeApiKey = progressPull.refreshedSession?.apiKey ?: activeApiKey
            latestSession = progressPull.refreshedSession ?: latestSession
            onProgress(KavitaSyncProgress(message = "Saving Kavita catalog changes...", fraction = 0.92f))
            remoteCatalogWriter.applySourceCatalogDelta(
                RemoteCatalogDelta(
                    sourceType = SourceType.KAVITA,
                    libraries = deltaPlan.libraries.map { library -> library.toEntity() },
                    upsertItems = deltaPlan.upsertItems.map { item ->
                        item.toEntity(updatedAtEpochMillis = syncStartedAt)
                    },
                    removeItemIds = deltaPlan.removeItemIds,
                    syncGroups = deltaPlan.syncGroups,
                    removeSyncGroupIds = deltaPlan.removeSyncGroupIds,
                ),
            )
            clearRemoteCatalogProgressItems(
                itemIds = deltaPlan.removeItemIds,
                progressSyncStore = progressSyncStore,
            )
            applyFastSyncProgressEntries(
                entries = progressPull.value,
                nowEpochMillis = syncStartedAt,
                onProgress = onProgress,
            )
            val progressSync = pushDirtyProgress(
                api = api,
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
                initialApiKey = activeApiKey,
                items = deltaPlan.currentItems,
            )
            activeAccessToken = progressSync.accessToken
            activeRefreshToken = progressSync.refreshToken
            activeApiKey = progressSync.refreshedSession?.apiKey ?: activeApiKey
            latestSession = progressSync.refreshedSession ?: latestSession
            if (request.pullJournalDuringFastSync) {
                runCatching {
                    val journalPull = pullJournalCache(
                        api = api,
                        initialAccessToken = activeAccessToken,
                        initialRefreshToken = activeRefreshToken,
                        initialApiKey = activeApiKey,
                        libraries = deltaPlan.libraries,
                        entries = progressPull.value,
                    )
                    activeAccessToken = journalPull.accessToken
                    activeRefreshToken = journalPull.refreshToken
                    activeApiKey = journalPull.refreshedSession?.apiKey ?: activeApiKey
                    latestSession = journalPull.refreshedSession ?: latestSession
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
            }
            val journalSync = pushDirtyJournalEntries(
                api = api,
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
                initialApiKey = activeApiKey,
                entries = deltaPlan.currentItems,
            )
            activeAccessToken = journalSync.accessToken
            activeRefreshToken = journalSync.refreshToken
            activeApiKey = journalSync.refreshedSession?.apiKey ?: activeApiKey
            latestSession = journalSync.refreshedSession ?: latestSession
            KavitaCatalogSyncResult(
                libraryCount = deltaPlan.libraries.size,
                itemCount = deltaPlan.currentItems.size,
                supportedFormatCounts = deltaPlan.currentItems.groupingBy(ReadableItem::format).eachCount(),
                libraries = deltaPlan.libraries,
                session = latestSession,
                progressPushFailures = progressSync.value + journalSync.value,
                catalogChangedItemIds = deltaPlan.upsertItems.mapTo(mutableSetOf(), ReadableItem::id),
                catalogRemovedItemIds = deltaPlan.removeItemIds,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    private suspend fun pushDirtyProgress(
        api: KavitaApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        initialApiKey: String?,
        items: List<ReadableItem>,
    ): KavitaTokenRefreshResult<List<Throwable>> {
        val store = readerSessionStore ?: return KavitaTokenRefreshResult(
            value = emptyList(),
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
            itemsAreWholeCatalog = true,
        )
    }

    private suspend fun pullFastSyncProgressEntries(
        api: KavitaApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        initialApiKey: String?,
        deltaPlan: KavitaFastCatalogDeltaPlan,
        pullUnchangedProgress: Boolean,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): KavitaTokenRefreshResult<List<KavitaChapterCatalogEntry>> {
        val itemsNeedingProgressPull = deltaPlan.itemsNeedingProgressPull()
        if (!pullUnchangedProgress || !shouldPullFastSyncProgress() || itemsNeedingProgressPull.isEmpty()) {
            val entries = deltaPlan.progressEntriesForFastSync(remoteProgressEntries = emptyList())
            return KavitaTokenRefreshResult(
                value = entries,
                accessToken = initialAccessToken,
                refreshToken = initialRefreshToken,
                refreshedSession = null,
            )
        }
        val pullResult = api.runWithTokenRefresh(
            initialAccessToken = initialAccessToken,
            initialRefreshToken = initialRefreshToken,
            initialApiKey = initialApiKey,
        ) { accessToken, _ ->
            fetchFastSyncProgressEntries(
                api = api,
                accessToken = accessToken,
                items = itemsNeedingProgressPull,
                onProgress = onProgress,
            )
        }
        val entries = deltaPlan.progressEntriesForFastSync(remoteProgressEntries = pullResult.value)
        return pullResult.copy(value = entries)
    }

    private fun shouldPullFastSyncProgress(): Boolean =
        progressSyncStore != null || journalRepository != null

    private fun applyFastSyncProgressEntries(
        entries: List<KavitaChapterCatalogEntry>,
        nowEpochMillis: Long,
        onProgress: (KavitaSyncProgress) -> Unit,
    ) {
        onProgress(KavitaSyncProgress(message = "Applying Kavita progress...", fraction = 0.97f))
        applyRemoteCatalogProgress(
            entries = entries,
            progressSyncStore = progressSyncStore,
            nowEpochMillis = nowEpochMillis,
            itemOf = KavitaChapterCatalogEntry::item,
            progressOf = KavitaChapterCatalogEntry::progress,
            missingRemoteClearsLocal = KavitaChapterCatalogEntry::isExplicitUnreadReset,
            remoteClearOverridesLocal = { false },
            localRefinesRemote = ::kavitaEpubLocalRefinesRemote,
        )
    }

    private suspend fun fetchFastSyncProgressEntries(
        api: KavitaApi,
        accessToken: String,
        items: List<ReadableItem>,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): List<KavitaChapterCatalogEntry> = coroutineScope {
        val detailRequestLimiter = Semaphore(KAVITA_PROGRESS_REQUEST_CONCURRENCY)
        val completedItemCount = AtomicInteger(0)
        val reportedItemCount = AtomicInteger(0)
        onProgress(KavitaSyncProgress(message = "Loading Kavita progress 0/${items.size}...", fraction = 0.94f))
        items.map { item ->
            async {
                detailRequestLimiter.withPermit {
                    item.toFastSyncProgressEntry(
                        progress = item.remoteId?.let { chapterId ->
                            api.getProgress(
                                accessToken = accessToken,
                                chapterId = chapterId,
                            ).toReadingProgress(
                                itemId = item.id,
                                pageCount = item.pageCount,
                            )
                        },
                        progressWasFetched = item.remoteId != null,
                    ).also {
                        val completed = completedItemCount.incrementAndGet()
                        if (shouldReportSeriesProgress(completed, items.size, reportedItemCount, KAVITA_PROGRESS_REPORT_STEPS)) {
                            val fraction = 0.94f + (0.025f * completed.toFloat() / items.size.toFloat())
                            onProgress(
                                KavitaSyncProgress(
                                    message = "Loading Kavita progress $completed/${items.size}...",
                                    fraction = fraction.coerceIn(0.94f, 0.965f),
                                ),
                            )
                        }
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun pushDirtyJournalEntries(
        api: KavitaApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        initialApiKey: String?,
        entries: List<ReadableItem>,
    ): KavitaTokenRefreshResult<List<Throwable>> = api.runWithTokenRefresh(
        initialAccessToken = initialAccessToken,
        initialRefreshToken = initialRefreshToken,
        initialApiKey = initialApiKey,
    ) { accessToken, _ ->
        pushDirtyKavitaJournalEntries(
            api = api,
            accessToken = accessToken,
            entries = entries,
            journalRepository = journalRepository,
            itemsAreWholeCatalog = true,
        )
    }

    private suspend fun pullJournalCache(
        api: KavitaApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        initialApiKey: String?,
        libraries: List<com.example.unifiedreader.core.model.Library>,
        entries: List<KavitaChapterCatalogEntry>,
    ): KavitaTokenRefreshResult<Unit> = api.runWithTokenRefresh(
        initialAccessToken = initialAccessToken,
        initialRefreshToken = initialRefreshToken,
        initialApiKey = initialApiKey,
    ) { accessToken, _ ->
        pullKavitaJournalCache(
            api = api,
            accessToken = accessToken,
            libraries = libraries,
            entries = entries,
            journalRepository = journalRepository,
        )
    }

    private suspend fun fetchRecentlyUpdatedSeries(
        api: KavitaApi,
        accessToken: String,
    ): List<KavitaSeriesUpdateEntry> = runCatching {
        api.getRecentlyUpdatedSeries(accessToken)
    }.getOrElse { error ->
        if (error is CancellationException || error is KavitaUnauthorizedException) {
            throw error
        }
        throw KavitaRecentUpdatesUnavailableException(error)
    }

    internal suspend fun fetchSeriesEntries(
        api: KavitaApi,
        accessToken: String,
        series: List<KavitaSeriesEntry>,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): List<KavitaChapterCatalogEntry> = fetchSeriesEntriesWithProgress(
        sourceLabel = "Kavita",
        series = series,
        detailRequestConcurrency = KAVITA_SERIES_DETAIL_REQUEST_CONCURRENCY,
        onProgress = { message, fraction ->
            onProgress(KavitaSyncProgress(message = message, fraction = fraction))
        },
    ) { seriesEntry ->
        api.getSeriesDetail(
            accessToken = accessToken,
            series = seriesEntry,
        )
    }
}

private data class KavitaFullCatalogSyncFetch(
    val fetch: RemoteCatalogFetch<KavitaChapterCatalogEntry>,
    val syncGroups: List<RemoteCatalogSyncGroupEntity>,
)

private class KavitaRecentUpdatesUnavailableException(
    cause: Throwable,
) : RuntimeException(
    "Kavita fast sync needs recent update markers. Run Sync Kavita for a full resync, then try Fast Sync again.",
    cause,
)

data class KavitaCatalogSyncRequest(
    val displayName: String,
    val baseUrl: String,
    val backupBaseUrl: String? = null,
    val activeBaseUrl: String? = null,
    val username: String,
    val password: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val apiKey: String? = null,
    val pullUnchangedProgressDuringFastSync: Boolean = false,
    val pullJournalDuringFastSync: Boolean = false,
)

data class KavitaCatalogConnectionResult(
    val libraryCount: Int,
    val libraries: List<Library> = emptyList(),
    val session: KavitaSession? = null,
    val activeBaseUrl: String? = null,
)

data class KavitaSyncProgress(
    val message: String,
    val fraction: Float,
)

data class KavitaCatalogSyncResult(
    val libraryCount: Int,
    val itemCount: Int,
    val supportedFormatCounts: Map<FormatType, Int>,
    val libraries: List<Library> = emptyList(),
    val session: KavitaSession? = null,
    val activeBaseUrl: String? = null,
    val progressPushFailures: List<Throwable> = emptyList(),
    val catalogChangedItemIds: Set<String> = emptySet(),
    val catalogRemovedItemIds: Set<String> = emptySet(),
)

internal val KavitaCatalogSyncResult.hasCatalogChanges: Boolean
    get() = catalogChangedItemIds.isNotEmpty() || catalogRemovedItemIds.isNotEmpty()

private const val KAVITA_SERIES_DETAIL_REQUEST_CONCURRENCY = 1
private const val KAVITA_PROGRESS_REQUEST_CONCURRENCY = 6
private const val KAVITA_PROGRESS_REPORT_STEPS = 24
