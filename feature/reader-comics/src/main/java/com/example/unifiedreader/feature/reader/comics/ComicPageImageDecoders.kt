package com.example.unifiedreader.feature.reader.comics

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

data class ComicPageImageDecodeOptions(
    val maxLongEdge: Int = 4096,
    val maxPixelCount: Int = 16_000_000,
) {
    init {
        require(maxLongEdge > 0) { "maxLongEdge must be > 0" }
        require(maxPixelCount > 0) { "maxPixelCount must be > 0" }
    }
}

object ComicPageLoaderCacheSizing {
    const val METADATA_CACHE_ENTRIES = 256
    const val BYTE_CACHE_ENTRIES = 12
    const val VISIBLE_LOAD_CONCURRENCY = 3
    const val METADATA_LOAD_CONCURRENCY = 3
    const val PREFETCH_LOAD_CONCURRENCY = 2
    const val PREVIEW_LOAD_CONCURRENCY = 2
    const val PREVIEW_PREFETCH_CONCURRENCY = 1

    fun bitmapCacheBytes(
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
    ): Int {
        val targetBytes = maxMemoryBytes / BITMAP_CACHE_MEMORY_DIVISOR
        return targetBytes
            .coerceIn(MIN_BITMAP_CACHE_BYTES.toLong(), MAX_BITMAP_CACHE_BYTES.toLong())
            .toInt()
    }

    fun previewCacheBytes(
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
    ): Int {
        val targetBytes = maxMemoryBytes / PREVIEW_CACHE_MEMORY_DIVISOR
        return targetBytes
            .coerceIn(MIN_PREVIEW_CACHE_BYTES.toLong(), MAX_PREVIEW_CACHE_BYTES.toLong())
            .toInt()
    }

    fun previewByteCacheBytes(
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
    ): Int {
        val targetBytes = maxMemoryBytes / PREVIEW_BYTE_CACHE_MEMORY_DIVISOR
        return targetBytes
            .coerceIn(MIN_PREVIEW_BYTE_CACHE_BYTES.toLong(), MAX_PREVIEW_BYTE_CACHE_BYTES.toLong())
            .toInt()
    }
}

fun comicPageBitmapCacheEntryBytes(
    result: ComicPageLoadResult.Success,
): Int = (result.image.width.toLong() * result.image.height.toLong() * BYTES_PER_ARGB_8888_PIXEL)
    .coerceAtMost(Int.MAX_VALUE.toLong())
    .toInt()

fun comicPagePreviewCacheEntryBytes(
    result: ComicPagePreviewResult.Success,
): Int = (result.image.width.toLong() * result.image.height.toLong() * BYTES_PER_ARGB_8888_PIXEL)
    .coerceAtMost(Int.MAX_VALUE.toLong())
    .toInt()

fun decodeComicPageMetadata(
    bytes: ByteArray,
): ComicPageMetadata? {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val width = options.outWidth
    val height = options.outHeight
    return if (width > 0 && height > 0) {
        ComicPageMetadata(
            width = width,
            height = height,
        )
    } else {
        null
    }
}

fun decodeComicPagePreview(
    bytes: ByteArray,
    options: ComicPagePreviewOptions = ComicPagePreviewOptions(),
): ComicPagePreviewResult.Success? {
    val bounds = decodeComicPageMetadata(bytes) ?: return null
    val bitmapOptions = BitmapFactory.Options().apply {
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        inSampleSize = comicPagePreviewSampleSize(
            width = bounds.width,
            height = bounds.height,
            options = options,
        )
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bitmapOptions)
        ?.asImageBitmap()
        ?.also { image -> image.prepareToDraw() }
        ?.let(ComicPagePreviewResult::Success)
}

fun decodeComicPagePreviewBytes(
    bytes: ByteArray,
    options: ComicPagePreviewOptions = ComicPagePreviewOptions(),
): ByteArray? {
    val bounds = decodeComicPageMetadata(bytes) ?: return null
    val bitmapOptions = BitmapFactory.Options().apply {
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        inSampleSize = comicPagePreviewSampleSize(
            width = bounds.width,
            height = bounds.height,
            options = options,
        )
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bitmapOptions) ?: return null
    return ByteArrayOutputStream().use { output ->
        if (bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, PREVIEW_JPEG_QUALITY, output)) {
            output.toByteArray().takeIf(ByteArray::isNotEmpty)
        } else {
            null
        }
    }
}

fun decodeComicPageImage(
    bytes: ByteArray,
    options: ComicPageImageDecodeOptions = ComicPageImageDecodeOptions(),
): ComicPageLoadResult.Success? {
    val bounds = decodeComicPageMetadata(bytes) ?: return null
    val bitmapOptions = BitmapFactory.Options().apply {
        inSampleSize = comicPageBitmapSampleSize(
            width = bounds.width,
            height = bounds.height,
            options = options,
        )
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bitmapOptions)
        ?.asImageBitmap()
        ?.also { image -> image.prepareToDraw() }
        ?.let(ComicPageLoadResult::Success)
}

internal fun comicPagePreviewSampleSize(
    width: Int,
    height: Int,
    options: ComicPagePreviewOptions,
): Int = comicPageBitmapSampleSize(
    width = width,
    height = height,
    options = ComicPageImageDecodeOptions(
        maxLongEdge = options.targetLongEdge,
        maxPixelCount = options.maxPixelCount,
    ),
)

internal fun comicPageBitmapSampleSize(
    width: Int,
    height: Int,
    options: ComicPageImageDecodeOptions,
): Int {
    if (width <= 0 || height <= 0) return 1
    var sampleSize = 1
    while (sampledLongEdge(width, height, sampleSize) > options.maxLongEdge ||
        sampledPixelCount(width, height, sampleSize) > options.maxPixelCount
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun sampledLongEdge(
    width: Int,
    height: Int,
    sampleSize: Int,
): Int = maxOf(
    ceil(width / sampleSize.toDouble()).toInt(),
    ceil(height / sampleSize.toDouble()).toInt(),
)

private fun sampledPixelCount(
    width: Int,
    height: Int,
    sampleSize: Int,
): Int {
    val sampledWidth = ceil(width / sampleSize.toDouble()).toLong()
    val sampledHeight = ceil(height / sampleSize.toDouble()).toLong()
    return (sampledWidth * sampledHeight).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private const val BITMAP_CACHE_MEMORY_DIVISOR = 8
private const val PREVIEW_CACHE_MEMORY_DIVISOR = 32
private const val PREVIEW_BYTE_CACHE_MEMORY_DIVISOR = 64
private const val MIN_BITMAP_CACHE_BYTES = 16 * 1024 * 1024
private const val MAX_BITMAP_CACHE_BYTES = 96 * 1024 * 1024
private const val MIN_PREVIEW_CACHE_BYTES = 4 * 1024 * 1024
private const val MAX_PREVIEW_CACHE_BYTES = 24 * 1024 * 1024
private const val MIN_PREVIEW_BYTE_CACHE_BYTES = 2 * 1024 * 1024
private const val MAX_PREVIEW_BYTE_CACHE_BYTES = 16 * 1024 * 1024
private const val BYTES_PER_ARGB_8888_PIXEL = 4
private const val PREVIEW_JPEG_QUALITY = 82
