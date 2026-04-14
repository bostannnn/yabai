package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.candidateIndexes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal class LocalComicPageLoader(
    private val context: Context,
    private val comicArchiveCodecs: List<ComicArchiveCodec>,
    private val scope: CoroutineScope,
) {
    private val pageBitmapCache = LruCache<String, ComicPageLoadResult.Success>(
        ComicPageLoaderCacheSizing.BITMAP_CACHE_ENTRIES,
    )
    private val pageMetadataCache = LruCache<String, ComicPageMetadata>(
        ComicPageLoaderCacheSizing.METADATA_CACHE_ENTRIES,
    )
    private val pageBytesCache = LruCache<String, ByteArray>(
        ComicPageLoaderCacheSizing.BYTE_CACHE_ENTRIES,
    )
    private val cacheGenerationByItemId = mutableMapOf<String, Int>()
    private val inFlightByteRequests = mutableMapOf<LocalPageByteRequestKey, Deferred<ByteArray?>>()
    private val byteRequestLock = Any()
    private val visibleLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.VISIBLE_LOAD_CONCURRENCY)
    private val metadataLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.METADATA_LOAD_CONCURRENCY)
    private val prefetchLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.PREFETCH_LOAD_CONCURRENCY)
    private val prefetchJobs = mutableMapOf<String, Job>()
    private val prefetchLock = Any()

    suspend fun loadPageMetadata(
        item: ReadableItem,
        record: LocalDocumentRecord,
        entryName: String,
    ): ComicPageMetadata? {
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format } ?: return null
        val cacheKey = pageCacheKey(item.id, entryName)
        val requestGeneration = generationFor(item.id)
        pageMetadataCache[cacheKey]?.let { return it }
        return withContext(Dispatchers.IO) {
            metadataLoadSemaphore.withPermit {
                pageMetadataCache[cacheKey]?.let { return@withPermit it }
                val bytes = loadEntryBytes(
                    itemId = item.id,
                    cacheKey = cacheKey,
                    requestGeneration = requestGeneration,
                    record = record,
                    codec = codec,
                    entryName = entryName,
                ) ?: return@withPermit null
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
        val cacheKey = pageCacheKey(item.id, entryName)
        val requestGeneration = generationFor(item.id)
        pageBitmapCache[cacheKey]?.let { return it }
        val loadedResult = withContext(Dispatchers.IO) {
            semaphoreFor(priority).withPermit {
                pageBitmapCache[cacheKey]?.let { return@withPermit it }
                decodePageImage(
                    itemId = item.id,
                    record = record,
                    codec = codec,
                    entryName = entryName,
                    cacheKey = cacheKey,
                    requestGeneration = requestGeneration,
                )
            }
        }
        if (loadedResult is ComicPageLoadResult.Success && generationFor(item.id) == requestGeneration) {
            pageBitmapCache.put(cacheKey, loadedResult)
        }
        return loadedResult
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
            pageCacheKey(item.id, record.pageEntryNames[pageIndex])
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

    fun clear(itemId: String) {
        synchronized(byteRequestLock) {
            cacheGenerationByItemId[itemId] = generationFor(itemId) + 1
            inFlightByteRequests.keys
                .filter { key -> key.cacheKey.startsWith("$itemId::") }
                .forEach(inFlightByteRequests::remove)
        }
        pageBitmapCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(pageBitmapCache::remove)
        pageMetadataCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(pageMetadataCache::remove)
        pageBytesCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(pageBytesCache::remove)
        synchronized(prefetchLock) {
            prefetchJobs.entries
                .filter { (cacheKey, _) -> cacheKey.startsWith("$itemId::") }
                .toList()
                .forEach { (cacheKey, job) ->
                    job.cancel()
                    prefetchJobs.remove(cacheKey)
                }
        }
    }

    private suspend fun decodePageImage(
        itemId: String,
        record: LocalDocumentRecord,
        codec: ComicArchiveCodec,
        entryName: String,
        cacheKey: String,
        requestGeneration: Int,
    ): ComicPageLoadResult {
        return runCatching {
            val bytes = loadEntryBytes(
                itemId = itemId,
                cacheKey = cacheKey,
                requestGeneration = requestGeneration,
                record = record,
                codec = codec,
                entryName = entryName,
            ) ?: return@runCatching ComicPageLoadResult.Failure("Unable to decode this page.")
            decodeComicPageMetadata(bytes)?.let { metadata ->
                if (generationFor(itemId) == requestGeneration) {
                    pageMetadataCache.put(cacheKey, metadata)
                }
            }
            decodeComicPageImage(bytes) ?: ComicPageLoadResult.Failure("Unable to decode this page.")
        }.getOrElse { throwable ->
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
    ): ByteArray? {
        pageBytesCache[cacheKey]?.let { return it }
        val requestKey = LocalPageByteRequestKey(
            cacheKey = cacheKey,
            generation = requestGeneration,
        )
        val request = synchronized(byteRequestLock) {
            pageBytesCache[cacheKey]?.let { cachedBytes ->
                return cachedBytes
            }
            inFlightByteRequests[requestKey] ?: scope.async(Dispatchers.IO) {
                val bytes = codec.readEntryBytes(
                    context = context,
                    uri = Uri.parse(record.uri),
                    entryName = entryName,
                )
                if (bytes != null && generationFor(itemId) == requestGeneration) {
                    synchronized(byteRequestLock) {
                        if (generationFor(itemId) == requestGeneration) {
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

    private fun pageCacheKey(
        itemId: String,
        entryName: String,
    ): String = "$itemId::$entryName"

    private fun generationFor(
        itemId: String,
    ): Int = synchronized(byteRequestLock) {
        cacheGenerationByItemId[itemId] ?: 0
    }

    private companion object {
        const val LOG_TAG = "ImportedComicArchive"
    }
}

private data class LocalPageByteRequestKey(
    val cacheKey: String,
    val generation: Int,
)
