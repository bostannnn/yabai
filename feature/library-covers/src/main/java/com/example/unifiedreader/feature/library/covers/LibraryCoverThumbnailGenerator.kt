package com.example.unifiedreader.feature.library.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.writeFileAtomically
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/**
 * Writes the cached copy of a downloaded cover at a [LibraryCoverVariant]'s size. The copy keeps the cover's own
 * proportions: it is scaled to fit within the variant, never cropped to 2:3 and never enlarged. Cropping to the
 * grid's slot is the screen's job, and only when covers show "All the same"; "As printed" needs the real shape.
 */
@Singleton
class LibraryCoverThumbnailGenerator @Inject constructor(
    private val appDispatchers: AppDispatchers,
) {
    suspend fun ensureThumbnail(
        sourceFile: File,
        destinationFile: File,
        variant: LibraryCoverVariant,
    ): File? = withContext(appDispatchers.io) {
        if (!sourceFile.isFile || sourceFile.length() <= 0L) return@withContext null
        if (destinationFile.isFile && destinationFile.length() > 0L) return@withContext destinationFile

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val decoded = BitmapFactory.decodeFile(
            sourceFile.absolutePath,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = calculateInSampleSize(
                    sourceWidth = bounds.outWidth,
                    sourceHeight = bounds.outHeight,
                    targetWidth = variant.width,
                    targetHeight = variant.height,
                )
            },
        ) ?: return@withContext null

        // Never enlarge. A cover smaller than the variant keeps its own size and is scaled on screen: the same
        // detail, without a resample and a JPEG pass that only make a small cover look like a large one.
        val scaled = decoded.scaleToFit(
            maxWidth = variant.width,
            maxHeight = variant.height,
        )
        if (scaled !== decoded) {
            decoded.recycle()
        }

        try {
            writeFileAtomically(
                destination = destinationFile,
                targetDescription = "library ${variant.fileKey} thumbnail",
            ) { temporaryFile ->
                temporaryFile.outputStream().use { output ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, output)) {
                        error("Unable to encode ${variant.fileKey} thumbnail.")
                    }
                }
            }
        } finally {
            scaled.recycle()
        }

        destinationFile.takeIf { file -> file.isFile && file.length() > 0L }
    }

    /** This bitmap scaled down to fit within [maxWidth] x [maxHeight] in its own proportions, or itself if it fits. */
    private fun Bitmap.scaleToFit(
        maxWidth: Int,
        maxHeight: Int,
    ): Bitmap {
        if (width <= maxWidth && height <= maxHeight) return this
        val scale = min(maxWidth.toFloat() / width.toFloat(), maxHeight.toFloat() / height.toFloat())
        val scaledWidth = (width * scale).roundToInt().coerceIn(1, maxWidth)
        val scaledHeight = (height * scale).roundToInt().coerceIn(1, maxHeight)
        return Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        // Halve while both sides stay at or above the target, so the fit below only ever scales down a little.
        var sampleSize = 1
        var currentWidth = sourceWidth
        var currentHeight = sourceHeight
        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight) {
            sampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }
}

private const val THUMBNAIL_QUALITY = 95
