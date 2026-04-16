package com.example.unifiedreader.feature.library

import android.content.Context
import android.graphics.Bitmap
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
    val decodePolicy = libraryCoverImageDecodePolicy(variant)
    return remember(context, imageUrl, requestSize, decodePolicy) {
        val builder = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(requestSize.width, requestSize.height)
            .precision(Precision.INEXACT)
            .scale(Scale.FILL)
            .allowHardware(decodePolicy.allowHardware)
        decodePolicy.bitmapConfig?.let(builder::bitmapConfig)
        builder.build()
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

internal data class LibraryCoverImageDecodePolicy(
    val allowHardware: Boolean,
    val bitmapConfig: Bitmap.Config?,
)

internal fun libraryCoverImageDecodePolicy(
    variant: LibraryCoverImageVariant,
): LibraryCoverImageDecodePolicy = when (variant) {
    LibraryCoverImageVariant.Grid,
    LibraryCoverImageVariant.ShelfPreview,
    LibraryCoverImageVariant.ShelfRow -> LibraryCoverImageDecodePolicy(
        allowHardware = false,
        bitmapConfig = Bitmap.Config.RGB_565,
    )
    LibraryCoverImageVariant.Detailed -> LibraryCoverImageDecodePolicy(
        allowHardware = true,
        bitmapConfig = null,
    )
}
