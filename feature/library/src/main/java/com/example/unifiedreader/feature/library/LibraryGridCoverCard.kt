package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem

@Composable
internal fun GridCoverCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    rating: LibraryRatingBadgeState?,
    isDownloaded: Boolean,
    fadeRead: Boolean,
    imageVariant: LibraryCoverImageVariant,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onClick: () -> Unit,
    menuContent: (@Composable () -> Unit)? = null,
) {
    val progressLabel = progress
        ?.takeIf { it.shouldShowCoverProgress }
        ?.percentLabel

    LibraryCoverActionCard(
        item = item,
        title = itemPrimaryText(item),
        coverImageUrlProvider = coverImageUrlProvider,
        imageVariant = imageVariant,
        isDownloaded = isDownloaded,
        isRead = progress?.isComplete == true,
        fadeRead = fadeRead,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-grid-card-${item.id}"),
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f),
        trailingText = progressLabel,
        menuContent = menuContent,
        coverOverlay = if (rating != null) {
            {
                LibraryRatingBadge(
                    state = rating,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                )
            }
        } else {
            null
        },
    )
}
