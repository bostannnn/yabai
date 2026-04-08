package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class LocalComicPageLoader(
    private val context: Context,
    private val comicArchiveCodecs: List<ComicArchiveCodec>,
    private val scope: CoroutineScope,
) {
    private val pageBitmapCache = LruCache<String, ComicPageLoadResult.Success>(12)
    private val prefetchInFlight = mutableSetOf<String>()
    private val prefetchLock = Any()

    suspend fun loadPageImage(
        item: ReadableItem,
        record: LocalDocumentRecord,
        entryName: String,
    ): ComicPageLoadResult {
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format }
            ?: return ComicPageLoadResult.Failure("Unsupported archive format.")
        pageBitmapCache[pageCacheKey(item.id, entryName)]?.let { return it }
        val loadedResult = decodePageImage(
            record = record,
            codec = codec,
            entryName = entryName,
        )
        if (loadedResult is ComicPageLoadResult.Success) {
            pageBitmapCache.put(pageCacheKey(item.id, entryName), loadedResult)
        }
        return loadedResult
    }

    fun prefetchAround(
        item: ReadableItem,
        record: LocalDocumentRecord,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        if (pageCount <= 1) return
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format } ?: return
        val candidateIndexes = buildList {
            for (offset in 1..4) {
                add(settledPageIndex + offset)
            }
            for (offset in 1..4) {
                add(settledPageIndex - offset)
            }
        }.filter { it in 0 until minOf(pageCount, record.pageEntryNames.size) }

        candidateIndexes.forEach { pageIndex ->
            val entryName = record.pageEntryNames[pageIndex]
            val cacheKey = pageCacheKey(item.id, entryName)
            if (pageBitmapCache.get(cacheKey) != null) {
                return@forEach
            }
            val shouldLaunch = synchronized(prefetchLock) {
                prefetchInFlight.add(cacheKey)
            }
            if (!shouldLaunch) {
                return@forEach
            }
            scope.launch {
                try {
                    val result = decodePageImage(
                        record = record,
                        codec = codec,
                        entryName = entryName,
                    )
                    if (result is ComicPageLoadResult.Success) {
                        pageBitmapCache.put(cacheKey, result)
                    }
                } finally {
                    synchronized(prefetchLock) {
                        prefetchInFlight.remove(cacheKey)
                    }
                }
            }
        }
    }

    fun clear(itemId: String) {
        pageBitmapCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(pageBitmapCache::remove)
        synchronized(prefetchLock) {
            prefetchInFlight.removeAll { it.startsWith("$itemId::") }
        }
    }

    private suspend fun decodePageImage(
        record: LocalDocumentRecord,
        codec: ComicArchiveCodec,
        entryName: String,
    ): ComicPageLoadResult {
        return runCatching {
            val image = codec.decodePageImage(
                context = context,
                uri = Uri.parse(record.uri),
                entryName = entryName,
            )
            if (image != null) {
                ComicPageLoadResult.Success(image)
            } else {
                ComicPageLoadResult.Failure("Unable to decode this page.")
            }
        }.getOrElse { throwable ->
            Log.w(LOG_TAG, "Failed to decode page $entryName for ${record.displayName}", throwable)
            ComicPageLoadResult.Failure("Unable to decode this page.")
        }
    }

    private fun pageCacheKey(
        itemId: String,
        entryName: String,
    ): String = "$itemId::$entryName"

    private companion object {
        const val LOG_TAG = "ImportedComicArchive"
    }
}
