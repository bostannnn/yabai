package com.example.unifiedreader.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem

@Composable
fun VisibleResolvedLibraryCover(
    item: ReadableItem,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    fadeRead: Boolean = false,
    downloadBadgeBottomInset: Dp = 0.dp,
    imageVariant: LibraryCoverImageVariant = LibraryCoverImageVariant.Grid,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(item.id, item.coverUrl, imageVariant) {
        onCoverVisible(item, imageVariant)
    }
    ResolvedLibraryCover(
        item = item,
        coverImageUrlProvider = coverImageUrlProvider,
        isDownloaded = isDownloaded,
        isRead = isRead,
        fadeRead = fadeRead,
        downloadBadgeBottomInset = downloadBadgeBottomInset,
        imageVariant = imageVariant,
        modifier = modifier,
    )
}
