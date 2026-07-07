package com.example.unifiedreader.feature.reader.comics

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComicPageImageDecodersTest {
    @Test
    fun `sample size caps long edge`() {
        val options = ComicPageImageDecodeOptions(
            maxLongEdge = 4096,
            maxPixelCount = 16_000_000,
        )

        assertEquals(
            4,
            comicPageBitmapSampleSize(
                width = 9000,
                height = 1000,
                options = options,
            ),
        )
    }

    @Test
    fun `sample size caps pixel count`() {
        val options = ComicPageImageDecodeOptions(
            maxLongEdge = 10_000,
            maxPixelCount = 4_000_000,
        )

        assertEquals(
            4,
            comicPageBitmapSampleSize(
                width = 6000,
                height = 6000,
                options = options,
            ),
        )
    }

    @Test
    fun `decode downsamples large bitmap`() {
        val bytes = encodedBitmap(
            width = 9000,
            height = 1000,
        )

        val decoded = decodeComicPageImage(
            bytes = bytes,
            options = ComicPageImageDecodeOptions(
                maxLongEdge = 4096,
                maxPixelCount = 16_000_000,
            ),
        )

        assertNotNull(decoded)
        val image = requireNotNull(decoded).image
        assertTrue(image.width <= 4096)
        assertTrue(image.height <= 4096)
    }

    @Test
    fun `decode preview uses smaller target bounds`() {
        val bytes = encodedBitmap(
            width = 2400,
            height = 3600,
        )

        val decoded = decodeComicPagePreview(
            bytes = bytes,
            options = ComicPagePreviewOptions(
                targetLongEdge = 220,
                maxPixelCount = 90_000,
            ),
        )

        assertNotNull(decoded)
        val image = requireNotNull(decoded).image
        assertTrue(image.width <= 300)
        assertTrue(image.height <= 300)
    }

    @Test
    fun `bitmap cache sizing is byte based and bounded`() {
        assertEquals(
            16 * 1024 * 1024,
            ComicPageLoaderCacheSizing.bitmapCacheBytes(maxMemoryBytes = 8L * 1024 * 1024),
        )
        assertEquals(
            96 * 1024 * 1024,
            ComicPageLoaderCacheSizing.bitmapCacheBytes(maxMemoryBytes = 2L * 1024 * 1024 * 1024),
        )
    }

    @Test
    fun `preview cache sizing is byte based and bounded`() {
        assertEquals(
            4 * 1024 * 1024,
            ComicPageLoaderCacheSizing.previewCacheBytes(maxMemoryBytes = 8L * 1024 * 1024),
        )
        assertEquals(
            24 * 1024 * 1024,
            ComicPageLoaderCacheSizing.previewCacheBytes(maxMemoryBytes = 2L * 1024 * 1024 * 1024),
        )
    }

    @Test
    fun `page byte cache sizing is byte based and bounded`() {
        assertEquals(
            8 * 1024 * 1024,
            ComicPageLoaderCacheSizing.pageByteCacheBytes(maxMemoryBytes = 8L * 1024 * 1024),
        )
        assertEquals(
            32 * 1024 * 1024,
            ComicPageLoaderCacheSizing.pageByteCacheBytes(maxMemoryBytes = 2L * 1024 * 1024 * 1024),
        )
    }

    private fun encodedBitmap(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }.also {
            bitmap.recycle()
        }
    }
}
