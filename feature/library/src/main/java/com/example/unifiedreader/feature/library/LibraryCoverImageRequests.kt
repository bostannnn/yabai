package com.example.unifiedreader.feature.library

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import kotlin.math.ceil
import kotlin.math.max

enum class LibraryCoverImageVariant {
    Grid,
    GridLarge,
    GridExtraLarge,
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
    val density = LocalDensity.current.density
    val requestSize = libraryCoverImageRequestSize(variant, density)
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
    density: Float = 1f,
): IntSize {
    val minimumSize = libraryCoverImageMinimumRequestSize(variant)
    val densitySize = libraryCoverImageLogicalSize(variant).scaleBy(density)
    return IntSize(
        width = max(minimumSize.width, densitySize.width),
        height = max(minimumSize.height, densitySize.height),
    )
}

private fun libraryCoverImageMinimumRequestSize(
    variant: LibraryCoverImageVariant,
): IntSize = when (variant) {
    LibraryCoverImageVariant.Grid -> IntSize(480, 720)
    LibraryCoverImageVariant.GridLarge -> IntSize(720, 1080)
    LibraryCoverImageVariant.GridExtraLarge -> IntSize(960, 1440)
    LibraryCoverImageVariant.Detailed -> IntSize(960, 1440)
    LibraryCoverImageVariant.ShelfPreview,
    LibraryCoverImageVariant.ShelfRow -> IntSize(432, 648)
}

private fun libraryCoverImageLogicalSize(
    variant: LibraryCoverImageVariant,
): IntSize = when (variant) {
    LibraryCoverImageVariant.Grid -> IntSize(160, 240)
    LibraryCoverImageVariant.GridLarge -> IntSize(180, 270)
    LibraryCoverImageVariant.GridExtraLarge -> IntSize(240, 360)
    LibraryCoverImageVariant.Detailed -> IntSize(320, 480)
    LibraryCoverImageVariant.ShelfPreview,
    LibraryCoverImageVariant.ShelfRow -> IntSize(144, 216)
}

private fun IntSize.scaleBy(
    density: Float,
): IntSize {
    val safeDensity = density.takeIf { it > 0f } ?: 1f
    return IntSize(
        width = ceil(width * safeDensity).toInt(),
        height = ceil(height * safeDensity).toInt(),
    )
}

internal data class LibraryCoverImageDecodePolicy(
    val allowHardware: Boolean,
    val bitmapConfig: Bitmap.Config?,
)

internal fun libraryCoverImageDecodePolicy(
    variant: LibraryCoverImageVariant,
): LibraryCoverImageDecodePolicy = when (variant) {
    LibraryCoverImageVariant.Grid,
    LibraryCoverImageVariant.GridLarge,
    LibraryCoverImageVariant.GridExtraLarge -> LibraryCoverImageDecodePolicy(
        allowHardware = true,
        bitmapConfig = null,
    )
    LibraryCoverImageVariant.ShelfPreview,
    LibraryCoverImageVariant.ShelfRow -> LibraryCoverImageDecodePolicy(
        allowHardware = true,
        bitmapConfig = null,
    )
    LibraryCoverImageVariant.Detailed -> LibraryCoverImageDecodePolicy(
        allowHardware = true,
        bitmapConfig = null,
    )
}
