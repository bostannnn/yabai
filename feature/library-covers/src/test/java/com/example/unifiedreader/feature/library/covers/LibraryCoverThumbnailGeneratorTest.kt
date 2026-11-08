package com.example.unifiedreader.feature.library.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.common.AppDispatchers
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryCoverThumbnailGeneratorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `ensureThumbnail scales a 2 by 3 cover to the variant`() = runTest {
        val decoded = generate(sourceWidth = 1000, sourceHeight = 1500, variant = LibraryCoverVariant.Grid)

        assertEquals(LibraryCoverVariant.Grid.width, decoded.width)
        assertEquals(LibraryCoverVariant.Grid.height, decoded.height)
    }

    @Test
    fun `ensureThumbnail keeps a wider cover's proportions instead of cropping it`() = runTest {
        // A squarer cover (4:5) fits the variant's width and comes out shorter than the slot: no centre crop to 2:3.
        val decoded = generate(sourceWidth = 1200, sourceHeight = 1500, variant = LibraryCoverVariant.GridLarge)

        assertEquals(LibraryCoverVariant.GridLarge.width, decoded.width)
        assertEquals(900, decoded.height)
    }

    @Test
    fun `ensureThumbnail keeps a taller cover's proportions instead of cropping it`() = runTest {
        // A tall cover (1:2) fits the variant's height and comes out narrower than the slot.
        val decoded = generate(sourceWidth = 1600, sourceHeight = 3200, variant = LibraryCoverVariant.GridExtraLarge)

        assertEquals(LibraryCoverVariant.GridExtraLarge.height, decoded.height)
        assertEquals(720, decoded.width)
    }

    @Test
    fun `ensureThumbnail keeps a smaller cover as it is`() = runTest {
        // The shape and size of a Grimmory thumbnail: neither enlarged nor cropped.
        val decoded = generate(sourceWidth = 250, sourceHeight = 350, variant = LibraryCoverVariant.GridLarge)

        assertEquals(250, decoded.width)
        assertEquals(350, decoded.height)
    }

    private suspend fun generate(
        sourceWidth: Int,
        sourceHeight: Int,
        variant: LibraryCoverVariant,
    ): Bitmap {
        val source = File(context.cacheDir, "thumbnail-source-$sourceWidth-$sourceHeight.png")
        val destination = File(context.cacheDir, "thumbnail-dest-$sourceWidth-$sourceHeight-${variant.fileKey}.jpg")
        destination.delete()
        val bitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }

        val output = LibraryCoverThumbnailGenerator(AppDispatchers()).ensureThumbnail(
            sourceFile = source,
            destinationFile = destination,
            variant = variant,
        )

        assertTrue(output?.isFile == true)
        return BitmapFactory.decodeFile(destination.absolutePath)
    }
}
