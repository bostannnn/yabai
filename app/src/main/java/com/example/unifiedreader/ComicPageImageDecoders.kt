package com.example.unifiedreader

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata

internal fun decodeComicPageMetadata(
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

internal fun decodeComicPageImage(
    bytes: ByteArray,
): ComicPageLoadResult.Success? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    ?.asImageBitmap()
    ?.also { image -> image.prepareToDraw() }
    ?.let(ComicPageLoadResult::Success)
