package com.example.unifiedreader

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageLoaderCacheSizing
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewOptions
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import com.example.unifiedreader.feature.reader.comics.candidateIndexes
import com.example.unifiedreader.feature.reader.comics.comicPageBitmapCacheEntryBytes
import com.example.unifiedreader.feature.reader.comics.comicPagePreviewCacheEntryBytes
import com.example.unifiedreader.feature.reader.comics.decodeComicPageImage
import com.example.unifiedreader.feature.reader.comics.decodeComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.decodeComicPagePreview
import com.example.unifiedreader.feature.reader.comics.decodeComicPagePreviewBytes
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File

internal class LocalComicPageLoader(
    private val context: Context,
    private val comicArchiveCodecs: List<ComicArchiveCodec>,
    private val scope: CoroutineScope,
    private val previewStore: PersistentComicPagePreviewStore? = null,
    private val readerWorkCoordinator: ReaderWorkCoordinator = ReaderWorkCoordinator(),
) {
    private val pageBitmapCache = object : LruCache<String, ComicPageLoadResult.Success>(
        ComicPageLoaderCacheSizing.bitmapCacheBytes(),
    ) {
        override fun sizeOf(
            key: String,
            value: ComicPageLoadResult.Success,
        ): Int = comicPageBitmapCacheEntryBytes(value)
    }
    private val pageMetadataCache = LruCache<String, ComicPageMetadata>(
        ComicPageLoaderCacheSizing.METADATA_CACHE_ENTRIES,
    )
    private val pagePreviewCache = object : LruCache<String, ComicPagePreviewResult.Success>(
        ComicPageLoaderCacheSizing.previewCacheBytes(),
    ) {
        override fun sizeOf(
            key: String,
            value: ComicPagePreviewResult.Success,
        ): Int = comicPagePreviewCacheEntryBytes(value)
    }
    private val pageBytesCache = object : LruCache<String, ByteArray>(
        ComicPageLoaderCacheSizing.pageByteCacheBytes(),
    ) {
        override fun sizeOf(
            key: String,
            value: ByteArray,
        ): Int = value.size
    }
    private val previewBytesCache = object : LruCache<String, ByteArray>(
        ComicPageLoaderCacheSizing.previewByteCacheBytes(),
    ) {
        override fun sizeOf(
            key: String,
            value: ByteArray,
        ): Int = value.size
    }
    private val cacheGenerationByItemId = mutableMapOf<String, Int>()
    private var baseCacheGeneration = 0
    private val inFlightByteRequests = mutableMapOf<LocalPageByteRequestKey, LocalInFlightByteRequest>()
    private val inFlightPreviewByteRequests = mutableMapOf<LocalPageByteRequestKey, LocalInFlightByteRequest>()
    private val byteRequestLock = Any()
    private val visibleLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.VISIBLE_LOAD_CONCURRENCY)
    private val metadataLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.METADATA_LOAD_CONCURRENCY)
    private val prefetchLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.PREFETCH_LOAD_CONCURRENCY)
    private val previewLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.PREVIEW_LOAD_CONCURRENCY)
    private val previewPrefetchSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.PREVIEW_PREFETCH_CONCURRENCY)
    private val prefetchJobs = mutableMapOf<String, Job>()
    private val previewPrefetchJobs = mutableMapOf<String, LocalPreviewPrefetchJob>()
    private val prefetchLock = Any()
    private val previewVersionByCacheKeyFlow = MutableStateFlow<Map<String, Long>>(emptyMap())

    suspend fun loadPageMetadata(
        item: ReadableItem,
        record: LocalDocumentRecord,
        entryName: String,
    ): ComicPageMetadata? {
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format } ?: return null
        val cacheKey = pageCacheKey(item.id, record, entryName)
        val requestGeneration = generationFor(item.id)
        pageMetadataCache[cacheKey]?.let { return it }
        readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
        return withContext(Dispatchers.IO) {
            metadataLoadSemaphore.withPermit {
                readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
                pageMetadataCache[cacheKey]?.let { return@withPermit it }
                val bytes = try {
                    loadEntryBytes(
                        itemId = item.id,
                        cacheKey = cacheKey,
                        requestGeneration = requestGeneration,
                        record = record,
                        codec = codec,
                        entryName = entryName,
                        priority = ComicPageLoadPriority.Prefetch,
                    )
                } catch (cancellation: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    null
                } ?: return@withPermit null
                decodeComicPageMetadata(bytes)?.also { metadata ->
                    if (generationFor(item.id) == requestGeneration) {
                        pageMetadataCache.put(cacheKey, metadata)
                    }
                }
            }
        }
    }

    suspend fun loadPageImage(
        item: ReadableItem,
        record: LocalDocumentRecord,
        entryName: String,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format }
            ?: return ComicPageLoadResult.Failure("Unsupported archive format.")
        val cacheKey = pageCacheKey(item.id, record, entryName)
        val requestGeneration = generationFor(item.id)
        pageBitmapCache[cacheKey]?.let { return it }
        if (priority == ComicPageLoadPriority.Visible) {
            cancelBestEffortWorkForVisibleLoad(itemId = item.id)
        }
        val startedAt = if (priority == ComicPageLoadPriority.Visible) {
            SystemClock.elapsedRealtime().also {
                Log.d(LOG_TAG, "Visible page load start item=${item.id} entry=$entryName.")
            }
        } else {
            0L
        }
        var loadedResult: ComicPageLoadResult? = null
        try {
            val result = when (priority) {
                ComicPageLoadPriority.Visible -> readerWorkCoordinator.runVisiblePageLoad(item.id) {
                    try {
                        loadPageImageWithPriority(
                            itemId = item.id,
                            record = record,
                            codec = codec,
                            entryName = entryName,
                            cacheKey = cacheKey,
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
                            itemId = item.id,
                            record = record,
                            codec = codec,
                            entryName = entryName,
                            cacheKey = cacheKey,
                            requestGeneration = requestGeneration,
                            priority = priority,
                        )
                    } catch (cancellation: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        ComicPageLoadResult.Failure("Page prefetch was interrupted.")
                    }
                }
            }
            loadedResult = result
            if (result is ComicPageLoadResult.Success && generationFor(item.id) == requestGeneration) {
                pageBitmapCache.put(cacheKey, result)
            }
            return result
        } finally {
            if (priority == ComicPageLoadPriority.Visible) {
                Log.d(
                    LOG_TAG,
                    "Visible page load finish item=${item.id} entry=$entryName result=${loadedResult?.traceLabel() ?: "cancelled"} duration=${SystemClock.elapsedRealtime() - startedAt}ms.",
                )
            }
        }
    }

    suspend fun loadPagePreview(
        item: ReadableItem,
        record: LocalDocumentRecord,
        entryName: String,
        options: ComicPagePreviewOptions,
    ): ComicPagePreviewResult {
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format }
            ?: return ComicPagePreviewResult.Unavailable
        val cacheKey = pageCacheKey(item.id, record, entryName)
        val previewCacheKey = pagePreviewCacheKey(cacheKey, options)
        val requestGeneration = generationFor(item.id)
        pagePreviewCache[previewCacheKey]?.let { return it }
        readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
        val loadedResult = withContext(Dispatchers.IO) {
            previewLoadSemaphore.withPermit {
                readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
                pagePreviewCache[previewCacheKey]?.let { return@withPermit it }
                var previewBytesCancelled = false
                val previewBytes = try {
                    loadPreviewBytes(
                        itemId = item.id,
                        cacheKey = cacheKey,
                        previewCacheKey = previewCacheKey,
                        requestGeneration = requestGeneration,
                        record = record,
                        codec = codec,
                        entryName = entryName,
                        options = options,
                        priority = ComicPageLoadPriority.Prefetch,
                    )
                } catch (cancellation: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    previewBytesCancelled = true
                    null
                }
                if (previewBytesCancelled) {
                    return@withPermit ComicPagePreviewResult.Unavailable
                }
                decodePreviewResult(
                    itemId = item.id,
                    cacheKey = cacheKey,
                    requestGeneration = requestGeneration,
                    bytes = previewBytes,
                    options = options,
                ) ?: try {
                    loadEntryBytes(
                        itemId = item.id,
                        cacheKey = cacheKey,
                        requestGeneration = requestGeneration,
                        record = record,
                        codec = codec,
                        entryName = entryName,
                        priority = ComicPageLoadPriority.Prefetch,
                    )
                } catch (cancellation: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    null
                }?.let { bytes ->
                    decodePreviewResult(
                        itemId = item.id,
                        cacheKey = cacheKey,
                        requestGeneration = requestGeneration,
                        bytes = bytes,
                        options = options,
                    )
                } ?: ComicPagePreviewResult.Unavailable
            }
        }
        if (loadedResult is ComicPagePreviewResult.Success && generationFor(item.id) == requestGeneration) {
            pagePreviewCache.put(previewCacheKey, loadedResult)
        }
        return loadedResult
    }

    private fun decodePreviewResult(
        itemId: String,
        cacheKey: String,
        requestGeneration: Int,
        bytes: ByteArray?,
        options: ComicPagePreviewOptions,
    ): ComicPagePreviewResult.Success? {
        if (bytes == null) return null
        decodeComicPageMetadata(bytes)?.let { metadata ->
            if (generationFor(itemId) == requestGeneration) {
                pageMetadataCache.put(cacheKey, metadata)
            }
        }
        return decodeComicPagePreview(bytes, options)
    }

    fun cachedPageMetadata(
        item: ReadableItem,
        record: LocalDocumentRecord,
        entryName: String,
    ): ComicPageMetadata? = pageMetadataCache[pageCacheKey(item.id, record, entryName)]

    fun observePagePreviewVersion(
        previewCacheKeys: Set<String>,
    ): Flow<Long> = previewVersionByCacheKeyFlow
        .map { versions -> previewCacheKeys.sumOf { key -> versions[key] ?: 0L } }
        .distinctUntilChanged()

    fun pagePreviewCacheKeys(
        item: ReadableItem,
        record: LocalDocumentRecord,
        entryNames: List<String>,
        options: ComicPagePreviewOptions,
    ): Set<String> = entryNames
        .map { entryName ->
            pagePreviewCacheKey(
                pageCacheKey = pageCacheKey(item.id, record, entryName),
                options = options,
            )
        }
        .toSet()

    fun prefetchPagePreviews(
        item: ReadableItem,
        record: LocalDocumentRecord,
        entryNames: List<String>,
        options: ComicPagePreviewOptions,
        priorityPageIndex: Int?,
    ) {
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format } ?: return
        if (entryNames.isEmpty()) return
        val prefetchKeyPrefix = "${item.id}::preview-prefetch:${options.targetLongEdge}:${options.maxPixelCount}"
        val prefetchKey = "$prefetchKeyPrefix:${entryNames.previewWindowKey()}"
        val priorityKey = priorityPageIndex ?: -1
        synchronized(prefetchLock) {
            previewPrefetchJobs.entries
                .filter { (_, prefetchJob) -> prefetchJob.job.isCancelled || prefetchJob.job.isCompleted }
                .map(Map.Entry<String, LocalPreviewPrefetchJob>::key)
                .forEach(previewPrefetchJobs::remove)
            previewPrefetchJobs.entries
                .filter { (key, _) -> key.startsWith(prefetchKeyPrefix) && key != prefetchKey }
                .toList()
                .forEach { (key, prefetchJob) ->
                    prefetchJob.job.cancel()
                    previewPrefetchJobs.remove(key)
                }
            previewPrefetchJobs[prefetchKey]?.let { existingJob ->
                if (existingJob.priorityPageIndex == priorityKey) return
                existingJob.job.cancel()
                previewPrefetchJobs.remove(prefetchKey)
            }
            val job = scope.launch {
                val startedAt = SystemClock.elapsedRealtime()
                Log.d(
                    LOG_TAG,
                    "Preview prefetch start item=${item.id} pages=${entryNames.size} priorityPage=$priorityPageIndex.",
                )
                try {
                    kotlinx.coroutines.coroutineScope {
                        val pendingIndexes = entryNames.indices.sortedByPriority(priorityPageIndex)
                        val nextPageIndex = java.util.concurrent.atomic.AtomicInteger(0)
                        repeat(minOf(ComicPageLoaderCacheSizing.PREVIEW_LOAD_CONCURRENCY, pendingIndexes.size)) {
                            launch {
                                while (true) {
                                    val index = nextPageIndex.getAndIncrement()
                                    if (index >= pendingIndexes.size) return@launch
                                    val pageIndex = pendingIndexes[index]
                                    val entryName = entryNames[pageIndex]
                                    val cacheKey = pageCacheKey(item.id, record, entryName)
                                    val previewCacheKey = pagePreviewCacheKey(cacheKey, options)
                                    if (previewBytesCache[previewCacheKey] == null) {
                                        previewPrefetchSemaphore.withPermit {
                                            readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
                                            warmPreviewBytes(
                                                itemId = item.id,
                                                cacheKey = cacheKey,
                                                previewCacheKey = previewCacheKey,
                                                requestGeneration = generationFor(item.id),
                                                record = record,
                                                codec = codec,
                                                entryName = entryName,
                                                options = options,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    // Preview warming is best effort and can stop when the selected page changes.
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
            previewPrefetchJobs[prefetchKey] = LocalPreviewPrefetchJob(
                priorityPageIndex = priorityKey,
                job = job,
            )
        }
    }

    fun prefetchAround(
        item: ReadableItem,
        record: LocalDocumentRecord,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        prefetchAround(
            item = item,
            record = record,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
            window = ComicPagePrefetchWindow(),
        )
    }

    fun prefetchAround(
        item: ReadableItem,
        record: LocalDocumentRecord,
        settledPageIndex: Int,
        pageCount: Int,
        window: ComicPagePrefetchWindow,
    ) {
        if (pageCount <= 1) return
        val candidateIndexes = window.candidateIndexes(
            settledPageIndex = settledPageIndex,
            pageCount = minOf(pageCount, record.pageEntryNames.size),
        )
        val targetKeys = candidateIndexes.associateWith { pageIndex ->
            pageCacheKey(item.id, record, record.pageEntryNames[pageIndex])
        }

        synchronized(prefetchLock) {
            prefetchJobs.entries
                .filter { (_, job) -> job.isCancelled || job.isCompleted }
                .map(Map.Entry<String, Job>::key)
                .forEach(prefetchJobs::remove)
            prefetchJobs.entries
                .filter { (cacheKey, _) -> cacheKey !in targetKeys.values }
                .toList()
                .forEach { (cacheKey, job) ->
                    job.cancel()
                    prefetchJobs.remove(cacheKey)
                }
        }

        candidateIndexes.forEach { pageIndex ->
            val entryName = record.pageEntryNames[pageIndex]
            val cacheKey = targetKeys.getValue(pageIndex)
            if (pageBitmapCache.get(cacheKey) != null) {
                return@forEach
            }
            val shouldLaunch = synchronized(prefetchLock) {
                if (prefetchJobs.containsKey(cacheKey)) {
                    false
                } else {
                    val job = scope.launch {
                        try {
                            loadPageImage(
                                item = item,
                                record = record,
                                entryName = entryName,
                                priority = ComicPageLoadPriority.Prefetch,
                            )
                        } catch (_: CancellationException) {
                            // Prefetch is best effort and should stop immediately when the plan changes.
                        } finally {
                            synchronized(prefetchLock) {
                                prefetchJobs.remove(cacheKey)
                            }
                        }
                    }
                    prefetchJobs[cacheKey] = job
                    true
                }
            }
            if (!shouldLaunch) {
                return@forEach
            }
        }
    }

    private suspend fun loadPageImageWithPriority(
        itemId: String,
        record: LocalDocumentRecord,
        codec: ComicArchiveCodec,
        entryName: String,
        cacheKey: String,
        requestGeneration: Int,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = withContext(Dispatchers.IO) {
        semaphoreFor(priority).withPermit {
            if (priority == ComicPageLoadPriority.Prefetch) {
                readerWorkCoordinator.awaitReaderBestEffortWorkAllowed()
            }
            pageBitmapCache[cacheKey]?.let { return@withPermit it }
            decodePageImage(
                itemId = itemId,
                record = record,
                codec = codec,
                entryName = entryName,
                cacheKey = cacheKey,
                requestGeneration = requestGeneration,
                priority = priority,
            )
        }
    }

    private fun cancelBestEffortWorkForVisibleLoad(
        itemId: String,
    ) {
        val byteRequestsToCancel = mutableListOf<Deferred<ByteArray?>>()
        synchronized(byteRequestLock) {
            inFlightByteRequests.entries
                .filter { (key, request) ->
                    key.cacheKey.startsWith("$itemId::") && request.priority == ComicPageLoadPriority.Prefetch
                }
                .toList()
                .forEach { (key, request) ->
                    inFlightByteRequests.remove(key)
                    byteRequestsToCancel += request.deferred
                }
            inFlightPreviewByteRequests.entries
                .filter { (key, request) ->
                    key.cacheKey.startsWith("$itemId::") && request.priority == ComicPageLoadPriority.Prefetch
                }
                .toList()
                .forEach { (key, request) ->
                    inFlightPreviewByteRequests.remove(key)
                    byteRequestsToCancel += request.deferred
                }
        }
        byteRequestsToCancel.forEach { request -> request.cancel() }
        synchronized(prefetchLock) {
            prefetchJobs.entries
                .filter { (cacheKey, _) -> cacheKey.startsWith("$itemId::") }
                .toList()
                .forEach { (cacheKey, job) ->
                    job.cancel()
                    prefetchJobs.remove(cacheKey)
                }
            previewPrefetchJobs.entries
                .filter { (cacheKey, _) -> cacheKey.startsWith("$itemId::") }
                .toList()
                .forEach { (cacheKey, prefetchJob) ->
                    prefetchJob.job.cancel()
                    previewPrefetchJobs.remove(cacheKey)
                }
        }
    }

    fun clear(itemId: String) {
        val byteRequestsToCancel = mutableListOf<Deferred<ByteArray?>>()
        synchronized(byteRequestLock) {
            cacheGenerationByItemId[itemId] = generationFor(itemId) + 1
            inFlightByteRequests.entries
                .filter { (key, _) -> key.cacheKey.startsWith("$itemId::") }
                .toList()
                .forEach { (key, request) ->
                    inFlightByteRequests.remove(key)
                    byteRequestsToCancel += request.deferred
                }
            inFlightPreviewByteRequests.entries
                .filter { (key, _) -> key.cacheKey.startsWith("$itemId::") }
                .toList()
                .forEach { (key, request) ->
                    inFlightPreviewByteRequests.remove(key)
                    byteRequestsToCancel += request.deferred
                }
        }
        byteRequestsToCancel.forEach { request -> request.cancel() }
        pageBitmapCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(pageBitmapCache::remove)
        pageMetadataCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(pageMetadataCache::remove)
        pagePreviewCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(pagePreviewCache::remove)
        pageBytesCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(pageBytesCache::remove)
        previewBytesCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(previewBytesCache::remove)
        synchronized(prefetchLock) {
            prefetchJobs.entries
                .filter { (cacheKey, _) -> cacheKey.startsWith("$itemId::") }
                .toList()
                .forEach { (cacheKey, job) ->
                    job.cancel()
                    prefetchJobs.remove(cacheKey)
                }
            previewPrefetchJobs.entries
                .filter { (cacheKey, _) -> cacheKey.startsWith("$itemId::") }
                .toList()
                .forEach { (cacheKey, prefetchJob) ->
                    prefetchJob.job.cancel()
                    previewPrefetchJobs.remove(cacheKey)
                }
        }
    }

    fun clearAll() {
        val byteRequestsToCancel = mutableListOf<Deferred<ByteArray?>>()
        synchronized(byteRequestLock) {
            advanceAllCacheGenerationsLocked()
            byteRequestsToCancel += inFlightByteRequests.values.map(LocalInFlightByteRequest::deferred)
            byteRequestsToCancel += inFlightPreviewByteRequests.values.map(LocalInFlightByteRequest::deferred)
            inFlightByteRequests.clear()
            inFlightPreviewByteRequests.clear()
        }
        byteRequestsToCancel.forEach { request -> request.cancel() }
        pageBitmapCache.evictAll()
        pageMetadataCache.evictAll()
        pagePreviewCache.evictAll()
        pageBytesCache.evictAll()
        previewBytesCache.evictAll()
        synchronized(prefetchLock) {
            prefetchJobs.values.forEach(Job::cancel)
            prefetchJobs.clear()
            previewPrefetchJobs.values.forEach { prefetchJob ->
                prefetchJob.job.cancel()
            }
            previewPrefetchJobs.clear()
        }
    }

    fun trimMemoryCaches(
        request: ReaderMemoryTrimRequest,
    ) {
        if (request.mode == ReaderMemoryTrimMode.ClearAll) {
            clearAll()
            return
        }
        synchronized(byteRequestLock) {
            advanceAllCacheGenerationsLocked()
        }
        cancelBestEffortWorkForMemoryTrim()
        pagePreviewCache.evictAll()
        previewBytesCache.evictAll()
        pageBytesCache.evictAll()
        trimPageBitmapCache(request.retention)
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
        synchronized(prefetchLock) {
            prefetchJobs.values.forEach(Job::cancel)
            prefetchJobs.clear()
            previewPrefetchJobs.values.forEach { prefetchJob ->
                prefetchJob.job.cancel()
            }
            previewPrefetchJobs.clear()
        }
    }

    private fun trimPageBitmapCache(
        retention: ReaderPageCacheRetention?,
    ) {
        if (retention == null) {
            pageBitmapCache.evictAll()
            return
        }
        pageBitmapCache.snapshot().keys
            .filterNot { cacheKey -> retention.shouldRetainLocalPageCacheKey(cacheKey) }
            .forEach(pageBitmapCache::remove)
    }

    private fun ReaderPageCacheRetention.shouldRetainLocalPageCacheKey(
        cacheKey: String,
    ): Boolean = pageIds.isNotEmpty() &&
        cacheKey.startsWith("$itemId::") &&
        pageIds.any { pageId -> cacheKey.endsWith("::$pageId") }

    private suspend fun decodePageImage(
        itemId: String,
        record: LocalDocumentRecord,
        codec: ComicArchiveCodec,
        entryName: String,
        cacheKey: String,
        requestGeneration: Int,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        return runCatching {
            val bytes = loadEntryBytes(
                itemId = itemId,
                cacheKey = cacheKey,
                requestGeneration = requestGeneration,
                record = record,
                codec = codec,
                entryName = entryName,
                priority = priority,
            ) ?: return@runCatching ComicPageLoadResult.Failure("Unable to decode this page.")
            decodeComicPageMetadata(bytes)?.let { metadata ->
                if (generationFor(itemId) == requestGeneration) {
                    pageMetadataCache.put(cacheKey, metadata)
                }
            }
            decodeComicPageImage(bytes) ?: ComicPageLoadResult.Failure("Unable to decode this page.")
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.w(LOG_TAG, "Failed to decode page $entryName for ${record.displayName}", throwable)
            ComicPageLoadResult.Failure("Unable to decode this page.")
        }
    }

    private fun semaphoreFor(
        priority: ComicPageLoadPriority,
    ): Semaphore = when (priority) {
        ComicPageLoadPriority.Visible -> visibleLoadSemaphore
        ComicPageLoadPriority.Prefetch -> prefetchLoadSemaphore
    }

    private suspend fun loadEntryBytes(
        itemId: String,
        cacheKey: String,
        requestGeneration: Int,
        record: LocalDocumentRecord,
        codec: ComicArchiveCodec,
        entryName: String,
        priority: ComicPageLoadPriority,
    ): ByteArray? {
        pageBytesCache[cacheKey]?.let { return it }
        val requestKey = LocalPageByteRequestKey(
            cacheKey = cacheKey,
            generation = requestGeneration,
        )
        suspend fun readAndCacheEntryBytes(): ByteArray? {
            val bytes = codec.readEntryBytes(
                context = context,
                uri = Uri.parse(record.uri),
                entryName = entryName,
            )
            currentCoroutineContext().ensureActive()
            if (bytes != null && generationFor(itemId) == requestGeneration) {
                synchronized(byteRequestLock) {
                    if (generationFor(itemId) == requestGeneration) {
                        pageBytesCache.put(cacheKey, bytes)
                    }
                }
            }
            return bytes
        }

        if (priority == ComicPageLoadPriority.Visible) {
            var cancelledBestEffortRequest: Deferred<ByteArray?>? = null
            var requestToAwait: Deferred<ByteArray?>? = null
            synchronized(byteRequestLock) {
                pageBytesCache[cacheKey]?.let { cachedBytes ->
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
            return readAndCacheEntryBytes()
        }

        val request = synchronized(byteRequestLock) {
            pageBytesCache[cacheKey]?.let { cachedBytes ->
                return cachedBytes
            }
            val existingRequest = inFlightByteRequests[requestKey]
            if (existingRequest != null) {
                return@synchronized existingRequest.deferred
            }
            scope.async(Dispatchers.IO) {
                readAndCacheEntryBytes()
            }.also { deferred ->
                inFlightByteRequests[requestKey] = LocalInFlightByteRequest(
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

    private suspend fun loadPreviewBytes(
        itemId: String,
        cacheKey: String,
        previewCacheKey: String,
        requestGeneration: Int,
        record: LocalDocumentRecord,
        codec: ComicArchiveCodec,
        entryName: String,
        options: ComicPagePreviewOptions,
        priority: ComicPageLoadPriority,
    ): ByteArray? {
        previewBytesCache[previewCacheKey]?.let { return it }
        previewStore?.readBytes(previewCacheKey)
            ?.takeIf { cachedBytes ->
                cachedBytes.hasJpegSignature() && decodeComicPageMetadata(cachedBytes) != null
            }
            ?.let { cachedBytes ->
                previewBytesCache.put(previewCacheKey, cachedBytes)
                return cachedBytes
            }
        val requestKey = LocalPageByteRequestKey(
            cacheKey = previewCacheKey,
            generation = requestGeneration,
        )
        var cancelledBestEffortRequest: Deferred<ByteArray?>? = null
        val request = synchronized(byteRequestLock) {
            previewBytesCache[previewCacheKey]?.let { cachedBytes ->
                return cachedBytes
            }
            val existingRequest = inFlightPreviewByteRequests[requestKey]
            if (existingRequest != null) {
                if (priority == ComicPageLoadPriority.Visible &&
                    existingRequest.priority == ComicPageLoadPriority.Prefetch
                ) {
                    inFlightPreviewByteRequests.remove(requestKey)
                    cancelledBestEffortRequest = existingRequest.deferred
                } else {
                    return@synchronized existingRequest.deferred
                }
            }
            scope.async(Dispatchers.IO) {
                val bytes = loadEntryBytes(
                    itemId = itemId,
                    cacheKey = cacheKey,
                    requestGeneration = requestGeneration,
                    record = record,
                    codec = codec,
                    entryName = entryName,
                    priority = priority,
                )?.also {
                    currentCoroutineContext().ensureActive()
                }?.let { sourceBytes ->
                    decodeComicPagePreviewBytes(sourceBytes, options)
                }
                if (bytes != null && generationFor(itemId) == requestGeneration) {
                    synchronized(byteRequestLock) {
                        if (generationFor(itemId) == requestGeneration) {
                            previewBytesCache.put(previewCacheKey, bytes)
                        }
                    }
                    previewStore?.writeBytes(previewCacheKey, bytes)
                    previewVersionByCacheKeyFlow.increment(previewCacheKey)
                }
                bytes
            }.also { deferred ->
                inFlightPreviewByteRequests[requestKey] = LocalInFlightByteRequest(
                    priority = priority,
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
        cancelledBestEffortRequest?.cancel()
        return request.await()
    }

    private suspend fun warmPreviewBytes(
        itemId: String,
        cacheKey: String,
        previewCacheKey: String,
        requestGeneration: Int,
        record: LocalDocumentRecord,
        codec: ComicArchiveCodec,
        entryName: String,
        options: ComicPagePreviewOptions,
    ): ByteArray? = loadPreviewBytes(
        itemId = itemId,
        cacheKey = cacheKey,
        previewCacheKey = previewCacheKey,
        requestGeneration = requestGeneration,
        record = record,
        codec = codec,
        entryName = entryName,
        options = options,
        priority = ComicPageLoadPriority.Prefetch,
    )

    private fun pageCacheKey(
        itemId: String,
        record: LocalDocumentRecord,
        entryName: String,
    ): String = "$itemId::${record.cacheIdentity(context)}::$entryName"

    private fun pagePreviewCacheKey(
        pageCacheKey: String,
        options: ComicPagePreviewOptions,
    ): String = "$pageCacheKey::preview:${options.targetLongEdge}:${options.maxPixelCount}"

    private fun generationFor(
        itemId: String,
    ): Int = synchronized(byteRequestLock) {
        cacheGenerationByItemId[itemId] ?: baseCacheGeneration
    }

    private fun advanceAllCacheGenerationsLocked() {
        baseCacheGeneration = maxOf(
            baseCacheGeneration,
            cacheGenerationByItemId.values.maxOrNull() ?: baseCacheGeneration,
        ) + 1
        cacheGenerationByItemId.clear()
    }

    private companion object {
        const val LOG_TAG = "ImportedComicArchive"
    }
}

private fun List<String>.previewWindowKey(): String = joinToString(separator = "|").hashCode().toUInt().toString(16)

private fun LocalDocumentRecord.cacheIdentity(
    context: Context,
): String {
    val uri = Uri.parse(uri)
    if (uri.scheme == "file") {
        val file = uri.path?.let(::File)
        if (file != null && file.isFile) {
            return "${uri}::${file.length()}::${file.lastModified()}"
        }
    }
    return "${uri}::${context.archiveSignature(uri)}::${pageCount}::${pageEntryNames.size}"
}

private fun Context.archiveSignature(
    uri: Uri,
): String {
    if (uri.scheme != "content") return "unknown"
    val projection = arrayOf(
        OpenableColumns.SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )
    return runCatching {
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use "unknown"
            val size = cursor.getLongOrNull(OpenableColumns.SIZE) ?: -1L
            val lastModified = cursor.getLongOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: -1L
            if (size < 0L && lastModified < 0L) {
                "unknown"
            } else {
                "$size:$lastModified"
            }
        } ?: "unknown"
    }.getOrDefault("unknown")
}

private fun Cursor.getLongOrNull(
    columnName: String,
): Long? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

private fun ByteArray.hasJpegSignature(): Boolean {
    return size >= 3 &&
        this[0] == 0xFF.toByte() &&
        this[1] == 0xD8.toByte() &&
        this[2] == 0xFF.toByte()
}

private data class LocalPageByteRequestKey(
    val cacheKey: String,
    val generation: Int,
)

private data class LocalInFlightByteRequest(
    val priority: ComicPageLoadPriority,
    val deferred: Deferred<ByteArray?>,
)

private data class LocalPreviewPrefetchJob(
    val priorityPageIndex: Int,
    val job: Job,
)

private fun MutableStateFlow<Map<String, Long>>.increment(
    key: String,
) {
    update { versions ->
        versions + (key to ((versions[key] ?: 0L) + 1L))
    }
}

private fun IntRange.sortedByPriority(
    priorityPageIndex: Int?,
): List<Int> {
    val priority = priorityPageIndex ?: return toList()
    return sortedWith(
        compareBy<Int> { pageIndex -> kotlin.math.abs(pageIndex - priority) }
            .thenBy { pageIndex -> pageIndex },
    )
}

private fun ComicPageLoadResult.traceLabel(): String = when (this) {
    is ComicPageLoadResult.Success -> "success"
    is ComicPageLoadResult.Failure -> "failure"
}
