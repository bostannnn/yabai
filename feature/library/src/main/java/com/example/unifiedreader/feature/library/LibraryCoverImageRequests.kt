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
    variant: LibraryCoverImageVariant,
): ImageRequest {
    val requestSize = libraryCoverImageRequestSize(variant)
    return remember(context, imageUrl, requestSize, variant) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(requestSize.width, requestSize.height)
            .precision(Precision.INEXACT)
            .scale(Scale.FILL)
            .build()
    }
}

internal fun libraryCoverImageRequestSize(
    variant: LibraryCoverImageVariant,
): IntSize = when (variant) {
    LibraryCoverImageVariant.Grid -> IntSize(240, 360)
    LibraryCoverImageVariant.Detailed -> IntSize(320, 480)
    LibraryCoverImageVariant.ShelfPreview,
    LibraryCoverImageVariant.ShelfRow -> IntSize(144, 216)
}
