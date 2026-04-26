package com.example.unifiedreader.feature.library.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LibraryCoverThumbnailGenerator @Inject constructor() {
    suspend fun ensureThumbnail(
        sourceFile: File,
        destinationFile: File,
        variant: LibraryCoverVariant,
    ): File? = withContext(Dispatchers.IO) {
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

        val cropped = decoded.centerCropToAspectRatio(
            targetWidth = variant.width,
            targetHeight = variant.height,
        )
        val scaled = if (cropped.width == variant.width && cropped.height == variant.height) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, variant.width, variant.height, true)
        }
        if (cropped !== decoded) {
            decoded.recycle()
        }

        try {
            writeCoverCacheFile(
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

    private fun Bitmap.centerCropToAspectRatio(
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        val sourceRatio = width.toFloat() / height.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val cropWidth: Int
        val cropHeight: Int
        val cropX: Int
        val cropY: Int
        if (sourceRatio > targetRatio) {
            cropHeight = height
            cropWidth = (height * targetRatio).toInt()
            cropX = ((width - cropWidth) / 2).coerceAtLeast(0)
            cropY = 0
        } else {
            cropWidth = width
            cropHeight = (width / targetRatio).toInt()
            cropX = 0
            cropY = ((height - cropHeight) / 2).coerceAtLeast(0)
        }
        return if (cropWidth == width && cropHeight == height) {
            this
        } else {
            Bitmap.createBitmap(this, cropX, cropY, cropWidth, cropHeight)
        }
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
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
