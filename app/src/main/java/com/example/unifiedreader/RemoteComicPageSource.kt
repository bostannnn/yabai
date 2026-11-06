package com.example.unifiedreader

import android.os.SystemClock
import android.util.Log
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageLoaderCacheSizing
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewOptions
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import com.example.unifiedreader.feature.reader.comics.candidateIndexes
import com.example.unifiedreader.feature.reader.comics.decodeComicPageImage
import com.example.unifiedreader.feature.reader.comics.decodeComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.decodeComicPagePreview
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

abstract class RemoteComicPageSource<Session : StoredSourceSession>(
    private val sourceType: SourceType,
    private val supportedFormats: Set<FormatType>,
    private val missingSessionMessage: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope,
    private val previewStore: PersistentComicPagePreviewStore? = null,
    private val readerWorkCoordinator: ReaderWorkCoordinator = ReaderWorkCoordinator(),
) : ComicPageSource {
    private val memory = ComicPageMemoryCaches()
    private val cacheGeneration = AtomicInteger(0)
    private val inFlightByteRequests = mutableMapOf<RemoteComicPageByteRequestKey, RemoteInFlightByteRequest>()
    private val inFlightPreviewByteRequests = mutableMapOf<RemoteComicPageByteRequestKey, RemoteInFlightByteRequest>()
    private val byteRequestLock = Any()
    private val prefetchJobs = ComicPagePrefetchJobs(scope)
    private val previewPrefetchJobs = mutableMapOf<String, PreviewPrefetchJob>()
    private val prefetchLock = Any()
    private val previewVersionByCacheKeyFlow = MutableStateFlow<Map<String, Long>>(emptyMap())

    protected abstract fun activeSessionFor(
        item: ReadableItem,
    ): Session?

    protected abstract suspend fun downloadPageBytes(
        session: Session,
        item: ReadableItem,
        page: ComicPageSpec,
    ): ByteArray?

    protected open val pagePreviewStrategy: RemoteComicPagePreviewStrategy =
        RemoteComicPagePreviewStrategy.CachedPageImage

    protected open suspend fun downloadPagePreviewBytes(
        session: Session,
        item: ReadableItem,
        page: ComicPageSpec,
    ): ByteArray? = null

    protected open fun onReaderContentFailure(
        item: ReadableItem,
        throwable: Throwable,
    ) = Unit

    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> {
        if (item.sourceType != sourceType || item.format !in supportedFormats) return emptyList()
        val pageCount = item.pageCount?.coerceAtLeast(0) ?: return emptyList()
        return List(pageCount) { index ->
            ComicPageSpec(
                id = index.toString(),
                pageIndex = index,
                contentDescription = "${item.libraryDisplayLabel()} page ${index + 1}",
            )
        }
    }

    override fun observePagePreviewVersion(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
    ): Flow<Long> {
        val session = activeSessionFor(item) ?: return kotlinx.coroutines.flow.flowOf(0L)
        val itemKey = item.cacheIdentityKey()
        val previewCacheKeys = pages
            .flatMap { page ->
                val pageCacheKey = pageCacheKey(
                    baseUrl = session.baseUrl,
                    itemKey = itemKey,
                    pageId = page.id,
                )
                pagePreviewStrategy.observableByteSources.map { byteSource ->
                    pagePreviewCacheKey(
                        pageCacheKey = pageCacheKey,
                        options = options,
                        byteSource = byteSource,
                    )
                }
            }
            .toSet()
        return previewVersionByCacheKeyFlow
            .map { versions -> previewCacheKeys.sumOf { key -> versions[key] ?: 0L } }
            .distinctUntilChanged()
    }

    override open suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? {
        val session = activeSessionFor(item) ?: return null
        val cacheKey = pageCacheKey(
            baseUrl = session.baseUrl,
            itemKey = item.cacheIdentityKey(),
            pageId = page.id,
        )
        val requestGeneration = cacheGeneration.get()
        memory.pageMetadataCache[cacheKey]?.let { return it }
        readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
        return withContext(ioDispatcher) {
            memory.metadataLoadSemaphore.withPermit {
                readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
                memory.pageMetadataCache[cacheKey]?.let { return@withPermit it }
                val bytes = try {
                    fetchPageBytes(
                        item = item,
                        page = page,
                        cacheKey = cacheKey,
                        session = session,
                        priority = ComicPageLoadPriority.Prefetch,
                    )
                } catch (cancellation: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    null
                } ?: return@withPermit null
                decodeComicPageMetadata(bytes)?.also { metadata ->
                    if (cacheGeneration.get() == requestGeneration) {
                        memory.pageMetadataCache.put(cacheKey, metadata)
                    }
                }
            }
        }
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        if (item.sourceType != sourceType) {
            return ComicPageLoadResult.Failure("Unsupported source.")
        }
        val session = activeSessionFor(item)
            ?: return ComicPageLoadResult.Failure(missingSessionMessage)
        val cacheKey = pageCacheKey(
            baseUrl = session.baseUrl,
            itemKey = item.cacheIdentityKey(),
            pageId = page.id,
        )
        val requestGeneration = cacheGeneration.get()
        memory.pageBitmapCache[cacheKey]?.let { return it }
        if (priority == ComicPageLoadPriority.Visible) {
            cancelBestEffortWorkForVisibleLoad()
        }
        val startedAt = if (priority == ComicPageLoadPriority.Visible) {
            SystemClock.elapsedRealtime().also {
                Log.d(LOG_TAG, "Visible page load start item=${item.id} page=${page.pageIndex}.")
            }
        } else {
            0L
        }
        var tracedResult: ComicPageLoadResult? = null
        try {
            val result = when (priority) {
                ComicPageLoadPriority.Visible -> readerWorkCoordinator.runVisiblePageLoad(item.id) {
                    try {
                        loadPageImageWithPriority(
                            item = item,
                            page = page,
                            cacheKey = cacheKey,
                            session = session,
                            requestGeneration = requestGeneration,
                            priority = priority,
                        )
                    } catch (cancellation: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        ComicPageLoadResult.Failure("Page load was interrupted.")
                    }
                }
                ComicPageLoadPriority.Prefetch -> {
                    readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
                    try {
                        loadPageImageWithPriority(
                            item = item,
                            page = page,
                            cacheKey = cacheKey,
                            session = session,
                            requestGeneration = requestGeneration,
                            priority = priority,
                        )
                    } catch (cancellation: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        ComicPageLoadResult.Failure("Page prefetch was interrupted.")
                    }
                }
            }
            tracedResult = result
            if (result is ComicPageLoadResult.Success && cacheGeneration.get() == requestGeneration) {
                memory.pageBitmapCache.put(cacheKey, result)
            }
            return result
        } finally {
            if (priority == ComicPageLoadPriority.Visible) {
                Log.d(
                    LOG_TAG,
                    "Visible page load finish item=${item.id} page=${page.pageIndex} result=${tracedResult?.traceLabel() ?: "cancelled"} duration=${SystemClock.elapsedRealtime() - startedAt}ms.",
                )
            }
        }
    }

    override suspend fun loadPagePreview(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPagePreviewResult {
        if (item.sourceType != sourceType) {
            return ComicPagePreviewResult.Unavailable
        }
        val session = activeSessionFor(item) ?: return ComicPagePreviewResult.Unavailable
        val cacheKey = pageCacheKey(
            baseUrl = session.baseUrl,
            itemKey = item.cacheIdentityKey(),
            pageId = page.id,
        )
        val previewByteSource = pagePreviewStrategy.visibleByteSource
        val previewCacheKey = pagePreviewCacheKey(
            pageCacheKey = cacheKey,
            options = options,
            byteSource = previewByteSource,
        )
        val requestGeneration = cacheGeneration.get()
        memory.pagePreviewCache[previewCacheKey]?.let { return it }
        readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
        val result = withContext(ioDispatcher) {
            memory.previewLoadSemaphore.withPermit {
                readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
                memory.pagePreviewCache[previewCacheKey]?.let { return@withPermit it }
                val bytes = try {
                    fetchPreviewBytes(
                        item = item,
                        page = page,
                        pageCacheKey = cacheKey,
                        previewCacheKey = previewCacheKey,
                        session = session,
                        byteSource = previewByteSource,
                    )
                } catch (cancellation: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    null
                } ?: return@withPermit ComicPagePreviewResult.Unavailable
                if (previewByteSource != RemoteComicPreviewByteSource.DedicatedPreview) {
                    decodeComicPageMetadata(bytes)?.let { metadata ->
                        if (cacheGeneration.get() == requestGeneration) {
                            memory.pageMetadataCache.put(cacheKey, metadata)
                        }
                    }
                }
                decodeComicPagePreview(bytes, options) ?: ComicPagePreviewResult.Unavailable
            }
        }
        if (result is ComicPagePreviewResult.Success && cacheGeneration.get() == requestGeneration) {
            memory.pagePreviewCache.put(previewCacheKey, result)
            if (previewByteSource != RemoteComicPreviewByteSource.DedicatedPreview) {
                previewVersionByCacheKeyFlow.increment(previewCacheKey)
            }
        }
        return result
    }

    override suspend fun loadPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? {
        if (!pagePreviewStrategy.canUseCachedPagePreviewMetadata) return null
        return loadCachedPagePreviewMetadata(
            item = item,
            page = page,
            options = options,
        )
    }

    override suspend fun loadCachedPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? {
        if (item.sourceType != sourceType) return null
        val session = activeSessionFor(item) ?: return null
        val pageCacheKey = pageCacheKey(
            baseUrl = session.baseUrl,
            itemKey = item.cacheIdentityKey(),
            pageId = page.id,
        )
        memory.pageMetadataCache[pageCacheKey]?.let { return it }
        if (!pagePreviewStrategy.canUseCachedPagePreviewMetadata) return null
        val previewCacheKey = pagePreviewCacheKey(
            pageCacheKey = pageCacheKey,
            options = options,
            byteSource = RemoteComicPreviewByteSource.CachedPage,
        )
        val bytes = memory.previewBytesCache[previewCacheKey]
            ?: previewStore?.readBytes(previewCacheKey)
            ?: return null
        memory.previewBytesCache.put(previewCacheKey, bytes)
        return decodeComicPageMetadata(bytes)?.also { metadata ->
            memory.pageMetadataCache.put(pageCacheKey, metadata)
        }
    }

    override fun prefetchPagePreviews(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
        priorityPageIndex: Int?,
    ) {
        if (!pagePreviewStrategy.prefetchesDedicatedPreviewBytes || item.sourceType != sourceType || pages.isEmpty()) return
        val session = activeSessionFor(item) ?: return
        val prefetchKey = pagePreviewPrefetchKey(
            baseUrl = session.baseUrl,
            itemKey = item.cacheIdentityKey(),
            options = options,
        )
        val priorityKey = priorityPageIndex ?: -1
        synchronized(prefetchLock) {
            previewPrefetchJobs.entries
                .filter { (_, prefetchJob) -> prefetchJob.job.isCancelled || prefetchJob.job.isCompleted }
                .map(Map.Entry<String, PreviewPrefetchJob>::key)
                .forEach(previewPrefetchJobs::remove)
            previewPrefetchJobs[prefetchKey]?.let { existingJob ->
                if (existingJob.priorityPageIndex == priorityKey) return
                existingJob.job.cancel()
                previewPrefetchJobs.remove(prefetchKey)
            }
            val job = scope.launch {
                val startedAt = SystemClock.elapsedRealtime()
                Log.d(
                    LOG_TAG,
                    "Preview prefetch start item=${item.id} pages=${pages.size} priorityPage=$priorityPageIndex.",
                )
                try {
                    kotlinx.coroutines.coroutineScope {
                        val pendingPages = pages.sortedByPriority(priorityPageIndex)
                        val nextPageIndex = AtomicInteger(0)
                        repeat(minOf(ComicPageLoaderCacheSizing.PREVIEW_LOAD_CONCURRENCY, pendingPages.size)) {
                            launch {
                                while (true) {
                                    val index = nextPageIndex.getAndIncrement()
                                    if (index >= pendingPages.size) return@launch
                                    val page = pendingPages[index]
                                    val pageCacheKey = pageCacheKey(
                                        baseUrl = session.baseUrl,
                                        itemKey = item.cacheIdentityKey(),
                                        pageId = page.id,
                                    )
                                    val previewCacheKey = pagePreviewCacheKey(
                                        pageCacheKey = pageCacheKey,
                                        options = options,
                                        byteSource = RemoteComicPreviewByteSource.DedicatedPreview,
                                    )
                                    if (memory.previewBytesCache[previewCacheKey] == null) {
                                        memory.previewPrefetchSemaphore.withPermit {
                                            readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
                                            warmDedicatedPreviewBytes(
                                                item = item,
                                                page = page,
                                                previewCacheKey = previewCacheKey,
                                                requestGeneration = cacheGeneration.get(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    // Preview warming is best effort and can stop when the source cache is cleared.
                } finally {
                    Log.d(
                        LOG_TAG,
                        "Preview prefetch finish item=${item.id} duration=${SystemClock.elapsedRealtime() - startedAt}ms.",
                    )
                    synchronized(prefetchLock) {
                        if (previewPrefetchJobs[prefetchKey]?.job === coroutineContext[Job]) {
                            previewPrefetchJobs.remove(prefetchKey)
                        }
                    }
                }
            }
            previewPrefetchJobs[prefetchKey] = PreviewPrefetchJob(
                priorityPageIndex = priorityKey,
                job = job,
            )
        }
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        prefetchAround(
            item = item,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
            window = ComicPagePrefetchWindow(),
        )
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
        window: ComicPagePrefetchWindow,
    ) {
        if (item.sourceType != sourceType || pageCount <= 1) return
        val session = activeSessionFor(item) ?: return
        val pages = getPages(item)
        val candidateIndexes = window.candidateIndexes(
            settledPageIndex = settledPageIndex,
            pageCount = minOf(pageCount, pages.size),
        )
        val itemKey = item.cacheIdentityKey()
        prefetchJobs.retarget(
            targets = candidateIndexes.map { pageIndex ->
                val page = pages[pageIndex]
                pageCacheKey(baseUrl = session.baseUrl, itemKey = itemKey, pageId = page.id) to suspend {
                    loadPageImage(
                        item = item,
                        page = page,
                        priority = ComicPageLoadPriority.Prefetch,
                    )
                }
            },
            isCached = { cacheKey -> memory.pageBitmapCache.get(cacheKey) != null },
        )
    }

    private suspend fun loadPageImageWithPriority(
        item: ReadableItem,
        page: ComicPageSpec,
        cacheKey: String,
        session: Session,
        requestGeneration: Int,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = withContext(ioDispatcher) {
        memory.semaphoreFor(priority).withPermit {
            if (priority == ComicPageLoadPriority.Prefetch) {
                readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
            }
            memory.pageBitmapCache[cacheKey]?.let { return@withPermit it }
            val bytes = fetchPageBytes(
                item = item,
                page = page,
                cacheKey = cacheKey,
                session = session,
                priority = priority,
            ) ?: return@withPermit ComicPageLoadResult.Failure("Unable to load this page.")
            decodeComicPageMetadata(bytes)?.let { metadata ->
                if (cacheGeneration.get() == requestGeneration) {
                    memory.pageMetadataCache.put(cacheKey, metadata)
                }
            }
            decodeComicPageImage(bytes) ?: ComicPageLoadResult.Failure("Unable to decode this page.")
        }
    }

    private fun cancelBestEffortWorkForVisibleLoad() {
        val byteRequestsToCancel = mutableListOf<Deferred<ByteArray?>>()
        synchronized(byteRequestLock) {
            inFlightByteRequests.entries
                .filter { (_, request) -> request.priority == ComicPageLoadPriority.Prefetch }
                .toList()
                .forEach { (key, request) ->
                    inFlightByteRequests.remove(key)
                    byteRequestsToCancel += request.deferred
                }
            inFlightPreviewByteRequests.entries
                .filter { (_, request) -> request.priority == ComicPageLoadPriority.Prefetch }
                .toList()
                .forEach { (key, request) ->
                    inFlightPreviewByteRequests.remove(key)
                    byteRequestsToCancel += request.deferred
                }
        }
        byteRequestsToCancel.forEach { request -> request.cancel() }
        cancelAllPrefetches()
    }

    private fun cancelAllPrefetches() {
        prefetchJobs.cancelAll()
        synchronized(prefetchLock) {
            previewPrefetchJobs.values.forEach { it.job.cancel() }
            previewPrefetchJobs.clear()
        }
    }

    open fun clear() {
        val byteRequestsToCancel = mutableListOf<Deferred<ByteArray?>>()
        cacheGeneration.incrementAndGet()
        memory.evictAll()
        synchronized(byteRequestLock) {
            byteRequestsToCancel += inFlightByteRequests.values.map(RemoteInFlightByteRequest::deferred)
            byteRequestsToCancel += inFlightPreviewByteRequests.values.map(RemoteInFlightByteRequest::deferred)
            inFlightByteRequests.clear()
            inFlightPreviewByteRequests.clear()
        }
        byteRequestsToCancel.forEach { request -> request.cancel() }
        cancelAllPrefetches()
    }

    open fun trimMemoryCaches(
        request: ReaderMemoryTrimRequest,
    ) {
        if (request.mode == ReaderMemoryTrimMode.ClearAll) {
            clear()
            return
        }
        cacheGeneration.incrementAndGet()
        cancelBestEffortWorkForMemoryTrim()
        val retainedPageCacheKeys = retainedPageCacheKeys(request.retention)
        memory.trimForMemory(retainPage = retainedPageCacheKeys.takeIf(Set<String>::isNotEmpty)?.let { it::contains })
    }

    private fun cancelBestEffortWorkForMemoryTrim() {
        val byteRequestsToCancel = mutableListOf<Deferred<ByteArray?>>()
        synchronized(byteRequestLock) {
            inFlightByteRequests.entries
                .filter { (_, request) -> request.priority == ComicPageLoadPriority.Prefetch }
                .toList()
                .forEach { (key, request) ->
                    inFlightByteRequests.remove(key)
                    byteRequestsToCancel += request.deferred
                }
            inFlightPreviewByteRequests.entries
                .filter { (_, request) -> request.priority == ComicPageLoadPriority.Prefetch }
                .toList()
                .forEach { (key, request) ->
                    inFlightPreviewByteRequests.remove(key)
                    byteRequestsToCancel += request.deferred
                }
        }
        byteRequestsToCancel.forEach { request -> request.cancel() }
        cancelAllPrefetches()
    }

    private fun retainedPageCacheKeys(
        retention: ReaderPageCacheRetention?,
    ): Set<String> {
        if (retention == null ||
            retention.pageIds.isEmpty() ||
            retention.item.sourceType != sourceType
        ) {
            return emptySet()
        }
        val session = activeSessionFor(retention.item) ?: return emptySet()
        val itemKey = retention.item.cacheIdentityKey()
        return retention.pageIds.mapTo(linkedSetOf()) { pageId ->
            pageCacheKey(
                baseUrl = session.baseUrl,
                itemKey = itemKey,
                pageId = pageId,
            )
        }
    }

    private suspend fun fetchPreviewBytes(
        item: ReadableItem,
        page: ComicPageSpec,
        pageCacheKey: String,
        previewCacheKey: String,
        session: Session,
        byteSource: RemoteComicPreviewByteSource,
    ): ByteArray? {
        val requestGeneration = cacheGeneration.get()
        return when (byteSource) {
            RemoteComicPreviewByteSource.DedicatedPreview -> fetchDedicatedPreviewBytes(
                item = item,
                page = page,
                previewCacheKey = previewCacheKey,
                requestGeneration = requestGeneration,
            )
            RemoteComicPreviewByteSource.CachedPage -> cachedPageBytesOrInFlight(
                cacheKey = pageCacheKey,
                requestGeneration = requestGeneration,
            )
            RemoteComicPreviewByteSource.PageImage -> fetchPageBytes(
                item = item,
                page = page,
                cacheKey = pageCacheKey,
                session = session,
                priority = ComicPageLoadPriority.Prefetch,
            )
        }
    }

    private suspend fun cachedPageBytesOrInFlight(
        cacheKey: String,
        requestGeneration: Int,
    ): ByteArray? {
        memory.pageBytesCache[cacheKey]?.let { return it }
        val inFlight = synchronized(byteRequestLock) {
            memory.pageBytesCache[cacheKey]?.let { cachedBytes ->
                return cachedBytes
            }
            inFlightByteRequests[RemoteComicPageByteRequestKey(cacheKey, requestGeneration)]?.deferred
        }
        return inFlight?.await()
    }

    private suspend fun fetchDedicatedPreviewBytes(
        item: ReadableItem,
        page: ComicPageSpec,
        previewCacheKey: String,
        requestGeneration: Int,
    ): ByteArray? {
        memory.previewBytesCache[previewCacheKey]?.let { return it }
        previewStore?.readBytes(previewCacheKey)?.let { cachedBytes ->
            memory.previewBytesCache.put(previewCacheKey, cachedBytes)
            return cachedBytes
        }
        val requestKey = RemoteComicPageByteRequestKey(
            cacheKey = previewCacheKey,
            generation = requestGeneration,
        )
        val request = synchronized(byteRequestLock) {
            memory.previewBytesCache[previewCacheKey]?.let { cachedBytes ->
                return cachedBytes
            }
            inFlightPreviewByteRequests[requestKey]?.deferred ?: scope.async {
                downloadAndCacheDedicatedPreviewBytes(
                    item = item,
                    page = page,
                    previewCacheKey = previewCacheKey,
                    requestGeneration = requestGeneration,
                )
            }.also { deferred ->
                inFlightPreviewByteRequests[requestKey] = RemoteInFlightByteRequest(
                    priority = ComicPageLoadPriority.Prefetch,
                    deferred = deferred,
                )
                deferred.invokeOnCompletion {
                    synchronized(byteRequestLock) {
                        if (inFlightPreviewByteRequests[requestKey]?.deferred === deferred) {
                            inFlightPreviewByteRequests.remove(requestKey)
                        }
                    }
                }
            }
        }
        return try {
            request.await()
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    private suspend fun warmDedicatedPreviewBytes(
        item: ReadableItem,
        page: ComicPageSpec,
        previewCacheKey: String,
        requestGeneration: Int,
    ): ByteArray? {
        memory.previewBytesCache[previewCacheKey]?.let { return it }
        previewStore?.readBytes(previewCacheKey)?.let { cachedBytes ->
            memory.previewBytesCache.put(previewCacheKey, cachedBytes)
            return cachedBytes
        }
        val requestKey = RemoteComicPageByteRequestKey(
            cacheKey = previewCacheKey,
            generation = requestGeneration,
        )
        return kotlinx.coroutines.coroutineScope {
            val request = synchronized(byteRequestLock) {
                memory.previewBytesCache[previewCacheKey]?.let { cachedBytes ->
                    return@coroutineScope cachedBytes
                }
                inFlightPreviewByteRequests[requestKey]?.deferred ?: async {
                    downloadAndCacheDedicatedPreviewBytes(
                        item = item,
                        page = page,
                        previewCacheKey = previewCacheKey,
                        requestGeneration = requestGeneration,
                    )
                }.also { deferred ->
                    inFlightPreviewByteRequests[requestKey] = RemoteInFlightByteRequest(
                        priority = ComicPageLoadPriority.Prefetch,
                        deferred = deferred,
                    )
                    deferred.invokeOnCompletion {
                        synchronized(byteRequestLock) {
                            if (inFlightPreviewByteRequests[requestKey]?.deferred === deferred) {
                                inFlightPreviewByteRequests.remove(requestKey)
                            }
                        }
                    }
                }
            }
            request.await()
        }
    }

    private suspend fun downloadAndCacheDedicatedPreviewBytes(
        item: ReadableItem,
        page: ComicPageSpec,
        previewCacheKey: String,
        requestGeneration: Int,
    ): ByteArray? {
        val bytes = try {
            val activeSession = activeSessionFor(item) ?: return null
            downloadPagePreviewBytes(
                session = activeSession,
                item = item,
                page = page,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        currentCoroutineContext().ensureActive()
        if (bytes != null && cacheGeneration.get() == requestGeneration) {
            synchronized(byteRequestLock) {
                if (cacheGeneration.get() == requestGeneration) {
                    memory.previewBytesCache.put(previewCacheKey, bytes)
                }
            }
            previewStore?.writeBytes(previewCacheKey, bytes)
            previewVersionByCacheKeyFlow.increment(previewCacheKey)
        }
        return bytes
    }

    private suspend fun fetchPageBytes(
        item: ReadableItem,
        page: ComicPageSpec,
        cacheKey: String,
        session: Session,
        priority: ComicPageLoadPriority,
    ): ByteArray? {
        memory.pageBytesCache[cacheKey]?.let { return it }
        val requestGeneration = cacheGeneration.get()
        val requestKey = RemoteComicPageByteRequestKey(
            cacheKey = cacheKey,
            generation = requestGeneration,
        )
        suspend fun downloadAndCachePageBytes(): ByteArray? {
            val bytes = try {
                val activeSession = activeSessionFor(item) ?: return null
                downloadPageBytes(
                    session = activeSession,
                    item = item,
                    page = page,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onReaderContentFailure(item, error)
                null
            }
            currentCoroutineContext().ensureActive()
            if (bytes != null && cacheGeneration.get() == requestGeneration) {
                synchronized(byteRequestLock) {
                    if (cacheGeneration.get() == requestGeneration) {
                        memory.pageBytesCache.put(cacheKey, bytes)
                    }
                }
            }
            return bytes
        }

        if (priority == ComicPageLoadPriority.Visible) {
            var cancelledBestEffortRequest: Deferred<ByteArray?>? = null
            var requestToAwait: Deferred<ByteArray?>? = null
            synchronized(byteRequestLock) {
                memory.pageBytesCache[cacheKey]?.let { cachedBytes ->
                    return cachedBytes
                }
                val existingRequest = inFlightByteRequests[requestKey]
                if (existingRequest != null) {
                    if (existingRequest.priority == ComicPageLoadPriority.Prefetch) {
                        inFlightByteRequests.remove(requestKey)
                        cancelledBestEffortRequest = existingRequest.deferred
                    } else {
                        requestToAwait = existingRequest.deferred
                    }
                }
            }
            cancelledBestEffortRequest?.cancel()
            requestToAwait?.let { request -> return request.await() }
            return downloadAndCachePageBytes()
        }

        val request = synchronized(byteRequestLock) {
            memory.pageBytesCache[cacheKey]?.let { cachedBytes ->
                return cachedBytes
            }
            val existingRequest = inFlightByteRequests[requestKey]
            if (existingRequest != null) {
                return@synchronized existingRequest.deferred
            }
            scope.async {
                downloadAndCachePageBytes()
            }.also { deferred ->
                inFlightByteRequests[requestKey] = RemoteInFlightByteRequest(
                    priority = priority,
                    deferred = deferred,
                )
                deferred.invokeOnCompletion {
                    synchronized(byteRequestLock) {
                        if (inFlightByteRequests[requestKey]?.deferred === deferred) {
                            inFlightByteRequests.remove(requestKey)
                        }
                    }
                }
            }
        }
        return request.await()
    }

    private fun pageCacheKey(
        baseUrl: String,
        itemKey: String,
        pageId: String,
    ): String = "${baseUrl.trimEnd('/')}::$itemKey::$pageId"

    private fun pagePreviewCacheKey(
        pageCacheKey: String,
        options: ComicPagePreviewOptions,
        byteSource: RemoteComicPreviewByteSource,
    ): String = "$pageCacheKey::preview:$PagePreviewCacheVersion:${byteSource.cacheKeyToken}:${options.targetLongEdge}:${options.maxPixelCount}"

    private fun pagePreviewPrefetchKey(
        baseUrl: String,
        itemKey: String,
        options: ComicPagePreviewOptions,
    ): String = "${baseUrl.trimEnd('/')}::$itemKey::preview-prefetch:${options.targetLongEdge}:${options.maxPixelCount}"
}

enum class RemoteComicPreviewByteSource(
    val cacheKeyToken: String,
) {
    DedicatedPreview("dedicated"),
    CachedPage("cached-page"),
    PageImage("page-image"),
}

enum class RemoteComicPagePreviewStrategy(
    val visibleByteSource: RemoteComicPreviewByteSource,
    val observableByteSources: List<RemoteComicPreviewByteSource>,
    val prefetchesDedicatedPreviewBytes: Boolean,
    val canUseCachedPagePreviewMetadata: Boolean,
) {
    CachedPageImage(
        visibleByteSource = RemoteComicPreviewByteSource.CachedPage,
        observableByteSources = listOf(RemoteComicPreviewByteSource.CachedPage),
        prefetchesDedicatedPreviewBytes = false,
        canUseCachedPagePreviewMetadata = true,
    ),
    DedicatedPreview(
        visibleByteSource = RemoteComicPreviewByteSource.DedicatedPreview,
        observableByteSources = listOf(RemoteComicPreviewByteSource.DedicatedPreview),
        prefetchesDedicatedPreviewBytes = true,
        canUseCachedPagePreviewMetadata = false,
    ),
    PageImagePreview(
        visibleByteSource = RemoteComicPreviewByteSource.PageImage,
        observableByteSources = listOf(RemoteComicPreviewByteSource.PageImage),
        prefetchesDedicatedPreviewBytes = false,
        canUseCachedPagePreviewMetadata = false,
    ),
}

private fun MutableStateFlow<Map<String, Long>>.increment(
    key: String,
) {
    update { versions ->
        versions + (key to ((versions[key] ?: 0L) + 1L))
    }
}

private fun ReadableItem.cacheIdentityKey(): String = listOf(
    sourceType.name,
    id,
    remoteId.orEmpty(),
    remoteFileId.orEmpty(),
    remoteFileType.orEmpty(),
    pageCount?.toString().orEmpty(),
).joinToString(separator = "::")

private fun List<ComicPageSpec>.sortedByPriority(
    priorityPageIndex: Int?,
): List<ComicPageSpec> {
    val priority = priorityPageIndex ?: return this
    return sortedWith(
        compareBy<ComicPageSpec> { page -> abs(page.pageIndex - priority) }
            .thenBy(ComicPageSpec::pageIndex),
    )
}

private fun ComicPageLoadResult.traceLabel(): String = when (this) {
    is ComicPageLoadResult.Success -> "success"
    is ComicPageLoadResult.Failure -> "failure"
}

private data class RemoteComicPageByteRequestKey(
    val cacheKey: String,
    val generation: Int,
)

private data class RemoteInFlightByteRequest(
    val priority: ComicPageLoadPriority,
    val deferred: Deferred<ByteArray?>,
)

private data class PreviewPrefetchJob(
    val priorityPageIndex: Int,
    val job: Job,
)

private const val PagePreviewCacheVersion = "v4"
private const val LOG_TAG = "RemoteComicPageSource"
