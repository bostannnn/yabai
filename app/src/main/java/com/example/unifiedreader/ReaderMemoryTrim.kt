package com.example.unifiedreader

import android.content.ComponentCallbacks2
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import kotlin.math.abs

enum class ReaderMemoryTrimMode {
    DropPreviewsAndDistantPages,
    ClearAll,
}

data class ReaderMemoryTrimRequest(
    val mode: ReaderMemoryTrimMode,
    val retention: ReaderPageCacheRetention? = null,
) {
    companion object {
        val ClearAll = ReaderMemoryTrimRequest(
            mode = ReaderMemoryTrimMode.ClearAll,
        )

        fun dropPreviewsAndDistantPages(
            retention: ReaderPageCacheRetention?,
        ): ReaderMemoryTrimRequest = ReaderMemoryTrimRequest(
            mode = ReaderMemoryTrimMode.DropPreviewsAndDistantPages,
            retention = retention,
        )
    }
}

data class ReaderPageCacheRetention(
    val item: ReadableItem,
    val pageIds: Set<String>,
) {
    val itemId: String = item.id

    init {
        require(itemId.isNotBlank()) { "itemId must not be blank" }
    }
}

fun readerMemoryTrimRequestForAndroid(
    level: Int,
    retention: ReaderPageCacheRetention?,
): ReaderMemoryTrimRequest? = when {
    level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> ReaderMemoryTrimRequest.ClearAll
    level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
        ReaderMemoryTrimRequest.dropPreviewsAndDistantPages(retention)
    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> ReaderMemoryTrimRequest.ClearAll
    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ->
        ReaderMemoryTrimRequest.dropPreviewsAndDistantPages(retention)
    else -> null
}

fun readerPageCacheRetentionFor(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    currentPageIndex: Int?,
    radius: Int = ReaderMemoryRetainedPageRadius,
): ReaderPageCacheRetention? {
    if (pages.isEmpty()) return null
    val safeCurrentPage = (currentPageIndex ?: 0).coerceIn(0, pages.lastIndex)
    val retainedPageIds = pages
        .filter { page -> abs(page.pageIndex - safeCurrentPage) <= radius }
        .mapTo(linkedSetOf(), ComicPageSpec::id)
    if (retainedPageIds.isEmpty()) return null
    return ReaderPageCacheRetention(
        item = item,
        pageIds = retainedPageIds,
    )
}

private const val ReaderMemoryRetainedPageRadius = 1
