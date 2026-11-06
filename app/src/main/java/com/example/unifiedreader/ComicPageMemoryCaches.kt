package com.example.unifiedreader

import android.util.LruCache
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageLoaderCacheSizing
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.comicPageBitmapCacheEntryBytes
import com.example.unifiedreader.feature.reader.comics.comicPagePreviewCacheEntryBytes
import kotlinx.coroutines.sync.Semaphore

/**
 * The memory caches and load limits of one comic page loader: decoded pages, page metadata, decoded previews,
 * and the raw page and preview bytes, each sized by [ComicPageLoaderCacheSizing]. The local-file loader and
 * the server page sources each hold one; what differs between them is only how a page is keyed and fetched.
 */
internal class ComicPageMemoryCaches {
    val pageBitmapCache = object : LruCache<String, ComicPageLoadResult.Success>(
        ComicPageLoaderCacheSizing.bitmapCacheBytes(),
    ) {
        override fun sizeOf(
            key: String,
            value: ComicPageLoadResult.Success,
        ): Int = comicPageBitmapCacheEntryBytes(value)
    }
    val pageMetadataCache = LruCache<String, ComicPageMetadata>(
        ComicPageLoaderCacheSizing.METADATA_CACHE_ENTRIES,
    )
    val pagePreviewCache = object : LruCache<String, ComicPagePreviewResult.Success>(
        ComicPageLoaderCacheSizing.previewCacheBytes(),
    ) {
        override fun sizeOf(
            key: String,
            value: ComicPagePreviewResult.Success,
        ): Int = comicPagePreviewCacheEntryBytes(value)
    }
    val pageBytesCache = byteCache(ComicPageLoaderCacheSizing.pageByteCacheBytes())
    val previewBytesCache = byteCache(ComicPageLoaderCacheSizing.previewByteCacheBytes())

    val metadataLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.METADATA_LOAD_CONCURRENCY)
    val previewLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.PREVIEW_LOAD_CONCURRENCY)
    val previewPrefetchSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.PREVIEW_PREFETCH_CONCURRENCY)
    private val visibleLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.VISIBLE_LOAD_CONCURRENCY)
    private val prefetchLoadSemaphore = Semaphore(permits = ComicPageLoaderCacheSizing.PREFETCH_LOAD_CONCURRENCY)

    /** Visible pages and prefetches have separate limits, so a prefetch never holds up the page on screen. */
    fun semaphoreFor(
        priority: ComicPageLoadPriority,
    ): Semaphore = when (priority) {
        ComicPageLoadPriority.Visible -> visibleLoadSemaphore
        ComicPageLoadPriority.Prefetch -> prefetchLoadSemaphore
    }

    fun evictAll() {
        pageBitmapCache.evictAll()
        pageMetadataCache.evictAll()
        pagePreviewCache.evictAll()
        pageBytesCache.evictAll()
        previewBytesCache.evictAll()
    }

    /** Removes every entry whose cache key matches [predicate], from all five caches. */
    fun evictKeys(predicate: (String) -> Boolean) {
        listOf(pageBitmapCache, pageMetadataCache, pagePreviewCache, pageBytesCache, previewBytesCache)
            .forEach { cache -> cache.removeKeys(predicate) }
    }

    /**
     * A memory-pressure trim: previews and raw bytes go, and decoded pages go too except those [retainPage]
     * keeps (the pages around the one on screen). With no [retainPage], every decoded page goes.
     */
    fun trimForMemory(retainPage: ((String) -> Boolean)?) {
        pagePreviewCache.evictAll()
        previewBytesCache.evictAll()
        pageBytesCache.evictAll()
        if (retainPage == null) {
            pageBitmapCache.evictAll()
        } else {
            pageBitmapCache.removeKeys { cacheKey -> !retainPage(cacheKey) }
        }
    }
}

private fun byteCache(maxBytes: Int) = object : LruCache<String, ByteArray>(maxBytes) {
    override fun sizeOf(
        key: String,
        value: ByteArray,
    ): Int = value.size
}

private fun <V : Any> LruCache<String, V>.removeKeys(predicate: (String) -> Boolean) {
    snapshot().keys.filter(predicate).forEach(::remove)
}
