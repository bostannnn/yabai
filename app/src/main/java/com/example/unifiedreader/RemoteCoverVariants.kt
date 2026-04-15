package com.example.unifiedreader

import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.LibraryCoverVariant

internal fun LibraryCoverImageVariant.toCoverAssetVariant(): LibraryCoverVariant = when (this) {
    LibraryCoverImageVariant.Grid -> LibraryCoverVariant.Grid
    LibraryCoverImageVariant.Detailed -> LibraryCoverVariant.Detail
    LibraryCoverImageVariant.ShelfPreview,
    LibraryCoverImageVariant.ShelfRow -> LibraryCoverVariant.Shelf
}
