package com.example.unifiedreader.feature.library.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryCoverThumbnailGeneratorTest {
    @Test
    fun `ensureThumbnail writes fixed size thumbnail`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "thumbnail-source.png")
        val destination = File(context.cacheDir, "thumbnail-dest.jpg")
        val bitmap = Bitmap.createBitmap(900, 1400, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }

        val output = LibraryCoverThumbnailGenerator().ensureThumbnail(
            sourceFile = source,
            destinationFile = destination,
            variant = LibraryCoverVariant.Grid,
        )

        assertTrue(output?.isFile == true)
        val decoded = BitmapFactory.decodeFile(destination.absolutePath)
        assertEquals(LibraryCoverVariant.Grid.width, decoded.width)
        assertEquals(LibraryCoverVariant.Grid.height, decoded.height)
    }

    @Test
    fun `ensureThumbnail writes large grid thumbnail`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "thumbnail-large-source.png")
        val destination = File(context.cacheDir, "thumbnail-large-dest.jpg")
        val bitmap = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }

        val output = LibraryCoverThumbnailGenerator().ensureThumbnail(
            sourceFile = source,
            destinationFile = destination,
            variant = LibraryCoverVariant.GridLarge,
        )

        assertTrue(output?.isFile == true)
        val decoded = BitmapFactory.decodeFile(destination.absolutePath)
        assertEquals(LibraryCoverVariant.GridLarge.width, decoded.width)
        assertEquals(LibraryCoverVariant.GridLarge.height, decoded.height)
    }

    @Test
    fun `ensureThumbnail writes extra large grid thumbnail`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "thumbnail-extra-large-source.png")
        val destination = File(context.cacheDir, "thumbnail-extra-large-dest.jpg")
        val bitmap = Bitmap.createBitmap(1600, 2400, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }

        val output = LibraryCoverThumbnailGenerator().ensureThumbnail(
            sourceFile = source,
            destinationFile = destination,
            variant = LibraryCoverVariant.GridExtraLarge,
        )

        assertTrue(output?.isFile == true)
        val decoded = BitmapFactory.decodeFile(destination.absolutePath)
        assertEquals(LibraryCoverVariant.GridExtraLarge.width, decoded.width)
        assertEquals(LibraryCoverVariant.GridExtraLarge.height, decoded.height)
    }
}
