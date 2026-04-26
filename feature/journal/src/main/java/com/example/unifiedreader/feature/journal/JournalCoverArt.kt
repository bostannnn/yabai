package com.example.unifiedreader.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.ResolvedLibraryCover

@Composable
internal fun JournalCoverArt(
    item: JournalItemRef,
    modifier: Modifier = Modifier,
    imageVariant: LibraryCoverImageVariant = LibraryCoverImageVariant.ShelfRow,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { readableItem, _ -> readableItem.coverUrl },
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
) {
    val readableItem = item.toReadableItem()
    LaunchedEffect(readableItem.id, imageVariant) {
        onCoverVisible(readableItem, imageVariant)
    }
    ResolvedLibraryCover(
        item = readableItem,
        coverImageUrlProvider = coverImageUrlProvider,
        imageVariant = imageVariant,
        modifier = modifier,
    )
}

internal fun JournalItemRef.toReadableItem(): ReadableItem = ReadableItem(
    id = itemId,
    sourceType = sourceType,
    libraryId = libraryId,
    title = displayTitle,
    format = format,
    kind = itemKind,
    coverUrl = coverUrl,
)
