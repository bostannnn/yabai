package com.example.unifiedreader.feature.library

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

enum class LibraryCoverImageVariant {
    Grid,
    Detailed,
    ShelfPreview,
    ShelfRow,
}

@Composable
fun rememberLibraryCoverImageRequest(
    context: Context,
    imageUrl: String,
    targetSize: IntSize,
    variant: LibraryCoverImageVariant,
): ImageRequest? {
    val bucketedSize = bucketedCoverSize(
        targetSize = targetSize,
        variant = variant,
    )
        ?: return null
    return remember(context, imageUrl, bucketedSize, variant) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(bucketedSize.width, bucketedSize.height)
            .precision(Precision.INEXACT)
            .scale(Scale.FILL)
            .build()
    }
}

internal fun bucketedCoverSize(
    targetSize: IntSize,
    variant: LibraryCoverImageVariant,
): IntSize? {
    if (targetSize.width <= 0 || targetSize.height <= 0) return null
    val step = when (variant) {
        LibraryCoverImageVariant.Grid -> 64
        LibraryCoverImageVariant.Detailed -> 48
        LibraryCoverImageVariant.ShelfPreview -> 48
        LibraryCoverImageVariant.ShelfRow -> 32
    }
    return IntSize(
        width = bucketCoverDimension(targetSize.width, step),
        height = bucketCoverDimension(targetSize.height, step),
    )
}

internal fun bucketCoverDimension(
    rawSize: Int,
    step: Int,
): Int {
    if (rawSize <= 0) return 0
    val roundedUp = ((rawSize + step - 1) / step) * step
    return roundedUp.coerceAtLeast(step)
}
