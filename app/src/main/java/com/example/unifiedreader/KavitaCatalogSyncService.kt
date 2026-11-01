package com.example.unifiedreader

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
    ): KavitaCatalogSyncResult {
        val syncStartedAt = nowEpochMillis()
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = kavitaApiFactory.create(selectedBaseUrl)
            val tokens = api.runTokensFor(request, onSessionRenewed)
            onProgress(KavitaSyncProgress(message = "Loading Kavita libraries...", fraction = 0.05f))
            val libraries = tokens.run { token, _ -> api.getLibraries(token) }
            onProgress(KavitaSyncProgress(message = "Loading Kavita series...", fraction = 0.15f))
            val series = tokens.run { token, apiKey ->
                api.getSeries(
                    accessToken = token,
                    apiKey = apiKey,
                )
            }
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
                onProgress = { message, fraction ->
                    onProgress(KavitaSyncProgress(message = message, fraction = fraction))
                },
                savingMessage = "Saving Kavita catalog...",
                applyingProgressMessage = "Applying Kavita progress...",
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
                    ),
                )
            }
            runCatching {
                pullJournalCache(
                    api = api,
                    tokens = tokens,
                    libraries = fetch.libraries,
                    entries = fetch.entries,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
            val journalFailures = pushDirtyJournalEntries(
                api = api,
                tokens = tokens,
                entries = fetch.entries.map(KavitaChapterCatalogEntry::item),
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

    override suspend fun fastSync(
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
            val tokens = api.runTokensFor(request, onSessionRenewed)
            onProgress(KavitaSyncProgress(message = "Loading Kavita libraries...", fraction = 0.05f))
            val libraries = tokens.run { token, _ -> api.getLibraries(token) }
            onProgress(KavitaSyncProgress(message = "Checking Kavita catalog changes...", fraction = 0.15f))
            val series = tokens.run { token, apiKey ->
                api.getSeries(
                    accessToken = token,
                    apiKey = apiKey,
                )
            }
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
            val changedEntries = if (changedSeries.isEmpty()) {
                onProgress(KavitaSyncProgress(message = "No Kavita catalog changes found.", fraction = 0.85f))
                emptyList()
            } else {
                fetchSeriesEntries(
                    api = api,
                    tokens = tokens,
                    series = changedSeries,
                    onProgress = onProgress,
                )
            }
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
                entries = progressEntries,
                nowEpochMillis = syncStartedAt,
                onProgress = onProgress,
            )
            val progressFailures = pushDirtyProgress(
                api = api,
                tokens = tokens,
                items = deltaPlan.currentItems,
            )
            if (request.pullJournalDuringFastSync) {
                runCatching {
                    pullJournalCache(
                        api = api,
                        tokens = tokens,
                        libraries = deltaPlan.libraries,
                        entries = progressEntries,
                    )
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
            }
            val journalFailures = pushDirtyJournalEntries(
                api = api,
                tokens = tokens,
                entries = deltaPlan.currentItems,
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
    ): List<Throwable> {
        val store = readerSessionStore ?: return emptyList()
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
        onProgress(KavitaSyncProgress(message = "Applying Kavita progress...", fraction = 0.97f))
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
        onProgress(KavitaSyncProgress(message = "Loading Kavita progress 0/${items.size}...", fraction = 0.94f))
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
        tokens: KavitaRunTokens,
        entries: List<ReadableItem>,
    ): List<Throwable> = tokens.run { accessToken, _ ->
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
    ) = tokens.run { accessToken, _ ->
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
    ): List<KavitaSeriesUpdateEntry> = runCatching {
        tokens.run { accessToken, _ -> api.getRecentlyUpdatedSeries(accessToken) }
    }.getOrElse { error ->
        if (error is CancellationException || error is KavitaUnauthorizedException) {
            throw error
        }
        throw KavitaRecentUpdatesUnavailableException(error)
    }

    internal suspend fun fetchSeriesEntries(
        api: KavitaApi,
        tokens: KavitaRunTokens,
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
        tokens.run { accessToken, _ ->
            api.getSeriesDetail(
                accessToken = accessToken,
                series = seriesEntry,
            )
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
