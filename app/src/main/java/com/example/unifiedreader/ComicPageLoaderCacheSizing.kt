package com.example.unifiedreader

internal object ComicPageLoaderCacheSizing {
    const val BITMAP_CACHE_ENTRIES = 24
    const val METADATA_CACHE_ENTRIES = 256
    const val BYTE_CACHE_ENTRIES = 12
    const val VISIBLE_LOAD_CONCURRENCY = 3
    const val METADATA_LOAD_CONCURRENCY = 3
    const val PREFETCH_LOAD_CONCURRENCY = 2
}
