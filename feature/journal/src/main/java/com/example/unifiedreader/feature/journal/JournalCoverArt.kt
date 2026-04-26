package com.example.unifiedreader.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCover
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant

@Composable
internal fun JournalCoverArt(
    item: JournalItemRef,
    modifier: Modifier = Modifier,
    imageVariant: LibraryCoverImageVariant = LibraryCoverImageVariant.ShelfRow,
) {
    LibraryCover(
        item = item.toReadableItem(),
        coverUrl = item.coverUrl,
        imageVariant = imageVariant,
        modifier = modifier,
    )
}

private fun JournalItemRef.toReadableItem(): ReadableItem = ReadableItem(
    id = itemId,
    sourceType = sourceType,
    libraryId = libraryId,
    title = displayTitle,
    format = format,
    kind = itemKind,
    coverUrl = coverUrl,
)
