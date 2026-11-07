package com.example.unifiedreader

import com.example.unifiedreader.core.model.runSuspendCatching
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

fun interface KavitaApiFactory {
    fun create(
        baseUrl: String,
    ): KavitaApi
}

/** Each run takes [onSessionRenewed], which hears of every pair of tokens the run renews, at once. */
interface KavitaCatalogSyncer {
    suspend fun connect(
        request: KavitaCatalogSyncRequest,
        onSessionRenewed: suspend (KavitaSession) -> Unit = {},
    ): KavitaCatalogConnectionResult

    suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit = {},
        onSessionRenewed: suspend (KavitaSession) -> Unit = {},
    ): KavitaCatalogSyncResult

    suspend fun fastSync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit = {},
        onSessionRenewed: suspend (KavitaSession) -> Unit = {},
    ): KavitaCatalogSyncResult
}

class KavitaCatalogSyncService(
    private val kavitaApiFactory: KavitaApiFactory,
    private val remoteCatalogWriter: RemoteCatalogWriter,
    private val readerSessionStore: ReaderSessionStore? = null,
    private val journalRepository: JournalRepository? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    // The sync's own work: decoding the book list, comparing it with the stored catalog, saving. Off the
    // main thread, where a large library froze the app for seconds after the book list arrived.
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : KavitaCatalogSyncer {
    private val progressSyncStore: ProgressSyncStore? = readerSessionStore?.let(::ReaderSessionProgressStoreAdapter)

    override suspend fun connect(
        request: KavitaCatalogSyncRequest,
        onSessionRenewed: suspend (KavitaSession) -> Unit,
    ): KavitaCatalogConnectionResult {
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = kavitaApiFactory.create(selectedBaseUrl)
            val tokens = api.runTokensFor(request, onSessionRenewed)
            val libraries = tokens.run { token, _ -> api.getLibraries(token) }
            KavitaCatalogConnectionResult(
                libraryCount = libraries.size,
                libraries = libraries,
                session = tokens.sessionToSave,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    override suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
        onSessionRenewed: suspend (KavitaSession) -> Unit,
    ): KavitaCatalogSyncResult = offCallerThread(onProgress) { progress ->
        syncCatalog(request, progress, onSessionRenewed)
    }

    override suspend fun fastSync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
        onSessionRenewed: suspend (KavitaSession) -> Unit,
    ): KavitaCatalogSyncResult = offCallerThread(onProgress) { progress ->
        fastSyncCatalog(request, progress, onSessionRenewed)
    }

    /**
     * Runs [work] on [workDispatcher] and relays its progress to [onProgress] in the caller's context, in
     * order and all of it before returning, so the caller's state is only ever touched on its own thread.
     */
    private suspend fun <T> offCallerThread(
        onProgress: (KavitaSyncProgress) -> Unit,
        work: suspend (onProgress: (KavitaSyncProgress) -> Unit) -> T,
    ): T = coroutineScope {
        val steps = Channel<KavitaSyncProgress>(Channel.UNLIMITED)
        launch { for (step in steps) onProgress(step) }
        try {
            withContext(workDispatcher) { work { step -> steps.trySend(step) } }
        } finally {
            steps.close()
        }
    }

    private suspend fun syncCatalog(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
        onSessionRenewed: suspend (KavitaSession) -> Unit,
    ): KavitaCatalogSyncResult {
        val syncStartedAt = nowEpochMillis()
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = kavitaApiFactory.create(selectedBaseUrl)
            onProgress(KavitaSyncSteps.connecting)
            val tokens = api.runTokensFor(request, onSessionRenewed)
            val libraries = tokens.run { token, _ -> api.getLibraries(token) }
            onProgress(KavitaSyncSteps.bookList)
            val series = withKavitaSeriesMetadata(
                api = api,
                tokens = tokens,
                series = tokens.run { token, apiKey ->
                    api.getSeries(
                        accessToken = token,
                        apiKey = apiKey,
                    )
                },
            )
            val catalogFetch = fetchKavitaCatalog(
                api = api,
                tokens = tokens,
                libraries = libraries,
                series = series,
                onProgress = onProgress,
            )
            val fetch = RemoteCatalogFetch(
                libraries = libraries,
                entries = catalogFetch.entries,
            )
            val syncOutcome = finalizeRemoteCatalogSync(
                sourceType = SourceType.KAVITA,
                fetch = fetch,
                remoteCatalogWriter = remoteCatalogWriter,
                progressSyncStore = progressSyncStore,
                nowEpochMillis = syncStartedAt,
                onProgress = { _, fraction -> onProgress(KavitaSyncSteps.saving(fraction)) },
                savingMessage = KavitaSyncSteps.saving(0f).message,
                applyingProgressMessage = KavitaSyncSteps.saving(1f).message,
                itemOf = KavitaChapterCatalogEntry::item,
                progressOf = KavitaChapterCatalogEntry::progress,
                missingRemoteClearsLocal = KavitaChapterCatalogEntry::isExplicitUnreadReset,
                remoteClearOverridesLocal = { false },
                localRefinesRemote = ::kavitaLocalRefinesRemote,
                syncGroups = buildKavitaSeriesSyncGroups(
                    series = series,
                    nowEpochMillis = syncStartedAt,
                ),
            ) { items ->
                RemoteCatalogProgressPushResult<KavitaSession>(
                    session = null,
                    failures = pushDirtyProgress(
                        api = api,
                        tokens = tokens,
                        items = items,
                        onProgress = onProgress,
                    ),
                )
            }
            runSuspendCatching {
                pullJournalCache(
                    api = api,
                    tokens = tokens,
                    libraries = fetch.libraries,
                    entries = fetch.entries,
                    onProgress = onProgress,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
            val journalFailures = pushDirtyJournalEntries(
                api = api,
                tokens = tokens,
                entries = fetch.entries.map(KavitaChapterCatalogEntry::item),
                onProgress = onProgress,
            )
            KavitaCatalogSyncResult(
                libraryCount = syncOutcome.libraryCount,
                itemCount = syncOutcome.itemCount,
                supportedFormatCounts = syncOutcome.supportedFormatCounts,
                libraries = fetch.libraries,
                session = tokens.sessionToSave,
                progressPushFailures = syncOutcome.progressPushFailures + journalFailures,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    private suspend fun fastSyncCatalog(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
        onSessionRenewed: suspend (KavitaSession) -> Unit,
    ): KavitaCatalogSyncResult {
        val syncStartedAt = nowEpochMillis()
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = kavitaApiFactory.create(selectedBaseUrl)
            onProgress(KavitaSyncSteps.connecting)
            val tokens = api.runTokensFor(request, onSessionRenewed)
            val libraries = tokens.run { token, _ -> api.getLibraries(token) }
            onProgress(KavitaSyncSteps.checkingChanges)
            val series = withKavitaSeriesMetadata(
                api = api,
                tokens = tokens,
                series = tokens.run { token, apiKey ->
                    api.getSeries(
                        accessToken = token,
                        apiKey = apiKey,
                    )
                },
            )
            val recentlyUpdatedSeries = fetchRecentlyUpdatedSeries(
                api = api,
                tokens = tokens,
            )
            val existingItems = remoteCatalogWriter.getItemsForSourceType(SourceType.KAVITA)
            val existingSyncGroups = remoteCatalogWriter.getCatalogSyncGroups(SourceType.KAVITA)
            val changedSeries = selectChangedKavitaSeries(
                series = series,
                existingSyncGroups = existingSyncGroups,
                recentlyUpdatedSeries = recentlyUpdatedSeries,
            )
            val changedEntries = fetchSeriesEntries(
                api = api,
                tokens = tokens,
                series = changedSeries,
                onProgress = onProgress,
            )
            val deltaPlan = buildKavitaFastCatalogDeltaPlan(
                libraries = libraries,
                series = series,
                existingItems = existingItems,
                existingSyncGroups = existingSyncGroups,
                changedSeries = changedSeries,
                changedEntries = changedEntries,
                recentlyUpdatedSeries = recentlyUpdatedSeries,
                nowEpochMillis = syncStartedAt,
            )
            val progressEntries = pullFastSyncProgressEntries(
                api = api,
                tokens = tokens,
                deltaPlan = deltaPlan,
                pullUnchangedProgress = request.pullUnchangedProgressDuringFastSync,
                onProgress = onProgress,
            )
            onProgress(KavitaSyncSteps.saving(0f))
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
                entries = progressEntries,
                nowEpochMillis = syncStartedAt,
                onProgress = onProgress,
            )
            val progressFailures = pushDirtyProgress(
                api = api,
                tokens = tokens,
                items = deltaPlan.currentItems,
                onProgress = onProgress,
            )
            if (request.pullJournalDuringFastSync) {
                runSuspendCatching {
                    pullJournalCache(
                        api = api,
                        tokens = tokens,
                        libraries = deltaPlan.libraries,
                        entries = progressEntries,
                        onProgress = onProgress,
                    )
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
            }
            val journalFailures = pushDirtyJournalEntries(
                api = api,
                tokens = tokens,
                entries = deltaPlan.currentItems,
                onProgress = onProgress,
            )
            KavitaCatalogSyncResult(
                libraryCount = deltaPlan.libraries.size,
                itemCount = deltaPlan.currentItems.size,
                supportedFormatCounts = deltaPlan.currentItems.groupingBy(ReadableItem::format).eachCount(),
                libraries = deltaPlan.libraries,
                session = tokens.sessionToSave,
                progressPushFailures = progressFailures + journalFailures,
                catalogChangedItemIds = deltaPlan.upsertItems.mapTo(mutableSetOf(), ReadableItem::id),
                catalogRemovedItemIds = deltaPlan.removeItemIds,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    private suspend fun pushDirtyProgress(
        api: KavitaApi,
        tokens: KavitaRunTokens,
        items: List<ReadableItem>,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): List<Throwable> {
        val store = readerSessionStore ?: return emptyList()
        onProgress(KavitaSyncSteps.uploadingProgress)
        return pushDirtyKavitaProgress(
            api = api,
            tokens = tokens,
            items = items,
            readerSessionStore = store,
            itemsAreWholeCatalog = true,
        )
    }

    private suspend fun pullFastSyncProgressEntries(
        api: KavitaApi,
        tokens: KavitaRunTokens,
        deltaPlan: KavitaFastCatalogDeltaPlan,
        pullUnchangedProgress: Boolean,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): List<KavitaChapterCatalogEntry> {
        val itemsNeedingProgressPull = deltaPlan.itemsNeedingProgressPull()
        if (!pullUnchangedProgress || !shouldPullFastSyncProgress() || itemsNeedingProgressPull.isEmpty()) {
            return deltaPlan.progressEntriesForFastSync(remoteProgressEntries = emptyList())
        }
        return deltaPlan.progressEntriesForFastSync(
            remoteProgressEntries = fetchFastSyncProgressEntries(
                api = api,
                tokens = tokens,
                items = itemsNeedingProgressPull,
                onProgress = onProgress,
            ),
        )
    }

    private fun shouldPullFastSyncProgress(): Boolean =
        progressSyncStore != null || journalRepository != null

    private fun applyFastSyncProgressEntries(
        entries: List<KavitaChapterCatalogEntry>,
        nowEpochMillis: Long,
        onProgress: (KavitaSyncProgress) -> Unit,
    ) {
        onProgress(KavitaSyncSteps.saving(1f))
        applyRemoteCatalogProgress(
            entries = entries,
            progressSyncStore = progressSyncStore,
            nowEpochMillis = nowEpochMillis,
            itemOf = KavitaChapterCatalogEntry::item,
            progressOf = KavitaChapterCatalogEntry::progress,
            missingRemoteClearsLocal = KavitaChapterCatalogEntry::isExplicitUnreadReset,
            remoteClearOverridesLocal = { false },
            localRefinesRemote = ::kavitaLocalRefinesRemote,
        )
    }

    private suspend fun fetchFastSyncProgressEntries(
        api: KavitaApi,
        tokens: KavitaRunTokens,
        items: List<ReadableItem>,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): List<KavitaChapterCatalogEntry> = coroutineScope {
        val detailRequestLimiter = Semaphore(KAVITA_PROGRESS_REQUEST_CONCURRENCY)
        val completedItemCount = AtomicInteger(0)
        val reportedItemCount = AtomicInteger(0)
        onProgress(KavitaSyncSteps.readingProgress(loaded = 0, total = items.size))
        items.map { item ->
            async {
                detailRequestLimiter.withPermit {
                    item.toFastSyncProgressEntry(
                        progress = item.remoteId?.let { chapterId ->
                            tokens.run { accessToken, _ ->
                                api.getProgress(
                                    accessToken = accessToken,
                                    chapterId = chapterId,
                                )
                            }.toReadingProgress(
                                itemId = item.id,
                                pageCount = item.pageCount,
                            )
                        },
                        progressWasFetched = item.remoteId != null,
                    ).also {
                        // Counted and reported together: the requests finish on several threads at once.
                        synchronized(completedItemCount) {
                            val completed = completedItemCount.incrementAndGet()
                            if (shouldReportSeriesProgress(completed, items.size, reportedItemCount, KAVITA_PROGRESS_REPORT_STEPS)) {
                                onProgress(KavitaSyncSteps.readingProgress(loaded = completed, total = items.size))
                            }
                        }
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun pushDirtyJournalEntries(
        api: KavitaApi,
        tokens: KavitaRunTokens,
        entries: List<ReadableItem>,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): List<Throwable> = tokens.run { accessToken, _ ->
        onProgress(KavitaSyncSteps.uploadingJournal)
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
        tokens: KavitaRunTokens,
        libraries: List<com.example.unifiedreader.core.model.Library>,
        entries: List<KavitaChapterCatalogEntry>,
        onProgress: (KavitaSyncProgress) -> Unit,
    ) = tokens.run { accessToken, _ ->
        onProgress(KavitaSyncSteps.readingHistory)
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
        tokens: KavitaRunTokens,
    ): List<KavitaSeriesUpdateEntry> = runSuspendCatching {
        tokens.run { accessToken, _ -> api.getRecentlyUpdatedSeries(accessToken) }
    }.getOrElse { error ->
        if (error is CancellationException || error is KavitaUnauthorizedException) {
            throw error
        }
        throw KavitaRecentUpdatesUnavailableException(error)
    }

    // Counts the series as their books arrive (KavitaSyncSteps.bookDetails); none to read is no step.
    internal suspend fun fetchSeriesEntries(
        api: KavitaApi,
        tokens: KavitaRunTokens,
        series: List<KavitaSeriesEntry>,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): List<KavitaChapterCatalogEntry> {
        if (series.isEmpty()) return emptyList()
        val loadedSeriesCount = AtomicInteger(0)
        val reportedSeriesCount = AtomicInteger(0)
        onProgress(KavitaSyncSteps.bookDetails(loaded = 0, total = series.size))
        return fetchSeriesEntriesWithProgress(
            sourceLabel = "Kavita",
            series = series,
            detailRequestConcurrency = KAVITA_SERIES_DETAIL_REQUEST_CONCURRENCY,
            onProgress = { _, _ -> },
        ) { seriesEntry ->
            tokens.run { accessToken, _ ->
                api.getSeriesDetail(
                    accessToken = accessToken,
                    series = seriesEntry,
                )
            }.also {
                synchronized(loadedSeriesCount) {
                    val loaded = loadedSeriesCount.incrementAndGet()
                    if (shouldReportSeriesProgress(loaded, series.size, reportedSeriesCount, KAVITA_SERIES_REPORT_STEPS)) {
                        onProgress(KavitaSyncSteps.bookDetails(loaded = loaded, total = series.size))
                    }
                }
            }
        }
    }
}

/**
 * The tokens a run starts with: a new sign-in when the request carries the password, else the saved
 * pair, renewed first when it is past half its life so it doesn't run out while the run is away.
 */
private suspend fun KavitaApi.runTokensFor(
    request: KavitaCatalogSyncRequest,
    onSessionRenewed: suspend (KavitaSession) -> Unit,
): KavitaRunTokens {
    val password = request.password?.takeIf(String::isNotBlank)
    if (password != null) {
        return KavitaRunTokens(
            api = this,
            initial = login(
                username = request.username,
                password = password,
            ),
            isNewSignIn = true,
            onRenewed = onSessionRenewed,
        )
    }
    val accessToken = request.accessToken
        ?: error("Kavita needs a password or a saved sign-in.")
    return KavitaRunTokens(
        api = this,
        initial = KavitaSession(
            accessToken = accessToken,
            refreshToken = request.refreshToken,
            apiKey = request.apiKey,
        ),
        onRenewed = onSessionRenewed,
    ).also { tokens -> tokens.renewIfAging() }
}

private class KavitaRecentUpdatesUnavailableException(
    cause: Throwable,
) : RuntimeException(
    "Kavita fast sync needs recent update markers. Run Sync Kavita for a full resync, then try Fast Sync again.",
    cause,
)

data class KavitaCatalogSyncRequest(
    val displayName: String,
    override val baseUrl: String,
    val backupBaseUrl: String? = null,
    val activeBaseUrl: String? = null,
    override val username: String,
    val password: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val apiKey: String? = null,
    val pullUnchangedProgressDuringFastSync: Boolean = false,
    val pullJournalDuringFastSync: Boolean = false,
) : SourceSyncRequest

data class KavitaCatalogConnectionResult(
    val libraryCount: Int,
    val libraries: List<Library> = emptyList(),
    val session: KavitaSession? = null,
    val activeBaseUrl: String? = null,
)

data class KavitaSyncProgress(
    val message: String,
    // Null while the step's length can't be known: the bar moves instead of filling (KavitaSyncSteps).
    val fraction: Float?,
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

// A step for each 1% of the series, so the count above the bar keeps moving on a large library.
private const val KAVITA_SERIES_REPORT_STEPS = 100
