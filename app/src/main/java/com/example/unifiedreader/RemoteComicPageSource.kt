package com.example.unifiedreader

import android.util.LruCache
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.candidateIndexes
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

abstract class RemoteComicPageSource<Session : StoredSourceSession>(
    private val sourceType: SourceType,
    private val supportedFormats: Set<FormatType>,
    private val missingSessionMessage: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : ComicPageSource {
    private val pageBitmapCache = LruCache<String, ComicPageLoadResult.Success>(
        ComicPageLoaderCacheSizing.BITMAP_CACHE_ENTRIES,
    )
    private val pageMetadataCache = LruCache<String, ComicPageMetadata>(
        ComicPageLoaderCacheSizing.METADATA_CACHE_ENTRIES,
    )
    private val pageBytesCache = LruCache<String, ByteArray>(
        ComicPageLoaderCacheSizing.BYTE_CACHE_ENTRIES,
    )
    private val cacheGeneration = AtomicInteger(0)
    private val inFlightByteRequests = mutableMapOf<RemoteComicPageByteRequestKey, Deferred<ByteArray?>>()
    private val byteRequestLock = Any()
    private val visibleLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.VISIBLE_LOAD_CONCURRENCY)
    private val metadataLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.METADATA_LOAD_CONCURRENCY)
    private val prefetchLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.PREFETCH_LOAD_CONCURRENCY)
    private val prefetchJobs = mutableMapOf<String, Job>()
    private val prefetchLock = Any()

    protected abstract fun activeSessionFor(
        item: ReadableItem,
    ): Session?

    protected abstract suspend fun downloadPageBytes(
        session: Session,
        item: ReadableItem,
        page: ComicPageSpec,
    ): ByteArray?

    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> {
        if (item.sourceType != sourceType || item.format !in supportedFormats) return emptyList()
        val pageCount = item.pageCount?.coerceAtLeast(0) ?: return emptyList()
        return List(pageCount) { index ->
            ComicPageSpec(
                id = index.toString(),
                pageIndex = index,
                contentDescription = "${item.title} page ${index + 1}",
            )
        }
    }

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? {
        val session = activeSessionFor(item) ?: return null
        val cacheKey = pageCacheKey(
            baseUrl = session.baseUrl,
            itemId = item.id,
            pageId = page.id,
        )
        val requestGeneration = cacheGeneration.get()
        pageMetadataCache[cacheKey]?.let { return it }
        return withContext(ioDispatcher) {
            metadataLoadSemaphore.withPermit {
                pageMetadataCache[cacheKey]?.let { return@withPermit it }
                val bytes = fetchPageBytes(
                    item = item,
                    page = page,
                    cacheKey = cacheKey,
                    session = session,
                ) ?: return@withPermit null
                decodeComicPageMetadata(bytes)?.also { metadata ->
                    if (cacheGeneration.get() == requestGeneration) {
                        pageMetadataCache.put(cacheKey, metadata)
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
            itemId = item.id,
            pageId = page.id,
        )
        val requestGeneration = cacheGeneration.get()
        pageBitmapCache[cacheKey]?.let { return it }
        val result = withContext(ioDispatcher) {
            semaphoreFor(priority).withPermit {
                pageBitmapCache[cacheKey]?.let { return@withPermit it }
                val bytes = fetchPageBytes(
                    item = item,
                    page = page,
                    cacheKey = cacheKey,
                    session = session,
                ) ?: return@withPermit ComicPageLoadResult.Failure("Unable to load this page.")
                decodeComicPageMetadata(bytes)?.let { metadata ->
                    if (cacheGeneration.get() == requestGeneration) {
                        pageMetadataCache.put(cacheKey, metadata)
                    }
                }
                decodeComicPageImage(bytes) ?: ComicPageLoadResult.Failure("Unable to decode this page.")
            }
        }
        if (result is ComicPageLoadResult.Success && cacheGeneration.get() == requestGeneration) {
            pageBitmapCache.put(cacheKey, result)
        }
        return result
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
        val targetKeys = candidateIndexes.associateWith { pageIndex ->
            pageCacheKey(
                baseUrl = session.baseUrl,
                itemId = item.id,
                pageId = pages[pageIndex].id,
            )
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
            val page = pages[pageIndex]
            val cacheKey = targetKeys.getValue(pageIndex)
            if (pageBitmapCache.get(cacheKey) != null) {
                return@forEach
            }
            synchronized(prefetchLock) {
                if (prefetchJobs.containsKey(cacheKey)) {
                    return@forEach
                }
                val job = scope.launch {
                    try {
                        loadPageImage(
                            item = item,
                            page = page,
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
            }
        }
    }

    fun clear() {
        cacheGeneration.incrementAndGet()
        pageBitmapCache.evictAll()
        pageMetadataCache.evictAll()
        pageBytesCache.evictAll()
        synchronized(prefetchLock) {
            prefetchJobs.values.forEach(Job::cancel)
            prefetchJobs.clear()
        }
    }

    private suspend fun fetchPageBytes(
        item: ReadableItem,
        page: ComicPageSpec,
        cacheKey: String,
        session: Session,
    ): ByteArray? {
        pageBytesCache[cacheKey]?.let { return it }
        val requestGeneration = cacheGeneration.get()
        val requestKey = RemoteComicPageByteRequestKey(
            cacheKey = cacheKey,
            generation = requestGeneration,
        )
        val request = synchronized(byteRequestLock) {
            pageBytesCache[cacheKey]?.let { cachedBytes ->
                return cachedBytes
            }
            inFlightByteRequests[requestKey] ?: scope.async {
                val bytes = runCatching {
                    val activeSession = activeSessionFor(item) ?: return@runCatching null
                    downloadPageBytes(
                        session = activeSession,
                        item = item,
                        page = page,
                    )
                }.getOrNull()
                if (bytes != null && cacheGeneration.get() == requestGeneration) {
                    synchronized(byteRequestLock) {
                        if (cacheGeneration.get() == requestGeneration) {
                            pageBytesCache.put(cacheKey, bytes)
                        }
                    }
                }
                bytes
            }.also { deferred ->
                inFlightByteRequests[requestKey] = deferred
                deferred.invokeOnCompletion {
                    synchronized(byteRequestLock) {
                        if (inFlightByteRequests[requestKey] === deferred) {
                            inFlightByteRequests.remove(requestKey)
                        }
                    }
                }
            }
        }
        return request.await()
    }

    private fun semaphoreFor(
        priority: ComicPageLoadPriority,
    ): Semaphore = when (priority) {
        ComicPageLoadPriority.Visible -> visibleLoadSemaphore
        ComicPageLoadPriority.Prefetch -> prefetchLoadSemaphore
    }

    private fun pageCacheKey(
        baseUrl: String,
        itemId: String,
        pageId: String,
    ): String = "${baseUrl.trimEnd('/')}::$itemId::$pageId"
}

private data class RemoteComicPageByteRequestKey(
    val cacheKey: String,
    val generation: Int,
)
